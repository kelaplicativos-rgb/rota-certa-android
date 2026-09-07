package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AgendaCanonicalProjectionConvergence0408Test {
    private val tenant = "tenant-fixture"
    private val profileA = "11111111-1111-4111-8111-111111111111"
    private val profileB = "22222222-2222-4222-8222-222222222222"
    private val now = 1_800_000_000_000L

    @Test
    fun twoProfilesRemainIndependentAndBothMatchTheirOwnProjection() {
        val a = trip(profileA, "trip-a", now + 10_000L)
        val b = trip(profileB, "trip-b", now + 20_000L)
        val remotes = listOf(remote(a, "remote-a"), remote(b, "remote-b"))
        assertEquals(1, remotes.count { remoteMatchesCanonicalProjection0408(a, it) })
        assertEquals(1, remotes.count { remoteMatchesCanonicalProjection0408(b, it) })
        assertNotEquals(a.tripKey, b.tripKey)
    }

    @Test
    fun multipleTripsOnSameDayAreNotCollapsedByDate() {
        val departure = now + 86_400_000L
        val a = trip(profileA, "same-day-a", departure)
        val b = trip(profileA, "same-day-b", departure + 60_000L)
        assertFalse(remoteMatchesCanonicalProjection0408(a, remote(b, "remote-b")))
        assertFalse(remoteMatchesCanonicalProjection0408(b, remote(a, "remote-a")))
        assertNotEquals(a.tripKey, b.tripKey)
    }

    @Test
    fun sameRouteOnDifferentDatesUsesStrongIdentityNotRouteText() {
        val a = trip(profileA, "route-day-a", now + 86_400_000L, origin = "A", destination = "C")
        val b = trip(profileA, "route-day-b", now + 2L * 86_400_000L, origin = "A", destination = "C")
        assertFalse(remoteMatchesCanonicalProjection0408(a, remote(b, "remote-b")))
        assertFalse(remoteMatchesCanonicalProjection0408(b, remote(a, "remote-a")))
    }

    @Test
    fun sameTimeAndRouteDifferentProviderTripIdsRemainDistinct() {
        val departure = now + 3L * 86_400_000L
        val a = trip(profileA, "parallel-a", departure)
        val b = trip(profileA, "parallel-b", departure)
        assertFalse(remoteMatchesCanonicalProjection0408(a, remote(b, "remote-b")))
        assertFalse(remoteMatchesCanonicalProjection0408(b, remote(a, "remote-a")))
    }

    @Test
    fun duplicateRemoteProjectionChoosesOneWinnerWithoutChangingCanonicalIdentity() {
        val canonical = trip(profileA, "duplicate-trip", now + 4L * 86_400_000L)
        val first = remote(canonical, "remote-old", publicationRevision = 4)
        val preferred = remote(canonical, "remote-preferred", publicationRevision = 5)
        val candidates = listOf(first, preferred).filter { remoteMatchesCanonicalProjection0408(canonical, it) }
        assertEquals(2, candidates.size)
        assertEquals(
            "remote-preferred",
            chooseProjectionWinner0408(canonical, "remote-preferred", candidates)?.remoteTripId,
        )
        assertEquals(canonical.id, candidates.first().canonicalTripId)
    }

    @Test
    fun availabilitySequenceFourTwoZeroOneKeepsIdentityAndOnlyZeroIsFull() {
        val tripId = "capacity-trip"
        val capacities = listOf(4, 2, 0, 1)
        val identities = capacities.map { seats ->
            val canonical = trip(profileA, tripId, now + 5L * 86_400_000L, publishedSeats = seats)
            val range = canonicalProjectionAvailabilityRange0408(canonical, emptyList(), now)
            assertEquals(seats, range.minimum)
            assertEquals(seats, range.maximum)
            assertEquals(if (seats == 0) "FULL" else "PUBLISHED", expectedProjectionStatus0408(canonical, emptyList(), now))
            canonical.tripKey
        }
        assertEquals(1, identities.distinct().size)
    }

    @Test
    fun partialRunPreservesFailedProfileWhileAllowingProvenCompleteProfileScope() {
        val response = response(status = "partial", complete = false, global = false)
        val completeProfiles = setOf(profileA.lowercase())
        val a = trip(profileA, "partial-a", now + 6L * 86_400_000L)
        val b = trip(profileB, "partial-b", now + 6L * 86_400_000L)
        assertTrue(externalCanonicalTripWithinCompleteScope0408(a, response, completeProfiles))
        assertFalse(externalCanonicalTripWithinCompleteScope0408(b, response, completeProfiles))
    }

    @Test
    fun completeLegacyMonthScopeAuthorizesOnlyMatchingProfileAndMonth() {
        val response = BlaBlaCollectorMonthResponse(
            status = "success",
            month = "2027-06",
            profiles = listOf(BlaBlaCollectorProfile(uuid = profileA)),
            coverage = BlaBlaCollectorCoverage(
                complete_for_scope = true,
                global_profile_month_complete = true,
            ),
        )
        val matching = trip(profileA, "complete-month", epoch("2027-06-28T10:00:00Z"), sourceDate = "2027-06-28")
        val otherProfile = trip(profileB, "complete-month-b", epoch("2027-06-28T10:00:00Z"), sourceDate = "2027-06-28")
        assertTrue(externalCanonicalTripWithinCompleteScope0408(matching, response, emptySet()))
        assertFalse(externalCanonicalTripWithinCompleteScope0408(otherProfile, response, emptySet()))
    }

    @Test
    fun concurrentOlderRevisionCannotWin() {
        assertEquals(
            CanonicalTripRevisionDecision0395.SKIP_STALE_REVISION,
            canonicalTripRevisionDecision0395(
                currentRevision = 12,
                incomingRevision = 11,
                semanticChanged = true,
            ),
        )
        assertEquals(13, nextCanonicalTripRevision0395(12, 12, semanticChanged = true))
    }

    @Test
    fun remote404RecreationIsLegacyOnlyWhileServerAuthorityOwnsCanonicalCreation() {
        val publisher = source("PublicAgendaAutoSync0300.kt")
        assertTrue(publisher.contains("isRemoteTripNotFound(firstError) && !serverCanonicalAuthority0468 ->"))
        assertTrue(publisher.contains("PUBLIC_PROJECTION_REMOTE_MISSING_RECREATED_0410"))
        assertTrue(publisher.contains("val created = api.publish(publicTrip.copy(capacityReliable = false))"))
        assertTrue(publisher.contains("remoteTripId = created.tripId"))
        assertTrue(publisher.contains("serverCanonicalAuthority0468 = serverCanonicalAuthority0468"))
        assertTrue(publisher.contains("SERVER_CANONICAL_INGESTION_REQUIRES_COMPLETE_INITIAL_SNAPSHOT"))
    }

    @Test
    fun projectionRepairInvalidatesDeliveredProofButKeepsSingleOutbox() {
        val background = source("AgendaBackgroundSync0392.kt")
        val outbox = source("TripPublicationOutbox0387.kt")
        assertTrue(background.contains("remoteProjectionDivergenceObserved = true"))
        assertTrue(outbox.contains("shouldDeduplicatePublicationEvent0410"))
        assertTrue(outbox.contains("remoteProjectionDivergenceObserved &&"))
        assertTrue(outbox.contains("latest.status in setOf("))
        assertTrue(outbox.contains("TripPublicationStatus0387.DELIVERED"))
        assertTrue(outbox.contains("TripPublicationStatus0387.FAILED_FINAL"))
        assertTrue(outbox.contains(") return false"))
        assertTrue(outbox.contains("return true"))
        assertFalse(outbox.contains("class ProjectionPublicationOutbox0410"))
    }

    @Test
    fun publisherRetryAndStaleRebaseRemainOnSingleExistingOutbox() {
        val source = source("TripPublicationOutbox0387.kt")
        assertTrue(source.contains("FAILED_RETRYABLE"))
        assertTrue(source.contains("fun rebase("))
        assertTrue(source.contains("recordProjectionTombstone0408"))
        assertTrue(source.contains("ensureRevisionAtLeast"))
        assertFalse(source.contains("class ProjectionPublicationOutbox0408"))
    }

    @Test
    fun orphanCleanupRequiresProvenCoverageForItsProfile() {
        val canonical = trip(profileA, "orphan-source", now + 7L * 86_400_000L)
        val orphan = remote(canonical, "remote-orphan").copy(
            canonicalTripId = "missing-canonical",
            tripKey = "missing-key",
        )
        val partial = response(status = "partial", complete = false, global = false)
        assertFalse(remoteProjectionWithinCompleteScope0408(orphan, partial, emptySet()))
        assertTrue(remoteProjectionWithinCompleteScope0408(orphan, partial, setOf(profileA.lowercase())))
    }

    @Test
    fun publicProjectionOrphanCleanupUsesCanonicalTripStoreWithoutCollectorCoverageGate() {
        val background = source("AgendaBackgroundSync0392.kt")
        val orphanBlock = background.substring(
            background.indexOf("val orphanStates = remoteStates.filter"),
            background.indexOf("val report = ProjectionIntegrity0406"),
        )
        assertTrue(orphanBlock.contains("authority=CANONICAL_TRIP_STORE"))
        assertTrue(orphanBlock.contains("mutationType = \"CANONICAL_PUBLIC_ORPHAN\""))
        assertFalse(orphanBlock.contains("remoteProjectionWithinCompleteScope0408("))
    }

    @Test
    fun publicPublisherNeverCollapsesDistinctCanonicalTripsByRouteOrTime() {
        val publisher = source("PublicAgendaAutoSync0300.kt")
        assertFalse(publisher.contains("samePhysicalTrip("))
        assertFalse(publisher.contains("localTrips.any { local ->"))
        assertTrue(publisher.contains(".distinctBy { it.trip.tripKey.ifBlank { it.trip.id } }"))
    }

    @Test
    fun deliveredIncrementalPublicationUsesServerCanonicalReadbackBeforeLegacyVerification() {
        val outbox = source("TripPublicationOutbox0387.kt")
        assertTrue(outbox.contains("backendCanonicalVerified0468"))
        assertTrue(outbox.contains("readPublicTripProjection0411("))
        assertTrue(outbox.contains("canonicalPublicProjectionHash0411(readback0468.payload)"))
        assertTrue(outbox.contains("reportPublicTripAttestation0417("))
        assertTrue(outbox.contains("serverPublicProjectionConfirmed0469("))
        assertTrue(outbox.contains("publicationResult=server_canonical_readback_confirmed_0469"))
        assertTrue(outbox.contains("return@eventLoop"))
        assertTrue(outbox.contains("includePastForVerification0429 = true"))
        assertTrue(outbox.contains("PublicMirrorAttestationCoordinator0411.attest("))
        assertTrue(outbox.contains("force = true"))
    }

    @Test
    fun durableReadbackIdentityResolutionDoesNotReuseFutureDiscoveryFilter() {
        val backend = File("../trip-platform/functions/index.js").readText()
        val remoteApi = source("TripRemoteApi.kt")
        val outbox = source("TripPublicationOutbox0387.kt")
        val attestation = source("PublicMirrorAttestationCoordinator0411.kt")

        assertTrue(backend.contains("includePastForVerification"))
        assertTrue(backend.contains("(includePastForVerification || trip.departureAtMillis > now)"))
        assertTrue(remoteApi.contains("includePastForVerification0429: Boolean = false"))
        assertTrue(remoteApi.contains("append(\"/v1/driver/trips/sync-state\")"))
        assertTrue(remoteApi.contains("if (includePastForVerification0429) add(\"includePastForVerification=1\")"))
        assertTrue(remoteApi.contains("if (timelineProjection0494) add(\"timelineProjection=1\")"))
        assertTrue(outbox.contains("stage = \"PUBLIC_IDENTITY_RESOLUTION\""))
        assertTrue(outbox.contains("includePastForVerification0429 = true"))
        assertTrue(outbox.contains("status = \"FAILED\""))
        assertTrue(attestation.contains("transportEvidence0421"))
        assertTrue(attestation.contains("evidence0421 = transportEvidence0421"))
    }

    @Test
    fun realBarbosaRegressionTripIdsAllRemainDistinctFixtures() {
        // Real reproduction values are fixtures only. No production rule may depend on this profile or these IDs.
        val profile = "175a7068-50d8-40c3-a27a-214b9c6e0461"
        val ids = listOf(
            "01a0356f-7a2d-7d0b-bf84-157eff07ff35",
            "019ed00c-1919-7896-8507-5315d113f690",
            "01a0356f-7a2e-782a-9643-1450a7a32bee",
            "019ebd94-904a-7eca-ba06-addfc6b4af21",
            "01a0356f-7a2e-7e3e-9644-bc58b7fa45f6",
            "01a03566-5325-7526-abd3-7f54222bcfb7",
            "01a0356f-7a2f-727a-a2f6-642322b5de45",
            "01a03566-5325-7de5-abd4-d2a7a5e858e3",
            "019ecffe-a757-7aa1-83aa-3016ef16c455",
            "01a058cd-2e23-72b3-9d6f-fdf86a8d603b",
        )
        val canonicals = ids.mapIndexed { index, id ->
            trip(profile, id, now + (index + 8L) * 86_400_000L)
        }
        val remotes = canonicals.mapIndexed { index, canonical -> remote(canonical, "remote-$index") }
        assertEquals(ids.size, canonicals.map(Trip::tripKey).distinct().size)
        canonicals.forEach { canonical ->
            assertEquals(1, remotes.count { remoteMatchesCanonicalProjection0408(canonical, it) })
        }
    }

    @Test
    fun staleRemoteRevisionIsDetectableAgainstNewerCanonicalProjectionRevision() {
        val canonical = trip(profileA, "stale-revision", now + 20L * 86_400_000L)
            .copy(publicationRevision = 9)
        val stale = remote(canonical, "remote-stale", publicationRevision = 8)
        assertTrue(stale.publicationRevision < canonical.publicationRevision)
        assertTrue(remoteMatchesCanonicalProjection0408(canonical, stale))
    }

    @Test
    fun tombstonePathIsVersionedSoDelayedEventCannotResurrectProjection() {
        val source = source("TripPublicationOutbox0387.kt")
        assertTrue(source.contains("publicationTombstone = true"))
        assertTrue(source.contains("outbox.ensureRevisionAtLeast"))
        assertTrue(source.contains("PublicationStaleRevision0387"))
        assertTrue(source.contains("rebase("))
    }

    @Test
    fun canonicalPositiveAvailabilityRejectsRemoteLotadoSnapshot() {
        val canonical = trip(
            profileA,
            "019ec8c9-9d45-77ff-a92a-06a2417567af",
            epoch("2027-06-28T10:00:00Z"),
            publishedSeats = 1,
        )
        val wrongRemote = remote(canonical, "remote-capacity").copy(
            status = "FULL",
            capacity = 1,
            publishedSeats = 1,
            operationalAvailableSeats = 0,
            availableSeatsMinimum = 0,
            availableSeatsMaximum = 0,
        )
        assertFalse(projectionCapacityMatches0408(canonical, emptyList(), wrongRemote, now))
        val correct = wrongRemote.copy(
            status = "PUBLISHED",
            operationalAvailableSeats = 1,
            availableSeatsMinimum = 1,
            availableSeatsMaximum = 1,
        )
        assertTrue(projectionCapacityMatches0408(canonical, emptyList(), correct, now))
        assertEquals("PUBLISHED", expectedProjectionStatus0408(canonical, emptyList(), now))
    }

    @Test
    fun legacySyncStateWithoutExtendedAvailabilityDoesNotCreateRepairLoop() {
        val canonical = trip(
            profileA,
            "legacy-sync-state",
            now + 21L * 86_400_000L,
            publishedSeats = 2,
        )
        val legacy = DriverTripSyncState0402(
            remoteTripId = "legacy-remote",
            status = "PUBLISHED",
            departureAtMillis = canonical.departureAtMillis,
            stops = canonical.stops,
            capacityReliable = true,
            publicationRevision = canonical.publicationRevision,
            canonicalTripId = canonical.id,
            canonicalStateHash = canonical.canonicalStateHash,
            tripKey = canonical.tripKey,
            blablaProfileUuid = canonical.blablaProfileUuid.orEmpty(),
            blablaTripId = canonical.blablaTripId.orEmpty(),
            title = canonical.title,
            capacity = operationalInventoryCapacity(canonical, emptyList()),
        )
        assertTrue(projectionCapacityMatches0408(canonical, emptyList(), legacy, now))
    }

    @Test
    fun productionSourceHasNoBarbosaOrFixtureSpecificBusinessRule() {
        val background = source("AgendaBackgroundSync0392.kt")
        val publicSync = source("PublicAgendaAutoSync0300.kt")
        assertFalse(background.contains("Barbosa"))
        assertFalse(publicSync.contains("Barbosa"))
        assertFalse(background.contains("175a7068-50d8-40c3-a27a-214b9c6e0461"))
        assertFalse(publicSync.contains("175a7068-50d8-40c3-a27a-214b9c6e0461"))
    }

    private fun response(status: String, complete: Boolean, global: Boolean) =
        BlaBlaCollectorMonthResponse(
            status = status,
            month = "2030-09",
            profiles = listOf(
                BlaBlaCollectorProfile(uuid = profileA),
                BlaBlaCollectorProfile(uuid = profileB),
            ),
            coverage = BlaBlaCollectorCoverage(
                complete_for_scope = complete,
                global_profile_month_complete = global,
            ),
        )

    private fun trip(
        profile: String,
        providerTripId: String,
        departure: Long,
        publishedSeats: Int = 4,
        origin: String = "A",
        destination: String = "C",
        sourceDate: String = "2030-09-01",
    ): Trip {
        val internalId = "canonical-" + providerTripId
        return Trip(
            id = internalId,
            title = "$origin → $destination",
            departureAtMillis = departure,
            capacity = publishedSeats,
            status = TripStatus.PUBLISHED,
            stops = listOf(
                TripStop(id = internalId + "-a", order = 0, name = origin),
                TripStop(id = internalId + "-c", order = 1, name = destination),
            ),
            blablaProfileUuid = profile,
            blablaTripId = providerTripId,
            publishedSeats = publishedSeats,
            rotaCertaSeatAllocation = 0,
            capacityReliable = true,
            recordOrigin = TripRecordOrigin.EXTERNAL_BACKING,
            canonicalRevision = 7,
            publicationRevision = 7,
            tripKey = canonicalTripKey0406(tenant, "BLABLACAR", profile, providerTripId).orEmpty(),
            canonicalStateHash = "hash-" + providerTripId,
            externalSnapshot = BlaBlaCollectorTrip(
                profile_uuid = profile,
                date = sourceDate,
                departure_time = "10:00",
                actual_departure = origin,
                actual_arrival = destination,
                trip_id = providerTripId,
                trip_href = "https://example.invalid/rides/offer/" + providerTripId,
                published_seats = publishedSeats,
                passenger_roster_complete = true,
            ),
            externalSnapshotComplete = true,
        )
    }

    private fun remote(
        canonical: Trip,
        remoteId: String,
        publicationRevision: Long = canonical.publicationRevision,
    ): DriverTripSyncState0402 {
        val available = canonicalProjectionAvailabilityRange0408(canonical, emptyList(), now)
        return DriverTripSyncState0402(
            remoteTripId = remoteId,
            status = expectedProjectionStatus0408(canonical, emptyList(), now),
            departureAtMillis = canonical.departureAtMillis,
            stops = canonical.stops,
            capacityReliable = canonical.capacityReliable,
            publicationRevision = publicationRevision,
            canonicalTripId = canonical.id,
            canonicalStateHash = canonical.canonicalStateHash,
            tripKey = canonical.tripKey,
            blablaProfileUuid = canonical.blablaProfileUuid.orEmpty(),
            blablaTripId = canonical.blablaTripId.orEmpty(),
            title = canonical.title,
            capacity = operationalInventoryCapacity(canonical, emptyList()),
            publishedSeats = canonical.publishedSeats,
            rotaCertaSeatAllocation = canonical.rotaCertaSeatAllocation ?: 0,
            operationalAvailableSeats = available.minimum,
            availableSeatsMinimum = available.minimum,
            availableSeatsMaximum = available.maximum,
            occupancyRevision = 1,
        )
    }

    private fun source(name: String): String =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/" + name).readText()

    private fun epoch(value: String): Long = java.time.Instant.parse(value).toEpochMilli()
}
