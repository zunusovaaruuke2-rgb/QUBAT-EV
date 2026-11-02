package kg.qubat.ev

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

private val networkTime = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(PARTNER_ZONE)
private fun networkNumber(value: BigDecimal) = value.stripTrailingZeros().toPlainString().replace('.', ',')
private fun networkDecimal(value: String) = value.trim().replace(',', '.').toBigDecimalOrNull()
private fun networkId() = "demo-" + UUID.randomUUID().toString().take(12)

@Composable private fun NetworkCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    DepthSurface(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            AppText(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable private fun NetworkNote(text: String, error: Boolean = false, tag: String = "") {
    AppText(text, Modifier.testTag(tag), fontSize = 12.sp, color = if (error) MaterialTheme.colorScheme.error else Muted)
}

@Composable private fun NetworkField(label: String, value: String, tag: String, numeric: Boolean = false, onChange: (String) -> Unit) {
    PressOutlinedTextField(value, { onChange(it.take(300)) }, Modifier.fillMaxWidth().testTag(tag), singleLine = true,
        label = { AppText(label) }, keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text))
}

@Composable private fun <T> NetworkChoice(label: String, value: T, options: List<T>, title: (T) -> String, tag: String, onChange: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        DepthOutlinedButton({ expanded = true }, Modifier.fillMaxWidth().testTag(tag)) { AppText("$label: ${title(value)}", Modifier.weight(1f)); AppText("▾") }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEachIndexed { index, option ->
                PressTextButton({ expanded = false; onChange(option) }, Modifier.fillMaxWidth().testTag("$tag-option-$index")) { AppText(title(option)) }
            }
        }
    }
}

@Composable private fun NetworkEditor(title: String, tag: String, error: String?, onDismiss: () -> Unit, onSave: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DepthSurface(Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth().imePadding().testTag(tag)) {
            Column(Modifier.padding(18.dp).heightIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppText(title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
                error?.let { NetworkNote(it, true, "$tag-error") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DepthOutlinedButton(onDismiss, Modifier.weight(1f).testTag("$tag-cancel")) { AppText("Отмена") }
                    DepthButton(onSave, Modifier.weight(1f).testTag("$tag-save")) { AppText("Сохранить") }
                }
            }
        }
    }
}

private fun networkStatus(repo: NativePartnerRepository, station: NativeStation): String = when {
    station.archived -> "Архив"
    repo.isStale(station) -> "Данные не обновлялись"
    station.ports.any { it.status == NativePortStatus.FAULT } -> "Ошибка"
    station.ports.any { it.status == NativePortStatus.BUSY } -> "Занят"
    station.ports.all { it.status == NativePortStatus.UNAVAILABLE } -> "Нет связи"
    else -> "В сети"
}

private fun networkPortStatus(status: NativePortStatus): String = when (status) {
    NativePortStatus.FREE -> "Свободен"; NativePortStatus.BUSY -> "Занят"
    NativePortStatus.FAULT -> "Неисправен"; NativePortStatus.UNAVAILABLE -> "Недоступен"
}

@Composable fun NativePartnerPoints(repo: NativePartnerRepository, revision: Int, onChanged: () -> Unit, initialStationId: String? = null) {
    var selected by rememberSaveable(initialStationId) { mutableStateOf(initialStationId) }
    var query by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("Все статусы") }
    var group by rememberSaveable { mutableStateOf("Все группы") }
    var owner by rememberSaveable { mutableStateOf("Все владельцы") }
    var brand by rememberSaveable { mutableStateOf("Все бренды") }
    var archived by rememberSaveable { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val stations = remember(repo, revision, archived) { repo.stations(includeArchived = archived) }
    val brands = remember(repo, revision) { repo.brands() }
    val station = selected?.let { id -> stations.firstOrNull { it.id == id } }
    BackHandler(selected != null) { selected = null }
    if (station != null) {
        NetworkPointDetails(repo, station, revision, { selected = null }, onChanged)
    } else {
        val filtered = stations.filter { item ->
            (query.isBlank() || listOf(item.title, item.id, item.serialNumber, item.city, item.address).any { it.contains(query, true) }) &&
                (status == "Все статусы" || networkStatus(repo, item) == status) &&
                (group == "Все группы" || item.groupId == group) && (owner == "Все владельцы" || item.ownerId == owner) &&
                (brand == "Все бренды" || item.brandId == brand)
        }
        LazyColumn(Modifier.fillMaxSize().testTag("network-points"), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            item("point-search") {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    NetworkField("Название, ID, серийный номер, город", query, "network-point-search") { query = it }
                    NetworkChoice("Статус", status, listOf("Все статусы", "В сети", "Нет связи", "Занят", "Ошибка", "Данные не обновлялись", "Архив"), { it }, "network-point-status") { status = it }
                    NetworkChoice("Группа", group, listOf("Все группы") + stations.map { it.groupId }.distinct(), { it }, "network-point-group") { group = it }
                    NetworkChoice("Владелец", owner, listOf("Все владельцы") + stations.map { it.ownerId }.distinct(), { it }, "network-point-owner") { owner = it }
                    NetworkChoice("Бренд", brand, listOf("Все бренды") + brands.map { it.id }, { id -> brands.firstOrNull { it.id == id }?.name ?: id }, "network-point-brand") { brand = it }
                    DepthChip(archived, { archived = !archived }, Modifier.testTag("network-point-show-archive")) { AppText("Показывать архив") }
                    if (repo.can(NativePermission.EDIT_STATION)) DepthButton({ creating = true }, Modifier.fillMaxWidth().testTag("network-point-create")) { AppText("Добавить точку") }
                    NetworkNote("Найдено: ${filtered.size} · аппаратов: ${filtered.sumOf { it.apparatusCount }} · разъёмов: ${filtered.sumOf { it.ports.size }}")
                }
            }
            if (filtered.isEmpty()) item("point-empty") { NetworkCard("Точки не найдены") { NetworkNote("Измените поиск или фильтры.") } }
            items(filtered, key = { "point-${it.id}" }) { item ->
                DepthSurface({ selected = item.id }, Modifier.fillMaxWidth().testTag("network-point-${item.id}")) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        AppText(item.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        NetworkNote(item.address)
                        AppText(networkStatus(repo, item), color = if (networkStatus(repo, item) in listOf("В сети", "Занят")) Lime else MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        AppText("${networkNumber(item.powerKw)} кВт · ${item.apparatusCount} аппарат · ${item.ports.size} разъёма", fontSize = 13.sp)
                        NetworkNote("${item.city} · ${item.groupId} · ${brands.firstOrNull { it.id == item.brandId }?.name ?: item.brandId}")
                        NetworkNote("Сигнал: ${networkTime.format(item.lastSeen)} · ${item.source}")
                    }
                }
            }
        }
    }
    if (creating) NetworkStationEditor(repo, null, { creating = false }) { creating = false; onChanged() }
}

