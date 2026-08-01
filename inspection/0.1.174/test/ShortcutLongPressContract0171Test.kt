package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutLongPressContract0171Test {
    private val main = File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").readText()
    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
    private val overlay = File("src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt").readText()
    private val policy = File("src/main/java/br/com/mapeiaia/rotacerta/ShortcutLongPressPolicy0171.kt").readText()

    @Test
    fun allModulesRemainAvailableFromHomeWithoutCustomization() {
        assertTrue(main.contains("ShortcutModulesHome0171"))
        assertTrue(main.contains("BubbleShortcutCatalog.modules.forEach"))
        assertTrue(main.contains("Ações fixas na grade"))
        assertTrue(main.contains("quando não existe ação secundária, repete a principal"))
        assertFalse(main.contains("Ação ao manter pressionado o atalho"))
        assertFalse(main.contains("Salvar ação do toque longo"))
    }

    @Test
    fun overlayDelegatesToFixedRestoredPolicy() {
        assertTrue(overlay.contains("onShortcutLongPress"))
        assertFalse(overlay.contains("(doubleAction ?: singleAction).invoke()"))
        assertTrue(service.contains("executeShortcutLongPress0173"))
        assertTrue(service.contains("SHORTCUT_LONG_PRESS_FIXED_0173"))
        assertFalse(service.contains("ShortcutLongPressPreferenceStore0171"))
        assertTrue(policy.contains("ShortcutLongPressResolved0173.Secondary"))
        assertTrue(policy.contains("ShortcutLongPressResolved0173.Primary"))
    }

    @Test
    fun legacyCustomizationIsClearedAndCacheConfirmationIsPreserved() {
        assertTrue(policy.contains("clearLegacyPreferences"))
        assertTrue(policy.contains(".clear()"))
        assertTrue(overlay.contains("showShortcutConfirmation0171"))
        assertTrue(service.contains("ShortcutGridPolicy0173.requiresConfirmation"))
        assertTrue(service.contains("executeShortcutDoubleTap"))
    }
}
