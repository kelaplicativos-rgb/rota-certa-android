package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutLongPressPolicy0171Test {
    private fun spec(secondary: BubbleShortcutQuickAction? = null, id: String = "route") = BubbleShortcutSpec(
        id = id,
        emoji = "⚡",
        label = "Rota",
        action = BubbleShortcutAction.OpenRoute,
        doubleTapAction = secondary,
    )

    @Test
    fun longPressRestoresOriginalSecondaryWhenAvailable() {
        val withSecondary = spec(BubbleShortcutQuickAction.DefineDestinationAtCurrentLocation)
        assertEquals(
            ShortcutLongPressResolved0173.Secondary,
            ShortcutGridPolicy0173.resolve(withSecondary),
        )
    }

    @Test
    fun longPressRestoresOriginalPrimaryFallbackWithoutSecondary() {
        assertEquals(
            ShortcutLongPressResolved0173.Primary,
            ShortcutGridPolicy0173.resolve(spec()),
        )
    }

    @Test
    fun onlyLegacyCacheCleanupNeedsConfirmation() {
        assertTrue(
            ShortcutGridPolicy0173.requiresConfirmation(
                spec(BubbleShortcutQuickAction.ClearApplicationCache, id = "clear_clipboard"),
                ShortcutLongPressResolved0173.Secondary,
            ),
        )
        assertFalse(
            ShortcutGridPolicy0173.requiresConfirmation(
                spec(BubbleShortcutQuickAction.CreateQuickReply, id = "quick_replies"),
                ShortcutLongPressResolved0173.Secondary,
            ),
        )
        assertFalse(
            ShortcutGridPolicy0173.requiresConfirmation(
                spec(id = "stop_app"),
                ShortcutLongPressResolved0173.Primary,
            ),
        )
    }
}
