package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaHeadlessCollector0401Test {
    private val coordinator = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaAutomaticCollection0400.kt").readText()
    private val dynamic = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()
    private val background = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaBackgroundSync0392.kt").readText()
    private val publicAgenda = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PublicAgendaAutoSync0300.kt").readText()

    @Test
    fun automaticCollectorHasNoActivityOrTaskLaunch() {
        assertTrue(coordinator.contains("runPendingHeadless"))
        assertTrue(coordinator.contains("visualHost = null"))
        assertTrue(coordinator.contains("executionHost=worker_headless_webview"))
        assertFalse(coordinator.contains("startActivity("))
        assertFalse(coordinator.contains("FLAG_ACTIVITY_NEW_TASK"))
        assertFalse(coordinator.contains("BlaBlaDynamicAccountSessionActivity::class.java"))
        assertTrue(dynamic.contains("internal class BlaBlaDynamicAccountSessionController0401"))
        assertTrue(dynamic.contains("visualHost?.invoke(view)"))
        assertTrue(dynamic.contains("BLABLACAR_HEADLESS_PAGE_FALLBACK_0404"))
        assertTrue(dynamic.contains("headlessPageFinishedNavigationGeneration0404 == expectedNavigation"))
        assertTrue(coordinator.contains("withTimeout(HEADLESS_ACCOUNT_TIMEOUT_MS_0404)"))
        assertTrue(coordinator.contains("headless_account_timeout_0404"))
    }

    @Test
    fun sourceAccessProbeIsBoundedSoExactTripCannotHangAfterPageFinished() {
        assertEquals(4_000L, BlaBlaDynamicAccountSessionController0401.SOURCE_ACCESS_PROBE_TIMEOUT_MS_0447)
        val start = dynamic.indexOf("private fun inspectSourceAccess0426")
        val end = dynamic.indexOf("private fun handleTemporaryRestriction0426", start)
        assertTrue(start >= 0 && end > start)
        val probe = dynamic.substring(start, end)
        assertTrue(probe.contains("request = BlaBlaBrowserRequest.PAGE_STATE"))
        assertTrue(probe.contains("timeoutMs = SOURCE_ACCESS_PROBE_TIMEOUT_MS_0447"))
        assertTrue(probe.contains("pageAccessInspectionInFlight0426 = false"))
        assertTrue(probe.contains("sourceAccessInspectedSyncGeneration0448 == expectedSync"))
        assertTrue(probe.contains("sourceAccessInspectedNavigationGeneration0448 == expectedNavigation"))
        assertTrue(probe.contains("sourceAccessInspectedSyncGeneration0448 = expectedSync"))
        assertTrue(probe.contains("sourceAccessInspectedNavigationGeneration0448 = expectedNavigation"))
        assertTrue(probe.contains("onAvailable()"))
    }

    @Test
    fun headlessStateMachineDelaysDoNotDependOnViewAttachment() {
        assertTrue(dynamic.contains("private val headlessDelayedHandler0405 = Handler(Looper.getMainLooper())"))
        assertTrue(dynamic.contains("headlessDelayedHandler0405.postDelayed(guarded, delayMs)"))
        assertTrue(dynamic.contains("private fun postSessionDelayed0405"))
        assertFalse(dynamic.contains("view.postDelayed("))
        assertEquals(1, dynamic.split("webView.postDelayed(").size - 1)
        assertTrue(dynamic.contains("headlessDelayedHandler0405.removeCallbacksAndMessages(null)"))
    }

    @Test
    fun navigationNeverRequestsExternalCollection() {
        assertEquals(AgendaBackgroundSyncMode0392.DELTA_ONLY, agendaBackgroundSyncMode0392("timeline_open"))
        assertEquals(AgendaBackgroundSyncMode0392.DELTA_ONLY, agendaBackgroundSyncMode0392("timeline_pull_refresh"))
        assertEquals(AgendaBackgroundSyncMode0392.DELTA_ONLY, agendaBackgroundSyncMode0392("trip_mutation"))
        assertEquals(AgendaBackgroundSyncMode0392.COLLECTOR_RECONCILE, agendaBackgroundSyncMode0392("periodic"))
        assertTrue(background.contains("runPendingHeadless"))
    }

    @Test
    fun expiredAuthenticationIsExplicitAndDoesNotOpenBrowser() {
        assertEquals(
            "account-b",
            nextAutomaticCollectorAccountId0400(
                listOf("account-a", "account-b"),
                emptySet(),
                emptySet(),
                setOf("account-a"),
            ),
        )
        assertTrue(background.contains("KEY_COLLECTOR_PENDING_AUTH"))
        assertTrue(dynamic.contains("completeAutomaticAuthenticationRequired"))
        assertTrue(coordinator.contains("PENDING_AUTH"))
        assertTrue(coordinator.contains("browserOpened=false"))
    }

    @Test
    fun publicAgendaUsesCanonicalTripStoreProjectionAndCanonicalStopIdsFirst() {
        assertFalse(publicAgenda.contains("BlaBlaCollectorStateStore(context).lastResponseRecoveringDynamicSessions()"))
        assertTrue(publicAgenda.contains("PUBLIC_AGENDA_CANONICAL_SOURCE_0406"))
        assertTrue(publicAgenda.contains("PUBLIC_CAPACITY_CANONICAL_SHAPE_REUSED_0401"))
        assertTrue(publicAgenda.contains("firstRequestUsesCanonical=true"))
        assertTrue(publicAgenda.contains("PUBLIC_CAPACITY_SERVER_SHAPE_REUSED_0402"))
        assertTrue(publicAgenda.contains("PUBLIC_CAPACITY_REMOTE_REVISION_NO_OP_0402"))
        val preserve = publicAgenda.indexOf("val authoritativeStops0402")
        val request = publicAgenda.indexOf("suspend fun reconcile(): DriverCapacitySnapshotResponse", startIndex = preserve.coerceAtLeast(0))
        assertTrue(preserve >= 0 && request > preserve)
    }

    @Test
    fun cachedCanonicalTripsAreMaterializedBeforeHeadlessCollectionAndRefreshedAfterward() {
        val cacheMaterialization = background.indexOf("EXTERNAL_CANONICAL_CACHE_MATERIALIZED_0404")
        val headlessCollection = background.indexOf("runPendingHeadless")
        val freshReconcile = background.indexOf("val freshCanonical = reconcileCollectedExternalTrips0403")
        assertTrue(cacheMaterialization >= 0)
        assertTrue(headlessCollection > cacheMaterialization)
        assertTrue(freshReconcile > headlessCollection)
        assertTrue(background.contains("BookingRealtimeEvents0356.notifyChanged()"))
    }

    @Test
    fun configuredCadenceRespectsWorkManagerMinimum() {
        assertEquals(15L, AgendaBackgroundSyncConfig0392.MIN_INTERVAL_MINUTES)
        assertTrue(background.contains("PeriodicWorkRequestBuilder<AgendaBackgroundSyncWorker0392>"))
    }
}
