package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripFlexibleCapacityStage47R2Test {
    @Test
    fun fortySeatVehicleSupportsLargeReservationsWithoutBreakingSegmentReuse() {
        val stops = listOf(
            TripStop(id = "a", order = 0, name = "A"),
            TripStop(id = "b", order = 1, name = "B"),
            TripStop(id = "c", order = 2, name = "C"),
        )
        val trip = Trip(
            id = "bus-40",
            title = "A → C",
            departureAtMillis = 1_800_000_000_000L,
            capacity = 40,
            status = TripStatus.PUBLISHED,
            stops = stops,
        )
        val bookings = listOf(
            Booking(
                id = "group-20",
                tripId = trip.id,
                passengerName = "Grupo 20",
                boardingStopId = "a",
                dropoffStopId = "b",
                seats = 20,
                status = BookingStatus.CONFIRMED,
            ),
            Booking(
                id = "group-30",
                tripId = trip.id,
                passengerName = "Grupo 30",
                boardingStopId = "b",
                dropoffStopId = "c",
                seats = 30,
                status = BookingStatus.CONFIRMED,
            ),
        )

        val firstSegment = SeatAvailabilityEngine.availability(trip, bookings, "a", "b", requestedSeats = 20)
        assertTrue(firstSegment.canBook)
        assertEquals(20, firstSegment.availableSeats)

        val secondSegment = SeatAvailabilityEngine.availability(trip, bookings, "b", "c", requestedSeats = 10)
        assertTrue(secondSegment.canBook)
        assertEquals(10, secondSegment.availableSeats)

        val wholeRoute = SeatAvailabilityEngine.availability(trip, bookings, "a", "c", requestedSeats = 10)
        assertTrue(wholeRoute.canBook)
        assertEquals(10, wholeRoute.availableSeats)
        assertEquals(listOf(20, 30), wholeRoute.segmentLoads.map { it.occupiedSeats })
    }
}
