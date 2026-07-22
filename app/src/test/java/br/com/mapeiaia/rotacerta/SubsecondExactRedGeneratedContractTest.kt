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
    fun definitelyOutsideDestinationTurnsRedBeforeAwaitingExactRoutes() {
        val service = serviceSource()
        val analysisStart = service.indexOf("private suspend fun analyzeUniversalTwoAddress(")
        val analysisEnd = service.indexOf("private suspend fun applyUniversalTwoAddressResult(", analysisStart)
        val region = service.substring(analysisStart, analysisEnd)
        val fastApply = region.indexOf("showOverlay(RadarColor.Red, distanceKm = null)")
        val firstExactRouteAwait = listOf(
            region.indexOf("homeRouteDeferred127.await()"),
            region.indexOf("alternativeRouteDeferred127.await()"),
        ).filter { it >= 0 }.minOrNull() ?: -1

        assertTrue("Politica geometrica precisa continuar no codigo", "subsecond_exact_red_lower_bound_0_1_125" in region)
        assertTrue("Geocodificacao deve ocorrer em paralelo", "destinationCoordinateDeferred128 = async" in region)
        assertTrue(
            "Vermelho provisorio precisa aparecer antes de aguardar as rotas exatas",
            fastApply >= 0 && firstExactRouteAwait >= 0 && fastApply < firstExactRouteAwait,
        )
        assertTrue("Rota exata precisa continuar para preencher o km", "fast_red_continues_exact_route_0_1_127" in region)
        assertTrue("Rotas exatas devem ser paralelas", "parallel_exact_routes_0_1_127" in region)
        assertTrue("Diagnostico deve registrar continuacao da rota", "exact_route_continues=true" in region)
        assertFalse("Linha reta jamais pode liberar verde", "fastInsideResult" in region)
    }
}
