package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Test

class TripUnifiedOccupancyStage47R4Test {
    private val trip = Trip(
        id = "trip",
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

    @Test
    fun privatePassengerAndMatchingBlablacarReservedSeatCountOnlyOnce() {
        val bookings = listOf(
            Booking(
                id = "blabla-two",
                tripId = trip.id,
                passengerName = "BlaBlaCar 2",
                boardingStopId = "a",
                dropoffStopId = "c",
                seats = 2,
                status = BookingStatus.CONFIRMED,
                source = BookingSource.BLABLACAR,
                sourceReference = "trip-123",
            ),
            Booking(
                id = "private-one",
                tripId = trip.id,
                passengerName = "Passageiro particular",
                boardingStopId = "a",
                dropoffStopId = "c",
                seats = 1,
                status = BookingStatus.CONFIRMED,
                source = BookingSource.PRIVATE,
                occupancyGroupId = "same-physical-seat",
            ),
            Booking(
                id = "blabla-reserved-for-private",
                tripId = trip.id,
                passengerName = "Vaga reservada na BlaBlaCar",
                boardingStopId = "a",
                dropoffStopId = "c",
                seats = 1,
                status = BookingStatus.CONFIRMED,
                source = BookingSource.BLABLACAR,
                capacityClaimType = CapacityClaimType.RESERVED_SEAT,
                occupancyGroupId = "same-physical-seat",
            ),
        )

        val loads = SeatAvailabilityEngine.segmentLoads(trip, bookings)

        assertEquals(listOf(3, 3), loads.map { it.occupiedSeats })
        assertEquals(listOf(1, 1), loads.map { it.availableSeats })
        assertEquals(1, SeatAvailabilityEngine.remainingSeatsForWholeTrip(trip, bookings))
    }

    @Test
    fun unlinkedSourcesAreIndependentPhysicalSeats() {
        val bookings = listOf(
            Booking(
                id = "blabla",
                tripId = trip.id,
                passengerName = "BlaBlaCar",
                boardingStopId = "a",
                dropoffStopId = "c",
                seats = 2,
                status = BookingStatus.CONFIRMED,
                source = BookingSource.BLABLACAR,
            ),
            Booking(
                id = "private",
                tripId = trip.id,
                passengerName = "Particular",
                boardingStopId = "a",
                dropoffStopId = "c",
                seats = 1,
                status = BookingStatus.CONFIRMED,
                source = BookingSource.PRIVATE,
            ),
        )

        assertEquals(1, SeatAvailabilityEngine.remainingSeatsForWholeTrip(trip, bookings))
    }

    @Test
    fun linkedClaimsUseLargestSeatCountInsteadOfSummingDuplicates() {
        val bookings = listOf(
            Booking(
                id = "private-group",
                tripId = trip.id,
                passengerName = "Grupo particular",
                boardingStopId = "a",
                dropoffStopId = "c",
                seats = 2,
                status = BookingStatus.CONFIRMED,
                source = BookingSource.PRIVATE,
                occupancyGroupId = "group-x",
            ),
            Booking(
                id = "platform-mirror",
                tripId = trip.id,
                passengerName = "Espelho plataforma",
                boardingStopId = "a",
                dropoffStopId = "c",
                seats = 1,
                status = BookingStatus.CONFIRMED,
                source = BookingSource.BLABLACAR,
                capacityClaimType = CapacityClaimType.RESERVED_SEAT,
                occupancyGroupId = "group-x",
            ),
        )

        assertEquals(2, SeatAvailabilityEngine.remainingSeatsForWholeTrip(trip, bookings))
    }

    @Test
    fun legacyBookingDefaultsRemainCompatible() {
        val booking = Booking(
            id = "legacy",
            tripId = trip.id,
            passengerName = "Legado",
            boardingStopId = "a",
            dropoffStopId = "b",
            status = BookingStatus.CONFIRMED,
        )

        assertEquals(BookingSource.OTHER, booking.source)
        assertEquals(CapacityClaimType.PASSENGER, booking.capacityClaimType)
        assertEquals(null, booking.occupancyGroupId)
    }
}
