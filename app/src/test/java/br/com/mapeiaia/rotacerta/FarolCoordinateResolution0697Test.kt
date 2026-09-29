package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertTrue
import org.junit.Test

class FarolCoordinateResolution0697Test {
    @Test
    fun providerBudgetsFitGlobalDeadline() {
        assertTrue(FarolCoordinateResolution0697.budgetsFitGlobalDeadline())
    }

    @Test
    fun globalDeadlineIsHardAndShort() {
        assertTrue(FarolCoordinateResolution0697.GLOBAL_DEADLINE_MS in 1_000L..3_500L)
        assertTrue(FarolCoordinateResolution0697.PLATFORM_DEADLINE_MS < 1_000L)
        assertTrue(FarolCoordinateResolution0697.GOOGLE_DEADLINE_MS < 1_500L)
    }
}
