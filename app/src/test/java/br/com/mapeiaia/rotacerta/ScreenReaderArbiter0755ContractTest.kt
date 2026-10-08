package br.com.mapeiaia.rotacerta

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreenReaderArbiter0755ContractTest {
    private fun live(): String =
        File(System.getProperty("user.dir"), "src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()

    @Test
    fun manual_read_never_reports_transient_shared_screenshot_busy() {
        val source = live()
        val start = source.indexOf("private fun requestFullScreenCopyOcr138")
        val end = source.indexOf("private fun copyAllVisibleTextToClipboard138", start)
        val method = source.substring(start, end)
        assertTrue(method.contains("SCREEN_READER_ARBITER_0755"))
        assertTrue(method.contains("arbitrationAttempt0755 < 40"))
        assertTrue(method.contains("delay(50L)"))
        assertFalse(method.contains("A leitura da tela está ocupada"))
    }

    @Test
    fun farol_releases_physical_screenshot_before_shared_ocr() {
        val source = live()
        val start = source.indexOf("private fun scheduleUniversalAddressVisual0752")
        val end = source.indexOf("private data class ManualVisualTarget0742", start)
        val method = source.substring(start, end)
        val bitmap = method.indexOf("bitmap0752 = screenshot0752.toSoftwareBitmap()")
        val release = method.indexOf("screenshotInProgress.set(false)", bitmap)
        val ocr = method.indexOf("ScreenVisualReader0742(ocrService).read", bitmap)
        assertTrue(bitmap >= 0)
        assertTrue(release > bitmap)
        assertTrue(ocr > release)
    }

    @Test
    fun manual_read_releases_physical_screenshot_before_ocr() {
        val source = live()
        val start = source.indexOf("private fun requestFullScreenCopyOcr138")
        val end = source.indexOf("private fun copyAllVisibleTextToClipboard138", start)
        val method = source.substring(start, end)
        val bitmap = method.indexOf("bitmap = screenshot.toSoftwareBitmap()")
        val release = method.indexOf("screenshotInProgress.set(false)", bitmap)
        val ocr = method.indexOf("ScreenVisualReader0742(ocrService).read", bitmap)
        assertTrue(bitmap >= 0)
        assertTrue(release > bitmap)
        assertTrue(ocr > release)
    }
}
