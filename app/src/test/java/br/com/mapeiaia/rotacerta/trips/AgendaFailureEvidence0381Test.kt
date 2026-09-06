package br.com.mapeiaia.rotacerta.trips

import java.io.File
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgendaFailureEvidence0381Test {
    @Test
    fun transportFailureCarriesEndToEndByteEvidence() {
        val error = TripRemoteApiException(
            httpMethod = "POST",
            endpoint = "/v1/driver/agenda/ensure",
            httpStatus = 0,
            backendErrorCode = "",
            sanitizedResponse = "",
            requestId = "req-381",
            correlationId = "corr-381",
            networkCallId = "net-381",
            transportPhase = "response_body_read",
            requestBytes = 317,
            responseBytes = 0,
            requestSha256 = "a".repeat(64),
            responseSha256 = "b".repeat(64),
            sanitizedRequest = "{\"driverDisplayName\":\"Motorista\"}",
            responseContentType = "application/json",
            elapsedMs = 12_345L,
            cause = SocketTimeoutException("Read timed out"),
        )

        val report = AgendaFailureEvidence.describe(
            error = error,
            operation = "PUBLIC_AGENDA_SYNC",
            component = "TripRemoteApi",
            method = "request",
            timestampMillis = 1_000L,
        )

        listOf(
            "failureFingerprint=\"",
            "networkCallId=\"net-381\"",
            "transportPhase=\"response_body_read\"",
            "httpMethod=\"POST\"",
            "endpoint=\"/v1/driver/agenda/ensure\"",
            "requestBytes=317",
            "responseBytes=0",
            "requestSha256=\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"",
            "responseSha256=\"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\"",
            "networkElapsedMs=12345",
            "requestId=\"req-381\"",
            "correlationId=\"corr-381\"",
            "rootCauseClass=\"SocketTimeoutException\"",
            "rootCauseMessage=\"Read timed out\"",
        ).forEach { expected ->
            assertTrue(report.contains(expected), "missing $expected in $report")
        }
        assertFalse(report.contains("httpStatus=0"))
    }

    @Test
    fun fingerprintIsStableAcrossDuplicateLayersAndTimestamps() {
        val error = TripRemoteApiException(
            httpMethod = "GET",
            endpoint = "/v1/driver/trips/trip-1/bookings",
            httpStatus = 503,
            backendErrorCode = "UNAVAILABLE",
            sanitizedResponse = "{\"code\":\"UNAVAILABLE\"}",
            requestId = "",
            correlationId = "",
            networkCallId = "net-same",
            transportPhase = "http_status",
            requestBytes = 0,
            responseBytes = 22,
            requestSha256 = "0".repeat(64),
            responseSha256 = "1".repeat(64),
        )
        val one = AgendaFailureEvidence.describe(
            error = error,
            operation = "BOOKING_REMOTE_FETCH",
            component = "PublicBookingRemoteSync0296",
            timestampMillis = 10L,
        )
        val two = AgendaFailureEvidence.describe(
            error = error,
            operation = "BOOKING_REMOTE_FETCH",
            component = "PublicBookingRemoteSync0296",
            timestampMillis = 20L,
        )

        fun fingerprint(text: String): String? =
            Regex("failureFingerprint=\\\"([0-9a-f]+)\\\"").find(text)?.groupValues?.getOrNull(1)

        val left = fingerprint(one)
        val right = fingerprint(two)
        assertNotNull(left)
        assertEquals(left, right)
    }

    @Test
    fun remoteApiWrapsEveryTransportPhaseAndHashesExactUtf8Bytes() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripRemoteApi.kt").readText()

        listOf(
            "validate_configuration",
            "open_connection",
            "request_body_write",
            "response_status",
            "response_body_read",
            "http_status",
            "decode_json",
            "input.readBytes()",
            "requestBytes = requestBytes.size",
            "responseBytes = responseBytes.size",
            "requestSha256 = sha256Hex(requestBytes)",
            "responseSha256 = sha256Hex(responseBytes)",
            "sanitizeForExport(requestText)",
            "catch (error: Throwable)",
            "cause = cause",
        ).forEach { expected ->
            assertTrue(source.contains(expected), "TripRemoteApi missing $expected")
        }
    }

    @Test
    fun forensicReportHasDedicatedStructuredFailureSectionAndAgendaFailurePrefixes() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaForensicReport.kt").readText()

        assertTrue(source.contains("--- EVIDÊNCIAS ESTRUTURADAS DE FALHA ---"))
        assertTrue(source.contains("falhas com envelope byte a byte"))
        assertTrue(source.contains("stage.startsWith(\"PUBLIC_BOOKING_\")"))
        assertTrue(source.contains("stage.startsWith(\"PUBLIC_LOCAL_\")"))
        assertTrue(source.contains("stage.startsWith(\"PUBLIC_CAPACITY_\")"))
        assertTrue(source.contains("structuredDetail(event, \"requestBytes\")"))
        assertTrue(source.contains("structuredDetail(event, \"responseBytes\")"))
    }
}
