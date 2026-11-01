/* QUBAT EV partner demo — dependency-free, local-only OOXML export. */
const encoder = new TextEncoder();
const SHEETS = ['Сводка', 'Сессии', 'По станциям', 'Платежи', 'Расходы'];
const CURRENCY = 2;
const ENERGY = 3;
const INTEGER = 4;
const PERCENT = 5;

const labels = {
  from: 'Дата с', to: 'Дата по', timezone: 'Часовой пояс', orgId: 'Организация',
  organizationId: 'Организация', ownerId: 'Владелец', locationId: 'Локация',
  groupId: 'Группа', stationId: 'Станция', portId: 'Разъём', port: 'Порт',
  customerId: 'Пользователь', operation: 'Тип операции', paymentStatus: 'Статус оплаты',
  successful: 'Успешные сессии', completed: 'Завершённые сессии', canceled: 'Отменённые сессии',
  cancelled: 'Отменённые сессии', failed: 'Ошибочные сессии', sessions: 'Количество сессий',
  count: 'Количество сессий', sessionCount: 'Количество сессий', energy: 'Энергия, кВт·ч',
  energyKwh: 'Энергия, кВт·ч', durationMinutes: 'Время зарядки, мин', duration: 'Время зарядки, мин',
  durationSeconds: 'Время зарядки, сек', charged: 'Начислено, сом', billed: 'Начислено, сом',
  accrued: 'Начислено, сом', paid: 'Оплачено, сом', paidAmount: 'Оплачено, сом',
  grossRevenue: 'Оплачено, сом', refunds: 'Возвраты, сом', revenue: 'Выручка после возвратов, сом',
  netRevenue: 'Выручка после возвратов, сом', unpaid: 'Не оплачено, сом',
  averageTariff: 'Средний тариф, сом/кВт·ч', uniqueCustomers: 'Уникальные клиенты', customers: 'Уникальные клиенты',
  energyEstimate: 'Электроэнергия расчёт, сом', estimatedEnergy: 'Электроэнергия расчёт, сом',
  electricityEstimate: 'Электроэнергия расчёт, сом', estimatedElectricity: 'Электроэнергия расчёт, сом', electricityActual: 'Электроэнергия факт, сом', actualElectricity: 'Электроэнергия факт, сом',
  actualEnergy: 'Электроэнергия факт, сом', actualBill: 'Электроэнергия факт, сом',
  platformCommission: 'Комиссия платформы, сом', acquiringCommission: 'Эквайринг, сом',
  platformFee: 'Комиссия платформы, сом', acquiringFee: 'Эквайринг, сом',
  commission: 'Комиссия, сом', tax: 'Налог, сом', rent: 'Аренда, сом',
  other: 'Прочие расходы, сом', otherExpenses: 'Прочие расходы, сом',
  payout: 'Выплаты партнёру, сом', partnerPayout: 'Выплаты партнёру, сом',
  estimatedProfit: 'Прибыль расчёт, сом', actualProfit: 'Прибыль факт, сом', profit: 'Прибыль, сом',
  margin: 'Маржа, %', marginPercent: 'Маржа, %', debt: 'Задолженность, сом',
  ledgerPaid: 'Поступления платёжной системы, сом', ledgerReceived: 'Поступления платёжной системы, сом',
  paymentReceived: 'Поступления платёжной системы, сом', reconciliationDifference: 'Расхождение сверки, сом',
  balance: 'Остаток, сом', meterKwh: 'Внешний счётчик, кВт·ч', meterDifference: 'Разница счётчика, кВт·ч'
};
const countKeys = new Set(['successful', 'completed', 'canceled', 'cancelled', 'failed', 'sessions', 'count', 'sessionCount', 'uniqueCustomers', 'customers', 'durationMinutes', 'duration', 'durationSeconds']);
const energyKeys = new Set(['energy', 'energyKwh', 'meterKwh', 'meterDifference']);
const percentKeys = new Set(['margin', 'marginPercent']);

function xml(value) {
  return String(value ?? '').replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\uFFFE\uFFFF]/g, '')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&apos;');
}

