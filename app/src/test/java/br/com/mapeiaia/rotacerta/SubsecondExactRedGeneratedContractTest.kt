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
    fun definitelyOutsideDestinationTurnsRedBeforeAnyRouteRequest() {
        val service = serviceSource()
        val analysisStart = service.indexOf("private suspend fun analyzeUniversalTwoAddress(")
        val analysisEnd = service.indexOf("private suspend fun applyUniversalTwoAddressResult(", analysisStart)
        val region = service.substring(analysisStart, analysisEnd)
        val fastApply = region.indexOf("fastOutsideResult")
        val firstRoute = region.indexOf("val homeRouteStartedAt")

        assertTrue("Politica de limite geometrico precisa estar no codigo gerado", "subsecond_exact_red_lower_bound_0_1_125" in region)
        assertTrue("Vermelho exato precisa ser aplicado antes da primeira rota", fastApply >= 0 && fastApply < firstRoute)
        assertTrue("Fluxo comprovadamente fora precisa encerrar antes da API", "return\n        } // subsecond_exact_red_lower_bound_0_1_125" in region)
        assertFalse("Linha reta jamais pode liberar verde", "fastInsideResult" in region)
    }
}