@Composable private fun NetworkPointDetails(repo: NativePartnerRepository, station: NativeStation, revision: Int, onBack: () -> Unit, onChanged: () -> Unit) {
    var tab by rememberSaveable(station.id) { mutableStateOf("Информация") }
    var editing by remember { mutableStateOf(false) }
    var tariffEditor by remember { mutableStateOf(false) }
    var archive by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val tariffs = remember(repo, revision, station.id) { if (repo.can(NativePermission.EDIT_TARIFF, station.id)) repo.tariffs(station.id) else emptyList() }
    val publicTariffs = remember(repo, revision, station.id) { repo.publicTariffs(station.id) }
    val history = remember(repo, revision, station.id) { repo.stationHistory(station.id) }
    LazyColumn(Modifier.fillMaxSize().testTag("network-point-details"), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item("detail-header") {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                PressTextButton(onBack, Modifier.testTag("network-point-back")) { AppText("‹ Все точки") }
                AppText(station.title, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                AppText(networkStatus(repo, station), color = if (networkStatus(repo, station) in listOf("В сети", "Занят")) Lime else MaterialTheme.colorScheme.error)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Информация", "Параметры", "История изменений").forEachIndexed { index, text ->
                        DepthChip(tab == text, { tab = text }, Modifier.testTag("network-point-tab-${listOf("information", "parameters", "history")[index]}")) { AppText(text) }
                    }
                }
                error?.let { NetworkNote(it, true) }
            }
        }
        when (tab) {
            "Информация" -> {
                item("detail-info") { NetworkCard("Паспорт точки") {
                    listOf("ID: ${station.id}", "Владелец: ${station.ownerId}", "Адрес: ${station.address}", "Город: ${station.city}", "Группа: ${station.groupId}", "Бренд: ${station.brandId}", "Модель: ${station.model}", "Серийный номер: ${station.serialNumber}", "OCPP ID: ${station.ocppId}", "Прошивка: ${station.firmware}", "Координаты: ${networkNumber(station.latitude)}, ${networkNumber(station.longitude)}", "Режим: ${station.operatingHours}").forEach { AppText(it, fontSize = 13.sp) }
                    NetworkNote("Последний сигнал: ${networkTime.format(station.lastSeen)} · источник ${station.source}")
                    station.errorReason?.let { NetworkNote("Причина: $it", true) }
                } }
                item("detail-equipment") { NetworkCard("${station.apparatusCount} аппарат · ${station.ports.size} независимых разъёма") {
                    AppText("Номинальная мощность: ${networkNumber(station.powerKw)} кВт", fontSize = 13.sp)
                    NetworkNote("Мощность аппарата и число разъёмов учитываются отдельно.")
                } }
                items(station.ports, key = { "port-${it.id}" }) { port -> NetworkCard("Порт ${port.number} · ${port.type}") {
                    val stale = repo.isPortStale(station.id, port.id)
                    AppText(if (stale) "Данные не обновлялись" else networkPortStatus(port.status), fontSize = 13.sp, color = if (stale) MaterialTheme.colorScheme.error else Muted)
                    NetworkNote("До ${networkNumber(port.maxPowerKw)} кВт / ${networkNumber(port.maxCurrentA)} А · ${networkTime.format(port.updatedAt)}")
                } }
                if (repo.can(NativePermission.EDIT_STATION, station.id) && !station.archived) item("detail-actions") {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        DepthButton({ editing = true }, Modifier.fillMaxWidth().testTag("network-point-edit")) { AppText("Редактировать точку") }
                        DepthOutlinedButton({ archive = true }, Modifier.fillMaxWidth().testTag("network-point-archive")) { AppText("Архивировать точку") }
                    }
                }
            }
            "Параметры" -> {
                item("tariff-header") { NetworkCard("Тарифы клиента") {
                    NetworkNote("Версии действуют с указанной даты. Завершённые сессии сохраняют исходную цену.")
                    if (!repo.can(NativePermission.EDIT_TARIFF, station.id)) NetworkNote("Вам доступны клиентские тарифы. Изменение и внутренние финансовые ставки ограничены ролью.")
                    if (repo.can(NativePermission.EDIT_TARIFF, station.id) && !station.archived) DepthButton({ tariffEditor = true }, Modifier.fillMaxWidth().testTag("network-tariff-edit")) { AppText("Новая версия тарифа") }
                } }
                items(publicTariffs, key = { "tariff-${it.id}" }) { tariff -> NetworkCard("С ${tariff.effectiveFrom} · до ${tariff.effectiveTo ?: "изменения"}") {
                    AppText("Базовый: ${networkNumber(tariff.customerRate)} сом/кВт·ч", fontWeight = FontWeight.Bold)
                    AppText("Премиум: ${networkNumber(tariff.premiumRate)} сом/кВт·ч", fontSize = 13.sp)
                    NetworkNote("Минута: ${networkNumber(tariff.minuteRate)} сом · простой: ${networkNumber(tariff.idleMinuteRate)} сом/мин")
                    NetworkNote("Бронь: ${tariff.reservationMinutes} мин · порог окончания: ${tariff.stopEnergyKwh?.let(::networkNumber) ?: "не задан"} кВт·ч")
                } }
            }
            else -> {
                if (history.isEmpty()) item("history-empty") { NetworkCard("История изменений") { NetworkNote("Изменений этой точки пока нет.") } }
                items(history, key = { "history-${it.id}" }) { event -> NetworkAuditCard(event) }
            }
        }
    }
    if (editing) NetworkStationEditor(repo, station, { editing = false }) { editing = false; onChanged() }
    if (tariffEditor) NetworkTariffEditor(repo, station, tariffs.firstOrNull(), { tariffEditor = false }) { tariffEditor = false; onChanged() }
    if (archive) AlertDialog(onDismissRequest = { archive = false }, title = { AppText("Архивировать точку?") }, text = { AppText("Точка исчезнет из активного реестра. История сессий и расчёты сохранятся.") }, confirmButton = { PressTextButton({
        runCatching { repo.archiveStation(station.id, "Архивация из карточки точки") }.onSuccess { archive = false; onChanged(); onBack() }.onFailure { error = it.message ?: "Не удалось архивировать"; archive = false }
    }, Modifier.testTag("network-point-archive-confirm")) { AppText("Архивировать") } }, dismissButton = { PressTextButton({ archive = false }) { AppText("Отмена") } })
}

