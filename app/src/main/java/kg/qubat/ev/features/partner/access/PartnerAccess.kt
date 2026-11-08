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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

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

@Composable internal fun NetworkStationSelection(stations: List<NativeStation>, selected: Set<String>, tag: String, onChanged: (Set<String>) -> Unit) {
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
        }.onSuccess { onSaved() }.onFailure { error = it.message ?: "Проверьте даты" }
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
        CalendarDateField("Доступ с", starts, "network-member-from") { starts = it }
        CalendarDateField("Доступ до (необязательно)", ends, "network-member-to",optional=true) { ends = it }
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
