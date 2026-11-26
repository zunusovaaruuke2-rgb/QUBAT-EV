package kg.qubat.ev

import java.time.Instant
import java.time.LocalDate
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class JournalPresentationTest {
    private val now = Instant.parse("2026-11-26T12:00:00Z")
    private val point = NativeStation("p", "org", "owner", "Example", "Address",
        lastSeen = now.minusSeconds(1200))
    private val rule = NativeNotificationSettings("r", "org", setOf("p"), offlineMinutes = 10)

    @Test fun currentAlertsRespectEnabledRuleScopeAndNeverInventNotificationTime() {
        val alerts = journalAlerts(listOf(point), listOf(rule), emptyList(), now) { false }
        assertEquals(1, alerts.size)
        assertEquals(JournalAlertKind.OFFLINE, alerts.single().kind)
        assertEquals(point.lastSeen, alerts.single().referenceAt)
        assertTrue(journalAlerts(listOf(point), listOf(rule.copy(enabled = false)), emptyList(), now) { false }.isEmpty())
        assertTrue(journalAlerts(listOf(point), listOf(rule.copy(organizationId = "other")), emptyList(), now) { false }.isEmpty())
        assertTrue(journalAlerts(listOf(point), listOf(rule.copy(stationIds = setOf("other"))), emptyList(), now) { false }.isEmpty())
    }

    @Test fun eachPointConditionHasOnlyOneRowAndStalePowerDoesNotCreateLowPowerAlert() {
        val fixture = NativePartnerRepository.seed(LocalDate.of(2026, 11, 26))
        val session = fixture.sessions.first().copy(stationId = "p", status = NativeSessionStatus.ACTIVE, startedAt = now.minusSeconds(9000))
        val rules = listOf(rule, rule.copy(id = "r2", lowPowerKw = BigDecimal("9999")))
        val alerts = journalAlerts(listOf(point), rules, listOf(session), now) { false }
        assertEquals(alerts.map { it.id }.distinct().size, alerts.size)
        assertTrue(alerts.any { it.kind == JournalAlertKind.LONG })
        assertFalse(alerts.any { it.kind == JournalAlertKind.LOW })
    }

    @Test fun readableChangesKeepExactDecimalsAndRejectAmbiguousLegacyFields() {
        fun event(before: String, after: String) = NativeAudit("audit", "org", setOf("p"), now, "owner", "Версия тарифа", before, after, "Reason")
        val changes = journalReadableChanges(event("NativeTariff(customerRate=13.50)", "NativeTariff(customerRate=14.75)"))
        assertEquals(1, changes.size)
        assertEquals("13,5", changes.single().second)
        assertEquals("14,75", changes.single().third)
        assertTrue(journalReadableChanges(event("unknown", "unknown")).isEmpty())
        assertTrue(journalReadableChanges(event("NativeTariff(customerRate=13.50)", "NativeTariff(customerRate=14.75, customerRate=17)")).isEmpty())
    }
}
