package kg.qubat.ev

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class PartnerDomainTest {
    private val today=LocalDate.of(2026,11,1)
    private val now=today.atTime(20,0).atZone(PARTNER_ZONE).toInstant()
    private val repo=PartnerDemoRepository(today,now)
    private val ownerA=repo.accounts.first {it.id=="demo-owner-a"}
    private val ownerB=repo.accounts.first {it.id=="demo-owner-b"}
    private val admin=repo.accounts.first {it.role==PartnerRole.ADMIN}
    private val filter=PartnerFilter(PartnerPeriod.sevenDays(today))
    private fun decimal(expected:String,actual:BigDecimal)=assertEquals(0,BigDecimal(expected).compareTo(actual))

    @Test fun ownersHaveSeparateAssignedStationsAndAdminSeesBoth() {
        val a=repo.stations(ownerA);val b=repo.stations(ownerB)
        assertEquals(3,a.size);assertEquals(2,b.size);assertEquals(5,repo.stations(admin).size)
        assertTrue(a.map {it.id}.toSet().intersect(b.map {it.id}.toSet()).isEmpty())
        val report=repo.report(ownerA,filter)
        assertTrue(report.stations.all {it.station.ownerId==ownerA.id})
        assertTrue(report.sessions.all {row->a.any {it.id==row.stationId}})
        assertTrue(report.payouts.all {row->a.any {it.id==row.stationId}})
    }
    @Test fun directForeignIdAndForgedRoleAndDriverAreRejected() {
        val foreign=repo.stations(ownerB).first().id
        expectDenied {repo.station(ownerA,foreign)}
        expectDenied {repo.report(ownerA,filter.copy(stationId=foreign))}
        expectDenied {repo.sessionsPage(ownerA,filter.copy(stationId=foreign),0)}
        expectDenied {repo.stations(ownerA.copy(role=PartnerRole.ADMIN))}
        expectDenied {repo.report(repo.accounts.first {it.role==PartnerRole.DRIVER},filter)}
    }
    @Test fun datesUseBishkekAndIncludeTheEntireLastDay() {
        val day=PartnerPeriod.today(today)
        assertTrue(today.atTime(0,0).atZone(PARTNER_ZONE).toInstant() in day)
        assertTrue(today.atTime(23,59,59).atZone(PARTNER_ZONE).toInstant() in day)
        assertFalse(today.plusDays(1).atStartOfDay(PARTNER_ZONE).toInstant() in day)
        assertFalse(today.minusDays(1).atTime(23,59,59).atZone(PARTNER_ZONE).toInstant() in day)
        assertEquals("2026-10-31T18:00:00Z",day.start.toString())
        val report=repo.report(ownerA,filter.copy(period=day))
        assertEquals(9,report.sessions.size)
        assertEquals(6,report.totals.completedCount)
        assertEquals(3,report.totals.unfinishedCount)
        assertEquals(0,report.totals.cancelledCount)
    }
    @Test fun exactGrossDeductionsAndPayableNeverRoundIntermediateValues() {
        val report=repo.report(ownerA,filter)
        val completed=report.sessions.filter {it.status==PartnerSessionStatus.COMPLETED}
        val gross=completed.fold(BigDecimal.ZERO) {sum,row->sum+row.energyKwh*row.tariffKgs}
        val commissions=completed.fold(BigDecimal.ZERO) {sum,row->sum+row.commissionKgs}
        val refunds=completed.fold(BigDecimal.ZERO) {sum,row->sum+row.refundKgs}
        assertTrue(refunds.signum()>0)
        assertEquals(0,gross.compareTo(report.totals.revenueKgs))
        assertEquals(0,(commissions+refunds).compareTo(report.totals.deductionsKgs))
        assertEquals(0,(gross-commissions-refunds).compareTo(report.totals.payableKgs))
        assertTrue(completed.any {it.commissionKgs.scale()>2})
        assertTrue(repo.stations(ownerA).map {it.commissionRate}.distinct().size>1)
        assertTrue(completed.filter {it.refundKgs.signum()>0}.all {it.refundReason!=null && it.refundConfirmedAt!=null && it.refundResponsible!=null})
    }
    @Test fun carryAndActualConfirmedTransfersReconcileAcrossAdjacentPeriods() {
        val first=repo.report(ownerA,filter.copy(period=PartnerPeriod(today.minusDays(6),today.minusDays(1))))
        val last=repo.report(ownerA,filter.copy(period=PartnerPeriod.today(today)))
        val combined=repo.report(ownerA,filter)
        assertEquals(0,first.totals.balanceKgs!!.compareTo(last.totals.openingBalanceKgs))
        assertEquals(0,last.totals.balanceKgs!!.compareTo(combined.totals.balanceKgs))
        assertEquals(0,(combined.totals.openingBalanceKgs!!+combined.totals.payableKgs-combined.totals.paidKgs!!).compareTo(combined.totals.balanceKgs))
        val confirmed=combined.payouts.filter {it.confirmed}.fold(BigDecimal.ZERO) {sum,row->sum+row.amountKgs}
        assertEquals(0,confirmed.compareTo(combined.totals.paidKgs))
        assertTrue(last.payouts.isNotEmpty())
        assertTrue(last.payouts.none {it.confirmed})
        decimal("0",last.totals.paidKgs!!)
    }
    @Test fun missingPayoutLedgerStaysUnknownRatherThanZero() {
        val report=repo.report(ownerB,filter)
        assertTrue(report.totals.revenueKgs.signum()>0)
        assertNull(report.totals.paidKgs)
        assertNull(report.totals.openingBalanceKgs)
        assertNull(report.totals.balanceKgs)
        assertNull(repo.report(ownerB,filter.copy(query="NO-MATCH")).totals.paidKgs)
        val combined=repo.report(admin,filter)
        assertNull(combined.totals.paidKgs)
        assertNull(combined.totals.balanceKgs)
        assertTrue(combined.payouts.isNotEmpty()) // Some known records cannot imply a complete ledger.
    }
    @Test fun stationAndSearchFiltersAffectSnapshotAndStablePagesTogether() {
        val station=repo.stations(ownerA).first()
        val selected=filter.copy(stationId=station.id,sort=PartnerSort.OLDEST)
        val report=repo.report(ownerA,selected)
        assertEquals(1,report.stations.size)
        assertEquals(21,report.sessions.size)
        assertTrue(report.sessions.all {it.stationId==station.id})
        val first=repo.sessionsPage(ownerA,selected,0,10)
        val second=repo.sessionsPage(ownerA,selected,1,10)
        val third=repo.sessionsPage(ownerA,selected,2,10)
        assertEquals(report.sessions,first.items+second.items+third.items)
        assertEquals(21,first.totalCount)
        assertTrue(first.hasNext);assertFalse(third.hasNext)
        assertTrue(first.items.map {it.id}.intersect(second.items.map {it.id}.toSet()).isEmpty())
        val searched=repo.report(ownerA,filter.copy(query="Каракол"))
        assertEquals(1,searched.stations.size)
        assertTrue(searched.sessions.all {it.stationId==searched.stations.single().station.id})
        val empty=repo.report(ownerA,filter.copy(query="NOT-A-STATION"))
        assertTrue(empty.sessions.isEmpty());assertTrue(empty.stations.isEmpty())
        decimal("0",empty.totals.revenueKgs)
    }
    @Test fun completedCancelledAndUnfinishedAreSeparateAndProvenanceIsDemo() {
        val report=repo.report(ownerA,filter)
        assertEquals(42,report.totals.completedCount)
        assertEquals(18,report.totals.cancelledCount)
        assertEquals(3,report.totals.unfinishedCount)
        assertEquals(63,report.sessions.size)
        assertTrue(report.sessions.all {it.source=="DEMO_QUBAT" && it.updatedAt==now})
        assertTrue(report.sessions.filter {it.status!=PartnerSessionStatus.COMPLETED}.all {it.revenueKgs.signum()==0 && it.paymentStatus==PartnerPaymentStatus.UNKNOWN})
        val future=repo.report(ownerA,filter.copy(period=PartnerPeriod(today.plusDays(1),today.plusDays(3))))
        assertTrue(future.sessions.isEmpty())
        decimal("0",future.totals.payableKgs)
    }
    private fun expectDenied(block:()->Unit) {
        try {block();fail("Expected station/role access to be rejected")} catch(expected:SecurityException) {assertTrue(expected.message.orEmpty().isNotBlank())}
    }
}
