package kg.qubat.ev

import java.io.OutputStream
import java.math.BigDecimal
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Small OOXML writer for the bounded demo. It exports the same calculated snapshot as the screen. */
object PartnerExcel {
    private val fileDate=DateTimeFormatter.BASIC_ISO_DATE
    private val timestamp=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX").withZone(PARTNER_ZONE)

    fun fileName(report:PartnerReport):String="QubatEV_Report_${report.filter.period.from.format(fileDate)}_${report.filter.period.to.format(fileDate)}.xlsx"

    fun write(report:PartnerReport,output:OutputStream) {
        validateScope(report)
        val total=report.totals
        val period=report.filter.period
        val summary=buildList<List<Cell>> {
            add(header("Показатель","Значение"))
            add(listOf(text("Режим"),text("ДЕМО — вымышленные данные, не реальные деньги и не банковская выписка")))
            add(listOf(text("Партнёр"),text(report.account.name)))
            add(listOf(text("Роль"),text(report.account.role.label)))
            add(listOf(text("С даты (включительно)"),text(period.from.toString())))
            add(listOf(text("По дату (включительно)"),text(period.to.toString())))
            add(listOf(text("Часовой пояс"),text("Asia/Bishkek, UTC+06:00")))
            add(listOf(text("Валюта"),text("KGS / сом")))
            add(listOf(text("Сформирован"),text(time(report.generatedAt))))
            add(listOf(text("Фильтр станции"),text(report.filter.stationId ?: "Все мои станции")))
            add(listOf(text("Поиск"),text(report.filter.query.ifBlank {"Без поиска"})))
            add(listOf(text("Сортировка зарядок"),text(report.filter.sort.label)))
            add(listOf(text("Продано электроэнергии, кВт·ч"),number(total.energyKwh,3)))
            add(listOf(text("Завершённые сессии"),number(total.completedCount)))
            add(listOf(text("Отменённые сессии"),number(total.cancelledCount)))
            add(listOf(text("Незавершённые сессии"),number(total.unfinishedCount)))
            add(listOf(text("Выручка до удержаний, KGS"),number(total.revenueKgs)))
            add(listOf(text("Удержания и подтверждённые возвраты, KGS"),number(total.deductionsKgs)))
            add(listOf(text("К перечислению за период, KGS"),number(total.payableKgs)))
            add(listOf(text("Перенос остатка до начала периода, KGS"),money(total.openingBalanceKgs)))
            add(listOf(text("Подтверждённые выплаты в периоде, KGS"),money(total.paidKgs)))
            add(listOf(text("Остаток с учётом переноса, KGS"),money(total.balanceKgs)))
            add(listOf(text("Правило расчёта"),text("Выручка минус договорные комиссии и подтверждённые возвраты. Остаток = перенос + к перечислению − подтверждённые переводы. Оплата водителями и переводы владельцу учитываются отдельно.")))
            add(listOf(text("Источник"),text("DEMO_QUBAT; реальные станции и платёжные системы не подключены")))
            add(emptyList())
            add(header("Станция","ID","Адрес","Мощность, кВт","Разъёмы","Тариф, KGS/кВт·ч","кВт·ч","Выручка, KGS","Завершённые сессии","Подключение","Обновлено","Источник","Актуальность"))
            report.stations.forEach {row->
                val station=row.station
                add(listOf(text(station.title),text(station.id),text(station.address),number(station.powerKw),number(station.connectorCount),number(station.tariffKgs),number(row.energyKwh,3),number(row.revenueKgs),number(row.completedCount),text(station.connectionStatus ?: "Нет данных"),text(time(station.updatedAt)),text(station.source),text(if(station.isCurrent) "Данные актуальны · демо" else "Данные требуют обновления")))
            }
            if(report.stations.isEmpty())add(listOf(text("По выбранным фильтрам станции не найдены")))
        }
        val sessions=buildList<List<Cell>> {
            add(header("ID сессии","Станция","ID станции","Порт","Начало (Asia/Bishkek)","Конец (Asia/Bishkek)","Длительность, сек","Энергия, кВт·ч","Тариф, KGS/кВт·ч","Выручка, KGS","Комиссия, KGS","Подтверждённый возврат, KGS","Статус сессии","Оплата водителем","Причина возврата","Возврат подтверждён","Ответственный за возврат","Обновлено","Источник","Актуальность"))
            report.sessions.forEach {session->
                add(listOf(text(session.id),text(session.stationTitle),text(session.stationId),text(session.port),text(time(session.startedAt)),text(time(session.endedAt)),session.durationSeconds?.let {Cell.Number(it.toBigDecimal())} ?: text("Не завершена"),number(session.energyKwh,3),number(session.tariffKgs),number(session.revenueKgs),number(session.commissionKgs),number(session.refundKgs),text(session.status.label),text(session.paymentStatus.label),text(session.refundReason ?: "—"),text(session.refundConfirmedAt?.let(timestamp::format) ?: "—"),text(session.refundResponsible ?: "—"),text(time(session.updatedAt)),text(session.source),text(if(session.isCurrent) "Данные актуальны · демо" else "Данные требуют обновления")))
            }
            if(report.sessions.isEmpty())add(listOf(text("За выбранный период зарядок не найдено")))
        }
        val payouts=buildList<List<Cell>> {
            add(header("Дата перевода (Asia/Bishkek)","Номер выплаты","Станция","ID станции","Сумма, KGS","Статус подтверждения","Расчётный период с","Расчётный период по","Примечание","Обновлено","Источник","Актуальность"))
            report.payouts.forEach {payout->
                add(listOf(text(time(payout.paidAt)),text(payout.id),text(payout.stationTitle),text(payout.stationId),number(payout.amountKgs),text(if(payout.confirmed) "Подтверждено · демо" else "Ожидает подтверждения · демо"),text(payout.period.from.toString()),text(payout.period.to.toString()),text(payout.note),text(time(payout.updatedAt)),text(payout.source),text(if(payout.isCurrent) "Данные актуальны · демо" else "Данные требуют обновления")))
            }
            if(report.payouts.isEmpty())add(listOf(text(if(total.paidKgs==null) "Нет данных о подтверждённых выплатах" else "За выбранный период выплат не найдено")))
            add(emptyList())
            add(listOf(text("Подтверждённые выплаты за период, KGS"),money(total.paidKgs)))
            add(listOf(text("Примечание"),text("ДЕМО. Это демонстрация сверки, а не подтверждение реальных банковских переводов. Выплаты отбираются по дате перевода, начисления — по завершению зарядок; прежний остаток вынесен отдельно.")))
        }
        writeWorkbook(listOf(Sheet("Сводка",summary,listOf(46,39,48,18,14,22,18,22,23,24,32,24,30)),Sheet("Зарядки",sessions,listOf(39,30,25,28,31,31,22,20,24,22,22,26,24,29,46,31,32,31,24,30)),Sheet("Выплаты",payouts,listOf(33,43,30,25,22,33,24,24,70,31,24,30))),output)
    }

