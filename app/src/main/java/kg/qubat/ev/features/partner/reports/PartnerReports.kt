@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package kg.qubat.ev

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.*
import kotlinx.coroutines.*

@Composable internal fun NativeExport(repo:NativePartnerRepository,report:NativeNetworkReport,onChanged:()->Unit){
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var pending by remember(repo.account.id){mutableStateOf<NativeNetworkReport?>(null)}
    var busy by remember{mutableStateOf(false)};var message by remember{mutableStateOf<String?>(null)}
    DisposableEffect(repo.account.id){onDispose{pending=null}}
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")){uri->
        val frozen=pending;pending=null
        if(uri==null||frozen==null||frozen.account.id!=repo.account.id){busy=false;return@rememberLauncherForActivityResult}
        scope.launch{try{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri,"w")?.use{NativePartnerExcel.write(frozen,it)}?:error("Нет доступа к файлу")};message="Excel сохранён: 5 листов · ${frozen.filter.period.from} — ${frozen.filter.period.to}"}catch(cancel:CancellationException){throw cancel}catch(_:Exception){message="Не удалось сохранить Excel. Повторите попытку."}finally{busy=false}}
    }
    if(repo.can(NativePermission.EXPORT))DepthButton(onClick={pending=report;busy=true;message=null;launcher.launch(NativePartnerExcel.fileName(report));repo.recordExport(report);onChanged()},enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("network-export")){AppText(if(busy)"Подготовка Excel…" else "Скачать отчёт в Excel")}
    message?.let{AppText(it,fontSize=11.sp,color=Muted)}
}

@Composable internal fun NativeReports(repo:NativePartnerRepository,revision:Int,onChanged:()->Unit){
    if(!repo.can(NativePermission.REPORT)){NativeDenied();return}
    val selected=simpleReportFilters(repo);val filter=selected.filter
    if(filter.stationId!=null && repo.stations(includeArchived=true).none{it.id==filter.stationId}){
        LazyColumn(Modifier.fillMaxSize().testTag("network-reports-scroll"),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            item{selected.content()}
            item{NativePanel("Точка недоступна"){AppText("Выберите другую точку",color=Muted)}}
        }
        return
    }
    val report=remember(repo,filter,revision){repo.report(filter)}
    var page by rememberSaveable(repo.account.id){mutableIntStateOf(0)}
    var details by rememberSaveable(repo.account.id){mutableStateOf(false)}
    var issues by rememberSaveable(repo.account.id){mutableStateOf(false)}
    LaunchedEffect(filter){page=0}
    val pages=((report.sessions.size+19)/20).coerceAtLeast(1)
    if(page>=pages)page=pages-1
    LazyColumn(Modifier.fillMaxSize().testTag("network-reports-scroll"),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item(key="filters"){selected.content()}
        item(key="heading"){AppText("Результат за период",fontSize=17.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold)}
        item(key="energy"){ReportEnergySummary(report.summary)}
        item(key="revenue"){if(repo.can(NativePermission.FINANCE))ReportMoneySummary(report.summary)}
        item(key="counts"){
            NativePanel("Отмены и ошибки"){
                PressTextButton(onClick={issues=!issues},modifier=Modifier.fillMaxWidth().testTag("network-report-issues")){
                    AppText(if(issues)"Скрыть подробности" else "Показать подробности",Modifier.weight(1f),fontSize=14.sp);AppText(if(issues)"▴" else "▾")
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    Column(Modifier.weight(1f)){AppText("Отменено",fontSize=13.sp,color=Muted);AppText(report.summary.cancelledCount.toString(),Modifier.testTag("network-report-cancelled"))}
                    Column(Modifier.weight(1f)){AppText("С ошибкой",fontSize=13.sp,color=Muted);AppText(report.summary.errorCount.toString(),Modifier.testTag("network-report-errors"))}
                }
                if(issues){
                    AppText("Активные зарядки",fontSize=13.sp,color=Muted)
                    AppText(report.summary.activeCount.toString())
                    AppText("Клиентов с завершёнными зарядками",fontSize=13.sp,color=Muted)
                    AppText(report.summary.uniqueCustomers.toString())
                }
            }
        }
        item(key="export"){NativeExport(repo,report,onChanged)}
        item(key="details"){DepthOutlinedButton(onClick={details=!details},modifier=Modifier.fillMaxWidth().testTag("network-report-details")){
            AppText(if(details)"Скрыть зарядки" else "Подробности по зарядкам",Modifier.weight(1f));AppText(if(details)"▴" else "▾")
        }}
        item(key="notice"){AppText("Пример данных",fontSize=12.sp,color=Muted)}
        if(report.sessions.isEmpty())item(key="empty"){NativePanel("За выбранный период зарядок не найдено"){AppText("Измените период или фильтр",color=Muted)}}
        if(details)items(report.sessions.drop(page*20).take(20),key={it.id},contentType={"session"}){s->NativePanel(report.stations.firstOrNull{it.station.id==s.stationId}?.station?.title?:s.stationId,"network-session-${s.id}"){
            AppText(s.id,fontSize=10.sp,color=Muted);AppText(s.status.label,color=if(s.status==NativeSessionStatus.COMPLETED)Lime else Muted)
            AppText("Порт: ${s.portId} · клиент: ${s.customerId}",fontSize=11.sp)
            AppText("Начало: ${nativeTime.format(s.startedAt)}\nКонец: ${s.endedAt?.let(nativeTime::format)?:"Не завершена"}",fontSize=11.sp,color=Muted)
            AppText("${s.durationSeconds/60} мин · ${nativeEnergy(s.energyKwh)}")
            if(repo.can(NativePermission.FINANCE)){AppText("Тариф: ${nativeMoney(s.rates?.customerRate)} / кВт·ч");AppText("Начислено: ${nativeMoney(s.chargedKgs)} · оплачено: ${nativeMoney(s.paidKgs)}");AppText("Возврат: ${nativeMoney(s.refundKgs)} · ${s.paymentStatus.label}",fontSize=12.sp);AppText("Платформа: ${nativeMoney(s.platformFeeKgs)} · эквайринг: ${nativeMoney(s.acquiringFeeKgs)}",fontSize=11.sp,color=Muted)}
            s.errorReason?.let{AppText(it,color=MaterialTheme.colorScheme.error)}
            AppText("${s.source} · обновлено ${nativeTime.format(s.updatedAt)}",fontSize=10.sp,color=Muted)
        }}
        if(details && report.sessions.isNotEmpty())item(key="pages"){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){PressTextButton(onClick={page--},enabled=page>0,modifier=Modifier.testTag("network-page-prev")){AppText("Назад")};AppText("${page+1} / $pages",Modifier.testTag("network-page-label"));PressTextButton(onClick={page++},enabled=page+1<pages,modifier=Modifier.testTag("network-page-next")){AppText("Далее")}}}
    }
}
