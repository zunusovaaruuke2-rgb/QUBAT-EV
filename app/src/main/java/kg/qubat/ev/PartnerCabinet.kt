@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package kg.qubat.ev

import androidx.compose.ui.semantics.testTagsAsResourceId

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

private enum class NativePage(val label:String) { MONITOR("Мониторинг"),POINTS("Точки"),REPORTS("Отчёты"),FINANCE("Финансы"),ACCESS("Доступы"),BRANDS("Бренды"),JOURNAL("Журнал и уведомления"),PROFILE("Профиль партнёра") }
private val nativeTime=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(PARTNER_ZONE)
private val nativeDate=DateTimeFormatter.ofPattern("dd.MM.yyyy")
internal fun nativeMoney(value:BigDecimal?)=value?.setScale(2,RoundingMode.HALF_UP)?.toPlainString()?.replace('.',',')?.plus(" сом") ?: "Нет данных"
private fun nativeEnergy(value:BigDecimal)=value.setScale(3,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.',',')+" кВт·ч"
private fun knownSum(values:List<BigDecimal?>):BigDecimal?=if(values.isEmpty()||values.any{it==null})null else values.filterNotNull().fold(BigDecimal.ZERO,BigDecimal::add)

@Composable fun PartnerCabinetScreen(account:PartnerAccount,onBack:()->Unit,onSignOut:()->Unit,onSwitchDemo:()->Unit) {
    val context=LocalContext.current
    val disk=remember {NativePartnerDisk(context.applicationContext)}
    var state by remember(account.id) {mutableStateOf<NativePartnerState?>(null)}
    LaunchedEffect(account.id) {state=withContext(Dispatchers.IO){disk.load()}}
    if(state==null) Surface(Modifier.fillMaxSize().testTag("partner-screen").semantics{stateDescription="Загрузка отчёта"},color=Forest) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            PressTextButton(onClick=onBack){AppText("Назад в приложение")}
            AppText("Кабинет партнёра",fontSize=26.sp,fontWeight=FontWeight.Bold)
            CircularProgressIndicator();AppText("Загрузка демонстрационного кабинета…")
        }
    } else {
        val current=state!!
        val effective=current.members.firstOrNull{it.id==account.id}?.let{account.copy(name=it.name,role=it.role)}?:account
        NativePartnerContent(effective,current,disk,onBack,onSignOut,onSwitchDemo)
    }
}

@Composable private fun NativePartnerContent(account:PartnerAccount,state:NativePartnerState,disk:NativePartnerDisk,onBack:()->Unit,onSignOut:()->Unit,onSwitchDemo:()->Unit) {
    val repo=remember(account.id,state){NativePartnerRepository(account,state)}
    var revision by remember{mutableIntStateOf(0)}
    var persistenceError by remember{mutableStateOf<String?>(null)}
    val changed:()->Unit={revision++;disk.saveLater(state){persistenceError=it}}
    var pageName by rememberSaveable(account.id){mutableStateOf(NativePage.MONITOR.name)}
    val page=NativePage.valueOf(pageName)
    var initialPoint by remember{mutableStateOf<String?>(null)}
    val drawer=rememberDrawerState(DrawerValue.Closed)
    val scope=rememberCoroutineScope()
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(page,account.id,lifecycle) {
        if(page in setOf(NativePage.MONITOR,NativePage.POINTS,NativePage.JOURNAL)) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while(isActive) {delay(30_000);revision++}
            }
        }
    }
    if(!repo.can(NativePermission.VIEW)) {
        Surface(Modifier.fillMaxSize().testTag("partner-screen").semantics{stateDescription="Доступ отозван"},color=Forest) {
            Column(Modifier.safeDrawingPadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                AppText("Доступ отозван или срок закончился",fontSize=24.sp,fontWeight=FontWeight.Bold)
                AppText("Демонстрационная роль больше не имеет действующих назначений.",color=Muted)
                DepthButton(onClick=onSwitchDemo){AppText("Сменить демо-аккаунт")}
                PressTextButton(onClick=onSignOut){AppText("Выйти в профиль водителя")}
            }
        }
        return
    }
    val pages=NativePage.entries.filter{when(it){NativePage.REPORTS->repo.can(NativePermission.REPORT);NativePage.FINANCE->repo.can(NativePermission.FINANCE);else->true}}
    ModalNavigationDrawer(drawerState=drawer,drawerContent={ModalDrawerSheet {
        Column(Modifier.widthIn(max=330.dp).verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            AppText("QUBAT EV",fontSize=24.sp,fontWeight=FontWeight.Black)
            AppText("Кабинет партнёра · ДЕМО",color=Lime)
            AppText(account.name,fontWeight=FontWeight.SemiBold)
            AppText(account.role.label,color=Muted,fontSize=12.sp)
            HorizontalDivider()
            pages.forEach{item->DepthSurface(onClick={pageName=item.name;scope.launch{drawer.close()}},modifier=Modifier.fillMaxWidth().testTag("network-menu-${item.name.lowercase()}"),color=if(item==page)MaterialTheme.colorScheme.primaryContainer else SurfaceGreen){
                AppText(item.label,Modifier.padding(15.dp),fontWeight=if(item==page)FontWeight.Bold else FontWeight.Normal)
            }}
            PressTextButton(onClick={scope.launch{drawer.close()};onSwitchDemo()},modifier=Modifier.testTag("network-drawer-switch-demo")){AppText("Сменить демо-аккаунт")}
            PressTextButton(onClick=onSignOut,modifier=Modifier.testTag("network-drawer-sign-out")){AppText("Выйти из кабинета")}
        }
    }}) {
        Surface(Modifier.fillMaxSize().testTag("partner-screen").semantics{stateDescription="Отчёт загружен";testTagsAsResourceId=true},color=Forest) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                    PressIconButton(onClick={scope.launch{drawer.open()}},modifier=Modifier.testTag("network-menu")){AppIcon(Icons.Default.Menu,"Меню кабинета")}
                    Column(Modifier.weight(1f)) {AppText(page.label,fontSize=21.sp,fontWeight=FontWeight.Bold);AppText("Кабинет партнёра · ДЕМО",fontSize=10.sp,color=Lime)}
                    ThemeToggle()
                    PressIconButton(onClick=onBack,modifier=Modifier.testTag("partner-back")){AppIcon(Icons.Default.Close,"Назад в приложение")}
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.3f))
                persistenceError?.let{AppText(it,Modifier.padding(10.dp),color=MaterialTheme.colorScheme.error)}
                Box(Modifier.weight(1f)) {when(page) {
                    NativePage.MONITOR->NativeMonitor(repo,revision){id->initialPoint=id;pageName=NativePage.POINTS.name}
                    NativePage.POINTS->NativePartnerPoints(repo,revision,changed,initialPoint)
                    NativePage.REPORTS->NativeReports(repo,revision,changed)
                    NativePage.FINANCE->NativeFinance(repo,revision,changed)
                    NativePage.ACCESS->NativePartnerAccess(repo,revision,changed)
                    NativePage.BRANDS->NativePartnerBrands(repo,revision,changed)
                    NativePage.JOURNAL->NativePartnerJournal(repo,revision,changed)
                    NativePage.PROFILE->NativeProfile(account,onSwitchDemo,onSignOut)
                }}
            }
        }
    }
}

