package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolEdge0636Test {
    private fun root(): File {
        val cwd = File(System.getProperty("user.dir"))
        return if (File(cwd, "app/src/main/java").isDirectory) cwd
        else if (cwd.name == "app" && File(cwd, "src/main/java").isDirectory) cwd.parentFile
        else cwd
    }

    private fun src(name: String): String =
        File(root(), "app/src/main/java/br/com/mapeiaia/rotacerta/" + name).readText()

    @Test
    fun ambiguous_target_mutation_resets_old_public_final_before_verification() {
        val live = src("LiveRideAccessibilityService.kt")
        val guard = live.indexOf("} else if (verifyWithoutBlinkStage46R4) {")
        val reset = live.indexOf(
            "invalidateOldVisualBeforeCollectStage26(admissionStage26.visualGeneration, eventStartedNsStage26)",
            guard,
        )
        val marker = live.indexOf("S636_FINAL_PUBLIC_RESET_BEFORE_VERIFY", reset)
        assertTrue(guard >= 0)
        assertTrue(reset > guard)
        assertTrue(marker > reset)
        assertTrue(live.substring(guard, marker).contains("stage19VisualVerificationPending = true"))
    }

    @Test
    fun public_reset_clears_km_and_advances_generation_without_killing_semantic_work() {
        val live = src("LiveRideAccessibilityService.kt")
        val start = live.indexOf("private fun invalidateOldVisualBeforeCollectStage26")
        val end = live.indexOf("private fun collectUniversalAccessibilityBlocksStage19", start)
        assertTrue(start >= 0 && end > start)
        val block = live.substring(start, end)
        assertTrue(block.contains("universalScreenGeneration += 1L"))
        assertTrue(block.contains("universalWindowGeneration += 1L"))
        assertTrue(block.contains("currentDistanceKm = null"))
        assertTrue(block.contains("showOverlay(RadarColor.Default, distanceKm = null)"))
        assertFalse(block.contains("universalRouteJob?.cancel()"))
        assertFalse(block.contains("stage19OcrSerial += 1L"))
    }

    @Test
    fun semantically_proven_same_card_returns_before_stage636_public_reset() {
        val live = src("LiveRideAccessibilityService.kt")
        val sameCard = live.indexOf("FarolSemanticFinalLeaseStage44.preservesSameSemanticCard")
        val sameCardReturn = live.indexOf("return true", sameCard)
        val stage636Reset = live.indexOf("S636_FINAL_PUBLIC_RESET_BEFORE_VERIFY")
        assertTrue(sameCard >= 0)
        assertTrue(sameCardReturn > sameCard)
        assertTrue(stage636Reset > sameCardReturn)
    }

    @Test
    fun foreign_overlay_churn_still_preserves_confirmed_final_without_reset() {
        val live = src("LiveRideAccessibilityService.kt")
        val foreign = live.indexOf("S46_R4_FINAL_LATCH_PRESERVED_FOREIGN")
        val returned = live.indexOf("return true", foreign)
        val stage636Reset = live.indexOf("S636_FINAL_PUBLIC_RESET_BEFORE_VERIFY", foreign)
        assertTrue(foreign >= 0)
        assertTrue(returned > foreign)
        assertTrue(stage636Reset > returned)
    }

    @Test
    fun inflight_0635_semantic_lease_is_retained_for_non_final_work() {
        val live = src("LiveRideAccessibilityService.kt")
        assertTrue(live.contains("S635_INFLIGHT_SEMANTIC_LEASE_PRESERVED"))
        assertTrue(live.contains("S635_TRANSIENT_NO_CANDIDATE_INFLIGHT_PRESERVED"))
        assertTrue(live.contains("generationNotAdvanced=true"))
    }

    @Test
    fun release_metadata_0636_5927_remains_in_history() {
        val history = File(root(), "app/src/main/assets/release_history.json").readText()
        assertTrue(history.contains("\"version\": \"0.1.636\""))
        assertTrue(history.contains("\"build\": 5927"))
    }
}
