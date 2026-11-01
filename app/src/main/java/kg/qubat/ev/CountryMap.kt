package kg.qubat.ev

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*

@Composable fun CountryMap(stations:List<Station>,city:String?,location:GeoPoint?,selectedId:String,modifier:Modifier=Modifier,focusRequest:Int=0,onSelect:(Station)->Unit) {
    val centre=DemoData.cities.firstOrNull {it.name==city}?.point
    if(BuildConfig.MAPS_CONFIGURED) {
        val camera=rememberCameraPositionState { position=CameraPosition.fromLatLngZoom(LatLng(41.5,74.7),5f) }
        LaunchedEffect(city) {camera.position=CameraPosition.fromLatLngZoom(centre?.let {LatLng(it.lat,it.lon)} ?: LatLng(41.5,74.7),if(centre!=null)11f else 5f)}
        LaunchedEffect(location) { location?.let {camera.position=CameraPosition.fromLatLngZoom(LatLng(it.lat,it.lon),11f)} }
        LaunchedEffect(focusRequest) { if(focusRequest>0) camera.position=CameraPosition.fromLatLngZoom(LatLng(41.5,74.7),5f) }
        var loaded by remember {mutableStateOf(false)}
        Box(modifier) {
            GoogleMap(modifier=Modifier.matchParentSize(),cameraPositionState=camera,contentPadding=PaddingValues(top=82.dp,bottom=230.dp),
                properties=MapProperties(mapStyleOptions=MapStyleOptions("""[{"elementType":"geometry","stylers":[{"color":"#16372b"}]},{"elementType":"labels.text.fill","stylers":[{"color":"#b9cfbd"}]},{"elementType":"labels.text.stroke","stylers":[{"color":"#173428"}]},{"featureType":"water","elementType":"geometry","stylers":[{"color":"#0c2229"}]}]""")),
                uiSettings=MapUiSettings(mapToolbarEnabled=false,zoomControlsEnabled=true),onMapLoaded={loaded=true}) {
                stations.forEach {s -> Marker(state=remember(s.id) { MarkerState(position=LatLng(s.point.lat,s.point.lon)) },title="ДЕМО · ${s.city}",snippet="Вымышленная станция · 80 кВт",onClick={onSelect(s);true})}
                location?.let {Marker(state=remember(it) { MarkerState(position=LatLng(it.lat,it.lon)) },title="Ваша позиция")}
            }
            if(!loaded) Surface(modifier=Modifier.align(Alignment.TopCenter).padding(12.dp),shape=RoundedCornerShape(12.dp),color=SurfaceGreen) {Text("Загрузка карты… Проверьте сеть и ключ",modifier=Modifier.padding(10.dp),fontSize=11.sp)}
        }
    } else {
        Column(modifier.background(Color(0xFF15372B)).padding(horizontal=16.dp).padding(top=100.dp,bottom=160.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {

            // Deliberately schematic, not a geographic boundary or licensed map tile.
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val width=maxWidth;val height=maxHeight
                fun x(lon:Double)=((lon-69.8)/10.8).toFloat().coerceIn(.04f,.90f)
                fun y(lat:Double)=((43.4-lat)/4.8).toFloat().coerceIn(.05f,.90f)
                Canvas(Modifier.matchParentSize()) {
                    for(i in 1..7) drawLine(Color(0xFF204333),Offset(size.width*i/8,0f),Offset(size.width*i/8,size.height),1f)
                    for(i in 1..5) drawLine(Color(0xFF204333),Offset(0f,size.height*i/6),Offset(size.width,size.height*i/6),1f)
                    val path=Path().apply {moveTo(0f,size.height*.78f);cubicTo(size.width*.2f,size.height*.32f,size.width*.5f,size.height*.96f,size.width,size.height*.28f)}
                    drawPath(path,Color(0xFF2A513E),style=Stroke(24f))
                    drawOval(Color(0xFF23545B),topLeft=Offset(size.width*.61f,size.height*.16f),size=androidx.compose.ui.geometry.Size(size.width*.22f,size.height*.09f))
                }
                DemoData.cities.filter {it.name in listOf("Бишкек","Ош","Каракол","Нарын","Талас","Баткен")}.forEach { c ->
                    Text(c.name,Modifier.offset(width*x(c.point.lon),height*y(c.point.lat)),fontSize=9.sp,color=Muted)
                }
                stations.forEach { s ->
                    Box(Modifier.semantics { contentDescription="Демо-станция ${s.city}" }.offset(width*x(s.point.lon)-10.dp,height*y(s.point.lat)-26.dp).size(28.dp).clip(RoundedCornerShape(10.dp)).background(if(s.id==selectedId)Lime else Color(0xFF689C61)).clickable {onSelect(s)},contentAlignment=Alignment.Center) {Text("ϟ",color=Forest,fontWeight=FontWeight.Black,fontSize=21.sp)}
                }
            }

        }
    }
}
