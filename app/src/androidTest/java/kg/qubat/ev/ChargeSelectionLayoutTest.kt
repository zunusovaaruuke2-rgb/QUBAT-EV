package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class ChargeSelectionLayoutTest {
    private val compose = createComposeRule()
    private val display = object : TestRule {
        override fun apply(base: Statement, description: Description) = object : Statement() {
            override fun evaluate() {
                val size = Regex("Override size: (\\d+x\\d+)").find(chargeShell("wm size"))?.groupValues?.get(1)
                val density = Regex("Override density: (\\d+)").find(chargeShell("wm density"))?.groupValues?.get(1)
                try {
                    chargeShell("wm size 360x800"); chargeShell("wm density 160")
                    base.evaluate()
                } finally {
                    chargeShell(if (size == null) "wm size reset" else "wm size $size")
                    chargeShell(if (density == null) "wm density reset" else "wm density $density")
                }
            }
        }
    }
    @get:Rule val chain: RuleChain = RuleChain.outerRule(display).around(compose)

    @Test fun equalCardsReadableLabelsAndCorrectConnectorSelection() {
        val station = DemoData.stations.first()
        var port by mutableStateOf(station.freeConnector()!!)
        var language by mutableStateOf(AppLanguage.RU)
        var fontScale by mutableFloatStateOf(1.2f)
        var width by mutableIntStateOf(332)
        var starts = 0
        compose.setContent {
            val density = LocalDensity.current.density
            QubatTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(density, fontScale),
                    LocalAppLanguage provides language,
                    LocalTranslate provides { translate(it, language) },
                ) {
                    Box(Modifier.padding(14.dp).width(width.dp).height(550.dp)) {
                        ChargeSelection(station, port, AppState(), { port = it }, { starts++ }, {}, {}, {})
                    }
                }
            }
        }
        for (lang in AppLanguage.entries) {
            compose.runOnIdle { language = lang }
            compose.waitForIdle()
            val bounds = station.chargers.map { charger ->
                compose.onNodeWithTag("charge-unit-${charger.id}").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            }
            bounds.forEach { assertEquals(bounds.first().height, it.height, 1f); assertEquals(bounds.first().width, it.width, 1f) }
            bounds.zipWithNext().forEach { (a, b) -> assertTrue(a.right < b.left); assertEquals(a.top, b.top, 1f) }
            station.chargers.forEach { charger ->
                listOf("unit-number-", "unit-power-", "unit-status-").forEach { prefix ->
                    assertChargeTextFits(compose.onNodeWithTag(prefix + charger.id, useUnmergedTree = true))
                }
            }
            station.chargers.first().connectors.forEach { connector ->
                assertChargeTextFits(compose.onNodeWithTag("choice-name-${connector.id}", useUnmergedTree = true))
                assertChargeTextFits(compose.onNodeWithTag("choice-status-${connector.id}", useUnmergedTree = true))
            }
        }
        compose.runOnIdle { language = AppLanguage.RU }
        saveChargePreview(compose, "charging-selection-equal-cards")
        // The busy port must never enable the start action, even when deliberately selected.
        val busy = station.chargers.first().connectors[1]
        compose.onNodeWithTag("charge-choice-${busy.id}").performScrollTo().performClick()
        compose.waitUntil(5000) { port.id == busy.id }
        compose.onNodeWithTag("start-charge").assertIsNotEnabled()
        compose.onNodeWithTag("charge-selection-summary").performScrollTo()
        compose.onNodeWithTag("selection-apparatus", useUnmergedTree = true).assertTextEquals("Аппарат №1 · GB/T 2")
        compose.onNodeWithTag("charge-unit-${station.chargers[2].id}").performScrollTo().performClick()
        compose.waitUntil(5000) { port.id == station.chargers[2].connectors.first().id }
        compose.onAllNodes(hasTestTag("charge-choice-${station.chargers[2].connectors[0].id}")).assertCountEquals(1)
        compose.onNodeWithTag("start-charge").assertIsNotEnabled()
        compose.onNodeWithTag("charge-unit-${station.chargers.first().id}").performScrollTo().performClick()
        compose.waitUntil(5000) { port.id == station.freeConnector()!!.id }
        compose.onNodeWithTag("start-charge").assertIsEnabled().performClick()
        compose.waitUntil(5000) { starts == 1 }
        compose.runOnIdle { assertEquals(1, starts); width = 292; fontScale = 1.6f }
        compose.onNodeWithTag("charge-unit-${station.chargers.first().id}").performScrollTo()
        listOf("unit-number-", "unit-power-", "unit-status-").forEach { prefix ->
            assertChargeTextFits(compose.onNodeWithTag(prefix + station.chargers.first().id, useUnmergedTree = true))
        }
        compose.onNodeWithTag("start-charge").assertIsDisplayed()
        saveChargePreview(compose, "charging-selection-accessibility")
    }
}
