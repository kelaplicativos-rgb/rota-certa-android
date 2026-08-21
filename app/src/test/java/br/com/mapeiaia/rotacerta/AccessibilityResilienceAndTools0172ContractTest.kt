package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityResilienceAndTools0172ContractTest {
    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
    private val main = File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").readText()
    private val replies = File("src/main/java/br/com/mapeiaia/rotacerta/QuickRepliesActivity.kt").readText()
    private val catalog = File("src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutModule.kt").readText()
    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val tracking = File("src/main/java/br/com/mapeiaia/rotacerta/WorkTrackingService.kt").readText()

    @Test
    fun accessibilityBoundariesContainUnexpectedFailures() {
        assertTrue(service.contains("CoroutineExceptionHandler"))
        assertTrue(service.contains("handleAccessibilityEvent0172"))
        assertTrue(service.contains("UNEXPECTED_FAILURE_CONTAINED_0172"))
        assertTrue(service.contains("SERVICE_LIFECYCLE_FAILURE_CONTAINED_0172"))
        assertTrue(service.contains("invalidateServiceRuntime0172"))
        assertTrue(service.substringAfter("override fun onInterrupt").substringBefore("override fun onDestroy").contains("invalidateServiceRuntime0172"))
    }

    @Test
    fun intensiveMonitorIsTemporaryAndDoesNotCaptureScreens() {
        val tools = File("src/main/java/br/com/mapeiaia/rotacerta/RotaCertaTools0172.kt").readText()
        assertTrue(tools.contains("MAX_DURATION_MILLIS"))
        assertTrue(service.contains("delay(1_000L)"))
        val loop = service.substringAfter("startIntensiveDiagnosticLoop0172").substringBefore("override fun onInterrupt")
        assertTrue(loop.contains("IntensiveDiagnostics0172.isActive"))
        assertFalse(loop.contains("takeScreenshot"))
        assertFalse(loop.contains("ocrService.extractText"))
    }

    @Test
    fun homeBackAndNewModulesArePresent() {
        assertTrue(main.contains("BackHandler"))
        assertTrue(main.contains("QuickLinksActivity"))
        assertTrue(main.contains("MessageTemplatesActivity"))
        assertTrue(catalog.contains("QuickLinksBubbleShortcutModule"))
        assertTrue(catalog.contains("ClearApplicationCache"))
        assertTrue(catalog.contains("OpenPrimaryQuickLink"))
        assertTrue(catalog.contains("modules.size >= 22"))
        assertTrue(catalog.contains("ReadingBubbleShortcutModule"))
        assertTrue(catalog.contains("PermissionsBubbleShortcutModule"))
    }

    @Test
    fun quickReplySearchUsesSystemThemeContrast() {
        assertTrue(replies.contains("isSystemInDarkTheme"))
        assertTrue(replies.contains("focusedTextColor = MaterialTheme.colorScheme.onSurface"))
        assertTrue(replies.contains("EXTRA_QUICK_REPLY_OVERLAY_MODE_0172"))
    }

    @Test
    fun longCopyAlwaysRequestsOneManualOcrFrame() {
        val copyMethod = service.substringAfter("private fun copyAllVisibleTextFromBubble138").substringBefore("private fun collectAllVisibleTextForCopy138")
        assertTrue(copyMethod.contains("requestFullScreenCopyOcr138(accessibilityText)"))
        assertFalse(copyMethod.contains("if (accessibilityText.isNotBlank())"))
    }

    @Test
    fun manifestAndTrackingContainNewSafeComponents() {
        assertTrue(manifest.contains(".QuickLinksActivity"))
        assertTrue(manifest.contains(".MessageTemplatesActivity"))
        assertTrue(tracking.contains("WORK_TRACKING_LOCATION_FAILURE_CONTAINED_0172"))
        assertTrue(tracking.contains("catch (error0172: Exception)"))
    }
}
