package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun StationFilterSheet(count:Int,city:String?,minPower:Int,statusName:String?,onCity:(String?)->Unit,onPower:(Int)->Unit,onStatus:(String?)->Unit,onDismiss:()->Unit) {
    ModalBottomSheet(modifier=Modifier.depthFrame(RoundedCornerShape(topStart=28.dp,topEnd=28.dp),SurfaceGreen,3.dp,fill=false),onDismissRequest=onDismiss,containerColor=SurfaceGreen) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Title("Фильтры")
            AppText("Мощность аппарата",color=Muted)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf(0,50,80,120).forEach {power->
                DepthChip(selected=minPower==power,onClick={onPower(power)},label={AppText(if(power==0)"Любая" else "≥ $power кВт")})
            }}
            AppText("Доступность локации",color=Muted);FlowRowCompatStatus(statusName,onStatus)
            AppText("Разъёмы · GB/T DC",color=Muted,fontSize=13.sp)
            DepthButton(onClick=onDismiss,modifier=Modifier.fillMaxWidth()) {AppText("Показать станции · $count")}
            TextButton(onClick={onCity(null);onPower(0);onStatus(null)}) {AppText("Сбросить фильтры")}
        }
    }
}
@Composable fun StationsList(stations:List<Station>,query:String,city:String?,minPower:Int,statusName:String?,location:GeoPoint?,
    onQuery:(String)->Unit,onCity:(String?)->Unit,onPower:(Int)->Unit,onStatus:(String?)->Unit,onDetail:(Station)->Unit,onConnector:(Station,Connector)->Unit) {
    var filters by rememberSaveable {mutableStateOf(false)}
    val keyboard=LocalSoftwareKeyboardController.current
    val listState=rememberLazyListState()
    val detailCallback=rememberUpdatedState(onDetail)
    val connectorCallback=rememberUpdatedState(onConnector)
    var displayedLocation by remember {mutableStateOf(location)}
    var orderedIds by remember {mutableStateOf(stations.map {it.id})}
    val scrolling by remember {derivedStateOf {listState.isScrollInProgress}}
    LaunchedEffect(stations,location,scrolling) {if(!scrolling) {displayedLocation=location;orderedIds=stations.map {it.id}}}
    val ordered=remember(stations,orderedIds,query) {val rank=orderedIds.withIndex().associate {it.value to it.index};stations.sortedBy {rank[it.id] ?: Int.MAX_VALUE}}
    Column(Modifier.fillMaxSize()) {
        StationSearchControls(query,onQuery,{keyboard?.hide();filters=true},"Фильтры списка")
        LazyColumn(Modifier.weight(1f).testTag("stations-list"),state=listState,contentPadding=PaddingValues(horizontal=12.dp,vertical=4.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(stations.isEmpty()) item {
                Panel {AppText("Демо-станций пока нет");AppText("С такими фильтрами учебных точек нет.",color=Muted)
                    TextButton(onClick={onQuery("");onCity(null);onPower(0);onStatus(null)}) {AppText("Сбросить фильтры")}}
            }
            items(ordered,key={it.id},contentType={it.chargerCount}) {station->
                val latestStation=rememberUpdatedState(station)
                val openDetail=remember(station.id) {{detailCallback.value(latestStation.value)}}
                val openConnector=remember(station.id) {{port:Connector->connectorCallback.value(latestStation.value,port)}}
                LocationEquipmentCard(station,displayedLocation,openDetail,openConnector)
            }
            item {Spacer(Modifier.height(12.dp))}
        }
    }
    if(filters) StationFilterSheet(stations.size,city,minPower,statusName,onCity,onPower,onStatus) {filters=false}
}

@Composable fun StationSearchControls(query:String,onQuery:(String)->Unit,onFilters:()->Unit,filterDescription:String) {
    var expanded by rememberSaveable {mutableStateOf(false)}
    val keyboard=LocalSoftwareKeyboardController.current
    val focus=remember {androidx.compose.ui.focus.FocusRequester()}
    androidx.activity.compose.BackHandler(expanded) {expanded=false;onQuery("");keyboard?.hide()}
    Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        if(expanded) {
            OutlinedTextField(query,onQuery,modifier=Modifier.weight(1f).then(Modifier.focusRequester(focus)).testTag("station-search-field").depthFrame(RoundedCornerShape(16.dp),SurfaceGreen,1.dp,fill=false),singleLine=true,
                placeholder={AppText("Название станции",fontSize=14.sp)},leadingIcon={AppIcon(Icons.Default.Search,null)},
                trailingIcon={IconButton(onClick={onQuery("");expanded=false;keyboard?.hide()}) {AppIcon(Icons.Default.Close,"Закрыть поиск")}},
                shape=RoundedCornerShape(16.dp),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={keyboard?.hide()}))
            LaunchedEffect(Unit) {focus.requestFocus();keyboard?.show()}
        } else {
            DepthSurface(shape=RoundedCornerShape(16.dp),color=SurfaceGreen,shadowElevation=3.dp) {
                IconButton(onClick={expanded=true}) {AppIcon(Icons.Default.Search,"Поиск станции",tint=Lime)}
            }
            if(query.isNotBlank()) AppText(query,modifier=Modifier.weight(1f),maxLines=1,fontSize=13.sp) else Spacer(Modifier.weight(1f))
            DepthSurface(shape=RoundedCornerShape(16.dp),color=SurfaceGreen) {ThemeToggle()}
            DepthSurface(shape=RoundedCornerShape(16.dp),color=SurfaceGreen) {IconButton(onClick=onFilters) {AppIcon(Icons.Default.Settings,filterDescription,tint=Lime)}}
        }
    }
}
