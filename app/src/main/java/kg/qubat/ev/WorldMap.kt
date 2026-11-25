@file:Suppress("DEPRECATION")

package kg.qubat.ev

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.LinkAnnotation
import kotlin.math.log2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
@Composable fun WorldMap(stations:List<Station>,city:String?,location:GeoPoint?,selectedId:String,
    modifier:Modifier,focusRequest:Int,zoomRequest:Int,locationFocus:Int,stationFocus:Int,onSelect:(Station)->Unit) {
    val localize=LocalTranslate.current
    val language=LocalAppLanguage.current
    val night=LocalNight.current
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    val density=LocalDensity.current.density
    val select by rememberUpdatedState(onSelect)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var viewport by remember { mutableStateOf("Карта загружается") }
    var ready by remember { mutableStateOf(false) }
    var loadError by remember {mutableStateOf(false)}
    val icons=remember {object:LinkedHashMap<String,org.maplibre.android.annotations.Icon>(32,.75f,true) {
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,org.maplibre.android.annotations.Icon>?)=size>64
    }}
    val markers=remember {mutableMapOf<String,org.maplibre.android.annotations.Marker>()}
    val markerKeys=remember {mutableMapOf<String,String>()}
    var renderedStyle by remember {mutableStateOf<Style?>(null)}
    val mapView=remember {
        MapLibre.getInstance(context)
        MapView(context).also { view ->
            view.onCreate(null)
            view.addOnDidFailLoadingMapListener { error -> loadError=true; android.util.Log.e("QUBAT_MAP",error) }
            view.getMapAsync { native ->
                native.cameraPosition=CameraPosition.Builder().target(LatLng(41.5,74.7)).zoom(5.0).build()
                native.setMinZoomPreference(0.0)
                native.setMaxZoomPreference(19.0)
                native.uiSettings.apply {
                    setAllGesturesEnabled(false)
                    isCompassEnabled=false; isAttributionEnabled=false; isLogoEnabled=false
                }
                native.addOnCameraIdleListener {
                    val centre=native.projection.fromScreenLocation(PointF(view.width/2f,view.height/2f))
                    viewport=String.format(Locale.ROOT,"Масштаб %.1f; %.3f, %.3f",native.zoom,centre.latitude,centre.longitude)
                }
                map=native
                viewport="Масштаб 5.0; 41.500, 74.700"


            }
        }
    }
    LaunchedEffect(map,night,language) {
        val native=map ?: return@LaunchedEffect
        ready=false;loadError=false
        native.setStyle(Style.Builder().fromUri(if(night) "https://tiles.openfreemap.org/styles/dark" else WORLD_STYLE)) {style->if(night) improveNightMap(style);improveMapLabelContrast(style,night);localizeMapLabels(style,language);ready=true;loadError=false}
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
    LaunchedEffect(map,stations,selectedId,ready,language) {
        val native=map ?: return@LaunchedEffect
        if(!ready) return@LaunchedEffect
        if(renderedStyle!==native.style) {
            markers.values.forEach {native.removeMarker(it)}
            markers.clear();markerKeys.clear();renderedStyle=native.style
        }
        val ids=stations.mapTo(hashSetOf()) {it.id}
        markers.keys.filter {it !in ids}.forEach {id->markers.remove(id)?.let {native.removeMarker(it)};markerKeys.remove(id)}
        stations.forEach {station->
            val iconKey="${station.chargerCount}:${station.connectors.joinToString {it.state.name}}:${station.id==selectedId}"
            val key="$iconKey:${station.point}:${station.availableConnectors}:${language.code}"
            if(markerKeys[station.id]!=key) {
                val icon=icons.getOrPut(iconKey) {IconFactory.getInstance(context).fromBitmap(stationMarker(station,station.id==selectedId))}
                val existing=markers[station.id]
                if(existing==null) markers[station.id]=native.addMarker(MarkerOptions().position(LatLng(station.point.lat,station.point.lon)).title(localize("ДЕМО · ${station.city}")).icon(icon))
                else {existing.icon=icon;if(existing.position.latitude!=station.point.lat || existing.position.longitude!=station.point.lon)existing.position=LatLng(station.point.lat,station.point.lon);existing.title=localize("ДЕМО · ${station.city}")}
                markerKeys[station.id]=key
            }
        }
    }
    LaunchedEffect(map,ready,location) {
        val style=map?.style ?: return@LaunchedEffect
        if(ready) updateUserLocation(style,location)
    }
    LaunchedEffect(map,city) {
        val target=DemoData.cities.firstOrNull {it.name==city}?.point
        map?.moveCamera(CameraUpdateFactory.newLatLngZoom(target?.let {LatLng(it.lat,it.lon)} ?: LatLng(41.5,74.7),if(target==null)5.0 else 11.0))
    }
    LaunchedEffect(map,locationFocus) {if(locationFocus>0) location?.let {map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.lat,it.lon),15.5))}}
    LaunchedEffect(map,stationFocus) {if(stationFocus>0) stations.firstOrNull {it.id==selectedId}?.let {map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.point.lat,it.point.lon),15.0))}}
    LaunchedEffect(focusRequest) {if(focusRequest>0) map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(41.5,74.7),5.0))}
    var previousZoom by remember {mutableIntStateOf(zoomRequest)}
    LaunchedEffect(zoomRequest) {
        if(zoomRequest!=previousZoom) map?.let {native ->
            val delta=(zoomRequest-previousZoom).toDouble()
            native.setZoom((native.zoom+delta).coerceIn(0.0,19.0),PointF(mapView.width/2f,mapView.height/2f),200L)
        }
        previousZoom=zoomRequest
    }
    Box(modifier) {
        AndroidView(factory={mapView},modifier=Modifier.fillMaxSize().testTag("world-map").semantics {contentDescription=localize("Карта мира");stateDescription=localize(viewport+if(ready) " · карта загружена" else " · ожидание сети")})
        // Compose owns touch recognition; native projection/camera own the geographic transform.
        Box(Modifier.matchParentSize()
            .pointerInput(map) {
                detectTransformGestures(panZoomLock=true) { centroid,pan,scale,_ ->
                    val native=map ?: return@detectTransformGestures
                    native.cancelTransitions()
                    // centroid is the PREVIOUS finger centre. Zoom around it first,
                    // then translate by the centroid movement to keep the same map point under the fingers.
                    // Direct native values avoid the SDK's cached CameraPosition during continuous gestures.
                    if(scale.isFinite() && scale>0f && scale!=1f) {
                        native.setZoom((native.zoom+log2(scale.toDouble())).coerceIn(0.0,19.0),PointF(centroid.x,centroid.y),0L)
                    }
                    if(pan!=Offset.Zero) native.scrollBy(pan.x,pan.y)

                }
            }
            .pointerInput(map,stations) {
                detectTapGestures(
                    onDoubleTap={ point ->map?.let {native ->native.setZoom((native.zoom+1).coerceIn(0.0,19.0),PointF(point.x,point.y),200L)}},
                    onTap={ point ->
                        val native=map ?: return@detectTapGestures
                        fun distance(station:Station):Float {
                            val projected=native.projection.toScreenLocation(LatLng(station.point.lat,station.point.lon))
                            return (Offset(projected.x,projected.y-20*density)-point).getDistance()
                        }
                        stations.minByOrNull(::distance)?.takeIf {distance(it)<44*density}?.let(select)
                    }
                )
            })
        if(location!=null && ready) {
            Spacer(Modifier.size(1.dp).testTag("user-location").semantics {contentDescription=localize("Вы здесь");stateDescription="${location.lat},${location.lon}"})
        }
        Box(Modifier.align(Alignment.TopStart).padding(top=86.dp,start=16.dp)) {MapCredits()}
        if(!ready) DepthSurface(modifier=Modifier.align(Alignment.TopStart).padding(top=140.dp,start=16.dp),shape=androidx.compose.foundation.shape.RoundedCornerShape(10.dp),color=SurfaceGreen) {
            AppText(if(loadError) "Карта недоступна · проверьте интернет" else "Загрузка карты · нужен интернет",modifier=Modifier.padding(8.dp),fontSize=11.sp,color=Muted)
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
    station.connectors.forEachIndexed {index,port->
        paint.color=port.state.argb
        canvas.drawArc(RectF(15f,15f,97f,97f),-90+index*arc,(arc-3).coerceAtLeast(1f),false,paint)
    }
    paint.style=Paint.Style.FILL;paint.color=android.graphics.Color.rgb(12,48,37)
    paint.textSize=40f;paint.textAlign=Paint.Align.CENTER;paint.typeface=android.graphics.Typeface.DEFAULT_BOLD
    canvas.drawText(station.chargerCount.toString(),56f,70f,paint)
    return bitmap
}

internal fun userLocationMarker():Bitmap {
    val image=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888)
    val canvas=Canvas(image)
    val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color=0x443287FF;canvas.drawCircle(40f,40f,38f,paint)
    paint.color=android.graphics.Color.WHITE;canvas.drawCircle(40f,40f,18f,paint)
    paint.color=0xFF287DFA.toInt();canvas.drawCircle(40f,40f,12f,paint)
    return image
}

