@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun BrandPointsEditor(repo: NativePartnerRepository, brand: NativeBrand, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val points = repo.stations(includeArchived = true).filter { it.organizationId == brand.organizationId }
    val previous = points.filter { it.brandId == brand.id }.map { it.id }.toSet()
    var selected by remember { mutableStateOf(previous) }
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val removed = previous - selected
    val alternatives = repo.brands().filter { it.organizationId == brand.organizationId && it.id != brand.id && !it.archived }
    NetworkEditor("Выбрать точки", "network-brand-points-editor", error, onDismiss, {
        runCatching {
            repo.assignBrandPoints(brand.id, selected, reason.trim(), replacement.takeIf { it.isNotBlank() })
        }.onSuccess { onSaved() }.onFailure { error = it.message ?: "Не удалось сохранить выбор" }
    }) {
        AppText(brand.name)
        NetworkNote("Отметьте точки, которые должны относиться к этому названию.")
        NetworkField("Найти точку", query, "network-brand-point-search") { query = it }
        val visible = points.filter { query.isBlank() || it.title.contains(query.trim(), true) || it.city.contains(query.trim(), true) }
        visible.forEach { point ->
            DepthChip(point.id in selected, { selected = if (point.id in selected) selected - point.id else selected + point.id },
                Modifier.fillMaxWidth().testTag("network-brand-select-${point.id}")) {
                Column {
                    AppText(point.title, fontSize = 14.sp)
                    if (point.archived) AppText("В архиве", fontSize = 12.sp, color = Muted)
                    else if (point.brandId != brand.id) {
                        val name = repo.brands().firstOrNull { it.id == point.brandId }?.name
                        if (name != null) AppText(name, fontSize = 12.sp, color = Muted)
                    }
                }
            }
        }
        if (visible.isEmpty()) NetworkNote("Точки не найдены.")
        if (removed.isNotEmpty()) {
            NetworkNote("Снятым точкам нужно другое название сети.")
            if (alternatives.isEmpty()) NetworkNote("Сначала добавьте другое название сети или оставьте эти точки выбранными.", error = true)
            else NetworkChoice("Другое название для снятых точек", replacement,
                listOf("") + alternatives.map { it.id },
                { id -> alternatives.firstOrNull { it.id == id }?.name ?: "Выберите название" },
                "network-brand-replacement") { replacement = it }
        }
        NetworkNote("Выбранные точки перейдут к этому названию. Адреса и названия самих точек не меняются.")
        NetworkField("Причина изменения *", reason, "network-brand-points-reason") { reason = it }
    }
}
