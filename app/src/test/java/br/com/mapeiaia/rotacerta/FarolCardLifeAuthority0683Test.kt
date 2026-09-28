package br.com.mapeiaia.rotacerta

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FarolCardLifeAuthority0683Test {
    private fun evaluation(
        pickup: String,
        destination: String,
        text: String = "$pickup\n$destination",
    ) = FarolUniversalVisualPipelineStage19.Evaluation(
        windowId = 10,
        blockId = "test",
        source = FarolUniversalVisualPipelineStage19.Source.Accessibility,
        analysisText = text,
        addresses = listOf(pickup, destination),
        pickup = pickup,
        destination = destination,
        addressSignature = DestinationAddressIdentityPolicy.signature("visual", destination),
        screenHash = 1,
    )

    private fun source(name: String): String {
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOf(
            File(cwd, "src/main/java/br/com/mapeiaia/rotacerta/$name"),
            File(cwd, "app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
            File(cwd.parentFile ?: cwd, "app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("source not found: $name; cwd=${cwd.absolutePath}")
    }

    @Test
    fun sameCardIgnoresVolatileFareEtaAndDriverDistance() {
        val first = evaluation(
            "Rua Ana Jarvis, 31, Jardim Ester",
            "Rua Gil de Oliveira, 540, Chacara Seis de Outubro",
            "Voce 1,1 km 5 min\nRua Ana Jarvis, 31, Jardim Ester\nRua Gil de Oliveira, 540, Chacara Seis de Outubro\nR$ 30\n11,8 km",
        )
        val second = evaluation(
            "Rua Ana Jarvis, 31, Jardim Ester",
            "Rua Gil de Oliveira, 540, Chacara Seis de Outubro",
            "Voce 0,7 km 3 min\nRua Ana Jarvis, 31, Jardim Ester\nRua Gil de Oliveira, 540, Chacara Seis de Outubro\nR$ 34\n11,8 km",
        )
        val a = FarolCardLifeAuthority0683.identity(first)
        val b = FarolCardLifeAuthority0683.identity(second)
        assertTrue(FarolCardLifeAuthority0683.sameCard(a, b))
    }

    @Test
    fun differentPickupWithSameDestinationIsAReplacement() {
        val first = FarolCardLifeAuthority0683.identity(evaluation(
            "Rua A, 10, Sao Paulo - SP",
            "Rua Destino, 100, Sao Bernardo do Campo - SP",
        ))
        val second = FarolCardLifeAuthority0683.identity(evaluation(
            "Rua B, 20, Sao Paulo - SP",
            "Rua Destino, 100, Sao Bernardo do Campo - SP",
        ))
        assertNotEquals(first, second)
        assertTrue(FarolCardLifeAuthority0683.provesReplacement(true, first, second))
    }

    @Test
    fun equivalentAddressSpellingKeepsSameCard() {
        val first = FarolCardLifeAuthority0683.identity(evaluation(
            "Av. Paulista, 1000, Sao Paulo - SP",
            "Rua Vergueiro, 2000, Sao Paulo - SP",
        ))
        val second = FarolCardLifeAuthority0683.identity(evaluation(
            "Avenida Paulista, 1000, Sao Paulo - SP",
            "Rua Vergueiro, 2000, Sao Paulo - SP",
        ))
        assertTrue(FarolCardLifeAuthority0683.sameCard(first, second))
    }

    @Test
    fun serviceHasNoDecisionTimerAndUsesProvenReplacementClear() {
        val live = source("LiveRideAccessibilityService.kt")
        assertFalse(live.contains("DECISION_VISUAL_TTL_MILLIS_0682"))
        assertFalse(live.contains("scheduleDecisionBubbleExpiry0682"))
        assertTrue(live.contains("S683_PROVEN_CARD_REPLACEMENT_CLEARED"))
        assertTrue(live.contains("S683_AMBIGUOUS_SURFACE_FINAL_PRESERVED_FOR_VERIFY"))
        assertTrue(live.contains("S683_PARTIAL_NO_ADDRESS_ESCALATED_TO_FULL_SURFACE"))
        assertTrue(live.contains("universalActiveCardIdentity0683"))
    }

    @Test
    fun cardReplacementInvalidatorCancelsOldRouteBeforeYellow() {
        val live = source("LiveRideAccessibilityService.kt")
        val start = live.indexOf("private fun invalidateProvenCardReplacement0683(")
        val end = live.indexOf("private fun collectUniversalAccessibilityBlocksStage19(", start)
        assertTrue(start >= 0 && end > start)
        val block = live.substring(start, end)
        val cancel = block.indexOf("universalRouteJob?.cancel()")
        val clear = block.indexOf("showOverlay(RadarColor.Default, distanceKm = null)")
        assertTrue(cancel >= 0 && clear > cancel)
    }
}
