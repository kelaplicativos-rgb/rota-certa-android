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

        assertTrue("Snapshot de configuracoes em memoria ausente", "instant_farol_cached_settings_0_1_124" in region)
        assertFalse("A rota nao pode aguardar DataStore", "repository.settings.first()" in region)
    }

    @Test
    fun bubbleIsPaintedBeforeHistoryPersistence() {
        val service = serviceSource()
        val start = service.indexOf("private suspend fun applyUniversalTwoAddressResult(")
        val end = service.indexOf("private fun isUniversalResultFresh(", start)
        val region = service.substring(start, end)
        val paint = region.indexOf("showOverlay(color, distanceKm)")
        val history = region.indexOf("repository.addAnalysis(result)")

        assertTrue("Marcador de pintura imediata ausente", "instant_farol_paint_before_history_0_1_124" in region)
        assertTrue("A bolinha precisa ser pintada antes do historico", paint >= 0 && history > paint)
        assertTrue("Historico precisa ser persistido sem bloquear a cor", "scope.launch" in region)
    }

    @Test
    fun anySelectedApplicationUsesSameImmediateClearContract() {
        val service = serviceSource()
        val start = service.indexOf("private suspend fun processRideText(")
        val end = service.indexOf("private suspend fun analyzeUniversalTwoAddress(", start)
        val region = service.substring(start, end)

        assertTrue("Card precisa exigir passageiro unico", "global_single_passenger_gate_0_1_124" in region)
        assertTrue("Card precisa exigir passageiro e enderecos", "global_passenger_and_addresses_card_0_1_124" in region)
        assertTrue("Leitura incompleta precisa limpar imediatamente", "global_inactive_clear_now_0_1_124" in region)
        assertTrue("Qualquer mudanca da tela precisa invalidar o resultado", "global_full_screen_hash_0_1_124" in region)
        assertFalse("Contrato nao pode citar pacote especifico", "sinet.startup.indriver" in region)
        assertFalse("Contrato nao pode tolerar leitura vazia", "transient_empty_ignored_route_inflight=true" in service)
        assertFalse("Contrato nao pode proteger a cor anterior", "resetToIdle guarded active_ride_window" in service)
    }

    @Test
    fun emptyAccessibilityReadsCannotKeepThePreviousDecision() {
        val service = serviceSource()

        assertTrue("Limpeza imediata global precisa existir", "global_inactive_clear_now_0_1_124" in service)
        assertTrue("Reset para cinza nao pode ser bloqueado", "global_idle_never_guarded_0_1_124" in service)
        assertTrue("Overlay cinza precisa ser permitido", "global_overlay_idle_allowed_0_1_124" in service)
        assertFalse("Bloco executavel nao pode ignorar leitura vazia", "if (ignoreTransientOverlayEmpty)" in service)
        assertFalse("Rota em andamento nao pode proteger leitura vazia", "transient_empty_ignored_route_inflight=true" in service)
    }

    @Test
    fun yellowWaitingStateRemainsAvailable() {
        val service = serviceSource()
        assertTrue("Amarelo precisa continuar indicando analise", "showOverlay(RadarColor.Default, distanceKm = null)" in service)
    }
}
