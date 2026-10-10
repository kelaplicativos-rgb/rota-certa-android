package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression protection: only a transient profile WebView lease collision may
 * be rescheduled. All other failures must remain fail-closed and protect the
 * previous complete canonical HTML state.
 */
class BlaBlaGlobalHtmlBusyRecovery0768Test {
    @Test
    fun retryOnlyTransientSingleFlightCollision0768() {
        assertTrue(globalHtmlBusyRetryable0768("UNIFIED_SINGLE_FLIGHT_BUSY"))
    }

    @Test
    fun doNotRetryIdentityOrIncompletePassengerData0768() {
        listOf(
            "",
            "EXPECTED_PROFILE_UUID_MISSING",
            "ACCOUNT_PROFILE_UUID_MISMATCH",
            "RIDES_HTML_NOT_MATERIALIZED",
            "RIDES_OBSERVED_INVENTORY_MISMATCH_0613",
            "UNIFIED_TRIP_PASSENGER_INCOMPLETE",
            "CANONICAL_FINAL_COMMIT_NOT_CONFIRMED",
            "PROFILE_BROWSER_BUSY",
        ).forEach { assertFalse(globalHtmlBusyRetryable0768(it), "must fail-closed: $it") }
    }
}
