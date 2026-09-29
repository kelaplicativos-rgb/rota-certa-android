package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Test

class KeepScreenAwakeContract0688Test {
    @Test
    fun labelReflectsActualState() {
        assertEquals("Tela OFF", KeepScreenAwakeContract0688.displayLabel(false))
        assertEquals("Tela ON", KeepScreenAwakeContract0688.displayLabel(true))
    }

    @Test
    fun statusExplainsAutomaticLockBehavior() {
        assertEquals(
            "Tela ON: bloqueio automático desativado.",
            KeepScreenAwakeContract0688.statusMessage(true),
        )
        assertEquals(
            "Tela OFF: bloqueio automático normal.",
            KeepScreenAwakeContract0688.statusMessage(false),
        )
    }

    @Test
    fun shortcutIdIsStable() {
        assertEquals("action_keep_screen_awake", KeepScreenAwakeContract0688.SHORTCUT_ID)
    }
}
