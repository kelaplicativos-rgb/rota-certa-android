package br.com.mapeiaia.rotacerta.trips

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class PublicAgendaStopTimes0672Test {
    private val zone = ZoneId.of("America/Sao_Paulo")

    private fun millis(day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(2026, 9, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private fun source(
        date: String = "2026-09-27",
        departure: String = "10:30",
        arrival: String = "15:10",
        times: List<String> = listOf("10:30", "10:50", "13:50", "15:10"),
    ) = BlaBlaCollectorTrip(
        profile_uuid = "7371f028-9c55-4903-8444-308015823efd",
        profile_name = "Ezequiel",
        date = date,
        departure_time = departure,
        arrival_time = arrival,
        actual_departure = "Santo André",
        actual_arrival = "Três Corações",
        trip_href = "https://www.blablacar.com.br/rides/offer/trip-0672",
        trip_id = "trip-0672",
        itinerary_stops = listOf("Santo André", "São Paulo", "Pouso Alegre", "Três Corações"),
        itinerary_stop_times = times,
        itinerary_authoritative = true,
        published_seats = 4,
        passenger_roster_complete = true,
    )

    @Test
    fun htmlTimesBecomeCanonicalTripStopTimes() {
        val projected = PublicAgendaAutoSync0300.toPublicTrip(
            source = source(),
            capacity = 4,
            nowMillis = Long.MIN_VALUE,
            zoneId = zone,
        )
        assertNotNull(projected)
        val stops = projected.trip.stops.sortedBy(TripStop::order)
        assertEquals(millis(27, 10, 30), stops[0].plannedDepartureMillis)
        assertEquals(millis(27, 10, 50), stops[1].plannedDepartureMillis)
        assertEquals(millis(27, 13, 50), stops[2].plannedDepartureMillis)
        assertEquals(millis(27, 15, 10), stops[3].plannedArrivalMillis)
    }

    @Test
    fun stopTimesRollAcrossMidnightWithoutGoingBackwards() {
        val overnight = source(
            departure = "23:30",
            arrival = "01:00",
            times = listOf("23:30", "23:50", "00:10", "01:00"),
        )
        val projected = PublicAgendaAutoSync0300.toPublicTrip(
            source = overnight,
            capacity = 4,
            nowMillis = Long.MIN_VALUE,
            zoneId = zone,
        )
        assertNotNull(projected)
        val stops = projected.trip.stops.sortedBy(TripStop::order)
        assertEquals(millis(27, 23, 30), stops[0].plannedDepartureMillis)
        assertEquals(millis(27, 23, 50), stops[1].plannedDepartureMillis)
        assertEquals(millis(28, 0, 10), stops[2].plannedDepartureMillis)
        assertEquals(millis(28, 1, 0), stops[3].plannedArrivalMillis)
    }

    @Test
    fun itineraryTimeChangeChangesTheSemanticSnapshot() {
        val first = PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(source(), 0)
        val changed = PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(
            source(times = listOf("10:30", "10:55", "13:50", "15:10")),
            0,
        )
        assertNotEquals(first, changed)
    }
}
