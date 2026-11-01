package kg.qubat.ev

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date

private data class PaymentOption(val title:String,val mark:String,val color:Color,val description:String)
private val paymentOptions=listOf(
    PaymentOption("Visa","VISA",Color(0xFF1A4296),"Банковская карта"),
    PaymentOption("О!Деньги","О!",Color(0xFFE40087),"Электронный кошелёк"),
    PaymentOption("Элкарт","ЭЛ",Color(0xFF1683AD),"Национальная платёжная система"),
    PaymentOption("ELQR","QR",Color(0xFF7955D9),"Оплата по QR"),
    PaymentOption("MBANK","M",Color(0xFF00866A),"Интернет-банкинг"),
    PaymentOption("Бакай","Б",Color(0xFF175C9E),"Интернет-банкинг"),
    PaymentOption("Демир","db",Color(0xFFBC3148),"Интернет-банкинг")
)
@Composable private fun AccountRow(title:String,subtitle:String,mark:String,color:Color=Color(0xFF187758),tag:String="",onClick:()->Unit) {
    Surface(onClick=onClick,modifier=Modifier.fillMaxWidth().then(if(tag.isBlank()) Modifier else Modifier.testTag(tag)),shape=RoundedCornerShape(18.dp),color=SurfaceGreen,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=.35f))) {
        Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(45.dp).background(color.copy(alpha=.12f),RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center) {
                Text(mark,color=if(LocalNight.current) Color(0xFFDCECF7) else color,fontSize=if(mark.length>3)12.sp else 20.sp,fontWeight=FontWeight.ExtraBold,fontStyle=if(mark=="VISA")FontStyle.Italic else FontStyle.Normal)
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {Text(title,fontSize=16.sp,fontWeight=FontWeight.SemiBold);Text(subtitle,fontSize=11.sp,lineHeight=15.sp,color=Muted)}
            Icon(Icons.Default.ArrowForward,null,tint=Muted,modifier=Modifier.size(18.dp))
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun WalletContent(state:AppState,onTopUp:(Long,String)->Boolean) {
    var selected by rememberSaveable {mutableStateOf<String?>(null)}
    var historyOnly by rememberSaveable {mutableStateOf(false)}
    var notice by rememberSaveable {mutableStateOf<String?>(null)}
    val available=(state.balanceMinor-(if(state.session!=null)state.progress.costMinor else 0)).coerceAtLeast(0)
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text(if(historyOnly) "История баланса" else "Кошелёк",Modifier.weight(1f),fontSize=27.sp,fontWeight=FontWeight.Bold);ThemeToggle()}
        if(historyOnly) TextButton(onClick={historyOnly=false}) {Text("Назад к кошельку")}
        else {
            Surface(shape=RoundedCornerShape(23.dp),color=SurfaceGreen,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=.35f))) {
                Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Text("Учебный баланс",fontSize=12.sp,color=Muted)
                        Surface(shape=RoundedCornerShape(7.dp),color=Lime.copy(alpha=.1f)) {Text("ДЕМО",Modifier.padding(horizontal=8.dp,vertical=4.dp),fontSize=10.sp,color=Lime)}
                    }
                    Text(money(available),fontSize=34.sp,fontWeight=FontWeight.Bold,color=Lime)
                    Text("Кыргызский сом · KGS",fontSize=11.sp,color=Muted)
                    HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.3f))
                    TextButton(onClick={historyOnly=true},contentPadding=PaddingValues(0.dp)) {Icon(Icons.Default.DateRange,null,Modifier.size(18.dp));Text("  История баланса",Modifier.weight(1f));Icon(Icons.Default.ArrowForward,null,Modifier.size(18.dp))}
                }
            }
            Text("Способы пополнения",fontSize=18.sp,fontWeight=FontWeight.Bold)
            Text("Демо-режим · банковские списания отключены",fontSize=11.sp,color=Muted)
            paymentOptions.take(4).forEach {option->AccountRow(option.title,option.description,option.mark,option.color,"payment-${option.title}") {selected=option.title}}
            Text("Интернет-банкинг и кошельки",fontSize=18.sp,fontWeight=FontWeight.Bold)
            paymentOptions.drop(4).forEach {option->AccountRow(option.title,"Пополнение через приложение банка",option.mark,option.color,"payment-${option.title}") {selected=option.title}}
            AccountRow("Промокод","Подарочные коды и акции","%",Color(0xFFAA7A14)) {notice="Промокоды будут доступны после подключения сервиса акций. Сейчас деньги не начисляются по кодам."}
            AccountRow("Платёжный терминал","Пополнение наличными","Т",Color(0xFF7552AE)) {notice="Терминальное пополнение пока не подключено. Идентификатор для настоящего платежа не выдаётся. Для проверки интерфейса выберите способ демо-пополнения выше."}
        }
        if(historyOnly || state.history.isNotEmpty()) {
            Text("История операций",fontSize=18.sp,fontWeight=FontWeight.Bold)
            if(state.history.isEmpty()) Text("Здесь появятся пополнения и завершённые зарядки",fontSize=13.sp,color=Muted)
            state.history.forEach {item->
                Surface(shape=RoundedCornerShape(16.dp),color=SurfaceGreen) {
                    Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {Text(item.title,Modifier.weight(1f),fontSize=13.sp,fontWeight=FontWeight.SemiBold);Text((if(item.amountMinor>0) "+" else "")+money(item.amountMinor),fontSize=12.sp,color=if(item.amountMinor>0)Lime else MaterialTheme.colorScheme.onSurface)}
                        Text(item.detail,fontSize=11.sp,color=Muted)
                        Text(SimpleDateFormat("dd.MM.yyyy · HH:mm",Ru).format(Date(item.timestamp)),fontSize=10.sp,color=Muted)
                    }
                }
            }
        }
    }
    selected?.let {method->
        var amount by rememberSaveable(method) {mutableStateOf("500")}
        var error by remember(method) {mutableStateOf<String?>(null)}
        val minor=amount.toLongOrNull()?.takeIf {it in 1..50000}?.times(100)
        ModalBottomSheet(onDismissRequest={selected=null},containerColor=SurfaceGreen) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal=20.dp).padding(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Text(method,fontSize=24.sp,fontWeight=FontWeight.Bold)
                Text("Демо-пополнение",fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                Text("Это проверка интерфейса. Карта и приложение банка не используются; средства будут учебными.",fontSize=12.sp,lineHeight=17.sp,color=Muted)
                OutlinedTextField(amount,{value->if(value.length<=5 && value.all {it.isDigit()})amount=value},modifier=Modifier.fillMaxWidth().testTag("topup-amount"),label={Text("Сумма в сомах")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),suffix={Text("KGS")})
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf(100,500,1000).forEach {value->FilterChip(selected=amount==value.toString(),onClick={amount=value.toString()},label={Text("$value")})}}
                if(state.session!=null) Text("Пополнение доступно после завершения сеанса",color=Amber,fontSize=12.sp)
                error?.let {Text(it,color=Amber,fontSize=12.sp)}
                Button(onClick={if(minor!=null && onTopUp(minor,method))selected=null else error="Пополнение сейчас недоступно"},enabled=minor!=null && state.session==null,modifier=Modifier.fillMaxWidth().height(52.dp).testTag("confirm-demo-topup"),shape=RoundedCornerShape(15.dp)) {Text("Пополнить демо-баланс")}
            }
        }
    }
    notice?.let {text->AlertDialog(onDismissRequest={notice=null},title={Text("QUBAT EV")},text={Text(text)},confirmButton={TextButton(onClick={notice=null}) {Text("Понятно")}})}
}

