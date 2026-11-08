@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.*
import kotlinx.coroutines.*

@Composable internal fun NativeProfile(account:PartnerAccount,onSwitch:()->Unit,onSignOut:()->Unit){
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
