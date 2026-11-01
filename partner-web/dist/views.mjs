/* QUBAT EV partner cabinet — isolated, local demonstration views. */

const STATUS = {
  online: ['В сети', 'green'], busy: ['Идёт зарядка', 'green'],
  offline: ['Нет связи', 'gray'], error: ['Требует внимания', 'red'],
  available: ['Свободен', 'green'], fault: ['Неисправен', 'red'],
  unavailable: ['Недоступен', 'gray'], active: ['Активен', 'green'],
  invited: ['Демо-приглашение', 'amber'], revoked: ['Доступ отозван', 'gray']
};
const ROLES = { employee: 'Сотрудник партнёра', accountant: 'Бухгалтер', observer: 'Наблюдатель' };
const RIGHTS = [
  ['view', 'Просмотр точек'], ['reports', 'Просмотр отчётов'], ['export', 'Экспорт Excel'],
  ['tariff.edit', 'Изменение тарифов'], ['stations.edit', 'Редактирование точек'],
  ['finance', 'Финансы'], ['discounts.manage', 'Скидки'], ['free.grant', 'Бесплатная зарядка'],
  ['members.manage', 'Управление доступами'], ['notifications.edit', 'Настройка уведомлений']
];
const ROLE_RIGHTS = { employee: ['view','notifications.edit'], accountant: ['view','reports','export','finance','notifications.edit'], observer: ['view','reports','export'] };
const TARIFF_NAMES = { customer: 'Базовый тариф', premium: 'Премиум-тариф', electricity: 'Закупка электроэнергии', platform: 'Комиссия платформы', acquiring: 'Эквайринг', tax: 'Налог', partner: 'Доля партнёра', reserve: 'Резерв', rent: 'Аренда' };

function esc(ctx, value = '') { return ctx.esc(String(value ?? '')); }
function num(value = 0) { const n = Number(value); return Number.isFinite(n) ? new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 }).format(n) : '—'; }
function price(ctx, value) { return value === undefined || value === null || value === '' ? '—' : ctx.money(value); }
function date(ctx, value) { return value ? ctx.dateTime(value) : '—'; }
function day(value) { return value ? String(value).slice(0, 10) : ''; }
function today(ctx) { return day(ctx.store.today || ctx.store.asOf || '2026-11-01'); }
function yes(ctx, right) { return !!ctx.repo.permission(right); }
function badge(ctx, status, stale = false) {
  const [text, color] = stale ? ['Данные устарели', 'amber'] : (STATUS[status] || ['Нет данных', 'gray']);
  return `<span class="chip status ${color}"><span class="status-dot" aria-hidden="true"></span>${esc(ctx, text)}</span>`;
}
function btn(ctx, action, label, cls = 'btn-secondary', attrs = '') {
  return `<button type="button" class="btn ${cls}" data-action="${action}" ${attrs}>${esc(ctx, label)}</button>`;
}
function empty(ctx, title, description) { return `<div class="empty"><strong>${esc(ctx, title)}</strong><p class="muted">${esc(ctx, description)}</p></div>`; }
function input(ctx, name, label, value = '', type = 'text', attrs = '') {
  return `<label class="field"><span>${esc(ctx, label)}</span><input class="input" type="${type}" name="${name}" value="${esc(ctx, value)}" ${attrs}></label>`;
}
function select(ctx, name, label, values, selected = '', attrs = '') {
  return `<label class="field"><span>${esc(ctx, label)}</span><select class="select" name="${name}" ${attrs}>${values.map(([v, l]) => `<option value="${esc(ctx, v)}" ${String(selected) === String(v) ? 'selected' : ''}>${esc(ctx, l)}</option>`).join('')}</select></label>`;
}
function textArea(ctx, name, label, value = '') { return `<label class="field"><span>${esc(ctx, label)}</span><textarea class="input" name="${name}" rows="3">${esc(ctx, value)}</textarea></label>`; }
function definition(ctx, items) { return `<dl class="definition-list">${items.map(([label, value]) => `<div><dt class="muted">${esc(ctx, label)}</dt><dd>${esc(ctx, value === '' || value == null ? '—' : value)}</dd></div>`).join('')}</dl>`; }
function stationName(ctx, id) { return ctx.repo.stations().find(s => s.id === id)?.name || id; }
function organizationField(ctx, selected = 'org-a') {
  if (ctx.repo.account.role !== 'admin') return '';
  const owners = ctx.repo.accounts().filter(a => a.role === 'owner');
  return select(ctx, 'orgId', 'Организация', owners.map(a => [a.orgId, a.orgName]), selected);
}
function stationChecks(ctx, selected = [], name = 'stationIds') {
  return `<fieldset class="form-section"><legend>Доступные точки <span class="muted">· обязательно</span></legend><div class="checkbox-grid">${ctx.repo.stations().map(s => `<label class="checkbox-row"><input type="checkbox" name="${name}" value="${esc(ctx, s.id)}" ${selected.includes(s.id) ? 'checked' : ''}><span>${esc(ctx, s.name)}<small class="muted">${esc(ctx, s.city)} · ${esc(ctx, s.id)}</small></span></label>`).join('')}</div></fieldset>`;
}
function saveModal(ctx, work) {
  return ctx.save(() => { const result = work(); ctx.closeModal(); return result; });
}
function chargerArt() {
  return `<svg class="station-art" viewBox="0 0 120 130" fill="none" aria-hidden="true"><rect x="32" y="10" width="51" height="103" rx="9" fill="#E7EEEA" stroke="#BACDC1"/><path d="M33 85h49v20a8 8 0 0 1-8 8H41a8 8 0 0 1-8-8V85Z" fill="#D0E0D6"/><rect x="41" y="23" width="33" height="33" rx="4" fill="#163F32"/><path d="m60 29-10 14h7l-2 9 11-15h-7l1-8Z" fill="#7DE0A4"/><rect x="44" y="65" width="8" height="12" rx="3" fill="#176D49"/><rect x="63" y="65" width="8" height="12" rx="3" fill="#176D49"/><path d="M83 32h8a7 7 0 0 1 7 7v50a8 8 0 0 1-16 0V73" stroke="#527565" stroke-width="4" stroke-linecap="round"/><path d="M32 34H20a7 7 0 0 0-7 7v49a8 8 0 0 0 16 0V73" stroke="#527565" stroke-width="4" stroke-linecap="round"/><path d="M29 111h59v8H29z" fill="#B6CABE"/><path d="M19 124h78" stroke="#D5E0D9" stroke-width="3" stroke-linecap="round"/></svg>`;
}

