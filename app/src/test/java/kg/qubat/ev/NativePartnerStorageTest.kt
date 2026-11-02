package kg.qubat.ev

import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.time.LocalDate

class NativePartnerStorageTest {
    @Test fun stateRoundTripPreservesSessionsAndBills() {
        val original=NativePartnerRepository.seed(LocalDate.of(2026,11,1))
        val restored=NativeStateCodec.decode(NativeStateCodec.encode(original))
        assertEquals(original.sessions,restored.sessions)
        assertEquals(original.monthBills,restored.monthBills)
        assertEquals(original.members,restored.members)
        assertEquals(original.stations,restored.stations)
    }
    @Test fun unapprovedClassesCannotBeImported() {
        val bytes=ByteArrayOutputStream().also {buffer->ObjectOutputStream(buffer).use {it.writeObject(java.util.Date())}}.toByteArray()
        assertThrows(InvalidClassException::class.java) {NativeStateCodec.decode(bytes)}
    }
}
