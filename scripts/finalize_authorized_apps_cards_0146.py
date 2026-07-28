from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PKG = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta'
MAIN = PKG / 'MainActivity.kt'
SERVICE = PKG / 'LiveRideAccessibilityService.kt'
PICKER = PKG / 'InstalledRideAppPickerActivity.kt'
OVERLAY = PKG / 'BubbleShortcutOverlayController.kt'
GRADLE = ROOT / 'app/build.gradle.kts'

main = MAIN.read_text(encoding='utf-8')
main = main.replace(
    'O endereço é validado ao salvar. Assim, o farol não precisa localizar a Casa quando a corrida aparece.',
    'O endereço é salvo e validado automaticamente ao concluir no teclado ou usar o GPS. Assim, o farol não precisa localizar a Casa quando a corrida aparece.',
)
button = '''            Button(
                onClick = {
                    focusManager.clearFocus()
                    onSave()
                },
                enabled = quickSettings.homeAddress.isNotBlank() && !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (saving) "Localizando..." else "Salvar Casa")
            }
'''
main = main.replace(button, '', 1)
if 'Text(if (saving) "Localizando..." else "Salvar Casa")' in main:
    raise SystemExit('Salvar Casa button still present')
MAIN.write_text(main, encoding='utf-8')

service = SERVICE.read_text(encoding='utf-8')
required_service = [
    'BubbleShortcutAction.OpenAuthorizedAppsAndCards -> openAuthorizedAppsAndCards146()',
    'BubbleShortcutQuickAction.CaptureCurrentAppAndScreen -> captureCurrentAppAndScreen138()',
    'SelectedRideAppStore.save(',
    'activePackageChecklist13 in SelectedRideAppStore.read(applicationContext)',
]
for marker in required_service:
    if marker not in service:
        raise SystemExit(f'missing service contract: {marker}')
if '// cards_supplemental_not_contract_0_1_146' not in service:
    service = service.replace(
        '    private suspend fun analyzeUniversalTwoAddress(',
        '    // cards_supplemental_not_contract_0_1_146\n    private suspend fun analyzeUniversalTwoAddress(',
        1,
    )
SERVICE.write_text(service, encoding='utf-8')

picker = PICKER.read_text(encoding='utf-8')
required_picker = [
    'Text("Aplicativos e cards autorizados"',
    'Cards são apenas complementos para OCR e reconhecimento.',
    'Text("Excluir somente este card")',
    'Text("Excluir aplicativo e cards")',
    'ManualAppScreenCaptureStore.removePackage(applicationContext, packageName)',
]
for marker in required_picker:
    if marker not in picker:
        raise SystemExit(f'missing picker contract: {marker}')

# No package is born authorized: the explicit store must be initialized empty.
if 'SelectedRideAppStore.save(applicationContext, emptySet())' not in service:
    raise SystemExit('explicit empty authorization initialization missing')

# Gesture contract: short tap opens management; 1.5 s hold executes capture.
overlay = OVERLAY.read_text(encoding='utf-8')
if 'postDelayed(longPressAction, 1_500L)' not in overlay:
    raise SystemExit('1.5 second long press missing')
if 'onDoubleTap(event' in overlay:
    raise SystemExit('legacy double tap remains')

gradle = GRADLE.read_text(encoding='utf-8')
if 'versionName = "0.1.145"' in gradle:
    gradle = gradle.replace('versionName = "0.1.145"', 'versionName = "0.1.146"', 1)
if 'versionCode = 5060' in gradle:
    gradle = gradle.replace('versionCode = 5060', 'versionCode = 5070', 1)
if 'versionName = "0.1.146"' not in gradle or 'versionCode = 5070' not in gradle:
    raise SystemExit('version 0.1.146 / 5070 not materialized')
GRADLE.write_text(gradle, encoding='utf-8')

print('Finalized Rota Certa 0.1.146 authorized apps/cards correction')