export function renderStations(ctx) {
  const f = ctx.ui.stationFilters || {};
  const all = ctx.repo.stations();
  const search = String(f.search || '').toLocaleLowerCase('ru-RU');
  const rows = all.filter(s => (!search || [s.name, s.id, s.serialNumber, s.city, s.address].join(' ').toLocaleLowerCase('ru-RU').includes(search)) && (!f.status || (f.status === 'offline' ? s.status === 'offline' || s.stale : s.status === f.status && !s.stale)) && (!f.brandId || s.brandId === f.brandId) && (!f.groupId || s.groupId === f.groupId) && (!f.ownerId || s.ownerId === f.ownerId));
  const brands = [...new Map(all.map(s => [s.brandId, s.brandName])).entries()];
  const groups = [...new Map(all.map(s => [s.groupId, s.groupName])).entries()];
  const owners = [...new Map(all.map(s => [s.ownerId, s.ownerName])).entries()];
  return `<div class="section-head"><div><p class="eyebrow">РЕЕСТР СЕТИ</p><h2>Точки</h2><p class="muted">Каждая локация, аппарат и разъём — под контролем.</p></div>${yes(ctx, 'stations.edit') ? btn(ctx, 'view-station-create', '+ Добавить точку', 'btn-primary') : ''}</div>
    <div class="card filter-panel"><div class="filter-grid">${input(ctx, 'search', 'Поиск по сети', f.search || '', 'search', 'placeholder="Название, ID, серийный номер, город"')}${select(ctx, 'status', 'Статус', [['', 'Все статусы'], ['online', 'В сети'], ['busy', 'Идёт зарядка'], ['offline', 'Нет свежих данных'], ['error', 'Ошибка']], f.status)}${select(ctx, 'brandId', 'Бренд', [['', 'Все бренды'], ...brands], f.brandId)}${select(ctx, 'groupId', 'Группа', [['', 'Все группы'], ...groups], f.groupId)}${select(ctx, 'ownerId', 'Владелец', [['', 'Все владельцы'], ...owners], f.ownerId)}</div><div class="row"><span class="muted result-count">Найдено: ${rows.length} · аппаратов: ${rows.reduce((n, s) => n + Number(s.hardwareCount || 1), 0)} · разъёмов: ${rows.reduce((n, s) => n + s.ports.length, 0)}</span><div class="actions">${btn(ctx, 'view-stations-reset', 'Сбросить', 'btn-ghost')}${btn(ctx, 'view-stations-filter', 'Применить', 'btn-secondary')}</div></div></div>
    ${rows.length ? `<div class="station-grid">${rows.map(s => stationCard(ctx, s)).join('')}</div>` : empty(ctx, 'Точки не найдены', 'Измените поисковый запрос или сбросьте фильтры.')}
    <p class="muted field-help">ДЕМО · локальные данные. Изменения сохраняются в этом браузере. Подключение к оборудованию отсутствует.</p>`;
}

function stationCard(ctx, s) {
  const active = s.ports.filter(p => p.status === 'busy').length;
  return `<article class="card station-card"><div class="station-top"><span class="eyebrow">${esc(ctx, s.id)} <span class="muted">/ ${esc(ctx, s.brandName)}</span></span>${badge(ctx, s.status, s.stale)}</div><div class="station-heading"><div><h3><button type="button" class="text-button" data-action="view-station-open" data-id="${esc(ctx, s.id)}">${esc(ctx, s.name)}</button></h3><p class="station-address muted">${esc(ctx, s.city)}, ${esc(ctx, s.address)}</p><div class="station-meta"><strong>${num(s.nominalPower)} <span class="muted">кВт</span></strong><span class="muted">${s.hardwareCount || 1} аппарат · ${s.ports.length} разъёма</span></div></div>${chargerArt()}</div><div class="station-ports">${s.ports.map(p => `<span class="port-badge ${s.stale ? 'gray' : (STATUS[p.status]?.[1] || 'gray')}" title="Порт ${esc(ctx, p.number)} · ${esc(ctx, STATUS[p.status]?.[0])}"><span class="port-dot" aria-hidden="true"></span>${esc(ctx, p.type)} <span class="muted">№${esc(ctx, p.number)}</span></span>`).join('')}</div><div class="statline"><span class="muted">${s.stale || s.status === 'offline' ? 'Последний сигнал' : active ? 'Мощность сейчас' : 'Готова к зарядке'}</span><strong>${s.stale || s.status === 'offline' ? esc(ctx, date(ctx, s.lastSeen)) : active ? `${num(s.powerKw)} кВт` : `${price(ctx, s.tariff)} / кВт·ч`}</strong></div><div class="station-bottom"><span class="muted">${esc(ctx, s.groupName)}</span>${btn(ctx, 'view-station-open', 'Подробнее →', 'btn-ghost', `data-id="${esc(ctx, s.id)}"`)}</div></article>`;
}