private data class NetworkPortDraft(val number: String, val type: String, val power: String, val current: String, val status: NativePortStatus, val id: String)

@Composable private fun NetworkStationEditor(repo: NativePartnerRepository, original: NativeStation?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val organizations = remember(repo) { repo.organizations() }
    val organization = organizations.firstOrNull { it.id == original?.organizationId } ?: organizations.firstOrNull()
    var organizationId by remember { mutableStateOf(organization?.id.orEmpty()) }
    var title by remember { mutableStateOf(original?.title.orEmpty()) }
    var address by remember { mutableStateOf(original?.address.orEmpty()) }
    var city by remember { mutableStateOf(original?.city ?: "Бишкек") }
    var group by remember { mutableStateOf(original?.groupId ?: "Основная") }
    var location by remember { mutableStateOf(original?.locationId ?: "Основная") }
    var brand by remember { mutableStateOf(original?.brandId ?: repo.brands().firstOrNull()?.id.orEmpty()) }
    var model by remember { mutableStateOf(original?.model.orEmpty()) }
    var serial by remember { mutableStateOf(original?.serialNumber.orEmpty()) }
    var ocpp by remember { mutableStateOf(original?.ocppId.orEmpty()) }
    var firmware by remember { mutableStateOf(original?.firmware ?: "demo") }
    var latitude by remember { mutableStateOf(original?.latitude?.toPlainString() ?: "42.8746") }
    var longitude by remember { mutableStateOf(original?.longitude?.toPlainString() ?: "74.5698") }
    var hours by remember { mutableStateOf(original?.operatingHours ?: "24/7") }
    var power by remember { mutableStateOf(original?.powerKw?.toPlainString() ?: "80") }
    var apparatus by remember { mutableStateOf(original?.apparatusCount?.toString() ?: "1") }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val ports = remember { mutableStateListOf<NetworkPortDraft>().apply {
        addAll(original?.ports?.map { NetworkPortDraft(it.number.toString(), it.type, it.maxPowerKw.toPlainString(), it.maxCurrentA.toPlainString(), it.status, it.id) }
            ?: listOf(NetworkPortDraft("1", "GB/T DC", "80", "200", NativePortStatus.UNAVAILABLE, networkId()), NetworkPortDraft("2", "GB/T DC", "80", "200", NativePortStatus.UNAVAILABLE, networkId())))
    } }
    NetworkEditor(if (original == null) "Новая точка · ДЕМО" else "Редактирование точки", "network-point-editor", error, onDismiss, {
        runCatching {
            require(title.isNotBlank() && address.isNotBlank() && city.isNotBlank() && group.isNotBlank() && model.isNotBlank() && serial.isNotBlank() && ocpp.isNotBlank() && hours.isNotBlank()) { "Заполните название, адрес, город, группу, модель, серийный номер, OCPP ID и режим работы." }
            require(reason.isNotBlank()) { "Укажите причину изменения." }
            val lat = requireNotNull(networkDecimal(latitude)) { "Некорректная широта." }
            val lon = requireNotNull(networkDecimal(longitude)) { "Некорректная долгота." }
            require(lat >= BigDecimal("-90") && lat <= BigDecimal("90") && lon >= BigDecimal("-180") && lon <= BigDecimal("180")) { "Координаты вне допустимого диапазона." }
            val kw = requireNotNull(networkDecimal(power)?.takeIf { it > BigDecimal.ZERO }) { "Мощность должна быть больше нуля." }
            val count = requireNotNull(apparatus.toIntOrNull()?.takeIf { it in 1..100 }) { "Число аппаратов: от 1 до 100." }
            require(ports.isNotEmpty() && ports.map { it.number }.distinct().size == ports.size) { "Добавьте разъём. Номера портов должны быть уникальны." }
            val validatedPorts = ports.map { p ->
                val number = requireNotNull(p.number.toIntOrNull()?.takeIf { it > 0 }) { "Номер порта должен быть положительным." }
                val portPower = requireNotNull(networkDecimal(p.power)?.takeIf { it > BigDecimal.ZERO }) { "Проверьте мощность порта." }
                val current = requireNotNull(networkDecimal(p.current)?.takeIf { it > BigDecimal.ZERO }) { "Проверьте ток порта." }
                require(p.type.isNotBlank()) { "Укажите тип каждого разъёма." }
                NativePort(p.id, number, p.type.trim(), portPower, current, p.status, original?.ports?.firstOrNull { it.id == p.id }?.updatedAt ?: Instant.now())
            }
            val org = requireNotNull(organizations.firstOrNull { it.id == organizationId }) { "Выберите организацию." }
            val base = original ?: NativeStation(networkId(), org.id, org.ownerId, title.trim(), address.trim())
            val value = base.copy(title = title.trim(), address = address.trim(), city = city.trim(), groupId = group.trim(), locationId = location.trim(), brandId = brand, model = model.trim(), serialNumber = serial.trim(), ocppId = ocpp.trim(), firmware = firmware.trim(), latitude = lat, longitude = lon, operatingHours = hours.trim(), powerKw = kw, apparatusCount = count, ports = validatedPorts)
            if (original == null) repo.createStation(value, reason.trim()) else repo.updateStation(value, reason.trim())
        }.onSuccess { onSaved() }.onFailure { error = it.message ?: "Не удалось сохранить точку" }
    }) {
        NetworkNote("Обязательны паспортные данные и причина изменения. Команды оборудованию не отправляются.")
        if (original == null && organizations.size > 1) NetworkChoice("Организация", organizationId, organizations.map { it.id }, { id -> organizations.first { it.id == id }.name }, "network-edit-organization") { organizationId = it; brand = repo.brands().firstOrNull { b -> b.organizationId == it }?.id.orEmpty() }
        NetworkField("Название *", title, "network-edit-title") { title = it }
        NetworkField("Адрес *", address, "network-edit-address") { address = it }
        NetworkField("Город *", city, "network-edit-city") { city = it }
        NetworkField("Группа *", group, "network-edit-group") { group = it }
        NetworkField("Локация *", location, "network-edit-location") { location = it }
        val brandOptions = repo.brands().filter { !it.archived && it.organizationId == organizationId }
        if (brandOptions.isNotEmpty()) NetworkChoice("Бренд", brand, brandOptions.map { it.id }, { id -> brandOptions.firstOrNull { it.id == id }?.name ?: id }, "network-edit-brand") { brand = it }
        NetworkField("Модель *", model, "network-edit-model") { model = it }
        NetworkField("Серийный номер *", serial, "network-edit-serial") { serial = it }
        NetworkField("OCPP ID *", ocpp, "network-edit-ocpp") { ocpp = it }
        NetworkField("Прошивка", firmware, "network-edit-firmware") { firmware = it }
        NetworkField("Широта *", latitude, "network-edit-latitude", true) { latitude = it }
        NetworkField("Долгота *", longitude, "network-edit-longitude", true) { longitude = it }
        NetworkField("Режим работы *", hours, "network-edit-hours") { hours = it }
        NetworkField("Номинальная мощность, кВт *", power, "network-edit-power", true) { power = it }
        NetworkField("Физических аппаратов *", apparatus, "network-edit-apparatus", true) { apparatus = it }
        ports.forEachIndexed { index, p ->
            HorizontalDivider()
            AppText("Разъём ${index + 1}", fontWeight = FontWeight.Bold)
            NetworkField("Номер порта", p.number, "network-edit-port-$index-number", true) { ports[index] = p.copy(number = it) }
            NetworkChoice("Тип", p.type, listOf("GB/T DC", "CCS2", "Type 2", "CHAdeMO", "GB/T AC"), { it }, "network-edit-port-$index-type") { ports[index] = p.copy(type = it) }
            NetworkField("Максимум кВт", p.power, "network-edit-port-$index-power", true) { ports[index] = p.copy(power = it) }
            NetworkField("Максимум А", p.current, "network-edit-port-$index-current", true) { ports[index] = p.copy(current = it) }
            NetworkNote("Статус поступает от источника данных: ${networkPortStatus(p.status)}")
            if (ports.size > 1) PressTextButton({ ports.removeAt(index) }, Modifier.testTag("network-edit-port-$index-remove")) { AppText("Убрать разъём") }
        }
        DepthOutlinedButton({ ports.add(NetworkPortDraft((ports.size + 1).toString(), "GB/T DC", power, "200", NativePortStatus.UNAVAILABLE, networkId())) }, Modifier.fillMaxWidth().testTag("network-edit-port-add")) { AppText("Добавить разъём") }
        NetworkField("Причина изменения *", reason, "network-edit-reason") { reason = it }
    }
}

