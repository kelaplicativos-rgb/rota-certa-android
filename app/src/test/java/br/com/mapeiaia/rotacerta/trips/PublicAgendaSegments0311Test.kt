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
        assertEquals(listOf(1, 3, 1), loads.map(SegmentLoad::occupiedSeats))
        assertEquals(listOf(3, 1, 3), loads.map(SegmentLoad::availableSeats))
    }

    @Test
    fun fullTripWithoutRosterUsesObservedFullStateWithoutInventingStops() {
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
        assertEquals(4, published.bookedSeats)
        assertEquals(1, published.capacityClaims.size)
        assertEquals(4, published.capacityClaims.single().seats)
    }
}
