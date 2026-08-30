package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookingExternalSeatSync0353Test {
    private val profileA = "7371f028-9c55-4903-8444-308015823efd"
    private val profileB = "175a7068-50d8-40c3-a27a-214b9c6e0461"
    private val trip = Trip(
        id = "public-external:remote-1",
        title = "A → B",
        departureAtMillis = 1_800_000_000_000L,
        capacity = 4,
        status = TripStatus.PUBLISHED,
        stops = listOf(
            TripStop(id = "a", order = 0, name = "A"),
            TripStop(id = "b", order = 1, name = "B"),
        ),
    )

    @Test
    fun caseAOneSeatReservationChangesFourToThree() {
        val after = listOf(booking("r1", 1, BookingStatus.CONFIRMED))
        assertEquals(-1, publicBookingSeatDelta(trip, emptyList(), after))
        val decision = BlaBlaReliableSeatSyncPolicy.decide(4, true, true, -1, null)
        assertEquals(BlaBlaReliableSeatSyncAction.APPLY_TARGET, decision.action)
        assertEquals(3, decision.targetSeats)
    }

    @Test
    fun caseBTwoSeatReservationChangesFourToTwo() {
        val after = listOf(booking("r2", 2, BookingStatus.CONFIRMED))
        assertEquals(-2, publicBookingSeatDelta(trip, emptyList(), after))
        val decision = BlaBlaReliableSeatSyncPolicy.decide(4, true, true, -2, null)
        assertEquals(BlaBlaReliableSeatSyncAction.APPLY_TARGET, decision.action)
        assertEquals(2, decision.targetSeats)
    }

    @Test
    fun caseCCancellationReturnsThreeToFour() {
        val before = listOf(booking("r1", 1, BookingStatus.CONFIRMED))
        val after = listOf(booking("r1", 1, BookingStatus.CANCELLED))
        assertEquals(1, publicBookingSeatDelta(trip, before, after))
        val decision = BlaBlaReliableSeatSyncPolicy.decide(3, true, true, 1, null)
        assertEquals(BlaBlaReliableSeatSyncAction.APPLY_TARGET, decision.action)
        assertEquals(4, decision.targetSeats)
    }

    @Test
    fun caseDRetryNeverChangesThreeToTwoAgain() {
        val attempt = BlaBlaManualSeatSyncAttempt(
            requestId = "request",
            localBookingId = "booking",
            profileUuid = profileA,
            tripId = "trip-1",
            seatDelta = -1,
            observedSeatsBefore = 4,
            targetSeats = 3,
        )
        val decision = BlaBlaReliableSeatSyncPolicy.decide(3, true, true, -1, attempt)
        assertEquals(BlaBlaReliableSeatSyncAction.COMPLETE_ALREADY_APPLIED, decision.action)
        assertEquals(3, decision.targetSeats)
    }

    @Test
    fun caseEStrongPersistedBindingWinsWhenTimelineHasZeroMatches() {
        val result = resolvePublicBookingSeatSyncIdentity(binding(profileA, "trip-a"), timelineMatchCount = 0)
        assertEquals(PublicBookingSeatSyncIdentityResolution.PERSISTED_STRONG_BINDING, result)
    }

    @Test
    fun caseFAmbiguousIdentityFailsClosed() {
        val result = resolvePublicBookingSeatSyncIdentity(binding("", ""), timelineMatchCount = 0)
        assertEquals(PublicBookingSeatSyncIdentityResolution.BLOCKED, result)
    }

    @Test
    fun caseGOnlyCorrectAccountMatchesPublicationProfile() {
        assertTrue(seatSyncAccountMatches(profileA, profileA.uppercase()))
        assertFalse(seatSyncAccountMatches(profileA, profileB))
        assertFalse(seatSyncAccountMatches(profileA, null))
    }

    @Test
    fun cancellationBeforePendingDecreaseExecutesCoalescesToNoOp() {
        val pending = publicRequest("pending", -1)
        val merged = mergePublicBookingSeatWork(pending, null, 1)
        assertEquals(BlaBlaPublicBookingQueueMergeKind.NO_OP, merged.kind)
    }

    @Test
    fun newReservationDuringUncertainPreviousWriteBecomesAbsoluteFinalTarget() {
        val pending = publicRequest("pending", -1)
        val attempt = BlaBlaManualSeatSyncAttempt(
            requestId = pending.id,
            localBookingId = pending.localBookingId,
            profileUuid = profileA,
            tripId = "trip-a",
            seatDelta = -1,
            observedSeatsBefore = 4,
            targetSeats = 3,
        )
        val merged = mergePublicBookingSeatWork(pending, attempt, -1)
        assertEquals(BlaBlaPublicBookingQueueMergeKind.ABSOLUTE, merged.kind)
        assertEquals(2, merged.desiredPublishedSeats)
    }

    @Test
    fun unknownPublishedSeatsCannotBeConfirmedAsSuccess() {
        val decision = BlaBlaReliableSeatSyncPolicy.decideDesired(-1, true, true, 3)
        assertEquals(BlaBlaReliableSeatSyncAction.INVALID, decision.action)
    }

    @Test
    fun queueAndRemoteConfirmationContractsAreCausal() {
        val bookingSync = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PublicBookingSync0296.kt").readText()
        val reliable = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaReliableSeatSync.kt").readText()
        val activity = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()
        val collector = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripBlaBlaCollectorUi.kt").readText()

        listOf(
            "BOOKING_SEAT_SYNC_REQUESTED",
            "BOOKING_SEAT_SYNC_IDENTITY_RESOLVED",
            "BOOKING_SEAT_SYNC_ENQUEUED",
            "BOOKING_SEAT_SYNC_BLOCKED",
        ).forEach { assertTrue(bookingSync.contains(it)) }

        listOf(
            "BOOKING_SEAT_SYNC_EXECUTING",
            "BOOKING_SEAT_SYNC_REMOTE_MUTATION_SENT",
            "BOOKING_SEAT_SYNC_REMOTE_CONFIRMED",
            "SEAT_SYNC_TRIGGER",
            "SEAT_SYNC_TRIP_RESOLUTION",
            "SEAT_SYNC_ACCOUNT_RESOLUTION",
            "SEAT_SYNC_BEFORE",
            "SEAT_SYNC_DESIRED",
            "SEAT_SYNC_MUTATION_START",
            "SEAT_SYNC_MUTATION_RESULT",
            "SEAT_SYNC_READBACK_START",
            "SEAT_SYNC_READBACK_RESULT",
            "SEAT_SYNC_CONFIRMED",
            "SEAT_SYNC_FAILED",
            "CAPACITY_REMOTE_CONFIRMATION",
        ).forEach { assertTrue(reliable.contains(it)) }

        assertFalse(activity.contains("\"CAPACITY_REMOTE_CONFIRMATION\""))
        assertTrue(activity.contains("CAPACITY_PUBLIC_AGENDA_SYNC_RESULT"))
        assertTrue(collector.contains("automatic_queue_continuation"))
        assertTrue(collector.contains("requestedProfile == null ||"))
    }

    private fun booking(id: String, seats: Int, status: BookingStatus) = Booking(
        id = id,
        tripId = trip.id,
        passengerName = id,
        boardingStopId = "a",
        dropoffStopId = "b",
        seats = seats,
        status = status,
        source = BookingSource.ROTA_CERTA,
        capacityClaimType = CapacityClaimType.PASSENGER,
    )

    private fun binding(profileUuid: String, blablaTripId: String) = PublicExternalTripBinding(
        remoteTripId = "remote-1",
        publicToken = "public-token",
        bookingTripId = trip.id,
        profileUuid = profileUuid,
        blablaTripId = blablaTripId,
        title = trip.title,
        departureAtMillis = trip.departureAtMillis,
        capacity = trip.capacity,
        stops = trip.stops,
    )

    private fun publicRequest(id: String, seatDelta: Int) = BlaBlaManualSeatSyncRequest(
        id = id,
        profileUuid = profileA,
        tripId = "trip-a",
        seatDelta = seatDelta,
        localTripId = trip.id,
        localBookingId = "public-booking-state",
        source = PUBLIC_BOOKING_SEAT_SYNC_SOURCE,
    )
}
