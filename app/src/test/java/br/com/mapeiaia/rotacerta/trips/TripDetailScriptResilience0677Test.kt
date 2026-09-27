package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TripDetailScriptResilience0677Test {
    @Test
    fun itineraryDedupUsesTheDefinedPlaceNormalizer0677() {
        val script = File("src/main/assets/blablacar/scripts/trip_detail.js").readText()

        assertTrue(script.contains("placeKey(previous.label) === placeKey(value)"))
        assertTrue(script.contains("placeKey(item.label) !== placeKey(values[index - 1].label)"))
        assertFalse(script.contains("key(previous.label) === key(value)"))
        assertFalse(script.contains("key(item.label) !== key(values[index - 1].label)"))
    }

    @Test
    fun scriptFailureIsClassifiedWithoutTransportRetry0677() {
        val script = File("src/main/assets/blablacar/scripts/trip_detail.js").readText()
        val capture = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaUnifiedHtmlCapture0605.kt",
        ).readText()

        assertTrue(script.contains("scriptError:"))
        assertTrue(script.contains("scriptStage: 'trip_detail_0677'"))
        assertTrue(capture.contains("TRIP_DETAIL_SCRIPT_ERROR_0677"))
        assertTrue(capture.contains("scriptErrorPresent="))
        assertFalse(shouldRetryTripDetailFailure0676("TRIP_DETAIL_SCRIPT_ERROR_0677"))
    }

    @Test
    fun oneExhaustedCardDoesNotAbortTheRemainingProfile0677() {
        val capture = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaUnifiedHtmlCapture0605.kt",
        ).readText()

        assertTrue(capture.contains("action=SKIP_FAILED_CARD_CONTINUE_PROFILE"))
        assertTrue(capture.contains("remainingUnattempted=0"))
        assertFalse(capture.contains("action=STOP_PROFILE_PRESERVE_CANONICAL"))
        assertTrue(capture.contains("capture != null && previous != null"))
    }
}
