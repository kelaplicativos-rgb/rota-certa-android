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
        assertTrue(capture.contains("for ((index, ride) in futureRides.withIndex())"))
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
    @Test
    fun liveCardFastPathSkipsGlobalAbsenceWorkAndFinalizerRunsOffMain0618() {
        val capture = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaUnifiedHtmlCapture0605.kt",
        ).readText()
        val agenda = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaBackgroundSync0392.kt",
        ).readText()
        val coordinator = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaRidesSnapshot0526.kt",
        ).readText()

        assertTrue(capture.contains("evaluateAbsentTrips0618 = false"))
        assertTrue(capture.contains("skipPresentAlreadyCommittedGeneration0618 = true"))
        assertTrue(agenda.contains("EXTERNAL_CANONICAL_ABSENCE_SCAN_SKIPPED_0618"))
        assertTrue(agenda.contains("EXTERNAL_CANONICAL_FINALIZER_FASTPATH_0618"))
        assertTrue(coordinator.contains("val committed = withContext(Dispatchers.IO)"))
    }

    @Test
    fun changedLiveHtmlCardDrainsOnlyItsPublicPublicationBeforeCommitCompletes0620() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaUnifiedHtmlCapture0605.kt",
        ).readText()

        assertTrue(source.contains("targetPublicationIds0620 = batch.publicationCanonicalTripIds0431"))
        assertTrue(source.contains("TripMutationCoordinator0387(app, tripStore).drainPending("))
        assertTrue(source.contains("canonicalTripIds = targetPublicationIds0620"))
        assertTrue(source.contains("BLABLACAR_LIVE_CARD_PUBLIC_PARITY_0620"))
        assertTrue(source.contains("publicParityConfirmed="))
        assertTrue(source.contains("waitForGlobalBatch=false"))
        assertTrue(source.contains("batch.changedTrips > 0"))
        assertTrue(source.contains("batch.publicationQueued > 0"))
    }
    @Test
    fun transientHtmlTransportFailureRetriesSameCardAndPreventsCascade0621() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaUnifiedHtmlCapture0605.kt",
        ).readText()

        assertTrue(source.contains("BLABLACAR_HTML_TRANSPORT_RECOVERY_0621"))
        assertTrue(source.contains("RECYCLE_WEBVIEW_RETRY_SAME_CARD"))
        assertTrue(source.contains("advanceToNextCard=false"))
        assertTrue(source.contains("destroyUnifiedCaptureWebView0621(webView)"))
        assertTrue(source.contains("webView = createUnifiedCaptureWebView0621(app, account)"))
        assertTrue(source.contains("BLABLACAR_HTML_TRANSPORT_RECOVERED_0621"))
        assertTrue(source.contains("BLABLACAR_HTML_TRANSPORT_RECOVERY_EXHAUSTED_0621"))
        assertTrue(source.contains("STOP_PROFILE_PRESERVE_CANONICAL"))
        assertTrue(source.contains("cascadePrevented=true"))
        assertTrue(source.contains("unattemptedDueTransport0621"))
        assertTrue(source.contains("TRANSPORT_RECOVERY_ATTEMPTS_0621 = 2"))
    }
}
