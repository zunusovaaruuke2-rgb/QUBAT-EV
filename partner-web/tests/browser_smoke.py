"""Portable browser acceptance checks. Start the static server first; no real accounts or hardware."""
from pathlib import Path
from playwright.sync_api import sync_playwright
from openpyxl import load_workbook
import json

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'work'/'browser-checks'
OUT.mkdir(parents=True,exist_ok=True)
with sync_playwright() as p:
    browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
    context=browser.new_context(viewport={'width':1440,'height':1024},locale='ru-RU',accept_downloads=True)
    page=context.new_page()
    errors=[]
    page.on('pageerror',lambda e: errors.append(str(e)))
    def click(action,extra=''):
        page.locator(f'[data-action="{action}"]'+extra).first.click()
    def nav(name):
        page.locator(f'.sidebar .nav-link[data-page="{name}"]').click()
    def role(name):
        click('roles')
        click('role-select',f'[data-id="{name}"]')
    def shot(name):
        page.wait_for_timeout(350)
        page.screenshot(path=str(OUT/f'{name}.png'),full_page=True)
    def stored():
        return json.loads(page.evaluate("localStorage.getItem('qubat-partner-web-demo-v1')"))
    page.goto('http://127.0.0.1:4173',wait_until='networkidle')
    assert page.locator('h1').inner_text()=='Мониторинг'
    assert page.locator('.kpi').count()==4
    assert page.locator('[data-filter=to]').input_value()=='2026-11-01'
    shot('desktop-monitoring')
    nav('stations')
    assert page.locator('.station-card').count()==3
    click('view-station-open')
    click('view-station-tab','[data-tab="parameters"]')
    assert page.locator('[data-action=view-tariff-edit]').is_visible(), 'Parameters tab must switch'
    click('view-tariff-edit')
    page.locator('#modal [name=value]').fill('14.75')
    page.locator('#modal [name=effectiveFrom]').fill('2026-11-02')
    click('view-tariff-preview')
    assert '14,75' in page.locator('[data-tariff-preview]').inner_text()
    page.locator('#modal [type=submit]').click()
    assert not page.locator('#modal').is_visible()
    assert any(t['value']=='14.75' and t['effectiveFrom']=='2026-11-02' for t in stored()['store']['stations'][0]['tariffs'])
    before=stored()['store']['sessions'][0]['charged']
    page.reload(wait_until='networkidle')
    assert stored()['store']['sessions'][0]['charged']==before
    click('view-station-tab','[data-tab="history"]')
    assert '14.75' in page.locator('#main').inner_text() or '14,75' in page.locator('#main').inner_text()
    shot('desktop-station-history')
    nav('reports')
    assert page.locator('.table tbody tr').count()<=20
    click('report-next')
    assert '2 /' in page.locator('.pagination').inner_text()
    with page.expect_download() as d:
        click('export')
    report_file=OUT/d.value.suggested_filename
    d.value.save_as(str(report_file))
    book=load_workbook(report_file,data_only=True)
    assert book.sheetnames==['Сводка','Сессии','По станциям','Платежи','Расходы']
    assert len(list(book['Сессии'].values))>20, 'Export includes full filtered data, not just page'
    nav('finance')
    click('period','[data-value="previous"]')
    page.locator('[data-filter=stationIdFilter]').select_option('st-a1')
    click('finance-mode','[data-value="actual"]')
    click('month-edit','[data-station="st-a1"]')
    page.locator('#modal [name=actualElectricity]').fill('6543.21')
    page.locator('#modal [name=billNumber]').fill('ДЕМО-ПРОВЕРКА-001')
    page.locator('#modal [name=comment]').fill('Тест сохранения квитанции')
    page.locator('#modal [type=submit]').click()
    assert not page.locator('#modal').is_visible()
    ledger=next(x for x in stored()['store']['monthLedger'] if x['stationId']=='st-a1' and x['month']=='2026-10')
    assert ledger['bill']['amount']=='6543.21'
    page.reload(wait_until='networkidle')
    assert next(x for x in stored()['store']['monthLedger'] if x['id']==ledger['id'])['bill']['number']=='ДЕМО-ПРОВЕРКА-001'
    click('period','[data-value="previous"]')
    page.locator('[data-filter=stationIdFilter]').select_option('st-a1')
    shot('desktop-finances')
    click('profit-formula')
    page.locator('#modal [name=deductions][value=payout]').check()
    page.locator('#modal [type=submit]').click()
    assert 'Переводы партнёру · в прибыли' in page.locator('#main').inner_text()
    nav('brands')
    click('view-brand-create')
    page.locator('#modal [name=name]').fill('QUBAT QA · ДЕМО')
    page.locator('#modal [name=description]').fill('Тестовый бренд')
    page.locator('#modal [type=submit]').click()
    assert 'QUBAT QA' in page.locator('#main').inner_text()
    nav('access')
    click('view-member-invite')
    page.locator('#modal [name=name]').fill('Демо-тестировщик')
    page.locator('#modal [name=contact]').fill('qa@example.invalid')
    page.locator('#modal [name=stationIds]').first.check()
    page.locator('#modal [type=submit]').click()
    assert 'Демо-тестировщик' in page.locator('#main').inner_text()
    shot('desktop-access')
    role('owner-b')
    nav('stations')
    assert page.locator('.station-card').count()==2
    page.goto('http://127.0.0.1:4173/#station/st-a1',wait_until='networkidle')
    page.wait_for_function("document.querySelector('#main').innerText.toLowerCase().includes('недоступ')")
    assert 'Асанбай' not in page.locator('#main').inner_text()
    role('observer')
    assert page.locator('.sidebar .nav-link[data-page=finance]').count()==0
    nav('reports')
    assert 'Комиссии' not in page.locator('#main table').inner_text()
    with page.expect_download() as d:
        click('export')
    observer_file=OUT/'observer.xlsx'
    d.value.save_as(str(observer_file))
    ob=load_workbook(observer_file,data_only=True)
    assert ob['Расходы'].max_row<=2, 'Observer gets no cost rows'
    role('employee')
    assert page.locator('.sidebar .nav-link[data-page=reports]').count()==0
    nav('stations')
    assert page.locator('.station-card').count()==1
    assert page.locator('[data-action=view-station-create]').count()==0
    role('owner-a')
    nav('access')
    click('view-member-revoke','[data-id="member-employee"]')
    page.locator('#modal [type=submit]').click()
    role('employee')
    assert 'Доступ отозван' in page.locator('h1').inner_text()
    role('admin')
    nav('stations')
    assert page.locator('.station-card').count()==5
    role('owner-a')
    click('theme')
    assert page.locator('html').get_attribute('data-theme')=='dark'
    shot('desktop-dark')
    click('theme')
    page.set_viewport_size({'width':390,'height':844})
    nav_button=page.locator('[aria-label="Открыть меню"]')
    nav_button.click()
    page.locator('.sidebar .nav-link[data-page=monitor]').click()
    assert not page.locator('.shell').evaluate("e=>e.classList.contains('nav-open')")
    assert page.evaluate('document.documentElement.scrollWidth<=window.innerWidth'), 'No horizontal page overflow'
    shot('mobile-monitoring')
    nav_button.click()
    page.locator('.sidebar .nav-link[data-page=finance]').click()
    click('period','[data-value="previous"]')
    shot('mobile-finances')
    assert page.evaluate('document.documentElement.scrollWidth<=window.innerWidth')
    # Expected foreign-station UI denial is logged via console.error, not an uncaught pageerror.
    assert not errors, errors
    print('PASS: roles, station scope, tariff history, persistence, XLSX, actual bills, formula, access, brands, revocation, mobile and dark theme')
    browser.close()
