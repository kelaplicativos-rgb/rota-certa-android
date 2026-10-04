package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TimelineCardShortcutGlyphs0728Test {
    @Test
    fun manualPassengerShortcutUsesExactRequestedGlyphs() {
        assertEquals("+👤", TIMELINE_MANUAL_PASSENGER_SHORTCUT_0728)
    }

    @Test
    fun publicBlaBlaCarShortcutUsesGlobeOnly() {
        assertEquals("🌐", TIMELINE_PUBLIC_BLABLACAR_SHORTCUT_0728)
    }

    @Test
    fun cardShortcutsRemainVisuallyDistinct() {
        assertNotEquals(
            TIMELINE_MANUAL_PASSENGER_SHORTCUT_0728,
            TIMELINE_PUBLIC_BLABLACAR_SHORTCUT_0728,
        )
    }
}
