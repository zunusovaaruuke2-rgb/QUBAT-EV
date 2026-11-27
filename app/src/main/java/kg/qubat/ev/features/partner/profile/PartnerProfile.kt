@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun NativeProfile(repo: NativePartnerRepository, revision: Int, onSwitch: () -> Unit,
    onSignOut: () -> Unit, onNavigate: (String) -> Unit) {
    val account = repo.account
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("qubat_partner_profile_demo", 0) }
    var name by rememberSaveable(account.id) { mutableStateOf(prefs.getString("${account.id}.name", account.name).orEmpty().ifBlank { account.name }) }
    var contact by rememberSaveable(account.id) { mutableStateOf(prefs.getString("${account.id}.contact", "").orEmpty()) }
    var edit by remember { mutableStateOf(false) }
    var draftName by remember { mutableStateOf(name) }
    var draftContact by remember { mutableStateOf(contact) }
    var languages by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val points = remember(repo, revision) { repo.stations() }
    val toggleTheme = LocalToggleTheme.current
    val night = LocalNight.current
    val language = LocalAppLanguage.current
    val links = buildList {
        if(runCatching{DriverDiscountService(repo,DriverDiscountStore(context)).canOpen()}.getOrDefault(false))add(ProfileShortcut("discounts","Скидки водителям","Для выбранных точек",Icons.Default.Person))
        add(ProfileShortcut("points", "Мои точки", "Адреса и аппараты", Icons.Default.LocationOn))
        if (repo.can(NativePermission.REPORT)) add(ProfileShortcut("reports", "Отчёты", "Результаты за период", Icons.Default.List))
        if (repo.can(NativePermission.FINANCE)) add(ProfileShortcut("finance", "Финансы", "Доходы и расходы", Icons.Default.AccountBox))
        if (repo.can(NativePermission.MANAGE_MEMBERS)) add(ProfileShortcut("access", "Сотрудники", "Кому открыт доступ", Icons.Default.Person))
    }
    LazyColumn(Modifier.fillMaxSize().testTag("network-profile-scroll"), contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item("welcome") { ProfileWelcome(name, account.role.label) }
        item("counts") { ProfileNetworkCounts(points) }
        item("network-title") { AppText("Ваша сеть", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
        item("shortcuts") { ProfileShortcuts(links, onNavigate) }
        item("settings-title") { AppText("Настройки", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
        item("settings") {
            DepthSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 8.dp)) {
                    ProfileSettingRow("Имя и контакты", icon = Icons.Default.Person, tag = "network-profile-edit") {
                        draftName = name; draftContact = contact; edit = true
                    }
                    HorizontalDivider()
                    ProfileSettingRow("Язык", language.nativeName, Icons.Default.List, "network-profile-language") { languages = true }
                    HorizontalDivider()
                    ProfileSettingRow("Оформление", if (night) "Тёмное" else "Светлое", Icons.Default.Settings, "network-profile-theme") { toggleTheme() }
                    HorizontalDivider()
                    ProfileSettingRow("Помощь по кабинету", icon = Icons.Default.Info, tag = "network-profile-help") { help = true }
                }
            }
        }
        if (saved) item("saved") { AppText("Сохранено на устройстве", fontSize = 13.sp, color = Lime) }
        item("switch-demo") { PressTextButton(onClick = onSwitch, modifier = Modifier.testTag("partner-switch-demo")) { AppText("Сменить демо-аккаунт", fontSize = 13.sp) } }
        item("sign-out") { PressTextButton(onClick = onSignOut, modifier = Modifier.testTag("partner-sign-out")) {
            AppIcon(Icons.Default.ExitToApp, null, tint = Muted)
            Spacer(Modifier.width(8.dp)); AppText("Выйти из кабинета", color = Muted, fontSize = 14.sp)
        } }
        item("demo") { AppText("Демоверсия · учебные данные", fontSize = 12.sp, color = Muted) }
    }
    if (edit) NetworkEditor("Имя и контакты", "network-profile-editor", null, { edit = false }, {
        if (draftName.isNotBlank()) {
            name = draftName.trim(); contact = draftContact.trim()
            prefs.edit().putString("${account.id}.name", name).putString("${account.id}.contact", contact).apply()
            saved = true; edit = false
        }
    }) {
        NativeField("Отображаемое имя", draftName, "network-profile-name") { draftName = it.take(80) }
        NativeField("Телефон или email", draftContact, "network-profile-contact") { draftContact = it.take(100) }
        if (draftName.isBlank()) NetworkNote("Укажите имя.", error = true)
        NetworkNote("Имя в профиле не меняет вашу роль и доступные точки.")
        NetworkNote("Контакт хранится на этом устройстве. Это не подтверждение телефона или почты.")
    }
    if (languages) LanguagePicker { languages = false }
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { AppText("Помощь по кабинету") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppText("Мои точки", fontWeight = FontWeight.SemiBold); AppText("Здесь адреса, аппараты и разъёмы вашей сети.", fontSize = 14.sp)
            AppText("Отчёты", fontWeight = FontWeight.SemiBold); AppText("Выберите период и точку, чтобы посмотреть результаты зарядок.", fontSize = 14.sp)
            AppText("Финансы", fontWeight = FontWeight.SemiBold); AppText("Доходы, расходы и прибыль. Выплаты владельцу показаны отдельно.", fontSize = 14.sp)
            AppText("Сотрудники", fontWeight = FontWeight.SemiBold); AppText("Кто видит ваши точки и что может делать.", fontSize = 14.sp)
            AppText("Демонстрационный режим", fontWeight = FontWeight.SemiBold)
            AppText("Станции и суммы учебные. Реальные платежи и управление оборудованием не подключены.", fontSize = 14.sp)
            AppText("Изменения хранятся только на этом устройстве.", fontSize = 14.sp)
        }
    }, confirmButton = { PressTextButton({ help = false }) { AppText("Понятно") } })
}
