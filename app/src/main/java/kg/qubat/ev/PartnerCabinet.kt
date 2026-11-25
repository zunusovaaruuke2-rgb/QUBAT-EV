package kg.qubat.ev

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class CabinetTab(val label:String) { SUMMARY("Сводка"), STATIONS("Станции"), SESSIONS("Зарядки"), PAYOUTS("Выплаты"), PROFILE("Профиль") }
private val cabinetDate=DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val cabinetTime=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(PARTNER_ZONE)
private fun partnerAmount(value:BigDecimal?)=value?.setScale(2,RoundingMode.HALF_UP)?.toPlainString()?.replace('.',',')?.plus(" сом") ?: "Нет данных"
private fun partnerEnergy(value:BigDecimal)=value.setScale(3,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.',',')+" кВт·ч"
private fun partnerTime(value:Instant?)=value?.let(cabinetTime::format) ?: "Не завершена"

/** Phase 1 uses isolated demo fixtures. Identity is never inferred from the driver's profile. */
@OptIn(ExperimentalFoundationApi::class)
@Composable fun PartnerCabinetScreen(account:PartnerAccount,onBack:()->Unit,onSignOut:()->Unit,onSwitchDemo:()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val repo=remember(account.id) {PartnerDemoRepository()}
    val today=remember {LocalDate.now(PARTNER_ZONE)}
    var from by rememberSaveable(account.id) {mutableStateOf(today.withDayOfMonth(1).toString())}
    var to by rememberSaveable(account.id) {mutableStateOf(today.toString())}
    var stationId by rememberSaveable(account.id) {mutableStateOf<String?>(null)}
    var query by rememberSaveable(account.id) {mutableStateOf("")}
    var sort by rememberSaveable(account.id) {mutableStateOf(PartnerSort.NEWEST.name)}
    var tabName by rememberSaveable(account.id) {mutableStateOf(CabinetTab.SUMMARY.name)}
    val tab=CabinetTab.valueOf(tabName)
    var page by rememberSaveable(account.id) {mutableIntStateOf(0)}
    var retry by remember {mutableIntStateOf(0)}
    var report by remember(account.id) {mutableStateOf<PartnerReport?>(null)}
    var stations by remember(account.id) {mutableStateOf<List<PartnerStation>>(emptyList())}
    var loading by remember(account.id) {mutableStateOf(true)}
    var error by remember(account.id) {mutableStateOf(false)}
    var dateDialog by rememberSaveable {mutableStateOf(false)}
    var menu by remember {mutableStateOf(false)}
    var exportBusy by remember {mutableStateOf(false)}
    var pendingExport by remember(account.id) {mutableStateOf<PartnerReport?>(null)}
    var exportStatus by remember {mutableStateOf<String?>(null)}
    var profileEditor by remember {mutableStateOf(false)}
    val prefs=remember {context.getSharedPreferences("qubat_partner_profile_demo",0)}
    var partnerName by remember(account.id) {mutableStateOf(prefs.getString("${account.id}.name",account.name).orEmpty())}
    var contact by remember(account.id) {mutableStateOf(prefs.getString("${account.id}.contact","").orEmpty())}
    DisposableEffect(account.id) {onDispose {pendingExport=null}}
    val filter=remember(from,to,stationId,query,sort) {PartnerFilter(PartnerPeriod(LocalDate.parse(from),LocalDate.parse(to)),stationId,query,PartnerSort.valueOf(sort))}
    val currentReport=report?.takeIf {it.account.id==account.id && it.filter==filter}
    val waiting=loading || (currentReport==null && !error)
    LaunchedEffect(account,filter,retry) {
        loading=true;error=false;report=null;page=0
        try {
            val loaded=withContext(Dispatchers.Default) {repo.stations(account) to repo.report(account,filter)}
            stations=loaded.first;report=loaded.second
            if(BuildConfig.DEBUG) android.util.Log.d("QUBAT_PARTNER","Report loaded: ${loaded.second.sessions.size} sessions, matching filter=${loaded.second.filter==filter}")
        } catch(cancel:CancellationException) {throw cancel}
        catch(failure:Exception) {android.util.Log.e("QUBAT_PARTNER","Demo report loading failed",failure);error=true}
        finally {loading=false}
    }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {uri->
        val snapshot=pendingExport
        pendingExport=null
        if(uri==null || snapshot==null || snapshot.account.id!=account.id) {exportBusy=false;return@rememberLauncherForActivityResult}
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri,"w")?.use {PartnerExcel.write(snapshot,it)} ?: throw java.io.IOException("Output stream unavailable")
                }
                exportStatus="Excel сохранён · ${snapshot.filter.period.from.format(cabinetDate)} — ${snapshot.filter.period.to.format(cabinetDate)}"
            } catch(cancel:CancellationException) {throw cancel}
            catch(_:Exception) {exportStatus="Не удалось сохранить Excel. Повторите выгрузку."}
            finally {exportBusy=false}
        }
    }
    fun choosePeriod(period:PartnerPeriod) {from=period.from.toString();to=period.to.toString()}
    Surface(color=Forest,modifier=Modifier.fillMaxSize().testTag("partner-screen").semantics {stateDescription=if(waiting) "Загрузка отчёта" else if(error) "Ошибка отчёта" else "Отчёт загружен"}) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                PressIconButton(onClick=onBack,modifier=Modifier.testTag("partner-back")) {AppIcon(Icons.Default.ArrowBack,"Назад в приложение")}
                Column(Modifier.weight(1f)) {AppText("Кабинет партнёра",fontSize=20.sp,fontWeight=FontWeight.Bold);AppText(account.role.label,fontSize=11.sp,color=Muted)}
                ThemeToggle()
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.3f))
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("partner-scroll"),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                item(key="identity",contentType="header") {
                    DepthSurface(shape=RoundedCornerShape(20.dp),color=SurfaceGreen) {
                        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text(partnerName,Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.Bold)
                                AppText("ДЕМО",Modifier.padding(start=8.dp),fontSize=11.sp,color=Lime,fontWeight=FontWeight.Bold)
                            }
                            AppText("Демонстрационные станции и расчёты. Реальные платежи не подключены.",fontSize=11.sp,color=Muted)
                            AppText("Только просмотр и отчёты",fontSize=11.sp,color=Lime)
                        }
                    }
                }
                item(key="filters",contentType="filters") {
                    Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
                        Box(Modifier.fillMaxWidth()) {
                            DepthOutlinedButton(onClick={menu=true},modifier=Modifier.fillMaxWidth().testTag("partner-station-filter")) {
                                AppIcon(Icons.Default.Place,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp))
                                AppText(stations.firstOrNull {it.id==stationId}?.title ?: "Все мои станции",Modifier.weight(1f),maxLines=1)
                                AppIcon(Icons.Default.ArrowDropDown,null)
                            }
                            DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                                PressTextButton(onClick={stationId=null;menu=false},modifier=Modifier.testTag("partner-station-all")) {AppText("Все мои станции")}
                                stations.forEach {station->PressTextButton(onClick={stationId=station.id;menu=false},modifier=Modifier.testTag("partner-select-${station.id}")) {Text(station.title)}}
                            }
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                            DepthChip(filter.period==PartnerPeriod.today(today),{choosePeriod(PartnerPeriod.today(today))},Modifier.testTag("partner-period-today")) {AppText("Сегодня")}
                            DepthChip(filter.period==PartnerPeriod.sevenDays(today),{choosePeriod(PartnerPeriod.sevenDays(today))},Modifier.testTag("partner-period-7")) {AppText("7 дней")}
                            DepthChip(filter.period==PartnerPeriod.month(today),{choosePeriod(PartnerPeriod.month(today))},Modifier.testTag("partner-period-month")) {AppText("Месяц")}
                            DepthChip(false,{dateDialog=true},Modifier.testTag("partner-period-custom")) {AppText("Выбрать даты")}
                        }
                        AppText("${filter.period.from.format(cabinetDate)} — ${filter.period.to.format(cabinetDate)} · Asia/Bishkek",fontSize=11.sp,color=Muted,modifier=Modifier.testTag("partner-period-label"))
                        PressOutlinedTextField(query,{query=it.take(100)},modifier=Modifier.fillMaxWidth().testTag("partner-search"),singleLine=true,label={AppText("Поиск по станции")},leadingIcon={AppIcon(Icons.Default.Search,null)})
                        DepthButton(onClick={currentReport?.let {snapshot->pendingExport=snapshot;exportBusy=true;exportStatus=null;export.launch(PartnerExcel.fileName(snapshot))}},enabled=currentReport!=null && !waiting && !exportBusy,modifier=Modifier.fillMaxWidth().testTag("partner-export")) {
                            if(exportBusy) {CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp);Spacer(Modifier.width(8.dp))}
                            AppText(if(exportBusy) "Подготовка Excel…" else "Скачать Excel (.xlsx)")
                        }
                        exportStatus?.let {AppText(it,fontSize=11.sp,color=Muted,modifier=Modifier.testTag("partner-export-status"))}
                    }
                }
                stickyHeader(key="tabs") {
                    Row(Modifier.fillMaxWidth().background(Forest).padding(vertical=6.dp).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        CabinetTab.entries.forEach {item->DepthChip(tab==item,{tabName=item.name;page=0},Modifier.testTag("partner-tab-${item.name.lowercase()}")) {AppText(item.label)}}
                    }
                }
                if(waiting) item(key="loading") {Row(Modifier.fillMaxWidth().padding(20.dp),horizontalArrangement=Arrangement.Center) {CircularProgressIndicator(Modifier.size(28.dp));Spacer(Modifier.width(12.dp));AppText("Загрузка отчёта…")}}
                else if(error) item(key="error") {PartnerInfoCard("Не удалось загрузить отчёт","Повторите попытку") {PressTextButton(onClick={retry++},modifier=Modifier.testTag("partner-retry")) {AppText("Повторить")}}}
                else currentReport?.let {snapshot->
                    when(tab) {
                        CabinetTab.SUMMARY -> {
                            item(key="energy") {PartnerMetric("Продано электроэнергии",partnerEnergy(snapshot.totals.energyKwh),"Только завершённые зарядки","partner-energy")}
                            item(key="sessions") {PartnerMetric("Зарядные сессии",snapshot.totals.completedCount.toString(),"Завершённые","partner-count")}
                            item(key="counts") {PartnerInfoCard("Отмечены отдельно","Отменённые: ${snapshot.totals.cancelledCount} · Незавершённые: ${snapshot.totals.unfinishedCount}")}
                            item(key="revenue") {PartnerMetric("Выручка",partnerAmount(snapshot.totals.revenueKgs),"Начислено до вычетов; не подтверждение оплаты","partner-revenue")}
                            item(key="deductions") {PartnerMetric("Удержания",partnerAmount(snapshot.totals.deductionsKgs),"Возвраты и комиссии по демонстрационному договору","partner-deductions")}
                            item(key="payable") {PartnerMetric("К перечислению",partnerAmount(snapshot.totals.payableKgs),"За выбранный период","partner-payable")}
                            item(key="opening") {PartnerMetric("Перенос прошлых периодов",partnerAmount(snapshot.totals.openingBalanceKgs),"Остаток на начало периода","partner-opening")}
                            item(key="paid") {PartnerMetric("Выплачено",partnerAmount(snapshot.totals.paidKgs),"По дате подтверждённого перевода · демо","partner-paid")}
                            item(key="balance") {PartnerMetric("Остаток",partnerAmount(snapshot.totals.balanceKgs),"С учётом переноса и подтверждённых выплат","partner-balance")}
                            if(snapshot.sessions.isEmpty()) item(key="empty-summary") {PartnerInfoCard("За выбранный период зарядок не найдено","Измените период или фильтр станций")}
                            item(key="snapshot") {AppText("Обновлено: ${partnerTime(snapshot.generatedAt)} · Asia/Bishkek",fontSize=10.sp,color=Muted)}
                        }
                        CabinetTab.STATIONS -> {
                            if(snapshot.stations.isEmpty()) item(key="empty-stations") {PartnerInfoCard("Станции не найдены","Измените поиск или фильтр")}
                            items(snapshot.stations,key={it.station.id},contentType={"station"}) {row->PartnerStationCard(row)}
                        }
                        CabinetTab.SESSIONS -> {
                            item(key="sort") {
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                    PartnerSort.entries.forEach {item->DepthChip(sort==item.name,{sort=item.name},Modifier.testTag("partner-sort-${item.name.lowercase()}")) {AppText(item.label)}}
                                }
                            }
                            if(snapshot.sessions.isEmpty()) item(key="empty-sessions") {PartnerInfoCard("За выбранный период зарядок не найдено","Измените период или фильтр станций")}
                            val pages=((snapshot.sessions.size+19)/20).coerceAtLeast(1)
                            items(snapshot.sessions.drop(page*20).take(20),key={it.id},contentType={"session"}) {session->PartnerSessionCard(session)}
                            if(snapshot.sessions.isNotEmpty()) item(key="pages") {
                                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                                    PressTextButton(onClick={page--},enabled=page>0,modifier=Modifier.testTag("partner-page-prev")) {AppText("Назад")}
                                    AppText("${page+1} / $pages",fontSize=12.sp,modifier=Modifier.testTag("partner-page-label"))
                                    PressTextButton(onClick={page++},enabled=page+1<pages,modifier=Modifier.testTag("partner-page-next")) {AppText("Далее")}
                                }
                            }
                        }
                        CabinetTab.PAYOUTS -> {
                            item(key="payment-note") {PartnerInfoCard("Подтверждённые выплаты","Начислено, оплачено водителями и перечислено владельцу — разные показатели. Здесь только демонстрационные записи.")}
                            if(snapshot.payouts.isEmpty()) item(key="empty-payouts") {PartnerInfoCard(if(snapshot.totals.paidKgs==null) "Нет данных о выплатах" else "За выбранный период выплат не найдено",if(snapshot.totals.paidKgs==null) "Отсутствие подтверждения не означает нулевую выплату" else "Подтверждённых переводов за выбранные даты нет")}
                            items(snapshot.payouts,key={it.id},contentType={"payout"}) {payout->PartnerPayoutCard(payout)}
                        }
                        CabinetTab.PROFILE -> {
                            item(key="owner-profile") {PartnerInfoCard("Профиль партнёра",account.role.label) {
                                Text(partnerName,fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                                AppText("Учётная запись: ${account.name}",fontSize=11.sp,color=Muted)
                                if(contact.isNotBlank()) {Text(contact,fontSize=13.sp,color=Muted)}
                                AppText("Изменения хранятся только на этом устройстве",fontSize=11.sp,color=Muted)
                                PressTextButton(onClick={profileEditor=true},modifier=Modifier.testTag("partner-edit-profile")) {AppText("Изменить профиль")}
                            }}
                            item(key="demo-security") {PartnerInfoCard("Демонстрационный доступ","Это локальные тестовые роли. Серверный вход, восстановление доступа и завершение потерянных сессий появятся после подключения сервера.")}
                            item(key="switch") {DepthOutlinedButton(onClick=onSwitchDemo,modifier=Modifier.fillMaxWidth().testTag("partner-switch-demo")) {AppText("Сменить демо-аккаунт")}}
                            item(key="logout") {DepthButton(onClick=onSignOut,modifier=Modifier.fillMaxWidth().testTag("partner-sign-out")) {AppText("Выйти из кабинета")}}
                        }
                    }
                }
                item(key="bottom-space") {Spacer(Modifier.height(12.dp))}
            }
        }
    }
    if(dateDialog) PartnerDateDialog(filter.period,onDismiss={dateDialog=false},onApply={choosePeriod(it);dateDialog=false})
    if(profileEditor) {
        var editedName by remember {mutableStateOf(partnerName)}
        var editedContact by remember {mutableStateOf(contact)}
        AlertDialog(onDismissRequest={profileEditor=false},title={AppText("Профиль партнёра")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            PressOutlinedTextField(editedName,{editedName=it.take(80)},label={AppText("Название партнёра")},singleLine=true,modifier=Modifier.testTag("partner-profile-name"))
            PressOutlinedTextField(editedContact,{editedContact=it.take(100)},label={AppText("Контакт для демо-профиля")},singleLine=true)
        }},confirmButton={PressTextButton(onClick={partnerName=editedName.trim();contact=editedContact.trim();prefs.edit().putString("${account.id}.name",partnerName).putString("${account.id}.contact",contact).apply();profileEditor=false},enabled=editedName.isNotBlank()) {AppText("Сохранить")}},dismissButton={PressTextButton(onClick={profileEditor=false}) {AppText("Отмена")}})
    }
}

