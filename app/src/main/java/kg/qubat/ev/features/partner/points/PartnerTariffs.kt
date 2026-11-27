@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.time.LocalDate

@Composable internal fun NetworkTariffEditor(repo: NativePartnerRepository, station: NativeStation, original: NativeTariff?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val base = original ?: NativeTariff(networkId(), station.id, repo.today)
    var customer by remember { mutableStateOf(base.customerRate.toPlainString()) }
    var premium by remember { mutableStateOf(base.premiumRate.toPlainString()) }
    var minute by remember { mutableStateOf(base.minuteRate.toPlainString()) }
    var idle by remember { mutableStateOf(base.idleMinuteRate.toPlainString()) }
    var reservation by remember { mutableStateOf(base.reservationMinutes.toString()) }
    var stop by remember { mutableStateOf(base.stopEnergyKwh?.toPlainString().orEmpty()) }
    var from by remember { mutableStateOf(maxOf(repo.today, base.effectiveFrom.plusDays(1)).toString()) }
    var to by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    NetworkEditor("Версия тарифа · ДЕМО", "network-tariff-editor", error, onDismiss, {
        runCatching {
            fun price(value: String): BigDecimal = requireNotNull(networkDecimal(value)?.takeIf { it >= BigDecimal.ZERO && it <= BigDecimal("100000") }) { "Цена должна быть от 0 до 100 000 сом." }
            val start = LocalDate.parse(from.trim())
            val end = to.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it.trim()) }
            require(end == null || end >= start) { "Дата окончания раньше начала." }
            require(reason.isNotBlank()) { "Укажите причину изменения тарифа." }
            val minutes = requireNotNull(reservation.toIntOrNull()?.takeIf { it in 0..1440 }) { "Бронь: от 0 до 1440 минут." }
            val stopEnergy = stop.takeIf { it.isNotBlank() }?.let { requireNotNull(networkDecimal(it)?.takeIf { n -> n > BigDecimal.ZERO }) { "Порог энергии должен быть больше нуля." } }
            repo.updateTariff(base.copy(id = networkId(), effectiveFrom = start, effectiveTo = end, customerRate = price(customer), premiumRate = price(premium), minuteRate = price(minute), idleMinuteRate = price(idle), reservationMinutes = minutes, stopEnergyKwh = stopEnergy), reason.trim())
        }.onSuccess { onSaved() }.onFailure { error = it.message ?: "Проверьте даты" }
    }) {
        NetworkField("Базовый, сом/кВт·ч", customer, "network-tariff-customer", true) { customer = it }
        NetworkField("Премиум, сом/кВт·ч", premium, "network-tariff-premium", true) { premium = it }
        NetworkField("Тариф за минуту, сом", minute, "network-tariff-minute", true) { minute = it }
        NetworkField("Простой за минуту, сом", idle, "network-tariff-idle", true) { idle = it }
        NetworkField("Бронь, минут", reservation, "network-tariff-reservation", true) { reservation = it }
        NetworkField("Порог окончания, кВт·ч (необязательно)", stop, "network-tariff-stop", true) { stop = it }
        CalendarDateField("Действует с", from, "network-tariff-from") { from = it }
        CalendarDateField("Действует до (необязательно)", to, "network-tariff-to",optional=true) { to = it }
        NetworkCard("Предварительный просмотр") {
            AppText("Базовый: ${networkNumber(base.customerRate)} → $customer сом/кВт·ч", fontSize = 13.sp)
            AppText("Премиум: ${networkNumber(base.premiumRate)} → $premium сом/кВт·ч", fontSize = 13.sp)
            NetworkNote("Новая версия с $from. Старые зарядки не пересчитываются.")
        }
        NetworkField("Причина изменения *", reason, "network-tariff-reason") { reason = it }
    }
}
