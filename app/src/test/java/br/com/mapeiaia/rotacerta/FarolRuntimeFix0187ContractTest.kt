package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolRuntimeFix0187ContractTest {
    private val source = File("src/main/java/br/com/mapeiaia/rotacerta")
    private fun read(name: String) = File(source, name).readText()

    @Test
    fun staleRecoveryCannotMutateTheFarolOrMixBuffersDirectly() {
        val service = read("LiveRideAccessibilityService.kt")
        assertTrue(service.contains("FarolRecoveryBinding0187"))
        assertTrue(service.contains("isRecoveryBindingFresh0187(binding0187, snapshotText0161)"))
        assertTrue(service.contains("BUBBLE_FAILED_CARD_RECOVERY_DISCARDED_0187"))
        assertFalse(service.contains("applyRecoveredCard0161(\n                    selectedPackage0161 = selectedPackageChecklist13"))
    }

    @Test
    fun criticalTimingIsMonotonicAndExternalNoiseIsCollapsed() {
        val service = read("LiveRideAccessibilityService.kt")
        assertTrue(service.contains("universalLastActiveReadAtElapsedMillis0187"))
        assertTrue(service.contains("FarolExternalPackageEventGate0187"))
        assertFalse(service.contains("System.currentTimeMillis() - universalLastActiveReadAtMillis"))
        assertFalse(service.contains("activeAnalysisStartedAt143"))
    }

    @Test
    fun diagnosticsCannotThrowIntoTheAccessibilityCriticalPath() {
        val unified = read("UnifiedDebugLog.kt")
        val recorder = read("FarolFlightRecorder0163.kt")
        assertTrue(unified.contains("runCatching {\n            FarolFlightRecorder0163.record"))
        assertTrue(recorder.contains("runCatching {\n            appendEvent"))
    }
    @Test
    fun packageRootAndWindowAreCapturedBeforeTextTraversal() {
        val service = read("LiveRideAccessibilityService.kt")
        val safety = read("FarolRuntimeSafety0187.kt")
        assertTrue(safety.contains("ATOMIC_ROOT_SNAPSHOT_GATE_0187"))
        assertTrue(safety.contains("ACCESSIBILITY_READ_BINDING_0187"))
        assertTrue(service.contains("captureRootHandle0187()"))
        assertTrue(service.contains("collectImmediateVisibleTextChecklist13(rootHandle0187.node)"))
        assertTrue(service.contains("collectFailedCardNodeLines0161(rootHandle0187.node)"))
        assertTrue(service.contains("BUBBLE_ROOT_SNAPSHOT_REJECTED_0187"))
        assertTrue(service.contains("BUBBLE_ACCESSIBILITY_READ_DISCARDED_0187"))
        assertFalse(service.contains("val rootPackage = currentRootPackageName()"))
    }

    @Test
    fun accessibilityEvaluationRevalidatesItsImmutableBindingAfterSuspension() {
        val service = read("LiveRideAccessibilityService.kt")
        assertTrue(service.contains("readBinding0187: FarolReadBinding0187? = null"))
        assertTrue(service.contains("!isReadBindingFresh0187(readBinding0187)"))
        assertTrue(service.contains("readBinding0187 = readBinding0187"))
        assertFalse(service.contains("val currentWindow0187 = safeRootWindowId0185()"))
        assertFalse(service.contains("val currentAccessibility0187 = collectImmediateVisibleTextChecklist13()"))
        assertFalse(service.contains("val currentNodes0187 = collectFailedCardNodeLines0161()"))
    }

    @Test
    fun rejectedRootSnapshotCannotClearOrRenderTheBubble() {
        val service = read("LiveRideAccessibilityService.kt")
        val safety = read("FarolRuntimeSafety0187.kt")
        val start = service.indexOf("if (!rootAdmission0187.accepted || rootHandle0187 == null)")
        val end = service.indexOf("lastRejectedForegroundPackage0162 = null", start)
        assertTrue(start >= 0)
        assertTrue(end > start)
        val rejectionBlock = service.substring(start, end)
        assertTrue(safety.contains("REJECTED_SNAPSHOT_HAS_NO_VISUAL_SIDE_EFFECT_0187_PHASE3"))
        assertTrue(rejectionBlock.contains("FarolRejectedSnapshotPolicy0187Phase3.effect"))
        assertTrue(rejectionBlock.contains("BUBBLE_ROOT_SNAPSHOT_DISCARDED_0187_PHASE3"))
        assertTrue(rejectionBlock.contains("invalidateRejectedSnapshotRead0187Phase3"))
        assertFalse(rejectionBlock.contains("hardClearUniversalTwoAddress"))
        assertFalse(rejectionBlock.contains("showOverlay"))
    }



    @Test
    fun allRouteResultsUseMonotonicDecisionBindingAndCentralCancellation() {
        val service = read("LiveRideAccessibilityService.kt")
        val safety = read("FarolRuntimeSafety0187.kt")
        assertTrue(safety.contains("DECISION_RESULT_MONOTONIC_BINDING_0187_PHASE4"))
        assertTrue(service.contains("createDecisionBinding0187Phase4"))
        assertTrue(service.contains("isDecisionBindingFresh0187Phase4"))
        assertTrue(service.contains("BUBBLE_ROUTE_RESULT_DISCARDED_0187_PHASE4"))
        assertFalse(service.contains("private fun isUniversalResultFresh("))

        val cancellationStart = service.indexOf("private fun invalidateFarolAsyncWork0187Phase4")
        val cancellationEnd = service.indexOf("private fun invalidateRejectedSnapshotRead0187Phase3", cancellationStart)
        assertTrue(cancellationStart >= 0)
        assertTrue(cancellationEnd > cancellationStart)
        val cancellationBlock = service.substring(cancellationStart, cancellationEnd)
        assertTrue(cancellationBlock.contains("universalRouteJob?.cancel()"))
        assertTrue(cancellationBlock.contains("analyzeJob?.cancel()"))
        assertTrue(cancellationBlock.contains("screenshotFallbackJob127?.cancel()"))
        assertTrue(cancellationBlock.contains("partialReadConfirmationJobChecklist14?.cancel()"))
        assertTrue(cancellationBlock.contains("liveAnalysisJob?.cancel()"))

        val rejectedStart = service.indexOf("private fun invalidateRejectedSnapshotRead0187Phase3")
        val rejectedEnd = service.indexOf("private fun handleRejectedForeground0162", rejectedStart)
        assertTrue(service.substring(rejectedStart, rejectedEnd).contains("invalidateFarolAsyncWork0187Phase4"))
        val clearStart = service.indexOf("private fun hardClearUniversalTwoAddress")
        val clearEnd = service.indexOf("private fun shouldProtectLockedPopupSession128", clearStart)
        assertTrue(service.substring(clearStart, clearEnd).contains("invalidateFarolAsyncWork0187Phase4"))
    }

}
