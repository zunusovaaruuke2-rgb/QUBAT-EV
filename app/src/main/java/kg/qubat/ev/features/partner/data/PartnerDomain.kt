package kg.qubat.ev

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

val PARTNER_ZONE:ZoneId = ZoneId.of("Asia/Bishkek")

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
data class PartnerPage<T>(val items:List<T>,val page:Int,val pageSize:Int,val totalCount:Int) {
    val hasNext:Boolean get()=(page.toLong()+1)*pageSize<totalCount
}
