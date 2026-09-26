package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlaBlaSyncMenuRestore0424Test {
    private val automaticSync = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaAutomaticSyncUi0397.kt").readText()
    private val collector = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripBlaBlaCollectorUi.kt").readText()
    private val activity = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()
    private val timeline = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()

    @Test
    fun operationalCollectorIsRestoredInsideAutomaticSyncInsteadOfTimeline() {
        assertTrue(activity.contains("TripScreen.AUTO_SYNC -> AgendaAutomaticSyncScreen0397("))
        assertTrue(automaticSync.contains("BlaBlaAccountsAndBrowsersScreen0399()"))
        assertTrue(automaticSync.contains("BlaBlaCollectorPanel("))
        assertTrue(automaticSync.indexOf("BlaBlaAccountsAndBrowsersScreen0399()") < automaticSync.indexOf("BlaBlaCollectorPanel("))
        assertTrue(automaticSync.contains("showAccountManagement = false"))
        assertFalse(timeline.contains("BlaBlaCollectorPanel("))
    }

    @Test
    fun restoredSurfaceReusesCanonicalCollectorAndItsPhysicalTestActions() {
        assertTrue(collector.contains("fun BlaBlaCollectorPanel("))
        assertTrue(collector.contains("BlaBlaDynamicSessionStore(context)"))
        assertTrue(collector.contains("Sincronizar todas as contas"))
        assertTrue(collector.contains("📅 Sincronizar por data/período"))
        assertTrue(collector.contains("Sincronizar esta data"))
        assertTrue(collector.contains("showAccountManagement: Boolean = true"))
        assertTrue(collector.contains("Use Contas e navegadores acima"))
    }

    @Test
    fun accountConfigurationRemainsSingleAndCollectorStateIsNotForked() {
        assertTrue(automaticSync.contains("BlaBlaAccountsAndBrowsersScreen0399()"))
        assertTrue(collector.contains("if (showAccountManagement)"))
        assertTrue(automaticSync.contains("collectorStore.lastResponseRecoveringDynamicSessions()"))
        assertFalse(automaticSync.contains("BlaBlaDynamicSessionStore(context)"))
    }
}
