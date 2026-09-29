package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarPopupSingleCore0685ContractTest {
    @Test
    fun `accessibility service renders projection without second proximity engine`() {
        val source = sourceText("LiveRideAccessibilityService.kt")
        assertTrue(source.contains("ProximityAlertProjection0685.state.collect"))
        assertTrue(source.contains("dismissCentralProximityTarget0685"))
        assertFalse(source.contains("directionalAlertEngineChecklist5.check("))
        assertFalse(source.contains("sharedCoreOwnsGps0680"))
    }

    @Test
    fun `background core owns visual publication and acknowledgement`() {
        val source = sourceText("BackgroundLocationCore0680.kt")
        assertTrue(source.contains("ProximityAlertProjection0685.publishFix(fix)"))
        assertTrue(source.contains("ProximityAlertProjection0685.publishVisual("))
        assertTrue(source.contains("ProximityAlertProjection0685.clearVisual(targetId)"))
    }

    @Test
    fun `popup defaults to pinned and supports only explicit timeouts`() {
        val source = sourceText("DirectionalAlertOverlayController.kt")
        assertTrue(source.contains("popupTimeoutMillis: Long = 0L"))
        assertTrue(source.contains("SUPPORTED_TIMEOUT_MILLIS_0685"))
        assertTrue(source.contains("15_000L"))
        assertTrue(source.contains("20_000L"))
        assertTrue(source.contains("30_000L"))
        assertFalse(source.contains("const val ALERT_TIMEOUT_MILLIS_0647 = 20_000L"))
    }

    private fun sourceText(fileName: String): String {
        val relative = "src/main/java/br/com/mapeiaia/rotacerta/$fileName"
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Source file not found: $fileName; user.dir=" + System.getProperty("user.dir"))
    }
}
