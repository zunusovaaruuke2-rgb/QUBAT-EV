@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package kg.qubat.ev

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.time.Instant

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
    var selected by rememberSaveable(repo.account.id, initialStationId) { mutableStateOf(initialStationId) }
    var query by rememberSaveable(repo.account.id) { mutableStateOf("") }
    var status by rememberSaveable(repo.account.id) { mutableStateOf("Все статусы") }
    var group by rememberSaveable(repo.account.id) { mutableStateOf("Все группы") }
    var owner by rememberSaveable(repo.account.id) { mutableStateOf("Все владельцы") }
    var brand by rememberSaveable(repo.account.id) { mutableStateOf("Все бренды") }
    var archived by rememberSaveable(repo.account.id) { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var filtersOpen by remember { mutableStateOf(false) }
    val keyboard=LocalSoftwareKeyboardController.current
    val allStations=remember(repo,revision){repo.stations(includeArchived=true)}
    val stations=if(archived)allStations else allStations.filter{!it.archived}
    val brands=remember(repo,revision){repo.brands()}
    val station=selected?.let{id->allStations.firstOrNull{it.id==id}}
    val states=remember(repo,revision,allStations){allStations.associate{it.id to networkStatus(repo,it)}}
    BackHandler(selected!=null){selected=null}
    if(station!=null) {
        NetworkPointDetails(repo,station,revision,{selected=null},onChanged)
    } else {
        val filtered=stations.filter{item->
            (query.isBlank() || listOf(item.title,item.id,item.serialNumber,item.city,item.address).any{it.contains(query,true)}) &&
                (status=="Все статусы" || states[item.id]==status) &&
                (group=="Все группы" || item.groupId==group) &&
                (owner=="Все владельцы" || item.ownerId==owner) &&
                (brand=="Все бренды" || item.brandId==brand)
        }
        val applied=listOf(status!="Все статусы",group!="Все группы",owner!="Все владельцы",brand!="Все бренды",archived).count{it}
        val clear:()->Unit={query="";status="Все статусы";group="Все группы";owner="Все владельцы";brand="Все бренды";archived=false}
        LazyColumn(Modifier.fillMaxSize().testTag("network-points"),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item("point-search") {
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    PressOutlinedTextField(query,{query=it.take(300)},modifier=Modifier.fillMaxWidth().testTag("network-point-search"),
                        singleLine=true,placeholder={AppText("Название или адрес точки",fontSize=14.sp)},
                        leadingIcon={AppIcon(Icons.Default.Search,null)},
                        trailingIcon={if(query.isNotEmpty())PressIconButton(onClick={query=""}){AppIcon(Icons.Default.Close,"Очистить поиск")}},
                        keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={keyboard?.hide()}))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                        DepthOutlinedButton(onClick={keyboard?.hide();filtersOpen=true},modifier=Modifier.weight(1f).testTag("network-point-filters")) {
                            AppIcon(Icons.Default.Tune,null,Modifier.size(20.dp));Spacer(Modifier.width(6.dp))
                            AppText(if(applied==0)"Фильтры" else "Фильтры · $applied",fontSize=14.sp)
                        }
                        if(repo.can(NativePermission.EDIT_STATION))DepthButton(onClick={creating=true},modifier=Modifier.weight(1f).testTag("network-point-create")) {
                            AppIcon(Icons.Default.Add,null,Modifier.size(20.dp));AppText("Добавить",fontSize=14.sp)
                        }
                    }
                    if(applied>0 || query.isNotBlank())PressTextButton(onClick=clear,modifier=Modifier.testTag("network-point-clear-filters")){AppText("Сбросить поиск и фильтры",fontSize=12.sp)}
                }
            }
            item("point-counts"){PointListEquipmentCounts(filtered)}
            if(filtered.isEmpty())item("point-empty"){NetworkCard("Точки не найдены"){
                NetworkNote("Измените поиск или фильтры.")
                PressTextButton(onClick=clear){AppText("Показать все точки")}
            }}
            items(filtered,key={"point-${it.id}"},contentType={"point"}){item->
                PointListCard(item,states.getValue(item.id)){selected=item.id}
            }
        }
        if(filtersOpen)PointListFilters(
            allStations,brands,status,group,owner,brand,archived,
            ownerTitle={id->NativePartnerRepository.accounts.firstOrNull{it.id==id}?.name?:id},
            onDismiss={filtersOpen=false},
            onApply={s,g,o,b,archive->status=s;group=g;owner=o;brand=b;archived=archive;filtersOpen=false}
        )
    }
    if(creating)NetworkStationEditor(repo,null,{creating=false}){creating=false;onChanged()}
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
                    AppText("Общая установленная мощность: ${networkNumber(station.powerKw)} кВт", fontSize = 13.sp)
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
            val kw = requireNotNull(pointTotalPower(power)) { "Введите положительную общую мощность или сумму мощностей." }
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
        NetworkField("Общая мощность, кВт *", power, "network-edit-power") { power = it }
        NetworkNote("Укажите итог или сумму мощностей аппаратов: 80 + 120. Сохраняется общая мощность.")
        pointTotalPower(power)?.let{total->AppText("Общая мощность: ${networkNumber(total)} кВт",fontWeight=FontWeight.SemiBold,color=Lime,modifier=Modifier.testTag("network-edit-total-power"))}
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
        DepthOutlinedButton({ ports.add(NetworkPortDraft((ports.size + 1).toString(), "GB/T DC", ports.firstOrNull()?.power?.takeIf{networkDecimal(it)?.signum()==1} ?: "80", "200", NativePortStatus.UNAVAILABLE, networkId())) }, Modifier.fillMaxWidth().testTag("network-edit-port-add")) { AppText("Добавить разъём") }
        NetworkField("Причина изменения *", reason, "network-edit-reason") { reason = it }
    }
}