@Composable private fun NetworkTariffEditor(repo: NativePartnerRepository, station: NativeStation, original: NativeTariff?, onDismiss: () -> Unit, onSaved: () -> Unit) {
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
        }.onSuccess { onSaved() }.onFailure { error = it.message ?: "Проверьте даты: ГГГГ-ММ-ДД" }
    }) {
        NetworkField("Базовый, сом/кВт·ч", customer, "network-tariff-customer", true) { customer = it }
        NetworkField("Премиум, сом/кВт·ч", premium, "network-tariff-premium", true) { premium = it }
        NetworkField("Тариф за минуту, сом", minute, "network-tariff-minute", true) { minute = it }
        NetworkField("Простой за минуту, сом", idle, "network-tariff-idle", true) { idle = it }
        NetworkField("Бронь, минут", reservation, "network-tariff-reservation", true) { reservation = it }
        NetworkField("Порог окончания, кВт·ч (необязательно)", stop, "network-tariff-stop", true) { stop = it }
        NetworkField("Действует с · ГГГГ-ММ-ДД", from, "network-tariff-from") { from = it }
        NetworkField("До · ГГГГ-ММ-ДД (необязательно)", to, "network-tariff-to") { to = it }
        NetworkCard("Предварительный просмотр") {
            AppText("Базовый: ${networkNumber(base.customerRate)} → $customer сом/кВт·ч", fontSize = 13.sp)
            AppText("Премиум: ${networkNumber(base.premiumRate)} → $premium сом/кВт·ч", fontSize = 13.sp)
            NetworkNote("Новая версия с $from. Старые зарядки не пересчитываются.")
        }
        NetworkField("Причина изменения *", reason, "network-tariff-reason") { reason = it }
    }
}

