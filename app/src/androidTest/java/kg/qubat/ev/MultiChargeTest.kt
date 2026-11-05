package kg.qubat.ev

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import androidx.lifecycle.ViewModelProvider
import android.graphics.Bitmap
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class MultiChargeTest {
    private val compose=createAndroidComposeRule<MainActivity>()
    private fun reset() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("qubat_demo_v1",0).edit().clear().commit()
        context.getSharedPreferences("qubat_partner_demo_access",0).edit().clear().commit()
        context.getSharedPreferences("qubat_ui",0).edit().putString("language","ru").commit()
    }
    private val cleanup=object:TestRule{override fun apply(base:Statement,description:Description)=object:Statement(){override fun evaluate(){reset();try{base.evaluate()}finally{reset()}}}}
    @get:Rule val chain:RuleChain=RuleChain.outerRule(cleanup).around(compose)
    private fun preview(name:String) {
        val dir=File(compose.activity.getExternalFilesDir(null),"partner-preview").apply{mkdirs()}
        val file=File(dir,"$name.png")
        file.outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
        // AGP may uninstall the target after instrumentation; keep previews in shell-owned storage.
        if(android.os.Build.VERSION.SDK_INT>=31) {
            val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("mkdir -p /data/local/tmp/qubat-charging")).use{it.readBytes()}
            val pipes=automation.executeShellCommandRw("dd of=/data/local/tmp/qubat-charging/$name.png")
            android.os.ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]).use{it.write(file.readBytes())}
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pipes[0]).use{it.readBytes()}
        }
    }
    @Test fun addSecondVehicleFromPlusAndStopEachIndependently() {
        compose.waitUntil(5000){compose.onAllNodesWithTag("launch-splash").fetchSemanticsNodes().isEmpty()}
        val vm=compose.runOnIdle{ViewModelProvider(compose.activity)[ChargingViewModel::class.java]}
        val station=DemoData.stations.first();val first=station.chargers[0].connectors[0].id;val second=station.chargers[1].connectors[1].id
        compose.onNodeWithText("Зарядка",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("start-charge").performScrollTo().performClick()
        compose.waitUntil(5000){vm.state.value.charges.size==1}
        compose.waitForIdle()
        compose.onNodeWithTag("charge-details-$first").assertExists()
        compose.onNodeWithTag("charge-power-chart").performScrollTo();preview("charging-one-car-light")
        compose.onNodeWithTag("add-charge").performScrollTo().performClick()
        compose.onNodeWithTag("stations-list").performScrollToIndex(0)
        compose.onNodeWithTag("connector-$second").performScrollTo().performClick()
        compose.onNodeWithTag("start-charge").performScrollTo().performClick()
        compose.waitUntil(5000){vm.state.value.charges.size==2}
        compose.waitForIdle()
        compose.onNodeWithTag("charge-session-$first").assertExists()
        compose.onNodeWithTag("charge-session-$second").assertExists()
        compose.onNodeWithTag("add-charge").assertDoesNotExist()
        compose.onNodeWithText("Зарядить ещё автомобиль",substring=true).assertDoesNotExist()
        compose.onNodeWithTag("charge-session-$first").performScrollTo();preview("charging-two-cars-light")
        val theme=if(compose.onAllNodesWithContentDescription("Ночная тема").fetchSemanticsNodes().isNotEmpty())"Ночная тема" else "Дневная тема"
        compose.onNodeWithContentDescription(theme).performClick()
        compose.onNodeWithTag("charge-session-$first").performScrollTo();preview("charging-two-cars-dark")
        compose.waitUntil(5000){vm.state.value.charges.all{it.progress.seconds>=1}}
        val context=compose.activity.application
        val saved=compose.runOnIdle{ChargingViewModel(context)}
        compose.runOnIdle{
            assertEquals(setOf(first,second),saved.state.value.charges.map{it.session.connectorId}.toSet())
            assertTrue(saved.state.value.availableBalanceMinor>=0)
        }
        compose.onNodeWithTag("stop-charge-$first").performScrollTo().performClick()
        compose.waitUntil(5000){vm.state.value.charges.size==1}
        assertEquals(second,vm.state.value.charges.single().session.connectorId)
        compose.waitForIdle()
        compose.onNodeWithTag("charge-details-$second").assertExists()
        compose.onNodeWithTag("stop-charge-$second").performScrollTo().assertIsDisplayed()
        compose.waitForIdle()
        compose.onNodeWithTag("stop-charge-$second").performClick()
        try{compose.waitUntil(5000){!vm.state.value.active}}catch(failure:Throwable){
            preview("charging-stop-failure")
            val ui=runCatching{compose.onRoot().printToString()}.getOrElse{it.toString()}
            throw AssertionError("Second stop: ${vm.state.value}; UI: $ui",failure)
        }
        assertEquals(2,vm.state.value.history.size)
        assertTrue(vm.state.value.balanceMinor>=0)
        assertEquals(setOf(first,second),vm.state.value.completedConnectorIds)
        compose.runOnIdle{saved.viewModelScope.cancel()}
    }
    @Test fun previousSingleSessionFormatMigratesWithoutLosingWallet() {
        compose.waitUntil(5000){compose.onAllNodesWithTag("launch-splash").fetchSemanticsNodes().isEmpty()}
        val station=DemoData.stations.first();val port=station.freeConnector()!!.id
        val now=System.currentTimeMillis()
        val context=compose.activity.application
        val json="""{"balance":123456,"session":{"id":"${station.id}","connector":"$port","start":$now,"budget":123456},"history":[],"completed":[]}"""
        context.getSharedPreferences("qubat_demo_v1",0).edit().putString("state",json).commit()
        val restored=compose.runOnIdle{ChargingViewModel(context)}
        assertEquals(123456,restored.state.value.balanceMinor)
        assertEquals(port,restored.state.value.charges.single().session.connectorId)
        compose.runOnIdle{restored.viewModelScope.cancel()}
    }
}
