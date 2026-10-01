package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolStage44SemanticFinalLeaseTest {
    private fun source(name: String): String {
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOf(
            File(cwd, "src/main/java/br/com/mapeiaia/rotacerta/$name"),
            File(cwd, "app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
            File(cwd.parentFile ?: cwd, "app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Stage44 source not found: $name; cwd=${cwd.absolutePath}")
    }

    @Test fun contractMarkersDescribeProofBeforeRevocation() {
        assertEquals("FAROL_SEMANTIC_FINAL_LEASE_STAGE44", FarolSemanticFinalLeaseStage44.CONTRACT_MARKER)
        assertEquals("RAW_STRUCTURAL_EVENT_CANNOT_REVOKE_FINAL_STAGE44", FarolSemanticFinalLeaseStage44.RAW_EVENT_MARKER)
        assertEquals("UNCHANGED_SNAPSHOT_PRESERVES_FINAL_STAGE44", FarolSemanticFinalLeaseStage44.RAW_DUPLICATE_MARKER)
        assertEquals("SAME_ADDRESS_SIGNATURE_PRESERVES_FINAL_STAGE44", FarolSemanticFinalLeaseStage44.SAME_SIGNATURE_MARKER)
        assertEquals("YELLOW_ONLY_AFTER_PROVEN_CARD_CHANGE_STAGE44", FarolSemanticFinalLeaseStage44.PROVEN_CHANGE_MARKER)
    }

    @Test fun redAndGreenWithDistanceAndSignatureOwnAnActiveFinalLease() {
        val red = FarolSemanticFinalLeaseStage44.capture("Red", 45.865, "visual|rua gomes de carvalho 1005 vila olimpia sao paulo sp")
        val green = FarolSemanticFinalLeaseStage44.capture("Green", 3.2, "visual|destino")
        assertTrue(red.activeFinal)
        assertTrue(green.activeFinal)
    }

    @Test fun yellowNeverOwnsAFinalLease() {
        val yellow = FarolSemanticFinalLeaseStage44.capture("Default", null, "visual|destino")
        assertFalse(yellow.activeFinal)
    }

    @Test fun finalColorWithoutDistanceOrSignatureCannotBeLeased() {
        assertFalse(FarolSemanticFinalLeaseStage44.capture("Red", null, "visual|destino").activeFinal)
        assertFalse(FarolSemanticFinalLeaseStage44.capture("Green", 3.2, null).activeFinal)
        assertFalse(FarolSemanticFinalLeaseStage44.capture("Green", 3.2, "   ").activeFinal)
    }

    @Test fun sameAddressSignaturePreservesExistingGoogleFinal() {
        val signature = "visual|rua gomes de carvalho 1005 vila olimpia sao paulo sp"
        val lease = FarolSemanticFinalLeaseStage44.capture("Red", 45.865, signature)
        assertTrue(FarolSemanticFinalLeaseStage44.preservesSameSemanticCard(lease, signature))
    }

    @Test fun differentAddressSignatureRevokesLease() {
        val lease = FarolSemanticFinalLeaseStage44.capture("Red", 45.865, "visual|old")
        assertFalse(FarolSemanticFinalLeaseStage44.preservesSameSemanticCard(lease, "visual|new"))
        assertFalse(FarolSemanticFinalLeaseStage44.preservesSameSemanticCard(lease, null))
    }

    @Test fun physicalInDriveRegressionKeeps459RedAcrossStructuralDuplicate() {
        val signature = "visual|rua gomes de carvalho 1005 vila olimpia sao paulo sp"
        val lease = FarolSemanticFinalLeaseStage44.capture("Red", 45.865, signature)
        assertTrue(lease.activeFinal)
        assertTrue(FarolSemanticFinalLeaseStage44.preservesSameSemanticCard(lease, signature))
        assertEquals(45.865, lease.distanceKm!!, 0.000001)
    }

    @Test fun heavyAdmissionCollectsBeforeAnyDestructiveInvalidation() {
        val s = source("LiveRideAccessibilityService.kt")
        val lease = s.indexOf("val finalLeaseStage44 = FarolSemanticFinalLeaseStage44.capture(")
        val collect = s.indexOf("val collectionStage26 = collectUniversalAccessibilitySnapshotStage28(eventStage26)", lease)
        val visual = s.indexOf("val visualDecisionStage23 = stage23VisualGate.observe(collectionStage26.snapshot.hash)", collect)
        val evaluate = s.indexOf("val evaluationStage19 = FarolLatencyProbeStage9.measureValue(", visual)
        val invalidate = s.indexOf("invalidateOldVisualBeforeCollectStage26(admissionStage26.visualGeneration, eventStartedNsStage26)", evaluate)
        assertTrue(lease >= 0)
        assertTrue(collect > lease)
        assertTrue(visual > collect)
        assertTrue(evaluate > visual)
        assertTrue(invalidate > evaluate)
    }

    @Test fun unchangedRawSnapshotReturnsWithoutRevokingFinal() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("if (!visualDecisionStage23.process) {")
        val b = s.indexOf("val evaluateStartedNsStage26", a)
        val block = s.substring(a, b)
        assertTrue(block.contains("S44_RAW_DUPLICATE_FINAL_PRESERVED"))
        assertTrue(block.contains("return true"))
        assertFalse(block.contains("invalidateOldVisualBeforeCollectStage26("))
        assertFalse(block.contains("showOverlay(RadarColor.Default"))
    }

    @Test fun sameSemanticCandidateReturnsBeforeInvalidation() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("FarolSemanticFinalLeaseStage44.preservesSameSemanticCard(finalLeaseStage44, evaluationStage19.addressSignature)")
        val invalidate = s.indexOf("invalidateOldVisualBeforeCollectStage26(admissionStage26.visualGeneration, eventStartedNsStage26)", a)
        val block = s.substring(a, invalidate)
        assertTrue(block.contains("S44_SEMANTIC_SAME_CARD_FINAL_PRESERVED"))
        assertTrue(block.contains("return true"))
        assertFalse(block.contains("currentDistanceKm = null"))
        assertFalse(block.contains("universalActiveAddressSignature = null"))
    }

    @Test fun noYellowOrFinalStateClearOccursBeforeCollectionProof() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("val finalLeaseStage44 = FarolSemanticFinalLeaseStage44.capture(")
        val b = s.indexOf("val evaluationStage19 = FarolLatencyProbeStage9.measureValue(", a)
        val preProof = s.substring(a, b)
        assertFalse(preProof.contains("currentDistanceKm = null"))
        assertFalse(preProof.contains("universalActiveAddressSignature = null"))
        assertFalse(preProof.contains("invalidateOldVisualBeforeCollectStage26("))
    }

    @Test fun provenDifferentOrAmbiguousCardStillFailsClosedToYellow() {
        val s = source("LiveRideAccessibilityService.kt")
        val call = s.indexOf("invalidateOldVisualBeforeCollectStage26(admissionStage26.visualGeneration, eventStartedNsStage26)")
        val candidateBranch = s.indexOf("if (evaluationStage19 != null) {", call)
        assertTrue(call >= 0 && candidateBranch > call)
        val function = s.indexOf("private fun invalidateOldVisualBeforeCollectStage26(")
        val end = s.indexOf("private fun collectUniversalAccessibilityBlocksStage19", function)
        val block = s.substring(function, end)
        // Stage36 deliberately retains the old address signature as a freshness lease; Stage44 must not undo it.
        assertFalse(block.contains("universalActiveAddressSignature = null"))
        assertTrue(block.contains("currentDistanceKm = null"))
        assertTrue(block.contains("stage19VisualVerificationPending = true"))
        // Stage40 materialization rewrites legacy Orange public callers to Default/yellow.
        assertTrue(block.contains("showOverlay(RadarColor.Default, distanceKm = null)"))
    }

    @Test fun stage43PhysicalOffAndStage41FreshnessRemainPresent() {
        val service = source("LiveRideAccessibilityService.kt")
        assertTrue(service.contains("S43_MANUAL_OFF_RENDER_COMMIT"))
        assertTrue(source("FarolManualOffVisualCommitStage43.kt").contains("MANUAL_OFF_PHYSICAL_VIEW_COMMIT_STAGE43"))
        assertTrue(source("FarolFinalPaintFreshnessStage41.kt").contains("FAROL_SUBSECOND_SAME_FRAME_FINAL_PAINT_STAGE41"))
    }

    @Test fun stage44IntroducesNoPollingTimersSleepOrContinuousOcr() {
        val helper = source("FarolSemanticFinalLeaseStage44.kt")
        assertEquals("NO_POLLING_NO_CONTINUOUS_OCR_STAGE44", FarolSemanticFinalLeaseStage44.NO_POLLING_MARKER)
        listOf(
            "Thread.sleep(", "SystemClock.sleep(", "Timer(", "scheduleAtFixedRate(",
            "fixedRateTimer(", "while (true)", "requestUniversalScreenshotStage19(",
        ).forEach { forbidden -> assertFalse("forbidden Stage44 primitive: $forbidden", helper.contains(forbidden)) }
    }
}
