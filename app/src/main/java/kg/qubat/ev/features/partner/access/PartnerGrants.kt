@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.math.BigDecimal
import java.time.LocalDate

internal fun networkGrantLabel(kind: NativeGrantKind): String = when (kind) { NativeGrantKind.PERCENT -> "Скидка в процентах"; NativeGrantKind.FIXED -> "Скидка в сомах"; NativeGrantKind.FREE -> "Бесплатная зарядка" }

@Composable internal fun NetworkGrantEditor(repo: NativePartnerRepository, initial: NativeGrantKind, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val organizations = repo.organizations()
    var organizationId by remember { mutableStateOf(organizations.firstOrNull()?.id.orEmpty()) }
    val stations = repo.stations().filter { it.organizationId == organizationId }
    var kind by remember { mutableStateOf(initial) }
    var stationIds by remember { mutableStateOf(emptySet<String>()) }
    var customer by remember { mutableStateOf("") }
    var value by remember { mutableStateOf(if (initial == NativeGrantKind.FREE) "0" else "10") }
    var starts by remember { mutableStateOf(repo.today.toString()) }
    var ends by remember { mutableStateOf(repo.today.plusMonths(1).toString()) }
    var limit by remember { mutableStateOf("5") }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    NetworkEditor(if (initial == NativeGrantKind.FREE) "Разрешение на зарядку · ДЕМО" else "Новая скидка · ДЕМО", "network-grant-editor", error, onDismiss, {
        runCatching {
            require(customer.isNotBlank() && reason.isNotBlank()) { "Укажите ID клиента и основание одобрения." }
            require(stationIds.isNotEmpty()) { "Выберите точки действия разрешения." }
            val from = LocalDate.parse(starts.trim()); val to = LocalDate.parse(ends.trim())
            require(to >= from) { "Окончание действия раньше начала." }
            val uses = requireNotNull(limit.toIntOrNull()?.takeIf { it in 1..100000 }) { "Лимит: от 1 до 100 000 использований." }
            val discount = if (kind == NativeGrantKind.FREE) BigDecimal.ZERO else requireNotNull(networkDecimal(value)?.takeIf { it > BigDecimal.ZERO }) { "Введите положительную скидку." }
            require(kind != NativeGrantKind.PERCENT || discount <= BigDecimal("100")) { "Скидка не может превышать 100 %." }
            repo.saveGrant(NativeGrant(networkId(), organizationId, stationIds, customer.trim(), kind, discount, from, to, uses, approvedBy = repo.account.id, reason = reason.trim()), reason.trim())
        }.onSuccess { onSaved() }.onFailure { error = it.message ?: "Проверьте даты" }
    }) {
        if (organizations.size > 1) NetworkChoice("Организация", organizationId, organizations.map { it.id }, { id -> organizations.first { it.id == id }.name }, "network-grant-organization") { organizationId = it; stationIds = emptySet() }
        NetworkField("ID клиента *", customer, "network-grant-customer") { customer = it }
        if (initial != NativeGrantKind.FREE) {
            NetworkChoice("Тип скидки", kind, listOf(NativeGrantKind.PERCENT, NativeGrantKind.FIXED), ::networkGrantLabel, "network-grant-kind") { kind = it }
            NetworkField(if (kind == NativeGrantKind.PERCENT) "Скидка, %" else "Скидка, сом", value, "network-grant-value", true) { value = it }
        }
        NetworkStationSelection(stations, stationIds, "network-grant-station") { stationIds = it }
        CalendarDateField("С даты", starts, "network-grant-from") { starts = it }
        CalendarDateField("По дату", ends, "network-grant-to") { ends = it }
        NetworkField("Лимит использований *", limit, "network-grant-limit", true) { limit = it }
        NetworkNote("Одобряет: ${repo.account.name}")
        NetworkField("Основание одобрения *", reason, "network-grant-reason") { reason = it }
    }
}
