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
    fun liveReaderNoLongerDependsOnSelectedAppsOrRegisteredCards() {
        val service = source("LiveRideAccessibilityService.kt")
        val scanStart = service.indexOf("private fun shouldScanPackage(")
        val scanEnd = service.indexOf("private fun selectedRidePackages", scanStart)
        val scanRegion = service.substring(scanStart, scanEnd)

        assertTrue("Leitura deve usar a portaria universal do Core", "CorePackageMonitor.classify(" in scanRegion)
        assertTrue("Decisao deve respeitar somente a classificacao real do pacote", "classification.canScan" in scanRegion)
        assertFalse("Lista selecionada nao pode controlar o farol", "SelectedRideAppStore.selectedPackages" in scanRegion)
        assertFalse("Pertencimento a lista nao pode controlar o farol", "normalized in selectedPackages" in scanRegion)
        assertTrue("Limpeza unica de apps e cards precisa existir", "pre_registered_runtime_cleanup_0_1_126" in service)
        assertTrue("Templates antigos precisam ser removidos", "repository.removeCardTemplate(template.id)" in service)
        assertTrue("Selecao antiga precisa ser zerada", "SelectedRideAppStore.save(applicationContext, emptySet())" in service)
        assertFalse("Motivo cego antigo nao pode continuar", "Aplicativo fora da selecao do usuario." in service)
    }

    @Test
    fun interfaceAndReportExplainUniversalPolicy() {
        val main = source("MainActivity.kt")

        assertTrue("Interface deve informar ausencia de apps pre-cadastrados", "Nao existem aplicativos pre-cadastrados" in main)
        assertTrue("Interface deve informar que cards pre-cadastrados foram apagados", "Cards pre-cadastrados foram apagados" in main)
        assertTrue("Relatorio deve registrar a politica nova", "Filtro por aplicativos pre-cadastrados: removido" in main)
        assertTrue("Relatorio deve registrar que modelos nao sao requisito", "Modelos de cards como requisito: removidos" in main)
        assertTrue("Relatorio deve explicar a regra real do card", "pacote externo comum + passageiro + pelo menos dois enderecos" in main)
        assertFalse("Seletor antigo de apps nao deve continuar visivel", "Buscar aplicativos instalados" in main)
        assertFalse("Chaves fixas de 99, Uber e inDrive nao devem continuar na interface", "SettingsSwitchRow(\"99 Motorista\"" in main)
    }
}
