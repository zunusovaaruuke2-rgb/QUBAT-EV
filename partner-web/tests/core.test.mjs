import test from 'node:test';
import assert from 'node:assert/strict';
import { createDemoStore, DemoRepository, money, energy, add, sub, mul, div, percent, dateRange, bishkekDate } from '../dist/core.mjs';

const fixture = (id = 'owner-a') => { const store = createDemoStore(); return { store, repo: new DemoRepository(store, id) }; };

test('fixed-point decimal money stays exact, including large values and rounding', () => {
  assert.equal(add('0.10', '0.20'), '0.30');
  assert.equal(sub('1.00', '0.90'), '0.10');
  assert.equal(add('9007199254740993.01', '0.09'), '9007199254740993.10');
  assert.equal(mul('13.50', '15.123'), '204.16');
  assert.equal(percent('204.16', '1.50'), '3.06');
  assert.equal(money('-1.005'), '-1.01');
  assert.equal(energy('1.2'), '1.200');
  assert.equal(div('10', '3'), '3.33');
  assert.throws(() => money(0.1), /строку/);
  assert.throws(() => money('NaN'));
  assert.throws(() => div('1', '0'));
});

test('each account has explicit organization and station scope', () => {
  const { store, repo } = fixture();
  assert.deepEqual(repo.stations().map(s => s.id), ['st-a1', 'st-a2', 'st-a3']);
  assert.deepEqual(new DemoRepository(store, 'owner-b').stations().map(s => s.id), ['st-b1', 'st-b2']);
  assert.equal(new DemoRepository(store, 'admin').stations().length, 5);
  assert.deepEqual(new DemoRepository(store, 'employee').stations().map(s => s.id), ['st-a1']);
  assert.equal(new DemoRepository(store, 'observer').stations().length, 2);
  assert.throws(() => repo.station('st-b1'), /недоступна/);
  assert.throws(() => repo.report({ stationId: 'st-b1' }), /недоступна/);
  assert.throws(() => repo.report({ orgId: 'org-b' }), /недоступна/);
  assert.throws(() => repo.report({ portId: 'st-b1-p1' }), /недоступен/);
  assert.throws(() => repo.report({ groupId: 'osh' }), /недоступна/);
  assert.throws(() => repo.report({ locationId: 'loc-st-b1' }), /недоступна/);
});

test('employee cannot query finance, exports, access lists, or modify direct identifiers', () => {
  const { repo } = fixture('employee');
  assert.equal(repo.permission('finance'), false);
  assert.equal(repo.permission('hardware'), false);
  for (const operation of [() => repo.report(), () => repo.monthly(), () => repo.exportReport(), () => repo.members(), () => repo.saveStation({ id: 'st-a1', name: 'x' }), () => repo.updateTariff('st-a1', { value: '20', effectiveFrom: '2026-11-02' }), () => repo.saveMonthExpense({ stationId: 'st-a1', month: '2026-10', rent: '0' }), () => repo.saveFreeGrant({}), () => repo.inviteMember({})]) assert.throws(operation, /прав/);
  const station = repo.station('st-a1');
  assert.deepEqual(Object.keys(station.currentTariffs).sort(), ['customer', 'premium']);
  assert.equal(station.tariffs.some(t => t.kind === 'electricity'), false);
});

test('accountant can enter expenses but cannot change stations or tariffs', () => {
  const { repo } = fixture('accountant');
  assert.ok(repo.report().totals.sessions > 0);
  assert.throws(() => repo.saveStation({ id: 'st-a1', name: 'x' }), /прав/);
  assert.throws(() => repo.updateTariff('st-a1', { value: '12', effectiveFrom: '2026-11-02' }), /прав/);
  assert.throws(() => repo.hardwareCommand('start'), /отключены/);
  assert.equal(repo.saveMonthExpense({ stationId: 'st-a1', month: '2026-10', rent: '123.45' }).actualRent, '123.45');
});

