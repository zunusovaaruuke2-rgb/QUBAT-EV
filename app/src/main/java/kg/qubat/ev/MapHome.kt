package kg.qubat.ev

import androidx.compose.foundation.layout.*
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
    onLocate: ()->Unit, onDetail: (Station)->Unit, onCharge: (Station)->Unit
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var filters by rememberSaveable { mutableStateOf(false) }
    var zoomRequest by remember {mutableIntStateOf(0)}
    var focusRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(location) { location?.let { selectedId=nearestStation(stations,it)?.id } }
    val selected=stations.firstOrNull {it.id==selectedId}
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
            if(selected!=null) Surface(shape=RoundedCornerShape(24.dp),color=SurfaceGreen,shadowElevation=8.dp) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {Text(selected.city,fontSize=20.sp,fontWeight=FontWeight.Bold);Text(selected.title,color=Muted,fontSize=12.sp)}
                        Text(selected.status.title,color=if(selected.status==StationStatus.AVAILABLE)Lime else Amber,fontSize=12.sp)
                        IconButton(onClick={selectedId=null}) {Icon(Icons.Default.Close,"Закрыть карточку")}
                    }
                    Text("${selected.availableConnectors} из ${selected.connectorCount} мест свободно · GB/T DC",color=Lime,fontSize=13.sp)
                    Text("80 кВт на место · 13,5 сом/кВт·ч",color=Muted,fontSize=12.sp)
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick={onDetail(selected)},modifier=Modifier.weight(1f)) {Text("Подробнее")}
                        Button(onClick={onCharge(selected)},modifier=Modifier.weight(1f)) {Text("Зарядка")}
                    }
                }
            }
            if(stations.isEmpty()) Surface(shape=RoundedCornerShape(18.dp),color=SurfaceGreen) {Text("Нет демо-станций по выбранным фильтрам",modifier=Modifier.padding(16.dp),color=Muted)}
        }
    }
    if(filters) ModalBottomSheet(onDismissRequest={filters=false},containerColor=SurfaceGreen) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Фильтры",fontSize=25.sp,fontWeight=FontWeight.Bold)
            CityPicker(city,onCity)
            Text("Мощность",color=Muted)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf(0,50,80,120).forEach { power -> FilterChip(selected=minPower==power,onClick={onPower(power)},label={Text(if(power==0) "Любая" else "≥ $power кВт")})}}
            Text("Статус",color=Muted)
            FlowRowCompatStatus(statusName,onStatus)
            Text("Разъём · GB/T DC",color=Muted,fontSize=13.sp)
            Button(onClick={filters=false},modifier=Modifier.fillMaxWidth()) {Text("Показать станции · ${stations.size}")}
            TextButton(onClick={onCity(null);onPower(0);onStatus(null)}) {Text("Сбросить фильтры")}
        }
    }
}
