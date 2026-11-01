package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MapHome(
    stations: List<Station>, city: String?, location: GeoPoint?, locating: Boolean,
    locationMessage: String, minPower: Int, statusName: String?,
    onCity: (String?)->Unit, onPower: (Int)->Unit, onStatus: (String?)->Unit,
    onLocate: ()->Unit, onDetail: (Station)->Unit, onConnector: (Station,Connector)->Unit
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var filters by rememberSaveable { mutableStateOf(false) }
    var zoomRequest by remember {mutableIntStateOf(0)}
    var focusRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(location) { location?.let { selectedId=nearestStation(stations,it)?.id } }
    val selected=stations.firstOrNull {it.id==selectedId}
    BackHandler(enabled=selected!=null) {selectedId=null}
    Box(Modifier.fillMaxSize()) {
        CountryMap(stations,city,location,selectedId ?: "",Modifier.fillMaxSize(),focusRequest,zoomRequest) { selectedId=it.id }
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
            Surface(shape=RoundedCornerShape(16.dp),color=SurfaceGreen,shadowElevation=4.dp,modifier=Modifier.weight(1f)) {
                TextButton(onClick={filters=true},modifier=Modifier.fillMaxWidth()) {Icon(Icons.Default.Place,null,modifier=Modifier.size(18.dp));Text(city ?: "Кыргызстан",modifier=Modifier.weight(1f),fontWeight=FontWeight.SemiBold);Icon(Icons.Default.ArrowDropDown,null)}
            }
            FilledIconButton(onClick={filters=true},shape=RoundedCornerShape(16.dp),modifier=Modifier.size(50.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=SurfaceGreen,contentColor=Lime)) {Icon(Icons.Default.Settings,"Фильтры карты")}
        }
        Column(Modifier.align(Alignment.CenterEnd).padding(end=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            FilledIconButton(onClick={zoomRequest++},shape=RoundedCornerShape(16.dp),modifier=Modifier.size(50.dp).semantics {contentDescription="Увеличить карту"},colors=IconButtonDefaults.filledIconButtonColors(containerColor=SurfaceGreen,contentColor=Lime)) {Text("+",fontSize=28.sp)}
            FilledIconButton(onClick={zoomRequest--},shape=RoundedCornerShape(16.dp),modifier=Modifier.size(50.dp).semantics {contentDescription="Уменьшить карту"},colors=IconButtonDefaults.filledIconButtonColors(containerColor=SurfaceGreen,contentColor=Lime)) {Text("−",fontSize=28.sp)}
            FilledIconButton(onClick={focusRequest++},shape=RoundedCornerShape(16.dp),modifier=Modifier.size(50.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=SurfaceGreen,contentColor=Lime)) {Icon(Icons.Default.Refresh,"Обзор Кыргызстана")}
            FilledIconButton(onClick={onLocate},enabled=!locating,shape=RoundedCornerShape(16.dp),modifier=Modifier.size(50.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=SurfaceGreen,contentColor=Lime)) {if(locating) CircularProgressIndicator(modifier=Modifier.size(20.dp),strokeWidth=2.dp) else Icon(Icons.Default.LocationOn,"Моя позиция")}
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(locating || locationMessage!="Местоположение не определено") Surface(shape=RoundedCornerShape(10.dp),color=SurfaceGreen) {Text(locationMessage,modifier=Modifier.padding(10.dp),fontSize=11.sp,color=Muted)}
            Surface(shape=RoundedCornerShape(10.dp),color=SurfaceGreen.copy(alpha=.94f)) {Text("Демо · ${DemoData.stations.size} вымышленных локаций",modifier=Modifier.padding(horizontal=10.dp,vertical=6.dp),fontSize=11.sp,color=Muted)}
            if(selected!=null) Surface(modifier=Modifier.testTag("map-station-card"),shape=RoundedCornerShape(24.dp),color=SurfaceGreen,shadowElevation=8.dp) {
                Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {Text(selected.title,fontSize=18.sp,fontWeight=FontWeight.Bold);Text("${selected.city} · ДЕМО",color=Muted,fontSize=12.sp)}
                        IconButton(onClick={onDetail(selected)}) {Icon(Icons.Default.Place,"Маршрут и детали",tint=Lime)}
                        IconButton(onClick={selectedId=null}) {Icon(Icons.Default.Close,"Закрыть карточку")}
                    }
                    Text("${selected.chargerCount} ${if(selected.chargerCount==4) "аппарата" else "аппаратов"} · свободно ${selected.availableConnectors} из ${selected.connectorCount} разъёмов",color=Muted,fontSize=11.sp)
                    ConnectorLegend()
                    StationEquipment(selected) {onConnector(selected,it)}
                }
            }
            if(stations.isEmpty()) Surface(shape=RoundedCornerShape(18.dp),color=SurfaceGreen) {Text("Нет демо-станций по выбранным фильтрам",modifier=Modifier.padding(16.dp),color=Muted)}
        }
    }
    if(filters) StationFilterSheet(stations.size,city,minPower,statusName,onCity,onPower,onStatus) {filters=false}
}
