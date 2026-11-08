package kg.qubat.ev

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date

@OptIn(ExperimentalFoundationApi::class)
@Composable fun ProfileContent(available:Long,partnerLabel:String?=null,onPartner:()->Unit={},onPartnerDemo:()->Unit={},onWallet:()->Unit) {
    val context=LocalContext.current
    val prefs=remember {context.getSharedPreferences("qubat_profile",0)}
    var name by remember {mutableStateOf(prefs.getString("name","").orEmpty())}
    var phone by remember {mutableStateOf(prefs.getString("phone","").orEmpty())}
    var car by remember {mutableStateOf(prefs.getString("car","").orEmpty())}
    var plate by remember {mutableStateOf(prefs.getString("plate","").orEmpty())}
    val created=remember {prefs.getLong("created",0L).takeIf {it>0} ?: System.currentTimeMillis().also {prefs.edit().putLong("created",it).apply()}}
    val createdLabel=remember(created) {SimpleDateFormat("dd.MM.yyyy",Ru).format(Date(created))}
    var languagePicker by rememberSaveable {mutableStateOf(false)}
    var editor by rememberSaveable {mutableStateOf<String?>(null)}
    var info by rememberSaveable {mutableStateOf<String?>(null)}
    LazyColumn(Modifier.fillMaxSize().testTag("profile-scroll"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item(key="header") {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {AppText("Профиль",Modifier.weight(1f),fontSize=27.sp,fontWeight=FontWeight.Bold);ThemeToggle()}}
        item(key="avatar") {DepthSurface(shape=RoundedCornerShape(24.dp),color=SurfaceGreen,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=.35f))) {
            Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(84.dp).depthFrame(CircleShape,Lime.copy(alpha=.13f),2.dp),contentAlignment=Alignment.Center) {
                    if(name.isBlank()) AppIcon(Icons.Default.Person,null,tint=Lime,modifier=Modifier.size(48.dp)) else AppText(name.split(" ").filter {it.isNotBlank()}.take(2).joinToString("") {it.take(1).uppercase()},color=Lime,fontSize=28.sp,fontWeight=FontWeight.Bold)
                }
                Text(if(name.isBlank()) LocalTranslate.current("Демо-пользователь") else name,fontSize=22.sp,fontWeight=FontWeight.Bold,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                Text(if(phone.isBlank()) LocalTranslate.current("Номер телефона не указан") else phone,fontSize=13.sp,color=Muted)
                AppText("Профиль создан: $createdLabel",fontSize=10.sp,color=Muted)
                PressTextButton(onClick={editor="profile"},modifier=Modifier.testTag("edit-profile")) {AppText("Изменить профиль")}
            }
        }}
        item(key="balance") {DepthSurface(shape=RoundedCornerShape(20.dp),color=SurfaceGreen) {
            Column(Modifier.fillMaxWidth().padding(17.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {AppText("БАЛАНС",Modifier.weight(1f),fontSize=12.sp,color=Muted);DepthTonalButton(onClick=onWallet) {AppText("+ Пополнить")}}
                AppText(money(available),fontSize=29.sp,fontWeight=FontWeight.Bold,color=Lime)
                AppText("Учебные средства",fontSize=10.sp,color=Muted)
            }
        }}
        item(key="setting-0",contentType="setting") {AccountRow("Валюта","Кыргызский сом (KGS)","С") {info="Расчёты в приложении отображаются в кыргызских сомах. 1 сом = 100 тыйынов."}}
        item(key="setting-1",contentType="setting") {AccountRow("Безопасность","Данные и доступ к геопозиции","✓",Color(0xFF3A7EBF)) {info="Профиль и учебный баланс хранятся на этом устройстве. Номера банковских карт и пароли не запрашиваются. Геопозиция обновляется только при открытом приложении. Серверной авторизации пока нет."}}
        item(key="setting-2",contentType="setting") {AccountRow("Информация о машине",car.ifBlank {LocalTranslate.current("Добавить автомобиль")}+(if(plate.isNotBlank()) " · $plate" else ""),"EV",Color(0xFFC27E24),"edit-car",translateSubtitle=false) {editor="car"}}
        item(key="setting-3",contentType="setting") {AccountRow("Мои полисы","Страхование автомобиля","П",Color(0xFF586FAF)) {info="Страховые полисы пока не подключены. Оформление и оплата ОСАГО в приложении недоступны."}}
        item(key="setting-4",contentType="setting") {AccountRow("Язык",LocalAppLanguage.current.nativeName,LocalAppLanguage.current.code.uppercase(),tag="profile-language") {languagePicker=true}}
        item(key="setting-5",contentType="setting") {AccountRow("О приложении","QUBAT EV · ${BuildConfig.VERSION_NAME}","Q",tag="profile-about") {info="Демонстрационное приложение зарядки электромобилей. Станции и операции вымышленные. Банки, платёжные системы и реальные зарядные станции не подключены."}}
        if(partnerLabel!=null) item(key="partner",contentType="setting") {
            AccountRow("Кабинет партнёра",partnerLabel,"EV",tag="partner-entry",translateSubtitle=false,onClick=onPartner)
        }
        item(key="footer") {AppText("Личный профиль сохраняется только на этом устройстве",fontSize=10.sp,color=Muted)}
    }
    if(languagePicker) LanguagePicker {languagePicker=false}
    editor?.let {kind->
        var first by rememberSaveable(kind) {mutableStateOf(if(kind=="profile")name else car)}
        var second by rememberSaveable(kind) {mutableStateOf(if(kind=="profile")phone else plate)}
        AlertDialog(modifier=Modifier.depthFrame(RoundedCornerShape(28.dp),MaterialTheme.colorScheme.surfaceContainerHigh,3.dp,fill=false),onDismissRequest={editor=null},title={AppText(if(kind=="profile") "Личные данные" else "Мой автомобиль")},text={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                PressOutlinedTextField(first,{if(it.length<=80)first=it},label={AppText(if(kind=="profile") "Имя" else "Марка и модель")},modifier=Modifier.testTag("profile-field-first").depthFrame(RoundedCornerShape(16.dp),SurfaceGreen,1.dp,fill=false),shape=RoundedCornerShape(16.dp),singleLine=true)
                PressOutlinedTextField(second,{if(it.length<=30)second=it},modifier=Modifier.depthFrame(RoundedCornerShape(16.dp),SurfaceGreen,1.dp,fill=false),shape=RoundedCornerShape(16.dp),label={AppText(if(kind=="profile") "Телефон" else "Госномер")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=if(kind=="profile")KeyboardType.Phone else KeyboardType.Text))
            }
        },confirmButton={PressTextButton(onClick={
            if(kind=="profile") {name=first.trim();phone=second.trim();prefs.edit().putString("name",name).putString("phone",phone).apply()}
            else {car=first.trim();plate=second.trim();prefs.edit().putString("car",car).putString("plate",plate).apply()}
            editor=null
        },modifier=Modifier.testTag("save-profile")) {AppText("Сохранить")}},dismissButton={PressTextButton(onClick={editor=null}) {AppText("Отмена")}})
    }
    info?.let {text->AlertDialog(modifier=Modifier.depthFrame(RoundedCornerShape(28.dp),MaterialTheme.colorScheme.surfaceContainerHigh,3.dp,fill=false),onDismissRequest={info=null},title={AppText("QUBAT EV",if(text.startsWith("Демонстрационное приложение")) Modifier.testTag("partner-demo-access").combinedClickable(onClick={},onLongClick={info=null;onPartnerDemo()}) else Modifier)},text={AppText(text)},confirmButton={PressTextButton(onClick={info=null}) {AppText("Понятно")}})}
}
