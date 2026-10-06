package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlaBlaRemoteTripQuery0737Test {
    @Test
    fun normalizaJobUuid() {
        val id = "7371f028-9c55-4903-8444-308015823efd"
        assertEquals(id, normalizeBlaBlaRemoteTripQueryJobId0737(id))
        assertNull(normalizeBlaBlaRemoteTripQueryJobId0737("nao-e-uuid"))
    }

    @Test
    fun snapshotRemotoPreservaOcupacaoSemTelefoneNemBookingHref() {
        val source = BlaBlaCollectorTrip(
            profile_uuid = "7371f028-9c55-4903-8444-308015823efd",
            date = "2026-11-08",
            departure_time = "10:30",
            arrival_time = "15:30",
            actual_departure = "São Paulo",
            actual_arrival = "São Tomé das Letras",
            trip_id = "01a10f40-5046-7e0f-a0f1-084aaeb436e9",
            passengers = listOf(
                BlaBlaCollectorPassenger(
                    name = "Pessoa",
                    seats = 2,
                    boarding = "São Paulo",
                    dropoff = "Pouso Alegre",
                    phone = "+5511999999999",
                    booking_href = "https://www.blablacar.com.br/private",
                ),
            ),
            booked_seats = 2,
            published_seats = 4,
            passenger_roster_complete = true,
            itinerary_stops = listOf("São Paulo", "Pouso Alegre", "São Tomé das Letras"),
            itinerary_stop_times = listOf("10:30", "13:00", "15:30"),
            itinerary_authoritative = true,
        )
        val snapshot = toBlaBlaRemoteTripSnapshot0737(
            result = BlaBlaTargetedHtmlRefreshResult0607(
                trip = source,
                operationalComplete = true,
            ),
            expectedProfileUuid = source.profile_uuid,
            expectedTripId = source.trip_id.orEmpty(),
        )
        assertNotNull(snapshot)
        assertEquals(1, snapshot.passengerCount)
        assertEquals(2, snapshot.passengerSeatCount)
        assertEquals(4, snapshot.publishedSeats)
        assertEquals("COMPLETE", blaBlaRemoteTripQueryResultState0737(snapshot))
        val passenger = snapshot.passengers.single()
        assertEquals("Pessoa", passenger.name)
        assertEquals("São Paulo", passenger.boarding)
        assertFalse(passenger.toString().contains("5511999999999"))
        assertFalse(passenger.toString().contains("private"))
    }

    @Test
    fun partialContinuaNaoDecisivo() {
        val snapshot = BlaBlaRemoteTripSnapshot0737(
            profileUuid = "7371f028-9c55-4903-8444-308015823efd",
            tripId = "01a10f40-5046-7e0f-a0f1-084aaeb436e9",
            passengerRosterComplete = true,
            itineraryAuthoritative = true,
            operationalComplete = false,
            publishedSeats = 4,
        )
        assertEquals("PARTIAL", blaBlaRemoteTripQueryResultState0737(snapshot))
    }

    @Test
    fun identidadeDivergenteNuncaGeraSnapshot() {
        val source = BlaBlaCollectorTrip(
            profile_uuid = "7371f028-9c55-4903-8444-308015823efd",
            date = "2026-11-08",
            trip_id = "01a10f40-5046-7e0f-a0f1-084aaeb436e9",
        )
        assertNull(
            toBlaBlaRemoteTripSnapshot0737(
                result = BlaBlaTargetedHtmlRefreshResult0607(trip = source),
                expectedProfileUuid = "175a7068-50d8-40c3-a27a-214b9c6e0461",
                expectedTripId = source.trip_id.orEmpty(),
            ),
        )
    }

    @Test
    fun tripIdRemotoNaoAceitaBarraNemUrl() {
        assertTrue(normalizeBlaBlaRemoteTripId0737("01a10f40-5046-7e0f-a0f1-084aaeb436e9") != null)
        assertNull(normalizeBlaBlaRemoteTripId0737("../rides/offer"))
        assertNull(normalizeBlaBlaRemoteTripId0737("https://example.com"))
    }
}
