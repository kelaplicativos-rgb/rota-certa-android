package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactRadiusLowerBoundPolicyTest {
    @Test
    fun destinationBeyondEveryConfiguredRadiusIsDefinitelyOutside() {
        val destination = Coordinate(-23.8000, -46.7000)
        val home = Coordinate(-23.6000, -46.5000)
        val settings = AppSettings(
            homeTargetEnabled = true,
            alternativeTargetEnabled = false,
            homeRadiusKm = 10.0,
        )

        val decision = ExactRadiusLowerBoundPolicy.evaluate(
            destinationCoordinate = destination,
            settings = settings,
            homeCoordinate = home,
            alternativeCoordinate = null,
        )

        assertTrue(decision.definitelyOutside)
        assertTrue((decision.nearestLowerBoundKm ?: 0.0) > settings.homeRadiusKm)
    }

    @Test
    fun oneTargetInsidePreventsPrematureRed() {
        val destination = Coordinate(-23.6100, -46.4800)
        val home = Coordinate(-23.61119, -46.47619)
        val alternative = Coordinate(-23.9000, -46.9000)
        val settings = AppSettings(
            homeTargetEnabled = true,
            alternativeTargetEnabled = true,
            homeRadiusKm = 10.0,
            alternativeRadiusKm = 10.0,
        )

        val decision = ExactRadiusLowerBoundPolicy.evaluate(
            destinationCoordinate = destination,
            settings = settings,
            homeCoordinate = home,
            alternativeCoordinate = alternative,
        )

        assertFalse(decision.definitelyOutside)
    }

    @Test
    fun missingDestinationOrConfiguredTargetCannotProveOutside() {
        val settings = AppSettings(
            homeTargetEnabled = true,
            alternativeTargetEnabled = true,
        )

        assertFalse(
            ExactRadiusLowerBoundPolicy.evaluate(
                destinationCoordinate = null,
                settings = settings,
                homeCoordinate = Coordinate(-23.6, -46.5),
                alternativeCoordinate = null,
            ).definitelyOutside,
        )
        assertFalse(
            ExactRadiusLowerBoundPolicy.evaluate(
                destinationCoordinate = Coordinate(-23.8, -46.8),
                settings = settings,
                homeCoordinate = null,
                alternativeCoordinate = null,
            ).definitelyOutside,
        )
    }
}