@Composable private fun NativePanel(title:String,tag:String="",content:@Composable ColumnScope.()->Unit) {
    DepthSurface(Modifier.fillMaxWidth().testTag(tag),shape=RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {AppText(title,fontSize=17.sp,fontWeight=FontWeight.SemiBold);content()}
    }
}
@Composable private fun NativeMetric(title:String,value:String,tag:String="") {NativePanel(title,tag){AppText(value,fontSize=25.sp,fontWeight=FontWeight.Bold,color=Lime)}}
@Composable private fun NativeNotice(){AppText("ДЕМО. Все станции, показания и суммы вымышленные. Реальные платежи и управление оборудованием отключены.",fontSize=11.sp,color=Muted)}

@Composable private fun NativeMonitor(repo:NativePartnerRepository,revision:Int,onPoint:(String)->Unit) {
    val report=remember(repo,revision){repo.monitoring()}
    val summary=report.summary
    val stations=remember(repo,revision){repo.stations()}
    val active=remember(repo,revision){repo.activeSessions()}
    var zoom by remember{mutableIntStateOf(0)}
    var focus by remember{mutableIntStateOf(0)}
    val mapStations=remember(stations,revision){stations.map{s->
        val stale=repo.isStale(s)
        val apparatus=List(s.apparatusCount){index->
            val ports=s.ports.filterIndexed{portIndex,_->portIndex%s.apparatusCount==index}.map{p->
                Connector(p.id,p.number,if(stale||repo.isPortStale(s.id,p.id))ConnectorState.OFFLINE else when(p.status){NativePortStatus.FREE->ConnectorState.FREE;NativePortStatus.BUSY->ConnectorState.CHARGING;NativePortStatus.FAULT->ConnectorState.ERROR;NativePortStatus.UNAVAILABLE->ConnectorState.OFFLINE})
            }
            Charger("${s.id}-apparatus-$index",index+1,ports)
        }
        Station(s.id,s.city,s.title,GeoPoint(s.latitude.toDouble(),s.longitude.toDouble()),apparatus,powerKw=s.powerKw.toInt())
    }}
    LazyColumn(Modifier.fillMaxSize().testTag("network-monitor-scroll"),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item(key="notice"){NativeNotice()}
        item(key="stations"){NativeMetric("Станции / аппараты / разъёмы","${summary.stationCount} / ${summary.apparatusCount} / ${summary.connectorCount}","network-station-count")}
        item(key="power"){NativePanel("Состояние сети"){AppText("Активных портов: ${summary.activePorts} · без связи: ${summary.offlineStations}");AppText("Текущая мощность: ${summary.currentPowerKw.stripTrailingZeros().toPlainString()} кВт",color=Lime);AppText("Состояния относятся к демонстрационному снимку. Старый сигнал не отображается зелёным.",fontSize=10.sp,color=Muted)}}
        if(repo.can(NativePermission.FINANCE))item(key="revenue"){NativeMetric("Оплаченная выручка за сегодня",nativeMoney(summary.revenueKgs),"network-daily-revenue")}
        item(key="map"){NativePanel("Назначенные станции на карте"){
            if(mapStations.isNotEmpty())WorldMap(mapStations,null,null,"",Modifier.fillMaxWidth().height(230.dp).testTag("network-map"),focus,zoom,0,0,onSelect={onPoint(it.id)})
            MapCredits()
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){DepthOutlinedButton(onClick={zoom++}){AppText("+")};DepthOutlinedButton(onClick={zoom--}){AppText("−")};PressTextButton(onClick={focus++}){AppText("Обзор Кыргызстана")}}
            AppText("Демо-координаты; не используйте для поездки к реальной зарядке.",fontSize=10.sp,color=Muted)
        }}
        item(key="active-title"){AppText("Активные сессии · ДЕМО",fontSize=20.sp,fontWeight=FontWeight.Bold)}
        if(active.isEmpty())item(key="no-active"){AppText("Активных демонстрационных сессий нет",color=Muted)}
        items(active,key={it.id},contentType={"active"}){session->NativePanel(stations.firstOrNull{it.id==session.stationId}?.title?:session.stationId){
            AppText("${session.portId} · ${nativeTime.format(session.startedAt)}",fontSize=11.sp,color=Muted)
            AppText(nativeEnergy(session.energyKwh),color=Lime,fontWeight=FontWeight.Bold)
            val point=stations.firstOrNull{it.id==session.stationId}
            val fresh=point!=null && !repo.isStale(point) && !repo.isPortStale(point.id,session.portId)
            AppText("Мощность: ${if(fresh)point!!.instantaneousPowerKw.stripTrailingZeros().toPlainString()+" кВт" else "Нет свежих данных"}",fontSize=12.sp)
            AppText("По снимку: ${Duration.between(session.startedAt,session.updatedAt).seconds.coerceAtLeast(0)/60} мин · клиент ${session.customerId}",fontSize=11.sp,color=Muted)
            if(repo.can(NativePermission.FINANCE))AppText("Расчётная стоимость: ${nativeMoney(session.chargedKgs)}",fontSize=12.sp)
            AppText("Источник: ${session.source} · обновлено ${nativeTime.format(session.updatedAt)}",fontSize=10.sp,color=Muted)
        }}
        items(stations.filter{repo.isStale(it)||it.errorReason!=null},key={"alert-${it.id}"},contentType={"alert"}){s->NativePanel("Тревога · ${s.title}"){
            AppText(if(repo.isStale(s))"Данные не обновлялись" else s.errorReason.orEmpty(),color=MaterialTheme.colorScheme.error)
            AppText("Последний сигнал: ${nativeTime.format(s.lastSeen)} · ${s.source}",fontSize=11.sp,color=Muted)
            PressTextButton(onClick={onPoint(s.id)}){AppText("Открыть точку")}
        }}
    }
}

