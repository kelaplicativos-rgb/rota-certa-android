package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaFailureEvidence0382Test {
    private fun source(name: String): String =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/$name").readText()

    @Test
    fun allKnownAgendaThrowableProducersUseStructuredFailureEnvelope() {
        val expectations = mapOf(
            "TripsActivity.kt" to listOf(
                "DRIVER_NOTIFICATION_CENTER_REFRESH_FAILED",
                "operation = \"DRIVER_NOTIFICATION_CENTER_REFRESH\"",
                "failureEvidence=",
            ),
            "BlaBlaBrowserOrchestrator.kt" to listOf(
                "BROWSER_REQUEST_DECODE_ERROR",
                "operation = \"BROWSER_REQUEST_DECODE\"",
                "BROWSER_REQUEST_SCRIPT_ERROR",
                "operation = \"BROWSER_REQUEST_SCRIPT\"",
            ),
            "BlaBlaHarvestNavigationWatchdogProvider.kt" to listOf(
                "HARVEST_FAST_PATH_REFLECTION_FAILED",
                "operation = \"HARVEST_FAST_PATH_REFLECTION\"",
            ),
            "BlaBlaManualSeatAutomation.kt" to listOf(
                "MHTML_ARCHIVE_FAILED",
                "operation = \"MHTML_ARCHIVE_SAVE\"",
            ),
            "BlaBlaPublicSearchActivity.kt" to listOf(
                "PUBLIC_SEARCH_AUDIT_SNAPSHOT_FAILED",
                "operation = \"PUBLIC_SEARCH_AUDIT_SNAPSHOT\"",
            ),
            "TripPublicationOutbox0387.kt" to listOf(
                "TRIP_MUTATION_OUTBOX_FAILED",
                "failureSummary0387(error)",
                "exceptionClass=",
                "exceptionMessage=",
                "rootCauseClass=",
                "rootCauseMessage=",
            ),
            "RotaCertaBookingMessagingService.kt" to listOf(
                "PUBLIC_BOOKING_PUSH_REGISTER_FAILED",
                "operation = \"PUBLIC_BOOKING_PUSH_REGISTER\"",
                "PUBLIC_BOOKING_NOTIFICATION_FAILED",
                "operation = \"PUBLIC_BOOKING_NOTIFICATION\"",
            ),
        )

        expectations.forEach { (file, required) ->
            val text = source(file)
            required.forEach { marker ->
                assertTrue(text.contains(marker), "$file missing $marker")
            }
        }
    }

    @Test
    fun forensicFailureEventsDoNotUseLegacyClassOnlyPayloads() {
        val checks = mapOf(
            "TripsActivity.kt" to listOf(
                "\"reason=\" + error.javaClass.simpleName",
                "background=true errorClass=",
            ),
            "BlaBlaBrowserOrchestrator.kt" to listOf(
                "error=\${error.javaClass.simpleName}",
            ),
            "BlaBlaHarvestNavigationWatchdogProvider.kt" to listOf(
                "error=\${it.javaClass.simpleName}",
            ),
            "BlaBlaManualSeatAutomation.kt" to listOf(
                "reason=\${it.javaClass.simpleName}",
            ),
            "BlaBlaPublicSearchActivity.kt" to listOf(
                "exception=\${error.javaClass.simpleName}",
            ),
            "PassengerTimelineUi.kt" to listOf(
                "reasonClass=\${error.javaClass.simpleName}",
            ),
            "RotaCertaBookingMessagingService.kt" to listOf(
                "reason=\${error.javaClass.simpleName}",
            ),
        )

        checks.forEach { (file, forbidden) ->
            val text = source(file)
            forbidden.forEach { marker ->
                assertFalse(text.contains(marker), "$file still contains class-only diagnostic: $marker")
            }
        }
    }

    @Test
    fun forensicReportIncludesEveryStructuredAgendaFailureProducer() {
        val report = source("AgendaForensicReport.kt")
        listOf(
            "stage.startsWith(\"BROWSER_\")",
            "stage.startsWith(\"HARVEST_\")",
            "stage.startsWith(\"MHTML_\")",
            "stage.startsWith(\"PASSENGER_\")",
            "stage.startsWith(\"DRIVER_NOTIFICATION_\")",
            "stage.startsWith(\"PUBLIC_SEARCH_\")",
        ).forEach { marker ->
            assertTrue(report.contains(marker), "AgendaForensicReport missing $marker")
        }
        assertTrue(report.contains("event.details.contains(\"failureFingerprint=\")"))
    }

    @Test
    fun evidenceSanitizationComparesEveryUtf8ByteWithoutExportingRawSecrets() {
        val raw = (
            "authorization=Bearer super-secret-token " +
                "email=passenger@example.com " +
                "url=https://example.com/private?token=abc " +
                "phone=11999998888\u0000"
            ).toByteArray(Charsets.UTF_8)

        val evidence = AgendaFailureEvidence.byteSanitizationEvidence0458(raw)

        assertTrue(evidence.utf8RoundTrip)
        assertTrue(evidence.sanitizerSucceeded)
        assertTrue(evidence.sanitizationChanged)
        assertTrue(evidence.changedByteCount > 0)
        assertTrue(evidence.firstSanitizedDiffOffset >= 0)
        assertTrue(evidence.sanitizedDiffRanges.isNotEmpty())
        assertEquals(1, evidence.nulByteCount)
        assertTrue(evidence.rawSha256 != evidence.sanitizedSha256)
        val compact = evidence.compactDetails0458()
        assertFalse(compact.contains("super-secret-token"))
        assertFalse(compact.contains("passenger@example.com"))
        assertFalse(compact.contains("example.com/private"))
    }

    @Test
    fun structuredPublicationEvidenceCoversSerializationSanitizationAndCausalFailure() {
        val remote = source("TripRemoteApi.kt")
        val outbox = source("TripPublicationOutbox0387.kt")
        val timeline = source("TripTimelineUi.kt")
        val privateMirror = source("PrivateAgendaMirror0434.kt")
        val autoSync = source("PublicAgendaAutoSync0300.kt")
        val unifiedDebug = File("src/main/java/br/com/mapeiaia/rotacerta/UnifiedDebugLog.kt").readText()

        listOf(
            "stage = \"REQUEST_SERIALIZATION\"",
            "stage = \"REQUEST_SANITIZATION\"",
            "byteSanitizationEvidence0458",
            "EVIDENCE_REDACTION_APPLIED",
        ).forEach { marker -> assertTrue(remote.contains(marker), "TripRemoteApi missing $marker") }
        assertTrue(privateMirror.contains("PRIVATE_MIRROR_CANONICAL_SERIALIZATION"))
        assertTrue(autoSync.contains("DIAGNOSTIC_CONTEXT_FALLBACK"))
        assertTrue(autoSync.contains("CANONICAL_OPERATIONAL_BUILD"))
        assertTrue(autoSync.contains("DIAGNOSTIC_KEY_BUILD"))
        assertTrue(autoSync.contains("REMOTE_API_CONTEXT_BUILD"))
        assertTrue(outbox.contains("stage = \"OUTBOX_FAILURE\""))
        assertTrue(outbox.contains("UNCAUGHT_PUBLICATION_EXCEPTION"))
        assertTrue(outbox.contains("canonicalByteEvidence0458.compactDetails0458()"))
        assertTrue(timeline.contains("\\\"failure\\\":{"))
        assertTrue(timeline.contains("\\\"firstSanitizedDiffOffset\\\":"))
        assertTrue(timeline.contains("\\\"attemptState\\\":"))
        assertTrue(timeline.contains("\\\"stalePersistedFailureIgnored\\\":"))
        assertTrue(timeline.contains("latestOutboxDequeueIndex0459"))
        assertTrue(timeline.contains("causalFailureEvent0458"))
        assertTrue(unifiedDebug.contains("private fun sanitizeForRecord"))
        assertTrue(unifiedDebug.contains("runCatching { sanitizeForExport(value) }"))
        assertTrue(unifiedDebug.contains("details = safeDetails"))
        assertFalse(unifiedDebug.contains("recordFlight(stage, packageName, sanitizeForExport(details), nowMillis)"))
    }

    @Test
    fun remoteByteEnvelopeFrom0381RemainsMandatory() {
        val remote = source("TripRemoteApi.kt")
        val evidence = source("AgendaFailureEvidence.kt")
        listOf(
            "requestBytes = requestBytes.size",
            "responseBytes = responseBytes.size",
            "requestSha256 = sha256Hex(requestBytes)",
            "responseSha256 = sha256Hex(responseBytes)",
            "networkCallId = networkCallId",
            "transportPhase = phase",
        ).forEach { marker -> assertTrue(remote.contains(marker), "TripRemoteApi missing $marker") }
        listOf(
            "field(\"failureFingerprint\"",
            "field(\"networkCallId\"",
            "intField(\"requestBytes\"",
            "intField(\"responseBytes\"",
            "field(\"requestSha256\"",
            "field(\"responseSha256\"",
        ).forEach { marker -> assertTrue(evidence.contains(marker), "AgendaFailureEvidence missing $marker") }
    }
}
