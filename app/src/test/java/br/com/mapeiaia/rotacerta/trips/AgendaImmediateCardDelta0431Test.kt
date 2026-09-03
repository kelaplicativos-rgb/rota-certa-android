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

        assertTrue(background.contains("publicationCanonicalTripIds0431"))
        assertTrue(background.contains("publicationCanonicalTripIds0431 += event.canonicalTripId"))
        assertTrue(background.contains("runCollectorCardDelta0431("))
        assertTrue(background.contains("canonicalTripIds = batch.publicationCanonicalTripIds0431"))
        assertTrue(background.contains("collectorDeltaMutexes0431"))
        assertTrue(collector.contains("AgendaBackgroundSync0392.enqueueCollectorDelta0431"))
        assertTrue(collector.contains("account_" + '$' + "{normalizedResult.lowercase()}"))
        assertTrue(collector.contains("run_terminal:" + '$' + "result"))
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
