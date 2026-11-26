@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package kg.qubat.ev

import androidx.compose.ui.semantics.testTagsAsResourceId

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import java.time.*
import kotlinx.coroutines.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

private enum class NativePage(val label:String) { MONITOR("Мониторинг"),POINTS("Точки"),REPORTS("Отчёты"),FINANCE("Финансы"),ACCESS("Сотрудники и доступ"),BRANDS("Название и цвет"),JOURNAL("Журнал и уведомления"),PROFILE("Профиль партнёра") }
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
