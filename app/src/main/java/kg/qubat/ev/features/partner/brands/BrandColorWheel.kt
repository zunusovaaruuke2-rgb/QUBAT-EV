@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package kg.qubat.ev

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.*

/** Drawn on demand; no bitmaps, network calls, animations or external picker library. */
@Composable internal fun BrandColorWheel(value: String, onChange: (String) -> Unit) {
    val initial = remember { brandHsv(value) }
    val inset = with(LocalDensity.current) { 8.dp.toPx() }
    var fineTuning by remember { mutableStateOf(false) }
    var hue by remember { mutableFloatStateOf(initial[0]) }
    var saturation by remember { mutableFloatStateOf(initial[1]) }
    var brightness by remember { mutableFloatStateOf(initial[2]) }
    var emitted by remember { mutableStateOf(value) }
    val change by rememberUpdatedState(onChange)
    LaunchedEffect(value) {
        if (!value.equals(emitted, ignoreCase = true)) {
            val hsv = brandHsv(value)
            hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]
            emitted = value
        }
    }
    fun emit() {
        val rgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
        val next = String.format(Locale.ROOT, "#%06X", rgb and 0xFFFFFF)
        emitted = next
        change(next)
    }
    val choose by rememberUpdatedState<(Offset, Float) -> Unit>({ position, diameter ->
        val radius = (diameter / 2f - inset).coerceAtLeast(1f)
        val dx = position.x - diameter / 2f
        val dy = position.y - diameter / 2f
        val distance = hypot(dx, dy)
        if (distance > 0.001f) hue = ((atan2(dy, dx) * 180f / PI.toFloat()) + 360f) % 360f
        saturation = (distance / radius).coerceIn(0f, 1f)
        emit()
    })
    val wheelLabel = LocalTranslate.current("Цветовой круг")
    val brightnessLabel = LocalTranslate.current("Яркость")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.widthIn(max = 220.dp).fillMaxWidth().aspectRatio(1f)
            .testTag("network-brand-color-wheel")
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val diameter = size.width.toFloat()
                    val radius = (diameter / 2f - inset).coerceAtLeast(1f)
                    val center = Offset(diameter / 2f, diameter / 2f)
                    if ((down.position - center).getDistance() <= radius) {
                        down.consume()
                        choose(down.position, diameter)
                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull { it.id == down.id }
                            if (pointer == null || !pointer.pressed) pressed = false
                            else { pointer.consume(); choose(pointer.position, diameter) }
                        }
                    }
                }
            }
            .appDescription(wheelLabel)
            .drawWithCache {
                val radius = (size.minDimension / 2f - inset).coerceAtLeast(1f)
                val center = Offset(size.width / 2f, size.height / 2f)
                val colors = Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green,
                    Color.Cyan, Color.Blue, Color.Magenta, Color.Red), center)
                val white = Brush.radialGradient(listOf(Color.White, Color.Transparent), center, radius)
                onDrawBehind {
                    drawCircle(colors, radius, center)
                    drawCircle(white, radius, center)
                    drawCircle(Color.Black.copy(alpha = 1f - brightness), radius, center)
                    val angle = hue * PI.toFloat() / 180f
                    val marker = center + Offset(cos(angle), sin(angle)) * (saturation * radius)
                    drawCircle(Color.Black, 6.dp.toPx(), marker, style = Stroke(3.dp.toPx()))
                    drawCircle(Color.White, 6.dp.toPx(), marker, style = Stroke(1.5.dp.toPx()))
                }
            })
        AppText("Коснитесь круга, чтобы выбрать цвет", fontSize = 12.sp, color = Muted)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(Modifier.size(28.dp), color = networkBrandColor(value),
                shape = androidx.compose.foundation.shape.CircleShape) {}
            AppText("Выбранный цвет", fontSize = 14.sp)
        }
        AppText("Яркость", modifier = Modifier.fillMaxWidth(), fontSize = 13.sp)
        Slider(value = brightness, onValueChange = { brightness = it; emit() },
            modifier = Modifier.fillMaxWidth().testTag("network-brand-brightness").appDescription(brightnessLabel))
        PressTextButton(onClick = { fineTuning = !fineTuning }) { AppText(if (fineTuning) "Скрыть точную настройку" else "Точная настройка", fontSize = 13.sp) }
        if (fineTuning) {
        // Keyboard and screen-reader users can choose the same hue/saturation as touch users.
        AppText("Оттенок", modifier = Modifier.fillMaxWidth(), fontSize = 13.sp)
        Slider(value = hue, onValueChange = { hue = it; emit() }, valueRange = 0f..360f,
            modifier = Modifier.fillMaxWidth().testTag("network-brand-hue").appDescription("Оттенок"))
        AppText("Насыщенность", modifier = Modifier.fillMaxWidth(), fontSize = 13.sp)
        Slider(value = saturation, onValueChange = { saturation = it; emit() },
            modifier = Modifier.fillMaxWidth().testTag("network-brand-saturation").appDescription("Насыщенность"))
        }
    }
}

private fun brandHsv(value: String): FloatArray {
    val hsv = FloatArray(3)
    val color = runCatching { android.graphics.Color.parseColor(value) }.getOrDefault(android.graphics.Color.GREEN)
    android.graphics.Color.colorToHSV(color, hsv)
    return hsv
}
