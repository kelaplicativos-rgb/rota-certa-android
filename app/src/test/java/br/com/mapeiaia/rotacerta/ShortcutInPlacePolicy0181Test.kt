package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutInPlacePolicy0181Test {
    @Test
    fun internalModulesUseOverlayFirst() {
        assertTrue("saved_places" in ShortcutInPlacePolicy0181.overlayFirstIds)
        assertTrue("alerts" in ShortcutInPlacePolicy0181.overlayFirstIds)
        assertTrue("route" in ShortcutInPlacePolicy0181.overlayFirstIds)
        assertTrue("radars" in ShortcutInPlacePolicy0181.overlayFirstIds)
        assertTrue("finance" in ShortcutInPlacePolicy0181.overlayFirstIds)
    }

    @Test
    fun radarStatusShowsCount() {
        assertEquals(
            "Radares carregados: 42",
            ShortcutInPlacePolicy0181.statusLine("radars", "cinza", null, 42),
        )
    }
}
