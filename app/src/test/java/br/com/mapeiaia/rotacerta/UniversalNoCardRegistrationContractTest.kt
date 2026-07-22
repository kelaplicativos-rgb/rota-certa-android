package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalNoCardRegistrationContractTest {
    @Test
    fun cardRegistrationExistsButRemainsOptionalForRuntime() {
        val main = File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").readText()
        val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()

        listOf(
            "Modelos de cards opcionais",
            "Anexar modelos de cards (prints)",
            "Modelos cadastrados:",
            "Nenhum modelo nasce cadastrado",
        ).forEach { required ->
            assertTrue("Cadastro opcional de cards ausente: $required", required in main)
        }

        val processStart = service.indexOf("    private suspend fun processRideText(")
        val processEnd = service.indexOf("    private fun resolveRidePackageForText(", processStart)
        assertTrue(processStart >= 0 && processEnd > processStart)
        val processBlock = service.substring(processStart, processEnd)

        listOf(
            "RideCardTemplateMatcher",
            "RegisteredCardAddressGate",
            "currentCardTemplates",
            "manual_registered_card_gate_0_1_127",
        ).forEach { forbidden ->
            assertFalse("Modelo opcional nao pode bloquear a leitura do card real: $forbidden", forbidden in processBlock)
        }

        assertTrue("Modelos manuais precisam ser carregados", "currentCardTemplates = repository.cardTemplates.first()" in service)
        assertTrue("Alteracoes manuais precisam ser observadas", "repository.cardTemplates.collect { currentCardTemplates = it }" in service)
        assertFalse("Inicializacao nao pode apagar modelos", "removedTemplates126.forEach" in service)
        assertFalse("Configuracao padrao nao pode exigir modelo", AppSettings().requireRegisteredRideCard)
    }

    @Test
    fun twoNumberedAddressesStillUseTheLastAndOneStaysInactive() {
        val two = UniversalAddressTrigger.evaluate(
            "Rua Primeiro Destino, 10 - Centro\nAvenida Destino Final, 250 - Bairro Azul",
        )
        assertTrue(two.active)
        assertTrue(two.destination?.startsWith("Avenida Destino Final, 250") == true)

        val one = UniversalAddressTrigger.evaluate("Rua Unica, 99 - Centro")
        assertFalse(one.active)
    }
}
