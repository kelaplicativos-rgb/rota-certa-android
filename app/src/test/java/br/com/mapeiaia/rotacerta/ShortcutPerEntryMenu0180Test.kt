package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutPerEntryMenu0180Test {
    @Test
    fun legacyGestureValuesAreNormalizedToDirectTap() {
        val original = ShortcutGridCustomizationPolicy0179.legacyDefaults().first()
        val persisted = original.copy(
            quickAction0180 = ShortcutGestureAction0180.NONE,
            holdAction0180 = ShortcutGestureAction0180.NONE,
            holdActionType0186 = null,
        )
        val resolved = ShortcutGridCustomizationPolicy0179.resolve(listOf(persisted)).single()
        assertEquals(ShortcutGestureAction0180.PRIMARY_ACTION, resolved.quickAction0180)
        assertEquals(ShortcutGestureAction0180.NONE, resolved.holdAction0180)
    }

    @Test
    fun thresholdsRemainCompatibleButDoNotDelaySingleTap() {
        assertEquals(1_500L, ShortcutGesturePolicy0179.SHORTCUT_LONG_PRESS_MILLIS)
        assertTrue(ShortcutGesturePolicy0179.MAX_GRID_ITEMS == 32)
    }
}
