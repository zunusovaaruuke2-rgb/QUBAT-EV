package kg.qubat.ev

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream

internal fun chargeShell(command:String):String = ParcelFileDescriptor.AutoCloseInputStream(
    InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
).use{it.readBytes().toString(Charsets.UTF_8)}

internal fun saveChargePreview(compose:ComposeTestRule,name:String) {
    val bytes=ByteArrayOutputStream().use{stream->
        compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,stream)
        stream.toByteArray()
    }
    chargeShell("mkdir -p /data/local/tmp/qubat-charging")
    val pipes=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommandRw("dd of=/data/local/tmp/qubat-charging/$name.png")
    ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]).use{it.write(bytes)}
    ParcelFileDescriptor.AutoCloseInputStream(pipes[0]).use{it.readBytes()}
}

/** Paragraph width may equal its full constraint while the Text node wraps a short line. */
internal fun assertChargeTextFits(node:androidx.compose.ui.test.SemanticsNodeInteraction) {
    node.assertIsDisplayed()
    val results=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
    node.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult){it(results)}
    val result=results.single()
    val message="Clipped text: ${result.layoutInput.text}; size=${result.size}"
    org.junit.Assert.assertFalse(message,result.multiParagraph.didExceedMaxLines)
    org.junit.Assert.assertEquals(message,1,result.lineCount)
    org.junit.Assert.assertTrue(message,result.getLineLeft(0)>=-1f)
    org.junit.Assert.assertTrue(message,result.getLineRight(0)<=result.size.width+1f)
    org.junit.Assert.assertTrue(message,result.getLineTop(0)>=-1f)
    org.junit.Assert.assertTrue(message,result.getLineBottom(0)<=result.size.height+1f)
}

internal fun saveNativeCalendarPreview(name:String) {
    val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
    val bytes=ByteArrayOutputStream().use{stream->bitmap.compress(Bitmap.CompressFormat.PNG,100,stream);stream.toByteArray()}
    bitmap.recycle()
    chargeShell("mkdir -p /data/local/tmp/qubat-charging")
    val pipes=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommandRw("dd of=/data/local/tmp/qubat-charging/$name.png")
    ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]).use{it.write(bytes)}
    ParcelFileDescriptor.AutoCloseInputStream(pipes[0]).use{it.readBytes()}
}
