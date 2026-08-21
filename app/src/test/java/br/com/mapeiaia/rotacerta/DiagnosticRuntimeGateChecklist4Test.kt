package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test

class DiagnosticRuntimeGateChecklist4Test {
    @After
    fun cleanup() {
        DiagnosticRuntimeGate.setEnabled(false)
        DiagnosticRuntimeGate.endManualCapture()
    }

    @Test
    fun `usuario pode ligar diagnostico continuo explicitamente`() {
        DiagnosticRuntimeGate.setEnabled(true)

        assertTrue(DiagnosticRuntimeGate.isContinuousEnabled())
        assertTrue(DiagnosticRuntimeGate.isEnabled(nowMillis = 1_000L))
    }

    @Test
    fun `captura manual funciona somente dentro da janela solicitada`() {
        DiagnosticRuntimeGate.beginManualCapture(durationMillis = 1_000L, nowMillis = 5_000L)

        assertTrue(DiagnosticRuntimeGate.isEnabled(nowMillis = 5_500L))
        assertFalse(DiagnosticRuntimeGate.isEnabled(nowMillis = 6_001L))
    }

    @Test
    fun `desligar encerra diagnostico continuo e captura manual`() {
        DiagnosticRuntimeGate.setEnabled(true)
        DiagnosticRuntimeGate.beginManualCapture(durationMillis = 2_000L, nowMillis = 10_000L)
        DiagnosticRuntimeGate.setEnabled(false)

        assertFalse(DiagnosticRuntimeGate.isContinuousEnabled())
        assertFalse(DiagnosticRuntimeGate.isEnabled(nowMillis = 10_001L))
    }
}
