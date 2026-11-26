package kg.qubat.ev

import java.time.Instant
import java.time.format.DateTimeFormatter

internal enum class JournalAlertKind(val title: String) {
    OFFLINE("Нет связи"), FAULT("Ошибка аппарата"), LONG("Долгая зарядка"), LOW("Низкая мощность")
}
internal data class JournalAlert(
    val station: NativeStation, val kind: JournalAlertKind, val message: String,
    val referenceAt: Instant, val ruleId: String
) {
    val id: String get() = station.id + "-" + kind.name
}
internal val journalTime = DateTimeFormatter.ofPattern("HH:mm").withZone(PARTNER_ZONE)

/** Current conditions only. referenceAt is evidence time, never a fabricated notification time. */
internal fun journalAlerts(stations: List<NativeStation>, settings: List<NativeNotificationSettings>,
    sessions: List<NativeSession>, now: Instant, fresh: (NativeStation) -> Boolean): List<JournalAlert> =
    stations.flatMap { point ->
        val alerts = linkedMapOf<JournalAlertKind, JournalAlert>()
        val active = sessions.filter { it.stationId == point.id && it.status == NativeSessionStatus.ACTIVE }
        settings.filter { it.enabled && point.id in it.stationIds && it.organizationId == point.organizationId }.forEach { rule ->
            fun add(kind: JournalAlertKind, message: String, at: Instant = point.lastSeen) {
                alerts.putIfAbsent(kind, JournalAlert(point, kind, message, at, rule.id))
            }
            if (point.errorReason != null || point.ports.any { it.status == NativePortStatus.FAULT })
                add(JournalAlertKind.FAULT, "В данных точки отмечена ошибка аппарата или разъёма.")
            if (point.lastSeen.isBefore(now.minusSeconds(rule.offlineMinutes.toLong() * 60)))
                add(JournalAlertKind.OFFLINE, "Кабинет давно не получал данные от этой точки.")
            active.firstOrNull { it.startedAt.isBefore(now.minusSeconds(rule.longSessionMinutes.toLong() * 60)) }?.let {
                add(JournalAlertKind.LONG, "Зарядка длится дольше времени, указанного в настройках.", it.startedAt)
            }
            if (active.isNotEmpty() && fresh(point) && point.instantaneousPowerKw < rule.lowPowerKw)
                add(JournalAlertKind.LOW, "Мощность активной зарядки ниже значения, указанного в настройках.")
        }
        alerts.values
    }.sortedByDescending { it.referenceAt }

internal fun journalActionTitle(action: String): String = when (action) {
    "Версия тарифа" -> "Изменён тариф"
    "Локальное приглашение / права" -> "Изменён доступ сотрудника"
    "Изменение бренда" -> "Изменены название или цвет"
    "Назначение названия сети" -> "Изменены точки сети"
    "Настройка уведомлений" -> "Изменены уведомления"
    "Фактический счёт" -> "Сохранена квитанция"
    "Расход / выплата" -> "Сохранён расход или выплата"
    "Экспорт Excel" -> "Выгружен отчёт"
    "Добавление точки" -> "Добавлена точка"
    "Изменение точки" -> "Изменена точка"
    else -> action
}
