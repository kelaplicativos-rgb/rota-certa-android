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
    fun noAppIsPreselectedAndOnlyManualPackagesCanBeRead() {
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
    fun registeredModelFromTheSamePackageIsRequiredBeforeRouteOrColor() {
        val service = source("LiveRideAccessibilityService.kt")
        val processStart = service.indexOf("private suspend fun processRideText(")
        val processEnd = service.indexOf("private fun resolveRidePackageForText(", processStart)
        assertTrue(processStart >= 0 && processEnd > processStart)
        val process = service.substring(processStart, processEnd)

        assertTrue("A configuracao precisa exigir card cadastrado", "requireRegisteredRideCard = true" in service)
        assertTrue("O match precisa usar apenas modelos do pacote selecionado", "templates = packageCardTemplates" in process)
        assertTrue("Sem modelo a rota deve ser bloqueada", "manual_card_required" in process)
        assertTrue("Sem correspondencia a rota deve ser bloqueada", "manual_card_waiting" in process)
        assertTrue("A rota so continua depois do match", "manual_registered_card_gate_0_1_127" in process)
        assertTrue("Resultado antigo deve depender do modelo ainda ativo", "manual_registered_card_freshness_0_1_127" in service)
    }

    @Test
    fun interfaceStartsEmptyAndExplainsBothManualSteps() {
        val main = source("MainActivity.kt")

        assertTrue("Seletor manual de apps precisa estar visivel", "Buscar aplicativos instalados" in main)
        assertTrue("Tela deve explicar que nenhum app nasce marcado", "Nenhum aplicativo vem marcado" in main)
        assertTrue("Cadastro de cards precisa estar visivel", "Anexar modelos de cards (prints)" in main)
        assertTrue("Tela deve explicar que nenhum modelo nasce cadastrado", "Nenhum modelo nasce cadastrado" in main)
        assertTrue("Tela deve declarar modelos obrigatorios", "Modelos de cards obrigatorios" in main)
        assertTrue("Relatorio deve declarar ausencia de pre-cadastros", "Aplicativos pre-cadastrados: nenhum" in main)
        assertTrue("Relatorio deve listar a selecao manual", "Selecao manual de apps obrigatoria: true" in main)
        assertTrue("Relatorio deve declarar card obrigatorio", "Modelos de cards obrigatorios: true" in main)
        assertFalse("Chaves fixas de 99, Uber e inDrive nao devem voltar", "SettingsSwitchRow(\"99 Motorista\"" in main)
    }

    @Test
    fun continuousScanIsFallbackRatherThanAggressivePolling() {
        val service = source("LiveRideAccessibilityService.kt")

        assertTrue("O ciclo de seguranca precisa usar 350 ms", "const val SCAN_LOOP_MS = 350L" in service)
        assertFalse("Polling de 120 ms nao pode voltar", "const val SCAN_LOOP_MS = 120L" in service)
    }
}
