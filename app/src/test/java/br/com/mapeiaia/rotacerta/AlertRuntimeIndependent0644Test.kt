package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertRuntimeIndependent0644Test {
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

    @Test
    fun alertAuthorityIgnoresFarolAndReadingFlags() {
        val readingOff = AppSettings(
            appEnabled = false,
            liveReadingEnabled = false,
            proximityAlertsEnabled = true,
        )
        assertTrue(AlertRuntimePolicy0644.isEnabled(readingOff))
        assertTrue(AlertRuntimePolicy0644.shouldTrack(readingOff, hasTargets = true))
    }

    @Test
    fun alertModuleToggleRemainsTheSoleFunctionalAuthority() {
        val alertsOff = AppSettings(
            appEnabled = true,
            liveReadingEnabled = true,
            proximityAlertsEnabled = false,
        )
        assertFalse(AlertRuntimePolicy0644.isEnabled(alertsOff))
        assertFalse(AlertRuntimePolicy0644.shouldTrack(alertsOff, hasTargets = true))
        assertFalse(AlertRuntimePolicy0644.shouldTrack(AppSettings(proximityAlertsEnabled = true), hasTargets = false))
    }

    @Test
    fun serviceMonitorUsesIndependentPolicyForBothSavedAlertsAndRadars() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("    private fun startProximityAlertMonitor()")
        val b = s.indexOf("    private fun checkDirectionalProximityAlertsChecklist5(", a)
        assertTrue(a >= 0 && b > a)
        val block = s.substring(a, b)
        assertTrue(block.contains("currentSavedPlaces.filter { it.type == SavedPlaceType.ProximityAlert }"))
        assertTrue(block.contains("val radars = currentImportedRadars"))
        assertTrue(block.contains("AlertRuntimePolicy0644.shouldTrack(currentSettings, hasTargets)"))
        assertFalse(block.contains("currentSettings.appEnabled"))
        assertFalse(block.contains("currentSettings.liveReadingEnabled"))
    }

    @Test
    fun directionalEngineDoesNotDependOnFarolState() {
        val s = source("DirectionalProximityAlertEngine.kt")
        assertTrue(s.contains("if (!AlertRuntimePolicy0644.isEnabled(settings))"))
        assertFalse(s.contains("!settings.appEnabled || !settings.proximityAlertsEnabled"))
        assertFalse(s.contains("settings.liveReadingEnabled"))
    }

    @Test
    fun manualReadingOffPreservesAlertAndRadarRuntimeAndSurface() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("    private fun applyManualReadingRuntimeStage43(")
        val b = s.indexOf("    private fun applyWorkModeRuntime0162(", a)
        assertTrue(a >= 0 && b > a)
        val block = s.substring(a, b)
        assertTrue(block.contains("shortcutOverlayController.hideFarolUiKeepAlerts0644()"))
        assertFalse(block.contains("shortcutOverlayController.hideAll()"))
        assertFalse(block.contains("preciseNavigationTrackerChecklist5.stop()"))
        assertFalse(block.contains("directionalAlertOverlayChecklist5.hide()"))
    }

    @Test
    fun workModeOffCannotUnconditionallyKillTheAlertTracker() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("    private fun applyWorkModeRuntime0162(")
        val b = s.indexOf("    private fun ensureDriverCardSession0162(", a)
        assertTrue(a >= 0 && b > a)
        val block = s.substring(a, b)
        assertTrue(block.contains("AlertRuntimePolicy0644.shouldTrack(currentSettings, alertTargetsPresent0644)"))
        assertTrue(block.contains("shortcutOverlayController.hideFarolUiKeepAlerts0644()"))
        assertTrue(block.contains("if (!AlertRuntimePolicy0644.shouldTrack(currentSettings, alertTargetsPresent0644))"))
    }

    @Test
    fun farolUiCleanupDoesNotDismissAnActiveAlertPopup() {
        val s = source("BubbleShortcutOverlayController.kt")
        val a = s.indexOf("    fun hideFarolUiKeepAlerts0644()")
        val b = s.indexOf("\n    fun ", a + 5).let { if (it < 0) s.length else it }
        assertTrue(a >= 0 && b > a)
        val block = s.substring(a, b)
        assertTrue(block.contains("hideShortcuts()"))
        assertFalse(block.contains("hideProximityAlert()"))
    }
}
