from pathlib import Path
import runpy
import re

ROOT = Path(__file__).resolve().parents[1]
runpy.run_path(str(ROOT / "scripts/apply_user_fixes_0148.py"), run_name="__main__")

# Nova versão: aplicativos com capturas/cards aparecem primeiro, mesmo desmarcados.
gradle = ROOT / "app/build.gradle.kts"
text = gradle.read_text()
text = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 5100', text, count=1)
text = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.1.149"', text, count=1)
gradle.write_text(text)

picker = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/InstalledRideAppPickerActivity.kt"
text = picker.read_text()
old = '''    val filtered = remember(applications, search, selectedPackages) {
        val query = search.trim().lowercase(Locale.ROOT)
        val matching = if (query.isBlank()) {
            applications
        } else {
            applications.filter {
                it.label.lowercase(Locale.ROOT).contains(query) || it.packageName.lowercase(Locale.ROOT).contains(query)
            }
        }
        matching.sortedWith(
            compareByDescending<InstalledRideAppInfo> { it.packageName in selectedPackages }
                .thenBy { it.label.lowercase(Locale.ROOT) }
                .thenBy { it.packageName.lowercase(Locale.ROOT) },
        )
    } // authorized_apps_first_0_1_148
'''
new = '''    val filtered = remember(applications, search, selectedPackages, captures) {
        val query = search.trim().lowercase(Locale.ROOT)
        val capturedPackages = captures.asSequence().map { it.packageName }.toSet()
        val matching = if (query.isBlank()) {
            applications
        } else {
            applications.filter {
                it.label.lowercase(Locale.ROOT).contains(query) || it.packageName.lowercase(Locale.ROOT).contains(query)
            }
        }
        matching.sortedWith(
            compareByDescending<InstalledRideAppInfo> { it.packageName in capturedPackages }
                .thenByDescending { it.packageName in selectedPackages }
                .thenBy { it.label.lowercase(Locale.ROOT) }
                .thenBy { it.packageName.lowercase(Locale.ROOT) },
        )
    } // captured_apps_first_0_1_149
'''
if old not in text:
    raise SystemExit("Ordenação 0.1.148 não encontrada")
text = text.replace(old, new, 1)
picker.write_text(text)

contract = ROOT / "app/src/test/java/br/com/mapeiaia/rotacerta/CapturedAppsFirst149ContractTest.kt"
contract.write_text('''package br.com.mapeiaia.rotacerta

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class CapturedAppsFirst149ContractTest {
    @Test fun capturedAppsStayAboveUncapturedAppsEvenWhenUnchecked() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/InstalledRideAppPickerActivity.kt").readText()
        assertTrue("captured_apps_first_0_1_149" in source)
        assertTrue("capturedPackages" in source)
        assertTrue("compareByDescending<InstalledRideAppInfo> { it.packageName in capturedPackages }" in source)
        assertTrue("thenByDescending { it.packageName in selectedPackages }" in source)
        assertTrue("remember(applications, search, selectedPackages, captures)" in source)
    }
}
''')

print("0.1.149 aplicada: aplicativos capturados no topo, mesmo desmarcados")
