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
    fun increaseInUniqueIncidentsWithFullWindowIsClassifiedAsRegression() {
        val events = listOf(
            UnifiedDebugEventStore.SnapshotEvent(
                atMillis = now - 13L * 60L * 60L * 1000L,
                monotonicNs = (now - 13L * 60L * 60L * 1000L) * 1_000_000L,
                stage = "HEALTH_BASELINE",
                packageName = "br.com.mapeiaia.rotacerta",
                details = "baseline=true",
                threadName = "test",
            ),
            event(now - 7L * 60L * 60L * 1000L, "NETWORK_TIMEOUT", "timeout", "SYNC"),
            event(now - 60L * 60L * 1000L, "NETWORK_TIMEOUT", "timeout", "SYNC"),
            event(now - 30L * 60L * 1000L, "CANONICAL_MISMATCH", "canonical_mismatch", "SYNC"),
        )
        val result = OperationalHealthEngine.analyze(snapshot(events), now)
        assertEquals(OperationalValidationState.REGRESSION, result.validation)
        assertTrue(result.validationSummary.contains("Incidentes únicos aumentaram de 1 para 2"))
    }

    @Test
    fun truncatedHistoryCannotBeCalledRegression() {
        val events = buildList {
            repeat(113) { index ->
                add(event(now - index * 1_000L, "NETWORK_TIMEOUT", "timeout", "SYNC"))
            }
        }
        val result = OperationalHealthEngine.analyze(
            snapshot(events).copy(droppedEvents = 22_895L),
            now,
        )
        assertEquals(OperationalValidationState.INSUFFICIENT_DATA, result.validation)
        assertTrue(result.validationSummary.contains("Evidência histórica reidratada não conta como cobertura contínua"))
    }

    @Test
    fun recoveredHistoricalEventCannotFakeTwelveHourCoverage() {
        val recovered = UnifiedDebugEventStore.SnapshotEvent(
            atMillis = now - 15L * 60L * 60L * 1000L,
            monotonicNs = now * 1_000_000L,
            stage = "RECOVERED_UNCAUGHT_AGENDA_SYNC_CRASH_0573",
            packageName = "br.com.mapeiaia.rotacerta",
            details = "source=persisted_agenda_crash recovered=true",
            threadName = "main",
        )
        val current = event(now - 1_000L, "NETWORK_TIMEOUT", "timeout", "SYNC")
        val result = OperationalHealthEngine.analyze(snapshot(listOf(recovered, current)), now)
        assertEquals(OperationalValidationState.INSUFFICIENT_DATA, result.validation)
        assertTrue(result.validationSummary.contains("janela anterior=0"))
    }

    @Test
    fun staleOneShotSkippedIsProtectiveOutcomeNotIncident() {
        val stale = UnifiedDebugEventStore.SnapshotEvent(
            atMillis = now - 1_000L,
            monotonicNs = (now - 1_000L) * 1_000_000L,
            stage = "AGENDA_BACKGROUND_SYNC_STALE_ONE_SHOT_0435",
            packageName = "br.com.mapeiaia.rotacerta",
            details = "trigger=ADMIN_UPDATE_NOW result=SKIPPED",
            threadName = "worker",
        )
        val result = OperationalHealthEngine.analyze(snapshot(listOf(stale)), now)
        assertTrue(result.incidents.isEmpty())
    }

    @Test
    fun staleCallbackIgnoredWithoutExtraResultIsNotIncident() {
        val stale = UnifiedDebugEventStore.SnapshotEvent(
            atMillis = now - 1_000L,
            monotonicNs = (now - 1_000L) * 1_000_000L,
            stage = "BROWSER_STALE_CALLBACK_IGNORED",
            packageName = "br.com.mapeiaia.rotacerta",
            details = "callbackGeneration=7 activeGeneration=8",
            threadName = "main",
        )
        val result = OperationalHealthEngine.analyze(snapshot(listOf(stale)), now)
        assertTrue(result.incidents.isEmpty())
    }

    @Test
    fun tripIdentityWithoutSpecificHrefBecomesSingleCoverageIncident() {
        fun identity(at: Long, index: Int) = UnifiedDebugEventStore.SnapshotEvent(
            atMillis = at,
            monotonicNs = at * 1_000_000L,
            stage = "TRIP_IDENTITY",
            packageName = "br.com.mapeiaia.rotacerta",
            details = "index=$index/2 externalTripIdPresent=true specificHrefPresent=false fallbackIdentityUsed=false",
            threadName = "worker",
        )
        val result = OperationalHealthEngine.analyze(
            snapshot(listOf(identity(now - 2_000L, 1), identity(now - 1_000L, 2))),
            now,
        )
        assertEquals(1, result.incidents.size)
        assertEquals(2, result.incidents.single().count)
        assertEquals("SPECIFIC_TRIP_HREF_COVERAGE_MISSING_0576", result.incidents.single().errorCode)
        assertTrue(result.incidents.single().probableRootCause.contains("href específico"))
    }

    @Test
    fun recoveredAgendaCrashRemainsCriticalAtOriginalTimestamp() {
        val crash = UnifiedDebugEventStore.SnapshotEvent(
            atMillis = now - 5_000L,
            monotonicNs = (now - 5_000L) * 1_000_000L,
            stage = "RECOVERED_UNCAUGHT_AGENDA_SYNC_CRASH_0573",
            packageName = "br.com.mapeiaia.rotacerta",
            details = "source=persisted_agenda_crash; recovered=true; rootCauseClass=PatternSyntaxException",
            threadName = "main",
        )
        val result = OperationalHealthEngine.analyze(snapshot(listOf(crash)), now)
        assertEquals(OperationalHealthState.RED, result.state)
        assertEquals(OperationalIncidentSeverity.CRITICAL, result.incidents.single().severity)
    }

    @Test
    fun slowOperationAndJankAreOperationalIncidents() {
        val events = listOf(
            UnifiedDebugEventStore.SnapshotEvent(
                atMillis = now - 2_000L,
                monotonicNs = (now - 2_000L) * 1_000_000L,
                stage = "SLOW_OPERATION",
                packageName = "br.com.mapeiaia.rotacerta",
                details = "durationMs=1251",
                threadName = "main",
            ),
            UnifiedDebugEventStore.SnapshotEvent(
                atMillis = now - 1_000L,
                monotonicNs = (now - 1_000L) * 1_000_000L,
                stage = "AGENDA_JANK_FRAME_500MS",
                packageName = "br.com.mapeiaia.rotacerta",
                details = "durationMs=641",
                threadName = "main",
            ),
        )
        val result = OperationalHealthEngine.analyze(snapshot(events), now)
        assertEquals(2, result.incidents.size)
        assertTrue(result.incidents.all { it.severity == OperationalIncidentSeverity.WARNING })
        assertTrue(result.incidents.any { it.probableRootCause.contains("responsividade") })
        assertEquals(OperationalHealthState.YELLOW, result.state)
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