export function renderStationDetail(ctx, id) {
  let s;
  try { s = ctx.repo.station(id); } catch (_) { return empty(ctx, 'Точка недоступна', 'Точка отсутствует или у текущей роли нет права её просмотра.'); }
  if (!s) return empty(ctx, 'Точка недоступна', 'Выберите точку из списка.');
  const tab = ctx.ui.stationTab || 'info';
  return `<div class="row">${btn(ctx, 'view-stations-back', '← Все точки', 'btn-ghost')}<span class="eyebrow">ДЕМО · ${esc(ctx, s.id)}</span></div><div class="detail-hero card"><div><div class="row"><p class="eyebrow">${esc(ctx, s.brandName)} · ${esc(ctx, s.groupName)}</p>${badge(ctx, s.status, s.stale)}</div><h2>${esc(ctx, s.name)}</h2><p class="muted">${esc(ctx, s.city)}, ${esc(ctx, s.address)}</p><div class="station-meta"><strong>${num(s.nominalPower)} кВт</strong><span>${s.hardwareCount} аппарат · ${s.ports.length} разъёма</span><span>${price(ctx, s.tariff)} / кВт·ч</span></div></div><div class="actions">${yes(ctx, 'stations.edit') ? `${btn(ctx, 'view-station-edit', 'Редактировать', 'btn-secondary', `data-id="${esc(ctx, s.id)}"`)}${btn(ctx, 'view-station-archive', 'В архив', 'btn-ghost', `data-id="${esc(ctx, s.id)}"`)}` : ''}</div></div>
    <div class="tabs" role="tablist" aria-label="Карточка точки">${[['info', 'Информация'], ['parameters', 'Параметры'], ['history', 'История изменений']].map(([value, label]) => `<button type="button" role="tab" aria-selected="${tab === value}" class="tab ${tab === value ? 'active' : ''}" data-action="view-station-tab" data-id="${esc(ctx, s.id)}" data-tab="${value}">${label}</button>`).join('')}</div>
    <section role="tabpanel">${tab === 'parameters' ? parameters(ctx, s) : tab === 'history' ? history(ctx, s) : stationInfo(ctx, s)}</section>`;
}

function stationInfo(ctx, s) {
  return `<div class="grid-2"><article class="card"><div class="section-head"><h3>Оборудование</h3><span class="chip gray">${esc(ctx, s.model)}</span></div>${definition(ctx, [['Внутренний ID', s.id], ['Внешний ID оператора', s.externalId], ['Серийный номер', s.serialNumber], ['OCPP ID', s.ocppId], ['Прошивка', s.firmware], ['Владелец', s.ownerName], ['Группа', s.groupName], ['Режим работы', s.operatingHours], ['Координаты', `${s.latitude || '—'}, ${s.longitude || '—'}`]])}</article><article class="card"><div class="section-head"><h3>Связь и состояние</h3>${badge(ctx, s.status, s.stale)}</div><div class="tariff-value">${s.stale || s.status === 'offline' ? '—' : num(s.powerKw)} <small>кВт сейчас</small></div><p class="muted">${s.stale ? 'Данные не обновлялись. Текущее состояние и мощность неизвестны.' : s.status === 'offline' ? 'Станция не на связи. Ожидаем новый сигнал.' : 'Демонстрационное состояние на момент последнего сигнала.'}</p>${definition(ctx, [['Последний сигнал', date(ctx, s.lastSeen)], ['Источник', s.source], ['Причина / сообщение', s.offlineReason || (s.status === 'error' ? 'Проверьте событие оборудования в журнале.' : 'Нет активных ошибок')]])}<p class="data-source field-help">ДЕМО · Удалённые команды недоступны. Для подключения потребуются официальный API оператора и отдельные права.</p></article></div>
    <article class="card"><div class="section-head"><div><p class="eyebrow">${s.hardwareCount} АППАРАТ · ${s.ports.length} РАЗЪЁМА</p><h3>Независимые порты</h3></div><span class="muted">Статус каждого разъёма</span></div><div class="table-wrap"><table class="table"><thead><tr><th>Порт</th><th>Разъём</th><th>Макс. мощность</th><th>Макс. ток</th><th>Состояние</th><th>Последний сигнал</th></tr></thead><tbody>${s.ports.map(p => `<tr><td><strong>№ ${esc(ctx, p.number)}</strong></td><td>${esc(ctx, p.type)}</td><td>${num(p.maxPower)} кВт</td><td>${num(p.maxCurrent)} А</td><td>${badge(ctx, p.status, s.stale)}</td><td class="muted">${esc(ctx, date(ctx, p.lastSeen))}</td></tr>`).join('')}</tbody></table></div></article>`;
}

function parameters(ctx, s) {
  return `<div class="grid-2"><article class="card"><div class="section-head"><div><p class="eyebrow">СТОИМОСТЬ ДЛЯ КЛИЕНТА</p><h3>Тарифы зарядки</h3></div>${yes(ctx, 'tariff.edit') ? btn(ctx, 'view-tariff-edit', 'Изменить тариф', 'btn-secondary', `data-id="${esc(ctx, s.id)}"`) : '<span class="chip gray">Только просмотр</span>'}</div><div class="grid-2"><div><p class="muted">Базовый тариф</p><div class="tariff-value">${price(ctx, s.currentTariffs?.customer)}<small>/ кВт·ч</small></div></div><div><p class="muted">Премиум-тариф</p><div class="tariff-value">${price(ctx, s.currentTariffs?.premium)}<small>/ кВт·ч</small></div></div></div><p class="field-help muted">Версия тарифа определяется датой сессии. Смена цены сохраняет стоимость завершённых зарядок.</p></article><article class="card"><div class="section-head"><h3>Бронь и ограничения</h3>${yes(ctx, 'stations.edit') ? btn(ctx, 'view-config-edit', 'Настроить', 'btn-ghost', `data-id="${esc(ctx, s.id)}"`) : ''}</div>${definition(ctx, [['Простой, сом/мин', s.idleTariff || '0.00'], ['Окно бронирования', `${s.reservationMinutes ?? 15} мин`], ['Стоимость брони', price(ctx, s.reservationFee || '0')], ['Порог окончания сессии', `${s.stopAtPercent ?? 100} %`]])}<p class="field-help muted">Локальная конфигурация. Настройки не отправляются на оборудование.</p></article></div>
    <article class="card"><div class="section-head"><h3>История тарифов</h3><span class="chip gray">Версии с датами действия</span></div><div class="table-wrap"><table class="table"><thead><tr><th>Условие</th><th>Значение</th><th>Начало действия</th><th>Конец действия</th><th>Автор</th></tr></thead><tbody>${(s.tariffs || []).slice().sort((a,b) => b.effectiveFrom.localeCompare(a.effectiveFrom)).map(t => `<tr><td>${esc(ctx, TARIFF_NAMES[t.kind] || t.kind)}</td><td><strong>${['platform','acquiring','tax','partner','reserve'].includes(t.kind) ? `${num(t.value)} %` : price(ctx, t.value)}</strong></td><td>${esc(ctx, t.effectiveFrom)}</td><td class="muted">${esc(ctx, t.effectiveTo || 'Действует')}</td><td class="muted">${esc(ctx, t.actor || 'QUBAT · демо')}</td></tr>`).join('')}</tbody></table></div></article>`;
}

