package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionAccelerationPlannerTest {
    private val center = Coordinate(-23.6633, -46.5333)

    @Test
    fun signatureChangesWhenAddressRadiusOrCoordinateChanges() {
        val base = AppSettings(
            homeAddress = "Rua Exemplo, 100",
            homeCoordinate = center,
            homeRadiusKm = 7.0,
        )
        val first = RegionAccelerationPlanner.signature(base)

        assertNotEquals(first, RegionAccelerationPlanner.signature(base.copy(homeAddress = "Rua Exemplo, 200")))
        assertNotEquals(first, RegionAccelerationPlanner.signature(base.copy(homeRadiusKm = 8.0)))
        assertNotEquals(first, RegionAccelerationPlanner.signature(base.copy(homeCoordinate = Coordinate(-23.66, -46.54))))
        assertEquals(first, RegionAccelerationPlanner.signature(base.copy(homeAddress = "  RUA   EXEMPLO, 100  ")))
    }

    @Test
    fun boundaryPointsAreDeterministicAndStayNearConfiguredRadius() {
        val first = RegionAccelerationPlanner.boundaryPoints(center, radiusKm = 7.0)
        val second = RegionAccelerationPlanner.boundaryPoints(center, radiusKm = 7.0)

        assertEquals(RegionAccelerationPlanner.DEFAULT_BOUNDARY_POINTS, first.size)
        assertEquals(first, second)
        first.forEach { point ->
            val distanceKm = GeoDistance.meters(center, point) / 1000.0
            assertTrue("Ponto deveria ficar proximo de 7 km: $distanceKm", distanceKm in 6.95..7.05)
        }
    }

    @Test
    fun profileExpiresAndInvalidatesAfterRegionChange() {
        val settings = AppSettings(
            homeAddress = "Casa",
            homeCoordinate = center,
            homeRadiusKm = 5.0,
        )
        val preparedAt = 1_000L
        val profile = RegionAccelerationProfile(
            signature = RegionAccelerationPlanner.signature(settings),
            preparedAtMillis = preparedAt,
            expiresAtMillis = preparedAt + RegionAccelerationPlanner.PROFILE_TTL_MILLIS,
            homeCoordinate = center,
            alternativeCoordinate = null,
            homeRadiusKm = 5.0,
            alternativeRadiusKm = 10.0,
            boundaryPointCount = 16,
            routesApiReady = true,
            geocodeElapsedMillis = 100L,
            routeProbeElapsedMillis = 150L,
        )

        assertTrue(RegionAccelerationPlanner.isValid(profile, settings, preparedAt + 1L))
        assertFalse(RegionAccelerationPlanner.isValid(profile, settings.copy(homeRadiusKm = 6.0), preparedAt + 1L))
        assertFalse(RegionAccelerationPlanner.isValid(profile, settings, profile.expiresAtMillis))
    }

    @Test
    fun routeProbeIsSmallAndInsideThePreparedRegion() {
        val probe = RegionAccelerationPlanner.routeProbePoint(center, configuredRadiusKm = 10.0)
        val distanceKm = GeoDistance.meters(center, probe) / 1000.0

        assertTrue(distanceKm in 0.39..0.41)
        assertTrue(distanceKm < 10.0)
    }
}
