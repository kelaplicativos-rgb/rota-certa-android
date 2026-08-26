package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TripSegmentSeatSync0292Test {
    private val stops = listOf(
        TripStop(id = "sa", order = 0, name = "Santo André"),
        TripStop(id = "sp", order = 1, name = "São Paulo"),
        TripStop(id = "ex", order = 2, name = "Extrema"),
        TripStop(id = "pa", order = 3, name = "Pouso Alegre"),
        TripStop(id = "tc", order = 4, name = "Três Corações"),
        TripStop(id = "st", order = 5, name = "São Thomé"),
    )
    private val trip = Trip(
        id = "trip",
        title = "Santo André → São Thomé",
        departureAtMillis = 1L,
        capacity = 4,
        status = TripStatus.PUBLISHED,
        stops = stops,
    )

    @Test
    fun mixedExternalAndManualPassengersAreCalculatedPerSegment() {
        val bookings = listOf(
            booking("a", BookingSource.BLABLACAR, "sa", "pa", 1),
            booking("b", BookingSource.BLABLACAR, "sp", "tc", 1),
            booking("c", BookingSource.PRIVATE, "pa", "st", 2),
            booking("d", BookingSource.PRIVATE, "tc", "st", 1),
        )
        val loads = SeatAvailabilityEngine.segmentLoads(trip, bookings)
        assertEquals(listOf(3, 2, 2, 1, 1), loads.map(SegmentLoad::availableSeats))
        assertEquals(1, loads.minOf(SegmentLoad::availableSeats))
    }

    @Test
    fun seatBecomesAvailableAgainAfterPassengerDropoff() {
        val bookings = listOf(booking("a", BookingSource.PRIVATE, "sa", "pa", 1))
        val loads = SeatAvailabilityEngine.segmentLoads(trip, bookings)
        assertEquals(listOf(3, 3, 3, 4, 4), loads.map(SegmentLoad::availableSeats))
    }

    @Test
    fun editingOneSeatToTwoRechecksOnlyAffectedSegments() {
        val original = booking("manual", BookingSource.PRIVATE, "pa", "st", 1)
        val updated = QuickPassengerEngine.updateManualBooking(
            trip = trip,
            existingBookings = listOf(original),
            booking = original,
            boardingStopId = "pa",
            dropoffStopId = "st",
            seats = 2,
        )
        val loads = SeatAvailabilityEngine.segmentLoads(trip, listOf(updated))
        assertEquals(listOf(4, 4, 4, 2, 2), loads.map(SegmentLoad::availableSeats))
    }

    @Test
    fun cancellationImmediatelyReturnsPhysicalCapacity() {
        val active = booking("manual", BookingSource.PRIVATE, "tc", "st", 3)
        val cancelled = active.copy(status = BookingStatus.CANCELLED)
        assertEquals(1, SeatAvailabilityEngine.segmentLoads(trip, listOf(active)).last().availableSeats)
        assertEquals(4, SeatAvailabilityEngine.segmentLoads(trip, listOf(cancelled)).last().availableSeats)
    }

    @Test
    fun duplicateOccupancyGroupIsNotCountedTwice() {
        val external = booking("external", BookingSource.BLABLACAR, "sp", "tc", 2).copy(occupancyGroupId = "same-seat")
        val manual = booking("manual", BookingSource.PRIVATE, "sp", "tc", 2).copy(occupancyGroupId = "same-seat")
        val loads = SeatAvailabilityEngine.segmentLoads(trip, listOf(external, manual))
        assertEquals(2, loads[1].occupiedSeats)
        assertEquals(2, loads[2].occupiedSeats)
    }

    @Test
    fun desiredStateIsIdempotentAndNeverBlindlyDecrements() {
        val first = BlaBlaReliableSeatSyncPolicy.decideDesired(3, canAdd = true, canRemove = true, desiredPublishedSeats = 2)
        assertEquals(BlaBlaReliableSeatSyncAction.APPLY_TARGET, first.action)
        assertEquals(2, first.targetSeats)

        val repeated = BlaBlaReliableSeatSyncPolicy.decideDesired(2, canAdd = true, canRemove = true, desiredPublishedSeats = 2)
        assertEquals(BlaBlaReliableSeatSyncAction.COMPLETE_ALREADY_APPLIED, repeated.action)
        assertEquals(2, repeated.targetSeats)
    }

    @Test
    fun desiredStateCanRestorePlacesWithoutRememberingOldDelta() {
        val restore = BlaBlaReliableSeatSyncPolicy.decideDesired(1, canAdd = true, canRemove = true, desiredPublishedSeats = 4)
        assertEquals(BlaBlaReliableSeatSyncAction.APPLY_TARGET, restore.action)
        assertEquals(4, restore.targetSeats)
    }

    @Test
    fun unavailableEditorFailsPendingWithoutPretendingSuccess() {
        val decision = BlaBlaReliableSeatSyncPolicy.decideDesired(3, canAdd = true, canRemove = false, desiredPublishedSeats = 2)
        assertEquals(BlaBlaReliableSeatSyncAction.PENDING_UNAVAILABLE, decision.action)
    }

    @Test
    fun configuredCapacityAlwaysOverridesExternalOrStaleCardCapacity() {
        val entry = TripTimelineEntry(
            tripId = "x",
            profileId = "p",
            profileLabel = "P",
            departureAtMillis = 1L,
            arrivalAtMillis = null,
            origin = "A",
            destination = "B",
            status = TripStatus.PUBLISHED,
            capacity = 2,
            minimumOccupiedSeats = 0,
            maximumOccupiedSeats = 0,
            sourcePassengerSeats = emptyMap(),
        )
        assertEquals(4, applyConfiguredVehicleCapacity(listOf(entry), 4).single().capacity)
    }

    private fun booking(id: String, source: BookingSource, from: String, to: String, seats: Int) = Booking(
        id = id,
        tripId = trip.id,
        passengerName = id,
        boardingStopId = from,
        dropoffStopId = to,
        seats = seats,
        status = BookingStatus.CONFIRMED,
        source = source,
        capacityClaimType = CapacityClaimType.PASSENGER,
    )
}