function history(ctx, s) {
  const events = ctx.repo.events().filter(e => e.stationId === s.id || e.entityId === s.id || e.targetId === s.id || (e.stationIds || []).includes(s.id));
  return `<article class="card"><div class="section-head"><div><h3>Журнал изменений</h3><p class="muted">Время, автор и значения до и после изменения.</p></div><span class="chip gray">${events.length} событий</span></div>${events.length ? `<div class="timeline">${events.map(e => auditEvent(ctx, e)).join('')}</div>` : empty(ctx, 'Изменений пока нет', 'Сохранение параметров и новых тарифов добавит запись в этот журнал.')}</article>`;
}

function auditEvent(ctx, e) {
  const actions = { 'station.updated':'Параметры точки изменены', 'station.created':'Точка добавлена', 'station.archived':'Точка отправлена в архив', 'tariff.updated':'Создана версия тарифа', 'finance.month.updated':'Обновлены финансы месяца', 'report.exported':'Отчёт экспортирован', 'member.invited':'Создано демо-приглашение', 'member.revoked':'Доступ сотрудника отозван', 'discount.updated':'Скидка сохранена', 'free.granted':'Одобрена бесплатная зарядка', 'brand.updated':'Бренд сохранён', 'notifications.updated':'Условия уведомлений изменены' };
  const label = actions[e.action] || e.title || e.description || 'Изменение параметров';
  const before = e.before ?? e.oldValue;
  const after = e.after ?? e.newValue;
  let changes = '';
  if (Array.isArray(after) && after[0]?.kind) {
    const previous = Array.isArray(before) ? before : [];
    const added = after.find(t => !previous.some(p => p.id === t.id)) || after[after.length - 1];
    const old = previous.filter(t => t.effectiveFrom <= added.effectiveFrom).sort((a,b) => b.effectiveFrom.localeCompare(a.effectiveFrom))[0];
    changes = `<div class="audit-values"><span class="muted">Было: ${price(ctx, old?.value)}</span><span>Стало: ${price(ctx, added.value)} · ${esc(ctx, TARIFF_NAMES[added.kind] || added.kind)} · с ${esc(ctx, added.effectiveFrom)}</span></div>`;
  } else if (after && typeof after === 'object' && !Array.isArray(after)) {
    const labels = {name:'Название',city:'Город',address:'Адрес',groupName:'Группа',model:'Модель',serialNumber:'Серийный номер',ocppId:'OCPP ID',externalId:'Внешний ID',firmware:'Прошивка',operatingHours:'Режим работы',nominalPower:'Мощность, кВт',hardwareCount:'Аппаратов',latitude:'Широта',longitude:'Долгота',brandName:'Бренд',idleTariff:'Простой, сом/мин',reservationMinutes:'Окно брони, мин',reservationFee:'Стоимость брони, сом',stopAtPercent:'Порог заряда, %',archived:'В архиве'};
    const changed = Object.keys(labels).filter(key => after[key] !== undefined && String(before?.[key] ?? '') !== String(after[key] ?? ''));
    changes = changed.map(key => `<div class="audit-values"><strong>${labels[key]}</strong><span class="muted">Было: ${esc(ctx, before?.[key] ?? '—')}</span><span>Стало: ${esc(ctx, typeof after[key] === 'boolean' ? after[key] ? 'Да' : 'Нет' : after[key])}</span></div>`).join('');
    if (after.ports && JSON.stringify(after.ports) !== JSON.stringify(before?.ports)) {
      const ports = list => (list || []).map(p => `№${p.number} ${p.type} · ${p.maxPower} кВт`).join('; ') || '—';
      changes += `<div class="audit-values"><strong>Разъёмы</strong><span class="muted">Было: ${esc(ctx, ports(before?.ports))}</span><span>Стало: ${esc(ctx, ports(after.ports))}</span></div>`;
    }
  } else if (before !== undefined || after !== undefined) {
    changes = `<div class="audit-values"><span class="muted">Было: ${esc(ctx, before ?? '—')}</span><span>Стало: ${esc(ctx, after ?? '—')}</span></div>`;
  }
  const actor = e.actorName || ctx.repo.accounts?.().find(a => a.id === e.actor)?.name || e.actor || 'QUBAT · демо';
  return `<div class="audit-item"><div class="row"><strong>${esc(ctx, label)}</strong><time class="muted">${esc(ctx, date(ctx, e.at || e.createdAt || e.timestamp))}</time></div><p class="audit-meta muted">${esc(ctx, actor)}</p>${changes || (e.message && !e.action ? `<p class="muted">${esc(ctx, e.message)}</p>` : '')}</div>`;
}

