package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaReadbackForensics0421Test {
    @Test
    fun protectedBookingConflictIsDeterministicAndDoesNotRetryStorm() {
        val conflict = TripRemoteApiException(
            httpMethod = "PUT",
            endpoint = "/v1/driver/trips/redacted/capacity-snapshot",
            httpStatus = 409,
            backendErrorCode = "protected_booking_required",
            sanitizedResponse = "{error:protected_booking_required}",
            requestId = "req",
            correlationId = "corr",
        )
        assertFalse(publicationFailureRetryable0387(conflict))

        val transient = TripRemoteApiException(
            httpMethod = "PUT",
            endpoint = "/v1/driver/trips/redacted/capacity-snapshot",
            httpStatus = 429,
            backendErrorCode = "rate_limited",
            sanitizedResponse = "",
            requestId = "req",
            correlationId = "corr",
        )
        assertTrue(publicationFailureRetryable0387(transient))
    }

    @Test
    fun capacitySnapshotCarriesOnlyProtectedBookingsThatStillConsumeOrHoldCapacity() {
        val now = 1_800_000_000_000L
        fun booking(status: BookingStatus, hold: Long? = null) = Booking(
            id = "booking-$status",
            tripId = "trip",
            passengerName = "Passenger",
            boardingStopId = "a",
            dropoffStopId = "b",
            seats = 1,
            status = status,
            source = BookingSource.ROTA_CERTA,
            holdExpiresAtMillis = hold,
        )

        assertTrue(PublicAgendaAutoSync0300.protectedBookingParticipatesInCapacitySnapshot0421(booking(BookingStatus.CONFIRMED), now))
        assertTrue(PublicAgendaAutoSync0300.protectedBookingParticipatesInCapacitySnapshot0421(booking(BookingStatus.REQUESTED), now))
        assertTrue(PublicAgendaAutoSync0300.protectedBookingParticipatesInCapacitySnapshot0421(booking(BookingStatus.HELD, now + 1_000), now))
        assertFalse(PublicAgendaAutoSync0300.protectedBookingParticipatesInCapacitySnapshot0421(booking(BookingStatus.HELD, now - 1), now))
        assertFalse(PublicAgendaAutoSync0300.protectedBookingParticipatesInCapacitySnapshot0421(booking(BookingStatus.CANCELLED), now))
        assertFalse(PublicAgendaAutoSync0300.protectedBookingParticipatesInCapacitySnapshot0421(booking(BookingStatus.REJECTED), now))
    }

    @Test
    fun outboxAndReadbackShareOneEvidenceIdForTheSameLogicalRevision() {
        val traceId = publicationEventId0387("tenant", "trip-0421", 31)
        assertTrue(publicationEvidenceId0421(traceId, 7).startsWith("ev_"))
        assertTrue(publicationEvidenceId0421(traceId, 7) == publicationEvidenceId0421(traceId, 7))
        assertFalse(publicationEvidenceId0421(traceId, 7) == publicationEvidenceId0421(traceId, 8))
    }

    @Test
    fun transportRebasePreservesMutationIdentityAndIdempotencyKey() {
        val trip = Trip(
            id = "trip-stable-0421",
            title = "A → B",
            departureAtMillis = 1_900_000_000_000L,
            capacity = 2,
            status = TripStatus.PUBLISHED,
            stops = listOf(
                TripStop(id = "a", order = 0, name = "A"),
                TripStop(id = "b", order = 1, name = "B"),
            ),
            canonicalRevision = 9,
            canonicalStateHash = "state-9",
        )
        val snapshot = TripPublicationSnapshot0387(
            trip = trip,
            semanticSignature = "semantic-9",
            seatAllocationVersion = 4,
        )
        val identity = publicationMutationIdentity0421(trip.id, snapshot)
        val original = TripPublicationOutboxEvent0387(
            id = publicationEventId0387("tenant", trip.id, 31),
            tenantId = "tenant",
            canonicalTripId = trip.id,
            revision = 31,
            operation = TripPublicationOperation0387.UPSERT_LOCAL,
            mutationType = "TEST",
            source = "TEST",
            snapshot = snapshot,
            mutationId0421 = identity.first,
            idempotencyKey0421 = identity.second,
        )
        val rebased = original.copy(
            id = publicationEventId0387("tenant", trip.id, 99),
            revision = 99,
        )
        assertTrue(original.resolvedMutationId0421() == rebased.resolvedMutationId0421())
        assertTrue(original.resolvedIdempotencyKey0421() == rebased.resolvedIdempotencyKey0421())
        assertFalse(original.id == rebased.id)
    }

    @Test
    fun strongIdentityConsolidationRequiresCompatiblePhysicalTrip() {
        val profile = "11111111-1111-4111-8111-111111111111"
        fun trip(id: String, departure: Long, origin: String = "Três Corações", destination: String = "Santo André") = Trip(
            id = id,
            title = "$origin → $destination",
            departureAtMillis = departure,
            capacity = 3,
            status = TripStatus.PUBLISHED,
            stops = listOf(
                TripStop(id = "$id-a", order = 0, name = origin),
                TripStop(id = "$id-b", order = 1, name = destination),
            ),
            blablaProfileUuid = profile,
            blablaTripId = "provider-trip",
        )
        val canonical = trip("canonical", 1_800_000_000_000L)
        val legacyBacking = trip("timeline-ext-legacy", 1_800_000_060_000L)
        val reusedIdentityNextYear = trip("other-year", 1_831_536_000_000L)

        assertTrue(canonicalProjectionPhysicalIdentityCompatible0421(canonical, legacyBacking))
        assertFalse(canonicalProjectionPhysicalIdentityCompatible0421(canonical, reusedIdentityNextYear))
    }

    @Test
    fun publicBindingNeverFallsBackToRouteAndTimeSimilarity() {
        val binding = PublicExternalTripBinding(
            remoteTripId = "public-1",
            publicToken = "public-token",
            bookingTripId = "canonical-1",
            title = "A → B",
            departureAtMillis = 1_900_000_000_000L,
            capacity = 2,
            stops = listOf(
                TripStop(id = "a", order = 0, name = "A"),
                TripStop(id = "b", order = 1, name = "B"),
            ),
        )
        val visuallySimilar = TripTimelineEntry(
            tripId = "timeline-card",
            profileId = "",
            profileLabel = "",
            departureAtMillis = 1_900_000_000_000L,
            arrivalAtMillis = null,
            origin = "A",
            destination = "B",
            status = TripStatus.PUBLISHED,
            capacity = 2,
            minimumOccupiedSeats = 0,
            maximumOccupiedSeats = 0,
            sourcePassengerSeats = emptyMap(),
        )
        assertFalse(binding.matches(visuallySimilar))
    }

    @Test
    fun duplicatePublicProjectionIsExplicitlyDeniedMatchBeforeReadback() {
        val source = java.io.File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaBackgroundSync0392.kt").readText()
        assertTrue(source.contains("PUBLIC_PROJECTION_DUPLICATE"))
        assertTrue(source.contains("duplicateProjection"))
        assertTrue(source.contains("PublicMirrorAttestationState0411.DIVERGENT"))
    }

    @Test
    fun projectionRepairCannotCreateANewRevisionForTheSameLogicalSnapshot() {
        val trip = Trip(
            id = "trip-0421",
            title = "A → B",
            departureAtMillis = 1_900_000_000_000L,
            capacity = 2,
            status = TripStatus.PUBLISHED,
            stops = listOf(
                TripStop(id = "a", order = 0, name = "A"),
                TripStop(id = "b", order = 1, name = "B"),
            ),
            canonicalRevision = 7,
            publicationRevision = 31,
            canonicalStateHash = "state-7",
        )
        val snapshot = TripPublicationSnapshot0387(
            trip = trip,
            seatAllocationVersion = 3,
            semanticSignature = "semantic-7",
        )
        val latest = TripPublicationOutboxEvent0387(
            id = publicationEventId0387("tenant", trip.id, 31),
            tenantId = "tenant",
            canonicalTripId = trip.id,
            revision = 31,
            operation = TripPublicationOperation0387.UPSERT_LOCAL,
            mutationType = "CANONICAL_REMOTE_PROJECTION_REPAIR",
            source = "CANONICAL_VERIFY",
            snapshot = snapshot,
            status = TripPublicationStatus0387.DELIVERED,
        )
        assertTrue(
            shouldDeduplicatePublicationEvent0410(
                latest = latest,
                operation = TripPublicationOperation0387.UPSERT_LOCAL,
                snapshot = snapshot,
                remoteProjectionDivergenceObserved = true,
            ),
        )
    }
}
