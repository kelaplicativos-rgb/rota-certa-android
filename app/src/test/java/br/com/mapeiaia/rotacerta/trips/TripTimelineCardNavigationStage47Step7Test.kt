package br.com.mapeiaia.rotacerta.trips

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TripTimelineCardNavigationStage47Step7Test {
    private val zone = ZoneId.of("America/Sao_Paulo")

    @Test
    fun local_trip_published_later_on_blablacar_becomes_one_card_and_keeps_exact_link() {
        val departure = LocalDate.of(2026, 8, 21).atTime(LocalTime.of(11, 0)).atZone(zone).toInstant().toEpochMilli()
        val local = TripTimelineEntry(
            tripId = "local-1", localTripId = "local-1", profileId = "local", profileLabel = "Agenda",
            departureAtMillis = departure, arrivalAtMillis = departure + 6 * 60 * 60 * 1000L,
            origin = "Santo André", destination = "Três Corações", status = TripStatus.PUBLISHED,
            capacity = 4, minimumOccupiedSeats = 1, maximumOccupiedSeats = 1,
            sourcePassengerSeats = mapOf(BookingSource.PRIVATE to 1),
        )
        val response = BlaBlaCollectorMonthResponse(
            status = "validated",
            trips = listOf(BlaBlaCollectorTrip(
                profile_uuid = "7371f028-9c55-4903-8444-308015823efd", profile_name = "Ezequiel S",
                date = "2026-08-21", departure_time = "11:20", arrival_time = "17:10",
                actual_departure = "Santo André, SP", actual_arrival = "Três Corações, MG",
                price = "R$ 89,00", trip_href = "https://www.blablacar.com.br/trip?source=CARPOOLING&id=trip-123&search_uuid=abc",
                trip_id = "trip-123", uuid_validation = "verified_from_authenticated_profile_session",
            )),
        )
        val merged = BlaBlaTimelineAdapter.merge(listOf(local), response, zone)
        assertEquals(1, merged.size)
        val card = merged.single()
        assertEquals("local-1", card.tripId)
        assertEquals("local-1", card.localTripId)
        assertEquals("trip-123", card.blablaTripId)
        assertEquals("https://www.blablacar.com.br/trip?source=CARPOOLING&id=trip-123&search_uuid=abc", card.blablaTripHref)
        assertEquals("7371f028-9c55-4903-8444-308015823efd", card.blablaProfileUuid)
        assertEquals("R$ 89,00", card.blablaPrice)
        assertEquals("Santo André, SP", card.origin)
        assertEquals("Três Corações, MG", card.destination)
        assertEquals(1, card.sourcePassengerSeats[BookingSource.PRIVATE])
        assertFalse(TripTimelineIssue.DUPLICATE in card.issues)
    }

    @Test
    fun ezequiel_and_barbosa_are_checked_as_one_physical_timeline() {
        fun entry(id: String, profile: String, start: Long, end: Long, origin: String, destination: String) = TripTimelineEntry(
            tripId = id, profileId = profile, profileLabel = profile, departureAtMillis = start, arrivalAtMillis = end,
            origin = origin, destination = destination, status = TripStatus.PUBLISHED, capacity = 4,
            minimumOccupiedSeats = 0, maximumOccupiedSeats = 0, sourcePassengerSeats = emptyMap(),
        )
        val first = entry("a", "7371f028-9c55-4903-8444-308015823efd", 1_000L, 2_000L, "Santo André", "Três Corações")
        val continuous = entry("b", "175a7068-50d8-40c3-a27a-214b9c6e0461", 3_000L, 4_000L, "Três Corações", "Santo André")
        val ok = TripTimelineEngine.annotate(listOf(first, continuous))
        assertFalse(TripTimelineIssue.PROFILE_CONTINUITY in ok.last().issues)
        val warned = TripTimelineEngine.annotate(listOf(first, continuous.copy(tripId = "c", origin = "Varginha")))
        assertTrue(TripTimelineIssue.PROFILE_CONTINUITY in warned.last().issues)
    }
}
