package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScopedHtmlIsolation0662Test {
    @Test
    fun todayFinalizerNeverRebuildsCombinedAccountState0662() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaTodayHtmlCapture0661.kt").readText()
        val start = source.indexOf("private suspend fun commitPresenceOnly(")
        assertTrue(start >= 0)
        val scoped = source.substring(start)
        assertTrue(scoped.contains("CENTRAL_TODAY_HTML_SCOPE_ISOLATED_0662"))
        assertTrue(scoped.contains("combinedResponse=false"))
        assertFalse(scoped.contains(".combinedResponse("))
        assertFalse(scoped.contains("BlaBlaDynamicSessionStore("))
    }

    @Test
    fun todayDeepCaptureBlocksSessionProjectionPerCard0662() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaUnifiedHtmlCapture0605.kt").readText()
        val start = source.indexOf("private suspend fun publishLiveHtmlCard0617(")
        val end = source.indexOf("suspend fun captureSingleTrip0607(", start)
        assertTrue(start >= 0 && end > start)
        val liveCommit = source.substring(start, end)
        assertTrue(liveCommit.contains("scopedStateIsolation0662"))
        assertTrue(liveCommit.contains("BLABLACAR_SCOPED_SESSION_PROJECTION_BLOCKED_0662"))
        assertTrue(liveCommit.contains("combinedResponse=false"))
    }

    @Test
    fun exactCardReconcilesFreshHtmlWithoutDynamicSessionReadback0662() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaBackgroundSync0392.kt").readText()
        val start = source.indexOf("internal suspend fun refreshCanonicalTripFromCollector0517(")
        val end = source.indexOf("fun enqueueRecoveryIfNeeded", start)
        assertTrue(start >= 0 && end > start)
        val exact = source.substring(start, end)
        assertTrue(exact.contains("scopedStateIsolation0662 = true"))
        assertTrue(exact.contains("val capturedTrip0662 = htmlResult.trip"))
        assertTrue(exact.contains("strategy = \"html_trip_scope_0662\""))
        assertTrue(exact.contains("TARGET_CARD_HTML_SCOPE_ISOLATED_0662"))
        assertFalse(exact.contains("lastResponseRecoveringDynamicSessions"))
        assertFalse(exact.contains("BlaBlaDynamicSessionStore("))
    }

    @Test
    fun centralCardButtonRunsDirectTripOnlyWithoutWorkManager0662() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/CentralDoDia0552.kt").readText()
        val start = source.indexOf("suspend fun refreshTripDirect0662(")
        val end = source.indexOf("internal fun target(", start)
        assertTrue(start >= 0 && end > start)
        val direct = source.substring(start, end)
        assertTrue(direct.contains("refreshCanonicalTripFromCollector0517("))
        assertTrue(direct.contains("workManager=false"))
        assertFalse(direct.contains("enqueueTripCollectorRefresh0517("))
        assertTrue(source.contains("Atualizando somente este card pelo HTML"))
        assertTrue(source.contains("refreshTripDirect0662("))
    }

    @Test
    fun exactTripCaptureCanDisableAggregateStateWrites0662() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaUnifiedHtmlCapture0605.kt").readText()
        val start = source.indexOf("suspend fun captureSingleTrip0607(")
        val end = source.indexOf("private fun createUnifiedCaptureWebView0621(", start)
        assertTrue(start >= 0 && end > start)
        val exact = source.substring(start, end)
        assertTrue(exact.contains("scopedStateIsolation0662: Boolean = false"))
        assertTrue(exact.contains("if (!scopedStateIsolation0662)"))
        assertTrue(exact.contains("BLABLACAR_TARGETED_SCOPE_ISOLATED_0662"))
    }
}
