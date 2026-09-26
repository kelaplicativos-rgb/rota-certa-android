package br.com.mapeiaia.rotacerta.trips

import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CentralTodayDirectHtml0661Test {
    @Test
    fun centralTodayButtonUsesDirectHtmlAndNeverLegacySync0661() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/CentralDoDia0552.kt").readText()
        assertTrue(source.contains("BlaBlaTodayHtmlCaptureCoordinator0661.capture("))
        assertTrue(source.contains("CENTRAL_DAY_TODAY_HTML_REQUESTED_0661"))
        assertTrue(source.contains("BlaBlaAcquisitionAuthority0607.HTML_DIRECT"))
        assertFalse(source.contains("BlaBlaDynamicSessionIntents.syncToday("))
        assertFalse(source.contains("rememberLauncherForActivityResult"))
        assertFalse(source.contains("ActivityResultContracts.StartActivityForResult"))
        assertFalse(source.contains("todaySyncLauncher0660"))
    }

    @Test
    fun todayCoordinatorReusesGlobalDirectHtmlClassesAndNoModeSync0661() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaTodayHtmlCapture0661.kt").readText()
        assertTrue(source.contains("BlaBlaDirectAccountCapture0608.capture("))
        assertTrue(source.contains("targetDate0661 = targetDate"))
        assertTrue(source.contains("authority=${BlaBlaAcquisitionAuthority0607.HTML_DIRECT}"))
        assertTrue(source.contains("legacySync=false modeSync=false"))
        assertFalse(source.contains("BlaBlaDynamicSessionIntents"))
        assertFalse(source.contains("BlaBlaBrowserOrchestrator"))
        assertTrue(source.contains("evaluateAbsentTrips0618 = false"))
        assertTrue(source.contains("preserveSiblings=true tombstone=false"))
    }

    @Test
    fun directTodayReaderStopsAtBoundaryAndNeverJumpsToPageBottom0661() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDirectAccountCapture0608.kt").readText()
        val start = source.indexOf("private suspend fun loadTodayStable0661(")
        val end = source.indexOf("private suspend fun loadStable(", start)
        assertTrue(start >= 0 && end > start)
        val scoped = source.substring(start, end)
        assertTrue(scoped.contains("todayScopeStopDecision0660("))
        assertTrue(scoped.contains("BLABLACAR_DIRECT_TODAY_BOUNDARY_0661"))
        assertTrue(scoped.contains("window.scrollBy"))
        assertTrue(scoped.contains("fullTraversal=false"))
        assertFalse(scoped.contains("window.scrollTo(0,Math.max(document.body.scrollHeight"))
    }

    @Test
    fun deepCaptureFiltersExactTargetDate0661() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaUnifiedHtmlCapture0605.kt").readText()
        val start = source.indexOf("suspend fun captureProfile(")
        val end = source.indexOf("private suspend fun publishLiveHtmlCard0617", start)
        assertTrue(start >= 0 && end > start)
        val capture = source.substring(start, end)
        assertTrue(capture.contains("targetDate0661: LocalDate? = null"))
        assertTrue(capture.contains("LocalDate.parse(ride.date)"))
        assertTrue(capture.contains("== targetDate0661"))
    }

    @Test
    fun singleCardRefreshRemainsCaptureSingleTripOnly0661() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaBackgroundSync0392.kt").readText()
        val start = source.indexOf("internal suspend fun refreshCanonicalTripFromCollector0517(")
        val end = source.indexOf("fun enqueueRecoveryIfNeeded", start)
        assertTrue(start >= 0 && end > start)
        val exact = source.substring(start, end)
        assertTrue(exact.contains("BlaBlaUnifiedHtmlCapture0605.captureSingleTrip0607("))
        assertFalse(exact.contains("BlaBlaTodayHtmlCaptureCoordinator0661"))
        assertFalse(exact.contains("BlaBlaDynamicSessionIntents.sync("))
    }

    @Test
    fun pureBoundaryDecisionStopsAfterTodayBeforeTomorrow0661() {
        val today = LocalDate.of(2026, 9, 26)
        fun candidate(id: String, date: LocalDate) = BlaBlaDomRideCandidate(
            href = "https://www.blablacar.com.br/rides/offer/$id",
            text = date.toString(),
            dateText = date.toString(),
        )
        val decision = BlaBlaCollectorCardModule.todayScopeStopDecision0660(
            candidates = listOf(
                candidate("today-a", today),
                candidate("today-b", today),
                candidate("tomorrow", today.plusDays(1)),
            ),
            targetDate = today,
            targetObservedEarlier = true,
            today = today,
        )
        assertTrue(decision == BlaBlaTodayScopeStopDecision0660.COMPLETE_TARGET_RANGE)
    }
}
