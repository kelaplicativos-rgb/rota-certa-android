package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextualDiagnostics0507Test {
    private fun event(
        at: Long,
        stage: String,
        parent: DiagnosticModule0507,
        origin: DiagnosticModule0507 = parent,
        executor: DiagnosticModule0507 = origin,
        correlation: String = "",
        operationId: String = "",
        parentOperationId: String = "",
        details: String = "",
        severity: DiagnosticSeverity0507 = DiagnosticSeverity0507.INFO,
    ) = UnifiedDebugEventStore.SnapshotEvent(
        atMillis = at,
        monotonicNs = at * 1_000_000,
        stage = stage,
        packageName = "br.com.mapeiaia.rotacerta.trips",
        details = details,
        threadName = "test",
        diagnosticContext = DiagnosticEventContext0507(
            parentModule = parent,
            originModule = origin,
            executorModule = executor,
            operation = stage,
            severity = severity,
            correlationId = correlation,
            traceId = correlation,
            operationId = operationId,
            parentOperationId = parentOperationId,
        ),
    )

    private fun snapshot(events: List<UnifiedDebugEventStore.SnapshotEvent>) =
        UnifiedDebugEventStore.Snapshot(
            events = events,
            droppedEvents = 0,
            bufferCapacity = UnifiedDebugEventStore.MAX_EVENTS,
            recordCalls = events.size.toLong(),
            recordOverheadTotalNs = 0,
            recordMedianNs = 0,
            recordP95Ns = 0,
            recordMaxNs = 0,
        )

    @Test
    fun scriptsReportIncludesOnlyOwnAndExplicitlyCorrelatedCrossModuleEvents() {
        val events = listOf(
            event(10, "SCRIPT_START", DiagnosticModule0507.SCRIPTS, correlation = "corr-script"),
            event(
                11,
                "REMOTE_WRITE",
                parent = DiagnosticModule0507.SCRIPTS,
                origin = DiagnosticModule0507.SCRIPTS,
                executor = DiagnosticModule0507.BLABLACAR,
                correlation = "corr-script",
            ),
            event(12, "BLABLA_INDEPENDENT", DiagnosticModule0507.BLABLACAR, correlation = "corr-other"),
            event(13, "PASSENGER_INDEPENDENT", DiagnosticModule0507.PASSENGERS, correlation = "corr-passenger"),
        )

        val selected = ContextualDebugReport0507.select(snapshot(events), DiagnosticModule0507.SCRIPTS)
        assertEquals(listOf("REMOTE_WRITE", "SCRIPT_START"), selected.map { it.stage })
        assertFalse(selected.any { it.stage == "BLABLA_INDEPENDENT" })
        assertFalse(selected.any { it.stage == "PASSENGER_INDEPENDENT" })
    }

    @Test
    fun sharedTraceAloneDoesNotCrossParentModuleBoundary() {
        val origin = event(
            20,
            "SCRIPT_START",
            DiagnosticModule0507.SCRIPTS,
            correlation = "corr-one",
            operationId = "op-script",
        )
        val unrelatedExecutor = event(
            21,
            "BROWSER_STEP",
            parent = DiagnosticModule0507.BLABLACAR,
            origin = DiagnosticModule0507.BLABLACAR,
            executor = DiagnosticModule0507.BLABLACAR,
            correlation = "corr-one",
            operationId = "op-browser",
        )
        val selected = ContextualDebugReport0507.select(
            snapshot(listOf(origin, unrelatedExecutor)),
            DiagnosticModule0507.SCRIPTS,
        )
        assertEquals(listOf("SCRIPT_START"), selected.map { it.stage })
    }

    @Test
    fun explicitParentOperationCanBringChildExecutorStepWithoutDuplicatingStorage() {
        val origin = event(
            22,
            "SCRIPT_START",
            DiagnosticModule0507.SCRIPTS,
            correlation = "corr-two",
            operationId = "op-script",
        )
        val executor = event(
            23,
            "BROWSER_STEP",
            parent = DiagnosticModule0507.BLABLACAR,
            origin = DiagnosticModule0507.BLABLACAR,
            executor = DiagnosticModule0507.BLABLACAR,
            correlation = "corr-two",
            operationId = "op-browser",
            parentOperationId = "op-script",
        )
        val source = snapshot(listOf(origin, executor))
        val selected = ContextualDebugReport0507.select(source, DiagnosticModule0507.SCRIPTS)
        assertEquals(2, selected.size)
        assertEquals(2, source.events.size)
    }

    @Test
    fun blaBlaReportDoesNotImportAllTripsStartupJustBecauseTraceMatches() {
        val trace = "ag-ubzl1kpss-1s"
        val blaBla = event(
            24,
            "MODULE_VIEW_OPENED_0507",
            DiagnosticModule0507.BLABLACAR,
            correlation = trace,
        )
        val allTrips = event(
            25,
            "TIMELINE_STARTUP",
            DiagnosticModule0507.ALL_TRIPS,
            correlation = trace,
            operationId = "op-timeline",
        )
        val selected = ContextualDebugReport0507.select(
            snapshot(listOf(blaBla, allTrips)),
            DiagnosticModule0507.BLABLACAR,
        )
        assertEquals(listOf("MODULE_VIEW_OPENED_0507"), selected.map { it.stage })
    }

    @Test
    fun sanitizerIsIdempotentAndRemovesControlledSecretsButKeepsTechnicalIds() {
        val raw = "authorization=Bearer SECRET_TOKEN password=hunter2 sessionId=SESSION_SECRET " +
            "phone=+551199998888 address=Rua_A_123 latitude=-23.55 email=private@example.com " +
            "canonicalTripId=canon-123 operation=REMOTE_WRITE correlationId=corr-55"
        val once = UnifiedDebugEventStore.sanitizeForExport(raw)
        val twice = UnifiedDebugEventStore.sanitizeForExport(once)
        assertEquals(once, twice)
        for (secret in listOf("SECRET_TOKEN", "hunter2", "SESSION_SECRET", "1199998888", "Rua_A_123", "-23.55", "private@example.com")) {
            assertFalse(secret, once.contains(secret))
        }
        assertTrue(once.contains("canonicalTripId=canon-123"))
        assertTrue(once.contains("operation=REMOTE_WRITE"))
        assertTrue(once.contains("correlationId=corr-55"))
    }

    @Test
    fun sanitizerPreservesTechnicalUuidWhileMaskingStandalonePhone() {
        val technicalId = "12345678-1234-1234-1234-123456789012"
        val sanitized = UnifiedDebugEventStore.sanitizeForExport(
            "entityId=$technicalId phone=1199998888",
        )
        assertTrue(sanitized.contains("entityId=$technicalId"))
        assertFalse(sanitized.contains("1199998888"))
        assertTrue(sanitized.contains("[telefone mascarado]"))
    }

    @Test
    fun reportExportSanitizesAgainAtOutputBoundary() {
        val e = event(
            30,
            "FAIL",
            DiagnosticModule0507.BLABLACAR,
            correlation = "corr-1",
            details = "cookie=COOKIE_SECRET canonicalTripId=canon-1",
            severity = DiagnosticSeverity0507.ERROR,
        )
        val report = ContextualDebugReport0507.export(
            DiagnosticModule0507.BLABLACAR,
            snapshot(listOf(e)),
            ContextualDiagnosticFilter0507(),
            listOf("VersionName: test"),
        )
        assertFalse(report.contains("COOKIE_SECRET"))
        assertTrue(report.contains("canonicalTripId=canon-1"))
        assertTrue(report.contains("ERROR"))
    }

    @Test
    fun oneBoundedGlobalStoreRemainsThePhysicalSource() {
        assertEquals(6_000, UnifiedDebugEventStore.MAX_EVENTS)
        assertTrue(DiagnosticModule0507.entries.size > 1)
    }
}
