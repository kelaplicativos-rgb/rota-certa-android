package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class UniversalRuntimeGuardsTest {
    @Test
    fun emptyOcrDoesNotClearValidAccessibilityCard() {
        val gate = UniversalLiveReadGate()

        assertEquals(
            UniversalLiveReadAction.Analyze,
            gate.submit(UniversalLiveReadSource.Accessibility, active = true, nowMillis = 1_000L),
        )
        assertEquals(
            UniversalLiveReadAction.Ignore,
            gate.submit(UniversalLiveReadSource.Ocr, active = false, nowMillis = 1_100L),
        )
    }

    @Test
    fun ocrFallbackSurvivesTemporaryEmptyAccessibility() {
        val gate = UniversalLiveReadGate()

        assertEquals(
            UniversalLiveReadAction.Analyze,
            gate.submit(UniversalLiveReadSource.Ocr, active = true, nowMillis = 2_000L),
        )
        assertEquals(
            UniversalLiveReadAction.Ignore,
            gate.submit(UniversalLiveReadSource.Accessibility, active = false, nowMillis = 2_300L),
        )
        assertEquals(
            UniversalLiveReadAction.Clear,
            gate.submit(UniversalLiveReadSource.Ocr, active = false, nowMillis = 2_350L),
        )
    }

    @Test
    fun accessibilityKeepsPriorityOverCompetingOcr() {
        val gate = UniversalLiveReadGate()

        gate.submit(UniversalLiveReadSource.Accessibility, active = true, nowMillis = 3_000L)
        assertEquals(
            UniversalLiveReadAction.Ignore,
            gate.submit(UniversalLiveReadSource.Ocr, active = true, nowMillis = 3_200L),
        )
    }

    @Test
    fun duplicateHistoryIsBlockedInsideWindow() {
        val deduper = UniversalAnalysisDeduper(duplicateWindowMillis = 60_000L)

        assertTrue(deduper.shouldPersist("destino|vermelho|10.057", nowMillis = 10_000L))
        assertFalse(deduper.shouldPersist("destino|vermelho|10.057", nowMillis = 10_150L))
        assertTrue(deduper.shouldPersist("outro|verde|4.649", nowMillis = 10_200L))
        assertTrue(deduper.shouldPersist("destino|vermelho|10.057", nowMillis = 70_001L))
    }
}
