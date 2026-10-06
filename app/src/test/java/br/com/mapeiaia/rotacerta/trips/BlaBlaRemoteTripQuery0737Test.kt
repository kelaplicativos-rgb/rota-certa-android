package br.com.mapeiaia.rotacerta.trips

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlaBlaRemoteTripQuery0737Test {
    private val profileUuid = "7371f028-9c55-4903-8444-308015823efd"
    private val tripId = "01a0359e-de23-7a2b-ab27-43990c399a74"

    @Test
    fun uuidNormalizationRejectsNamesAndKeepsCanonicalUuid() {
        assertEquals(profileUuid, normalizeRemoteTripQueryUuid0737(profileUuid))
        assertNull(normalizeRemoteTripQueryUuid0737("Ezequiel S"))
        assertNull(normalizeRemoteTripQueryUuid0737(""))
    }

    @Test
    fun hrefMustBeBoundToExactTripId() {
        val href = "https://www.blablacar.com.br/rides/offer/$tripId"
        assertNotNull(normalizeRemoteTripQueryHref0737(href, tripId))
        assertNull(
            normalizeRemoteTripQueryHref0737(
                "https://www.blablacar.com.br/rides/offer/01a0359e-de23-7a2b-ab27-43990c399a75",
                tripId,
            ),
        )
    }

    @Test
    fun completeProjectionContainsOperationalDataButNoPrivatePassengerFields() {
        val trip = BlaBlaCollectorTrip(
            profile_uuid = profileUuid,
            profile_name = "Perfil",
            date = "2026-11-08",
            departure_time = "10:30",
            arrival_time = "15:40",
            search_from = "São Paulo",
            search_to = "São Tomé das Letras",
            actual_departure = "São Paulo",
            actual_arrival = "São Tomé das Letras",
            price = "R$ 100",
            availability = "available",
            trip_id = tripId,
            passengers = listOf(
                BlaBlaCollectorPassenger(
                    name = "Pessoa A",
                    seats = 2,
                    boarding = "São Paulo",
                    dropoff = "Pouso Alegre",
                    phone = "+5511999999999",
                    booking_href = "https://www.blablacar.com.br/private/passenger",
                ),
            ),
            itinerary_stops = listOf("São Paulo", "Pouso Alegre", "São Tomé das Letras"),
            itinerary_stop_times = listOf("10:30", "13:20", "15:40"),
            itinerary_authoritative = true,
            booked_seats = 2,
            published_seats = 2,
            passenger_roster_complete = true,
        )
        val payload = buildBlaBlaRemoteTripQueryPayload0737(
            profileUuid = profileUuid,
            tripId = tripId,
            result = BlaBlaTargetedHtmlRefreshResult0607(
                trip = trip,
                operationalComplete = true,
                evidencePath = "private-evidence/hidden.html",
            ),
        )

        assertEquals("COMPLETE", payload.result)
        assertEquals(1, payload.passengers.size)
        assertEquals(2, payload.passengers.single().seats)
        assertTrue(payload.passengerRosterComplete)
        assertTrue(payload.itineraryAuthoritative)
        assertTrue(payload.evidencePresent)

        val encoded = Json { encodeDefaults = true }.encodeToString(payload)
        assertFalse(encoded.contains("+5511999999999"))
        assertFalse(encoded.contains("booking_href"))
        assertFalse(encoded.contains("private/passenger"))
        assertFalse(encoded.contains("private-evidence/hidden.html"))
    }

    @Test
    fun incompleteCaptureNeverBecomesComplete() {
        val trip = BlaBlaCollectorTrip(
            profile_uuid = profileUuid,
            date = "2026-11-08",
            trip_id = tripId,
            passenger_roster_complete = false,
            itinerary_authoritative = false,
            published_seats = null,
        )
        val payload = buildBlaBlaRemoteTripQueryPayload0737(
            profileUuid = profileUuid,
            tripId = tripId,
            result = BlaBlaTargetedHtmlRefreshResult0607(
                trip = trip,
                operationalComplete = false,
                errorCode = "MISSING_ROSTER",
                evidencePath = "private-evidence/partial.html",
            ),
        )

        assertEquals("PARTIAL", payload.result)
        assertFalse(payload.operationalComplete)
        assertEquals("MISSING_ROSTER", payload.errorCode)
    }
}
