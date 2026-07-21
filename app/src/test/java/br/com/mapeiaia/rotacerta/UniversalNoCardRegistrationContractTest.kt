package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalNoCardRegistrationContractTest {
    @Test
    fun cardRegistrationIsRemovedAndCannotGateUniversalRuntime() {
        val main = File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").readText()
        val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()

        listOf(
            "Cards pre-cadastrados foram apagados",
            "Nenhum modelo pre-cadastrado controla a bolinha",
            "no_pre_registered_cards_ui_0_1_126",
            "no_registered_cards_module_0_1_126",
        ).forEach { required ->
            assertTrue("Contrato universal de cards ausente: $required", required in main)
        }

        listOf(
            "Anexar modelos de cards (prints)",
            "Modelos cadastrados: ${'$'}{cardTemplates.size}",
        ).forEach { removed ->
            assertFalse("Cadastro antigo de cards ainda aparece na interface: $removed", removed in main)
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
        assertTrue("pre_registered_runtime_cleanup_0_1_126" in service)
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
