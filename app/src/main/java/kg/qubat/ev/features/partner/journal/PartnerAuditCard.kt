@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp

@Composable internal fun NetworkAuditCard(event: NativeAudit) {
    NetworkCard(event.action, Modifier.testTag("network-audit-${event.id}")) {
        NetworkNote("${networkTime.format(event.at)} · ${event.authorId}")
        AppText("Было: ${event.before.ifBlank { "—" }}", fontSize = 12.sp)
        AppText("Стало: ${event.after.ifBlank { "—" }}", fontSize = 12.sp)
        NetworkNote("Причина: ${event.reason}")
    }
}
