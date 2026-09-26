package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReservationApproval0356Test {
    private fun trip() = Trip(
        title = "A → C",
        departureAtMillis = System.currentTimeMillis() + 3_600_000,
        capacity = 4,
        status = TripStatus.PUBLISHED,
        stops = listOf(
            TripStop(id = "a", order = 0, name = "A"),
            TripStop(id = "b", order = 1, name = "B"),
            TripStop(id = "c", order = 2, name = "C"),
        ),
    )

    @Test
    fun requestedProtectsCapacityAndApprovalPromotesSameClaimWithoutDoubleCharge() {
        val trip = trip()
        val requested = Booking(
            id = "booking-1",
            tripId = trip.id,
            passengerName = "Maria",
            passengerId = "passenger-1",
            boardingStopId = "a",
            dropoffStopId = "c",
            seats = 2,
            status = BookingStatus.REQUESTED,
            source = BookingSource.ROTA_CERTA,
            capacityClaimType = CapacityClaimType.PASSENGER,
            occupancyGroupId = "booking-1",
        )
        val before = SeatAvailabilityEngine.segmentLoads(trip, listOf(requested)).map(SegmentLoad::occupiedSeats)
        val after = SeatAvailabilityEngine.segmentLoads(trip, listOf(requested.copy(status = BookingStatus.CONFIRMED))).map(SegmentLoad::occupiedSeats)
        assertEquals(listOf(2, 2), before)
        assertEquals(before, after)
        assertEquals(2, SeatAvailabilityEngine.remainingSeatsForWholeTrip(trip, listOf(requested)))
    }

    @Test
    fun rejectedReleasesOnlyTheClaimedSegmentAndIsNotCancelled() {
        val trip = trip()
        val requested = Booking(
            id = "booking-2",
            tripId = trip.id,
            passengerName = "João",
            passengerId = "passenger-2",
            boardingStopId = "b",
            dropoffStopId = "c",
            seats = 3,
            status = BookingStatus.REQUESTED,
            source = BookingSource.ROTA_CERTA,
            capacityClaimType = CapacityClaimType.PASSENGER,
            occupancyGroupId = "booking-2",
        )
        val pending = SeatAvailabilityEngine.segmentLoads(trip, listOf(requested)).map(SegmentLoad::occupiedSeats)
        val rejected = SeatAvailabilityEngine.segmentLoads(trip, listOf(requested.copy(status = BookingStatus.REJECTED))).map(SegmentLoad::occupiedSeats)
        assertEquals(listOf(0, 3), pending)
        assertEquals(listOf(0, 0), rejected)
        assertNotEquals(BookingStatus.REJECTED, BookingStatus.CANCELLED)
    }

    @Test
    fun twoPendingRequestsCannotFitIntoTheSameLastSeats() {
        val trip = trip().copy(capacity = 2)
        val first = Booking(
            id = "booking-a",
            tripId = trip.id,
            passengerName = "A",
            passengerId = "passenger-a",
            boardingStopId = "a",
            dropoffStopId = "c",
            seats = 2,
            status = BookingStatus.REQUESTED,
            source = BookingSource.ROTA_CERTA,
            occupancyGroupId = "booking-a",
        )
        val availability = SeatAvailabilityEngine.availability(
            trip = trip,
            bookings = listOf(first),
            boardingStopId = "a",
            dropoffStopId = "c",
            requestedSeats = 1,
        )
        assertEquals(0, availability.availableSeats)
        assertEquals(false, availability.canBook)
    }
}
