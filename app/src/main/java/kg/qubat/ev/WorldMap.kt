@file:Suppress("DEPRECATION")

package kg.qubat.ev

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.util.Locale

private const val WORLD_STYLE="https://tiles.openfreemap.org/styles/liberty"

@Suppress("DEPRECATION")
@OptIn(ExperimentalComposeUiApi::class)
@Composable fun WorldMap(stations:List<Station>,city:String?,location:GeoPoint?,selectedId:String,
    modifier:Modifier,focusRequest:Int,zoomRequest:Int,onSelect:(Station)->Unit) {
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    val select by rememberUpdatedState(onSelect)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var viewport by remember { mutableStateOf("Карта загружается") }
    var ready by remember { mutableStateOf(false) }
    var loadError by remember {mutableStateOf(false)}
    val markerStations=remember { mutableMapOf<Long,Station>() }
    val icons=remember {mutableMapOf<String,org.maplibre.android.annotations.Icon>()}
    val mapView=remember {
        fun px(dp:Int)=(dp*context.resources.displayMetrics.density).toInt()
        MapLibre.getInstance(context)
        MapView(context).also { view ->
            view.onCreate(null)
            view.addOnDidFailLoadingMapListener { error -> loadError=true; android.util.Log.e("QUBAT_MAP",error) }
            view.getMapAsync { native ->
                native.cameraPosition=CameraPosition.Builder().target(LatLng(41.5,74.7)).zoom(5.0).build()
                native.setMinZoomPreference(0.0)
                native.setMaxZoomPreference(19.0)
                native.uiSettings.apply {
                    isScrollGesturesEnabled=true; isHorizontalScrollGesturesEnabled=true; isZoomGesturesEnabled=true
                    isRotateGesturesEnabled=true; isTiltGesturesEnabled=false
                    isAttributionEnabled=true; isLogoEnabled=false
                    setAttributionMargins(px(12),px(110),px(12),px(12))
                    attributionGravity=android.view.Gravity.TOP or android.view.Gravity.END
                    logoGravity=android.view.Gravity.TOP or android.view.Gravity.START
                    setLogoMargins(px(12),px(135),px(12),px(12))
                }
                native.addOnCameraIdleListener {
                    val centre=native.projection.fromScreenLocation(PointF(view.width/2f,view.height/2f))
                    viewport=String.format(Locale.ROOT,"Масштаб %.1f; %.3f, %.3f",native.zoom,centre.latitude,centre.longitude)
                }
                native.setOnMarkerClickListener { marker -> markerStations[marker.id]?.let(select); true }
                map=native
                viewport="Масштаб 5.0; 41.500, 74.700"
                native.setStyle(Style.Builder().fromUri(WORLD_STYLE)) { ready=true;loadError=false }

            }
        }
    }
    DisposableEffect(mapView,owner) {
        var started=false;var resumed=false
        fun sync(state:Lifecycle.State) {
            if(state.isAtLeast(Lifecycle.State.STARTED) && !started) {mapView.onStart();started=true}
            if(state.isAtLeast(Lifecycle.State.RESUMED) && !resumed) {mapView.onResume();resumed=true}
            if(!state.isAtLeast(Lifecycle.State.RESUMED) && resumed) {mapView.onPause();resumed=false}
            if(!state.isAtLeast(Lifecycle.State.STARTED) && started) {mapView.onStop();started=false}
        }
        val observer=LifecycleEventObserver { _,_ -> sync(owner.lifecycle.currentState) }
        owner.lifecycle.addObserver(observer);sync(owner.lifecycle.currentState)
        val memory=object:android.content.ComponentCallbacks2 {
            override fun onLowMemory()=mapView.onLowMemory()
            override fun onTrimMemory(level:Int) {if(level>=android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) mapView.onLowMemory()}
            override fun onConfigurationChanged(configuration:android.content.res.Configuration) {}
        }
        context.registerComponentCallbacks(memory)
        onDispose {
            owner.lifecycle.removeObserver(observer);context.unregisterComponentCallbacks(memory)
            if(resumed) mapView.onPause()
            if(started) mapView.onStop()
            mapView.onDestroy()
        }
    }
    LaunchedEffect(map,stations,selectedId,ready,location) {
        val native=map ?: return@LaunchedEffect
        if(!ready) return@LaunchedEffect
        native.clear();markerStations.clear()
        stations.forEach { station ->
            val iconKey="${station.connectorCount}:${station.availableConnectors}:${station.status}:${station.id==selectedId}"
            val icon=icons.getOrPut(iconKey) {IconFactory.getInstance(context).fromBitmap(stationMarker(station,station.id==selectedId))}
            val marker=native.addMarker(MarkerOptions().position(LatLng(station.point.lat,station.point.lon))
                .title("ДЕМО · ${station.city}")
                .snippet("${station.availableConnectors} из ${station.connectorCount} свободно · GB/T DC")
                .icon(icon))
            markerStations[marker.id]=station
        }
        location?.let { native.addMarker(MarkerOptions().position(LatLng(it.lat,it.lon)).title("Ваша позиция")) }
    }
    LaunchedEffect(map,city) {
        val target=DemoData.cities.firstOrNull {it.name==city}?.point
        map?.moveCamera(CameraUpdateFactory.newLatLngZoom(target?.let {LatLng(it.lat,it.lon)} ?: LatLng(41.5,74.7),if(target==null)5.0 else 11.0))
    }
    LaunchedEffect(map,location) {location?.let {map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.lat,it.lon),11.0))}}
    LaunchedEffect(focusRequest) {if(focusRequest>0) map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(41.5,74.7),5.0))}
    var previousZoom by remember {mutableIntStateOf(zoomRequest)}
    LaunchedEffect(zoomRequest) {
        if(zoomRequest!=previousZoom) map?.animateCamera(if(zoomRequest>previousZoom)CameraUpdateFactory.zoomIn() else CameraUpdateFactory.zoomOut())
        previousZoom=zoomRequest
    }
    Box(modifier) {
        AndroidView(factory={mapView},modifier=Modifier.fillMaxSize().testTag("world-map").semantics {contentDescription="Карта мира";stateDescription=viewport+if(ready) " · карта загружена" else " · ожидание сети"})
        // One MotionEvent stream goes directly to the native pan/pinch/tap recognizers.
        // The Compose controls above this map retain their own touch targets.
        Box(Modifier.matchParentSize().pointerInteropFilter { event ->
            mapView.dispatchTouchEvent(event)
            true
        })
        Surface(modifier=Modifier.align(Alignment.TopStart).padding(top=76.dp,start=16.dp,end=16.dp),shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp),color=SurfaceGreen.copy(alpha=.92f)) {
            Text("© OpenStreetMap · OpenMapTiles · OpenFreeMap",fontSize=10.sp,color=Muted,modifier=Modifier.clickable {context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://openfreemap.org/")))}.padding(6.dp))
        }
        if(!ready) Surface(modifier=Modifier.align(Alignment.TopStart).padding(top=140.dp,start=16.dp),shape=androidx.compose.foundation.shape.RoundedCornerShape(10.dp),color=SurfaceGreen) {
            Text(if(loadError) "Карта недоступна · проверьте интернет" else "Загрузка карты · нужен интернет",modifier=Modifier.padding(8.dp),fontSize=11.sp,color=Muted)
        }
    }
}

