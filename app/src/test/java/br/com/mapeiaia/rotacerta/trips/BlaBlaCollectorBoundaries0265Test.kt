package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlaBlaCollectorBoundaries0265Test {
    private fun passenger(
        name: String = "Passenger A",
        phone: String? = null,
        bookingHref: String = "https://www.blablacar.com.br/rides/offer/passenger/booking-a/0?id=trip-a",
    ): BlaBlaCollectorPassenger = BlaBlaCollectorPassenger(
        name = name,
        seats = 1,
        boarding = "Origin",
        dropoff = "Destination",
        phone = phone,
        booking_href = bookingHref,
    )

    private fun trip(
        id: String,
        passengers: List<BlaBlaCollectorPassenger> = emptyList(),
        price: String? = null,
        rosterComplete: Boolean = true,
    ): BlaBlaCollectorTrip = BlaBlaCollectorTrip(
        profile_uuid = "7371f028-9c55-4903-8444-308015823efd",
        date = "2026-08-24",
        departure_time = "10:30",
        actual_departure = "Origin",
        actual_arrival = "Destination",
        price = price,
        trip_href = "https://www.blablacar.com.br/rides/offer?id=$id",
        trip_id = id,
        passengers = passengers,
        booked_seats = passengers.sumOf { it.seats.coerceAtLeast(1) },
        passenger_roster_complete = rosterComplete,
    )

    @Test
    fun exactCollectorOriginRejectsLookalikeAndForeignHosts() {
        assertTrue(BlaBlaCollectorUrlModule.isAllowed("https://www.blablacar.com.br/rides"))
        assertTrue(BlaBlaCollectorUrlModule.isAllowed("/rides"))
        assertFalse(BlaBlaCollectorUrlModule.isAllowed("https://www.blablacar.com.br.evil.invalid/rides"))
        assertFalse(BlaBlaCollectorUrlModule.isAllowed("https://evilblablacar.com.br/rides"))
        assertFalse(BlaBlaCollectorUrlModule.isAllowed("http://www.blablacar.com.br/rides"))
    }

    @Test
    fun canonicalUrlRemovesSearchUuidInAnyPositionAndFragment() {
        val first = BlaBlaCollectorUrlModule.canonical(
            "/rides/offer?search_uuid=noise&id=trip-a&source=CARPOOLING#passengers",
        )
        val middle = BlaBlaCollectorUrlModule.canonical(
            "https://www.blablacar.com.br/rides/offer?id=trip-a&search_uuid=noise&source=CARPOOLING#passengers",
        )

        assertEquals(
            "https://www.blablacar.com.br/rides/offer?id=trip-a&source=CARPOOLING",
            first,
        )
        assertEquals(first, middle)
    }

    @Test
    fun tripAndPassengerIdentityUseTheSameUrlAuthority() {
        val passengerPage = "/rides/offer/passenger/booking-a/0?id=trip-a&search_uuid=noise"

        assertEquals("trip-a", BlaBlaCollectorUrlModule.tripId("/rides/offer?id=trip-a"))
        assertEquals("booking-a", BlaBlaCollectorUrlModule.passengerIdentityKey(passengerPage))
        assertTrue(
            BlaBlaCollectorUrlModule.samePassengerPage(
                passengerPage,
                "https://www.blablacar.com.br/rides/offer/passenger/booking-a/0?id=trip-a",
            ),
        )
        assertNull(BlaBlaCollectorUrlModule.tripId("https://example.invalid/rides/offer?id=trip-a"))
    }

    @Test
    fun manageTargetAcceptsPassengerPageWithoutWeakeningOriginBoundary() {
        assertTrue(
            BlaBlaCollectorUrlModule.isManageTarget(
                "https://www.blablacar.com.br/rides/offer/passenger/booking-a/0?id=trip-a",
            ),
        )
        assertTrue(BlaBlaCollectorUrlModule.isManageTarget("/rides/offer?id=trip-a"))
        assertFalse(BlaBlaCollectorUrlModule.isManageTarget("https://www.blablacar.com.br/rides"))
        assertFalse(BlaBlaCollectorUrlModule.isManageTarget("/rides/offer/edit/trip-a"))
        assertFalse(
            BlaBlaCollectorUrlModule.isManageTarget(
                "https://www.blablacar.com.br.evil.invalid/rides/offer/passenger/booking-a/0?id=trip-a",
            ),
        )
    }

    @Test
    fun phoneNormalizationHasOneCollectorAuthority() {
        assertEquals("+5511999999999", BlaBlaCollectorPassengerModule.normalizePhone("+55 (11) 99999-9999"))
        assertEquals("5511999999999", BlaBlaCollectorPassengerModule.normalizePhone("55 11 99999-9999"))
        assertNull(BlaBlaCollectorPassengerModule.normalizePhone("123"))
    }

    @Test
    fun staleHarvestCannotEraseIndividualPassengerActionsOrNewerCards() {
        val confirmedPassenger = passenger(phone = "+5511999999999")
        val latest = listOf(
            trip("trip-a", passengers = listOf(confirmedPassenger), price = "R$ 42"),
            trip("trip-b", passengers = listOf(passenger(name = "Passenger B"))),
        )
        val staleHarvest = listOf(
            trip("trip-a", passengers = emptyList(), price = "R$ 10", rosterComplete = true),
            trip("trip-removed", passengers = listOf(passenger(name = "Stale"))),
        )

        val merged = BlaBlaCollectorSessionModule.mergeHarvestTrips(latest, staleHarvest)

        assertEquals(listOf("trip-a", "trip-b"), merged.trips.map { it.trip_id })
        assertEquals("R$ 42", merged.trips.first().price)
        assertEquals(confirmedPassenger, merged.trips.first().passengers.single())
        assertEquals(1, merged.enrichedTrips)
        assertEquals(1, merged.ignoredStaleTrips)
    }

    @Test
    fun harvestMayEnrichButNeverReplaceLatestDirectPassengerEvidence() {
        val bookingHref = "https://www.blablacar.com.br/rides/offer/passenger/booking-a/0?id=trip-a"
        val latestPassenger = passenger(phone = null, bookingHref = bookingHref)
        val enrichedPassenger = passenger(phone = "+5511888888888", bookingHref = bookingHref)

        val merged = BlaBlaCollectorSessionModule.mergeHarvestTrips(
            latest = listOf(trip("trip-a", listOf(latestPassenger))),
            harvested = listOf(trip("trip-a", listOf(enrichedPassenger))),
        ).trips.single()

        assertEquals("+5511888888888", merged.passengers.single().phone)
        assertEquals(bookingHref, merged.passengers.single().booking_href)
        assertTrue(merged.passenger_roster_complete)
    }
}
