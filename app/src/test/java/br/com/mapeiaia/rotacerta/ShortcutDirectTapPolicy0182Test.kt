package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortcutDirectTapPolicy0182Test {
    @Test
    fun allLegacyGestureValuesResolveToThePrimaryShortcutAction() {
        ShortcutGestureAction0180.values().forEach { persisted ->
            assertEquals(
                ShortcutGestureAction0180.PRIMARY_ACTION,
                ShortcutDirectTapPolicy0182.actionForTap(persisted),
            )
        }
    }

    @Test
    fun actionIsReachedWithAtMostTwoTapsFromTheMainBubble() {
        assertEquals(2, ShortcutDirectTapPolicy0182.MAX_TAPS_FROM_MAIN_BUBBLE_TO_ACTION)
    }
}
