package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlaBlaExactPublicSearch0588Test {
    private val administrativeTripId = "01a03566-5325-7de5-abd4-d2a7a5e858e3"
    private val profileUuid = "175a7068-50d8-40c3-a27a-214b9c6e0461"
    private val origin = "https://www.blablacar.com.br"

    @Test
    fun strongProfileEvidenceOutranksSameNameDecoyWithoutGuessing() {
        val decoy = DynamicPublicSearchLinkCard(
            driverName = "Barbosa",
            departureTime = "11:30",
            href = "/trip?id=PublicTokenDecoy0588",
        )
        val verified = DynamicPublicSearchLinkCard(
            driverName = "Barbosa",
            departureTime = "11:30",
            href = "/trip?id=PublicTokenVerified0588",
            profileHrefs = listOf("/member/$profileUuid"),
        )

        val resolved = resolveExactPublicSearchTripLink0448(
            expectedAdministrativeTripId = administrativeTripId,
            expectedProfileUuid = profileUuid,
            expectedDriverName = "Barbosa",
            expectedDepartureTime = "11:30",
            cards = listOf(decoy, verified),
            providerOrigin = origin,
        )

        assertEquals(
            "$origin/trip?id=PublicTokenVerified0588",
            resolved?.href,
        )
        assertEquals(BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_ORCHESTRATOR_NAVIGATION, resolved?.binding)
    }

    @Test
    fun twoStrongProfileMatchesRemainAmbiguousAndFailClosed() {
        val cards = listOf(
            DynamicPublicSearchLinkCard(
                driverName = "Barbosa",
                departureTime = "19:00",
                href = "/trip?id=PublicTokenOne0588",
                profileHrefs = listOf("/user/$profileUuid"),
            ),
            DynamicPublicSearchLinkCard(
                driverName = "Barbosa",
                departureTime = "19:00",
                href = "/trip?id=PublicTokenTwo0588",
                profileHrefs = listOf("/user/$profileUuid"),
            ),
        )

        val resolved = resolveExactPublicSearchTripLink0448(
            expectedAdministrativeTripId = administrativeTripId,
            expectedProfileUuid = profileUuid,
            expectedDriverName = "Barbosa",
            expectedDepartureTime = "19:00",
            cards = cards,
            providerOrigin = origin,
        )
        val summary = exactPublicSearchMatchSummary0588(
            expectedAdministrativeTripId = administrativeTripId,
            expectedProfileUuid = profileUuid,
            expectedDriverName = "Barbosa",
            expectedDepartureTime = "19:00",
            cards = cards,
            providerOrigin = origin,
        )

        assertNull(resolved)
        assertEquals(2, summary.timeMatched)
        assertEquals(2, summary.acceptedPublicHref)
        assertEquals(2, summary.profileRank)
        assertEquals(4, summary.strongestRank)
        assertEquals(2, summary.strongestDistinctHrefs)
    }

    @Test
    fun publicSearchScriptUsesProfileAnchorAsDriverNameFallback() {
        val script = File("src/main/assets/blablacar/scripts/public_search_results.js").readText()
        assertTrue(script.contains("const profileAnchors"))
        assertTrue(script.contains("||profileName"))
        assertTrue(script.contains("a[href*=\"/profile\"]"))
        assertTrue(script.contains("a[href*=\"/member\"]"))
        assertTrue(script.contains("a[href*=\"/user\"]"))
    }
}
