package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiAddressWorkRegionChecklist7Test {
    private val airport = WorkRegionPin(
        id = "airport",
        address = "Aeroporto Internacional",
        coordinate = Coordinate(-23.4356, -46.4731),
        enabled = true,
    )
    private val busStation = WorkRegionPin(
        id = "bus",
        address = "Rodoviaria Central",
        coordinate = Coordinate(-23.5164, -46.6251),
        enabled = true,
    )

    @Test
    fun legacyAlternativeBecomesFirstEditablePinWithoutDataLoss() {
        val settings = AppSettings(
            alternativeAddress = "Aeroporto antigo",
            alternativeCoordinate = Coordinate(-23.4, -46.4),
            workRegionPins = emptyList(),
        )

        val pins = WorkRegionTargetPolicy.editablePins(settings)

        assertEquals(1, pins.size)
        assertEquals(WorkRegionTargetPolicy.LEGACY_PIN_ID, pins.single().id)
        assertEquals("Aeroporto antigo", pins.single().address)
    }

    @Test
    fun addingPinMigratesLegacyAddressAndPreventsDuplicates() {
        val settings = AppSettings(
            alternativeAddress = airport.address,
            alternativeCoordinate = airport.coordinate,
        )

        val withBus = WorkRegionTargetPolicy.addOrUpdate(settings, busStation)
        val duplicatedAirport = WorkRegionTargetPolicy.addOrUpdate(withBus, airport.copy(id = "airport-copy"))

        assertTrue(duplicatedAirport.alternativeAddress.isBlank())
        assertEquals(2, WorkRegionTargetPolicy.editablePins(duplicatedAirport).size)
    }

    @Test
    fun anyExactPinInsideRadiusMakesFarolGreen() {
        val result = DecisionEngine().decideWorkRegion(
            fields = RideFields(destination = "Destino da corrida"),
            settings = AppSettings(
                homeTargetEnabled = false,
                alternativeTargetEnabled = true,
                alternativeRadiusKm = 5.0,
            ),
            fullText = "card",
            homeTargetActive = false,
            homeDistanceKm = null,
            pinRoutes = listOf(
                WorkRegionPinRoute(airport, 7.0),
                WorkRegionPinRoute(busStation, 2.8),
            ),
        )

        assertEquals(Recommendation.GoodRide, result.recommendation)
        assertTrue(result.reason.contains("Rodoviaria Central"))
        assertEquals(2.8, result.destinationToAlternativeKm ?: 0.0, 0.0001)
    }

    @Test
    fun missingExactDistanceCannotCreateFalseRed() {
        val result = DecisionEngine().decideWorkRegion(
            fields = RideFields(destination = "Destino da corrida"),
            settings = AppSettings(
                homeTargetEnabled = false,
                alternativeTargetEnabled = true,
                alternativeRadiusKm = 5.0,
            ),
            fullText = "card",
            homeTargetActive = false,
            homeDistanceKm = null,
            pinRoutes = listOf(
                WorkRegionPinRoute(airport, 8.0),
                WorkRegionPinRoute(busStation, null),
            ),
        )

        assertEquals(Recommendation.InsufficientData, result.recommendation)
        assertFalse(result.recommendation == Recommendation.OutsideRadius)
    }

    @Test
    fun allExactTargetsOutsideMakeFarolRed() {
        val result = DecisionEngine().decideWorkRegion(
            fields = RideFields(destination = "Destino da corrida"),
            settings = AppSettings(
                homeTargetEnabled = true,
                alternativeTargetEnabled = true,
                homeRadiusKm = 5.0,
                alternativeRadiusKm = 5.0,
            ),
            fullText = "card",
            homeTargetActive = true,
            homeDistanceKm = 9.0,
            pinRoutes = listOf(
                WorkRegionPinRoute(airport, 8.0),
                WorkRegionPinRoute(busStation, 6.0),
            ),
        )

        assertEquals(Recommendation.OutsideRadius, result.recommendation)
        assertEquals(6.0, result.destinationToAlternativeKm ?: 0.0, 0.0001)
    }

    @Test
    fun groupTogglesSelectCasaPinsOrBoth() {
        val pinsOnly = AppSettings(homeTargetEnabled = false, alternativeTargetEnabled = true)
        val homeOnly = AppSettings(homeTargetEnabled = true, alternativeTargetEnabled = false)
        val both = AppSettings(homeTargetEnabled = true, alternativeTargetEnabled = true)

        assertFalse(pinsOnly.homeTargetEnabled)
        assertTrue(pinsOnly.alternativeTargetEnabled)
        assertTrue(homeOnly.homeTargetEnabled)
        assertFalse(homeOnly.alternativeTargetEnabled)
        assertTrue(both.homeTargetEnabled && both.alternativeTargetEnabled)
    }
}
