package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalNoCardRegistrationContractTest {
    @Test
    fun cardRegistrationIsAbsentFromUiAndUniversalRuntime() {
        val main = File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").readText()
        val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()

        listOf(
            "Modelos de cards",
            "Anexar modelos de cards",
            "Cadastrar texto lido como modelo",
            "CardModelsCard(",
            "cardModelPicker",
            "onRegisterRideCard",
            "MonitoredAppsCard(",
        ).forEach { forbidden ->
            assertFalse("Recurso removido ainda aparece: $forbidden", forbidden in main)
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
            assertFalse("Cadastro de cards ainda interfere na leitura: $forbidden", forbidden in processBlock)
        }

        assertTrue("universal_no_card_registration_0_1_102" in main)
        assertTrue("universal_no_card_runtime_0_1_102" in service)
        assertTrue("Leitura universal de tela: true" in main)
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
