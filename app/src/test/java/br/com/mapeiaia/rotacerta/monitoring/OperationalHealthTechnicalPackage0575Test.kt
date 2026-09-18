package br.com.mapeiaia.rotacerta.monitoring

import br.com.mapeiaia.rotacerta.DiagnosticEventContext0507
import br.com.mapeiaia.rotacerta.DiagnosticModule0507
import br.com.mapeiaia.rotacerta.DiagnosticSeverity0507
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OperationalHealthTechnicalPackage0575Test {
    @Test
    fun evidenceSelectionKeepsLocalContextAndCorrelatedEvents() {
        val unrelatedBefore = event0575(1L, "NORMAL_BEFORE")
        val target = event0575(
            at = 2L,
            stage = "PUBLIC_TRIP_LINK_UNAVAILABLE",
            diagnostic = diagnostic0575(operationId = "op-1", correlationId = "corr-1"),
        )
        val unrelatedNear = event0575(3L, "NORMAL_NEAR")
        val unrelatedFar = event0575(4L, "NORMAL_FAR")
        val correlatedFar = event0575(
            at = 5L,
            stage = "FOLLOWUP_EVENT",
            diagnostic = diagnostic0575(operationId = "op-2", correlationId = "corr-1"),
        )
        val events = listOf(unrelatedBefore, target, unrelatedNear, unrelatedFar, correlatedFar)
        val incident = OperationalIncident(
            id = "INC-TEST",
            severity = OperationalIncidentSeverity.WARNING,
            module = "Operação",
            fingerprint = OperationalHealthEngine.fingerprintForEvidence0575(target),
            firstSeenMillis = 2L,
            lastSeenMillis = 2L,
            count = 1,
            errorCode = "PUBLIC_TRIP_LINK_UNAVAILABLE",
            symptom = "PUBLIC_TRIP_LINK_UNAVAILABLE",
            probableRootCause = "teste",
            confidencePercent = 60,
            suggestedCorrection = "teste",
        )

        val selected = OperationalHealthTechnicalPackage0575.selectEvidenceEvents0575(
            events = events,
            incidents = listOf(incident),
            contextRadius = 1,
            maxEvents = 20,
        )

        assertTrue(target in selected)
        assertTrue(unrelatedBefore in selected)
        assertTrue(unrelatedNear in selected)
        assertTrue(correlatedFar in selected)
        assertFalse(unrelatedFar in selected)
    }

    @Test
    fun zipBuilderSanitizesSensitiveTextBeforeWriting() {
        val bytes = OperationalHealthTechnicalPackage0575.zipSanitized0575(
            mapOf(
                "events.ndjson" to
                    "email=teste@example.com token=abc123 url=https://example.com/path telefone=11999998888",
            ),
        )

        val extracted = ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            val entry = zip.nextEntry
            assertTrue(entry != null && entry.name == "events.ndjson")
            zip.readBytes().toString(Charsets.UTF_8)
        }

        assertTrue("[email mascarado]" in extracted)
        assertTrue("[segredo mascarado]" in extracted)
        assertTrue("[url mascarada]" in extracted)
        assertTrue("[telefone mascarado]" in extracted)
        assertFalse("teste@example.com" in extracted)
        assertFalse("abc123" in extracted)
        assertFalse("https://example.com/path" in extracted)
        assertFalse("11999998888" in extracted)
    }

    private fun event0575(
        at: Long,
        stage: String,
        diagnostic: DiagnosticEventContext0507? = null,
    ): UnifiedDebugEventStore.SnapshotEvent =
        UnifiedDebugEventStore.SnapshotEvent(
            atMillis = at,
            monotonicNs = at * 1_000_000L,
            stage = stage,
            packageName = "br.com.mapeiaia.rotacerta",
            details = "",
            threadName = "test",
            diagnosticContext = diagnostic,
        )

    private fun diagnostic0575(
        operationId: String,
        correlationId: String,
    ): DiagnosticEventContext0507 =
        DiagnosticEventContext0507(
            parentModule = DiagnosticModule0507.ALL_TRIPS,
            operation = "TEST_OPERATION",
            severity = DiagnosticSeverity0507.WARNING,
            operationId = operationId,
            correlationId = correlationId,
            errorCode = "PUBLIC_TRIP_LINK_UNAVAILABLE",
        )
}