@Composable private fun PartnerMetric(title:String,value:String,note:String,tag:String) {
    DepthSurface(modifier=Modifier.fillMaxWidth().testTag(tag),shape=RoundedCornerShape(20.dp),color=SurfaceGreen) {
        Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            AppText(title,fontSize=13.sp,color=Muted)
            AppText(value,fontSize=25.sp,fontWeight=FontWeight.Bold,color=Lime)
            AppText(note,fontSize=10.sp,color=Muted)
        }
    }
}
@Composable private fun PartnerInfoCard(title:String,description:String,content:@Composable ColumnScope.()->Unit={}) {
    DepthSurface(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),color=SurfaceGreen) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            AppText(title,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
            AppText(description,fontSize=11.sp,lineHeight=16.sp,color=Muted)
            content()
        }
    }
}
@Composable private fun PartnerDateDialog(period:PartnerPeriod,onDismiss:()->Unit,onApply:(PartnerPeriod)->Unit) {
    var first by remember {mutableStateOf(period.from.toString())}
    var last by remember {mutableStateOf(period.to.toString())}
    val valid=remember(first,last) {runCatching {PartnerPeriod(LocalDate.parse(first),LocalDate.parse(last))}.getOrNull()}
    AlertDialog(onDismissRequest=onDismiss,title={AppText("Период отчёта")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        AppText("Даты по Asia/Bishkek. Последний день включается целиком.",fontSize=12.sp,color=Muted)
        PressOutlinedTextField(first,{first=it.take(10)},label={AppText("С даты · ГГГГ-ММ-ДД")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Ascii),modifier=Modifier.testTag("partner-date-from"))
        PressOutlinedTextField(last,{last=it.take(10)},label={AppText("По дату · ГГГГ-ММ-ДД")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Ascii),modifier=Modifier.testTag("partner-date-to"))
        if(valid==null) AppText("Проверьте даты: окончание должно быть не раньше начала",fontSize=11.sp,color=MaterialTheme.colorScheme.error)
    }},confirmButton={PressTextButton(onClick={valid?.let(onApply)},enabled=valid!=null,modifier=Modifier.testTag("partner-date-apply")) {AppText("Применить")}},dismissButton={PressTextButton(onClick=onDismiss) {AppText("Отмена")}})
}

