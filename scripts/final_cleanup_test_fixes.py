from pathlib import Path

project = Path.cwd()
test_root = project / "app/src/test/java/br/com/mapeiaia/rotacerta"
source_root = project / "app/src/main/java/br/com/mapeiaia/rotacerta"

(test_root / "FarolCriticalPathChecklist6Test.kt").write_text(
    '''package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolCriticalPathChecklist6Test {
    @Test
    fun `ocr desiste quando acessibilidade aceitou depois do pedido`() {
        assertTrue(FarolCriticalPathPolicy.shouldSkipOcr(1_000L, 1_001L))
        assertTrue(FarolCriticalPathPolicy.shouldSkipOcr(1_000L, 1_000L))
        assertFalse(FarolCriticalPathPolicy.shouldSkipOcr(1_000L, 999L))
    }

    @Test
    fun `alvo visual permanece em oitocentos e cinquenta milissegundos`() {
        assertTrue(FarolCriticalPathPolicy.TARGET_RESULT_MILLIS < 1_000L)
        assertTrue(FarolCriticalPathPolicy.elapsedWithinTarget(1_000L, 1_850L))
        assertFalse(FarolCriticalPathPolicy.elapsedWithinTarget(1_000L, 1_851L))
        assertTrue(FarolCriticalPathPolicy.OCR_FALLBACK_DELAY_MILLIS <= 40L)
    }
}
''',
    encoding="utf-8",
)

core_test = test_root / "core/CorePackageMonitorTest.kt"
core_test.parent.mkdir(parents=True, exist_ok=True)
core_test.write_text(
    '''package br.com.mapeiaia.rotacerta.core

import br.com.mapeiaia.rotacerta.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorePackageMonitorTest {
    private val ownPackage = "br.com.mapeiaia.rotacerta"

    @Test
    fun onlyPackagePersistedByUserIsReleased() {
        val settings = AppSettings(
            appEnabled = true,
            liveReadingEnabled = true,
            extraMonitoredPackages = "com.exemplo.entregas",
        )
        val selected = CorePackageMonitor.classify("com.exemplo.entregas", ownPackage, settings)
        val other = CorePackageMonitor.classify("com.exemplo.outro", ownPackage, settings)
        assertTrue(selected.canScan)
        assertEquals(CorePackageKind.SelectedApp, selected.kind)
        assertFalse(other.canScan)
        assertEquals(CorePackageKind.NotSelected, other.kind)
    }

    @Test
    fun emptySelectionDoesNotReleaseAnyExternalPackage() {
        val settings = AppSettings(appEnabled = true, liveReadingEnabled = true, extraMonitoredPackages = "")
        assertFalse(CorePackageMonitor.classify("com.exemplo.qualquer", ownPackage, settings).canScan)
    }
}
''',
    encoding="utf-8",
)

parser = source_root / "RideTextParser.kt"
parser_text = parser.read_text(encoding="utf-8")
old_loop = "while (next < lines.size && parts.size < 3 && looksLikeContinuation(lines[next])) {"
new_loop = "while (next < lines.size && parts.size < 3 && !looksLikeAddress(lines[next]) && looksLikeContinuation(lines[next])) {"
if old_loop not in parser_text:
    raise SystemExit("Address continuation loop not found.")
parser.write_text(parser_text.replace(old_loop, new_loop, 1), encoding="utf-8")

parser_test = test_root / "GenericLastDestinationParserTest.kt"
parser_test.write_text(
    '''package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenericLastDestinationParserTest {
    @Test
    fun usesLastAddressAsDestinationForAnyManuallySelectedAppText() {
        val text = """
            Oferta de corrida
            R$ 21,90
            6,4 km
            Rua Primeira, 100 - Centro
            Avenida Segunda, 200 - Jardim Brasil
            Rua Final, 300 - Vila Nova
            Aceitar
        """.trimIndent()

        val fields = RideTextParser().parse(text, packageName = "com.regional.qualquer")

        assertEquals("Rua Primeira, 100 - Centro", fields.pickup)
        assertEquals("Rua Final, 300 - Vila Nova", fields.destination)
    }

    @Test
    fun oneAddressDoesNotActivateDestinationTrigger() {
        val text = """
            Chamada disponível
            R$ 12,50
            Rua Única, 777 - Centro
            Aceitar
        """.trimIndent()

        val fields = RideTextParser().parse(text, packageName = "com.app.local")

        assertEquals("Rua Única, 777 - Centro", fields.pickup)
        assertNull(fields.destination)
    }
}
''',
    encoding="utf-8",
)

service = source_root / "LiveRideAccessibilityService.kt"
service_text = service.read_text(encoding="utf-8")
if "bubble_drag_process_pause_0_1_116" not in service_text:
    anchor = "        if (bubbleGestureActive || !serviceReady || !currentSettings.appEnabled || !currentSettings.liveReadingEnabled) return"
    if anchor not in service_text:
        raise SystemExit("Bubble process pause guard not found.")
    service_text = service_text.replace(anchor, anchor + " // bubble_drag_process_pause_0_1_116", 1)
    service.write_text(service_text, encoding="utf-8")

print("Tests and generic two-address behavior aligned with the final manual-only contract.")