export function renderAccess(ctx) {
  if (!yes(ctx, 'members.manage')) return empty(ctx, 'Доступы управляются владельцем сети', 'Ваша роль позволяет работать только с назначенными разделами и точками.');
  const members = ctx.repo.members();
  const discounts = ctx.repo.discounts();
  const canManage = yes(ctx, 'members.manage');
  return `<div class="section-head"><div><p class="eyebrow">КОМАНДА И ПРИВИЛЕГИИ</p><h2>Доступы</h2><p class="muted">Нужные права — нужным людям. Каждое изменение в журнале.</p></div>${canManage ? btn(ctx, 'view-member-invite', '+ Пригласить сотрудника', 'btn-primary') : ''}</div><div class="grid-3 access-summary"><article class="card"><p class="muted">Участники команды</p><strong class="tariff-value">${members.filter(m => m.status !== 'revoked').length}</strong><span class="muted">в пределах вашей организации</span></article><article class="card"><p class="muted">Роли и права</p><strong class="tariff-value">3 <small>роли команды</small></strong><span class="muted">назначение на отдельные точки</span></article><article class="card"><p class="muted">Безопасность</p><strong>Действия записываются</strong><p class="muted">В демо вход и приглашения локальные. Для реальной системы необходимы 2FA и серверная проверка.</p></article></div>
    <article class="card"><div class="section-head"><h3>Сотрудники и партнёры</h3><span class="chip gray">ДЕМО · без отправки сообщений</span></div>${members.length ? `<div class="table-wrap"><table class="table"><thead><tr><th>Участник</th><th>Роль и права</th><th>Доступные точки</th><th>Срок доступа</th><th>Статус</th><th><span class="sr-only">Действия</span></th></tr></thead><tbody>${members.map(m => `<tr><td><strong>${esc(ctx, m.name)}</strong><div class="muted">${esc(ctx, m.contact)}</div></td><td>${esc(ctx, ROLES[m.role] || m.role)}<div class="muted field-help">${esc(ctx, (m.permissions || []).map(p => RIGHTS.find(r => r[0] === p)?.[1] || p).join(' · ') || 'По роли')}</div></td><td>${(m.stationIds || []).map(id => `<div>${esc(ctx, stationName(ctx, id))}</div>`).join('')}</td><td>${esc(ctx, m.startsAt)}<div class="muted">до ${esc(ctx, m.endsAt || 'бессрочно')}</div></td><td>${badge(ctx, m.status)}</td><td>${canManage && m.status !== 'revoked' ? btn(ctx, 'view-member-revoke', 'Отозвать', 'btn-ghost', `data-id="${esc(ctx, m.id)}"`) : ''}</td></tr>`).join('')}</tbody></table></div>` : empty(ctx, 'Команда ещё не создана', 'Добавьте локальное демо-приглашение и назначьте конкретные точки.')}</article>
    <article class="card"><div class="section-head"><div><h3>Скидки и бесплатная зарядка</h3><p class="muted">Ограничены сроком, числом использований и списком точек.</p></div><div class="actions">${yes(ctx, 'discounts.manage') ? btn(ctx, 'view-discount-create', '+ Скидка', 'btn-secondary') : ''}${yes(ctx, 'free.grant') ? btn(ctx, 'view-free-create', '+ Бесплатная зарядка', 'btn-secondary') : ''}</div></div>${discounts.length ? `<div class="table-wrap"><table class="table"><thead><tr><th>Разрешение</th><th>Условия</th><th>Точки</th><th>Период</th><th>Лимит</th><th>Одобрил</th></tr></thead><tbody>${discounts.map(d => `<tr><td><strong>${esc(ctx, d.name || d.reason || 'Бесплатная зарядка')}</strong><div class="muted">${d.type === 'free' ? 'Отдельное разрешение' : 'Скидка'}</div></td><td>${d.type === 'free' ? '<span class="chip green">Бесплатно</span>' : d.kind === 'percent' ? `${num(d.value)} %` : price(ctx, d.value)}</td><td>${(d.stationIds || []).map(id => esc(ctx, stationName(ctx, id))).join('<br>')}</td><td>${esc(ctx, d.startsAt)}<div class="muted">до ${esc(ctx, d.endsAt)}</div></td><td>${num(d.used || 0)} / ${num(d.usageLimit)}</td><td class="muted">${esc(ctx, d.approvedBy)}</td></tr>`).join('')}</tbody></table></div>` : empty(ctx, 'Привилегий пока нет', 'Добавьте скидку или отдельное разрешение на бесплатную зарядку.')}</article>
    <article class="card"><h3>Разделение полномочий</h3><div class="grid-3"><div><h4>Сотрудник партнёра</h4><p class="muted">Назначенные точки и разрешённые действия. Общие финансы по умолчанию скрыты.</p></div><div><h4>Бухгалтер</h4><p class="muted">Финансы, расходы и отчёты назначенного владельца. Команды оборудованию недоступны.</p></div><div><h4>Наблюдатель</h4><p class="muted">Просмотр назначенных точек и отчётов. Изменение данных запрещено.</p></div></div><p class="field-help muted">ДЕМО · Это демонстрация прав доступа. Подтверждение контакта и двухфакторный вход появятся при подключении сервера.</p></article>`;
}

export function renderBrands(ctx) {
  const brands = ctx.repo.brands();
  const stations = ctx.repo.stations();
  return `<div class="section-head"><div><p class="eyebrow">БРЕНДЫ ВАШЕЙ СЕТИ</p><h2>Бренды</h2><p class="muted">Единое представление точек и оборудования.</p></div>${yes(ctx, 'brands.edit') ? btn(ctx, 'view-brand-create', '+ Добавить бренд', 'btn-primary') : ''}</div>${brands.length ? `<div class="grid-3 brand-grid">${brands.map(b => { const list = stations.filter(s => s.brandId === b.id); return `<article class="card"><div class="section-head"><span class="brand-swatch" style="background-color:${/^#[0-9a-fA-F]{6}$/.test(b.color || '') ? b.color : '#176D49'}" aria-hidden="true">${esc(ctx, b.name.slice(0, 1))}</span><span class="chip gray">${list.length} точек</span></div><h3>${esc(ctx, b.name)}</h3><p class="muted">${esc(ctx, b.description || 'Бренд оборудования вашей организации.')}</p><div class="statline"><span class="muted">Разъёмов в сети</span><strong>${list.reduce((n,s) => n + s.ports.length, 0)}</strong></div><div class="row"><span class="eyebrow">${esc(ctx, b.id)}</span>${yes(ctx, 'brands.edit') ? btn(ctx, 'view-brand-edit', 'Редактировать', 'btn-ghost', `data-id="${esc(ctx, b.id)}"`) : ''}</div></article>`; }).join('')}</div>` : empty(ctx, 'Бренды ещё не добавлены', 'Создайте бренд, чтобы объединить станции своей сети.')}`;
}