@Composable fun ProfileContent(state:AppState,onWallet:()->Unit) {
    val context=LocalContext.current
    val prefs=remember {context.getSharedPreferences("qubat_profile",0)}
    var name by remember {mutableStateOf(prefs.getString("name","").orEmpty())}
    var phone by remember {mutableStateOf(prefs.getString("phone","").orEmpty())}
    var car by remember {mutableStateOf(prefs.getString("car","").orEmpty())}
    var plate by remember {mutableStateOf(prefs.getString("plate","").orEmpty())}
    val created=remember {prefs.getLong("created",0L).takeIf {it>0} ?: System.currentTimeMillis().also {prefs.edit().putLong("created",it).apply()}}
    var editor by rememberSaveable {mutableStateOf<String?>(null)}
    var info by rememberSaveable {mutableStateOf<String?>(null)}
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text("Профиль",Modifier.weight(1f),fontSize=27.sp,fontWeight=FontWeight.Bold);ThemeToggle()}
        Surface(shape=RoundedCornerShape(24.dp),color=SurfaceGreen,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=.35f))) {
            Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(84.dp).background(Lime.copy(alpha=.13f),CircleShape),contentAlignment=Alignment.Center) {
                    if(name.isBlank()) Icon(Icons.Default.Person,null,tint=Lime,modifier=Modifier.size(48.dp)) else Text(name.split(" ").filter {it.isNotBlank()}.take(2).joinToString("") {it.take(1).uppercase()},color=Lime,fontSize=28.sp,fontWeight=FontWeight.Bold)
                }
                Text(name.ifBlank {"Демо-пользователь"},fontSize=22.sp,fontWeight=FontWeight.Bold,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                Text(phone.ifBlank {"Номер телефона не указан"},fontSize=13.sp,color=Muted)
                Text("Профиль создан: ${SimpleDateFormat("dd.MM.yyyy",Ru).format(Date(created))}",fontSize=10.sp,color=Muted)
                TextButton(onClick={editor="profile"},modifier=Modifier.testTag("edit-profile")) {Text("Изменить профиль")}
            }
        }
        Surface(shape=RoundedCornerShape(20.dp),color=SurfaceGreen) {
            Column(Modifier.fillMaxWidth().padding(17.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text("БАЛАНС",Modifier.weight(1f),fontSize=12.sp,color=Muted);FilledTonalButton(onClick=onWallet) {Text("+ Пополнить")}}
                Text(money((state.balanceMinor-(if(state.session!=null)state.progress.costMinor else 0)).coerceAtLeast(0)),fontSize=29.sp,fontWeight=FontWeight.Bold,color=Lime)
                Text("Учебные средства",fontSize=10.sp,color=Muted)
            }
        }
        AccountRow("Валюта","Кыргызский сом (KGS)","С") {info="Расчёты в приложении отображаются в кыргызских сомах. 1 сом = 100 тыйынов."}
        AccountRow("Безопасность","Данные и доступ к геопозиции","✓",Color(0xFF3A7EBF)) {info="Профиль и учебный баланс хранятся на этом устройстве. Номера банковских карт и пароли не запрашиваются. Геопозиция обновляется только при открытом приложении. Серверной авторизации пока нет."}
        AccountRow("Информация о машине",car.ifBlank {"Добавить автомобиль"}+(if(plate.isNotBlank()) " · $plate" else ""),"EV",Color(0xFFC27E24),"edit-car") {editor="car"}
        AccountRow("Мои полисы","Страхование автомобиля","П",Color(0xFF586FAF)) {info="Страховые полисы пока не подключены. Оформление и оплата ОСАГО в приложении недоступны."}
        AccountRow("Язык","Русский","RU") {info="Сейчас доступен русский язык интерфейса."}
        AccountRow("О приложении","QUBAT EV · ${BuildConfig.VERSION_NAME}","Q") {info="Демонстрационное приложение зарядки электромобилей. Станции и операции вымышленные. Банки, платёжные системы и реальные зарядные станции не подключены."}
        Text("Личный профиль сохраняется только на этом устройстве",fontSize=10.sp,color=Muted)
    }
    editor?.let {kind->
        var first by rememberSaveable(kind) {mutableStateOf(if(kind=="profile")name else car)}
        var second by rememberSaveable(kind) {mutableStateOf(if(kind=="profile")phone else plate)}
        AlertDialog(onDismissRequest={editor=null},title={Text(if(kind=="profile") "Личные данные" else "Мой автомобиль")},text={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(first,{if(it.length<=80)first=it},label={Text(if(kind=="profile") "Имя" else "Марка и модель")},modifier=Modifier.testTag("profile-field-first"),singleLine=true)
                OutlinedTextField(second,{if(it.length<=30)second=it},label={Text(if(kind=="profile") "Телефон" else "Госномер")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=if(kind=="profile")KeyboardType.Phone else KeyboardType.Text))
            }
        },confirmButton={TextButton(onClick={
            if(kind=="profile") {name=first.trim();phone=second.trim();prefs.edit().putString("name",name).putString("phone",phone).apply()}
            else {car=first.trim();plate=second.trim();prefs.edit().putString("car",car).putString("plate",plate).apply()}
            editor=null
        },modifier=Modifier.testTag("save-profile")) {Text("Сохранить")}},dismissButton={TextButton(onClick={editor=null}) {Text("Отмена")}})
    }
    info?.let {text->AlertDialog(onDismissRequest={info=null},title={Text("QUBAT EV")},text={Text(text)},confirmButton={TextButton(onClick={info=null}) {Text("Понятно")}})}
}
