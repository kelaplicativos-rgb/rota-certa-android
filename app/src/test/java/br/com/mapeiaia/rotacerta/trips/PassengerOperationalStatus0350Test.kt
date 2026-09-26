package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Test

class PassengerOperationalStatus0350Test {
    private val trip = Trip(
        id = "trip-0350",
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

    private val confirmed = Booking(
        id = "booking-0350",
        tripId = trip.id,
        passengerName = "Passageiro",
        boardingStopId = "a",
        dropoffStopId = "c",
        seats = 2,
        status = BookingStatus.CONFIRMED,
        source = BookingSource.ROTA_CERTA,
    )

    @Test
    fun operationalAndPaymentChangesDoNotAlterSegmentCapacity() {
        val inCarPaid = confirmed.copy(
            operationalStatus = PassengerOperationalStatus.IN_CAR,
            paymentStatus = PassengerPaymentStatus.PAID,
            lastDriverSelection = "PAID",
        )
        val completed = inCarPaid.copy(
            operationalStatus = PassengerOperationalStatus.COMPLETED,
            lastDriverSelection = "COMPLETED",
        )

        assertEquals(listOf(2, 2), SeatAvailabilityEngine.segmentLoads(trip, listOf(confirmed)).map { it.occupiedSeats })
        assertEquals(listOf(2, 2), SeatAvailabilityEngine.segmentLoads(trip, listOf(inCarPaid)).map { it.occupiedSeats })
        assertEquals(listOf(2, 2), SeatAvailabilityEngine.segmentLoads(trip, listOf(completed)).map { it.occupiedSeats })
    }

    @Test
    fun paidKeepsJourneyPhaseIndependent() {
        val boarded = confirmed.copy(operationalStatus = PassengerOperationalStatus.IN_CAR)
        val paid = boarded.copy(
            paymentStatus = PassengerPaymentStatus.PAID,
            lastDriverSelection = "PAID",
        )

        assertEquals(PassengerOperationalStatus.IN_CAR, paid.operationalStatus)
        assertEquals(PassengerPaymentStatus.PAID, paid.paymentStatus)
    }

    @Test
    fun cancellationReleasesCapacityOnlyThroughBookingStatus() {
        val operationalOnly = confirmed.copy(operationalStatus = PassengerOperationalStatus.CANCELLED)
        val cancelled = operationalOnly.copy(status = BookingStatus.CANCELLED)

        assertEquals(listOf(2, 2), SeatAvailabilityEngine.segmentLoads(trip, listOf(operationalOnly)).map { it.occupiedSeats })
        assertEquals(listOf(0, 0), SeatAvailabilityEngine.segmentLoads(trip, listOf(cancelled)).map { it.occupiedSeats })
    }
    @Test
    fun cancellationOfOneSeatReturnsExactlyOneSeat() {
        val oneSeat = confirmed.copy(seats = 1)
        val cancelled = oneSeat.copy(
            status = BookingStatus.CANCELLED,
            operationalStatus = PassengerOperationalStatus.CANCELLED,
            lastDriverSelection = "CANCELLED",
        )

        assertEquals(listOf(1, 1), SeatAvailabilityEngine.segmentLoads(trip, listOf(oneSeat)).map { it.occupiedSeats })
        assertEquals(listOf(0, 0), SeatAvailabilityEngine.segmentLoads(trip, listOf(cancelled)).map { it.occupiedSeats })
    }

    @Test
    fun cancellationReleasesOnlyTheBookedSegmentsForMultiSeatReservation() {
        val segmented = trip.copy(
            stops = listOf(
                TripStop(id = "a", order = 0, name = "A"),
                TripStop(id = "b", order = 1, name = "B"),
                TripStop(id = "c", order = 2, name = "C"),
                TripStop(id = "d", order = 3, name = "D"),
            ),
        )
        val booking = confirmed.copy(
            tripId = segmented.id,
            boardingStopId = "b",
            dropoffStopId = "d",
            seats = 2,
        )
        val before = SeatAvailabilityEngine.segmentLoads(segmented, listOf(booking)).map { it.occupiedSeats }
        val after = SeatAvailabilityEngine.segmentLoads(
            segmented,
            listOf(
                booking.copy(
                    status = BookingStatus.CANCELLED,
                    operationalStatus = PassengerOperationalStatus.CANCELLED,
                    lastDriverSelection = "CANCELLED",
                ),
            ),
        ).map { it.occupiedSeats }

        assertEquals(listOf(0, 2, 2), before)
        assertEquals(listOf(0, 0, 0), after)
    }

    @Test
    fun exactExternalCancellationTombstoneRemovesOnlyThatOccurrenceAndItsSeats() {
        val cancelledPassenger = BlaBlaCollectorPassenger(
            name = "Cancelado",
            seats = 2,
            boarding = "B",
            dropoff = "D",
            booking_href = "/rides/booking/cancelled",
        )
        val activePassenger = BlaBlaCollectorPassenger(
            name = "Ativo",
            seats = 1,
            boarding = "A",
            dropoff = "D",
            booking_href = "/rides/booking/active",
        )
        val source = BlaBlaCollectorMonthResponse(
            status = "ok",
            trips = listOf(
                BlaBlaCollectorTrip(
                    profile_uuid = "driver-profile",
                    date = "2030-09-10",
                    departure_time = "11:00",
                    search_from = "A",
                    search_to = "D",
                    availability = "full",
                    passengers = listOf(cancelledPassenger, activePassenger),
                    booked_seats = 3,
                    passenger_roster_complete = true,
                ),
            ),
        )
        val cancelledKey = externalPassengerReservationKey(
            "driver-profile",
            cancelledPassenger.booking_href,
        )!!

        val filtered = applyInternalCancellationTombstones(source, setOf(cancelledKey))!!
        val tripAfter = filtered.trips.single()

        assertEquals(listOf("Ativo"), tripAfter.passengers.map(BlaBlaCollectorPassenger::name))
        assertEquals(1, tripAfter.booked_seats)
        assertEquals("internal_cancelled", tripAfter.availability)
    }

}
