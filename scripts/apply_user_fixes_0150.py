from pathlib import Path
import runpy
import re

ROOT = Path(__file__).resolve().parents[1]
runpy.run_path(str(ROOT / "scripts/apply_user_fixes_0149.py"), run_name="__main__")

# Nova versão: exibe pacotes autorizados/capturados mesmo sem atividade launcher.
gradle = ROOT / "app/build.gradle.kts"
text = gradle.read_text()
text = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 5110', text, count=1)
text = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.1.150"', text, count=1)
gradle.write_text(text)

picker = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/InstalledRideAppPickerActivity.kt"
text = picker.read_text()
old = '''    LaunchedEffect(Unit) {
        applications = withContext(Dispatchers.Default) { loadLaunchableApplications(context.packageManager, context.packageName) }
        loading = false
    }
'''
new = '''    LaunchedEffect(Unit) {
        applications = withContext(Dispatchers.Default) {
            val launchable = loadLaunchableApplications(context.packageManager, context.packageName)
            val visiblePackages = launchable.asSequence().map { it.packageName }.toSet()
            val relatedPackages = (selectedPackages + captures.map { it.packageName })
                .mapNotNull(SelectedRideAppStore::normalize)
                .toSortedSet()
            val hiddenOrRemoved = relatedPackages
                .filterNot { it in visiblePackages }
                .map { packageName -> resolveStoredApplication(context.packageManager, packageName) }
            (hiddenOrRemoved + launchable).distinctBy { it.packageName }
        }
        loading = false
    } // expose_hidden_authorized_packages_0_1_150
'''
if old not in text:
    raise SystemExit("Carregamento de aplicativos não encontrado")
text = text.replace(old, new, 1)

anchor = '''private fun loadLaunchableApplications(packageManager: PackageManager, ownPackageName: String): List<InstalledRideAppInfo> {
'''
helper = '''private fun resolveStoredApplication(packageManager: PackageManager, packageName: String): InstalledRideAppInfo {
    val label = runCatching {
        val applicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION") packageManager.getApplicationInfo(packageName, 0)
        }
        packageManager.getApplicationLabel(applicationInfo).toString().ifBlank { packageName }
    }.getOrElse { "Pacote salvo ou aplicativo removido" }
    return InstalledRideAppInfo(label = label, packageName = packageName)
} // expose_hidden_authorized_packages_0_1_150

'''
if anchor not in text:
    raise SystemExit("Função de aplicativos iniciáveis não encontrada")
text = text.replace(anchor, helper + anchor, 1)
picker.write_text(text)

contract = ROOT / "app/src/test/java/br/com/mapeiaia/rotacerta/HiddenAuthorizedPackage150ContractTest.kt"
contract.write_text('''package br.com.mapeiaia.rotacerta

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class HiddenAuthorizedPackage150ContractTest {
    @Test fun storedPackagesWithoutLauncherRemainVisibleAndRemovable() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/InstalledRideAppPickerActivity.kt").readText()
        assertTrue("expose_hidden_authorized_packages_0_1_150" in source)
        assertTrue("selectedPackages + captures.map { it.packageName }" in source)
        assertTrue("resolveStoredApplication" in source)
        assertTrue("Pacote salvo ou aplicativo removido" in source)
        assertTrue("hiddenOrRemoved + launchable" in source)
    }
}
''')

print("0.1.150 aplicada: pacotes autorizados invisíveis são exibidos e removíveis")
