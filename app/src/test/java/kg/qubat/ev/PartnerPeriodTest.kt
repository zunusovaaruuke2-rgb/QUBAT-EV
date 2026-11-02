package kg.qubat.ev

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class PartnerPeriodTest {
    private val day=LocalDate.of(2026,11,1)
    @Test fun wholeLastDayIsIncludedInBishkekTime() {
        val period=PartnerPeriod.today(day)
        assertEquals(Instant.parse("2026-10-31T18:00:00Z"),period.start)
        assertEquals(Instant.parse("2026-11-01T18:00:00Z"),period.endExclusive)
        assertTrue(period.start in period)
        assertTrue(period.endExclusive.minusNanos(1) in period)
        assertFalse(period.start.minusNanos(1) in period)
        assertFalse(period.endExclusive in period)
    }
    @Test fun reversedDatesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {PartnerPeriod(day,day.minusDays(1))}
    }
    @Test fun presetsKeepTheirInclusiveBounds() {
        assertEquals(PartnerPeriod(day.minusDays(6),day),PartnerPeriod.sevenDays(day))
        assertEquals(PartnerPeriod(day.withDayOfMonth(1),day),PartnerPeriod.month(day))
    }
}
