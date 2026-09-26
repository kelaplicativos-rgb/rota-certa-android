package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolNotificationFailureCircuit0170Test {
    @Test
    fun failureBlocksOnlyTheNotificationWakePathForBoundedTime() {
        val circuit = FarolNotificationFailureCircuit0170(cooldownMillis = 1_000L)
        assertTrue(circuit.canAttempt(10_000L))
        circuit.onFailure(10_000L)
        assertFalse(circuit.canAttempt(10_999L))
        assertTrue(circuit.canAttempt(11_000L))
    }

    @Test
    fun resetReleasesCircuitImmediately() {
        val circuit = FarolNotificationFailureCircuit0170(cooldownMillis = 60_000L)
        circuit.onFailure(1_000L)
        assertFalse(circuit.canAttempt(1_001L))
        circuit.reset()
        assertTrue(circuit.canAttempt(1_001L))
    }
}
