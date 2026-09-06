package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolStage46StableFinalLatchR4Test {
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
    private fun presence(active: Boolean = true, focused: Boolean = true, id: Int = 9001) =
        FarolAcquisitionSurfaceStage46R3.SurfacePresence(id, active, focused, 9)

    @Test fun r4_contract_markers_are_present() {
        val h = source("FarolStableFinalLatchStage46R4.kt")
        listOf(
            "FAROL_STABLE_FINAL_LATCH_STAGE46_R4",
            "FINAL_COLOR_STAYS_LIT_UNTIL_PROVEN_CHANGE_STAGE46_R4",
            "FOREIGN_CHURN_CANNOT_YELLOW_FINAL_STAGE46_R4",
            "SAME_SURFACE_AMBIGUOUS_EVENT_VERIFIES_WITHOUT_BLINK_STAGE46_R4",
            "PROVEN_SURFACE_CHANGE_CLEARS_IMMEDIATELY_STAGE46_R4",
            "NEW_CARD_REPLACES_LATCH_ONLY_AFTER_STAGE21_STAGE46_R4",
            "IDENTICAL_RENDER_IS_SUPPRESSED_STAGE46_R4",
            "ANY_VISIBLE_APP_OR_POPUP_CAN_ACQUIRE_STAGE46_R4",
            "EVENT_DRIVEN_NO_POLLING_STAGE46_R4",
        ).forEach { assertTrue(it, h.contains(it)) }
    }

    @Test fun physical_systemui_event_cannot_yellow_valid_indrive_final() {
        val a = FarolStableFinalLatchStage46R4.ambiguousAction(
            true, false, "sinet.startup.indriver", "sinet.startup.indriver", "com.android.systemui", own, presence(),
        )
        assertEquals(FarolStableFinalLatchStage46R4.AmbiguousAction.PRESERVE_NO_VERIFY, a)
    }

    @Test fun systemui_cannot_yellow_valid_99_popup_over_whatsapp() {
        val a = FarolStableFinalLatchStage46R4.ambiguousAction(
            true, false, "com.app99.driver", "com.whatsapp", "com.android.systemui", own, presence(active = true, focused = false),
        )
        assertEquals(FarolStableFinalLatchStage46R4.AmbiguousAction.PRESERVE_NO_VERIFY, a)
    }

    @Test fun foreign_indrive_event_cannot_yellow_valid_99_popup() {
        val a = FarolStableFinalLatchStage46R4.ambiguousAction(
            true, false, "com.app99.driver", "com.whatsapp", "sinet.startup.indriver", own, presence(active = false, focused = true),
        )
        assertEquals(FarolStableFinalLatchStage46R4.AmbiguousAction.PRESERVE_NO_VERIFY, a)
    }

    @Test fun confirmed_target_own_mutation_verifies_without_blink() {
        val a = FarolStableFinalLatchStage46R4.ambiguousAction(
            true, false, "com.app99.driver", "com.app99.driver", "com.app99.driver", own, presence(),
        )
        assertEquals(FarolStableFinalLatchStage46R4.AmbiguousAction.PRESERVE_AND_VERIFY, a)
    }

    @Test fun source_less_mutation_on_target_root_verifies_without_blink() {
        val a = FarolStableFinalLatchStage46R4.ambiguousAction(
            true, false, "com.ubercab.driver", "com.ubercab.driver", null, own, presence(),
        )
        assertEquals(FarolStableFinalLatchStage46R4.AmbiguousAction.PRESERVE_AND_VERIFY, a)
    }

    @Test fun proven_root_handoff_with_inactive_target_is_not_latched() {
        val a = FarolStableFinalLatchStage46R4.ambiguousAction(
            true, false, "com.app99.driver", "com.whatsapp", "com.whatsapp", own, presence(active = false, focused = false),
        )
        assertEquals(FarolStableFinalLatchStage46R4.AmbiguousAction.NONE, a)
    }

    @Test fun new_semantic_candidate_is_not_hidden_by_latch() {
        val a = FarolStableFinalLatchStage46R4.ambiguousAction(
            true, true, "com.app99.driver", "com.app99.driver", "com.app99.driver", own, presence(),
        )
        assertEquals(FarolStableFinalLatchStage46R4.AmbiguousAction.NONE, a)
    }

    @Test fun no_final_means_no_latch() {
        val a = FarolStableFinalLatchStage46R4.ambiguousAction(
            false, false, "com.app99.driver", "com.app99.driver", "com.android.systemui", own, presence(),
        )
        assertEquals(FarolStableFinalLatchStage46R4.AmbiguousAction.NONE, a)
    }

    @Test fun no_confirmed_target_means_no_latch() {
        val a = FarolStableFinalLatchStage46R4.ambiguousAction(
            true, false, null, "com.app99.driver", "com.android.systemui", own, presence(),
        )
        assertEquals(FarolStableFinalLatchStage46R4.AmbiguousAction.NONE, a)
    }

    @Test fun red_with_distance_and_signature_is_final() {
        assertTrue(FarolStableFinalLatchStage46R4.isFinalDecision("Red", 6.319, "visual|a|b"))
    }

    @Test fun green_with_distance_and_signature_is_final() {
        assertTrue(FarolStableFinalLatchStage46R4.isFinalDecision("Green", 4.3, "visual|a|b"))
    }

    @Test fun yellow_is_not_final() {
        assertFalse(FarolStableFinalLatchStage46R4.isFinalDecision("Default", null, "visual|a|b"))
    }

    @Test fun final_without_distance_is_not_final() {
        assertFalse(FarolStableFinalLatchStage46R4.isFinalDecision("Red", null, "visual|a|b"))
    }

    @Test fun final_without_signature_is_not_final() {
        assertFalse(FarolStableFinalLatchStage46R4.isFinalDecision("Green", 2.0, null))
    }

    @Test fun identical_final_render_is_equivalent() {
        assertTrue(FarolStableFinalLatchStage46R4.sameRenderedDecision("Red", 6.319, "red", 6.319))
    }

    @Test fun tiny_double_rounding_does_not_make_new_render() {
        assertTrue(FarolStableFinalLatchStage46R4.sameRenderedDecision("Green", 4.3000, "Green", 4.3004))
    }

    @Test fun different_distance_is_not_equivalent() {
        assertFalse(FarolStableFinalLatchStage46R4.sameRenderedDecision("Red", 6.319, "Red", 7.3))
    }

    @Test fun yellow_and_red_are_not_equivalent() {
        assertFalse(FarolStableFinalLatchStage46R4.sameRenderedDecision("Default", null, "Red", 6.319))
    }

    @Test fun service_integrates_foreign_preserve_and_verify_without_blink() {
        val s = source("LiveRideAccessibilityService.kt")
        assertTrue(s.contains("FarolStableFinalLatchStage46R4.ambiguousAction("))
        assertTrue(s.contains("S46_R4_FINAL_LATCH_PRESERVED_FOREIGN"))
        assertTrue(s.contains("S46_R4_FINAL_LATCH_VERIFY_WITHOUT_BLINK"))
        assertTrue(s.contains("noYellow=true; noOcr=true"))
        assertTrue(s.contains("noYellow=true; ocrMayVerify=true"))
    }

    @Test fun foreign_preserve_returns_before_stage44_destructive_invalidation() {
        val s = source("LiveRideAccessibilityService.kt")
        val action = s.indexOf("S46_R4_FINAL_LATCH_PRESERVED_FOREIGN")
        val returned = s.indexOf("return true", action)
        val invalidate = s.indexOf("invalidateOldVisualBeforeCollectStage26(admissionStage26.visualGeneration", action)
        assertTrue(action >= 0 && returned > action && invalidate > returned)
    }

    @Test fun same_surface_verify_does_not_call_yellow_before_ocr_branch() {
        val s = source("LiveRideAccessibilityService.kt")
        val verify = s.indexOf("S46_R4_FINAL_LATCH_VERIFY_WITHOUT_BLINK")
        val destructive = s.indexOf("invalidateOldVisualBeforeCollectStage26(admissionStage26.visualGeneration", verify)
        val guard = s.lastIndexOf("if (verifyWithoutBlinkStage46R4)", destructive)
        assertTrue(guard >= 0 && verify > guard && destructive > verify)
        assertTrue(s.substring(guard, destructive).contains("stage19VisualVerificationPending = true"))
        assertFalse(s.substring(guard, destructive).contains("showOverlay(RadarColor.Default"))
    }

    @Test fun r4_preserves_r3_immediate_handoff_and_r2_target_empty_clear() {
        val s = source("LiveRideAccessibilityService.kt")
        assertTrue(s.contains("S46_R3_FOREGROUND_HANDOFF_FINAL_REVOKED"))
        assertTrue(s.contains("S46_TARGET_EMPTY_FINAL_REVOKED"))
        assertTrue(s.contains("showOverlay(RadarColor.Default, distanceKm = null)"))
        assertTrue(source("FarolAcquisitionSurfaceStage46R3.kt").contains("PROVEN_FOREGROUND_HANDOFF_CLEARS_OLD_FINAL_STAGE46_R3"))
        assertTrue(source("FarolTargetSurfaceStage46R2.kt").contains("TARGET_EMPTY_CONTENT_REVOKES_FINAL_STAGE46_R2"))
    }

    @Test fun r4_is_universal_event_driven_and_keeps_stage21_google_contracts() {
        val h = source("FarolStableFinalLatchStage46R4.kt")
        assertFalse(h.contains("Thread.sleep"))
        assertFalse(h.contains("delay("))
        assertFalse(h.contains("Timer("))
        assertFalse(h.contains("scheduleAtFixedRate"))
        assertTrue(source("FarolCausalCorrectionStage21.kt").contains("FAROL_CAUSAL_CORRECTION_STAGE21"))
        assertTrue(source("FarolAcquisitionSurfaceStage46R3.kt").contains("FOREGROUND_SURFACE_CAN_ACQUIRE_AFTER_OLD_TARGET_STAGE46_R3"))
    }

    @Test fun inherited_stage46_version_tracks_r8_0_1_226_5510() {
        val b = File(projectRoot(), "app/build.gradle.kts").readText()
        assertTrue(b.contains("versionCode = 5510"))
        assertTrue(b.contains("versionName = \"0.1.226\""))
    }
}