@Composable private fun NetworkAuditCard(event: NativeAudit) {
    NetworkCard(event.action, Modifier.testTag("network-audit-${event.id}")) {
        NetworkNote("${networkTime.format(event.at)} · ${event.authorId}")
        AppText("Было: ${event.before.ifBlank { "—" }}", fontSize = 12.sp)
        AppText("Стало: ${event.after.ifBlank { "—" }}", fontSize = 12.sp)
        NetworkNote("Причина: ${event.reason}")
    }
}

@Composable fun NativePartnerAccess(repo: NativePartnerRepository, revision: Int, onChanged: () -> Unit) {
    var inviting by remember { mutableStateOf(false) }
    var editingMember by remember { mutableStateOf<NativeMember?>(null) }
    var grantKind by remember { mutableStateOf<NativeGrantKind?>(null) }
    var revoke by remember { mutableStateOf<NativeMember?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val members = remember(repo, revision) { if (repo.can(NativePermission.MANAGE_MEMBERS)) repo.members() else emptyList() }
    val grants = remember(repo, revision) { if (repo.can(NativePermission.MANAGE_MEMBERS)) repo.grants() else emptyList() }
    val stations = remember(repo, revision) { repo.stations(includeArchived = true) }
    LazyColumn(Modifier.fillMaxSize().testTag("network-access"), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item("access-header") { NetworkCard("Сотрудники и партнёры") {
            NetworkNote("Доступ ограничен организацией и назначенными точками. Отзыв действует сразу в локальной демо-модели.")
            if (!repo.can(NativePermission.MANAGE_MEMBERS)) NetworkNote("Список сотрудников и разрешений ограничен вашей ролью.")
            if (repo.can(NativePermission.MANAGE_MEMBERS)) DepthButton({ inviting = true }, Modifier.fillMaxWidth().testTag("network-access-invite")) { AppText("Добавить приглашение") }
            NetworkField("Поиск сотрудника", query, "network-access-search") { query = it }
            error?.let { NetworkNote(it, true, "network-access-error") }
        } }
        val filtered = members.filter { query.isBlank() || it.name.contains(query, true) || it.contact.contains(query, true) }
        if (filtered.isEmpty()) item("members-empty") { NetworkNote("Сотрудники не найдены.") }
        items(filtered, key = { "member-${it.id}" }) { member -> NetworkCard(member.name, Modifier.testTag("network-member-${member.id}")) {
            AppText("${member.role.label} · ${if (member.revoked) "Доступ отозван" else "Локальное приглашение"}", fontSize = 13.sp)
            NetworkNote(member.contact)
            NetworkNote("С ${member.startsOn} · до ${member.endsOn ?: "без срока"}")
            NetworkNote("Точки: ${member.stationIds.joinToString { id -> stations.firstOrNull { it.id == id }?.title ?: id }}")
            NetworkNote("Права: ${member.rights.joinToString { networkPermissionLabel(it) }.ifBlank { "По роли" }}")
            if (!member.revoked && repo.can(NativePermission.MANAGE_MEMBERS)) {
                DepthOutlinedButton({ editingMember = member }, Modifier.fillMaxWidth().testTag("network-member-edit-${member.id}")) { AppText("Изменить роль и назначения") }
                DepthOutlinedButton({ revoke = member }, Modifier.fillMaxWidth().testTag("network-member-revoke-${member.id}")) { AppText("Отозвать доступ") }
            }
        } }
        item("grant-header") { NetworkCard("Скидки и бесплатные зарядки") {
            NetworkNote("Отдельные разрешения с датами, лимитами, одобрением и обязательным аудитом.")
            if (repo.can(NativePermission.DISCOUNT)) DepthOutlinedButton({ grantKind = NativeGrantKind.PERCENT }, Modifier.fillMaxWidth().testTag("network-discount-create")) { AppText("Добавить скидку") }
            if (repo.can(NativePermission.FREE_CHARGE)) DepthOutlinedButton({ grantKind = NativeGrantKind.FREE }, Modifier.fillMaxWidth().testTag("network-free-grant-create")) { AppText("Разрешить бесплатную зарядку") }
        } }
        if (grants.isEmpty()) item("grants-empty") { NetworkNote("Скидок и разрешений пока нет.") }
        items(grants, key = { "grant-${it.id}" }) { grant -> NetworkCard("${networkGrantLabel(grant.kind)} · ${grant.customerId}", Modifier.testTag("network-grant-${grant.id}")) {
            if (grant.kind != NativeGrantKind.FREE) AppText("${networkNumber(grant.value)} ${if (grant.kind == NativeGrantKind.PERCENT) "%" else "сом"}", fontWeight = FontWeight.Bold)
            NetworkNote("С ${grant.startsOn} по ${grant.endsOn} · использовано ${grant.used} из ${grant.usageLimit}")
            NetworkNote("Точки: ${grant.stationIds.joinToString { id -> stations.firstOrNull { it.id == id }?.title ?: id }}")
            NetworkNote("Одобрил: ${grant.approvedBy} · ${grant.reason}")
        } }
    }
    if (inviting || editingMember != null) NetworkMemberEditor(repo, editingMember, { inviting = false; editingMember = null }) { inviting = false; editingMember = null; onChanged() }
    grantKind?.let { kind -> NetworkGrantEditor(repo, kind, { grantKind = null }) { grantKind = null; onChanged() } }
    revoke?.let { member -> AlertDialog(onDismissRequest = { revoke = null }, title = { AppText("Отозвать доступ?") }, text = { AppText("${member.name} сразу потеряет назначенные права в демо-кабинете.") }, confirmButton = { PressTextButton({
        runCatching { repo.revokeMember(member.id, "Отзыв доступа владельцем из раздела доступов") }.onSuccess { revoke = null; onChanged() }.onFailure { error = it.message ?: "Не удалось отозвать доступ"; revoke = null }
    }, Modifier.testTag("network-member-revoke-confirm")) { AppText("Отозвать") } }, dismissButton = { PressTextButton({ revoke = null }) { AppText("Отмена") } }) }
}

private fun networkPermissionLabel(permission: NativePermission): String = when (permission) {
    NativePermission.VIEW -> "Просмотр"; NativePermission.REPORT -> "Отчёты"; NativePermission.EXPORT -> "Экспорт"
    NativePermission.FINANCE -> "Просмотр финансов"; NativePermission.EDIT_STATION -> "Редактирование точек"
    NativePermission.EDIT_TARIFF -> "Изменение тарифа"; NativePermission.EDIT_FINANCE -> "Изменение финансов"
    NativePermission.MANAGE_MEMBERS -> "Управление сотрудниками"; NativePermission.DISCOUNT -> "Скидки"
    NativePermission.FREE_CHARGE -> "Бесплатные зарядки"; NativePermission.MANAGE_BRANDS -> "Управление брендами"
    NativePermission.NOTIFICATIONS -> "Уведомления"
}

@Composable private fun NetworkStationSelection(stations: List<NativeStation>, selected: Set<String>, tag: String, onChanged: (Set<String>) -> Unit) {
    AppText("Назначенные точки *", fontWeight = FontWeight.Bold, fontSize = 14.sp)
    stations.forEach { station ->
        DepthChip(station.id in selected, { onChanged(if (station.id in selected) selected - station.id else selected + station.id) }, Modifier.fillMaxWidth().testTag("$tag-${station.id}")) { AppText(station.title) }
    }
}

@Composable private fun NetworkMemberEditor(repo: NativePartnerRepository, original: NativeMember?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val organizations = repo.organizations()
    var organizationId by remember { mutableStateOf(original?.organizationId ?: organizations.firstOrNull()?.id.orEmpty()) }
    val stations = repo.stations().filter { it.organizationId == organizationId }
    var name by remember { mutableStateOf(original?.name.orEmpty()) }
    var contact by remember { mutableStateOf(original?.contact.orEmpty()) }
    var role by remember { mutableStateOf(original?.role ?: PartnerRole.OBSERVER) }
    var stationIds by remember { mutableStateOf(original?.stationIds ?: emptySet()) }
    var starts by remember { mutableStateOf(original?.startsOn?.toString() ?: repo.today.toString()) }
    var ends by remember { mutableStateOf(if (original != null) original.endsOn?.toString().orEmpty() else repo.today.plusYears(1).toString()) }
    var rights by remember { mutableStateOf(original?.rights?.takeIf { it.isNotEmpty() } ?: setOf(NativePermission.VIEW, NativePermission.REPORT)) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    NetworkEditor(if (original == null) "Приглашение · ДЕМО" else "Роль и назначения", "network-member-editor", error, onDismiss, {
        runCatching {
            require(name.isNotBlank()) { "Укажите имя сотрудника." }
            require(contact.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) || contact.replace(Regex("[ +()−-]"), "").matches(Regex("\\d{9,15}"))) { "Укажите корректный телефон или электронную почту." }
            require(stationIds.isNotEmpty()) { "Назначьте хотя бы одну точку." }
            val from = LocalDate.parse(starts.trim()); val to = ends.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it.trim()) }
            require(to == null || to >= from) { "Окончание доступа раньше начала." }
            require(reason.isNotBlank()) { "Укажите причину назначения прав." }
            repo.inviteMember(NativeMember(original?.id ?: networkId(), organizationId, name.trim(), contact.trim(), role, stationIds, from, to, rights, invitedBy = repo.account.id), reason.trim())
        }.onSuccess { onSaved() }.onFailure { error = it.message ?: "Проверьте даты: ГГГГ-ММ-ДД" }
    }) {
        NetworkNote("Приглашение сохраняется локально. Письма и SMS не отправляются; подтверждение контакта и двухфакторный вход требуют серверной интеграции.")
        if (original == null && organizations.size > 1) NetworkChoice("Организация", organizationId, organizations.map { it.id }, { id -> organizations.first { it.id == id }.name }, "network-member-organization") { organizationId = it; stationIds = emptySet() }
        NetworkField("Имя *", name, "network-member-name") { name = it }
        NetworkField("Телефон или email *", contact, "network-member-contact") { contact = it }
        NetworkChoice("Роль", role, listOf(PartnerRole.EMPLOYEE, PartnerRole.ACCOUNTANT, PartnerRole.OBSERVER), { it.label }, "network-member-role") {
            role = it
            rights = when (it) {
                PartnerRole.ACCOUNTANT -> setOf(NativePermission.VIEW, NativePermission.REPORT, NativePermission.EXPORT, NativePermission.FINANCE, NativePermission.EDIT_FINANCE)
                PartnerRole.EMPLOYEE -> setOf(NativePermission.VIEW)
                else -> setOf(NativePermission.VIEW, NativePermission.REPORT)
            }
        }
        NetworkStationSelection(stations, stationIds, "network-member-station") { stationIds = it }
        NetworkField("Доступ с · ГГГГ-ММ-ДД", starts, "network-member-from") { starts = it }
        NetworkField("До · ГГГГ-ММ-ДД", ends, "network-member-to") { ends = it }
        AppText("Отдельные разрешения", fontWeight = FontWeight.Bold)
        val rolePermissions = when (role) {
            PartnerRole.ACCOUNTANT -> setOf(NativePermission.REPORT, NativePermission.EXPORT, NativePermission.FINANCE, NativePermission.EDIT_FINANCE)
            PartnerRole.OBSERVER -> setOf(NativePermission.REPORT, NativePermission.EXPORT)
            else -> emptySet()
        }
        rolePermissions.filter { repo.can(it) }.forEach { permission ->
            DepthChip(permission in rights, { rights = if (permission in rights) rights - permission else rights + permission }, Modifier.fillMaxWidth().testTag("network-member-right-${permission.name.lowercase()}")) { AppText(networkPermissionLabel(permission)) }
        }
        NetworkField("Основание назначения *", reason, "network-member-reason") { reason = it }
    }
}

