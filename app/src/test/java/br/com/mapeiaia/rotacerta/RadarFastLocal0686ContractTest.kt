package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarFastLocal0686ContractTest {
    @Test
    fun `work tracking consumes local GPS faster without changing five second sharing cadence`() {
        val source = sourceText("WorkTrackingService.kt")
        assertTrue(source.contains("RadarSafetyPolicy0686.LOCAL_LOCATION_INTERVAL_MS"))
        assertTrue(source.contains("RadarSafetyPolicy0686.LOCAL_MIN_UPDATE_INTERVAL_MS"))
        assertTrue(source.contains("RadarSafetyPolicy0686.shouldPersistSharedPoint("))
        assertTrue(source.contains("private const val HEARTBEAT_INTERVAL_MS = 5_000L"))
        assertFalse(source.contains("private const val UPDATE_INTERVAL_MS = 5_000L"))
        assertFalse(source.contains("private const val MIN_UPDATE_INTERVAL_MS = 5_000L"))
    }

    @Test
    fun `background core prearms radars and gives passed state a five second close`() {
        val source = sourceText("BackgroundLocationCore0680.kt")
        assertTrue(source.contains("RadarSafetyPolicy0686.PREARM_RADIUS_METERS"))
        assertTrue(source.contains("RadarSafetyPolicy0686.PASSED_AUTO_CLOSE_MILLIS"))
        assertTrue(source.contains("schedulePassedAutoDismiss0686"))
    }

    @Test
    fun `engine no longer waits for multiple approach samples and protects between fix crossing`() {
        val source = sourceText("DirectionalProximityAlertEngine.kt")
        assertTrue(source.contains("RadarSafetyPolicy0686.segmentCrossesTarget("))
        assertTrue(source.contains("val eligible = distance <= threshold && !runtime.passed"))
        assertFalse(source.contains("REQUIRED_APPROACHING_SAMPLES"))
    }

    private fun sourceText(fileName: String): String {
        val relative = "src/main/java/br/com/mapeiaia/rotacerta/$fileName"
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Source file not found: $fileName; user.dir=" + System.getProperty("user.dir"))
    }
}
