package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaBackgroundSync0392Test {
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
        assertEquals(AgendaBackgroundSyncMode0392.FULL_RECONCILE, agendaBackgroundSyncMode0392("periodic"))
        assertEquals(AgendaBackgroundSyncMode0392.FULL_RECONCILE, agendaBackgroundSyncMode0392("timeline_pull_refresh"))
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
        assertTrue(source.contains("TripMutationCoordinator0387(appContext, store).drainPending()"))
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
        assertTrue(activity.contains("reason = \"timeline_open\""))
        assertTrue(activity.contains("reason = \"timeline_pull_refresh\""))
        assertTrue(activity.contains("blablaAutomatic=false"))
        assertTrue(activity.contains("onRefresh = requestFullTimelineRefresh"))
    }

    @Test
    fun collectorMutationsAndPushEventsDelegateToSameBackgroundModule() {
        val timeline = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()
        val push = File("src/main/java/br/com/mapeiaia/rotacerta/trips/RotaCertaBookingMessagingService.kt").readText()

        assertFalse(timeline.contains("tripMutationCoordinator.drainPending()"))
        assertFalse(timeline.contains("mutationCoordinator.drainPending()"))
        assertTrue(timeline.contains("reason = \"blablacar_collection_result\""))
        assertTrue(timeline.contains("AgendaBackgroundSync0392.enqueueImmediate"))

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
        assertTrue(background.contains("TripMutationCoordinator0387(appContext, store).drainPending()"))
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
        assertTrue(background.contains("val reconcileAllCanonicalTrips = mode in setOf("))
        assertTrue(timeline.contains("recordExternalManualMutation("))
        assertTrue(timeline.contains("exactMatches.size != 1"))
        assertFalse(background.contains("BlaBlaReliableSeatSyncActivity"))
        assertFalse(background.contains("BlaBlaManualSeatSyncActivity"))
    }

}
