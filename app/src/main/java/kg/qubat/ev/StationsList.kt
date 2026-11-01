package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    ModalBottomSheet(onDismissRequest=onDismiss,containerColor=SurfaceGreen) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Title("Фильтры")
            Text("Мощность аппарата",color=Muted)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf(0,50,80,120).forEach {power->
                FilterChip(selected=minPower==power,onClick={onPower(power)},label={Text(if(power==0)"Любая" else "≥ $power кВт")})
            }}
            Text("Доступность локации",color=Muted);FlowRowCompatStatus(statusName,onStatus)
            Text("Разъёмы · GB/T DC",color=Muted,fontSize=13.sp)
            Button(onClick=onDismiss,modifier=Modifier.fillMaxWidth()) {Text("Показать станции · $count")}
            TextButton(onClick={onCity(null);onPower(0);onStatus(null)}) {Text("Сбросить фильтры")}
        }
    }
}
@Composable fun StationsList(stations:List<Station>,query:String,city:String?,minPower:Int,statusName:String?,location:GeoPoint?,
    onQuery:(String)->Unit,onCity:(String?)->Unit,onPower:(Int)->Unit,onStatus:(String?)->Unit,onDetail:(Station)->Unit,onConnector:(Station,Connector)->Unit) {
    var filters by rememberSaveable {mutableStateOf(false)}
    val keyboard=LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxSize()) {
        StationSearchControls(query,onQuery,{keyboard?.hide();filters=true},"Фильтры списка")
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=12.dp,vertical=4.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(stations.isEmpty()) item {
                Panel {Text("Демо-станций пока нет");Text("С такими фильтрами учебных точек нет.",color=Muted)
                    TextButton(onClick={onQuery("");onCity(null);onPower(0);onStatus(null)}) {Text("Сбросить фильтры")}}
            }
            items(stations,key={it.id}) {station->LocationEquipmentCard(station,location,{onDetail(station)}) {onConnector(station,it)}}
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
            OutlinedTextField(query,onQuery,modifier=Modifier.weight(1f).then(Modifier.focusRequester(focus)).testTag("station-search-field"),singleLine=true,
                placeholder={Text("Название станции",fontSize=14.sp)},leadingIcon={Icon(Icons.Default.Search,null)},
                trailingIcon={IconButton(onClick={onQuery("");expanded=false;keyboard?.hide()}) {Icon(Icons.Default.Close,"Закрыть поиск")}},
                shape=RoundedCornerShape(16.dp),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={keyboard?.hide()}))
            LaunchedEffect(Unit) {focus.requestFocus();keyboard?.show()}
        } else {
            Surface(shape=RoundedCornerShape(16.dp),color=SurfaceGreen,shadowElevation=3.dp) {
                IconButton(onClick={expanded=true}) {Icon(Icons.Default.Search,"Поиск станции",tint=Lime)}
            }
            if(query.isNotBlank()) Text(query,modifier=Modifier.weight(1f),maxLines=1,fontSize=13.sp) else Spacer(Modifier.weight(1f))
            Surface(shape=RoundedCornerShape(16.dp),color=SurfaceGreen) {ThemeToggle()}
            Surface(shape=RoundedCornerShape(16.dp),color=SurfaceGreen) {IconButton(onClick=onFilters) {Icon(Icons.Default.Settings,filterDescription,tint=Lime)}}
        }
    }
}
