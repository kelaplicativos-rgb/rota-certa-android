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
    fun savedAppRunsDirectlyThroughTheTwoAddressTrigger() {
        val service = serviceSource()
        val processStart = service.indexOf("private suspend fun processRideText(")
        val processEnd = service.indexOf("//    private fun resolveRidePackageForText(", processStart)
        assertTrue(processStart >= 0 && processEnd > processStart)
        val process = service.substring(processStart, processEnd)

        val savedApps = process.indexOf("SelectedRideAppStore.read(applicationContext)")
        val trigger = process.indexOf("SimpleSavedAppFarolPolicy.evaluate")
        val destination = process.indexOf("destination = evaluationChecklist13.destination")

        assertTrue("Pacote salvo deve ser verificado antes do gatilho", savedApps >= 0 && savedApps < trigger)
        assertTrue("Último endereço precisa ser usado depois do gatilho", trigger >= 0 && trigger < destination)
        assertTrue("Avaliação de endereços deve ficar fora da thread principal", "withContext(Dispatchers.Default)" in process)
        assertFalse("Seletor visual não pode voltar ao caminho crítico", "PrimaryVisibleRideCardSelector" in process)
        assertFalse("Passageiro não pode voltar ao caminho crítico", "RidePassengerIdentityPolicy" in process)
        assertFalse("Trace contínuo não pode voltar", "traceEvent(\"universal.card.scope" in process)
    }
}
