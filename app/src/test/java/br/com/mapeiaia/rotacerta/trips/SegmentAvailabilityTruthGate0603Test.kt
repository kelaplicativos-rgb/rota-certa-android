package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SegmentAvailabilityTruthGate0603Test {
    private val profile = "11111111-1111-4111-8111-111111111111"

    private fun source(
        itinerary: List<String>,
        authoritative: Boolean,
        rosterComplete: Boolean = true,
    ) = BlaBlaCollectorTrip(
        profile_uuid = profile,
        profile_name = "Motorista",
        date = "2026-09-25",
        departure_time = "11:00",
        arrival_time = "16:40",
        actual_departure = "Santo André",
        actual_arrival = "São Tomé das Letras",
        trip_href = "https://www.blablacar.com.br/rides/offer/trip-0603",
        trip_id = "trip-0603",
        itinerary_stops = itinerary,
        itinerary_authoritative = authoritative,
        passengers = listOf(
            BlaBlaCollectorPassenger(
                name = "Julio",
                seats = 1,
                boarding = "Santo André",
                dropoff = "São Tomé das Letras",
            ),
            BlaBlaCollectorPassenger(
                name = "Ingrid",
                seats = 2,
                boarding = "Camanducaia",
                dropoff = "Minas Gerais",
            ),
        ),
        booked_seats = 3,
        published_seats = 4,
        passenger_roster_complete = rosterComplete,
    )

    @Test
    fun videoRegression_endpointOnlyCanonicalRouteMustNeverClaimWholeTripThreeOfFour() {
        val external = source(
            itinerary = listOf("Santo André", "São Tomé das Letras"),
            authoritative = false,
        )
        val trip = Trip(
            id = "canonical-video",
            title = "Santo André → São Tomé das Letras",
            departureAtMillis = 1_000L,
            capacity = 4,
            status = TripStatus.PUBLISHED,
            stops = listOf(
                TripStop(id = "sa", order = 0, name = "Santo André"),
                TripStop(id = "stl", order = 1, name = "São Tomé das Letras"),
            ),
            blablaProfileUuid = profile,
            blablaTripId = "trip-0603",
            itineraryAuthoritative = false,
            capacityReliable = true,
            externalSnapshot = external,
            externalSnapshotComplete = true,
        )
        val truth = segmentAvailabilityTruth0603(trip)

        assertFalse(truth.verified)
        assertEquals(
            SegmentAvailabilityTruthReason0603.ITINERARY_NOT_AUTHORITATIVE,
            truth.reason,
        )
        assertTrue(AgendaSeatPolicy0582.segments(trip, emptyList(), 0L).isEmpty())

        val entry = TripTimelineEntry(
            tripId = trip.id,
            profileId = profile,
            profileLabel = "Motorista",
            departureAtMillis = trip.departureAtMillis,
            arrivalAtMillis = 2_000L,
            origin = "Santo André",
            destination = "São Tomé das Letras",
            status = TripStatus.PUBLISHED,
            capacity = 4,
            minimumOccupiedSeats = 3,
            maximumOccupiedSeats = 3,
            sourcePassengerSeats = emptyMap(),
            localTripId = trip.id,
            blablaProfileUuid = profile,
            blablaTripId = "trip-0603",
            canonicalBackendAuthoritative0494 = true,
            canonicalCapacityReliable0494 = true,
            canonicalSegmentLoads0494 = listOf(3),
            canonicalSegmentPassengerLoads0494 = listOf(3),
            canonicalSegmentBlockedLoads0494 = listOf(0),
            canonicalSegmentAvailableSeats0494 = listOf(1),
        )
        assertTrue(canonicalTimelineSegmentLoads0494(entry, trip).isEmpty())
    }

    @Test
    fun authoritativeCompleteRouteMayExposeTheRealPerSegmentPattern() {
        val names = listOf(
            "Santo André",
            "São Paulo",
            "Camanducaia",
            "Três Corações",
            "Minas Gerais",
            "São Tomé das Letras",
        )
        val stops = names.mapIndexed { index, name ->
            TripStop(id = "stop-$index", order = index, name = name)
        }
        val external = source(itinerary = names, authoritative = true)
        val trip = Trip(
            id = "canonical-verified",
            title = "Santo André → São Tomé das Letras",
            departureAtMillis = 1_000L,
            capacity = 4,
            status = TripStatus.PUBLISHED,
            stops = stops,
            blablaProfileUuid = profile,
            blablaTripId = "trip-0603",
            itineraryAuthoritative = true,
            capacityReliable = true,
            externalSnapshot = external,
            externalSnapshotComplete = true,
        )
        val bookings = listOf(
            Booking(
                id = "julio",
                tripId = trip.id,
                passengerName = "Julio",
                boardingStopId = stops[0].id,
                dropoffStopId = stops[5].id,
                seats = 1,
                status = BookingStatus.CONFIRMED,
                source = BookingSource.BLABLACAR,
            ),
            Booking(
                id = "ingrid",
                tripId = trip.id,
                passengerName = "Ingrid",
                boardingStopId = stops[2].id,
                dropoffStopId = stops[4].id,
                seats = 2,
                status = BookingStatus.CONFIRMED,
                source = BookingSource.BLABLACAR,
            ),
        )

        val truth = segmentAvailabilityTruth0603(trip)
        assertTrue(truth.verified)
        val segments = AgendaSeatPolicy0582.segments(trip, bookings, 0L)

        assertEquals(5, segments.size)
        assertEquals(listOf(3, 3, 1, 1, 3), segments.map { it.availableSeats })
        assertEquals(listOf(1, 1, 3, 3, 1), segments.map { it.passengerSeats })
    }

    @Test
    fun timelineAndCentralHideTheSectionWhenTruthIsNotVerified() {
        val timeline = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/OperationalAllTripsBrowserUi0563.kt",
        ).readText()
        val central = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/CentralDoDia0552.kt",
        ).readText()

        assertTrue(timeline.contains("if (row.segmentLoads0602.isNotEmpty())"))
        assertFalse(timeline.contains("Aguardando atualização canônica das vagas."))
        assertTrue(central.contains("if (item.segmentLoads.isNotEmpty())"))
        assertFalse(central.contains("Disponibilidade por trecho aguardando estado canônico."))
        assertTrue(central.contains("segmentAvailabilityTruth0603(trip)"))
    }
}
