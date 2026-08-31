package br.com.mapeiaia.rotacerta.trips

import br.com.mapeiaia.rotacerta.RotaCertaTenantIdentity
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlaBlaAuditableCollection0379Test {
    private val zone = ZoneId.of("UTC")
    private val ownUuid = "11111111-1111-4111-8111-111111111111"
    private val otherUuid = "22222222-2222-4222-8222-222222222222"
    private val tenant = RotaCertaTenantIdentity(
        tenantId = "tenant-generic",
        displayName = "Generic Tenant",
        localeTag = "en-US",
        currencyCode = "USD",
    )
    private val ownProfile = BlaBlaDynamicAccount(
        id = "account-a",
        label = "Account A",
        webProfileName = "profile-a",
        profileUuid = ownUuid,
        profileName = "Driver Alpha",
    )

    @Test
    fun oneDayCreatesExactlyOutboundAndReturn() {
        val request = request(listOf("2026-09-03"))
        val tasks = BlaBlaPublicSearchPlanner.tasks(request)
        assertEquals(2, tasks.size)
        assertEquals("OUTBOUND", publicSearchDirectionName(request, tasks[0]))
        assertEquals("RETURN", publicSearchDirectionName(request, tasks[1]))
        assertEquals("Origin City", tasks[0].from)
        assertEquals("Destination City", tasks[0].to)
        assertEquals("Destination City", tasks[1].from)
        assertEquals("Origin City", tasks[1].to)
    }

    @Test
    fun inclusivePeriodCreatesDaysTimesTwoAndCorrectReverse() {
        val dates = (3..6).map { "2026-09-0$it" }
        val request = request(dates)
        val tasks = BlaBlaPublicSearchPlanner.tasks(request)
        assertEquals(8, tasks.size)
        assertEquals(dates, tasks.map { it.date.toString() }.distinct())
        tasks.chunked(2).forEach { pair ->
            assertEquals(pair[0].from, pair[1].to)
            assertEquals(pair[0].to, pair[1].from)
        }
    }

    @Test
    fun coverageStatesStayExplicitAndAnyNonCompleteMakesCoverageIncomplete() {
        val request = request(listOf("2026-09-03", "2026-09-04"))
        val tasks = BlaBlaPublicSearchPlanner.tasks(request)
        val coverages = listOf("COMPLETE", "PARTIAL", "PENDING_UNKNOWN", "FAILED")
        val response = response(
            request,
            tasks.mapIndexed { index, task -> query(request, task, coverages[index], cards = if (index == 0) 0 else 1) },
        )
        val snapshot = build(response)
        assertEquals(4, snapshot.summary.expectedQueries)
        assertEquals(1, snapshot.summary.completeQueries)
        assertEquals(1, snapshot.summary.partialQueries)
        assertEquals(1, snapshot.summary.pendingUnknownQueries)
        assertEquals(1, snapshot.summary.failedQueries)
        assertFalse(snapshot.summary.coverageComplete)
        assertTrue(snapshot.queries.first().evidence.zeroResultsConfirmed)
        assertFalse(snapshot.queries[2].evidence.zeroResultsConfirmed)
    }

    @Test
    fun completeZeroIsDifferentFromPartialPendingAndFailedZero() {
        val request = request(listOf("2026-09-03", "2026-09-04"))
        val tasks = BlaBlaPublicSearchPlanner.tasks(request)
        val states = listOf("COMPLETE", "PARTIAL", "PENDING_UNKNOWN", "FAILED")
        val snapshot = build(response(request, tasks.mapIndexed { i, t -> query(request, t, states[i], 0) }))
        assertTrue(snapshot.queries[0].evidence.zeroResultsConfirmed)
        assertFalse(snapshot.queries[1].evidence.zeroResultsConfirmed)
        assertFalse(snapshot.queries[2].evidence.zeroResultsConfirmed)
        assertFalse(snapshot.queries[3].evidence.zeroResultsConfirmed)
    }

    @Test
    fun allPublicCardsArePreservedAndOwnershipNeedsStrongProfileUuid() {
        val request = request(listOf("2026-09-03"))
        val tasks = BlaBlaPublicSearchPlanner.tasks(request)
        val q = publicSearchQueryId(request, tasks[0])
        val response = response(
            request = request,
            queries = tasks.map { query(request, it, "COMPLETE", if (it == tasks[0]) 3 else 0) },
            cards = listOf(
                card(q, tasks[0], "trip-own", ownUuid, "Driver Alpha", 0),
                card(q, tasks[0], "trip-third", otherUuid, "Driver Beta", 1),
                card(q, tasks[0], "trip-name-only", null, "Driver Alpha", 2),
            ),
        )
        val snapshot = build(response, profiles = listOf(ownProfile))
        assertEquals(3, snapshot.publicCards.size)
        assertEquals("CONFIRMED", snapshot.publicCards.single { it.tripId == "trip-own" }.ownership.ownership)
        assertEquals(listOf("PROFILE_UUID"), snapshot.publicCards.single { it.tripId == "trip-own" }.ownership.matchedBy)
        assertEquals("PENDING_UNKNOWN", snapshot.publicCards.single { it.tripId == "trip-third" }.ownership.ownership)
        assertEquals("PENDING_UNKNOWN", snapshot.publicCards.single { it.tripId == "trip-name-only" }.ownership.ownership)
        assertEquals(1, snapshot.summary.ownPublicTripsRecognized)
    }

    @Test
    fun tripIdProfileUuidAndSearchUuidRemainDistinctIdentityTypes() {
        val href = "https://www.blablacar.com.br/trip?id=trip-strong-123&search_uuid=temporary-search-999"
        assertEquals("trip-strong-123", BlaBlaCollectorUrlModule.tripId(href))
        assertFalse(BlaBlaCollectorUrlModule.canonical(href).contains("search_uuid"))
        assertNotEquals(ownUuid, BlaBlaCollectorUrlModule.tripId(href))
        assertNull(BlaBlaAuditableCollectionBuilder.strongUuid("trip-strong-123"))
        assertEquals(ownUuid, BlaBlaAuditableCollectionBuilder.strongUuid(ownUuid))
    }

    @Test
    fun strongPublicTripReconcilesCanonicalInventoryWithoutPassengerPii() {
        val request = request(listOf("2026-09-03"))
        val tasks = BlaBlaPublicSearchPlanner.tasks(request)
        val from = TripStop(id = "stop-a", order = 0, name = "Origin City")
        val to = TripStop(id = "stop-b", order = 1, name = "Destination City")
        val trip = Trip(
            id = "local-trip-a",
            title = "Origin City to Destination City",
            departureAtMillis = LocalDate.of(2026, 9, 3).atTime(10, 0).atZone(zone).toInstant().toEpochMilli(),
            capacity = 5,
            status = TripStatus.PUBLISHED,
            stops = listOf(from, to),
            blablaProfileUuid = ownUuid,
            blablaTripId = "trip-own",
            blablaPublicUrl = "https://www.blablacar.com.br/trip?id=trip-own&search_uuid=temporary",
            publishedSeats = 3,
            rotaCertaSeatAllocation = 2,
        )
        val booking = Booking(
            id = "booking-a",
            tripId = trip.id,
            passengerName = "PRIVATE PERSON MUST NOT EXPORT",
            passengerContact = "+000000000",
            boardingStopId = from.id,
            dropoffStopId = to.id,
            seats = 2,
            status = BookingStatus.CONFIRMED,
            source = BookingSource.ROTA_CERTA,
        )
        val response = response(
            request,
            tasks.map { query(request, it, "COMPLETE", if (it == tasks[0]) 1 else 0) },
            cards = listOf(card(publicSearchQueryId(request, tasks[0]), tasks[0], "trip-own", ownUuid, "Driver Alpha", 0)),
        )
        val snapshot = build(response, listOf(ownProfile), listOf(trip), listOf(booking))
        val local = snapshot.reconciledTrips.single()
        assertEquals("CONFIRMED_STRONG_IDENTITY", local.reconciliation.state)
        assertEquals(listOf("PROFILE_UUID", "TRIP_ID"), local.reconciliation.matchedBy)
        assertEquals(3, local.inventory.blablaQuotaSeats)
        assertEquals(2, local.inventory.rotaCertaQuotaSeats)
        assertEquals(5, local.inventory.operationalInventorySeats)
        assertEquals(2, local.inventory.confirmedPassengerSeats)
        assertEquals(3, local.segmentAvailability.single().availableSeats)
        val json = BlaBlaAuditableCollectionJson.encode(snapshot)
        assertFalse(json.contains("PRIVATE PERSON MUST NOT EXPORT"))
        assertFalse(json.contains("+000000000"))
    }

    @Test
    fun unmatchedAndSimilarTripsAreNeverIncorrectlyFused() {
        val request = request(listOf("2026-09-03"))
        val tasks = BlaBlaPublicSearchPlanner.tasks(request)
        val time = LocalDate.of(2026, 9, 3).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        fun local(id: String, external: String) = Trip(
            id = id,
            title = "Same route and time",
            departureAtMillis = time,
            status = TripStatus.PUBLISHED,
            stops = listOf(
                TripStop(id = "${id}-a", order = 0, name = "Origin City"),
                TripStop(id = "${id}-b", order = 1, name = "Destination City"),
            ),
            blablaTripId = external,
            blablaProfileUuid = ownUuid,
            publishedSeats = 2,
            rotaCertaSeatAllocation = 1,
        )
        val response = response(
            request,
            tasks.map { query(request, it, "COMPLETE", if (it == tasks[0]) 1 else 0) },
            listOf(card(publicSearchQueryId(request, tasks[0]), tasks[0], "public-only", otherUuid, "Driver Beta", 0)),
        )
        val snapshot = build(response, listOf(ownProfile), listOf(local("local-a", "local-a-ex"), local("local-b", "local-b-ex")))
        assertEquals(2, snapshot.reconciledTrips.size)
        assertTrue(snapshot.reconciledTrips.all { it.reconciliation.state == "NO_STRONG_PUBLIC_MATCH" })
        assertEquals(1, snapshot.publicCards.size)
    }

    @Test
    fun duplicateStrongPublicTripMergesQueriesButFallbackCardsRemainSeparate() {
        val request = request(listOf("2026-09-03"))
        val tasks = BlaBlaPublicSearchPlanner.tasks(request)
        val q0 = publicSearchQueryId(request, tasks[0])
        val q1 = publicSearchQueryId(request, tasks[1])
        val sameStrongA = card(q0, tasks[0], "shared-trip", ownUuid, "Driver Alpha", 0)
        val sameStrongB = card(q1, tasks[1], "shared-trip", ownUuid, "Driver Alpha", 0)
        val fallbackA = sameStrongA.copy(tripHref = null, tripId = null, profileUuid = null, captureIndex = 8)
        val fallbackB = fallbackA.copy(captureIndex = 9)
        val response = response(
            request,
            tasks.map { query(request, it, "COMPLETE", 2) },
            listOf(sameStrongA, sameStrongB, fallbackA, fallbackB),
        )
        val snapshot = build(response, listOf(ownProfile))
        val strong = snapshot.publicCards.single { it.tripId == "shared-trip" }
        assertEquals(listOf(q0, q1).sorted(), strong.queryIds)
        assertEquals(2, snapshot.publicCards.count { it.identityKind == "COMPOSITE_FALLBACK_NON_CANONICAL" })
    }

    @Test
    fun multipleDriverProfilesRemainDistinctAndNeverDependOnDisplayName() {
        val second = BlaBlaDynamicAccount(
            id = "account-b", label = "Account B", webProfileName = "profile-b",
            profileUuid = otherUuid, profileName = "Driver Alpha",
        )
        val request = request(listOf("2026-09-03"))
        val tasks = BlaBlaPublicSearchPlanner.tasks(request)
        val response = response(
            request,
            tasks.map { query(request, it, "COMPLETE", 0) },
        )
        val snapshot = build(response, listOf(ownProfile, second))
        assertEquals(listOf(ownUuid, otherUuid).sorted(), snapshot.driverProfiles.map { it.profileUuid }.sorted())
        assertEquals(2, snapshot.driverProfiles.size)
    }

    @Test
    fun jsonSchemaIsValidDeterministicVersionedUtf8AndRejectsUnknownSchema() {
        val request = request(listOf("2026-09-03"))
        val tasks = BlaBlaPublicSearchPlanner.tasks(request)
        val snapshot = build(response(request, tasks.map { query(request, it, "COMPLETE", 0) }))
        val first = BlaBlaAuditableCollectionJson.encode(snapshot)
        val second = BlaBlaAuditableCollectionJson.encode(snapshot)
        assertEquals(first, second)
        assertEquals("1.0", BlaBlaAuditableCollectionJson.decode(first).schemaVersion)
        assertTrue(first.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8).contains("\"schemaVersion\""))
        val unknown = first.replace("\"schemaVersion\": \"1.0\"", "\"schemaVersion\": \"2.0\"")
        assertFailsWith<IllegalArgumentException> { BlaBlaAuditableCollectionJson.decode(unknown) }

        val output = File("build/auditable-collection-sample.json")
        output.parentFile?.mkdirs()
        output.writeBytes(first.toByteArray(Charsets.UTF_8))
        assertTrue(output.isFile && output.length() > 0)
        assertEquals("1.0", BlaBlaAuditableCollectionJson.decode(output.readText(Charsets.UTF_8)).schemaVersion)
    }

    @Test
    fun forbiddenTermsAreRedactedAndFinalJsonContainsNoSecrets() {
        val request = request(listOf("2026-09-03"))
        val tasks = BlaBlaPublicSearchPlanner.tasks(request)
        val malicious = tasks.mapIndexed { i, task ->
            query(
                request, task, if (i == 0) "FAILED" else "COMPLETE", 0,
                error = if (i == 0) BlaBlaPublicSearchErrorDetail(
                    stage = "authorization",
                    exceptionClass = "BearerException",
                    exceptionMessage = "cookie secret accessToken refreshToken sessionToken password",
                    rootCauseMessage = "Authorization: Bearer hidden",
                ) else null,
            )
        }
        val json = BlaBlaAuditableCollectionJson.encode(build(response(request, malicious)))
        assertTrue(BlaBlaAuditableCollectionJson.forbiddenHits(json).isEmpty())
    }

    @Test
    fun fileShareContractReusesExistingProviderContentUriGrantAndJsonMime() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaAuditableCollection.kt").readText()
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val paths = File("src/main/res/xml/trip_file_paths_stage47.xml").readText()
        assertTrue(source.contains("FileProvider.getUriForFile"))
        assertTrue(source.contains("uri.scheme == \"content\""))
        assertTrue(source.contains("Intent.ACTION_SEND"))
        assertTrue(source.contains("Intent.FLAG_GRANT_READ_URI_PERMISSION"))
        assertTrue(source.contains("application/json"))
        assertTrue(source.contains("toByteArray(Charsets.UTF_8)"))
        assertTrue(manifest.contains("\${applicationId}.tripfiles"))
        assertTrue(paths.contains("trip_calendar"))
    }

    @Test
    fun collectionIsReadOnlyGlobalAndContainsNoPersonalScenarioHardcode() {
        val files = listOf(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaPublicSearch.kt",
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaPublicSearchActivity.kt",
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaPublicSearchUi.kt",
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaAuditableCollection.kt",
        ).map { File(it).readText() }.joinToString("\n")
        listOf("Ezequiel", "Barbosa", "Santo André", "São Thomé das Letras", "\"BRL\"").forEach {
            assertFalse(files.contains(it, ignoreCase = true), "Personal hardcode found: $it")
        }
        val activity = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaPublicSearchActivity.kt").readText()
        assertFalse(activity.contains("executeRemoteWrite("))
        assertFalse(activity.contains("matchesTarget(it.driverName"))
        assertTrue(activity.contains("BlaBlaBrowserRequest.PUBLIC_SEARCH_RESULTS"))
        assertTrue(activity.contains("BlaBlaBrowserRequest.PUBLIC_SEARCH_SCROLL"))
        val ui = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaPublicSearchUi.kt").readText()
        assertTrue(ui.contains("includeReverse = true"))
        assertTrue(ui.contains("RotaCertaDateSelectionMode.SINGLE"))
        assertTrue(ui.contains("RotaCertaDateSelectionMode.RANGE"))
        assertFalse(ui.contains("Nomes dos motoristas/perfis"))
    }

    private fun request(dates: List<String>) = BlaBlaPublicSearchRequest(
        targetNames = emptyList(),
        from = "Origin City",
        to = "Destination City",
        period = "",
        includeReverse = true,
        selectedDates = dates,
        captureDemand = true,
        collectionId = "collection-test",
    )

    private fun response(
        request: BlaBlaPublicSearchRequest,
        queries: List<BlaBlaPublicSearchQueryResult>,
        cards: List<BlaBlaPublicSearchCard> = emptyList(),
    ) = BlaBlaPublicSearchResponse(
        collectedAtMillis = 1_800_000_000_000L,
        status = if (queries.all { it.coverageStatus == "COMPLETE" }) "validated" else "partial",
        request = request,
        queries = queries,
        cards = cards,
    )

    private fun query(
        request: BlaBlaPublicSearchRequest,
        task: BlaBlaPublicSearchTask,
        coverage: String,
        cards: Int,
        error: BlaBlaPublicSearchErrorDetail? = null,
    ) = BlaBlaPublicSearchQueryResult(
        date = task.date.toString(),
        from = task.from,
        to = task.to,
        status = if (coverage == "COMPLETE") "validated" else coverage.lowercase(),
        cardCount = cards,
        zeroResultsConfirmed = coverage == "COMPLETE" && cards == 0,
        queryId = publicSearchQueryId(request, task),
        direction = publicSearchDirectionName(request, task),
        coverageStatus = coverage,
        startedAtMillis = 1_799_999_999_000L,
        finishedAtMillis = 1_800_000_000_000L,
        evidence = BlaBlaPublicSearchQueryEvidence(
            requestedDateConfirmed = coverage != "FAILED",
            requestedRouteConfirmed = coverage != "FAILED",
            terminalEvidence = coverage == "COMPLETE",
            stableAtBottom = coverage == "COMPLETE" && cards > 0,
        ),
        errorDetail = error,
    )

    private fun card(
        queryId: String,
        task: BlaBlaPublicSearchTask,
        tripId: String,
        profileUuid: String?,
        name: String,
        index: Int,
    ) = BlaBlaPublicSearchCard(
        driverName = name,
        date = task.date.toString(),
        searchFrom = task.from,
        searchTo = task.to,
        departureTime = "10:00",
        price = "25.00 USD",
        tripHref = "https://www.blablacar.com.br/trip?id=$tripId&search_uuid=temporary",
        queryId = queryId,
        direction = publicSearchDirectionName(request(listOf(task.date.toString())), task),
        tripId = tripId,
        profileUuid = profileUuid,
        profileUuidEvidence = profileUuid?.let { "PUBLIC_CARD_PROFILE_LINK" },
        currency = "USD",
        capturedAtMillis = 1_800_000_000_000L,
        captureIndex = index,
    )

    private fun build(
        response: BlaBlaPublicSearchResponse,
        profiles: List<BlaBlaDynamicAccount> = emptyList(),
        trips: List<Trip> = emptyList(),
        bookings: List<Booking> = emptyList(),
    ) = BlaBlaAuditableCollectionBuilder.build(
        response = response,
        tenant = tenant,
        profiles = profiles,
        trips = trips,
        bookings = bookings,
        collectorVersion = "0.1.379+test",
        zoneId = zone,
    )
}
