package kg.qubat.ev

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

val PARTNER_ZONE:ZoneId = ZoneId.of("Asia/Bishkek")
private val ZERO=BigDecimal.ZERO

enum class PartnerRole(val label:String) {DRIVER("Водитель"),OWNER("Владелец сети"),ADMIN("Суперадминистратор QUBAT"),EMPLOYEE("Сотрудник партнёра"),ACCOUNTANT("Бухгалтер"),OBSERVER("Наблюдатель")}
data class PartnerAccount(val id:String,val name:String,val role:PartnerRole):java.io.Serializable
data class PartnerPeriod(val from:LocalDate,val to:LocalDate):java.io.Serializable {
    init {require(!to.isBefore(from)) {"Дата «По» должна быть не раньше даты «С»"}}
    val start:Instant get()=from.atStartOfDay(PARTNER_ZONE).toInstant()
    val endExclusive:Instant get()=to.plusDays(1).atStartOfDay(PARTNER_ZONE).toInstant()
    operator fun contains(time:Instant):Boolean=time>=start && time<endExclusive
    companion object {
        fun today(today:LocalDate=LocalDate.now(PARTNER_ZONE))=PartnerPeriod(today,today)
        fun sevenDays(today:LocalDate=LocalDate.now(PARTNER_ZONE))=PartnerPeriod(today.minusDays(6),today)
        fun month(today:LocalDate=LocalDate.now(PARTNER_ZONE))=PartnerPeriod(today.withDayOfMonth(1),today)
    }
}
enum class PartnerSort(val label:String) {NEWEST("Сначала новые"),OLDEST("Сначала старые"),ENERGY_DESC("По энергии"),REVENUE_DESC("По выручке")}
data class PartnerFilter(val period:PartnerPeriod,val stationId:String?=null,val query:String="",val sort:PartnerSort=PartnerSort.NEWEST)
enum class PartnerSessionStatus(val label:String) {COMPLETED("Завершена"),CANCELLED("Отменена"),IN_PROGRESS("Не завершена")}
enum class PartnerPaymentStatus(val label:String) {PAID("Оплачено · демо"),UNKNOWN("Нет данных"),PARTIALLY_REFUNDED("Частичный возврат · демо")}
data class PartnerStation(
    val id:String,val ownerId:String,val title:String,val address:String,val powerKw:Int,val connectorCount:Int,
    val tariffKgs:BigDecimal,val commissionRate:BigDecimal,val contractId:String,val connectionStatus:String?,
    val updatedAt:Instant,val source:String="DEMO_QUBAT",val isCurrent:Boolean=true
)
data class PartnerSession(
    val id:String,val stationId:String,val stationTitle:String,val port:String,val startedAt:Instant,val endedAt:Instant?,
    val energyKwh:BigDecimal,val tariffKgs:BigDecimal,val revenueKgs:BigDecimal,val commissionKgs:BigDecimal,
    val refundKgs:BigDecimal,val refundReason:String?,val refundConfirmedAt:Instant?,val refundResponsible:String?,
    val status:PartnerSessionStatus,val paymentStatus:PartnerPaymentStatus,val updatedAt:Instant,
    val source:String="DEMO_QUBAT",val isCurrent:Boolean=true
) {
    val accountingAt:Instant get()=endedAt ?: startedAt
    val payableKgs:BigDecimal get()=if(status==PartnerSessionStatus.COMPLETED)revenueKgs-commissionKgs-refundKgs else ZERO
    val durationSeconds:Long? get()=endedAt?.let {java.time.Duration.between(startedAt,it).seconds}
}
data class PartnerPayout(
    val id:String,val stationId:String,val stationTitle:String,val paidAt:Instant,val amountKgs:BigDecimal,
    val confirmed:Boolean,val period:PartnerPeriod,val note:String,val updatedAt:Instant,
    val source:String="DEMO_QUBAT",val isCurrent:Boolean=true
)
data class PartnerStationReport(val station:PartnerStation,val energyKwh:BigDecimal,val revenueKgs:BigDecimal,val completedCount:Int)
data class PartnerTotals(
    val energyKwh:BigDecimal,val completedCount:Int,val cancelledCount:Int,val unfinishedCount:Int,
    val revenueKgs:BigDecimal,val deductionsKgs:BigDecimal,val payableKgs:BigDecimal,
    val openingBalanceKgs:BigDecimal?,val paidKgs:BigDecimal?,val balanceKgs:BigDecimal?
)
data class PartnerReport(
    val account:PartnerAccount,val filter:PartnerFilter,val generatedAt:Instant,
    val stations:List<PartnerStationReport>,val sessions:List<PartnerSession>,val payouts:List<PartnerPayout>,val totals:PartnerTotals
)
data class PartnerPage<T>(val items:List<T>,val page:Int,val pageSize:Int,val totalCount:Int) {
    val hasNext:Boolean get()=(page.toLong()+1)*pageSize<totalCount
}

