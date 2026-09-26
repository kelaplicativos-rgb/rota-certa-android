package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutInteractionPolicy0186Test {
    @Test
    fun quickReleaseIsImmediateAndLongReleaseIsConsumed() {
        assertEquals(1_500L, ShortcutInteractionPolicy0186.HOLD_MILLIS)
        assertEquals(ShortcutReleaseAction0186.Quick, ShortcutInteractionPolicy0186.release(ShortcutPressState0186()))
        val consumed = ShortcutInteractionPolicy0186.consumeLong(ShortcutPressState0186())
        assertTrue(consumed.longConsumed)
        assertEquals(ShortcutReleaseAction0186.None, ShortcutInteractionPolicy0186.release(consumed))
    }

    @Test
    fun movementCancelsQuickAndLong() {
        val moved = ShortcutInteractionPolicy0186.moved(ShortcutPressState0186())
        assertTrue(moved.moved)
        assertFalse(ShortcutInteractionPolicy0186.consumeLong(moved).longConsumed)
        assertEquals(ShortcutReleaseAction0186.None, ShortcutInteractionPolicy0186.release(moved))
    }
}
