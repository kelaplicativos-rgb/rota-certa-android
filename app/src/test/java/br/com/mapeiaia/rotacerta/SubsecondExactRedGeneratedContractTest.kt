package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsecondExactRedGeneratedContractTest {
    private fun serviceSource(): String = listOf(
        File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"),
        File("app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"),
    ).firstOrNull(File::exists)?.readText()
        ?: error("LiveRideAccessibilityService.kt nao encontrado")

    @Test
    fun exactAddressMatrixFeedsFinalDecisionWithoutProvisionalColor() {
        val service = serviceSource()
        val analysisStart = service.indexOf("private suspend fun analyzeUniversalTwoAddress(")
        val analysisEnd = service.indexOf("private suspend fun applyUniversalTwoAddressResult(", analysisStart)
        val region = service.substring(analysisStart, analysisEnd)

        assertTrue("Rota direta por endereco deve usar a matriz final", "single_exact_route_matrix_checklist_13" in region)
        assertTrue("Casa e alfinetes devem compartilhar a mesma matriz", "targetsChecklist13.destinations" in region)
        assertEquals(1, Regex("drivingDistancesFromAddressKm\\(").findAll(region).count())
        assertTrue("Resultado exato deve alimentar o motor final", "decideFastWorkRegionChecklist13(" in region)
        assertTrue("Resultado deve ser aplicado somente após a matriz", "applyUniversalTwoAddressResult(resultChecklist13" in region)
        assertFalse("Chamadas sequenciais por coordenada não podem voltar", "routeDistanceKm(" in region)
        assertFalse("Linha reta jamais pode liberar verde", "fastInsideResult" in region)
        assertFalse("Nao deve publicar vermelho sem quilometro antes da rota exata", "showOverlay(RadarColor.Red, distanceKm = null)" in region)
    }
}
