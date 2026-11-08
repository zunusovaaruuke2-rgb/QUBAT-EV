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

@Composable fun NativePartnerJournal(repo: NativePartnerRepository, revision: Int, onChanged: () -> Unit) {
    var editingSettings by remember { mutableStateOf<NativeNotificationSettings?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf(if (repo.can(NativePermission.NOTIFICATIONS)) "Уведомления" else "Журнал действий") }
    val events = remember(repo, revision) { if (repo.can(NativePermission.MANAGE_MEMBERS)) repo.audit().sortedByDescending { it.at } else emptyList() }
    val settings = remember(repo, revision) { if (repo.can(NativePermission.NOTIFICATIONS)) repo.notificationSettings() else emptyList() }
    val stations = remember(repo, revision) { repo.stations() }
    val activeSessions = remember(repo, revision) { repo.monitoring().sessions.filter { it.status == NativeSessionStatus.ACTIVE } }
    LazyColumn(Modifier.fillMaxSize().testTag("network-journal"), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item("journal-tabs") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                listOf("Уведомления", "Журнал действий").forEachIndexed { index, title -> DepthChip(section == title, { section = title }, Modifier.testTag("network-journal-tab-$index")) { AppText(title) } }
            }
        }
        if (section == "Уведомления") {
            item("notification-header") { NetworkCard("Правила уведомлений") {
                NetworkNote("ДЕМО · уведомления формируются в кабинете, внешние сообщения не отправляются.")
                if (!repo.can(NativePermission.NOTIFICATIONS)) NetworkNote("Настройки и тревоги ограничены вашей ролью.")
                if (repo.can(NativePermission.NOTIFICATIONS) && settings.isEmpty()) DepthOutlinedButton({
                    repo.organizations().firstOrNull()?.let { organization ->
                        editingSettings = NativeNotificationSettings(networkId(), organization.id, stations.filter { it.organizationId == organization.id }.map { it.id }.toSet())
                    }
                }, Modifier.fillMaxWidth().testTag("network-notifications-edit")) { AppText("Создать правило") }
            } }
            items(settings, key = { "notification-settings-${it.id}" }) { rule -> NetworkCard("Пороги · ${repo.organizations().firstOrNull { it.id == rule.organizationId }?.name ?: rule.organizationId}") {
                NetworkNote("Канал: ${rule.channel} · ${if (rule.enabled) "Включены" else "Выключены"}")
                NetworkNote("Нет связи: ${rule.offlineMinutes} мин · длительная зарядка: ${rule.longSessionMinutes} мин · низкая мощность: ${networkNumber(rule.lowPowerKw)} кВт")
                DepthOutlinedButton({ editingSettings = rule }, Modifier.fillMaxWidth().testTag(if (settings.first().id == rule.id) "network-notifications-edit" else "network-notifications-edit-${rule.id}")) { AppText("Настроить пороги") }
            } }
            val now = Instant.now()
            val alerts = stations.mapNotNull { station ->
                val rules = settings.filter { it.enabled && station.id in it.stationIds }
                val messages = mutableSetOf<String>()
                rules.forEach { rule ->
                    if (station.errorReason != null || station.ports.any { it.status == NativePortStatus.FAULT }) messages.add("Неисправность")
                    if (station.lastSeen.isBefore(now.minusSeconds(rule.offlineMinutes.toLong() * 60))) messages.add("Потеря связи")
                    val active = activeSessions.filter { it.stationId == station.id }
                    if (active.any { it.startedAt.isBefore(now.minusSeconds(rule.longSessionMinutes.toLong() * 60)) }) messages.add("Длительная зарядка: более ${rule.longSessionMinutes} мин")
                    if (active.isNotEmpty() && !repo.isStale(station) && station.instantaneousPowerKw < rule.lowPowerKw) messages.add("Низкая мощность: ${networkNumber(station.instantaneousPowerKw)} кВт")
                }
                if (messages.isEmpty()) null else station to messages.toList()
            }
            if (alerts.isEmpty()) item("alerts-empty") { NetworkCard("Новых тревог нет") { NetworkNote("Учитываются назначенные точки и настроенный порог потери связи.") } }
            items(alerts, key = { "alert-${it.first.id}" }) { (station, messages) -> NetworkCard(station.title, Modifier.testTag("network-alert-${station.id}")) {
                messages.forEach { AppText(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                station.errorReason?.let { NetworkNote(it, true) }
                NetworkNote("Сигнал: ${networkTime.format(station.lastSeen)} · источник ${station.source}")
            } }
        } else {
            item("audit-search") { NetworkField("Поиск по действию, автору и причине", query, "network-audit-search") { query = it } }
            val filtered = events.filter { event -> query.isBlank() || listOf(event.action, event.authorId, event.reason, event.before, event.after).any { it.contains(query, true) } }
            if (filtered.isEmpty()) item("audit-empty") { NetworkCard("Журнал действий") { NetworkNote(if (repo.can(NativePermission.MANAGE_MEMBERS)) "Изменения точек, тарифов, финансов и доступов появятся здесь." else "Внутренний аудит доступен владельцу и администратору. Ваша роль сохраняет доступ к назначенным точкам.") } }
            items(filtered, key = { "audit-${it.id}" }) { NetworkAuditCard(it) }
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
    NetworkEditor("Пороги уведомлений", "network-notifications-editor", error, onDismiss, {
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
        NetworkNote("Канал: в кабинете. Внешние каналы появятся после согласованной интеграции.")
        NetworkField("Потеря связи, минут", offline, "network-notifications-offline", true) { offline = it }
        NetworkField("Длительная зарядка, минут", longSession, "network-notifications-long", true) { longSession = it }
        NetworkField("Низкая мощность, кВт", lowPower, "network-notifications-power", true) { lowPower = it }
        NetworkNote("Правило применяется к выбранным точкам организации.")
        NetworkStationSelection(repo.stations().filter { it.organizationId == original.organizationId }, stationIds, "network-notifications-station") { stationIds = it }
        NetworkField("Причина изменения *", reason, "network-notifications-reason") { reason = it }
    }
}
