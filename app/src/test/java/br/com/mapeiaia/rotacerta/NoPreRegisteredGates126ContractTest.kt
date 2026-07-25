package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoPreRegisteredGates126ContractTest {
    private fun source(path: String): String = listOf(
        File("src/main/java/br/com/mapeiaia/rotacerta/$path"),
        File("app/src/main/java/br/com/mapeiaia/rotacerta/$path"),
    ).firstOrNull(File::exists)?.readText()
        ?: error("$path nao encontrado")

    @Test
    fun noAppIsPreselectedAndOnlySavedPackagesCanBeRead() {
        val service = source("LiveRideAccessibilityService.kt")
        val scanStart = service.indexOf("private fun shouldScanPackage(")
        val scanEnd = service.indexOf("private fun selectedRidePackages", scanStart)
        val scanRegion = service.substring(scanStart, scanEnd)

        assertTrue("Pacotes passivos continuam classificados pelo Core", "CorePackageMonitor.classify(" in scanRegion)
        assertTrue("Selecao deve vir somente do armazenamento manual", "SelectedRideAppStore.read(applicationContext)" in scanRegion)
        assertTrue("Somente pacote escolhido pode ser lido", "StrictSelectedAppReadPolicy.canRead(" in scanRegion)
        assertTrue("Instalacao sem escolha precisa criar selecao vazia", "SelectedRideAppStore.save(applicationContext, emptySet())" in service)
        assertTrue("Cards manuais devem ser preservados", "currentCardTemplates = repository.cardTemplates.first()" in service)
        assertFalse("A versao nova nao pode apagar modelos do usuario", "removedTemplates126.forEach" in service)
        assertFalse("A versao nova nao pode remover modelo durante a inicializacao", "repository.removeCardTemplate(template.id)" in service)
    }

    @Test
    fun savedPackageAndTwoAddressesAreRequiredButVisualModelIsNot() {
        val service = source("LiveRideAccessibilityService.kt")
        val processStart = service.indexOf("private suspend fun processRideText(")
        val processEnd = service.indexOf("//    private fun resolveRidePackageForText(", processStart)
        assertTrue(processStart >= 0 && processEnd > processStart)
        val process = service.substring(processStart, processEnd)

        assertFalse("Configuracao não pode exigir modelo", "requireRegisteredRideCard = true" in service)
        assertTrue("Pacote salvo precisa ser validado", "SelectedRideAppStore.read(applicationContext)" in process)
        assertTrue("Dois endereços precisam ser avaliados", "SimpleSavedAppFarolPolicy.evaluate" in process)
        assertTrue("O último endereço precisa alimentar o destino", "destination = evaluationChecklist13.destination" in process)
        assertFalse("Modelo visual não pode bloquear rota", "RideCardTemplateMatcher.match" in process)
        assertFalse("Passageiro não pode bloquear rota", "RidePassengerIdentityPolicy" in process)
        assertFalse("Portaria manual antiga não pode voltar", "manual_registered_card_gate_0_1_127" in process)
    }

    @Test
    fun interfaceStartsEmptyAndExplainsSavedAppsAndOptionalModels() {
        val main = source("MainActivity.kt")

        assertTrue("Seletor manual de apps precisa estar visivel", "Buscar aplicativos instalados" in main)
        assertTrue("Tela deve explicar que nenhum app nasce marcado", "Nenhum aplicativo vem marcado" in main)
        assertTrue("Cadastro de cards precisa continuar visivel", "Anexar modelos de cards (prints)" in main)
        assertTrue("Tela deve explicar que nenhum modelo nasce cadastrado", "Nenhum modelo nasce cadastrado" in main)
        assertTrue("Tela deve declarar modelo opcional", "Modelos de cards (apoio opcional)" in main)
        assertFalse("Tela não pode declarar modelo obrigatório", "Modelos de cards obrigatorios" in main)
        assertFalse("Chaves fixas de 99, Uber e inDrive nao devem voltar", "SettingsSwitchRow(\"99 Motorista\"" in main)
    }

    @Test
    fun continuousScanIsFallbackRatherThanAggressivePolling() {
        val service = source("LiveRideAccessibilityService.kt")

        assertTrue("O ciclo de seguranca precisa usar 350 ms", "const val SCAN_LOOP_MS = 350L" in service)
        assertFalse("Polling de 120 ms nao pode voltar", "const val SCAN_LOOP_MS = 120L" in service)
    }
}
