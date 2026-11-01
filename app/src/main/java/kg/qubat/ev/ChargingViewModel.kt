package kg.qubat.ev

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(val title: String, val amountMinor: Long, val timestamp: Long, val detail: String)
data class ChargeSession(val stationId: String, val startedAt: Long, val budgetMinor: Long,val connectorId:String="")
data class AppState(val balanceMinor: Long = 150000, val session: ChargeSession? = null,
    val progress: ChargeResult = ChargeResult(0,0.0,0,false), val history: List<HistoryEntry> = emptyList(),val completedConnectorIds:Set<String> = emptySet())
class ChargingViewModel(app: Application): AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("qubat_demo_v1",0)
    private val _state = MutableStateFlow(restore())
    val state = _state.asStateFlow()
    init { tick(); viewModelScope.launch { while(true) { delay(1000); tick() } } }
    private fun restore(): AppState = runCatching {
        val json = JSONObject(prefs.getString("state", "{}")!!)
        val items = json.optJSONArray("history") ?: JSONArray()
        val history = (0 until items.length()).map { val item=items.getJSONObject(it)
            HistoryEntry(item.getString("title"),item.getLong("amount"),item.getLong("date"),item.getString("detail")) }
        val session=json.optJSONObject("session")?.let { ChargeSession(it.getString("id"),it.getLong("start"),it.getLong("budget"),it.optString("connector","")) }
            ?.takeIf { s -> DemoData.stations.any { it.id==s.stationId } }
        val completedJson=json.optJSONArray("completed") ?: JSONArray()
        val known=DemoData.stations.flatMap {it.connectors}.map {it.id}.toSet()
        val completed=(0 until completedJson.length()).map {completedJson.getString(it)}.filter {it in known}.toSet()
        val restoredSession=session?.let {saved ->
            val station=DemoData.stations.first {it.id==saved.stationId}
            val port=station.connectors.firstOrNull {it.id==saved.connectorId}
                ?: station.connectors.firstOrNull {it.state==ConnectorState.FREE && it.id !in completed}
            port?.let {saved.copy(connectorId=it.id)}
        }
        AppState(json.optLong("balance",150000).coerceAtLeast(0),restoredSession,history=history,completedConnectorIds=completed)
    }.getOrDefault(AppState())
    private fun persist() {
        val s=_state.value
        val json=JSONObject().put("balance",s.balanceMinor)
        s.session?.let { json.put("session",JSONObject().put("id",it.stationId).put("start",it.startedAt).put("budget",it.budgetMinor).put("connector",it.connectorId)) }
        json.put("history",JSONArray().also { array -> s.history.take(100).forEach { array.put(JSONObject().put("title",it.title).put("amount",it.amountMinor).put("date",it.timestamp).put("detail",it.detail)) } })
        json.put("completed",JSONArray(s.completedConnectorIds.toList()))
        prefs.edit().putString("state",json.toString()).apply()
    }
    fun start(station:Station,connectorId:String?=null):Boolean {
        val s=_state.value
        if(s.session!=null || s.balanceMinor<1) return false
        val current=DemoData.stations.firstOrNull {it.id==station.id}
            ?.withConnectorSession(null,s.completedConnectorIds) ?: return false
        val port=current.freeConnector(connectorId) ?: return false
        _state.value=s.copy(session=ChargeSession(station.id,System.currentTimeMillis(),s.balanceMinor,port.id),progress=ChargeResult(0,0.0,0,false))
        persist();return true
    }
    fun releaseConnector(id:String) {
        val s=_state.value
        if(s.session?.connectorId==id || id !in s.completedConnectorIds) return
        _state.value=s.copy(completedConnectorIds=s.completedConnectorIds-id)
        persist()
    }
    private fun tick() {
        val s=_state.value; val session=s.session ?: return
        val station=DemoData.stations.first { it.id==session.stationId }
        val seconds=((System.currentTimeMillis()-session.startedAt)/1000).coerceAtLeast(0)
        _state.value=s.copy(progress=calculateCharge(seconds,station.powerKw,station.tariffMinor,session.budgetMinor))
        if(_state.value.progress.depleted) finish()
    }
    fun stop() { tick(); if(_state.value.session!=null) finish() }
    private fun finish() {
        val s=_state.value; val session=s.session ?: return
        val station=DemoData.stations.first { it.id==session.stationId }
        val entry=HistoryEntry("Демо-зарядка · ${station.city}",-s.progress.costMinor,System.currentTimeMillis(),
            String.format(java.util.Locale("ru"),"%.3f кВт·ч · %d сек",s.progress.energyKwh,s.progress.seconds))
        _state.value=s.copy(balanceMinor=(s.balanceMinor-s.progress.costMinor).coerceAtLeast(0),session=null,history=(listOf(entry)+s.history).take(100),completedConnectorIds=s.completedConnectorIds+session.connectorId)
        persist()
    }
    fun topUp() {
        val s=_state.value
        if(s.session!=null) return
        _state.value=s.copy(balanceMinor=s.balanceMinor+50000,history=(listOf(HistoryEntry("Учебное пополнение",50000,System.currentTimeMillis(),"Без банковской операции"))+s.history).take(100))
        persist()
    }
}
