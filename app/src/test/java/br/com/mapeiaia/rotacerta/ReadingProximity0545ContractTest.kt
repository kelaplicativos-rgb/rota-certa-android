package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingProximity0545ContractTest {
    private fun source(name: String): String {
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOf(
            File(cwd, "src/main/java/br/com/mapeiaia/rotacerta/$name"),
            File(cwd, "app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
            File(cwd.parentFile ?: cwd, "app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("source not found: $name; cwd=${cwd.absolutePath}")
    }

    @Test fun manualReadingRuntimeDoesNotOwnProximityLifecycle() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("    private fun applyManualReadingRuntimeStage43(")
        val b = s.indexOf("    private fun applyWorkModeRuntime0162(", a)
        assertTrue(a >= 0 && b > a)
        val block = s.substring(a, b)
        assertTrue(block.contains("MANUAL_READING_RUNTIME_STAGE43"))
        assertFalse(block.contains("preciseNavigationTrackerChecklist5.stop()"))
        assertFalse(block.contains("directionalAlertOverlayChecklist5.hide()"))
        assertFalse(block.contains("workModeRuntimeActive0162 ="))
    }

    @Test fun transientNoCandidateUsesExistingStage44LeaseBeforeClearing() {
        val s = source("LiveRideAccessibilityService.kt")
        assertTrue(s.contains("S44_TRANSIENT_NO_CANDIDATE_FINAL_PRESERVED"))
        assertTrue(s.contains("transientLeaseStage44.activeFinal && transientPresenceStage44.active"))
        assertTrue(s.contains("sem lease Stage44 ativa"))
    }

    @Test fun routeMatrixHttpFailureIsObservableAndUsesExistingRouteFallback() {
        val s = source("GoogleMapsService.kt")
        assertTrue(s.contains("ROUTE_MATRIX_HTTP_ERROR"))
        assertTrue(s.contains("sanitizeMapsErrorBody"))
        assertTrue(s.contains("ROUTE_MATRIX_FALLBACK_GEOCODE_STARTED"))
        assertTrue(s.contains("requestDrivingDistance(coordinateRouteBody(origin, destination), apiKey)"))
    }
}