test('observer has assigned reports and export, without mutations', () => {
  const { repo } = fixture('observer');
  assert.ok(repo.report().sessions.every(s => ['st-a1', 'st-a2'].includes(s.stationId)));
  assert.throws(() => repo.station('st-a3'));
  assert.throws(() => repo.monthly(), /прав/);
  assert.throws(() => repo.saveDiscount({}), /прав/);
  assert.ok(repo.exportReport().demo);
});

test('operational reports and audit history cannot bypass finance or access capabilities', () => {
  const { repo, store } = fixture();
  repo.saveStation({ id: 'st-a1', name: 'ДЕМО · изменено' });
  repo.updateTariff('st-a1', { kind: 'electricity', value: '6.25', effectiveFrom: '2026-11-02' });
  repo.inviteMember({ name: 'Секретный контакт', contact: 'private@example.invalid', role: 'observer', stationIds: ['st-a3'] });
  const observer = new DemoRepository(store, 'observer');
  const report = observer.exportReport({ from: '2026-10-01', to: '2026-10-31' });
  assert.equal(report.restrictedFinance, true);
  assert.deepEqual(report.expenses, []);
  assert.deepEqual(report.monthly, []);
  assert.equal(report.totals.estimatedElectricity, null);
  assert.equal(report.totals.actualProfit, null);
  assert.ok(report.sessions.every(s => s.platformFee === null && !('electricityRate' in s)));
  assert.equal(report.reconciliation.ledgerReceived, null);
  const events = observer.events();
  assert.equal(events.some(e => e.action === 'member.invited'), false);
  assert.equal(events.some(e => e.action === 'tariff.updated'), false);
  const stationChange = events.find(e => e.action === 'station.updated');
  assert.ok(stationChange);
  assert.ok(stationChange.after.tariffs.every(t => ['customer', 'premium'].includes(t.kind)));
  assert.equal(new DemoRepository(store, 'accountant').events().some(e => e.action === 'member.invited'), false);
});

test('foreign direct mutation identifiers are rejected across every entity', () => {
  const { repo } = fixture();
  assert.throws(() => repo.saveStation({ id: 'st-b1', name: 'x' }), /недоступна/);
  assert.throws(() => repo.archiveStation('st-b1'), /недоступна/);
  assert.throws(() => repo.updateTariff('st-b1', { value: '22' }), /недоступна/);
  assert.throws(() => repo.saveMonthExpense({ stationId: 'st-b1', month: '2026-10', rent: '10' }), /недоступна/);
  assert.throws(() => repo.saveBrand({ id: 'brand-b', name: 'x' }), /недоступна/);
  assert.throws(() => repo.inviteMember({ name: 'x', contact: 'x@example.invalid', role: 'observer', stationIds: ['st-b1'] }), /недоступна/);
  assert.throws(() => repo.saveDiscount({ name: 'x', value: '5', stationIds: ['st-b1'] }), /недоступна/);
  assert.throws(() => repo.saveStation({ id: 'st-a1', orgId: 'org-b' }), /переносить/);
  assert.throws(() => repo.saveStation({ id: 'st-a1', brandId: 'brand-b' }), /недоступен/);
});

test('Bishkek includes final second and excludes next-day boundary', () => {
  const { store, repo } = fixture();
  const base = store.sessions.find(s => s.stationId === 'st-a1');
  store.sessions.push({ ...base, id: 'edge-first', startedAt: '2026-10-30T18:00:00.000Z' }, { ...base, id: 'edge-last', startedAt: '2026-10-31T17:59:59.999Z' }, { ...base, id: 'edge-next', startedAt: '2026-10-31T18:00:00.000Z' });
  const rows = repo.report({ from: '2026-10-31', to: '2026-10-31', stationId: 'st-a1' }).sessions;
  assert.ok(rows.some(s => s.id === 'edge-first'));
  assert.ok(rows.some(s => s.id === 'edge-last'));
  assert.equal(rows.some(s => s.id === 'edge-next'), false);
  assert.equal(bishkekDate('2026-10-31T18:00:00Z'), '2026-11-01');
  assert.equal(dateRange('2026-10-31', '2026-10-31').endExclusive, '2026-10-31T18:00:00.000Z');
  assert.throws(() => repo.report({ from: '2026-02-30', to: '2026-03-01' }), /дата/);
  assert.throws(() => repo.report({ from: '2026-11-02', to: '2026-11-01' }), /позже/);
});

