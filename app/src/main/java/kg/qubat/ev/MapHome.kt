package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MapHome(
    stations: List<Station>, query:String, location: GeoPoint?, locationFocus:Int, locating: Boolean,
    locationMessage: String, minPower: Int, statusName: String?,
    onQuery:(String)->Unit,onCity: (String?)->Unit, onPower: (Int)->Unit, onStatus: (String?)->Unit,
    onLocate: ()->Unit, onDetail: (Station)->Unit, onConnector: (Station,Connector)->Unit
) {
    val context=androidx.compose.ui.platform.LocalContext.current
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var filters by rememberSaveable { mutableStateOf(false) }
    var zoomRequest by remember {mutableIntStateOf(0)}
    var focusRequest by remember { mutableIntStateOf(0) }
    var stationFocus by remember {mutableIntStateOf(0)}
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val selected=stations.firstOrNull {it.id==selectedId}
    BackHandler(enabled=selected!=null) {selectedId=null}
    Box(Modifier.fillMaxSize()) {
        CountryMap(stations,null,location,selectedId ?: "",Modifier.fillMaxSize(),focusRequest,zoomRequest,locationFocus,stationFocus) { selectedId=it.id }
        Column(Modifier.align(Alignment.TopCenter)) {
            StationSearchControls(query,onQuery,{filters=true},"Фильтры карты")
            if(query.isNotBlank()) DepthSurface(Modifier.padding(horizontal=12.dp),shape=RoundedCornerShape(16.dp),color=SurfaceGreen,shadowElevation=4.dp) {
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max=220.dp)) {
                    if(stations.isEmpty()) item {AppText("Станция не найдена",Modifier.padding(16.dp),fontSize=13.sp)}
                    items(stations.size) {index->val station=stations[index]
                        PressTextButton(onClick={selectedId=station.id;stationFocus++;onQuery("");keyboard?.hide()},modifier=Modifier.fillMaxWidth()) {Column(Modifier.fillMaxWidth().padding(6.dp)) {AppText(station.title,fontWeight=FontWeight.SemiBold);AppText(station.city,fontSize=11.sp,color=Muted)}}
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.CenterEnd).padding(end=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            PressFilledIconButton(onClick={zoomRequest++},shape=RoundedCornerShape(16.dp),modifier=Modifier.size(50.dp).depthFrame(RoundedCornerShape(16.dp),SurfaceGreen,3.dp,fill=false).appDescription("Увеличить карту"),colors=IconButtonDefaults.filledIconButtonColors(containerColor=SurfaceGreen,contentColor=Lime)) {AppText("+",fontSize=28.sp)}
            PressFilledIconButton(onClick={zoomRequest--},shape=RoundedCornerShape(16.dp),modifier=Modifier.size(50.dp).depthFrame(RoundedCornerShape(16.dp),SurfaceGreen,3.dp,fill=false).appDescription("Уменьшить карту"),colors=IconButtonDefaults.filledIconButtonColors(containerColor=SurfaceGreen,contentColor=Lime)) {AppText("−",fontSize=28.sp)}
            PressFilledIconButton(onClick={focusRequest++},shape=RoundedCornerShape(16.dp),modifier=Modifier.size(50.dp).depthFrame(RoundedCornerShape(16.dp),SurfaceGreen,3.dp,fill=false),colors=IconButtonDefaults.filledIconButtonColors(containerColor=SurfaceGreen,contentColor=Lime)) {AppIcon(Icons.Default.Refresh,"Обзор Кыргызстана")}
            PressFilledIconButton(onClick={selectedId=null;onLocate()},enabled=!locating,shape=RoundedCornerShape(16.dp),modifier=Modifier.size(50.dp).depthFrame(RoundedCornerShape(16.dp),SurfaceGreen,3.dp,fill=false).appDescription("Моя позиция"),colors=IconButtonDefaults.filledIconButtonColors(containerColor=SurfaceGreen,contentColor=Lime)) {if(locating) CircularProgressIndicator(modifier=Modifier.size(20.dp),strokeWidth=2.dp) else AppIcon(Icons.Default.LocationOn,null)}
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(locating || locationMessage!="Местоположение не определено") DepthSurface(shape=RoundedCornerShape(10.dp),color=SurfaceGreen) {
                Column(Modifier.padding(10.dp)) {
                    AppText(locationMessage,fontSize=11.sp,color=Muted)
                    if(locationMessage.contains("настройках") || locationMessage.contains("точную геопозицию")) PressTextButton(onClick={
                        val intent=if(locationMessage.contains("Разрешите") || locationMessage.contains("точную геопозицию")) android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:${context.packageName}")) else android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        runCatching {context.startActivity(intent)}
                    }) {AppText("Настройки геопозиции",fontSize=12.sp)}
                }
            }
            DepthSurface(shape=RoundedCornerShape(10.dp),color=SurfaceGreen.copy(alpha=.94f)) {AppText("ДЕМО",modifier=Modifier.padding(horizontal=10.dp,vertical=6.dp),fontSize=11.sp,color=Muted)}
            if(selected!=null) DepthSurface(onClick={onDetail(selected)},modifier=Modifier.testTag("map-station-card"),shape=RoundedCornerShape(24.dp),color=SurfaceGreen,shadowElevation=8.dp) {
                Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {AppText(selected.title,fontSize=18.sp,fontWeight=FontWeight.Bold);AppText("${selected.city} · ДЕМО",color=Muted,fontSize=12.sp)}
                        PressIconButton(onClick={onDetail(selected)}) {AppIcon(Icons.Default.Place,"Маршрут и детали",tint=Lime)}
                        PressIconButton(onClick={selectedId=null}) {AppIcon(Icons.Default.Close,"Закрыть карточку")}
                    }
                    AppText("${selected.chargerCount} ${if(selected.chargerCount==4) "аппарата" else "аппаратов"} · свободно ${selected.availableConnectors} из ${selected.connectorCount} разъёмов",color=Muted,fontSize=11.sp)
                    StationEquipment(selected) {onConnector(selected,it)}
                }
            }
            if(stations.isEmpty()) DepthSurface(shape=RoundedCornerShape(18.dp),color=SurfaceGreen) {AppText("Нет демо-станций по выбранным фильтрам",modifier=Modifier.padding(16.dp),color=Muted)}
        }
    }
    if(filters) StationFilterSheet(stations.size,null,minPower,statusName,onCity,onPower,onStatus) {filters=false}
}
