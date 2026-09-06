package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolStage46AcquisitionSurfaceR3Test {
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

    private val own = "br.com.mapeiaia.rotacerta"

    @Test fun r3_contract_markers_present() {
        val h = source("FarolAcquisitionSurfaceStage46R3.kt")
        listOf(
            "FAROL_ACQUISITION_SURFACE_STAGE46_R3",
            "ACQUISITION_SURFACE_DIFFERS_FROM_CONFIRMED_TARGET_STAGE46_R3",
            "CONFIRMED_TARGET_RELEASED_WHEN_FINAL_REVOKED_STAGE46_R3",
            "FOREGROUND_SURFACE_CAN_ACQUIRE_AFTER_OLD_TARGET_STAGE46_R3",
            "OCR_ACQUISITION_NOT_PINNED_TO_STALE_CONFIRMED_TARGET_STAGE46_R3",
            "OCR_ACQUISITION_REQUIRES_CURRENT_OR_INTERACTIVE_SURFACE_STAGE46_R3",
            "TARGET_PROMOTED_ONLY_AFTER_SEMANTIC_VALIDATION_STAGE46_R3",
            "FOREIGN_OVERLAY_CANNOT_STEAL_INTERACTIVE_TARGET_STAGE46_R3",
            "PROVEN_FOREGROUND_HANDOFF_CLEARS_OLD_FINAL_STAGE46_R3",
            "MANUAL_OFF_RELEASES_CONFIRMED_TARGET_STAGE46_R3",
            "EVENT_DRIVEN_NO_POLLING_STAGE46_R3",
        ).forEach { assertTrue(it, h.contains(it)) }
    }

    @Test fun physical_comuto_background_then_99_foreground_acquires_99() {
        val d = FarolAcquisitionSurfaceStage46R3.chooseAcquisitionPackage(
            confirmedTargetPackage = "com.comuto",
            currentRootPackage = "com.app99.driver",
            eventPackage = "com.app99.driver",
            ownPackageName = own,
            confirmedPresence = FarolAcquisitionSurfaceStage46R3.SurfacePresence(
                windowId = 6866, active = false, focused = false, layer = 2,
            ),
        )
        assertEquals("com.app99.driver", d.packageName)
        assertEquals("foreground_root_acquisition", d.reason)
    }

    @Test fun interactive_confirmed_popup_stays_acquisition_over_other_foreground_app() {
        val d = FarolAcquisitionSurfaceStage46R3.chooseAcquisitionPackage(
            "com.app99.driver", "com.whatsapp", "com.whatsapp", own,
            FarolAcquisitionSurfaceStage46R3.SurfacePresence(7001, active = true, focused = false, layer = 8),
        )
        assertEquals("com.app99.driver", d.packageName)
        assertEquals("interactive_confirmed_popup", d.reason)
    }

    @Test fun focused_confirmed_popup_also_stays_acquisition() {
        val d = FarolAcquisitionSurfaceStage46R3.chooseAcquisitionPackage(
            "com.ubercab.driver", "com.sec.android.app.launcher", "com.android.systemui", own,
            FarolAcquisitionSurfaceStage46R3.SurfacePresence(7002, active = false, focused = true, layer = 9),
        )
        assertEquals("com.ubercab.driver", d.packageName)
    }

    @Test fun same_foreground_target_remains_acquisition() {
        val d = FarolAcquisitionSurfaceStage46R3.chooseAcquisitionPackage(
            "sinet.startup.indriver", "sinet.startup.indriver", "com.android.systemui", own,
            FarolAcquisitionSurfaceStage46R3.SurfacePresence(7003, active = true, focused = true, layer = 5),
        )
        assertEquals("sinet.startup.indriver", d.packageName)
        assertEquals("same_foreground_target", d.reason)
    }

    @Test fun no_confirmed_target_99_root_acquires_99() {
        val d = FarolAcquisitionSurfaceStage46R3.chooseAcquisitionPackage(
            null, "com.app99.driver", "com.app99.driver", own,
            FarolAcquisitionSurfaceStage46R3.SurfacePresence(),
        )
        assertEquals("com.app99.driver", d.packageName)
    }

    @Test fun uber_popup_event_over_launcher_can_acquire_without_confirmed_target() {
        val d = FarolAcquisitionSurfaceStage46R3.chooseAcquisitionPackage(
            null, "com.sec.android.app.launcher", "com.ubercab.driver", own,
            FarolAcquisitionSurfaceStage46R3.SurfacePresence(),
        )
        assertEquals("com.ubercab.driver", d.packageName)
        assertEquals("popup_event_acquisition", d.reason)
    }

    @Test fun foreign_indrive_event_cannot_steal_interactive_99_target() {
        val d = FarolAcquisitionSurfaceStage46R3.chooseAcquisitionPackage(
            "com.app99.driver", "com.app99.driver", "sinet.startup.indriver", own,
            FarolAcquisitionSurfaceStage46R3.SurfacePresence(7004, active = true, focused = true, layer = 6),
        )
        assertEquals("com.app99.driver", d.packageName)
    }

    @Test fun physical_foreground_handoff_comuto_to_99_revokes_old_final() {
        assertTrue(
            FarolAcquisitionSurfaceStage46R3.provesForegroundSurfaceHandoff(
                eventType = 32, heavyCollect = true, ownOverlay = false, activeFinal = true,
                confirmedTargetPackage = "com.comuto", currentRootPackage = "com.app99.driver",
                ownPackageName = own,
                confirmedPresence = FarolAcquisitionSurfaceStage46R3.SurfacePresence(6866, false, false, 1),
            ),
        )
    }

    @Test fun windows_changed_can_prove_same_noninteractive_foreground_handoff() {
        assertTrue(
            FarolAcquisitionSurfaceStage46R3.provesForegroundSurfaceHandoff(
                4_194_304, true, false, true, "com.comuto", "com.app99.driver", own,
                FarolAcquisitionSurfaceStage46R3.SurfacePresence(6866, false, false, 1),
            ),
        )
    }

    @Test fun interactive_popup_blocks_foreground_handoff_revocation() {
        assertFalse(
            FarolAcquisitionSurfaceStage46R3.provesForegroundSurfaceHandoff(
                32, true, false, true, "com.app99.driver", "com.whatsapp", own,
                FarolAcquisitionSurfaceStage46R3.SurfacePresence(7001, true, false, 8),
            ),
        )
    }

    @Test fun launcher_root_does_not_by_itself_revoke_confirmed_popup_final() {
        assertFalse(
            FarolAcquisitionSurfaceStage46R3.provesForegroundSurfaceHandoff(
                32, true, false, true, "com.ubercab.driver", "com.sec.android.app.launcher", own,
                FarolAcquisitionSurfaceStage46R3.SurfacePresence(7002, false, false, 8),
            ),
        )
    }

    @Test fun ordinary_content_changed_is_not_foreground_handoff_authority() {
        assertFalse(
            FarolAcquisitionSurfaceStage46R3.provesForegroundSurfaceHandoff(
                2_048, true, false, true, "com.comuto", "com.app99.driver", own,
                FarolAcquisitionSurfaceStage46R3.SurfacePresence(6866, false, false, 1),
            ),
        )
    }

    @Test fun no_active_final_means_handoff_does_not_invent_revocation() {
        assertFalse(
            FarolAcquisitionSurfaceStage46R3.provesForegroundSurfaceHandoff(
                32, true, false, false, "com.comuto", "com.app99.driver", own,
                FarolAcquisitionSurfaceStage46R3.SurfacePresence(6866, false, false, 1),
            ),
        )
    }

    @Test fun acquisition_ocr_same_root_same_epoch_is_fresh() {
        val token = FarolVisualEpochNoResultStage46.captureSurface("com.app99.driver", null, 6872, 27L)
        assertTrue(
            FarolAcquisitionSurfaceStage46R3.acquisitionSurfaceFresh(
                token, "com.app99.driver",
                FarolAcquisitionSurfaceStage46R3.SurfacePresence(6872, true, true, 5), 27L,
            ),
        )
    }

    @Test fun acquisition_ocr_interactive_popup_over_launcher_is_fresh() {
        val token = FarolVisualEpochNoResultStage46.captureSurface("com.ubercab.driver", null, 7010, 28L)
        assertTrue(
            FarolAcquisitionSurfaceStage46R3.acquisitionSurfaceFresh(
                token, "com.sec.android.app.launcher",
                FarolAcquisitionSurfaceStage46R3.SurfacePresence(7010, true, false, 9), 28L,
            ),
        )
    }

    @Test fun acquisition_ocr_background_old_app_after_root_handoff_is_stale() {
        val token = FarolVisualEpochNoResultStage46.captureSurface("com.comuto", null, 6866, 27L)
        assertFalse(
            FarolAcquisitionSurfaceStage46R3.acquisitionSurfaceFresh(
                token, "com.app99.driver",
                FarolAcquisitionSurfaceStage46R3.SurfacePresence(6866, false, false, 1), 27L,
            ),
        )
    }

    @Test fun acquisition_ocr_different_window_is_stale() {
        val token = FarolVisualEpochNoResultStage46.captureSurface("com.app99.driver", null, 6871, 30L)
        assertFalse(
            FarolAcquisitionSurfaceStage46R3.acquisitionSurfaceFresh(
                token, "com.app99.driver",
                FarolAcquisitionSurfaceStage46R3.SurfacePresence(6872, true, true, 5), 30L,
            ),
        )
    }

    @Test fun acquisition_ocr_different_epoch_is_stale() {
        val token = FarolVisualEpochNoResultStage46.captureSurface("com.app99.driver", null, 6872, 30L)
        assertFalse(
            FarolAcquisitionSurfaceStage46R3.acquisitionSurfaceFresh(
                token, "com.app99.driver",
                FarolAcquisitionSurfaceStage46R3.SurfacePresence(6872, true, true, 5), 31L,
            ),
        )
    }

    @Test fun service_ocr_capture_uses_r3_acquisition_not_r2_pinned_target() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("private fun requestUniversalScreenshotStage19(")
        val b = s.indexOf("private suspend fun processUniversalVisualStage19(", a)
        val ocr = s.substring(a, b)
        assertTrue(ocr.contains("FarolAcquisitionSurfaceStage46R3.chooseAcquisitionPackage("))
        assertTrue(ocr.contains("S46_R3_ACQUISITION_SURFACE_CAPTURED"))
        assertFalse(ocr.contains("FarolTargetSurfaceStage46R2.chooseTargetPackage("))
    }

    @Test fun ocr_visual_blocks_bind_to_captured_acquisition_window() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("private fun requestUniversalScreenshotStage19(")
        val b = s.indexOf("private suspend fun processUniversalVisualStage19(", a)
        val ocr = s.substring(a, b)
        assertTrue(ocr.contains("windowId = surfaceTokenStage46.windowId"))
        assertFalse(ocr.contains("windowId = visualWindowIdStage19,"))
    }

    @Test fun confirmed_target_promotion_occurs_only_after_stage21_acceptance() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("private suspend fun processUniversalVisualStage19(")
        val b = s.indexOf("private fun stage20BindingSnapshot(", a)
        val process = s.substring(a, b)
        val reject = process.indexOf("if (!semanticStage21.accepted)")
        val promotion = process.indexOf("S46_R3_TARGET_PROMOTED_AFTER_STAGE21")
        val cache = process.indexOf("cachedDrivingDistancesFromAddressKm")
        assertTrue(reject >= 0 && promotion > reject)
        assertTrue(cache < 0 || promotion < cache)
        assertFalse(process.substring(0, reject).contains("bindCandidateTargetSurfaceStage46("))
    }

    @Test fun all_confirmed_target_revocation_paths_release_package_window_and_provenance() {
        val s = source("LiveRideAccessibilityService.kt")
        val release = s.substring(
            s.indexOf("private fun releaseConfirmedTargetStage46R3("),
            s.indexOf("private fun revokeForegroundSurfaceHandoffStage46R3("),
        )
        assertTrue(release.contains("stage46TargetSourcePackage = null"))
        assertTrue(release.contains("stage46TargetWindowId = 0"))
        assertTrue(release.contains("stage46AcquisitionSurfaceByWindowId.clear()"))
        assertTrue(s.contains("releaseConfirmedTargetStage46R3(\"target_empty\""))
        assertTrue(s.contains("releaseConfirmedTargetStage46R3(\"hard_visual_boundary\""))
        assertTrue(s.contains("releaseConfirmedTargetStage46R3(\"reading_off\""))
    }

    @Test fun foreground_handoff_commits_yellow_and_continues_same_cycle() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("private fun revokeForegroundSurfaceHandoffStage46R3(")
        val b = s.indexOf("private fun revokeEmptyTargetStage46(", a)
        val body = s.substring(a, b)
        assertTrue(body.contains("showOverlay(RadarColor.Default, distanceKm = null)"))
        assertTrue(body.contains("universalRouteJob?.cancel()"))
        assertTrue(body.contains("S46_R3_FOREGROUND_HANDOFF_FINAL_REVOKED"))
        assertTrue(body.contains("acquisitionContinuesSameCycle=true"))
    }

    @Test fun r3_preserves_r2_r1_stage45_stage44_stage43_stage41_and_no_polling() {
        assertTrue(source("FarolTargetSurfaceStage46R2.kt").contains("FAROL_TARGET_SURFACE_AUTHORITY_STAGE46_R2"))
        assertTrue(source("FarolVisualEpochNoResultStage46.kt").contains("FAROL_VISUAL_SURFACE_EPOCH_STAGE46"))
        assertTrue(source("FarolOcrMultilineAddressStage45.kt").contains("FAROL_OCR_MULTILINE_ADDRESS_STAGE45"))
        assertTrue(source("FarolSemanticFinalLeaseStage44.kt").contains("FAROL_SEMANTIC_FINAL_LEASE_STAGE44"))
        assertTrue(source("FarolManualOffVisualCommitStage43.kt").contains("MANUAL_OFF_PHYSICAL_VIEW_COMMIT_STAGE43"))
        assertTrue(source("FarolFinalPaintFreshnessStage41.kt").contains("FAROL_SUBSECOND_SAME_FRAME_FINAL_PAINT_STAGE41"))
        val h = source("FarolAcquisitionSurfaceStage46R3.kt")
        assertFalse(h.contains("Thread.sleep"))
        assertFalse(h.contains("delay("))
        assertFalse(h.contains("Timer("))
        assertFalse(h.contains("scheduleAtFixedRate"))
        assertFalse(h.contains("scheduleWithFixedDelay"))
    }

    @Test fun inherited_stage46_version_tracks_r8_0_1_226_5510() {
        val b = File(projectRoot(), "app/build.gradle.kts").readText()
        assertTrue(b.contains("versionCode = 5510"))
        assertTrue(b.contains("versionName = \"0.1.226\""))
    }
}
