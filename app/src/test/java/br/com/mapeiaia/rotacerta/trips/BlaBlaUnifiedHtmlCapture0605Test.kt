package br.com.mapeiaia.rotacerta.trips

import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
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

    @Test
    fun deterministicSeatOptionsUrlKeepsStrongTripIdentity() {
        val tripId = "01a0886e-44ee-7901-95e5-b69582171242"
        val options = "https://www.blablacar.com.br/rides/offer/edit/$tripId/options"
        assertEquals(tripId, BlaBlaCollectorUrlModule.optionsTripId(options))
    }

    @Test
    fun tripDetailScriptContainsCurrentDomEvidenceFallbacks() {
        val candidates = listOf(
            File("src/main/assets/blablacar/scripts/trip_detail.js"),
            File("app/src/main/assets/blablacar/scripts/trip_detail.js"),
        )
        val script = candidates.firstOrNull(File::isFile)?.readText().orEmpty()
        assertTrue(script.isNotBlank())
        assertTrue(script.contains("strongPassengerLinks"))
        assertTrue(script.contains("/rides/offer/map"))
        assertTrue(script.contains("fallbackItineraryStops"))
        assertTrue(script.contains("optionsHref"))
    }

    @Test
    fun backgroundNeverRequestsLegacyCollector0607() {
        assertFalse(agendaBackgroundSyncRequestsCollector0430("periodic"))
        assertFalse(agendaBackgroundSyncRequestsCollector0430("admin_update_now:manual"))
        assertFalse(agendaBackgroundSyncRequestsCollector0430("recovery"))
    }

    @Test
    fun legacyResponseCannotAuthorizeCanonicalTombstones0607() {
        val legacy = BlaBlaCollectorMonthResponse(
            status = "complete",
            coverage = BlaBlaCollectorCoverage(
                complete_for_scope = true,
                global_profile_month_complete = true,
            ),
        )
        assertFalse(externalCollectorAllowsTombstones0406(legacy))
    }

    @Test
    fun htmlResponseCanAuthorizeCompleteScope0607() {
        val html = BlaBlaCollectorMonthResponse(
            status = "complete",
            authority_source_0607 = BlaBlaAcquisitionAuthority0607.HTML_DIRECT,
            coverage = BlaBlaCollectorCoverage(
                complete_for_scope = true,
                global_profile_month_complete = true,
            ),
        )
        assertTrue(externalCollectorAllowsTombstones0406(html))
    }
}
