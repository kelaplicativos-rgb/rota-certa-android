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
    fun exactPublicSearchBindsDifferentPublicTokenWhenVerifiedProfileAndTimeAreUnique() {
        val resolved = resolveExactPublicSearchTripLink0448(
            expectedAdministrativeTripId = adminA,
            expectedDriverName = "Barbosa",
            expectedDepartureTime = "19:00",
            cards = listOf(
                DynamicPublicSearchLinkCard(
                    driverName = "Outro Motorista",
                    departureTime = "19:00",
                    href = href(publicB),
                ),
                DynamicPublicSearchLinkCard(
                    driverName = "Barbosa",
                    departureTime = "19:00",
                    href = "/trip?id=$publicA",
                ),
            ),
            providerOrigin = "https://www.blablacar.com.br/search?fn=origem&tn=destino",
        )

        assertEquals(href(publicA), resolved?.href)
        assertEquals("exact_public_search", resolved?.source)
        assertEquals(BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_ORCHESTRATOR_NAVIGATION, resolved?.binding)
    }

    @Test
    fun exactPublicSearchFailsClosedWhenProfileAndTimeMatchMoreThanOnePublicCard() {
        assertNull(
            resolveExactPublicSearchTripLink0448(
                expectedAdministrativeTripId = adminA,
                expectedDriverName = "Barbosa",
                expectedDepartureTime = "19:00",
                cards = listOf(
                    DynamicPublicSearchLinkCard(driverName = "Barbosa", departureTime = "19:00", href = href(publicA)),
                    DynamicPublicSearchLinkCard(driverName = "Barbosa", departureTime = "19:00", href = href(publicB)),
                ),
                providerOrigin = "https://www.blablacar.com.br/search",
            ),
        )
    }

    @Test
    fun exactPublicSearchRejectsWrongProfileWrongTimeAndForgedHost() {
        assertNull(
            resolveExactPublicSearchTripLink0448(
                expectedAdministrativeTripId = adminA,
                expectedDriverName = "Barbosa",
                expectedDepartureTime = "19:00",
                cards = listOf(
                    DynamicPublicSearchLinkCard(driverName = "Outro", departureTime = "19:00", href = href(publicA)),
                    DynamicPublicSearchLinkCard(driverName = "Barbosa", departureTime = "18:00", href = href(publicA)),
                    DynamicPublicSearchLinkCard(driverName = "Barbosa", departureTime = "19:00", href = "https://example.com/trip?id=$publicA"),
                ),
                providerOrigin = "https://www.blablacar.com.br/search",
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
        assertTrue(source.contains("resolveExactPublicSearchTripLink0448"))
        assertTrue(source.contains("PUBLIC_TRIP_BINDING_ORCHESTRATOR_NAVIGATION"))
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

    @Test
    fun collectorAuthorityOverwritesCorruptedCanonicalPublicLinkAndIsIdempotent0490() {
        val corrupted = href(publicB)
        val observed = source(adminA, href(publicA))
        val trip = canonicalTrip0490(
            adminId = adminA,
            canonicalUrl = corrupted,
            snapshot = observed,
        )

        val repaired = reconciledCanonicalBlaBlaPublicUrl0490(trip)
        assertEquals(href(publicA), repaired)

        val secondPass = reconciledCanonicalBlaBlaPublicUrl0490(
            trip.copy(blablaPublicUrl = repaired),
        )
        assertEquals(repaired, secondPass)
    }

    @Test
    fun temporaryCollectorAbsenceNeverErasesPreviouslyValidatedCanonicalLink0490() {
        val existing = href(publicA)
        val missingObservation = source(adminA, "")
        val trip = canonicalTrip0490(
            adminId = adminA,
            canonicalUrl = existing,
            snapshot = missingObservation,
        )

        assertNull(canonicalCollectorAuthorityPublicUrl0490(trip))
        assertEquals(existing, reconciledCanonicalBlaBlaPublicUrl0490(trip))
    }

    @Test
    fun collectorEvidenceFromAnotherStrongTripCannotCrossCanonicalIdentity0490() {
        val wrongTrip = canonicalTrip0490(
            adminId = adminA,
            canonicalUrl = null,
            snapshot = source(adminB, href(publicB)),
        )
        val wrongProfile = canonicalTrip0490(
            adminId = adminA,
            canonicalUrl = null,
            snapshot = source(adminA, href(publicA)).copy(
                profile_uuid = "22222222-2222-4222-8222-222222222222",
            ),
        )

        assertNull(canonicalCollectorAuthorityPublicUrl0490(wrongTrip))
        assertNull(reconciledCanonicalBlaBlaPublicUrl0490(wrongTrip))
        assertNull(canonicalCollectorAuthorityPublicUrl0490(wrongProfile))
        assertNull(reconciledCanonicalBlaBlaPublicUrl0490(wrongProfile))
    }

    @Test
    fun timelinePublicAndOverflowActionsReadTheSameCanonicalProjection0490() {
        val timelineModel = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimeline.kt").readText()
        val timelineUi = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()
        val passengerUi = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt").readText()

        assertTrue(timelineModel.contains("canonicalTimelineBlaBlaPublicHref0490"))
        assertTrue(timelineModel.contains("canonicalBoundBlaBlaPublicUrl0423(entry.blablaPublicHref, entry.blablaTripId)"))
        assertTrue(timelineUi.contains("val canonicalPublicationHref0490"))
        assertTrue(timelineUi.contains("val href = canonicalPublicationHref0490"))
        assertTrue(passengerUi.contains("val canonicalPublicHref0490 = canonicalTimelineBlaBlaPublicHref0490(entry)"))
        assertTrue(passengerUi.contains("openPublicTripBlaBla(context, canonicalPublicHref0490)"))
        assertTrue(!timelineUi.contains("entry.blablaPublicHref ?: entry.blablaTripHref.orEmpty()"))
    }

    @Test
    fun canonicalIntegrityMigrationProjectsCollectorAuthorityBackIntoExistingBindings0490() {
        val storeSource = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripStore.kt").readText()

        assertTrue(storeSource.contains("canonicalCollectorAuthorityPublicUrl0490(conflictAware)"))
        assertTrue(storeSource.contains("BLABLACAR_PUBLIC_URL_RECONCILED_0490"))
        assertTrue(storeSource.contains("blablaPublicHref = canonicalPublicHref0490.ifBlank { binding.blablaPublicHref }"))
    }

    private fun canonicalTrip0490(
        adminId: String,
        canonicalUrl: String?,
        snapshot: BlaBlaCollectorTrip,
    ) = Trip(
        id = "canonical-$adminId",
        title = "Origem → Destino",
        departureAtMillis = 1_900_000_000_000L,
        capacity = 2,
        status = TripStatus.PUBLISHED,
        stops = listOf(
            TripStop(id = "from", order = 0, name = "Origem"),
            TripStop(id = "to", order = 1, name = "Destino"),
        ),
        blablaProfileUuid = "11111111-1111-4111-8111-111111111111",
        blablaTripId = adminId,
        blablaManageUrl = "https://www.blablacar.com.br/rides/offer/$adminId",
        blablaPublicUrl = canonicalUrl,
        recordOrigin = TripRecordOrigin.EXTERNAL_BACKING,
        externalSnapshot = snapshot,
        externalSnapshotComplete = true,
        tripKey = "tenant|blablacar|profile|$adminId",
    )

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
