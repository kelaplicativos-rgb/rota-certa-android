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
    }

    @Test
    fun navigationNeverRequestsExternalCollection() {
        assertEquals(AgendaBackgroundSyncMode0392.DELTA_ONLY, agendaBackgroundSyncMode0392("timeline_open"))
        assertEquals(AgendaBackgroundSyncMode0392.DELTA_ONLY, agendaBackgroundSyncMode0392("timeline_pull_refresh"))
        assertEquals(AgendaBackgroundSyncMode0392.DELTA_ONLY, agendaBackgroundSyncMode0392("trip_mutation"))
        assertEquals(AgendaBackgroundSyncMode0392.FULL_RECONCILE, agendaBackgroundSyncMode0392("periodic"))
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
    fun publicAgendaUsesPersistedCollectorSnapshotAndCanonicalStopIdsFirst() {
        assertTrue(publicAgenda.contains("BlaBlaCollectorStateStore(context).lastResponseRecoveringDynamicSessions()"))
        assertTrue(publicAgenda.contains("PUBLIC_CAPACITY_CANONICAL_SHAPE_REUSED_0401"))
        assertTrue(publicAgenda.contains("firstRequestUsesCanonical=true"))
        assertTrue(publicAgenda.contains("PUBLIC_CAPACITY_SERVER_SHAPE_REUSED_0402"))
        assertTrue(publicAgenda.contains("PUBLIC_CAPACITY_REMOTE_REVISION_NO_OP_0402"))
        val preserve = publicAgenda.indexOf("val authoritativeStops0402")
        val request = publicAgenda.indexOf("suspend fun reconcile(): DriverCapacitySnapshotResponse", startIndex = preserve.coerceAtLeast(0))
        assertTrue(preserve >= 0 && request > preserve)
    }

    @Test
    fun configuredCadenceRespectsWorkManagerMinimum() {
        assertEquals(15L, AgendaBackgroundSyncConfig0392.MIN_INTERVAL_MINUTES)
        assertTrue(background.contains("PeriodicWorkRequestBuilder<AgendaBackgroundSyncWorker0392>"))
    }
}
