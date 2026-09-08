package br.com.mapeiaia.rotacerta.trips

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PublicAgendaSegments0311Test {
    private val zone = ZoneId.of("America/Sao_Paulo")

    @Test
    fun observedItineraryBecomesPublicStopsWithoutInventingSegmentPrices() {
        val source = BlaBlaCollectorTrip(
            profile_uuid = "profile-test",
            date = "2030-10-04",
            departure_time = "10:40",
            arrival_time = "16:10",
            actual_departure = "Santo André, SP",
            actual_arrival = "São Thomé das Letras, MG",
            price = "R$ 120,00",
            trip_id = "bb-real-trip",
            itinerary_stops = listOf(
                "Santo André, SP",
                "Pouso Alegre, MG",
                "Camanducaia, MG",
                "São Thomé das Letras, MG",
            ),
            passengers = listOf(
                BlaBlaCollectorPassenger(
                    name = "Passageiro",
                    seats = 2,
                    boarding = "Pouso Alegre, MG",
                    dropoff = "Camanducaia, MG",
                ),
            ),
            booked_seats = 3,
            passenger_roster_complete = true,
        )

        val published = PublicAgendaAutoSync0300.toPublicTrip(source, 4, 0L, zone)
        assertNotNull(published)
        assertEquals(
            listOf("Santo André", "Pouso Alegre", "Camanducaia", "São Thomé das Letras"),
            published.trip.stops.sortedBy(TripStop::order).map(TripStop::name),
        )
        assertTrue(published.trip.stops.all { it.priceToNextCents == 0L })
        assertEquals(3, published.bookedSeats)

        val loads = SeatAvailabilityEngine.segmentLoads(published.trip, published.capacityClaims)
        assertEquals(listOf(1, 3, 1), loads.map(SegmentLoad::passengerSeats))
        assertEquals(listOf(0, 0, 0), loads.map(SegmentLoad::blockedSeats))
        assertEquals(listOf(3, 1, 3), loads.map(SegmentLoad::availableSeats))
        assertTrue(published.capacityClaims.all { it.capacityClaimType == CapacityClaimType.EXTERNAL_OCCUPANCY })
    }


    @Test
    fun overlappingMultiSeatPassengersKeepContinuityAcrossIntermediateStop() {
        val source = BlaBlaCollectorTrip(
            profile_uuid = "profile-overlap",
            date = "2030-10-06",
            departure_time = "11:00",
            actual_departure = "A",
            actual_arrival = "E",
            trip_id = "trip-overlap",
            itinerary_stops = listOf("A", "B", "C", "D", "E"),
            itinerary_authoritative = true,
            published_seats = 4,
            booked_seats = 5,
            passenger_roster_complete = true,
            passengers = listOf(
                BlaBlaCollectorPassenger(name = "P1", seats = 3, boarding = "A", dropoff = "C"),
                BlaBlaCollectorPassenger(name = "P2", seats = 1, boarding = "B", dropoff = "E"),
                BlaBlaCollectorPassenger(name = "P3", seats = 1, boarding = "C", dropoff = "E"),
            ),
        )

        val projected = PublicAgendaAutoSync0300.toPublicTrip(source, 4, 0L, zone)
        assertNotNull(projected)
        assertTrue(projected.sourceComplete)
        assertEquals(listOf("A", "B", "C", "D", "E"), projected.trip.stops.map(TripStop::name))

        val loads = SeatAvailabilityEngine.segmentLoads(projected.trip, projected.capacityClaims)
        assertEquals(listOf(3, 4, 2, 2), loads.map(SegmentLoad::occupiedSeats))
        assertEquals(listOf(1, 0, 2, 2), loads.map(SegmentLoad::availableSeats))
    }

    @Test
    fun nonConsecutiveRepeatedStopIsNotCompactedOutOfCanonicalTopology() {
        assertEquals(
            listOf("A", "B", "C", "B", "D"),
            PublicAgendaAutoSync0300.buildObservedStopLabels(
                origin = "A",
                destination = "D",
                itineraryStops = listOf("A", "B", "C", "B", "D"),
            ),
        )
    }

    @Test
    fun ambiguousPassengerEndpointFailsClosedInsteadOfChoosingFirstMatchingStop() {
        val source = BlaBlaCollectorTrip(
            profile_uuid = "profile-ambiguous",
            date = "2030-10-07",
            departure_time = "11:00",
            actual_departure = "A",
            actual_arrival = "D",
            trip_id = "trip-ambiguous",
            itinerary_stops = listOf("A", "Centro, Cidade X", "Terminal, Cidade X", "D"),
            published_seats = 4,
            booked_seats = 1,
            passenger_roster_complete = true,
            passengers = listOf(
                BlaBlaCollectorPassenger(name = "P", seats = 1, boarding = "Cidade X", dropoff = "D"),
            ),
        )
        val projected = PublicAgendaAutoSync0300.toPublicTrip(source, 4, 0L, zone)
        assertNotNull(projected)
        assertEquals(false, projected.sourceComplete)
    }

    @Test
    fun blablaFullFlagWithoutRosterDoesNotInventOccupiedAgendaSeats() {
        val source = BlaBlaCollectorTrip(
            profile_uuid = "profile-test",
            date = "2030-10-05",
            departure_time = "11:00",
            actual_departure = "Santo André",
            actual_arrival = "São Thomé das Letras",
            availability = "full",
            itinerary_stops = emptyList(),
        )

        val published = PublicAgendaAutoSync0300.toPublicTrip(source, 4, 0L, zone)
        assertNotNull(published)
        assertEquals(2, published.trip.stops.size)
        assertEquals(TripStatus.PUBLISHED, published.trip.status)
        assertEquals(0, published.bookedSeats)
        assertTrue(published.capacityClaims.isEmpty())
    }
}
