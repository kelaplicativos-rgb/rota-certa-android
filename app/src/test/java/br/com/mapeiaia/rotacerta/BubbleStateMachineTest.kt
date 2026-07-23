package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleStateMachineTest {
    @Test
    fun appliesDecisionOnlyForActiveTokenSamePackageAndSameSnapshot() {
        val machine = BubbleStateMachine()
        val token = BubbleAnalysisToken(
            packageName = "com.regional.driver",
            snapshotHash = 123,
            templateId = "card-1",
        )

        machine.markAnalyzing(token)

        assertTrue(machine.canApplyResult(token, "com.regional.driver", 123))
    }

    @Test
    fun rejectsDecisionWhenScreenChangedDuringAnalysis() {
        val machine = BubbleStateMachine()
        val token = BubbleAnalysisToken(
            packageName = "com.regional.driver",
            snapshotHash = 123,
            templateId = "card-1",
        )

        machine.markAnalyzing(token)

        assertFalse(machine.canApplyResult(token, "com.regional.driver", 456))
    }

    @Test
    fun rejectsDecisionWhenPackageChangedDuringAnalysis() {
        val machine = BubbleStateMachine()
        val token = BubbleAnalysisToken(
            packageName = "com.regional.driver",
            snapshotHash = 123,
            templateId = "card-1",
        )

        machine.markAnalyzing(token)

        assertFalse(machine.canApplyResult(token, "com.other.driver", 123))
    }

    @Test
    fun clearingDecisionMovesBackToWaitingForRegisteredCard() {
        val machine = BubbleStateMachine()
        val token = BubbleAnalysisToken(
            packageName = "com.regional.driver",
            snapshotHash = 123,
            templateId = "card-1",
        )

        machine.markAnalyzing(token)
        machine.clearCardDecision()

        assertEquals(BubbleLifecycleState.WaitingForRegisteredCard, machine.state)
        assertFalse(machine.canApplyResult(token, "com.regional.driver", 123))
    }
}