test('tariff versions retain historical sessions and show effective dates', () => {
  const { repo } = fixture();
  const before = repo.report({ from: '2026-10-01', to: '2026-10-31', stationId: 'st-a1' });
  repo.updateTariff('st-a1', { kind: 'customer', value: '17.75', effectiveFrom: '2026-11-01' });
  const station = repo.station('st-a1'), versions = station.tariffs.filter(t => t.kind === 'customer');
  assert.equal(station.tariff, '17.75');
  assert.equal(versions[0].effectiveTo, '2026-10-31');
  assert.equal(versions[1].actor, 'owner-a');
  const after = repo.report({ from: '2026-10-01', to: '2026-10-31', stationId: 'st-a1' });
  assert.deepEqual(after.sessions, before.sessions);
  assert.equal(after.totals.charged, before.totals.charged);
  assert.ok(repo.events().some(e => e.action === 'tariff.updated' && e.before && e.after));
  assert.throws(() => repo.updateTariff('st-a1', { value: '-1', effectiveFrom: '2026-11-02' }), /отрицательной/);
  assert.throws(() => repo.updateTariff('st-a1', { kind: 'tax', value: '101', effectiveFrom: '2026-11-02' }), /100/);
});

test('electricity, commission and tax versions remain independent', () => {
  const { repo } = fixture();
  const before = repo.report({ from: '2026-10-01', to: '2026-10-31', stationId: 'st-a1' }).totals;
  for (const [kind, value] of [['electricity', '7.50'], ['platform', '7.00'], ['tax', '6.00']]) repo.updateTariff('st-a1', { kind, value, effectiveFrom: '2026-11-01' });
  const after = repo.report({ from: '2026-10-01', to: '2026-10-31', stationId: 'st-a1' }).totals;
  for (const field of ['estimatedElectricity', 'platformFee', 'tax', 'charged']) assert.equal(after[field], before[field]);
  assert.equal(repo.station('st-a1').currentTariffs.customer, '13.50');
});

test('actual bill retains estimate, metadata, paid status and audit actor', () => {
  const { repo, store } = fixture();
  const before = repo.monthly({ from: '2026-10-01', to: '2026-10-31', stationId: 'st-a3' })[0];
  assert.equal(before.actualElectricity, null);
  assert.equal(before.actualProfit, null);
  const row = repo.saveMonthExpense({ stationId: 'st-a3', month: '2026-10', bill: { amount: '7100.25', date: '2026-10-31', number: 'TEST-123', paid: true, attachment: { name: 'receipt.pdf', size: 1200, type: 'application/pdf' }, meterEnergy: '1300.500' }, rent: '15000', other: '725.20', payout: '1000.00', comment: 'Сверено' });
  assert.equal(row.estimatedElectricity, before.estimatedElectricity);
  assert.equal(row.actualElectricity, '7100.25');
  assert.equal(row.bill.amount, '7100.25');
  assert.equal(row.bill.paid, true);
  assert.equal(row.bill.attachment.name, 'receipt.pdf');
  assert.equal(row.electricityDifference, sub('7100.25', before.estimatedElectricity));
  assert.equal(row.actor, 'owner-a');
  assert.ok(row.updatedAt);
  assert.ok(store.audit.some(a => a.action === 'finance.month.updated' && a.actor === 'owner-a'));
  const saved = new DemoRepository(JSON.parse(JSON.stringify(store))).monthly({ from: '2026-10-01', to: '2026-10-31', stationId: 'st-a3' })[0];
  assert.equal(saved.bill.number, 'TEST-123');
});

