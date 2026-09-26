package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlaBlaPassengerDeepCapture0653Test {
    @Test
    fun directBookingHrefWinsOverCardPlaceholder() {
        val passenger = BlaBlaCollectorPassenger(
            name = "Bruna",
            seats = 2,
            booking_href = "https://www.blablacar.com.br/rides/offer/passenger/7371f028-9c55-4903-8444-308015823efd/0?id=trip-123",
        )

        val target = passengerDeepCaptureTarget0653(
            passenger = passenger,
            passengerIndex = 0,
            passengerHrefs = listOf("rotacerta-card:0"),
        )

        assertEquals(passenger.booking_href, target)
    }

    @Test
    fun cardPlaceholderIsUsedOnlyForTheSameRosterIndex() {
        val passenger = BlaBlaCollectorPassenger(name = "Aline", seats = 1)

        assertEquals(
            "rotacerta-card:1",
            passengerDeepCaptureTarget0653(
                passenger = passenger,
                passengerIndex = 1,
                passengerHrefs = listOf("rotacerta-card:0", "rotacerta-card:1"),
            ),
        )
        assertNull(
            passengerDeepCaptureTarget0653(
                passenger = passenger,
                passengerIndex = 2,
                passengerHrefs = listOf("rotacerta-card:0", "rotacerta-card:1"),
            ),
        )
    }

    @Test
    fun tripCannotBeCompleteUntilEveryPassengerPageIsResolved() {
        assertTrue(passengerDeepCaptureComplete0653(expectedPassengers = 0, resolvedPassengers = 0))
        assertTrue(passengerDeepCaptureComplete0653(expectedPassengers = 3, resolvedPassengers = 3))
        assertFalse(passengerDeepCaptureComplete0653(expectedPassengers = 3, resolvedPassengers = 2))
        assertFalse(passengerDeepCaptureComplete0653(expectedPassengers = 1, resolvedPassengers = 0))
    }

    @Test
    fun privatePassengerEvidenceRequiresPhoneFareAndBothSegmentEndpoints() {
        assertTrue(
            passengerPrivateEvidenceNeedsRetry0657(
                phone = null,
                fareMinorUnits = null,
                boardingSegment = "",
                dropoffSegment = "",
            ),
        )
        assertTrue(
            passengerPrivateEvidenceNeedsRetry0657(
                phone = "+5511999999999",
                fareMinorUnits = null,
                boardingSegment = "São Paulo",
                dropoffSegment = "São Tomé das Letras",
            ),
        )
        assertTrue(
            passengerPrivateEvidenceNeedsRetry0657(
                phone = null,
                fareMinorUnits = 10_600L,
                boardingSegment = "São Paulo",
                dropoffSegment = "São Tomé das Letras",
            ),
        )
        assertTrue(
            passengerPrivateEvidenceNeedsRetry0657(
                phone = "+5511999999999",
                fareMinorUnits = 10_600L,
                boardingSegment = "São Paulo",
                dropoffSegment = "",
            ),
        )
        assertFalse(
            passengerPrivateEvidenceNeedsRetry0657(
                phone = "+5511999999999",
                fareMinorUnits = 10_600L,
                boardingSegment = "São Paulo",
                dropoffSegment = "São Tomé das Letras",
            ),
        )
    }

    @Test
    fun passengerSegmentJoinsOnlyOneAuthoritativeTripMapStop() {
        val stops = listOf(
            BlaBlaTripStopLocation0659(
                label = "São Paulo",
                address = "Av. Radial Leste - Tatuapé, São Paulo - SP",
                latitude = -23.534088,
                longitude = -46.543105,
            ),
            BlaBlaTripStopLocation0659(
                label = "São Tomé das Letras",
                address = "Estrada Sao Thome x Sobradinho km 17 Rural, São Tomé das Letras - MG",
                latitude = -21.656621,
                longitude = -44.893860,
            ),
        )

        val boarding = passengerTripStopLocation0659("São Paulo", stops)
        val dropoff = passengerTripStopLocation0659("São Tomé das Letras", stops)

        assertEquals("Av. Radial Leste - Tatuapé, São Paulo - SP", boarding?.address)
        assertEquals(-23.534088, boarding?.latitude ?: 0.0, 0.000001)
        assertEquals("Estrada Sao Thome x Sobradinho km 17 Rural, São Tomé das Letras - MG", dropoff?.address)
        assertEquals(-21.656621, dropoff?.latitude ?: 0.0, 0.000001)
        assertTrue(
            passengerOperationalEvidenceComplete0659(
                phone = "+5511999999999",
                fareMinorUnits = 10_600L,
                boardingSegment = "São Paulo",
                dropoffSegment = "São Tomé das Letras",
                boardingStop = boarding,
                dropoffStop = dropoff,
            ),
        )
        assertNull(
            passengerTripStopLocation0659(
                "São Paulo",
                stops + stops.first().copy(address = "Outro endereço"),
            ),
        )
    }

    @Test
    fun passengerPageMustRemainBoundToTheExactTrip() {
        val url = "https://www.blablacar.com.br/rides/offer/passenger/7371f028-9c55-4903-8444-308015823efd/0?id=trip-123"
        assertTrue(passengerPageBelongsToTrip0653(url, "trip-123"))
        assertFalse(passengerPageBelongsToTrip0653(url, "trip-999"))
        assertFalse(passengerPageBelongsToTrip0653("https://www.blablacar.com.br/rides/offer/trip-123", "trip-123"))
    }

    @Test
    fun brazilianFareParsingProducesMinorUnitsWithoutGuessingMissingFare() {
        assertEquals(
            18_650L,
            parsePassengerFareMinorUnits0653(listOf("", "R$ 186,50", "R$ 190,00")),
        )
        assertEquals(
            93_00L,
            parsePassengerFareMinorUnits0653(listOf("R$ 93")),
        )
        assertEquals(
            123_456L,
            parsePassengerFareMinorUnits0653(listOf("R$ 1.234,56")),
        )
        assertEquals(
            8_800L,
            parsePassengerFareMinorUnits0653(listOf("88 R$")),
        )
        assertEquals(
            123_456L,
            parsePassengerFareMinorUnits0653(listOf("1.234,56\u00A0R$")),
        )
        assertNull(parsePassengerFareMinorUnits0653(listOf("", null, "sem valor")))
        assertEquals("BRL", passengerFareCurrency0653("", listOf("R$ 93")))
        assertEquals("BRL", passengerFareCurrency0653("brl", emptyList()))
    }
}
