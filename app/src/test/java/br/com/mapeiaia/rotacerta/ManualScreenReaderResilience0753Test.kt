package br.com.mapeiaia.rotacerta

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ManualScreenReaderResilience0753Test {
    private val source = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()

    @Test
    fun manualScreenReaderFallsBackFromWindowToDisplayAndAccessibility() {
        assertTrue(source.contains("takeScreenshotOfWindow(windowId0742, mainExecutor, callback0753)"))
        assertTrue(source.contains("takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, this)"))
        assertTrue(source.contains("finishWithAccessibility0753"))
        assertTrue(source.contains("MANUAL_SCREEN_TEXT_ACCESSIBILITY_FALLBACK_0753"))
    }

    @Test
    fun screenshotFailureDoesNotDiscardAlreadyCollectedAccessibilityText() {
        assertTrue(source.contains("if (accessibilityText.isNotBlank())"))
        assertTrue(source.contains("copyAllVisibleTextToClipboard138(accessibilityText)"))
        assertTrue(source.contains("MANUAL_SCREEN_SCREENSHOT_FAILED_0753"))
    }

    @Test
    fun ocrBlankResultFallsBackToAccessibilityText() {
        assertTrue(source.contains("result0742?.text.orEmpty().ifBlank { accessibilityText }"))
    }
}
