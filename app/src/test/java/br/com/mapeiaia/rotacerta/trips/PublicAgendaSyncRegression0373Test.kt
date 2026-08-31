package br.com.mapeiaia.rotacerta.trips

import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PublicAgendaSyncRegression0373Test {
    private val profileUuid = "7371f028-9c55-4903-8444-308015823efd"

    private fun externalEntry(tripId: String = "ride-0373") = TripTimelineEntry(
        tripId = "timeline-source",
        profileId = profileUuid,
        profileLabel = "Conta",
        departureAtMillis = 4_000_000_000_000L,
        arrivalAtMillis = null,
        origin = "A",
        destination = "B",
        status = TripStatus.PUBLISHED,
        capacity = 4,
        minimumOccupiedSeats = 0,
        maximumOccupiedSeats = 0,
        sourcePassengerSeats = emptyMap(),
        blablaTripId = tripId,
        blablaTripHref = "https://www.blablacar.com.br/rides/offer/$tripId",
        blablaProfileUuid = profileUuid,
        blablaPassengerRosterComplete = true,
        blablaPublishedSeats = 2,
        rotaCertaSeatAllocation = 2,
    )

    @Test
    fun externalBackingHasExplicitOriginAndNeverBecomesLocalPublisherSource() {
        val trip = buildTimelineExternalBackingTrip(externalEntry(), 4)
        assertEquals(TripRecordOrigin.EXTERNAL_BACKING, trip.recordOrigin)
        assertEquals(TripRecordOrigin.EXTERNAL_BACKING, resolvedTripRecordOrigin(trip))
        assertFalse(trip.isCanonicalLocalPublishSource())
    }

    @Test
    fun legacy0372BackingIsRecoveredFromStrongIdentityAndDeterministicId() {
        val created = buildTimelineExternalBackingTrip(externalEntry("legacy-trip"), 4)
        val legacy = created.copy(recordOrigin = TripRecordOrigin.LOCAL)
        assertTrue(legacy.id.startsWith("timeline-ext-"))
        assertEquals(TripRecordOrigin.EXTERNAL_BACKING, resolvedTripRecordOrigin(legacy))
        assertFalse(legacy.isCanonicalLocalPublishSource())
    }

    @Test
    fun prefixOrBlaBlaMetadataAloneNeverProvesExternalOrigin() {
        val linkedLocal = Trip(
            id = "local-real-trip",
            title = "A → B",
            departureAtMillis = 4_000_000_000_000L,
            status = TripStatus.PUBLISHED,
            stops = listOf(
                TripStop(id = "a", order = 0, name = "A"),
                TripStop(id = "b", order = 1, name = "B"),
            ),
            blablaProfileUuid = profileUuid,
            blablaTripId = "ride-0373",
            blablaManageUrl = "https://www.blablacar.com.br/rides/offer/ride-0373",
        )
        val fakePrefix = linkedLocal.copy(id = "timeline-ext-not-derived-from-identity")
        assertEquals(TripRecordOrigin.LOCAL, resolvedTripRecordOrigin(linkedLocal))
        assertEquals(TripRecordOrigin.LOCAL, resolvedTripRecordOrigin(fakePrefix))
        assertTrue(linkedLocal.isCanonicalLocalPublishSource())
        assertTrue(fakePrefix.isCanonicalLocalPublishSource())
    }

    @Test
    fun recordOriginSurvivesSerializationReload() {
        val trip = buildTimelineExternalBackingTrip(externalEntry("persisted"), 4)
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val restored = json.decodeFromString<Trip>(json.encodeToString(trip))
        assertEquals(TripRecordOrigin.EXTERNAL_BACKING, restored.recordOrigin)
        assertEquals(TripRecordOrigin.EXTERNAL_BACKING, resolvedTripRecordOrigin(restored))
    }

    @Test
    fun clearTimelinePreservesHistoryButCannotRepublishBackingAsLocal() {
        val timeline = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()
        val publicSync = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PublicAgendaAutoSync0300.kt").readText()
        assertTrue(timeline.contains("clearSynchronizedTimelineData()"))
        assertTrue(timeline.contains("externalBackingHistoryPreserved"))
        assertTrue(timeline.contains("externalBackingPublishableAsLocal=false"))
        assertTrue(publicSync.contains(".filter(Trip::isCanonicalLocalPublishSource)"))
        assertTrue(publicSync.contains("externalBackingsExcluded"))
    }

    @Test
    fun clearedExternalSourceMayLeaveHistoryBackingButEffectiveLocalPublishSetIsEmpty() {
        val backing = buildTimelineExternalBackingTrip(externalEntry("clear-reload"), 4)
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val reloadedPersistedTrips = listOf(
            json.decodeFromString<Trip>(json.encodeToString(backing)),
        )
        val clearedCollectorTrips = emptyList<BlaBlaCollectorTrip>()

        assertEquals(1, reloadedPersistedTrips.size)
        assertTrue(clearedCollectorTrips.isEmpty())
        assertTrue(reloadedPersistedTrips.none(Trip::isCanonicalLocalPublishSource))
        assertEquals(
            TripRecordOrigin.EXTERNAL_BACKING,
            resolvedTripRecordOrigin(reloadedPersistedTrips.single()),
        )
    }

    @Test
    fun illegalStateExceptionBoundaryIsNotReachableForExternalBackingThroughLocalDiscovery() {
        val backing = buildTimelineExternalBackingTrip(externalEntry("illegal-state-regression"), 4)
        val remoteApi = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripRemoteApi.kt").readText()
        assertFalse(backing.isCanonicalLocalPublishSource())
        assertTrue(remoteApi.contains("throw IllegalStateException(\"Servidor respondeu HTTP"))
    }

    @Test
    fun repeatedIdenticalRequestsRunOnlyOneEffectiveSync(): Unit = runBlocking {
        val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val executions = AtomicInteger(0)
            val coordinator = PublicAgendaSyncCoordinator0373(
                scope = workerScope,
                signatureProvider = { "same-signature" },
                syncAction = {
                    executions.incrementAndGet()
                    entered.complete(Unit)
                    release.await()
                    PublicAgendaAutoSyncResult(localPublished = 1)
                },
            )
            val completion = async { coordinator.completions.first() }
            coordinator.request(4, "first")
            withTimeout(2_000L) { entered.await() }
            repeat(20) { coordinator.request(4, "duplicate-$it") }
            release.complete(Unit)
            withTimeout(2_000L) { completion.await() }
            delay(150L)
            assertEquals(1, executions.get())
        } finally {
            workerScope.cancel()
        }
    }

    @Test
    fun partialFailedSyncIsNotMemorizedAsIdenticalSuccess(): Unit = runBlocking {
        val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val executions = AtomicInteger(0)
            val coordinator = PublicAgendaSyncCoordinator0373(
                scope = workerScope,
                signatureProvider = { "stable-failed-input" },
                syncAction = {
                    if (executions.incrementAndGet() == 1) {
                        PublicAgendaAutoSyncResult(failures = 1)
                    } else {
                        PublicAgendaAutoSyncResult(localPublished = 1)
                    }
                },
            )
            coordinator.request(4, "first-partial")
            val firstCompletion = withTimeout(2_000L) { coordinator.completions.first() }
            assertEquals(1, firstCompletion.result?.failures)
            assertEquals(1, executions.get())

            coordinator.request(4, "explicit-retry")
            withTimeout(2_000L) {
                while (executions.get() < 2) delay(10L)
            }
            assertEquals(2, executions.get())
        } finally {
            workerScope.cancel()
        }
    }

    @Test
    fun realSourceChangeDuringSyncProducesExactlyOneFollowUpPass(): Unit = runBlocking {
        val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val version = AtomicInteger(1)
            val executions = AtomicInteger(0)
            val firstEntered = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val events = java.util.Collections.synchronizedList(mutableListOf<String>())
            val coordinator = PublicAgendaSyncCoordinator0373(
                scope = workerScope,
                signatureProvider = { "signature-${version.get()}" },
                syncAction = {
                    val count = executions.incrementAndGet()
                    if (count == 1) {
                        firstEntered.complete(Unit)
                        releaseFirst.await()
                    }
                    PublicAgendaAutoSyncResult(externalPublished = 1)
                },
                eventSink = { event, _ -> events += event },
            )
            coordinator.request(4, "initial")
            withTimeout(2_000L) { firstEntered.await() }
            version.set(2)
            coordinator.request(4, "changed")
            releaseFirst.complete(Unit)
            withTimeout(2_000L) {
                while (executions.get() < 2) delay(10L)
            }
            delay(150L)
            assertEquals(2, executions.get())
            assertTrue(events.contains("CAPACITY_PUBLIC_SYNC_COALESCED"))
            assertTrue(events.contains("CAPACITY_PUBLIC_SYNC_DIRTY_PENDING"))
        } finally {
            workerScope.cancel()
        }
    }

    @Test
    fun tenantScopedCoordinatorsDoNotBlockEachOther(): Unit = runBlocking {
        val scopeA = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val scopeB = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val enteredA = CompletableDeferred<Unit>()
            val enteredB = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val coordinatorA = PublicAgendaSyncCoordinator0373(
                scopeA,
                signatureProvider = { "tenant-a" },
                syncAction = {
                    enteredA.complete(Unit)
                    release.await()
                    PublicAgendaAutoSyncResult(localPublished = 1)
                },
            )
            val coordinatorB = PublicAgendaSyncCoordinator0373(
                scopeB,
                signatureProvider = { "tenant-b" },
                syncAction = {
                    enteredB.complete(Unit)
                    release.await()
                    PublicAgendaAutoSyncResult(localPublished = 1)
                },
            )
            coordinatorA.request(1, "tenant-a")
            coordinatorB.request(2, "tenant-b")
            withTimeout(2_000L) {
                enteredA.await()
                enteredB.await()
            }
            release.complete(Unit)
        } finally {
            scopeA.cancel()
            scopeB.cancel()
        }
    }

    @Test
    fun composeEffectOnlyQueuesAndDoesNotOwnLongRunningSync() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()
        val coordinator = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PublicAgendaSyncCoordinator0373.kt").readText()
        assertTrue(source.contains("publicAgendaSyncCoordinator.request("))
        assertFalse(source.contains("TripApp.publicAgendaEffect"))
        assertFalse(source.contains("appSettings.rotaCertaSeatAllocation,\n        appSettings.rotaCertaSeatAllocation"))
        assertTrue(coordinator.contains("Channel.CONFLATED"))
        assertTrue(coordinator.contains("CAPACITY_PUBLIC_SYNC_COALESCED"))
        assertTrue(coordinator.contains("CAPACITY_PUBLIC_SYNC_IDENTICAL_SKIPPED"))
        assertTrue(coordinator.contains("CAPACITY_PUBLIC_SYNC_DIRTY_PENDING"))
    }

    @Test
    fun bookingReconcileRunsOffMainAndRejectsExternalBackingsAsLocalCandidates() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PublicBookingSync0296.kt").readText()
        assertTrue(source.contains("withContext(kotlinx.coroutines.Dispatchers.IO)"))
        assertTrue(source.contains("it.isCanonicalLocalPublishSource()"))
        assertTrue(source.contains("externalBackingsExcluded"))
        assertTrue(source.contains("bookingSnapshot"))
        assertTrue(source.contains("BOOKING_FETCH_CONCURRENCY_0373 = 4"))
        assertTrue(source.contains("BOOKING_RECONCILE_PHASES_0373"))
        assertTrue(source.indexOf("\"BOOKING_REMOTE_FETCH\"") < source.indexOf("\"BOOKING_COMPARE\""))
        assertTrue(source.indexOf("\"BOOKING_COMPARE\"") < source.indexOf("\"BOOKING_IMPORT\""))
    }

    @Test
    fun firebaseBookingServiceNeverBlocksItsDeliveryThread() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/RotaCertaBookingMessagingService.kt").readText()
        assertFalse(source.contains("runBlocking"))
        assertTrue(source.contains("CoroutineScope(SupervisorJob() + Dispatchers.IO)"))
        assertTrue(source.contains("serviceScope.launch"))
        assertTrue(source.contains("serviceScope.cancel()"))
    }
}
