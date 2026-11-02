package kg.qubat.ev

import java.io.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

private val N_ZERO = BigDecimal.ZERO
private fun nd(value:String)=BigDecimal(value)
private inline fun <T> Iterable<T>.nSum(value:(T)->BigDecimal):BigDecimal=fold(N_ZERO){sum,row->sum+value(row)}
private fun BigDecimal.nMoney()=setScale(2,RoundingMode.HALF_UP)

enum class NativePermission { VIEW,REPORT,EXPORT,FINANCE,EDIT_STATION,EDIT_TARIFF,EDIT_FINANCE,MANAGE_MEMBERS,DISCOUNT,FREE_CHARGE,MANAGE_BRANDS,NOTIFICATIONS }
enum class NativePortStatus(val label:String) { FREE("Свободен"),BUSY("Занят"),FAULT("Неисправен"),UNAVAILABLE("Недоступен") }
enum class NativeSessionStatus(val label:String) { COMPLETED("Завершена"),CANCELLED("Отменена"),ERROR("Ошибка"),ACTIVE("Активна") }
enum class NativePaymentStatus(val label:String) { PAID("Оплачено · ДЕМО"),UNPAID("Не оплачено"),REFUNDED("Возврат · ДЕМО"),UNKNOWN("Нет данных") }
enum class NativeOperation(val label:String) { CHARGE("Зарядка"),DISCOUNT("Скидка"),FREE("Бесплатная зарядка") }
enum class NativeGrantKind { PERCENT,FIXED,FREE }
enum class NativeExpenseKind(val label:String) { RENT("Аренда"),COMMUNICATION("Связь"),MAINTENANCE("Обслуживание"),SALARY("Зарплата"),OTHER("Прочее"),PAYOUT("Выплата партнёру") }
enum class NativeDeduction(val label:String) { ELECTRICITY("Электричество"),PLATFORM("Платформа"),ACQUIRING("Эквайринг"),TAX("Налог"),RENT("Аренда"),OTHER("Прочие расходы"),PARTNER_SHARE("Доля партнёра"),RESERVE("Резерв"),PAYOUT("Выплаты") }
enum class NativePeriodPreset { DAY,WEEK,MONTH,YEAR,CUSTOM }

data class NativeOrganization(val id:String,val name:String,val ownerId:String):Serializable

data class NativePort(val id:String,val number:Int,val type:String="GB/T DC",val maxPowerKw:BigDecimal=nd("80"),val maxCurrentA:BigDecimal=nd("200"),val status:NativePortStatus=NativePortStatus.FREE,val updatedAt:Instant=Instant.now()):Serializable

data class NativeStation(
    val id:String,val organizationId:String,val ownerId:String,val title:String,val address:String,
    val city:String="Бишкек",val groupId:String="main",val locationId:String="main",val brandId:String="brand-north",
    val model:String="80 DC",val serialNumber:String="",val ocppId:String="",val firmware:String="demo",
    val latitude:BigDecimal=nd("42.8746"),val longitude:BigDecimal=nd("74.5698"),val operatingHours:String="24/7",
    val powerKw:BigDecimal=nd("80"),val apparatusCount:Int=1,val ports:List<NativePort> = emptyList(),
    val lastSeen:Instant=Instant.now(),val source:String="DEMO_QUBAT",val errorReason:String?=null,val archived:Boolean=false,
    val partnerId:String?=null,val partnerName:String="",val contractId:String=""
):Serializable {
    val connectorCount:Int get()=ports.size
    val instantaneousPowerKw:BigDecimal get(){val cutoff=Instant.now().minusSeconds(300);return if(lastSeen<cutoff)N_ZERO else ports.filter{it.status==NativePortStatus.BUSY && it.updatedAt>=cutoff}.nSum{it.maxPowerKw*nd("0.42")}.min(powerKw)}
}

data class NativeTariff(
    val id:String,val stationId:String,val effectiveFrom:LocalDate,val effectiveTo:LocalDate?=null,
    val customerRate:BigDecimal=nd("13.50"),val premiumRate:BigDecimal=nd("12.50"),val electricityRate:BigDecimal=nd("4.50"),
    val platformRate:BigDecimal=nd("0.025"),val acquiringRate:BigDecimal=nd("0.02"),val taxRate:BigDecimal=nd("0.02"),
    val rentMonthly:BigDecimal=N_ZERO,val partnerRate:BigDecimal=N_ZERO,val reserveRate:BigDecimal=N_ZERO,
    val minuteRate:BigDecimal=N_ZERO,val idleMinuteRate:BigDecimal=N_ZERO,val reservationMinutes:Int=15,val stopEnergyKwh:BigDecimal?=null,val partnerFixedMonthly:BigDecimal=N_ZERO
):Serializable

data class NativePublicTariff(val id:String,val stationId:String,val effectiveFrom:LocalDate,val effectiveTo:LocalDate?,val customerRate:BigDecimal,val premiumRate:BigDecimal,val minuteRate:BigDecimal,val idleMinuteRate:BigDecimal,val reservationMinutes:Int,val stopEnergyKwh:BigDecimal?):Serializable

data class NativeRateSnapshot(
    val tariffId:String,val customerRate:BigDecimal,val electricityRate:BigDecimal,val platformRate:BigDecimal,
    val acquiringRate:BigDecimal,val taxRate:BigDecimal,val rentMonthly:BigDecimal,val partnerRate:BigDecimal,val reserveRate:BigDecimal,val partnerFixedMonthly:BigDecimal=N_ZERO
):Serializable

data class NativeSession(
    val id:String,val stationId:String,val portId:String,val customerId:String,val startedAt:Instant,val endedAt:Instant?,
    val energyKwh:BigDecimal,val status:NativeSessionStatus,val paymentStatus:NativePaymentStatus,
    val rates:NativeRateSnapshot?,val chargedKgs:BigDecimal?,val paidKgs:BigDecimal?,val refundKgs:BigDecimal?,
    val operation:NativeOperation=NativeOperation.CHARGE,val paymentMethod:String="Демо-кошелёк",val errorReason:String?=null,
    val source:String="DEMO_QUBAT",val updatedAt:Instant=Instant.now()
):Serializable {
    val accountingAt:Instant get()=endedAt?:startedAt
    val durationSeconds:Long get()=endedAt?.let{Duration.between(startedAt,it).seconds.coerceAtLeast(0)}?:0
    val revenueKgs:BigDecimal? get()=if(paidKgs!=null && refundKgs!=null)paidKgs-refundKgs else null
    val platformFeeKgs:BigDecimal? get()=revenueKgs?.let{base->rates?.let{(base*it.platformRate).nMoney()}}
    val acquiringFeeKgs:BigDecimal? get()=paidKgs?.let{base->rates?.let{(base*it.acquiringRate).nMoney()}}
    val taxKgs:BigDecimal? get()=revenueKgs?.let{base->rates?.let{(base*it.taxRate).nMoney()}}
    val partnerShareKgs:BigDecimal? get()=revenueKgs?.let{base->rates?.let{(base*it.partnerRate).nMoney()}}
    val reserveKgs:BigDecimal? get()=revenueKgs?.let{base->rates?.let{(base*it.reserveRate).nMoney()}}
}

data class NativePayment(val id:String,val stationId:String,val sessionId:String,val at:Instant,val amountKgs:BigDecimal,val refundKgs:BigDecimal=N_ZERO,val feeKgs:BigDecimal=N_ZERO,val method:String="Демо-кошелёк",val status:String="Подтверждён · ДЕМО",val source:String="DEMO_LEDGER"):Serializable

data class NativeExpense(val id:String,val stationId:String,val date:LocalDate,val kind:NativeExpenseKind,val amountKgs:BigDecimal,val note:String="",val documentNumber:String="",val paid:Boolean=false):Serializable

data class NativeAttachment(val fileName:String,val mediaType:String,val byteSize:Long,val localReference:String=""):Serializable

