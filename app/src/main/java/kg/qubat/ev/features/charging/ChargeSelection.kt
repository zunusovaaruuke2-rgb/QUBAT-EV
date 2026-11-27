package kg.qubat.ev

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Selection only: changing this screen does not change the running session layouts. */
@Composable internal fun ChargeSelection(
    station: Station, port: Connector?, state: AppState,
    onChoose: (Connector) -> Unit, onStart: () -> Unit, onRelease: () -> Unit,
    onStations: () -> Unit, onCancel: () -> Unit,
) {
    val unit = station.chargers.firstOrNull { charger -> charger.connectors.any { it.id == port?.id } }
        ?: station.chargers.first()
    BoxWithConstraints(Modifier.fillMaxSize().testTag("charge-selection")) {
        val compact = maxHeight < 560.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    DepthSurface(onClick = onStations, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp)) {
                        ChargeStationCard(station, port, compact = true, selection = true)
                    }
                    if (state.active) PressIconButton(onClick = onCancel) { AppIcon(Icons.Default.Close, "Вернуться к зарядкам") }
                }
                ChargeText("1 · Выберите аппарат", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                ChargeApparatusRow(station, unit, compact, onChoose)
                ChargeText("2 · Выберите разъём", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val columns = if (maxWidth < 300.dp || LocalDensity.current.fontScale > 1.25f) 1 else 2
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        unit.connectors.chunked(columns).forEach { ports ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ports.forEach { item ->
                                    ChargeConnectorChoice(item, item.id == port?.id, Modifier.weight(1f)) { onChoose(item) }
                                }
                            }
                        }
                    }
                }
                if (port != null) ChargeSelectionSummary(unit, port)
                DriverDiscountPrice(station.id)
            }
            DepthButton(
                onClick = onStart,
                enabled = port?.state == ConnectorState.CONNECTED && state.availableBalanceMinor > 0 && state.charges.size < ChargingEngine.MAX_CARS,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("start-charge"),
            ) {
                AppIcon(Icons.Default.PlayArrow, null, Modifier.size(26.dp))
                Spacer(Modifier.width(6.dp))
                ChargeText("Начать зарядку", fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            if (port?.id in state.completedConnectorIds) PressTextButton(onClick = onRelease) {
                ChargeText("Освободить разъём после сеанса", fontSize = 12.sp)
            }
        }
    }
}

@Composable private fun ChargeApparatusRow(station: Station, selected: Charger, compact: Boolean, onChoose: (Connector) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Four equal cards on a normal phone. Larger accessibility fonts get more space per card.
        val visibleCount = when { maxWidth < 300.dp || fontScale > 1.5f -> 2; fontScale > 1.25f -> 3; else -> 4 }
        val gap = 6.dp
        // Round once in pixels: four independently rounded fractional widths can clip the last card.
        val cardWidth = with(LocalDensity.current) {
            ((maxWidth.roundToPx() - gap.roundToPx() * (visibleCount - 1)) / visibleCount).toDp()
        }
        val cardHeight = with(LocalDensity.current) { (if (compact) 76.dp else 92.dp) + 48.sp.toDp() }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(gap)) {
            station.chargers.forEach { charger ->
                val isSelected = charger.id == selected.id
                val available = charger.connectors.any { it.state == ConnectorState.FREE }
                val offline = charger.connectors.all { it.state == ConnectorState.OFFLINE }
                val status = if (available) "Свободен" else if (offline) "Офлайн" else "Занят"
                val statusColor = if (available) Lime else Muted
                DepthSurface(
                    onClick = { onChoose(charger.connectors.firstOrNull { it.state == ConnectorState.CONNECTED } ?: charger.connectors.first()) },
                    modifier = Modifier.width(cardWidth).height(cardHeight).testTag("charge-unit-${charger.id}"),
                    shape = RoundedCornerShape(16.dp), color = if (isSelected) Lime.copy(alpha = .08f) else SurfaceGreen,
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) Lime else MaterialTheme.colorScheme.outline),
                ) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            ChargerCabinet(charger.connectors.size, Modifier.size(width = 58.dp, height = if (compact) 54.dp else 68.dp))
                        }
                        ChargeText("№${charger.number}", Modifier.testTag("unit-number-${charger.id}"), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        ChargeText("${charger.powerKw} кВт", Modifier.testTag("unit-power-${charger.id}"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Surface(shape = CircleShape, color = statusColor.copy(alpha = .1f)) {
                            ChargeText(status, Modifier.padding(horizontal = 4.dp, vertical = 3.dp).testTag("unit-status-${charger.id}"), color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun ChargeConnectorChoice(port: Connector, selected: Boolean, modifier: Modifier, onChoose: () -> Unit) {
    val color = when (port.state) {
        ConnectorState.FREE -> Lime
        ConnectorState.CONNECTED -> Color(0xFF2388CE)
        else -> Color(port.state.argb)
    }
    DepthSurface(
        onClick = onChoose, modifier = modifier.testTag("charge-choice-${port.id}"), shape = RoundedCornerShape(18.dp),
        color = if (selected) Lime.copy(alpha = .08f) else SurfaceGreen,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Lime else MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ConnectorGlyph(color, Modifier.size(28.dp))
                ChargeText("GB/T ${port.number}", Modifier.weight(1f).testTag("choice-name-${port.id}"), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                if (selected) AppIcon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = Lime)
            }
            ChargeText(port.state.title, Modifier.align(Alignment.CenterHorizontally).testTag("choice-status-${port.id}"), fontSize = 12.sp, color = color, maxLines = 1)
        }
    }
}

@Composable private fun ChargeSelectionSummary(unit: Charger, port: Connector) {
    DepthSurface(Modifier.fillMaxWidth().testTag("charge-selection-summary"), shape = RoundedCornerShape(18.dp), color = SurfaceGreen) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CarGlyph(Modifier.size(38.dp))
            VerticalDivider(Modifier.height(42.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ChargeText("Выбрано:", fontSize = 11.sp, color = Muted)
                ChargeText("Аппарат №${unit.number} · GB/T ${port.number}", Modifier.testTag("selection-apparatus"), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = if (LocalDensity.current.fontScale > 1.25f) 2 else 1)
                ChargeText(if (port.state == ConnectorState.FREE) "Подключите кабель" else port.state.title, fontSize = 12.sp, color = if (port.state == ConnectorState.FREE) Lime else Color(port.state.argb), maxLines = 1)
            }
        }
    }
}
