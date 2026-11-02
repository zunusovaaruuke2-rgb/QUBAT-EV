package kg.qubat.ev

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Clock
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

class NativePartnerExcelTest {
    private val day=LocalDate.of(2026,11,1)
    private fun repo(id:String="demo-owner-a",state:NativePartnerState=NativePartnerRepository.seed(day))=
        NativePartnerRepository(NativePartnerRepository.accounts.first{it.id==id},state,Clock.fixed(day.atTime(12,0).atZone(PARTNER_ZONE).toInstant(),PARTNER_ZONE))
    private fun report(id:String="demo-owner-a")=repo(id).exportReport(NativeReportFilter(PartnerPeriod.sevenDays(day)))
    private fun archive(report:NativeNetworkReport):Map<String,ByteArray> {
        val output=ByteArrayOutputStream()
        NativePartnerExcel.write(report,output)
        val entries=linkedMapOf<String,ByteArray>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use{zip->
            while(true) {
                val entry=zip.nextEntry?:break
                entries[entry.name]=zip.readBytes()
                zip.closeEntry()
            }
        }
        return entries
    }
    private fun xml(bytes:ByteArray):Document {
        val factory=DocumentBuilderFactory.newInstance().apply{
            isNamespaceAware=true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl",true)
            setFeature("http://xml.org/sax/features/external-general-entities",false)
            setFeature("http://xml.org/sax/features/external-parameter-entities",false)
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }
    private fun rows(document:Document):List<List<Element>> {
        val nodes=document.getElementsByTagNameNS("*","row")
        return (0 until nodes.length).map{i->
            val cells=(nodes.item(i) as Element).getElementsByTagNameNS("*","c")
            (0 until cells.length).map{cells.item(it) as Element}
        }
    }
    private fun value(document:Document,label:String):Element=rows(document).first{it.isNotEmpty() && it.first().textContent==label}[1]
    private fun number(expected:BigDecimal,cell:Element) {
        assertNotEquals("inlineStr",cell.getAttribute("t"))
        assertEquals(0,expected.compareTo(cell.textContent.toBigDecimal()))
    }
    private fun changed(
        source:NativeNetworkReport,account:PartnerAccount=source.account,stations:List<NativeStationReport> = source.stations,
        sessions:List<NativeSession> = source.sessions,exportAllowed:Boolean=source.exportAllowed
    )=NativeNetworkReport(account,source.filter,source.generatedAt,stations,sessions,source.payments,source.expenses,source.summary,source.monthlyFinance,source.scopedStationIds,exportAllowed,source.financeVisible,source.monthBills)

    @Test fun `five sheets are valid OOXML and every session has exact numeric amounts`() {
        val report=report()
        val entries=archive(report)
        assertTrue(entries.keys.containsAll(listOf("[Content_Types].xml","_rels/.rels","xl/workbook.xml","xl/_rels/workbook.xml.rels","xl/styles.xml")+(1..5).map{"xl/worksheets/sheet$it.xml"}))
        entries.values.forEach{xml(it)}
        val sheets=xml(entries.getValue("xl/workbook.xml")).getElementsByTagNameNS("*","sheet")
        assertEquals(listOf("Сводка","Сессии","По станциям","Платежи","Расходы"),(0 until sheets.length).map{(sheets.item(it) as Element).getAttribute("name")})
        val sessionRows=rows(xml(entries.getValue("xl/worksheets/sheet2.xml"))).drop(1)
        assertTrue(report.sessions.size>20)
        assertEquals(report.sessions.size,sessionRows.size)
        report.sessions.forEachIndexed{i,session->
            val row=sessionRows[i]
            assertEquals(session.id,row[0].textContent)
            number(session.energyKwh,row[8])
            session.chargedKgs?.let{number(it,row[10])}
            session.paidKgs?.let{number(it,row[11])}
            session.refundKgs?.let{number(it,row[12])}
            session.platformFeeKgs?.let{number(it,row[15])}
            session.acquiringFeeKgs?.let{number(it,row[16])}
            session.taxKgs?.let{number(it,row[17])}
            assertTrue(row[1].textContent.endsWith("+06:00"))
        }
        assertFalse(entries.keys.any{it.contains("externalLink",true)||it.contains("vba",true)})
    }
    @Test fun `summary values and filter metadata match the report snapshot`() {
        val report=report()
        val summary=xml(archive(report).getValue("xl/worksheets/sheet1.xml"))
        number(report.summary.energyKwh,value(summary,"Энергия, кВт·ч"))
        number(report.summary.chargedKgs!!,value(summary,"Начислено, KGS"))
        val revenue=value(summary,"Выручка: оплачено минус возвраты, KGS")
        report.summary.revenueKgs?.let{number(it,revenue)}?:assertEquals("Нет данных",revenue.textContent)
        assertEquals(report.filter.period.from.toString(),value(summary,"С даты (включительно)").textContent)
        assertEquals(report.filter.period.to.toString(),value(summary,"По дату (включительно)").textContent)
        listOf("Организация","Владелец","Группа","Локация","Станция","Разъём","Клиент","Тип операции","Статус оплаты","Статус сессии").forEach{assertEquals("Все доступные",value(summary,"Фильтр: $it").textContent)}
        assertTrue(value(summary,"Режим").textContent.contains("ДЕМО"))
        assertEquals("QubatEV_Partner_20261026_20261101.xlsx",NativePartnerExcel.fileName(report))
    }
    @Test fun `exported fee rows sum to the same rounded monthly finance totals`() {
        val report=repo().exportReport(NativeReportFilter(PartnerPeriod(day.withDayOfMonth(1),day),paymentStatus=NativePaymentStatus.PAID))
        val sessionRows=rows(xml(archive(report).getValue("xl/worksheets/sheet2.xml"))).drop(1)
        val monthly=report.monthlyFinance.single()
        listOf(15 to monthly.platformKgs,16 to monthly.acquiringKgs,17 to monthly.taxKgs).forEach{(column,expected)->
            val total=sessionRows.filter{it[18].textContent==NativeSessionStatus.COMPLETED.label}.fold(BigDecimal.ZERO){sum,row->sum+row[column].textContent.toBigDecimal()}
            assertEquals(0,expected!!.compareTo(total))
        }
        val stations=rows(xml(archive(report).getValue("xl/worksheets/sheet3.xml"))).drop(1)
        val power=stations.fold(BigDecimal.ZERO){sum,row->sum+row[16].textContent.toBigDecimal()}
        assertEquals(0,report.summary.currentPowerKw.compareTo(power))
    }

    @Test fun `unknown payment ledger exports explicit missing values`() {
        val report=report("demo-owner-b")
        assertNull(report.summary.paymentLedgerKgs)
        val summary=xml(archive(report).getValue("xl/worksheets/sheet1.xml"))
        listOf("Поступления по платёжному реестру, KGS","Разница сверки, KGS").forEach{label->
            val cell=value(summary,label)
            assertEquals("inlineStr",cell.getAttribute("t"))
            assertEquals("Нет данных",cell.textContent)
        }
    }
    @Test fun `observer receives reports without any financial amount`() {
        val report=report("demo-observer")
        assertFalse(report.financeVisible)
        val archive=archive(report)
        val summary=xml(archive.getValue("xl/worksheets/sheet1.xml"))
        assertEquals("Нет права просмотра финансов",value(summary,"Начислено, KGS").textContent)
        assertEquals("Нет права просмотра финансов",value(summary,"Выручка: оплачено минус возвраты, KGS").textContent)
        val sessions=rows(xml(archive.getValue("xl/worksheets/sheet2.xml"))).drop(1)
        sessions.forEach{row->(9..13).forEach{assertEquals("Нет права просмотра финансов",row[it].textContent)}}
        assertTrue(report.payments.isEmpty())
        assertTrue(report.expenses.isEmpty())
    }
    @Test fun `foreign rows and driver exports are rejected before output`() {
        val own=report()
        val foreign=report("demo-owner-b")
        listOf(
            changed(own,stations=own.stations+foreign.stations.first()),
            changed(own,sessions=own.sessions+foreign.sessions.first()),
            changed(own,account=NativePartnerRepository.accounts.first{it.role==PartnerRole.DRIVER}),
            changed(own,exportAllowed=false)
        ).forEach{forged->
            val output=ByteArrayOutputStream()
            try {NativePartnerExcel.write(forged,output);fail("Forged export succeeded")}catch(expected:SecurityException){assertEquals(0,output.size())}
        }
    }
    @Test fun `locally created scoped station exports and formula labels stay literal`() {
        val repo=repo()
        val original=repo.stations().first()
        val title="=HYPERLINK(\"https://example.invalid\",\"<тест>&\")\u0001"
        val newStation=original.copy(id="created-demo",title=title,serialNumber="CREATED-123",ocppId="CREATED-OCPP",ports=original.ports.mapIndexed{i,port->port.copy(id="created-port-$i")})
        repo.createStation(newStation,"Проверка локального создания")
        val report=repo.exportReport(NativeReportFilter(PartnerPeriod.sevenDays(day)))
        val entries=archive(report)
        val sheet=xml(entries.getValue("xl/worksheets/sheet3.xml"))
        val row=rows(sheet).first{it.size>1 && it[1].textContent==newStation.id}
        assertEquals("inlineStr",row[0].getAttribute("t"))
        assertEquals(title.dropLast(1),row[0].textContent)
        entries.values.forEach{assertEquals(0,xml(it).getElementsByTagNameNS("*","f").length)}
    }
    @Test fun `full month exports factual invoice metadata and partial period excludes it`() {
        val repo=repo()
        val previous=java.time.YearMonth.from(day).minusMonths(1)
        val report=repo.exportReport(NativeReportFilter(PartnerPeriod(previous.atDay(1),previous.atEndOfMonth())))
        assertTrue(report.monthBills.isNotEmpty())
        val doc=xml(archive(report).getValue("xl/worksheets/sheet5.xml"))
        report.monthBills.forEach{bill->
            val row=rows(doc).first{it.isNotEmpty() && it[0].textContent==bill.id}
            number(bill.electricityKgs,row[4])
            assertEquals(bill.billNumber,row[5].textContent)
            assertEquals(bill.billDate.toString(),row[6].textContent)
            assertEquals(if(bill.paid)"Да · ДЕМО" else "Нет",row[7].textContent)
            bill.meterEnergyKwh?.let{number(it,row[8])}
        }
        val partial=repo.exportReport(NativeReportFilter(PartnerPeriod(previous.atDay(2),previous.atEndOfMonth())))
        assertTrue(partial.monthBills.isEmpty())
        assertFalse(archive(partial).getValue("xl/worksheets/sheet5.xml").toString(Charsets.UTF_8).contains(report.monthBills.first().billNumber))
    }

    @Test fun `caller retains output ownership`() {
        var closed=false
        val output=object:ByteArrayOutputStream(){override fun close(){closed=true;super.close()}}
        NativePartnerExcel.write(report(),output)
        assertFalse(closed)
        assertTrue(output.size()>1000)
    }
}
