from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
main = (root / "app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").read_text(encoding="utf-8")
policy = (root / "app/src/main/java/br/com/mapeiaia/rotacerta/HomeModuleExpansionPolicy0174.kt").read_text(encoding="utf-8")
build = (root / "app/build.gradle.kts").read_text(encoding="utf-8")

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

print("CONTRATO_HOME_INLINE_0174_OK")
