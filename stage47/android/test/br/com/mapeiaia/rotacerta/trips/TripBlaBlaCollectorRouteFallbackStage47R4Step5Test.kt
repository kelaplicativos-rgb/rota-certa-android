package br.com.mapeiaia.rotacerta.trips

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TripBlaBlaCollectorRouteFallbackStage47R4Step5Test {
    private val utc = ZoneId.of("UTC")
    private fun millis(value: String) = LocalDateTime.parse(value).atZone(utc).toInstant().toEpochMilli()

    @Test
    fun searchedRouteCanReconcileGenericCardStationsWithoutLosingLocalGeography() {
        val local = TripTimelineEntry(
            tripId = "local",
            profileId = "Agenda",
            profileLabel = "Agenda",
            departureAtMillis = millis("2026-09-22T11:00:00"),
            arrivalAtMillis = null,
            origin = "Cidade Precisa A",
            destination = "Cidade Precisa B",
            status = TripStatus.PUBLISHED,
            capacity = 4,
            minimumOccupiedSeats = 2,
            maximumOccupiedSeats = 2,
            sourcePassengerSeats = mapOf(BookingSource.PRIVATE to 2),
        )
        val response = BlaBlaCollectorMonthResponse(
            status = "validated",
            month = "2026-09",
            trips = listOf(
                BlaBlaCollectorTrip(
                    profile_uuid = "7371f028-9c55-4903-8444-308015823efd",
                    profile_name = "Perfil",
                    date = "2026-09-22",
                    departure_time = "11:05",
                    search_from = "Cidade Precisa A",
                    search_to = "Cidade Precisa B",
                    actual_departure = "Estado A",
                    actual_arrival = "Estado B",
                    trip_id = "remote",
                    uuid_validation = "verified_from_trip_detail_profile_link",
                ),
            ),
        )

        val merged = BlaBlaTimelineAdapter.merge(listOf(local), response, utc).single()

        assertEquals("local", merged.tripId)
        assertEquals("Cidade Precisa A", merged.origin)
        assertEquals("Cidade Precisa B", merged.destination)
        assertEquals(2, merged.maximumOccupiedSeats)
        assertEquals("7371f028-9c55-4903-8444-308015823efd", merged.profileId)
    }
}