function stationModal(ctx, id) {
  const s = id ? ctx.repo.station(id) : {};
  const brands = ctx.repo.brands();
  const ports = s.ports || [{ number: 1, type: 'GB/T DC', maxPower: '80', maxCurrent: '200' }, { number: 2, type: 'GB/T DC', maxPower: '80', maxCurrent: '200' }];
  const body = `<p class="field-help muted">ДЕМО · Изменяется только локальный реестр. Один аппарат может иметь два независимых разъёма.</p><div class="form-grid">${!id ? organizationField(ctx) : ''}${input(ctx, 'name', 'Название точки *', s.name, 'text', 'required maxlength="100"')}${select(ctx, 'brandId', 'Бренд', brands.map(b => [b.id, b.name]), s.brandId || brands[0]?.id)}${input(ctx, 'city', 'Город *', s.city || 'Бишкек', 'text', 'required maxlength="80"')}${input(ctx, 'address', 'Адрес *', s.address, 'text', 'required maxlength="200"')}${input(ctx, 'groupName', 'Группа / локация', s.groupName || 'Новая локация')}${input(ctx, 'operatingHours', 'Режим работы', s.operatingHours || 'Круглосуточно')}${input(ctx, 'model', 'Модель', s.model)}${input(ctx, 'serialNumber', 'Серийный номер', s.serialNumber)}${input(ctx, 'externalId', 'Внешний ID оператора', s.externalId)}${input(ctx, 'ocppId', 'OCPP ID', s.ocppId)}${input(ctx, 'firmware', 'Версия прошивки', s.firmware)}${input(ctx, 'nominalPower', 'Номинальная мощность, кВт', s.nominalPower || '80', 'number', 'min="0.1" step="0.1" required')}${input(ctx, 'hardwareCount', 'Физические аппараты', s.hardwareCount || 1, 'number', 'min="1" max="100" step="1" required')}${select(ctx, 'portCount', 'Независимые разъёмы', [[1,'1 разъём'],[2,'2 разъёма'],[3,'3 разъёма'],[4,'4 разъёма']], ports.length)}${input(ctx, 'latitude', 'Широта', s.latitude || '42.8746', 'number', 'step="any" min="-90" max="90"')}${input(ctx, 'longitude', 'Долгота', s.longitude || '74.5698', 'number', 'step="any" min="-180" max="180"')}</div><fieldset class="form-section"><legend>Порты</legend><p class="muted field-help">Первые N портов сохранятся в соответствии с выбранным числом разъёмов.</p>${[0,1,2,3].map(i => { const p=ports[i] || {type:'GB/T DC',maxPower:s.nominalPower || '80',maxCurrent:'200'}; return `<div class="form-grid">${select(ctx, `portType${i}`, `Порт ${i+1} · тип`, [['GB/T DC','GB/T DC'],['CCS2','CCS2'],['Type 2','Type 2'],['CHAdeMO','CHAdeMO']], p.type)}${input(ctx, `portPower${i}`, 'Макс. мощность, кВт', p.maxPower, 'number', 'min="0.1" step="0.1"')}${input(ctx, `portCurrent${i}`, 'Макс. ток, А', p.maxCurrent, 'number', 'min="1" step="1"')}</div>`; }).join('')}</fieldset>`;
  ctx.openModal({ title: id ? 'Редактировать точку' : 'Добавить точку', body, onSubmit: fd => saveModal(ctx, () => {
    const patch = { ...(id ? { id } : {}), ...(fd.get('orgId') ? {orgId:fd.get('orgId')} : {}), name: fd.get('name'), city: fd.get('city'), address: fd.get('address'), brandId: fd.get('brandId'), groupName: fd.get('groupName'), operatingHours: fd.get('operatingHours'), model: fd.get('model'), serialNumber: fd.get('serialNumber'), externalId: fd.get('externalId'), ocppId: fd.get('ocppId'), firmware: fd.get('firmware'), nominalPower: fd.get('nominalPower'), hardwareCount: Number(fd.get('hardwareCount')), latitude: fd.get('latitude'), longitude: fd.get('longitude') };
    const groupName = String(fd.get('groupName') || '').trim();
    const existingGroup = ctx.repo.stations().find(g => g.groupName === groupName && g.orgId === (s.orgId || fd.get('orgId') || ctx.repo.account.orgId));
    patch.groupId = groupName ? existingGroup?.groupId || `group-${groupName.toLowerCase().replace(/[^\p{L}\p{N}]+/gu, '-').slice(0,100)}` : 'ungrouped';
    patch.ports = Array.from({length:Number(fd.get('portCount'))}, (_,i) => ({ ...(ports[i] || {}), number: i+1, type: fd.get(`portType${i}`), maxPower: fd.get(`portPower${i}`), maxCurrent: fd.get(`portCurrent${i}`), status: ports[i]?.status || 'unavailable', powerKw: ports[i]?.powerKw || '0', lastSeen: ports[i]?.lastSeen || null }));
    return ctx.repo.saveStation(patch);
  }) });
}

