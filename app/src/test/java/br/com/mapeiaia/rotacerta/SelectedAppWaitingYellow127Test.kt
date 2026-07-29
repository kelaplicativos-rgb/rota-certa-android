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
    fun savedAppWithoutTwoAddressesPreservesRecentValidDecisionBeforeYellow() {
        val service = serviceSource()
        val processStart = service.indexOf("private suspend fun processRideText(")
        val processEnd = service.indexOf("private suspend fun analyzeUniversalTwoAddress(", processStart)
        val processRegion = service.substring(processStart, processEnd)
        val inactiveStart = processRegion.indexOf("if (!evaluationChecklist13.active)")
        val inactiveEnd = processRegion.indexOf("universalLastActiveReadAtMillis =", inactiveStart)
        val inactiveRegion = processRegion.substring(inactiveStart, inactiveEnd)

        assertTrue("Decisão válida deve ser preservada", "preserveStableDecision141" in inactiveRegion)
        assertTrue(
            "Ausência vazia deve preservar a decisão enquanto o mesmo pacote selecionado estiver em primeiro plano",
            "universalForegroundPackageName == selectedPackageChecklist13" in inactiveRegion,
        )
        assertTrue("Leitura inválida preservada deve ser diagnosticada", "BUBBLE_INVALID_READ_DEFERRED" in inactiveRegion)
        assertTrue("Após confirmação, limpeza deve manter o estado amarelo", "keepWaitingYellow = true" in inactiveRegion)
        assertTrue("Contrato deve marcar ausência confirmada", "confirmed_absence_clear_0_1_141" in inactiveRegion)
        assertFalse("Fluxo nao pode pintar cinza antes do amarelo", "showOverlay(RadarColor.Idle" in inactiveRegion)
        assertFalse("Fluxo não pode fazer pintura manual duplicada", "showOverlay(RadarColor.Default" in inactiveRegion)
    }

    @Test
    fun hardClearPaintsOnlyTheResolvedTargetColorAndKeepsYellowIdempotent() {
        val service = serviceSource()
        val clearStart = service.indexOf("private fun hardClearUniversalTwoAddress(")
        val clearEnd = service.indexOf("private fun shouldProtectLockedPopupSession128(", clearStart)
        val clearRegion = service.substring(clearStart, clearEnd)

        assertTrue("Limpeza precisa aceitar alvo de espera", "keepWaitingYellow: Boolean = false" in clearRegion)
        assertTrue("Alvo amarelo deve ser escolhido atomicamente", "if (keepWaitingYellow) RadarColor.Default else RadarColor.Idle" in clearRegion)
        assertTrue("Pintura atomica precisa estar marcada", "atomic_hard_clear_single_paint_0_1_127" in clearRegion)
        assertTrue("Amarelo nao pode ser tratado como decisao ativa", "yellow_waiting_not_active_data_0_1_127" in clearRegion)
        assertTrue(
            "Somente verde ou vermelho devem contar como cor de decisao",
            "currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red" in clearRegion,
        )
        assertEquals(
            "Limpeza deve chamar showOverlay somente uma vez",
            1,
            Regex("showOverlay\\(").findAll(clearRegion).count(),
        )
    }
}
