package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaImmediateCardDelta0431Test {
    private fun source(name: String): String =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/" + name).readText()

    @Test
    fun passengerPushTargetsOnlyTheChangedPublicCard() {
        val messaging = source("RotaCertaBookingMessagingService.kt")
        val background = source("AgendaBackgroundSync0392.kt")

        assertTrue(messaging.contains("AgendaBackgroundSync0392.enqueueCardDelta0431("))
        assertTrue(messaging.contains("remoteTripId = remoteTripId"))
        assertTrue(background.contains("INPUT_REMOTE_TRIP_ID_0431"))
        assertTrue(background.contains("CARD_DELTA_WORK_0431 + \"-\" + sha256TripPublication0387(targetRemoteTripId).take(16)"))
        assertTrue(background.contains("targetedBookingRemoteTripId0431"))
        assertTrue(background.contains("runBookingCardDelta0431("))
        assertTrue(background.contains("targetRemoteTripId = remoteTripId"))
        assertTrue(background.contains("fullSyncRequested=false"))
    }

    @Test
    fun passengerBookingBecomesTimelineCanonicalThenEchoesSameCardBackToAgenda() {
        val booking = source("PublicBookingSync0296.kt")
        val outbox = source("TripPublicationOutbox0387.kt")

        assertTrue(booking.contains("target.remoteTripId == targetRemoteTripId"))
        assertTrue(booking.contains("mutationCoordinator.ensureRevisionAtLeast(tripId, remoteRevision)"))
        assertTrue(booking.contains("PUBLIC_BOOKING_CANONICAL_ECHO_0431"))
        assertTrue(booking.contains("source = \"PUBLIC_AGENDA_PUSH_PULL\""))
        assertTrue(booking.contains("canonicalEchoRequired=true"))
        assertTrue(booking.contains("drainPending(canonicalTripIds = changed)"))
        assertFalse(booking.contains("mutationCoordinator.recordRemoteAppliedLocal("))
        assertTrue(outbox.contains("canonicalTripIds: Set<String>? = null"))
        assertTrue(outbox.contains("canonicalTripIds == null || it.canonicalTripId in canonicalTripIds"))
    }

    @Test
    fun collectorPublishesOnlyCanonicalCardsWhoseSnapshotChanged() {
        val background = source("AgendaBackgroundSync0392.kt")
        val collector = source("BlaBlaAutomaticCollection0400.kt")
        val dynamic = source("BlaBlaDynamicAccounts.kt")

        assertTrue(background.contains("publicationCanonicalTripIds0431"))
        assertTrue(background.contains("publicationCanonicalTripIds0431 += event.canonicalTripId"))
        assertTrue(background.contains("runCollectorCardDelta0431("))
        assertTrue(background.contains("canonicalTripIds = batch.publicationCanonicalTripIds0431"))
        assertTrue(background.contains("collectorDeltaMutexes0431"))
        assertTrue(collector.contains("AgendaBackgroundSync0392.enqueueCollectorDelta0431"))
        assertTrue(collector.contains("account_" + '$' + "{normalizedResult.lowercase()}"))
        assertTrue(collector.contains("run_terminal:" + '$' + "result"))
        val checkpointScope = dynamic.substring(
            dynamic.indexOf("private fun saveProgressSnapshot(reason: String)"),
            dynamic.indexOf("private fun clearPendingCardState()"),
        )
        assertTrue(checkpointScope.contains("publishCurrentSessions("))
        assertTrue(checkpointScope.contains("AgendaBackgroundSync0392.enqueueCollectorDelta0431("))
        assertTrue(checkpointScope.contains("\"card_checkpoint:\" + reason"))
        val finalSnapshotScope = dynamic.substring(
            dynamic.indexOf("private fun saveFinalSnapshotOnce(verified: Boolean)"),
            dynamic.indexOf("private fun captureTripDetail("),
        )
        assertTrue(finalSnapshotScope.contains("if (targetTripId.isNotBlank())"))
        assertTrue(finalSnapshotScope.contains("AgendaBackgroundSync0392.enqueueCollectorDelta0431("))
        assertTrue(finalSnapshotScope.contains("\"exact_card_final\""))
        assertTrue(background.contains("TIMELINE_STATE_EMITTED_0451"))
        assertTrue(background.contains("observer=BookingRealtimeEvents0356"))
        assertTrue(background.contains("collectorCardAttestationIntegrity0433"))
        assertTrue(background.contains("serverAckRequired=true"))
        assertTrue(background.contains("cycle.projectionValidated0411 == cycle.projectionExpected0411"))
        assertTrue(background.contains("publicUpdated=\$publicUpdated"))
    }

    @Test
    fun externalIncrementalAcceptsCanonicalSnapshotTripKeyBeforePublicBinding() {
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
            externalIncrementalCanonicalIdentityMatches0452(
                resolvedInternalTripId = "timeline-ext-existing",
                expectedStrongId = "ext-strong-other",
                boundInternalTripId = "",
                canonicalTripSnapshot = canonical,
                source = source,
            ),
        )
        assertFalse(
            externalIncrementalCanonicalIdentityMatches0452(
                resolvedInternalTripId = "timeline-ext-existing",
                expectedStrongId = "ext-strong-other",
                boundInternalTripId = "",
                canonicalTripSnapshot = canonical,
                source = source.copy(trip_id = "trip-b"),
            ),
        )
        assertTrue(
            externalIncrementalCanonicalIdentityMatches0452(
                resolvedInternalTripId = "ext-strong-other",
                expectedStrongId = "ext-strong-other",
                boundInternalTripId = "",
                canonicalTripSnapshot = null,
                source = source,
            ),
        )
        assertTrue(
            externalIncrementalCanonicalIdentityMatches0452(
                resolvedInternalTripId = "timeline-bound",
                expectedStrongId = "ext-strong-other",
                boundInternalTripId = "timeline-bound",
                canonicalTripSnapshot = null,
                source = source,
            ),
        )
    }

    @Test
    fun timelineLazyCardsDoNotDeserializePersistentStoresWhileScrolling() {
        val ui = source("TripTimelineUi.kt")
        val capacity = source("TripGlobalPassengerFlow0256.kt")
        val control = source("BlaBlaTripControl0407.kt")
        val cardScope = ui.substring(
            ui.indexOf("private fun TimelineEntryCard("),
            ui.indexOf("internal data class TimelineQuickPassengerOption"),
        )

        assertTrue(ui.contains("registeredAccounts0432"))
        assertTrue(ui.contains("commandAuditsByCard0432"))
        assertTrue(ui.contains("bookingsSnapshot0432 = bookings"))
        assertFalse(cardScope.contains("BlaBlaPublicationSeatSyncStateStore(context).get"))
        assertFalse(cardScope.contains("BlaBlaTripCommandStatusStore0407(context).get"))
        assertFalse(cardScope.contains("BlaBlaTripControlEvents0407.revision.collectAsState"))
        assertFalse(cardScope.contains("resolveBlaBlaTripTarget0407(context, entry)"))
        assertTrue(cardScope.contains("timelineDesiredSeatSyncPlan(entry, trip, bookingsSnapshot0432)"))
        assertTrue(capacity.contains("bookingsSnapshot: List<Booking>"))
        assertTrue(control.contains("accounts: List<BlaBlaDynamicAccount>"))
    }

    @Test
    fun fullReconcileIsStrictlyTimelineToPublicAgendaAndDoesNotMaterializeCollectorSnapshot() {
        val background = source("AgendaBackgroundSync0392.kt")
        val collectorScope = background.substring(
            background.indexOf("val reconcileCollectorSnapshot"),
            background.indexOf("fun collectorResponseForThisCycle0407"),
        )
        assertTrue(collectorScope.contains("AgendaBackgroundSyncMode0392.COLLECTOR_RECONCILE"))
        assertFalse(collectorScope.contains("FULL_RECONCILE"))
        assertFalse(agendaBackgroundSyncRequestsCollector0430("admin_full_reconcile:test"))
        assertFalse(agendaBackgroundSyncRequestsCollector0430("manual"))
    }
}
