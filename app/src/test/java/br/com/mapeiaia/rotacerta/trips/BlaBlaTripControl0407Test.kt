package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlaBlaTripControl0407Test {
    private val target = BlaBlaTripTarget0407(
        tenantId = "tenant-test",
        accountId = "account-test",
        profileUuid = "11111111-1111-4111-8111-111111111111",
        tripId = "trip_test_123456",
        tripHref = "https://www.blablacar.com.br/trip/trip_test_123456",
    )

    @Test
    fun staticOnlyCapabilitiesAreNotExposedAsProductionActions() {
        val snapshot = BlaBlaCapabilityRegistry0407.snapshot(target = target)
        val palette = buildBlaBlaTripActionPalette0407(snapshot, hasPublicationHref = true)

        assertTrue(BlaBlaTripAction0407.REVERIFY in palette.primary)
        assertTrue(BlaBlaTripAction0407.OPEN_PUBLICATION in palette.overflow)
        assertFalse(snapshot.canShow(BlaBlaTripCapability0407.SET_TRIP_BOOST))
        assertEquals(
            BlaBlaCapabilityEvidence0407.DISCOVERED_STATIC,
            snapshot.state(BlaBlaTripCapability0407.SET_TRIP_BOOST)?.evidence,
        )
    }

    @Test
    fun missingStrongTargetFailsClosed() {
        val snapshot = BlaBlaCapabilityRegistry0407.snapshot(target = null)
        val palette = buildBlaBlaTripActionPalette0407(snapshot, hasPublicationHref = true)

        assertFalse(snapshot.canShow(BlaBlaTripCapability0407.REVERIFY_TRIP))
        assertTrue(palette.primary.isEmpty())
        assertTrue(palette.overflow.isEmpty())
    }

    @Test
    fun seatsBecomeWritableOnlyAfterVerifiedReadbackState() {
        val before = BlaBlaCapabilityRegistry0407.snapshot(
            target = target,
            seatSyncState = BlaBlaPublicationSeatSyncState(
                profileUuid = target.profileUuid,
                tripId = target.tripId,
                lastObservedPublishedSeats = 3,
                state = BlaBlaPublicationSeatSyncVisualState.AVAILABLE,
            ),
        )
        assertFalse(before.state(BlaBlaTripCapability0407.SET_TRIP_SEATS)?.writable == true)

        val after = BlaBlaCapabilityRegistry0407.snapshot(
            target = target,
            seatSyncState = BlaBlaPublicationSeatSyncState(
                profileUuid = target.profileUuid,
                tripId = target.tripId,
                desiredPublishedSeats = 2,
                lastObservedPublishedSeats = 2,
                state = BlaBlaPublicationSeatSyncVisualState.SYNCED,
            ),
        )
        assertTrue(after.state(BlaBlaTripCapability0407.SET_TRIP_SEATS)?.writable == true)
        assertEquals(
            BlaBlaCapabilityEvidence0407.WRITE_VERIFIED,
            after.state(BlaBlaTripCapability0407.SET_TRIP_SEATS)?.evidence,
        )
    }

    @Test
    fun commandIdentityKeepsTripAndAccountAndIsIdempotencyScoped() {
        val first = BlaBlaCommand0407.forTarget(
            target = target,
            operation = BlaBlaTripCapability0407.REVERIFY_TRIP,
            origin = "CARD",
        )
        val second = BlaBlaCommand0407.forTarget(
            target = target,
            operation = BlaBlaTripCapability0407.REVERIFY_TRIP,
            origin = "CARD",
        )

        assertEquals(target.tenantId, first.tenantId)
        assertEquals(target.accountId, first.accountId)
        assertEquals(target.profileUuid, first.profileUuid)
        assertEquals(target.tripId, first.tripId)
        assertNotEquals(first.commandId, second.commandId)
        assertNotEquals(first.idempotencyKey, second.idempotencyKey)
    }

    @Test
    fun networkFirstWaitIsBoundedBeforeDomFallback() {
        assertTrue(shouldAwaitNetworkTripSource0407(sourcePresent = false, readAttempts = 0, maxReadAttempts = 2))
        assertTrue(shouldAwaitNetworkTripSource0407(sourcePresent = false, readAttempts = 1, maxReadAttempts = 2))
        assertFalse(shouldAwaitNetworkTripSource0407(sourcePresent = false, readAttempts = 2, maxReadAttempts = 2))
        assertFalse(shouldAwaitNetworkTripSource0407(sourcePresent = true, readAttempts = 0, maxReadAttempts = 2))
    }
    @Test
    fun targetedReasonUsesCollectorReconcileWithoutFullCollectorSemantics() {
        assertEquals(
            AgendaBackgroundSyncMode0392.COLLECTOR_RECONCILE,
            agendaBackgroundSyncMode0392("trip_reverify"),
        )
        assertEquals("TRIP_REVERIFY", agendaBackgroundSyncTrigger0397("trip_reverify"))
        assertFalse(agendaBackgroundSyncRefreshesCoverageCheckpoint0403("trip_reverify"))
    }

    @Test
    fun targetedCollectorResponseUpdatesOnlyRequestedStrongTripAndNeverClaimsFullCoverage() {
        val tripA = BlaBlaCollectorTrip(
            profile_uuid = target.profileUuid,
            date = "2026-09-02",
            trip_href = target.tripHref,
            trip_id = target.tripId,
            published_seats = 2,
            passenger_roster_complete = true,
        )
        val tripB = BlaBlaCollectorTrip(
            profile_uuid = target.profileUuid,
            date = "2026-09-03",
            trip_href = "https://www.blablacar.com.br/trip/trip_other_654321",
            trip_id = "trip_other_654321",
            published_seats = 3,
            passenger_roster_complete = true,
        )
        val full = BlaBlaCollectorMonthResponse(
            status = "validated",
            trips = listOf(tripA, tripB),
            coverage = BlaBlaCollectorCoverage(
                complete_for_scope = true,
                global_profile_month_complete = true,
                reason = "full_before_targeted_reverify",
            ),
        )

        val targeted = targetedCollectorResponse0407(full, target)!!

        assertEquals(listOf(target.tripId), targeted.trips.map { it.trip_id })
        assertFalse(targeted.coverage.complete_for_scope)
        assertFalse(targeted.coverage.global_profile_month_complete)
        assertEquals("targeted_trip_reverify", targeted.coverage.reason)
    }

    @Test
    fun targetedCollectorResponseFailsClosedWhenStrongTargetIsMissing() {
        val unrelated = BlaBlaCollectorTrip(
            profile_uuid = target.profileUuid,
            date = "2026-09-03",
            trip_href = "https://www.blablacar.com.br/trip/trip_other_654321",
            trip_id = "trip_other_654321",
        )
        val response = BlaBlaCollectorMonthResponse(
            status = "validated",
            trips = listOf(unrelated),
            coverage = BlaBlaCollectorCoverage(complete_for_scope = true),
        )

        val targeted = targetedCollectorResponse0407(response, target)!!

        assertTrue(targeted.trips.isEmpty())
        assertFalse(targeted.coverage.complete_for_scope)
        assertEquals(1, targeted.coverage.unresolved_target_cards)
        assertEquals("targeted_trip_missing_or_ambiguous", targeted.coverage.reason)
    }

    @Test
    fun verificationLabelUsesTerminalCommandResultInsteadOfStaleObservedTimestamp() {
        val previouslyObserved = 1_700_000_000_000L
        assertEquals(
            "⟳ Atualizando",
            blaBlaVerificationLabel0407(
                audit = BlaBlaCommandAuditSnapshot0407(
                    commandId = "queued",
                    status = BlaBlaCommandStatus0407.QUEUED,
                    requestedAtMillis = previouslyObserved + 1,
                    finishedAtMillis = 0L,
                ),
                lastObservedAtMillis = previouslyObserved,
                strongTargetAvailable = true,
            ),
        )
        assertEquals(
            "⚠ Falha na verificação",
            blaBlaVerificationLabel0407(
                audit = BlaBlaCommandAuditSnapshot0407(
                    commandId = "failed",
                    status = BlaBlaCommandStatus0407.UNVERIFIED,
                    requestedAtMillis = previouslyObserved + 1,
                    finishedAtMillis = previouslyObserved + 2,
                    errorCode = "TIMEOUT",
                ),
                lastObservedAtMillis = previouslyObserved,
                strongTargetAvailable = true,
            ),
        )
        assertEquals(
            "⚠ Sessão necessária",
            blaBlaVerificationLabel0407(
                audit = BlaBlaCommandAuditSnapshot0407(
                    commandId = "auth",
                    status = BlaBlaCommandStatus0407.AUTH_REQUIRED,
                    requestedAtMillis = previouslyObserved + 1,
                    finishedAtMillis = previouslyObserved + 2,
                    errorCode = "AUTH_REQUIRED",
                ),
                lastObservedAtMillis = previouslyObserved,
                strongTargetAvailable = true,
            ),
        )
        assertEquals(
            "✓ Verificado agora",
            blaBlaVerificationLabel0407(
                audit = BlaBlaCommandAuditSnapshot0407(
                    commandId = "ok",
                    status = BlaBlaCommandStatus0407.VERIFIED_SUCCESS,
                    requestedAtMillis = previouslyObserved + 1,
                    finishedAtMillis = previouslyObserved + 2,
                ),
                lastObservedAtMillis = previouslyObserved,
                strongTargetAvailable = true,
            ),
        )
    }


    @Test
    fun targetedSessionDeltaPreservesSiblingTripInsteadOfReplacingAccountSnapshot() {
        val oldA = BlaBlaCollectorTrip(
            profile_uuid = target.profileUuid,
            date = "2026-09-02",
            trip_href = target.tripHref,
            trip_id = target.tripId,
            published_seats = 3,
            passenger_roster_complete = true,
        )
        val siblingB = BlaBlaCollectorTrip(
            profile_uuid = target.profileUuid,
            date = "2026-09-03",
            trip_href = "https://www.blablacar.com.br/trip/trip_other_654321",
            trip_id = "trip_other_654321",
            published_seats = 2,
            passenger_roster_complete = true,
        )
        val freshA = oldA.copy(published_seats = 1)

        val merged = BlaBlaCollectorTimelineModule.mergeSnapshotTrips(
            previous = listOf(oldA, siblingB),
            current = listOf(freshA),
            authoritativeComplete = false,
        )

        assertEquals(setOf(target.tripId, siblingB.trip_id), merged.trips.map { it.trip_id }.toSet())
        assertEquals(1, merged.preservedMissingTrips)
        assertEquals(2, merged.trips.size)
    }

}
