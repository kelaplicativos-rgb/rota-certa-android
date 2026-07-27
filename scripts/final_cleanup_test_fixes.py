from pathlib import Path

root = Path.cwd() / "app/src/test/java/br/com/mapeiaia/rotacerta"

(root / "FarolCriticalPathChecklist6Test.kt").write_text(
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

core_test = root / "core/CorePackageMonitorTest.kt"
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

print("Tests aligned with the final manual-only contract.")
