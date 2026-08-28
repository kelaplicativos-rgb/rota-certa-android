package br.com.mapeiaia.rotacerta.trips

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class BlaBlaNetworkSegments0319Test {
    private val profileUuid = "11111111-1111-4111-8111-111111111111"
    private val itinerary = listOf(
        "Santo André",
        "São Paulo",
        "Extrema",
        "Pouso Alegre",
        "Três Corações",
        "São Thomé das Letras",
    )

    @Test
    fun collectorProjectionKeepsObservedItineraryAndPublishedSeatSetting() {
        val source = BlaBlaCollectorTrip(
            profile_uuid = profileUuid,
            profile_name = "Perfil",
            date = "2030-10-04",
            departure_time = "10:40",
            actual_departure = "Santo André",
            actual_arrival = "São Thomé das Letras",
            trip_id = "trip-network-0319",
            uuid_validation = "verified_from_authenticated_profile_session",
            itinerary_stops = itinerary,
            booked_seats = 4,
            published_seats = 2,
            passenger_roster_complete = true,
        )
        val response = BlaBlaCollectorMonthResponse(
            status = "ok",
            month = "2030-10",
            trips = listOf(source),
        )

        val entry = BlaBlaTimelineAdapter.merge(
            localEntries = emptyList(),
            response = response,
            zoneId = ZoneId.of("America/Sao_Paulo"),
        ).single()

        assertEquals(itinerary, entry.blablaItineraryStops)
        assertEquals(2, entry.blablaPublishedSeats)
        assertEquals(4, entry.sourcePassengerSeats[BookingSource.BLABLACAR])
    }

    @Test
    fun observedItineraryDrivesPhysicalLoadsAcrossStopsWithoutPassengers() {
        val entry = externalEntry(
            passengers = listOf(
                BlaBlaCollectorPassenger(name = "A", seats = 1, boarding = "Santo André", dropoff = "Pouso Alegre"),
                BlaBlaCollectorPassenger(name = "B", seats = 2, boarding = "São Paulo", dropoff = "Três Corações"),
                BlaBlaCollectorPassenger(name = "C", seats = 1, boarding = "Três Corações", dropoff = "São Thomé das Letras"),
            ),
            sourceSeats = 4,
        )

        val backingTrip = buildTimelineExternalBackingTrip(entry, 4)
        assertEquals(itinerary, backingTrip.stops.sortedBy(TripStop::order).map(TripStop::name))

        val claims = planTimelineExternalCapacityClaims(entry, backingTrip, emptyList())
        val loads = SeatAvailabilityEngine.segmentLoads(backingTrip, claims)

        assertEquals(listOf(1, 3, 3, 2, 1), loads.map(SegmentLoad::occupiedSeats))
        assertEquals(listOf(3, 1, 1, 2, 3), loads.map(SegmentLoad::availableSeats))
    }

    @Test
    fun unrelatedObservedItineraryNeverOverridesExactCardEndpoints() {
        val entry = externalEntry(
            passengers = emptyList(),
            sourceSeats = 0,
            observedItinerary = listOf("Rio de Janeiro", "Belo Horizonte"),
        )

        assertEquals(
            listOf("Santo André", "São Thomé das Letras"),
            timelineExternalRoutePointLabels(entry),
        )
    }

    private fun externalEntry(
        passengers: List<BlaBlaCollectorPassenger>,
        sourceSeats: Int,
        observedItinerary: List<String> = itinerary,
    ) = TripTimelineEntry(
        tripId = "blablacar:test-0319",
        profileId = profileUuid,
        profileLabel = "Perfil",
        departureAtMillis = 1L,
        arrivalAtMillis = null,
        origin = "Santo André",
        destination = "São Thomé das Letras",
        status = TripStatus.PUBLISHED,
        capacity = 4,
        minimumOccupiedSeats = sourceSeats,
        maximumOccupiedSeats = sourceSeats,
        sourcePassengerSeats = if (sourceSeats > 0) mapOf(BookingSource.BLABLACAR to sourceSeats) else emptyMap(),
        blablaTripId = "trip-network-0319",
        blablaTripHref = "https://www.blablacar.com.br/rides/offer/trip-network-0319",
        blablaProfileUuid = profileUuid,
        blablaItineraryStops = observedItinerary,
        blablaPassengers = passengers,
        blablaPassengerRosterComplete = true,
    )
}