data class NativeMonthBill(
    val id:String,val stationId:String,val month:YearMonth,val electricityKgs:BigDecimal,val billNumber:String,val billDate:LocalDate,
    val paid:Boolean=false,val meterEnergyKwh:BigDecimal?=null,val actualRentKgs:BigDecimal?=null,val comment:String="",
    val attachment:NativeAttachment?=null,val updatedAt:Instant=Instant.now(),val authorId:String=""
):Serializable

data class NativeMember(
    val id:String,val organizationId:String,val name:String,val contact:String,val role:PartnerRole,val stationIds:Set<String>,
    val startsOn:LocalDate,val endsOn:LocalDate?=null,val rights:Set<NativePermission> = emptySet(),val revoked:Boolean=false,
    val invitedBy:String="",val localOnly:Boolean=true
):Serializable

data class NativeGrant(
    val id:String,val organizationId:String,val stationIds:Set<String>,val customerId:String,val kind:NativeGrantKind,val value:BigDecimal,
    val startsOn:LocalDate,val endsOn:LocalDate,val usageLimit:Int,val used:Int=0,val approvedBy:String="",val reason:String=""
):Serializable

data class NativeBrand(val id:String,val organizationId:String,val name:String,val color:String="#77C83F",val logoUrl:String="",val archived:Boolean=false):Serializable

data class NativeNotificationSettings(
    val id:String,val organizationId:String,val stationIds:Set<String>,val channel:String="В кабинете",val offlineMinutes:Int=10,
    val longSessionMinutes:Int=120,val lowPowerKw:BigDecimal=nd("5"),val enabled:Boolean=true
):Serializable

data class NativeAudit(val id:String,val organizationId:String,val stationIds:Set<String>,val at:Instant,val authorId:String,val action:String,val before:String,val after:String,val reason:String):Serializable

data class NativePartnerState(
    val today:LocalDate,val organizations:MutableList<NativeOrganization> = mutableListOf(),val stations:MutableList<NativeStation> = mutableListOf(),
    val tariffs:MutableList<NativeTariff> = mutableListOf(),val sessions:MutableList<NativeSession> = mutableListOf(),val payments:MutableList<NativePayment> = mutableListOf(),
    val expenses:MutableList<NativeExpense> = mutableListOf(),val monthBills:MutableList<NativeMonthBill> = mutableListOf(),val members:MutableList<NativeMember> = mutableListOf(),
    val grants:MutableList<NativeGrant> = mutableListOf(),val brands:MutableList<NativeBrand> = mutableListOf(),val notifications:MutableList<NativeNotificationSettings> = mutableListOf(),
    val audit:MutableList<NativeAudit> = mutableListOf(),val ledgerOrganizationIds:MutableSet<String> = mutableSetOf(),val schemaVersion:Int=2
):Serializable

data class NativeReportFilter(
    val period:PartnerPeriod,val organizationId:String?=null,val ownerId:String?=null,val groupId:String?=null,val locationId:String?=null,
    val stationId:String?=null,val portId:String?=null,val customerId:String?=null,val operation:NativeOperation?=null,
    val paymentStatus:NativePaymentStatus?=null,val status:NativeSessionStatus?=null,val query:String="",val sort:PartnerSort=PartnerSort.NEWEST
):Serializable {
    companion object {
        fun preset(preset:NativePeriodPreset,today:LocalDate=LocalDate.now(PARTNER_ZONE))=NativeReportFilter(when(preset){
            NativePeriodPreset.DAY->PartnerPeriod(today,today)
            NativePeriodPreset.WEEK->PartnerPeriod(today.minusDays(6),today)
            NativePeriodPreset.MONTH->PartnerPeriod(today.withDayOfMonth(1),today)
            NativePeriodPreset.YEAR->PartnerPeriod(today.withDayOfYear(1),today)
            NativePeriodPreset.CUSTOM->PartnerPeriod(today,today)
        })
    }
}

data class NativeSummary(
    val apparatusCount:Int,val connectorCount:Int,val stationCount:Int,val activePorts:Int,val offlineStations:Int,val currentPowerKw:BigDecimal,
    val completedCount:Int,val cancelledCount:Int,val errorCount:Int,val activeCount:Int,val energyKwh:BigDecimal,val durationSeconds:Long,val uniqueCustomers:Int,
    val chargedKgs:BigDecimal?,val paidKgs:BigDecimal?,val refundKgs:BigDecimal?,val revenueKgs:BigDecimal?,val unpaidKgs:BigDecimal?,
    val averageTariffKgs:BigDecimal?,val paymentLedgerKgs:BigDecimal?,val reconciliationDifferenceKgs:BigDecimal?,val unknownPaymentCount:Int
):Serializable

data class NativeStationReport(val station:NativeStation,val completedCount:Int,val energyKwh:BigDecimal,val revenueKgs:BigDecimal?):Serializable

data class NativeMonthFinance(
    val month:YearMonth,val stationIds:Set<String>,val fullMonth:Boolean,val energyKwh:BigDecimal,val chargedKgs:BigDecimal?,val revenueKgs:BigDecimal?,
    val unpaidKgs:BigDecimal?,val electricityEstimateKgs:BigDecimal?,val electricityFactKgs:BigDecimal?,val meterEnergyKwh:BigDecimal?,val meterDifferenceKwh:BigDecimal?,
    val platformKgs:BigDecimal?,val acquiringKgs:BigDecimal?,val taxKgs:BigDecimal?,val rentEstimateKgs:BigDecimal?,val rentFactKgs:BigDecimal?,val otherKgs:BigDecimal?,
    val partnerShareKgs:BigDecimal?,val reserveKgs:BigDecimal?,val payoutKgs:BigDecimal?,val estimatedProfitKgs:BigDecimal?,val actualProfitKgs:BigDecimal?,
    val estimatedMarginPercent:BigDecimal?,val actualMarginPercent:BigDecimal?,val outstandingKgs:BigDecimal?,val billCount:Int,val billPaidCount:Int,
    val selectedDeductions:Set<NativeDeduction>,val factUnavailableReason:String?,val paymentDataComplete:Boolean=true,val unknownPaymentCount:Int=0
):Serializable

class NativeNetworkReport internal constructor(
    val account:PartnerAccount,val filter:NativeReportFilter,val generatedAt:Instant,val stations:List<NativeStationReport>,val sessions:List<NativeSession>,
    val payments:List<NativePayment>,val expenses:List<NativeExpense>,val summary:NativeSummary,val monthlyFinance:List<NativeMonthFinance>,
    val scopedStationIds:Set<String>,val exportAllowed:Boolean,val financeVisible:Boolean,val monthBills:List<NativeMonthBill> = emptyList()
):Serializable

