/** QUBAT EV stage 1. Local DEMO data only; this repository is not an authentication server. */
const SCALE = 1000000n;
const DAY = 86400000;
const clone = value => JSON.parse(JSON.stringify(value));
const fail = message => { throw new Error(message); };
const has = (value, key) => Object.prototype.hasOwnProperty.call(value, key);
const sum = (values, places = 2) => values.reduce((total, value) => add(total, value, places), decimal('0', places));
function raw(value) {
  if (typeof value === 'bigint') return value * SCALE;
  if (typeof value === 'number' && !Number.isSafeInteger(value)) fail('Используйте точную десятичную строку');
  const text = String(value ?? '').trim();
  if (!/^-?\d+(?:\.\d{1,6})?$/.test(text)) fail('Некорректное десятичное значение');
  const negative = text.startsWith('-');
  const [whole, fraction = ''] = text.replace(/^-/, '').split('.');
  const result = BigInt(whole) * SCALE + BigInt(fraction.padEnd(6, '0'));
  return negative ? -result : result;
}
function rounded(numerator, denominator) {
  if (denominator === 0n) fail('Деление на ноль');
  const negative = (numerator < 0n) !== (denominator < 0n);
  const n = numerator < 0n ? -numerator : numerator;
  const d = denominator < 0n ? -denominator : denominator;
  const value = (n + d / 2n) / d;
  return negative ? -value : value;
}
function formatScaled(value, places) {
  if (!Number.isInteger(places) || places < 0 || places > 6) fail('Точность: от 0 до 6 знаков');
  const unit = 10n ** BigInt(places);
  const negative = value < 0n;
  const abs = negative ? -value : value;
  return `${negative ? '-' : ''}${abs / unit}${places ? `.${String(abs % unit).padStart(places, '0')}` : ''}`;
}
export function decimal(value, places = 2) { return formatScaled(rounded(raw(value), 10n ** BigInt(6 - places)), places); }
export const money = value => decimal(value, 2);
export const energy = value => decimal(value, 3);
export function add(a, b, places = 2) { return formatScaled(rounded(raw(a) + raw(b), 10n ** BigInt(6 - places)), places); }
export function sub(a, b, places = 2) { return formatScaled(rounded(raw(a) - raw(b), 10n ** BigInt(6 - places)), places); }
export function mul(a, b, places = 2) { return formatScaled(rounded(raw(a) * raw(b), 10n ** BigInt(12 - places)), places); }
export function div(a, b, places = 2) { return formatScaled(rounded(raw(a) * 10n ** BigInt(places), raw(b)), places); }
export function percent(amount, rate, places = 2) { return formatScaled(rounded(raw(amount) * raw(rate), 100n * 10n ** BigInt(12 - places)), places); }
const positiveMoney = value => { const result = money(value); if (raw(result) < 0n) fail('Сумма не может быть отрицательной'); return result; };
const positiveEnergy = value => { const result = energy(value); if (raw(result) < 0n) fail('Значение не может быть отрицательным'); return result; };
const integer = (value, min = 0, max = 1000000) => { const n = Number(value); if (!Number.isSafeInteger(n) || n < min || n > max) fail(`Требуется целое число от ${min} до ${max}`); return n; };
const text = (value, max = 200) => String(value ?? '').trim().slice(0, max);
function dateOnly(value) {
  const result = String(value ?? '');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(result) || !Number.isFinite(Date.parse(`${result}T00:00:00Z`)) || new Date(`${result}T00:00:00Z`).toISOString().slice(0, 10) !== result) fail('Некорректная дата');
  return result;
}
export function bishkekDate(iso) { return new Date(Date.parse(iso) + 6 * 3600000).toISOString().slice(0, 10); }
export function dateRange(from, to) {
  const start = dateOnly(from), end = dateOnly(to);
  if (start > end) fail('Начальная дата позже конечной');
  return { from: start, to: end, start: `${start}T00:00:00+06:00`, endExclusive: new Date(Date.parse(`${end}T00:00:00+06:00`) + DAY).toISOString(), timezone: 'Asia/Bishkek' };
}
const addDays = (date, offset) => new Date(Date.parse(`${date}T00:00:00Z`) + offset * DAY).toISOString().slice(0, 10);
const monthBounds = month => {
  if (!/^\d{4}-\d{2}$/.test(String(month))) fail('Некорректный месяц');
  const from = dateOnly(`${month}-01`);
  const next = new Date(`${from}T00:00:00Z`); next.setUTCMonth(next.getUTCMonth() + 1);
  return { from, to: new Date(next.getTime() - DAY).toISOString().slice(0, 10) };
};
const LABELS = { admin: 'Суперадминистратор QUBAT', owner: 'Владелец сети', employee: 'Сотрудник партнёра', accountant: 'Бухгалтер', observer: 'Наблюдатель' };
const FULL = ['view', 'reports', 'export', 'finance', 'stations.edit', 'tariff.edit', 'members.manage', 'discounts.manage', 'free.grant', 'brands.edit', 'notifications.edit'];
const PERMISSIONS = { admin: FULL, owner: FULL, employee: ['view', 'notifications.edit'], accountant: ['view', 'reports', 'export', 'finance', 'notifications.edit'], observer: ['view', 'reports', 'export'] };
const ALIASES = { editStations: 'stations.edit', editTariff: 'tariff.edit', manageMembers: 'members.manage', manageDiscounts: 'discounts.manage', freeCharge: 'free.grant', editBrands: 'brands.edit', reportsView: 'reports', financeView: 'finance', hardware: 'hardware' };
const TARIFF_KINDS = ['customer', 'premium', 'electricity', 'platform', 'acquiring', 'tax', 'partner', 'reserve', 'rent'];
const DEFAULT_DEDUCTIONS = ['electricity', 'platformFee', 'acquiringFee', 'tax', 'rent', 'other'];
function effectiveRate(station, kind, date) {
  const version = station.tariffs.filter(v => v.kind === kind && v.effectiveFrom <= date && (!v.effectiveTo || date <= v.effectiveTo)).sort((a, b) => b.effectiveFrom.localeCompare(a.effectiveFrom))[0];
  return version?.value ?? null;
}
function account(id, name, role, orgId, stationIds = []) {
  return { id, name, role, roleLabel: LABELS[role], orgId, orgName: orgId === 'org-a' ? 'ОсОО «Qubat Бишкек»' : orgId === 'org-b' ? 'ОсОО «Түштүк Энерго»' : 'QUBAT EV', stationIds, permissions: [...PERMISSIONS[role]], requires2FA: ['admin', 'owner', 'accountant'].includes(role), twoFactorEnabled: true, demo: true };
}
/** JSON-safe fixtures. Monetary/energy computations below use only fixed point integers. */
export function createDemoStore(today = '2026-11-01') {
  dateOnly(today);
  const now = `${today}T12:00:00.000Z`;
  const stations = [
    ['st-a1', 'org-a', 'Асанбай · QUBAT', 'Бишкек', 'ул. Токомбаева, 21/2', 'bishkek-south', 'Юг Бишкека', '80.00', 'busy', '42.817600', '74.624900'],
    ['st-a2', 'org-a', 'Asia Mall · QUBAT', 'Бишкек', 'пр. Ч. Айтматова, 3', 'bishkek-center', 'Центр Бишкека', '120.00', 'online', '42.855800', '74.583900'],
    ['st-a3', 'org-a', 'Кара-Балта · QUBAT', 'Кара-Балта', 'ул. Кожомбердиева, 46', 'chui', 'Чуйская область', '60.00', 'offline', '42.814200', '73.848600'],
    ['st-b1', 'org-b', 'Ош · Түштүк', 'Ош', 'ул. Курманжан Датка, 270', 'osh', 'Ошская область', '80.00', 'busy', '40.528300', '72.798500'],
    ['st-b2', 'org-b', 'Жалал-Абад · Түштүк', 'Жалал-Абад', 'ул. Ленина, 12', 'jalalabad', 'Жалал-Абад', '60.00', 'error', '40.933200', '73.002200'],
  ].map((row, index) => {
    const [id, orgId, name, city, address, groupId, groupName, nominalPower, status, latitude, longitude] = row;
    const lastSeen = new Date(Date.parse(now) - (status === 'offline' ? 3 * 3600000 : 90000)).toISOString();
    const station = { id, orgId, ownerId: orgId === 'org-a' ? 'owner-a' : 'owner-b', ownerName: orgId === 'org-a' ? 'ОсОО «Qubat Бишкек»' : 'ОсОО «Түштүк Энерго»', name, city, address, groupId, groupName, locationId: `loc-${id}`, locationName: name.split(' · ')[0], brandId: orgId === 'org-a' ? 'brand-a' : 'brand-b', brandName: orgId === 'org-a' ? 'QUBAT EV' : 'Түштүк EV', model: `QBT DC ${nominalPower.split('.')[0]}`, serialNumber: `DEMO-QBT-${1001 + index}`, ocppId: `DEMO-${id.toUpperCase()}`, externalId: `DEMO-EXT-${index + 1}`, firmware: 'demo-1.0.0', latitude, longitude, operatingHours: 'Круглосуточно', hardwareCount: 1, nominalPower, powerKw: status === 'busy' ? '48.500' : '0.000', status, lastSeen, source: 'ДЕМО · локальный набор', offlineReason: status === 'offline' ? 'Нет связи; последнее состояние устарело' : status === 'error' ? 'ДЕМО: ошибка температурного датчика' : '', archived: false, idleTariff: '0.00', reservationMinutes: 15, reservationFee: '0.00', stopAtPercent: 100, createdAt: '2026-01-01T00:00:00.000Z', updatedAt: now, demo: true, tariffs: [] };
    station.ports = [1, 2].map(number => ({ id: `${id}-p${number}`, number, type: number === 2 && index === 1 ? 'CCS2' : 'GB/T DC', maxPower: nominalPower, maxCurrent: '250.00', status: status === 'offline' ? 'unavailable' : status === 'error' && number === 1 ? 'fault' : status === 'busy' && number === 1 ? 'busy' : 'available', lastSeen, powerKw: status === 'busy' && number === 1 ? '48.500' : '0.000' }));
    const initial = { customer: '13.50', premium: '12.50', electricity: '4.20', platform: '3.00', acquiring: '1.50', tax: '2.00', partner: '10.00', reserve: '0.50', rent: index === 1 ? '22000.00' : '15000.00' };
    for (const [kind, value] of Object.entries(initial)) station.tariffs.push({ id: `${id}-${kind}-v1`, kind, value, base: ['platform', 'acquiring', 'tax', 'partner', 'reserve'].includes(kind) ? 'paid-less-refunds' : kind === 'rent' ? 'calendar-month' : 'kWh', effectiveFrom: '2026-01-01', effectiveTo: null, actor: 'admin', createdAt: '2026-01-01T00:00:00.000Z' });
    return station;
  });
  const accounts = [account('admin', 'QUBAT · администратор', 'admin', null), account('owner-a', 'Айбек · владелец Бишкек', 'owner', 'org-a', ['st-a1', 'st-a2', 'st-a3']), account('owner-b', 'Алина · владелец Ош', 'owner', 'org-b', ['st-b1', 'st-b2']), account('employee', 'Нурбек · сотрудник', 'employee', 'org-a', ['st-a1']), account('accountant', 'Асел · бухгалтер', 'accountant', 'org-a', ['st-a1', 'st-a2', 'st-a3']), account('observer', 'Данияр · наблюдатель', 'observer', 'org-a', ['st-a1', 'st-a2'])];
  const sessions = [], payments = [], monthLedger = [];
  const fixtureDays = Array.from({ length: 93 }, (_, i) => addDays(today, -i));
  // Extra older fixtures make year and monthly comparisons meaningful without a fake ledger aggregate.
  for (let m = 1; m <= 6; m++) for (const day of [5, 15, 25]) fixtureDays.push(`2026-${String(m).padStart(2, '0')}-${String(day).padStart(2, '0')}`);
  for (const [stationIndex, station] of stations.entries()) for (const [dayIndex, day] of [...new Set(fixtureDays)].entries()) {
    if (day > today) continue;
    for (let n = 0; n < 3; n++) {
      const sequence = stationIndex * 1000 + dayIndex * 3 + n;
      const cancelled = sequence % 29 === 0, error = !cancelled && sequence % 47 === 0;
      const status = cancelled ? 'cancelled' : error ? 'error' : 'completed';
      const amountEnergy = cancelled || error ? '0.000' : formatScaled(BigInt(15000 + sequence % 45000), 3);
      const tariff = effectiveRate(station, 'customer', day);
      const charged = mul(amountEnergy, tariff);
      const pending = !cancelled && !error && sequence % 31 === 0;
      const refund = !cancelled && !error && !pending && sequence % 43 === 0 ? money('25') : '0.00';
      const paid = pending ? '0.00' : charged;
      const revenue = sub(paid, refund);
      const startedAt = new Date(`${day}T${['07', '11', '15'][n]}:${String(sequence % 60).padStart(2, '0')}:00+06:00`).toISOString();
      const durationMinutes = cancelled || error ? 2 : 25 + sequence % 65;
      const id = `ses-${station.id}-${day}-${n + 1}`;
      const row = { id, orgId: station.orgId, ownerId: station.ownerId, stationId: station.id, stationName: station.name, groupId: station.groupId, groupName: station.groupName, locationId: station.locationId, locationName: station.locationName, portId: station.ports[n % 2].id, portNumber: n % 2 + 1, customerId: `client-${1 + sequence % 47}`, customerName: `Клиент ДЕМО ${1 + sequence % 47}`, startedAt, endedAt: new Date(Date.parse(startedAt) + durationMinutes * 60000).toISOString(), durationMinutes, energy: amountEnergy, tariff, tariffVersionId: `${station.id}-customer-v1`, charged, paid, refund, method: sequence % 2 ? 'Элкарт · ДЕМО' : 'Баланс · ДЕМО', paymentStatus: cancelled || error ? 'cancelled' : pending ? 'pending' : raw(refund) > 0n ? 'refunded' : 'paid', status, error: cancelled ? 'Отменено клиентом · ДЕМО' : error ? 'Нет ответа от разъёма · ДЕМО' : '', operation: 'charging', platformFee: percent(revenue, effectiveRate(station, 'platform', day)), acquiringFee: percent(revenue, effectiveRate(station, 'acquiring', day)), tax: percent(revenue, effectiveRate(station, 'tax', day)), estimatedElectricity: mul(amountEnergy, effectiveRate(station, 'electricity', day)), partnerShare: percent(revenue, effectiveRate(station, 'partner', day)), reserve: percent(revenue, effectiveRate(station, 'reserve', day)), electricityRate: effectiveRate(station, 'electricity', day), rentRate: effectiveRate(station, 'rent', day), taxRate: effectiveRate(station, 'tax', day), platformRate: effectiveRate(station, 'platform', day), acquiringRate: effectiveRate(station, 'acquiring', day), source: 'ДЕМО · локальный набор', demo: true };
      sessions.push(row);
      if (raw(paid) > 0n) payments.push({ id: `pay-${id}`, sessionId: id, orgId: station.orgId, stationId: station.id, stationName: station.name, date: row.endedAt, kind: 'payment', amount: paid, method: row.method, status: 'received', platformFee: row.platformFee, acquiringFee: row.acquiringFee, demo: true });
      if (raw(refund) > 0n) payments.push({ id: `ref-${id}`, sessionId: id, orgId: station.orgId, stationId: station.id, stationName: station.name, date: row.endedAt, kind: 'refund', amount: refund, method: row.method, status: 'refunded', platformFee: '0.00', acquiringFee: '0.00', demo: true });
    }
  }
  const previousMonth = addDays(`${today.slice(0, 7)}-01`, -1).slice(0, 7);
  for (const station of stations) for (const month of [previousMonth, today.slice(0, 7)]) {
    const sourceSessions = sessions.filter(s => s.stationId === station.id && bishkekDate(s.startedAt).startsWith(month));
    const estimate = sum(sourceSessions.map(s => s.estimatedElectricity));
    const previous = month === previousMonth;
    monthLedger.push({ id: `month-${station.id}-${month}`, orgId: station.orgId, stationId: station.id, month, bill: previous && station.id !== 'st-a3' ? { amount: add(estimate, '275.50'), number: `ДЕМО-ЭЭ-${month}-${station.id}`, date: `${month}-28`, periodFrom: `${month}-01`, periodTo: monthBounds(month).to, paid: station.id !== 'st-b2', attachment: { name: 'Квитанция-ДЕМО.pdf', size: 0, type: 'application/pdf', demo: true }, meterEnergy: add(sum(sourceSessions.map(s => s.energy), 3), '6.500', 3) } : null, rent: previous ? effectiveRate(station, 'rent', `${month}-01`) : null, other: previous ? '1200.00' : null, payout: previous ? '5000.00' : null, comment: previous ? 'ДЕМО: пример месячного учёта' : '', updatedAt: now, actor: 'owner-a', demo: true });
  }
  return { version: 2, demo: true, today, now, currency: 'KGS', timezone: 'Asia/Bishkek', accounts, stations, sessions, payments, monthLedger, brands: [{ id: 'brand-a', orgId: 'org-a', name: 'QUBAT EV', color: '#10B981', description: 'ДЕМО · сеть Бишкек' }, { id: 'brand-b', orgId: 'org-b', name: 'Түштүк EV', color: '#3B82F6', description: 'ДЕМО · южная сеть' }], members: [{ id: 'member-employee', accountId: 'employee', orgId: 'org-a', name: 'Нурбек · сотрудник', contact: 'employee@example.invalid', role: 'employee', stationIds: ['st-a1'], startsAt: '2026-01-01', endsAt: null, status: 'active', permissions: [...PERMISSIONS.employee] }, { id: 'member-accountant', accountId: 'accountant', orgId: 'org-a', name: 'Асел · бухгалтер', contact: 'accountant@example.invalid', role: 'accountant', stationIds: ['st-a1', 'st-a2', 'st-a3'], startsAt: '2026-01-01', endsAt: null, status: 'active', permissions: [...PERMISSIONS.accountant] }, { id: 'member-observer', accountId: 'observer', orgId: 'org-a', name: 'Данияр · наблюдатель', contact: 'observer@example.invalid', role: 'observer', stationIds: ['st-a1', 'st-a2'], startsAt: '2026-01-01', endsAt: null, status: 'active', permissions: [...PERMISSIONS.observer] }], discounts: [{ id: 'discount-a1', orgId: 'org-a', name: 'ДЕМО · резиденты Асанбая', type: 'discount', kind: 'percent', value: '5.00', stationIds: ['st-a1'], startsAt: '2026-01-01', endsAt: '2026-12-31', usageLimit: 100, used: 7, approvedBy: 'owner-a', createdAt: now }], notificationSettings: { 'owner-a': { email: true, push: true, offlineMinutes: 15, lowPowerKw: '5.000', longSessionMinutes: 120 }, 'owner-b': { email: true, push: true, offlineMinutes: 15, lowPowerKw: '5.000', longSessionMinutes: 120 } }, events: stations.filter(s => ['offline', 'error'].includes(s.status)).map((s, i) => ({ id: `event-${i}`, orgId: s.orgId, stationId: s.id, stationName: s.name, type: s.status === 'offline' ? 'offline' : 'fault', severity: 'warning', title: s.status === 'offline' ? 'ДЕМО: потеря связи' : 'ДЕМО: ошибка разъёма', message: s.offlineReason, actor: 'ДЕМО · оборудование', at: s.lastSeen, source: s.source, demo: true })), audit: [], sequence: 100 };
}

