@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package kg.qubat.ev

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.time.Instant
import androidx.activity.compose.BackHandler

@Composable fun NativePartnerJournal(repo: NativePartnerRepository, revision: Int, onChanged: () -> Unit, onOpenPoint: (String) -> Unit = {}) {
    var editingSettings by remember { mutableStateOf<NativeNotificationSettings?>(null) }
    var query by rememberSaveable(repo.account.id) { mutableStateOf("") }
    var section by rememberSaveable(repo.account.id) { mutableStateOf(if (repo.can(NativePermission.NOTIFICATIONS)) "Уведомления" else "Журнал действий") }
    var selectedAlert by rememberSaveable(repo.account.id) { mutableStateOf("") }
    var selectedAudit by rememberSaveable(repo.account.id) { mutableStateOf("") }
    var settingsOpen by rememberSaveable(repo.account.id) { mutableStateOf(false) }
    val events = remember(repo, revision) { if (repo.can(NativePermission.MANAGE_MEMBERS)) repo.audit() else emptyList() }
    val settings = remember(repo, revision) { if (repo.can(NativePermission.NOTIFICATIONS)) repo.notificationSettings() else emptyList() }
    val allStations = remember(repo, revision) { repo.stations(includeArchived = true) }
    val stations = allStations.filter { !it.archived }
    val active = remember(repo, revision) { if (repo.can(NativePermission.NOTIFICATIONS)) repo.monitoring().sessions.filter { it.status == NativeSessionStatus.ACTIVE } else emptyList() }
    val checkedAt = remember(repo, revision) { Instant.now() }
    val alerts = remember(repo, revision, checkedAt) { journalAlerts(stations, settings, active, checkedAt) { !repo.isStale(it, checkedAt) } }
    val members = remember(repo, revision) { if (repo.can(NativePermission.MANAGE_MEMBERS)) repo.members() else emptyList() }
    fun author(event: NativeAudit): String = members.firstOrNull { it.id == event.authorId }?.name
        ?: NativePartnerRepository.accounts.firstOrNull { it.id == event.authorId }?.name ?: "Автор не указан"
    val translate = LocalTranslate.current
    fun back() { selectedAlert = ""; selectedAudit = ""; settingsOpen = false }
    BackHandler(selectedAlert.isNotBlank() || selectedAudit.isNotBlank() || settingsOpen) { back() }
    LazyColumn(Modifier.fillMaxSize().testTag("network-journal"), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selectedAlert.isNotBlank() || selectedAudit.isNotBlank()) {
            item("detail-back") { PressTextButton({ back() }, Modifier.testTag("network-journal-back")) { AppText("К списку событий") } }
            val alert = alerts.firstOrNull { it.id == selectedAlert }
            val event = events.firstOrNull { it.id == selectedAudit }
            if (alert != null) item("alert-detail") { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                JournalAlertDetails(alert, checkedAt, settings.firstOrNull { it.id == alert.ruleId }) { onOpenPoint(alert.station.id) }
            } }
            else if (event != null) item("audit-detail") { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { JournalAuditDetails(event, author(event), allStations) } }
            else item("unavailable") { NativePanel("Событие больше не активно") { AppText("Вернитесь к списку, чтобы увидеть актуальные данные.", fontSize = 14.sp) } }
        } else if (settingsOpen) {
            item("settings-back") { PressTextButton({ back() }, Modifier.testTag("network-journal-back")) { AppText("К списку событий") } }
            item("settings-title") { AppText("Когда показывать уведомления", fontSize = 18.sp) }
            item("settings-note") { NetworkNote("Уведомления появляются внутри кабинета. Письма и SMS не отправляются.") }
            if (settings.isEmpty() && repo.can(NativePermission.NOTIFICATIONS)) item("settings-create") {
                DepthOutlinedButton({
                    repo.organizations().firstOrNull()?.let { organization ->
                        editingSettings = NativeNotificationSettings(networkId(), organization.id, stations.filter { it.organizationId == organization.id }.map { it.id }.toSet())
                    }
                }, Modifier.fillMaxWidth().testTag("network-notifications-edit")) { AppText("Добавить правило") }
            }
            items(settings, key = { "notification-settings-${it.id}" }) { rule ->
                val organization = repo.organizations().firstOrNull { it.id == rule.organizationId }?.name ?: "Настройки уведомлений"
                DepthOutlinedButton({ editingSettings = rule }, Modifier.fillMaxWidth().testTag(if (settings.first().id == rule.id) "network-notifications-edit" else "network-notifications-edit-${rule.id}")) {
                    AppText(organization, Modifier.weight(1f), fontSize = 14.sp)
                    AppText(if (rule.enabled) "Включены" else "Выключены", fontSize = 12.sp)
                    AppText(" ›")
                }
            }
        } else {
            item("journal-tabs") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    listOf("Уведомления", "Журнал действий").forEachIndexed { index, title ->
                        DepthChip(section == title, { section = title }, Modifier.testTag("network-journal-tab-$index")) {
                            AppText(if (index == 0) "Уведомления" else "Действия", fontSize = 14.sp)
                        }
                    }
                }
            }
            if (section == "Уведомления") {
                if (repo.can(NativePermission.NOTIFICATIONS)) {
                    item("notification-settings") { DepthOutlinedButton({ settingsOpen = true }, Modifier.fillMaxWidth().testTag("network-notifications-open")) { AppText("Настроить уведомления", Modifier.weight(1f)); AppText("›") } }
                    item("notification-note") { NetworkNote("Время в строке — время данных или начала зарядки. Это текущие состояния точек.") }
                    if (alerts.isEmpty()) item("alerts-empty") { NetworkNote("Активных уведомлений нет.") }
                    alerts.groupBy{it.referenceAt.atZone(PARTNER_ZONE).toLocalDate()}.forEach{(date,rows)->
                        item("alert-date-$date"){AppText(nativeDate.format(date),fontSize=12.sp,color=Muted)}
                        items(rows, key = { "alert-${it.id}" }) { alert ->
                            JournalEventRow(alert.referenceAt, alert.station.title + " · " + translate(alert.kind.title), true,
                                "network-alert-${alert.id}") { selectedAlert = alert.id }
                        }
                    }
                } else item("notification-denied") { NetworkNote("Уведомления недоступны вашей роли.") }
            } else {
                item("audit-search") { NetworkField("Найти действие или сотрудника", query, "network-audit-search") { query = it } }
                val filtered = events.filter { event -> query.isBlank() || listOf(event.action, journalActionTitle(event.action), author(event), event.authorId, event.reason, event.before, event.after).any { it.contains(query.trim(), true) } }
                if (filtered.isEmpty()) item("audit-empty") { NetworkNote(if (repo.can(NativePermission.MANAGE_MEMBERS)) "Действий пока нет." else "Журнал действий доступен владельцу и администратору.") }
                filtered.groupBy{it.at.atZone(PARTNER_ZONE).toLocalDate()}.forEach{(date,rows)->
                    item("audit-date-$date"){AppText(nativeDate.format(date),fontSize=12.sp,color=Muted)}
                    items(rows, key = { "audit-${it.id}" }) { event ->
                        JournalEventRow(event.at, author(event) + " · " + translate(journalActionTitle(event.action)), false,
                            "network-audit-${event.id}") { selectedAudit = event.id }
                    }
                }
            }
            item("journal-help") { NetworkNote("Нажмите на строку, чтобы узнать подробности.") }
            item("journal-demo") { NetworkNote("ДЕМО · события внутри кабинета") }
        }
    }
    editingSettings?.let { rule -> NetworkNotificationEditor(repo, rule, { editingSettings = null }) { editingSettings = null; onChanged() } }
}

