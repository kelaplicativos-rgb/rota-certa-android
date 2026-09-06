package br.com.mapeiaia.rotacerta.trips

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlaBlaManualSeatAutomationStage47Test {
    private val departure = LocalDateTime.of(2026, 8, 21, 10, 30)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    private val trip = Trip(
        id = "local-trip",
        title = "Santo André → São Tomé das Letras",
        departureAtMillis = departure,
        capacity = 4,
        status = TripStatus.PUBLISHED,
        stops = listOf(
            TripStop(id = "a", order = 0, name = "Santo André"),
            TripStop(id = "b", order = 1, name = "São Tomé das Letras"),
        ),
    )

    private fun external(id: String = "external-trip") = BlaBlaCollectorTrip(
        profile_uuid = "7371f028-9c55-4903-8444-308015823efd",
        profile_name = "Conta",
        date = "2026-08-21",
        departure_time = "10:30",
        actual_departure = "Santo André",
        actual_arrival = "São Tomé das Letras",
        trip_id = id,
        uuid_validation = "verified_from_authenticated_profile_session",
    )

    @Test
    fun resolvesOnlyOneExactPublicationWithCanonicalIds() {
        val match = BlaBlaManualSeatTripResolver.resolveExact(
            trip,
            BlaBlaCollectorMonthResponse(
                status = "validated",
                trips = listOf(external()),
            ),
        )
        assertEquals("external-trip", match?.trip_id)
        assertEquals("7371f028-9c55-4903-8444-308015823efd", match?.profile_uuid)
    }

    @Test
    fun refusesAmbiguousPublicationInsteadOfClickingBlindly() {
        val match = BlaBlaManualSeatTripResolver.resolveExact(
            trip,
            BlaBlaCollectorMonthResponse(
                status = "validated",
                trips = listOf(external("trip-a"), external("trip-b")),
            ),
        )
        assertNull(match)
    }

    @Test
    fun refusesPublicationWithoutExternalTripId() {
        val match = BlaBlaManualSeatTripResolver.resolveExact(
            trip,
            BlaBlaCollectorMonthResponse(
                status = "validated",
                trips = listOf(external().copy(trip_id = null)),
            ),
        )
        assertNull(match)
    }
}
