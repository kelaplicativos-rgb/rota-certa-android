from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else '.')
main = (root / 'app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt').read_text()
service = (root / 'app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt').read_text()
policy = (root / 'app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutLongPressPolicy0171.kt').read_text()
build = (root / 'app/build.gradle.kts').read_text()

required = {
    'versionName': 'versionName = "0.1.173"' in build,
    'versionCode': 'versionCode = 5340' in build,
    'fixed policy': 'object ShortcutGridPolicy0173' in policy,
    'fixed handler': 'executeShortcutLongPress0173' in service,
    'fixed event': 'SHORTCUT_LONG_PRESS_FIXED_0173' in service,
    'legacy cleanup': 'clearLegacyPreferences' in policy and 'prefs.edit().clear()' in policy,
    'home fixed text': 'Ações fixas na grade' in main,
    'home no personalization': 'não podem ser personalizadas' in main,
    'cache confirmation': 'ShortcutGridPolicy0173.requiresConfirmation' in service,
}
for name, ok in required.items():
    if not ok:
        raise SystemExit(f'FALHA_CONTRATO_0173: {name}')

for forbidden in (
    'ShortcutLongPressPreferenceStore0171',
    'Salvar ação do toque longo',
    'Ação ao manter pressionado o atalho',
    'choice0171 =',
    'shortcutLongPressStore0171.read',
):
    if forbidden in main or forbidden in service or forbidden in policy:
        raise SystemExit(f'FALHA_PERSONALIZACAO_REMOVIDA_0173: {forbidden}')

print('CONTRATO_ATALHOS_DETERMINISTICOS_0173_OK')
