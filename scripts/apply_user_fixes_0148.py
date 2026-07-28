from pathlib import Path
import runpy
import re

ROOT = Path(__file__).resolve().parents[1]
runpy.run_path(str(ROOT / "scripts/apply_user_suggestions_0147_v2.py"), run_name="__main__")

# Nova versão para as correções observadas no aparelho.
gradle = ROOT / "app/build.gradle.kts"
text = gradle.read_text()
text = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 5090', text, count=1)
text = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.1.148"', text, count=1)
gradle.write_text(text)

# Aplicativos autorizados sempre aparecem no topo, seguidos pelos demais em ordem alfabética.
picker = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/InstalledRideAppPickerActivity.kt"
text = picker.read_text()
old = '''    val filtered = remember(applications, search) {
        val query = search.trim().lowercase(Locale.ROOT)
        if (query.isBlank()) applications else applications.filter { it.label.lowercase(Locale.ROOT).contains(query) || it.packageName.contains(query) }
    }
'''
new = '''    val filtered = remember(applications, search, selectedPackages) {
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
if old not in text:
    raise SystemExit("Bloco de filtragem dos aplicativos não encontrado")
text = text.replace(old, new, 1)
picker.write_text(text)

# Reaplica imediatamente as dimensões no WindowManager quando o usuário move o controle de tamanho.
service = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
text = service.read_text()
old_collect = '        scope.launch { repository.settings.collect { currentSettings = it } }\n'
new_collect = '''        scope.launch {
            repository.settings.collect { updatedSettings ->
                val bubbleSizeChanged = currentSettings.bubbleSizeDp != updatedSettings.bubbleSizeDp
                currentSettings = updatedSettings
                if (bubbleSizeChanged) applyBubbleSizeImmediately148(updatedSettings.bubbleSizeDp)
            }
        } // bubble_size_live_render_0_1_148
'''
if old_collect not in text:
    raise SystemExit("Coletor de configurações não encontrado")
text = text.replace(old_collect, new_collect, 1)

anchor = '    override fun onServiceConnected() {\n'
method = '''    private fun applyBubbleSizeImmediately148(requestedSizeDp: Int) {
        val view = overlayView ?: return
        val params = overlayParams ?: return
        val manager = windowManager ?: return
        val sizePx = dp(requestedSizeDp.coerceIn(52, 96))
        if (params.width == sizePx && params.height == sizePx) return
        params.width = sizePx
        params.height = sizePx
        val maxX = (resources.displayMetrics.widthPixels - sizePx).coerceAtLeast(0)
        val maxY = (resources.displayMetrics.heightPixels - sizePx).coerceAtLeast(0)
        params.x = params.x.coerceIn(0, maxX)
        params.y = params.y.coerceIn(0, maxY)
        runCatching {
            manager.updateViewLayout(view, params)
            view.requestLayout()
            view.invalidate()
        }
    } // bubble_size_live_render_0_1_148

'''
if 'private fun applyBubbleSizeImmediately148' not in text:
    if anchor not in text:
        raise SystemExit("Ponto de inserção do renderizador da bolinha não encontrado")
    text = text.replace(anchor, method + anchor, 1)
service.write_text(text)

contract = ROOT / "app/src/test/java/br/com/mapeiaia/rotacerta/UserFixes148ContractTest.kt"
contract.write_text('''package br.com.mapeiaia.rotacerta

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class UserFixes148ContractTest {
    @Test fun authorizedAppsStayOnTopAndBubbleRerenders() {
        val root = File("src/main/java/br/com/mapeiaia/rotacerta")
        val picker = File(root, "InstalledRideAppPickerActivity.kt").readText()
        assertTrue("authorized_apps_first_0_1_148" in picker)
        assertTrue("compareByDescending<InstalledRideAppInfo>" in picker)
        assertTrue("selectedPackages" in picker)
        val service = File(root, "LiveRideAccessibilityService.kt").readText()
        assertTrue("bubble_size_live_render_0_1_148" in service)
        assertTrue("manager.updateViewLayout(view, params)" in service)
        assertTrue("params.width = sizePx" in service)
        assertTrue("params.height = sizePx" in service)
    }
}
''')

print("0.1.148 aplicada: autorizados no topo e bolinha redimensionada ao vivo")
