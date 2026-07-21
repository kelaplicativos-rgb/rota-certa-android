package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalNoCardRegistrationContractTest {
    @Test
    fun cardRegistrationExistsInUiButDoesNotGateUniversalRuntime() {
        val main = File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").readText()
        val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()

        listOf(
            "Cards cadastrados",
            "Anexar modelos de cards (prints)",
            "RegisteredCardsModuleCard(",
            "cardModelPicker",
            "onRegisterRideCard",
            "popup_navigation_card_state_0_1_120",
        ).forEach { required ->
            assertTrue("Recurso de Cards ausente: $required", required in main)
        }

        val processStart = service.indexOf("    private suspend fun processRideText(")
        val processEnd = service.indexOf("    private fun resolveRidePackageForText(", processStart)
        assertTrue(processStart >= 0 && processEnd > processStart)
        val processBlock = service.substring(processStart, processEnd)

        listOf(
            "RideCardTemplateMatcher",
            "RegisteredCardAddressGate",
            "selectedRidePackages",
            "currentCardTemplates",
        ).forEach { forbidden ->
            assertFalse("Cadastro de cards nao pode bloquear a leitura universal: $forbidden", forbidden in processBlock)
        }

        assertTrue("universal_no_card_runtime_0_1_102" in service)
        assertTrue("currentCardTemplates = emptyList()" in service)
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
