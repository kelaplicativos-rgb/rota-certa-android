package br.com.mapeiaia.rotacerta

import java.io.File
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
        val selector = service.indexOf("PrimaryVisibleRideCardSelector.select(fullSnapshotText)", processStart)
        val trigger = service.indexOf("UniversalAddressTrigger.evaluate(snapshotText)", processStart)
        val passenger = service.indexOf("RidePassengerIdentityPolicy.evaluate(snapshotText)", processStart)

        assertTrue("Marcador do escopo primario ausente", "primary_visible_card_scope_0_1_125" in service)
        assertTrue("Seletor deve executar antes do gatilho de enderecos", selector >= processStart && selector < trigger)
        assertTrue("Gatilho deve usar o texto ja isolado antes da identidade", trigger < passenger)
        assertTrue("Trace da selecao precisa existir", "universal.card.scope selected_index=" in service)
    }
}
