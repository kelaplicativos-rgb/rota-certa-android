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
    fun definitelyOutsideDestinationTurnsRedThenContinuesExactRouteForKm() {
        val service = serviceSource()
        val analysisStart = service.indexOf("private suspend fun analyzeUniversalTwoAddress(")
        val analysisEnd = service.indexOf("private suspend fun applyUniversalTwoAddressResult(", analysisStart)
        val region = service.substring(analysisStart, analysisEnd)
        val fastApply = region.indexOf("showOverlay(RadarColor.Red, distanceKm = null)")
        val firstRoute = region.indexOf("val homeRouteStartedAt")

        assertTrue("Politica geometrica precisa continuar no codigo", "subsecond_exact_red_lower_bound_0_1_125" in region)
        assertTrue("Vermelho provisório precisa aparecer antes da rota", fastApply >= 0 && fastApply < firstRoute)
        assertTrue("Rota exata precisa continuar para preencher o km", "fast_red_continues_exact_route_0_1_127" in region)
        assertTrue("Diagnostico deve registrar continuacao da rota", "exact_route_continues=true" in region)
        assertFalse(
            "Fluxo fora nao pode mais encerrar antes da consulta exata",
            "return\n        } // subsecond_exact_red_lower_bound_0_1_125" in region,
        )
        assertFalse("Linha reta jamais pode liberar verde", "fastInsideResult" in region)
    }
}