/** Local, persistent DEMO rules. A production server must repeat authorization at its API boundary. */
class NativePartnerRepository(val account:PartnerAccount,val state:NativePartnerState,private val clock:Clock=Clock.system(PARTNER_ZONE)) {
    val today:LocalDate get()=state.today
    val organizationId:String? get()=when(account.id){"demo-owner-a"->"demo-org-north";"demo-owner-b"->"demo-org-south";"demo-admin"->null;else->activeMember()?.organizationId}
    val authorizationDate:LocalDate get()=LocalDate.now(clock)
    private val now:Instant get()=clock.instant()
    private fun activeMember():NativeMember?=state.members.firstOrNull{it.id==account.id && it.role==account.role && !it.revoked && it.startsOn<=authorizationDate && (it.endsOn==null || authorizationDate<=it.endsOn)}
    private fun validateAccount() {
        val builtIn=accounts.firstOrNull{it.id==account.id && it.role==account.role}
        if(account.role==PartnerRole.DRIVER || (builtIn==null && activeMember()==null))throw SecurityException("Нет доступа к кабинету партнёра")
        if(account.role !in setOf(PartnerRole.ADMIN,PartnerRole.OWNER) && activeMember()==null)throw SecurityException("Доступ отозван или срок закончился")
    }
    private fun roleRights():Set<NativePermission> = when(account.role){
        PartnerRole.ADMIN,PartnerRole.OWNER->NativePermission.entries.toSet()
        PartnerRole.ACCOUNTANT->setOf(NativePermission.VIEW,NativePermission.REPORT,NativePermission.EXPORT,NativePermission.FINANCE,NativePermission.EDIT_FINANCE)
        PartnerRole.EMPLOYEE->setOf(NativePermission.VIEW)
        PartnerRole.OBSERVER->setOf(NativePermission.VIEW,NativePermission.REPORT,NativePermission.EXPORT)
        else->emptySet()
    }
    private fun scopedStations():List<NativeStation> {
        validateAccount()
        if(account.role==PartnerRole.ADMIN)return state.stations.toList()
        val org=organizationId
        val assigned=if(account.role==PartnerRole.OWNER)null else activeMember()!!.stationIds
        return state.stations.filter{it.organizationId==org && (assigned==null || it.id in assigned)}
    }
    fun can(permission:NativePermission,stationId:String?=null):Boolean=try {
        validateAccount()
        val role=roleRights()
        val member=activeMember()
        val allowed=permission in role && (member==null || member.rights.isEmpty() || permission in member.rights)
        allowed && (stationId==null || scopedStations().any{it.id==stationId})
    } catch(_:SecurityException){false}
    private fun requirePermission(permission:NativePermission,stationId:String?=null) {if(!can(permission,stationId))throw SecurityException("Недостаточно прав: ${permission.name}")}
    fun organizations():List<NativeOrganization> {validateAccount();return state.organizations.filter{account.role==PartnerRole.ADMIN || it.id==organizationId}}
    fun stations(includeArchived:Boolean=false):List<NativeStation> {requirePermission(NativePermission.VIEW);return scopedStations().filter{includeArchived || !it.archived}.sortedBy{it.title}}
    fun station(id:String):NativeStation {requirePermission(NativePermission.VIEW);return scopedStations().firstOrNull{it.id==id}?:throw SecurityException("Точка недоступна")}
    fun isStale(station:NativeStation,now:Instant=clock.instant()):Boolean {this.station(station.id);return station.lastSeen<now.minusSeconds(300) || station.source.isBlank()}
    fun isPortStale(stationId:String,portId:String,at:Instant=clock.instant()):Boolean {val station=station(stationId);val port=station.ports.firstOrNull{it.id==portId}?:throw SecurityException("Разъём недоступен");return isStale(station,at) || port.updatedAt<at.minusSeconds(300)}
    fun tariffs(stationId:String):List<NativeTariff> {requirePermission(NativePermission.EDIT_TARIFF,stationId);return state.tariffs.filter{it.stationId==stationId}.sortedByDescending{it.effectiveFrom}}
    fun publicTariffs(stationId:String):List<NativePublicTariff> {station(stationId);return state.tariffs.filter{it.stationId==stationId}.sortedByDescending{it.effectiveFrom}.map{NativePublicTariff(it.id,it.stationId,it.effectiveFrom,it.effectiveTo,it.customerRate,it.premiumRate,it.minuteRate,it.idleMinuteRate,it.reservationMinutes,it.stopEnergyKwh)}}
    fun stationHistory(stationId:String):List<NativeAudit> {
        val point=station(stationId)
        val allowed=scopedStations().map{it.id}.toSet()
        return state.audit.filter{it.organizationId==point.organizationId && stationId in it.stationIds && allowed.containsAll(it.stationIds)}.sortedByDescending{it.at}.map{row->
            val publicAction=row.action in setOf("Добавление точки","Изменение точки")
            val financialAction=row.action in setOf("Версия тарифа","Фактический счёт","Расход / выплата")
            if(publicAction || (financialAction && can(NativePermission.FINANCE)) || can(NativePermission.MANAGE_MEMBERS))row
            else row.copy(before="Скрыто ролью",after="Скрыто ролью",reason="Изменение зарегистрировано")
        }
    }
    fun tariffAt(stationId:String,date:LocalDate):NativeTariff? {requirePermission(NativePermission.EDIT_TARIFF,stationId);return rawTariffAt(stationId,date)}
    private fun rawTariffAt(stationId:String,date:LocalDate)=state.tariffs.asSequence().filter{it.stationId==stationId && it.effectiveFrom<=date && (it.effectiveTo==null || date<=it.effectiveTo)}.maxByOrNull{it.effectiveFrom}
    private fun orgCheck(org:String) {if(organizations().none{it.id==org})throw SecurityException("Организация недоступна")}
    private fun reasonCheck(reason:String) {require(reason.isNotBlank()){"Укажите причину изменения"}}
    private fun log(org:String,ids:Set<String>,action:String,before:Any?,after:Any?,reason:String) {state.audit.add(NativeAudit("audit-${now.toEpochMilli()}-${state.audit.size}",org,ids,now,account.id,action,before?.toString().orEmpty(),after?.toString().orEmpty(),reason))}
    private fun validateStation(value:NativeStation) {
        require(value.id.isNotBlank() && value.title.isNotBlank() && value.address.isNotBlank() && value.serialNumber.isNotBlank() && value.ocppId.isNotBlank()){"Заполните ID, название, адрес, серийный номер и OCPP ID"}
        require(value.powerKw.signum()>0 && value.apparatusCount>0 && value.ports.isNotEmpty()){"Укажите мощность, аппарат и разъёмы"}
        require(value.latitude in nd("-90")..nd("90") && value.longitude in nd("-180")..nd("180")){"Некорректные координаты"}
        require(value.ports.map{it.id}.distinct().size==value.ports.size && value.ports.map{it.number}.distinct().size==value.ports.size && value.ports.all{it.number>0 && it.maxPowerKw.signum()>0 && it.maxCurrentA.signum()>0}){"Проверьте номера и параметры разъёмов"}
        val org=state.organizations.firstOrNull{it.id==value.organizationId}?:throw IllegalArgumentException("Неизвестная организация")
        require(org.ownerId==value.ownerId){"Владелец не соответствует организации"}
        require(state.brands.any{it.id==value.brandId && it.organizationId==value.organizationId}){"Бренд не принадлежит организации"}
        require(state.stations.none{it.id!=value.id && (it.serialNumber==value.serialNumber || it.ocppId==value.ocppId)}){"Серийный номер или OCPP ID уже используется"}
    }
    fun createStation(value:NativeStation,reason:String) {requirePermission(NativePermission.EDIT_STATION);orgCheck(value.organizationId);reasonCheck(reason);require(state.stations.none{it.id==value.id}){"ID уже существует"};validateStation(value);state.stations.add(value);log(value.organizationId,setOf(value.id),"Добавление точки",null,value,reason)}
    fun updateStation(value:NativeStation,reason:String) {requirePermission(NativePermission.EDIT_STATION,value.id);val old=station(value.id);orgCheck(value.organizationId);require(old.organizationId==value.organizationId && old.ownerId==value.ownerId){"Смена организации требует отдельного переноса"};reasonCheck(reason);validateStation(value);state.stations[state.stations.indexOfFirst{it.id==value.id}]=value;log(value.organizationId,setOf(value.id),"Изменение точки",old,value,reason)}
    fun archiveStation(id:String,reason:String) {val old=station(id);updateStation(old.copy(archived=true),reason)}
    fun updateTariff(value:NativeTariff,reason:String) {
        requirePermission(NativePermission.EDIT_TARIFF,value.stationId);reasonCheck(reason)
        require(value.id.isNotBlank() && (value.effectiveTo==null || value.effectiveTo>=value.effectiveFrom)){"Проверьте период тарифа"}
        require(value.effectiveFrom>=authorizationDate){"Тариф нельзя менять задним числом; нужна отдельная явная корректировка"}
        require(listOf(value.customerRate,value.premiumRate,value.electricityRate,value.rentMonthly,value.minuteRate,value.idleMinuteRate,value.partnerFixedMonthly).all{it.signum()>=0} && value.customerRate.signum()>0){"Ставки должны быть неотрицательными"}
        require(listOf(value.platformRate,value.acquiringRate,value.taxRate,value.partnerRate,value.reserveRate).all{it>=N_ZERO && it<=BigDecimal.ONE}){"Процент должен быть от 0 до 100%"}
        require(value.partnerFixedMonthly.signum()==0 || value.partnerRate.signum()==0){"Выберите процентную или фиксированную долю партнёра"}
        require(value.reservationMinutes>=0 && (value.stopEnergyKwh==null || value.stopEnergyKwh.signum()>0)){"Проверьте бронь и порог"}
        require(state.tariffs.none{it.id==value.id}){"Для изменения создайте новую версию тарифа"}
        val future=state.tariffs.filter{it.stationId==value.stationId && it.effectiveFrom>=value.effectiveFrom}
        require(future.isEmpty()){"Новая версия должна начинаться после существующих версий"}
        val prior=state.tariffs.filter{it.stationId==value.stationId && (it.effectiveTo==null || it.effectiveTo>=value.effectiveFrom)}
        prior.forEach{old->state.tariffs[state.tariffs.indexOf(old)]=old.copy(effectiveTo=value.effectiveFrom.minusDays(1))}
        state.tariffs.add(value);log(station(value.stationId).organizationId,setOf(value.stationId),"Версия тарифа",prior,value,reason)
    }
    fun members():List<NativeMember> {requirePermission(NativePermission.MANAGE_MEMBERS);val ids=scopedStations().map{it.id}.toSet();return state.members.filter{(account.role==PartnerRole.ADMIN || it.organizationId==organizationId) && it.stationIds.all{key->key in ids}}.toList()}
    private fun validateAssignments(org:String,ids:Set<String>) {orgCheck(org);require(ids.isNotEmpty()){"Выберите точки"};ids.forEach{val s=station(it);if(s.organizationId!=org)throw SecurityException("Точка другой организации")}}
    fun inviteMember(value:NativeMember,reason:String) {
        requirePermission(NativePermission.MANAGE_MEMBERS);reasonCheck(reason);validateAssignments(value.organizationId,value.stationIds)
        require(value.id.isNotBlank() && value.name.isNotBlank() && (value.contact.contains("@") || value.contact.count{it.isDigit()}>=9)){"Укажите имя и телефон или email"}
        require(value.role in setOf(PartnerRole.EMPLOYEE,PartnerRole.ACCOUNTANT,PartnerRole.OBSERVER)){"Разрешены сотрудник, бухгалтер и наблюдатель"}
        require(value.endsOn==null || value.endsOn>=value.startsOn){"Проверьте срок доступа"}
        require(accounts.none{it.id==value.id && it.role in setOf(PartnerRole.ADMIN,PartnerRole.OWNER,PartnerRole.DRIVER)}){"Системный аккаунт нельзя изменить"}
        val old=state.members.firstOrNull{it.id==value.id};if(old!=null){validateAssignments(old.organizationId,old.stationIds);require(old.organizationId==value.organizationId){"Нельзя перенести пользователя между организациями"}}
        val saved=value.copy(invitedBy=account.id,localOnly=true)
        if(old==null)state.members.add(saved) else state.members[state.members.indexOf(old)]=saved
        log(value.organizationId,value.stationIds,"Локальное приглашение / права",old,saved,reason)
    }
    fun revokeMember(id:String,reason:String) {val old=members().firstOrNull{it.id==id}?:throw SecurityException("Пользователь недоступен");inviteMember(old.copy(revoked=true),reason)}
    fun grants():List<NativeGrant> {requirePermission(NativePermission.MANAGE_MEMBERS);val ids=scopedStations().map{it.id}.toSet();return state.grants.filter{(account.role==PartnerRole.ADMIN || it.organizationId==organizationId) && it.stationIds.all{key->key in ids}}.toList()}
    fun saveGrant(value:NativeGrant,reason:String) {
        requirePermission(if(value.kind==NativeGrantKind.FREE)NativePermission.FREE_CHARGE else NativePermission.DISCOUNT);reasonCheck(reason);validateAssignments(value.organizationId,value.stationIds)
        require(value.id.isNotBlank() && value.customerId.isNotBlank() && value.endsOn>=value.startsOn && value.usageLimit>0 && value.used in 0..value.usageLimit){"Проверьте клиента, сроки и лимит"}
        require(value.value.signum()>=0 && (value.kind!=NativeGrantKind.PERCENT || value.value<=nd("100"))){"Некорректная скидка"}
        val old=state.grants.firstOrNull{it.id==value.id};old?.let{validateAssignments(it.organizationId,it.stationIds);require(it.organizationId==value.organizationId && it.used==value.used){"Организация и счётчик использований не изменяются настройкой скидки"}};if(old==null)require(value.used==0){"Новый лимит начинается с нуля использований"}
        val saved=value.copy(approvedBy=account.id,reason=reason);if(old==null)state.grants.add(saved) else state.grants[state.grants.indexOf(old)]=saved
        log(value.organizationId,value.stationIds,"Скидка / бесплатная зарядка",old,saved,reason)
    }
    fun brands():List<NativeBrand> {requirePermission(NativePermission.VIEW);return state.brands.filter{brand->organizations().any{it.id==brand.organizationId}}.toList()}
    fun saveBrand(value:NativeBrand,reason:String) {requirePermission(NativePermission.MANAGE_BRANDS);orgCheck(value.organizationId);reasonCheck(reason);require(value.name.isNotBlank() && Regex("#[0-9A-Fa-f]{6}").matches(value.color)){"Укажите название и цвет #RRGGBB"};val old=state.brands.firstOrNull{it.id==value.id};old?.let{orgCheck(it.organizationId);require(it.organizationId==value.organizationId)};if(old==null)state.brands.add(value) else state.brands[state.brands.indexOf(old)]=value;log(value.organizationId,emptySet(),"Изменение бренда",old,value,reason)}
    fun notificationSettings():List<NativeNotificationSettings> {requirePermission(NativePermission.NOTIFICATIONS);val ids=scopedStations().map{it.id}.toSet();return state.notifications.filter{(account.role==PartnerRole.ADMIN || it.organizationId==organizationId) && it.stationIds.all{key->key in ids}}.toList()}
    fun saveNotificationSettings(value:NativeNotificationSettings,reason:String) {requirePermission(NativePermission.NOTIFICATIONS);validateAssignments(value.organizationId,value.stationIds);reasonCheck(reason);require(value.offlineMinutes>0 && value.longSessionMinutes>0 && value.lowPowerKw.signum()>=0 && value.channel=="В кабинете"){"Проверьте пороги; в ДЕМО доступен канал «В кабинете»"};val old=state.notifications.firstOrNull{it.id==value.id};old?.let{validateAssignments(it.organizationId,it.stationIds)};if(old==null)state.notifications.add(value) else state.notifications[state.notifications.indexOf(old)]=value;log(value.organizationId,value.stationIds,"Настройка уведомлений",old,value,reason)}
    fun audit():List<NativeAudit> {requirePermission(NativePermission.MANAGE_MEMBERS);val ids=scopedStations().map{it.id}.toSet();return state.audit.filter{(account.role==PartnerRole.ADMIN || it.organizationId==organizationId) && it.stationIds.all{key->key in ids}}.sortedByDescending{it.at}}
    fun saveMonthBill(value:NativeMonthBill,reason:String) {
        requirePermission(NativePermission.EDIT_FINANCE,value.stationId);reasonCheck(reason)
        require(value.id.isNotBlank() && value.electricityKgs.signum()>=0 && value.billNumber.isNotBlank() && (value.meterEnergyKwh==null || value.meterEnergyKwh.signum()>=0) && (value.actualRentKgs==null || value.actualRentKgs.signum()>=0)){"Укажите номер и неотрицательные суммы счёта"}
        value.attachment?.let{require(it.fileName.isNotBlank() && it.byteSize in 0..10485760){"Проверьте метаданные вложения"}}
        val old=state.monthBills.firstOrNull{it.id==value.id || (it.stationId==value.stationId && it.month==value.month)}
        old?.let{requirePermission(NativePermission.EDIT_FINANCE,it.stationId);require(it.stationId==value.stationId && it.month==value.month){"Нельзя перенести счёт в другую точку или месяц"}}
        val saved=value.copy(updatedAt=now,authorId=account.id);if(old==null)state.monthBills.add(saved) else state.monthBills[state.monthBills.indexOf(old)]=saved
        log(station(value.stationId).organizationId,setOf(value.stationId),"Фактический счёт",old,saved,reason)
    }
    fun monthBills(stationId:String?=null):List<NativeMonthBill> {requirePermission(NativePermission.FINANCE,stationId);val ids=scopedStations().map{it.id}.toSet();return state.monthBills.filter{it.stationId in ids && (stationId==null || it.stationId==stationId)}.sortedByDescending{it.month}}
    fun saveExpense(value:NativeExpense,reason:String) {requirePermission(NativePermission.EDIT_FINANCE,value.stationId);reasonCheck(reason);require(value.amountKgs.signum()>=0 && value.id.isNotBlank()){"Проверьте сумму расхода"};val old=state.expenses.firstOrNull{it.id==value.id};old?.let{requirePermission(NativePermission.EDIT_FINANCE,it.stationId)};if(old==null)state.expenses.add(value) else state.expenses[state.expenses.indexOf(old)]=value;log(station(value.stationId).organizationId,setOf(value.stationId),"Расход / выплата",old,value,reason)}
    private fun selectedStations(filter:NativeReportFilter):List<NativeStation> {
        val allowed=scopedStations()
        filter.stationId?.let{station(it)}
        filter.organizationId?.let{orgCheck(it)}
        filter.ownerId?.let{id->if(state.organizations.none{it.ownerId==id && (account.role==PartnerRole.ADMIN || it.id==organizationId)})throw SecurityException("Владелец недоступен")}
        filter.groupId?.let{id->if(state.stations.any{it.groupId==id} && allowed.none{it.groupId==id})throw SecurityException("Группа недоступна")}
        filter.locationId?.let{id->if(state.stations.any{it.locationId==id} && allowed.none{it.locationId==id})throw SecurityException("Локация недоступна")}
        filter.portId?.let{id->if(allowed.none{station->station.ports.any{it.id==id}})throw SecurityException("Разъём недоступен")}
        filter.customerId?.let{id->val allowedIds=allowed.map{it.id}.toSet();if(state.sessions.any{it.customerId==id} && state.sessions.none{it.customerId==id && it.stationId in allowedIds})throw SecurityException("Клиент недоступен")}
        return allowed.filter{(filter.stationId==null || it.id==filter.stationId) && (filter.organizationId==null || it.organizationId==filter.organizationId) &&
            (filter.ownerId==null || it.ownerId==filter.ownerId) && (filter.groupId==null || it.groupId==filter.groupId) && (filter.locationId==null || it.locationId==filter.locationId) &&
            (filter.portId==null || it.ports.any{port->port.id==filter.portId})}
    }
    private fun stationMatches(station:NativeStation,query:String):Boolean=listOf(station.id,station.title,station.address,station.city,station.serialNumber,station.ocppId).any{it.contains(query,ignoreCase=true)}
    private fun selectedSessions(stations:List<NativeStation>,filter:NativeReportFilter):List<NativeSession> {
        val ids=stations.map{it.id}.toSet();val query=filter.query.trim()
        val start=filter.period.start;val end=filter.period.endExclusive
        val matchingStations=if(query.isBlank())emptySet() else stations.filter{stationMatches(it,query)}.mapTo(hashSetOf()){it.id}
        val rows=state.sessions.filter{it.stationId in ids && it.accountingAt>=start && it.accountingAt<end && (filter.portId==null || it.portId==filter.portId) &&
            (filter.customerId==null || it.customerId==filter.customerId) && (filter.operation==null || it.operation==filter.operation) &&
            (filter.paymentStatus==null || it.paymentStatus==filter.paymentStatus) && (filter.status==null || it.status==filter.status) &&
            (query.isBlank() || it.id.contains(query,ignoreCase=true) || it.customerId.contains(query,ignoreCase=true) || it.stationId in matchingStations)}
        return when(filter.sort){
            PartnerSort.NEWEST->rows.sortedWith(compareByDescending<NativeSession>{it.startedAt}.thenBy{it.id})
            PartnerSort.OLDEST->rows.sortedWith(compareBy<NativeSession>{it.startedAt}.thenBy{it.id})
            PartnerSort.ENERGY_DESC->rows.sortedWith(compareByDescending<NativeSession>{it.energyKwh}.thenBy{it.id})
            PartnerSort.REVENUE_DESC->{requirePermission(NativePermission.FINANCE);rows.sortedWith(compareByDescending<NativeSession>{it.revenueKgs?:N_ZERO}.thenBy{it.id})}
        }
    }
    private fun redact(session:NativeSession,financeVisible:Boolean=can(NativePermission.FINANCE)):NativeSession=if(financeVisible)session else session.copy(rates=null,chargedKgs=null,paidKgs=null,refundKgs=null,paymentMethod="Скрыто ролью")
    fun session(id:String):NativeSession {
        requirePermission(NativePermission.VIEW)
        val session=state.sessions.firstOrNull{it.id==id}?:throw SecurityException("Сессия недоступна")
        station(session.stationId)
        if(!can(NativePermission.REPORT) && session.status!=NativeSessionStatus.ACTIVE)throw SecurityException("История недоступна этой роли")
        return redact(session)
    }
    fun sessionsPage(filter:NativeReportFilter,page:Int,pageSize:Int=20):PartnerPage<NativeSession> {
        requirePermission(NativePermission.VIEW);require(page>=0 && pageSize in 1..100){"Страница должна быть >= 0, размер от 1 до 100"}
        val all=selectedSessions(selectedStations(filter),filter).filter{can(NativePermission.REPORT) || it.status==NativeSessionStatus.ACTIVE}
        val offset=(page.toLong()*pageSize).coerceAtMost(all.size.toLong()).toInt()
        return PartnerPage(all.drop(offset).take(pageSize).map{redact(it)},page,pageSize,all.size)
    }
    fun summary(filter:NativeReportFilter):NativeSummary {requirePermission(NativePermission.VIEW);return buildReport(filter,false).summary}
    fun report(filter:NativeReportFilter,selectedDeductions:Set<NativeDeduction> = defaultDeductions):NativeNetworkReport {requirePermission(NativePermission.REPORT);return buildReport(filter,true,selectedDeductions)}
    fun exportReport(filter:NativeReportFilter,selectedDeductions:Set<NativeDeduction> = defaultDeductions):NativeNetworkReport {requirePermission(NativePermission.EXPORT);requirePermission(NativePermission.REPORT);return buildReport(filter,true,selectedDeductions)}
    fun activeSessions():List<NativeSession> {requirePermission(NativePermission.VIEW);val ids=scopedStations().map{it.id}.toSet();return state.sessions.filter{it.stationId in ids && it.status==NativeSessionStatus.ACTIVE}.sortedBy{it.startedAt}.map{redact(it)}}
    fun monitoring():NativeNetworkReport {requirePermission(NativePermission.VIEW);return buildReport(NativeReportFilter(PartnerPeriod.today(today)),false)}
    private fun sessionFiltered(filter:NativeReportFilter)=filter.portId!=null || filter.customerId!=null || filter.operation!=null || filter.paymentStatus!=null || filter.status!=null || filter.query.isNotBlank()
    private fun buildReport(filter:NativeReportFilter,includeFinance:Boolean,selectedDeductions:Set<NativeDeduction> = defaultDeductions):NativeNetworkReport {
        val base=selectedStations(filter)
        val raw=selectedSessions(base,filter)
        val query=filter.query.trim()
        val stations=if(query.isBlank())base else base.filter{stationMatches(it,query) || raw.any{s->s.stationId==it.id}}
        val ids=stations.map{it.id}.toSet()
        val rows=raw.filter{it.stationId in ids}
        val finance=can(NativePermission.FINANCE)
        val completed=rows.filter{it.status==NativeSessionStatus.COMPLETED}
        val charged=completed.nSum{it.chargedKgs?:N_ZERO}
        val paid=completed.nSum{it.paidKgs?:N_ZERO}
        val refunds=completed.nSum{it.refundKgs?:N_ZERO}
        val revenue=paid-refunds
        val paymentsComplete=completed.all{it.paidKgs!=null && it.refundKgs!=null}
        val chargesComplete=completed.all{it.chargedKgs!=null}
        val unpaid=completed.filter{it.paidKgs!=null}.nSum{((it.chargedKgs?:N_ZERO)-(it.paidKgs?:N_ZERO)).max(N_ZERO)}
        val sessionIds=rows.map{it.id}.toSet()
        val start=filter.period.start;val end=filter.period.endExclusive
        val payments=if(finance)state.payments.filter{it.stationId in ids && it.at>=start && it.at<end && (!sessionFiltered(filter) || it.sessionId in sessionIds)}.sortedByDescending{it.at} else emptyList()
        val expenses=if(finance && !sessionFiltered(filter))state.expenses.filter{it.stationId in ids && it.date>=filter.period.from && it.date<=filter.period.to}.sortedByDescending{it.date} else emptyList()
        val ledgerKnown=stations.all{it.organizationId in state.ledgerOrganizationIds}
        val ledger=if(finance && ledgerKnown)payments.nSum{it.amountKgs-it.refundKgs} else null
        val energy=completed.nSum{it.energyKwh}
        val generatedAt=now
        val freshStations=stations.filter{!isStale(it,generatedAt)}
        val summary=NativeSummary(stations.sumOf{it.apparatusCount},stations.sumOf{it.ports.size},stations.size,
            freshStations.sumOf{s->s.ports.count{it.status==NativePortStatus.BUSY && it.updatedAt>=generatedAt.minusSeconds(300)}},stations.count{isStale(it,generatedAt)},freshStations.nSum{s->s.ports.filter{it.status==NativePortStatus.BUSY && it.updatedAt>=generatedAt.minusSeconds(300)}.nSum{it.maxPowerKw*nd("0.42")}.min(s.powerKw)},
            completed.size,rows.count{it.status==NativeSessionStatus.CANCELLED},rows.count{it.status==NativeSessionStatus.ERROR},rows.count{it.status==NativeSessionStatus.ACTIVE},
            energy,completed.sumOf{it.durationSeconds},completed.map{it.customerId}.toSet().size,
            if(finance && chargesComplete)charged else null,if(finance && completed.all{it.paidKgs!=null})paid else null,if(finance && completed.all{it.refundKgs!=null})refunds else null,if(finance && paymentsComplete)revenue else null,if(finance && paymentsComplete && chargesComplete)unpaid else null,
            if(finance && chargesComplete && energy.signum()>0)charged.divide(energy,6,RoundingMode.HALF_UP) else null,ledger,ledger?.let{if(paymentsComplete)revenue-it else null},completed.count{it.paidKgs==null || it.refundKgs==null})
        val completedByStation=completed.groupBy{it.stationId}
        val stationRows=stations.map{s->val sessions=completedByStation[s.id].orEmpty();NativeStationReport(s,sessions.size,sessions.nSum{it.energyKwh},if(finance && sessions.all{it.revenueKgs!=null})sessions.nSum{it.revenueKgs?:N_ZERO} else null)}
        return NativeNetworkReport(account,filter,generatedAt,stationRows,rows.map{redact(it,finance)},payments,expenses,summary,
            if(finance && includeFinance)calculateMonthly(filter,stations,rows,selectedDeductions) else emptyList(),ids,can(NativePermission.EXPORT),finance,
            if(finance && !sessionFiltered(filter))state.monthBills.filter{it.stationId in ids && it.month.atDay(1)>=filter.period.from && it.month.atEndOfMonth()<=filter.period.to} else emptyList())
    }
    fun monthlyFinance(filter:NativeReportFilter,selectedDeductions:Set<NativeDeduction> = defaultDeductions):List<NativeMonthFinance> {
        requirePermission(NativePermission.FINANCE);require(!(NativeDeduction.PARTNER_SHARE in selectedDeductions && NativeDeduction.PAYOUT in selectedDeductions)){"Выберите долю партнёра или выплату: одновременный вычет учитывает один расход дважды"};val stations=selectedStations(filter);val sessions=selectedSessions(stations,filter)
        return calculateMonthly(filter,stations,sessions,selectedDeductions)
    }
    private fun calculateMonthly(filter:NativeReportFilter,stations:List<NativeStation>,sessions:List<NativeSession>,deductions:Set<NativeDeduction>):List<NativeMonthFinance> {
        require(!(NativeDeduction.PARTNER_SHARE in deductions && NativeDeduction.PAYOUT in deductions)){"Выберите долю партнёра или выплату: одновременный вычет учитывает один расход дважды"}
        if(stations.isEmpty())return emptyList()
        val ids=stations.map{it.id}.toSet()
        val months=generateSequence(YearMonth.from(filter.period.from)){it.plusMonths(1)}.takeWhile{it<=YearMonth.from(filter.period.to)}.toList()
        val completedByMonth=sessions.asSequence().filter{it.status==NativeSessionStatus.COMPLETED}.groupBy{YearMonth.from(it.accountingAt.atZone(PARTNER_ZONE))}
        val billsByMonth=state.monthBills.filter{it.stationId in ids}.groupBy{it.month}
        val expensesByMonth=state.expenses.filter{it.stationId in ids}.groupBy{YearMonth.from(it.date)}
        val subset=sessionFiltered(filter)
        return months.map{month->
            val from=maxOf(month.atDay(1),filter.period.from);val to=minOf(month.atEndOfMonth(),filter.period.to)
            val full=from==month.atDay(1) && to==month.atEndOfMonth() && !subset
            val rows=completedByMonth[month].orEmpty()
            val unknownPayments=rows.count{it.paidKgs==null || it.refundKgs==null}
            val paymentComplete=unknownPayments==0
            val chargesComplete=rows.all{it.chargedKgs!=null}
            val ratesComplete=rows.all{it.rates!=null}
            val revenue=if(paymentComplete)rows.nSum{it.revenueKgs!!} else null
            val charged=if(chargesComplete)rows.nSum{it.chargedKgs!!} else null
            val unpaid=if(paymentComplete && chargesComplete)rows.nSum{(it.chargedKgs!!-it.paidKgs!!).max(N_ZERO)} else null
            val energy=rows.nSum{it.energyKwh}
            val electricity=if(ratesComplete)rows.nSum{it.energyKwh*it.rates!!.electricityRate}.nMoney() else null
            val platform=if(ratesComplete && paymentComplete)rows.nSum{it.platformFeeKgs!!} else null
            val acquiring=if(ratesComplete && rows.all{it.paidKgs!=null})rows.nSum{it.acquiringFeeKgs!!} else null
            val tax=if(ratesComplete && paymentComplete)rows.nSum{it.taxKgs!!} else null
            val partnerPercent=if(ratesComplete && paymentComplete)rows.nSum{it.partnerShareKgs!!} else null
            val reserve=if(ratesComplete && paymentComplete)rows.nSum{it.reserveKgs!!} else null
            val days=generateSequence(from){it.plusDays(1)}.takeWhile{it<=to}.toList()
            val dailyRates=if(subset)emptyList() else stations.flatMap{s->days.map{rawTariffAt(s.id,it)}}
            val dailyRatesKnown=dailyRates.all{it!=null}
            val daysInMonth=BigDecimal(month.lengthOfMonth())
            // This daily allocation is explicitly an estimate. A receipt is never prorated.
            val fixedPartner=if(subset){if(rows.all{it.rates?.partnerFixedMonthly?.signum()==0})N_ZERO else null} else if(!dailyRatesKnown)null else dailyRates.nSum{it!!.partnerFixedMonthly.divide(daysInMonth,10,RoundingMode.HALF_UP)}.nMoney()
            val partner=if(partnerPercent!=null && fixedPartner!=null)partnerPercent+fixedPartner else null
            val rentEstimate=if(subset || !dailyRatesKnown)null else dailyRates.nSum{it!!.rentMonthly.divide(daysInMonth,10,RoundingMode.HALF_UP)}.nMoney()
            val bills=if(full)billsByMonth[month].orEmpty() else emptyList()
            val allBills=full && bills.map{it.stationId}.toSet()==ids
            val fact=if(allBills)bills.nSum{it.electricityKgs} else null
            val meter=if(allBills && bills.all{it.meterEnergyKwh!=null})bills.nSum{it.meterEnergyKwh!!} else null
            val costs=if(!subset)expensesByMonth[month].orEmpty().filter{it.date>=from && it.date<=to} else emptyList()
            val rentExpenses=costs.filter{it.kind==NativeExpenseKind.RENT}.nSum{it.amountKgs}
            val rentFact=if(allBills && bills.all{it.actualRentKgs!=null})bills.nSum{it.actualRentKgs!!}+rentExpenses else if(full && rentEstimate?.signum()==0)rentExpenses else null
            val other=if(subset)null else costs.filter{it.kind !in setOf(NativeExpenseKind.RENT,NativeExpenseKind.PAYOUT)}.nSum{it.amountKgs}
            val payout=if(subset || stations.any{it.organizationId !in state.ledgerOrganizationIds})null else costs.filter{it.kind==NativeExpenseKind.PAYOUT && it.paid}.nSum{it.amountKgs}
            val base=mapOf(NativeDeduction.PLATFORM to platform,NativeDeduction.ACQUIRING to acquiring,NativeDeduction.TAX to tax,NativeDeduction.OTHER to other,NativeDeduction.PARTNER_SHARE to partner,NativeDeduction.RESERVE to reserve,NativeDeduction.PAYOUT to payout)
            val selectedBase=deductions.filter{it in base}
            val baseKnown=selectedBase.all{base[it]!=null}
            val baseSum=if(baseKnown)selectedBase.nSum{base[it]!!} else null
            val estimateKnown=revenue!=null && baseSum!=null && (NativeDeduction.ELECTRICITY !in deductions || electricity!=null) && (NativeDeduction.RENT !in deductions || rentEstimate!=null)
            val estimatedProfit=if(estimateKnown)revenue!!-baseSum!!-(if(NativeDeduction.ELECTRICITY in deductions)electricity!! else N_ZERO)-(if(NativeDeduction.RENT in deductions)rentEstimate!! else N_ZERO) else null
            val actualKnown=revenue!=null && baseSum!=null && full && (NativeDeduction.ELECTRICITY !in deductions || fact!=null) && (NativeDeduction.RENT !in deductions || rentFact!=null)
            val actualProfit=if(actualKnown)revenue!!-baseSum!!-(if(NativeDeduction.ELECTRICITY in deductions)fact!! else N_ZERO)-(if(NativeDeduction.RENT in deductions)rentFact!! else N_ZERO) else null
            fun margin(profit:BigDecimal?):BigDecimal?=if(profit!=null && revenue!=null && revenue.signum()>0)profit.multiply(nd("100")).divide(revenue,2,RoundingMode.HALF_UP) else null
            // No trusted opening settlement balance exists in demo fixtures. Period movements are not debt.
            val outstanding:BigDecimal?=null
            NativeMonthFinance(month,ids,full,energy,charged,revenue,unpaid,electricity,fact,meter,meter?.minus(energy),platform,acquiring,tax,rentEstimate,rentFact,other,partner,reserve,payout,
                estimatedProfit,actualProfit,margin(estimatedProfit),margin(actualProfit),outstanding,bills.size,bills.count{it.paid},deductions.toSet(),
                when{!paymentComplete->"Нет подтверждённых данных об оплате $unknownPayments сессий; прибыль неизвестна";!ratesComplete->"Нет сохранённых ставок части сессий";!full->"Выбран неполный месяц или фильтр сессий: фактический месячный счёт не распределяется";!allBills->"Не по всем точкам внесён фактический счёт";rentFact==null->"Не указана фактическая аренда";else->null},paymentComplete,unknownPayments)
        }
    }
    fun recordExport(report:NativeNetworkReport) {
        requirePermission(NativePermission.EXPORT);requirePermission(NativePermission.REPORT)
        if(report.account.id!=account.id || report.account.role!=account.role || !report.exportAllowed || (report.financeVisible && !can(NativePermission.FINANCE)))throw SecurityException("Экспорт создан для другой учётной записи или прав")
        val allowed=scopedStations().associateBy{it.id}
        val reportIds=report.stations.map{it.station.id}.toSet()
        if(reportIds!=report.scopedStationIds || report.stations.any{row->val point=allowed[row.station.id];point==null || point.organizationId!=row.station.organizationId || point.ownerId!=row.station.ownerId} ||
            report.sessions.any{it.stationId !in reportIds || it.accountingAt !in report.filter.period} || report.payments.any{it.stationId !in reportIds || it.at !in report.filter.period} ||
            report.expenses.any{it.stationId !in reportIds || it.date<report.filter.period.from || it.date>report.filter.period.to} ||
            report.monthBills.any{it.stationId !in reportIds || it.month.atDay(1)<report.filter.period.from || it.month.atEndOfMonth()>report.filter.period.to})throw SecurityException("Экспорт выходит за текущую область доступа")
        val orgs=report.stations.map{it.station.organizationId}.distinct()
        orgs.forEach{org->val ids=report.stations.filter{it.station.organizationId==org}.map{it.station.id}.toSet();log(org,ids,"Экспорт Excel",null,"${report.filter}; сессий=${report.sessions.count{it.stationId in ids}}; станций=${ids.size}","Выгрузка видимых демонстрационных данных")}
    }
    /** No hardware adapter or payment command exists in this demo boundary. */
    fun command(stationId:String,command:String):Nothing {station(stationId);throw UnsupportedOperationException("ДЕМО: команды станции и реальные платежи отключены ($command)")}
    companion object {
        val defaultDeductions:Set<NativeDeduction> = NativeDeduction.entries.filter{it!=NativeDeduction.PAYOUT}.toSet()
        val accounts:List<PartnerAccount> = listOf(
            PartnerAccount("demo-driver","Демо-водитель",PartnerRole.DRIVER),
            PartnerAccount("demo-owner-a","ОсОО «Кубат Север» · ДЕМО",PartnerRole.OWNER),
            PartnerAccount("demo-owner-b","ОсОО «Кубат Юг» · ДЕМО",PartnerRole.OWNER),
            PartnerAccount("demo-admin","QUBAT EV · ДЕМО администратор",PartnerRole.ADMIN),
            PartnerAccount("demo-employee","Демо-сотрудник",PartnerRole.EMPLOYEE),
            PartnerAccount("demo-accountant","Демо-бухгалтер",PartnerRole.ACCOUNTANT),
            PartnerAccount("demo-observer","Демо-наблюдатель",PartnerRole.OBSERVER))
        fun seed(today:LocalDate=LocalDate.now(PARTNER_ZONE)):NativePartnerState {
            val state=NativePartnerState(today)
            val time=Instant.now()
            state.organizations.addAll(listOf(NativeOrganization("demo-org-north","Кубат Север · ДЕМО","demo-owner-a"),NativeOrganization("demo-org-south","Кубат Юг · ДЕМО","demo-owner-b")))
            state.brands.addAll(listOf(NativeBrand("brand-north","demo-org-north","QUBAT Север · ДЕМО"),NativeBrand("brand-south","demo-org-south","QUBAT Юг · ДЕМО","#43A0D6")))
            val names=listOf("Бишкек · Учебная 1","Каракол · Учебная 2","Токмок · Учебная 3","Ош · Учебная 4","Джалал-Абад · Учебная 5")
            val cities=listOf("Бишкек","Каракол","Токмок","Ош","Джалал-Абад")
            val keys=listOf("a-1","a-2","a-3","b-1","b-2")
            val latitudes=listOf("42.8746","42.4907","42.8419","40.5283","40.9333")
            val longitudes=listOf("74.5698","78.3936","75.3015","72.7985","72.9833")
            keys.forEachIndexed{index,key->
                val north=index<3;val id="partner-demo-$key";val org=if(north)"demo-org-north" else "demo-org-south"
                val stamp=if(index==4)time.minusSeconds(7200) else time
                val ports=(1..2).map{number->NativePort("$id-port-$number",number,status=when{index==4->NativePortStatus.UNAVAILABLE;index==2 && number==2->NativePortStatus.FAULT;index==0 && number==1->NativePortStatus.BUSY;else->NativePortStatus.FREE},updatedAt=stamp)}
                state.stations.add(NativeStation(id,org,if(north)"demo-owner-a" else "demo-owner-b","QUBAT ${index+1} · ДЕМО",names[index],cities[index],if(north)"group-north" else "group-south","location-$key",if(north)"brand-north" else "brand-south",serialNumber="DEMO-SN-${index+1}",ocppId="DEMO-OCPP-${index+1}",latitude=nd(latitudes[index]),longitude=nd(longitudes[index]),ports=ports,lastSeen=stamp,errorReason=if(index==4)"Нет демонстрационного сигнала 2 часа" else if(index==2)"Порт 2: демонстрационная ошибка" else null,partnerId="contract-partner-$key",partnerName="Демо-партнёр ${index+1}",contractId="DEMO-CONTRACT-$key"))
                val old=NativeTariff("tariff-$key-v1",id,today.minusYears(2),today.minusDays(46),electricityRate=nd("4.25"),rentMonthly=nd("3000"),partnerRate=nd("0.15"),reserveRate=nd("0.01"))
                val current=old.copy(id="tariff-$key-v2",effectiveFrom=today.minusDays(45),effectiveTo=null,electricityRate=nd("4.50"),customerRate=nd("13.50"))
                state.tariffs.addAll(listOf(old,current))
            }
            state.ledgerOrganizationIds.add("demo-org-north")
            val northIds=state.stations.filter{it.organizationId=="demo-org-north"}.map{it.id}.toSet()
            state.members.addAll(listOf(
                NativeMember("demo-employee","demo-org-north","Демо-сотрудник","employee@example.invalid",PartnerRole.EMPLOYEE,setOf("partner-demo-a-1"),today.minusYears(1)),
                NativeMember("demo-accountant","demo-org-north","Демо-бухгалтер","accountant@example.invalid",PartnerRole.ACCOUNTANT,northIds,today.minusYears(1)),
                NativeMember("demo-observer","demo-org-north","Демо-наблюдатель","observer@example.invalid",PartnerRole.OBSERVER,setOf("partner-demo-a-1","partner-demo-a-2"),today.minusYears(1))))
            val fixtureDates=((0L..89L).map{today.minusDays(it)}+(1L..9L).flatMap{offset->val m=YearMonth.from(today).minusMonths(offset);listOf(m.atDay(1),m.atDay(15),m.atEndOfMonth())}).distinct().sorted()
            state.stations.forEachIndexed{index,station->
                fixtureDates.forEachIndexed{dayIndex,day->
                    (0..2).forEach{slot->
                        val start=day.atTime(8+slot*5,10+index).atZone(PARTNER_ZONE).toInstant()
                        val active=day==today && index==0 && slot==2
                        val status=when{active->NativeSessionStatus.ACTIVE;slot==2 && dayIndex%3==0->NativeSessionStatus.ERROR;slot==2->NativeSessionStatus.CANCELLED;else->NativeSessionStatus.COMPLETED}
                        val end=if(active)null else start.plusSeconds(if(status==NativeSessionStatus.COMPLETED)1800L+index*120 else 120)
                        val energy=if(status==NativeSessionStatus.COMPLETED)nd("18.125")+BigDecimal(index*2+dayIndex%7+slot) else if(active)nd("7.125") else N_ZERO
                        val tariff=state.tariffs.last{it.stationId==station.id && it.effectiveFrom<=day && (it.effectiveTo==null || day<=it.effectiveTo)}
                        val rates=NativeRateSnapshot(tariff.id,tariff.customerRate,tariff.electricityRate,tariff.platformRate,tariff.acquiringRate,tariff.taxRate,tariff.rentMonthly,tariff.partnerRate,tariff.reserveRate,tariff.partnerFixedMonthly)
                        val charged=(energy*tariff.customerRate).nMoney()
                        val unknown=status==NativeSessionStatus.COMPLETED && index==4 && (dayIndex+index+slot)%11==0
                        val unpaid=status==NativeSessionStatus.COMPLETED && (dayIndex+index+slot)%9==2
                        val paid=if(unknown)null else if(status==NativeSessionStatus.COMPLETED && !unpaid)charged else N_ZERO
                        val refund=if(paid!=null && paid.signum()>0 && slot==0 && dayIndex%13==3)nd("13.50") else N_ZERO
                        val paymentStatus=when{unknown->NativePaymentStatus.UNKNOWN;refund.signum()>0->NativePaymentStatus.REFUNDED;paid!=null && paid.signum()>0->NativePaymentStatus.PAID;else->NativePaymentStatus.UNPAID}
                        val id="DEMO-${station.id}-$day-$slot"
                        state.sessions.add(NativeSession(id,station.id,station.ports[slot%2].id,"customer-${(dayIndex+slot+index)%17+1}",start,end,energy,status,paymentStatus,rates,charged,paid,if(unknown)null else refund,errorReason=when(status){NativeSessionStatus.ERROR->"ДЕМО: прервана связь";NativeSessionStatus.CANCELLED->"ДЕМО: отменено клиентом";else->null},updatedAt=time))
                        if(station.organizationId in state.ledgerOrganizationIds && paid!=null && paid.signum()>0 && end!=null)state.payments.add(NativePayment("payment-$id",station.id,id,end,paid,refund,(paid*tariff.acquiringRate).nMoney()))
                    }
                }
                (0L..9L).forEach{offset->val month=YearMonth.from(today).minusMonths(offset)
                    state.expenses.add(NativeExpense("expense-${station.id}-$month",station.id,month.atDay(10),NativeExpenseKind.COMMUNICATION,nd("450.00"),"Демонстрационная связь",paid=true))
                    state.expenses.add(NativeExpense("payout-${station.id}-$month",station.id,month.atDay(20),NativeExpenseKind.PAYOUT,nd("900.00"),"Демонстрационный перевод, не банковское подтверждение",paid=station.organizationId in state.ledgerOrganizationIds))
                    if(offset>0 && index<3 && offset%3L!=0L) {
                        val monthEnergy=state.sessions.filter{it.stationId==station.id && it.status==NativeSessionStatus.COMPLETED && YearMonth.from(it.accountingAt.atZone(PARTNER_ZONE).toLocalDate())==month}.nSum{it.energyKwh}
                        state.monthBills.add(NativeMonthBill("bill-${station.id}-$month",station.id,month,(monthEnergy*nd("4.70")).nMoney(),"DEMO-$month-${index+1}",month.atEndOfMonth(),paid=offset>1,meterEnergyKwh=monthEnergy+nd("3.25"),actualRentKgs=nd("3000"),comment="ДЕМО: не реальная квитанция",updatedAt=time,authorId="demo-owner-a"))
                    }
                }
            }
            state.grants.add(NativeGrant("grant-demo","demo-org-north",setOf("partner-demo-a-1"),"customer-1",NativeGrantKind.PERCENT,nd("10"),today.minusDays(7),today.plusDays(7),3,1,"demo-owner-a","ДЕМО: локальное разрешение"))
            state.notifications.add(NativeNotificationSettings("notify-north","demo-org-north",northIds))
            state.audit.add(NativeAudit("audit-seed","demo-org-north",northIds,time,"demo-admin","Создание демонстрационной сети","","5 аппаратов, 10 портов","ДЕМО, без подключения к оборудованию"))
            return state
        }
    }
}

/** Copy list containers before serializing off the UI thread; rows are immutable value objects. */
fun NativePartnerState.snapshot():NativePartnerState=copy(
    organizations=organizations.toMutableList(),stations=stations.toMutableList(),tariffs=tariffs.toMutableList(),sessions=sessions.toMutableList(),payments=payments.toMutableList(),
    expenses=expenses.toMutableList(),monthBills=monthBills.toMutableList(),members=members.toMutableList(),grants=grants.toMutableList(),brands=brands.toMutableList(),
    notifications=notifications.toMutableList(),audit=audit.toMutableList(),ledgerOrganizationIds=ledgerOrganizationIds.toMutableSet())
