package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripDomainStage47Test {
    private fun trip(capacity: Int = 1): Trip {
        val stops = listOf(
            TripStop(id = "a", order = 0, name = "A"),
            TripStop(id = "b", order = 1, name = "B"),
            TripStop(id = "c", order = 2, name = "C"),
            TripStop(id = "d", order = 3, name = "D"),
        )
        return Trip(id = "trip", title = "A → D", departureAtMillis = 1_800_000_000_000L, capacity = capacity, status = TripStatus.PUBLISHED, stops = stops)
    }

    private fun booking(id: String, from: String, to: String, seats: Int = 1, status: BookingStatus = BookingStatus.CONFIRMED, expiry: Long? = null) = Booking(
        id = id,
        tripId = "trip",
        passengerName = id,
        boardingStopId = from,
        dropoffStopId = to,
        seats = seats,
        status = status,
        holdExpiresAtMillis = expiry,
    )

    @Test
    fun sameSeatCanBeReusedAfterPassengerLeaves() {
        val trip = trip(capacity = 1)
        val bookings = listOf(booking("one", "a", "b"))
        val bToD = SeatAvailabilityEngine.availability(trip, bookings, "b", "d")
        assertTrue(bToD.canBook)
        assertEquals(1, bToD.availableSeats)
    }

    @Test
    fun overlapCannotOverbookAnyIntermediateSegment() {
        val trip = trip(capacity = 2)
        val bookings = listOf(
            booking("one", "a", "c"),
            booking("two", "b", "d"),
        )
        val request = SeatAvailabilityEngine.availability(trip, bookings, "a", "d")
        assertEquals(0, request.availableSeats)
        assertFalse(request.canBook)
        assertEquals(listOf(1, 2, 1), request.segmentLoads.map { it.occupiedSeats })
    }

    @Test
    fun expiredHoldDoesNotConsumeCapacity() {
        val trip = trip(capacity = 1)
        val bookings = listOf(booking("held", "a", "d", status = BookingStatus.HELD, expiry = 1_000L))
        val request = SeatAvailabilityEngine.availability(trip, bookings, "a", "d", nowMillis = 2_000L)
        assertTrue(request.canBook)
        assertEquals(1, request.availableSeats)
    }

    @Test
    fun liveHoldConsumesCapacityUntilExpiry() {
        val trip = trip(capacity = 1)
        val bookings = listOf(booking("held", "a", "d", status = BookingStatus.HELD, expiry = 3_000L))
        val request = SeatAvailabilityEngine.availability(trip, bookings, "a", "d", nowMillis = 2_000L)
        assertFalse(request.canBook)
        assertEquals(0, request.availableSeats)
    }

    @Test(expected = IllegalArgumentException::class)
    fun dropoffBeforeBoardingIsRejected() {
        SeatAvailabilityEngine.availability(trip(), emptyList(), "c", "b")
    }

    @Test
    fun requestedAndCancelledBookingsDoNotConsumeSeats() {
        val trip = trip(capacity = 1)
        val bookings = listOf(
            booking("requested", "a", "d", status = BookingStatus.REQUESTED),
            booking("cancelled", "a", "d", status = BookingStatus.CANCELLED),
        )
        assertEquals(1, SeatAvailabilityEngine.remainingSeatsForWholeTrip(trip, bookings))
    }

    @Test
    fun tripIsOnlyGloballyFullWhenEverySegmentHasNoSeat() {
        val trip = trip(capacity = 1)
        val firstSegmentOnly = listOf(booking("first", "a", "b"))
        assertEquals(TripStatus.PUBLISHED, SeatAvailabilityEngine.suggestedStatus(trip, firstSegmentOnly))
        val all = listOf(booking("all", "a", "d"))
        assertEquals(TripStatus.FULL, SeatAvailabilityEngine.suggestedStatus(trip, all))
    }
}