    private fun validateScope(report:PartnerReport) {
        // Demo validation only; the production service must authorize each request.
        val repository=PartnerDemoRepository(report.filter.period.to,report.generatedAt)
        val allowed=repository.stations(report.account).associateBy {it.id}
        report.filter.stationId?.let {repository.station(report.account,it)}
        val selected=report.stations.map {it.station.id}.toSet()
        if(report.stations.any {allowed[it.station.id]?.ownerId!=it.station.ownerId || (report.filter.stationId!=null && it.station.id!=report.filter.stationId)} ||
            report.sessions.any {it.stationId !in selected || it.stationId !in allowed || it.accountingAt !in report.filter.period} ||
            report.payouts.any {it.stationId !in selected || it.stationId !in allowed || it.paidAt !in report.filter.period}) {
            throw SecurityException("Отчёт содержит недоступную станцию")
        }
    }

    // Strings always use inlineStr, so labels beginning with '=' are never executed as formulas.
    private sealed interface Cell {
        data class Text(val value:String,val style:Int=0):Cell
        data class Number(val value:BigDecimal,val style:Int=0):Cell
    }
    private data class Sheet(val name:String,val rows:List<List<Cell>>,val widths:List<Int>,val freezeRows:Int=1)
    private fun text(value:String)=Cell.Text(value)
    private fun number(value:BigDecimal,style:Int=2)=Cell.Number(value,style)
    private fun number(value:Int)=Cell.Number(value.toBigDecimal())
    private fun money(value:BigDecimal?):Cell=if(value==null)text("Нет данных") else number(value)
    private fun time(value:Instant?):String=value?.let(timestamp::format) ?: "Нет данных"
    private fun header(vararg names:String)=names.map {Cell.Text(it,1)}

