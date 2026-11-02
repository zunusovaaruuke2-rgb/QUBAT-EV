package kg.qubat.ev

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class PartnerCabinetTest {
    private val compose=createAndroidComposeRule<MainActivity>()
    private fun clearAccess() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        c.getSharedPreferences("qubat_partner_demo_access",0).edit().clear().commit()
        c.getSharedPreferences("qubat_ui",0).edit().putString("language","ru").commit()
    }
    private val cleanup=object:TestRule {
        override fun apply(base:Statement,description:Description)=object:Statement(){override fun evaluate(){clearAccess();try{base.evaluate()}finally{clearAccess()}}}
    }
    @get:Rule val chain:RuleChain=RuleChain.outerRule(cleanup).around(compose)
    private fun scroll(tag:String):SemanticsNodeInteraction {
        val list=compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
        if(tag.startsWith("network-period-")||tag=="network-station-filter")list.performScrollToIndex(0) else list.performScrollToNode(hasTestTag(tag))
        try{return compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()}catch(t:Throwable){throw AssertionError("Native partner scroll $tag: ${compose.onNodeWithTag(tag).printToString()}",t)}
    }
    private fun ready(){compose.waitUntil(10000){compose.onNodeWithTag("partner-screen").fetchSemanticsNode().config[SemanticsProperties.StateDescription]=="Отчёт загружен"}}
    private fun section(name:String){compose.onNodeWithTag("network-menu").performClick();compose.onNodeWithTag("network-menu-$name").performScrollTo().performClick();compose.waitForIdle()}
    private fun preview(name:String){val dir=File(compose.activity.getExternalFilesDir(null),"partner-preview").apply{mkdirs()};File(dir,"$name.png").outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
    @Test fun expandedNativeCabinetPersistsAndScopesEveryRole(){
        var stage="hidden entry"
        try{
            compose.waitUntil(5000){compose.onAllNodesWithTag("launch-splash").fetchSemanticsNodes().isEmpty()}
            compose.onNodeWithText("Профиль",useUnmergedTree=true).performClick()
            compose.onNodeWithTag("partner-entry").assertDoesNotExist()
            scroll("profile-about").performClick()
            compose.onNodeWithTag("partner-demo-access").performTouchInput{longClick()}
            compose.onNodeWithTag("partner-demo-demo-owner-a").performScrollTo().performClick();ready()
            stage="monitoring counts"
            scroll("network-station-count")
            compose.onNode(hasText("3 / 3 / 6") and hasAnyAncestor(hasTestTag("network-station-count"))).assertIsDisplayed()
            stage="native point tariff"
            section("points")
            scroll("network-point-partner-demo-a-1").performClick()
            scroll("network-point-tab-parameters").performClick()
            scroll("network-tariff-edit").performClick()
            compose.onNodeWithTag("network-tariff-customer").performScrollTo().performTextReplacement("14.75")
            val tomorrow=LocalDate.now(PARTNER_ZONE).plusDays(1).toString()
            compose.onNodeWithTag("network-tariff-from").performScrollTo().performTextReplacement(tomorrow)
            compose.onNodeWithTag("network-tariff-reason").performScrollTo().performTextReplacement("ДЕМО тест новой версии")
            compose.onNodeWithTag("network-tariff-editor-save").performClick()
            compose.onNodeWithTag("network-tariff-editor").assertDoesNotExist()
            stage="report filters and pagination"
            section("reports")
            scroll("network-period-week").performClick()
            scroll("network-station-filter").performClick()
            compose.onNodeWithTag("network-select-station-partner-demo-b-1").assertDoesNotExist()
            compose.onNodeWithTag("network-select-station-").performClick()
            scroll("network-page-next").assertIsEnabled().performClick()
            scroll("network-page-label")
            compose.onNodeWithTag("network-page-label").assertTextEquals("2 / 4")
            scroll("network-report-summary");preview("android-expanded-reports")
            val state=NativePartnerDisk(compose.activity).load()
            val repo=NativePartnerRepository(NativePartnerRepository.accounts.first{it.id=="demo-owner-a"},state)
            val report=repo.report(NativeReportFilter(PartnerPeriod.sevenDays(state.today)))
            File(compose.activity.getExternalFilesDir(null),"partner-preview/${NativePartnerExcel.fileName(report)}").outputStream().use{NativePartnerExcel.write(report,it)}
            stage="monthly invoice"
            section("finance")
            scroll("network-period-previous").performClick()
            val previous=YearMonth.now(PARTNER_ZONE).minusMonths(1)
            scroll("network-bill-edit-$previous").performClick()
            compose.onNodeWithTag("network-bill-amount").performScrollTo().performTextReplacement("6543.21")
            compose.onNodeWithTag("network-bill-number").performScrollTo().performTextReplacement("DEMO-NATIVE-001")
            compose.onNodeWithTag("network-bill-reason").performScrollTo().performTextReplacement("Проверка квитанции Android")
            compose.onNodeWithTag("network-bill-save").performClick()
            compose.onNodeWithTag("network-bill-save").assertDoesNotExist()
            check(state.monthBills.any{it.billNumber=="DEMO-NATIVE-001" && it.electricityKgs.toPlainString()=="6543.21"})
            compose.activityRule.scenario.recreate();ready()
            scroll("network-finance-profit");preview("android-expanded-finance")
            stage="brands and access"
            section("brands");scroll("network-brand-create").assertIsEnabled()
            section("access");scroll("network-access-invite").assertIsEnabled()
            stage="observer cannot change prices"
            section("profile");scroll("partner-switch-demo").performClick()
            compose.onNodeWithTag("partner-demo-demo-observer").performScrollTo().performClick();ready()
            section("points");scroll("network-point-partner-demo-a-1").performClick()
            scroll("network-point-tab-parameters").performClick()
            compose.onNodeWithTag("network-tariff-edit").assertDoesNotExist()
            stage="owner B scope and unknown payments"
            section("profile");scroll("partner-switch-demo").performClick()
            compose.onNodeWithTag("partner-demo-demo-owner-b").performScrollTo().performClick();ready()
            section("reports");scroll("network-station-filter").performClick()
            compose.onNodeWithTag("network-select-station-partner-demo-a-1").assertDoesNotExist()
            compose.onNodeWithTag("network-select-station-").performClick()
            stage="back and sign out"
            compose.onNodeWithTag("partner-back").performClick()
            scroll("partner-entry").performClick();ready()
            section("profile");scroll("partner-sign-out").performClick()
            compose.onNodeWithTag("partner-screen").assertDoesNotExist()
            compose.onNodeWithTag("partner-entry").assertDoesNotExist()
        }catch(failure:Throwable){throw AssertionError("Native partner stage $stage: ${failure.message}",failure)}
    }
}
