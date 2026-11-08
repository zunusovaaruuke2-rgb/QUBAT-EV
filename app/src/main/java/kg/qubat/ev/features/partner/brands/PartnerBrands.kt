@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
