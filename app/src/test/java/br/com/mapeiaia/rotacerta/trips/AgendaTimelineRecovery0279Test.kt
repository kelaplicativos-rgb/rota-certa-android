package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Test

class AgendaTimelineRecovery0279Test {
    private val trip = BlaBlaCollectorTrip(
        profile_uuid = "profile-1",
        date = "2026-08-25",
        departure_time = "11:00",
        actual_departure = "Origem",
        actual_arrival = "Destino",
        trip_id = "trip-1",
    )

    @Test
    fun explicitClearNeverResurrectsSessionTrips() {
        val persisted = BlaBlaCollectorMonthResponse(status = "cleared", trips = emptyList())
        val dynamic = BlaBlaCollectorMonthResponse(status = "success", trips = listOf(trip))
        assertEquals(persisted, BlaBlaCollectorTimelineModule.recoverStartupResponse(persisted, dynamic))
    }

    @Test
    fun emptyPersistedTimelineRecoversVerifiedSessionTrips() {
        val persisted = BlaBlaCollectorMonthResponse(status = "partial", trips = emptyList())
        val dynamic = BlaBlaCollectorMonthResponse(status = "success", trips = listOf(trip))
        assertEquals(dynamic, BlaBlaCollectorTimelineModule.recoverStartupResponse(persisted, dynamic))
    }

    @Test
    fun existingPersistedTimelineRemainsStartupAuthority() {
        val persisted = BlaBlaCollectorMonthResponse(status = "success", trips = listOf(trip.copy(trip_id = "persisted")))
        val dynamic = BlaBlaCollectorMonthResponse(status = "success", trips = listOf(trip.copy(trip_id = "dynamic")))
        assertEquals(persisted, BlaBlaCollectorTimelineModule.recoverStartupResponse(persisted, dynamic))
    }
}
