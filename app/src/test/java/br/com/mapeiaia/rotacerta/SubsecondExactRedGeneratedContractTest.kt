package br.com.mapeiaia.rotacerta

import java.io.File
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
    fun exactAddressMatrixReplacesProvisionalColorAndStillHasCoordinateFallback() {
        val service = serviceSource()
        val analysisStart = service.indexOf("private suspend fun analyzeUniversalTwoAddress(")
        val analysisEnd = service.indexOf("private suspend fun applyUniversalTwoAddressResult(", analysisStart)
        val region = service.substring(analysisStart, analysisEnd)

        assertTrue("Rota direta por endereco deve ser tentada primeiro", "direct_address_route_matrix_runtime_0_1_128" in region)
        assertTrue("Resultado exato deve ser aplicado sem aguardar aquecimento", "applyUniversalTwoAddressResult(directResult128" in region)
        assertTrue("Geocodificacao antiga deve continuar como fallback", "destination_fallback" in region)
        assertTrue("Fallback de coordenadas deve continuar calculando distancia real", "routeDistanceKm(destinationCoordinate" in region)
        assertFalse("Linha reta jamais pode liberar verde", "fastInsideResult" in region)
        assertFalse("Nao deve publicar vermelho sem quilometro antes da rota exata", "showOverlay(RadarColor.Red, distanceKm = null)" in region)
    }
}
