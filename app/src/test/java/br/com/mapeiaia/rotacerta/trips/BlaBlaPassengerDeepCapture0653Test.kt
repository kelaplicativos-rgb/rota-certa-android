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
    fun blankPrivatePassengerEvidenceRequestsAnotherPagePass() {
        assertTrue(
            passengerPrivateEvidenceNeedsRetry0657(
                phone = null,
                fareMinorUnits = null,
                boardingAddress = "",
                dropoffAddress = "",
            ),
        )
        assertFalse(
            passengerPrivateEvidenceNeedsRetry0657(
                phone = "+5511999999999",
                fareMinorUnits = null,
                boardingAddress = "",
                dropoffAddress = "",
            ),
        )
        assertFalse(
            passengerPrivateEvidenceNeedsRetry0657(
                phone = null,
                fareMinorUnits = 9_300L,
                boardingAddress = "",
                dropoffAddress = "",
            ),
        )
        assertFalse(
            passengerPrivateEvidenceNeedsRetry0657(
                phone = null,
                fareMinorUnits = null,
                boardingAddress = "Rua Júlio Colaço, 73",
                dropoffAddress = "",
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
        assertNull(parsePassengerFareMinorUnits0653(listOf("", null, "sem valor")))
        assertEquals("BRL", passengerFareCurrency0653("", listOf("R$ 93")))
        assertEquals("BRL", passengerFareCurrency0653("brl", emptyList()))
    }
}
