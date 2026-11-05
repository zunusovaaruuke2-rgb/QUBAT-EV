package kg.qubat.ev

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class ChargingViewModel(app:Application):AndroidViewModel(app) {
    private val prefs=app.getSharedPreferences("qubat_demo_v1",0)
    private val _state=MutableStateFlow(restore())
    val state=_state.asStateFlow()
    init {
        tick()
        viewModelScope.launch {state.map{it.active}.distinctUntilChanged().collectLatest{active->if(active)while(true){delay(1000);tick()}}}
    }
    private fun restore():AppState=runCatching {
        val json=JSONObject(prefs.getString("state","{}")!!)
        val items=json.optJSONArray("history") ?: JSONArray()
        val history=(0 until minOf(items.length(),100)).map{val item=items.getJSONObject(it);HistoryEntry(item.getString("title"),item.getLong("amount"),item.getLong("date"),item.getString("detail"))}
        val balance=json.optLong("balance",150000).coerceAtLeast(0)
        val known=DemoData.stations.flatMap{it.connectors}.map{it.id}.toSet()
        val completedJson=json.optJSONArray("completed") ?: JSONArray()
        val completed=(0 until minOf(completedJson.length(),known.size)).map{completedJson.optString(it)}.filter{it in known}.toSet()
        val array=json.optJSONArray("charges") ?: JSONArray().also{a->json.optJSONObject("session")?.let{a.put(it)}}
        val charges=mutableListOf<RunningCharge>()
        for(i in 0 until minOf(array.length(),2))runCatching {
            val item=array.getJSONObject(i)
            val station=DemoData.stations.firstOrNull{it.id==item.getString("id")} ?: return@runCatching
            val savedId=item.optString("connector","")
            val port=if(savedId.isNotBlank())station.connectors.firstOrNull{it.id==savedId && it.state==ConnectorState.FREE}
                else station.connectors.firstOrNull{it.state==ConnectorState.FREE && it.id !in completed && charges.none{c->c.session.connectorId==it.id}}
            if(port==null)return@runCatching
            if(charges.any{it.session.connectorId==port.id})return@runCatching
            val start=item.getLong("start").coerceAtLeast(0)
            val energy=item.optDouble("energy",0.0).takeIf{it.isFinite() && it in 0.0..ChargingEngine.BATTERY_ENERGY_KWH} ?: return@runCatching
            val cost=item.optLong("cost",0).takeIf{it>=0 && it<=balance-charges.sumOf{c->c.progress.costMinor}} ?: return@runCatching
            val vehicle=item.optInt("vehicle",i+1).takeIf{it in 1..2 && charges.none{c->c.session.vehicleNumber==it}} ?: (1..2).first{n->charges.none{c->c.session.vehicleNumber==n}}
            val samples=item.optJSONArray("power") ?: JSONArray()
            val power=(0 until minOf(samples.length(),120)).mapNotNull{index->val p=samples.optJSONObject(index) ?: return@mapNotNull null;val kw=p.optDouble("kw",0.0);val sec=p.optLong("sec",0);if(kw.isFinite() && kw in 0.0..80.0 && sec>=0)PowerSample(sec,kw) else null}
            charges+=RunningCharge(ChargeSession(station.id,start,item.optLong("budget",balance).coerceAtLeast(0),port.id,vehicle),ChargeResult(item.optLong("seconds",0).coerceAtLeast(0),energy,cost,false),item.optLong("updated",start).coerceAtLeast(start),power)
        }
        AppState(balance,charges,history,completed-charges.map{it.session.connectorId}.toSet())
    }.getOrDefault(AppState())
    private fun persist() {
        val s=_state.value;val json=JSONObject().put("schema",2).put("balance",s.balanceMinor)
        json.put("charges",JSONArray().also{array->s.charges.forEach{c->array.put(JSONObject().put("id",c.session.stationId).put("connector",c.session.connectorId).put("start",c.session.startedAt).put("budget",c.session.budgetMinor).put("vehicle",c.session.vehicleNumber)
            .put("seconds",c.progress.seconds).put("energy",c.progress.energyKwh).put("cost",c.progress.costMinor).put("updated",c.updatedAt)
            .put("power",JSONArray().also{samples->c.powerHistory.forEach{samples.put(JSONObject().put("sec",it.seconds).put("kw",it.powerKw))}}))}})
        json.put("history",JSONArray().also{array->s.history.take(100).forEach{array.put(JSONObject().put("title",it.title).put("amount",it.amountMinor).put("date",it.timestamp).put("detail",it.detail))}})
        json.put("completed",JSONArray(s.completedConnectorIds.toList()))
        prefs.edit().putString("state",json.toString()).apply()
    }
    fun start(station:Station,connectorId:String?=null):Boolean {
        val result=ChargingEngine.start(_state.value,station.id,connectorId,System.currentTimeMillis())
        _state.value=result.state;persist();return result.started
    }
    fun stop(connectorId:String) {_state.value=ChargingEngine.stop(_state.value,connectorId,System.currentTimeMillis());persist()}
    fun releaseConnector(id:String) {
        val s=_state.value
        if(s.charges.any{it.session.connectorId==id} || id !in s.completedConnectorIds)return
        _state.value=s.copy(completedConnectorIds=s.completedConnectorIds-id);persist()
    }
    private fun tick() {
        val before=_state.value;val next=ChargingEngine.advance(before,System.currentTimeMillis());_state.value=next
        if(before.charges.map{it.session.connectorId}!=next.charges.map{it.session.connectorId})persist()
    }
    fun topUp(amountMinor:Long=50000,method:String="Демо"):Boolean {
        val s=_state.value
        if(s.active || amountMinor !in 100..5_000_000L || s.balanceMinor>Long.MAX_VALUE-amountMinor)return false
        _state.value=s.copy(balanceMinor=s.balanceMinor+amountMinor,history=(listOf(HistoryEntry("Учебное пополнение · $method",amountMinor,System.currentTimeMillis(),"Демо · без банковской операции"))+s.history).take(100));persist();return true
    }
}