function tariffModal(ctx, id) {
  const s = ctx.repo.station(id);
  let effectiveFrom = today(ctx);
  while (s.tariffs.some(t => ['customer','premium'].includes(t.kind) && t.effectiveFrom === effectiveFrom)) { const next = new Date(`${effectiveFrom}T00:00:00Z`); next.setUTCDate(next.getUTCDate() + 1); effectiveFrom = next.toISOString().slice(0,10); }
  ctx.openModal({ title: 'Новая версия тарифа', body: `<p class="muted">${esc(ctx, s.name)} · цена завершённых сессий сохранится.</p><div class="form-grid">${select(ctx, 'kind', 'Тип тарифа', [['customer','Базовый тариф'],['premium','Премиум-тариф']], 'customer')}${input(ctx, 'value', 'Новая цена, сом/кВт·ч *', s.currentTariffs?.customer || '13.50', 'number', 'min="0.01" step="0.01" required')}${input(ctx, 'effectiveFrom', 'Дата начала действия *', effectiveFrom, 'date', 'required')}</div><div class="card tariff-preview" data-tariff-preview><p class="eyebrow">ПРЕДВАРИТЕЛЬНЫЙ ПРОСМОТР</p><p>Текущая базовая цена: <strong>${price(ctx, s.currentTariffs?.customer)} / кВт·ч</strong></p><p class="muted">Укажите новую цену и дату, затем нажмите «Проверить изменение».</p></div>${btn(ctx, 'view-tariff-preview', 'Проверить изменение', 'btn-secondary', `data-id="${esc(ctx, id)}"`)}<p class="field-help muted">Сохранение создаст новую версию и запись с автором, временем и прежним значением.</p>`, onSubmit: fd => saveModal(ctx, () => ctx.repo.updateTariff(id, { kind: fd.get('kind'), value: fd.get('value'), effectiveFrom: fd.get('effectiveFrom') })) });
}

function configModal(ctx, id) {
  const s = ctx.repo.station(id);
  ctx.openModal({ title: 'Бронь и ограничения', body: `<p class="muted">ДЕМО · локальная конфигурация ${esc(ctx, s.name)}.</p><div class="form-grid">${input(ctx, 'idleTariff', 'Простой, сом/мин', s.idleTariff || '0', 'number', 'min="0" step="0.01" required')}${input(ctx, 'reservationMinutes', 'Окно бронирования, мин', s.reservationMinutes ?? 15, 'number', 'min="0" max="1440" step="1" required')}${input(ctx, 'reservationFee', 'Стоимость брони, сом', s.reservationFee || '0', 'number', 'min="0" step="0.01" required')}${input(ctx, 'stopAtPercent', 'Окончание сессии при заряде, %', s.stopAtPercent ?? 100, 'number', 'min="0" max="100" step="1" required')}</div>`, onSubmit: fd => saveModal(ctx, () => ctx.repo.saveStation({ id, idleTariff: fd.get('idleTariff'), reservationMinutes: Number(fd.get('reservationMinutes')), reservationFee: fd.get('reservationFee'), stopAtPercent: Number(fd.get('stopAtPercent')) })) });
}

function memberRights(ctx, role) {
  return `<p class="field-help muted">Просмотр назначенных точек включён. Остальные права можно ограничить.</p><div class="checkbox-grid">${(ROLE_RIGHTS[role] || []).filter(p => p !== 'view').map(key => `<label class="checkbox-row"><input type="checkbox" name="permissions" value="${key}" checked><span>${esc(ctx, RIGHTS.find(r => r[0] === key)?.[1] || key)}</span></label>`).join('')}</div>`;
}

function memberModal(ctx) {
  ctx.openModal({ title: 'Пригласить сотрудника · ДЕМО', body: `<div class="data-source"><strong>Локальное демо-приглашение</strong><p class="muted">Письмо и SMS не отправляются. Подтверждение контакта и двухфакторный вход потребуют подключения сервера.</p></div><div class="form-grid">${organizationField(ctx)}${input(ctx, 'name', 'Имя *', '', 'text', 'required maxlength="100"')}${input(ctx, 'contact', 'Телефон или email *', '', 'text', 'required maxlength="120" placeholder="name@example.kg"')}${input(ctx, 'startsAt', 'Начало доступа', today(ctx), 'date', 'required')}${input(ctx, 'endsAt', 'Окончание доступа', '', 'date')}</div><fieldset class="form-section"><legend>Роль</legend><input type="hidden" name="role" value="observer"><div class="actions" role="group" aria-label="Роль участника">${Object.entries(ROLES).map(([role,label]) => `<button type="button" class="btn ${role === 'observer' ? 'btn-primary' : 'btn-secondary'}" data-action="view-member-role" data-role="${role}" aria-pressed="${role === 'observer'}">${label}</button>`).join('')}</div></fieldset>${stationChecks(ctx)}<fieldset class="form-section"><legend>Права выбранной роли</legend><div data-member-rights>${memberRights(ctx, 'observer')}</div></fieldset>`, onSubmit: fd => saveModal(ctx, () => ctx.repo.inviteMember({ orgId: fd.get('orgId') || undefined, name: fd.get('name'), contact: fd.get('contact'), role: fd.get('role'), startsAt: fd.get('startsAt'), endsAt: fd.get('endsAt') || null, stationIds: fd.getAll('stationIds'), permissions: ['view', ...fd.getAll('permissions')] })) });
}

