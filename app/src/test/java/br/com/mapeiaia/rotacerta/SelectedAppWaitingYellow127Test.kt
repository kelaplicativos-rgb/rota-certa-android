package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectedAppWaitingYellow127Test {
    private fun serviceSource(): String = listOf(
        File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"),
        File("app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"),
    ).firstOrNull(File::exists)?.readText()
        ?: error("LiveRideAccessibilityService.kt nao encontrado")

    @Test
    fun selectedAppWithoutValidCardClearsDirectlyToYellowWithoutGrayFrame() {
        val service = serviceSource()
        val processStart = service.indexOf("private suspend fun processRideText(")
        val processEnd = service.indexOf("private suspend fun analyzeUniversalTwoAddress(", processStart)
        val processRegion = service.substring(processStart, processEnd)
        val inactiveStart = processRegion.indexOf("if (!activeTrigger)")
        val inactiveEnd = processRegion.indexOf("universalLastActiveReadAtMillis", inactiveStart)
        val inactiveRegion = processRegion.substring(inactiveStart, inactiveEnd)

        assertTrue("Aplicativo selecionado precisa definir a cor de espera", "val keepWaitingYellow127 = shouldScanCurrentWindow()" in inactiveRegion)
        assertTrue("Limpeza deve receber o alvo amarelo", "keepWaitingYellow = keepWaitingYellow127" in inactiveRegion)
        assertTrue("Contrato final precisa estar marcado", "atomic_selected_app_clear_color_0_1_127" in inactiveRegion)
        assertFalse("Fluxo nao pode pintar cinza antes do amarelo", "showOverlay(RadarColor.Idle" in inactiveRegion)
        assertFalse("Fluxo nao pode fazer uma segunda pintura amarela", "showOverlay(RadarColor.Default" in inactiveRegion)
    }

    @Test
    fun hardClearPaintsOnlyTheResolvedTargetColor() {
        val service = serviceSource()
        val clearStart = service.indexOf("private fun hardClearUniversalTwoAddress(")
        val clearEnd = service.indexOf("private fun universalResolvedForegroundPackage(", clearStart)
        val clearRegion = service.substring(clearStart, clearEnd)

        assertTrue("Limpeza precisa aceitar alvo de espera", "keepWaitingYellow: Boolean = false" in clearRegion)
        assertTrue("Alvo amarelo deve ser escolhido atomicamente", "if (keepWaitingYellow) RadarColor.Default else RadarColor.Idle" in clearRegion)
        assertTrue("Pintura atomica precisa estar marcada", "atomic_hard_clear_single_paint_0_1_127" in clearRegion)
        assertEquals(
            "Limpeza deve chamar showOverlay somente uma vez",
            1,
            Regex("showOverlay\\(").findAll(clearRegion).count(),
        )
    }
}