internal fun improveNightMap(style:org.maplibre.android.maps.Style) {
    style.layers.forEach {layer->
        val id=layer.id.lowercase(java.util.Locale.ROOT)
        when {
            layer is org.maplibre.android.style.layers.BackgroundLayer -> layer.setProperties(org.maplibre.android.style.layers.PropertyFactory.backgroundColor("#102635"))
            layer is org.maplibre.android.style.layers.FillLayer && id.contains("building") -> {
                layer.minZoom=13f
                layer.setProperties(org.maplibre.android.style.layers.PropertyFactory.visibility(org.maplibre.android.style.layers.Property.VISIBLE),org.maplibre.android.style.layers.PropertyFactory.fillColor("#2A4353"),org.maplibre.android.style.layers.PropertyFactory.fillOpacity(.95f),org.maplibre.android.style.layers.PropertyFactory.fillOutlineColor("#526D7D"))
            }
            layer is org.maplibre.android.style.layers.FillExtrusionLayer && id.contains("building") -> layer.setProperties(org.maplibre.android.style.layers.PropertyFactory.fillExtrusionColor("#2A4353"),org.maplibre.android.style.layers.PropertyFactory.fillExtrusionOpacity(.95f))
            layer is org.maplibre.android.style.layers.FillLayer && id.contains("water") -> layer.setProperties(org.maplibre.android.style.layers.PropertyFactory.fillColor("#153F59"))
            layer is org.maplibre.android.style.layers.LineLayer && (id.contains("road") || id.contains("highway")) -> layer.setProperties(org.maplibre.android.style.layers.PropertyFactory.lineColor(if(id.contains("casing")) "#132B3B" else if(id.contains("motorway") || id.contains("trunk")) "#E6B776" else if(id.contains("path") || id.contains("footway") || id.contains("cycleway")) "#6A918D" else if(id.contains("primary") || id.contains("secondary") || id.contains("tertiary")) "#C5D8E3" else "#96B2C4"),org.maplibre.android.style.layers.PropertyFactory.lineOpacity(1f))
        }
    }
}

