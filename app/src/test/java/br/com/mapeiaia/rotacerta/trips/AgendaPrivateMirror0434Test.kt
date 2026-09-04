package br.com.mapeiaia.rotacerta.trips

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaPrivateMirror0434Test {
    @Test
    fun mirrorPreservesCanonicalStateAndKeepsLocalAlias() {
        val trip = Trip(
            id = "timeline-ext-local-alias",
            title = "Origem → Destino",
            departureAtMillis = 1_800_000_000_000L,
            capacity = 6,
            status = TripStatus.PUBLISHED,
            stops = listOf(
                TripStop(
                    id = "stop-a",
                    order = 0,
                    name = "Origem",
                    address = "Rua A",
                    latitude = -23.0,
                    longitude = -46.0,
                    plannedDepartureMillis = 1_800_000_000_000L,
                    priceToNextCents = 5000L,
                ),
                TripStop(
                    id = "stop-b",
                    order = 1,
                    name = "Destino",
                    address = "Rua B",
                    plannedArrivalMillis = 1_800_003_600_000L,
                ),
            ),
            notes = "nota operacional",
            blablaProfileUuid = "7371f028-9c55-4903-8444-308015823efd",
            blablaTripId = "trip-provider-0434",
            publicBookingEnabled = true,
            itineraryAuthoritative = true,
            publishedSeats = 4,
            rotaCertaSeatAllocation = 2,
            capacityReliable = true,
            recordOrigin = TripRecordOrigin.EXTERNAL_BACKING,
            canonicalRevision = 8L,
            seatAllocationVersionUsed = 3L,
            tripKey = "tripkey:0434",
            canonicalStateHash = "canonical-state-0434",
            publicTimezoneId0411 = "America/Sao_Paulo",
            createdAtMillis = 1_700_000_000_000L,
            updatedAtMillis = 1_700_000_100_000L,
        )
        val booking = Booking(
            id = "booking-0434",
            tripId = trip.id,
            passengerId = "passenger-0434",
            passengerName = "Passageiro",
            passengerContact = "+5500000000000",
            boardingStopId = "stop-a",
            dropoffStopId = "stop-b",
            seats = 2,
            status = BookingStatus.CONFIRMED,
            operationalStatus = PassengerOperationalStatus.IN_CAR,
            paymentStatus = PassengerPaymentStatus.PAID,
            lastDriverSelection = "IN_CAR",
            source = BookingSource.ROTA_CERTA,
            capacityClaimType = CapacityClaimType.PASSENGER,
            fareMinorUnits = 10000L,
            fareCurrencyCode = "BRL",
            boardingAddress = "Rua A",
            dropoffAddress = "Rua B",
        )

        val strongCanonicalId = "blablacar:strong-canonical-0434"
        val canonicalOperational = canonicalOperationalSnapshot0434(
            trip = trip,
            bookings = listOf(booking),
            nowMillis = trip.updatedAtMillis,
        )
        val payload = privateAgendaMirrorPayload0434(
            trip = trip,
            bookings = listOf(booking),
            operationalSnapshot = canonicalOperational,
            canonicalTripId = strongCanonicalId,
        )
        val json = privateAgendaMirrorCanonicalJson0434(payload)

        assertEquals(strongCanonicalId, payload.canonicalTripId)
        assertEquals(trip.id, payload.internalTripId)
        assertEquals(listOf(trip.id), payload.identityAliases)
        assertEquals(8L, payload.canonicalRevision)
        assertEquals("canonical-state-0434", payload.canonicalStateHash)
        assertEquals("America/Sao_Paulo", payload.timezoneId)
        assertEquals(TripStatus.PUBLISHED.name, payload.status)
        assertEquals(2, payload.stops.size)
        assertEquals("Rua A", payload.stops.first().address)
        assertEquals(PassengerOperationalStatus.IN_CAR.name, payload.bookings.single().operationalStatus)
        assertEquals(PassengerPaymentStatus.PAID.name, payload.bookings.single().paymentStatus)
        assertEquals(10000L, payload.bookings.single().fareMinorUnits)
        assertEquals(canonicalOperational.segmentLoads, payload.segmentLoads)
        assertEquals(canonicalOperational.segmentPassengerLoads, payload.segmentPassengerLoads)
        assertEquals(canonicalOperational.segmentBlockedLoads, payload.segmentBlockedLoads)
        assertEquals(canonicalOperational.availableSeatsMinimum, payload.availableSeatsMinimum)
        assertEquals(canonicalOperational.availableSeatsMaximum, payload.availableSeatsMaximum)
        assertEquals(privateAgendaMirrorHash0434(json), privateAgendaMirrorHash0434(json))
        assertTrue(privateAgendaMirrorHash0434(json).startsWith("private-v1:"))
    }

    @Test
    fun privateMirrorPreservesCanonicalBoundPublicTokenDifferentFromAdministrativeId() {
        val trip = Trip(
            id = "timeline-ext-private-link-0434",
            title = "Origem → Destino",
            departureAtMillis = 1_900_000_000_000L,
            capacity = 4,
            status = TripStatus.PUBLISHED,
            stops = listOf(
                TripStop(id = "a", order = 0, name = "Origem"),
                TripStop(id = "b", order = 1, name = "Destino"),
            ),
            blablaProfileUuid = "11111111-1111-4111-8111-111111111111",
            blablaTripId = "admin-trip-0434",
            blablaPublicUrl = "https://www.blablacar.fr/trip?id=PublicTokenDifferent0434&search_uuid=temp&requested_seats=2",
            publishedSeats = 4,
            rotaCertaSeatAllocation = 0,
            capacityReliable = true,
            recordOrigin = TripRecordOrigin.EXTERNAL_BACKING,
            canonicalRevision = 2L,
            canonicalStateHash = "state-0434",
        )
        val operational = canonicalOperationalSnapshot0434(trip, emptyList(), trip.departureAtMillis - 1L)
        val payload = privateAgendaMirrorPayload0434(
            trip = trip,
            bookings = emptyList(),
            operationalSnapshot = operational,
            canonicalTripId = trip.id,
        )

        assertEquals(
            "https://www.blablacar.fr/trip?id=PublicTokenDifferent0434&requested_seats=2",
            payload.blablaPublicUrl,
        )
    }

    @Test
    fun privateMirrorDoesNotRecalculateCanonicalCapacityStatusOrItinerary() {
        val source = java.io.File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/PrivateAgendaMirror0434.kt",
        ).readText()
        assertTrue(source.contains("operationalSnapshot: CanonicalOperationalSnapshot0434"))
        assertTrue(source.contains("segmentLoads = operationalSnapshot.segmentLoads"))
        assertTrue(source.contains("status = trip.status.name"))
        assertTrue(source.contains("stops = trip.stops.sortedBy"))
        assertFalse(source.contains("SeatAvailabilityEngine.segmentLoads"))
        assertFalse(source.contains("operationalInventoryCapacity("))
        assertFalse(source.contains("operationalSeatSummary("))
    }

    @Test
    fun everyTripAndBookingFieldHasAnExplicitMirrorPolicy() {
        fun instanceFields(type: Class<*>): Set<String> = type.declaredFields
            .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet()

        assertEquals(instanceFields(Trip::class.java), timelineTripFieldPolicies0434().keys)
        assertEquals(instanceFields(Booking::class.java), timelineBookingFieldPolicies0434().keys)
        assertEquals(
            TimelineMirrorFieldPolicy0434.SECRET_NEVER_MIRROR,
            timelineTripFieldPolicies0434().getValue("blablaManageUrl"),
        )
        val protectedBookingCapabilityField = "cancellation" + "Token"
        assertEquals(
            TimelineMirrorFieldPolicy0434.SECRET_NEVER_MIRROR,
            timelineBookingFieldPolicies0434().getValue(protectedBookingCapabilityField),
        )
    }
    @Test
    fun canonicalExternalMirrorDoesNotDoubleTimelineOccupancyWithSynthesizedClaims() {
        val trip = Trip(
            id = "canonical-0441",
            title = "Origem → Destino",
            departureAtMillis = 2_000_000_000_000L,
            capacity = 4,
            status = TripStatus.PUBLISHED,
            stops = listOf(
                TripStop(id = "stop-a-0441", order = 0, name = "Origem", address = "Origem"),
                TripStop(id = "stop-b-0441", order = 1, name = "Destino", address = "Destino"),
            ),
            publishedSeats = 4,
            rotaCertaSeatAllocation = 0,
            capacityReliable = true,
            recordOrigin = TripRecordOrigin.EXTERNAL_BACKING,
            updatedAtMillis = 1_900_000_000_000L,
        )
        val canonicalPassenger = Booking(
            id = "canonical-passenger-0441",
            tripId = trip.id,
            passengerName = "Passageiro canônico",
            boardingStopId = "stop-a-0441",
            dropoffStopId = "stop-b-0441",
            seats = 4,
            status = BookingStatus.CONFIRMED,
            source = BookingSource.BLABLACAR,
            capacityClaimType = CapacityClaimType.EXTERNAL_OCCUPANCY,
            sourceReference = "CANONICAL:passenger-0441",
            occupancyGroupId = "canonical-passenger-0441",
        )
        val synthesizedDuplicate = canonicalPassenger.copy(
            id = "synthesized-claim-0441",
            sourceReference = "BLABLACAR_SYNC:duplicate-0441",
            occupancyGroupId = "synthesized-duplicate-0441",
        )

        val canonicalBookings = canonicalMirrorBookings0441(
            canonicalSourceAuthoritative = true,
            storedCanonicalBookings = listOf(canonicalPassenger),
            synthesizedCapacityClaims = listOf(synthesizedDuplicate),
        )
        val legacyFallbackBookings = canonicalMirrorBookings0441(
            canonicalSourceAuthoritative = false,
            storedCanonicalBookings = listOf(canonicalPassenger),
            synthesizedCapacityClaims = listOf(synthesizedDuplicate),
        )

        assertEquals(1, canonicalBookings.size)
        assertEquals(
            listOf(4),
            canonicalOperationalSnapshot0434(
                trip = trip,
                bookings = canonicalBookings,
                nowMillis = trip.updatedAtMillis,
            ).segmentLoads,
        )
        // Reproduces the physical evidence: re-appending the synthesized claim would
        // turn the canonical [4] load into [8].
        assertEquals(
            listOf(8),
            canonicalOperationalSnapshot0434(
                trip = trip,
                bookings = legacyFallbackBookings,
                nowMillis = trip.updatedAtMillis,
            ).segmentLoads,
        )
    }


}
