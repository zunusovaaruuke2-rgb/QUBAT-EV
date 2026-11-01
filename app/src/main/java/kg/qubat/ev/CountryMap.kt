package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.*

@Composable fun CountryMap(stations:List<Station>,city:String?,location:GeoPoint?,selectedId:String,
    modifier:Modifier=Modifier,focusRequest:Int=0,zoomRequest:Int=0,onSelect:(Station)->Unit) {
    if(!BuildConfig.MAPS_CONFIGURED) {
        WorldMap(stations,city,location,selectedId,modifier,focusRequest,zoomRequest,onSelect)
        return
    }
    val centre=DemoData.cities.firstOrNull {it.name==city}?.point
    val camera=rememberCameraPositionState {position=CameraPosition.fromLatLngZoom(LatLng(41.5,74.7),5f)}
    LaunchedEffect(city) {camera.position=CameraPosition.fromLatLngZoom(centre?.let {LatLng(it.lat,it.lon)} ?: LatLng(41.5,74.7),if(centre!=null)11f else 5f)}
    LaunchedEffect(location) {location?.let {camera.position=CameraPosition.fromLatLngZoom(LatLng(it.lat,it.lon),11f)}}
    LaunchedEffect(focusRequest) {if(focusRequest>0)camera.position=CameraPosition.fromLatLngZoom(LatLng(41.5,74.7),5f)}
    var previousZoom by remember {mutableIntStateOf(zoomRequest)}
    LaunchedEffect(zoomRequest) {
        if(previousZoom!=zoomRequest)camera.animate(if(zoomRequest>previousZoom)CameraUpdateFactory.zoomIn() else CameraUpdateFactory.zoomOut())
        previousZoom=zoomRequest
    }
    var loaded by remember {mutableStateOf(false)}
    Box(modifier) {
        GoogleMap(modifier=Modifier.matchParentSize(),cameraPositionState=camera,contentPadding=PaddingValues(top=82.dp,bottom=230.dp),
            properties=MapProperties(minZoomPreference=0f,maxZoomPreference=21f,mapStyleOptions=if(!LocalNight.current) null else MapStyleOptions("""[{"elementType":"geometry","stylers":[{"color":"#16372b"}]},{"elementType":"labels.text.fill","stylers":[{"color":"#b9cfbd"}]},{"elementType":"labels.text.stroke","stylers":[{"color":"#173428"}]},{"featureType":"water","elementType":"geometry","stylers":[{"color":"#0c2229"}]}]""")),
            uiSettings=MapUiSettings(mapToolbarEnabled=false,zoomControlsEnabled=false,scrollGesturesEnabled=true,zoomGesturesEnabled=true),onMapLoaded={loaded=true}) {
            stations.forEach { s -> Marker(state=remember(s.id) {MarkerState(position=LatLng(s.point.lat,s.point.lon))},icon=remember(s,selectedId) {BitmapDescriptorFactory.fromBitmap(stationMarker(s,s.id==selectedId))},title="ДЕМО · ${s.city}",snippet="${s.availableConnectors} из ${s.connectorCount} свободно",onClick={onSelect(s);true})}
            location?.let {Marker(state=remember(it) {MarkerState(position=LatLng(it.lat,it.lon))},title="Ваша позиция")}
        }
        if(!loaded) Surface(modifier=Modifier.align(Alignment.TopStart).padding(top=90.dp,start=16.dp),color=SurfaceGreen) {Text("Загрузка карты… Проверьте сеть и ключ",modifier=Modifier.padding(10.dp),fontSize=11.sp)}
    }
}