function discountModal(ctx, free = false) {
  const end = new Date(`${today(ctx)}T00:00:00Z`); end.setUTCMonth(end.getUTCMonth() + 1);
  ctx.openModal({ title: free ? 'Разрешить бесплатную зарядку · ДЕМО' : 'Создать скидку · ДЕМО', body: `<p class="muted">${free ? 'Отдельное разрешение с обязательным основанием, сроком и лимитом. Операции зарядки не запускаются.' : 'Привилегия действует только на выбранных точках и в пределах лимита.'}</p><div class="form-grid">${organizationField(ctx)}${input(ctx, free ? 'reason' : 'name', free ? 'Основание / получатель *' : 'Название / получатель *', '', 'text', 'required maxlength="150"')}${free ? '' : `${select(ctx, 'kind', 'Тип скидки', [['percent','Процент'],['fixed','Фиксированная сумма, сом']], 'percent')}${input(ctx, 'value', 'Размер скидки *', '10', 'number', 'min="0.01" step="0.01" required')}`}${input(ctx, 'startsAt', 'Начало действия *', today(ctx), 'date', 'required')}${input(ctx, 'endsAt', 'Окончание действия *', end.toISOString().slice(0,10), 'date', 'required')}${input(ctx, 'usageLimit', 'Лимит использований *', '3', 'number', 'min="1" max="100000" step="1" required')}</div>${stationChecks(ctx)}<p class="data-source field-help">Одобрение фиксируется от имени текущей роли: ${esc(ctx, ctx.repo.account.name || ctx.repo.account.role)}. Изменение будет записано в аудит.</p>`, onSubmit: fd => saveModal(ctx, () => {
    const patch = { orgId: fd.get('orgId') || undefined, stationIds: fd.getAll('stationIds'), startsAt: fd.get('startsAt'), endsAt: fd.get('endsAt'), usageLimit: Number(fd.get('usageLimit')) };
    return free ? ctx.repo.saveFreeGrant({ ...patch, reason: fd.get('reason') }) : ctx.repo.saveDiscount({ ...patch, name: fd.get('name'), kind: fd.get('kind'), value: fd.get('value') });
  }) });
}

function brandModal(ctx, id) {
  const b = id ? ctx.repo.brands().find(x => x.id === id) : {};
  if (!b) return;
  ctx.openModal({ title: id ? 'Редактировать бренд' : 'Добавить бренд', body: `<p class="muted">Бренд доступен только в вашей организации.</p><div class="form-grid">${!id ? organizationField(ctx) : ''}${input(ctx, 'name', 'Название бренда *', b.name, 'text', 'required maxlength="80"')}${input(ctx, 'color', 'Цвет бренда', b.color || '#176D49', 'color', 'required')}</div>${textArea(ctx, 'description', 'Описание', b.description)}`, onSubmit: fd => saveModal(ctx, () => ctx.repo.saveBrand({ ...(id ? {id} : {}), orgId: fd.get('orgId') || undefined, name: fd.get('name'), color: fd.get('color'), description: fd.get('description') })) });
}

export function handleViewAction(action, element, ctx) {
  const id = element.dataset.id;
  switch (action) {
    case 'view-stations-filter': {
      const panel = element.closest('.filter-panel');
      ctx.ui.stationFilters = Object.fromEntries(['search','status','brandId','groupId','ownerId'].map(k => [k, panel.querySelector(`[name="${k}"]`).value]));
      ctx.navigate('stations'); return true;
    }
    case 'view-stations-reset': ctx.ui.stationFilters = {}; ctx.navigate('stations'); return true;
    case 'view-station-open': ctx.ui.stationTab = 'info'; ctx.navigate('station', id); return true;
    case 'view-stations-back': ctx.navigate('stations'); return true;
    case 'view-station-tab': ctx.ui.stationTab = element.dataset.tab; ctx.navigate('station', id); return true;
    case 'view-station-create': stationModal(ctx); return true;
    case 'view-station-edit': stationModal(ctx, id); return true;
    case 'view-station-archive': {
      const s = ctx.repo.station(id);
      ctx.openModal({title:'Отправить точку в архив?',body:`<p><strong>${esc(ctx,s.name)}</strong> исчезнет из активного реестра. История сессий и финансов сохранится.</p><p class="muted">ДЕМО · команда на оборудование не отправляется.</p>`,onSubmit:() => saveModal(ctx,()=>{ctx.repo.archiveStation(id); ctx.navigate('stations');})}); return true;
    }
    case 'view-tariff-edit': tariffModal(ctx, id); return true;
    case 'view-config-edit': configModal(ctx, id); return true;
    case 'view-tariff-preview': {
      const form = element.closest('form');
      const value = form.querySelector('[name="value"]').value;
      const kind = form.querySelector('[name="kind"]').value;
      const from = form.querySelector('[name="effectiveFrom"]').value;
      const s = ctx.repo.station(id);
      const preview = form.querySelector('[data-tariff-preview]');
      preview.innerHTML = Number(value) > 0 && from ? `<p class="eyebrow">ПРЕДВАРИТЕЛЬНЫЙ ПРОСМОТР</p><p>${esc(ctx,TARIFF_NAMES[kind])}: <strong>${price(ctx,s.currentTariffs?.[kind])} → ${price(ctx,value)} / кВт·ч</strong></p><p class="muted">С ${esc(ctx,from)}. Завершённые сессии сохранят прежнюю цену.</p>` : '<p class="error-text">Укажите положительную цену и дату начала действия.</p>';
      return true;
    }
    case 'view-member-invite': memberModal(ctx); return true;
    case 'view-member-role': {
      const form = element.closest('form');
      const role = element.dataset.role;
      if (form && ROLE_RIGHTS[role]) {
        form.querySelector('[name="role"]').value = role;
        form.querySelector('[data-member-rights]').innerHTML = memberRights(ctx, role);
        form.querySelectorAll('[data-action="view-member-role"]').forEach(button => { const active = button.dataset.role === role; button.classList.toggle('btn-primary', active); button.classList.toggle('btn-secondary', !active); button.setAttribute('aria-pressed', String(active)); });
      }
      return true;
    }
    case 'view-member-revoke': {
      const m = ctx.repo.members().find(x=>x.id===id);
      ctx.openModal({title:'Отозвать доступ?',body:`<p>Доступ для <strong>${esc(ctx,m?.name)}</strong> будет отозван немедленно. Изменение попадёт в журнал.</p>`,onSubmit:()=>saveModal(ctx,()=>ctx.repo.revokeMember(id))}); return true;
    }
    case 'view-discount-create': discountModal(ctx); return true;
    case 'view-free-create': discountModal(ctx,true); return true;
    case 'view-brand-create': brandModal(ctx); return true;
    case 'view-brand-edit': brandModal(ctx,id); return true;
    default: return false;
  }
}
