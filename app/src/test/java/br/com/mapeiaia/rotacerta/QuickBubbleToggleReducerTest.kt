package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickBubbleToggleReducerTest {
    @Test
    fun everyFunctionalBubbleTogglesOnAndOff() {
        QuickBubbleToggle.values().forEach { toggle ->
            val initial = AppSettings()
            val once = QuickBubbleToggleReducer.toggle(initial, toggle)
            val twice = QuickBubbleToggleReducer.toggle(once, toggle)
            assertEquals("Segundo toque deve restaurar o estado de $toggle", initial, twice)
        }
    }

    @Test
    fun liveReadingIsIndependentFromMainSwitch() {
        val initial = AppSettings(appEnabled = true, liveReadingEnabled = true)
        val updated = QuickBubbleToggleReducer.toggle(initial, QuickBubbleToggle.LiveReading)

        assertTrue(updated.appEnabled)
        assertFalse(updated.liveReadingEnabled)
    }

    @Test
    fun homeAndAlternativeCanBeControlledSeparately() {
        val initial = AppSettings(homeTargetEnabled = true, alternativeTargetEnabled = true)
        val homeOff = QuickBubbleToggleReducer.toggle(initial, QuickBubbleToggle.HomeTarget)

        assertFalse(homeOff.homeTargetEnabled)
        assertTrue(homeOff.alternativeTargetEnabled)
    }
}
