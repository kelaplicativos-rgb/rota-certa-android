package br.com.mapeiaia.rotacerta.trips

import br.com.mapeiaia.rotacerta.AppSettings
import br.com.mapeiaia.rotacerta.Coordinate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PassengerTimeline0255RegressionTest {
    @Test
    fun explicitDirectionReferenceWinsOverHomeFallback() {
        val explicit = TripReferenceOrigin(10.0, 20.0, capturedAtMillis = 1L, radiusKm = 7.0)
        val settings = AppSettings(homeCoordinate = Coordinate(-23.0, -46.0), homeRadiusKm = 15.0)
        val result = timelineDirectionReference(explicit, settings)
        assertEquals(Coordinate(10.0, 20.0), result.coordinate)
        assertEquals(7.0, result.radiusKm)
    }

    @Test
    fun homeCoordinateIsSafeFallbackWhenExplicitReferenceIsAbsent() {
        val home = Coordinate(-23.0, -46.0)
        val result = timelineDirectionReference(null, AppSettings(homeCoordinate = home, homeRadiusKm = 12.0))
        assertEquals(home, result.coordinate)
        assertEquals(12.0, result.radiusKm)
    }

    @Test
    fun missingCoordinatesNeverInventDirectionReference() {
        val result = timelineDirectionReference(null, AppSettings())
        assertNull(result.coordinate)
    }

    @Test
    fun externalPassengerTargetRequiresCanonicalProfileAndExactBookingHref() {
        val row = row(2).copy(
            externalProfileUuid = " 7371F028-9C55-4903-8444-308015823EFD ",
            externalBookingHref = "https://www.blablacar.com.br/rides/offer/passenger/abc",
        )
        val target = externalPassengerTarget(row)!!
        assertEquals("7371f028-9c55-4903-8444-308015823efd", target.profileUuid)
        assertEquals("https://www.blablacar.com.br/rides/offer/passenger/abc", target.href)
        assertNull(externalPassengerTarget(row.copy(externalBookingHref = "https://www.blablacar.com.br/rides/offer/abc")))
    }

    @Test
    fun moneyActionIsOnlyTheMoneyEmoji() {
        assertEquals("💰", PASSENGER_FARE_ACTION_LABEL)
        assertTrue('?' !in PASSENGER_FARE_ACTION_LABEL)
    }

    @Test
    fun trustedGpsProgressCreatesReviewActionWithoutClaimingBoarding() {
        val rows = listOf(row(3, "Depois"), row(1, "Passou"), row(2, "Agora"))
        val progress = TripRouteProgress(stopIndexProgress = 2.2, corridorDistanceKm = 0.1)
        val ordered = passengerTimelineOperationalOrder(rows, progress)
        assertEquals("Passou", ordered.first().name)
        val next = passengerTimelineNextActionIndex(ordered, progress)
        assertEquals("⚠ EMBARQUE A CONFERIR", passengerTimelineActionLabel(ordered.first(), 0, next, progress))
    }

    @Test
    fun currencyEvidenceIsGlobalAndNeverDefaultsToBrl() {
        assertEquals("USD", normalizePassengerFareCurrency("usd"))
        assertEquals("EUR", resolvePassengerFareCurrency(null, "eur"))
        assertNull(normalizePassengerFareCurrency("R$"))
        assertNull(normalizePassengerFareCurrency("US"))
        assertNull(resolvePassengerFareCurrency(null, null))
    }

    private fun row(stop: Int, name: String = "Passageiro") = EnhancedPassengerCardRow(
        name = name,
        phone = null,
        seats = 1,
        boarding = "Origem",
        dropoff = "Destino",
        sources = setOf(BookingSource.BLABLACAR),
        boardingStopIndex = stop,
        bookingStatus = BookingStatus.CONFIRMED,
    )
}
