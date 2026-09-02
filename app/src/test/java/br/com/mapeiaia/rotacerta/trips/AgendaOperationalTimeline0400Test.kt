package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgendaOperationalTimeline0400Test {
    private val timeline = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()
    private val background = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaBackgroundSync0392.kt").readText()
    private val coordinator = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaAutomaticCollection0400.kt").readText()
    private val dynamic = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()
    private val collector = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripBlaBlaCollector.kt").readText()
    private val timelineModule = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaCollectorTimelineModule.kt").readText()
    private val activity = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()

    @Test
    fun operationalTimelineIsCollectorProjectionNotAgendaProjection() {
        assertTrue(timeline.contains("BlaBlaTimelineAdapter.merge(emptyList(), collectorResponseForTimeline)"))
        assertFalse(timeline.contains("TripTimelineEngine.fromLocalAgenda(trips, bookings)"))
        assertTrue(timeline.contains("BlaBlaCollectorTimelineEvents0400.revision.collectAsState()"))
        assertTrue(timeline.contains("val publicResponseForTimeline: BlaBlaPublicSearchResponse? = null"))
        assertTrue(timeline.contains("val publicTimelineCards: List<BlaBlaPublicSearchCard> = emptyList()"))
    }

    @Test
    fun permanentSchedulerRequestsTheExistingCollectorAndNeverClaimsSuccessWhilePending() {
        assertTrue(background.contains("requestAutomaticCollector0400"))
        assertTrue(background.contains("BlaBlaAutomaticCollectionCoordinator0400.runPendingHeadless"))
        assertTrue(background.contains("cycle.collectorPending -> \"COLLECTOR_PENDING\""))
        assertTrue(background.contains("fullReconcileComplete = fullReconcileComplete"))
        assertTrue(background.contains("reason == \"blablacar_collection_result\""))
        assertTrue(coordinator.contains("BlaBlaDynamicSessionIntents.syncPayload"))
        assertTrue(coordinator.contains("visualHost = null"))
        assertFalse(coordinator.contains("startActivity("))
        assertFalse(coordinator.contains("FLAG_ACTIVITY_NEW_TASK"))
        assertFalse(coordinator.contains("WebView("))
        assertFalse(coordinator.contains("BlaBlaDomNormalizer"))
        assertFalse(coordinator.contains("captureRideList"))
    }

    @Test
    fun allConfiguredAccountsRunSequentiallyAndPartialAccountDoesNotStopTheQueue() {
        val targets = listOf("account-a", "account-b", "account-c")
        assertEquals(
            "account-a",
            nextAutomaticCollectorAccountId0400(targets, emptySet(), emptySet(), emptySet()),
        )
        assertEquals(
            "account-b",
            nextAutomaticCollectorAccountId0400(targets, setOf("account-a"), emptySet(), emptySet()),
        )
        assertEquals(
            "account-c",
            nextAutomaticCollectorAccountId0400(targets, setOf("account-a"), setOf("account-b"), emptySet()),
        )
        assertNull(
            nextAutomaticCollectorAccountId0400(
                targets,
                setOf("account-a", "account-c"),
                setOf("account-b"),
                emptySet(),
            ),
        )
        assertFalse(coordinator.contains("automatic_chain"))
        assertTrue(coordinator.contains("automaticChainOwnedByWorker=true"))
    }

    @Test
    fun terminalStatusDistinguishesCompletePartialAndFailed() {
        val complete = BlaBlaCollectorMonthResponse(
            status = "success",
            trips = listOf(sampleTrip("trip-a")),
            coverage = BlaBlaCollectorCoverage(complete_for_scope = true),
        )
        val partial = complete.copy(
            status = "partial",
            coverage = complete.coverage.copy(complete_for_scope = false),
        )
        val failed = BlaBlaCollectorMonthResponse(
            status = "blocked",
            trips = emptyList(),
            coverage = BlaBlaCollectorCoverage(complete_for_scope = false),
        )

        assertEquals("COMPLETE", automaticCollectorTerminalStatus0400(complete, 0, 2, 0))
        assertEquals("PARTIAL", automaticCollectorTerminalStatus0400(partial, 1, 2, 0))
        assertEquals("FAILED", automaticCollectorTerminalStatus0400(failed, 2, 2, 0))
        assertEquals("PENDING_AUTH", automaticCollectorTerminalStatus0400(complete, 0, 2, 1))
        assertEquals("NO_ACCOUNTS", automaticCollectorTerminalStatus0400(failed, 0, 0, 0))
    }

    @Test
    fun collectorKeepsExhaustiveTraversalProgressiveCheckpointsAndStrongIdentity() {
        assertTrue(dynamic.contains("RIDES_TRAVERSAL_SCAN"))
        assertTrue(dynamic.contains("RIDES_TRAVERSAL_SCROLL"))
        assertTrue(dynamic.contains("REQUIRED_STABLE_BOTTOM_PASSES"))
        assertTrue(dynamic.contains("resolvedCardTraversalKeys"))
        assertTrue(dynamic.contains("saveProgressSnapshot(\"card_complete\")"))
        assertTrue(dynamic.contains("BlaBlaTripIdentity.externalTripIdFromHref"))
        assertTrue(dynamic.contains("publishCurrentSessions("))
        assertTrue(collector.contains("fun resolveDistinct(trips: List<BlaBlaCollectorTrip>)"))
        assertTrue(timelineModule.contains("fun mergeSnapshotTrips("))
    }

    @Test
    fun timelineSnapshotAndAutomaticQueueAreTenantScoped() {
        assertTrue(collector.contains("private val tenantScope = RotaCertaTenantRegistry(appContext).activeScope()"))
        assertTrue(collector.contains("tenantScope.key(KEY_RESPONSE)"))
        assertTrue(background.contains("scope.key(KEY_COLLECTOR_GENERATION)"))
        assertTrue(background.contains("scope.key(KEY_COLLECTOR_TARGETS)"))
    }

    @Test
    fun openingTimelineDoesNotStartCollectionAndManualSyncControlsStayRemoved() {
        assertFalse(activity.contains("BlaBlaAutomaticCollectionCoordinator0400.tryLaunchPending"))
        assertFalse(activity.contains("trips_activity_resume"))
        assertFalse(activity.contains("AgendaHeaderAction0396(\"Sincronizar agora\")"))
        assertFalse(activity.contains("AgendaHeaderAction0396(\"Sincronizar BlaBlaCar\")"))
        assertFalse(activity.contains("AgendaHeaderAction0396(\"Publicar agenda\")"))
        assertFalse(timeline.contains("autoSyncToken"))
        assertFalse(timeline.contains("BlaBlaDynamicSessionIntents.sync"))
    }

    private fun sampleTrip(id: String) = BlaBlaCollectorTrip(
        profile_uuid = "profile-test",
        date = "2026-09-01",
        departure_time = "10:00",
        actual_departure = "Origin",
        actual_arrival = "Destination",
        trip_href = "https://www.blablacar.com.br/rides/offer?id=$id",
        trip_id = id,
        passenger_roster_complete = true,
    )
}
