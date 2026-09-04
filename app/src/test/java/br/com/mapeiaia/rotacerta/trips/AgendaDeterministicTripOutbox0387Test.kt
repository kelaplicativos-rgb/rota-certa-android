package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AgendaDeterministicTripOutbox0387Test {
    private fun source(name: String): String =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/$name").readText()

    private fun backend(): String =
        File("../trip-platform/functions/index.js").readText()

    @Test
    fun strongExternalIdentityUsesTenantAccountProfileAndTripOnly() {
        val base = strongExternalCanonicalTripId0387("tenant-a", "account-a", "profile-a", "trip-a")
        assertEquals(base, strongExternalCanonicalTripId0387("tenant-a", "account-a", "PROFILE-A", "trip-a"))
        assertNotEquals(base, strongExternalCanonicalTripId0387("tenant-b", "account-a", "profile-a", "trip-a"))
        assertNotEquals(base, strongExternalCanonicalTripId0387("tenant-a", "account-b", "profile-a", "trip-a"))
        assertNotEquals(base, strongExternalCanonicalTripId0387("tenant-a", "account-a", "profile-b", "trip-a"))
        assertNotEquals(base, strongExternalCanonicalTripId0387("tenant-a", "account-a", "profile-a", "trip-b"))
    }

    @Test
    fun canonicalExternalSnapshotIdentityAllowsExistingTripKeyBeforePublicBinding() {
        val source = BlaBlaCollectorTrip(
            profile_uuid = "profile-a",
            date = "2030-09-04",
            trip_id = "trip-a",
        )
        val canonical = Trip(
            title = "External canonical",
            departureAtMillis = 0L,
            stops = emptyList(),
            recordOrigin = TripRecordOrigin.EXTERNAL_BACKING,
            blablaProfileUuid = "PROFILE-A",
            blablaTripId = "trip-a",
            tripKey = "timeline-ext-existing",
        )

        assertTrue(
            strongExternalSnapshotIdentityMatches0387(
                canonicalTripId = "timeline-ext-existing",
                snapshotTrip = canonical,
                sourceTrip = source,
            ),
        )
        assertFalse(
            strongExternalSnapshotIdentityMatches0387(
                canonicalTripId = "timeline-ext-existing",
                snapshotTrip = canonical,
                sourceTrip = source.copy(trip_id = "trip-b"),
            ),
        )
        assertFalse(
            strongExternalSnapshotIdentityMatches0387(
                canonicalTripId = "timeline-ext-existing",
                snapshotTrip = canonical.copy(recordOrigin = TripRecordOrigin.LOCAL),
                sourceTrip = source,
            ),
        )
        assertFalse(
            strongExternalSnapshotIdentityMatches0387(
                canonicalTripId = "different-canonical-id",
                snapshotTrip = canonical,
                sourceTrip = source,
            ),
        )
    }

    @Test
    fun projectionIdCannotOverrideAuthoritativeCanonicalTripKey() {
        val source = BlaBlaCollectorTrip(
            profile_uuid = "profile-a",
            date = "2030-09-04",
            trip_id = "trip-a",
        )
        val canonical = Trip(
            id = "timeline-ext-compatibility-id",
            title = "External canonical",
            departureAtMillis = 0L,
            stops = emptyList(),
            recordOrigin = TripRecordOrigin.EXTERNAL_BACKING,
            blablaProfileUuid = "profile-a",
            blablaTripId = "trip-a",
            tripKey = "canonical-authoritative-id",
        )
        assertTrue(strongExternalSnapshotIdentityMatches0387("canonical-authoritative-id", canonical, source))
        assertFalse(strongExternalSnapshotIdentityMatches0387("timeline-ext-compatibility-id", canonical, source))
    }

    @Test
    fun publicationIdempotencyKeyContainsTenantTripAndRevision() {
        val r55 = publicationEventId0387("tenant-a", "trip-a", 55)
        assertEquals(r55, publicationEventId0387("tenant-a", "trip-a", 55))
        assertNotEquals(r55, publicationEventId0387("tenant-a", "trip-a", 56))
        assertNotEquals(r55, publicationEventId0387("tenant-b", "trip-a", 55))
        assertNotEquals(r55, publicationEventId0387("tenant-a", "trip-b", 55))
    }

    @Test
    fun deliveredSemanticEventIsNotEternalProofWhenRemoteProjectionDiverges() {
        val snapshot = TripPublicationSnapshot0387(semanticSignature = "same-state")
        val delivered = TripPublicationOutboxEvent0387(
            id = "event-7",
            tenantId = "tenant-a",
            canonicalTripId = "trip-a",
            revision = 7,
            operation = TripPublicationOperation0387.UPSERT_EXTERNAL,
            mutationType = "COLLECTOR",
            source = "TEST",
            snapshot = snapshot,
            status = TripPublicationStatus0387.DELIVERED,
        )
        assertTrue(
            shouldDeduplicatePublicationEvent0410(
                latest = delivered,
                operation = TripPublicationOperation0387.UPSERT_EXTERNAL,
                snapshot = snapshot,
                remoteProjectionDivergenceObserved = false,
            ),
        )
        assertFalse(
            shouldDeduplicatePublicationEvent0410(
                latest = delivered,
                operation = TripPublicationOperation0387.UPSERT_EXTERNAL,
                snapshot = snapshot,
                remoteProjectionDivergenceObserved = true,
            ),
        )
        assertTrue(
            shouldDeduplicatePublicationEvent0410(
                latest = delivered.copy(status = TripPublicationStatus0387.PENDING),
                operation = TripPublicationOperation0387.UPSERT_EXTERNAL,
                snapshot = snapshot,
                remoteProjectionDivergenceObserved = true,
            ),
        )
        assertTrue(
            shouldDeduplicatePublicationEvent0410(
                latest = delivered.copy(status = TripPublicationStatus0387.FAILED_RETRYABLE),
                operation = TripPublicationOperation0387.UPSERT_EXTERNAL,
                snapshot = snapshot,
                remoteProjectionDivergenceObserved = true,
            ),
        )
    }

    @Test
    fun outboxIsDurableTenantScopedAndSupportsRetryRebaseAndSupersede() {
        val outbox = source("TripPublicationOutbox0387.kt")
        assertTrue(outbox.contains("getSharedPreferences(PREFS, Context.MODE_PRIVATE)"))
        assertTrue(outbox.contains("tenantScope.key(KEY_EVENTS)"))
        assertTrue(outbox.contains("tenantScope.key(KEY_REVISIONS)"))
        assertTrue(outbox.contains(".commit()"))
        assertTrue(outbox.contains("FAILED_RETRYABLE"))
        assertTrue(outbox.contains("FAILED_FINAL"))
        assertTrue(outbox.contains("SUPERSEDED"))
        assertTrue(outbox.contains("processing_lease_expired"))
        assertTrue(outbox.contains("fun rebase("))
        assertTrue(outbox.contains("superseded_by_revision_"))
        assertTrue(outbox.contains("publicationEventId0387(target.tenantId, target.canonicalTripId, nextRevision)"))
        assertTrue(outbox.contains("rebaseCount0453"))
        assertTrue(outbox.contains("MAX_STALE_REBASES_0453"))
        assertTrue(outbox.contains("TRIP_MUTATION_OUTBOX_READBACK_PENDING_0453"))
        assertTrue(outbox.contains("publicMirrorProjectionCurrent0411()"))
        assertFalse(outbox.contains("projection_replay_same_logical_revision"))
    }

    @Test
    fun genericUiChangesNoLongerTriggerFullAgendaSync() {
        val activity = source("TripsActivity.kt")
        assertFalse(activity.contains("publicAgendaSyncRevision"))
        assertFalse(activity.contains("createPublicAgendaSyncCoordinator0373"))
        assertFalse(activity.contains("PublicBookingRemoteSync0296.pullAndReconcile"))
        assertTrue(activity.contains("AgendaBackgroundSync0392.enqueueImmediate"))
        assertTrue(activity.contains("onChanged = { text -> refresh(); message = text }"))
        assertTrue(activity.contains("TripMutationCoordinator0387(activity, store)"))
    }

    @Test
    fun publicBookingReconcileQueuesOnlyChangedCanonicalTrips() {
        val bookingSync = source("PublicBookingSync0296.kt")
        assertTrue(bookingSync.contains("changed.forEach { tripId ->"))
        assertTrue(bookingSync.contains("mutationCoordinator.recordLocalMutation("))
        assertTrue(bookingSync.contains("canonicalTripId = tripId"))
        assertTrue(bookingSync.contains("mutationCoordinator.drainPending(canonicalTripIds = changed)"))
        assertFalse(bookingSync.contains("publicAgendaSyncRevision"))
    }

    @Test
    fun protectedBookingActionsAreLocalFirstOutboxAndNeverDirectHttpOrBlabla() {
        val passenger = source("PassengerTimelineUi.kt")
        val autoSync = source("PublicAgendaAutoSync0300.kt")
        val remote = source("TripRemoteApi.kt")
        assertFalse(passenger.contains("onSyncSeatsOnly?.invoke()"))
        assertFalse(passenger.contains("TripRemoteApi(settings).updateDriverPassengerOperationalStatus("))
        assertFalse(passenger.contains("TripRemoteApi(settings).decideDriverBooking("))
        assertFalse(passenger.contains("updateProtectedDriverBooking(remoteTripId"))
        assertTrue(passenger.contains("mutationCoordinator.recordLocalMutation("))
        assertFalse(passenger.contains("mutationCoordinator.drainPending()"))
        assertTrue(passenger.contains("AgendaBackgroundSync0392.enqueueImmediate"))
        assertTrue(passenger.contains("BlaBlaCar is never synchronized automatically after an internal mutation."))
        assertTrue(remote.contains("protectedBookings: List<DriverProtectedBookingSnapshot>"))
        assertTrue(autoSync.contains("protectedBookings = if (entityRevision > 0L)"))
        assertTrue(autoSync.contains("booking.operationalStatus.name"))
        assertTrue(autoSync.contains("booking.paymentStatus.name"))
        assertTrue(autoSync.contains("booking.lastDriverSelection.trim()"))
    }

    @Test
    fun timelineNoLongerExposesManualExactCardSynchronization() {
        val timeline = source("TripTimelineUi.kt")
        assertFalse(timeline.contains("onResult = { nextResponse ->"))
        assertFalse(timeline.contains("recordExternalManualMutation("))
        assertFalse(timeline.contains("manual_card_shortcut"))
        assertFalse(timeline.contains("syncExternalTripIncremental"))
        assertFalse(timeline.contains("AgendaAutomaticSyncTimelineStatus0398("))
        assertTrue(timeline.contains("AgendaTimelineDownloadAction0399"))
    }

    @Test
    fun operationalTimelineHasNoBulkClearAndKeepsLifecycleTombstoneInfrastructure() {
        val timeline = source("TripTimelineUi.kt")
        val outbox = source("TripPublicationOutbox0387.kt")
        assertFalse(timeline.contains("Remover da operação + Agenda"))
        assertFalse(timeline.contains("recordTombstone("))
        assertFalse(timeline.contains("recordExternalTombstone("))
        assertTrue(timeline.contains("clearLegacyBulkHiddenOnce0398()"))
        assertTrue(outbox.contains("publicationTombstone = true"))
        assertTrue(outbox.contains("status = TripStatus.CANCELLED"))
        assertTrue(outbox.contains("historyPreserved=true blablaMutation=false"))
    }

    @Test
    fun backendRejectsOldRevisionsAndProtectsVersionedStateFromLegacyFullSync() {
        val api = backend()
        val start = api.indexOf("async function reconcileDriverCapacitySnapshot")
        val end = api.indexOf("async function upsertDriverCapacityBooking", start)
        assertTrue(start >= 0 && end > start)
        val fn = api.substring(start, end)
        assertTrue(fn.contains("entityRevision < currentEntityRevision"))
        assertTrue(fn.contains("legacyAfterVersioned"))
        assertTrue(fn.contains("stale: true"))
        assertTrue(fn.contains("publication_revision_conflict"))
        assertTrue(fn.contains("publicationRevision: deterministicRequest ? entityRevision : currentEntityRevision"))
        assertTrue(fn.contains("rawProtectedBookings"))
        assertTrue(fn.contains("normalizeProtectedSnapshotBooking"))
        assertTrue(fn.contains("protected_snapshot_requires_revision"))
        assertTrue(fn.contains("ANDROID_OUTBOX_SNAPSHOT"))
    }

    @Test
    fun publicAndDriverBookingMutationsAdvanceCanonicalTripRevisionAtomically() {
        val api = backend()
        listOf(
            "async function createBooking",
            "async function cancelPublicBooking",
            "async function updatePublicBooking",
            "async function mutateDriverBookingDecision",
            "async function mutateDriverPassengerOperationalStatus",
            "async function mutateProtectedBooking",
            "async function updatePassengerBooking",
            "async function cancelPassengerBooking",
            "async function cancelActiveBookingsForBlockedPassenger",
        ).forEach { name ->
            val start = api.indexOf(name)
            assertTrue(start >= 0, "$name missing")
            val next = api.indexOf("\nasync function ", start + name.length).let { if (it < 0) api.length else it }
            val fn = api.substring(start, next)
            assertTrue(fn.contains("publicationRevision"), "$name must advance or preserve entity revision explicitly")
            assertTrue(fn.contains("publicationEventId") || fn.contains("entityRevision"), "$name must retain publication event/revision evidence")
        }
    }

    @Test
    fun serverAppliedMutationsAreJournaledDurablyWithoutSecondNetworkPublication() {
        val api = backend()
        val outbox = source("TripPublicationOutbox0387.kt")
        assertTrue(api.contains("function writeDeliveredTripPublicationOutbox"))
        assertTrue(api.contains("db.collection(\"tripPublicationOutbox\")"))
        assertTrue(api.contains("status: \"DELIVERED\""))
        assertTrue(api.contains("payloadReference: immutableSourceEventId ? \"tripChangeEvents/\""))
        assertTrue(api.contains("PASSENGER_MY_TRIPS_EDIT"))
        assertTrue(api.contains("PASSENGER_MY_TRIPS_CANCEL"))
        assertTrue(api.contains("PASSENGER_BLOCKED_BOOKINGS_CANCELLED"))
        assertTrue(outbox.contains("fun recordDeliveredAtRevision("))
        assertTrue(outbox.contains("fun recordRemoteAppliedLocal("))
        assertTrue(outbox.contains("remote_applied_revision_"))
    }

    @Test
    fun tenantWideSeatSettingUsesCanonicalDurableFanoutAndNeverDependsOnCompose() {
        val activity = source("TripsActivity.kt")
        val background = source("AgendaBackgroundSync0392.kt")
        assertTrue(background.contains("reconcileTenantSeatAllocation0395("))
        assertTrue(background.contains("recordExternalTenantMutation("))
        assertTrue(background.contains("externalRetryPending"))
        assertTrue(background.contains("fullSyncRequested=false"))
        assertTrue(activity.contains("reconcileTenantSeatAllocation0395("))
        assertFalse(activity.contains("TENANT_SEAT_ALLOCATION_EXACT_IMPACT"))
        assertFalse(activity.contains("externalAffected"))
    }

    @Test
    fun failuresRetainClassMessageAndRootCauseEvidence() {
        val outbox = source("TripPublicationOutbox0387.kt")
        assertTrue(outbox.contains("exceptionClass="))
        assertTrue(outbox.contains("exceptionMessage="))
        assertTrue(outbox.contains("rootCauseClass="))
        assertTrue(outbox.contains("rootCauseMessage="))
    }
}
