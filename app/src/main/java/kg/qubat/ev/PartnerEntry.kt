package kg.qubat.ev

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/** Local demo selection only. It is deliberately separate from the editable driver profile. */
@Composable fun QubatApp(vm:ChargingViewModel=viewModel()) {
    val context=LocalContext.current
    val preferences=remember {context.getSharedPreferences("qubat_partner_demo_access",0)}
    val accounts=remember {PartnerDemoRepository().accounts}
    var accountId by rememberSaveable {mutableStateOf(preferences.getString("account",null))}
    val account=accounts.firstOrNull {it.id==accountId && it.role!=PartnerRole.DRIVER}
    var cabinet by rememberSaveable {mutableStateOf(false)}
    var selector by rememberSaveable {mutableStateOf(false)}
    val screens=rememberSaveableStateHolder()
    fun signOut() {
        accountId=null
        preferences.edit().remove("account").apply()
        cabinet=false
        selector=false
    }
    BackHandler(enabled=cabinet && account!=null) {cabinet=false}
    if(cabinet && account!=null) {
        screens.SaveableStateProvider("partner:${account.id}") {
            PartnerCabinetScreen(account=account,onBack={cabinet=false},onSignOut={signOut()},onSwitchDemo={selector=true})
        }
    } else {
        screens.SaveableStateProvider("driver") {
            QubatDriverApp(vm,account?.name,onPartner={cabinet=true},onPartnerDemo={selector=true})
        }
    }
    if(selector) AlertDialog(modifier=Modifier.testTag("partner-demo-dialog").depthFrame(RoundedCornerShape(28.dp),MaterialTheme.colorScheme.surfaceContainerHigh,3.dp,fill=false),
        onDismissRequest={selector=false},title={AppText("Демо-доступ партнёра")},text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                AppText("Только вымышленные аккаунты и отчёты. Это демонстрация ролей, без серверной авторизации и реальных выплат.",color=Muted)
                accounts.forEach {item->
                    DepthSurface(onClick={
                        if(item.role==PartnerRole.DRIVER) signOut() else {
                            accountId=item.id
                            preferences.edit().putString("account",item.id).apply()
                            selector=false
                            cabinet=true
                        }
                    },modifier=Modifier.fillMaxWidth().testTag("partner-demo-${item.id}"),shape=RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            AppText(item.name,fontWeight=FontWeight.SemiBold)
                            AppText(when(item.role) {PartnerRole.DRIVER->"Водитель";PartnerRole.OWNER->"Владелец станции";PartnerRole.ADMIN->"Администратор · только просмотр"},color=Muted)
                        }
                    }
                }
            }
        },confirmButton={PressTextButton(onClick={selector=false}) {AppText("Закрыть")}})
}
