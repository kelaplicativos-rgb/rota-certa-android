package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoPreRegisteredGates126ContractTest {
    private fun source(path: String): String = listOf(
        File("src/main/java/br/com/mapeiaia/rotacerta/$path"),
        File("app/src/main/java/br/com/mapeiaia/rotacerta/$path"),
    ).firstOrNull(File::exists)?.readText() ?: error("$path nao encontrado")

    @Test
    fun installationStartsEmptyAndReadsOnlyChosenApps() {
        val service = source("LiveRideAccessibilityService.kt")
        val start = service.indexOf("private fun shouldScanPackage(")
        val end = service.indexOf("private fun selectedRidePackages", start)
        val gate = service.substring(start, end)

        assertTrue("SelectedRideAppStore.read(applicationContext)" in gate)
        assertTrue("normalized in selectedPackages" in gate)
        assertTrue("SelectedRideAppStore.save(applicationContext, emptySet())" in service)
        assertFalse("removedTemplates126.forEach" in service)
    }

    @Test
    fun cardModelsAreVisibleAndOptional() {
        val service = source("LiveRideAccessibilityService.kt")
        val main = source("MainActivity.kt")

        assertTrue("manual_cards_preserved_0_1_127" in service)
        assertFalse("manual_registered_card_gate_0_1_127" in service)
        assertFalse("reason=no_template" in service)
        assertTrue("Buscar aplicativos instalados" in main)
        assertTrue("Nenhum aplicativo vem marcado" in main)
        assertTrue("Modelos de cards opcionais" in main)
        assertTrue("Nenhum modelo nasce cadastrado" in main)
        assertTrue("Anexar modelos de cards (prints)" in main)
        assertFalse("Modelos de cards obrigatorios" in main)
    }

    @Test
    fun routeFallbackRemainsFastAndExactRouteContinues() {
        val service = source("LiveRideAccessibilityService.kt")

        assertTrue("const val SCAN_LOOP_MS = 120L" in service)
        assertFalse("const val SCAN_LOOP_MS = 350L" in service)
        assertTrue("fast_red_continues_exact_route_0_1_127" in service)
        assertTrue("stable_card_signature_route_0_1_127" in service)
    }
}
