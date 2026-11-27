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
    var assigning by remember { mutableStateOf<NativeBrand?>(null) }
    var query by rememberSaveable(repo.account.id) { mutableStateOf("") }
    val brands = remember(repo, revision) { repo.brands() }
    val stations = remember(repo, revision) { repo.stations(includeArchived = true) }
    LazyColumn(Modifier.fillMaxSize().testTag("network-brands"), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item("brands-header") {
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                AppText("Выберите название и цвет для своих зарядных точек.",fontSize=14.sp,color=Muted)
                NetworkField("Найти по названию",query,"network-brand-search"){query=it}
                if(repo.can(NativePermission.MANAGE_BRANDS))DepthButton({creating=true},Modifier.fillMaxWidth().testTag("network-brand-create")){AppText("Добавить название сети")}
            }
        }
        val filtered=brands.filter{query.isBlank() || it.name.contains(query.trim(),true)}
        if(filtered.isEmpty())item("brands-empty"){NetworkNote("Названия не найдены.")}
        items(filtered,key={"brand-${it.id}"}){brand->
            val points=stations.filter{it.brandId==brand.id && it.organizationId==brand.organizationId}
            NetworkCard(brand.name,Modifier.testTag("network-brand-${brand.id}")){
                if(brand.archived)NetworkNote("В архиве")
                BrandAppearancePreview(brand.name,brand.color)
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Surface(Modifier.size(26.dp),color=networkBrandColor(brand.color),shape=MaterialTheme.shapes.small){}
                    AppText("Цвет сети",fontSize=14.sp)
                    AppText(networkColorName(brand.color),fontSize=14.sp)
                }
                AppText("Относящиеся точки",fontSize=14.sp)
                AppText("Количество точек: ${points.size}",fontSize=13.sp,color=Muted)
                points.forEach{point->
                    AppText(point.title,fontSize=14.sp,modifier=Modifier.testTag("network-brand-point-${brand.id}-${point.id}"))
                    if(point.archived)NetworkNote("В архиве")
                }
                if(points.isEmpty())NetworkNote("Пока нет точек. Нажмите «Выбрать точки».")
                if(repo.can(NativePermission.MANAGE_BRANDS)){
                    DepthOutlinedButton({editing=brand},Modifier.fillMaxWidth().testTag("network-brand-edit-${brand.id}")){AppText("Изменить название и цвет")}
                    if(!brand.archived && repo.can(NativePermission.EDIT_STATION))DepthOutlinedButton({assigning=brand},Modifier.fillMaxWidth().testTag("network-brand-points-${brand.id}")){AppText("Выбрать точки")}
                }
            }
        }
        item("brands-help"){NetworkNote("Одно название и цвет можно выбрать для нескольких точек. Их адреса и собственные названия сохраняются.")}
        item("brands-demo"){NetworkNote("ДЕМО · пример оформления")}

    }
    assigning?.let{brand->BrandPointsEditor(repo,brand,{assigning=null}){assigning=null;onChanged()}}
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
    NetworkEditor(if (original == null) "Добавить название сети" else "Изменить название и цвет", "network-brand-editor", error, onDismiss, {
        runCatching {
            require(name.isNotBlank() && reason.isNotBlank()) { "Укажите название и причину изменения." }
            require(color.matches(Regex("#[0-9a-fA-F]{6}"))) { "Выберите цвет на круге." }
            require(logo.isBlank() || logo.startsWith("https://")) { "Для ссылки на логотип используйте HTTPS." }
            require(organizationId.isNotBlank()) { "Выберите организацию." }
            repo.saveBrand(NativeBrand(original?.id ?: networkId(), organizationId, name.trim(), color.uppercase(), logo.trim(), archived), reason.trim())
        }.onSuccess { onSaved() }.onFailure { error = it.message ?: "Не удалось сохранить бренд" }
    }) {
        if (original == null && organizations.size > 1) NetworkChoice("Организация", organizationId, organizations.map { it.id }, { id -> organizations.first { it.id == id }.name }, "network-brand-organization") { organizationId = it }
        NetworkField("Название сети *", name, "network-brand-name") { name = it }
        BrandAppearancePreview(name,color)
        BrandColorPicker(color){color=it}
        var details by remember { mutableStateOf(false) }
        PressTextButton(onClick={details=!details}){AppText(if(details)"Скрыть дополнительные настройки" else "Дополнительные настройки")}
        if(details)NetworkField("Ссылка на логотип (необязательно)", logo, "network-brand-logo") { logo = it }
        if (original != null) DepthChip(archived, { archived = !archived }, Modifier.testTag("network-brand-archived")) { AppText("Убрать в архив") }
        NetworkField("Причина изменения *", reason, "network-brand-reason") { reason = it }
    }
}
