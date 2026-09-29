package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolLocalDecisionAuthority0696Test {
    private fun result(
        recommendation: Recommendation,
        homeKm: Double? = null,
        alternativeKm: Double? = null,
    ) = AnalysisResult(
        createdAtMillis = 1L,
        extractedText = "Destino: Rua Teste, 100",
        fields = RideFields(destination = "Rua Teste, 100"),
        recommendation = recommendation,
        reason = "test",
        pickupToHomeKm = homeKm,
        pickupToAlternativeKm = alternativeKm,
    )

    @Test
    fun greenWithLocalDistanceIsImmediatelyFinal() {
        assertTrue(
            FarolLocalDecisionAuthority0696.isFinalLocalDecision(
                result(Recommendation.GoodRide, homeKm = 2.4),
            ),
        )
    }

    @Test
    fun redWithLocalDistanceIsImmediatelyFinal() {
        assertTrue(
            FarolLocalDecisionAuthority0696.isFinalLocalDecision(
                result(Recommendation.OutsideRadius, alternativeKm = 8.7),
            ),
        )
    }

    @Test
    fun insufficientOrMissingDistanceNeverPublishes() {
        assertFalse(
            FarolLocalDecisionAuthority0696.isFinalLocalDecision(
                result(Recommendation.InsufficientData, homeKm = 2.0),
            ),
        )
        assertFalse(
            FarolLocalDecisionAuthority0696.isFinalLocalDecision(
                result(Recommendation.GoodRide),
            ),
        )
    }

    @Test
    fun remoteRouteCanNeverFlipLocalGreenToRed() {
        assertEquals(
            Recommendation.GoodRide,
            FarolLocalDecisionAuthority0696.preserveLocalRecommendation(
                Recommendation.GoodRide,
                Recommendation.OutsideRadius,
            ),
        )
    }

    @Test
    fun remoteRouteCanNeverFlipLocalRedToGreen() {
        assertEquals(
            Recommendation.OutsideRadius,
            FarolLocalDecisionAuthority0696.preserveLocalRecommendation(
                Recommendation.OutsideRadius,
                Recommendation.GoodRide,
            ),
        )
    }
}
