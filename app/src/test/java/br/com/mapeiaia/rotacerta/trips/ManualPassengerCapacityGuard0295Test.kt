package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualPassengerCapacityGuard0295Test {
    private fun trip() = Trip(
        id = "trip-295",
        title = "A → C",
        departureAtMillis = 1_800_000_000_000L,
        capacity = 4,
        status = TripStatus.PUBLISHED,
        stops = listOf(
            TripStop(id = "a", order = 0, name = "A"),
            TripStop(id = "b", order = 1, name = "B"),
            TripStop(id = "c", order = 2, name = "C"),
        ),
    )

    private fun externalEntry(rosterComplete: Boolean?) = TripTimelineEntry(
        tripId = "blablacar:295",
        profileId = "11111111-1111-4111-8111-111111111111",
        profileLabel = "Perfil externo",
        departureAtMillis = 1_800_000_000_000L,
        arrivalAtMillis = 1_800_003_600_000L,
        origin = "A",
        destination = "C",
        status = TripStatus.PUBLISHED,
        capacity = 4,
        minimumOccupiedSeats = 0,
        maximumOccupiedSeats = 0,
        sourcePassengerSeats = emptyMap(),
        blablaTripId = "publication-295",
        blablaTripHref = "https://www.blablacar.com.br/rides/offer/publication-295",
        blablaProfileUuid = "11111111-1111-4111-8111-111111111111",
        blablaPassengerRosterComplete = rosterComplete,
    )

    @Test
    fun fullFirstSegmentBlocksCrossingPassengerButLaterFreeSegmentRemainsBookable() {
        val trip = trip()
        val occupiedFirstSegment = Booking(
            id = "full-a-b",
            tripId = trip.id,
            passengerName = "Quatro lugares A-B",
            boardingStopId = "a",
            dropoffStopId = "b",
            seats = 4,
            status = BookingStatus.CONFIRMED,
            source = BookingSource.BLABLACAR,
        )

        val crossing = SeatAvailabilityEngine.availability(
            trip = trip,
            bookings = listOf(occupiedFirstSegment),
            boardingStopId = "a",
            dropoffStopId = "c",
            requestedSeats = 1,
        )
        val later = SeatAvailabilityEngine.availability(
            trip = trip,
            bookings = listOf(occupiedFirstSegment),
            boardingStopId = "b",
            dropoffStopId = "c",
            requestedSeats = 1,
        )

        assertFalse(crossing.canBook)
        assertTrue(later.canBook)
        assertThrows(IllegalArgumentException::class.java) {
            QuickPassengerEngine.build(
                trip = trip,
                existingBookings = listOf(occupiedFirstSegment),
                request = QuickPassengerRequest(
                    passengerName = "Não cabe",
                    boardingStopId = "a",
                    dropoffStopId = "c",
                    seats = 1,
                ),
            )
        }
        assertTrue(
            QuickPassengerEngine.build(
                trip = trip,
                existingBookings = listOf(occupiedFirstSegment),
                request = QuickPassengerRequest(
                    passengerName = "Cabe depois",
                    boardingStopId = "b",
                    dropoffStopId = "c",
                    seats = 1,
                ),
            ).passenger.seats == 1,
        )
    }

    @Test
    fun externalCardFailsClosedUntilPassengerRosterIsComplete() {
        assertFalse(timelineManualPassengerOccupancyKnown(externalEntry(false)))
        assertFalse(timelineManualPassengerOccupancyKnown(externalEntry(null)))
        assertTrue(timelineManualPassengerOccupancyKnown(externalEntry(true)))
    }

    @Test
    fun localTripDoesNotDependOnExternalRosterCompleteness() {
        val localOnly = externalEntry(false).copy(
            blablaProfileUuid = null,
            blablaTripId = null,
            blablaTripHref = null,
        )
        assertTrue(timelineManualPassengerOccupancyKnown(localOnly))
    }
}
