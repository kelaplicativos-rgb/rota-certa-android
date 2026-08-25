package br.com.mapeiaia.rotacerta.trips

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AgendaTodayOnlySync0280Test {
    private fun trip(id: String, date: String) = BlaBlaCollectorTrip(
        profile_uuid = "profile-1",
        date = date,
        departure_time = "10:00",
        actual_departure = "Origem",
        actual_arrival = "Destino",
        trip_id = id,
    )

    @Test
    fun todayScopeKeepsOnlyTripsWhoseNormalizedDateMatches() {
        val response = BlaBlaCollectorMonthResponse(
            status = "success",
            trips = listOf(
                trip("yesterday", "2026-08-24"),
                trip("today-a", "2026-08-25"),
                trip("today-b", "2026-08-25"),
                trip("tomorrow", "2026-08-26"),
            ),
        )
        val scoped = BlaBlaCollectorTimelineModule.scopeResponseToDate(
            response,
            LocalDate.of(2026, 8, 25),
        )
        assertEquals(listOf("today-a", "today-b"), scoped.trips.map { it.trip_id })
        assertEquals("date_scope:2026-08-25", scoped.coverage.reason)
    }

    @Test
    fun emptyTodayScopeDoesNotResurrectOtherDatesOnStartup() {
        val persisted = BlaBlaCollectorTimelineModule.scopeResponseToDate(
            BlaBlaCollectorMonthResponse(status = "success", trips = listOf(trip("tomorrow", "2026-08-26"))),
            LocalDate.of(2026, 8, 25),
        )
        val dynamic = BlaBlaCollectorMonthResponse(
            status = "success",
            trips = listOf(trip("tomorrow", "2026-08-26")),
        )
        assertSame(persisted, BlaBlaCollectorTimelineModule.recoverStartupResponse(persisted, dynamic))
    }
}
