package br.com.mapeiaia.rotacerta.trips

import br.com.mapeiaia.rotacerta.AppSettings
import br.com.mapeiaia.rotacerta.Coordinate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripTimelineBaseDirectionStage47Test {
    private fun entry(
        origin: String = "Base persistida",
        destination: String = "Ponto externo",
    ) = TripTimelineEntry(
        tripId = "external:direction-test",
        profileId = "profile-test",
        profileLabel = "Perfil teste",
        departureAtMillis = 1_800_000_000_000L,
        arrivalAtMillis = 1_800_003_600_000L,
        origin = origin,
        destination = destination,
        status = TripStatus.PUBLISHED,
        capacity = 0,
        minimumOccupiedSeats = 0,
        maximumOccupiedSeats = 0,
        sourcePassengerSeats = emptyMap(),
    )

    @Test
    fun externalTrustedGeoReturnsIdaVoltaAndNeutralLabels() {
        val home = Coordinate(0.0, 0.0)
        val geo = mapOf(
            "Base persistida" to TimelineGeoPoint(0.0, 0.0),
            "Ponto externo" to TimelineGeoPoint(0.1, 0.0),
            "Ponto externo A" to TimelineGeoPoint(0.1, 0.0),
            "Ponto externo B" to TimelineGeoPoint(0.2, 0.0),
        )

        assertEquals(
            "↑ Ida • saindo da base",
            timelineBaseDirectionLabel(entry(), null, geo, home, 1.0),
        )
        assertEquals(
            "↓ Volta • retornando à base",
            timelineBaseDirectionLabel(entry("Ponto externo", "Base persistida"), null, geo, home, 1.0),
        )
        assertEquals(
            "↔ Neutra • não envolve a base",
            timelineBaseDirectionLabel(entry("Ponto externo A", "Ponto externo B"), null, geo, home, 1.0),
        )
    }

    @Test
    fun insufficientTrustedGeoFailsClosed() {
        val result = timelineBaseDirectionLabel(
            entry(),
            trip = null,
            trustedGeo = mapOf("Base persistida" to TimelineGeoPoint(0.0, 0.0)),
            home = Coordinate(0.0, 0.0),
            radiusKm = 1.0,
        )

        assertNull(result)
    }

    @Test
    fun trustedDirectionStopsUseOnlyPersistedCoordinates() {
        val settings = AppSettings(
            homeAddress = "Base persistida",
            homeCoordinate = Coordinate(0.0, 0.0),
            alternativeAddress = "Alfinete persistido",
            alternativeCoordinate = Coordinate(0.2, 0.0),
        )

        val trusted = timelineTrustedDirectionStops(emptyList(), settings)

        assertEquals(setOf("Base persistida", "Alfinete persistido"), trusted.map(TripStop::name).toSet())
        assertTrue(
            timelineTrustedDirectionStops(
                emptyList(),
                AppSettings(homeAddress = "Sem coordenada"),
            ).isEmpty(),
        )
    }

    @Test
    fun directionPresentationDoesNotChangePhysicalOverbookingCalculation() {
        val overloaded = entry().copy(
            capacity = 1,
            minimumOccupiedSeats = 0,
            maximumOccupiedSeats = 0,
            sourcePassengerSeats = mapOf(BookingSource.BLABLACAR to 2),
        )
        val physical = TripPhysicalRideConsolidator.consolidate(listOf(overloaded), emptyMap()).single()

        assertTrue(TripTimelineIssue.OVERBOOKING in physical.issues)
        assertEquals(
            "↑ Ida • saindo da base",
            timelineBaseDirectionLabel(
                physical,
                trip = null,
                trustedGeo = mapOf(
                    "Base persistida" to TimelineGeoPoint(0.0, 0.0),
                    "Ponto externo" to TimelineGeoPoint(0.1, 0.0),
                ),
                home = Coordinate(0.0, 0.0),
                radiusKm = 1.0,
            ),
        )
        assertTrue(TripTimelineIssue.OVERBOOKING in physical.issues)
    }

    @Test
    fun searchKeepsConflictAndOverbookingIssuesOnMatchedEntry() {
        val risky = entry().copy(
            profileLabel = "Perfil pesquisável",
            issues = setOf(TripTimelineIssue.PHYSICAL_CONFLICT, TripTimelineIssue.OVERBOOKING),
        )

        val matched = filterTimelineEntries(
            entries = listOf(risky),
            trips = emptyList(),
            bookings = emptyList(),
            query = "pesquisavel",
            zoneId = ZoneId.of("UTC"),
            locale = Locale("pt", "BR"),
            nowMillis = 0L,
        ).single()

        assertEquals(risky.issues, matched.issues)
    }
}
