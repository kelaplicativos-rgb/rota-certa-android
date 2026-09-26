package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TripSeatSyncAudit0293Test {
    private val profileA = "7371f028-9c55-4903-8444-308015823efd"
    private val profileB = "175a7068-50d8-40c3-a27a-214b9c6e0461"
    private val stops = listOf(
        TripStop(id = "sa", order = 0, name = "Santo André"),
        TripStop(id = "sp", order = 1, name = "São Paulo"),
        TripStop(id = "ex", order = 2, name = "Extrema"),
        TripStop(id = "pa", order = 3, name = "Pouso Alegre"),
        TripStop(id = "tc", order = 4, name = "Três Corações"),
        TripStop(id = "st", order = 5, name = "São Thomé"),
    )
    private val trip = Trip(
        id = "local-trip",
        title = "Santo André → São Thomé",
        departureAtMillis = 1_800_000_000_000L,
        capacity = 4,
        status = TripStatus.PUBLISHED,
        stops = stops,
    )

    @Test
    fun editingTwoSeatsToOneRestoresOnlyAffectedSegments() {
        val original = booking("manual", BookingSource.PRIVATE, "pa", "st", 2)
        val updated = QuickPassengerEngine.updateManualBooking(
            trip, listOf(original), original, "pa", "st", 1,
        )
        assertEquals(listOf(4, 4, 4, 3, 3), SeatAvailabilityEngine.segmentLoads(trip, listOf(updated)).map(SegmentLoad::availableSeats))
    }

    @Test
    fun changingBoardingMovesOccupancyToLaterSegments() {
        val original = booking("manual", BookingSource.PRIVATE, "sa", "st", 1)
        val updated = QuickPassengerEngine.updateManualBooking(
            trip, listOf(original), original, "pa", "st", 1,
        )
        assertEquals(listOf(4, 4, 4, 3, 3), SeatAvailabilityEngine.segmentLoads(trip, listOf(updated)).map(SegmentLoad::availableSeats))
    }

    @Test
    fun changingDropoffReleasesAllLaterSegments() {
        val original = booking("manual", BookingSource.PRIVATE, "sa", "st", 1)
        val updated = QuickPassengerEngine.updateManualBooking(
            trip, listOf(original), original, "sa", "pa", 1,
        )
        assertEquals(listOf(3, 3, 3, 4, 4), SeatAvailabilityEngine.segmentLoads(trip, listOf(updated)).map(SegmentLoad::availableSeats))
    }

    @Test
    fun partialPassengerConsumesOnlyFinalSegment() {
        val manual = booking("manual", BookingSource.PRIVATE, "tc", "st", 1)
        assertEquals(listOf(4, 4, 4, 4, 3), SeatAvailabilityEngine.segmentLoads(trip, listOf(manual)).map(SegmentLoad::availableSeats))
    }

    @Test
    fun exactExternalMirrorIsNotDoubleCountedWithManualPassenger() {
        val manual = booking("manual", BookingSource.PRIVATE, "sp", "tc", 1).copy(passengerContact = "11999999999")
        val entry = externalEntry(
            profileUuid = profileA,
            tripId = "publication-a",
            passengers = listOf(
                BlaBlaCollectorPassenger(
                    name = "Mesmo passageiro",
                    seats = 1,
                    boarding = "São Paulo",
                    dropoff = "Três Corações",
                    phone = "(11) 99999-9999",
                ),
            ),
            bookedSeats = 1,
        )
        val claims = planTimelineExternalCapacityClaims(entry, trip, listOf(manual))
        assertTrue(claims.isEmpty())
        assertEquals(1, SeatAvailabilityEngine.segmentLoads(trip, listOf(manual)).maxOf(SegmentLoad::occupiedSeats))
    }

    @Test
    fun distinctExternalAndManualPassengersAreBothCounted() {
        val manual = booking("manual", BookingSource.PRIVATE, "sp", "tc", 1).copy(passengerContact = "11888888888")
        val entry = externalEntry(
            profileUuid = profileA,
            tripId = "publication-a",
            passengers = listOf(
                BlaBlaCollectorPassenger(
                    name = "Externo",
                    seats = 1,
                    boarding = "São Paulo",
                    dropoff = "Três Corações",
                    phone = "11999999999",
                ),
            ),
            bookedSeats = 1,
        )
        val claims = planTimelineExternalCapacityClaims(entry, trip, listOf(manual))
        val loads = SeatAvailabilityEngine.segmentLoads(trip, listOf(manual) + claims)
        assertEquals(2, loads[1].occupiedSeats)
        assertEquals(2, loads[2].occupiedSeats)
        assertEquals(2, loads[3].occupiedSeats)
    }

    @Test
    fun repeatedDesiredStateNeverAccumulatesBlindDecrements() {
        val first = BlaBlaReliableSeatSyncPolicy.decideDesired(3, true, true, 2)
        val retryBeforeWrite = BlaBlaReliableSeatSyncPolicy.decideDesired(3, true, true, 2)
        val second = BlaBlaReliableSeatSyncPolicy.decideDesired(2, true, true, 2)
        val third = BlaBlaReliableSeatSyncPolicy.decideDesired(2, true, true, 2)
        assertEquals(2, first.targetSeats)
        assertEquals(2, retryBeforeWrite.targetSeats)
        assertEquals(BlaBlaReliableSeatSyncAction.COMPLETE_ALREADY_APPLIED, second.action)
        assertEquals(BlaBlaReliableSeatSyncAction.COMPLETE_ALREADY_APPLIED, third.action)
    }

    @Test
    fun rapidManualAndAutomaticTriggersCollapseToLatestDesiredState() {
        val manual = desiredRequest("manual", profileA, "publication-a", 2, "manual_card_shortcut")
        val automatic = desiredRequest("automatic", profileA, "publication-a", 1, "automatic_after_passenger_change")
        val afterManual = BlaBlaDesiredSeatQueuePolicy.replacePublication(emptyList(), manual)
        val afterAutomatic = BlaBlaDesiredSeatQueuePolicy.replacePublication(afterManual, automatic)
        assertEquals(1, afterAutomatic.size)
        assertEquals("automatic", afterAutomatic.single().id)
        assertEquals(1, afterAutomatic.single().desiredPublishedSeats)
    }

    @Test
    fun requestsForDifferentPublicationsAndProfilesStayIndependent() {
        val a = desiredRequest("a", profileA, "publication-a", 2, "manual")
        val b = desiredRequest("b", profileA, "publication-b", 1, "manual")
        val c = desiredRequest("c", profileB, "publication-a", 3, "manual")
        val queue = listOf(a, b).let { BlaBlaDesiredSeatQueuePolicy.replacePublication(it, c) }
        assertEquals(3, queue.size)
        assertTrue(queue.any { it.profileUuid == profileA && it.tripId == "publication-a" })
        assertTrue(queue.any { it.profileUuid == profileA && it.tripId == "publication-b" })
        assertTrue(queue.any { it.profileUuid == profileB && it.tripId == "publication-a" })
    }

    @Test
    fun selectedFreshQueueRequestIsTheExactRequestOpenedByWriter() {
        val retained = desiredRequest("retained", profileA, "publication-a", 2, "retry")
        val fresh = desiredRequest("fresh", profileB, "publication-b", 1, "manual")
        val selected = BlaBlaReliableSeatQueuePolicy.select(listOf(retained, fresh)) { it == "retained" }
        assertEquals("fresh", selected?.id)
        assertEquals(fresh, BlaBlaReliableSeatRequestSelector.select(listOf(retained, fresh), selected?.id))
        assertNull(BlaBlaReliableSeatRequestSelector.select(listOf(retained, fresh), "missing"))
    }

    @Test
    fun sameVisualTripWithDifferentPublicationIdsRemainsDistinct() {
        val a = externalEntry(profileA, "publication-a")
        val b = externalEntry(profileA, "publication-b")
        val targetA = BlaBlaReliableSeatSyncBridge.targetForTimeline(a)
        val targetB = BlaBlaReliableSeatSyncBridge.targetForTimeline(b)
        assertEquals("publication-a", targetA?.tripId)
        assertEquals("publication-b", targetB?.tripId)
        assertFalse(targetA == targetB)
    }

    @Test
    fun samePublicationIdUnderDifferentProfilesRemainsDistinct() {
        val targetA = BlaBlaReliableSeatSyncBridge.targetForTimeline(externalEntry(profileA, "same-id"))
        val targetB = BlaBlaReliableSeatSyncBridge.targetForTimeline(externalEntry(profileB, "same-id"))
        assertEquals(profileA, targetA?.profileUuid)
        assertEquals(profileB, targetB?.profileUuid)
        assertFalse(targetA == targetB)
    }

    @Test
    fun exactOptionsPageAssociationRejectsAnotherPublication() {
        val a = "https://www.blablacar.com.br/rides/offer/edit/publication-a/options"
        val b = "https://www.blablacar.com.br/rides/offer/edit/publication-b/options"
        assertTrue(BlaBlaHarvestAssociation.optionsPageMatches("publication-a", a))
        assertFalse(BlaBlaHarvestAssociation.optionsPageMatches("publication-a", b))
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

    private fun externalEntry(
        profileUuid: String,
        tripId: String,
        passengers: List<BlaBlaCollectorPassenger> = emptyList(),
        bookedSeats: Int = 0,
    ) = TripTimelineEntry(
        tripId = "timeline:$tripId",
        profileId = profileUuid,
        profileLabel = "Perfil",
        departureAtMillis = trip.departureAtMillis,
        arrivalAtMillis = null,
        origin = "Santo André",
        destination = "São Thomé",
        status = TripStatus.PUBLISHED,
        capacity = 4,
        minimumOccupiedSeats = bookedSeats,
        maximumOccupiedSeats = bookedSeats,
        sourcePassengerSeats = if (bookedSeats > 0) mapOf(BookingSource.BLABLACAR to bookedSeats) else emptyMap(),
        blablaTripId = tripId,
        blablaTripHref = "https://www.blablacar.com.br/rides/offer/$tripId",
        blablaProfileUuid = profileUuid,
        blablaPassengers = passengers,
        blablaPassengerRosterComplete = true,
    )

    private fun desiredRequest(
        id: String,
        profileUuid: String,
        tripId: String,
        desired: Int,
        reason: String,
    ) = BlaBlaManualSeatSyncRequest(
        id = id,
        profileUuid = profileUuid,
        tripId = tripId,
        seatDelta = 0,
        desiredPublishedSeats = desired,
        desiredStateReason = reason,
        localTripId = trip.id,
        localBookingId = "desired:$profileUuid:$tripId",
        source = "DESIRED_STATE",
    )
}
