package br.com.mapeiaia.rotacerta.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreCardAnalysisCoalescerTest {
    @Test
    fun sameCardOcrVariationDoesNotRestartActiveRoute() {
        val gate = CoreCardAnalysisCoalescer()

        assertEquals(
            CoreCardAnalysisAction.Start,
            gate.beforeStart("files|universal|rua a|44", activeJob = false, hasAppliedDecision = false),
        )
        assertEquals(
            CoreCardAnalysisAction.CoalesceActive,
            gate.beforeStart("files|universal|rua a|44", activeJob = true, hasAppliedDecision = false),
        )
    }

    @Test
    fun completedDecisionIsReusedForSameVisibleCard() {
        val gate = CoreCardAnalysisCoalescer()
        val signature = "files|universal|rua a|44"

        gate.beforeStart(signature, activeJob = false, hasAppliedDecision = false)
        gate.complete(signature)

        assertEquals(
            CoreCardAnalysisAction.ReuseCompleted,
            gate.beforeStart(signature, activeJob = false, hasAppliedDecision = true),
        )
    }

    @Test
    fun differentCardStillStartsAndSupersedesPreviousRoute() {
        val gate = CoreCardAnalysisCoalescer()

        gate.beforeStart("files|universal|rua a|44", activeJob = false, hasAppliedDecision = false)
        assertEquals(
            CoreCardAnalysisAction.Start,
            gate.beforeStart("files|universal|rua b|51", activeJob = true, hasAppliedDecision = false),
        )
        assertTrue(gate.isCurrent("files|universal|rua b|51"))
        assertFalse(gate.isCurrent("files|universal|rua a|44"))
    }

    @Test
    fun leavingCardInvalidatesActiveAndCompletedIdentity() {
        val gate = CoreCardAnalysisCoalescer()
        val signature = "files|universal|rua a|44"

        gate.beforeStart(signature, activeJob = false, hasAppliedDecision = false)
        gate.complete(signature)
        gate.invalidate()

        assertEquals(
            CoreCardAnalysisAction.Start,
            gate.beforeStart(signature, activeJob = false, hasAppliedDecision = true),
        )
    }
}
