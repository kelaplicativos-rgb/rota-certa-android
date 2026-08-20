package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutPerEntryMenuContract0180Test {
    private val overlay = File("src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt").readText()
    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()

    @Test
    fun shortcutTapHasNoDelayOrIntermediateMenu() {
        assertTrue(overlay.contains("SHORTCUT_DIRECT_TAP_AND_HOLD_0186"))
        assertTrue(overlay.contains("singleAction()"))
        assertFalse(overlay.contains("tapCount0180"))
        assertFalse(service.contains("showShortcutActionMenu0183(entry0180.spec)"))
        assertTrue(service.contains("executeShortcutModule(entry0180.spec)"))
        assertTrue(overlay.contains("ShortcutInteractionPolicy0186.HOLD_MILLIS"))
    }

    @Test
    fun plusWasRemovedFromFloatingGrid() {
        assertFalse(overlay.contains("shortcut_add_0179"))
        assertTrue(overlay.contains("plus=0"))
    }
}
