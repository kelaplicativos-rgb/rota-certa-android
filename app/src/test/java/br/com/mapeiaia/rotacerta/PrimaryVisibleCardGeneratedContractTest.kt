package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrimaryVisibleCardGeneratedContractTest {
    private fun serviceSource(): String = listOf(
        File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"),
        File("app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"),
    ).firstOrNull(File::exists)?.readText()
        ?: error("LiveRideAccessibilityService.kt nao encontrado")

    @Test
    fun selectorRunsBeforeAddressTriggerAndPassengerGate() {
        val service = serviceSource()
        val processStart = service.indexOf("private suspend fun processRideText(")
        val processEnd = service.indexOf("private fun resolveRidePackageForText(", processStart)
        assertTrue(processStart >= 0 && processEnd > processStart)
        val process = service.substring(processStart, processEnd)

        val selector = process.indexOf("PrimaryVisibleRideCardSelector.select(fullSnapshotText)")
        val trigger = process.indexOf("UniversalAddressTrigger.evaluate(snapshotText)")
        val passenger = process.indexOf("RidePassengerIdentityPolicy.evaluate(snapshotText)")

        assertTrue("Marcador do escopo primario ausente", "primary_visible_card_scope_0_1_125" in process)
        assertTrue("Seletor deve executar antes do gatilho de enderecos", selector >= 0 && selector < trigger)
        assertTrue("Gatilho deve usar o texto ja isolado antes da identidade", trigger >= 0 && trigger < passenger)
        assertTrue("Avaliação pesada deve ficar fora da thread principal", "withContext(Dispatchers.Default)" in process)
        assertFalse("Trace contínuo da seleção não pode voltar", "traceEvent(\"universal.card.scope" in process)
    }
}
