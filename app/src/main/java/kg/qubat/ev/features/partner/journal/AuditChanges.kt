package kg.qubat.ev

/** Best-effort readable fields for known legacy snapshots; full records remain available. */
internal fun journalReadableChanges(event: NativeAudit): List<Triple<String, String, String>> {
    val kind = event.after.substringBefore('(')
    val fields = when (kind) {
        "NativeTariff" -> listOf("customerRate" to "Тариф за кВт·ч", "electricityRate" to "Стоимость электричества за кВт·ч", "rentMonthly" to "Аренда за месяц")
        "NativeStation" -> listOf("powerKw" to "Общая мощность, кВт", "apparatusCount" to "Количество аппаратов")
        "NativeNotificationSettings" -> listOf("offlineMinutes" to "Минут без данных", "longSessionMinutes" to "Длительность зарядки, минут", "lowPowerKw" to "Мощность ниже, кВт")
        "NativeMonthBill" -> listOf("electricityKgs" to "Электричество по квитанции, сом", "actualRentKgs" to "Аренда по документам, сом", "meterEnergyKwh" to "Показания счётчика, кВт·ч")
        "NativeExpense" -> listOf("amountKgs" to "Сумма, сом")
        else -> return emptyList()
    }
    if (event.before.isNotBlank() && event.before.substringBefore('(') != kind) return emptyList()
    return fields.mapNotNull { (field, label) ->
        val pattern = Regex("(?:\\(|, )" + Regex.escape(field) + "=(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)(?=[,)])")
        fun read(value: String): String? = pattern.findAll(value).toList().singleOrNull()?.groupValues?.get(1)
            ?.toBigDecimalOrNull()?.let(::networkNumber)
        val after = read(event.after) ?: return@mapNotNull null
        val before = if (event.before.isBlank()) "Нет записи" else read(event.before) ?: return@mapNotNull null
        if (before == after) null else Triple(label, before, after)
    }
}
