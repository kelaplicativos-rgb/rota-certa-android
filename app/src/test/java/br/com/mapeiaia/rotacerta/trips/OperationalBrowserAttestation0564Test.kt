package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OperationalBrowserAttestation0564Test {
    @Test
    fun finalDestinationIsConfirmedOnlyForExactTripAndLiveProfile() {
        assertEquals(
            OperationalBrowserNavigationResult0564.CONFIRMED,
            operationalBrowserNavigationResult0564(
                requestedTripId = "trip-a",
                finalTripId = "trip-a",
                profileMatches = true,
                authRequired = false,
                finalDestinationAllowed = true,
            ),
        )
        assertEquals(
            OperationalBrowserNavigationResult0564.REDIRECTED,
            operationalBrowserNavigationResult0564(
                requestedTripId = "trip-a",
                finalTripId = "trip-b",
                profileMatches = true,
                authRequired = false,
                finalDestinationAllowed = true,
            ),
        )
        assertEquals(
            OperationalBrowserNavigationResult0564.REDIRECTED,
            operationalBrowserNavigationResult0564(
                requestedTripId = "trip-a",
                finalTripId = "",
                profileMatches = true,
                authRequired = false,
                finalDestinationAllowed = true,
            ),
        )
        assertEquals(
            OperationalBrowserNavigationResult0564.AUTH_REQUIRED,
            operationalBrowserNavigationResult0564(
                requestedTripId = "trip-a",
                finalTripId = "",
                profileMatches = true,
                authRequired = true,
                finalDestinationAllowed = true,
            ),
        )
        assertEquals(
            OperationalBrowserNavigationResult0564.IDENTITY_MISMATCH,
            operationalBrowserNavigationResult0564(
                requestedTripId = "trip-a",
                finalTripId = "trip-a",
                profileMatches = false,
                authRequired = false,
                finalDestinationAllowed = true,
            ),
        )
        assertEquals(
            OperationalBrowserNavigationResult0564.INVALID_DESTINATION,
            operationalBrowserNavigationResult0564(
                requestedTripId = "trip-a",
                finalTripId = "trip-a",
                profileMatches = true,
                authRequired = false,
                finalDestinationAllowed = false,
            ),
        )
    }

    @Test
    fun operationalIndexDoesNotClaimSuccessAtActivityDispatch() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/OperationalAllTripsBrowserUi0563.kt",
        ).readText()
        assertTrue(source.contains("OPERATIONAL_BROWSER_TARGET_NAVIGATION_REQUESTED_0564"))
        assertFalse(source.contains("OPERATIONAL_BROWSER_TRIP_OPENED_0563"))
        assertTrue(source.contains("confirmed=false"))
        assertTrue(source.contains("OperationalTripBrowserIntents0564.open"))
    }

    @Test
    fun browserAttestsFinalLoadedTripBeforeConfirmedEvent() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/OperationalTripBrowserActivity0564.kt",
        ).readText()
        assertTrue(source.contains("view.url.orEmpty()"))
        assertTrue(source.contains("BlaBlaCollectorUrlModule.tripId(finalUrl)"))
        assertTrue(source.contains("OPERATIONAL_BROWSER_TARGET_NAVIGATION_CONFIRMED_0564"))
        assertTrue(source.contains("OPERATIONAL_BROWSER_TARGET_NAVIGATION_FAILED_0564"))
        assertTrue(source.contains("finalTripMatches="))
        assertTrue(source.contains("profileMatches="))
    }

    @Test
    fun everyNonOpenableVisibilityDecisionHasExplicitReasonContract() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/OperationalBrowserContracts0564.kt",
        ).readText()
        listOf(
            "PROFILE_UUID_MISSING",
            "ACCOUNT_NOT_CONNECTED",
            "IDENTITY_CONFLICT",
            "TRIP_ID_MISSING",
            "TRIP_HREF_MISSING",
            "TARGET_UNRESOLVED",
        ).forEach { reason -> assertTrue(source.contains(reason), reason) }

        val ui = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/OperationalAllTripsBrowserUi0563.kt",
        ).readText()
        assertTrue(ui.contains("OPERATIONAL_BROWSER_ENTRY_DECISION_0564"))
        assertTrue(ui.contains("operationalConnectedSelection0564"))
        assertTrue(ui.contains("piiLogged=false"))
    }
}
