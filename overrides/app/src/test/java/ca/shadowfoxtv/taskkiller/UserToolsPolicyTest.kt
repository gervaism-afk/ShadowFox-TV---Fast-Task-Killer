package ca.shadowfoxtv.taskkiller

import org.junit.Assert.*
import org.junit.Test

class UserToolsPolicyTest {
    @Test fun stableSamplesReportMedianAndVariation() {
        val result = StabilityAnalysis.summarize(listOf(10, 12, 11, 13), false)
        assertEquals(11, result.medianMs)
        assertEquals(1, result.variationMs)
        assertEquals(13, result.maximumMs)
        assertEquals("CONSISTENT RESPONSE TIMES", result.verdict)
    }

    @Test fun failuresAreNotConvertedIntoFastConnections() {
        val result = StabilityAnalysis.summarize(listOf(10, -1, 50, -1), false)
        assertEquals(2, result.failed)
        assertEquals(30, result.medianMs)
        assertNull(result.variationMs)
        assertEquals("CONNECTION INTERRUPTIONS", result.verdict)
    }

    @Test fun unreachableEndpointDoesNotClaimInternetIsOffline() {
        val result = StabilityAnalysis.summarize(List(12) { -1 }, false)
        assertNull(result.medianMs)
        assertEquals("TEST ENDPOINT UNREACHABLE", result.verdict)
        assertEquals(12, result.failed)
    }

    @Test fun networkChangesInvalidateOneNetworkVerdict() {
        assertEquals("NETWORK CHANGED", StabilityAnalysis.summarize(listOf(10, 11), true).verdict)
        assertEquals("NOT TESTED", StabilityAnalysis.summarize(emptyList(), false).verdict)
    }

    @Test fun variableAndSlowConnectionsHaveDifferentResults() {
        assertEquals("VARIABLE RESPONSE TIMES", StabilityAnalysis.summarize(listOf(10, 250, 10), false).verdict)
        assertEquals("SLOW RESPONSE TIMES", StabilityAnalysis.summarize(listOf(200, 201, 199), false).verdict)
    }

    @Test fun storageRecoveryHasHysteresis() {
        assertTrue(HealthAlertPolicy.lowStorage(99, 100, false))
        assertFalse(HealthAlertPolicy.lowStorage(100, 100, false))
        assertTrue(HealthAlertPolicy.lowStorage(105, 100, true))
        assertFalse(HealthAlertPolicy.lowStorage(110, 100, true))
        assertFalse(HealthAlertPolicy.lowStorage(-1, 100, true))
    }

    @Test fun heatWarningsRequireSevereStatusAndRecoverBelowModerate() {
        assertFalse(HealthAlertPolicy.hot(null, true))
        assertFalse(HealthAlertPolicy.hot(2, false))
        assertTrue(HealthAlertPolicy.hot(3, false))
        assertTrue(HealthAlertPolicy.hot(2, true))
        assertFalse(HealthAlertPolicy.hot(1, true))
    }

    @Test fun unchangedAlertsAndQuickRecurrencesStayQuiet() {
        val now = 100_000_000L
        assertTrue(HealthAlertPolicy.notify(true, false, now, 0))
        assertFalse(HealthAlertPolicy.notify(true, true, now, 0))
        assertFalse(HealthAlertPolicy.notify(false, true, now, 0))
        assertFalse(HealthAlertPolicy.notify(true, false, now, now - 1000))
        assertTrue(HealthAlertPolicy.notify(true, false, now, now - HealthAlertPolicy.REPEAT_COOLDOWN_MS))
    }

    @Test fun reportRedactsAddressesAndPrivatePathsButKeepsVersion() {
        val clean = SupportRedaction.clean("7.2.0 user@example.com 192.168.2.56 /data/user/0/private/file App: 188")
        assertFalse(clean.contains("user@example.com"))
        assertFalse(clean.contains("192.168.2.56"))
        assertFalse(clean.contains("/data/"))
        assertTrue(clean.contains("7.2.0"))
        assertTrue(clean.contains("App: 188"))
        assertEquals(12000, SupportRedaction.clean("x".repeat(13000)).length)
    }
}
