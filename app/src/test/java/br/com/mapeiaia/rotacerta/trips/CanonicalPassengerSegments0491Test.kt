package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanonicalPassengerSegments0491Test {
    private val stops = listOf(
        TripStop(id = "A", order = 0, name = "A"),
        TripStop(id = "B", order = 1, name = "B"),
        TripStop(id = "C", order = 2, name = "C"),
        TripStop(id = "D", order = 3, name = "D"),
    )

    private fun trip(origin: TripRecordOrigin = TripRecordOrigin.LOCAL) = Trip(
        id = "trip-generic",
        title = "A → D",
        departureAtMillis = 2_000_000_000_000L,
        capacity = 4,
        status = TripStatus.PUBLISHED,
        stops = stops,
        recordOrigin = origin,
        blablaProfileUuid = if (origin == TripRecordOrigin.EXTERNAL_BACKING) "provider-profile" else null,
        blablaTripId = if (origin == TripRecordOrigin.EXTERNAL_BACKING) "provider-trip" else null,
    )

    private fun basePassenger(tripId: String) = Booking(
        id = "base-passengers",
        tripId = tripId,
        passengerId = "passenger-base",
        passengerName = "Passageiros existentes",
        boardingStopId = "A",
        dropoffStopId = "D",
        seats = 2,
        status = BookingStatus.CONFIRMED,
        source = BookingSource.OTHER,
        capacityClaimType = CapacityClaimType.PASSENGER,
    )

    private fun available(trip: Trip, bookings: List<Booking>): List<Int> =
        SeatAvailabilityEngine.segmentLoads(trip, bookings).map(SegmentLoad::availableSeats)

    @Test
    fun manualPassengerConsumesOnlyTravelledSegmentsAndCancellationRestoresThem() {
        val trip = trip()
        val base = basePassenger(trip.id)
        assertEquals(listOf(2, 2, 2), available(trip, listOf(base)))

        val added = QuickPassengerEngine.build(
            trip = trip,
            existingBookings = listOf(base),
            request = QuickPassengerRequest(
                passengerName = "Passageiro adicional",
                passengerId = "passenger-extra",
                boardingStopId = "B",
                dropoffStopId = "D",
                seats = 1,
                source = BookingSource.PRIVATE,
            ),
            idFactory = { "booking-extra" },
        ).passenger
        assertEquals(listOf(2, 1, 1), available(trip, listOf(base, added)))

        val cancelled = added.copy(
            status = BookingStatus.CANCELLED,
            operationalStatus = PassengerOperationalStatus.CANCELLED,
        )
        assertEquals(listOf(2, 2, 2), available(trip, listOf(base, cancelled)))
    }

    @Test
    fun changingBoardingFromBToCMovesOnlyTheCanonicalSegmentLoad() {
        val trip = trip()
        val base = basePassenger(trip.id)
        val added = QuickPassengerEngine.build(
            trip,
            listOf(base),
            QuickPassengerRequest(
                passengerName = "Passageiro adicional",
                passengerId = "passenger-extra",
                boardingStopId = "B",
                dropoffStopId = "D",
                source = BookingSource.PRIVATE,
            ),
            idFactory = { "booking-extra" },
        ).passenger
        val changed = QuickPassengerEngine.updateManualBooking(
            trip = trip,
            existingBookings = listOf(base, added),
            booking = added,
            boardingStopId = "C",
            dropoffStopId = "D",
            seats = 1,
        )
        assertEquals(listOf(2, 2, 1), available(trip, listOf(base, changed)))
    }


    @Test
    fun fiveStopOverlapProofIsContinuousAndIdempotent() {
        val proofStops = listOf(
            TripStop(id = "0", order = 0, name = "A"),
            TripStop(id = "1", order = 1, name = "B"),
            TripStop(id = "2", order = 2, name = "C"),
            TripStop(id = "3", order = 3, name = "D"),
            TripStop(id = "4", order = 4, name = "E"),
        )
        val proofTrip = Trip(
            id = "proof-trip",
            title = "A → E",
            departureAtMillis = 2_000_000_000_000L,
            capacity = 4,
            status = TripStatus.PUBLISHED,
            stops = proofStops,
        )
        val bookings = listOf(
            Booking(id = "r1", tripId = proofTrip.id, passengerId = "r1", passengerName = "R1", boardingStopId = "0", dropoffStopId = "2", seats = 3, status = BookingStatus.CONFIRMED),
            Booking(id = "r2", tripId = proofTrip.id, passengerId = "r2", passengerName = "R2", boardingStopId = "1", dropoffStopId = "4", seats = 1, status = BookingStatus.CONFIRMED),
            Booking(id = "r3", tripId = proofTrip.id, passengerId = "r3", passengerName = "R3", boardingStopId = "2", dropoffStopId = "4", seats = 1, status = BookingStatus.CONFIRMED),
        )

        val first = SeatAvailabilityEngine.segmentLoads(proofTrip, bookings)
        val replay = SeatAvailabilityEngine.segmentLoads(proofTrip, bookings)
        assertEquals(listOf(3, 4, 2, 2), first.map(SegmentLoad::occupiedSeats))
        assertEquals(listOf(1, 0, 2, 2), first.map(SegmentLoad::availableSeats))
        assertEquals(first, replay)
    }

    @Test
    fun manualAndExternalTripsUseTheSamePassengerBookingIdentityWithoutCreatingAnotherTrip() {
        val local = trip()
        assertTrue(local.isCanonicalLocalPublishSource())
        assertFalse(local.blablaTripId?.isNotBlank() == true)

        val external = trip(TripRecordOrigin.EXTERNAL_BACKING)
        val added = QuickPassengerEngine.build(
            external,
            listOf(basePassenger(external.id)),
            QuickPassengerRequest(
                passengerName = "Passageiro adicional",
                passengerId = "passenger-extra",
                boardingStopId = "B",
                dropoffStopId = "D",
                source = BookingSource.PRIVATE,
            ),
            idFactory = { "booking-extra" },
        ).passenger
        assertEquals(external.id, added.tripId)
        assertEquals("passenger-extra", added.passengerId)
        assertEquals(BookingSource.PRIVATE, added.source)
    }
}
