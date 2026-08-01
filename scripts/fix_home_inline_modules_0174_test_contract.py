from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
main_path = root / "app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"
policy_path = root / "app/src/main/java/br/com/mapeiaia/rotacerta/HomeModuleExpansionPolicy0174.kt"
build_path = root / "app/build.gradle.kts"
legacy_contract_path = root / "app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutLongPressContract0171Test.kt"

main = main_path.read_text(encoding="utf-8")
policy = policy_path.read_text(encoding="utf-8")
build = build_path.read_text(encoding="utf-8")

required = {
    "version name": 'versionName = "0.1.174"' in build,
    "version code": "versionCode = 5350" in build,
    "inline marker": "CONTRACT_MARKER" in main and "HOME_MODULE_CONTENT_INLINE_0174" in policy,
    "single expanded policy": "HomeModuleExpansionPolicy0174.toggle" in main,
    "inline card content": "content()" in main and "ShortcutModuleCard0174" in main,
    "module content renderer": "moduleContent(module.spec)" in main,
    "destination inline": "BubbleShortcutAction.OpenDestination -> AnalysisScreen" in main,
    "settings inline": "-> SettingsScreen(" in main,
    "reports inline": "-> ReportsGroupScreen(" in main,
    "no generic open module button": 'Text("Abrir módulo")' not in main,
    "inline contract marker": "home_module_content_inline_0_1_174" in main,
}
failed = [name for name, ok in required.items() if not ok]
if failed:
    raise SystemExit("FALHA_CONTRATO_HOME_INLINE_0174: " + ", ".join(failed))

for forbidden in (
    "var expanded by remember(spec.id)",
    "onOpenModule = ::openShortcutModuleFromHome0171",
):
    if forbidden in main:
        raise SystemExit(f"FALHA_ESTADO_LOCAL_0174: {forbidden}")

legacy_contract_path.write_text(
    r'''package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutLongPressContract0171Test {
    private val main = File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").readText()
    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
    private val overlay = File("src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt").readText()
    private val expansionPolicy = File("src/main/java/br/com/mapeiaia/rotacerta/HomeModuleExpansionPolicy0174.kt").readText()

    @Test
    fun allModulesRemainAvailableInlineFromHomeWithoutCustomization() {
        assertTrue(main.contains("ShortcutModulesHome0171"))
        assertTrue(main.contains("BubbleShortcutCatalog.modules.forEach"))
        assertTrue(main.contains("ShortcutModuleCard0174"))
        assertTrue(main.contains("moduleContent(module.spec)"))
        assertTrue(main.contains("content()"))
        assertFalse(main.contains("Text(\"Abrir módulo\")"))
        assertFalse(main.contains("Ação ao manter pressionado o atalho"))
        assertFalse(main.contains("Salvar ação do toque longo"))
    }

    @Test
    fun onlyOneModuleRemainsExpandedAndItsContentStaysInsideItsCard() {
        assertTrue(expansionPolicy.contains("HOME_MODULE_CONTENT_INLINE_0174"))
        assertTrue(expansionPolicy.contains("fun toggle"))
        assertTrue(main.contains("HomeModuleExpansionPolicy0174.toggle"))
        assertTrue(main.contains("content = { moduleContent(module.spec) }"))
        assertFalse(main.contains("var expanded by remember(spec.id)"))
    }

    @Test
    fun overlayKeepsDeterministicLongPressWithoutStoredCustomization() {
        assertTrue(overlay.contains("onShortcutLongPress"))
        assertFalse(overlay.contains("(doubleAction ?: singleAction).invoke()"))
        assertTrue(service.contains("executeShortcutLongPress0173"))
        assertTrue(service.contains("SHORTCUT_LONG_PRESS_FIXED_0173"))
        assertFalse(service.contains("ShortcutLongPressPreferenceStore0171"))
    }

    @Test
    fun sensitiveLongPressKeepsConfirmationPath() {
        assertTrue(overlay.contains("showShortcutConfirmation0171"))
        assertTrue(service.contains("ShortcutGridPolicy0173.requiresConfirmation"))
        assertTrue(service.contains("executeShortcutLongPress0173"))
    }
}
''',
    encoding="utf-8",
)

print("CONTRATO_HOME_INLINE_0174_OK")
print("CONTRATO_LEGADO_HOME_0171_ATUALIZADO_PARA_INLINE_0174")
