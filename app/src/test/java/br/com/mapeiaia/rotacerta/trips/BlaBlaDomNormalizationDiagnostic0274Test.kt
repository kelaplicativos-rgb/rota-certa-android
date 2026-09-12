package br.com.mapeiaia.rotacerta.trips

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BlaBlaDomNormalizationDiagnostic0274Test {
    private val today = LocalDate.of(2026, 8, 24)
    private val account = BlaBlaAccountDefinition(
        slot = "test",
        label = "Conta teste",
        uuid = "7371f028-9c55-4903-8444-308015823efd",
        dataDirectorySuffix = "test",
    )

    @Test
    fun acceptedTripKeepsLegacyBehavior() {
        val candidate = candidate()
        val detail = detail()
        val result = BlaBlaDomNormalizer.diagnoseTrip(account, candidate, detail, today, true)

        assertNull(result.rejectionReason)
        assertNotNull(result.trip)
        assertEquals(
            result.trip,
            BlaBlaDomNormalizer.toTrip(account, candidate, detail, today, true),
        )
    }

    @Test
    fun reportsIdentityUnverified() {
        val result = BlaBlaDomNormalizer.diagnoseTrip(account, candidate(), detail(), today, false)
        assertRejected("identity_unverified", result)
    }

    @Test
    fun reportsDateUnparseable() {
        val result = BlaBlaDomNormalizer.diagnoseTrip(
            account,
            candidate(dateText = "", text = "sem data"),
            detail(dateText = "", bodyText = "sem data"),
            today,
            true,
        )
        assertRejected("date_unparseable", result)
    }

    @Test
    fun reportsDepartureTimeUnparseable() {
        val result = BlaBlaDomNormalizer.diagnoseTrip(
            account,
            candidate(departureTime = "", text = "sem horario"),
            detail(departureTime = ""),
            today,
            true,
        )
        assertRejected("departure_time_unparseable", result)
    }

    @Test
    fun reportsOriginMissing() {
        val result = BlaBlaDomNormalizer.diagnoseTrip(
            account,
            candidate(origin = ""),
            detail(origin = ""),
            today,
            true,
        )
        assertRejected("origin_missing", result)
    }

    @Test
    fun reportsDestinationMissing() {
        val result = BlaBlaDomNormalizer.diagnoseTrip(
            account,
            candidate(destination = ""),
            detail(destination = ""),
            today,
            true,
        )
        assertRejected("destination_missing", result)
    }

    @Test
    fun reportsTripIdMismatch() {
        val result = BlaBlaDomNormalizer.diagnoseTrip(
            account,
            candidate(href = "https://www.blablacar.com.br/rides?id=trip-a"),
            detail(url = "https://www.blablacar.com.br/rides?id=trip-b"),
            today,
            true,
        )
        assertRejected("trip_id_mismatch", result)
    }

    private fun assertRejected(reason: String, result: BlaBlaDomNormalizationResult) {
        assertEquals(reason, result.rejectionReason)
        assertNull(result.trip)
    }

    private fun candidate(
        href: String = "https://www.blablacar.com.br/rides?id=trip-a",
        text: String = "25/08/2026 10:30 14:30",
        departureTime: String = "10:30",
        origin: String = "Origem",
        destination: String = "Destino",
        dateText: String = "25/08/2026",
    ) = BlaBlaDomRideCandidate(
        href = href,
        text = text,
        departureTime = departureTime,
        arrivalTime = "14:30",
        origin = origin,
        destination = destination,
        dateText = dateText,
    )

    private fun detail(
        url: String = "https://www.blablacar.com.br/rides?id=trip-a",
        bodyText: String = "25/08/2026 10:30 14:30",
        dateText: String = "25/08/2026",
        departureTime: String = "10:30",
        origin: String = "Origem",
        destination: String = "Destino",
    ) = BlaBlaDomTripDetail(
        url = url,
        bodyText = bodyText,
        dateText = dateText,
        departureTime = departureTime,
        arrivalTime = "14:30",
        origin = origin,
        destination = destination,
    )
}
