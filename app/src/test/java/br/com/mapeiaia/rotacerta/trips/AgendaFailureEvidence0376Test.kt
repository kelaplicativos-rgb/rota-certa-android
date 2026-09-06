package br.com.mapeiaia.rotacerta.trips

import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgendaFailureEvidence0376Test {
    @Test
    fun exceptionMessageIsPreserved() {
        val report = AgendaFailureEvidence.describe(
            error = IllegalStateException("public identity missing"),
            operation = "RESOLVE_PUBLIC_IDENTITY",
            component = "PublicAgendaAutoSync0300",
            method = "syncExternalCapacitySnapshot",
        )

        assertTrue(report.contains("exceptionClass=\"IllegalStateException\""))
        assertTrue(report.contains("exceptionMessage=\"public identity missing\""))
        assertTrue(report.contains("operation=\"RESOLVE_PUBLIC_IDENTITY\""))
    }

    @Test
    fun chainedCausePreservesOuterAndRootCause() {
        val error = IllegalStateException(
            "snapshot failed",
            RuntimeException("backend rejected capacity"),
        )
        val report = AgendaFailureEvidence.describe(
            error = error,
            operation = "EXTERNAL_CAPACITY_SNAPSHOT",
            component = "PublicAgendaAutoSync0300",
        )

        assertTrue(report.contains("exceptionMessage=\"snapshot failed\""))
        assertTrue(report.contains("rootCauseClass=\"RuntimeException\""))
        assertTrue(report.contains("rootCauseMessage=\"backend rejected capacity\""))
        assertTrue(report.contains("IllegalStateException(snapshot failed) -> RuntimeException(backend rejected capacity)"))
    }

    @Test
    fun tripAndCapacityContextIsPreservedWithoutReReadingMutableState() {
        val trip = AgendaFailureTripContext(
            tripKey = "8749d61a2ebf",
            canonicalIdentity = "profile|id:trip-123",
            publicIdentity = "public-trip-456",
            origin = "EXTERNAL_BACKING",
            route = "São Paulo -> São Tomé das Letras",
            date = "2026-09-04",
            time = "10:30",
            blablaQuota = 3,
            rotaCertaQuota = 2,
            operationalInventory = 5,
            confirmedSeats = 3,
            realAvailableSeats = 2,
            revision = "externalcap-v1:abc123",
            signature = "sig-789",
        )
        val report = AgendaFailureEvidence.describe(
            error = IllegalStateException("capacity publish failed"),
            operation = "PUBLISH_INCREMENTAL_CAPACITY",
            component = "PublicAgendaAutoSync0300",
            trip = trip,
        )

        listOf(
            "tripKey=\"8749d61a2ebf\"",
            "origin=\"EXTERNAL_BACKING\"",
            "date=\"2026-09-04\"",
            "time=\"10:30\"",
            "blablaQuota=3",
            "rotaCertaQuota=2",
            "operationalInventory=5",
            "confirmedSeats=3",
            "realAvailableSeats=2",
            "revision=\"externalcap-v1:abc123\"",
        ).forEach { expected -> assertTrue("missing $expected in $report", report.contains(expected)) }
    }

    @Test
    fun backendFailureCarriesHttpMetadataAndSanitizedResponse() {
        val response = UnifiedDebugEventStore.sanitizeForExport(
            "{\"code\":\"PUBLIC_TRIP_NOT_FOUND\",\"message\":\"public identity not found\",\"access_token\":\"SECRET_ACCESS\"}",
        )
        val error = TripRemoteApiException(
            httpMethod = "PUT",
            endpoint = "/v1/driver/trips/public-trip-456/capacity-snapshot",
            httpStatus = 404,
            backendErrorCode = "PUBLIC_TRIP_NOT_FOUND",
            sanitizedResponse = response,
            requestId = "req-123",
            correlationId = "corr-456",
        )
        val report = AgendaFailureEvidence.describe(
            error = error,
            operation = "PUBLISH_INCREMENTAL_CAPACITY",
            component = "TripRemoteApi",
            method = "request",
        )

        assertTrue(report.contains("httpStatus=404"))
        assertTrue(report.contains("httpMethod=\"PUT\""))
        assertTrue(report.contains("backendErrorCode=\"PUBLIC_TRIP_NOT_FOUND\""))
        assertTrue(report.contains("requestId=\"req-123\""))
        assertTrue(report.contains("correlationId=\"corr-456\""))
        assertTrue(report.contains("public identity not found"))
        assertFalse(report.contains("SECRET_ACCESS"))
    }

    @Test
    fun sanitizerMasksHeadersBearerCookiesAndTokenVariants() {
        val raw = "Authorization: Bearer SECRET_TOKEN Cookie: SESSION_SECRET Set-Cookie: AUTH_COOKIE " +
            "access_token=ACCESS_SECRET refresh_token=REFRESH_SECRET password=PASSWORD_SECRET api_key=API_SECRET"
        val report = AgendaFailureEvidence.describe(
            error = IllegalStateException(raw),
            operation = "PUBLIC_AGENDA_SYNC",
            component = "PublicAgendaAutoSync0300",
        )

        listOf(
            "SECRET_TOKEN",
            "SESSION_SECRET",
            "AUTH_COOKIE",
            "ACCESS_SECRET",
            "REFRESH_SECRET",
            "PASSWORD_SECRET",
            "API_SECRET",
        ).forEach { secret -> assertFalse("secret leaked: $secret in $report", report.contains(secret)) }
        assertTrue(report.contains("segredo mascarado"))
    }

    @Test
    fun stackIncludesRotaCertaFrameAndSourceLocation() {
        val error = captureStackFailure()
        val report = AgendaFailureEvidence.describe(
            error = error,
            operation = "EXTERNAL_CAPACITY_SNAPSHOT",
            component = "AgendaFailureEvidence0376Test",
        )

        assertTrue(report.contains("source=\"AgendaFailureEvidence0376Test.kt:captureStackFailure:"))
        assertTrue(report.contains("stackTrace=\""))
        assertTrue(report.contains("captureStackFailure"))
    }

    @Test
    fun nullMessageIsExplicitAndDoesNotBreakReport() {
        val report = AgendaFailureEvidence.describe(
            error = IllegalStateException(),
            operation = "CAPACITY_PUBLIC_SYNC",
            component = "PublicAgendaSyncCoordinator0373",
        )

        assertTrue(report.contains("exceptionMessage=\"<null>\""))
        assertTrue(report.contains("rootCauseMessage=\"<null>\""))
    }

    @Test
    fun causeResolutionStopsOnCycleAndDepthLimit() {
        val first = IllegalStateException("first")
        val second = RuntimeException("second")
        val cycle = AgendaFailureEvidence.resolveCauseChain(first) { current ->
            when (current) {
                first -> second
                second -> first
                else -> null
            }
        }
        assertTrue(cycle.cycleDetected)
        assertFalse(cycle.depthTruncated)
        assertTrue(cycle.chain.size == 2)

        var deep: Throwable = RuntimeException("root")
        repeat(12) { index -> deep = IllegalStateException("level-$index", deep) }
        val truncated = AgendaFailureEvidence.resolveCauseChain(deep)
        assertFalse(truncated.cycleDetected)
        assertTrue(truncated.depthTruncated)
        assertTrue(truncated.chain.size <= 8)
    }

    private fun captureStackFailure(): Throwable = try {
        throw IllegalStateException("stack location")
    } catch (error: Throwable) {
        error
    }
}
