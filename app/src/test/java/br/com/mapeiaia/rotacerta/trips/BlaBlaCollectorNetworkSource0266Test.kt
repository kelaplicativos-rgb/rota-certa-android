package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlaBlaCollectorNetworkSource0266Test {
    private val tripId = "01a01b38-b772-7349-933e-c3c22ba55858"

    private fun booking(
        passengerId: String = "707f1140-541a-11ea-a000-008600cf444c",
        name: String = "Passageira da fonte",
        amount: String = "89.00",
        currency: String = "BRL",
    ): BlaBlaNetworkBookingSourceEvidence = BlaBlaNetworkBookingSourceEvidence(
        passengerId = passengerId,
        passengerName = name,
        seats = 2,
        phone = "+55 (11) 99999-0000",
        fareAmount = amount,
        fareCurrencyCode = currency,
        fareFormatted = "R$ 89,00",
        pickup = BlaBlaNetworkWaypointSourceEvidence(
            label = "São Paulo",
            address = "Terminal de origem",
            latitude = -23.55,
            longitude = -46.63,
        ),
        dropoff = BlaBlaNetworkWaypointSourceEvidence(
            label = "Campinas",
            address = "Terminal de destino",
            latitude = -22.90,
            longitude = -47.06,
        ),
    )

    @Test
    fun exactCompleteSourceCreatesIndividualPassengerShortcutAndMetadata() {
        val source = BlaBlaNetworkTripSourceEvidence(
            tripId = tripId,
            bookingsComplete = true,
            bookings = listOf(booking()),
        )

        val resolved = BlaBlaCollectorNetworkSourceModule.resolve(tripId, source)!!
        val row = resolved.bookings.single()

        assertEquals("Passageira da fonte", row.passenger.name)
        assertEquals(2, row.passenger.seats)
        assertEquals("+5511999990000", row.passenger.phone)
        assertEquals("São Paulo", row.passenger.boarding)
        assertEquals("Campinas", row.passenger.dropoff)
        assertEquals(8_900L, row.fareMinorUnits)
        assertEquals("BRL", row.fareCurrencyCode)
        assertEquals("Terminal de origem", row.boardingAddress)
        assertEquals(
            "https://www.blablacar.com.br/rides/offer/passenger/707f1140-541a-11ea-a000-008600cf444c/0?id=$tripId",
            row.passenger.booking_href,
        )
        assertEquals(tripId, BlaBlaCollectorUrlModule.tripId(row.passenger.booking_href))
        assertTrue(BlaBlaCollectorUrlModule.isPassenger(row.passenger.booking_href))
    }

    @Test
    fun exactNetworkWaypointsBecomeAuthoritativeOrderedItinerary() {
        val source = BlaBlaNetworkTripSourceEvidence(
            tripId = tripId,
            bookingsComplete = true,
            bookings = listOf(booking()),
            waypointsComplete = true,
            waypoints = listOf(
                BlaBlaNetworkWaypointSourceEvidence(label = "Santo André"),
                BlaBlaNetworkWaypointSourceEvidence(label = "Extrema"),
                BlaBlaNetworkWaypointSourceEvidence(label = "Pouso Alegre"),
                BlaBlaNetworkWaypointSourceEvidence(label = "São Thomé das Letras"),
            ),
        )

        val resolved = BlaBlaCollectorNetworkSourceModule.resolve(tripId, source)!!

        assertTrue(resolved.itineraryAuthoritative)
        assertEquals(
            listOf("Santo André", "Extrema", "Pouso Alegre", "São Thomé das Letras"),
            resolved.itineraryStops,
        )
    }

    @Test
    fun canonicalApiAmountDoesNotDependOnBrazilianLocaleGrouping() {
        assertEquals(8_900L, BlaBlaCollectorNetworkSourceModule.parseCanonicalMinorUnits("89.00", "BRL"))
        assertEquals(5_200L, BlaBlaCollectorNetworkSourceModule.parseCanonicalMinorUnits("52.00", "BRL"))
        assertNull(BlaBlaCollectorNetworkSourceModule.parseCanonicalMinorUnits("89,00", "BRL"))
        assertNull(BlaBlaCollectorNetworkSourceModule.parseCanonicalMinorUnits("89.001", "BRL"))
    }

    @Test
    fun mismatchedOrIncompleteTripSourceIsRejectedFailClosed() {
        assertNull(
            BlaBlaCollectorNetworkSourceModule.resolve(
                tripId,
                BlaBlaNetworkTripSourceEvidence(
                    tripId = "01a019ed-120e-7b4d-a542-46fc33f7e8d4",
                    bookingsComplete = true,
                    bookings = listOf(booking()),
                ),
            ),
        )
        assertNull(
            BlaBlaCollectorNetworkSourceModule.resolve(
                tripId,
                BlaBlaNetworkTripSourceEvidence(
                    tripId = tripId,
                    bookingsComplete = false,
                    bookings = listOf(booking()),
                ),
            ),
        )
    }

    @Test
    fun completeEmptyBookingsAreExplicitlyAuthoritative() {
        val resolved = BlaBlaCollectorNetworkSourceModule.resolve(
            tripId,
            BlaBlaNetworkTripSourceEvidence(tripId = tripId, bookingsComplete = true),
        )!!

        assertTrue(resolved.explicitEmpty)
        assertTrue(resolved.passengers.isEmpty())
    }

    @Test
    fun verifiedSessionDoesNotTreatPassengerUuidAsDriverUuid() {
        val passengerProfile =
            "https://www.blablacar.com.br/profile/707f1140-541a-11ea-a000-008600cf444c"
        val expectedDriver = "7371f028-9c55-4903-8444-308015823efd"

        assertTrue(
            BlaBlaCollectorIdentityModule.trustedDriverProfileLinks(
                expectedUuid = expectedDriver,
                authenticatedProfileSessionVerified = true,
                observedLinks = listOf(passengerProfile),
            ).isEmpty(),
        )
        assertEquals(
            listOf(passengerProfile),
            BlaBlaCollectorIdentityModule.trustedDriverProfileLinks(
                expectedUuid = expectedDriver,
                authenticatedProfileSessionVerified = false,
                observedLinks = listOf(passengerProfile),
            ),
        )
    }
}
