import test from 'node:test';
import assert from 'node:assert/strict';
import {exportWorkbook, reportFilename} from '../dist/excel.mjs';

function unpack(bytes) {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const files = new Map();
  const decoder = new TextDecoder();
  let offset = 0;
  while (view.getUint32(offset, true) === 0x04034b50) {
    const method = view.getUint16(offset + 8, true);
    assert.equal(method, 0, 'local files use ZIP store compression');
    const size = view.getUint32(offset + 18, true);
    const nameLength = view.getUint16(offset + 26, true);
    const extraLength = view.getUint16(offset + 28, true);
    const start = offset + 30 + nameLength + extraLength;
    files.set(decoder.decode(bytes.slice(offset + 30, offset + 30 + nameLength)), decoder.decode(bytes.slice(start, start + size)));
    offset = start + size;
  }
  assert.equal(view.getUint32(offset, true), 0x02014b50, 'central directory follows local files');
  assert.equal(view.getUint32(bytes.length - 22, true), 0x06054b50, 'archive ends with ZIP directory record');
  assert.equal(view.getUint16(bytes.length - 12, true), files.size, 'directory entry count matches');
  assert.equal(view.getUint32(bytes.length - 6, true), offset, 'directory offset matches');
  return files;
}

const report = {
  filters: {from: '2026-10-01', to: '2026-10-31', stationId: 'station-a'},
  totals: {sessions: 1, energy: '1.250', charged: '16.88', paid: '16.88', refunds: '0.00', revenue: '16.88', estimatedElectricity: '3.14', actualElectricity: null, estimatedProfit: '11.01', customers: 1},
  reconciliation: {sessionPaid: '16.88', ledgerReceived: null, refunds: '0.00', commissions: '0.51', difference: null},
  sessions: [{id: 's1', stationId: 'station-a', stationName: '=1+1 <unsafe & "quoted">', locationName: 'Бишкек', portNumber: 2, customerName: 'Клиент 007', startedAt: '2026-10-01T18:00:00.000Z', endedAt: '2026-10-01T18:30:00.000Z', energy: '1.250', tariff: '13.50', charged: '16.88', paid: '16.88', refund: '0.00', method: 'ДЕМО карта', platformFee: '0.34', acquiringFee: '0.17', tax: '0.68', durationMinutes: 30, status: 'completed', paymentStatus: 'paid', error: null}],
  byStation: [{stationId: 'station-a', stationName: 'Станция А', sessions: 1, energy: '1.250', charged: '16.88', paid: '16.88', revenue: '16.88', estimatedElectricity: '3.14', estimatedProfit: '11.01'}],
  payments: [{id: 'p1', sessionId: 's1', stationId: 'station-a', date: '2026-10-01T18:30:00.000Z', kind: 'payment', amount: '16.88', method: 'ДЕМО карта', status: 'paid', platformFee: '0.34', acquiringFee: '0.17'}],
  expenses: [{id: 'e1', stationId: 'station-a', month: '2026-10', date: '2026-10-31', kind: 'electricity-estimate', amount: '3.14', estimated: true, paid: false, documentNumber: null, comment: 'ДЕМО'}]
};

test('XLSX is a real ZIP package with all five linked Russian worksheets', () => {
  const bytes = exportWorkbook(report);
  assert.ok(bytes instanceof Uint8Array);
  assert.equal(new TextDecoder().decode(bytes.slice(0, 2)), 'PK');
  const files = unpack(bytes);
  assert.equal(files.size, 10);
  const workbook = files.get('xl/workbook.xml');
  for (const name of ['Сводка', 'Сессии', 'По станциям', 'Платежи', 'Расходы']) assert.ok(workbook.includes(`name="${name}"`));
  for (let index = 1; index <= 5; index++) {
    assert.ok(files.has(`xl/worksheets/sheet${index}.xml`));
    assert.ok(files.get('xl/_rels/workbook.xml.rels').includes(`Target="worksheets/sheet${index}.xml"`));
    assert.ok(files.get('[Content_Types].xml').includes(`/xl/worksheets/sheet${index}.xml`));
  }
});

test('amounts stay exact decimal numeric cells; all row schemas export their fields', () => {
  const files = unpack(exportWorkbook(report));
  const sessions = files.get('xl/worksheets/sheet2.xml');
  assert.match(sessions, /t="n"><v>1\.250<\/v>/);
  assert.match(sessions, /t="n"><v>16\.88<\/v>/);
  assert.match(sessions, /t="n"><v>0\.34<\/v>/);
  assert.match(sessions, /t="n"><v>0\.17<\/v>/);
  assert.match(sessions, /2026-10-02 00:00:00 \+06:00/);
  assert.ok(files.get('xl/worksheets/sheet3.xml').includes('<v>3.14</v>'));
  assert.ok(files.get('xl/worksheets/sheet4.xml').includes('payment'));
  assert.ok(files.get('xl/worksheets/sheet5.xml').includes('electricity-estimate'));
  const longDecimal = '9007199254740993.25';
  const precise = unpack(exportWorkbook({...report, sessions: [{...report.sessions[0], charged: longDecimal}]}));
  assert.ok(precise.get('xl/worksheets/sheet2.xml').includes(`<v>${longDecimal}</v>`));
});

test('user strings cannot inject formulas or XML; nulls remain unknown', () => {
  const files = unpack(exportWorkbook(report));
  const sessionXml = files.get('xl/worksheets/sheet2.xml');
  assert.match(sessionXml, /t="inlineStr"><is><t xml:space="preserve">=1\+1 &lt;unsafe &amp; &quot;quoted&quot;&gt;/);
  assert.ok(!sessionXml.includes('<f>'));
  const summary = files.get('xl/worksheets/sheet1.xml');
  assert.ok(summary.includes('ДЕМО'));
  assert.ok(summary.includes('2026-10-01'));
  assert.ok(summary.includes('2026-10-31'));
  assert.ok(summary.includes('station-a'));
  assert.ok(summary.includes('Asia/Bishkek'));
  assert.ok(summary.includes('Электроэнергия факт, сом'));
  assert.ok(summary.includes('Нет данных'));
  assert.ok(summary.includes('Расхождение сверки, сом'));
  assert.ok(summary.includes('Поступления платёжной системы, сом'));
});

test('empty reports still produce valid header-only sheets and safe date-based filenames', () => {
  const files = unpack(exportWorkbook({filters: {}, totals: {paid: null}}));
  assert.ok(files.get('xl/worksheets/sheet2.xml').includes('<row r="1">'));
  assert.ok(!files.get('xl/worksheets/sheet2.xml').includes('<row r="2">'));
  assert.equal(reportFilename(report), 'qubat-demo-2026-10-01-2026-10-31.xlsx');
  assert.equal(reportFilename({filters: {from: '../bad', to: '<bad>'}}), 'qubat-demo-all-all.xlsx');
  assert.throws(() => exportWorkbook(null), TypeError);
});
