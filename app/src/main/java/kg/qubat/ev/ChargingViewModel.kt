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
data class ChargeSession(val stationId: String, val startedAt: Long, val budgetMinor: Long)
data class AppState(val balanceMinor: Long = 150000, val session: ChargeSession? = null,
    val progress: ChargeResult = ChargeResult(0,0.0,0,false), val history: List<HistoryEntry> = emptyList())
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
        val session=json.optJSONObject("session")?.let { ChargeSession(it.getString("id"),it.getLong("start"),it.getLong("budget")) }
            ?.takeIf { s -> DemoData.stations.any { it.id==s.stationId } }
        AppState(json.optLong("balance",150000).coerceAtLeast(0),session,history=history)
    }.getOrDefault(AppState())
    private fun persist() {
        val s=_state.value
        val json=JSONObject().put("balance",s.balanceMinor)
        s.session?.let { json.put("session",JSONObject().put("id",it.stationId).put("start",it.startedAt).put("budget",it.budgetMinor)) }
        json.put("history",JSONArray().also { array -> s.history.take(100).forEach { array.put(JSONObject().put("title",it.title).put("amount",it.amountMinor).put("date",it.timestamp).put("detail",it.detail)) } })
        prefs.edit().putString("state",json.toString()).apply()
    }
    fun start(station: Station): Boolean {
        val s=_state.value
        if(s.session!=null || station.status!=StationStatus.AVAILABLE || s.balanceMinor<1) return false
        _state.value=s.copy(session=ChargeSession(station.id,System.currentTimeMillis(),s.balanceMinor),progress=ChargeResult(0,0.0,0,false))
        persist(); return true
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
        _state.value=s.copy(balanceMinor=(s.balanceMinor-s.progress.costMinor).coerceAtLeast(0),session=null,history=(listOf(entry)+s.history).take(100))
        persist()
    }
    fun topUp() {
        val s=_state.value
        if(s.session!=null) return
        _state.value=s.copy(balanceMinor=s.balanceMinor+50000,history=(listOf(HistoryEntry("Учебное пополнение",50000,System.currentTimeMillis(),"Без банковской операции"))+s.history).take(100))
        persist()
    }
}