private data class NativeFilterUi(val filter:NativeReportFilter,val content:@Composable ()->Unit)
@Composable private fun nativeFilters(repo:NativePartnerRepository):NativeFilterUi {
    var from by rememberSaveable(repo.account.id){mutableStateOf(repo.today.minusDays(6).toString())}
    var to by rememberSaveable(repo.account.id){mutableStateOf(repo.today.toString())}
    var stationId by rememberSaveable{mutableStateOf("")}
    var organizationId by rememberSaveable{mutableStateOf("")}
    var ownerId by rememberSaveable{mutableStateOf("")}
    var groupId by rememberSaveable{mutableStateOf("")}
    var locationId by rememberSaveable{mutableStateOf("")}
    var portId by rememberSaveable{mutableStateOf("")}
    var customerId by rememberSaveable{mutableStateOf("")}
    var query by rememberSaveable{mutableStateOf("")}
    var payment by rememberSaveable{mutableStateOf("")}
    var status by rememberSaveable{mutableStateOf("")}
    var operation by rememberSaveable{mutableStateOf("")}
    var sort by rememberSaveable{mutableStateOf(PartnerSort.NEWEST.name)}
    var edit by remember{mutableStateOf(false)}
    var picker by remember{mutableStateOf<String?>(null)}
    val stations=repo.stations(includeArchived=true)
    val filter=NativeReportFilter(PartnerPeriod(LocalDate.parse(from),LocalDate.parse(to)),organizationId.takeIf{it.isNotBlank()},ownerId.takeIf{it.isNotBlank()},groupId.takeIf{it.isNotBlank()},locationId.takeIf{it.isNotBlank()},stationId.takeIf{it.isNotBlank()},portId.takeIf{it.isNotBlank()},customerId.takeIf{it.isNotBlank()},operation.takeIf{it.isNotBlank()}?.let(NativeOperation::valueOf),payment.takeIf{it.isNotBlank()}?.let(NativePaymentStatus::valueOf),status.takeIf{it.isNotBlank()}?.let(NativeSessionStatus::valueOf),query,PartnerSort.valueOf(sort))
    if(edit)NativeDateRange(from,to,{edit=false}){a,b->from=a;to=b;edit=false}
    picker?.let{kind->
        val choices=when(kind){
            "station"->stations.map{it.id to it.title}
            "organization"->repo.organizations().map{it.id to it.name}
            "owner"->repo.organizations().map{it.ownerId to (NativePartnerRepository.accounts.firstOrNull{a->a.id==it.ownerId}?.name?:it.ownerId)}
            "group"->stations.map{it.groupId to it.groupId}.distinct()
            "location"->stations.map{it.locationId to "${it.city} · ${it.locationId}"}.distinct()
            "port"->stations.flatMap{s->s.ports.map{it.id to "${s.title} · №${it.number}"}}
            "payment"->NativePaymentStatus.entries.map{it.name to it.label}
            "status"->NativeSessionStatus.entries.map{it.name to it.label}
            "operation"->NativeOperation.entries.map{it.name to it.label}
            else->emptyList()
        }
        AlertDialog(onDismissRequest={picker=null},title={AppText("Фильтр")},text={Column(Modifier.verticalScroll(rememberScrollState())){(listOf("" to "Все")+choices).forEach{(id,label)->PressTextButton(onClick={when(kind){"station"->stationId=id;"organization"->organizationId=id;"owner"->ownerId=id;"group"->groupId=id;"location"->locationId=id;"port"->portId=id;"payment"->payment=id;"status"->status=id;"operation"->operation=id};picker=null},modifier=Modifier.testTag("network-select-$kind-$id")){AppText(label)}}}},confirmButton={PressTextButton(onClick={picker=null}){AppText("Закрыть")}})
    }
    val content:@Composable ()->Unit={
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){
                listOf("Сегодня" to "today","7 дней" to "week","Месяц" to "month","Год" to "year","Прошлый месяц" to "previous").forEach{(label,id)->DepthOutlinedButton(onClick={val end=if(id=="previous")repo.today.withDayOfMonth(1).minusDays(1) else repo.today;to=end.toString();from=when(id){"today"->end;"week"->end.minusDays(6);"year"->end.withDayOfYear(1);else->end.withDayOfMonth(1)}.toString()},modifier=Modifier.testTag("network-period-$id")){AppText(label)}}
            }
            AppText("${nativeDate.format(filter.period.from)} — ${nativeDate.format(filter.period.to)} · Asia/Bishkek",fontSize=12.sp,color=Muted,modifier=Modifier.testTag("network-period-label"))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                DepthOutlinedButton(onClick={picker="station"},modifier=Modifier.weight(1f).testTag("network-station-filter")){AppText(stations.firstOrNull{it.id==stationId}?.title?:"Все мои станции",maxLines=1)}
                PressTextButton(onClick={edit=true},modifier=Modifier.testTag("network-period-custom")){AppText("Период")}
            }
            PressOutlinedTextField(query,{query=it.take(100)},modifier=Modifier.fillMaxWidth().testTag("network-search"),label={AppText("Поиск по станции / ID")},singleLine=true)
            var advanced by rememberSaveable{mutableStateOf(false)}
            PressTextButton(onClick={advanced=!advanced}){AppText(if(advanced)"Скрыть фильтры" else "Дополнительные фильтры")}
            if(advanced){
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("organization" to "Организация","owner" to "Владелец","group" to "Группа","location" to "Локация","port" to "Разъём","payment" to "Оплата","status" to "Сессия","operation" to "Операция").forEach{(id,label)->DepthOutlinedButton(onClick={picker=id}){AppText(label)}}}
                PressOutlinedTextField(customerId,{customerId=it.take(80)},label={AppText("ID демо-клиента")},singleLine=true,modifier=Modifier.fillMaxWidth())
                PartnerSort.entries.forEach{s->DepthChip(sort==s.name,{sort=s.name},label={AppText(s.label)})}
                PressTextButton(onClick={stationId="";organizationId="";ownerId="";groupId="";locationId="";portId="";customerId="";payment="";status="";operation="";query=""}){AppText("Сбросить фильтры")}
            }
        }
    }
    return NativeFilterUi(filter,content)
}

