package br.com.mapeiaia.rotacerta.trips

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
