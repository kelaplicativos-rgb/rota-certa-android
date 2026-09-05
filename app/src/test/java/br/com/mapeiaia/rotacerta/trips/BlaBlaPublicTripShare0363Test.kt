package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

class BlaBlaPublicTripShare0363Test {
    @Test
    fun documentedShareControlIsCapturedWithoutOpeningAndroidShareSheet() {
        val script = File("src/main/assets/blablacar/scripts/trip_public_share.js").readText()
        val browser = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()

        assertTrue(script.contains("compartilhar esta carona"))
        assertTrue(script.contains("Object.defineProperty(navigator, 'share'"))
        assertTrue(script.contains("canCaptureWithoutOpeningSystemShare"))
        assertTrue(script.contains("state.clicks < 3"))
        assertTrue(script.contains("if (requireAdministrativeId && id !== tripId) return '';"))
        assertTrue(script.contains("authoritativeSharedPublicTripUrl"))
        assertTrue(script.contains("acceptCandidate(piece, true)"))
        assertTrue(script.contains("url.searchParams.delete('search_uuid')"))

        assertTrue(browser.contains("Phase.PUBLIC_SHARE"))
        assertTrue(browser.contains("BlaBlaBrowserRequest.TRIP_PUBLIC_SHARE"))
        assertTrue(browser.contains("PUBLIC_TRIP_LINK_CAPTURED"))
        assertTrue(browser.contains("PUBLIC_TRIP_LINK_UNAVAILABLE"))
        assertTrue(browser.contains("systemShareOpened=false"))
        assertTrue(browser.contains("private const val MAX_PUBLIC_TRIP_SHARE_READ_ATTEMPTS = 2"))
        assertTrue(browser.contains("publicTripShareReadAttempts < MAX_PUBLIC_TRIP_SHARE_READ_ATTEMPTS"))
        assertTrue(browser.contains("postSessionDelayed0405({"))
        assertTrue(browser.contains("PUBLIC_TRIP_SHARE_RETRY_MS"))
        assertTrue(browser.contains("BlaBlaCollectorUrlModule.publicTripFromAuthoritativeOrchestratorNavigation("))
        assertTrue(browser.contains("PUBLIC_TRIP_BINDING_ORCHESTRATOR_NAVIGATION"))
    }

    @Test
    fun shareActionCanBindDifferentPublicTokenOnlyToVerifiedAdministrativeTrip() {
        val administrativeTripId = "01a0359e-de23-78ab-ab26-6cc973c5c3d1"
        val publicTripId = "019ed00c-1919-7896-8507-5315d113f690"
        val href = "https://www.blablacar.com.br/trip?id=$publicTripId"

        assertNull(BlaBlaCollectorUrlModule.publicTrip(href, administrativeTripId))
        assertEquals(
            href,
            BlaBlaCollectorUrlModule.publicTripFromAuthoritativeOrchestratorNavigation(
                raw = href,
                expectedAdministrativeTripId = administrativeTripId,
                boundAdministrativeTripId = administrativeTripId,
            ),
        )
        assertNull(
            BlaBlaCollectorUrlModule.publicTripFromAuthoritativeOrchestratorNavigation(
                raw = href,
                expectedAdministrativeTripId = administrativeTripId,
                boundAdministrativeTripId = "different-administrative-trip",
            ),
        )
    }

    @Test
    fun capturedPublicLinkSurvivesLaterCompleteRefreshThatDoesNotExposeShareUrl() {
        val prior = trip(
            publicHref = "https://www.blablacar.com.br/trip?id=trip-363",
            complete = true,
        )
        val later = trip(publicHref = null, complete = true)

        val merged = BlaBlaCollectorTimelineModule.mergeSnapshotTrips(
            previous = listOf(prior),
            current = listOf(later),
            authoritativeComplete = true,
        ).trips.single()

        assertEquals("https://www.blablacar.com.br/trip?id=trip-363", merged.public_trip_href)
    }

    private fun trip(publicHref: String?, complete: Boolean): BlaBlaCollectorTrip = BlaBlaCollectorTrip(
        profile_uuid = "7371f028-9c55-4903-8444-308015823efd",
        profile_name = "Ezequiel S",
        date = "2026-09-04",
        departure_time = "10:30",
        actual_departure = "São Paulo",
        actual_arrival = "São Tomé das Letras",
        trip_href = "https://www.blablacar.com.br/rides/offer/trip-363",
        public_trip_href = publicHref,
        trip_id = "trip-363",
        booked_seats = 0,
        passenger_roster_complete = complete,
    )
}
