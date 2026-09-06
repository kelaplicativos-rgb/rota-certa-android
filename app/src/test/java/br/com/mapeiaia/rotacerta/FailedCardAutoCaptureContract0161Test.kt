package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FailedCardAutoCaptureContract0161Test {
    @Test
    fun capturePathIsSilentAndSeparatedFromValueAndFinance() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
        val start = source.indexOf("private fun requestScreenshotAnalysis")
        val end = source.indexOf("private fun collectVisibleText", start)
        val section = source.substring(start, end)
        assertTrue(section.contains("BUBBLE_FAILED_CARD_CAPTURE_STARTED"))
        assertTrue(section.contains("FailedCardTechnicalCaptureStore0161.save"))
        assertFalse(section.contains("speak("))
        assertFalse(section.contains("textToSpeech"))
        assertFalse(section.contains("announceForAccessibility"))
        assertFalse(section.contains("FinancialActivity"))
        assertFalse(section.contains("PassengerValue"))
        assertFalse(section.contains("showOverlay("))
        assertFalse(section.contains("resetToIdle("))
    }

    @Test
    fun implementationAddsNoContinuousLoopOrBackgroundService() {
        val recovery = File("src/main/java/br/com/mapeiaia/rotacerta/FailedCardRecovery0161.kt").readText()
        val store = File("src/main/java/br/com/mapeiaia/rotacerta/FailedCardCaptureStore0161.kt").readText()
        assertFalse(recovery.contains("while ("))
        assertFalse(store.contains("while ("))
        assertFalse(recovery.contains("Service()"))
        assertFalse(store.contains("Service()"))
        assertTrue(store.contains("MAX_CAPTURES = 6"))
        assertTrue(store.contains("MAX_AGE_MILLIS"))
    }

    @Test
    fun farolKeepsRoutePriorityAndBitmapIsAlwaysRecycled() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
        assertTrue(source.contains("routeInFlight = universalRouteJob?.isActive == true"))
        assertTrue(source.contains("bitmap0161?.takeUnless"))
        assertTrue(source.contains("failedCardAutoCaptureGate0161.finish(captureSignature0161)"))
        assertTrue(source.contains("screenshotInProgress.set(false)"))
        assertTrue(source.contains("TransientOverlayPackagePolicy0161.shouldPreferSelectedRoot"))
    }
}
