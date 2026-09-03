package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PublicMirrorAttestation0411Test {
    private val now = 1_800_000_000_000L
    private val profile = "11111111-1111-4111-8111-111111111111"
    private val providerTripId = "trip-0411-a"

    @Test
    fun exactCommittedReadbackIsTheOnlyValidatedState() {
        val trip = canonicalTrip()
        val payload = canonicalPublicProjectionPayload0411(
            trip = trip,
            bookings = emptyList(),
            publicationRevision = trip.publicationRevision,
            nowMillis = now,
        )
        val hash = canonicalPublicProjectionHash0411(payload)
        val decision = evaluatePublicMirrorReadback0411(
            expected = payload,
            readback = DriverPublicTripReadback0411(
                remoteTripId = "remote-0411",
                payload = payload,
                publicProjectionHash = hash,
                persistedAtMillis = now,
            ),
        )

        assertEquals(PublicMirrorAttestationState0411.VALIDATED, decision.state)
        assertTrue(decision.identityValid)
        assertTrue(decision.revisionValid)
        assertTrue(decision.linkValid)
        assertTrue(decision.mismatchFields.isEmpty())
        assertEquals(hash, decision.expectedHash)
        assertEquals(hash, decision.readbackHash)
    }

    @Test
    fun staleLogicalCanonicalRevisionPreventsBlue() {
        val trip = canonicalTrip()
        val expected = canonicalPublicProjectionPayload0411(
            trip = trip,
            bookings = emptyList(),
            publicationRevision = trip.publicationRevision,
            nowMillis = now,
        )
        val stale = expected.copy(canonicalRevision = expected.canonicalRevision - 1)
        val decision = evaluatePublicMirrorReadback0411(
            expected,
            DriverPublicTripReadback0411(
                remoteTripId = "remote-0411",
                payload = stale,
                publicProjectionHash = canonicalPublicProjectionHash0411(stale),
            ),
        )

        assertEquals(PublicMirrorAttestationState0411.DIVERGENT, decision.state)
        assertFalse(decision.revisionValid)
        assertTrue("canonicalRevision" in decision.mismatchFields)
    }

    @Test
    fun canonicalByteDiffIgnoresTransportRevisionButLocatesRealPayloadChange() {
        val trip = canonicalTrip()
        val expected = canonicalPublicProjectionPayload0411(trip, emptyList(), trip.publicationRevision, now)
        val transportOnly = expected.copy(publicationRevision = expected.publicationRevision + 100)
        val transportDiff = compareCanonicalPublicBytes0421(expected, transportOnly)
        assertEquals(-1, transportDiff.firstDifferentByteOffset)
        assertTrue(transportDiff.differentByteRanges.isEmpty())
        assertEquals(transportDiff.expectedSha256, transportDiff.actualSha256)

        val changed = expected.copy(title = expected.title + " alterado")
        val changedDiff = compareCanonicalPublicBytes0421(expected, changed)
        assertTrue(changedDiff.firstDifferentByteOffset >= 0)
        assertTrue(changedDiff.differentByteRanges.isNotEmpty())
        assertNotEquals(changedDiff.expectedSha256, changedDiff.actualSha256)
    }

    @Test
    fun onePublicFieldDifferencePreventsBlue() {
        val trip = canonicalTrip()
        val expected = canonicalPublicProjectionPayload0411(
            trip = trip,
            bookings = emptyList(),
            publicationRevision = trip.publicationRevision,
            nowMillis = now,
        )
        val changed = expected.copy(operationalAvailableSeats = expected.operationalAvailableSeats + 1)
        val decision = evaluatePublicMirrorReadback0411(
            expected,
            DriverPublicTripReadback0411(
                remoteTripId = "remote-0411",
                payload = changed,
                publicProjectionHash = canonicalPublicProjectionHash0411(changed),
            ),
        )

        assertEquals(PublicMirrorAttestationState0411.DIVERGENT, decision.state)
        assertTrue("availability" in decision.mismatchFields)
        assertTrue("publicHash" in decision.mismatchFields)
    }

    @Test
    fun transportRevisionDifferenceDoesNotReplaceLogicalRevision() {
        val trip = canonicalTrip()
        val expected = canonicalPublicProjectionPayload0411(
            trip = trip,
            bookings = emptyList(),
            publicationRevision = trip.publicationRevision,
            nowMillis = now,
        )
        val stale = expected.copy(publicationRevision = expected.publicationRevision - 1)
        val decision = evaluatePublicMirrorReadback0411(
            expected,
            DriverPublicTripReadback0411(
                remoteTripId = "remote-0411",
                payload = stale,
                publicProjectionHash = canonicalPublicProjectionHash0411(stale),
            ),
        )

        assertEquals(PublicMirrorAttestationState0411.VALIDATED, decision.state)
        assertTrue(decision.revisionValid)
        assertFalse("canonicalRevision" in decision.mismatchFields)
    }

    @Test
    fun BlaBlaLinkForAnotherTripDoesNotForgeOrBlockAgendaMatch() {
        val trip = canonicalTrip()
        val expected = canonicalPublicProjectionPayload0411(
            trip = trip,
            bookings = emptyList(),
            publicationRevision = trip.publicationRevision,
            nowMillis = now,
        )
        val wrong = expected.copy(
            blablaPublicUrl = "https://www.blablacar.com.br/trip?id=another-trip",
        )
        val decision = evaluatePublicMirrorReadback0411(
            expected,
            DriverPublicTripReadback0411(
                remoteTripId = "remote-0411",
                payload = wrong,
                publicProjectionHash = canonicalPublicProjectionHash0411(wrong),
            ),
        )

        assertEquals(PublicMirrorAttestationState0411.VALIDATED, decision.state)
        assertFalse(decision.linkValid)
        assertTrue("blablaPublicUrl" in decision.mismatchFields)
    }

    @Test
    fun serverHashMustDescribeTheActualPersistedReadback() {
        val trip = canonicalTrip()
        val expected = canonicalPublicProjectionPayload0411(
            trip = trip,
            bookings = emptyList(),
            publicationRevision = trip.publicationRevision,
            nowMillis = now,
        )
        val decision = evaluatePublicMirrorReadback0411(
            expected,
            DriverPublicTripReadback0411(
                remoteTripId = "remote-0411",
                payload = expected,
                publicProjectionHash = "public-v2:not-the-readback-hash",
            ),
        )

        assertEquals(PublicMirrorAttestationState0411.DIVERGENT, decision.state)
        assertTrue("serverHash" in decision.mismatchFields)
    }

    @Test
    fun attestationIsRevokedByAnyNewCanonicalOrPublicationRevision() {
        val trip = canonicalTrip()
        val payload = canonicalPublicProjectionPayload0411(
            trip = trip,
            bookings = emptyList(),
            publicationRevision = trip.publicationRevision,
            nowMillis = now,
        )
        val hash = canonicalPublicProjectionHash0411(payload)
        val validated = trip.copy(
            publicMirrorAttestationState0411 = PublicMirrorAttestationState0411.VALIDATED,
            publicMirrorAttestedCanonicalRevision0411 = trip.canonicalRevision,
            publicMirrorAttestedPublicationRevision0411 = trip.publicationRevision,
            publicMirrorExpectedHash0411 = hash,
            publicMirrorReadbackHash0411 = hash,
        )

        assertTrue(validated.publicMirrorAttestationCurrent0411())
        assertFalse(validated.copy(canonicalRevision = trip.canonicalRevision + 1).publicMirrorAttestationCurrent0411())
        assertFalse(validated.copy(publicationRevision = trip.publicationRevision + 1).publicMirrorAttestationCurrent0411())
        assertFalse(validated.invalidatePublicMirror0411("TEST_MUTATION").publicMirrorAttestationCurrent0411())
    }

    @Test
    fun deterministicHashChangesForFunctionalPublicStateButNotInputListOrder() {
        val trip = canonicalTrip()
        val a = Booking(
            id = "booking-a",
            tripId = trip.id,
            passengerName = "Passenger A",
            boardingStopId = "a",
            dropoffStopId = "b",
            seats = 1,
            status = BookingStatus.CONFIRMED,
            source = BookingSource.ROTA_CERTA,
            occupancyGroupId = "person-a",
        )
        val b = Booking(
            id = "booking-b",
            tripId = trip.id,
            passengerName = "Passenger B",
            boardingStopId = "b",
            dropoffStopId = "c",
            seats = 1,
            status = BookingStatus.CONFIRMED,
            source = BookingSource.ROTA_CERTA,
            occupancyGroupId = "person-b",
        )
        val first = canonicalPublicProjectionPayload0411(trip, listOf(a, b), trip.publicationRevision, now)
        val reordered = canonicalPublicProjectionPayload0411(trip, listOf(b, a), trip.publicationRevision, now)
        val changed = canonicalPublicProjectionPayload0411(
            trip.copy(rotaCertaSeatAllocation = 2, capacity = 5),
            listOf(a, b),
            trip.publicationRevision,
            now,
        )

        assertEquals(canonicalPublicProjectionHash0411(first), canonicalPublicProjectionHash0411(reordered))
        assertNotEquals(canonicalPublicProjectionHash0411(first), canonicalPublicProjectionHash0411(changed))
    }

    @Test
    fun globalProjectionVerificationRequiresEveryExpectedCardAttested() {
        val complete = ProjectionIntegrity0406(
            canonicalActive = 3,
            agendaProjections = 3,
            attestationValidated0411 = 3,
        )
        assertTrue(complete.verified)
        assertFalse(complete.copy(attestationValidated0411 = 2, attestationPending0411 = 1).verified)
        assertFalse(complete.copy(attestationDivergent0411 = 1).verified)
        assertTrue(complete.copy(attestationInvalidLink0411 = 1).verified)
        assertFalse(complete.copy(attestationReadbackFailures0411 = 1, failures = 1).verified)
    }

    private fun canonicalTrip(): Trip {
        val base = Trip(
            id = "canonical-0411",
            title = "A → C",
            departureAtMillis = now + 86_400_000L,
            capacity = 4,
            status = TripStatus.PUBLISHED,
            stops = listOf(
                TripStop(id = "a", order = 0, name = "A", address = "A street"),
                TripStop(id = "b", order = 1, name = "B", address = "B street"),
                TripStop(id = "c", order = 2, name = "C", address = "C street"),
            ),
            remoteId = "remote-0411",
            publicToken = "public-token-0411",
            publicUrl = "https://rota.example/trip/public-token-0411",
            blablaProfileUuid = profile,
            blablaTripId = providerTripId,
            blablaPublicUrl = "https://www.blablacar.com.br/trip?id=$providerTripId",
            publicBookingEnabled = true,
            itineraryAuthoritative = true,
            publishedSeats = 3,
            rotaCertaSeatAllocation = 1,
            capacityReliable = true,
            recordOrigin = TripRecordOrigin.EXTERNAL_BACKING,
            canonicalRevision = 12,
            publicationRevision = 18,
            tripKey = "tenant|blablacar|$profile|$providerTripId",
            publicTimezoneId0411 = "America/Sao_Paulo",
        )
        return base.copy(canonicalStateHash = canonicalTripStateHash0406(base, emptyList()))
    }
}
