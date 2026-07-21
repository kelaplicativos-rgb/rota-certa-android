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
    fun inDriveExpandedSnapshotsDoNotRestartCurrentRoute() {
        val service = serviceSource()

        assertTrue("Estabilizador nao foi conectado", "RideCardSnapshotStabilizer()" in service)
        assertTrue("Portaria de snapshots intermediarios ausente", "instant_farol_snapshot_stability_0_1_124" in service)
        assertTrue("Reset do estabilizador ausente", "instant_farol_snapshot_reset_0_1_124" in service)
    }
}
