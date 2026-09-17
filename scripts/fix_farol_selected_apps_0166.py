from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
service_path = root / "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
build_path = root / "app/build.gradle.kts"
policy_path = root / "app/src/main/java/br/com/mapeiaia/rotacerta/FarolSelectedAppInputPolicy0166.kt"
test_path = root / "app/src/test/java/br/com/mapeiaia/rotacerta/FarolSelectedAppInputPolicy0166Test.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: esperado 1 trecho, encontrado {count}")
    return text.replace(old, new, 1)


service = service_path.read_text(encoding="utf-8")
old_window = '''        val immediateTextChecklist13 = collectImmediateVisibleTextChecklist13()
        val stableWindowId151 = when {
            transientOverlayEvent151 -> lastStableFarolWindowIdChecklist14 ?: 0
            eventPackage == resolvedPackage || rootPackage == resolvedPackage -> event.windowId
            else -> lastStableFarolWindowIdChecklist14 ?: event.windowId
        }
        if (!transientOverlayEvent151 && (eventPackage == resolvedPackage || rootPackage == resolvedPackage)) {
            lastStableFarolPackageChecklist14 = resolvedPackage
            lastStableFarolWindowIdChecklist14 = event.windowId
        }
'''
new_window = '''        val immediateTextChecklist13 = collectImmediateVisibleTextChecklist13()
        val activeRootWindowId0166 = rootInActiveWindow?.windowId
        val stableWindowId151 = FarolSelectedAppInputPolicy0166.resolveStableWindowId(
            eventPackageName = eventPackage,
            rootPackageName = rootPackage,
            selectedPackageName = resolvedPackage,
            eventWindowId = event.windowId,
            rootWindowId = activeRootWindowId0166,
            lastStableWindowId = lastStableFarolWindowIdChecklist14,
        )
        val selectedRootWindowIsStable0166 =
            (rootPackage == resolvedPackage && activeRootWindowId0166 != null) || eventPackage == resolvedPackage
        if (!transientOverlayEvent151 && selectedRootWindowIsStable0166) {
            lastStableFarolPackageChecklist14 = resolvedPackage
            lastStableFarolWindowIdChecklist14 = stableWindowId151
        }
'''
service = replace_once(service, old_window, new_window, "janela estavel universal")

old_ocr = '''        val probableCard0161 = FailedCardRecoveryEngine0161.probableRideCard(
            text = accessibilitySnapshot0161,
            packageName = resolvedOcrPackage,
        )
        if (parserEvaluation0161.active || !probableCard0161) return

        val ocrRequestToken = UniversalFastReadPolicy.createOcrRequestToken(
'''
new_ocr = '''        val probableCard0161 = FailedCardRecoveryEngine0161.probableRideCard(
            text = accessibilitySnapshot0161,
            packageName = resolvedOcrPackage,
        )
        val selectedRootAllowsOcr0166 = FarolSelectedAppInputPolicy0166.shouldAttemptOcr(
            packageName = resolvedOcrPackage,
            selectedPackages = savedPackages0161,
            strictRootPackageName = strictSelectedRootPackageChecklist1(),
            parserAlreadyActive = parserEvaluation0161.active,
        )
        if (!selectedRootAllowsOcr0166) return
        val probableCardForCapture0166 = probableCard0161 || selectedRootAllowsOcr0166

        val ocrRequestToken = UniversalFastReadPolicy.createOcrRequestToken(
'''
service = replace_once(service, old_ocr, new_ocr, "OCR universal para pacote selecionado")
service = replace_once(
    service,
    "            probableCard = probableCard0161,\n",
    "            probableCard = probableCardForCapture0166,\n",
    "reserva universal de captura",
)
service_path.write_text(service, encoding="utf-8")

build = build_path.read_text(encoding="utf-8")
build = replace_once(build, 'versionCode = 5260', 'versionCode = 5270', "versionCode")
build = replace_once(build, 'versionName = "0.1.165"', 'versionName = "0.1.166"', "versionName")
build_path.write_text(build, encoding="utf-8")

policy = '''package br.com.mapeiaia.rotacerta

/**
 * Política pura do caminho de entrada do farol.
 *
 * Vale para qualquer pacote escolhido pelo usuário. O nome do aplicativo não participa
 * da autorização: apenas a seleção persistida, a raiz realmente visível e a sessão atual.
 */
object FarolSelectedAppInputPolicy0166 {
    fun resolveStableWindowId(
        eventPackageName: String?,
        rootPackageName: String?,
        selectedPackageName: String,
        eventWindowId: Int,
        rootWindowId: Int?,
        lastStableWindowId: Int?,
    ): Int = when {
        rootPackageName == selectedPackageName && rootWindowId != null -> rootWindowId
        eventPackageName == selectedPackageName -> eventWindowId
        lastStableWindowId != null -> lastStableWindowId
        rootWindowId != null -> rootWindowId
        else -> eventWindowId
    }

    fun shouldAttemptOcr(
        packageName: String,
        selectedPackages: Set<String>,
        strictRootPackageName: String?,
        parserAlreadyActive: Boolean,
    ): Boolean =
        packageName in selectedPackages &&
            strictRootPackageName == packageName &&
            !parserAlreadyActive
}
'''
policy_path.parent.mkdir(parents=True, exist_ok=True)
if policy_path.exists() and policy_path.read_text(encoding="utf-8") != policy:
    raise SystemExit(f"conteudo inesperado em {policy_path}")
policy_path.write_text(policy, encoding="utf-8")

test = '''package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolSelectedAppInputPolicy0166Test {
    @Test
    fun `evento de overlay nao troca a janela real do aplicativo selecionado`() {
        assertEquals(
            1759,
            FarolSelectedAppInputPolicy0166.resolveStableWindowId(
                eventPackageName = null,
                rootPackageName = "com.exemplo.motorista",
                selectedPackageName = "com.exemplo.motorista",
                eventWindowId = 1766,
                rootWindowId = 1759,
                lastStableWindowId = 1759,
            ),
        )
    }

    @Test
    fun `qualquer pacote selecionado pode usar OCR pontual`() {
        val customPackage = "com.parceiro.corridas.driver"
        assertTrue(
            FarolSelectedAppInputPolicy0166.shouldAttemptOcr(
                packageName = customPackage,
                selectedPackages = setOf(customPackage),
                strictRootPackageName = customPackage,
                parserAlreadyActive = false,
            ),
        )
    }

    @Test
    fun `pacote nao selecionado nunca autoriza OCR`() {
        assertFalse(
            FarolSelectedAppInputPolicy0166.shouldAttemptOcr(
                packageName = "com.nao.selecionado",
                selectedPackages = setOf("com.outro.selecionado"),
                strictRootPackageName = "com.nao.selecionado",
                parserAlreadyActive = false,
            ),
        )
    }

    @Test
    fun `OCR nao compete com parser que ja encontrou os enderecos`() {
        val selected = "com.exemplo.driver"
        assertFalse(
            FarolSelectedAppInputPolicy0166.shouldAttemptOcr(
                packageName = selected,
                selectedPackages = setOf(selected),
                strictRootPackageName = selected,
                parserAlreadyActive = true,
            ),
        )
    }
}
'''
test_path.parent.mkdir(parents=True, exist_ok=True)
if test_path.exists() and test_path.read_text(encoding="utf-8") != test:
    raise SystemExit(f"conteudo inesperado em {test_path}")
test_path.write_text(test, encoding="utf-8")
