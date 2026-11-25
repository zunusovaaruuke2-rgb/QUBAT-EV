package kg.qubat.ev

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

class PartnerExcelTest {
    private val day=LocalDate.of(2026,11,1)
    private val repo=PartnerDemoRepository(day,Instant.parse("2026-11-01T15:00:00Z"))
    private fun report(owner:String="demo-owner-a")=repo.report(repo.accounts.first {it.id==owner},PartnerFilter(PartnerPeriod.sevenDays(day)))
    private fun archive(report:PartnerReport):Map<String,ByteArray> {
        val buffer=ByteArrayOutputStream()
        PartnerExcel.write(report,buffer)
        val entries=linkedMapOf<String,ByteArray>()
        ZipInputStream(ByteArrayInputStream(buffer.toByteArray())).use {zip->
            while(true) {
                val entry=zip.nextEntry ?: break
                entries[entry.name]=zip.readBytes()
                zip.closeEntry()
            }
        }
        return entries
    }
    private fun xml(bytes:ByteArray):Document {
        val factory=DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware=true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl",true)
            setFeature("http://xml.org/sax/features/external-general-entities",false)
            setFeature("http://xml.org/sax/features/external-parameter-entities",false)
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }
    private fun rows(document:Document):List<List<Element>> {
        val rows=document.getElementsByTagNameNS("*","row")
        return (0 until rows.length).map {i->
            val cells=(rows.item(i) as Element).getElementsByTagNameNS("*","c")
            (0 until cells.length).map {cells.item(it) as Element}
        }
    }
    private fun text(cell:Element)=cell.textContent
    private fun summaryCell(document:Document,label:String):Element=rows(document).first {it.isNotEmpty() && text(it.first())==label}[1]
    private fun assertNumber(expected:BigDecimal,cell:Element) {
        assertNotEquals("inlineStr",cell.getAttribute("t"))
        assertEquals(0,expected.compareTo(BigDecimal(text(cell))))
    }

    @Test fun `workbook has three named worksheets valid XML and all numeric session amounts`() {
        val report=report()
        val entries=archive(report)
        assertTrue(entries.keys.containsAll(listOf("[Content_Types].xml","_rels/.rels","xl/workbook.xml","xl/_rels/workbook.xml.rels","xl/styles.xml","xl/worksheets/sheet1.xml","xl/worksheets/sheet2.xml","xl/worksheets/sheet3.xml")))
        entries.values.forEach {xml(it)}
        val workbook=xml(entries.getValue("xl/workbook.xml"))
        val sheets=workbook.getElementsByTagNameNS("*","sheet")
        assertEquals(listOf("Сводка","Зарядки","Выплаты"),(0 until sheets.length).map {(sheets.item(it) as Element).getAttribute("name")})
        val sessionRows=rows(xml(entries.getValue("xl/worksheets/sheet2.xml"))).drop(1)
        assertEquals(report.sessions.size,sessionRows.size)
        report.sessions.forEachIndexed {i,session->
            val row=sessionRows[i]
            assertEquals(session.id,text(row[0]))
            assertNumber(session.energyKwh,row[7])
            assertNumber(session.tariffKgs,row[8])
            assertNumber(session.revenueKgs,row[9])
            assertTrue(text(row[4]).endsWith("+06:00"))
            assertFalse(text(row[17]).isBlank())
        }
        assertFalse(entries.keys.any {it.contains("externalLink",true) || it.contains("vba",true)})
    }
    @Test fun `summary uses exactly screen snapshot totals and includes carry`() {
        val report=report()
        val summary=xml(archive(report).getValue("xl/worksheets/sheet1.xml"))
        assertNumber(report.totals.energyKwh,summaryCell(summary,"Продано электроэнергии, кВт·ч"))
        assertNumber(report.totals.revenueKgs,summaryCell(summary,"Выручка до удержаний, KGS"))
        assertNumber(report.totals.deductionsKgs,summaryCell(summary,"Удержания и подтверждённые возвраты, KGS"))
        assertNumber(report.totals.payableKgs,summaryCell(summary,"К перечислению за период, KGS"))
        assertNumber(report.totals.openingBalanceKgs!!,summaryCell(summary,"Перенос остатка до начала периода, KGS"))
        assertNumber(report.totals.paidKgs!!,summaryCell(summary,"Подтверждённые выплаты в периоде, KGS"))
        assertNumber(report.totals.balanceKgs!!,summaryCell(summary,"Остаток с учётом переноса, KGS"))
        val sessionDoc=xml(archive(report).getValue("xl/worksheets/sheet2.xml"))
        val completedTotal=rows(sessionDoc).drop(1).filter {text(it[12])==PartnerSessionStatus.COMPLETED.label}.fold(BigDecimal.ZERO) {sum,row->sum+BigDecimal(text(row[9]))}
        assertEquals(0,report.totals.revenueKgs.compareTo(completedTotal))
    }
    @Test fun `unknown payout ledger is explicitly missing not numeric zero`() {
        val report=report("demo-owner-b")
        val summary=xml(archive(report).getValue("xl/worksheets/sheet1.xml"))
        listOf("Перенос остатка до начала периода, KGS","Подтверждённые выплаты в периоде, KGS","Остаток с учётом переноса, KGS").forEach {label->
            val cell=summaryCell(summary,label)
            assertEquals("inlineStr",cell.getAttribute("t"))
            assertEquals("Нет данных",text(cell))
        }
    }
    @Test fun `filename inclusive dates match filters and export ignores display pagination`() {
        val report=report()
        assertEquals("QubatEV_Report_20261026_20261101.xlsx",PartnerExcel.fileName(report))
        assertTrue(report.sessions.size>20)
        val sessions=rows(xml(archive(report).getValue("xl/worksheets/sheet2.xml")))
        assertEquals(report.sessions.size+1,sessions.size)
    }
    @Test fun `labels are safe literal strings even when they resemble formulas`() {
        val dangerous="=HYPERLINK(\"https://example.invalid\",\"<тест>&\")\u0001"
        val report=report().let {it.copy(account=it.account.copy(name=dangerous))}
        val entries=archive(report)
        val summary=xml(entries.getValue("xl/worksheets/sheet1.xml"))
        val cell=summaryCell(summary,"Партнёр")
        assertEquals("inlineStr",cell.getAttribute("t"))
        assertEquals(dangerous.dropLast(1),text(cell))
        entries.values.forEach {assertEquals(0,xml(it).getElementsByTagNameNS("*","f").length)}
    }
    @Test fun `export checks owner scope and refuses forged foreign station rows`() {
        val ownerA=report()
        val ownerB=report("demo-owner-b")
        try {
            archive(ownerA.copy(stations=ownerA.stations+ownerB.stations.first()))
            fail("Foreign station exported")
        } catch(expected:SecurityException) {assertTrue(expected.message!!.contains("станц"))}
        try {
            archive(ownerA.copy(sessions=ownerA.sessions+ownerB.sessions.first()))
            fail("Foreign session exported")
        } catch(expected:SecurityException) {assertTrue(expected.message!!.contains("станц"))}
        try {
            archive(ownerA.copy(account=repo.accounts.first {it.role==PartnerRole.DRIVER}))
            fail("Driver exported owner report")
        } catch(expected:SecurityException) {assertNotNull(expected.message)}
    }
    @Test fun `caller retains output stream ownership`() {
        var closed=false
        val output=object:ByteArrayOutputStream() {override fun close() {closed=true;super.close()}}
        PartnerExcel.write(report(),output)
        assertFalse(closed)
        assertTrue(output.size()>1000)
    }
}
