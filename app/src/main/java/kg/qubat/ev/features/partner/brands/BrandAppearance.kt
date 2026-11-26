@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val networkColorChoices = listOf(
    "#77C83F" to "Зелёный", "#008C65" to "Тёмно-зелёный", "#43A0D6" to "Голубой",
    "#315ACB" to "Синий", "#9354C8" to "Фиолетовый", "#E79A25" to "Оранжевый"
)
internal fun networkColorName(value: String): String = networkColorChoices.firstOrNull { it.first.equals(value, true) }?.second ?: "Свой цвет"
internal fun networkBrandColor(value: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(Color.Gray)

@Composable internal fun BrandAppearancePreview(name: String, color: String) {
    AppText("Пример оформления", fontSize = 13.sp, color = Muted)
    DepthSurface(Modifier.fillMaxWidth(), color = networkBrandColor(color).copy(alpha = .12f)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(Modifier.size(38.dp), color = networkBrandColor(color), shape = MaterialTheme.shapes.medium) {
                Box(contentAlignment = Alignment.Center) { AppText("⚡", color = Color.White, fontSize = 21.sp) }
            }
            Column(Modifier.weight(1f)) {
                AppText(name.ifBlank { "Название вашей сети" }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                AppText("Зарядная точка", fontSize = 13.sp, color = Muted)
            }
        }
    }
}

@Composable internal fun BrandColorPicker(value: String, onChange: (String) -> Unit) {
    AppText("Выберите цвет", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    BrandColorWheel(value, onChange)
}
