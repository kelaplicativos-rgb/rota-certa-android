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

    @Test fun contractMarkersDescribeOneSynchronizedManualStateAndPhysicalOffProof() {
        assertEquals("FAROL_MANUAL_TOGGLE_RUNTIME_SYNC_STAGE43", FarolManualToggleRuntimeSyncStage43.CONTRACT_MARKER)
        assertEquals("DATASTORE_EMISSION_APPLIES_LIVE_RUNTIME_STAGE43", FarolManualToggleRuntimeSyncStage43.FLOW_MARKER)
        assertEquals("GRID_TAP_APPLIES_RUNTIME_BEFORE_PERSIST_STAGE43", FarolManualToggleRuntimeSyncStage43.GRID_MARKER)
        assertEquals("MANUAL_OFF_IMMEDIATELY_INVALIDATES_AND_PAINTS_GRAY_STAGE43", FarolManualToggleRuntimeSyncStage43.OFF_MARKER)
        assertEquals("HOME_GRID_SERVICE_ONE_MANUAL_READING_STATE_STAGE43", FarolManualToggleRuntimeSyncStage43.SINGLE_STATE_MARKER)
        assertEquals("FORCED_OFF_BYPASSES_IDEMPOTENT_RENDER_SKIP_STAGE43", FarolManualToggleRuntimeSyncStage43.FORCE_RENDER_MARKER)
        assertEquals("OFF_RENDER_APPLIED_REQUIRED_STAGE43", FarolManualToggleRuntimeSyncStage43.APPLIED_REQUIRED_MARKER)
        assertEquals("OFF_RENDER_MISMATCH_IS_REPORT_FAIL_STAGE43", FarolManualToggleRuntimeSyncStage43.REPORT_FAIL_MARKER)
        assertEquals("MANUAL_OFF_PHYSICAL_VIEW_COMMIT_STAGE43", FarolManualOffVisualCommitStage43.CONTRACT_MARKER)
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

    @Test fun settingsFlowAlwaysEntersTheSingleRuntimeTransitionWhenReady() {
        val s = source("LiveRideAccessibilityService.kt")
        assertTrue(s.contains("repository.settings.collect { updatedStage43 ->"))
        assertTrue(s.contains("if (!workModeSettingsReady0162) return@collect"))
        assertTrue(s.contains("applyPersistedManualReadingStage43(updatedStage43, \"settings_flow\")"))
        assertFalse(s.contains("val previousEnabled0162 = WorkModePolicy0162.isEnabled(currentSettings)"))
    }

    @Test fun serviceBootstrapUsesSameTransitionAfterMigrationsAreReady() {
        val s = source("LiveRideAccessibilityService.kt")
        val ready = s.indexOf("workModeSettingsReady0162 = true")
        val apply = s.indexOf("applyPersistedManualReadingStage43(currentSettings, \"service_bootstrap\", forceStage43 = true)", ready)
        assertTrue(ready >= 0)
        assertTrue(apply > ready)
        assertTrue(s.contains("currentSettings = repository.settings.first()"))
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

    @Test fun readingGridShortcutInvokesExactlyOneStage43Command() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("    private fun toggleLiveReadingFromBubble()")
        val b = s.indexOf("    private fun stopApplicationFromBubble()", a)
        assertTrue(a >= 0 && b > a)
        val block = s.substring(a, b)
        assertEquals(1, Regex("applyManualReadingCommandStage43\\(enabled0162, \\\"grid_shortcut\\\"\\)").findAll(block).count())
        assertFalse(block.contains("applyWorkModeRuntime0162(enabled0162)"))
        assertFalse(block.contains("repository.saveSettings(updated0162)"))
    }

    @Test fun manualOffForcesRealGrayCommitWithoutPremutatingLogicalColor() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("    private fun applyWorkModeRuntime0162(")
        val b = s.indexOf("    private fun ensureDriverCardSession0162(", a)
        assertTrue(a >= 0 && b > a)
        val block = s.substring(a, b)
        assertTrue(block.contains("stage36RuntimeAuthority.setManualAuthority(enabled0162)"))
        assertTrue(block.contains("stage26ReadingActivation.setManualAuthority(enabled0162)"))
        assertTrue(block.contains("showOverlay(RadarColor.Idle, null, forcePhysicalCommitStage43 = true)"))
        assertTrue(block.contains("S43_MANUAL_OFF_RENDER_COMMIT"))
        assertTrue(block.contains("S43_MANUAL_OFF_RENDER_ANOMALY"))
        assertFalse(block.contains("currentRadarColor = RadarColor.Idle"))
        assertFalse(block.contains("currentDistanceKm = null"))
        assertFalse(block.contains("removeOverlay()"))
    }

    @Test fun forcedOffBypassesOnlyTheLegacyIdempotentSameValueSkip() {
        val s = source("LiveRideAccessibilityService.kt")
        assertTrue(s.contains("private fun renderOverlayStage40(color: RadarColor, distanceKm: Double? = null, forcePhysicalCommitStage43: Boolean = false)"))
        assertTrue(s.contains("if (!forcePhysicalCommitStage43 && existingViewChecklist15 != null && currentRadarColor == color"))
        assertTrue(s.contains("overlayIdempotentSkipped"))
    }

    @Test fun stage40VisualAuthorityStillDecidesColorAndForwardsTheForceFlag() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("    private fun showOverlay(color: RadarColor")
        val b = s.indexOf("    private fun renderOverlayStage40(", a)
        val block = s.substring(a, b)
        assertTrue(block.contains("FarolVisualStateAuthorityStage40.decide("))
        assertTrue(block.contains("renderOverlayStage40(effectiveColorStage40, decisionStage40.distanceKm, forcePhysicalCommitStage43)"))
        assertTrue(block.contains("S40_VISUAL_AUTHORITY_DECISION"))
    }

    @Test fun physicalOffProofSerialAdvancesOnlyAfterOverlayApplied() {
        val s = source("LiveRideAccessibilityService.kt")
        val applied = s.indexOf("FarolForensicTraceStage20.overlayApplied(")
        val serial = s.indexOf("stage43OffRenderAppliedSerial += 1L", applied)
        assertTrue(applied >= 0)
        assertTrue(serial > applied)
        val before = s.lastIndexOf("stage43OffRenderAppliedSerial", applied)
        assertTrue(before >= 0)
    }

    @Test fun diagnosticVerdictCannotCallMissingOffCommitPass() {
        FarolManualOffVisualCommitStage43.resetForTests()
        assertEquals("NOT_TESTED", FarolManualOffVisualCommitStage43.snapshot().status)
        assertTrue(FarolManualOffVisualCommitStage43.exportReport().contains("status=NOT_TESTED"))
        FarolManualOffVisualCommitStage43.recordAttempt(true)
        assertEquals("PASS", FarolManualOffVisualCommitStage43.snapshot().status)
        assertTrue(FarolManualOffVisualCommitStage43.exportReport().contains("status=PASS"))
        FarolManualOffVisualCommitStage43.recordAttempt(false)
        assertEquals("FAIL", FarolManualOffVisualCommitStage43.snapshot().status)
        assertTrue(FarolManualOffVisualCommitStage43.exportReport().contains("anomalies=1"))
        FarolManualOffVisualCommitStage43.resetForTests()
    }

    @Test fun manualTechnicalReportExportsStage43PhysicalVerdict() {
        val r = source("ManualTechnicalReportBuilder.kt")
        assertTrue(r.contains("FarolManualOffVisualCommitStage43.exportReport()"))
    }

    @Test fun manualOnKeepsTheNormalNonForcedYellowAuthorityPath() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("    private fun applyWorkModeRuntime0162(")
        val b = s.indexOf("        driverCardSessionGate0162.invalidate()", a)
        val onBlock = s.substring(a, b)
        assertTrue(onBlock.contains("showOverlay(RadarColor.Idle, null)"))
        assertFalse(onBlock.contains("forcePhysicalCommitStage43 = true"))
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
        val h = source("FarolManualToggleRuntimeSyncStage43.kt") + source("FarolManualOffVisualCommitStage43.kt")
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
