package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolStage43ManualToggleRuntimeSyncTest {
    private fun source(name: String): String {
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOf(
            File(cwd, "src/main/java/br/com/mapeiaia/rotacerta/$name"),
            File(cwd, "app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
            File(cwd.parentFile ?: cwd, "app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Stage43 source not found: $name; cwd=${cwd.absolutePath}")
    }

    @Test fun contractMarkersDescribeOneSynchronizedManualState() {
        assertEquals("FAROL_MANUAL_TOGGLE_RUNTIME_SYNC_STAGE43", FarolManualToggleRuntimeSyncStage43.CONTRACT_MARKER)
        assertEquals("DATASTORE_EMISSION_APPLIES_LIVE_RUNTIME_STAGE43", FarolManualToggleRuntimeSyncStage43.FLOW_MARKER)
        assertEquals("GRID_TAP_APPLIES_RUNTIME_BEFORE_PERSIST_STAGE43", FarolManualToggleRuntimeSyncStage43.GRID_MARKER)
        assertEquals("MANUAL_OFF_IMMEDIATELY_INVALIDATES_AND_PAINTS_GRAY_STAGE43", FarolManualToggleRuntimeSyncStage43.OFF_MARKER)
        assertEquals("HOME_GRID_SERVICE_ONE_MANUAL_READING_STATE_STAGE43", FarolManualToggleRuntimeSyncStage43.SINGLE_STATE_MARKER)
    }

    @Test fun stage43StateAdapterUsesTheStage42ManualAuthority() {
        val base = AppSettings(appEnabled = true, liveReadingEnabled = true)
        val off = FarolManualToggleRuntimeSyncStage43.withEnabled(base, false)
        assertFalse(FarolManualToggleRuntimeSyncStage43.enabled(off))
        assertFalse(off.appEnabled)
        assertFalse(off.liveReadingEnabled)
        val on = FarolManualToggleRuntimeSyncStage43.withEnabled(off, true)
        assertTrue(FarolManualToggleRuntimeSyncStage43.enabled(on))
        assertTrue(on.appEnabled)
        assertTrue(on.liveReadingEnabled)
    }

    @Test fun repeatedToggleCommandsRemainDeterministic() {
        var s = AppSettings(appEnabled = false, liveReadingEnabled = false)
        s = FarolManualToggleRuntimeSyncStage43.withEnabled(s, true)
        assertTrue(FarolManualToggleRuntimeSyncStage43.enabled(s))
        s = FarolManualToggleRuntimeSyncStage43.withEnabled(s, false)
        assertFalse(FarolManualToggleRuntimeSyncStage43.enabled(s))
        s = FarolManualToggleRuntimeSyncStage43.withEnabled(s, true)
        assertTrue(FarolManualToggleRuntimeSyncStage43.enabled(s))
    }

    @Test fun settingsFlowNoLongerOnlyCopiesCurrentSettings() {
        val s = source("LiveRideAccessibilityService.kt")
        assertTrue(s.contains("repository.settings.collect { updatedStage43 ->"))
        assertTrue(s.contains("applyPersistedManualReadingStage43(updatedStage43, \"settings_flow\")"))
        assertFalse(s.contains("repository.settings.collect { currentSettings = it }"))
    }

    @Test fun serviceBootstrapUsesTheSameRuntimeTransition() {
        val s = source("LiveRideAccessibilityService.kt")
        assertTrue(s.contains("applyPersistedManualReadingStage43(repository.settings.first(), \"service_bootstrap\", forceStage43 = true)"))
        assertFalse(s.contains("currentSettings = repository.settings.first()"))
    }

    @Test fun persistedTransitionWritesCurrentSettingsBeforeApplyingRuntime() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("    private fun applyPersistedManualReadingStage43(")
        val b = s.indexOf("    private fun applyManualReadingCommandStage43(", a)
        assertTrue(a >= 0 && b > a)
        val block = s.substring(a, b)
        val assign = block.indexOf("currentSettings = updatedStage43")
        val runtime = block.indexOf("applyWorkModeRuntime0162(enabledStage43, force0162 = true)")
        assertTrue(assign >= 0)
        assertTrue(runtime > assign)
        assertTrue(block.contains("stage43LastAppliedManualReading"))
    }

    @Test fun gridTapAppliesLiveRuntimeBeforePersistence() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("    private fun applyManualReadingCommandStage43(")
        val b = s.indexOf("    private fun applyWorkModeRuntime0162(", a)
        assertTrue(a >= 0 && b > a)
        val block = s.substring(a, b)
        val apply = block.indexOf("applyPersistedManualReadingStage43(updatedStage43, sourceStage43, forceStage43 = true)")
        val persist = block.indexOf("repository.saveSettings(updatedStage43)")
        assertTrue(apply >= 0)
        assertTrue(persist > apply)
    }

    @Test fun readingGridShortcutInvokesTheStage43Command() {
        val s = source("LiveRideAccessibilityService.kt")
        val toast = s.indexOf("if (enabled0162) \"Leitura do Farol ATIVADA\" else \"Leitura do Farol DESLIGADA\"")
        val command = s.lastIndexOf("applyManualReadingCommandStage43(enabled0162, \"grid_shortcut\")", toast)
        assertTrue(toast >= 0)
        assertTrue(command >= 0 && command < toast)
    }

    @Test fun stage42ManualOffStillOwnsImmediateGrayBubble() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("    private fun applyWorkModeRuntime0162(")
        val b = s.indexOf("    private fun ensureDriverCardSession0162(", a)
        assertTrue(a >= 0 && b > a)
        val block = s.substring(a, b)
        assertTrue(block.contains("stage36RuntimeAuthority.setManualAuthority(enabled0162)"))
        assertTrue(block.contains("stage26ReadingActivation.setManualAuthority(enabled0162)"))
        assertTrue(block.contains("showOverlay(RadarColor.Idle, null)"))
        assertFalse(block.contains("removeOverlay()"))
    }

    @Test fun noRideAppPresenceGateWasReintroduced() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("    private fun refreshReadingActivationStage26(")
        val b = s.indexOf("    private fun applyReadingOffStage26(", a)
        val block = s.substring(a, b)
        listOf(
            "SelectedRideAppStore.read",
            "hasUsageAccess",
            "readIncrementalUsage",
            "currentSelectedWindowPackagesStage40",
            "readProcessShadow",
            "selectedEventStage36",
        ).forEach { assertFalse("presence authority returned: $it", block.contains(it)) }
    }

    @Test fun stage43IntroducesNoPollingTimerSleepOrContinuousOcr() {
        val h = source("FarolManualToggleRuntimeSyncStage43.kt")
        listOf("Thread.sleep(", "SystemClock.sleep(", "Timer(", "scheduleAtFixedRate(", "fixedRateTimer(", "while (true)", "requestUniversalScreenshotStage19(").forEach {
            assertFalse("forbidden Stage43 primitive: $it", h.contains(it))
        }
    }

    @Test fun stage43DoesNotChangeGoogleOrFinalPaintAuthority() {
        val s = source("LiveRideAccessibilityService.kt")
        assertTrue(s.contains("drivingDistancesFromAddressKm("))
        assertTrue(source("FarolFinalPaintFreshnessStage41.kt").contains("FAROL_SUBSECOND_SAME_FRAME_FINAL_PAINT_STAGE41"))
    }
}
