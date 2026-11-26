package kg.qubat.ev

import java.time.Instant
import java.util.UUID

/** Local demo authorization. A production server must enforce the same checks itself. */
internal class DriverDiscountService(private val repo:NativePartnerRepository,private val store:DiscountLedgerStore) {
    val managesStaff:Boolean get()=repo.account.role in setOf(PartnerRole.OWNER,PartnerRole.ADMIN) && repo.can(NativePermission.MANAGE_MEMBERS)
    fun points(ledger:DiscountLedger=store.load()):List<NativeStation> {
        if(!repo.can(NativePermission.VIEW))return emptyList()
        val mapped=repo.stations().filter{demoDiscountBindings[it.id]?.first==it.organizationId}
        if(managesStaff)return mapped
        if(repo.account.role!=PartnerRole.EMPLOYEE)return emptyList()
        return mapped.filter{point->ledger.delegations.any{it.memberId==repo.account.id && it.organizationId==point.organizationId && point.id in it.pointIds && it.maxPercent in 1..99}}
    }
    fun canOpen():Boolean=managesStaff || points().isNotEmpty()
    fun maxPercent(pointId:String,ledger:DiscountLedger=store.load()):Int {
        if(points(ledger).none{it.id==pointId})return 0
        return if(managesStaff)99 else ledger.delegations.filter{it.memberId==repo.account.id && pointId in it.pointIds && repo.stations().any{p->p.id==pointId && p.organizationId==it.organizationId}}.maxOfOrNull{it.maxPercent}?:0
    }
    fun rules(ledger:DiscountLedger=store.load()):List<DriverDiscount> {
        val allowed=points(ledger).map{it.id}.toSet()
        return ledger.discounts.filter{rule->rule.pointIds.all{id->repo.stations().any{p->p.id==id && p.organizationId==rule.organizationId}} && rule.pointIds.isNotEmpty() && rule.pointIds.all{p->p in allowed} && rule.pointIds.all{p->rule.percent<=maxPercent(p,ledger)}}
    }
    fun save(originalId:String?,driverId:String,pointIds:Set<String>,percent:Int,endsOn:java.time.LocalDate?,enabled:Boolean=true) {
        store.update{ledger->
            val allowed=points(ledger).associateBy{it.id}
            require(pointIds.isNotEmpty() && pointIds.all{it in allowed}){"Выберите только разрешённые точки"}
            require(percent in 1..99 && pointIds.all{percent<=maxPercent(it,ledger)}){"Скидка превышает разрешённый предел"}
            val org=allowed.getValue(pointIds.first()).organizationId
            require(pointIds.all{allowed.getValue(it).organizationId==org}){"Выберите точки одной организации"}
            require(ledger.drivers.any{it.id==driverId}){"Водитель не найден"}
            val today=repo.authorizationDate
            require(!enabled || endsOn==null || endsOn>=today){"Проверьте окончание скидки"}
            val old=originalId?.let{id->ledger.discounts.firstOrNull{it.id==id}?:error("Скидка не найдена")}
            if(old!=null)require(old in rules(ledger) && old.organizationId==org){"Нет права менять эту скидку"}
            val value=DriverDiscount(old?.id?:UUID.randomUUID().toString(),org,driverId,pointIds,percent,old?.startsOn?:today,endsOn,enabled,repo.account.id)
            ledger.copy(discounts=ledger.discounts.filterNot{it.id==value.id}+value,
                audit=(ledger.audit+"${Instant.now()} · ${repo.account.id} · скидка ${value.id} · $percent% · ${pointIds.joinToString()}").takeLast(500))
        }
    }
    fun delegate(memberId:String,pointIds:Set<String>,maxPercent:Int?) {
        require(managesStaff){"Назначать сотрудников может только владелец"}
        store.update{ledger->
            val member=repo.members().firstOrNull{it.id==memberId}?:error("Сотрудник недоступен")
            val today=repo.authorizationDate
            require(member.role==PartnerRole.EMPLOYEE && !member.revoked && member.startsOn<=today && (member.endsOn==null || member.endsOn>=today)){"Выберите действующего сотрудника"}
            if(maxPercent!=null) {
                require(maxPercent in 1..99 && pointIds.isNotEmpty()){"Укажите точки и предел от 1 до 99%"}
                val allowed=points(ledger)
                require(pointIds.all{id->id in member.stationIds && allowed.any{it.id==id && it.organizationId==member.organizationId}}){"Точка не назначена сотруднику"}
            }
            val kept=ledger.delegations.filterNot{it.memberId==memberId}
            ledger.copy(delegations=if(maxPercent==null)kept else kept+DiscountDelegation(member.organizationId,memberId,pointIds,maxPercent),
                audit=(ledger.audit+"${Instant.now()} · ${repo.account.id} · доступ к скидкам · $memberId · ${maxPercent?:0}%").takeLast(500))
        }
    }
}
