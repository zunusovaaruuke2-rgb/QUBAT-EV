package kg.qubat.ev

import android.content.Context
import android.util.AtomicFile
import java.io.*

/** Private on-device DEMO storage. Never deserialize network responses or imported receipts. */
internal object NativeStateCodec {
    private const val MAX_BYTES=4*1024*1024
    fun encode(state:NativePartnerState):ByteArray=ByteArrayOutputStream().use {bytes->
        ObjectOutputStream(bytes).use {it.writeObject(state)}
        bytes.toByteArray().also {require(it.size<=MAX_BYTES){"Демонстрационный журнал слишком большой"}}
    }
    fun decode(bytes:ByteArray):NativePartnerState {
        require(bytes.size in 1..MAX_BYTES)
        val input=object:ObjectInputStream(ByteArrayInputStream(bytes)) {
            override fun resolveProxyClass(interfaces:Array<out String>):Class<*> = throw InvalidClassException("Proxy types are not local demo data")
            override fun resolveClass(descriptor:ObjectStreamClass):Class<*> {
                val name=descriptor.name
                val allowed=name.startsWith("kg.qubat.ev.Native") || name in setOf("kg.qubat.ev.PartnerRole","kg.qubat.ev.PartnerAccount","kg.qubat.ev.PartnerPeriod","kg.qubat.ev.PartnerSort") ||
                    name.startsWith("java.math.") || name=="java.time.Ser" || name in setOf("java.lang.Enum","java.lang.Number","java.lang.Integer","java.lang.Long","java.lang.Boolean","java.lang.String","java.util.ArrayList","java.util.HashMap","java.util.LinkedHashMap","java.util.HashSet","java.util.LinkedHashSet","java.util.Arrays\$ArrayList") ||
                    name.startsWith("java.util.Collections$") || name in setOf("kotlin.collections.EmptyList","kotlin.collections.EmptySet","kotlin.collections.EmptyMap") || Regex("\\[[BCDFIJSZ]").matches(name) || name=="[Ljava.lang.Object;" || name=="[Ljava.lang.String;" || name.startsWith("[Lkg.qubat.ev.Native")
                if(!allowed) throw InvalidClassException("Unsupported local demo type")
                return super.resolveClass(descriptor)
            }
        }
        val value=input.use {it.readObject() as? NativePartnerState} ?: throw InvalidObjectException("Invalid demo state")
        require(value.schemaVersion==2 && value.stations.size<=2000 && value.sessions.size<=50000 && value.audit.size<=50000)
        return value
    }
}
internal class NativePartnerDisk(context:Context) {
    private val file=AtomicFile(File(context.filesDir,"qubat-partner-network-v2.bin"))
    companion object {
        private val lock=Any()
        private var cached:NativePartnerState?=null
        private val writer=java.util.concurrent.Executors.newSingleThreadExecutor {job->Thread(job,"partner-demo-save").apply {isDaemon=true}}
    }
    fun load():NativePartnerState=synchronized(lock) {
        cached ?: runCatching {NativeStateCodec.decode(file.readFully())}.getOrElse {
            if(file.baseFile.exists()) android.util.Log.e("QUBAT_PARTNER","Cannot restore private demo snapshot",it)
            NativePartnerRepository.seed()
        }.also {android.util.Log.i("QUBAT_PARTNER","Demo snapshot loaded: exists=${file.baseFile.exists()}, tariffs=${it.tariffs.size}, audit=${it.audit.size}");cached=it}
    }
    fun saveLater(state:NativePartnerState,onError:(String)->Unit) {
        val snapshot=state.snapshot()
        synchronized(lock) {cached=state}
        writer.execute {
            try {save(NativeStateCodec.encode(snapshot))}
            catch(_:Exception) {android.os.Handler(android.os.Looper.getMainLooper()).post {onError("Не удалось сохранить демо-изменения")}}
        }
    }
    fun save(bytes:ByteArray) {
        val stream=file.startWrite()
        try {stream.write(bytes);file.finishWrite(stream)} catch(failure:Throwable) {file.failWrite(stream);throw failure}
    }
}
