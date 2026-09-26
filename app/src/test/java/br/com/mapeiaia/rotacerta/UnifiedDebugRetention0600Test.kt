package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedDebugRetention0600Test {
    @Test
    fun failureAndPerformanceEvidenceIsNeverEligibleForRoutineThrottling() {
        assertTrue(UnifiedDebugEventStore.isPriorityEvidence0600("AGENDA_JANK_FRAME_100MS", "frameMs=212"))
        assertTrue(UnifiedDebugEventStore.isPriorityEvidence0600("SLOW_OPERATION", "operation=BOOKING_OUTBOX_ENQUEUE"))
        assertTrue(UnifiedDebugEventStore.isPriorityEvidence0600("PUBLIC_EVIDENCE_0421", "stage=SERVER_ACK status=FAILED"))
        assertTrue(
            UnifiedDebugEventStore.isPriorityEvidence0600(
                "TRIP_IDENTITY",
                "externalTripIdPresent=true specificHrefPresent=false",
            ),
        )
        assertNull(UnifiedDebugEventStore.routineThrottleKey0600("AGENDA_JANK_FRAME_100MS", "frameMs=212"))
    }

    @Test
    fun onlyKnownHealthyHighVolumeEvidenceGetsAThrottleKey() {
        assertNotNull(
            UnifiedDebugEventStore.routineThrottleKey0600(
                "PUBLIC_EVIDENCE_0421",
                "stage=SERVER_ACK status=OK reasonCode=WRITE_ACCEPTED",
            ),
        )
        assertNotNull(
            UnifiedDebugEventStore.routineThrottleKey0600(
                "EXTERNAL_CANONICAL_DISPOSITION_0451",
                "result=UNCHANGED sourceComplete=true",
            ),
        )
        assertNotNull(
            UnifiedDebugEventStore.routineThrottleKey0600(
                "TRIP_IDENTITY",
                "externalTripIdPresent=true specificHrefPresent=true identityConflict=false",
            ),
        )
        assertNull(UnifiedDebugEventStore.routineThrottleKey0600("UNKNOWN_OPERATION", "status=OK"))
    }

    @Test
    fun sanitizerContractRemainsUnchanged() {
        val sanitized = UnifiedDebugEventStore.sanitizeForExport(
            "phone=11999999999 email=test@example.com authorization=Bearer abc123",
        )
        assertTrue(sanitized.contains("[telefone mascarado]"))
        assertTrue(sanitized.contains("[email mascarado]"))
        assertTrue(sanitized.contains("[segredo mascarado]"))
        assertEquals(false, sanitized.contains("11999999999"))
    }
}
