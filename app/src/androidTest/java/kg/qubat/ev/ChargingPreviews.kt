package kg.qubat.ev

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.asAndroidBitmap
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
