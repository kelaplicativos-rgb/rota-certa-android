package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PassengerCardActions0257Test {
    @Test
    fun tripTargetRequiresCanonicalProfileAndRideHref() {
        val uuid = "7371F028-9C55-4903-8444-308015823EFD"
        val target = externalTripTarget(
            uuid,
            "https://www.blablacar.com.br/rides/offer/abc&search_uuid=x",
        )!!
        assertEquals("7371f028-9c55-4903-8444-308015823efd", target.profileUuid)
        assertEquals("https://www.blablacar.com.br/rides/offer/abc", target.href)
        assertNull(externalTripTarget("not-a-uuid", target.href))
        assertNull(externalTripTarget(uuid, "https://example.com/rides/offer/abc"))
    }

    @Test
    fun pickupMapsPrefersExactReservationAddress() {
        val row = row().copy(
            boarding = "São Paulo",
            boardingAddress = "Av. Cruzeiro do Sul, 1230, São Paulo",
        )
        assertEquals(
            "Av. Cruzeiro do Sul, 1230, São Paulo",
            passengerPickupMapTarget(row)?.query,
        )
    }

    @Test
    fun pickupMapsFallsBackToCollectedBoardingWithoutInventing() {
        assertEquals(
            "Três Corações",
            passengerPickupMapTarget(row().copy(boarding = "Três Corações"))?.query,
        )
        assertNull(passengerPickupMapTarget(row().copy(boarding = null, boardingAddress = "")))
    }

    @Test
    fun operationalRouteOrderingRemainsAvailableWithoutVisualNextAction() {
        val rows = listOf(row(3, "Depois"), row(1, "Primeiro"), row(2, "Segundo"))
        val progress = TripRouteProgress(stopIndexProgress = 0.2, corridorDistanceKm = 0.1)
        val ordered = passengerTimelineOperationalOrder(rows, progress)
        assertEquals(listOf("Primeiro", "Segundo", "Depois"), ordered.map { it.name })
    }

    private fun row(stop: Int? = null, name: String = "Passageiro") = EnhancedPassengerCardRow(
        name = name,
        phone = null,
        seats = 1,
        boarding = null,
        dropoff = "Destino",
        sources = setOf(BookingSource.BLABLACAR),
        boardingStopIndex = stop,
        bookingStatus = BookingStatus.CONFIRMED,
    )
}