@Composable private fun NetworkNotificationEditor(repo: NativePartnerRepository, original: NativeNotificationSettings, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var offline by remember { mutableStateOf(original.offlineMinutes.toString()) }
    var longSession by remember { mutableStateOf(original.longSessionMinutes.toString()) }
    var lowPower by remember { mutableStateOf(original.lowPowerKw.toPlainString()) }
    var enabled by remember { mutableStateOf(original.enabled) }
    var stationIds by remember { mutableStateOf(original.stationIds) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    NetworkEditor("Настроить уведомления", "network-notifications-editor", error, onDismiss, {
        runCatching {
            val offlineMinutes = requireNotNull(offline.toIntOrNull()?.takeIf { it in 1..1440 }) { "Порог потери связи: от 1 до 1440 минут." }
            val longMinutes = requireNotNull(longSession.toIntOrNull()?.takeIf { it in 1..10080 }) { "Порог зарядки: от 1 до 10080 минут." }
            val lowKw = requireNotNull(networkDecimal(lowPower)?.takeIf { it > BigDecimal.ZERO }) { "Порог мощности должен быть больше нуля." }
            require(stationIds.isNotEmpty()) { "Выберите хотя бы одну точку." }
            require(reason.isNotBlank()) { "Укажите причину изменения правил." }
            repo.saveNotificationSettings(original.copy(stationIds = stationIds, channel = "В кабинете", offlineMinutes = offlineMinutes, longSessionMinutes = longMinutes, lowPowerKw = lowKw, enabled = enabled), reason.trim())
        }.onSuccess { onSaved() }.onFailure { error = it.message ?: "Не удалось сохранить пороги" }
    }) {
        DepthChip(enabled, { enabled = !enabled }, Modifier.testTag("network-notifications-enabled")) { AppText("Уведомления включены") }
        NetworkNote("Уведомления показываются только внутри кабинета.")
        NetworkField("Нет новых данных дольше, минут", offline, "network-notifications-offline", true) { offline = it }
        NetworkField("Зарядка дольше, минут", longSession, "network-notifications-long", true) { longSession = it }
        NetworkField("Мощность ниже, кВт", lowPower, "network-notifications-power", true) { lowPower = it }
        NetworkNote("Правило применяется к выбранным точкам организации.")
        NetworkStationSelection(repo.stations().filter { it.organizationId == original.organizationId }, stationIds, "network-notifications-station") { stationIds = it }
        NetworkField("Причина изменения *", reason, "network-notifications-reason") { reason = it }
    }
}
