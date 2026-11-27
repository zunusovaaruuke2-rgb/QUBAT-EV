package kg.qubat.ev

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

// Requests both providers. Updates stop whenever the activity leaves the foreground.
internal class ForegroundLocation(context:Context,private val lifecycle:Lifecycle,
    private val onFix:(Location)->Unit,private val onState:(Boolean,String)->Unit):LifecycleEventObserver {
    private val manager=context.getSystemService(LocationManager::class.java)
    private val handler=Handler(Looper.getMainLooper())
    private var requested=false
    private var listening=false
    private var best:Location?=null
    private val timeout=Runnable {if(best==null) onState(false,"Нет сигнала GPS. Выйдите на открытое место и повторите.")}
    private val listener=object:LocationListener {
        override fun onLocationChanged(location:Location) {accept(location)}
        override fun onProviderDisabled(provider:String) {if(providers().isEmpty()) onState(false,"Включите геолокацию в настройках телефона")}
        override fun onProviderEnabled(provider:String) {if(requested) start()}
        @Deprecated("Legacy Android callback") override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?) {}
    }
    init {lifecycle.addObserver(this)}
    private fun providers()=listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER).filter {runCatching {manager.isProviderEnabled(it)}.getOrDefault(false)}
    fun request() {requested=true;best=null;start()}
    @SuppressLint("MissingPermission") private fun start() {
        if(!requested || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        stop()
        val enabled=providers()
        if(enabled.isEmpty()) {onState(false,"Включите геолокацию в настройках телефона");return}
        best=null
        onState(true,"Определяем ваше местоположение…")
        try {
            enabled.mapNotNull {manager.getLastKnownLocation(it)}.filter {fresh(it)}.sortedBy {it.accuracy}.firstOrNull()?.let(::accept)
            listening=true
            enabled.forEach {manager.requestLocationUpdates(it,2000L,2f,listener,Looper.getMainLooper())}
            handler.postDelayed(timeout,15000)
        } catch(_:SecurityException) {stop();onState(false,"Разрешите доступ к геопозиции в настройках приложения")}
        catch(_:IllegalArgumentException) {stop();onState(false,"Служба геопозиции недоступна. Попробуйте ещё раз")}
    }
    private fun fresh(location:Location):Boolean {
        val age=SystemClock.elapsedRealtimeNanos()-location.elapsedRealtimeNanos
        return age in 0..120_000_000_000L && location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0
    }
    private fun accept(location:Location) {
        if(!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || !fresh(location)) return
        val previous=best
        if(previous!=null && location.elapsedRealtimeNanos<previous.elapsedRealtimeNanos) return
        if(previous!=null && location.accuracy>previous.accuracy*2 && location.elapsedRealtimeNanos-previous.elapsedRealtimeNanos<10_000_000_000L) return
        best=location
        handler.removeCallbacks(timeout)
        onFix(location)
        onState(false,if(location.accuracy>200) "Приблизительное положение · включите точную геопозицию" else "Вы на карте · точность ±${location.accuracy.toInt().coerceAtLeast(1)} м")
    }
    private fun stop() {handler.removeCallbacks(timeout);if(listening) manager.removeUpdates(listener);listening=false}
    override fun onStateChanged(source:LifecycleOwner,event:Lifecycle.Event) {
        if(event==Lifecycle.Event.ON_START && requested) start()
        if(event==Lifecycle.Event.ON_STOP) stop()
    }
    fun close() {stop();lifecycle.removeObserver(this)}
}