@Composable private fun NativeDateRange(from:String,to:String,onDismiss:()->Unit,onApply:(String,String)->Unit){
    var first by remember{mutableStateOf(from)};var last by remember{mutableStateOf(to)}
    val valid=runCatching{PartnerPeriod(LocalDate.parse(first),LocalDate.parse(last))}.isSuccess
    AlertDialog(onDismissRequest=onDismiss,title={AppText("Период отчёта")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        AppText("Последний день включается целиком по Asia/Bishkek.",fontSize=12.sp,color=Muted)
        PressOutlinedTextField(first,{first=it.take(10)},label={AppText("С даты · ГГГГ-ММ-ДД")},singleLine=true,modifier=Modifier.testTag("network-date-from"))
        PressOutlinedTextField(last,{last=it.take(10)},label={AppText("По дату · ГГГГ-ММ-ДД")},singleLine=true,modifier=Modifier.testTag("network-date-to"))
        if(!valid)AppText("Проверьте начало и окончание периода",color=MaterialTheme.colorScheme.error)
    }},confirmButton={PressTextButton(onClick={onApply(first,last)},enabled=valid,modifier=Modifier.testTag("network-date-apply")){AppText("Применить")}},dismissButton={PressTextButton(onClick=onDismiss){AppText("Отмена")}})
}

@Composable private fun NativeExport(repo:NativePartnerRepository,report:NativeNetworkReport,onChanged:()->Unit){
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var pending by remember(repo.account.id){mutableStateOf<NativeNetworkReport?>(null)}
    var busy by remember{mutableStateOf(false)};var message by remember{mutableStateOf<String?>(null)}
    DisposableEffect(repo.account.id){onDispose{pending=null}}
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")){uri->
        val frozen=pending;pending=null
        if(uri==null||frozen==null||frozen.account.id!=repo.account.id){busy=false;return@rememberLauncherForActivityResult}
        scope.launch{try{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri,"w")?.use{NativePartnerExcel.write(frozen,it)}?:error("Нет доступа к файлу")};message="Excel сохранён: 5 листов · ${frozen.filter.period.from} — ${frozen.filter.period.to}"}catch(cancel:CancellationException){throw cancel}catch(_:Exception){message="Не удалось сохранить Excel. Повторите попытку."}finally{busy=false}}
    }
    if(repo.can(NativePermission.EXPORT))DepthButton(onClick={pending=report;busy=true;message=null;launcher.launch(NativePartnerExcel.fileName(report));repo.recordExport(report);onChanged()},enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("network-export")){AppText(if(busy)"Подготовка Excel…" else "Скачать Excel (.xlsx)")}
    message?.let{AppText(it,fontSize=11.sp,color=Muted)}
}

