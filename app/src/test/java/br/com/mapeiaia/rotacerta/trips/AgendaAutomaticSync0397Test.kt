package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaAutomaticSync0397Test {
    private val background = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaBackgroundSync0392.kt").readText()
    private val ui = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaAutomaticSyncUi0397.kt").readText()
    private val header = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaHeaderNavigation0396.kt").readText()
    private val activity = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()
    private val trace = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaTrace.kt").readText()

    @Test
    fun workManagerCadenceRespectsPlatformMinimumAndOffersMultiplePresets() {
        assertEquals(15L, AgendaBackgroundSyncConfig0392.MIN_INTERVAL_MINUTES)
        assertEquals(15L, agendaBackgroundSyncIntervalMinutes0392(1L))
        assertEquals(30L, agendaBackgroundSyncIntervalMinutes0392(30L))
        assertTrue(agendaAutomaticSyncIntervals0397.size >= 5)
        assertTrue(agendaAutomaticSyncIntervals0397.all { it >= 15L })
        assertTrue(background.contains("PeriodicWorkRequestBuilder<AgendaBackgroundSyncWorker0392>"))
        assertTrue(background.contains("ExistingPeriodicWorkPolicy.UPDATE"))
        assertTrue(background.contains("NetworkType.CONNECTED"))
        assertFalse(background.contains("AlarmManager"))
    }

    @Test
    fun onOffIsTenantScopedAndOffCancelsOnlyPeriodicWork() {
        assertTrue(background.contains("KEY_ENABLED = \"automatic_sync_enabled_0397\""))
        assertTrue(background.contains("scope.key(KEY_ENABLED)"))
        assertTrue(background.contains("DEFAULT_ENABLED = true"))
        assertTrue(background.contains("fun updateEnabled(context: Context, enabled: Boolean)"))
        assertTrue(background.contains("cancelPeriodic(appContext, \"config_disabled\")"))
        assertTrue(background.contains("periodicOnly=true immediateEventsPreserved=true"))
        val immediate = background.substringAfter("fun enqueueImmediate(context: Context, reason: String)")
            .substringBefore("internal suspend fun reconcileTenantSeatAllocation0395")
        assertFalse(immediate.contains("ensureScheduled(appContext)"))
        assertTrue(immediate.contains("enqueueUniqueWork"))
    }

    @Test
    fun recoveryAndWorkerRejectStaleTenantWithoutLeakingAcrossAccounts() {
        assertTrue(trace.contains("AgendaBackgroundSync0392.ensureScheduled(application)"))
        assertTrue(trace.contains("AgendaBackgroundSync0392.enqueueRecoveryIfNeeded(application)"))
        assertTrue(background.contains("INPUT_TENANT_ID"))
        assertTrue(background.contains("scheduledTenantId != activeTenantId"))
        assertTrue(background.contains("cancelStaleTenantPeriodic"))
        assertTrue(background.contains("lastFullReconcileFinishedAtMillis"))
        assertTrue(background.contains("lastValidFullReconcile"))
        assertTrue(background.contains("failures == 0"))
        assertEquals(AgendaBackgroundSyncMode0392.FULL_RECONCILE, agendaBackgroundSyncMode0392("recovery"))
        assertEquals("RECOVERY", agendaBackgroundSyncTrigger0397("recovery"))
        assertEquals("MANUAL", agendaBackgroundSyncTrigger0397("manual"))
        assertEquals("PULL_TO_REFRESH", agendaBackgroundSyncTrigger0397("timeline_pull_refresh"))
    }

    @Test
    fun schedulerStatusAndManualSyncAreVisibleWithoutPromisingExactAndroidTiming() {
        assertTrue(ui.contains("Sincronização automática"))
        assertTrue(ui.contains("Última execução"))
        assertTrue(ui.contains("Próxima execução prevista"))
        assertTrue(ui.contains("Último resultado"))
        assertTrue(ui.contains("Falha/retry"))
        assertTrue(ui.contains("Sincronizar agora"))
        assertTrue(ui.contains("WorkManager"))
        assertTrue(ui.contains("Doze"))
        assertTrue(ui.contains("Forçar parada"))
        assertTrue(background.contains("recordRunStarted"))
        assertTrue(background.contains("recordRunFinished"))
        assertTrue(background.contains("workId=\$id"))
        assertTrue(background.contains("trigger="))
    }

    @Test
    fun drawerSeparatesPersistentDestinationsFromContextActions() {
        assertTrue(header.contains("AUTOMATIC_SYNC(\"Sincronização automática\")"))
        assertTrue(header.contains("PUBLIC_SEARCH(\"Consulta pública\")"))
        assertTrue(header.contains("PASSENGERS(\"Passageiros\")"))
        assertTrue(header.contains("INTEGRATIONS(\"Integrações\")"))
        assertTrue(header.contains("APP_SETTINGS(\"Configurações\")"))
        assertTrue(activity.contains("TripScreen.AUTO_SYNC -> AgendaAutomaticSyncScreen0397()"))
        assertTrue(activity.contains("activity.startActivity(Intent(activity, MainActivity::class.java))"))

        val overflow = activity
            .substringAfter("val headerActions0396 = when (screen) {")
            .substringBefore("val passengerSubscreenActive0396")
        assertTrue(overflow.contains("AgendaHeaderAction0396(\"Sincronizar agora\")"))
        assertTrue(overflow.contains("AgendaHeaderAction0396(\"Limpar Timeline\")"))
        assertFalse(overflow.contains("AgendaHeaderAction0396(\"Integração online\""))
        assertFalse(header.contains("horizontalScroll"))
    }

    @Test
    fun immediateAndPeriodicStillShareCanonicalSingleFlightPipeline() {
        assertTrue(background.contains("tenantMutexes.computeIfAbsent"))
        assertTrue(background.contains("TripMutationCoordinator0387(appContext, store).drainPending()"))
        assertTrue(background.contains("PublicAgendaAutoSync0300.sync("))
        assertTrue(background.contains("AgendaBackgroundSyncMode0392.DELTA_ONLY"))
        assertTrue(background.contains("stale"))
        assertFalse(background.contains("BlaBlaReliableSeatSyncActivity"))
        assertFalse(background.contains("BlaBlaManualSeatSyncActivity"))
    }
}