internal fun stationMarker(station:Station,selected:Boolean):Bitmap {
    val bitmap=Bitmap.createBitmap(112,112,Bitmap.Config.ARGB_8888)
    val canvas=Canvas(bitmap)
    val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color=if(selected) android.graphics.Color.rgb(181,244,129) else android.graphics.Color.WHITE
    canvas.drawCircle(56f,56f,52f,paint)
    paint.style=Paint.Style.STROKE;paint.strokeWidth=12f
    val arc=360f/station.connectorCount
    repeat(station.connectorCount) { index ->
        paint.color=when {
            station.status==StationStatus.OFFLINE -> android.graphics.Color.rgb(130,141,142)
            index<station.availableConnectors -> android.graphics.Color.rgb(25,143,80)
            else -> android.graphics.Color.rgb(232,155,66)
        }
        canvas.drawArc(RectF(15f,15f,97f,97f),-90+index*arc,arc-6,false,paint)
    }
    paint.style=Paint.Style.FILL;paint.color=android.graphics.Color.rgb(12,48,37)
    paint.textSize=40f;paint.textAlign=Paint.Align.CENTER;paint.typeface=android.graphics.Typeface.DEFAULT_BOLD
    canvas.drawText(station.connectorCount.toString(),56f,70f,paint)
    return bitmap
}
