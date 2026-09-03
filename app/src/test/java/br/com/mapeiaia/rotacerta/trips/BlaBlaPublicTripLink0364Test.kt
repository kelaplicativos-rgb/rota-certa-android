package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlaBlaPublicTripLink0364Test {
    @Test
    fun exactPublicSearchHrefIsAcceptedOnlyWhenTripIdMatchesTimelineCard() {
        val result = exactPublicTripHrefForTrip(
            expectedTripId = "trip-0364",
            hrefs = listOf(
                "https://www.blablacar.com.br/trip?id=other-trip",
                "/trip?id=trip-0364&search_uuid=temporary",
            ),
        )

        assertEquals(
            "https://www.blablacar.com.br/trip?id=trip-0364",
            result,
        )
    }

    @Test
    fun noPublicSearchResultMayInventUrlFromAdministrativeTripId() {
        val result = exactPublicTripHrefForTrip(
            expectedTripId = "trip-0364",
            hrefs = listOf(
                "https://www.blablacar.com.br/rides/offer/trip-0364",
                "https://www.blablacar.com.br/trip?id=another-trip",
            ),
        )

        assertNull(result)
    }

    @Test
    fun syncFallsBackFromShareActionToExactPublicSearchAndKeepsStrongIdentity() {
        val dynamic = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()

        assertTrue(dynamic.contains("PUBLIC_TRIP_SHARE_FALLBACK_REQUIRED"))
        assertTrue(dynamic.contains("beginExactPublicTripSearch"))
        assertTrue(dynamic.contains("Phase.PUBLIC_SEARCH_LINK"))
        assertTrue(dynamic.contains("BlaBlaPublicPlaceDirectory.searchUrl(it, providerOrigin)"))
        assertTrue(dynamic.contains("BlaBlaBrowserRequest.PUBLIC_SEARCH_RESULTS"))
        assertTrue(dynamic.contains("exactPublicTripHrefForTrip"))
        assertTrue(dynamic.contains("source=exact_public_search exactTrip=true"))
        assertTrue(dynamic.contains("reason=no_exact_trip_id_match action=continue_without_inventing_link"))
    }

    @Test
    fun septemberFourthRouteUsedInPhysicalFailureIsSupportedByExactSearchDirectory() {
        val task = BlaBlaPublicSearchTask(
            date = java.time.LocalDate.of(2026, 9, 4),
            from = "São Paulo",
            to = "São Tomé das Letras",
        )

        val url = BlaBlaPublicPlaceDirectory.searchUrl(task)

        assertTrue(url != null)
        assertTrue(url.contains("db=2026-09-04"))
        assertTrue(url.contains("fn=S%C3%A3o+Paulo"))
        assertTrue(url.contains("tn=S%C3%A3o+Tom%C3%A9+das+Letras"))
    }
}
