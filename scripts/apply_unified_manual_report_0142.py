from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt'
MAIN = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt'
GRADLE = ROOT / 'app/build.gradle.kts'

# Materializa primeiro a correção de estabilidade 0.1.141 quando a branch ainda está em 0.1.140.
gradle_before = GRADLE.read_text(encoding='utf-8')
if 'versionName = "0.1.140"' in gradle_before:
    subprocess.run(['python', str(ROOT / 'scripts/apply_bubble_stability_0141.py')], check=True)

service = SERVICE.read_text(encoding='utf-8')
old_export = '''    private fun exportDiagnosticFromBubble() {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        Unit /* diagnostics_off_checklist_4 */
        runCatching {
            startActivity(
                Intent(this@LiveRideAccessibilityService, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_OPEN_TAB, TAB_HISTORY)
                    .putExtra(EXTRA_OPEN_BUBBLE_GROUP, "reports")
                    .putExtra("auto_export_report", true),
            )
        }.onFailure { toast("Nao foi possivel abrir a exportacao do relatorio.") }
    }
'''
new_export = '''    private fun exportDiagnosticFromBubble() {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        UnifiedDebugEventStore.record(
            "BUBBLE_REPORT_SHORTCUT_OPENED",
            universalResolvedForegroundPackage(),
            "grade abriu a area de relatorios; exportacao automatica desativada",
        )
        runCatching {
            startActivity(
                Intent(this@LiveRideAccessibilityService, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_OPEN_TAB, TAB_HISTORY)
                    .putExtra(EXTRA_OPEN_BUBBLE_GROUP, "reports"),
            )
        }.onFailure { toast("Nao foi possivel abrir a area de relatorios.") }
    } // unified_manual_report_from_grid_0_1_142
'''
if old_export not in service:
    raise SystemExit('exportDiagnosticFromBubble block not found')
service = service.replace(old_export, new_export, 1)
SERVICE.write_text(service, encoding='utf-8')

main = MAIN.read_text(encoding='utf-8')
old_auto = '''        if (launchIntent?.getBooleanExtra("auto_export_report", false) == true) {
            Unit /* production_log_removed_checklist_4 */
            supportReportFileCreator.launch("rota-certa-relatorio-depuracao.txt")
        } // auto_export_report_0_1_119
'''
new_auto = '''        if (launchIntent?.getBooleanExtra("auto_export_report", false) == true) {
            UnifiedDebugEventStore.record(
                "LEGACY_AUTO_REPORT_IGNORED",
                context.packageName,
                "exportacao automatica antiga ignorada; use o botao Gerar relatorio para depuracao",
            )
            launchIntent.removeExtra("auto_export_report")
        } // automatic_report_disabled_unified_manual_export_0_1_142
'''
if old_auto not in main:
    raise SystemExit('legacy auto export block not found')
main = main.replace(old_auto, new_auto, 1)
MAIN.write_text(main, encoding='utf-8')

gradle = GRADLE.read_text(encoding='utf-8')
if 'versionName = "0.1.141"' not in gradle:
    raise SystemExit('expected 0.1.141 version not found')
gradle = gradle.replace('versionName = "0.1.141"', 'versionName = "0.1.142"', 1)
if 'versionCode = 5020' in gradle:
    gradle = gradle.replace('versionCode = 5020', 'versionCode = 5030', 1)
else:
    gradle = gradle.replace('versionCode = appVersionCode', 'versionCode = 5030', 1)
GRADLE.write_text(gradle, encoding='utf-8')

print('Applied unified manual report flow 0.1.142')