test('partial period does not apply a complete monthly bill to a few days', () => {
  const { repo } = fixture();
  const rows = repo.monthly({ from: '2026-10-25', to: '2026-10-31', stationId: 'st-a1' });
  assert.equal(rows[0].actualElectricity, null);
  assert.ok(rows[0].bill);
  assert.equal(rows[0].partialPeriod, true);
  assert.equal(rows[0].estimatedRent, '3387.10');
  assert.equal(rows[0].payout, null);
  assert.equal(rows[0].debt, null);
  const subset = repo.monthly({ from: '2026-10-01', to: '2026-10-31', stationId: 'st-a1', sessionStatus: 'completed' })[0];
  assert.equal(subset.actualElectricity, null);
  assert.equal(subset.rent, null);
  assert.equal(subset.estimatedProfit, null);
});

test('rent uses daily versions for future estimates and snapshots for historical reports', () => {
  const { repo } = fixture();
  const historical = repo.monthly({ from: '2026-10-01', to: '2026-10-31', stationId: 'st-a1' })[0].estimatedRent;
  repo.updateTariff('st-a1', { kind: 'rent', value: '30000', effectiveFrom: '2026-10-15' });
  assert.equal(repo.monthly({ from: '2026-10-01', to: '2026-10-31', stationId: 'st-a1' })[0].estimatedRent, historical);
  repo.updateTariff('st-a1', { kind: 'rent', value: '60000', effectiveFrom: '2026-11-16' });
  // Nov 1 retains the completed session snapshot 15000; Nov 2–15: 30000; Nov 16–30: 60000.
  const current = repo.monthly({ from: '2026-11-01', to: '2026-11-30', stationId: 'st-a1' })[0];
  assert.equal(current.estimatedRent, '44500.00');
});

test('profit has explicit selected deductions and excludes payout transfers by default', () => {
  const { repo } = fixture();
  const filters = { from: '2026-10-01', to: '2026-10-31', stationId: 'st-a1' };
  const totals = repo.report(filters).totals;
  let expected = totals.revenue;
  for (const value of [totals.estimatedElectricity, totals.platformFee, totals.acquiringFee, totals.tax, totals.rent, totals.other]) expected = sub(expected, value);
  assert.equal(totals.estimatedProfit, expected);
  const withTransfer = repo.report({ ...filters, deductions: [...totals.deductions, 'payout'] }).totals;
  assert.equal(withTransfer.estimatedProfit, sub(expected, totals.payout));
  assert.equal(repo.report({ ...filters, deductions: [] }).totals.estimatedProfit, totals.revenue);
  assert.throws(() => repo.report({ ...filters, deductions: ['whatever'] }), /Неизвестная/);
});

test('missing payment and actual bill are unknown, never a fabricated zero', () => {
  const { store, repo } = fixture();
  store.sessions.find(s => s.stationId === 'st-a1' && bishkekDate(s.startedAt) === '2026-11-01').paid = null;
  const report = repo.report({ from: '2026-11-01', to: '2026-11-01', stationId: 'st-a1' });
  for (const field of ['paid', 'revenue', 'unpaid', 'actualElectricity', 'actualProfit', 'estimatedProfit', 'margin']) assert.equal(report.totals[field], null, field);
  assert.equal(report.reconciliation.ledgerReceived, null);
  assert.equal(report.reconciliation.difference, null);
});

test('an unavailable payment ledger is unknown; an existing mismatched ledger is reconciled', () => {
  const { repo, store } = fixture();
  store.payments = null;
  const report = repo.report();
  assert.equal(report.reconciliation.ledgerReceived, null);
  assert.equal(report.reconciliation.difference, null);
  assert.deepEqual(report.payments, []);
  assert.ok(report.totals.paid !== null); // Session payment snapshot is a separate, known source.
  assert.equal(repo.report({ from: '2026-10-01', to: '2026-10-31', stationId: 'st-a1' }).totals.actualProfit, null);
  assert.equal(repo.monthly({ from: '2026-10-01', to: '2026-10-31', stationId: 'st-a1' })[0].actualProfit, null);
});

