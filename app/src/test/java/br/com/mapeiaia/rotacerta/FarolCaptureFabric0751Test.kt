package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolCaptureFabric0751Test {
    @Test fun emptyAccessibilityFromSelectedAppEscalatesImmediately() {
        assertTrue(FarolCaptureFabric0751.shouldEscalateEmptyAccessibility(true, "   "))
        assertFalse(FarolCaptureFabric0751.shouldEscalateEmptyAccessibility(false, ""))
        assertFalse(FarolCaptureFabric0751.shouldEscalateEmptyAccessibility(true, "Rua Beckman 300"))
    }

    @Test fun android34UsesConcreteWindowScreenshotWhenWindowIsKnown() {
        assertTrue(FarolCaptureFabric0751.shouldUseWindowScreenshot(34, 17))
        assertTrue(FarolCaptureFabric0751.shouldUseWindowScreenshot(36, 1))
        assertFalse(FarolCaptureFabric0751.shouldUseWindowScreenshot(33, 17))
        assertFalse(FarolCaptureFabric0751.shouldUseWindowScreenshot(36, 0))
        assertFalse(FarolCaptureFabric0751.shouldUseWindowScreenshot(36, null))
    }
}
