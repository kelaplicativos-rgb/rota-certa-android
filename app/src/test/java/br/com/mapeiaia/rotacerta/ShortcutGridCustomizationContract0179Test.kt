package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutGridCustomizationContract0179Test {
    private val main = File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").readText()
    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
    private val overlay = File("src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt").readText()
    private val store = File("src/main/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179.kt").readText()

    @Test
    fun homeIsTheOnlyActionSelectionSurface() {
        assertFalse(overlay.contains("shortcut_add_0179"))
        assertTrue(main.contains("HomeShortcutActions0184"))
        assertTrue(main.contains("Adicionar à grade"))
        assertTrue(main.contains("Remover da grade"))
        assertTrue(main.contains("Esvaziar grade"))
    }

    @Test
    fun emptyGridRoutesToHomeAndTapExecutesDirectly() {
        assertTrue(service.contains("shortcuts0184.isEmpty()"))
        assertTrue(service.contains("empty_action_grid_0184"))
        assertTrue(service.contains("executeShortcutModule(entry0180.spec)"))
        assertFalse(service.contains("showShortcutActionMenu0183(entry0180.spec)"))
    }

    @Test
    fun persistenceIsBoundedTypedAndMigrated() {
        assertTrue(store.contains("MAX_GRID_ITEMS"))
        assertTrue(store.contains("ShortcutActionCatalog0184.allSpecs"))
        assertTrue(store.contains("lastUpdateTime > info.firstInstallTime"))
        assertTrue(store.contains("initialEntries(isUpgradeInstallation())"))
        assertFalse(store.contains("Intent.parseUri"))
    }
}
