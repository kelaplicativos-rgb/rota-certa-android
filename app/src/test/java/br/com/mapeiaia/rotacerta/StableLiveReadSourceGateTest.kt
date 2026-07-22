package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Test

class StableLiveReadSourceGateTest {
    @Test
    fun incompleteOcrCannotClearValidAccessibilityDecision() {
        val gate = StableLiveReadSourceGate()

        assertEquals(
            StableLiveReadAction.Analyze,
            gate.submit(StableLiveReadSource.Accessibility, validRegisteredCard = true),
        )
        assertEquals(
            StableLiveReadAction.Ignore,
            gate.submit(StableLiveReadSource.Ocr, validRegisteredCard = false),
        )
    }

    @Test
    fun activeSourceClearsImmediatelyWhenItsCardLosesNumber() {
        val gate = StableLiveReadSourceGate()

        gate.submit(StableLiveReadSource.Accessibility, validRegisteredCard = true)

        assertEquals(
            StableLiveReadAction.Clear,
            gate.submit(StableLiveReadSource.Accessibility, validRegisteredCard = false),
        )
    }

    @Test
    fun ocrCanOwnCardWhenAccessibilityNeverFindsValidCard() {
        val gate = StableLiveReadSourceGate()

        assertEquals(
            StableLiveReadAction.Ignore,
            gate.submit(StableLiveReadSource.Accessibility, validRegisteredCard = false),
        )
        assertEquals(
            StableLiveReadAction.Analyze,
            gate.submit(StableLiveReadSource.Ocr, validRegisteredCard = true),
        )
        assertEquals(
            StableLiveReadAction.Ignore,
            gate.submit(StableLiveReadSource.Accessibility, validRegisteredCard = false),
        )
    }

    @Test
    fun resetAllowsNewWindowToChooseAnotherSource() {
        val gate = StableLiveReadSourceGate()
        gate.submit(StableLiveReadSource.Accessibility, validRegisteredCard = true)

        gate.reset()

        assertEquals(
            StableLiveReadAction.Analyze,
            gate.submit(StableLiveReadSource.Ocr, validRegisteredCard = true),
        )
    }
}
