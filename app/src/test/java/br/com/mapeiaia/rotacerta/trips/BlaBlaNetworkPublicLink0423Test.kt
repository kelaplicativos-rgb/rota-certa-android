package br.com.mapeiaia.rotacerta.trips

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlaBlaNetworkPublicLink0423Test {
    private val adminA = "admin-trip-0423-a"
    private val adminB = "admin-trip-0423-b"
    private val publicA = "AaA1b3otfQu_hBV-_wf_NUAE2q6YFjakQATP8vIER6w"
    private val publicB = "AaA1differentPublicToken_0423_B"

    @Test
    fun structuredNetworkEvidenceBindsDifferentPublicTokenToCurrentAdministrativeTrip() {
        val resolved = BlaBlaCollectorNetworkSourceModule.resolvePublicTrip(
            adminA,
            evidence(
                adminId = adminA,
                rawHref = "http://www.blablacar.com.br/trip?source=CARPOOLING&id=$publicA&search_uuid=temp&p0%5Bac%5D=adult",
            ),
        )

        assertEquals(adminA, resolved?.administrativeTripId)
        assertEquals(publicA, resolved?.publicTripId)
        assertEquals(
            "https://www.blablacar.com.br/trip?source=CARPOOLING&id=$publicA&p0%5Bac%5D=adult",
            resolved?.publicTripHref,
        )
        assertEquals(BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_NETWORK_AUTHORITATIVE, resolved?.binding)
    }

    @Test
    fun networkEvidenceFromAnotherCardIsRejectedAndConsecutiveCardsStayIndependent() {
        assertNull(BlaBlaCollectorNetworkSourceModule.resolvePublicTrip(adminA, evidence(adminB, href(publicB))))

        val a = BlaBlaCollectorNetworkSourceModule.resolvePublicTrip(adminA, evidence(adminA, href(publicA)))
        val b = BlaBlaCollectorNetworkSourceModule.resolvePublicTrip(adminB, evidence(adminB, href(publicB)))

        assertEquals(publicA, a?.publicTripId)
        assertEquals(publicB, b?.publicTripId)
        assertEquals(adminA, a?.administrativeTripId)
        assertEquals(adminB, b?.administrativeTripId)
    }

    @Test
    fun orchestratorNavigationBindsDifferentPublicTokenOnlyToTheRequestedAdministrativeTrip() {
        val bound = bindOrchestratorPublicTripNavigation0443(
            rawUrl = href(publicA),
            expectedAdministrativeTripId = adminA,
            requestedAdministrativeTripId = adminA,
        )
        assertEquals(href(publicA), bound?.href)
        assertEquals("orchestrator_navigation", bound?.source)
        assertEquals(BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_ORCHESTRATOR_NAVIGATION, bound?.binding)
        assertNull(
            bindOrchestratorPublicTripNavigation0443(
                rawUrl = href(publicA),
                expectedAdministrativeTripId = adminB,
                requestedAdministrativeTripId = adminA,
            ),
        )
        assertNull(
            bindOrchestratorPublicTripNavigation0443(
                rawUrl = "https://example.com/trip?id=$publicA",
                expectedAdministrativeTripId = adminA,
                requestedAdministrativeTripId = adminA,
            ),
        )
    }

    @Test
    fun structuredNetworkBindingIsCaseExactForAdministrativeTripIdentity() {
        assertNull(
            BlaBlaCollectorNetworkSourceModule.resolvePublicTrip(
                adminA,
                evidence(adminA.uppercase(), href(publicA)),
            ),
        )
    }

    @Test
    fun downstreamTimelineAndAgendaHashKeepAuthoritativeCollectorPermalink() {
        val timeline = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripBlaBlaCollector.kt").readText()
        val agenda = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PublicAgendaAutoSync0300.kt").readText()
        assertTrue(timeline.contains("BlaBlaCollectorUrlModule.publicTripForCollectorState("))
        assertTrue(timeline.contains("trip.public_trip_href_binding"))
        assertTrue(agenda.contains("canonicalBoundBlaBlaPublicUrl0423(trip.blablaPublicUrl, trip.blablaTripId)"))
    }

    @Test
    fun acquisitionOrderIsNetworkThenAuthoritativeNavigationThenDomThenPersisted() {
        val network = link("network")
        val navigation = ResolvedPublicTripLink0423(
            href = href(publicA),
            source = "orchestrator_navigation",
            binding = BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_ORCHESTRATOR_NAVIGATION,
        )
        val dom = link("dom")
        val persisted = link("persisted")

        assertEquals(network, resolvePreferredPublicTripLink0423(network, dom, persisted, navigation))
        assertEquals(navigation, resolvePreferredPublicTripLink0423(null, dom, persisted, navigation))
        assertEquals(dom, resolvePreferredPublicTripLink0423(null, dom, persisted, null))
        assertEquals(persisted, resolvePreferredPublicTripLink0423(null, null, persisted, null))
        assertNull(resolvePreferredPublicTripLink0423(null, null, null, null))
    }

    @Test
    fun browserFlowKeepsDomShareAndExactSearchAsOrderedFallbacks() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()
        val network = source.indexOf("val networkPublicLink =")
        val navigation = source.indexOf("val authoritativeNavigationPublicLink =")
        val dom = source.indexOf("val passivePublicLink =")
        val persisted = source.indexOf("val persistedPublicLink =")
        val share = source.indexOf("PUBLIC_TRIP_LINK_SHARE_FALLBACK")
        val exactSearch = source.indexOf("beginExactPublicTripSearch")

        assertTrue(network >= 0)
        assertTrue(navigation > network)
        assertTrue(dom > navigation)
        assertTrue(persisted > dom)
        assertTrue(share > persisted)
        assertTrue(exactSearch > share)
    }

    @Test
    fun staleCallbacksAndCardChangesRemainFailClosed() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()
        assertTrue(source.contains("expectedSync != syncGeneration"))
        assertTrue(source.contains("expectedNavigation != navigationGeneration"))
        assertTrue(source.contains("expectedCandidate != candidateIndex"))
        assertTrue(source.contains("!pendingTripIsCurrent(expectedSync, expectedCandidate)"))
        assertTrue(source.contains("targetTripId.isBlank()"))
        assertTrue(source.contains("capturedSync == syncGeneration"))
        assertTrue(source.contains("capturedNavigation == navigationGeneration"))
        assertTrue(source.contains("STALE_CALLBACK_IGNORED"))
    }

    @Test
    fun provenanceSurvivesCollectorSerialization() {
        val original = source(
            adminId = adminA,
            publicHref = "https://www.blablacar.com.br/trip?id=$publicA",
        )
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val restored = json.decodeFromString<BlaBlaCollectorTrip>(json.encodeToString(original))

        assertEquals(original.public_trip_href, restored.public_trip_href)
        assertEquals(original.public_trip_href_source, restored.public_trip_href_source)
        assertEquals(original.public_trip_href_binding, restored.public_trip_href_binding)
        assertEquals(adminA, restored.trip_id)
    }

    @Test
    fun agendaProjectionReceivesExactCanonicalNetworkPermalink() {
        val raw = "http://www.blablacar.com.br/trip?source=CARPOOLING&id=$publicA&p0%5Bac%5D=adult"
        val source = source(adminA, raw)
        val projected = PublicAgendaAutoSync0300.toPublicTrip(
            source = source,
            capacity = 2,
            nowMillis = Long.MIN_VALUE,
            rotaCertaSeatAllocation = 0,
        )

        assertEquals(adminA, projected?.trip?.blablaTripId)
        assertEquals(
            "https://www.blablacar.com.br/trip?source=CARPOOLING&id=$publicA&p0%5Bac%5D=adult",
            projected?.trip?.blablaPublicUrl,
        )
        assertEquals(projected?.trip?.blablaPublicUrl, projected?.blablaPublicHref)
    }

    @Test
    fun invalidFreshCollectorPermalinkPreservesPreviousValidatedPermalinkAndBinding() {
        val previous = source(adminA, href(publicA))
        val invalidFresh = source(adminA, "https://example.com/trip?id=forged-0423").copy(
            public_trip_href_source = "network_structured",
            public_trip_href_binding = BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_NETWORK_AUTHORITATIVE,
        )

        val merged = BlaBlaCollectorPassengerModule.mergeMonotonic(previous, invalidFresh)

        assertEquals(previous.public_trip_href, merged.public_trip_href)
        assertEquals(previous.public_trip_href_source, merged.public_trip_href_source)
        assertEquals(previous.public_trip_href_binding, merged.public_trip_href_binding)
    }

    @Test
    fun canonicalStatePreservesAuthoritativelyBoundPermalinkWhenFreshObservationIsEmpty() {
        val existing = "https://www.blablacar.com.br/trip?id=$publicA"
        assertEquals(
            existing,
            canonicalBlaBlaPublicUrl0409(
                existingUrl = existing,
                observedUrl = null,
                expectedTripId = adminA,
            ),
        )
    }

    private fun evidence(adminId: String, rawHref: String) = BlaBlaNetworkTripSourceEvidence(
        tripId = adminId,
        publicTripHref = rawHref,
        publicTripHrefSource = "network_structured",
        publicTripHrefBinding = BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_NETWORK_AUTHORITATIVE,
        publicTripHrefEndpoint = "https://api.blablacar.com/ride/v3",
        publicTripHrefJsonPath = "\$.tripConditions[0].tripActions.actions[0].share.url",
    )

    private fun source(adminId: String, publicHref: String) = BlaBlaCollectorTrip(
        profile_uuid = "11111111-1111-4111-8111-111111111111",
        date = "2030-09-10",
        departure_time = "11:00",
        search_from = "Origem",
        search_to = "Destino",
        actual_departure = "Origem",
        actual_arrival = "Destino",
        trip_href = "https://www.blablacar.com.br/rides/offer/$adminId",
        public_trip_href = publicHref,
        public_trip_href_source = "network_structured",
        public_trip_href_binding = BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_NETWORK_AUTHORITATIVE,
        trip_id = adminId,
        availability = "available",
        published_seats = 2,
        passenger_roster_complete = true,
    )

    private fun href(publicId: String) = "https://www.blablacar.com.br/trip?id=$publicId"

    private fun link(source: String) = ResolvedPublicTripLink0423(
        href = "https://www.blablacar.com.br/trip?id=$source-0423",
        source = source,
        binding = BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_SAME_ID,
    )
}
