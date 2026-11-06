package kg.qubat.ev

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.test.platform.app.InstrumentationRegistry

/** Reading uncached semantics can observe snapshot state: keep that observation on the UI thread. */
internal fun SemanticsNodeInteraction.stateDescriptionOnMain():String {
    val node=fetchSemanticsNode()
    var description=""
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        description=node.config[SemanticsProperties.StateDescription]
    }
    return description
}
