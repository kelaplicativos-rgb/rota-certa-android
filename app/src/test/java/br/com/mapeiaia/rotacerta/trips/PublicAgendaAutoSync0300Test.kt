package br.com.mapeiaia.rotacerta.trips

import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PublicAgendaAutoSync0300Test {
    private val zone = ZoneId.of("America/Sao_Paulo")

    @Test
    fun collectorTripBecomesPermanentPublicAgendaTrip() {
        val source = BlaBlaCollectorTrip(
            profile_uuid = "profile-ezequiel",
            profile_name = "Ezequiel S",
            date = "2030-09-10",
            departure_time = "11:00",
            arrival_time = "16:00",
            actual_departure = "Santo André, SP",
            actual_arrival = "São Thomé das Letras, MG",
            price = "R$ 93,00",
            trip_href = "https://www.blablacar.com.br/rides/offer/trip-123",
            public_trip_href = "https://www.blablacar.com.br/trip?id=trip-123&search_uuid=private-noise",
            trip_id = "trip-123",
            booked_seats = 2,
            passenger_roster_complete = true,
        )
        val trip = PublicAgendaAutoSync0300.toPublicTrip(
            source = source,
            capacity = 4,
            nowMillis = 0L,
            zoneId = zone,
        )
        assertNotNull(trip)
        assertEquals(4, trip.trip.capacity)
        assertEquals(TripStatus.PUBLISHED, trip.trip.status)
        assertEquals(2, trip.bookedSeats)
        assertEquals(9_300L, trip.trip.stops.first().priceToNextCents)
        assertEquals("trip-123", trip.sourceReference)
        assertTrue(trip.trip.publicBookingEnabled)
        assertTrue(trip.trip.publicToken.startsWith("bb"))
        assertEquals("profile-ezequiel", trip.trip.blablaProfileUuid)
        assertEquals("trip-123", trip.trip.blablaTripId)
        assertEquals("https://www.blablacar.com.br/rides/offer/trip-123", trip.trip.blablaManageUrl)
        assertEquals("https://www.blablacar.com.br/trip?id=trip-123", trip.trip.blablaPublicUrl)
        assertEquals("https://www.blablacar.com.br/trip?id=trip-123", trip.blablaPublicHref)
        assertEquals("", trip.trip.notes)
    }

    @Test
    fun blablaBookedEvidenceRemainsOccupancyAndNeverBecomesPhysicalCapacity() {
        val source = BlaBlaCollectorTrip(
            profile_uuid = "profile-barbosa",
            date = "2030-09-11",
            departure_time = "19:00",
            search_from = "São Thomé das Letras",
            search_to = "Santo André",
            availability = "full",
            booked_seats = 7,
        )
        val trip = PublicAgendaAutoSync0300.toPublicTrip(
            source = source,
            capacity = 4,
            rotaCertaSeatAllocation = 4,
            nowMillis = 0L,
            zoneId = zone,
        )
        assertNotNull(trip)
        assertEquals(4, trip.trip.capacity)
        assertEquals(4, trip.trip.rotaCertaSeatAllocation)
        assertEquals(7, trip.bookedSeats)
        assertEquals(7, trip.capacityClaims.sumOf(Booking::seats))
        assertTrue(trip.capacityClaims.all { it.capacityClaimType == CapacityClaimType.EXTERNAL_OCCUPANCY })
    }

    @Test
    fun localCapacityMirrorsArePrivateStableAndExcludePublicLinkBookings() {
        val trip = Trip(
            id = "local-trip-1",
            title = "Santo André → São Tomé das Letras",
            departureAtMillis = 4_000_000_000_000L,
            capacity = 4,
            status = TripStatus.FULL,
            stops = listOf(
                TripStop(id = "sa", order = 0, name = "Santo André"),
                TripStop(id = "sp", order = 1, name = "São Paulo"),
                TripStop(id = "stl", order = 2, name = "São Tomé das Letras"),
            ),
        )
        val blabla = Booking(
            id = "blabla-booking-1",
            tripId = trip.id,
            passengerName = "Nome real não deve subir",
            passengerContact = "(11) 99999-9999",
            boardingStopId = "sa",
            dropoffStopId = "stl",
            seats = 3,
            status = BookingStatus.CONFIRMED,
            source = BookingSource.BLABLACAR,
        )
        val publicLink = Booking(
            id = "public-booking-1",
            tripId = trip.id,
            passengerName = "Reserva do link",
            boardingStopId = "sp",
            dropoffStopId = "stl",
            seats = 1,
            status = BookingStatus.CONFIRMED,
            source = BookingSource.ROTA_CERTA,
            sourceReference = "PUBLIC_LINK:public-booking-1",
        )

        val mirrors = PublicAgendaAutoSync0300.localCapacityMirrors(trip, listOf(blabla, publicLink))

        assertEquals(1, mirrors.size)
        val mirror = mirrors.single()
        assertTrue(mirror.id.startsWith("mirror-"))
        assertEquals("Ocupação sincronizada", mirror.passengerName)
        assertEquals("", mirror.passengerContact)
        assertEquals(3, mirror.seats)
        assertEquals("sa", mirror.boardingStopId)
        assertEquals("stl", mirror.dropoffStopId)
        assertEquals(BookingSource.BLABLACAR, mirror.source)
        assertTrue(mirror.sourceReference.startsWith("LOCAL_MIRROR:"))
    }
    @Test
    fun channelAllocationsBuildOperationalTotalWithoutChangingPhysicalCapacity() {
        val breakdown = tripChannelAllocationBreakdown(
            physicalPassengerCapacity = 7,
            blablaPublishedSeats = 3,
            rotaCertaSeatAllocation = 4,
        )
        assertEquals(3, breakdown.blablaQuota)
        assertEquals(4, breakdown.rotaCertaQuota)
        assertEquals(7, breakdown.operationalInventory)
    }

    @Test
    fun externalTripPropagatesItineraryAuthorityAndFailsClosedUntilSeatClaimsReconcile() {
        val authoritative = BlaBlaCollectorTrip(
            profile_uuid = "profile",
            date = "2030-09-12",
            departure_time = "11:00",
            actual_departure = "Santo André",
            actual_arrival = "São Tomé das Letras",
            itinerary_stops = listOf("Santo André", "Três Corações", "São Tomé das Letras"),
            itinerary_authoritative = true,
            published_seats = 3,
        )
        val published = PublicAgendaAutoSync0300.toPublicTrip(authoritative, 4, 0L, zone)
        assertNotNull(published)
        assertTrue(published.trip.itineraryAuthoritative)
        assertEquals(3, published.trip.publishedSeats)
        assertEquals(false, published.trip.capacityReliable)

        val unknown = authoritative.copy(published_seats = null)
        val unknownPublished = PublicAgendaAutoSync0300.toPublicTrip(unknown, 4, 0L, zone)
        assertNotNull(unknownPublished)
        assertNull(unknownPublished.publishedSeats)
        assertEquals(false, unknownPublished.trip.capacityReliable)
    }

    @Test
    fun publicProjectionKeepsBlaBlaFreeSeatsAndExternalPassengersSeparate() {
        val source = BlaBlaCollectorTrip(
            profile_uuid = "profile",
            date = "2030-09-13",
            departure_time = "11:00",
            actual_departure = "Santo André",
            actual_arrival = "São Tomé das Letras",
            published_seats = 3,
            booked_seats = 3,
            passengers = listOf(
                BlaBlaCollectorPassenger(name = "P1", seats = 1, booking_href = "https://www.blablacar.com.br/rides/offer/trip/passenger/p1"),
                BlaBlaCollectorPassenger(name = "P2", seats = 1, booking_href = "https://www.blablacar.com.br/rides/offer/trip/passenger/p2"),
                BlaBlaCollectorPassenger(name = "P3", seats = 1, booking_href = "https://www.blablacar.com.br/rides/offer/trip/passenger/p3"),
            ),
        )
        val external = PublicAgendaAutoSync0300.toPublicTrip(
            source = source,
            capacity = 7,
            rotaCertaSeatAllocation = 4,
            nowMillis = 0L,
            zoneId = zone,
        )
        assertNotNull(external)
        assertEquals(7, external.trip.capacity)
        assertEquals(3, external.trip.publishedSeats)
        assertEquals(4, external.trip.rotaCertaSeatAllocation)
        assertEquals(3, external.capacityClaims.sumOf(Booking::seats))
        assertTrue(external.capacityClaims.all { it.capacityClaimType == CapacityClaimType.EXTERNAL_OCCUPANCY })
        val summary = operationalSeatSummary(external.trip, external.capacityClaims)
        assertEquals(3, summary.blablaQuotaSeats)
        assertEquals(4, summary.rotaCertaQuotaSeats)
        assertEquals(7, summary.operationalInventorySeats)
        assertEquals(4, summary.totalAvailableSeats)
        assertEquals(3, summary.confirmedPassengerSeats)
        assertEquals(4, summary.availableSeats)
    }

    @Test
    fun departedCollectorTripIsNotRepublished() {
        val departure = LocalDate.of(2030, 9, 10)
            .atTime(LocalTime.of(11, 0))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val source = BlaBlaCollectorTrip(
            profile_uuid = "profile-ezequiel",
            date = "2030-09-10",
            departure_time = "11:00",
            search_from = "Santo André",
            search_to = "São Thomé das Letras",
        )
        assertNull(PublicAgendaAutoSync0300.toPublicTrip(source, 4, departure + 1L, zone))
    }

    @Test
    fun externalPublicationFailuresExposeStageWithoutPassengerPii() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/PublicAgendaAutoSync0300.kt",
        ).readText()

        assertTrue(source.contains("EXTERNAL_CAPACITY_SNAPSHOT"))
        assertTrue(source.contains("PUBLIC_AGENDA_EXTERNAL_SYNC_FAILED"))
        assertTrue(source.contains("PUBLIC_CAPACITY_FAIL_CLOSED"))
        assertTrue(source.contains("PUBLIC_CAPACITY_INCREMENTAL_PUBLISHED"))
        assertTrue(source.contains("reason=\${error.javaClass.simpleName}"))
        assertTrue(source.contains("tripKey=\$diagnosticTripKey"))
        assertTrue(source.contains("failures++"))
        assertTrue(!source.contains("PUBLIC_AGENDA_EXTERNAL_SYNC_FAILED.*passengerName"))
        assertTrue(!source.contains("PUBLIC_AGENDA_EXTERNAL_SYNC_FAILED.*passengerContact"))
    }

    @Test
    fun immutableBookedTripShapeFailureIsRecognizedWithoutBroadeningOtherErrors() {
        val protected = IllegalStateException(
            "Servidor respondeu HTTP 400: {\"message\":\"Capacidade e estrutura de paradas não podem mudar depois da primeira reserva.\"}",
        )
        assertTrue(PublicAgendaAutoSync0300.isImmutablePublicTripShapeFailure(protected))
        assertTrue(!PublicAgendaAutoSync0300.isImmutablePublicTripShapeFailure(IllegalStateException("HTTP 500")))
    }

    @Test
    fun bookedExternalTripPreservesExistingBindingShapeAndRemapsClaims() {
        val token = "bb123456789012345678901234567890"
        val observed = Trip(
            id = "public:$token",
            title = "Santo André → São Thomé das Letras",
            departureAtMillis = 4_000_000_000_000L,
            capacity = 6,
            status = TripStatus.PUBLISHED,
            publicToken = token,
            remoteId = token,
            publicBookingEnabled = true,
            stops = listOf(
                TripStop(id = "new-sa", order = 0, name = "Santo André"),
                TripStop(id = "new-pa", order = 1, name = "Pouso Alegre"),
                TripStop(id = "new-cam", order = 2, name = "Camanducaia"),
                TripStop(id = "new-stl", order = 3, name = "São Thomé das Letras"),
            ),
        )
        val binding = PublicExternalTripBinding(
            remoteTripId = token,
            publicToken = token,
            bookingTripId = "public-external:$token",
            profileUuid = "profile",
            blablaTripId = "trip",
            title = observed.title,
            departureAtMillis = observed.departureAtMillis,
            capacity = 4,
            stops = listOf(
                TripStop(id = "old-sa", order = 0, name = "Santo André"),
                TripStop(id = "old-pa", order = 1, name = "Pouso Alegre"),
                TripStop(id = "old-stl", order = 2, name = "São Thomé das Letras"),
            ),
        )
        val preserved = PublicAgendaAutoSync0300.preserveExternalBindingShape(observed, binding)
        assertEquals(listOf("old-sa", "old-pa", "old-stl"), preserved.stops.map(TripStop::id))
        assertEquals(6, preserved.capacity)

        val matchingClaim = Booking(
            id = "claim-1",
            tripId = observed.id,
            passengerName = "Ocupação",
            boardingStopId = "new-pa",
            dropoffStopId = "new-stl",
            seats = 1,
            status = BookingStatus.CONFIRMED,
            source = BookingSource.BLABLACAR,
        )
        val newMiddleStopClaim = matchingClaim.copy(
            id = "claim-2",
            boardingStopId = "new-cam",
            dropoffStopId = "new-stl",
        )
        val remapped = PublicAgendaAutoSync0300.remapExternalClaimsToBindingStructure(
            claims = listOf(matchingClaim, newMiddleStopClaim),
            observedStops = observed.stops,
            preservedTrip = preserved,
        )
        assertEquals("old-pa", remapped[0].boardingStopId)
        assertEquals("old-stl", remapped[0].dropoffStopId)
        assertEquals("old-sa", remapped[1].boardingStopId)
        assertEquals("old-stl", remapped[1].dropoffStopId)
    }

    @Test
    fun cancellationIsNeverCountedAsExternalPublicationFailure() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/PublicAgendaAutoSync0300.kt",
        ).readText()
        assertTrue(source.contains("catch (error: CancellationException)"))
        assertTrue(source.contains("AgendaTrace.operationCancelled"))
        assertTrue(source.contains("throw error"))
        assertTrue(source.contains("shapePreserved = true"))
    }

    @Test
    fun priceParserAcceptsBrazilianFormatting() {
        assertEquals(9_300L, PublicAgendaAutoSync0300.parsePriceCents("R$ 93,00"))
        assertEquals(10_500L, PublicAgendaAutoSync0300.parsePriceCents("105"))
        assertEquals(0L, PublicAgendaAutoSync0300.parsePriceCents(null))
    }
}
