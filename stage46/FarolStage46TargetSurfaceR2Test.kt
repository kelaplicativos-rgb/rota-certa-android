package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolStage46TargetSurfaceR2Test {
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

    @Test fun r2_contract_markers_present() {
        val h = source("FarolTargetSurfaceStage46R2.kt")
        listOf(
            "FAROL_TARGET_SURFACE_AUTHORITY_STAGE46_R2",
            "ACCESSIBILITY_EVENT_IS_TRIGGER_NOT_CARD_AUTHORITY_STAGE46_R2",
            "FOREIGN_OVERLAY_CANNOT_REVOKE_TARGET_STAGE46_R2",
            "TARGET_WINDOW_ID_PARTICIPATES_IN_FRESHNESS_STAGE46_R2",
            "TARGET_EMPTY_CONTENT_REVOKES_FINAL_STAGE46_R2",
            "SAME_TARGET_WINDOW_PRESERVES_STAGE44_FINAL_STAGE46_R2",
            "TARGET_SELECTION_IS_VISUAL_NOT_PACKAGE_WHITELIST_STAGE46_R2",
            "EVENT_DRIVEN_NO_POLLING_STAGE46_R2",
        ).forEach { assertTrue(it, h.contains(it)) }
    }

    @Test fun physical_99_target_survives_foreign_indrive_event() {
        val target = FarolTargetSurfaceStage46R2.chooseTargetPackage(
            existingTargetPackage = "com.app99.driver",
            currentRootPackage = "com.app99.driver",
            eventPackage = "sinet.startup.indriver",
            ownPackageName = "br.com.mapeiaia.rotacerta",
        )
        assertTrue(target == "com.app99.driver")
    }

    @Test fun no_existing_target_can_be_acquired_from_popup_event_over_launcher() {
        val target = FarolTargetSurfaceStage46R2.chooseTargetPackage(
            existingTargetPackage = null,
            currentRootPackage = "com.sec.android.app.launcher",
            eventPackage = "com.ubercab.driver",
            ownPackageName = "br.com.mapeiaia.rotacerta",
        )
        assertTrue(target == "com.ubercab.driver")
    }

    @Test fun proven_candidate_can_move_target_to_new_visible_popup() {
        val target = FarolTargetSurfaceStage46R2.chooseCandidateTargetPackage(
            currentRootPackage = "com.sec.android.app.launcher",
            candidatePackage = "sinet.startup.indriver",
            ownPackageName = "br.com.mapeiaia.rotacerta",
        )
        assertTrue(target == "sinet.startup.indriver")
    }

    @Test fun physical_foreign_window_change_does_not_replace_same_99_target_window() {
        assertFalse(
            FarolTargetSurfaceStage46R2.isTargetWindowReplacement(
                eventType = 4_194_304,
                structuralSignature = "window-transition:6693",
                ownOverlay = false,
                heavyCollect = true,
                targetPackage = "com.app99.driver",
                previousTargetWindowId = 6688,
                currentTargetWindowId = 6688,
            ),
        )
    }

    @Test fun target_window_disappearance_is_real_replacement() {
        assertTrue(
            FarolTargetSurfaceStage46R2.isTargetWindowReplacement(
                eventType = 4_194_304,
                structuralSignature = "window-transition:6415",
                ownOverlay = false,
                heavyCollect = true,
                targetPackage = "com.ubercab.driver",
                previousTargetWindowId = 6415,
                currentTargetWindowId = 0,
            ),
        )
    }

    @Test fun target_window_id_change_is_real_replacement() {
        assertTrue(
            FarolTargetSurfaceStage46R2.isTargetWindowReplacement(
                eventType = 4_194_304,
                structuralSignature = "window-transition:7002",
                ownOverlay = false,
                heavyCollect = true,
                targetPackage = "com.app99.driver",
                previousTargetWindowId = 7001,
                currentTargetWindowId = 7002,
            ),
        )
    }

    @Test fun raw_windows_changed_without_owned_target_is_not_destructive() {
        assertFalse(
            FarolTargetSurfaceStage46R2.isTargetWindowReplacement(
                eventType = 4_194_304,
                structuralSignature = "window-transition:7002",
                ownOverlay = false,
                heavyCollect = true,
                targetPackage = null,
                previousTargetWindowId = 0,
                currentTargetWindowId = 7002,
            ),
        )
    }

    @Test fun physical_99_content_changed_zero_blocks_revokes_old_final() {
        assertTrue(
            FarolTargetSurfaceStage46R2.provesCurrentTargetEmpty(
                eventType = 2_048,
                eventPackage = "com.app99.driver",
                currentRootPackage = "com.app99.driver",
                targetPackage = "com.app99.driver",
                ownPackageName = "br.com.mapeiaia.rotacerta",
                ownOverlay = false,
                activeFinal = true,
                collectedBlockCount = 0,
            ),
        )
    }

    @Test fun foreign_indrive_content_change_cannot_clear_99_final() {
        assertFalse(
            FarolTargetSurfaceStage46R2.provesCurrentTargetEmpty(
                eventType = 2_048,
                eventPackage = "sinet.startup.indriver",
                currentRootPackage = "com.app99.driver",
                targetPackage = "com.app99.driver",
                ownPackageName = "br.com.mapeiaia.rotacerta",
                ownOverlay = false,
                activeFinal = true,
                collectedBlockCount = 0,
            ),
        )
    }

    @Test fun own_overlay_empty_collection_cannot_clear_target_final() {
        assertFalse(
            FarolTargetSurfaceStage46R2.provesCurrentTargetEmpty(
                eventType = 2_048,
                eventPackage = "br.com.mapeiaia.rotacerta",
                currentRootPackage = "com.app99.driver",
                targetPackage = "com.app99.driver",
                ownPackageName = "br.com.mapeiaia.rotacerta",
                ownOverlay = true,
                activeFinal = true,
                collectedBlockCount = 0,
            ),
        )
    }

    @Test fun empty_collection_without_active_final_does_not_create_revocation() {
        assertFalse(
            FarolTargetSurfaceStage46R2.provesCurrentTargetEmpty(
                eventType = 2_048,
                eventPackage = "com.app99.driver",
                currentRootPackage = "com.app99.driver",
                targetPackage = "com.app99.driver",
                ownPackageName = "br.com.mapeiaia.rotacerta",
                ownOverlay = false,
                activeFinal = false,
                collectedBlockCount = 0,
            ),
        )
    }

    @Test fun overlay_target_remains_fresh_while_root_is_launcher_and_window_is_present() {
        val token = FarolVisualEpochNoResultStage46.captureSurface("com.ubercab.driver", null, 6415, 84L)
        assertTrue(
            FarolTargetSurfaceStage46R2.surfaceFresh(
                token,
                currentRootPackage = "com.sec.android.app.launcher",
                currentTargetWindowId = 6415,
                currentVisualEpoch = 84L,
            ),
        )
    }

    @Test fun overlay_target_missing_is_stale_even_if_root_stays_launcher() {
        val token = FarolVisualEpochNoResultStage46.captureSurface("com.ubercab.driver", null, 6415, 84L)
        assertFalse(
            FarolTargetSurfaceStage46R2.surfaceFresh(
                token,
                currentRootPackage = "com.sec.android.app.launcher",
                currentTargetWindowId = 0,
                currentVisualEpoch = 84L,
            ),
        )
    }

    @Test fun different_nonzero_window_id_is_stale() {
        val token = FarolVisualEpochNoResultStage46.captureSurface("com.app99.driver", null, 7001, 90L)
        assertFalse(
            FarolTargetSurfaceStage46R2.surfaceFresh(
                token,
                currentRootPackage = "com.app99.driver",
                currentTargetWindowId = 7002,
                currentVisualEpoch = 90L,
            ),
        )
    }

    @Test fun same_root_same_window_same_epoch_is_fresh() {
        val token = FarolVisualEpochNoResultStage46.captureSurface("com.app99.driver", null, 7001, 90L)
        assertTrue(
            FarolTargetSurfaceStage46R2.surfaceFresh(
                token,
                currentRootPackage = "com.app99.driver",
                currentTargetWindowId = 7001,
                currentVisualEpoch = 90L,
            ),
        )
    }

    @Test fun same_surface_different_epoch_is_stale() {
        val token = FarolVisualEpochNoResultStage46.captureSurface("com.app99.driver", null, 7001, 90L)
        assertFalse(
            FarolTargetSurfaceStage46R2.surfaceFresh(
                token,
                currentRootPackage = "com.app99.driver",
                currentTargetWindowId = 7001,
                currentVisualEpoch = 91L,
            ),
        )
    }

    @Test fun service_no_longer_uses_r1_any_window_transition_as_authority() {
        val s = source("LiveRideAccessibilityService.kt")
        assertFalse(s.contains("FarolVisualEpochNoResultStage46.isHardWindowBoundary("))
        assertTrue(s.contains("FarolTargetSurfaceStage46R2.isTargetWindowReplacement("))
        assertTrue(s.contains("S46_R2_FOREIGN_WINDOW_PRESERVED"))
    }

    @Test fun target_replacement_observation_occurs_before_stage44_lease_capture() {
        val s = source("LiveRideAccessibilityService.kt")
        val r2 = s.indexOf("FarolTargetSurfaceStage46R2.isTargetWindowReplacement(")
        val lease = s.indexOf("S44_FINAL_LEASE_HELD_PRECOLLECT")
        assertTrue(r2 >= 0 && lease > r2)
    }

    @Test fun target_empty_proof_executes_before_stage44_raw_duplicate_preservation() {
        val s = source("LiveRideAccessibilityService.kt")
        val proof = s.indexOf("FarolTargetSurfaceStage46R2.provesCurrentTargetEmpty(")
        val revoke = s.indexOf("S46_TARGET_EMPTY_FINAL_REVOKED")
        val duplicate = s.indexOf("S44_RAW_DUPLICATE_FINAL_PRESERVED")
        assertTrue(proof >= 0 && revoke > proof && duplicate > proof)
    }

    @Test fun stale_ocr_is_checked_before_ocr_candidate_can_move_target() {
        val s = source("LiveRideAccessibilityService.kt")
        val staleCheck = s.indexOf("if (!isStage46OcrWorkFresh(workTokenStage36, surfaceTokenStage46))")
        val postFresh = s.indexOf("stage19VisualVerificationPending = false", staleCheck)
        val bind = s.indexOf("bindCandidateTargetSurfaceStage46(eventPackageStage19, visualWindowIdStage19, \"ocr\")", postFresh)
        assertTrue(staleCheck >= 0 && postFresh > staleCheck && bind > postFresh)
    }

    @Test fun route_and_paint_surface_binding_uses_target_package_and_target_window() {
        val s = source("LiveRideAccessibilityService.kt")
        assertTrue(s.contains("val routeTargetPackageStage46 = stage46TargetSourcePackage ?: currentRootPackageName()"))
        assertTrue(s.contains("observeTargetWindowIdStage46(routeTargetPackageStage46)"))
        assertTrue(s.contains("FarolTargetSurfaceStage46R2.surfaceFresh("))
    }

    @Test fun r2_does_not_add_polling_or_timer() {
        val h = source("FarolTargetSurfaceStage46R2.kt")
        assertFalse(h.contains("Thread.sleep"))
        assertFalse(h.contains("delay("))
        assertFalse(h.contains("Timer("))
        assertFalse(h.contains("scheduleAtFixedRate"))
        assertFalse(h.contains("scheduleWithFixedDelay"))
    }

    @Test fun version_is_stage46_r2_0_1_220_5504() {
        val b = File(projectRoot(), "app/build.gradle.kts").readText()
        assertTrue(b.contains("versionCode = 5504"))
        assertTrue(b.contains("versionName = \"0.1.220\""))
    }
}
