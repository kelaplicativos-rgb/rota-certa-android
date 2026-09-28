package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlaBlaHtmlTransactionHeartbeat0678Test {
    @Test
    fun longCaptureRemainsValidWhenHeartbeatIsRecent0678() {
        val minute = 60_000L
        assertFalse(
            htmlCaptureLeaseExpired0678(
                nowMillis = 21 * minute,
                lastHeartbeatMillis = 10 * minute,
                staleAfterMillis = 15 * minute,
            ),
        )
    }

    @Test
    fun abandonedCaptureStillExpiresAfterFifteenMinutesOfInactivity0678() {
        val minute = 60_000L
        assertTrue(
            htmlCaptureLeaseExpired0678(
                nowMillis = 31 * minute,
                lastHeartbeatMillis = 15 * minute,
                staleAfterMillis = 15 * minute,
            ),
        )
    }

    @Test
    fun ownershipRequiresBothCaptureIdAndGeneration0678() {
        val expected = BlaBlaHtmlCaptureTransactionState0610(
            captureId = "capture-0678",
            generation = 678L,
            startedAtMillis = 1L,
            lastHeartbeatMillis = 2L,
        )
        assertTrue(htmlCaptureOwnerMatches0678(expected, "capture-0678", 678L))
        assertFalse(htmlCaptureOwnerMatches0678(expected, "capture-0678", 679L))
        assertFalse(htmlCaptureOwnerMatches0678(expected, "other-capture", 678L))
    }

    @Test
    fun globalCaptureWiresHeartbeatAndExactGenerationThroughFinalizer0678() {
        val session = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaCollectorSessionModule.kt",
        ).readText()
        val coordinator = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaRidesSnapshot0526.kt",
        ).readText()
        val direct = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDirectAccountCapture0608.kt",
        ).readText()
        val unified = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaUnifiedHtmlCapture0605.kt",
        ).readText()

        assertTrue(session.contains("KEY_LAST_HEARTBEAT"))
        assertTrue(session.contains("fun heartbeat("))
        assertTrue(session.contains("htmlCaptureLeaseExpired0678"))
        assertTrue(coordinator.contains("transaction0610 = transaction"))
        assertTrue(coordinator.contains("expectedTransaction0610 = transaction"))
        assertTrue(coordinator.contains("BlaBlaHtmlCaptureTransaction0610.end(app, transaction)"))
        assertTrue(direct.contains("transaction0610: BlaBlaHtmlCaptureTransactionState0610? = null"))
        assertTrue(unified.contains("expectedTransaction0610: BlaBlaHtmlCaptureTransactionState0610? = null"))
        assertTrue(unified.contains("BlaBlaHtmlCaptureTransaction0610.heartbeat(app, it)"))
        assertTrue(unified.contains("transaction_not_active_or_owner_lost_0678"))
    }
}
