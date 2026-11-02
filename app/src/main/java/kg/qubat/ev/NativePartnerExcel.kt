package kg.qubat.ev

import java.io.OutputStream
import java.math.BigDecimal
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Pure JVM OOXML export of the exact, already scoped demo report used by the cabinet. */
object NativePartnerExcel {
    private val fileDate=DateTimeFormatter.BASIC_ISO_DATE
    private val timestamp=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX").withZone(PARTNER_ZONE)

    fun fileName(report:NativeNetworkReport):String =
        "QubatEV_Partner_${report.filter.period.from.format(fileDate)}_${report.filter.period.to.format(fileDate)}.xlsx"

    fun write(report:NativeNetworkReport,output:OutputStream) {
        validateScope(report)
        val summary=report.summary
        val filter=report.filter
        val stationById=report.stations.associateBy{it.station.id}
        fun stationName(id:String)=stationById[id]?.station?.title?:id
        fun financial(value:BigDecimal?):Cell=when {
            !report.financeVisible->text("Нет права просмотра финансов")
            value==null->text("Нет данных")
            else->number(value)
        }
        val summaryRows=buildList<List<Cell>> {
            add(header("Показатель","Значение"))
            add(listOf(text("Режим"),text("ДЕМО — вымышленные станции, сессии и деньги; не банковская выписка")))
            add(listOf(text("Партнёр"),text(report.account.name)))
            add(listOf(text("Роль"),text(report.account.role.label)))
            add(listOf(text("С даты (включительно)"),text(filter.period.from.toString())))
            add(listOf(text("По дату (включительно)"),text(filter.period.to.toString())))
            add(listOf(text("Часовой пояс"),text("Asia/Bishkek, UTC+06:00")))
            add(listOf(text("Валюта"),text("KGS / сом")))
            add(listOf(text("Сформирован"),text(time(report.generatedAt))))
            listOf(
                "Организация" to filter.organizationId,"Владелец" to filter.ownerId,"Группа" to filter.groupId,
                "Локация" to filter.locationId,"Станция" to filter.stationId,"Разъём" to filter.portId,
                "Клиент" to filter.customerId,"Тип операции" to filter.operation?.label,
                "Статус оплаты" to filter.paymentStatus?.label,"Статус сессии" to filter.status?.label
            ).forEach{(label,value)->add(listOf(text("Фильтр: $label"),text(value?:"Все доступные")))}
            add(listOf(text("Поиск"),text(filter.query.ifBlank{"Без поиска"})))
            add(listOf(text("Сортировка"),text(filter.sort.label)))
            add(listOf(text("Станции"),number(summary.stationCount)))
            add(listOf(text("Физические аппараты"),number(summary.apparatusCount)))
            add(listOf(text("Независимые разъёмы"),number(summary.connectorCount)))
            add(listOf(text("Занятые разъёмы"),number(summary.activePorts)))
            add(listOf(text("Станции без свежих данных"),number(summary.offlineStations)))
            add(listOf(text("Текущая мощность, кВт"),number(summary.currentPowerKw,3)))
            add(listOf(text("Завершённые сессии"),number(summary.completedCount)))
            add(listOf(text("Отменённые сессии"),number(summary.cancelledCount)))
            add(listOf(text("Сессии с ошибкой"),number(summary.errorCount)))
            add(listOf(text("Активные сессии"),number(summary.activeCount)))
            add(listOf(text("Энергия, кВт·ч"),number(summary.energyKwh,3)))
            add(listOf(text("Время зарядки, сек"),number(summary.durationSeconds.toBigDecimal(),0)))
            add(listOf(text("Уникальные клиенты"),number(summary.uniqueCustomers)))
            add(listOf(text("Начислено, KGS"),financial(summary.chargedKgs)))
            add(listOf(text("Оплачено водителями, KGS"),financial(summary.paidKgs)))
            add(listOf(text("Возвраты, KGS"),financial(summary.refundKgs)))
            add(listOf(text("Выручка: оплачено минус возвраты, KGS"),financial(summary.revenueKgs)))
            add(listOf(text("Начислено, но не оплачено, KGS"),financial(summary.unpaidKgs)))
            add(listOf(text("Средний тариф, KGS/кВт·ч"),financial(summary.averageTariffKgs)))
            add(listOf(text("Поступления по платёжному реестру, KGS"),financial(summary.paymentLedgerKgs)))
            add(listOf(text("Разница сверки, KGS"),financial(summary.reconciliationDifferenceKgs)))
            add(listOf(text("Сессии с неизвестной оплатой"),number(summary.unknownPaymentCount)))
            add(listOf(text("Правило выручки"),text("Выручка — оплата водителями минус возвраты; начисления и неоплаченный остаток показаны отдельно. Если часть оплат или возвратов неизвестна, общий итог и прибыль показаны как «Нет данных»; известные значения отдельных сессий сохранены. Нет данных не означает ноль.")))
            add(listOf(text("Правило факта"),text("Счёт за электроэнергию и фактическая аренда относятся к полному месяцу выбранных точек. Частичный период не получает вымышленную долю квитанции.")))
            add(listOf(text("Источник"),text("DEMO_QUBAT; реальные станции и платёжные системы не подключены")))
            add(emptyList())
            add(header("Месяц","Охват","кВт·ч","Выручка, KGS","Электричество расчёт, KGS","Электричество факт, KGS","Платформа, KGS","Эквайринг, KGS","Налог, KGS","Аренда расчёт, KGS","Аренда факт, KGS","Прочие, KGS","Доля партнёра, KGS","Резерв, KGS","Выплаты, KGS","Прибыль расчёт, KGS","Прибыль факт, KGS","Маржа расчёт, %","Маржа факт, %","Остаток задолженности, KGS","Выбранные расходы","Примечание","Полнота данных об оплате","Сессии с неизвестной оплатой"))
            report.monthlyFinance.forEach{month->
                add(listOf(text(month.month.toString()),text(if(month.fullMonth)"Полный месяц" else "Часть месяца"),number(month.energyKwh,3),money(month.revenueKgs),money(month.electricityEstimateKgs),money(month.electricityFactKgs),money(month.platformKgs),money(month.acquiringKgs),money(month.taxKgs),money(month.rentEstimateKgs),money(month.rentFactKgs),money(month.otherKgs),money(month.partnerShareKgs),money(month.reserveKgs),number(month.payoutKgs),money(month.estimatedProfitKgs),money(month.actualProfitKgs),month.estimatedMarginPercent?.let{number(it,4)}?:text("Нет данных"),month.actualMarginPercent?.let{number(it,4)}?:text("Нет данных"),money(month.outstandingKgs),text(month.selectedDeductions.sortedBy{it.ordinal}.joinToString(", "){it.label}),text(month.factUnavailableReason?:""),text(if(month.paymentDataComplete)"Полные данные · ДЕМО" else "Часть оплат неизвестна"),number(month.unknownPaymentCount)))
            }
            if(!report.financeVisible)add(listOf(text("Помесячные финансы"),text("Нет права просмотра финансов")))
        }
        val sessionRows=buildList<List<Cell>> {
            add(header("ID сессии","Начало (Asia/Bishkek)","Конец (Asia/Bishkek)","Локация","Станция","ID станции","Порт","Клиент","Энергия, кВт·ч","Тариф, KGS/кВт·ч","Начислено, KGS","Оплачено, KGS","Возврат, KGS","Выручка, KGS","Способ оплаты","Платформа, KGS","Эквайринг, KGS","Налог, KGS","Статус сессии","Статус оплаты","Операция","Длительность, сек","Ошибка / причина отмены","Версия тарифа","Обновлено","Источник"))
            report.sessions.forEach{session->
                val rates=session.rates
                add(listOf(text(session.id),text(time(session.startedAt)),text(time(session.endedAt)),text(stationById[session.stationId]?.station?.locationId?:""),text(stationName(session.stationId)),text(session.stationId),text(session.portId),text(session.customerId),number(session.energyKwh,3),financial(rates?.customerRate),financial(session.chargedKgs),financial(session.paidKgs),financial(session.refundKgs),financial(session.revenueKgs),text(session.paymentMethod),financial(session.platformFeeKgs),financial(session.acquiringFeeKgs),financial(session.taxKgs),text(session.status.label),text(session.paymentStatus.label),text(session.operation.label),number(session.durationSeconds.toBigDecimal(),0),text(session.errorReason?:""),text(rates?.tariffId?:if(report.financeVisible)"Нет данных" else "Нет права просмотра финансов"),text(time(session.updatedAt)),text(session.source)))
            }
            if(report.sessions.isEmpty())add(listOf(text("За выбранный период сессий не найдено")))
        }
        val stationRows=buildList<List<Cell>> {
            add(header("Станция","ID","Организация","Владелец","Группа","Локация","Город","Адрес","Модель","Серийный номер","OCPP ID","Прошивка","Бренд","Аппараты","Разъёмы","Мощность, кВт","Текущая мощность, кВт","Завершено сессий","Энергия, кВт·ч","Выручка, KGS","Последний сигнал (Asia/Bishkek)","Свежесть данных","Ошибка","Источник","Архив","ID партнёра","Партнёр","Договор"))
            report.stations.forEach{row->val station=row.station
                val cutoff=report.generatedAt.minusSeconds(300)
                val fresh=station.lastSeen>=cutoff && station.source.isNotBlank()
                val currentPower=if(fresh)station.ports.filter{it.status==NativePortStatus.BUSY && it.updatedAt>=cutoff}.fold(BigDecimal.ZERO){sum,port->sum+port.maxPowerKw*BigDecimal("0.42")}.min(station.powerKw) else BigDecimal.ZERO
                add(listOf(text(station.title),text(station.id),text(station.organizationId),text(station.ownerId),text(station.groupId),text(station.locationId),text(station.city),text(station.address),text(station.model),text(station.serialNumber),text(station.ocppId),text(station.firmware),text(station.brandId),number(station.apparatusCount),number(station.connectorCount),number(station.powerKw,3),number(currentPower,3),number(row.completedCount),number(row.energyKwh,3),financial(row.revenueKgs),text(time(station.lastSeen)),text(if(fresh)"Свежие данные · ДЕМО" else "Данные не обновлялись"),text(station.errorReason?:""),text(station.source),text(if(station.archived)"Да" else "Нет"),text(station.partnerId?:""),text(station.partnerName),text(station.contractId)))
            }
            if(report.stations.isEmpty())add(listOf(text("По выбранным фильтрам станции не найдены")))
        }
        val paymentRows=buildList<List<Cell>> {
            add(header("ID платежа","Дата (Asia/Bishkek)","Станция","ID станции","ID сессии","Поступление, KGS","Возврат, KGS","Комиссия реестра, KGS","Способ оплаты","Статус","Источник"))
            report.payments.forEach{payment->add(listOf(text(payment.id),text(time(payment.at)),text(stationName(payment.stationId)),text(payment.stationId),text(payment.sessionId),number(payment.amountKgs),number(payment.refundKgs),number(payment.feeKgs),text(payment.method),text(payment.status),text(payment.source)))}
            if(report.payments.isEmpty())add(listOf(text(if(!report.financeVisible)"Нет права просмотра финансов" else if(summary.paymentLedgerKgs==null)"Нет данных платёжного реестра" else "За выбранный период платежей не найдено")))
            add(emptyList())
            add(listOf(text("Выручка по сессиям, KGS"),financial(summary.revenueKgs)))
            add(listOf(text("Поступления по реестру минус возвраты, KGS"),financial(summary.paymentLedgerKgs)))
            add(listOf(text("Разница сверки, KGS"),financial(summary.reconciliationDifferenceKgs)))
            add(listOf(text("Правило сверки"),text("Сессии отобраны по дате окончания (активные — начала), платёжный реестр — по дате поступления; расхождения видны отдельно. Платформа и налог рассчитаны с оплаты минус возвраты, эквайринг — с оплаты. Неизвестный реестр не подменяется нулём.")))
        }
        val expenseRows=buildList<List<Cell>> {
            add(header("ID расхода","Дата","Станция","ID станции","Тип","Сумма, KGS","Оплачено","Номер документа","Комментарий"))
            report.expenses.forEach{expense->add(listOf(text(expense.id),text(expense.date.toString()),text(stationName(expense.stationId)),text(expense.stationId),text(expense.kind.label),number(expense.amountKgs),text(if(expense.paid)"Да · ДЕМО" else "Нет"),text(expense.documentNumber),text(expense.note)))}
            if(report.expenses.isEmpty())add(listOf(text(if(!report.financeVisible)"Нет права просмотра финансов" else "За выбранный период расходы не внесены")))
            add(emptyList())
            add(listOf(text("Квитанции за полные месяцы"),text("Суммы показаны за полный месяц каждой точки; частичные периоды и фильтры по отдельным сессиям не получают долю счёта.")))
            add(header("ID счёта","Месяц","Станция","ID станции","Электричество факт, KGS","Номер документа","Дата документа","Оплачено","Внешний счётчик, кВт·ч","Аренда факт, KGS","Комментарий","Автор","Изменено (Asia/Bishkek)","Вложение: имя","Вложение: тип","Вложение: размер, байт","Вложение: локальная ссылка"))
            report.monthBills.forEach{bill->
                add(listOf(text(bill.id),text(bill.month.toString()),text(stationName(bill.stationId)),text(bill.stationId),number(bill.electricityKgs),text(bill.billNumber),text(bill.billDate.toString()),text(if(bill.paid)"Да · ДЕМО" else "Нет"),bill.meterEnergyKwh?.let{number(it,3)}?:text("Нет данных"),money(bill.actualRentKgs),text(bill.comment),text(bill.authorId),text(time(bill.updatedAt)),text(bill.attachment?.fileName?:"Нет вложения"),text(bill.attachment?.mediaType?:""),bill.attachment?.byteSize?.let{number(it.toBigDecimal(),0)}?:text("Нет данных"),text(bill.attachment?.localReference?:"")))
            }
            if(report.monthBills.isEmpty())add(listOf(text(if(!report.financeVisible)"Нет права просмотра финансов" else "Квитанций за полные месяцы выбранного периода нет")))
            add(emptyList())
            add(listOf(text("Учёт выплат"),text("Выплата партнёру — движение денег. По умолчанию она не вычитается повторно из прибыли поверх договорной доли; набор выбранных расходов сохранён в сводке.")))
        }
        writeWorkbook(listOf(
            Sheet("Сводка",summaryRows,listOf(48,48,24,24,28,28,24,24,24,26,26,24,26,24,24,28,28,26,26,28,55,65)),
            Sheet("Сессии",sessionRows,listOf(42,32,32,24,32,30,28,28,23,25,23,23,23,23,26,25,25,25,26,28,26,24,55,36,32,25)),
            Sheet("По станциям",stationRows,listOf(34,30,30,30,24,24,24,55,25,30,30,24,26,18,18,24,26,25,24,24,34,34,50,25,18,30,38,36)),
            Sheet("Платежи",paymentRows,listOf(45,34,34,30,45,25,25,28,28,34,28)),
            Sheet("Расходы",expenseRows,listOf(38,24,34,30,32,32,25,24,30,28,70,30,34,35,35,28,55))
        ),output)
    }