test('report and export share all filters, totals, rows and org scope', () => {
  const { repo } = fixture();
  const filters = { from: '2026-10-01', to: '2026-10-31', stationId: 'st-a1', portId: 'st-a1-p1', paymentStatus: 'paid', sessionStatus: 'completed', operation: 'charging' };
  const r = repo.report(filters), exported = repo.exportReport(filters);
  assert.deepEqual(exported.totals, r.totals);
  assert.deepEqual(exported.sessions, r.sessions);
  assert.ok(exported.sessions.every(s => s.orgId === 'org-a' && s.portId === 'st-a1-p1' && s.paymentStatus === 'paid' && s.status === 'completed'));
  assert.ok(exported.payments.every(p => exported.sessions.some(s => s.id === p.sessionId)));
  assert.ok(repo.events().some(e => e.action === 'report.exported'));
});

test('apparatus count, connectors, freshness and active sessions are separate and honest', () => {
  const { repo } = fixture();
  const station = repo.station('st-a1');
  assert.equal(station.hardwareCount, 1);
  assert.equal(station.ports.length, 2);
  assert.equal(station.nominalPower, '80.00');
  const offline = repo.station('st-a3');
  assert.equal(offline.stale, true);
  assert.equal(offline.status, 'offline');
  assert.equal(offline.powerKw, null);
  assert.ok(offline.ports.every(p => p.status === 'unavailable' && p.powerKw === null));
  assert.ok(repo.activeSessions().every(s => s.stationId === 'st-a1' && s.demo));
});

test('revocation changes a pre-existing repository immediately', () => {
  const { store, repo } = fixture();
  const employee = new DemoRepository(store, 'employee');
  assert.equal(employee.stations().length, 1);
  repo.revokeMember('member-employee');
  assert.equal(employee.permission('view'), false);
  assert.throws(() => employee.stations(), /прав/);
  assert.throws(() => employee.station('st-a1'), /прав/);
});

test('invitations and free grants enforce scoped stations, expiry, reason and audit', () => {
  const { repo, store } = fixture();
  const member = repo.inviteMember({ name: 'Тест', contact: 'test@example.invalid', role: 'observer', stationIds: ['st-a1'], startsAt: '2026-11-01', endsAt: '2026-12-01' });
  assert.equal(member.status, 'invited');
  assert.match(member.delivery, /не отправляется/);
  assert.throws(() => repo.inviteMember({ name: 'Тест', contact: 'test@example.invalid', role: 'observer', stationIds: ['st-a1'], permissions: ['tariff.edit'] }), /превышают/);
  assert.throws(() => repo.saveFreeGrant({ stationIds: ['st-a1'], reason: '' }), /причину/);
  const free = repo.saveFreeGrant({ stationIds: ['st-a1'], reason: 'Техническая проверка · ДЕМО', startsAt: '2026-11-01', endsAt: '2026-11-02', usageLimit: 1 });
  assert.equal(free.value, '100.00');
  assert.equal(free.approvedBy, 'owner-a');
  assert.ok(store.audit.some(e => e.action === 'free.granted'));
});

test('station edits and archive validate and are logged, while ports remain distinct', () => {
  const { repo } = fixture();
  assert.throws(() => repo.saveStation({ id: 'st-a1', nominalPower: '0' }), /Мощность/);
  assert.throws(() => repo.saveStation({ id: 'st-a1', reservationMinutes: '-1' }), /целое/);
  const updated = repo.saveStation({ id: 'st-a1', name: 'Новое имя', idleTariff: '2.50', reservationFee: '20', stopAtPercent: 95 });
  assert.equal(updated.name, 'Новое имя');
  assert.equal(updated.idleTariff, '2.50');
  assert.equal(updated.ports.length, 2);
  const created = repo.saveStation({ name: 'ДЕМО · новая точка', city: 'Бишкек', address: 'Тест, 1', nominalPower: '80', hardwareCount: 1, ports: [{ number: 1, type: 'GB/T DC' }, { number: 2, type: 'GB/T DC' }] });
  assert.ok(repo.station(created.id));
  assert.equal(created.ports.length, 2);
  repo.archiveStation(created.id);
  assert.equal(repo.stations().some(s => s.id === created.id), false);
  assert.ok(repo.stations({ includeArchived: true }).some(s => s.id === created.id));
});