@Composable private fun NativeReports(repo:NativePartnerRepository,revision:Int,onChanged:()->Unit){
    if(!repo.can(NativePermission.REPORT)){NativeDenied();return}
    val selected=nativeFilters(repo);val filter=selected.filter
    val report=remember(repo,filter,revision){repo.report(filter)}
    var page by rememberSaveable{mutableIntStateOf(0)}
    LaunchedEffect(filter){page=0}
    val pages=((report.sessions.size+19)/20).coerceAtLeast(1)
    if(page>=pages)page=pages-1
    LazyColumn(Modifier.fillMaxSize().testTag("network-reports-scroll"),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item(key="filters"){selected.content()}
        item(key="export"){NativeExport(repo,report,onChanged)}
        item(key="energy"){NativeMetric("Завершённые сессии / энергия","${report.summary.completedCount} · ${nativeEnergy(report.summary.energyKwh)}","network-report-summary")}
        item(key="counts"){AppText("Отменено: ${report.summary.cancelledCount} · ошибок: ${report.summary.errorCount} · клиентов: ${report.summary.uniqueCustomers}",fontSize=12.sp,color=Muted)}
        if(repo.can(NativePermission.FINANCE))item(key="revenue"){NativePanel("Начислено / оплачено / возвраты"){AppText(nativeMoney(report.summary.chargedKgs));AppText(nativeMoney(report.summary.paidKgs));AppText("Возвраты: ${nativeMoney(report.summary.refundKgs)}");AppText("Не оплачено: ${nativeMoney(report.summary.unpaidKgs)}");AppText("Средний тариф: ${nativeMoney(report.summary.averageTariffKgs)} / кВт·ч",fontSize=12.sp)}}
        if(report.sessions.isEmpty())item(key="empty"){NativePanel("За выбранный период зарядок не найдено"){AppText("Измените период или фильтр",color=Muted)}}
        items(report.sessions.drop(page*20).take(20),key={it.id},contentType={"session"}){s->NativePanel(report.stations.firstOrNull{it.station.id==s.stationId}?.station?.title?:s.stationId,"network-session-${s.id}"){
            AppText(s.id,fontSize=10.sp,color=Muted);AppText(s.status.label,color=if(s.status==NativeSessionStatus.COMPLETED)Lime else Muted)
            AppText("Порт: ${s.portId} · клиент: ${s.customerId}",fontSize=11.sp)
            AppText("Начало: ${nativeTime.format(s.startedAt)}\nКонец: ${s.endedAt?.let(nativeTime::format)?:"Не завершена"}",fontSize=11.sp,color=Muted)
            AppText("${s.durationSeconds/60} мин · ${nativeEnergy(s.energyKwh)}")
            if(repo.can(NativePermission.FINANCE)){AppText("Тариф: ${nativeMoney(s.rates?.customerRate)} / кВт·ч");AppText("Начислено: ${nativeMoney(s.chargedKgs)} · оплачено: ${nativeMoney(s.paidKgs)}");AppText("Возврат: ${nativeMoney(s.refundKgs)} · ${s.paymentStatus.label}",fontSize=12.sp);AppText("Платформа: ${nativeMoney(s.platformFeeKgs)} · эквайринг: ${nativeMoney(s.acquiringFeeKgs)}",fontSize=11.sp,color=Muted)}
            s.errorReason?.let{AppText(it,color=MaterialTheme.colorScheme.error)}
            AppText("${s.source} · обновлено ${nativeTime.format(s.updatedAt)}",fontSize=10.sp,color=Muted)
        }}
        item(key="pages"){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){PressTextButton(onClick={page--},enabled=page>0,modifier=Modifier.testTag("network-page-prev")){AppText("Назад")};AppText("${page+1} / $pages",Modifier.testTag("network-page-label"));PressTextButton(onClick={page++},enabled=page+1<pages,modifier=Modifier.testTag("network-page-next")){AppText("Далее")}}}
    }
}

