package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolCriticalPathChecklist6Test {
    @Test
    fun `ocr desiste quando acessibilidade aceitou depois do pedido`() {
        assertTrue(FarolCriticalPathPolicy.shouldSkipOcr(1_000L, 1_001L))
        assertTrue(FarolCriticalPathPolicy.shouldSkipOcr(1_000L, 1_000L))
        assertFalse(FarolCriticalPathPolicy.shouldSkipOcr(1_000L, 999L))
    }

    @Test
    fun `captura nunca inicia durante rota ou screenshot normal`() {
        assertFalse(canCapture(route = true, screenshot = false, automatic = false))
        assertFalse(canCapture(route = false, screenshot = true, automatic = false))
        assertFalse(canCapture(route = false, screenshot = false, automatic = true))
        assertTrue(canCapture(route = false, screenshot = false, automatic = false))
    }

    @Test
    fun `alvo visual permanece em oitocentos e cinquenta milissegundos`() {
        assertTrue(FarolCriticalPathPolicy.TARGET_RESULT_MILLIS < 1_000L)
        assertTrue(FarolCriticalPathPolicy.elapsedWithinTarget(1_000L, 1_850L))
        assertFalse(FarolCriticalPathPolicy.elapsedWithinTarget(1_000L, 1_851L))
        assertTrue(FarolCriticalPathPolicy.OCR_FALLBACK_DELAY_MILLIS <= 40L)
    }

    private fun canCapture(route: Boolean, screenshot: Boolean, automatic: Boolean): Boolean =
        FarolCriticalPathPolicy.canStartDeferredCapture(
            serviceReady = true,
            packageStillSelected = true,
            sameRootPackage = true,
            routeRunning = route,
            normalScreenshotRunning = screenshot,
            automaticCaptureRunning = automatic,
        )
}
