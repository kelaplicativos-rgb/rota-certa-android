package br.com.mapeiaia.rotacerta.monitoring

import br.com.mapeiaia.rotacerta.DiagnosticEventContext0507
import br.com.mapeiaia.rotacerta.DiagnosticModule0507
import br.com.mapeiaia.rotacerta.DiagnosticSeverity0507
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.json.JSONObject

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
    fun ndjsonEntryPreservesRecordBoundariesAfterSanitization() {
        val bytes = OperationalHealthTechnicalPackage0575.zipSanitized0575(
            mapOf(
                "events.ndjson" to
                    "{\"stage\":\"ONE\",\"email\":\"one@example.com\"}\n" +
                    "{\"stage\":\"TWO\",\"email\":\"two@example.com\"}",
            ),
        )

        val extracted = ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            val entry = zip.nextEntry
            assertTrue(entry != null && entry.name == "events.ndjson")
            zip.readBytes().toString(Charsets.UTF_8)
        }

        val lines = extracted.lines().filter(String::isNotBlank)
        assertTrue(lines.size == 2)
        assertEquals("ONE", JSONObject(lines[0]).getString("stage"))
        assertEquals("TWO", JSONObject(lines[1]).getString("stage"))
        assertEquals("[email mascarado]", JSONObject(lines[0]).getString("email"))
        assertEquals("[email mascarado]", JSONObject(lines[1]).getString("email"))
        assertFalse("one@example.com" in extracted)
        assertFalse("two@example.com" in extracted)
    }

    @Test
    fun jsonSanitizationPreservesNumericObservabilityFieldsAndMasksOnlyStringValues() {
        val recordOverheadTotalNs = 11_999_998_888L
        val raw = JSONObject()
            .put("eventsInBuffer", 5_041)
            .put("recordCalls", 5_041L)
            .put("recordOverheadTotalNs", recordOverheadTotalNs)
            .put("recordMaxNs", 11_888_887_777L)
            .put("operatorNote", "telefone=11999998888")
            .put(
                "nested",
                JSONObject()
                    .put("generatedAtMillis", 1_758_337_083_530L)
                    .put("email", "teste@example.com"),
            )
            .toString(2)

        val bytes = OperationalHealthTechnicalPackage0575.zipSanitized0575(
            mapOf("buffer-stats.json" to raw),
        )

        val extracted = ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            val entry = zip.nextEntry
            assertTrue(entry != null && entry.name == "buffer-stats.json")
            zip.readBytes().toString(Charsets.UTF_8)
        }

        val parsed = JSONObject(extracted)
        assertEquals(5_041, parsed.getInt("eventsInBuffer"))
        assertEquals(5_041L, parsed.getLong("recordCalls"))
        assertEquals(recordOverheadTotalNs, parsed.getLong("recordOverheadTotalNs"))
        assertEquals(11_888_887_777L, parsed.getLong("recordMaxNs"))
        assertEquals("telefone=[telefone mascarado]", parsed.getString("operatorNote"))
        assertEquals(1_758_337_083_530L, parsed.getJSONObject("nested").getLong("generatedAtMillis"))
        assertEquals("[email mascarado]", parsed.getJSONObject("nested").getString("email"))
        assertFalse(parsed.getString("operatorNote").contains("11999998888"))
        assertFalse("teste@example.com" in extracted)
    }

    @Test
    fun persistedEvidenceCapsuleSanitizesSensitiveDetails() {
        val event = UnifiedDebugEventStore.SnapshotEvent(
            atMillis = 1L,
            monotonicNs = 1L,
            stage = "TEST_EVENT",
            packageName = "br.com.mapeiaia.rotacerta",
            details = "email=teste@example.com token=abc123 url=https://example.com/path telefone=11999998888",
            threadName = "main",
        )

        val json = OperationalHealthEvidenceCapsuleStore0576.eventJson0576(event).toString()

        assertTrue("[email mascarado]" in json)
        assertTrue("[segredo mascarado]" in json)
        assertTrue("[url mascarada]" in json)
        assertTrue("[telefone mascarado]" in json)
        assertFalse("teste@example.com" in json)
        assertFalse("abc123" in json)
        assertFalse("https://example.com/path" in json)
        assertFalse("11999998888" in json)
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

        val parsedFallback = JSONObject(extracted)
        assertEquals("legacy_non_json_line", parsedFallback.getString("sourceFormat"))
        assertTrue("[email mascarado]" in parsedFallback.getString("sanitizedText"))
        assertTrue("[segredo mascarado]" in parsedFallback.getString("sanitizedText"))
        assertTrue("[url mascarada]" in parsedFallback.getString("sanitizedText"))
        assertTrue("[telefone mascarado]" in parsedFallback.getString("sanitizedText"))
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
