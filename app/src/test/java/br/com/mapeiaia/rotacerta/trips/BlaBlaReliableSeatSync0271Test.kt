package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals

class BlaBlaReliableSeatSync0271Test {
    @Test
    fun freshPrivateBookingReducesExactPublishedSeats() {
        val decision = BlaBlaReliableSeatSyncPolicy.decide(
            currentSeats = 4,
            canAdd = true,
            canRemove = true,
            seatDelta = -1,
            attempt = null,
        )
        assertEquals(BlaBlaReliableSeatSyncAction.APPLY_TARGET, decision.action)
        assertEquals(3, decision.targetSeats)
    }

    @Test
    fun retryAfterExternalSaveDoesNotDecreaseTwice() {
        val attempt = attempt(before = 4, target = 3, delta = -1)
        val decision = BlaBlaReliableSeatSyncPolicy.decide(
            currentSeats = 3,
            canAdd = true,
            canRemove = true,
            seatDelta = -1,
            attempt = attempt,
        )
        assertEquals(BlaBlaReliableSeatSyncAction.COMPLETE_ALREADY_APPLIED, decision.action)
        assertEquals(3, decision.targetSeats)
    }

    @Test
    fun retryBeforeExternalSaveMayApplySameTargetOnce() {
        val attempt = attempt(before = 4, target = 3, delta = -1)
        val decision = BlaBlaReliableSeatSyncPolicy.decide(
            currentSeats = 4,
            canAdd = true,
            canRemove = true,
            seatDelta = -1,
            attempt = attempt,
        )
        assertEquals(BlaBlaReliableSeatSyncAction.APPLY_TARGET, decision.action)
        assertEquals(3, decision.targetSeats)
    }

    @Test
    fun changedExternalQuantityFailsClosedInsteadOfApplyingBlindDelta() {
        val attempt = attempt(before = 4, target = 3, delta = -1)
        val decision = BlaBlaReliableSeatSyncPolicy.decide(
            currentSeats = 2,
            canAdd = true,
            canRemove = true,
            seatDelta = -1,
            attempt = attempt,
        )
        assertEquals(BlaBlaReliableSeatSyncAction.PENDING_CONFLICT, decision.action)
    }

    @Test
    fun verifiedCancellationAddsSeatBack() {
        val decision = BlaBlaReliableSeatSyncPolicy.decide(
            currentSeats = 3,
            canAdd = true,
            canRemove = true,
            seatDelta = 1,
            attempt = null,
        )
        assertEquals(BlaBlaReliableSeatSyncAction.APPLY_TARGET, decision.action)
        assertEquals(4, decision.targetSeats)
    }

    @Test
    fun threeSeatPrivateBookingReducesFourToOne() {
        val decision = BlaBlaReliableSeatSyncPolicy.decide(
            currentSeats = 4,
            canAdd = true,
            canRemove = true,
            seatDelta = -3,
            attempt = null,
        )
        assertEquals(BlaBlaReliableSeatSyncAction.APPLY_TARGET, decision.action)
        assertEquals(1, decision.targetSeats)
    }

    @Test
    fun threeSeatCancellationRestoresOneToFour() {
        val decision = BlaBlaReliableSeatSyncPolicy.decide(
            currentSeats = 1,
            canAdd = true,
            canRemove = true,
            seatDelta = 3,
            attempt = null,
        )
        assertEquals(BlaBlaReliableSeatSyncAction.APPLY_TARGET, decision.action)
        assertEquals(4, decision.targetSeats)
    }

    @Test
    fun cancellationBeforeUncertainDecreaseLandedNeedsNoExternalWrite() {
        val attempt = attempt(before = 4, target = 3, delta = -1, compensate = true)
        val decision = BlaBlaReliableSeatSyncPolicy.decide(
            currentSeats = 4,
            canAdd = true,
            canRemove = true,
            seatDelta = -1,
            attempt = attempt,
        )
        assertEquals(BlaBlaReliableSeatSyncAction.COMPLETE_COMPENSATION, decision.action)
        assertEquals(4, decision.targetSeats)
    }

    @Test
    fun cancellationAfterUncertainDecreaseLandedRestoresOriginalSeatCount() {
        val attempt = attempt(before = 4, target = 3, delta = -1, compensate = true)
        val decision = BlaBlaReliableSeatSyncPolicy.decide(
            currentSeats = 3,
            canAdd = true,
            canRemove = true,
            seatDelta = -1,
            attempt = attempt,
        )
        assertEquals(BlaBlaReliableSeatSyncAction.APPLY_COMPENSATION, decision.action)
        assertEquals(4, decision.targetSeats)
    }

    @Test
    fun freshRequestIsPreferredOverRetainedAttempt() {
        val retained = request(id = "retained", bookingId = "booking-old", delta = -3, createdAt = 1L)
        val fresh = request(id = "fresh", bookingId = "booking-new", delta = -1, createdAt = 2L)

        val selected = BlaBlaReliableSeatQueuePolicy.select(listOf(retained, fresh)) { requestId ->
            requestId == retained.id
        }

        assertEquals(fresh.id, selected?.id)
    }

    @Test
    fun retainedAttemptStillRunsWhenItIsTheOnlyPendingRequest() {
        val retained = request(id = "retained", bookingId = "booking-old", delta = -3, createdAt = 1L)

        val selected = BlaBlaReliableSeatQueuePolicy.select(listOf(retained)) { true }

        assertEquals(retained.id, selected?.id)
    }

    @Test
    fun unavailableEditorKeepsOperationPending() {
        val decision = BlaBlaReliableSeatSyncPolicy.decide(
            currentSeats = 4,
            canAdd = true,
            canRemove = false,
            seatDelta = -1,
            attempt = null,
        )
        assertEquals(BlaBlaReliableSeatSyncAction.PENDING_UNAVAILABLE, decision.action)
    }

    private fun request(
        id: String,
        bookingId: String,
        delta: Int,
        createdAt: Long,
    ) = BlaBlaManualSeatSyncRequest(
        id = id,
        profileUuid = "7371f028-9c55-4903-8444-308015823efd",
        tripId = "trip-1",
        seatDelta = delta,
        localTripId = "local-trip-1",
        localBookingId = bookingId,
        source = BookingSource.PRIVATE.name,
        createdAtMillis = createdAt,
    )

    private fun attempt(
        before: Int,
        target: Int,
        delta: Int,
        compensate: Boolean = false,
    ) = BlaBlaManualSeatSyncAttempt(
        requestId = "request-1",
        localBookingId = "booking-1",
        profileUuid = "7371f028-9c55-4903-8444-308015823efd",
        tripId = "trip-1",
        seatDelta = delta,
        observedSeatsBefore = before,
        targetSeats = target,
        compensateAfterCancellation = compensate,
    )
}
