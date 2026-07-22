package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectedAppWaitingYellow127Test {
    private fun serviceSource(): String = listOf(
        File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"),
        File("app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"),
    ).firstOrNull(File::exists)?.readText()
        ?: error("LiveRideAccessibilityService.kt nao encontrado")

    @Test
    fun selectedAppWithoutValidCardReturnsToYellow() {
        val service = serviceSource()
        val processStart = service.indexOf("private suspend fun processRideText(")
        val processEnd = service.indexOf("private suspend fun analyzeUniversalTwoAddress(", processStart)
        val processRegion = service.substring(processStart, processEnd)
        val inactiveStart = processRegion.indexOf("if (!activeTrigger)")
        val inactiveEnd = processRegion.indexOf("universalLastActiveReadAtMillis", inactiveStart)
        val inactiveRegion = processRegion.substring(inactiveStart, inactiveEnd)

        assertTrue("Card antigo precisa ser limpo", "hardClearUniversalTwoAddress(clearReason)" in inactiveRegion)
        assertTrue("Amarelo so deve permanecer no app selecionado", "if (shouldScanCurrentWindow())" in inactiveRegion)
        assertTrue("Estado de espera deve ser amarelo", "showOverlay(RadarColor.Default, distanceKm = null)" in inactiveRegion)
        assertTrue("Contrato final precisa estar marcado", "selected_app_clear_to_yellow_0_1_127" in inactiveRegion)
    }
}
