package kg.qubat.ev

import androidx.compose.runtime.Immutable
import java.math.BigInteger
import kotlin.math.ceil
import kotlin.math.roundToLong

@Immutable data class HistoryEntry(val title:String,val amountMinor:Long,val timestamp:Long,val detail:String)
@Immutable data class ChargeSession(val stationId:String,val startedAt:Long,val budgetMinor:Long,val connectorId:String="",val vehicleNumber:Int=1)
@Immutable data class PowerSample(val seconds:Long,val powerKw:Double)
@Immutable data class RunningCharge(val session:ChargeSession,val progress:ChargeResult=ChargeResult(0,0.0,0,false),val updatedAt:Long=session.startedAt,val powerHistory:List<PowerSample> = emptyList())
@Immutable data class AppState(
    val balanceMinor:Long=150000,val charges:List<RunningCharge> = emptyList(),
    val history:List<HistoryEntry> = emptyList(),val completedConnectorIds:Set<String> = emptySet(),
    val lastProgress:ChargeResult=ChargeResult(0,0.0,0,false)
) {
    init {
        require(balanceMinor>=0 && charges.size<=2 && charges.map{it.session.connectorId}.distinct().size==charges.size)
        var reserved=0L
        charges.forEach{require(it.progress.costMinor>=0 && it.progress.costMinor<=balanceMinor-reserved);reserved+=it.progress.costMinor}
    }
    val availableBalanceMinor:Long get()=balanceMinor-charges.sumOf{it.progress.costMinor}
    val active:Boolean get()=charges.isNotEmpty()
}
data class ChargeStartResult(val state:AppState,val started:Boolean)

