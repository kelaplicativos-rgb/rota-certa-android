package br.com.mapeiaia.rotacerta.trips

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlaBlaUnifiedHtmlCapture0605Test {
    private fun ride(
        date: String,
        departure: String = "11:00",
        arrival: String = "14:00",
        status: String = "",
    ) = ParsedExternalRide0535(
        tripId = "trip-12345678",
        listPosition = 0,
        date = date,
        dateText = date,
        dateYearExplicit = true,
        dateResolution = "EXPLICIT_YEAR",
        departureTime = departure,
        arrivalTime = arrival,
        origin = "Origem",
        destination = "Destino",
        status = status,
        administrativeUrl = "https://www.blablacar.com.br/rides/offer?id=trip-12345678",
    )

    @Test
    fun futureDayIsCaptured() {
        assertTrue(
            shouldCaptureRide0605(
                ride("2026-09-22"),
                LocalDate.of(2026, 9, 21),
                LocalTime.of(12, 0),
            ),
        )
    }

    @Test
    fun inProgressRideUsesArrivalCutoff() {
        assertTrue(
            shouldCaptureRide0605(
                ride("2026-09-21", departure = "10:00", arrival = "14:00"),
                LocalDate.of(2026, 9, 21),
                LocalTime.of(12, 0),
            ),
        )
    }

    @Test
    fun expiredRideIsNotCaptured() {
        assertFalse(
            shouldCaptureRide0605(
                ride("2026-09-21", departure = "08:00", arrival = "10:00"),
                LocalDate.of(2026, 9, 21),
                LocalTime.of(12, 0),
            ),
        )
    }

    @Test
    fun cancelledRideIsNotCaptured() {
        assertFalse(
            shouldCaptureRide0605(
                ride("2026-09-22", status = "Cancelada"),
                LocalDate.of(2026, 9, 21),
                LocalTime.of(12, 0),
            ),
        )
    }
}