// Text paint must be adapted alongside roads/buildings: dark glyphs on the
// night style disappear even when street geometry itself is bright enough.
internal fun improveMapLabelContrast(style:Style,night:Boolean) {
    style.layers.filterIsInstance<org.maplibre.android.style.layers.SymbolLayer>().forEach {layer->
        val id=layer.id.lowercase(java.util.Locale.ROOT)
        val shield=id.contains("shield") || id.contains("ref")
        if(night || !shield) {
            layer.setProperties(
                org.maplibre.android.style.layers.PropertyFactory.textColor(if(night) "#F2F7FC" else "#203747"),
                org.maplibre.android.style.layers.PropertyFactory.textHaloColor(if(night) "#0B1B28" else "#FFFFFF"),
                org.maplibre.android.style.layers.PropertyFactory.textHaloWidth(if(night)1.6f else 1.3f),
                org.maplibre.android.style.layers.PropertyFactory.textHaloBlur(.25f),
                org.maplibre.android.style.layers.PropertyFactory.textOpacity(1f))
        }
        if(!shield && (id.contains("road") || id.contains("highway") || id.contains("street")) && !id.contains("oneway")) {
            layer.minZoom=minOf(layer.minZoom,13f)
            // Keep the native collision placement: names stay legible, not stacked.
            layer.setProperties(
                org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap(false),
                org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement(false))
        }
    }
}

internal const val USER_LOCATION_SOURCE="qubat-user-position"
internal const val USER_LOCATION_DOT="qubat-user-position-dot"
internal fun updateUserLocation(style:Style,point:GeoPoint?) {
    val source=style.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>(USER_LOCATION_SOURCE)
    if(point==null) {
        source?.setGeoJson(org.maplibre.geojson.FeatureCollection.fromFeatures(emptyArray<org.maplibre.geojson.Feature>()))
        return
    }
    val geometry=org.maplibre.geojson.Point.fromLngLat(point.lon,point.lat)
    if(source==null) {
        style.addSource(org.maplibre.android.style.sources.GeoJsonSource(USER_LOCATION_SOURCE,geometry))
        style.addLayer(org.maplibre.android.style.layers.CircleLayer("qubat-user-position-halo",USER_LOCATION_SOURCE).withProperties(
            org.maplibre.android.style.layers.PropertyFactory.circleColor("#3287FF"),
            org.maplibre.android.style.layers.PropertyFactory.circleOpacity(.2f),
            org.maplibre.android.style.layers.PropertyFactory.circleRadius(20f)))
        style.addLayer(org.maplibre.android.style.layers.CircleLayer(USER_LOCATION_DOT,USER_LOCATION_SOURCE).withProperties(
            org.maplibre.android.style.layers.PropertyFactory.circleColor("#287DFA"),
            org.maplibre.android.style.layers.PropertyFactory.circleRadius(6f),
            org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor("#FFFFFF"),
            org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth(3f)))
    } else source.setGeoJson(geometry)
}

internal fun localizeMapLabels(style:Style,language:AppLanguage) {
    style.layers.filterIsInstance<org.maplibre.android.style.layers.SymbolLayer>().filter {it.id.contains("label",true) && !it.id.contains("ref",true)}.forEach {layer->
        layer.setProperties(org.maplibre.android.style.layers.PropertyFactory.textField(org.maplibre.android.style.expressions.Expression.coalesce(
            org.maplibre.android.style.expressions.Expression.get("name:${language.code}"),
            org.maplibre.android.style.expressions.Expression.get(if(language==AppLanguage.EN) "name:latin" else "name"),
            org.maplibre.android.style.expressions.Expression.get("name"))))
    }
}
