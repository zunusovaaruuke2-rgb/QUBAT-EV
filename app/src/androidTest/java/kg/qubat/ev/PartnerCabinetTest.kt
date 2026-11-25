package kg.qubat.ev

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class PartnerCabinetTest {
    private val compose=createAndroidComposeRule<MainActivity>()
    private fun clearDemoAccess() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("qubat_partner_demo_access",0).edit().clear().commit()
        context.getSharedPreferences("qubat_ui",0).edit().putString("language","ru").commit()
    }
    private val cleanAccess=object:TestRule {
        override fun apply(base:Statement,description:Description)=object:Statement() {
            override fun evaluate() {
                clearDemoAccess()
                try {base.evaluate()} finally {clearDemoAccess()}
            }
        }
    }
    @get:Rule val chain:RuleChain=RuleChain.outerRule(cleanAccess).around(compose)
    private fun scroll(tag:String):SemanticsNodeInteraction {
        val list=compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
        when {
            tag.startsWith("partner-period-") || tag=="partner-station-filter" || tag=="partner-export" -> list.performScrollToIndex(1)
            tag.startsWith("partner-tab-") -> list.performScrollToIndex(2)
            else -> list.performScrollToNode(hasTestTag(tag))
        }
        try {return compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()}
        catch(failure:Throwable) {throw AssertionError("Partner scroll target $tag: ${compose.onNodeWithTag(tag).printToString()}",failure)}
    }
    private fun ready() {
        try {
            compose.waitUntil(10000) {
                val state=compose.onNodeWithTag("partner-screen").fetchSemanticsNode().config[SemanticsProperties.StateDescription]
                if(state=="Ошибка отчёта") throw AssertionError("Report failed: ${compose.onRoot().printToString()}")
                state=="Отчёт загружен"
            }
        } catch(failure:Throwable) {
            throw AssertionError("Report state: ${compose.onNodeWithTag("partner-screen").printToString()} — ${failure.message}",failure)
        }
        compose.waitForIdle()
    }
    private fun preview(name:String) {
        val directory=File(compose.activity.getExternalFilesDir(null),"partner-preview").apply {mkdirs()}
        File(directory,"$name.png").outputStream().use {output->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,output)
        }
    }
    @Test fun partnerDemoIsSeparateScopedAndNavigable() {
        var stage="hidden entry"
        try {
        compose.waitUntil(5000) {compose.onAllNodesWithTag("launch-splash").fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithText("Профиль",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("partner-entry").assertDoesNotExist()
        scroll("profile-about").performClick()
        compose.onNodeWithTag("partner-demo-access").performTouchInput {longClick()}
        compose.onNodeWithTag("partner-demo-dialog").assertIsDisplayed()
        stage="owner A open"
        compose.onNodeWithTag("partner-demo-demo-owner-a").performClick()
        compose.onNodeWithTag("partner-screen").assertIsDisplayed()
        ready()
        stage="seven-day report"
        scroll("partner-period-7").performClick()
        ready()
        scroll("partner-energy")
        preview("partner-summary")
        val repo=PartnerDemoRepository()
        val snapshot=repo.report(repo.accounts.first {it.id=="demo-owner-a"},PartnerFilter(PartnerPeriod.sevenDays()))
        File(compose.activity.getExternalFilesDir(null),"partner-preview/${PartnerExcel.fileName(snapshot)}").outputStream().use {PartnerExcel.write(snapshot,it)}
        stage="station selector"
        scroll("partner-station-filter").performClick()
        compose.onNodeWithTag("partner-select-partner-demo-a-1").assertExists()
        compose.onNodeWithTag("partner-select-partner-demo-b-1").assertDoesNotExist()
        compose.onNodeWithTag("partner-select-partner-demo-a-1").performClick()
        ready()
        stage="station details"
        scroll("partner-tab-stations").performClick()
        scroll("partner-station-partner-demo-a-1")
        preview("partner-station")
        compose.onNodeWithTag("partner-station-partner-demo-b-1").assertDoesNotExist()
        scroll("partner-station-filter").performClick()
        compose.onNodeWithTag("partner-station-all").performClick()
        ready()
        stage="pagination"
        scroll("partner-tab-sessions").performClick()
        scroll("partner-page-next").assertIsEnabled().performClick()
        scroll("partner-page-label").assertTextEquals("2 / 4")
        stage="date validation"
        scroll("partner-period-custom").performClick()
        compose.onNodeWithTag("partner-date-from").performTextReplacement("2026-11-02")
        compose.onNodeWithTag("partner-date-to").performTextReplacement("2026-11-01")
        compose.onNodeWithTag("partner-date-apply").assertIsNotEnabled()
        compose.onNodeWithTag("partner-date-from").performTextReplacement("2020-01-01")
        compose.onNodeWithTag("partner-date-to").performTextReplacement("2020-01-01")
        compose.onNodeWithTag("partner-date-apply").performClick()
        ready()
        compose.onNodeWithTag("partner-scroll").performScrollToNode(hasText("За выбранный период зарядок не найдено"))
        compose.onNodeWithText("За выбранный период зарядок не найдено").assertIsDisplayed()
        scroll("partner-period-7").performClick()
        ready()
        scroll("partner-tab-profile").performClick()
        scroll("partner-switch-demo").performClick()
        stage="owner B isolation"
        compose.onNodeWithTag("partner-demo-demo-owner-b").performClick()
        ready()
        scroll("partner-station-filter").performClick()
        compose.onNodeWithTag("partner-select-partner-demo-b-1").assertExists()
        compose.onNodeWithTag("partner-select-partner-demo-a-1").assertDoesNotExist()
        compose.onNodeWithTag("partner-station-all").performClick()
        ready()
        scroll("partner-tab-summary").performClick()
        scroll("partner-paid")
        compose.onNode(hasText("Нет данных") and hasAnyAncestor(hasTestTag("partner-paid"))).assertIsDisplayed()
        preview("partner-unknown-payouts")
        stage="activity recreation"
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("partner-screen").assertIsDisplayed()
        ready()
        stage="back and sign out"
        compose.onNodeWithTag("partner-back").performClick()
        scroll("partner-entry").performClick()
        ready()
        scroll("partner-tab-profile").performClick()
        scroll("partner-sign-out").performClick()
        compose.onNodeWithTag("partner-screen").assertDoesNotExist()
        compose.onNodeWithTag("partner-entry").assertDoesNotExist()
        } catch(failure:Throwable) {
            throw AssertionError("Partner UI stage: $stage — ${failure.message}",failure)
        }
    }
}
