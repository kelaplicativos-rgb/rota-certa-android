package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class LiveRideBubbleDecisionPolicyTest {
    private val policy = LiveRideBubbleDecisionPolicy()

    @Test
    fun blocksDecisionAndDistanceWhenPackageIsNotMonitored() {
        val decision = policy.decide(
            LiveRideBubbleDecisionInput(
                monitoredPackageActive = false,
                registeredCardMatched = true,
                destinationIdentified = true,
                result = goodResult(),
                nearestConfiguredDistanceKm = 4.2,
            ),
        )

        assertEquals(LiveRideBubbleSignal.Idle, decision.signal)
        assertNull(decision.distanceKm)
        assertTrue(decision.shouldClearActiveDecision)
    }

    @Test
    fun blocksDecisionAndDistanceWhenRegisteredCardIsMissing() {
        val decision = policy.decide(
            LiveRideBubbleDecisionInput(
                monitoredPackageActive = true,
                registeredCardMatched = false,
                destinationIdentified = true,
                result = goodResult(),
                nearestConfiguredDistanceKm = 4.2,
            ),
        )

        assertEquals(LiveRideBubbleSignal.WaitingForRegisteredCard, decision.signal)
        assertNull(decision.distanceKm)
        assertTrue(decision.shouldClearActiveDecision)
    }

    @Test
    fun blocksGreenWhenResultReasonSaysCardIsNotRegistered() {
        val decision = policy.decide(
            LiveRideBubbleDecisionInput(
                monitoredPackageActive = true,
                registeredCardMatched = true,
                destinationIdentified = true,
                result = goodResult(
                    reason = "Tela parece card de corrida, mas ainda nao bate com nenhum card cadastrado. Salvei a amostra; cadastre o modelo para liberar o farol.",
                ),
                nearestConfiguredDistanceKm = 3.15,
            ),
        )

        assertEquals(LiveRideBubbleSignal.WaitingForRegisteredCard, decision.signal)
        assertNull(decision.distanceKm)
        assertTrue(decision.shouldClearActiveDecision)
    }

    @Test
    fun blocksRedWhenResultReasonAsksToRegisterModel() {
        val decision = policy.decide(
            LiveRideBubbleDecisionInput(
                monitoredPackageActive = true,
                registeredCardMatched = true,
                destinationIdentified = true,
                result = goodResult(
                    recommendation = Recommendation.OutsideRadius,
                    reason = "Cadastre o modelo para liberar o farol.",
                ),
                nearestConfiguredDistanceKm = 15.56,
            ),
        )

        assertEquals(LiveRideBubbleSignal.WaitingForRegisteredCard, decision.signal)
        assertNull(decision.distanceKm)
        assertTrue(decision.shouldClearActiveDecision)
    }

    @Test
    fun blocksDecisionAndDistanceWhenDestinationIsMissing() {
        val decision = policy.decide(
            LiveRideBubbleDecisionInput(
                monitoredPackageActive = true,
                registeredCardMatched = true,
                destinationIdentified = false,
                result = goodResult(fields = RideFields(destination = null)),
                nearestConfiguredDistanceKm = 4.2,
            ),
        )

        assertEquals(LiveRideBubbleSignal.WaitingForData, decision.signal)
        assertNull(decision.distanceKm)
        assertTrue(decision.shouldClearActiveDecision)
    }

    @Test
    fun allowsGreenOnlyWhenAllPrerequisitesAreMet() {
        val decision = policy.decide(
            LiveRideBubbleDecisionInput(
                monitoredPackageActive = true,
                registeredCardMatched = true,
                destinationIdentified = true,
                result = goodResult(),
                nearestConfiguredDistanceKm = 4.2,
            ),
        )

        assertEquals(LiveRideBubbleSignal.Accept, decision.signal)
        assertEquals(4.2, decision.distanceKm!!, 0.0001)
        assertFalse(decision.shouldClearActiveDecision)
    }

    @Test
    fun allowsRedOnlyWhenAllPrerequisitesAreMet() {
        val decision = policy.decide(
            LiveRideBubbleDecisionInput(
                monitoredPackageActive = true,
                registeredCardMatched = true,
                destinationIdentified = true,
                result = goodResult(recommendation = Recommendation.OutsideRadius),
                nearestConfiguredDistanceKm = 24.0,
            ),
        )

        assertEquals(LiveRideBubbleSignal.Reject, decision.signal)
        assertEquals(24.0, decision.distanceKm!!, 0.0001)
        assertFalse(decision.shouldClearActiveDecision)
    }

    private fun goodResult(
        recommendation: Recommendation = Recommendation.GoodRide,
        fields: RideFields = RideFields(destination = "Centro"),
        reason: String = "Destino final dentro do raio da casa por rota real do Google Maps.",
    ) = AnalysisResult(
        createdAtMillis = 1L,
        extractedText = "Destino Centro",
        fields = fields,
        recommendation = recommendation,
        reason = reason,
        pickupToHomeKm = 4.2,
    )
}
