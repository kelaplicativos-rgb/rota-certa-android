package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolCoordinateResilience0697ContractTest {
    private fun root(): File {
        val cwd = File(System.getProperty("user.dir"))
        return if (File(cwd, "app/src/main/java").isDirectory) cwd
        else if (cwd.name == "app" && File(cwd, "src/main/java").isDirectory) cwd.parentFile
        else cwd
    }

    private fun src(name: String): String =
        File(root(), "app/src/main/java/br/com/mapeiaia/rotacerta/" + name).readText()

    @Test
    fun incidentAddressIsSanitizedBeforeGeocoding() {
        val result = FarolRouteAddressSanitizer0684.sanitize(
            "Avenida dos Nacionalistas Agora mesmo, 564 (Jardim Tango)",
        )
        assertTrue(result.accepted)
        assertEquals(
            "Avenida dos Nacionalistas, 564 (Jardim Tango)",
            result.sanitized,
        )
        assertTrue(result.cuts.any { it.startsWith("temporal_ui_noise_0697:") })
    }

    @Test
    fun android16PathUsesCallbackGeocoderWithDeadline() {
        val android = src("AndroidServices.kt")
        val start = android.indexOf("suspend fun geocodeBounded0697(")
        val end = android.indexOf("suspend fun reverseGeocode", start)
        assertTrue(start >= 0 && end > start)
        val block = android.substring(start, end)
        assertTrue(block.contains("Build.VERSION_CODES.TIRAMISU"))
        assertTrue(block.contains("Geocoder.GeocodeListener"))
        assertTrue(block.contains("suspendCancellableCoroutine"))
        assertTrue(block.contains("withTimeoutOrNull(timeoutMillis)"))
        assertTrue(block.contains("runInterruptible"))
    }

    @Test
    fun coordinateProvidersAreOfflineFirstThenHedgedAndGloballyBounded0751() {
        val maps = src("GoogleMapsService.kt")
        val capture = src("FarolCaptureFabric0751.kt")
        val start = maps.indexOf("suspend fun resolveFarolCoordinateResilient0697(")
        val end = maps.indexOf("suspend fun drivingDistanceKm", start)
        assertTrue(start >= 0 && end > start)
        val block = maps.substring(start, end)

        val organic = block.indexOf("organicMapsOfflineResolver0711?.resolve")
        val hedge = block.indexOf("GEOCODE_HEDGE_STARTED_MARKER")
        val platform = block.indexOf("\"android\" to async")
        val google = block.indexOf("\"google\" to async")
        val osm = block.indexOf("\"osm\" to async")

        assertTrue(organic >= 0)
        assertTrue(hedge > organic)
        assertTrue(platform > hedge)
        assertTrue(google > platform)
        assertTrue(osm > google)
        assertTrue(block.contains("withTimeoutOrNull(FarolCaptureFabric0751.OFFLINE_GEOCODE_BUDGET_MS)"))
        assertTrue(block.contains("withTimeoutOrNull(FarolCaptureFabric0751.HEDGED_GEOCODE_DEADLINE_MS)"))
        assertTrue(block.contains("if (apiKey.isNotBlank())"))
        assertTrue(block.contains("else {\n                providers0751 += \"osm\""))
        assertTrue(capture.contains("const val OFFLINE_GEOCODE_BUDGET_MS = 180L"))
        assertTrue(capture.contains("const val HEDGED_GEOCODE_DEADLINE_MS = 1_250L"))
    }

    @Test
    fun criticalLocalDistancePathNoLongerUsesLegacyInstant642() {
        val live = src("LiveRideAccessibilityService.kt")
        val start = live.indexOf("private suspend fun localDistancesFromAddressKm(")
        val end = live.indexOf("private suspend fun routeDistancesFromAddressKm(", start)
        assertTrue(start >= 0 && end > start)
        val block = live.substring(start, end)
        assertTrue(block.contains("resolveFarolCoordinateResilient0697"))
        assertFalse(block.contains("resolveFarolCoordinateInstant642"))
        assertTrue(block.contains("FAROL_TEMPORAL_UI_NOISE_REMOVED_0697") ||
            block.contains("FarolTemporalUiNoise0697.REMOVED_MARKER"))
    }

    @Test
    fun sanitizedSignatureOwnsDownstreamBinding() {
        val live = src("LiveRideAccessibilityService.kt")
        assertTrue(live.contains("stableSanitizedAddressSignature0697"))
        assertTrue(live.contains("universalActiveAddressSignature = stableSanitizedAddressSignature0697"))
        assertTrue(live.contains("addressSignature = stableSanitizedAddressSignature0697"))
    }
}
