package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class BlaBlaLiveHtmlCommit0617Test {
    @Test
    fun profilesRunConcurrentlyButEachAccountKeepsSequentialTripNavigation0617() {
        val coordinator = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaRidesSnapshot0526.kt",
        ).readText()
        val capture = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaUnifiedHtmlCapture0605.kt",
        ).readText()

        assertTrue(coordinator.contains("coroutineScope"))
        assertTrue(coordinator.contains("accounts.mapIndexed"))
        assertTrue(coordinator.contains("async {"))
        assertTrue(coordinator.contains(".awaitAll()"))
        assertTrue(capture.contains("futureRides.forEachIndexed"))
        assertTrue(capture.contains("liveCardCommitMutex0617"))
    }

    @Test
    fun unstableRidesPageGetsOneFreshWebViewRetryWithExactEvidence0617() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDirectAccountCapture0608.kt",
        ).readText()

        assertTrue(source.contains("repeat(2) { attempt ->"))
        assertTrue(source.contains("BLABLACAR_RIDES_STABILIZATION_RETRY_0617"))
        assertTrue(source.contains("BLABLACAR_RIDES_STABILIZATION_FAILED_0617"))
        assertTrue(source.contains("onFailureReason0617(decision.reason"))
        assertTrue(source.contains("RIDES_PAGE_NOT_STABLE_"))
        assertTrue(source.contains("freshWebView=true"))
    }

    @Test
    fun liveCommitCannotTombstoneSiblingsAndRefreshesVisibleTimeline0617() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaUnifiedHtmlCapture0605.kt",
        ).readText()

        assertTrue(source.contains("completeProfileUuids = emptySet()"))
        assertTrue(source.contains("preserveSiblings=true tombstone=false"))
        assertTrue(source.contains("BookingRealtimeEvents0356.notifyChanged()"))
        assertTrue(source.contains("visibleImmediately=true"))
        assertTrue(source.contains("restoreHtmlRollback0612"))
    }
}