function display(value) {
  if (value === null || value === undefined || value === '') return 'Нет данных';
  if (typeof value === 'boolean') return value ? 'Да' : 'Нет';
  if (Array.isArray(value)) return value.map(display).join(', ');
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

function colName(index) {
  let value = index + 1;
  let name = '';
  while (value > 0) { value -= 1; name = String.fromCharCode(65 + value % 26) + name; value = Math.floor(value / 26); }
  return name;
}

function cell(value, row, column, style = 0) {
  const ref = `${colName(column)}${row}`;
  // The decimal string goes directly into the OOXML cell: no float conversion.
  const decimal = typeof value === 'string' && /^-?\d+(?:\.\d+)?$/.test(value);
  const integer = typeof value === 'number' && Number.isSafeInteger(value);
  if (style >= CURRENCY && (decimal || integer)) return `<c r="${ref}" s="${style}" t="n"><v>${value}</v></c>`;
  return `<c r="${ref}" s="${style === 1 ? 1 : 0}" t="inlineStr"><is><t xml:space="preserve">${xml(display(value))}</t></is></c>`;
}

function worksheet(rows, widths, headerRow = 1) {
  const lastColumn = Math.max(1, ...rows.map(row => row.length));
  const data = rows.map((row, index) => `<row r="${index + 1}">${row.map((item, column) => {
    const c = item && typeof item === 'object' && !Array.isArray(item) && 'value' in item ? item : {value: item};
    return cell(c.value, index + 1, column, index + 1 === headerRow ? 1 : c.style || 0);
  }).join('')}</row>`).join('');
  const cols = widths.map((width, index) => `<col min="${index + 1}" max="${index + 1}" width="${width}" customWidth="1"/>`).join('');
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><dimension ref="A1:${colName(lastColumn - 1)}${Math.max(1, rows.length)}"/><sheetViews><sheetView workbookViewId="0"><pane ySplit="${headerRow}" topLeftCell="A${headerRow + 1}" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><sheetFormatPr defaultRowHeight="17"/><cols>${cols}</cols><sheetData>${data}</sheetData>${headerRow === 1 && rows.length > 1 ? `<autoFilter ref="A1:${colName(lastColumn - 1)}${rows.length}"/>` : ''}<pageMargins left="0.25" right="0.25" top="0.5" bottom="0.5" header="0.2" footer="0.2"/></worksheet>`;
}

function pick(row, keys) {
  for (const key of keys.split('|')) {
    const value = key.split('.').reduce((object, part) => object?.[part], row);
    if (value !== undefined) return value;
  }
  return null;
}

function bishkekTime(value) {
  if (!value) return null;
  // An explicit offset prevents dependence on the browser's local timezone.
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return new Date(date.getTime() + 6 * 60 * 60 * 1000).toISOString().slice(0, 19).replace('T', ' ') + ' +06:00';
}

function dataTable(rows, columns) {
  return [columns.map(column => column[0]), ...rows.map(row => columns.map(([, key, style, transform]) => {
    let value = pick(row, key);
    if (transform) value = transform(value);
    return {value, style};
  }))];
}

const sessionColumns = [
  ['ID сессии', 'id'], ['Начало (Asia/Bishkek)', 'startedAt|startAt|start', 0, bishkekTime],
  ['Окончание (Asia/Bishkek)', 'endedAt|endAt|end', 0, bishkekTime], ['Локация', 'locationName|location|address'],
  ['Станция', 'stationName|station.name|stationId'], ['Порт', 'portNumber|port|portId'],
  ['Пользователь', 'customerName|customerLabel|customerId|customer'], ['Энергия, кВт·ч', 'energyKwh|energy', ENERGY],
  ['Тариф, сом/кВт·ч', 'tariff|tariffRate|rate', CURRENCY], ['Начислено, сом', 'charged|billed|accrued|amount', CURRENCY],
  ['Оплачено, сом', 'paid|paidAmount', CURRENCY], ['Возврат, сом', 'refund|refunds', CURRENCY], ['Метод оплаты', 'paymentMethod|method'],
  ['Комиссия платформы, сом', 'commission|platformCommission|platformFee', CURRENCY],
  ['Эквайринг, сом', 'acquiringCommission|acquiringFee', CURRENCY], ['Налог, сом', 'tax', CURRENCY],
  ['Время зарядки, мин', 'durationMinutes', INTEGER], ['Статус сессии', 'status'],
  ['Статус оплаты', 'paymentStatus'], ['Ошибка / причина отмены', 'error|errorReason|cancelReason|reason']
];
const stationColumns = [
  ['ID станции', 'stationId|id'], ['Станция', 'stationName|name'], ['Локация', 'locationName|location|address'],
  ['Сессии', 'sessions|count|sessionCount', INTEGER], ['Успешные', 'successful|completed', INTEGER],
  ['Отменённые', 'cancelled|canceled', INTEGER], ['Ошибочные', 'failed', INTEGER],
  ['Энергия, кВт·ч', 'energyKwh|energy', ENERGY], ['Начислено, сом', 'charged|billed|accrued', CURRENCY],
  ['Оплачено, сом', 'paid|paidAmount', CURRENCY], ['Возвраты, сом', 'refunds', CURRENCY],
  ['Выручка, сом', 'revenue|netRevenue', CURRENCY], ['Электроэнергия расчёт, сом', 'estimatedElectricity|energyEstimate|estimatedEnergy|electricityEstimate', CURRENCY],
  ['Комиссия платформы, сом', 'platformCommission|platformFee', CURRENCY], ['Эквайринг, сом', 'acquiringCommission|acquiringFee', CURRENCY],
  ['Налог, сом', 'tax', CURRENCY], ['Прибыль расчёт, сом', 'estimatedProfit|profit', CURRENCY]
];
const paymentColumns = [
  ['ID платежа', 'id'], ['ID сессии', 'sessionId'], ['Дата (Asia/Bishkek)', 'createdAt|paidAt|at|date', 0, bishkekTime],
  ['Станция', 'stationName|stationId'], ['Тип операции', 'operation|kind|type'], ['Статус', 'status'],
  ['Сумма, сом', 'amount|paid', CURRENCY], ['Возврат, сом', 'refund|refunds', CURRENCY],
  ['Комиссия платформы, сом', 'platformCommission|platformFee|commission', CURRENCY],
  ['Эквайринг, сом', 'acquiringCommission|acquiringFee', CURRENCY],
  ['Поступление, сом', 'received|net|ledgerAmount', CURRENCY], ['Метод', 'method|paymentMethod'],
  ['Ссылка сверки', 'reference|externalId']
];
const expenseColumns = [
  ['ID расхода', 'id'], ['Месяц', 'month|period'], ['Станция', 'stationName|stationId'], ['Категория', 'category|kind|type'],
  ['Сумма, сом', 'amount', CURRENCY], ['Расчётная сумма', 'estimated'], ['Оплачено', 'paid'],
  ['Документ', 'documentNumber|document|receipt.number'], ['Дата документа', 'documentDate|receipt.date|date'],
  ['Вложение', 'attachmentName|receipt.fileName|attachment.name'], ['Комментарий', 'comment|note'],
  ['Автор', 'updatedBy|actor|actorName'], ['Изменено (Asia/Bishkek)', 'updatedAt|createdAt|at', 0, bishkekTime]
];

function summaryRows(report) {
  const rows = [
    ['QUBAT EV · Кабинет партнёра · ДЕМО', 'Тестовые данные, не подтверждённые финансовые результаты'],
    ['Часовой пояс', 'Asia/Bishkek (UTC+06:00)'], ['Валюта', 'KGS / сом'],
    ['Источник', 'Локальная демонстрационная модель; подключения к операторам отсутствуют'],
    ['Применённые фильтры', 'Значение']
  ];
  for (const [key, value] of Object.entries(report.filters || {})) rows.push([labels[key] || key, value === '' ? 'Все' : display(value)]);
  rows.push(['Итоговые показатели', 'Значение']);
  for (const [key, value] of Object.entries(report.totals || {})) {
    if (value && typeof value === 'object') {
      for (const [childKey, childValue] of Object.entries(value)) rows.push([`${labels[key] || key} · ${labels[childKey] || childKey}`, {value: childValue, style: countKeys.has(childKey) ? INTEGER : energyKeys.has(childKey) ? ENERGY : percentKeys.has(childKey) ? PERCENT : CURRENCY}]);
    } else rows.push([labels[key] || key, {value, style: countKeys.has(key) ? INTEGER : energyKeys.has(key) ? ENERGY : percentKeys.has(key) ? PERCENT : CURRENCY}]);
  }
  if (report.reconciliation) {
    rows.push(['Акт сверки', 'Значение']);
    const reconciliationLabels = {sessionPaid: 'Оплачено по сессиям, сом', ledgerReceived: 'Поступления платёжной системы, сом', refunds: 'Возвраты, сом', commissions: 'Комиссии, сом', difference: 'Расхождение сверки, сом'};
    for (const [key, value] of Object.entries(report.reconciliation)) rows.push([reconciliationLabels[key] || key, {value, style: CURRENCY}]);
  }
  rows.push(['Формула прибыли', 'Оплачено − возвраты − выбранные расходы. Выплаты партнёру учитываются отдельно, если выбрано их вычитание.']);
  rows.push(['Отсутствующие данные', 'Нет данных: отсутствие счёта или платёжного реестра не считается нулём. Расчёт и факт показаны отдельно.']);
  if (report.formula) rows.push(['Применённая формула', display(report.formula)]);
  if (report.deductions) rows.push(['Выбранные расходы', display(report.deductions)]);
  return rows;
}

const CRC_TABLE = Array.from({length: 256}, (_, byte) => {
  let crc = byte;
  for (let bit = 0; bit < 8; bit++) crc = crc & 1 ? 0xedb88320 ^ (crc >>> 1) : crc >>> 1;
  return crc >>> 0;
});

function crc32(bytes) {
  let crc = 0xffffffff;
  for (const byte of bytes) crc = CRC_TABLE[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

function write16(bytes, offset, value) { bytes[offset] = value & 255; bytes[offset + 1] = (value >>> 8) & 255; }
function write32(bytes, offset, value) { for (let byte = 0; byte < 4; byte++) bytes[offset + byte] = (value >>> (byte * 8)) & 255; }
function concatenate(parts) {
  const length = parts.reduce((sum, part) => sum + part.length, 0);
  const bytes = new Uint8Array(length);
  let offset = 0;
  for (const part of parts) { bytes.set(part, offset); offset += part.length; }
  return bytes;
}

function zip(files) {
  const local = [];
  const central = [];
  let offset = 0;
  for (const [path, content] of files) {
    const name = encoder.encode(path);
    const data = encoder.encode(content);
    const crc = crc32(data);
    const header = new Uint8Array(30 + name.length);
    write32(header, 0, 0x04034b50); write16(header, 4, 20); write16(header, 6, 0x0800);
    write16(header, 12, 0x21); write32(header, 14, crc); write32(header, 18, data.length);
    write32(header, 22, data.length); write16(header, 26, name.length); header.set(name, 30);
    local.push(header, data);
    const directory = new Uint8Array(46 + name.length);
    write32(directory, 0, 0x02014b50); write16(directory, 4, 20); write16(directory, 6, 20);
    write16(directory, 8, 0x0800); write16(directory, 14, 0x21); write32(directory, 16, crc);
    write32(directory, 20, data.length); write32(directory, 24, data.length); write16(directory, 28, name.length);
    write32(directory, 42, offset); directory.set(name, 46); central.push(directory);
    offset += header.length + data.length;
  }
  const directory = concatenate(central);
  const end = new Uint8Array(22);
  write32(end, 0, 0x06054b50); write16(end, 8, files.length); write16(end, 10, files.length);
  write32(end, 12, directory.length); write32(end, 16, offset);
  return concatenate([...local, directory, end]);
}

const styles = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="4"><numFmt numFmtId="164" formatCode="#,##0.00;[Red]-#,##0.00"/><numFmt numFmtId="165" formatCode="#,##0.000"/><numFmt numFmtId="166" formatCode="0"/><numFmt numFmtId="167" formatCode="0.00&quot;%&quot;"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF185A45"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="6"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="166" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="167" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>`;

/** Export an already permission-filtered report. Never accepts a repository/store. */
export function exportWorkbook(report) {
  if (!report || typeof report !== 'object') throw new TypeError('Необходим подготовленный отчёт');
  const tables = [
    worksheet(summaryRows(report), [54, 110]),
    worksheet(dataTable(report.sessions || [], sessionColumns), sessionColumns.map((_, index) => index === 1 || index === 2 ? 29 : 23)),
    worksheet(dataTable(report.byStation || [], stationColumns), stationColumns.map(() => 25)),
    worksheet(dataTable(report.payments || [], paymentColumns), paymentColumns.map(() => 25)),
    worksheet(dataTable(report.expenses || [], expenseColumns), expenseColumns.map(() => 25))
  ];
  const files = [
    ['[Content_Types].xml', `<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>${SHEETS.map((_, index) => `<Override PartName="/xl/worksheets/sheet${index + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>`).join('')}</Types>`],
    ['_rels/.rels', '<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>'],
    ['xl/workbook.xml', `<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><bookViews><workbookView activeTab="0"/></bookViews><sheets>${SHEETS.map((name, index) => `<sheet name="${name}" sheetId="${index + 1}" r:id="rId${index + 1}"/>`).join('')}</sheets><calcPr calcId="191029"/></workbook>`],
    ['xl/_rels/workbook.xml.rels', `<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${SHEETS.map((_, index) => `<Relationship Id="rId${index + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${index + 1}.xml"/>`).join('')}<Relationship Id="rId6" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>`],
    ['xl/styles.xml', styles],
    ...tables.map((content, index) => [`xl/worksheets/sheet${index + 1}.xml`, content])
  ];
  return zip(files);
}

export function reportFilename(report) {
  const safeDate = value => /^\d{4}-\d{2}-\d{2}$/.test(String(value)) ? String(value) : 'all';
  return `qubat-demo-${safeDate(report?.filters?.from)}-${safeDate(report?.filters?.to)}.xlsx`;
}

export function downloadReport(report) {
  const bytes = exportWorkbook(report);
  const blob = new Blob([bytes], {type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'});
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url; anchor.download = reportFilename(report); anchor.hidden = true;
  document.body.appendChild(anchor); anchor.click(); anchor.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  return {filename: anchor.download, bytes: bytes.length};
}
