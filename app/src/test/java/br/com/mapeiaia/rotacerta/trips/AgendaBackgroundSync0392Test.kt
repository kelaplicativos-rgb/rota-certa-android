package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaBackgroundSync0392Test {
    private fun backgroundSource(): String =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaBackgroundSync0392.kt").readText()

    @Test
    fun durableSilentPolicyUsesConfigurableCentralizedPeriodicCadence() {
        assertEquals(AgendaBackgroundSyncConfig0392.DEFAULT_INTERVAL_MINUTES, agendaBackgroundSyncIntervalMinutes0392())
        assertEquals(AgendaBackgroundSyncConfig0392.MIN_INTERVAL_MINUTES, agendaBackgroundSyncIntervalMinutes0392(1L))
        assertEquals(60L, agendaBackgroundSyncIntervalMinutes0392(60L))
        assertEquals(AgendaBackgroundSyncConfig0392.MAX_INTERVAL_MINUTES, agendaBackgroundSyncIntervalMinutes0392(Long.MAX_VALUE))
        assertFalse(agendaBackgroundSyncShowsUiStatus0392())
    }

    @Test
    fun immediateMutationReasonsAreDeltaOnlyWhilePeriodicAndExplicitRepairCanReconcile() {
        assertEquals(AgendaBackgroundSyncMode0392.DELTA_ONLY, agendaBackgroundSyncMode0392("trip_mutation"))
        assertEquals(AgendaBackgroundSyncMode0392.DELTA_ONLY, agendaBackgroundSyncMode0392("reservation_approved"))
        assertEquals(AgendaBackgroundSyncMode0392.BOOKING_EVENT, agendaBackgroundSyncMode0392("booking_push:reservation_created"))
        assertEquals(AgendaBackgroundSyncMode0392.COLLECTOR_RECONCILE, agendaBackgroundSyncMode0392("blablacar_collection_result"))
        assertEquals(AgendaBackgroundSyncMode0392.COLLECTOR_RECONCILE, agendaBackgroundSyncMode0392("periodic"))
        assertEquals(AgendaBackgroundSyncMode0392.DELTA_ONLY, agendaBackgroundSyncMode0392("timeline_pull_refresh"))
        assertEquals(AgendaBackgroundSyncMode0392.DELTA_ONLY, agendaBackgroundSyncMode0392("timeline_open"))
    }

    @Test
    fun targetedVerifyDelegatesTransportRevisionAndRetryToCanonicalOutbox() {
        val source = backgroundSource()
        val start = source.indexOf("internal suspend fun reverifyCanonicalMirror0435")
        val end = source.indexOf("fun enqueueRecoveryIfNeeded", start)
        assertTrue(start >= 0 && end > start)
        val exact = source.substring(start, end)

        assertTrue(exact.contains("TripMutationCoordinator0387(appContext, store)"))
        assertTrue(exact.contains("recordExternalCollectionMutation("))
        assertTrue(exact.contains("drainPending(canonicalTripIds = setOf(canonicalTripId))"))
        assertTrue(exact.contains("samePublisher=true directHttp=false"))
        assertFalse(exact.contains("PublicAgendaAutoSync0300.syncExternalTripIncremental("))
        assertFalse(exact.contains("TripRemoteApi(settings)"))
        assertFalse(exact.contains("targetedReverifyTransportRevision0439("))
        assertFalse(exact.contains("recordPublicationCommitted0411("))
    }

    @Test
    fun cardVerifyUsesCanonicalMirrorAndOnlyAcquiresMissingPublicUrlWithoutRetry() {
        assertEquals(AgendaBackgroundSyncMode0392.DELTA_ONLY, agendaBackgroundSyncMode0392("trip_reverify"))
        assertFalse(agendaBackgroundSyncRequestsCollector0430("trip_reverify"))
        assertTrue(AgendaBackgroundSync0392.staleDurableOneShot0435("trip_reverify", 0L, 1_000_000L))
        assertTrue(AgendaBackgroundSync0392.staleDurableOneShot0435("admin_update_now:old", 0L, 1_000_000L))
        assertFalse(
            AgendaBackgroundSync0392.staleDurableOneShot0435(
                reason = "trip_reverify",
                requestedAtMillis = 900_000L,
                nowMillis = 1_000_000L,
            ),
        )
        assertTrue(
            AgendaBackgroundSync0392.staleDurableOneShot0435(
                reason = "admin_update_now:old",
                requestedAtMillis = 1L,
                nowMillis = AgendaBackgroundSync0392.ONE_SHOT_MAX_AGE_MILLIS_0435 + 2L,
            ),
        )

        val source = backgroundSource()
        assertTrue(source.contains("INPUT_REQUESTED_AT_0435 to requestedAtMillis"))
        assertTrue(source.contains("STALE_DURABLE_WORK_0435"))
        assertTrue(source.contains("reverifyCanonicalMirror0435"))
        assertTrue(source.contains("recordExternalCollectionMutation("))
        assertTrue(source.contains("drainPending(canonicalTripIds = setOf(canonicalTripId))"))
        assertTrue(source.contains("publicMirrorProjectionCurrent0411()"))
        assertTrue(source.contains("canonicalBoundBlaBlaPublicUrl0423(canonical.blablaPublicUrl, target.tripId).isNullOrBlank()"))
        assertFalse(source.substring(source.indexOf("internal suspend fun reverifyCanonicalMirror0435"), source.indexOf("fun enqueueRecoveryIfNeeded")).contains("BlaBlaAutomaticCollectionCoordinator0400.reverifyTripHeadless0407"))
        assertTrue(source.contains("PUBLIC_TRIP_LINK_OPTIONAL_0465"))
        val reverify = source.substring(
            source.indexOf("internal suspend fun reverifyCanonicalMirror0435"),
            source.indexOf("fun enqueueRecoveryIfNeeded"),
        )
        assertFalse(reverify.contains("BLABLACAR_PUBLIC_URL_CANONICALIZED_0442"))
        assertTrue(reverify.contains("publicationBlocked=false"))
        assertTrue(reverify.contains("PUBLISHED_URL_PENDING"))
        assertFalse(source.contains("reason == \"trip_reverify\" ||\n            reason.startsWith(\"admin_update_now:\")"))
        assertTrue(source.contains("val targetedRetryable = false"))
    }
    @Test
    fun targetedCollectorPublicUrlAcceptsOnlyExactStrongTripAndAuthoritativeBinding() {
        val target = BlaBlaTripTarget0407(
            tenantId = "tenant-0442",
            accountId = "account-0442",
            profileUuid = "11111111-1111-4111-8111-111111111111",
            tripId = "admin-trip-0442",
            tripHref = "https://www.blablacar.com.br/rides/offer/admin-trip-0442",
        )
        val publicToken = "AaA1PublicToken0442DifferentFromAdmin"
        val exact = BlaBlaCollectorTrip(
            profile_uuid = target.profileUuid,
            date = "2030-09-10",
            trip_href = target.tripHref,
            public_trip_href = "http://www.blablacar.com.br/trip?source=CARPOOLING&id=$publicToken&search_uuid=temp",
            public_trip_href_source = "network_structured",
            public_trip_href_binding = BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_NETWORK_AUTHORITATIVE,
            trip_id = target.tripId,
        )
        val unrelated = exact.copy(
            trip_id = "other-admin-trip-0442",
            trip_href = "https://www.blablacar.com.br/rides/offer/other-admin-trip-0442",
        )

        assertEquals(
            "https://www.blablacar.com.br/trip?source=CARPOOLING&id=$publicToken",
            targetedCollectorPublicUrl0442(
                BlaBlaCollectorMonthResponse(status = "validated", trips = listOf(exact)),
                target,
            ),
        )
        assertEquals(
            null,
            targetedCollectorPublicUrl0442(
                BlaBlaCollectorMonthResponse(status = "validated", trips = listOf(unrelated)),
                target,
            ),
        )
        assertEquals(
            "https://www.blablacar.com.br/trip?source=CARPOOLING&id=$publicToken",
            targetedCollectorPublicUrl0442(
                BlaBlaCollectorMonthResponse(
                    status = "validated",
                    trips = listOf(
                        exact.copy(
                            public_trip_href_binding = BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_ORCHESTRATOR_NAVIGATION,
                            public_trip_href_source = "orchestrator_navigation",
                        ),
                    ),
                ),
                target,
            ),
        )
    }

    @Test
    fun canonicalFullReconcileProjectsTimelineWithoutWaitingForExternalCollector() {
        assertEquals(
            AgendaBackgroundSyncMode0392.FULL_RECONCILE,
            agendaBackgroundSyncMode0392("admin_full_reconcile:physical-0429"),
        )
        assertFalse(agendaBackgroundSyncRequestsCollector0430("admin_full_reconcile:physical-0429"))
        assertFalse(agendaBackgroundSyncRequestsCollector0430("manual"))
        assertFalse(agendaBackgroundSyncRequestsCollector0430("recovery"))
        assertTrue(agendaBackgroundSyncRequestsCollector0430("periodic"))
        assertTrue(agendaBackgroundSyncRequestsCollector0430("admin_update_now:explicit-collector"))

        val source = backgroundSource()
        assertTrue(source.contains("val collectorRequested = agendaBackgroundSyncRequestsCollector0430(reason)"))
        assertTrue(source.contains("collectorPending = collectorRequested && collectorState.pending"))
        assertTrue(source.contains("val collectorWasRequested = agendaBackgroundSyncRequestsCollector0430(reason)"))
        assertTrue(source.contains("val collectorAuthRequired = collectorWasRequested && collectorState.status == \"PENDING_AUTH\""))
        assertTrue(source.contains("agendaBackgroundSyncMode0392(reason) == AgendaBackgroundSyncMode0392.FULL_RECONCILE -> true"))
    }

    @Test
    fun oneBackgroundModuleFeedsTimelineAndPublicAgenda() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaBackgroundSync0392.kt").readText()

        assertTrue(source.contains("PeriodicWorkRequestBuilder<AgendaBackgroundSyncWorker0392>"))
        assertTrue(source.contains("enqueueUniquePeriodicWork"))
        assertTrue(source.contains("ExistingPeriodicWorkPolicy.UPDATE"))
        assertTrue(source.contains("enqueueUniqueWork"))
        assertTrue(source.contains("ExistingWorkPolicy.APPEND_OR_REPLACE"))
        assertTrue(source.contains("NetworkType.CONNECTED"))
        assertTrue(source.contains("AgendaBackgroundSyncConfig0392.configuredIntervalMinutes"))
        assertTrue(source.contains("tenantScopedWorkName"))
        assertTrue(source.contains("tenantMutexes.computeIfAbsent"))
        assertTrue(source.contains("BackoffPolicy.EXPONENTIAL"))
        assertTrue(source.contains("PublicBookingRemoteSync0296.pullAndReconcile"))
        assertTrue(source.contains("TripMutationCoordinator0387(appContext, store).drainPending("))
        assertTrue(source.contains("PublicAgendaAutoSync0300.sync"))
        assertTrue(source.contains("BookingRealtimeEvents0356.notifyChanged()"))
        assertTrue(source.contains("TripWidgetProvider.updateAll(appContext)"))
        assertTrue(source.contains("silentUi=true"))
    }

    @Test
    fun timelineActivityNoLongerOwnsAutomaticNetworkSyncOrStartupBanner() {
        val activity = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()

        assertFalse(activity.contains("PublicBookingRemoteSync0296.pullAndReconcile"))
        assertFalse(activity.contains("createPublicAgendaSyncCoordinator0373"))
        assertFalse(activity.contains("publicAgendaSyncCoordinator"))
        assertFalse(activity.contains("publicAgendaSyncRevision"))
        assertFalse(activity.contains("timeline_startup_booking_reconcile_begin"))
        assertFalse(activity.contains("tripMutationCoordinator.drainPending()"))
        assertFalse(activity.contains("mutationCoordinator.drainPending()"))
        assertFalse(activity.contains("message = \"Sincronizando tudo:"))
        assertTrue(activity.contains("AgendaBackgroundSync0392.enqueueImmediate"))
        assertFalse(activity.contains("reason = \"timeline_open\""))
        assertFalse(activity.contains("timeline_resume"))
        assertFalse(activity.contains("reason = \"timeline_pull_refresh\""))
        assertTrue(activity.contains("networkSync=false automaticSyncOnly=true"))
        assertTrue(activity.contains("onRefresh = requestTimelineVisualReload"))
    }

    @Test
    fun collectorMutationsAndPushEventsDelegateToSameBackgroundModule() {
        val timeline = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()
        val push = File("src/main/java/br/com/mapeiaia/rotacerta/trips/RotaCertaBookingMessagingService.kt").readText()

        assertFalse(timeline.contains("tripMutationCoordinator.drainPending()"))
        assertFalse(timeline.contains("mutationCoordinator.drainPending()"))
        assertTrue(backgroundSource().contains("AgendaBackgroundSyncMode0392.COLLECTOR_RECONCILE"))

        assertFalse(push.contains("PublicBookingRemoteSync0296.pullAndReconcile"))
        assertTrue(push.contains("AgendaBackgroundSync0392.enqueueImmediate"))
        assertTrue(push.contains("reason = \"booking_push:"))
    }

    @Test
    fun processStartOnlySchedulesWorkAndNeverRunsNetworkOnProviderThread() {
        val trace = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaTrace.kt").readText()
        val provider = trace
            .substringAfter("class AgendaTraceProvider")
            .substringBefore("internal data class AgendaOperationToken")

        assertTrue(provider.contains("AgendaBackgroundSync0392.ensureScheduled(application)"))
        assertFalse(provider.contains("PublicBookingRemoteSync0296"))
        assertFalse(provider.contains("PublicAgendaAutoSync0300"))
        assertFalse(provider.contains("drainPending()"))
    }
    @Test
    fun agendaCanBeFedWithTimelineInactiveBecauseWorkerOwnsTheEngines() {
        val background = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaBackgroundSync0392.kt").readText()
        val activity = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()

        assertTrue(background.contains("PublicAgendaAutoSync0300.sync("))
        assertTrue(background.contains("TripMutationCoordinator0387(appContext, store).drainPending("))
        assertFalse(background.contains("TripsActivity"))
        assertFalse(activity.contains("PublicAgendaAutoSync0300.sync("))
    }

    @Test
    fun passengerAndDriverMutationsPersistThenQueueImmediateDeltaWithoutWaitingForPeriodicWorker() {
        val passenger = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt").readText()
        val timeline = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()

        assertFalse(passenger.contains("mutationCoordinator.drainPending()"))
        assertFalse(passenger.contains("PublicBookingRemoteSync0296.pullAndReconcile(context, store)"))
        assertTrue(passenger.contains("AgendaBackgroundSync0392.enqueueImmediate"))
        assertTrue(passenger.contains("BookingRealtimeEvents0356.notifyChanged()"))
        assertTrue(passenger.contains("mutationType = \"RESERVATION_APPROVED\""))
        assertTrue(passenger.contains("mutationType = \"BOOKING_CANCELLED_BY_DRIVER\""))
        assertTrue(timeline.contains("AgendaBackgroundSync0392.enqueueImmediate"))
    }

    @Test
    fun normalSingleTripMutationsDoNotRequestFullReconcileOrAutomaticBlablacarWrite() {
        val background = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaBackgroundSync0392.kt").readText()
        val timeline = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()

        assertTrue(background.contains("AgendaBackgroundSyncMode0392.DELTA_ONLY"))
        assertTrue(background.contains("val reconcileAllCanonicalTrips = mode == AgendaBackgroundSyncMode0392.FULL_RECONCILE"))
        assertFalse(timeline.contains("recordExternalManualMutation("))
        assertFalse(timeline.contains("manual_card_shortcut"))
        assertFalse(background.contains("BlaBlaReliableSeatSyncActivity"))
        assertFalse(background.contains("BlaBlaManualSeatSyncActivity"))
    }


    @Test
    fun manualPublicUrlUsesExistingTargetedPushCanonicalStoreAndOutbox0465() {
        val source = backgroundSource()
        assertTrue(source.contains("booking_push:admin_public_url_saved"))
        assertTrue(source.contains("manualPublicUrlAssignments0465"))
        assertTrue(source.contains("ADMIN_PUBLIC_URL_CANONICAL_APPLIED_0465"))
        assertTrue(source.contains("store.saveTrip("))
        assertTrue(source.contains("recordExternalCollectionMutation("))
        assertTrue(source.contains("drainPending(canonicalTripIds = setOf(canonicalTripId))"))
        assertTrue(source.contains("PUBLISHED_URL_PENDING"))
    }

}