private fun networkGrantLabel(kind: NativeGrantKind): String = when (kind) { NativeGrantKind.PERCENT -> "Скидка в процентах"; NativeGrantKind.FIXED -> "Скидка в сомах"; NativeGrantKind.FREE -> "Бесплатная зарядка" }

@Composable private fun NetworkGrantEditor(repo: NativePartnerRepository, initial: NativeGrantKind, onDismiss: () -> Unit, onSaved: () -> Unit) {
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
        }.onSuccess { onSaved() }.onFailure { error = it.message ?: "Проверьте даты: ГГГГ-ММ-ДД" }
    }) {
        if (organizations.size > 1) NetworkChoice("Организация", organizationId, organizations.map { it.id }, { id -> organizations.first { it.id == id }.name }, "network-grant-organization") { organizationId = it; stationIds = emptySet() }
        NetworkField("ID клиента *", customer, "network-grant-customer") { customer = it }
        if (initial != NativeGrantKind.FREE) {
            NetworkChoice("Тип скидки", kind, listOf(NativeGrantKind.PERCENT, NativeGrantKind.FIXED), ::networkGrantLabel, "network-grant-kind") { kind = it }
            NetworkField(if (kind == NativeGrantKind.PERCENT) "Скидка, %" else "Скидка, сом", value, "network-grant-value", true) { value = it }
        }
        NetworkStationSelection(stations, stationIds, "network-grant-station") { stationIds = it }
        NetworkField("С · ГГГГ-ММ-ДД", starts, "network-grant-from") { starts = it }
        NetworkField("По · ГГГГ-ММ-ДД", ends, "network-grant-to") { ends = it }
        NetworkField("Лимит использований *", limit, "network-grant-limit", true) { limit = it }
        NetworkNote("Одобряет: ${repo.account.name}")
        NetworkField("Основание одобрения *", reason, "network-grant-reason") { reason = it }
    }
}

