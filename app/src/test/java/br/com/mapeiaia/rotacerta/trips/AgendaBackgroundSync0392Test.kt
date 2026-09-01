package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaBackgroundSync0392Test {
    @Test
    fun durableSilentPolicyUsesAndroidMinimumPeriodicCadence() {
        assertEquals(15L, agendaBackgroundSyncIntervalMinutes0392())
        assertFalse(agendaBackgroundSyncShowsUiStatus0392())
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
}