@Composable private fun NativeFinance(repo:NativePartnerRepository,revision:Int,onChanged:()->Unit){
    if(!repo.can(NativePermission.FINANCE)){NativeDenied();return}
    val selected=nativeFilters(repo)
    var deductions by remember{mutableStateOf(NativePartnerRepository.defaultDeductions)}
    var formula by remember{mutableStateOf(false)}
    var actual by rememberSaveable{mutableStateOf(false)}
    var billMonth by remember{mutableStateOf<YearMonth?>(null)}
    var expense by remember{mutableStateOf(false)}
    var terms by remember{mutableStateOf(false)}
    val report=remember(repo,selected.filter,revision,deductions){repo.report(selected.filter,deductions)}
    val rows=report.monthlyFinance
    val context=LocalContext.current
    var attachmentError by remember{mutableStateOf<String?>(null)}
    if(formula)AlertDialog(onDismissRequest={formula=false},title={AppText("Статьи расчёта прибыли")},text={Column(Modifier.verticalScroll(rememberScrollState())){
        AppText("Выручка = оплачено − возвраты. Вычитаются только выбранные статьи. Доля партнёра и её перевод не вычитаются одновременно.",fontSize=12.sp,color=Muted)
        NativeDeduction.entries.forEach{d->Row(verticalAlignment=Alignment.CenterVertically){Checkbox(d in deductions,onCheckedChange={checked->deductions=if(checked){var next=deductions+d;if(d==NativeDeduction.PAYOUT)next=next-NativeDeduction.PARTNER_SHARE;if(d==NativeDeduction.PARTNER_SHARE)next=next-NativeDeduction.PAYOUT;next}else deductions-d});AppText(d.label)}}
    }},confirmButton={PressTextButton(onClick={formula=false}){AppText("Применить")}})
    billMonth?.let{month->NativeBillEditor(repo,month,{billMonth=null}){onChanged();billMonth=null}}
    if(expense)NativeExpenseEditor(repo,selected.filter.period.to,{expense=false}){onChanged();expense=false}
    if(terms)NativeFinancialTerms(repo,{terms=false}){onChanged();terms=false}
    LazyColumn(Modifier.fillMaxSize().testTag("network-finance-scroll"),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item(key="filters"){selected.content()}
        item(key="mode"){Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){DepthChip(!actual,{actual=false},label={AppText("Расчётный")});DepthChip(actual,{actual=true},Modifier.testTag("network-finance-actual"),label={AppText("Фактический")})}}
        item(key="rule"){AppText(if(actual)"Факт берётся из квитанции за полный месяц. При отсутствии счёта показывается «Нет данных»." else "Расчётное электричество и фактическая квитанция сохраняются отдельно. Все значения демонстрационные.",fontSize=11.sp,color=Muted)}
        item(key="profit"){NativeMetric(if(actual)"Фактическая прибыль" else "Расчётная прибыль",nativeMoney(knownSum(rows.map{if(actual)it.actualProfitKgs else it.estimatedProfitKgs})),"network-finance-profit")}
        item(key="reconcile"){NativePanel("Акт сверки"){
            AppText("Начислено: ${nativeMoney(report.summary.chargedKgs)}")
            AppText("Оплачено по сессиям: ${nativeMoney(report.summary.paidKgs)}")
            AppText("Поступления реестра: ${nativeMoney(report.summary.paymentLedgerKgs)}")
            AppText("Возвраты: ${nativeMoney(report.summary.refundKgs)}")
            AppText("Разница: ${nativeMoney(report.summary.reconciliationDifferenceKgs)}",fontWeight=FontWeight.Bold,color=Lime)
            AppText("Не оплачено: ${nativeMoney(report.summary.unpaidKgs)}",fontSize=12.sp,color=Muted)
            PressTextButton(onClick={formula=true},modifier=Modifier.testTag("network-finance-formula")){AppText("Статьи расчёта")}
        }}
        if(repo.can(NativePermission.EDIT_FINANCE))item(key="expense"){DepthOutlinedButton(onClick={expense=true},modifier=Modifier.fillMaxWidth().testTag("network-expense-create")){AppText("Внести расход / выплату")}}
        if(repo.can(NativePermission.EDIT_TARIFF))item(key="terms"){DepthOutlinedButton(onClick={terms=true},modifier=Modifier.fillMaxWidth().testTag("network-financial-terms")){AppText("Договорные ставки и история")}}
        item(key="export"){NativeExport(repo,report,onChanged)}
        attachmentError?.let{message->item(key="attachment-error"){AppText(message,color=MaterialTheme.colorScheme.error)}}
        items(report.monthBills,key={"invoice-${it.id}"},contentType={"invoice"}){invoice->NativePanel("Квитанция №${invoice.billNumber}","network-invoice-${invoice.id}"){
            AppText(report.stations.firstOrNull{it.station.id==invoice.stationId}?.station?.title?:invoice.stationId)
            AppText("${invoice.month} · ${nativeMoney(invoice.electricityKgs)}",color=Lime,fontWeight=FontWeight.Bold)
            AppText("Дата документа: ${invoice.billDate} · ${if(invoice.paid)"Оплачено · ДЕМО" else "Не оплачено"}",fontSize=12.sp)
            AppText("Обновлено: ${nativeTime.format(invoice.updatedAt)} · ${invoice.authorId}",fontSize=10.sp,color=Muted)
            AppText(invoice.comment,fontSize=12.sp,color=Muted)
            invoice.attachment?.let{file->
                AppText("Вложение: ${file.fileName} · ${file.byteSize} байт",fontSize=11.sp)
                if(file.localReference.isNotBlank())PressTextButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(android.net.Uri.parse(file.localReference),file.mediaType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}.onFailure{attachmentError="Не удалось открыть вложение. Выберите файл заново."}}){AppText("Открыть квитанцию")}
            }
        }}
        if(rows.isEmpty())item(key="empty"){NativePanel("За период финансовых записей не найдено"){AppText("Измените даты или выбранную станцию",color=Muted)}}
        items(rows,key={it.month.toString()},contentType={"month"}){m->NativePanel("${m.month} · ${m.stationIds.size} точек","network-month-${m.month}"){
            AppText(nativeEnergy(m.energyKwh),color=Lime)
            AppText("Оплаченная выручка: ${nativeMoney(m.revenueKgs)}")
            AppText("Электричество: расчёт ${nativeMoney(m.electricityEstimateKgs)}\nФакт ${nativeMoney(m.electricityFactKgs)}",Modifier.testTag("network-month-electricity"),fontSize=12.sp)
            AppText("Показания счётчика: ${m.meterEnergyKwh?.let(::nativeEnergy)?:"Нет данных"}\nРазница счётчика и сессий: ${m.meterDifferenceKwh?.let(::nativeEnergy)?:"Нет данных"}",fontSize=11.sp,color=Muted)
            AppText("Платформа: ${nativeMoney(m.platformKgs)} · эквайринг: ${nativeMoney(m.acquiringKgs)}\nНалог: ${nativeMoney(m.taxKgs)}",fontSize=12.sp)
            AppText("Аренда: расчёт ${nativeMoney(m.rentEstimateKgs)} · факт ${nativeMoney(m.rentFactKgs)}\nПрочее: ${nativeMoney(m.otherKgs)}",fontSize=12.sp)
            AppText("Доля партнёра: ${nativeMoney(m.partnerShareKgs)}\nРезерв: ${nativeMoney(m.reserveKgs)} · перевод: ${nativeMoney(m.payoutKgs)}",fontSize=12.sp)
            AppText("Прибыль: ${nativeMoney(if(actual)m.actualProfitKgs else m.estimatedProfitKgs)}",fontWeight=FontWeight.Bold,color=Lime)
            val margin=if(actual)m.actualMarginPercent else m.estimatedMarginPercent
            AppText("Маржа: ${margin?.setScale(2,RoundingMode.HALF_UP)?.toPlainString()?.plus(" %")?:"Нет данных"}",fontSize=12.sp)
            AppText("Задолженность с переносом: ${nativeMoney(m.outstandingKgs)}",fontSize=11.sp,color=Muted)
            AppText("Квитанций: ${m.billCount}, оплачено: ${m.billPaidCount}",fontSize=11.sp,color=Muted)
            m.factUnavailableReason?.let{AppText(it,fontSize=11.sp,color=Muted)}
            if(repo.can(NativePermission.EDIT_FINANCE))PressTextButton(onClick={billMonth=m.month},modifier=Modifier.testTag("network-bill-edit-${m.month}")){AppText("Квитанция и аренда за месяц")}
        }}
    }
}

@Composable private fun NativeField(label:String,value:String,tag:String="",onChange:(String)->Unit){
    PressOutlinedTextField(value,onChange,label={AppText(label)},singleLine=true,modifier=Modifier.fillMaxWidth().testTag(tag).depthFrame(RoundedCornerShape(16.dp),SurfaceGreen,1.dp,fill=false))
}
private fun nativeDecimal(value:String)=value.trim().replace(',','.').toBigDecimalOrNull()

