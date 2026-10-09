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
                coreOperationalComplete0737 = true,
                paymentEvidence0764 = listOf(
                    BlaBlaPassengerPaymentEvidence0764(
                        passengerTotalMinorUnits = 21200L,
                        driverReceivesMinorUnits = 18700L,
                        currencyCode = "BRL",
                    ),
                ),
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
        assertEquals(21200L, passenger.passengerTotalMinorUnits)
        assertEquals(18700L, passenger.driverReceivesMinorUnits)
        assertEquals("BRL", passenger.fareCurrencyCode)
        assertTrue(snapshot.individualFaresComplete)
        assertFalse(passenger.toString().contains("5511999999999"))
        assertFalse(passenger.toString().contains("private"))
    }


    @Test
    fun nuncaCalculaValorIndividualDividindoPrecoDoCard() {
        val source = BlaBlaCollectorTrip(
            profile_uuid = "7371f028-9c55-4903-8444-308015823efd",
            date = "2026-11-08",
            trip_id = "01a10f40-5046-7e0f-a0f1-084aaeb436e9",
            price = "R$ 110",
            passengers = listOf(
                BlaBlaCollectorPassenger(name = "Pessoa A", seats = 2, boarding = "São Paulo", dropoff = "Pouso Alegre"),
                BlaBlaCollectorPassenger(name = "Pessoa B", seats = 1, boarding = "Extrema", dropoff = "Três Corações"),
            ),
            booked_seats = 3,
            published_seats = 4,
            passenger_roster_complete = true,
            itinerary_authoritative = true,
        )
        val result = toBlaBlaRemoteTripSnapshot0737(
            BlaBlaTargetedHtmlRefreshResult0607(
                trip = source,
                operationalComplete = true,
                coreOperationalComplete0737 = true,
                paymentEvidence0764 = listOf(
                    BlaBlaPassengerPaymentEvidence0764(
                        passengerTotalMinorUnits = 15350,
                        driverReceivesMinorUnits = 13100,
                        currencyCode = "BRL",
                    ),
                    null,
                ),
            ),
            source.profile_uuid,
            source.trip_id.orEmpty(),
        )
        assertNotNull(result)
        assertEquals("R$ 110", result.price)
        assertEquals(15350L, result.passengers[0].passengerTotalMinorUnits)
        assertNull(result.passengers[1].passengerTotalMinorUnits)
        assertFalse(result.individualFaresComplete)
        assertEquals("PARTIAL", blaBlaRemoteTripQueryResultState0737(result))
    }

    @Test
    fun valorSemMoedaVerificadaNaoEPublicavel() {
        val source = BlaBlaCollectorTrip(
            profile_uuid = "7371f028-9c55-4903-8444-308015823efd",
            date = "2026-11-08",
            trip_id = "01a10f40-5046-7e0f-a0f1-084aaeb436e9",
            passengers = listOf(BlaBlaCollectorPassenger(name = "Pessoa", seats = 1)),
        )
        val result = toBlaBlaRemoteTripSnapshot0737(
            BlaBlaTargetedHtmlRefreshResult0607(
                trip = source,
                paymentEvidence0764 = listOf(
                    BlaBlaPassengerPaymentEvidence0764(passengerTotalMinorUnits = 20000, currencyCode = ""),
                ),
            ),
            source.profile_uuid,
            source.trip_id.orEmpty(),
        )
        assertNotNull(result)
        assertNull(result.passengers.single().passengerTotalMinorUnits)
        assertFalse(result.individualFaresComplete)
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

    @Test
    fun pollingDetalhadoSemFcmExigeJobValidoNovoENaoExpirado() {
        val now = System.currentTimeMillis()
        val job = StandaloneCoversPendingJob0758(
            pending = true,
            jobId = "7371f028-9c55-4903-8444-308015823efd",
            state = "PENDING_DEVICE",
            expiresAtMillis = now + 60_000L,
        )
        assertTrue(shouldOfferRemoteTripConsent0760(job, "", now))
        assertFalse(shouldOfferRemoteTripConsent0760(job, job.jobId, now))
        assertFalse(shouldOfferRemoteTripConsent0760(job.copy(pending = false), "", now))
        assertFalse(shouldOfferRemoteTripConsent0760(job.copy(expiresAtMillis = now), "", now))
        assertFalse(shouldOfferRemoteTripConsent0760(job.copy(jobId = "invalid"), "", now))
    }
}
