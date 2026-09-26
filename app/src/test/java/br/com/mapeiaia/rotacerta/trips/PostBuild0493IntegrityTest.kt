package br.com.mapeiaia.rotacerta.trips

import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostBuild0493IntegrityTest {
    private fun source(path: String): String = File("src/main/java/$path").readText()

    private fun event(
        traceId: String,
        stage: String,
        monotonicNs: Long,
        details: String = "",
    ) = UnifiedDebugEventStore.SnapshotEvent(
        atMillis = monotonicNs / 1_000_000L,
        monotonicNs = monotonicNs,
        stage = stage,
        packageName = "br.com.mapeiaia.rotacerta",
        details = "traceId=$traceId $details".trim(),
        threadName = "main",
    )

    @Test
    fun latestCompletedAgendaTraceNeverMixesOverlappingSessionsOrBackground() {
        val events = listOf(
            event("ag-a", "AGENDA_OPEN_REQUESTED", 100_000_000L),
            event("trace_unavailable", "OPERATION_START", 800_000_000L, "operation=BOOKING_RECONCILE operationId=bg-1"),
            event("ag-b", "AGENDA_OPEN_REQUESTED", 300_000_000L),
            event("ag-a", "AGENDA_FIRST_INTERACTIVE_FRAME", 220_000_000L),
            event("ag-c", "AGENDA_OPEN_REQUESTED", 500_000_000L),
            event("ag-b", "AGENDA_FIRST_INTERACTIVE_FRAME", 460_000_000L),
            event("trace_unavailable", "OPERATION_END", 900_000_000L, "operation=BOOKING_RECONCILE operationId=bg-1 durationMs=100"),
            event("ag-c", "AGENDA_FIRST_INTERACTIVE_FRAME", 650_000_000L),
            event("ag-d", "AGENDA_OPEN_REQUESTED", 700_000_000L),
        )

        val selected = selectLatestAgendaTrace0493(events)

        assertEquals("ag-c", selected.traceId)
        assertTrue(selected.complete)
        assertFalse(selected.inconsistent)
        assertEquals(500_000_000L, selected.openRequested?.monotonicNs)
        assertEquals(650_000_000L, selected.firstInteractive?.monotonicNs)
        assertTrue(selected.events.all { it.details.contains("traceId=ag-c") })
    }

    @Test
    fun inconsistentTraceIsMarkedInvalidInsteadOfProducingDuration() {
        val selected = selectLatestAgendaTrace0493(
            listOf(
                event("ag-z", "AGENDA_SCREEN", 400_000_000L),
                event("ag-z", "AGENDA_OPEN_REQUESTED", 500_000_000L),
                event("ag-z", "AGENDA_FIRST_INTERACTIVE_FRAME", 650_000_000L),
            ),
        )

        assertEquals("ag-z", selected.traceId)
        assertTrue(selected.inconsistent)
        assertFalse(selected.complete)
        assertNull(selected.firstInteractive)
    }

    @Test
    fun canonicalLocalBookingSourceSuppressesCompetingExternalBinding() {
        val localShadow = PublicExternalTripBinding(
            remoteTripId = "remote-shadow",
            publicToken = "public-shadow",
            bookingTripId = "canonical-local",
            title = "A → B",
            departureAtMillis = 1_000L,
            capacity = 4,
            stops = listOf(
                TripStop(order = 0, name = "A"),
                TripStop(order = 1, name = "B"),
            ),
        )
        val realExternal = localShadow.copy(
            remoteTripId = "remote-external",
            publicToken = "public-external",
            bookingTripId = "external-backing",
        )

        val selected = canonicalBookingExternalBindings0493(
            localCandidateTripIds = setOf("canonical-local"),
            externalBindings = listOf(localShadow, realExternal),
        )

        assertEquals(listOf(realExternal), selected)
    }

    @Test
    fun bookingReconcileCommitsCanonicalStateBeforeTransportAndDoesNotDrainNetwork() {
        val reconcile = source("br/com/mapeiaia/rotacerta/trips/PublicBookingSync0296.kt")
        val background = source("br/com/mapeiaia/rotacerta/trips/AgendaBackgroundSync0392.kt")

        listOf(
            "BOOKING_POST_IMPORT",
            "BOOKING_INVENTORY_RECALC",
            "BOOKING_OUTBOX_ENQUEUE",
            "BOOKING_PUBLICATION_HANDOFF",
            "transportAwaited=false",
            "recordRemoteAppliedLocal",
            "canonicalBookingExternalBindings0493",
        ).forEach { assertTrue(reconcile.contains(it)) }
        assertFalse(reconcile.contains("drainPending(canonicalTripIds = changed)"))
        assertTrue(background.contains("BOOKING_PUBLICATION_DRAIN"))
        assertTrue(background.contains("canonicalTripIds = booking.changedTripIds"))
    }

    @Test
    fun staleLocalSnapshotSignatureChangesWhenProtectedBookingStateAdvances() {
        val trip = Trip(
            id = "canonical-trip",
            title = "A → B",
            departureAtMillis = 1_000L,
            capacity = 4,
            status = TripStatus.PUBLISHED,
            stops = listOf(
                TripStop(id = "a", order = 0, name = "A"),
                TripStop(id = "b", order = 1, name = "B"),
            ),
            remoteId = "remote-trip",
            rotaCertaSeatAllocation = 0,
            canonicalStateHash = "state-10",
        )
        val revision10 = Booking(
            id = "booking-1",
            tripId = trip.id,
            passengerName = "Passenger",
            passengerContact = "contact",
            boardingStopId = "a",
            dropoffStopId = "b",
            seats = 1,
            status = BookingStatus.CONFIRMED,
            updatedAtMillis = 10L,
        )
        val revision11 = revision10.copy(
            operationalStatus = PassengerOperationalStatus.IN_CAR,
            updatedAtMillis = 11L,
        )

        val signature10 = localPublicationSnapshotSignature0493(trip, listOf(revision10), 0)
        val signature11 = localPublicationSnapshotSignature0493(trip, listOf(revision11), 0)

        assertFalse(signature10 == signature11)
    }

    @Test
    fun protectedBookingConflictRequiresCanonicalReconciliationAndNeverBlindRetry() {
        val conflict = TripRemoteApiException(
            httpMethod = "PUT",
            endpoint = "/capacity-snapshot",
            httpStatus = 409,
            backendErrorCode = "protected_booking_required",
            sanitizedResponse = "{}",
            requestId = "request",
            correlationId = "correlation",
        )
        assertFalse(publicationFailureRetryable0387(conflict))
        assertTrue(publicationFailureRequiresReconciliation0493(conflict))

        val background = source("br/com/mapeiaia/rotacerta/trips/AgendaBackgroundSync0392.kt")
        val outbox = source("br/com/mapeiaia/rotacerta/trips/TripPublicationOutbox0387.kt")
        assertTrue(background.contains("outbox_semantic_reconcile:"))
        assertTrue(background.contains("AgendaBackgroundSyncMode0392.FULL_RECONCILE"))
        assertTrue(outbox.contains("OUTBOX_REQUIRES_RECONCILIATION_0493"))
        assertTrue(outbox.contains("blindRetry=false"))
    }

    @Test
    fun forensicReportIsTraceScopedAndSeparatesGlobalOperations() {
        val report = source("br/com/mapeiaia/rotacerta/trips/AgendaForensicReport.kt")

        listOf(
            "summaryTraceId=",
            "traceComplete=",
            "mixedTraceDetected=",
            "FORENSIC_TRACE_INCONSISTENT",
            "--- BACKGROUND / GLOBAL OPERATIONS ---",
            "selectLatestAgendaTrace0493",
            "summaryEvents",
        ).forEach { assertTrue(report.contains(it)) }
        assertFalse(report.contains("val openRequested = agendaEvents.firstOrNull"))
        assertFalse(report.contains("val firstInteractive = agendaEvents.firstOrNull"))
    }

    @Test
    fun timelineAvoidsNoOpInvalidationAndBatchesHealthyCapacityDiagnostics() {
        val activity = source("br/com/mapeiaia/rotacerta/trips/TripsActivity.kt")
        val timeline = source("br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt")

        assertTrue(activity.contains("if (fanOut.localCanonicalUpdated > 0)"))
        assertTrue(activity.contains("OPERATIONAL_INVENTORY_RENDER_INVALIDATION_SKIPPED_0493"))
        assertTrue(timeline.contains("TIMELINE_CAPACITY_RESOLVED_BATCH_0493"))
        assertTrue(timeline.contains("TIMELINE_CAPACITY_ANOMALY_0493"))
        assertTrue(timeline.contains("representative=true"))
    }
}
