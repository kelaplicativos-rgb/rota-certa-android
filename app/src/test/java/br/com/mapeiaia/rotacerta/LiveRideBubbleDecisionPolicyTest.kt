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
    ) = AnalysisResult(
        createdAtMillis = 1L,
        extractedText = "Destino Centro",
        fields = fields,
        recommendation = recommendation,
        reason = "resultado de teste",
        pickupToHomeKm = 4.2,
    )
}