    private fun writeWorkbook(sheets:List<Sheet>,output:OutputStream) {
        // Release the ZIP deflater without taking ownership of the caller's document stream.
        val zip=ZipOutputStream(object:java.io.FilterOutputStream(output) {override fun close() {flush()}})
        fun entry(path:String,xml:String) {
            zip.putNextEntry(ZipEntry(path).apply {time=0L})
            zip.write(xml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        try {
        entry("[Content_Types].xml","""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>${sheets.indices.joinToString("") {"<Override PartName=\"/xl/worksheets/sheet${it+1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"}}</Types>""")
        entry("_rels/.rels","""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
        entry("xl/workbook.xml","""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>${sheets.mapIndexed {i,s->"<sheet name=\"${escape(s.name)}\" sheetId=\"${i+1}\" r:id=\"rId${i+1}\"/>"}.joinToString("")}</sheets></workbook>""")
        entry("xl/_rels/workbook.xml.rels","""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${sheets.indices.joinToString("") {"<Relationship Id=\"rId${it+1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${it+1}.xml\"/>"}}<Relationship Id="rId${sheets.size+1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
        entry("xl/styles.xml",styles)
        sheets.forEachIndexed {i,sheet->entry("xl/worksheets/sheet${i+1}.xml",sheetXml(sheet))}
        } finally {zip.close()}
    }

    private fun sheetXml(sheet:Sheet):String=buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><dimension ref="A1:""")
        append(column((sheet.rows.maxOfOrNull {it.size} ?: 1)-1));append(sheet.rows.size.coerceAtLeast(1));append("\"/>")
        append("<sheetViews><sheetView workbookViewId=\"0\">")
        if(sheet.freezeRows>0)append("<pane ySplit=\"${sheet.freezeRows}\" topLeftCell=\"A${sheet.freezeRows+1}\" activePane=\"bottomLeft\" state=\"frozen\"/>")
        append("</sheetView></sheetViews><sheetFormatPr defaultRowHeight=\"20\"/><cols>")
        sheet.widths.forEachIndexed {i,width->append("<col min=\"${i+1}\" max=\"${i+1}\" width=\"$width\" customWidth=\"1\"/>")}
        append("</cols><sheetData>")
        sheet.rows.forEachIndexed {rowIndex,row->
            append("<row r=\"${rowIndex+1}\">")
            row.forEachIndexed {columnIndex,cell->
                val address="${column(columnIndex)}${rowIndex+1}"
                when(cell) {
                    is Cell.Text->append("<c r=\"$address\" t=\"inlineStr\" s=\"${cell.style}\"><is><t xml:space=\"preserve\">${escape(cell.value)}</t></is></c>")
                    is Cell.Number->append("<c r=\"$address\" s=\"${cell.style}\"><v>${cell.value.toPlainString()}</v></c>")
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }
    private fun column(index:Int):String {
        var n=index+1
        val label=StringBuilder()
        while(n>0) {label.append(('A'.code+(n-1)%26).toChar());n=(n-1)/26}
        return label.reverse().toString()
    }
    private fun escape(value:String):String=buildString {
        value.codePoints().forEach {code->
            when(code) {
                38->append("&amp;");60->append("&lt;");62->append("&gt;");34->append("&quot;");39->append("&apos;")
                else->if(code==9 || code==10 || code==13 || code in 32..0xD7FF || code in 0xE000..0xFFFD || code in 0x10000..0x10FFFF) appendCodePoint(code)
            }
        }
    }
    private val styles="""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="2"><numFmt numFmtId="164" formatCode="#,##0.00 &quot;KGS&quot;"/><numFmt numFmtId="165" formatCode="0.000"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF17644E"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="4"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
}
