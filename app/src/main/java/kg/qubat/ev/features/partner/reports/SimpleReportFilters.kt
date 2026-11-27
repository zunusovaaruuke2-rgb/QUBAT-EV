@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/** Simple report controls are separate from the finance screen's advanced filters. */
@Composable internal fun simpleReportFilters(repo: NativePartnerRepository, includePreviousMonth: Boolean = false): NativeFilterUi {
    var from by rememberSaveable(repo.account.id) { mutableStateOf(repo.today.minusDays(6).toString()) }
    var to by rememberSaveable(repo.account.id) { mutableStateOf(repo.today.toString()) }
    var stationId by rememberSaveable(repo.account.id) { mutableStateOf("") }
    var edit by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val stations = repo.stations(includeArchived = true)
    val period = PartnerPeriod(LocalDate.parse(from), LocalDate.parse(to))
    val filter = NativeReportFilter(period, stationId = stationId.takeIf { it.isNotBlank() })
    if (edit) NativeDateRange(from, to, { edit = false }) { a, b -> from = a; to = b; edit = false }
    if (picker) AlertDialog(
        onDismissRequest = { picker = false },
        title = { AppText("Выберите точку") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PressOutlinedTextField(query, { query = it.take(100) }, singleLine = true,
                    label = { AppText("Поиск точки") },
                    modifier = Modifier.fillMaxWidth().testTag("network-report-point-search"))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item {
                        PressTextButton(onClick = { stationId = ""; picker = false },
                            modifier = Modifier.fillMaxWidth().testTag("network-select-station-")) { AppText("Все точки") }
                    }
                    items(stations.filter { query.isBlank() || listOf(it.title, it.city, it.address).any { value -> value.contains(query.trim(), ignoreCase = true) } }, key = { it.id }) { point ->
                        PressTextButton(onClick = { stationId = point.id; picker = false },
                            modifier = Modifier.fillMaxWidth().testTag("network-select-station-${point.id}")) {
                            Column(Modifier.fillMaxWidth()) {
                                AppText(point.title)
                                AppText(point.city, fontSize = 12.sp, color = Muted)
                                if (point.archived) AppText("В архиве", fontSize = 12.sp, color = Muted)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { PressTextButton(onClick = { picker = false }) { AppText("Закрыть") } }
    )
    val content: @Composable () -> Unit = {
        NativePanel("За какой период?") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Сегодня" to "today", "7 дней" to "week", "Месяц" to "month").forEach { (label, id) ->
                    val start = when (id) { "today" -> repo.today; "week" -> repo.today.minusDays(6); else -> repo.today.withDayOfMonth(1) }
                    DepthChip(period.from == start && period.to == repo.today,
                        { from = start.toString(); to = repo.today.toString() },
                        modifier = Modifier.weight(1f).testTag("network-period-$id"), label = { AppText(label, fontSize = 14.sp) })
                }
            }
            DepthOutlinedButton(onClick = { edit = true },
                modifier = Modifier.fillMaxWidth().testTag("network-period-custom")) {
                AppText("${nativeDate.format(period.from)} — ${nativeDate.format(period.to)}",
                    modifier = Modifier.weight(1f).testTag("network-period-label"), fontSize = 14.sp)
                AppText("▾")
            }
            AppText("Даты по времени Бишкека", fontSize = 12.sp, color = Muted)
            if (includePreviousMonth) PressTextButton(onClick = {
                val end = repo.today.withDayOfMonth(1).minusDays(1)
                from = end.withDayOfMonth(1).toString(); to = end.toString()
            }, modifier = Modifier.testTag("network-period-previous")) { AppText("Прошлый месяц", fontSize = 13.sp) }
            DepthOutlinedButton(onClick = { query = ""; picker = true },
                modifier = Modifier.fillMaxWidth().testTag("network-station-filter")) {
                // A deleted/revoked selection is never silently presented as all locations.
                AppText(if (stationId.isBlank()) "Все точки" else stations.firstOrNull { it.id == stationId }?.title ?: "Точка недоступна",
                    modifier = Modifier.weight(1f), fontSize = 15.sp)
                AppText("▾")
            }
        }
    }
    return NativeFilterUi(filter, content)
}
