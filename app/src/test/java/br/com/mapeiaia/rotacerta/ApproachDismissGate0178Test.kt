package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApproachDismissGate0178Test {
    @Test
    fun dismissalSurvivesCurrentApproachAndClearsAfterExit() {
        val gate = ApproachDismissGate0178()
        gate.dismissUntilExit("radar-1")
        assertTrue(gate.isDismissed("radar-1"))
        gate.clearAfterExit("radar-1")
        assertFalse(gate.isDismissed("radar-1"))
    }

    @Test
    fun removedTargetsCannotLeakInMemory() {
        val gate = ApproachDismissGate0178()
        gate.dismissUntilExit("radar-1")
        gate.dismissUntilExit("radar-2")
        gate.retainActive(setOf("radar-2"))
        assertFalse(gate.isDismissed("radar-1"))
        assertTrue(gate.isDismissed("radar-2"))
    }
}