export class DemoRepository {
  constructor(store, accountId = 'owner-a') {
    if (!store || store.version !== 2 || !store.demo) fail('Неподдерживаемый набор ДЕМО');
    this.store = store; this.accountId = accountId;
    if (!store.accounts.some(a => a.id === accountId)) fail('Учётная запись не найдена');
  }
  get account() { return clone(this._account()); }
  accounts() { return this.store.accounts.map(({ id, name, role, roleLabel, orgId, orgName, demo }) => ({ id, name, role, roleLabel, orgId, orgName, demo })); }
  _account() {
    const account = this.store.accounts.find(a => a.id === this.accountId);
    if (!account) fail('Учётная запись не найдена');
    const member = this.store.members.find(m => m.accountId === account.id);
    if (member && (member.status === 'revoked' || member.startsAt > this.store.today || member.endsAt && member.endsAt < this.store.today)) return { ...account, stationIds: [], permissions: [], revoked: true };
    return member ? { ...account, stationIds: member.stationIds, permissions: member.permissions || PERMISSIONS[member.role] || [], role: member.role, roleLabel: LABELS[member.role] } : account;
  }
  permission(name) { const key = ALIASES[name] || name; return key !== 'hardware' && this._account().permissions.includes(key); }
  _require(name) { if (!this.permission(name)) fail('Недостаточно прав: доступ запрещён'); }
  _scope(station) { const a = this._account(); return a.role === 'admin' || station.orgId === a.orgId && a.stationIds.includes(station.id); }
  _rawStation(id, archived = true) { const s = this.store.stations.find(s => s.id === id && (archived || !s.archived)); if (!s || !this._scope(s)) fail('Точка не найдена или недоступна'); return s; }
  _org(id) { const a = this._account(); if (a.role !== 'admin' && id && id !== a.orgId) fail('Организация недоступна'); return id || a.orgId || 'org-a'; }
  _id(prefix) { this.store.sequence += 1; return `${prefix}-${this.store.sequence}`; }
  _audit(action, stationId, before, after, orgId) {
    const station = stationId ? this._rawStation(stationId) : null;
    const entry = { id: this._id('audit'), action, type: 'audit', orgId: orgId || station?.orgId || this._account().orgId, stationId: stationId || null, stationName: station?.name || null, title: action, message: `ДЕМО · ${action}`, actor: this.accountId, actorName: this._account().name, at: new Date().toISOString(), before: clone(before ?? null), after: clone(after ?? null), source: 'ДЕМО · локальный журнал', demo: true };
    this.store.audit.push(entry); return entry;
  }
  _presentStation(station) {
    const result = clone(station);
    const fullTariffs = result.tariffs;
    if (!this.permission('finance')) result.tariffs = result.tariffs.filter(t => ['customer', 'premium'].includes(t.kind));
    result.currentTariffs = Object.fromEntries((this.permission('finance') ? TARIFF_KINDS : ['customer', 'premium']).map(kind => [kind, effectiveRate({ tariffs: fullTariffs }, kind, this.store.today)]));
    result.tariff = result.currentTariffs.customer;
    result.stale = Date.parse(this.store.now) - Date.parse(result.lastSeen) > 15 * 60000;
    if (result.stale) { result.status = 'offline'; result.powerKw = null; result.ports.forEach(p => { p.status = 'unavailable'; p.powerKw = null; }); }
    return result;
  }
  stations(filters = {}) {
    this._require('view');
    const normalized = this._filters(filters, false);
    return this.store.stations.filter(s => this._scope(s) && (!s.archived || filters.includeArchived) && this._matchesStation(s, normalized)).map(s => this._presentStation(s));
  }
  station(id) { this._require('view'); return this._presentStation(this._rawStation(id)); }
  _filters(filters = {}, dated = true) {
    const f = { ...filters };
    for (const name of ['station', 'group', 'location', 'port', 'customer', 'organization', 'owner']) if (f[name] && !f[`${name}Id`]) f[`${name}Id`] = f[name];
    if (f.organizationId && !f.orgId) f.orgId = f.organizationId;
    if (f.orgId) this._org(f.orgId);
    if (f.ownerId) { const owner = this.store.accounts.find(a => a.id === f.ownerId && a.role === 'owner'); if (!owner) fail('Владелец не найден'); this._org(owner.orgId); }
    if (f.stationId) this._rawStation(f.stationId);
    if (f.portId) { const station = this.store.stations.find(s => s.ports.some(p => p.id === f.portId)); if (!station || !this._scope(station)) fail('Разъём недоступен'); }
    for (const key of ['groupId', 'locationId']) if (f[key]) { const matches = this.store.stations.filter(s => s[key] === f[key]); if (!matches.length || !matches.some(s => this._scope(s))) fail('Группа или локация недоступна'); }
    if (dated) { const range = dateRange(f.from || addDays(this.store.today, -6), f.to || this.store.today); Object.assign(f, range); }
    f.timezone = 'Asia/Bishkek'; f.demo = true;
    return f;
  }
  _matchesStation(station, f) {
    for (const key of ['stationId', 'orgId', 'groupId', 'locationId', 'ownerId']) if (f[key] && (key === 'stationId' ? station.id : station[key]) !== f[key]) return false;
    if (f.portId && !station.ports.some(p => p.id === f.portId)) return false;
    if (f.status && station.status !== f.status) return false;
    if (f.search && ![station.name, station.id, station.city, station.address, station.serialNumber, station.ocppId].join(' ').toLowerCase().includes(String(f.search).toLowerCase())) return false;
    return true;
  }
  activeSessions(filters = {}) {
    const visible = this.stations(filters).filter(s => s.status === 'busy');
    return visible.map(s => ({ id: `active-${s.id}`, stationId: s.id, stationName: s.name, portId: s.ports[0].id, portNumber: 1, customerName: 'Клиент ДЕМО', startedAt: new Date(Date.parse(this.store.now) - 35 * 60000).toISOString(), powerKw: s.powerKw, energy: '24.350', charged: mul('24.350', s.tariff), durationMinutes: 35, source: s.source, stale: s.stale, demo: true }));
  }
  _sessions(f) {
    const stations = this.store.stations.filter(s => this._scope(s) && this._matchesStation(s, f));
    const ids = new Set(stations.map(s => s.id));
    const start = Date.parse(f.start), end = Date.parse(f.endExclusive);
    return this.store.sessions.filter(s => ids.has(s.stationId) && Date.parse(s.startedAt) >= start && Date.parse(s.startedAt) < end && (!f.portId || s.portId === f.portId) && (!f.customerId || s.customerId === f.customerId) && (!f.operation || s.operation === f.operation) && (!f.paymentStatus || s.paymentStatus === f.paymentStatus) && (!f.sessionStatus || s.status === f.sessionStatus)).sort((a, b) => b.startedAt.localeCompare(a.startedAt));
  }
  _totals(sessions) {
    const paidKnown = sessions.every(s => s.paid !== null && s.paid !== undefined);
    const paid = paidKnown ? sum(sessions.map(s => s.paid)) : null;
    const refunds = sessions.every(s => s.refund !== null && s.refund !== undefined) ? sum(sessions.map(s => s.refund)) : null;
    const revenue = paid !== null && refunds !== null ? sub(paid, refunds) : null;
    const totalEnergy = sum(sessions.map(s => s.energy), 3);
    const charged = sum(sessions.map(s => s.charged));
    return { sessions: sessions.length, successful: sessions.filter(s => s.status === 'completed').length, cancelled: sessions.filter(s => s.status === 'cancelled').length, failed: sessions.filter(s => s.status === 'error').length, energy: totalEnergy, durationMinutes: sessions.reduce((n, s) => n + s.durationMinutes, 0), charged, paid, refunds, revenue, unpaid: paid === null ? null : sub(charged, paid), averageTariff: raw(totalEnergy) > 0n ? div(charged, totalEnergy) : null, customers: new Set(sessions.filter(s => s.status === 'completed').map(s => s.customerId)).size, platformFee: sum(sessions.map(s => s.platformFee)), acquiringFee: sum(sessions.map(s => s.acquiringFee)), tax: sum(sessions.map(s => s.tax)), estimatedElectricity: sum(sessions.map(s => s.estimatedElectricity)), partnerShare: sum(sessions.map(s => s.partnerShare || '0.00')), reserve: sum(sessions.map(s => s.reserve || '0.00')), actualElectricity: null, estimatedRent: '0.00', actualRent: null, rent: '0.00', other: '0.00', payout: '0.00', estimatedProfit: null, actualProfit: null, margin: null, actualMargin: null, currency: 'KGS' };
  }
  _calculateProfit(totals, deductions = DEFAULT_DEDUCTIONS) {
    const selected = Array.isArray(deductions) ? deductions : DEFAULT_DEDUCTIONS;
    const permitted = [...DEFAULT_DEDUCTIONS, 'partnerShare', 'reserve', 'payout'];
    if (selected.some(key => !permitted.includes(key))) fail('Неизвестная статья расчёта прибыли');
    const compute = actual => {
      if (actual && !Array.isArray(this.store.payments)) return null;
      if (totals.revenue === null) return null;
      let profit = totals.revenue;
      for (const key of selected) {
        const value = key === 'electricity' ? actual ? totals.actualElectricity : totals.estimatedElectricity : key === 'rent' && actual ? totals.actualRent : totals[key];
        if (value === null || value === undefined) return null;
        profit = sub(profit, value);
      }
      return profit;
    };
    totals.estimatedProfit = compute(false); totals.actualProfit = compute(true);
    totals.margin = totals.estimatedProfit !== null && totals.revenue !== null && raw(totals.revenue) > 0n ? div(mul(totals.estimatedProfit, '100'), totals.revenue) : null;
    totals.actualMargin = totals.actualProfit !== null && totals.revenue !== null && raw(totals.revenue) > 0n ? div(mul(totals.actualProfit, '100'), totals.revenue) : null;
    totals.deductions = [...selected];
    totals.formula = 'Оплачено − возвраты − выбранные расходы. Выплата партнёру — отдельный денежный перевод; вычитается только при выборе payout.';
    return totals;
  }
  _monthlyRows(f, selectedSessions, fullMonths = false) {
    const pairs = new Map();
    for (const s of selectedSessions) pairs.set(`${s.stationId}|${bishkekDate(s.startedAt).slice(0, 7)}`, { stationId: s.stationId, month: bishkekDate(s.startedAt).slice(0, 7) });
    // Include manually entered months even when no session took place.
    for (const row of this.store.monthLedger) if (row.month >= f.from.slice(0, 7) && row.month <= f.to.slice(0, 7)) {
      const station = this.store.stations.find(s => s.id === row.stationId);
      if (station && this._scope(station) && this._matchesStation(station, f) && !f.portId && !f.customerId && !f.operation && !f.paymentStatus && !f.sessionStatus) pairs.set(`${row.stationId}|${row.month}`, { stationId: row.stationId, month: row.month });
    }
    return [...pairs.values()].map(({ stationId, month }) => {
      const station = this._rawStation(stationId), bounds = monthBounds(month);
      const rows = selectedSessions.filter(s => s.stationId === stationId && bishkekDate(s.startedAt).startsWith(month));
      const ledger = this.store.monthLedger.find(r => r.stationId === stationId && r.month === month);
      const totals = this._totals(rows);
      const from = f.from > bounds.from ? f.from : bounds.from, to = f.to < bounds.to ? f.to : bounds.to;
      const days = BigInt((Date.parse(`${to}T00:00Z`) - Date.parse(`${from}T00:00Z`)) / DAY + 1);
      const monthDays = BigInt(Number(bounds.to.slice(-2)));
      const rentSnapshots = new Map(this.store.sessions.filter(s => s.stationId === stationId && bishkekDate(s.startedAt).startsWith(month) && s.rentRate !== undefined).map(s => [bishkekDate(s.startedAt), s.rentRate]));
      const historicalFallback = rentSnapshots.values().next().value;
      let dailyRent = 0n;
      for (let day = from; day <= to; day = addDays(day, 1)) {
        const rate = rentSnapshots.get(day) || (day < this.store.today && historicalFallback ? historicalFallback : effectiveRate(station, 'rent', day)) || '0.00';
        dailyRent += raw(rate);
      }
      totals.estimatedRent = formatScaled(rounded(dailyRent, monthDays * 10000n), 2);
      const complete = f.from <= bounds.from && f.to >= bounds.to && !f.portId && !f.customerId && !f.operation && !f.paymentStatus && !f.sessionStatus;
      const bill = ledger?.bill ? clone(ledger.bill) : null;
      totals.actualElectricity = complete ? bill?.amount ?? null : null;
      totals.actualRent = complete ? ledger?.rent ?? null : null;
      totals.rent = ledger?.rent !== null && ledger?.rent !== undefined ? formatScaled(rounded(raw(ledger.rent) * days, monthDays * 10000n), 2) : totals.estimatedRent;
      totals.other = ledger?.other !== null && ledger?.other !== undefined ? formatScaled(rounded(raw(ledger.other) * days, monthDays * 10000n), 2) : '0.00';
      const subScope = Boolean(f.portId || f.customerId || f.operation || f.paymentStatus || f.sessionStatus);
      if (subScope) { totals.estimatedRent = null; totals.rent = null; totals.other = null; }
      totals.payout = complete ? ledger?.payout ?? null : null;
      this._calculateProfit(totals, f.deductions);
      return { ...totals, id: ledger?.id || `month-${stationId}-${month}`, month, stationId, stationName: station.name, orgId: station.orgId, bill, electricityDifference: bill && complete ? sub(bill.amount, totals.estimatedElectricity) : null, meterEnergy: bill?.meterEnergy ?? null, meterDifference: bill?.meterEnergy && complete ? sub(bill.meterEnergy, totals.energy, 3) : null, comment: ledger?.comment || '', actor: ledger?.actor || null, updatedAt: ledger?.updatedAt || null, paid: bill?.paid ?? null, debt: complete && ledger?.payout !== null && ledger?.payout !== undefined ? sub(totals.partnerShare, ledger.payout) : null, completePeriod: complete, partialPeriod: !complete, source: 'ДЕМО · локальный журнал', demo: true };
    }).sort((a, b) => b.month.localeCompare(a.month) || a.stationName.localeCompare(b.stationName));
  }
  monthly(filters = {}) {
    this._require('finance');
    const f = this._filters(filters), rows = this._sessions(f);
    return clone(this._monthlyRows(f, rows));
  }
  report(filters = {}) {
    this._require('reports');
    const f = this._filters(filters), sessions = this._sessions(f), totals = this._totals(sessions);
    const monthly = this._monthlyRows(f, sessions);
    const applyMonths = (target, months) => {
      for (const key of ['estimatedRent', 'rent', 'other']) target[key] = months.every(m => m[key] !== null) ? sum(months.map(m => m[key])) : null;
      target.payout = months.length && months.every(m => m.payout !== null) ? sum(months.map(m => m.payout)) : null;
      target.actualElectricity = months.length && months.every(m => m.actualElectricity !== null) ? sum(months.map(m => m.actualElectricity)) : null;
      target.actualRent = months.length && months.every(m => m.actualRent !== null) ? sum(months.map(m => m.actualRent)) : null;
      return this._calculateProfit(target, f.deductions);
    };
    applyMonths(totals, monthly);
    const ids = new Set(sessions.map(s => s.id));
    const ledgerAvailable = Array.isArray(this.store.payments);
    const payments = (this.store.payments || []).filter(p => ids.has(p.sessionId) && this._scope(this._rawStation(p.stationId)) && Date.parse(p.date) >= Date.parse(f.start) && Date.parse(p.date) < Date.parse(f.endExclusive)).map(clone);
    const byStation = [...new Set([...sessions.map(s => s.stationId), ...monthly.map(m => m.stationId)])].map(stationId => ({ stationId, stationName: this._rawStation(stationId).name, ...applyMonths(this._totals(sessions.filter(s => s.stationId === stationId)), monthly.filter(m => m.stationId === stationId)) }));
    const expenses = [];
    for (const row of monthly) {
      const push = (kind, amount, estimated = false, extras = {}) => { if (amount !== null) expenses.push({ id: `${row.id}-${kind}`, orgId: row.orgId, stationId: row.stationId, stationName: row.stationName, month: row.month, date: `${row.month}-01`, kind, amount, estimated, paid: null, comment: row.comment, ...extras, demo: true }); };
      push('electricity-estimate', row.estimatedElectricity, true);
      push('electricity-actual', row.actualElectricity, false, { paid: row.bill?.paid ?? null, documentNumber: row.bill?.number ?? null });
      push('rent', row.rent, row.actualRent === null); push('other', row.other); push('platform', row.platformFee); push('acquiring', row.acquiringFee); push('tax', row.tax); if (row.payout !== null) push('payout', row.payout);
    }
    const ledgerReceived = !ledgerAvailable || totals.paid === null ? null : sum(payments.filter(p => p.kind === 'payment' && p.status === 'received').map(p => p.amount));
    const ledgerRefunds = !ledgerAvailable || totals.refunds === null ? null : sum(payments.filter(p => p.kind === 'refund').map(p => p.amount));
    const reconciliation = { sessionPaid: totals.paid, ledgerReceived, refunds: ledgerRefunds, commissions: add(totals.platformFee, totals.acquiringFee), difference: totals.paid === null || ledgerReceived === null ? null : sub(totals.paid, ledgerReceived), dateBasis: 'Сессии — по началу; их платежи — по дате проводки внутри выбранного периода (Asia/Bishkek). Проводки за границей периода дают разницу сверки.', source: 'ДЕМО · реестры сессий и платежей', demo: true };
    const result = { filters: f, totals, sessions, byStation, payments, expenses, reconciliation, monthly, generatedAt: new Date().toISOString(), source: 'ДЕМО · локальные тестовые данные', demo: true };
    if (!this.permission('finance')) {
      const restricted = ['platformFee', 'acquiringFee', 'tax', 'estimatedElectricity', 'actualElectricity', 'estimatedRent', 'actualRent', 'rent', 'other', 'payout', 'partnerShare', 'reserve', 'estimatedProfit', 'actualProfit', 'margin', 'actualMargin'];
      const redact = value => { const row = { ...value }; for (const key of restricted) if (has(row, key)) row[key] = null; delete row.electricityRate; delete row.rentRate; delete row.taxRate; delete row.platformRate; delete row.acquiringRate; delete row.deductions; delete row.formula; return row; };
      result.totals = redact(totals); result.byStation = byStation.map(redact); result.sessions = sessions.map(redact); result.payments = payments.map(redact); result.expenses = []; result.monthly = [];
      result.reconciliation = { ...reconciliation, ledgerReceived: null, commissions: null, difference: null };
      result.restrictedFinance = true;
    }
    return clone(result);
  }
  exportReport(filters = {}) {
    this._require('export');
    const snapshot = this.report(filters);
    this._audit('report.exported', snapshot.filters.stationId || null, null, { filters: snapshot.filters, sessions: snapshot.totals.sessions }, snapshot.filters.orgId || this._account().orgId);
    return snapshot;
  }
  events() {
    this._require('view'); const a = this._account();
    const finance = this.permission('finance');
    return clone([...this.store.events, ...this.store.audit].filter(e => (a.role === 'admin' || e.orgId === a.orgId) && (!e.stationId || this._scope(this.store.stations.find(s => s.id === e.stationId))) && (!e.action?.startsWith('member.') || this.permission('members.manage')) && (!e.action?.startsWith('discount.') || this.permission('discounts.manage')) && (!e.action?.startsWith('free.') || this.permission('free.grant')) && (e.action !== 'notifications.updated' || e.actor === this.accountId || a.role === 'admin') && (e.action !== 'report.exported' || e.actor === this.accountId || a.role === 'admin' || a.role === 'owner') && (finance || !e.action?.startsWith('finance.') && (e.action !== 'tariff.updated' || e.after?.every(t => ['customer', 'premium'].includes(t.kind))))).map(e => {
      if (finance || !e.action) return e;
      const result = { ...e };
      if (e.action.startsWith('station.')) {
        for (const field of ['before', 'after']) if (result[field]?.tariffs) result[field] = this._presentStation(result[field]);
      } else if (!['tariff.updated', 'report.exported'].includes(e.action)) { delete result.before; delete result.after; }
      return result;
    }).sort((a, b) => b.at.localeCompare(a.at)));
  }
  brands() { this._require('view'); const a = this._account(); return clone(this.store.brands.filter(b => a.role === 'admin' || b.orgId === a.orgId)); }
  members() { this._require('members.manage'); const a = this._account(); return clone(this.store.members.filter(m => a.role === 'admin' || m.orgId === a.orgId)); }
  discounts() { this._require('discounts.manage'); const a = this._account(); return clone(this.store.discounts.filter(d => a.role === 'admin' || d.orgId === a.orgId)); }
  notificationSettings() { this._require('view'); return clone(this.store.notificationSettings[this.accountId] || { email: true, push: true, offlineMinutes: 15, lowPowerKw: '5.000', longSessionMinutes: 120 }); }
  saveNotifications(patch) {
    this._require('notifications.edit'); const before = this.notificationSettings();
    const after = { email: Boolean(patch.email), push: Boolean(patch.push), offlineMinutes: integer(patch.offlineMinutes ?? before.offlineMinutes, 1, 1440), lowPowerKw: positiveEnergy(patch.lowPowerKw ?? before.lowPowerKw), longSessionMinutes: integer(patch.longSessionMinutes ?? before.longSessionMinutes, 1, 1440) };
    this.store.notificationSettings[this.accountId] = after; this._audit('notifications.updated', null, before, after); return clone(after);
  }
  saveStation(patch) {
    this._require('stations.edit');
    const existing = patch.id ? this._rawStation(patch.id) : null;
    const orgId = existing?.orgId || this._org(patch.orgId);
    if (patch.orgId && patch.orgId !== orgId) fail('Нельзя переносить точку в другую организацию');
    const before = clone(existing), next = existing ? clone(existing) : { id: this._id('st'), orgId, ownerId: orgId === 'org-b' ? 'owner-b' : 'owner-a', ownerName: orgId === 'org-b' ? 'ОсОО «Түштүк Энерго»' : 'ОсОО «Qubat Бишкек»', ports: [], tariffs: [], archived: false, status: 'offline', lastSeen: this.store.now, powerKw: '0.000', source: 'ДЕМО · локальный набор', demo: true, createdAt: new Date().toISOString() };
    for (const key of ['name', 'city', 'address', 'groupId', 'groupName', 'locationId', 'locationName', 'model', 'serialNumber', 'ocppId', 'externalId', 'firmware', 'operatingHours', 'brandId']) if (has(patch, key)) next[key] = text(patch[key]);
    if (!next.name || !next.city || !next.address) fail('Заполните название, город и адрес');
    if (patch.brandId) { const brand = this.store.brands.find(b => b.id === patch.brandId && b.orgId === orgId); if (!brand) fail('Бренд недоступен'); next.brandName = brand.name; }
    next.hardwareCount = integer(patch.hardwareCount ?? next.hardwareCount ?? 1, 1, 1000);
    next.nominalPower = positiveMoney(patch.nominalPower ?? next.nominalPower ?? '60.00');
    if (raw(next.nominalPower) === 0n) fail('Мощность должна быть больше нуля');
    for (const key of ['latitude', 'longitude']) if (has(patch, key)) { const normalized = decimal(patch[key], 6); if (raw(normalized) < raw(key === 'latitude' ? '-90' : '-180') || raw(normalized) > raw(key === 'latitude' ? '90' : '180')) fail('Некорректные координаты'); next[key] = normalized; }
    next.idleTariff = positiveMoney(patch.idleTariff ?? next.idleTariff ?? '0'); next.reservationFee = positiveMoney(patch.reservationFee ?? next.reservationFee ?? '0'); next.reservationMinutes = integer(patch.reservationMinutes ?? next.reservationMinutes ?? 15, 0, 1440); next.stopAtPercent = integer(patch.stopAtPercent ?? next.stopAtPercent ?? 100, 0, 100);
    if (patch.ports) {
      if (!Array.isArray(patch.ports) || !patch.ports.length || patch.ports.length > 32) fail('От 1 до 32 разъёмов');
      const numbers = new Set();
      next.ports = patch.ports.map((port, i) => { const number = integer(port.number ?? i + 1, 1, 99); if (numbers.has(number)) fail('Номера разъёмов должны различаться'); numbers.add(number); const previous = next.ports.find(p => p.id === port.id || p.number === number); if (port.id && !next.ports.some(p => p.id === port.id)) fail('Разъём не принадлежит точке'); return { id: previous?.id || `${next.id}-p${number}`, number, type: text(port.type || 'GB/T DC', 30), maxPower: positiveMoney(port.maxPower ?? next.nominalPower), maxCurrent: positiveMoney(port.maxCurrent ?? '250'), status: previous?.status || 'unavailable', lastSeen: previous?.lastSeen || this.store.now, powerKw: previous?.powerKw || '0.000' }; });
    }
    if (!next.ports.length) next.ports = [{ id: `${next.id}-p1`, number: 1, type: 'GB/T DC', maxPower: next.nominalPower, maxCurrent: '250.00', status: 'unavailable', lastSeen: this.store.now, powerKw: '0.000' }];
    next.locationId ||= `loc-${next.id}`; next.locationName ||= next.name; next.groupId ||= 'ungrouped'; next.groupName ||= 'Без группы';
    if (!existing) for (const kind of TARIFF_KINDS) next.tariffs.push({ id: this._id('tariff'), kind, value: kind === 'customer' ? '13.50' : kind === 'premium' ? '12.50' : '0.00', base: 'DEMO', effectiveFrom: this.store.today, effectiveTo: null, actor: this.accountId, createdAt: new Date().toISOString() });
    next.updatedAt = new Date().toISOString();
    if (existing) Object.assign(existing, next); else { this.store.stations.push(next); const owner = this.store.accounts.find(a => a.id === next.ownerId); if (owner) owner.stationIds.push(next.id); }
    this._audit(existing ? 'station.updated' : 'station.created', next.id, before, next, orgId);
    return this.station(next.id);
  }
  archiveStation(id) { this._require('stations.edit'); const station = this._rawStation(id); const before = clone(station); station.archived = true; station.updatedAt = new Date().toISOString(); this._audit('station.archived', id, before, station); return this.station(id); }
  updateTariff(id, patch) {
    this._require('tariff.edit'); const station = this._rawStation(id); const kind = patch.kind || 'customer';
    if (!TARIFF_KINDS.includes(kind)) fail('Неизвестный вид тарифа');
    const value = positiveMoney(patch.value); if (['platform', 'acquiring', 'tax', 'partner', 'reserve'].includes(kind) && raw(value) > raw('100')) fail('Ставка не может превышать 100%');
    if (['customer', 'premium'].includes(kind) && raw(value) === 0n) fail('Клиентский тариф должен быть больше нуля');
    const effectiveFrom = dateOnly(patch.effectiveFrom || this.store.today), before = clone(station.tariffs.filter(t => t.kind === kind));
    if (station.tariffs.some(t => t.kind === kind && t.effectiveFrom === effectiveFrom)) fail('Версия с этой датой уже существует; выберите другую дату');
    const versions = [...before, { id: this._id('tariff'), kind, value, base: patch.base || before[0]?.base || 'kWh', effectiveFrom, effectiveTo: null, actor: this.accountId, createdAt: new Date().toISOString() }].sort((a, b) => a.effectiveFrom.localeCompare(b.effectiveFrom));
    versions.forEach((version, index) => { version.effectiveTo = versions[index + 1] ? addDays(versions[index + 1].effectiveFrom, -1) : null; });
    station.tariffs = [...station.tariffs.filter(t => t.kind !== kind), ...versions];
    this._audit('tariff.updated', id, before, versions); return this.station(id);
  }
  saveMonthExpense(patch) {
    this._require('finance'); const station = this._rawStation(patch.stationId); const bounds = monthBounds(patch.month);
    let row = this.store.monthLedger.find(r => r.stationId === station.id && r.month === patch.month);
    const before = clone(row ?? null), next = row ? clone(row) : { id: this._id('month'), orgId: station.orgId, stationId: station.id, month: patch.month, bill: null, rent: null, other: null, payout: null, comment: '', demo: true };
    if (has(patch, 'bill') || has(patch, 'actualElectricity')) {
      const bill = patch.bill === null || patch.actualElectricity === null ? null : { ...(next.bill || {}), ...(patch.bill || {}), ...(has(patch, 'actualElectricity') ? { amount: patch.actualElectricity } : {}) };
      if (bill) {
        bill.amount = positiveMoney(bill.amount); bill.number = text(bill.number, 100); bill.date = dateOnly(bill.date || bounds.to); bill.periodFrom = dateOnly(bill.periodFrom || bounds.from); bill.periodTo = dateOnly(bill.periodTo || bounds.to); if (bill.periodFrom > bill.periodTo) fail('Некорректный период счёта'); bill.paid = Boolean(bill.paid);
        if (bill.meterEnergy !== null && bill.meterEnergy !== undefined && bill.meterEnergy !== '') bill.meterEnergy = positiveEnergy(bill.meterEnergy); else bill.meterEnergy = null;
        if (bill.attachment) bill.attachment = { name: text(bill.attachment.name, 200), size: integer(bill.attachment.size ?? 0, 0, 10000000), type: text(bill.attachment.type, 100), demo: true };
      }
      next.bill = bill;
    }
    for (const key of ['rent', 'other', 'payout']) if (has(patch, key)) next[key] = patch[key] === null || patch[key] === '' ? null : positiveMoney(patch[key]);
    if (has(patch, 'comment')) next.comment = text(patch.comment, 1000);
    next.actor = this.accountId; next.updatedAt = new Date().toISOString();
    if (row) Object.assign(row, next); else { this.store.monthLedger.push(next); row = next; }
    this._audit('finance.month.updated', station.id, before, next);
    return this.monthly({ ...bounds, stationId: station.id }).find(r => r.month === patch.month);
  }
  inviteMember(patch) {
    this._require('members.manage'); const orgId = this._org(patch.orgId);
    if (!['employee', 'accountant', 'observer'].includes(patch.role)) fail('Разрешены роли сотрудника, бухгалтера и наблюдателя');
    const stationIds = this._stationIds(patch.stationIds, orgId);
    const name = text(patch.name), contact = text(patch.contact, 150);
    if (!name || !(/^\+?[\d\s()-]{8,20}$/.test(contact) || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(contact))) fail('Введите имя и корректный email или телефон');
    const startsAt = dateOnly(patch.startsAt || this.store.today), endsAt = patch.endsAt ? dateOnly(patch.endsAt) : null;
    if (endsAt && endsAt < startsAt) fail('Некорректный срок доступа');
    const allowed = PERMISSIONS[patch.role], permissions = patch.permissions ? [...new Set(patch.permissions)] : [...allowed];
    if (permissions.some(p => !allowed.includes(p))) fail('Права превышают полномочия выбранной роли');
    const member = { id: this._id('member'), orgId, name, contact, role: patch.role, stationIds, startsAt, endsAt, status: 'invited', permissions, createdAt: new Date().toISOString(), invitedBy: this.accountId, demo: true, delivery: 'ДЕМО: приглашение не отправляется' };
    this.store.members.push(member); this._audit('member.invited', null, null, member, orgId); return clone(member);
  }
  revokeMember(id) {
    this._require('members.manage'); const member = this.store.members.find(m => m.id === id); if (!member) fail('Сотрудник недоступен'); this._org(member.orgId); const before = clone(member); member.status = 'revoked'; member.revokedAt = new Date().toISOString(); member.revokedBy = this.accountId; this._audit('member.revoked', null, before, member, member.orgId); return clone(member);
  }
  _stationIds(ids, orgId) { if (!Array.isArray(ids) || !ids.length) fail('Выберите хотя бы одну точку'); return [...new Set(ids)].map(id => { const s = this._rawStation(id); if (s.orgId !== orgId) fail('Точки должны принадлежать одной организации'); return s.id; }); }
  saveDiscount(patch) { this._require('discounts.manage'); return this._saveDiscount(patch, false); }
  saveFreeGrant(patch) { this._require('free.grant'); return this._saveDiscount(patch, true); }
  _saveDiscount(patch, free) {
    const existing = patch.id ? this.store.discounts.find(d => d.id === patch.id) : null; if (patch.id && !existing) fail('Разрешение не найдено');
    const orgId = this._org(existing?.orgId || patch.orgId); const source = { ...existing, ...patch };
    const stationIds = this._stationIds(source.stationIds, orgId), startsAt = dateOnly(source.startsAt || this.store.today), endsAt = dateOnly(source.endsAt || addDays(this.store.today, 30));
    if (startsAt > endsAt) fail('Некорректный срок действия');
    const kind = free ? 'percent' : source.kind || 'percent'; if (!['percent', 'fixed'].includes(kind)) fail('Неизвестный вид скидки');
    const value = free ? '100.00' : positiveMoney(source.value); if (raw(value) === 0n || kind === 'percent' && raw(value) > raw('100')) fail('Некорректная скидка');
    const reason = text(source.reason, 1000); if (free && !reason) fail('Укажите причину бесплатной зарядки');
    const name = text(source.name || (free ? 'ДЕМО · бесплатная зарядка' : '')); if (!name) fail('Введите название скидки');
    const row = { id: existing?.id || this._id('discount'), orgId, name, type: free ? 'free' : 'discount', kind, value, reason, stationIds, startsAt, endsAt, usageLimit: integer(source.usageLimit ?? 1, 1, 1000000), used: existing?.used || 0, approvedBy: this.accountId, createdAt: existing?.createdAt || new Date().toISOString(), updatedAt: new Date().toISOString(), demo: true };
    if (row.usageLimit < row.used) fail('Лимит меньше уже использованного количества');
    const before = clone(existing); if (existing) Object.assign(existing, row); else this.store.discounts.push(row); this._audit(free ? 'free.granted' : 'discount.updated', null, before, row, orgId); return clone(row);
  }
  saveBrand(patch) {
    this._require('brands.edit'); const existing = patch.id ? this.store.brands.find(b => b.id === patch.id) : null; if (patch.id && !existing) fail('Бренд не найден');
    const orgId = this._org(existing?.orgId || patch.orgId); const name = text(patch.name ?? existing?.name), color = text(patch.color ?? existing?.color ?? '#10B981'); if (!name || !/^#[0-9a-f]{6}$/i.test(color)) fail('Введите название и цвет #RRGGBB');
    const row = { id: existing?.id || this._id('brand'), orgId, name, color, description: text(patch.description ?? existing?.description, 1000), demo: true }; const before = clone(existing); if (existing) Object.assign(existing, row); else this.store.brands.push(row); this._audit('brand.updated', null, before, row, orgId); return clone(row);
  }
  hardwareCommand() { fail('ДЕМО: команды действующему оборудованию отключены'); }
}
