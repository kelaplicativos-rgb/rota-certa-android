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
    fun noAppIsPreselectedButManualSelectionControlsReading() {
        val service = source("LiveRideAccessibilityService.kt")
        val scanStart = service.indexOf("private fun shouldScanPackage(")
        val scanEnd = service.indexOf("private fun selectedRidePackages", scanStart)
        val scanRegion = service.substring(scanStart, scanEnd)

        assertTrue("Pacotes passivos continuam classificados pelo Core", "CorePackageMonitor.classify(" in scanRegion)
        assertTrue("Selecao deve vir somente do armazenamento manual", "SelectedRideAppStore.read(applicationContext)" in scanRegion)
        assertTrue("Somente pacote escolhido pode ser lido", "normalized in selectedPackages" in scanRegion)
        assertTrue("Instalacao sem escolha precisa criar selecao vazia", "SelectedRideAppStore.save(applicationContext, emptySet())" in service)
        assertTrue("Cards manuais devem ser preservados", "currentCardTemplates = repository.cardTemplates.first()" in service)
        assertFalse("A versao nova nao pode apagar modelos do usuario", "removedTemplates126.forEach" in service)
        assertFalse("A versao nova nao pode remover modelo durante a inicializacao", "repository.removeCardTemplate(template.id)" in service)
    }

    @Test
    fun interfaceRestoresManualPickerAndOptionalCardModels() {
        val main = source("MainActivity.kt")

        assertTrue("Seletor manual de apps precisa estar visivel", "Buscar aplicativos instalados" in main)
        assertTrue("Tela deve explicar que nenhum app nasce marcado", "Nenhum aplicativo vem marcado" in main)
        assertTrue("Cadastro opcional de cards precisa estar visivel", "Anexar modelos de cards (prints)" in main)
        assertTrue("Tela deve explicar que nenhum modelo nasce cadastrado", "Nenhum modelo nasce cadastrado" in main)
        assertTrue("Relatorio deve declarar ausencia de pre-cadastros", "Aplicativos pre-cadastrados: nenhum" in main)
        assertTrue("Relatorio deve listar a selecao manual", "Selecao manual de apps obrigatoria: true" in main)
        assertFalse("Chaves fixas de 99, Uber e inDrive nao devem voltar", "SettingsSwitchRow(\"99 Motorista\"" in main)
    }
}
