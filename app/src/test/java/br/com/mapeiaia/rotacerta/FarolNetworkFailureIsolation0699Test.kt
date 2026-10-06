package br.com.mapeiaia.rotacerta

import java.io.IOException
import java.io.File
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolNetworkFailureIsolation0699Test {
    private fun root(): File {
        val cwd = File(System.getProperty("user.dir"))
        return if (File(cwd, "app/src/main/java").isDirectory) cwd
        else if (cwd.name == "app" && File(cwd, "src/main/java").isDirectory) cwd.parentFile
        else cwd
    }

    private fun src(name: String): String =
        File(root(), "app/src/main/java/br/com/mapeiaia/rotacerta/$name").readText()

    @Test
    fun ioFailuresAreRecoverableButCancellationIsNot() {
        assertTrue(FarolNetworkFailureIsolation0699.isRecoverableProviderFailure(SocketTimeoutException("timeout")))
        assertTrue(FarolNetworkFailureIsolation0699.isRecoverableProviderFailure(IOException("offline")))
        assertTrue(
            FarolNetworkFailureIsolation0699.isRecoverableProviderFailure(
                IllegalStateException("wrapper", IOException("network")),
            ),
        )
        assertTrue(FarolNetworkFailureIsolation0699.isRecoverableProviderFailure(SerializationException("payload")))
        assertTrue(FarolNetworkFailureIsolation0699.isRecoverableTransportFailure(SocketTimeoutException("timeout")))
        assertFalse(FarolNetworkFailureIsolation0699.isRecoverableTransportFailure(SerializationException("payload")))
        assertFalse(FarolNetworkFailureIsolation0699.isRecoverableProviderFailure(CancellationException("cancel")))
        assertFalse(FarolNetworkFailureIsolation0699.isRecoverableProviderFailure(IllegalStateException("bug")))
    }

    @Test
    fun requestContextCarriesSemanticLeaseAndTrace() {
        val token = FarolRuntimeAuthorityStage36.WorkToken(
            readingEpoch = 7L,
            leaseId = 42L,
            destinationKey = "rua padre pompeu de almeida",
        )
        val context = FarolNetworkFailureIsolation0699.requestContext(
            token = token,
            traceId = "trace-0699",
            operationId = "route-0699",
            destinationAddress = "Rua Padre Pompeu de Almeida",
        )
        val details = context.diagnostic("google_geocode", "elapsedMs=600")
        assertTrue(details.contains("trace=trace-0699"))
        assertTrue(details.contains("operation=route-0699"))
        assertTrue(details.contains("epoch=7"))
        assertTrue(details.contains("lease=42"))
        assertTrue(details.contains("destinationKey=rua padre pompeu de almeida"))
        assertTrue(details.contains("provider=google_geocode"))
    }

    @Test
    fun google0697DirectPathCannotLeakRecoverableTransportFailure() {
        val google = src("GoogleMapsService.kt")
        val resolverStart = google.indexOf("private suspend fun resolveGoogleOrigin0697(")
        val resolverEnd = google.indexOf("private fun cacheCoordinateAliases0697(", resolverStart)
        assertTrue(resolverStart >= 0 && resolverEnd > resolverStart)
        val resolver = google.substring(resolverStart, resolverEnd)
        assertTrue(resolver.contains("requestGeocode(query, apiKey, requestContext0699, boundsBias0703)"))
        assertTrue(resolver.contains("targetBiasBounds0703(targetHints)"))
        assertTrue(resolver.contains("GOOGLE_GEOCODE_UNAVAILABLE_MARKER"))

        val requestStart = google.indexOf("private fun requestGeocode(")
        val requestEnd = google.indexOf("private fun parseCoordinate(", requestStart)
        assertTrue(requestStart >= 0 && requestEnd > requestStart)
        val request = google.substring(requestStart, requestEnd)
        assertTrue(request.contains("isRecoverableProviderFailure(error0699)"))
        assertTrue(request.contains("GOOGLE_GEOCODE_TRANSPORT_FAILED_MARKER"))
        assertTrue(request.contains("null"))
    }

    @Test
    fun lateCoordinateWorkIsRejectedBeforeItCanChangeNewCardState() {
        val live = src("LiveRideAccessibilityService.kt")
        val analyzeStart = live.indexOf("private suspend fun analyzeUniversalTwoAddressStage19(")
        val localCall = live.indexOf("val localDistances0696 = localDistancesFromAddressKm(", analyzeStart)
        val freshness = live.indexOf("val localFreshness0698 = stage19LocalSemanticVerdict0698(bindingStage19)", localCall)
        val decision = live.indexOf("val localResult0696 = decideFastWorkRegionChecklist13(", localCall)
        val unresolved = live.indexOf("stage696_local_coordinate_unresolved", localCall)
        assertTrue(analyzeStart >= 0 && localCall > analyzeStart)
        assertTrue(freshness > localCall)
        assertTrue(decision > freshness)
        assertTrue(unresolved > decision)
        val guard = live.substring(freshness, decision)
        assertTrue(guard.contains("LATE_RESULT_DROPPED_MARKER"))
        assertTrue(guard.contains("return"))
    }

    @Test
    fun remoteRefinementFailureIsSoftAndKeepsLocalKmPrivate() {
        val live = src("LiveRideAccessibilityService.kt")
        val analyzeStart = live.indexOf("private suspend fun analyzeUniversalTwoAddressStage19(")
        val remoteStart = live.indexOf("val remoteToken0699 =", analyzeStart)
        val remoteEnd = live.indexOf("val refinedResult0696 =", remoteStart)
        assertTrue(remoteStart > analyzeStart && remoteEnd > remoteStart)
        val remote = live.substring(remoteStart, remoteEnd)
        assertTrue(remote.contains("REMOTE_REFINEMENT_FAILED_SOFT_MARKER"))
        assertTrue(remote.contains("catch (cancelled0699: kotlinx.coroutines.CancellationException)"))
        assertTrue(remote.contains("throw cancelled0699"))
        assertTrue(remote.contains("exactRoadDistancesCandidate0699 ?: run"))
        assertTrue(remote.contains("KM permanece oculto"))
        assertTrue(remote.contains("REMOTE_REFINEMENT_FAILED_SOFT_MARKER"))
        assertFalse(remote.contains("hardClearUniversalTwoAddress"))
    }

    @Test
    fun rootHandlerCannotHardClearForRecoverableProviderFailure() {
        val live = src("LiveRideAccessibilityService.kt")
        val start = live.indexOf("private fun containUnexpectedFailure0172(")
        val end = live.indexOf("private fun containLifecycleFailure0172(", start)
        assertTrue(start >= 0 && end > start)
        val block = live.substring(start, end)
        val recoverable = block.indexOf("isRecoverableTransportFailure(error0172)")
        val preserve = block.indexOf("NETWORK_FAILURE_STATE_PRESERVED_MARKER")
        val earlyReturn = block.indexOf("return", preserve)
        val noObservationPreserve = block.indexOf("preserveUniversalTwoAddressOnNoObservation0740")
        assertTrue(recoverable >= 0)
        assertTrue(preserve > recoverable)
        assertTrue(earlyReturn > preserve)
        assertTrue(noObservationPreserve > earlyReturn)
        assertFalse(block.contains("hardClearUniversalTwoAddress"))
    }

    @Test
    fun unresolvedCurrentCardStillUsesFormalAllProvidersFailedPath() {
        val live = src("LiveRideAccessibilityService.kt")
        val start = live.indexOf("private suspend fun localDistancesFromAddressKm(")
        val end = live.indexOf("private suspend fun routeDistancesFromAddressKm(", start)
        assertTrue(start >= 0 && end > start)
        val block = live.substring(start, end)
        assertTrue(block.contains("FarolCoordinateResolution0697.ALL_FAILED_MARKER"))
        assertTrue(block.contains("return_null_keep_yellow"))
        assertFalse(block.contains("hardClearUniversalTwoAddress"))
    }
}
