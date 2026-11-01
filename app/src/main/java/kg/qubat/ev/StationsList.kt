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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun StationFilterSheet(count:Int,city:String?,minPower:Int,statusName:String?,onCity:(String?)->Unit,onPower:(Int)->Unit,onStatus:(String?)->Unit,onDismiss:()->Unit) {
    ModalBottomSheet(onDismissRequest=onDismiss,containerColor=SurfaceGreen) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Title("Фильтры");CityPicker(city,onCity)
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
        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query,onQuery,modifier=Modifier.weight(1f),singleLine=true,label={Text("Город или название станции",fontSize=12.sp)},
                leadingIcon={Icon(Icons.Default.Search,null)},shape=RoundedCornerShape(16.dp),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={keyboard?.hide()}))
            ThemeToggle()
            FilledIconButton(onClick={keyboard?.hide();filters=true}) {Icon(Icons.Default.Settings,"Фильтры списка")}
        }
        Column(Modifier.padding(horizontal=16.dp).padding(bottom=10.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Text("Станции Кыргызстана",fontSize=19.sp)
            Text("Демо · найдено ${stations.size} локаций",color=Muted,fontSize=11.sp)
            ConnectorLegend()
        }
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