/** Two local DEMO sessions share one wallet; a physical apparatus shares its 80 kW across its ports. */
object ChargingEngine {
    const val MAX_CARS=2
    const val BATTERY_ENERGY_KWH=48.0 // 60 kWh battery, initial charge 20%.
    private fun site(charge:RunningCharge)=DemoData.stations.first{it.id==charge.session.stationId}
    private fun apparatus(charge:RunningCharge)=site(charge).chargers.first{unit->unit.connectors.any{it.id==charge.session.connectorId}}
    fun powerKw(state:AppState,charge:RunningCharge):Double {
        val unit=apparatus(charge)
        val peers=state.charges.count{it.session.stationId==charge.session.stationId && unit.connectors.any{p->p.id==it.session.connectorId}}
        return unit.powerKw.toDouble()/peers.coerceAtLeast(1)
    }
    private fun refreshPowers(state:AppState):AppState=state.copy(charges=state.charges.map{charge->
        val power=powerKw(state,charge)
        if(charge.powerHistory.lastOrNull()?.powerKw==power)charge else charge.copy(powerHistory=(charge.powerHistory+PowerSample(charge.progress.seconds,power)).let{if(it.size>120)listOf(it.first())+it.takeLast(119) else it})
    })
    fun start(state:AppState,stationId:String,connectorId:String?,now:Long):ChargeStartResult {
        val current=advance(state,now)
        val station=DemoData.stations.firstOrNull{it.id==stationId} ?: return ChargeStartResult(current,false)
        if(current.charges.size>=MAX_CARS || current.availableBalanceMinor<=0)return ChargeStartResult(current,false)
        val occupied=current.charges.map{it.session.connectorId}.toSet()+current.completedConnectorIds
        val port=station.connectors.firstOrNull{(connectorId==null || it.id==connectorId) && it.state==ConnectorState.FREE && it.id !in occupied} ?: return ChargeStartResult(current,false)
        val number=(1..MAX_CARS).first{n->current.charges.none{it.session.vehicleNumber==n}}
        val charge=RunningCharge(ChargeSession(station.id,now,current.balanceMinor,port.id,number))
        return ChargeStartResult(refreshPowers(current.copy(charges=current.charges+charge)),true)
    }
    fun advance(state:AppState,now:Long):AppState {
        if(!state.active)return state
        val current=refreshPowers(state)
        // A battery can finish while the app is closed. Split catch-up at that
        // event so the other port receives the freed power for the remaining time.
        val nextFullAt=current.charges.minOf{charge->
            val seconds=ceil((BATTERY_ENERGY_KWH-charge.progress.energyKwh).coerceAtLeast(0.0)*3600/powerKw(current,charge)).toLong()
            if(charge.updatedAt>Long.MAX_VALUE-seconds*1000)Long.MAX_VALUE else charge.updatedAt+seconds*1000
        }
        if(nextFullAt<now)return advance(advance(current,nextFullAt),now)
        val desired=current.charges.map{charge->
            val elapsed=((now-charge.updatedAt).coerceAtLeast(0)/1000)
            val power=powerKw(current,charge)
            val duration=ceil((BATTERY_ENERGY_KWH-charge.progress.energyKwh).coerceAtLeast(0.0)*3600/power).toLong()
            val seconds=minOf(elapsed,duration)
            val energy=(charge.progress.energyKwh+power*seconds/3600).coerceAtMost(BATTERY_ENERGY_KWH).let{if(it>=BATTERY_ENERGY_KWH-1e-9)BATTERY_ENERGY_KWH else it}
            val cost=(energy*site(charge).tariffMinor).roundToLong().coerceAtLeast(charge.progress.costMinor)
            charge.copy(progress=ChargeResult(charge.progress.seconds+seconds,energy,cost,false),updatedAt=charge.updatedAt+elapsed*1000)
        }
        val demands=desired.mapIndexed{i,c->c.progress.costMinor-current.charges[i].progress.costMinor}
        val total=demands.fold(BigInteger.ZERO){sum,v->sum+BigInteger.valueOf(v)}
        val free=current.availableBalanceMinor
        val allocations=if(total<=BigInteger.valueOf(free))demands else {
            val shares=demands.map{BigInteger.valueOf(free).multiply(BigInteger.valueOf(it)).divide(total).toLong()}.toMutableList()
            var left=free-shares.sum()
            for(i in shares.indices)if(left>0 && shares[i]<demands[i]){shares[i]++;left--}
            shares
        }
        val updated=desired.mapIndexed{i,charge->
            if(allocations[i]==demands[i])charge else {
                val previous=current.charges[i];val cost=previous.progress.costMinor+allocations[i]
                val energy=(cost.toDouble()/site(charge).tariffMinor).coerceIn(previous.progress.energyKwh,BATTERY_ENERGY_KWH)
                val seconds=minOf(charge.progress.seconds,previous.progress.seconds+ceil((energy-previous.progress.energyKwh)*3600/powerKw(current,previous)).toLong())
                charge.copy(progress=ChargeResult(seconds,energy,cost,true))
            }
        }
        val next=current.copy(charges=updated)
        val exhausted=next.availableBalanceMinor==0L
        val finished=updated.filter{exhausted || it.progress.depleted || it.progress.energyKwh>=BATTERY_ENERGY_KWH-1e-9}.map{it.session.connectorId}.toSet()
        return if(finished.isEmpty())next else settle(next,finished,now)
    }
    fun stop(state:AppState,connectorId:String,now:Long):AppState {
        val current=advance(state,now)
        return if(current.charges.none{it.session.connectorId==connectorId})current else settle(current,setOf(connectorId),now)
    }
    private fun settle(state:AppState,ids:Set<String>,now:Long):AppState {
        val finished=state.charges.filter{it.session.connectorId in ids}
        val entries=finished.map{c->HistoryEntry("Демо-зарядка · ${site(c).city}",-c.progress.costMinor,now,
            "Автомобиль ${c.session.vehicleNumber} · аппарат ${apparatus(c).number} · GB/T ${site(c).connectors.first{it.id==c.session.connectorId}.number} · "+String.format(java.util.Locale("ru"),"%.3f кВт·ч · %d сек",c.progress.energyKwh,c.progress.seconds))}
        return refreshPowers(state.copy(balanceMinor=state.balanceMinor-finished.sumOf{it.progress.costMinor},charges=state.charges.filter{it.session.connectorId !in ids},
            history=(entries+state.history).take(100),completedConnectorIds=state.completedConnectorIds+ids,lastProgress=finished.lastOrNull()?.progress ?: state.lastProgress))
    }
}

@Immutable data class ChargePercentSample(val seconds:Long,val percent:Double)
/** Integrate each historical power interval; anchor the endpoint to the billed energy. */
fun chargePercentTimeline(progress:ChargeResult,history:List<PowerSample>):List<ChargePercentSample> {
    val samples=history.filter{it.seconds in 0..progress.seconds}.sortedBy{it.seconds}
    val result=mutableListOf(ChargePercentSample(0,20.0))
    var seconds=0L;var energy=0.0;var power=samples.firstOrNull()?.powerKw ?: 0.0
    samples.forEach {sample->
        energy+=(sample.seconds-seconds)*power/3600
        if(sample.seconds>0)result+=ChargePercentSample(sample.seconds,(20+energy/60*100).coerceIn(20.0,100.0))
        seconds=sample.seconds;power=sample.powerKw
    }
    val endpoint=ChargePercentSample(progress.seconds,(20+progress.energyKwh/60*100).coerceIn(20.0,100.0))
    if(result.last().seconds==endpoint.seconds)result[result.lastIndex]=endpoint else result+=endpoint
    return result
}
