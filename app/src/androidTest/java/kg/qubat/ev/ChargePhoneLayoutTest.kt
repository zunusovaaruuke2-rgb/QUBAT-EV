package kg.qubat.ev

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.json.JSONArray
import org.json.JSONObject

/** Reproduce the taller phone and enlarged system font from the reported screenshots. */
class ChargePhoneLayoutTest {
    private val compose=createAndroidComposeRule<MainActivity>()
    private val display=object:TestRule {
        override fun apply(base:Statement,description:Description)=object:Statement(){override fun evaluate(){
            val size=Regex("Override size: (\\d+x\\d+)").find(chargeShell("wm size"))?.groupValues?.get(1)
            val density=Regex("Override density: (\\d+)").find(chargeShell("wm density"))?.groupValues?.get(1)
            val font=chargeShell("settings get system font_scale").trim().takeIf{it.toFloatOrNull()!=null} ?: "1.0"
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            val prefs=context.getSharedPreferences("qubat_demo_v1",0)
            try {
                chargeShell("wm size 360x800");chargeShell("wm density 160");chargeShell("settings put system font_scale 1.2")
                context.getSharedPreferences("qubat_ui",0).edit().putString("language","ru").putBoolean("night",false).commit()
                val now=System.currentTimeMillis()
                val entries=JSONArray()
                listOf(Triple(DemoData.stations[0],18.0,810L),Triple(DemoData.stations[1],12.42,559L)).forEachIndexed{i,(site,energy,seconds)->
                    entries.put(JSONObject().put("id",site.id).put("connector",site.freeConnector()!!.id).put("vehicle",i+1)
                        .put("start",now-seconds*1000).put("updated",now).put("seconds",seconds).put("energy",energy)
                        .put("cost",kotlin.math.round(energy*site.tariffMinor).toLong()).put("budget",150000)
                        .put("power",JSONArray().put(JSONObject().put("sec",0).put("kw",80))))
                }
                prefs.edit().putString("state",JSONObject().put("schema",2).put("balance",150000).put("charges",entries).put("history",JSONArray()).put("completed",JSONArray()).toString()).commit()
                base.evaluate()
            } finally {
                prefs.edit().clear().commit()
                chargeShell(if(size==null)"wm size reset" else "wm size $size")
                chargeShell(if(density==null)"wm density reset" else "wm density $density")
                chargeShell("settings put system font_scale $font")
            }
        }}
    }
    @get:Rule val chain:RuleChain=RuleChain.outerRule(display).around(compose)
    private fun noOverflow(node:SemanticsNodeInteraction) {
        node.assertIsDisplayed()
        val results=mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(results)}
        assertFalse("Text is clipped: ${results.single().layoutInput.text}; size=${results.single().size}, constraints=${results.single().layoutInput.constraints}, width=${results.single().didOverflowWidth}, height=${results.single().didOverflowHeight}; paragraph=${results.single().multiParagraph.width}x${results.single().multiParagraph.height}",results.single().hasVisualOverflow)
        assertEquals(1,results.single().lineCount)
    }
    @Test fun twoCarsAndDetailFitWithEnlargedPhoneFont() {
        compose.waitUntil(10000){compose.onAllNodesWithTag("launch-splash").fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithText("Зарядка",useUnmergedTree=true).performClick()
        val ids=listOf(DemoData.stations[0].freeConnector()!!.id,DemoData.stations[1].freeConnector()!!.id)
        compose.waitUntil(5000){compose.onAllNodesWithTag("charge-session-${ids[1]}").fetchSemanticsNodes().isNotEmpty()}
        compose.waitForIdle()
        compose.onAllNodes(hasScrollAction()).assertCountEquals(0)
        noOverflow(compose.onNodeWithTag("charge-heading",useUnmergedTree=true))
        noOverflow(compose.onNodeWithText("Кошелёк",useUnmergedTree=true))
        saveChargePreview(compose,"charging-phone-two-cars")
        ids.forEach {id->
            val card=compose.onNodeWithTag("charge-session-$id").fetchSemanticsNode().boundsInRoot
            val gauge=compose.onNodeWithTag("charge-gauge-$id",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            val metrics=compose.onNodeWithTag("charge-metrics-$id",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            assertTrue(gauge.left>=card.left && gauge.right<=card.right && gauge.top>=card.top && gauge.bottom<=card.bottom)
            assertTrue("Gauge overlaps metrics",gauge.right+1<=metrics.left)
            compose.onNodeWithTag("stop-charge-$id").assertIsDisplayed()
            (0..3).forEach{noOverflow(compose.onNodeWithTag("card-value-$id-$it",useUnmergedTree=true))}
        }
        saveChargePreview(compose,"charging-phone-two-cars")
        compose.onNodeWithTag("charge-session-${ids[1]}").performClick()
        compose.waitUntil(5000){compose.onAllNodesWithTag("charge-percent-chart").fetchSemanticsNodes().isNotEmpty()}
        noOverflow(compose.onNodeWithTag("charge-heading",useUnmergedTree=true))
        saveChargePreview(compose,"charging-phone-detail")
        noOverflow(compose.onNodeWithTag("chart-y-100",useUnmergedTree=true))
        (0..3).forEach{noOverflow(compose.onNodeWithTag("metric-value-$it",useUnmergedTree=true))}
        val ticks=(0..2).map{index->
            val node=compose.onNodeWithTag("chart-time-$index",useUnmergedTree=true)
            noOverflow(node);node.fetchSemanticsNode().boundsInRoot
        }
        ticks.zipWithNext().forEach{(left,right)->assertTrue("Time labels overlap",left.right<right.left)}
        compose.onNodeWithTag("stop-charge-${ids[1]}").assertIsDisplayed()
        saveChargePreview(compose,"charging-phone-detail")
    }
}