    private fun validateScope(report:NativeNetworkReport) {
        if(!report.exportAllowed || report.account.role in setOf(PartnerRole.DRIVER,PartnerRole.EMPLOYEE))throw SecurityException("Нет права экспорта отчёта")
        val filter=report.filter
        val ids=report.stations.map{it.station.id}.toSet()
        if(ids.size!=report.stations.size || ids!=report.scopedStationIds || report.stations.any{row->
            val station=row.station
            (filter.stationId!=null && station.id!=filter.stationId) ||
            (filter.organizationId!=null && station.organizationId!=filter.organizationId) ||
            (filter.ownerId!=null && station.ownerId!=filter.ownerId) ||
            (filter.groupId!=null && station.groupId!=filter.groupId) ||
            (filter.locationId!=null && station.locationId!=filter.locationId) ||
            (report.account.role==PartnerRole.OWNER && station.ownerId!=report.account.id)
        } || report.sessions.any{it.stationId !in ids || it.accountingAt !in filter.period} ||
            report.payments.any{it.stationId !in ids || it.at !in filter.period} ||
            report.expenses.any{it.stationId !in ids || it.date<filter.period.from || it.date>filter.period.to} ||
            report.monthlyFinance.any{!ids.containsAll(it.stationIds)} ||
            report.monthBills.any{it.stationId !in ids || it.month.atDay(1)<filter.period.from || it.month.atEndOfMonth()>filter.period.to})throw SecurityException("Отчёт содержит недоступную станцию или дату")
        if(!report.financeVisible && (report.monthBills.isNotEmpty() || report.monthlyFinance.isNotEmpty() || report.payments.isNotEmpty() || report.expenses.isNotEmpty() || report.stations.any{it.revenueKgs!=null} || report.sessions.any{it.rates!=null || it.chargedKgs!=null || it.paidKgs!=null || it.refundKgs!=null} || listOf(report.summary.chargedKgs,report.summary.paidKgs,report.summary.refundKgs,report.summary.revenueKgs,report.summary.unpaidKgs,report.summary.averageTariffKgs,report.summary.paymentLedgerKgs,report.summary.reconciliationDifferenceKgs).any{it!=null}))throw SecurityException("Финансы недоступны этой роли")
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
    private val styles="""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="3"><numFmt numFmtId="164" formatCode="#,##0.00 &quot;KGS&quot;"/><numFmt numFmtId="165" formatCode="0.000"/><numFmt numFmtId="166" formatCode="0.00"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF17644E"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="5"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="166" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
}
