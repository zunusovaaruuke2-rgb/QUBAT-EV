package kg.qubat.ev

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import java.util.Locale

val Ru=Locale("ru","KG")
fun money(minor: Long) = String.format(Ru,"%,.2f сом",minor/100.0)
fun energy(kwh: Double) = String.format(Ru,"%.3f",kwh)
class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); enableEdgeToEdge(); setContent { QubatTheme { AppLanguageProvider { QubatLaunch() } } } }
}
private enum class Tab(val label: String,val icon: ImageVector) {
    HOME("Карта",Icons.Default.Home), STATIONS("Список",Icons.Default.Place), CHARGE("Зарядка",Icons.Default.PlayArrow), WALLET("Кошелёк",Icons.Default.AccountBox), PROFILE("Профиль",Icons.Default.Person)
}
@Composable internal fun QubatDriverApp(vm:ChargingViewModel,partnerLabel:String?,onPartner:()->Unit,onPartnerDemo:()->Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tabName by rememberSaveable { mutableStateOf(Tab.HOME.name) }
    val tab=Tab.valueOf(tabName)
    var selectedId by rememberSaveable { mutableStateOf(DemoData.stations.first().id) }
    var selectedConnectorId by rememberSaveable {mutableStateOf<String?>(null)}
    var focusedConnectorId by rememberSaveable {mutableStateOf<String?>(null)}
    var addingVehicle by rememberSaveable {mutableStateOf(false)}
    androidx.activity.compose.BackHandler(addingVehicle) {addingVehicle=false;tabName=Tab.CHARGE.name}
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val city:String?=null
    var minPower by rememberSaveable { mutableIntStateOf(0) }
    var statusName by rememberSaveable { mutableStateOf<String?>(null) }
    var location by remember { mutableStateOf<GeoPoint?>(null) }
    var locating by remember { mutableStateOf(false) }
    var locationMessage by remember { mutableStateOf("Местоположение не определено") }
    var locationFocus by remember {mutableIntStateOf(0)}
    var waitingForLocationFocus by remember {mutableStateOf(false)}
    var refiningLocation by remember {mutableStateOf(false)}
    val context=LocalContext.current
    val keyboard=LocalSoftwareKeyboardController.current
    LaunchedEffect(tab) { keyboard?.hide() }
    val scope=rememberCoroutineScope()
    val snackbar=remember { SnackbarHostState() }
    fun message(text:String) { scope.launch { snackbar.showSnackbar(text) } }
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val tracker=remember(context,lifecycle) {
        ForegroundLocation(context,lifecycle,onFix={fix->
            location=GeoPoint(fix.latitude,fix.longitude)
            if(waitingForLocationFocus || (refiningLocation && fix.accuracy<=100f)) {locationFocus++;waitingForLocationFocus=false}
            if(fix.accuracy<=100f) refiningLocation=false
        },onState={busy,text->locating=busy;locationMessage=text})
    }
    DisposableEffect(tracker) {onDispose {tracker.close()}}
    fun locate() {waitingForLocationFocus=true;refiningLocation=true;tracker.request()}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if(result.values.any { it }) locate() else { locationMessage="Разрешите доступ к геопозиции в настройках приложения."; locating=false }
    }
    fun askLocation() {
        if(context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED) locate()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))
    }
    LaunchedEffect(tracker) {
        if(context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED) locate()
    }
    val chargingPercents=state.charges.associate{it.session.connectorId to demoBatteryPercent(it.progress.energyKwh)}
    val stations=remember(chargingPercents,state.completedConnectorIds) {
        DemoData.stations.map {it.withConnectorSessions(chargingPercents,state.completedConnectorIds)}
    }
    val filtered=remember(stations,query,city,minPower,statusName) {filterStations(stations,query,city,minPower,statusName?.let { StationStatus.valueOf(it) })}
    val visible=remember(filtered,location) {location?.let { p -> filtered.sortedBy { distanceKm(p,it.point) } } ?: filtered}
    val selected=stations.first { it.id==selectedId }
    fun openConnector(site:Station,port:Connector) {
        if(state.charges.any{it.session.connectorId==port.id}) {
            addingVehicle=false;focusedConnectorId=null;tabName=Tab.CHARGE.name
        } else if(port.state==ConnectorState.FREE) {
            if(state.charges.size>=ChargingEngine.MAX_CARS) {message("Уже заряжаются два автомобиля");return}
            selectedId=site.id;selectedConnectorId=port.id;addingVehicle=state.active
            focusedConnectorId=null;tabName=Tab.CHARGE.name
        } else focusedConnectorId=port.id
    }
    run {
        Scaffold(modifier=Modifier.imePadding(),containerColor=Forest,snackbarHost={ SnackbarHost(snackbar) {data->Snackbar {AppText(data.visuals.message)}} },bottomBar={
            NavigationBar(modifier=Modifier.depthFrame(RoundedCornerShape(topStart=18.dp,topEnd=18.dp),SurfaceGreen,0.dp,fill=false),containerColor=SurfaceGreen) { Tab.entries.forEach { item -> PressNavigationBarItem(selected=tab==item,onClick={tabName=item.name},icon={AppIcon(item.icon,item.label)},label={AppText(item.label,fontSize=12.sp,maxLines=1)},colors=NavigationBarItemDefaults.colors(indicatorColor=MaterialTheme.colorScheme.primary.copy(alpha=.15f),selectedIconColor=Lime,selectedTextColor=Lime,unselectedIconColor=Muted,unselectedTextColor=Muted)) } }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).pageOpening(tab)) {
                if(tab==Tab.HOME) {
                    MapHome(visible,query,location,locationFocus,locating,locationMessage,minPower,statusName,
                        onQuery={query=it},
                        onCity={},onPower={minPower=it},onStatus={statusName=it},onLocate={askLocation()},
                        onDetail={detailId=it.id;selectedId=it.id},onConnector=::openConnector)
                } else if(tab==Tab.STATIONS) {
                    StationsList(visible,query,city,minPower,statusName,location,
                        onQuery={query=it},onCity={},onPower={minPower=it},onStatus={statusName=it},
                        onDetail={detailId=it.id},onConnector=::openConnector)
                } else if(tab==Tab.WALLET) {
                    WalletContent(state.availableBalanceMinor,state.active,state.history) {amount,method->vm.topUp(amount,method)}
                } else if(tab==Tab.PROFILE) {
                    ProfileContent(state.availableBalanceMinor,partnerLabel=partnerLabel,onPartner=onPartner,onPartnerDemo=onPartnerDemo) {tabName=Tab.WALLET.name}
                } else {
                if(tab==Tab.CHARGE) {
                Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
                    BrandMark(44.dp)
                    Column(Modifier.weight(1f).padding(start=12.dp)) { AppText("QUBAT EV",fontWeight=FontWeight.Black,fontSize=21.sp,letterSpacing=2.sp); AppText("Энергия для всего Кыргызстана",color=Muted,fontSize=11.sp,lineHeight=14.sp,maxLines=2) }
                    ThemeToggle()
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.3f))
                }
                Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal=14.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    when(tab) {
                        Tab.HOME -> Unit
                        Tab.STATIONS -> Unit
                        Tab.CHARGE -> {
                            val station=selected
                            val chosenId=selectedConnectorId
                            val chosenPort=station.connectors.firstOrNull {it.id==chosenId}
                                ?: station.connectors.firstOrNull {it.state==ConnectorState.FREE}
                            ChargeContent(station,chosenPort,state,addingVehicle,
                                onChoose={selectedId=station.id;selectedConnectorId=it.id},
                                onStart={if(vm.start(station,chosenPort?.id)) {selectedConnectorId=chosenPort?.id;addingVehicle=false} else message("Разъём недоступен или недостаточно учебного баланса")},
                                onStop={id->vm.stop(id)},
                                onRelease={chosenPort?.let {vm.releaseConnector(it.id)}},
                                onStations={tabName=Tab.STATIONS.name},
                                onAddVehicle={addingVehicle=true;selectedConnectorId=null;query="";minPower=0;statusName=StationStatus.AVAILABLE.name;tabName=Tab.STATIONS.name},
                                onCancel={addingVehicle=false})
                        }
                        Tab.WALLET -> Unit
                        Tab.PROFILE -> Unit
                    }
                }
                }
            }
            focusedConnectorId?.let {id->
                val site=stations.firstOrNull {s->s.connectors.any {it.id==id}}
                val port=site?.connectors?.firstOrNull {it.id==id}
                if(site!=null && port!=null) AlertDialog(modifier=Modifier.depthFrame(RoundedCornerShape(28.dp),MaterialTheme.colorScheme.surfaceContainerHigh,3.dp,fill=false),onDismissRequest={focusedConnectorId=null},
                    title={AppText("Аппарат ${site.chargerNumber(id)} · разъём ${port.number}")},
                    text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        AppText(port.state.title,color=Color(port.state.argb),fontWeight=FontWeight.Bold,modifier=Modifier.testTag("connector-dialog-state"))
                        AppText("${site.city} · ${site.title}")
                        AppText("GB/T DC · 80 кВт · 13,5 сом/кВт·ч",color=Muted,fontSize=13.sp)
                        port.percent?.let {AppText("Демо-уровень заряда: $it%",color=Color(port.state.argb))}
                        AppText(when(port.state) {
                            ConnectorState.FREE -> "Разъём свободен. Можно выбрать для учебной зарядки."
                            ConnectorState.CONNECTED -> "Автомобиль уже подключён и ожидает старта. Разъём занят."
                            ConnectorState.CHARGING -> "Идёт демонстрационная зарядка. Разъём занят."
                            ConnectorState.COMPLETED -> "Сеанс завершён. Разъём ещё не освобождён."
                            ConnectorState.ERROR -> "Демонстрационная ошибка разъёма. Запуск недоступен."
                            ConnectorState.OFFLINE -> "Разъём недоступен."
                        },color=Muted,fontSize=13.sp)
                    }},confirmButton={
                        if(id in state.completedConnectorIds) PressTextButton(onClick={vm.releaseConnector(id);focusedConnectorId=null}) {AppText("Освободить разъём")}
                    },dismissButton={PressTextButton(onClick={focusedConnectorId=null}) {AppText("Закрыть")}})
            }
            detailId?.let { id ->
                val s=stations.first {it.id==id}
                AlertDialog(modifier=Modifier.depthFrame(RoundedCornerShape(28.dp),MaterialTheme.colorScheme.surfaceContainerHigh,3.dp,fill=false),onDismissRequest={detailId=null},title={AppText("${s.city} · ДЕМО")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    AppText(s.title,fontWeight=FontWeight.Bold);AppText("${s.status.title} · ${s.availableConnectors} из ${s.connectorCount} мест свободно\n80 кВт на место · GB/T DC · 13,5 сом/кВт·ч")
                    AppText("Вымышленная станция у центра города. По этим координатам зарядной станции может не быть. Маршрут — только для проверки интерфейса.",color=Amber)
                    AppText("${s.point.lat}, ${s.point.lon}",color=Muted)
                    DepthOutlinedButton(onClick={openRoute(context,s,::message)},modifier=Modifier.fillMaxWidth()) {AppText("Учебный маршрут · 2ГИС")}
                    PressTextButton(onClick={openGoogleRoute(context,s,::message)}) {AppText("Открыть маршрут в Google Maps")}
                }},confirmButton={PressTextButton(onClick={selectedId=s.id;selectedConnectorId=null;addingVehicle=state.active && state.charges.size<ChargingEngine.MAX_CARS;detailId=null;tabName=Tab.CHARGE.name}) {AppText("К зарядке")}},dismissButton={PressTextButton(onClick={detailId=null}){AppText("Закрыть")}})
            }
        }
    }
}
@Composable fun Eyebrow(text:String) {AppText(text,color=Lime,fontSize=10.sp,letterSpacing=2.sp,fontWeight=FontWeight.Bold)}
@Composable fun Title(text:String) {AppText(text,fontSize=25.sp,fontWeight=FontWeight.Bold)}
@Composable fun Panel(content:@Composable ColumnScope.()->Unit) { Column(Modifier.fillMaxWidth().depthFrame(RoundedCornerShape(22.dp),SurfaceGreen).padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content) }
@Composable fun DemoNotice() {Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF2B3624)).padding(12.dp),verticalAlignment=Alignment.CenterVertically) {AppIcon(Icons.Default.Info,null,tint=Amber,modifier=Modifier.size(18.dp));AppText(" Демо-данные. Станции вымышленные. Платежей нет.",color=Amber,fontSize=11.sp)}}
@Composable fun Metric(label:String,value:String,modifier:Modifier) {Column(modifier.depthFrame(RoundedCornerShape(18.dp),SurfaceGreen).padding(16.dp)) {AppText(label,color=Muted,fontSize=12.sp);Spacer(Modifier.height(8.dp));AppText(value,fontSize=17.sp,fontWeight=FontWeight.Bold)}}
@Composable fun FlowRowCompatStatus(selected:String?,onSelect:(String?)->Unit) {
    Column {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        DepthChip(selected=selected==null,onClick={onSelect(null)},label={AppText("Все")})
        DepthChip(selected=selected==StationStatus.AVAILABLE.name,onClick={onSelect(StationStatus.AVAILABLE.name)},label={AppText("Свободны")})
    };Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {StationStatus.entries.filter {it!=StationStatus.AVAILABLE}.forEach {s -> DepthChip(selected=selected==s.name,onClick={onSelect(s.name)},label={AppText(s.title)})}} }
}
fun openRoute(context:android.content.Context,s:Station,message:(String)->Unit) {
    // Official 2GIS deep link, longitude first; navigator determines the source.
    val uri=Uri.parse("dgis://2gis.ru/routeSearch/rsType/car/to/${s.point.lon},${s.point.lat}")
    runCatching {context.startActivity(Intent(Intent.ACTION_VIEW,uri))}.onFailure {message("Установите 2ГИС или выберите Google Maps")}
}
fun openGoogleRoute(context:android.content.Context,s:Station,message:(String)->Unit) {
    val uri=Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${s.point.lat},${s.point.lon}&travelmode=driving")
    runCatching {context.startActivity(Intent(Intent.ACTION_VIEW,uri))}.onFailure {message("Не найдено приложение для маршрута")}
}
