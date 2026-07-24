package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstantFarolDecision124Test {
    private fun serviceSource(): String = listOf(
        File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"),
        File("app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"),
    ).firstOrNull(File::exists)?.readText() ?: error("LiveRideAccessibilityService.kt nao encontrado")

    @Test
    fun routeUsesSettingsAlreadyLoadedInMemory() {
        val service = serviceSource()
        val start = service.indexOf("private suspend fun analyzeUniversalTwoAddress(")
        val end = service.indexOf("private suspend fun applyUniversalTwoAddressResult(", start)
        val region = service.substring(start, end)

        assertTrue("Snapshot de configuracoes em memoria ausente", "val settingsChecklist13 = currentSettings" in region)
        assertFalse("A rota nao pode aguardar DataStore", "repository.settings.first()" in region)
    }

    @Test
    fun bubbleIsPaintedBeforeHistoryPersistence() {
        val service = serviceSource()
        val start = service.indexOf("private suspend fun applyUniversalTwoAddressResult(")
        val end = service.indexOf("private fun isUniversalResultFresh(", start)
        val region = service.substring(start, end)
        val paint = region.indexOf("showOverlay(colorChecklist13, distanceChecklist13)")
        val history = region.indexOf("repository.addAnalysis(result)")

        assertTrue("Marcador de medição final ausente", "measured_end_to_end_farol_checklist_13" in region)
        assertTrue("A bolinha precisa ser pintada antes do historico", paint >= 0 && history > paint)
        assertTrue("Historico precisa ser persistido sem bloquear a cor", "scope.launch(Dispatchers.IO)" in region)
    }

    @Test
    fun anySavedApplicationUsesTheSameImmediateClearContract() {
        val service = serviceSource()
        val start = service.indexOf("private suspend fun processRideText(")
        val end = service.indexOf("private suspend fun analyzeUniversalTwoAddress(", start)
        val region = service.substring(start, end)

        assertTrue("Aplicativo salvo precisa ser validado", "SelectedRideAppStore.read(applicationContext)" in region)
        assertTrue("Dois enderecos precisam ser o unico gatilho", "SimpleSavedAppFarolPolicy.evaluate" in region)
        assertTrue("Leitura incompleta precisa limpar imediatamente", "simple_two_address_clear_checklist_13" in region)
        assertTrue("Novo endereço precisa invalidar resultado anterior", "Novo endereco detectado; resultado anterior removido imediatamente." in region)
        assertFalse("Contrato nao pode citar pacote especifico", "sinet.startup.indriver" in region)
        assertFalse("Passageiro não pode voltar ao caminho crítico", "RidePassengerIdentityPolicy" in region)
        assertFalse("Modelo visual não pode voltar ao caminho crítico", "RideCardTemplateMatcher.match" in region)
    }

    @Test
    fun emptyAccessibilityReadsCannotKeepThePreviousDecision() {
        val service = serviceSource()
        val eventStart = service.indexOf("override fun onAccessibilityEvent(")
        val eventEnd = service.indexOf("override fun onInterrupt()", eventStart)
        val eventRegion = service.substring(eventStart, eventEnd)

        assertTrue("Mudança real da tela precisa limpar imediatamente", "immediate_screen_change_clear_checklist_13" in eventRegion)
        assertTrue("Texto vazio precisa remover resultado", "Tela alterada sem dois enderecos visiveis; resultado removido imediatamente." in eventRegion)
        assertFalse("Rota em andamento não pode preservar leitura vazia", "shouldIgnoreTransientInactiveRead" in eventRegion)
    }

    @Test
    fun yellowWaitingStateRemainsAvailableOnlyWhileCalculating() {
        val service = serviceSource()
        val start = service.indexOf("private suspend fun processRideText(")
        val end = service.indexOf("private suspend fun analyzeUniversalTwoAddress(", start)
        val region = service.substring(start, end)

        assertTrue("Amarelo precisa continuar indicando rota nova", "Dois enderecos identificados; calculando o ultimo destino." in region)
        assertTrue("Amarelo precisa ser pintado somente após falha do cache", "showOverlay(RadarColor.Default, distanceKm = null)" in region)
        assertTrue("Cache exato precisa vir antes do amarelo", region.indexOf("exact_cache_before_yellow_checklist_13") < region.indexOf("showOverlay(RadarColor.Default, distanceKm = null)"))
    }
}
