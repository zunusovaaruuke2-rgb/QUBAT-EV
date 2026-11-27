@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant

@Composable internal fun JournalEventRow(time: Instant, title: String, warning: Boolean, tag: String, onOpen: () -> Unit) {
    val translate = LocalTranslate.current
    val foreground = MaterialTheme.colorScheme.onSurface
    DepthSurface(onClick = onOpen, modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            AppText(journalTime.format(time), fontSize = 13.sp, color = Muted)
            AppText("●", color = if (warning) Amber else Lime, fontSize = 10.sp)
            // Truncate only the preview; the accessible name and opened details retain the full title.
            Text(translate(title), Modifier.weight(1f).appDescription(title), color = foreground,
                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            AppText("›", fontSize = 19.sp)
        }
    }
}

@Composable internal fun JournalAlertDetails(alert: JournalAlert, checkedAt: Instant,
    rule: NativeNotificationSettings?, onOpenPoint: () -> Unit) {
    var technical by rememberSaveable(alert.id) { mutableStateOf(false) }
    NativePanel(alert.kind.title, "network-alert-details") {
        AppText(alert.station.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        AppText(alert.message, fontSize = 14.sp)
        AppText(if (alert.kind == JournalAlertKind.LONG) "Начало зарядки" else "Последние данные",
            fontSize = 12.sp, color = Muted)
        AppText(networkTime.format(alert.referenceAt), fontSize = 14.sp)
        AppText("Проверено в кабинете", fontSize = 12.sp, color = Muted)
        AppText(networkTime.format(checkedAt), fontSize = 14.sp)
        rule?.let {
            AppText("Условие уведомления", fontSize = 12.sp, color = Muted)
            when (alert.kind) {
                JournalAlertKind.OFFLINE -> { AppText("Минут без новых данных"); AppText(it.offlineMinutes.toString()) }
                JournalAlertKind.LONG -> { AppText("Длительность зарядки, минут"); AppText(it.longSessionMinutes.toString()) }
                JournalAlertKind.LOW -> { AppText("Мощность ниже, кВт"); AppText(networkNumber(it.lowPowerKw)) }
                JournalAlertKind.FAULT -> AppText("Ошибка в состоянии аппарата или разъёма")
            }
        }
    }
    NativePanel("Что проверить") {
        when (alert.kind) {
            JournalAlertKind.OFFLINE -> {
                AppText("Проверьте питание аппарата и интернет на точке.", fontSize = 14.sp)
                AppText("Если новые данные не появились, свяжитесь с техником.", fontSize = 14.sp)
            }
            JournalAlertKind.FAULT -> AppText("Откройте точку, посмотрите состояние разъёмов и передайте описание ошибки технику.", fontSize = 14.sp)
            JournalAlertKind.LONG -> AppText("Откройте точку и проверьте активные зарядки и выбранный порог времени.", fontSize = 14.sp)
            JournalAlertKind.LOW -> AppText("Откройте точку и проверьте мощность активных зарядок. Значение может зависеть от автомобиля и распределения мощности.", fontSize = 14.sp)
        }
    }
    DepthOutlinedButton(onOpenPoint, Modifier.fillMaxWidth().testTag("network-journal-open-point")) { AppText("Открыть точку") }
    PressTextButton({ technical = !technical }, Modifier.testTag("network-alert-technical")) { AppText("Технические данные") }
    if (technical) NativePanel("Данные точки") {
        AppText(alert.station.id, fontSize = 13.sp)
        AppText(alert.station.source, fontSize = 13.sp)
        AppText(networkTime.format(alert.station.lastSeen), fontSize = 13.sp)
        alert.station.errorReason?.let { AppText(it, fontSize = 13.sp) }
        alert.station.ports.forEach { port ->
            AppText("№${port.number}", fontSize = 13.sp)
            AppText(port.status.label, fontSize = 13.sp)
        }
    }
}

@Composable internal fun JournalAuditDetails(event: NativeAudit, author: String, stations: List<NativeStation>) {
    var technical by rememberSaveable(event.id) { mutableStateOf(false) }
    NativePanel("Что изменилось", "network-audit-details") {
        AppText(journalActionTitle(event.action), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        AppText("Кто изменил", fontSize = 12.sp, color = Muted)
        AppText(author, fontSize = 14.sp)
        AppText("Когда", fontSize = 12.sp, color = Muted)
        AppText(networkTime.format(event.at), fontSize = 14.sp)
        AppText("Затронутые точки", fontSize = 12.sp, color = Muted)
        val titles = stations.filter { it.id in event.stationIds }.map { it.title }
        if (titles.isEmpty()) AppText(if(event.stationIds.isEmpty()) "Настройки организации" else "Точка недоступна", fontSize = 14.sp)
        else titles.forEach { AppText(it, fontSize = 14.sp) }
        AppText("Причина изменения", fontSize = 12.sp, color = Muted)
        AppText(event.reason.ifBlank { "Причина не указана" }, fontSize = 14.sp)
    }
    val changes = journalReadableChanges(event)
    if (changes.isNotEmpty()) NativePanel("Было и стало") {
        changes.forEach { (label, before, after) ->
            AppText(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            AppText("Было", fontSize = 12.sp, color = Muted); AppText(before, fontSize = 14.sp)
            AppText("Стало", fontSize = 12.sp, color = Muted); AppText(after, fontSize = 14.sp)
        }
    }
    PressTextButton({ technical = !technical }, Modifier.testTag("network-audit-technical")) { AppText("Полная запись изменения") }
    if (technical) NetworkAuditCard(event)
}
