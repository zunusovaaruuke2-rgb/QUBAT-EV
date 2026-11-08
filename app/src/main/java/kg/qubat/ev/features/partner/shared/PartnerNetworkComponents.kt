@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package kg.qubat.ev

import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.semantics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.UUID

internal val networkTime = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(PARTNER_ZONE)
internal fun networkNumber(value: BigDecimal) = value.stripTrailingZeros().toPlainString().replace('.', ',')
internal fun networkDecimal(value: String) = value.trim().replace(',', '.').toBigDecimalOrNull()
internal fun networkId() = "demo-" + UUID.randomUUID().toString().take(12)

@Composable internal fun NetworkCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    DepthSurface(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            AppText(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable internal fun NetworkNote(text: String, error: Boolean = false, tag: String = "") {
    AppText(text, Modifier.testTag(tag), fontSize = 12.sp, color = if (error) MaterialTheme.colorScheme.error else Muted)
}

@Composable internal fun NetworkField(label: String, value: String, tag: String, numeric: Boolean = false, onChange: (String) -> Unit) {
    PressOutlinedTextField(value, { onChange(it.take(300)) }, Modifier.fillMaxWidth().testTag(tag), singleLine = true,
        label = { AppText(label) }, keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text))
}

@Composable internal fun <T> NetworkChoice(label: String, value: T, options: List<T>, title: (T) -> String, tag: String, onChange: (T) -> Unit) {
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

@Composable internal fun NetworkEditor(title: String, tag: String, error: String?, onDismiss: () -> Unit, onSave: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DepthSurface(Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth().imePadding().testTag(tag).semantics { testTagsAsResourceId = true }) {
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
