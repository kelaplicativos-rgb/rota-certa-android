package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test

class DiagnosticRuntimeGateChecklist4Test {
    @After
    fun cleanup() {
        DiagnosticRuntimeGate.endManualCapture()
    }

    @Test
    fun `configuracao antiga nao consegue ligar diagnostico continuo`() {
        DiagnosticRuntimeGate.setEnabled(true)

        assertFalse(DiagnosticRuntimeGate.isEnabled(nowMillis = 1_000L))
    }

    @Test
    fun `captura manual funciona somente dentro da janela solicitada`() {
        DiagnosticRuntimeGate.beginManualCapture(durationMillis = 1_000L, nowMillis = 5_000L)

        assertTrue(DiagnosticRuntimeGate.isEnabled(nowMillis = 5_500L))
        assertFalse(DiagnosticRuntimeGate.isEnabled(nowMillis = 6_001L))
    }

    @Test
    fun `desligar encerra imediatamente a captura manual`() {
        DiagnosticRuntimeGate.beginManualCapture(durationMillis = 2_000L, nowMillis = 10_000L)
        DiagnosticRuntimeGate.setEnabled(false)

        assertFalse(DiagnosticRuntimeGate.isEnabled(nowMillis = 10_001L))
    }
}
