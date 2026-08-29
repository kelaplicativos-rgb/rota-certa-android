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
        assertEquals("", trip.trip.notes)
    }

    @Test
    fun fullCollectorTripStaysFullAndCannotInventMoreCapacity() {
        val source = BlaBlaCollectorTrip(
            profile_uuid = "profile-barbosa",
            date = "2030-09-11",
            departure_time = "19:00",
            search_from = "São Thomé das Letras",
            search_to = "Santo André",
            availability = "full",
            booked_seats = 7,
        )
        val trip = PublicAgendaAutoSync0300.toPublicTrip(source, 4, 0L, zone)
        assertNotNull(trip)
        assertEquals(TripStatus.FULL, trip.trip.status)
        assertEquals(4, trip.bookedSeats)
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

        assertTrue(source.contains("PUBLIC_AGENDA_EXTERNAL_PUBLISH_RETRY"))
        assertTrue(source.contains("PUBLIC_AGENDA_EXTERNAL_SYNC_FAILED"))
        assertTrue(source.contains("failureStage = \"update_after_publish_failure\""))
        assertTrue(source.contains("failureStage = \"capacity_claims\""))
        assertTrue(source.contains("reason=\${error.javaClass.simpleName}"))
        assertTrue(source.contains("tripKey=\$diagnosticTripKey"))
        assertTrue(source.contains("failures++"))
        assertTrue(!source.contains("PUBLIC_AGENDA_EXTERNAL_SYNC_FAILED.*passengerName"))
        assertTrue(!source.contains("PUBLIC_AGENDA_EXTERNAL_SYNC_FAILED.*passengerContact"))
    }

    @Test
    fun priceParserAcceptsBrazilianFormatting() {
        assertEquals(9_300L, PublicAgendaAutoSync0300.parsePriceCents("R$ 93,00"))
        assertEquals(10_500L, PublicAgendaAutoSync0300.parsePriceCents("105"))
        assertEquals(0L, PublicAgendaAutoSync0300.parsePriceCents(null))
    }
}