@Composable private fun NativeBillEditor(repo:NativePartnerRepository,month:YearMonth,onDismiss:()->Unit,onSaved:()->Unit){
    val context=LocalContext.current
    val stations=repo.stations(includeArchived=true)
    var stationId by remember{mutableStateOf(stations.first().id)}
    val existing=repo.monthBills(stationId).firstOrNull{it.month==month}
    var amount by remember(stationId){mutableStateOf(existing?.electricityKgs?.toPlainString().orEmpty())}
    var number by remember(stationId){mutableStateOf(existing?.billNumber.orEmpty())}
    var date by remember(stationId){mutableStateOf(existing?.billDate?.toString()?:month.atEndOfMonth().toString())}
    var meter by remember(stationId){mutableStateOf(existing?.meterEnergyKwh?.toPlainString().orEmpty())}
    var rent by remember(stationId){mutableStateOf(existing?.actualRentKgs?.toPlainString().orEmpty())}
    var paid by remember(stationId){mutableStateOf(existing?.paid?:false)}
    var comment by remember(stationId){mutableStateOf(existing?.comment.orEmpty())}
    var reason by remember{mutableStateOf("")}
    var attachment by remember(stationId){mutableStateOf(existing?.attachment)}
    var error by remember{mutableStateOf<String?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null)runCatching{
            var fileName="Квитанция";var size=0L
            context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE),null,null,null)?.use{cursor->if(cursor.moveToFirst()){fileName=cursor.getString(0).orEmpty();size=cursor.getLong(1)}}
            require(size in 0..10*1024*1024){"Вложение — до 10 МБ"}
            runCatching{context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
            attachment=NativeAttachment(fileName,context.contentResolver.getType(uri)?:"application/octet-stream",size,uri.toString())
        }.onFailure{error=it.message}
    }
    val valid=nativeDecimal(amount)?.signum()?.let{it>=0}==true&&number.isNotBlank()&&reason.isNotBlank()&&runCatching{LocalDate.parse(date)}.isSuccess&&(meter.isBlank()||nativeDecimal(meter)?.signum()?.let{it>=0}==true)&&(rent.isBlank()||nativeDecimal(rent)?.signum()?.let{it>=0}==true)
    AlertDialog(onDismissRequest=onDismiss,title={AppText("Квитанция · $month · ДЕМО")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(11.dp)){
        AppText("Расчётная сумма не удаляется. Данные сохраняются только на этом устройстве.",fontSize=11.sp,color=Muted)
        stations.forEach{s->DepthChip(stationId==s.id,{stationId=s.id},modifier=Modifier.fillMaxWidth(),label={AppText(s.title)})}
        NativeField("Электричество по квитанции, сом",amount,"network-bill-amount"){amount=it.take(18)}
        NativeField("Номер документа",number,"network-bill-number"){number=it.take(80)}
        NativeField("Дата документа · ГГГГ-ММ-ДД",date){date=it.take(10)}
        NativeField("Внешний счётчик, кВт·ч · необязательно",meter){meter=it.take(18)}
        NativeField("Фактическая аренда, сом · необязательно",rent){rent=it.take(18)}
        DepthChip(paid,{paid=!paid},label={AppText("Квитанция оплачена · ДЕМО")})
        DepthOutlinedButton(onClick={picker.launch(arrayOf("application/pdf","image/*"))}){AppText("Прикрепить квитанцию")}
        attachment?.let{AppText("${it.fileName} · ${it.byteSize} байт",fontSize=11.sp,color=Muted)}
        NativeField("Комментарий",comment){comment=it.take(300)}
        NativeField("Причина изменения",reason,"network-bill-reason"){reason=it.take(300)}
        error?.let{AppText(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={PressTextButton(onClick={runCatching{repo.saveMonthBill(NativeMonthBill(existing?.id?:"bill-${stationId}-${month}",stationId,month,nativeDecimal(amount)!!,number,LocalDate.parse(date),paid,nativeDecimal(meter),nativeDecimal(rent),comment,attachment),reason);onSaved()}.onFailure{error=it.message}},enabled=valid,modifier=Modifier.testTag("network-bill-save")){AppText("Сохранить")}},dismissButton={PressTextButton(onClick=onDismiss){AppText("Отмена")}})
}

@Composable private fun NativeExpenseEditor(repo:NativePartnerRepository,initialDate:LocalDate,onDismiss:()->Unit,onSaved:()->Unit){
    val stations=repo.stations(includeArchived=true)
    var stationId by remember{mutableStateOf(stations.first().id)}
    var kind by remember{mutableStateOf(NativeExpenseKind.OTHER)}
    var amount by remember{mutableStateOf("")};var date by remember{mutableStateOf(initialDate.toString())};var reason by remember{mutableStateOf("")};var paid by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    val valid=nativeDecimal(amount)?.signum()?.let{it>=0}==true&&reason.isNotBlank()&&runCatching{LocalDate.parse(date)}.isSuccess
    AlertDialog(onDismissRequest=onDismiss,title={AppText("Расход / выплата · ДЕМО")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
        stations.forEach{s->DepthChip(stationId==s.id,{stationId=s.id},label={AppText(s.title)})}
        NativeExpenseKind.entries.forEach{k->DepthChip(kind==k,{kind=k},label={AppText(k.label)})}
        NativeField("Сумма, сом",amount,"network-expense-amount"){amount=it.take(18)}
        NativeField("Дата · ГГГГ-ММ-ДД",date){date=it.take(10)}
        NativeField("Причина / документ",reason,"network-expense-reason"){reason=it.take(300)}
        DepthChip(paid,{paid=!paid},label={AppText("Оплачено / подтверждено · ДЕМО")})
        error?.let{AppText(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={PressTextButton(onClick={runCatching{repo.saveExpense(NativeExpense("expense-${java.util.UUID.randomUUID()}",stationId,LocalDate.parse(date),kind,nativeDecimal(amount)!!,reason,paid=paid),reason);onSaved()}.onFailure{error=it.message}},enabled=valid,modifier=Modifier.testTag("network-expense-save")){AppText("Сохранить")}},dismissButton={PressTextButton(onClick=onDismiss){AppText("Отмена")}})
}

@Composable private fun NativeDenied(){Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){AppText("Нет права просмотра этого раздела",fontSize=22.sp,fontWeight=FontWeight.Bold);AppText("Роли и назначения проверяются в демонстрационном репозитории. Реальная серверная авторизация появится после подключения backend.",color=Muted)}}

@Composable private fun NativeProfile(account:PartnerAccount,onSwitch:()->Unit,onSignOut:()->Unit){
    val context=LocalContext.current
    val prefs=remember{context.getSharedPreferences("qubat_partner_profile_demo",0)}
    var name by rememberSaveable(account.id){mutableStateOf(prefs.getString("${account.id}.name",account.name).orEmpty())}
    var contact by rememberSaveable(account.id){mutableStateOf(prefs.getString("${account.id}.contact","").orEmpty())}
    var saved by remember{mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize().testTag("network-profile-scroll"),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item(key="notice"){NativeNotice()}
        item(key="profile"){NativePanel("Профиль партнёра"){
            AppText(account.role.label,color=Lime)
            NativeField("Отображаемое имя",name,"network-profile-name"){name=it.take(80);saved=false}
            NativeField("Контакт демо-профиля",contact){contact=it.take(100);saved=false}
            DepthButton(onClick={prefs.edit().putString("${account.id}.name",name.trim()).putString("${account.id}.contact",contact.trim()).apply();saved=true},enabled=name.isNotBlank()){AppText("Сохранить профиль")}
            if(saved)AppText("Сохранено на устройстве",color=Lime)
            AppText("Изменение имени не меняет права и назначенные станции.",fontSize=11.sp,color=Muted)
        }}
        item(key="auth"){NativePanel("Демонстрационный доступ"){
            AppText("Единый сервер, подтверждение телефона/email, 2FA и синхронизация с веб-кабинетом относятся к подключению реального backend. Пароли и реальные платёжные данные здесь не запрашиваются.",fontSize=12.sp,color=Muted)
            DepthOutlinedButton(onClick=onSwitch,modifier=Modifier.fillMaxWidth().testTag("partner-switch-demo")){AppText("Сменить демо-аккаунт")}
            PressTextButton(onClick=onSignOut,modifier=Modifier.testTag("partner-sign-out")){AppText("Выйти из кабинета")}
        }}
    }
}


@Composable private fun NativeFinancialTerms(repo:NativePartnerRepository,onDismiss:()->Unit,onSaved:()->Unit){
    val stations=repo.stations(includeArchived=true)
    var stationId by remember{mutableStateOf(stations.first().id)}
    val station=stations.first{it.id==stationId}
    val history=repo.tariffs(stationId)
    val base=history.firstOrNull()?:NativeTariff("new",stationId,repo.today)
    var electricity by remember(stationId){mutableStateOf(base.electricityRate.toPlainString())}
    var platform by remember(stationId){mutableStateOf(base.platformRate.movePointRight(2).toPlainString())}
    var acquiring by remember(stationId){mutableStateOf(base.acquiringRate.movePointRight(2).toPlainString())}
    var tax by remember(stationId){mutableStateOf(base.taxRate.movePointRight(2).toPlainString())}
    var rent by remember(stationId){mutableStateOf(base.rentMonthly.toPlainString())}
    var partner by remember(stationId){mutableStateOf(base.partnerRate.movePointRight(2).toPlainString())}
    var fixed by remember(stationId){mutableStateOf(base.partnerFixedMonthly.toPlainString())}
    var reserve by remember(stationId){mutableStateOf(base.reserveRate.movePointRight(2).toPlainString())}
    var date by remember(stationId){mutableStateOf(maxOf(repo.today,base.effectiveFrom.plusDays(1)).toString())}
    var reason by remember{mutableStateOf("")}
    var error by remember{mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=onDismiss,title={AppText("Условия договора · ДЕМО")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(11.dp)){
        AppText("Новая версия действует с выбранной даты. Суммы завершённых зарядок не пересчитываются.",fontSize=11.sp,color=Muted)
        stations.forEach{s->DepthChip(stationId==s.id,{stationId=s.id},modifier=Modifier.fillMaxWidth(),label={AppText(s.title)})}
        AppText("Партнёр: ${station.partnerName} · договор ${station.contractId}",fontSize=12.sp,color=Muted)
        NativeField("Закупочная цена, сом/кВт·ч",electricity){electricity=it.take(18)}
        NativeField("Комиссия платформы, %",platform){platform=it.take(8)}
        NativeField("Эквайринг, %",acquiring){acquiring=it.take(8)}
        NativeField("Налог, %",tax){tax=it.take(8)}
        NativeField("Аренда, сом/месяц",rent){rent=it.take(18)}
        NativeField("Доля партнёра, %",partner){partner=it.take(8)}
        NativeField("Или фиксированная доля, сом/месяц",fixed){fixed=it.take(18)}
        NativeField("Резерв, %",reserve){reserve=it.take(8)}
        NativeField("С даты · ГГГГ-ММ-ДД",date){date=it.take(10)}
        AppText("Предыдущие версии:",fontWeight=FontWeight.Bold)
        history.forEach{t->AppText("${t.effectiveFrom} — ${t.effectiveTo?:"действует"}: электричество ${nativeMoney(t.electricityRate)}, платформа ${t.platformRate.movePointRight(2)}%, налог ${t.taxRate.movePointRight(2)}%",fontSize=10.sp,color=Muted)}
        NativeField("Причина изменения",reason){reason=it.take(300)}
        error?.let{AppText(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={PressTextButton(onClick={runCatching{
        fun amount(value:String)=requireNotNull(nativeDecimal(value)?.takeIf{it.signum()>=0}){"Укажите неотрицательную сумму"}
        fun rate(value:String)=requireNotNull(nativeDecimal(value)?.takeIf{it>=BigDecimal.ZERO&&it<=BigDecimal("100")}){"Процент — от 0 до 100"}.movePointLeft(2)
        repo.updateTariff(base.copy(id="terms-${java.util.UUID.randomUUID()}",effectiveFrom=LocalDate.parse(date),effectiveTo=null,electricityRate=amount(electricity),platformRate=rate(platform),acquiringRate=rate(acquiring),taxRate=rate(tax),rentMonthly=amount(rent),partnerRate=rate(partner),partnerFixedMonthly=amount(fixed),reserveRate=rate(reserve)),reason)
        onSaved()
    }.onFailure{error=it.message}},enabled=reason.isNotBlank()){AppText("Сохранить версию")}},dismissButton={PressTextButton(onClick=onDismiss){AppText("Отмена")}})
}
