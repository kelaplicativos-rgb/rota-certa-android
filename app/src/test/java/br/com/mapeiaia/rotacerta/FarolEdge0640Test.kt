package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolEdge0640Test {
    private fun root(): File {
        val cwd = File(System.getProperty("user.dir"))
        return if (File(cwd, "app/src/main/java").isDirectory) cwd
        else if (cwd.name == "app" && File(cwd, "src/main/java").isDirectory) cwd.parentFile
        else cwd
    }

    private fun src(name: String): String =
        File(root(), "app/src/main/java/br/com/mapeiaia/rotacerta/" + name).readText()

    @Test
    fun untrained_surface_remains_fail_closed() {
        val decision = FarolInstantFirstPaintStage640.decide(
            hasModels = false,
            signatureMatched = false,
            activeSemanticLeaseSameSurface = false,
            hasFinalPublicDecision = false,
        )
        assertFalse(decision.allowHeavyPipeline)
        assertTrue(decision.hardReset)
        assertTrue(decision.outcome == FarolInstantFirstPaintStage640.Outcome.BLOCK_UNTRAINED)
    }

    @Test
    fun exact_signature_admits_and_requests_immediate_waiting_feedback() {
        val decision = FarolInstantFirstPaintStage640.decide(
            hasModels = true,
            signatureMatched = true,
            activeSemanticLeaseSameSurface = false,
            hasFinalPublicDecision = false,
        )
        assertTrue(decision.allowHeavyPipeline)
        assertFalse(decision.hardReset)
        assertTrue(decision.paintWaitingImmediately)
        assertTrue(decision.outcome == FarolInstantFirstPaintStage640.Outcome.ALLOW_EXACT_SIGNATURE)
    }

    @Test
    fun active_semantic_lease_survives_transient_signature_miss() {
        val decision = FarolInstantFirstPaintStage640.decide(
            hasModels = true,
            signatureMatched = false,
            activeSemanticLeaseSameSurface = true,
            hasFinalPublicDecision = false,
        )
        assertTrue(decision.allowHeavyPipeline)
        assertFalse(decision.hardReset)
        assertFalse(decision.paintWaitingImmediately)
        assertTrue(decision.outcome == FarolInstantFirstPaintStage640.Outcome.ALLOW_ACTIVE_SEMANTIC_LEASE)
    }

    @Test
    fun bare_signature_miss_without_semantic_lease_still_blocks() {
        val decision = FarolInstantFirstPaintStage640.decide(
            hasModels = true,
            signatureMatched = false,
            activeSemanticLeaseSameSurface = false,
            hasFinalPublicDecision = false,
        )
        assertFalse(decision.allowHeavyPipeline)
        assertTrue(decision.hardReset)
        assertTrue(decision.outcome == FarolInstantFirstPaintStage640.Outcome.BLOCK_SIGNATURE_MISS)
    }

    @Test
    fun existing_green_red_is_not_blinked_yellow_on_exact_signature_event() {
        val decision = FarolInstantFirstPaintStage640.decide(
            hasModels = true,
            signatureMatched = true,
            activeSemanticLeaseSameSurface = false,
            hasFinalPublicDecision = true,
        )
        assertTrue(decision.allowHeavyPipeline)
        assertFalse(decision.paintWaitingImmediately)
    }

    @Test
    fun service_checks_active_lease_before_repeating_structural_signature_match() {
        val live = src("LiveRideAccessibilityService.kt")
        val start = live.indexOf("private fun admitTrainedCardStage640")
        val lease = live.indexOf("if (sameSurfaceLease640)", start)
        val matcher = live.indexOf("matchesTrainedCardSignature638(packageName639, root640)", start)
        assertTrue(start >= 0)
        assertTrue(lease > start)
        assertTrue(matcher > lease)
        assertTrue(live.substring(lease, matcher).contains("return true"))
    }

    @Test
    fun first_feedback_is_painted_before_stage38_and_stage19_for_a_new_admitted_card() {
        val live = src("LiveRideAccessibilityService.kt")
        val gate = live.indexOf("admitTrainedCardStage640(authorityPackage638, \"accessibility_event\")")
        val feedback = live.indexOf("S640_SIGNATURE_FIRST_FEEDBACK_PAINTED")
        val stage38 = live.indexOf("S38_ACCESSIBILITY_EVENT_RECEIVED", gate)
        val stage19 = live.indexOf("handleUniversalVisualEventStage19", gate)
        assertTrue(gate >= 0)
        assertTrue(feedback >= 0)
        assertTrue(stage38 > gate)
        assertTrue(stage19 > gate)
        // The feedback lives inside the gate itself, therefore it is physically requested before
        // the caller can advance to Stage38/Stage19.
        assertTrue(live.contains("showOverlay(RadarColor.Default, distanceKm = null)"))
    }

    @Test
    fun farol_local_color_uses_stage640_platform_first_resolver() {
        val live = src("LiveRideAccessibilityService.kt")
        assertTrue(live.contains("resolveFarolCoordinateInstant640(originAddress, destinations, apiKey)"))

        val maps = src("GoogleMapsService.kt")
        val start = maps.indexOf("suspend fun resolveFarolCoordinateInstant640")
        val cache = maps.indexOf("cachedFarolCoordinate(originAddress)", start)
        val platform = maps.indexOf("resolvePlatformFirstOrigin640(originAddress)", start)
        val network = maps.indexOf("resolveFreePrimaryOrigin0547(originAddress, targetHints)", start)
        assertTrue(start >= 0)
        assertTrue(cache > start)
        assertTrue(platform > cache)
        assertTrue(network > platform)
    }

    @Test
    fun color_remains_phase_a_and_exact_road_km_remains_phase_b() {
        val live = src("LiveRideAccessibilityService.kt")
        val local = live.indexOf("applyUniversalPreliminaryColorStage637(")
        val exact = live.indexOf("trafficAwareDrivingDistancesFromAddressKm(", local)
        assertTrue(local >= 0)
        assertTrue(exact > local)
        val apply = live.indexOf("private fun applyUniversalPreliminaryColorStage637")
        assertTrue(apply >= 0)
        assertTrue(live.substring(apply, live.indexOf("private suspend fun applyUniversalTwoAddressResultStage19", apply))
            .contains("showOverlay(colorStage637, null)"))
    }

    @Test
    fun release_metadata_is_0640_5931() {
        val gradle = File(root(), "app/build.gradle.kts").readText()
        assertTrue(gradle.contains("releaseVersionName = \"0.1.640\""))
        assertTrue(gradle.contains("releaseVersionCode = 5_931"))

        val history = File(root(), "app/src/main/assets/release_history.json").readText()
        assertTrue(history.contains("\"version\": \"0.1.640\""))
        assertTrue(history.contains("\"build\": 5931"))
        assertTrue(history.contains("lease semântico"))
        assertTrue(history.contains("primeiro Green/Red"))
    }
}
