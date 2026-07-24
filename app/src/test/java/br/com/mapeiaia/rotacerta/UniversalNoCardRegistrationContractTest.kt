package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalNoCardRegistrationContractTest {
    @Test
    fun cardRegistrationExistsAsOptionalSupportAndNeverBlocksRuntime() {
        val main = File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").readText()
        val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()

        listOf(
            "Modelos de cards (apoio opcional)",
            "Anexar modelos de cards (prints)",
            "Modelos cadastrados:",
            "Nenhum modelo nasce cadastrado",
        ).forEach { required ->
            assertTrue("Cadastro opcional de cards ausente: $required", required in main)
        }
        assertFalse("Interface não pode declarar modelo obrigatório", "Modelos de cards obrigatorios" in main)

        val processStart = service.indexOf("    private suspend fun processRideText(")
        val processEnd = service.indexOf("    //    private fun resolveRidePackageForText(", processStart)
        assertTrue(processStart >= 0 && processEnd > processStart)
        val processBlock = service.substring(processStart, processEnd)

        assertTrue("Aplicativo salvo precisa ser validado", "SelectedRideAppStore.read(applicationContext)" in processBlock)
        assertTrue("Dois endereços precisam acionar a leitura", "SimpleSavedAppFarolPolicy.evaluate" in processBlock)
        assertTrue("O último endereço precisa ser o destino", "destination = evaluationChecklist13.destination" in processBlock)
        assertFalse("Portaria de modelo não pode voltar", "manual_registered_card_gate_0_1_127" in processBlock)
        assertFalse("Match de modelo não pode bloquear rota", "RideCardTemplateMatcher.match" in processBlock)
        assertTrue("Modelos manuais precisam continuar carregados como apoio", "currentCardTemplates = repository.cardTemplates.first()" in service)
        assertFalse("Inicializacao nao pode apagar modelos", "removedTemplates126.forEach" in service)
        assertFalse("Configuracao padrão não pode exigir modelo", AppSettings().requireRegisteredRideCard)
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
