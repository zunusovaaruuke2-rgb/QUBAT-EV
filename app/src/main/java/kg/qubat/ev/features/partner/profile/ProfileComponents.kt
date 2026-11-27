@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package kg.qubat.ev

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class ProfileShortcut(val route: String, val title: String, val subtitle: String, val icon: ImageVector)

@Composable internal fun ProfileWelcome(name: String, role: String) {
    val initials = name.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
        .take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").ifBlank { "Q" }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF07543D), Color(0xFF008963))))
        .padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            AppText("QUBAT EV", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            AppText("ДЕМО", color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(52.dp), shape = CircleShape, color = Color(0xFFC9F2DF)) {
                Box(contentAlignment = Alignment.Center) { AppText(initials, color = Color(0xFF07543D), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppText(name, Modifier.testTag("network-profile-display-name"), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                AppText(role, color = Color.White.copy(alpha = .85f), fontSize = 14.sp)
            }
        }
        AppText("Рады вас видеть", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable internal fun ProfileNetworkCounts(points: List<NativeStation>) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Triple(points.size,"Точки","points"),Triple(points.sumOf { it.apparatusCount },"Аппараты","apparatus"),
            Triple(points.sumOf { it.ports.size },"Разъёмы","ports")).forEach { (count, title, tag) ->
            DepthSurface(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.padding(vertical = 14.dp, horizontal = 7.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    AppText(count.toString(), Modifier.testTag("network-profile-count-$tag"), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Lime)
                    AppText(title, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable internal fun ProfileShortcuts(shortcuts: List<ProfileShortcut>, onNavigate: (String) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 300.dp || fontScale > 1.2f) 1 else 2
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            shortcuts.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { shortcut ->
                        DepthSurface(onClick = { onNavigate(shortcut.route) },
                            modifier = Modifier.weight(1f).fillMaxHeight().testTag("network-profile-go-${shortcut.route}")) {
                            Column(Modifier.heightIn(min = 108.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                AppIcon(shortcut.icon, null, Modifier.size(24.dp), tint = Lime)
                                AppText(shortcut.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                AppText(shortcut.subtitle, fontSize = 12.sp, color = Muted)
                            }
                        }
                    }
                    if (row.size < columns) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable internal fun ProfileSettingRow(title: String, value: String = "", icon: ImageVector, tag: String, onClick: () -> Unit) {
    PressTextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(tag)) {
        AppIcon(icon, null, Modifier.size(23.dp), tint = Lime)
        Spacer(Modifier.width(12.dp))
        AppText(title, Modifier.weight(1f), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
        if (value.isNotBlank()) AppText(value, Modifier.widthIn(max = 110.dp), fontSize = 13.sp, color = Muted)
        AppText(" ›", fontSize = 19.sp)
    }
}
