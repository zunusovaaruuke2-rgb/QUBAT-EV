package kg.qubat.ev

import java.math.BigDecimal

/** Installed total entered directly or as a sum, e.g. 80 + 120.
 * Exact decimal arithmetic; never sums connector limits or assumes identical apparatuses.
 * Only the resulting existing point.powerKw is stored, not a per-apparatus catalogue.
 */
internal fun pointTotalPower(value:String):BigDecimal? {
    if(value.isBlank() || value.length>300)return null
    var total=BigDecimal.ZERO
    for(raw in value.split('+')) {
        val term=raw.trim().replace(',','.')
        if(!pointPowerTerm.matches(term))return null
        val power=term.toBigDecimalOrNull()?.takeIf{it.signum()>0}?:return null
        total=total.add(power)
    }
    return total
}
private val pointPowerTerm=Regex("(?:\\d+(?:\\.\\d*)?|\\.\\d+)")
