package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolStage46AtomicTransitionR5Test {
    private fun projectRoot(): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            if (File(dir, "app/src/main/java/br/com/mapeiaia/rotacerta").exists()) return dir
            dir = dir.parentFile ?: return@repeat
        }
        error("project root not found")
    }

    private fun source(name: String): String =
        File(projectRoot(), "app/src/main/java/br/com/mapeiaia/rotacerta/$name").readText()

    @Test fun r5_contract_markers_are_present() {
        val h = source("FarolAtomicTransitionStage46R5.kt")
        listOf(
            "FAROL_ATOMIC_TRANSITION_STAGE46_R5",
            "PROVEN_CHANGE_CLEARS_OLD_FINAL_SAME_EVENT_STAGE46_R5",
            "CLEAR_AND_REARM_ARE_ATOMIC_STAGE46_R5",
            "TARGET_EMPTY_REQUESTS_CURRENT_SURFACE_OCR_SAME_EVENT_STAGE46_R5",
            "PROVEN_NEW_CANDIDATE_CONTINUES_SAME_CYCLE_STAGE46_R5",
            "NO_SECOND_ACCESSIBILITY_EVENT_REQUIRED_FOR_REARM_STAGE46_R5",
            "NEXT_ACQUISITION_STARTS_AFTER_OLD_EPOCH_INVALIDATION_STAGE46_R5",
            "OLD_OCR_ROUTE_PAINT_CANNOT_CROSS_ATOMIC_TRANSITION_STAGE46_R5",
            "R4_FINAL_LATCH_SURVIVES_NON_SEMANTIC_CHURN_STAGE46_R5",
            "CURRENT_VISIBLE_SURFACE_REARMS_WITHOUT_PACKAGE_WHITELIST_STAGE46_R5",
            "EVENT_DRIVEN_SINGLE_SHOT_REARM_NO_POLLING_STAGE46_R5",
        ).forEach { assertTrue(it, h.contains(it)) }
    }

    @Test fun proven_clear_without_candidate_requests_single_shot_ocr_now() {
        assertEquals(
            FarolAtomicTransitionStage46R5.RearmAction.REQUEST_SINGLE_SHOT_OCR_NOW,
            FarolAtomicTransitionStage46R5.actionAfterProvenClear(true, true, false, false),
        )
    }

    @Test fun proven_clear_with_candidate_processes_same_cycle() {
        assertEquals(
            FarolAtomicTransitionStage46R5.RearmAction.PROCESS_CANDIDATE_SAME_CYCLE,
            FarolAtomicTransitionStage46R5.actionAfterProvenClear(true, true, false, true),
        )
    }

    @Test fun reading_off_never_rearms() {
        assertEquals(
            FarolAtomicTransitionStage46R5.RearmAction.NONE,
            FarolAtomicTransitionStage46R5.actionAfterProvenClear(false, true, false, false),
        )
    }

    @Test fun service_not_ready_never_rearms() {
        assertEquals(
            FarolAtomicTransitionStage46R5.RearmAction.NONE,
            FarolAtomicTransitionStage46R5.actionAfterProvenClear(true, false, false, false),
        )
    }

    @Test fun bubble_drag_never_rearms() {
        assertEquals(
            FarolAtomicTransitionStage46R5.RearmAction.NONE,
            FarolAtomicTransitionStage46R5.actionAfterProvenClear(true, true, true, false),
        )
    }

    @Test fun next_epoch_must_be_strictly_newer() {
        assertTrue(FarolAtomicTransitionStage46R5.nextEpochIsFresh(40L, 41L))
        assertFalse(FarolAtomicTransitionStage46R5.nextEpochIsFresh(41L, 41L))
        assertFalse(FarolAtomicTransitionStage46R5.nextEpochIsFresh(42L, 41L))
    }

    @Test fun target_empty_revoke_is_followed_by_ocr_request_before_return() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("if (targetEmptyProofStage46)")
        val b = s.indexOf("if (!visualDecisionStage23.process)", a)
        val block = s.substring(a, b)
        val revoke = block.indexOf("revokeEmptyTargetStage46(")
        val request = block.indexOf("requestUniversalScreenshotStage19(eventPackageStage19)")
        val returned = block.indexOf("return true", request)
        assertTrue(revoke >= 0 && request > revoke && returned > request)
    }

    @Test fun target_empty_rearm_uses_new_epoch_after_revocation() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("if (targetEmptyProofStage46)")
        val b = s.indexOf("if (!visualDecisionStage23.process)", a)
        val block = s.substring(a, b)
        assertTrue(block.contains("val previousEpochStage46R5 = stage46VisualEpoch"))
        assertTrue(block.contains("revokeEmptyTargetStage46("))
        assertTrue(block.contains("nextEpochIsFresh(previousEpochStage46R5, stage46VisualEpoch)"))
    }

    @Test fun target_empty_rearm_keeps_yellow_and_released_target_before_new_capture() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("if (targetEmptyProofStage46)")
        val b = s.indexOf("if (!visualDecisionStage23.process)", a)
        val block = s.substring(a, b)
        assertTrue(block.contains("yellowCommitted=true"))
        assertTrue(block.contains("oldTargetReleased=${stage46TargetSourcePackage == null}"))
        assertTrue(block.indexOf("revokeEmptyTargetStage46(") < block.indexOf("requestUniversalScreenshotStage19("))
    }

    @Test fun target_empty_rearm_does_not_need_second_accessibility_event() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("S46_R5_ATOMIC_CLEAR_REARM_REQUESTED")
        val b = s.indexOf("return true", a)
        val block = s.substring(a, b)
        assertTrue(block.contains("noSecondEventRequired=true"))
        assertTrue(block.contains("requestUniversalScreenshotStage19(eventPackageStage19)"))
    }

    @Test fun busy_screenshot_is_coalesced_not_dropped() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("S46_R5_ATOMIC_CLEAR_REARM_REQUESTED")
        val b = s.indexOf("return true", a)
        val block = s.substring(a, b)
        assertTrue(block.contains("screenshotAlreadyRunningStage46R5 = screenshotInProgress.get()"))
        assertTrue(block.contains("coalesced_rerun"))
        assertTrue(block.contains("immediate_screenshot"))
    }

    @Test fun atomic_rearm_has_forensic_requested_and_dispatched_markers() {
        val s = source("LiveRideAccessibilityService.kt")
        assertTrue(s.contains("S46_R5_ATOMIC_CLEAR_REARM_REQUESTED"))
        assertTrue(s.contains("S46_R5_ATOMIC_CLEAR_REARM_DISPATCHED"))
        assertTrue(s.contains("eventToStage46R5AtomicRearm"))
    }

    @Test fun foreground_handoff_is_explicitly_same_cycle() {
        val s = source("LiveRideAccessibilityService.kt")
        val site = s.indexOf("if (FarolAcquisitionSurfaceStage46R3.provesForegroundSurfaceHandoff(")
        val finalLease = s.indexOf("val finalLeaseStage44 = FarolSemanticFinalLeaseStage44.capture(", site)
        val block = s.substring(site, finalLease)
        assertTrue(site >= 0 && finalLease > site)
        assertTrue(block.contains("revokeForegroundSurfaceHandoffStage46R3("))
        assertTrue(block.contains("S46_R5_HANDOFF_CONTINUES_SAME_CYCLE"))
        assertTrue(block.contains("noSecondEventRequired=true"))
        assertFalse(block.contains("return true"))
    }

    @Test fun foreground_handoff_reaches_normal_candidate_or_ocr_pipeline_without_second_event() {
        val s = source("LiveRideAccessibilityService.kt")
        val site = s.indexOf("if (FarolAcquisitionSurfaceStage46R3.provesForegroundSurfaceHandoff(")
        val finalLease = s.indexOf("val finalLeaseStage44 = FarolSemanticFinalLeaseStage44.capture(", site)
        val laterOcr = s.indexOf("requestUniversalScreenshotStage19(eventPackageStage19)", finalLease)
        assertTrue(site >= 0 && finalLease > site && laterOcr > finalLease)
        assertFalse(s.substring(site, finalLease).contains("return"))
    }

    @Test fun proven_new_candidate_continues_same_cycle_without_redundant_ocr() {
        val s = source("LiveRideAccessibilityService.kt")
        val marker = s.indexOf("S46_R5_NEW_CANDIDATE_CONTINUES_SAME_CYCLE")
        assertTrue(marker >= 0)
        assertTrue(s.substring(marker, marker + 500).contains("noOcrNeeded=true"))
        assertTrue(s.substring(marker, marker + 500).contains("noSecondEventRequired=true"))
    }

    @Test fun r4_final_latch_is_still_present() {
        val s = source("LiveRideAccessibilityService.kt")
        assertTrue(s.contains("S46_R4_FINAL_LATCH_PRESERVED_FOREIGN"))
        assertTrue(s.contains("S46_R4_FINAL_LATCH_VERIFY_WITHOUT_BLINK"))
        assertTrue(source("FarolStableFinalLatchStage46R4.kt").contains("FINAL_COLOR_STAYS_LIT_UNTIL_PROVEN_CHANGE_STAGE46_R4"))
    }

    @Test fun systemui_churn_still_cannot_clear_a_valid_final() {
        val p = FarolAcquisitionSurfaceStage46R3.SurfacePresence(9100, true, true, 9)
        val action = FarolStableFinalLatchStage46R4.ambiguousAction(
            true, false, "sinet.startup.indriver", "sinet.startup.indriver", "com.android.systemui",
            "br.com.mapeiaia.rotacerta", p,
        )
        assertEquals(FarolStableFinalLatchStage46R4.AmbiguousAction.PRESERVE_NO_VERIFY, action)
    }

    @Test fun r2_target_empty_and_r3_release_are_still_authorities() {
        val s = source("LiveRideAccessibilityService.kt")
        assertTrue(s.contains("S46_TARGET_EMPTY_FINAL_REVOKED"))
        assertTrue(s.contains("releaseConfirmedTargetStage46R3(\"target_empty\""))
        assertTrue(source("FarolTargetSurfaceStage46R2.kt").contains("TARGET_EMPTY_CONTENT_REVOKES_FINAL_STAGE46_R2"))
    }

    @Test fun stage21_remains_semantic_authority_for_new_candidate() {
        val s = source("LiveRideAccessibilityService.kt")
        val reject = s.indexOf("if (!semanticStage21.accepted)")
        val promote = s.indexOf("S46_R3_TARGET_PROMOTED_AFTER_STAGE21", reject)
        assertTrue(reject >= 0 && promote > reject)
        assertTrue(source("FarolCausalCorrectionStage21.kt").contains("FAROL_CAUSAL_CORRECTION_STAGE21"))
    }

    @Test fun old_route_and_ocr_cancellation_contracts_are_preserved() {
        val s = source("LiveRideAccessibilityService.kt")
        assertTrue(s.contains("universalRouteJob?.cancel()"))
        assertTrue(s.contains("stage19OcrSerial += 1L"))
        assertTrue(s.contains("stage46BindingSurfaceToken.clear()"))
        assertTrue(source("FarolVisualEpochNoResultStage46.kt").contains("ROUTE_CANNOT_CROSS_VISIBLE_SURFACE_STAGE46"))
        assertTrue(source("FarolVisualEpochNoResultStage46.kt").contains("OCR_CANNOT_CROSS_VISIBLE_SURFACE_STAGE46"))
    }

    @Test fun current_surface_acquisition_remains_universal() {
        val h = source("FarolAtomicTransitionStage46R5.kt")
        assertFalse(h.contains("com.app99.driver"))
        assertFalse(h.contains("com.ubercab.driver"))
        assertFalse(h.contains("sinet.startup.indriver"))
        assertTrue(source("FarolAcquisitionSurfaceStage46R3.kt").contains("FOREGROUND_SURFACE_CAN_ACQUIRE_AFTER_OLD_TARGET_STAGE46_R3"))
    }

    @Test fun r5_is_single_shot_event_driven_not_polling() {
        val h = source("FarolAtomicTransitionStage46R5.kt")
        listOf("Thread.sleep", "delay(", "Timer(", "scheduleAtFixedRate", "scheduleWithFixedDelay").forEach {
            assertFalse(it, h.contains(it))
        }
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("if (targetEmptyProofStage46)")
        val b = s.indexOf("if (!visualDecisionStage23.process)", a)
        assertEquals(1, Regex("requestUniversalScreenshotStage19\\(eventPackageStage19\\)").findAll(s.substring(a, b)).count())
    }

    @Test fun version_is_stage46_r5_0_1_223_5507() {
        val b = File(projectRoot(), "app/build.gradle.kts").readText()
        assertTrue(b.contains("versionCode = 5507"))
        assertTrue(b.contains("versionName = \"0.1.223\""))
    }
}
