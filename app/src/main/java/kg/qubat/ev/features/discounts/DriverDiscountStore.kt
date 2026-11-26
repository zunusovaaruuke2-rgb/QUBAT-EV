package kg.qubat.ev

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

internal interface DiscountLedgerStore {
    fun load():DiscountLedger
    fun update(transform:(DiscountLedger)->DiscountLedger):DiscountLedger
}

/** Separate versioned JSON: never change Java-serialized partner snapshots or wallet keys. */
internal class DriverDiscountStore(context:Context):DiscountLedgerStore {
    val preferences=context.applicationContext.getSharedPreferences("qubat_driver_discounts_v1",0)
    private val profile=context.applicationContext.getSharedPreferences("qubat_profile",0)
    companion object { private val lock=Any() }
    override fun load():DiscountLedger=synchronized(lock) {
        val raw=preferences.getString("ledger",null)?:return@synchronized DiscountLedger()
        require(raw.length<=2_000_000){"Слишком много записей скидок"}
        val root=JSONObject(raw);require(root.getInt("schema")==1)
        fun array(key:String)=root.optJSONArray(key)?:JSONArray()
        fun ids(array:JSONArray)= (0 until array.length()).map{array.getString(it)}.toSet()
        val drivers=array("drivers");val rules=array("discounts");val delegates=array("delegations");val audit=array("audit")
        require(drivers.length()<=10000 && rules.length()<=5000 && delegates.length()<=2000 && audit.length()<=500)
        DiscountLedger(
            (0 until drivers.length()).map{val o=drivers.getJSONObject(it);DiscountDriver(o.getString("id"),o.getString("phone"),o.getString("name"))},
            (0 until rules.length()).map{val o=rules.getJSONObject(it);DriverDiscount(o.getString("id"),o.getString("org"),o.getString("driver"),ids(o.getJSONArray("points")),o.getInt("percent"),LocalDate.parse(o.getString("from")),o.optString("to").takeIf(String::isNotBlank)?.let(LocalDate::parse),o.getBoolean("enabled"),o.getString("author"))},
            (0 until delegates.length()).map{val o=delegates.getJSONObject(it);DiscountDelegation(o.getString("org"),o.getString("member"),ids(o.getJSONArray("points")),o.getInt("max"))},
            (0 until audit.length()).map{audit.getString(it)}
        )
    }
    override fun update(transform:(DiscountLedger)->DiscountLedger):DiscountLedger=synchronized(lock) {
        val next=transform(load())
        require(next.drivers.size<=10000 && next.discounts.size<=5000 && next.delegations.size<=2000)
        val root=JSONObject().put("schema",1)
        root.put("drivers",JSONArray().also{a->next.drivers.forEach{d->a.put(JSONObject().put("id",d.id).put("phone",d.phone).put("name",d.name))}})
        root.put("discounts",JSONArray().also{a->next.discounts.forEach{d->a.put(JSONObject().put("id",d.id).put("org",d.organizationId).put("driver",d.driverId).put("points",JSONArray(d.pointIds.toList())).put("percent",d.percent).put("from",d.startsOn.toString()).put("to",d.endsOn?.toString().orEmpty()).put("enabled",d.enabled).put("author",d.authorId))}})
        root.put("delegations",JSONArray().also{a->next.delegations.forEach{d->a.put(JSONObject().put("org",d.organizationId).put("member",d.memberId).put("points",JSONArray(d.pointIds.toList())).put("max",d.maxPercent))}})
        root.put("audit",JSONArray(next.audit.takeLast(500)))
        val raw=root.toString();require(raw.length<=2_000_000)
        preferences.edit().putString("ledger",raw).apply()
        next
    }
    fun syncLocalDriver():String?=synchronized(lock) {
        val phone=discountPhone(profile.getString("phone","").orEmpty())?:return@synchronized null
        val id=profile.getString("discount_driver_id",null)?:UUID.randomUUID().toString().also{profile.edit().putString("discount_driver_id",it).apply()}
        val name=profile.getString("name","").orEmpty().ifBlank{"Водитель"}
        val before=load()
        require(before.drivers.none{it.phone==phone && it.id!=id}){"Номер уже занят другим демо-профилем"}
        val driver=DiscountDriver(id,phone,name)
        if(before.drivers.firstOrNull{it.id==id}!=driver)update{it.copy(drivers=it.drivers.filterNot{d->d.id==id}+driver)}
        id
    }
}
