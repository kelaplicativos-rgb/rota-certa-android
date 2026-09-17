package br.com.mapeiaia.rotacerta.monitoring

import br.com.mapeiaia.rotacerta.DiagnosticEventContext0507
import br.com.mapeiaia.rotacerta.DiagnosticModule0507
import br.com.mapeiaia.rotacerta.DiagnosticSeverity0507
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationalHealthEngineTest {
    private val now = 1_800_000_000_000L

    @Test
    fun duplicateEditTargetMissingBecomesOneCriticalIdentityIncident() {
        val events = listOf(
            event(now - 2_000L, "SET_TRIP_BOOST_ERROR", "edit_target_missing", "SET_TRIP_BOOST"),
            event(now - 1_000L, "SET_TRIP_BOOST_ERROR", "edit_target_missing", "SET_TRIP_BOOST"),
        )
        val result = OperationalHealthEngine.analyze(snapshot(events), now)
        assertEquals(1, result.incidents.size)
        assertEquals(2, result.incidents.single().count)
        assertEquals(OperationalIncidentSeverity.CRITICAL, result.incidents.single().severity)
        assertTrue(result.incidents.single().suggestedCorrection.contains("TripOperationalIdentity"))
        assertTrue(result.opportunities.any { it.title.contains("Identidade") })
    }

    @Test
    fun canonicalSeatMismatchProposesSingleCanonicalContract() {
        val result = OperationalHealthEngine.analyze(
            snapshot(
                listOf(
                    event(
                        now - 500L,
                        "CANONICAL_SEGMENT_AVAILABILITY_MISMATCH",
                        "segment_availability_mismatch",
                        "SYNC_TIMELINE",
                    ),
                ),
            ),
            now,
        )
        assertTrue(result.incidents.single().probableRootCause.contains("contrato canônico"))
        assertTrue(result.incidents.single().suggestedCorrection.contains("Agenda.segmentAvailability"))
    }

    @Test
    fun increaseInRecentWindowIsClassifiedAsRegression() {
        val events = listOf(
            event(now - 7L * 60L * 60L * 1000L, "NETWORK_TIMEOUT", "timeout", "SYNC"),
            event(now - 60L * 60L * 1000L, "NETWORK_TIMEOUT", "timeout", "SYNC"),
            event(now - 30L * 60L * 1000L, "NETWORK_TIMEOUT", "timeout", "SYNC"),
        )
        val result = OperationalHealthEngine.analyze(snapshot(events), now)
        assertEquals(OperationalValidationState.REGRESSION, result.validation)
    }

    private fun event(
        at: Long,
        stage: String,
        errorCode: String,
        operation: String,
    ) = UnifiedDebugEventStore.SnapshotEvent(
        atMillis = at,
        monotonicNs = at * 1_000_000L,
        stage = stage,
        packageName = "br.com.mapeiaia.rotacerta",
        details = "sanitized=true",
        threadName = "test",
        diagnosticContext = DiagnosticEventContext0507(
            parentModule = DiagnosticModule0507.BLABLACAR,
            operation = operation,
            severity = DiagnosticSeverity0507.ERROR,
            errorCode = errorCode,
            result = "ERROR",
        ),
    )

    private fun snapshot(events: List<UnifiedDebugEventStore.SnapshotEvent>) =
        UnifiedDebugEventStore.Snapshot(
            events = events,
            droppedEvents = 0L,
            bufferCapacity = 6_000,
            recordCalls = events.size.toLong(),
            recordOverheadTotalNs = 0L,
            recordMedianNs = 0L,
            recordP95Ns = 0L,
            recordMaxNs = 0L,
        )
}
