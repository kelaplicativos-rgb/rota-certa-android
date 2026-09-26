package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TripQuickPassengerStage47R4Step4Test {
    private fun trip(capacity: Int = 4) = Trip(
        id = "trip",
        title = "A → C",
        departureAtMillis = 1_800_000_000_000L,
        capacity = capacity,
        status = TripStatus.PUBLISHED,
        stops = listOf(
            TripStop(id = "a", order = 0, name = "A"),
            TripStop(id = "b", order = 1, name = "B"),
            TripStop(id = "c", order = 2, name = "C"),
        ),
    )

    @Test
    fun privatePassengerWithBlablacarMirrorUsesOnePhysicalSeat() {
        val t = trip()
        val existing = listOf(
            Booking(
                id = "bb-two",
                tripId = t.id,
                passengerName = "BlaBlaCar 2",
                boardingStopId = "a",
                dropoffStopId = "c",
                seats = 2,
                status = BookingStatus.CONFIRMED,
                source = BookingSource.BLABLACAR,
            ),
        )
        var counter = 0
        val plan = QuickPassengerEngine.build(
            t,
            existing,
            QuickPassengerRequest(
                passengerName = "Particular",
                boardingStopId = "a",
                dropoffStopId = "c",
                seats = 1,
                source = BookingSource.PRIVATE,
                mirrorSource = BookingSource.BLABLACAR,
            ),
            idFactory = { "id-${counter++}" },
        )

        assertNotNull(plan.mirror)
        assertEquals(plan.passenger.occupancyGroupId, plan.mirror?.occupancyGroupId)
        val loads = SeatAvailabilityEngine.segmentLoads(t, existing + plan.writes())
        assertEquals(listOf(3, 3), loads.map { it.occupiedSeats })
        assertEquals(1, SeatAvailabilityEngine.remainingSeatsForWholeTrip(t, existing + plan.writes()))
    }

    @Test
    fun alreadyReservedFullSeatCanBeNamedWithoutConsumingAnotherSeat() {
        val t = trip(capacity = 1)
        val reserved = Booking(
            id = "held-seat",
            tripId = t.id,
            passengerName = "Vaga bloqueada BlaBlaCar",
            boardingStopId = "a",
            dropoffStopId = "c",
            seats = 1,
            status = BookingStatus.CONFIRMED,
            source = BookingSource.BLABLACAR,
            capacityClaimType = CapacityClaimType.RESERVED_SEAT,
        )
        var counter = 0
        val plan = QuickPassengerEngine.build(
            t,
            listOf(reserved),
            QuickPassengerRequest(
                passengerName = "Passageiro particular",
                boardingStopId = "a",
                dropoffStopId = "c",
                source = BookingSource.PRIVATE,
                linkReservedSeatBookingId = reserved.id,
            ),
            idFactory = { "linked-${counter++}" },
        )
        val updated = plan.linkedReservedSeatUpdate
        assertNotNull(updated)
        assertEquals(plan.passenger.occupancyGroupId, updated?.occupancyGroupId)
        val projected = listOf(reserved).filterNot { it.id == updated?.id } + plan.writes()
        assertEquals(0, SeatAvailabilityEngine.remainingSeatsForWholeTrip(t, projected))
        assertEquals(listOf(1, 1), SeatAvailabilityEngine.segmentLoads(t, projected).map { it.occupiedSeats })
    }

    @Test
    fun fullTripRejectsNewUnlinkedPassenger() {
        val t = trip(capacity = 1)
        val existing = listOf(
            Booking(
                id = "existing",
                tripId = t.id,
                passengerName = "Já ocupa",
                boardingStopId = "a",
                dropoffStopId = "c",
                seats = 1,
                status = BookingStatus.CONFIRMED,
                source = BookingSource.BLABLACAR,
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            QuickPassengerEngine.build(
                t,
                existing,
                QuickPassengerRequest(
                    passengerName = "Novo",
                    boardingStopId = "a",
                    dropoffStopId = "c",
                    source = BookingSource.PRIVATE,
                ),
            )
        }
    }

    @Test
    fun onlyActiveReservedSeatsAreOfferedForLinking() {
        val t = trip()
        val active = Booking(
            id = "active",
            tripId = t.id,
            passengerName = "Ativa",
            boardingStopId = "a",
            dropoffStopId = "c",
            status = BookingStatus.CONFIRMED,
            source = BookingSource.BLABLACAR,
            capacityClaimType = CapacityClaimType.RESERVED_SEAT,
        )
        val cancelled = active.copy(id = "cancelled", status = BookingStatus.CANCELLED)
        val passenger = active.copy(id = "passenger", capacityClaimType = CapacityClaimType.PASSENGER)
        assertEquals(listOf("active"), QuickPassengerEngine.activeReservedSeatLinks(listOf(active, cancelled, passenger)).map { it.id })
    }
}
