package br.com.mapeiaia.rotacerta.trips

import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CentralTodayOnlyRefresh0660Test {
    private val today = LocalDate.of(2026, 9, 26)

    private fun candidate(id: String, date: LocalDate) = BlaBlaDomRideCandidate(
        href = "https://www.blablacar.com.br/rides/offer/$id",
        text = date.toString(),
        dateText = date.toString(),
    )

    @Test
    fun todayScopeStopsBeforeWalkingFutureDatesWhenTodayIsAbsent0660() {
        val decision = BlaBlaCollectorCardModule.todayScopeStopDecision0660(
            candidates = listOf(
                candidate("tomorrow", today.plusDays(1)),
                candidate("later", today.plusDays(2)),
            ),
            targetDate = today,
            targetObservedEarlier = false,
            today = today,
        )
        assertEquals(BlaBlaTodayScopeStopDecision0660.COMPLETE_TARGET_ABSENT, decision)
    }

    @Test
    fun todayScopeStopsImmediatelyAfterLastTodayCardAndFutureBoundary0660() {
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
        assertEquals(BlaBlaTodayScopeStopDecision0660.COMPLETE_TARGET_RANGE, decision)
    }

    @Test
    fun todayScopeContinuesWhileOnlyTodayCardsAreMaterialized0660() {
        val decision = BlaBlaCollectorCardModule.todayScopeStopDecision0660(
            candidates = listOf(
                candidate("today-a", today),
                candidate("today-b", today),
            ),
            targetDate = today,
            targetObservedEarlier = true,
            today = today,
        )
        assertEquals(BlaBlaTodayScopeStopDecision0660.CONTINUE, decision)
    }

    @Test
    fun todayScopeFailsClosedOnUnreadableDateInsteadOfFallingBackToFull0660() {
        val unreadable = BlaBlaDomRideCandidate(
            href = "https://www.blablacar.com.br/rides/offer/unknown",
            text = "Viagem sem data verificável",
            dateText = "",
        )
        val decision = BlaBlaCollectorCardModule.todayScopeStopDecision0660(
            candidates = listOf(unreadable),
            targetDate = today,
            targetObservedEarlier = false,
            today = today,
        )
        assertEquals(BlaBlaTodayScopeStopDecision0660.FAIL_DATE_EVIDENCE, decision)
    }

    @Test
    fun centralButtonDiscoversTodayEvenWithZeroLocalCanonicalCards0660() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/CentralDoDia0552.kt").readText()
        val buttonStart = source.indexOf("CENTRAL_DAY_TODAY_HTML_REQUESTED_0661")
        assertTrue(buttonStart >= 0)
        assertTrue(source.contains("BlaBlaTodayHtmlCaptureCoordinator0661.capture("))
        assertTrue(source.contains("discoverWhenLocalEmpty=true"))
        assertFalse(source.contains("BlaBlaDynamicSessionIntents.syncToday("))
        assertFalse(source.substring(buttonStart.coerceAtLeast(0)).take(3500).contains("CentralDayCommandBridge0552.refreshAll"))
    }

    @Test
    fun individualCardRefreshRemainsSingleCardAndNeverFallsBackToFull0660() {
        val background = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaBackgroundSync0392.kt").readText()
        val start = background.indexOf("internal suspend fun refreshCanonicalTripFromCollector0517(")
        val end = background.indexOf("fun enqueueRecoveryIfNeeded", start)
        assertTrue(start >= 0 && end > start)
        val targeted = background.substring(start, end)
        assertTrue(targeted.contains("BlaBlaUnifiedHtmlCapture0605.captureSingleTrip0607("))
        assertFalse(targeted.contains("BlaBlaDynamicSessionIntents.sync("))
        assertFalse(targeted.contains("FULL_RECONCILE"))

        val dynamic = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()
        assertTrue(dynamic.contains("SCOPE_SINGLE_CARD_0660"))
        assertTrue(dynamic.contains("scope=TODAY_ONLY"))
        assertTrue(dynamic.contains("outOfScopeCardsOpened=0 fullTraversal=false"))
    }
}