@Composable fun NativePartnerBrands(repo: NativePartnerRepository, revision: Int, onChanged: () -> Unit) {
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<NativeBrand?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val brands = remember(repo, revision) { repo.brands() }
    val stations = remember(repo, revision) { repo.stations(includeArchived = true) }
    LazyColumn(Modifier.fillMaxSize().testTag("network-brands"), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item("brands-header") { NetworkCard("Бренды сети") {
            NetworkNote("Названия и цвета брендов применяются к локальному реестру точек.")
            NetworkField("Найти бренд", query, "network-brand-search") { query = it }
            if (repo.can(NativePermission.MANAGE_BRANDS)) DepthButton({ creating = true }, Modifier.fillMaxWidth().testTag("network-brand-create")) { AppText("Добавить бренд") }
        } }
        val filtered = brands.filter { query.isBlank() || it.name.contains(query, true) }
        if (filtered.isEmpty()) item("brands-empty") { NetworkNote("Бренды не найдены.") }
        items(filtered, key = { "brand-${it.id}" }) { brand -> NetworkCard(brand.name, Modifier.testTag("network-brand-${brand.id}")) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                val color = runCatching { Color(android.graphics.Color.parseColor(brand.color)) }.getOrDefault(Color.Gray)
                Surface(Modifier.size(24.dp), color = color, shape = MaterialTheme.shapes.small) {}
                AppText(brand.color, fontSize = 13.sp)
            }
            NetworkNote("Точек: ${stations.count { it.brandId == brand.id }} · ${if (brand.archived) "В архиве" else "Активный бренд"}")
            if (brand.logoUrl.isNotBlank()) NetworkNote("Логотип: ${brand.logoUrl}")
            if (repo.can(NativePermission.MANAGE_BRANDS)) DepthOutlinedButton({ editing = brand }, Modifier.fillMaxWidth().testTag("network-brand-edit-${brand.id}")) { AppText("Редактировать бренд") }
        } }
    }
    if (creating || editing != null) NetworkBrandEditor(repo, editing, { creating = false; editing = null }) { creating = false; editing = null; onChanged() }
}

