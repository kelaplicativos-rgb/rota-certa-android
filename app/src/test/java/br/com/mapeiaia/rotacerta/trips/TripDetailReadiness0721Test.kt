package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TripDetailReadiness0721Test {
    private val tripId = "01a0c63c-1184-7469-b42b-2ee37631b217"

    @Test
    fun notReadyIsDistinctAndRetryable0721() {
        assertEquals(
            "TRIP_DETAIL_NOT_READY_0721",
            tripDetailVerificationError0676(
                detailPagePresent = true,
                payloadPresent = false,
                payloadDecoded = false,
                expectedTripId = tripId,
                observedTripId = null,
                domHtmlBytes = 0,
                detailReady0721 = false,
            ),
        )
        assertTrue(shouldRetryTripDetailFailure0676("TRIP_DETAIL_NOT_READY_0721"))
    }

    @Test
    fun exhaustedReadinessPollsNeverExecuteTripParser0721() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaUnifiedHtmlCapture0605.kt",
        ).readText()

        assertFalse(source.contains("if (ready || pass + 1 >= PREPARE_PASSES_0605)"))
        assertTrue(source.contains("if (ready) {"))
        assertTrue(source.contains("tripReady0721 = false"))
        assertTrue(source.contains("detailReady0721 = detailPage?.tripReady0721 == true"))
    }

    @Test
    fun visibleLoadingIndicatorsBlockSemanticReadiness0721() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaUnifiedHtmlCapture0605.kt",
        ).readText()

        assertTrue(source.contains("[role=\"progressbar\"]"))
        assertTrue(source.contains("[aria-busy=\"true\"]"))
        assertTrue(source.contains("aria-valuetext"))
        assertTrue(source.contains("return !loadingActive && summary && boundControl"))
    }

    @Test
    fun notReadyCardGetsFreshDeferredPassWithoutAbortingSiblings0721() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaUnifiedHtmlCapture0605.kt",
        ).readText()

        assertTrue(source.contains("deferredNotReady0721"))
        assertTrue(source.contains("DEFERRED_NOT_READY_BACKOFF_MS_0721"))
        assertTrue(source.contains("BLABLACAR_TRIP_DETAIL_DEFERRED_RECOVERED_0721"))
        assertTrue(source.contains("BLABLACAR_TRIP_DETAIL_DEFERRED_STILL_PENDING_0721"))
        assertTrue(source.contains("preservePreviousCanonical=true"))
    }
}