/** Local demo boundary only. Production must enforce the same scope on its server. */
class PartnerDemoRepository(
    val today:LocalDate=LocalDate.now(PARTNER_ZONE),
    private val now:Instant=Instant.now()
) {
    val accounts:List<PartnerAccount> = listOf(
        PartnerAccount("demo-driver","Демо-водитель",PartnerRole.DRIVER),
        PartnerAccount("demo-owner-a","ОсОО «Кубат Север» · ДЕМО",PartnerRole.OWNER),
        PartnerAccount("demo-owner-b","ОсОО «Кубат Юг» · ДЕМО",PartnerRole.OWNER),
        PartnerAccount("demo-admin","Qubat EV · ДЕМО администратор",PartnerRole.ADMIN))
    private val allStations:List<PartnerStation> = listOf(
        stationFixture("a-1","demo-owner-a","Север · ДЕМО 01","Бишкек · условный адрес, ул. Учебная, 1",8,"0.025"),
        stationFixture("a-2","demo-owner-a","Иссык-Куль · ДЕМО 02","Каракол · условный адрес, ул. Учебная, 2",16,"0.025"),
        stationFixture("a-3","demo-owner-a","Чуй · ДЕМО 03","Токмок · условный адрес, ул. Учебная, 3",8,"0.03"),
        stationFixture("b-1","demo-owner-b","Юг · ДЕМО 04","Ош · условный адрес, ул. Учебная, 4",16,"0.04"),
        stationFixture("b-2","demo-owner-b","Долина · ДЕМО 05","Джалал-Абад · условный адрес, ул. Учебная, 5",8,"0.045"))
    private val allSessions:List<PartnerSession> = allStations.flatMapIndexed {stationIndex,station->
        (0 until 30).flatMap {daysAgo->
            val day=today.minusDays(daysAgo.toLong())
            (0..2).map {slot->
                val start=day.atTime(7+slot*4,5+stationIndex).atZone(PARTNER_ZONE).toInstant()
                val status=when {slot<2->PartnerSessionStatus.COMPLETED;daysAgo==0->PartnerSessionStatus.IN_PROGRESS;else->PartnerSessionStatus.CANCELLED}
                val end=if(status==PartnerSessionStatus.IN_PROGRESS)null else start.plusSeconds(if(slot<2)1800L+stationIndex*120 else 120)
                val energy=if(status==PartnerSessionStatus.COMPLETED) BigDecimal("18.125")+BigDecimal(stationIndex*3+daysAgo%7+slot*2) else ZERO
                val revenue=energy*station.tariffKgs
                val refund=if(status==PartnerSessionStatus.COMPLETED && slot==0 && daysAgo%11==3)BigDecimal("13.50") else ZERO
                PartnerSession("DEMO-${station.id}-${day.toString().replace("-","")}-$slot",station.id,station.title,"Аппарат ${slot+1} · GB/T ${slot%2+1}",start,end,
                    energy,station.tariffKgs,revenue,revenue*station.commissionRate,refund,
                    if(refund.signum()>0)"Демонстрационная частичная корректировка" else null,
                    if(refund.signum()>0)end?.plusSeconds(60) else null,
                    if(refund.signum()>0)"ДЕМО администратор" else null,status,
                    when {status!=PartnerSessionStatus.COMPLETED->PartnerPaymentStatus.UNKNOWN;refund.signum()>0->PartnerPaymentStatus.PARTIALLY_REFUNDED;(daysAgo+slot+stationIndex)%4==0->PartnerPaymentStatus.UNKNOWN;else->PartnerPaymentStatus.PAID},now)
            }
        }
    }.distinctBy {it.id}
    // Transfers are allocated to an individual station, never ambiguously split at report time.
    // Owner B has no connected payout ledger: absence is unknown, not a zero payment.
    private val allPayouts:List<PartnerPayout> = allSessions.filter {it.stationId.startsWith("partner-demo-a") && it.status==PartnerSessionStatus.COMPLETED}
        .mapNotNull {session->
            val coverage=session.accountingAt.atZone(PARTNER_ZONE).toLocalDate()
            val paymentDate=coverage.plusDays(1)
            if(paymentDate>today)null else PartnerPayout("PAY-${session.id}",session.stationId,session.stationTitle,
                paymentDate.atTime(12,0).atZone(PARTNER_ZONE).toInstant(),session.payableKgs*BigDecimal("0.60"),paymentDate<today,
                PartnerPeriod(coverage,coverage),"Демонстрационный перевод: 60% начисления по выбранной зарядке. Не банковское подтверждение.",now)
        }

    private fun stationFixture(key:String,owner:String,title:String,address:String,ports:Int,rate:String)=PartnerStation(
        "partner-demo-$key",owner,title,address,80,ports,BigDecimal("13.50"),BigDecimal(rate),"DEMO-CONTRACT-$key",
        if(key=="b-2")null else "Онлайн · демо",now,isCurrent=key!="b-2")
    private fun account(account:PartnerAccount):PartnerAccount {
        val known=accounts.firstOrNull {it.id==account.id && it.role==account.role} ?: throw SecurityException("Неизвестная демонстрационная учётная запись")
        if(known.role==PartnerRole.DRIVER)throw SecurityException("Кабинет доступен владельцу станции")
        return known
    }
    fun stations(account:PartnerAccount):List<PartnerStation> {
        val known=account(account)
        return allStations.filter {known.role==PartnerRole.ADMIN || it.ownerId==known.id}
    }
    fun station(account:PartnerAccount,id:String):PartnerStation = stations(account).firstOrNull {it.id==id}
        ?: throw SecurityException("Станция недоступна этой учётной записи")
    private fun filteredStations(account:PartnerAccount,filter:PartnerFilter):List<PartnerStation> {
        val allowed=stations(account)
        filter.stationId?.let {station(account,it)}
        return allowed.filter {station->(filter.stationId==null || station.id==filter.stationId) &&
            (filter.query.isBlank() || listOf(station.title,station.address,station.id).any {it.contains(filter.query.trim(),ignoreCase=true)})}
    }
    private fun selectedSessions(stations:List<PartnerStation>,filter:PartnerFilter):List<PartnerSession> {
        val ids=stations.map {it.id}.toSet()
        val values=allSessions.filter {it.stationId in ids && it.accountingAt in filter.period}
        return when(filter.sort) {
            PartnerSort.NEWEST->values.sortedWith(compareByDescending<PartnerSession> {it.startedAt}.thenBy {it.id})
            PartnerSort.OLDEST->values.sortedWith(compareBy<PartnerSession> {it.startedAt}.thenBy {it.id})
            PartnerSort.ENERGY_DESC->values.sortedWith(compareByDescending<PartnerSession> {it.energyKwh}.thenBy {it.id})
            PartnerSort.REVENUE_DESC->values.sortedWith(compareByDescending<PartnerSession> {it.revenueKgs}.thenBy {it.id})
        }
    }
    fun sessionsPage(account:PartnerAccount,filter:PartnerFilter,page:Int,pageSize:Int=20):PartnerPage<PartnerSession> {
        require(page>=0 && pageSize in 1..100)
        val items=selectedSessions(filteredStations(account,filter),filter)
        val offset=(page.toLong()*pageSize).coerceAtMost(items.size.toLong()).toInt()
        return PartnerPage(items.drop(offset).take(pageSize),page,pageSize,items.size)
    }
    fun report(account:PartnerAccount,filter:PartnerFilter):PartnerReport {
        val known=account(account)
        val allowed=filteredStations(known,filter)
        val stationIds=allowed.map {it.id}.toSet()
        val sessions=selectedSessions(allowed,filter)
        val completed=sessions.filter {it.status==PartnerSessionStatus.COMPLETED}
        val payouts=allPayouts.filter {it.stationId in stationIds && it.paidAt in filter.period}.sortedByDescending {it.paidAt}
        val ledgerScope=allowed.ifEmpty {if(filter.stationId!=null)listOf(station(known,filter.stationId)) else stations(known)}
        val payoutLedgerKnown=ledgerScope.none {it.ownerId=="demo-owner-b"}
        val paid=if(payoutLedgerKnown)payouts.filter {it.confirmed}.sumExact {it.amountKgs} else null
        val opening=if(payoutLedgerKnown) {
            allSessions.filter {it.stationId in stationIds && it.accountingAt<filter.period.start}.sumExact {it.payableKgs} -
                allPayouts.filter {it.stationId in stationIds && it.confirmed && it.paidAt<filter.period.start}.sumExact {it.amountKgs}
        } else null
        val energy=completed.sumExact {it.energyKwh}
        val revenue=completed.sumExact {it.revenueKgs}
        val deductions=completed.sumExact {it.commissionKgs+it.refundKgs}
        val payable=revenue-deductions
        val totals=PartnerTotals(energy,completed.size,sessions.count {it.status==PartnerSessionStatus.CANCELLED},sessions.count {it.status==PartnerSessionStatus.IN_PROGRESS},
            revenue,deductions,payable,opening,paid,if(opening!=null && paid!=null)opening+payable-paid else null)
        val rows=allowed.map {station->val rows=completed.filter {it.stationId==station.id};PartnerStationReport(station,rows.sumExact {it.energyKwh},rows.sumExact {it.revenueKgs},rows.size)}
        return PartnerReport(known,filter,now,rows,sessions,payouts,totals)
    }
}
private inline fun <T> Iterable<T>.sumExact(value:(T)->BigDecimal):BigDecimal=fold(ZERO) {total,row->total+value(row)}