@Composable private fun NetworkBrandEditor(repo: NativePartnerRepository, original: NativeBrand?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val organizations = repo.organizations()
    var organizationId by remember { mutableStateOf(original?.organizationId ?: organizations.firstOrNull()?.id.orEmpty()) }
    var name by remember { mutableStateOf(original?.name.orEmpty()) }
    var color by remember { mutableStateOf(original?.color ?: "#77C83F") }
    var logo by remember { mutableStateOf(original?.logoUrl.orEmpty()) }
    var archived by remember { mutableStateOf(original?.archived ?: false) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    NetworkEditor(if (original == null) "Новый бренд" else "Редактирование бренда", "network-brand-editor", error, onDismiss, {
        runCatching {
            require(name.isNotBlank() && reason.isNotBlank()) { "Укажите название и причину изменения." }
            require(color.matches(Regex("#[0-9a-fA-F]{6}"))) { "Цвет должен иметь вид #77C83F." }
            require(logo.isBlank() || logo.startsWith("https://")) { "Для ссылки на логотип используйте HTTPS." }
            require(organizationId.isNotBlank()) { "Выберите организацию." }
            repo.saveBrand(NativeBrand(original?.id ?: networkId(), organizationId, name.trim(), color.uppercase(), logo.trim(), archived), reason.trim())
        }.onSuccess { onSaved() }.onFailure { error = it.message ?: "Не удалось сохранить бренд" }
    }) {
        if (original == null && organizations.size > 1) NetworkChoice("Организация", organizationId, organizations.map { it.id }, { id -> organizations.first { it.id == id }.name }, "network-brand-organization") { organizationId = it }
        NetworkField("Название *", name, "network-brand-name") { name = it }
        NetworkField("Цвет #RRGGBB *", color, "network-brand-color") { color = it }
        NetworkField("Ссылка на логотип (необязательно)", logo, "network-brand-logo") { logo = it }
        if (original != null) DepthChip(archived, { archived = !archived }, Modifier.testTag("network-brand-archived")) { AppText("Бренд в архиве") }
        NetworkField("Причина изменения *", reason, "network-brand-reason") { reason = it }
    }
}

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
