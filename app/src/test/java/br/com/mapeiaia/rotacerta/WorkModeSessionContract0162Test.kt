package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkModeSessionContract0162Test {
    private fun source(name: String): String = listOf(
        File("src/main/java/br/com/mapeiaia/rotacerta/$name"),
        File("app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
    ).firstOrNull(File::exists)?.readText() ?: error("$name not found")

    @Test
    fun farolHasNoContinuousAccessibilityOrOcrLoop() {
        val service = source("LiveRideAccessibilityService.kt")
        val start = service.indexOf("private fun startContinuousScan")
        val end = service.indexOf("private fun startProximityAlertMonitor", start)
        val section = service.substring(start, end)
        assertTrue("event_driven_farol_0_1_162" in section)
        assertFalse("while (serviceReady)" in section)
        assertFalse("collectVisibleText(" in section)
        assertFalse("requestScreenshotAnalysis(" in section)
    }

    @Test
    fun asyncProcessingRequiresExplicitImmutablePackage() {
        val service = source("LiveRideAccessibilityService.kt")
        assertTrue("immutable_package_required_0_1_162" in service)
        assertTrue("driverCardSessionGate0162.isCurrent" in service)
        assertFalse("?: universalActiveRidePackageName?.takeIf" in service)
        assertFalse("?: normalizePackageName(universalForegroundPackageName)?.takeIf" in service)
    }

    @Test
    fun workModeAndSelectionAreVisibleAndSafe() {
        val activity = source("MainActivity.kt")
        val selectedStore = source("SelectedRideAppStore.kt")
        assertTrue("Modo Trabalho" in activity)
        assertTrue("WorkModePolicy0162.setEnabled" in activity)
        assertTrue("DriverAppPackagePolicy0162.sanitize" in selectedStore)
        assertTrue("work_mode_default_off_0_1_162" in activity)
    }

    @Test
    fun valueAndFinanceRemainOutsideAutomaticCapturePath() {
        val service = source("LiveRideAccessibilityService.kt")
        val start = service.indexOf("private fun requestScreenshotAnalysis")
        val end = service.indexOf("private fun collectVisibleText", start)
        val section = service.substring(start, end)
        assertFalse("FinancialActivity" in section)
        assertFalse("PassengerValue" in section)
        assertFalse("announceForAccessibility" in section)
        assertFalse("textToSpeech" in section)
    }
}
