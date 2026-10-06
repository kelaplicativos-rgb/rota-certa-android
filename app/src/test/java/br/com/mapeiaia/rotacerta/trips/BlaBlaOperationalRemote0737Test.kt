package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BlaBlaOperationalRemote0737Test {
    private fun trip(
        rosterComplete: Boolean = true,
        itineraryAuthoritative: Boolean = true,
        publishedSeats: Int? = 3,
        identityConflict: Boolean = false,
        passengers: List<BlaBlaCollectorPassenger> = emptyList(),
    ) = BlaBlaCollectorTrip(
        profile_uuid = "7371f028-9c55-4903-8444-308015823efd",
        profile_name = "Conta teste",
        date = "2026-11-08",
        departure_time = "10:30",
        arrival_time = "15:30",
        actual_departure = "São Paulo",
        actual_arrival = "São Tomé das Letras",
        price = "R$ 100",
        availability = "3",
        trip_href = "https://www.blablacar.com.br/ride-plan/trip-edit/01a0359e-de23-7a2b-ab27-43990c399a74",
        public_trip_href = "https://www.blablacar.com.br/trip/01a0359e-de23-7a2b-ab27-43990c399a74",
        trip_id = "01a0359e-de23-7a2b-ab27-43990c399a74",
        uuid_validation = "confirmed",
        passengers = passengers,
        itinerary_stops = listOf("São Paulo", "São Tomé das Letras"),
        itinerary_stop_times = listOf("10:30", "15:30"),
        itinerary_authoritative = itineraryAuthoritative,
        booked_seats = passengers.sumOf { it.seats },
        published_seats = publishedSeats,
        passenger_roster_complete = rosterComplete,
        identity_conflict = identityConflict,
    )

    @Test
    fun completeRequiresStrongOperationalEvidence() {
        assertTrue(
            blaBlaOperationalCoreComplete0737(
                trip(),
                "7371f028-9c55-4903-8444-308015823efd",
                "01a0359e-de23-7a2b-ab27-43990c399a74",
            ),
        )
        assertFalse(
            blaBlaOperationalCoreComplete0737(
                trip(rosterComplete = false),
                "7371f028-9c55-4903-8444-308015823efd",
                "01a0359e-de23-7a2b-ab27-43990c399a74",
            ),
        )
        assertFalse(
            blaBlaOperationalCoreComplete0737(
                trip(publishedSeats = null),
                "7371f028-9c55-4903-8444-308015823efd",
                "01a0359e-de23-7a2b-ab27-43990c399a74",
            ),
        )
        assertFalse(
            blaBlaOperationalCoreComplete0737(
                trip(identityConflict = true),
                "7371f028-9c55-4903-8444-308015823efd",
                "01a0359e-de23-7a2b-ab27-43990c399a74",
            ),
        )
    }

    @Test
    fun passengerIdentityIsNotExportedAndSegmentsAreAggregated() {
        val payload = buildBlaBlaOperationalPayload0737(
            trip(
                passengers = listOf(
                    BlaBlaCollectorPassenger(name = "Pessoa A", seats = 1, boarding = "A", dropoff = "B"),
                    BlaBlaCollectorPassenger(name = "Pessoa B", seats = 2, boarding = "A", dropoff = "B"),
                    BlaBlaCollectorPassenger(name = "Pessoa C", seats = 1, boarding = "B", dropoff = "C"),
                ),
            ),
            expectedProfileUuid = "7371f028-9c55-4903-8444-308015823efd",
            expectedTripId = "01a0359e-de23-7a2b-ab27-43990c399a74",
        )
        assertEquals("COMPLETE", payload.result)
        assertEquals(3, payload.passengerCount)
        assertEquals(
            listOf(
                BlaBlaOperationalSegment0737("A", "B", 3),
                BlaBlaOperationalSegment0737("B", "C", 1),
            ),
            payload.segments,
        )
        val raw = Json.encodeToString(payload)
        assertFalse(raw.contains("Pessoa A"))
        assertFalse(raw.contains("Pessoa B"))
        assertFalse(raw.contains("Pessoa C"))
        assertFalse(raw.contains("phone", ignoreCase = true))
        assertFalse(raw.contains("booking_href", ignoreCase = true))
    }

    @Test
    fun incompleteCaptureStaysPartial() {
        val payload = buildBlaBlaOperationalPayload0737(
            trip(rosterComplete = false),
            expectedProfileUuid = "7371f028-9c55-4903-8444-308015823efd",
            expectedTripId = "01a0359e-de23-7a2b-ab27-43990c399a74",
            errorCode = "HTML_TARGET_OPERATIONALLY_INCOMPLETE_0615",
        )
        assertEquals("PARTIAL", payload.result)
        assertFalse(payload.passengerRosterComplete)
        assertEquals("HTML_TARGET_OPERATIONALLY_INCOMPLETE_0615", payload.errorCode)
    }

    @Test
    fun callbackTokenAndIdsAreStrict() {
        assertEquals(
            "01a0359e-de23-7a2b-ab27-43990c399a74",
            normalizeOperationalRemoteUuid0737("01a0359e-de23-7a2b-ab27-43990c399a74"),
        )
        assertNull(normalizeOperationalRemoteUuid0737("trip"))
        assertEquals(
            "AbCd_0123456789-AbCd_0123456789-AbCd",
            normalizeOperationalCallbackToken0737("AbCd_0123456789-AbCd_0123456789-AbCd"),
        )
        assertNull(normalizeOperationalCallbackToken0737("curto"))
    }
}
