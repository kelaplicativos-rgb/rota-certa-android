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
    fun allModulesRemainAvailableInHomeBubbleGridWithoutCustomization() {
        assertTrue(main.contains("ShortcutModulesHome0171"))
        assertTrue(main.contains("HomeModuleBubbleGridPolicy0175.rows"))
        assertTrue(main.contains("BubbleShortcutCatalog.modules"))
        assertTrue(main.contains("HomeModuleBubble0175"))
        assertTrue(main.contains("HomeModuleInlinePanel0175"))
        assertTrue(main.contains("uma bolinha para cada módulo e recurso"))
        assertFalse(main.contains("ShortcutModuleCard0174"))
        assertFalse(main.contains("Ação ao manter pressionado o atalho"))
        assertFalse(main.contains("Salvar ação do toque longo"))
        assertFalse(main.contains("combinedClickable"))
    }

    @Test
    fun selectedModuleContentStaysBelowItsOwnRowAndOnlyOneSelectionIsAuthoritative() {
        assertTrue(main.contains("HomeModuleExpansionPolicy0174.isExpanded"))
        assertTrue(main.contains("HomeModuleBubbleGridPolicy0175.expandedIdInRow"))
        assertTrue(main.contains("rowModules.firstOrNull"))
        assertTrue(main.contains("content = { moduleContent(expandedModule.spec) }"))
        assertTrue(main.contains("moduleContent: @Composable (BubbleShortcutSpec) -> Unit"))
    }

    @Test
    fun floatingOverlayKeepsEntryIdentityWithoutThePlusShortcut() {
        assertTrue(overlay.contains("ResolvedShortcutGridEntry0179"))
        assertFalse(overlay.contains("shortcut_add_0179"))
        assertFalse(overlay.contains("(doubleAction ?: singleAction).invoke()"))
        assertTrue(service.contains("executeShortcutQuickTap0180"))
        assertTrue(service.contains("executeShortcutHold0180"))
        assertTrue(service.contains("entry0180.holdActionType0186"))
        assertTrue(overlay.contains("ShortcutInteractionPolicy0186.HOLD_MILLIS"))
        assertFalse(service.contains("SHORTCUT_TRIPLE_TAP_OPEN_EDITOR_0180"))
        assertFalse(service.contains("private fun executeShortcutLongPress0179"))
    }

    @Test
    fun legacyCustomizationRemainsClearedAndNewGridUsesSeparateStore() {
        assertTrue(policy.contains("clearLegacyPreferences"))
        assertTrue(policy.contains(".clear()"))
        assertTrue(service.contains("ShortcutGridPreferenceStore0179"))
        assertTrue(service.contains("openShortcutCustomization0179"))
        assertTrue(service.contains("EXTRA_EDIT_SHORTCUT_ENTRY_ID_0180"))
    }
}