@Composable private fun PartnerStationCard(row:PartnerStationReport) {
    val station=row.station
    DepthSurface(modifier=Modifier.fillMaxWidth().testTag("partner-station-${station.id}"),shape=RoundedCornerShape(20.dp),color=SurfaceGreen) {
        Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Text(station.title,fontSize=18.sp,fontWeight=FontWeight.Bold)
            AppText(station.address,fontSize=12.sp,color=Muted)
            AppText("ID: ${station.id}",fontSize=10.sp,color=Muted)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                AppText("${station.powerKw} кВт · ${station.connectorCount} разъёмов",fontSize=12.sp)
                AppText(station.connectionStatus ?: "Нет данных",fontSize=11.sp,color=Lime)
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.3f))
            PartnerKeyValue("Энергия за период",partnerEnergy(row.energyKwh))
            PartnerKeyValue("Выручка за период",partnerAmount(row.revenueKgs))
            PartnerKeyValue("Завершённые сессии",row.completedCount.toString())
            PartnerKeyValue("Тариф",partnerAmount(station.tariffKgs)+" /кВт·ч")
            PartnerKeyValue("Комиссия договора",station.commissionRate.multiply(BigDecimal(100)).stripTrailingZeros().toPlainString()+"%")
            AppText("Договор: ${station.contractId}",fontSize=10.sp,color=Muted)
            PartnerFreshness(station.source,station.updatedAt,station.isCurrent)
        }
    }
}
@Composable private fun PartnerSessionCard(session:PartnerSession) {
    DepthSurface(modifier=Modifier.fillMaxWidth().testTag("partner-session-${session.id}"),shape=RoundedCornerShape(18.dp),color=SurfaceGreen) {
        Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(session.stationTitle,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                AppText(session.status.label,fontSize=12.sp,color=if(session.status==PartnerSessionStatus.COMPLETED)Lime else Muted)
                AppText(partnerEnergy(session.energyKwh),fontSize=13.sp,fontWeight=FontWeight.Bold)
            }
            AppText("ID: ${session.id}",fontSize=10.sp,color=Muted)
            PartnerKeyValue("Порт",session.port)
            PartnerKeyValue("Начало",partnerTime(session.startedAt))
            PartnerKeyValue("Конец",partnerTime(session.endedAt))
            val duration=session.durationSeconds?.let {seconds->"${seconds/3600}:${((seconds%3600)/60).toString().padStart(2,'0')}:${(seconds%60).toString().padStart(2,'0')}"} ?: "Не завершена"
            PartnerKeyValue("Продолжительность",duration)
            PartnerKeyValue("Тариф",partnerAmount(session.tariffKgs)+" /кВт·ч")
            PartnerKeyValue("Выручка",partnerAmount(session.revenueKgs))
            PartnerKeyValue("Комиссия",partnerAmount(session.commissionKgs))
            PartnerKeyValue("Оплата",session.paymentStatus.label)
            PartnerKeyValue("Возврат",partnerAmount(session.refundKgs))
            if(session.refundKgs>BigDecimal.ZERO) {
                session.refundReason?.let {AppText("Причина возврата: $it",fontSize=10.sp,color=Muted)}
                session.refundConfirmedAt?.let {AppText("Подтверждён: ${partnerTime(it)}",fontSize=10.sp,color=Muted)}
                session.refundResponsible?.let {AppText("Ответственный: $it",fontSize=10.sp,color=Muted)}
            }
            PartnerFreshness(session.source,session.updatedAt,session.isCurrent)
        }
    }
}
@Composable private fun PartnerPayoutCard(payout:PartnerPayout) {
    DepthSurface(modifier=Modifier.fillMaxWidth().testTag("partner-payout-${payout.id}"),shape=RoundedCornerShape(18.dp),color=SurfaceGreen) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(payout.stationTitle,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
            AppText(partnerAmount(payout.amountKgs),fontSize=23.sp,fontWeight=FontWeight.Bold,color=Lime)
            AppText(if(payout.confirmed) "Подтверждено · демо" else "Не подтверждено · демо",fontSize=12.sp,color=Muted)
            AppText("ID: ${payout.id}",fontSize=10.sp,color=Muted)
            PartnerKeyValue("Дата выплаты",partnerTime(payout.paidAt))
            PartnerKeyValue("Период расчёта","${payout.period.from.format(cabinetDate)} — ${payout.period.to.format(cabinetDate)}")
            AppText(payout.note,fontSize=11.sp,color=Muted)
            PartnerFreshness(payout.source,payout.updatedAt,payout.isCurrent)
        }
    }
}
@Composable private fun PartnerKeyValue(label:String,value:String) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top) {
        AppText(label,Modifier.weight(1f),fontSize=11.sp,color=Muted)
        AppText(value,Modifier.weight(1.3f),fontSize=11.sp,textAlign=androidx.compose.ui.text.style.TextAlign.End)
    }
}
@Composable private fun PartnerFreshness(source:String,updatedAt:Instant,current:Boolean) {
    AppText("Источник: $source · ДЕМО",fontSize=10.sp,color=Muted)
    AppText("Обновлено: ${partnerTime(updatedAt)} · Asia/Bishkek",fontSize=10.sp,color=Muted)
    if(!current) AppText("Данные требуют обновления",fontSize=10.sp,color=MaterialTheme.colorScheme.error)
}
