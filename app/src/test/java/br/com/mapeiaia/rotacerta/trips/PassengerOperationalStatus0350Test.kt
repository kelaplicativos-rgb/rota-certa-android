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
}
