package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlaBlaRidesEnrichment0533Test {
    private val profileUuid = "11111111-1111-4111-8111-111111111111"
    private val otherProfileUuid = "22222222-2222-4222-8222-222222222222"
    private val tripId = "trip_admin_0001"
    private val tripHref = "https://www.blablacar.fr/rides/offer?id=$tripId"

    private fun completeTrip(
        passengers: List<BlaBlaCollectorPassenger> = emptyList(),
        rosterComplete: Boolean = true,
        profile: String = profileUuid,
        id: String = tripId,
    ) = BlaBlaCollectorTrip(
        profile_uuid = profile,
        date = "2026-09-20",
        departure_time = "11:30",
        actual_departure = "Origin",
        actual_arrival = "Destination",
        trip_href = "https://www.blablacar.fr/rides/offer?id=$id",
        trip_id = id,
        passengers = passengers,
        passenger_roster_complete = rosterComplete,
        booked_seats = passengers.sumOf { it.seats },
    )

    private fun session(
        updatedAt: Long = 2_000L,
        profile: String = profileUuid,
        identityVerified: Boolean = true,
        access: BlaBlaSourceAccessStatus0426 = BlaBlaSourceAccessStatus0426.AVAILABLE,
        trips: List<BlaBlaCollectorTrip> = listOf(completeTrip()),
    ) = BlaBlaDynamicSessionSnapshot(
        accountId = "account",
        profileUuid = profile,
        identityVerified = identityVerified,
        updatedAtMillis = updatedAt,
        trips = trips,
        sourceAccessStatus0426 = access,
    )

    @Test
    fun inventoryHrefExtractionUsesAuthenticatedMarketOriginAndStrongTripId() {
        val html = """
            <a href="/rides/offer?id=trip_admin_0001&amp;search_uuid=volatile">A</a>
            <a href="/trip/public_00000001">public</a>
            <a href="/rides/offer/edit/trip_admin_0002">edit</a>
        """.trimIndent()

        val targets = extractInventoryTripTargets0533(html, "https://www.blablacar.fr/rides")

        assertEquals(setOf(tripId), targets.keys)
        assertEquals("https://www.blablacar.fr/rides/offer?id=$tripId", targets[tripId])
        assertFalse(targets.values.any { it.contains("search_uuid") })
    }

    @Test
    fun ambiguousInventoryHrefNeverChoosesByPosition() {
        val html = """
            <a href="https://www.blablacar.fr/rides/offer?id=$tripId">A</a>
            <a href="https://www.blablacar.com/rides/offer?id=$tripId">B</a>
        """.trimIndent()

        val targets = extractInventoryTripTargets0533(html, "https://www.blablacar.fr/rides")

        assertFalse(targets.containsKey(tripId))
    }

    @Test
    fun exactSyncWithAuthoritativeEmptyRosterIsCompleteNotNoDataInference() {
        val observed = completeTrip(passengers = emptyList(), rosterComplete = true)
        val decision = classifyEnrichmentResult0533(
            runSucceeded = true,
            runFailure = "",
            expectedProfileUuid = profileUuid,
            expectedTripId = tripId,
            attemptStartedAtMillis = 1_000L,
            session = session(trips = listOf(observed)),
            trip = observed,
        )

        assertEquals(BlaBlaRidesEnrichmentStatus0533.COMPLETE, decision.status)
        assertEquals("", decision.error)
    }

    @Test
    fun emptyOrPartialParserStateNeverBecomesNoPassengers() {
        val observed = completeTrip(passengers = emptyList(), rosterComplete = false)
        val decision = classifyEnrichmentResult0533(
            runSucceeded = true,
            runFailure = "",
            expectedProfileUuid = profileUuid,
            expectedTripId = tripId,
            attemptStartedAtMillis = 1_000L,
            session = session(trips = listOf(observed)),
            trip = observed,
        )

        assertEquals(BlaBlaRidesEnrichmentStatus0533.PARTIAL, decision.status)
        assertEquals("PASSENGER_ROSTER_NOT_TERMINAL", decision.error)
        assertTrue(shouldRetryEnrichment0533(decision.status, decision.error))
    }

    @Test
    fun httpRestrictionOrFailedRunIsFailedInsteadOfEmptyData() {
        val decision = classifyEnrichmentResult0533(
            runSucceeded = false,
            runFailure = "TEMPORARILY_RESTRICTED",
            expectedProfileUuid = profileUuid,
            expectedTripId = tripId,
            attemptStartedAtMillis = 1_000L,
            session = null,
            trip = null,
        )

        assertEquals(BlaBlaRidesEnrichmentStatus0533.FAILED, decision.status)
        assertEquals("TEMPORARILY_RESTRICTED", decision.error)
        assertFalse(shouldRetryEnrichment0533(decision.status, decision.error))
    }

    @Test
    fun timeoutIsTerminalFailedAndRetryableWithoutInventingTripData() {
        val decision = classifyEnrichmentResult0533(
            runSucceeded = false,
            runFailure = "TIMEOUT",
            expectedProfileUuid = profileUuid,
            expectedTripId = tripId,
            attemptStartedAtMillis = 1_000L,
            session = null,
            trip = null,
        )

        assertEquals(BlaBlaRidesEnrichmentStatus0533.FAILED, decision.status)
        assertTrue(shouldRetryEnrichment0533(decision.status, decision.error))
    }

    @Test
    fun staleSessionAfterRunIsPartialNotSuccess() {
        val observed = completeTrip()
        val decision = classifyEnrichmentResult0533(
            runSucceeded = true,
            runFailure = "",
            expectedProfileUuid = profileUuid,
            expectedTripId = tripId,
            attemptStartedAtMillis = 2_000L,
            session = session(updatedAt = 1_999L, trips = listOf(observed)),
            trip = observed,
        )

        assertEquals(BlaBlaRidesEnrichmentStatus0533.PARTIAL, decision.status)
        assertEquals("SESSION_SNAPSHOT_STALE", decision.error)
    }

    @Test
    fun wrongAuthenticatedProfileIsHardFailure() {
        val observed = completeTrip(profile = otherProfileUuid)
        val decision = classifyEnrichmentResult0533(
            runSucceeded = true,
            runFailure = "",
            expectedProfileUuid = profileUuid,
            expectedTripId = tripId,
            attemptStartedAtMillis = 1_000L,
            session = session(profile = otherProfileUuid, trips = listOf(observed)),
            trip = observed,
        )

        assertEquals(BlaBlaRidesEnrichmentStatus0533.FAILED, decision.status)
        assertEquals("PROFILE_UUID_MISMATCH", decision.error)
    }

    @Test
    fun duplicateOrMismatchedTripIdentityCannotBeComplete() {
        val observed = completeTrip(id = "trip_admin_9999")
        val decision = classifyEnrichmentResult0533(
            runSucceeded = true,
            runFailure = "",
            expectedProfileUuid = profileUuid,
            expectedTripId = tripId,
            attemptStartedAtMillis = 1_000L,
            session = session(trips = listOf(observed)),
            trip = observed,
        )

        assertEquals(BlaBlaRidesEnrichmentStatus0533.FAILED, decision.status)
        assertEquals("TRIP_IDENTITY_MISMATCH", decision.error)
    }

    @Test
    fun twentySevenExpectedRequiresTwentySevenCompleteForCoverageComplete() {
        val rides = (1..27).map { index ->
            BlaBlaRidesEnrichedRide0533(
                captureId = "capture_0001",
                authenticatedProfileUuid = profileUuid,
                tripId = "trip_admin_${index.toString().padStart(4, '0')}",
                inventoryHref = "https://www.blablacar.fr/rides/offer?id=trip_admin_${index.toString().padStart(4, '0')}",
                status = BlaBlaRidesEnrichmentStatus0533.COMPLETE,
                lastError = "",
            )
        }
        val manifest = BlaBlaRidesEnrichmentManifest0533(
            captureId = "capture_0001",
            inventorySchemaVersion = "blablacar-rides-snapshot-v2",
            inventoryResult = BlaBlaRidesSnapshotStatus0526.COMPLETE,
            inventoryCompletedAt = "2026-09-10T12:00:00Z",
            inventoryComplete = true,
            startedAt = "2026-09-10T12:00:01Z",
            expectedTripCount = 27,
            identifiedTripCount = 27,
            unresolvedInventoryTripCount = 0,
            profiles = listOf(
                BlaBlaRidesEnrichedProfile0533(
                    accountKey = "0123456789abcdef",
                    expectedProfileUuid = profileUuid,
                    authenticatedProfileUuid = profileUuid,
                    inventoryStatus = BlaBlaRidesSnapshotStatus0526.COMPLETE,
                    expectedTripCount = 27,
                    identifiedTripCount = 27,
                    unresolvedInventoryTripCount = 0,
                    rides = rides,
                ),
            ),
        )

        val final = finalizeEnrichmentCoverage0533(manifest, "2026-09-10T12:10:00Z")

        assertEquals(27, final.terminalTripCount)
        assertEquals(27, final.completeCount)
        assertTrue(final.enrichmentComplete)
    }

    @Test
    fun onePartialAmongTwentySevenKeepsAllTerminalButCoverageIncomplete() {
        val rides = (1..27).map { index ->
            BlaBlaRidesEnrichedRide0533(
                captureId = "capture_0002",
                authenticatedProfileUuid = profileUuid,
                tripId = "trip_admin_${index.toString().padStart(4, '0')}",
                status = if (index == 27) BlaBlaRidesEnrichmentStatus0533.PARTIAL else BlaBlaRidesEnrichmentStatus0533.COMPLETE,
                lastError = if (index == 27) "PASSENGER_ROSTER_NOT_TERMINAL" else "",
            )
        }
        val base = BlaBlaRidesEnrichmentManifest0533(
            captureId = "capture_0002",
            inventorySchemaVersion = "blablacar-rides-snapshot-v2",
            inventoryResult = BlaBlaRidesSnapshotStatus0526.COMPLETE,
            inventoryCompletedAt = "done",
            inventoryComplete = true,
            startedAt = "start",
            expectedTripCount = 27,
            identifiedTripCount = 27,
            unresolvedInventoryTripCount = 0,
            profiles = listOf(
                BlaBlaRidesEnrichedProfile0533(
                    accountKey = "0123456789abcdef",
                    expectedProfileUuid = profileUuid,
                    authenticatedProfileUuid = profileUuid,
                    inventoryStatus = BlaBlaRidesSnapshotStatus0526.COMPLETE,
                    expectedTripCount = 27,
                    identifiedTripCount = 27,
                    unresolvedInventoryTripCount = 0,
                    rides = rides,
                ),
            ),
        )

        val final = finalizeEnrichmentCoverage0533(base)

        assertEquals(27, final.terminalTripCount)
        assertEquals(26, final.completeCount)
        assertEquals(1, final.partialCount)
        assertFalse(final.enrichmentComplete)
    }

    @Test
    fun unresolvedInventoryCountBecomesPendingUnknownAndNeverCompleteCoverage() {
        val base = BlaBlaRidesEnrichmentManifest0533(
            captureId = "capture_0003",
            inventorySchemaVersion = "blablacar-rides-snapshot-v2",
            inventoryResult = "INCOMPLETE",
            inventoryCompletedAt = "done",
            inventoryComplete = false,
            startedAt = "start",
            expectedTripCount = 2,
            identifiedTripCount = 1,
            unresolvedInventoryTripCount = 1,
            profiles = listOf(
                BlaBlaRidesEnrichedProfile0533(
                    accountKey = "0123456789abcdef",
                    expectedProfileUuid = profileUuid,
                    authenticatedProfileUuid = profileUuid,
                    inventoryStatus = "INCOMPLETE",
                    expectedTripCount = 2,
                    identifiedTripCount = 1,
                    unresolvedInventoryTripCount = 1,
                    rides = listOf(
                        BlaBlaRidesEnrichedRide0533(
                            captureId = "capture_0003",
                            authenticatedProfileUuid = profileUuid,
                            tripId = tripId,
                            status = BlaBlaRidesEnrichmentStatus0533.COMPLETE,
                            lastError = "",
                        ),
                    ),
                ),
            ),
        )

        val final = finalizeEnrichmentCoverage0533(base)

        assertEquals(2, final.terminalTripCount)
        assertEquals(1, final.completeCount)
        assertEquals(1, final.pendingUnknownCount)
        assertFalse(final.enrichmentComplete)
    }
}
