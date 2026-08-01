#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
test_path = root / "app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutLongPressContract0171Test.kt"

test_path.write_text('''package br.com.mapeiaia.rotacerta

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
    fun floatingOverlayStillDelegatesToFixedRestoredPolicy() {
        assertTrue(overlay.contains("onShortcutLongPress"))
        assertFalse(overlay.contains("(doubleAction ?: singleAction).invoke()"))
        assertTrue(service.contains("executeShortcutLongPress0173"))
        assertTrue(service.contains("SHORTCUT_LONG_PRESS_FIXED_0173"))
        assertFalse(service.contains("ShortcutLongPressPreferenceStore0171"))
        assertTrue(policy.contains("ShortcutLongPressResolved0173.Secondary"))
        assertTrue(policy.contains("ShortcutLongPressResolved0173.Primary"))
    }

    @Test
    fun legacyCustomizationIsClearedAndSensitiveConfirmationIsPreserved() {
        assertTrue(policy.contains("clearLegacyPreferences"))
        assertTrue(policy.contains(".clear()"))
        assertTrue(overlay.contains("showShortcutConfirmation0171"))
        assertTrue(service.contains("ShortcutGridPolicy0173.requiresConfirmation"))
        assertTrue(service.contains("executeShortcutDoubleTap"))
    }
}
''', encoding="utf-8")

print("CONTRATO_HOME_BOLINHAS_0175_ATUALIZADO")
