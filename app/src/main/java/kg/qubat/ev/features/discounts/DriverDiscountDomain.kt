package kg.qubat.ev

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

internal data class DiscountDriver(val id:String,val phone:String,val name:String)
internal data class DriverDiscount(val id:String,val organizationId:String,val driverId:String,val pointIds:Set<String>,
    val percent:Int,val startsOn:LocalDate,val endsOn:LocalDate?,val enabled:Boolean=true,val authorId:String="")
internal data class DiscountDelegation(val organizationId:String,val memberId:String,val pointIds:Set<String>,val maxPercent:Int)
internal data class DiscountLedger(val drivers:List<DiscountDriver> = emptyList(),val discounts:List<DriverDiscount> = emptyList(),
    val delegations:List<DiscountDelegation> = emptyList(),val audit:List<String> = emptyList())
internal data class DriverDiscountQuote(val ruleId:String,val pointId:String,val stationId:String,val percent:Int,val baseMinor:Long,val rateMinor:Long,val endsOn:LocalDate?)

/** Explicit bindings for the five pre-existing demo fixtures. Never infer ownership from names/cities. */
internal val demoDiscountBindings = mapOf(
    "partner-demo-a-1" to ("demo-org-north" to "demo-bishkek"),
    "partner-demo-a-2" to ("demo-org-north" to "demo-karakol"),
    "partner-demo-a-3" to ("demo-org-north" to "demo-tokmok"),
    "partner-demo-b-1" to ("demo-org-south" to "demo-osh"),
    "partner-demo-b-2" to ("demo-org-south" to "demo-jalal")
)
internal fun discountPhone(value:String):String? {
    val compact=value.trim().filterNot{it in " ()-−"}
    if(!Regex("\\+?[0-9]{9,15}").matches(compact))return null
    val digits=compact.removePrefix("+")
    return when {digits.length==12 && digits.startsWith("996")->"+$digits"
        digits.length==10 && digits.startsWith("0")->"+996"+digits.drop(1)
        digits.length in 10..15 && value.trim().startsWith("+")->"+$digits"
        else->null}
}
internal fun discountedRate(baseMinor:Long,percent:Int):Long {
    require(baseMinor>0 && percent in 1..99)
    return BigDecimal.valueOf(baseMinor).multiply(BigDecimal.valueOf((100-percent).toLong()))
        .divide(BigDecimal("100"),0,RoundingMode.HALF_UP).longValueExact().coerceAtLeast(1)
}
internal fun resolveDriverDiscounts(ledger:DiscountLedger,driverId:String?,points:List<NativeStation>,catalog:List<Station>,today:LocalDate):Map<String,DriverDiscountQuote> {
    if(driverId==null || ledger.drivers.none{it.id==driverId})return emptyMap()
    val candidates=ledger.discounts.filter{it.driverId==driverId && it.enabled && it.percent in 1..99 && it.startsOn<=today && (it.endsOn==null || today<=it.endsOn)}
    return points.filter{!it.archived}.mapNotNull{point->
        val binding=demoDiscountBindings[point.id]?.takeIf{it.first==point.organizationId}?:return@mapNotNull null
        val station=catalog.firstOrNull{it.id==binding.second}?:return@mapNotNull null
        val rule=candidates.filter{it.organizationId==point.organizationId && point.id in it.pointIds}
            .sortedWith(compareByDescending<DriverDiscount>{it.percent}.thenBy{it.id}).firstOrNull()?:return@mapNotNull null
        station.id to DriverDiscountQuote(rule.id,point.id,station.id,rule.percent,station.tariffMinor,discountedRate(station.tariffMinor,rule.percent),rule.endsOn)
    }.toMap()
}
