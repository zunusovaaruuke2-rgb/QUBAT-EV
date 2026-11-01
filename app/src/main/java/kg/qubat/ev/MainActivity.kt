package kg.qubat.ev

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Forest=Color(0xFF081D17)
val SurfaceGreen=Color(0xFF112D24)
val Lime=Color(0xFFB5F481)
val Muted=Color(0xFFA5BFB2)
val Amber=Color(0xFFFFCF80)
val Ru=Locale("ru","KG")
fun money(minor: Long) = String.format(Ru,"%,.2f сом",minor/100.0)
fun energy(kwh: Double) = String.format(Ru,"%.3f",kwh)
class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); enableEdgeToEdge(); setContent { QubatApp() } }
}
private enum class Tab(val label: String,val icon: ImageVector) {
    HOME("Карта",Icons.Default.Home), STATIONS("Список",Icons.Default.Place), CHARGE("Зарядка",Icons.Default.PlayArrow), WALLET("Кошелёк",Icons.Default.AccountBox), PROFILE("Профиль",Icons.Default.Person)
}
@Composable fun QubatApp(vm: ChargingViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tabName by rememberSaveable { mutableStateOf(Tab.HOME.name) }
    val tab=Tab.valueOf(tabName)
    var selectedId by rememberSaveable { mutableStateOf(DemoData.stations.first().id) }
    var selectedConnectorId by rememberSaveable {mutableStateOf<String?>(null)}
    var focusedConnectorId by rememberSaveable {mutableStateOf<String?>(null)}
    LaunchedEffect(state.session?.connectorId) {
        state.session?.let {selectedId=it.stationId;selectedConnectorId=it.connectorId}
    }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf<String?>(null) }
    var minPower by rememberSaveable { mutableIntStateOf(0) }
    var statusName by rememberSaveable { mutableStateOf<String?>(null) }
    var location by remember { mutableStateOf<GeoPoint?>(null) }
    var locating by remember { mutableStateOf(false) }
    var locationMessage by remember { mutableStateOf("Местоположение не определено") }
    var cancelLocation by remember { mutableStateOf<CancellationSignal?>(null) }
    val context=LocalContext.current
    val keyboard=LocalSoftwareKeyboardController.current
    val contentScroll=key(tab) { rememberScrollState() }
    LaunchedEffect(tab) { keyboard?.hide() }
    val scope=rememberCoroutineScope()
    val snackbar=remember { SnackbarHostState() }
    fun message(text:String) { scope.launch { snackbar.showSnackbar(text) } }
    DisposableEffect(Unit) { onDispose { cancelLocation?.cancel() } }
    fun locate() {
        cancelLocation?.cancel(); locating=true; locationMessage="Определяем местоположение…"
        val manager=context.getSystemService(LocationManager::class.java)
        val provider=listOf(LocationManager.NETWORK_PROVIDER,LocationManager.GPS_PROVIDER).firstOrNull { manager.isProviderEnabled(it) }
        if(provider==null) { locating=false; locationMessage="Включите геолокацию в настройках устройства"; return }
        val cancellation=CancellationSignal(); cancelLocation=cancellation
        try {
            if(android.os.Build.VERSION.SDK_INT >= 30) {
                manager.getCurrentLocation(provider,cancellation,context.mainExecutor) { result ->
                    locating=false
                    if(result==null) locationMessage="Не удалось определить положение. Выберите город вручную."
                    else { location=GeoPoint(result.latitude,result.longitude); locationMessage="Расстояние по прямой от вашей позиции" }
                }
                scope.launch { kotlinx.coroutines.delay(15000); if(locating && cancelLocation===cancellation) { cancellation.cancel(); locating=false; locationMessage="Нет сигнала. Выберите город вручную." } }
            } else {
                // One foreground update for Android 8–10; no background tracking.
                val listener=object: android.location.LocationListener {
                    override fun onLocationChanged(result: android.location.Location) {
                        manager.removeUpdates(this); locating=false
                        location=GeoPoint(result.latitude,result.longitude); locationMessage="Расстояние по прямой от вашей позиции"
                    }
                    override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?) {}
                    override fun onProviderEnabled(provider:String) {}
                    override fun onProviderDisabled(provider:String) {}
                }
                manager.requestLocationUpdates(provider,0L,0f,listener,android.os.Looper.getMainLooper())
                cancellation.setOnCancelListener { manager.removeUpdates(listener) }
                scope.launch { kotlinx.coroutines.delay(15000); if(locating && cancelLocation===cancellation) { cancellation.cancel(); locating=false; locationMessage="Нет сигнала. Выберите город вручную." } }
            }
        } catch (_: SecurityException) { locating=false; locationMessage="Нет доступа к геопозиции. Выберите город." }
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if(result.values.any { it }) locate() else { locationMessage="Доступ не предоставлен. Доступен выбор города."; locating=false }
    }
    fun askLocation() {
        if(context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED) locate()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))
    }
    val stations=remember(state.session?.connectorId,state.completedConnectorIds) {
        DemoData.stations.map {it.withConnectorSession(state.session?.connectorId,state.completedConnectorIds)}
    }
    val filtered=filterStations(stations,query,city,minPower,statusName?.let { StationStatus.valueOf(it) })
    val visible=location?.let { p -> filtered.sortedBy { distanceKm(p,it.point) } } ?: filtered
    val selected=stations.first { it.id==selectedId }
    val sessionStation=state.session?.let { s -> stations.first { it.id==s.stationId } }
    MaterialTheme(colorScheme=darkColorScheme(primary=Lime,onPrimary=Forest,background=Forest,onBackground=Color(0xFFF0F8EF),surface=SurfaceGreen,onSurface=Color(0xFFF0F8EF),onSurfaceVariant=Muted,secondary=Lime,outline=Color(0xFF315044))) {
        Scaffold(modifier=Modifier.imePadding(),containerColor=Forest,snackbarHost={ SnackbarHost(snackbar) },bottomBar={
            NavigationBar(containerColor=Color(0xFF0C241C)) { Tab.entries.forEach { item -> NavigationBarItem(selected=tab==item,onClick={tabName=item.name},icon={Icon(item.icon,item.label)},label={Text(item.label,fontSize=10.sp,maxLines=1)},colors=NavigationBarItemDefaults.colors(indicatorColor=Color(0xFF284B36),selectedIconColor=Lime,selectedTextColor=Lime,unselectedIconColor=Muted,unselectedTextColor=Muted)) } }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if(tab==Tab.HOME) {
                    MapHome(filterStations(stations,"",city,minPower,statusName?.let { StationStatus.valueOf(it) }),city,location,locating,locationMessage,minPower,statusName,
                        onCity={city=it},onPower={minPower=it},onStatus={statusName=it},onLocate={askLocation()},
                        onDetail={detailId=it.id;selectedId=it.id},onConnector={_,port->focusedConnectorId=port.id})
                } else if(tab==Tab.STATIONS) {
                    StationsList(visible,query,city,minPower,statusName,location,
                        onQuery={query=it},onCity={city=it},onPower={minPower=it},onStatus={statusName=it},
                        onDetail={detailId=it.id},onConnector={_,port->focusedConnectorId=port.id})
                } else {
                Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Lime),contentAlignment=Alignment.Center) { Icon(Icons.Default.PlayArrow,"QUBAT",tint=Forest) }
                    Column(Modifier.weight(1f).padding(start=12.dp)) { Text("QUBAT EV",fontWeight=FontWeight.Black,fontSize=21.sp,letterSpacing=2.sp); Text("Энергия для всего Кыргызстана",color=Muted,fontSize=11.sp) }
                    Text("ДЕМО",color=Lime,fontSize=11.sp,fontWeight=FontWeight.Bold)
                }
                HorizontalDivider(color=Color(0xFF203C30))
                Column(Modifier.weight(1f).verticalScroll(contentScroll).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    when(tab) {
                        Tab.HOME -> Unit
                        Tab.STATIONS -> Unit
                        Tab.CHARGE -> {
                            Eyebrow("УЧЕБНЫЙ СЕАНС"); Title(if(state.session!=null) "Зарядка идёт" else "Готовы к зарядке?")
                            DemoNotice()
                            val station=sessionStation ?: selected
                            val chosenId=state.session?.connectorId ?: selectedConnectorId
                            val chosenPort=station.connectors.firstOrNull {it.id==chosenId}
                                ?: station.connectors.firstOrNull {it.state==ConnectorState.FREE}
                            Panel {
                                Text(station.city,color=Lime,fontSize=13.sp); Text(station.title,fontSize=22.sp,fontWeight=FontWeight.Bold)
                                Text("GB/T DC  ·  80 кВт  ·  13,5 сом/кВт·ч",color=Muted,fontSize=13.sp)
                                chosenPort?.let {Text("Аппарат ${station.chargerNumber(it.id)} · разъём ${it.number} · ${it.state.title}",color=Color(it.state.argb),fontSize=13.sp)}
                                Text(if(state.session!=null) "● Симуляция активна" else station.status.title,color=if(station.status==StationStatus.AVAILABLE)Lime else Amber)
                            }
                            Box(Modifier.fillMaxWidth().height(220.dp),contentAlignment=Alignment.Center) {
                                CircularProgressIndicator(progress={if(state.session!=null) (state.progress.energyKwh/(state.session!!.budgetMinor/1350.0)).toFloat().coerceIn(0f,1f) else 0f},modifier=Modifier.size(204.dp),color=Lime,trackColor=Color(0xFF234337),strokeWidth=8.dp)
                                Column(horizontalAlignment=Alignment.CenterHorizontally) { Text(energy(state.progress.energyKwh),fontSize=37.sp,fontWeight=FontWeight.Bold);Text("кВт·ч",color=Muted); Text(if(state.session!=null) "Подача энергии · демо" else "Последний сеанс",color=Lime,fontSize=11.sp) }
                            }
                            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                Metric("Время", "%02d:%02d:%02d".format(state.progress.seconds/3600,state.progress.seconds/60%60,state.progress.seconds%60),Modifier.weight(1f))
                                Metric("Стоимость",money(state.progress.costMinor),Modifier.weight(1f))
                            }
                            Text("Доступно: ${money((state.balanceMinor-(if(state.session!=null)state.progress.costMinor else 0)).coerceAtLeast(0))}",color=Muted)
                            if(state.session!=null) Button(onClick={vm.stop();message("Демо-сеанс завершён. Запись добавлена в кошелёк.")},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Amber)) { Icon(Icons.Default.Close,null);Text(" Остановить симуляцию") }
                            else {
                                Button(onClick={if(vm.start(station,chosenPort?.id)) message("Запущена симуляция. Реальная станция не подключена.") else message("Станция недоступна или недостаточно учебного баланса")},enabled=chosenPort?.state==ConnectorState.FREE && state.balanceMinor>0,modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.PlayArrow,null);Text(" Начать симуляцию") }
                                if(chosenPort?.id in state.completedConnectorIds) TextButton(onClick={chosenPort?.let {vm.releaseConnector(it.id)}}) {Text("Освободить разъём после сеанса")}
                                OutlinedButton(onClick={tabName=Tab.STATIONS.name},modifier=Modifier.fillMaxWidth()) {Text("Выбрать другую станцию")}
                            }
                            Text("Симуляция рассчитывается по времени: 80 кВт, 13,5 сом/кВт·ч. Продолжается при сворачивании; автоматически завершается при исчерпании учебного баланса.",color=Muted,fontSize=12.sp)
                        }
                        Tab.WALLET -> {
                            Eyebrow("ТОЛЬКО УЧЕБНЫЕ СРЕДСТВА"); Title("Кошелёк")
                            Panel {
                                Text("Учебный баланс",color=Muted)
                                Text(money((state.balanceMinor-(if(state.session!=null)state.progress.costMinor else 0)).coerceAtLeast(0)),fontSize=32.sp,fontWeight=FontWeight.Bold,color=Lime)
                                Text("Деньги не списываются с банковских карт",color=Muted,fontSize=12.sp)
                                Button(onClick={vm.topUp();message("Добавлено 500 учебных сомов")},enabled=state.session==null) {Text("Добавить 500 учебных сомов")}
                                if(state.session!=null) Text("Пополнение доступно после завершения сеанса",color=Amber,fontSize=12.sp)
                            }
                            Title("История операций")
                            if(state.history.isEmpty()) Panel {Text("Здесь появится ваша история");Text("Завершите демо-зарядку или пополните учебный баланс.",color=Muted)}
                            state.history.forEach { item -> Panel {
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text(item.title,modifier=Modifier.weight(1f),fontWeight=FontWeight.Medium);Text(if(item.amountMinor>0) "+${money(item.amountMinor)}" else money(item.amountMinor),color=if(item.amountMinor>0)Lime else Color.White,fontSize=13.sp)}
                                Text(item.detail,color=Muted,fontSize=12.sp)
                                Text(SimpleDateFormat("dd.MM.yyyy · HH:mm",Ru).format(Date(item.timestamp)),color=Muted,fontSize=11.sp)
                            } }
                        }
                        Tab.PROFILE -> {
                            Eyebrow("ЛИЧНЫЙ КАБИНЕТ");Title("Профиль")
                            Panel { Icon(Icons.Default.Person,null,tint=Lime,modifier=Modifier.size(40.dp)); Text("Демо-пользователь",fontSize=24.sp,fontWeight=FontWeight.Bold); Text("Без регистрации · данные на этом устройстве",color=Muted) }
                            Panel {Text("Мой автомобиль",color=Muted);Text("Учебный электромобиль · GB/T DC",fontWeight=FontWeight.Medium);Text("Проверяйте разъём своего автомобиля перед реальной зарядкой.",color=Muted,fontSize=12.sp)}
                            Panel {Text("Язык интерфейса");Text("Русский",color=Lime);Text("Регион · Кыргызская Республика",color=Muted)}
                            Panel {Text("О приложении",fontWeight=FontWeight.Bold);Text("QUBAT EV  ${BuildConfig.VERSION_NAME}",color=Lime);Text("Нативное приложение на Kotlin и Jetpack Compose. Все станции, их статусы и операции — демонстрационные. API EEJANN и платежи не подключены.",color=Muted,fontSize=13.sp)}
                            Panel {Text("Конфиденциальность",fontWeight=FontWeight.Bold);Text("Геопозиция запрашивается только по кнопке и используется для расстояний. Фоновое отслеживание отключено. Учебный баланс и последние 100 операций сохраняются локально. При использовании карты её провайдер получает сетевые запросы; внешний навигатор получает выбранную точку.",color=Muted,fontSize=13.sp)}
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                }
            }
            focusedConnectorId?.let {id->
                val site=stations.firstOrNull {s->s.connectors.any {it.id==id}}
                val port=site?.connectors?.firstOrNull {it.id==id}
                if(site!=null && port!=null) AlertDialog(onDismissRequest={focusedConnectorId=null},
                    title={Text("Аппарат ${site.chargerNumber(id)} · разъём ${port.number}")},
                    text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text(port.state.title,color=Color(port.state.argb),fontWeight=FontWeight.Bold,modifier=Modifier.testTag("connector-dialog-state"))
                        Text("${site.city} · ${site.title}")
                        Text("GB/T DC · 80 кВт · 13,5 сом/кВт·ч",color=Muted,fontSize=13.sp)
                        port.percent?.let {Text("Демо-уровень заряда: $it%",color=Color(port.state.argb))}
                        Text(when(port.state) {
                            ConnectorState.FREE -> "Разъём свободен. Можно выбрать для учебной зарядки."
                            ConnectorState.CONNECTED -> "Автомобиль уже подключён и ожидает старта. Разъём занят."
                            ConnectorState.CHARGING -> "Идёт демонстрационная зарядка. Разъём занят."
                            ConnectorState.COMPLETED -> "Сеанс завершён. Разъём ещё не освобождён."
                            ConnectorState.ERROR -> "Демонстрационная ошибка разъёма. Запуск недоступен."
                            ConnectorState.OFFLINE -> "Разъём недоступен."
                        },color=Muted,fontSize=13.sp)
                    }},confirmButton={
                        if(port.state==ConnectorState.FREE) TextButton(onClick={selectedId=site.id;selectedConnectorId=id;focusedConnectorId=null;tabName=Tab.CHARGE.name}) {Text("Выбрать разъём")}
                        else if(id in state.completedConnectorIds) TextButton(onClick={vm.releaseConnector(id);focusedConnectorId=null}) {Text("Освободить разъём")}
                    },dismissButton={TextButton(onClick={focusedConnectorId=null}) {Text("Закрыть")}})
            }
            detailId?.let { id ->
                val s=stations.first {it.id==id}
                AlertDialog(onDismissRequest={detailId=null},title={Text("${s.city} · ДЕМО")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(s.title,fontWeight=FontWeight.Bold);Text("${s.status.title} · ${s.availableConnectors} из ${s.connectorCount} мест свободно\n80 кВт на место · GB/T DC · 13,5 сом/кВт·ч")
                    Text("Вымышленная станция у центра города. По этим координатам зарядной станции может не быть. Маршрут — только для проверки интерфейса.",color=Amber)
                    Text("${s.point.lat}, ${s.point.lon}",color=Muted)
                    OutlinedButton(onClick={openRoute(context,s,::message)},modifier=Modifier.fillMaxWidth()) {Text("Учебный маршрут · 2ГИС")}
                    TextButton(onClick={openGoogleRoute(context,s,::message)}) {Text("Открыть маршрут в Google Maps")}
                }},confirmButton={TextButton(onClick={selectedId=s.id;detailId=null;tabName=Tab.CHARGE.name}) {Text("К зарядке")}},dismissButton={TextButton(onClick={detailId=null}){Text("Закрыть")}})
            }
        }
    }
}
@Composable fun Eyebrow(text:String) {Text(text,color=Lime,fontSize=10.sp,letterSpacing=2.sp,fontWeight=FontWeight.Bold)}
@Composable fun Title(text:String) {Text(text,fontSize=25.sp,fontWeight=FontWeight.Bold)}
@Composable fun Panel(content:@Composable ColumnScope.()->Unit) { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(SurfaceGreen).padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content) }
@Composable fun DemoNotice() {Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF2B3624)).padding(12.dp),verticalAlignment=Alignment.CenterVertically) {Icon(Icons.Default.Info,null,tint=Amber,modifier=Modifier.size(18.dp));Text(" Демо-данные. Станции вымышленные. Платежей нет.",color=Amber,fontSize=11.sp)}}
@Composable fun Metric(label:String,value:String,modifier:Modifier) {Column(modifier.clip(RoundedCornerShape(18.dp)).background(SurfaceGreen).padding(16.dp)) {Text(label,color=Muted,fontSize=12.sp);Spacer(Modifier.height(8.dp));Text(value,fontSize=17.sp,fontWeight=FontWeight.Bold)}}
@Composable fun CityPicker(city:String?,onSelect:(String?)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    Box {OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()) {Icon(Icons.Default.Place,null);Text(" ${city ?: "Весь Кыргызстан"}",modifier=Modifier.weight(1f));Icon(Icons.Default.ArrowDropDown,null)}
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false},modifier=Modifier.heightIn(max=380.dp)) {
            DropdownMenuItem(text={Text("Весь Кыргызстан")},onClick={onSelect(null);expanded=false})
            DemoData.cities.forEach { c -> DropdownMenuItem(text={Text(c.name)},onClick={onSelect(c.name);expanded=false}) }
        }
    }
}
@Composable fun FlowRowCompatStatus(selected:String?,onSelect:(String?)->Unit) {
    Column {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        FilterChip(selected=selected==null,onClick={onSelect(null)},label={Text("Все")})
        FilterChip(selected=selected==StationStatus.AVAILABLE.name,onClick={onSelect(StationStatus.AVAILABLE.name)},label={Text("Свободны")})
    };Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {StationStatus.entries.filter {it!=StationStatus.AVAILABLE}.forEach {s -> FilterChip(selected=selected==s.name,onClick={onSelect(s.name)},label={Text(s.title)})}} }
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
