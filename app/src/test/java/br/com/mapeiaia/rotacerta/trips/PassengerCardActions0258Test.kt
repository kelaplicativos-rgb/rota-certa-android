package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PassengerCardActions0258Test {
    private val uuid = "7371f028-9c55-4903-8444-308015823efd"

    @Test
    fun tripTargetAcceptsStrongQueryIdOfferAndRemovesSearchUuid() {
        val target = externalTripTarget(
            uuid,
            "https://www.blablacar.com.br/rides/offer?id=trip-123&search_uuid=volatile",
        )
        assertNotNull(target)
        assertEquals(uuid, target.profileUuid)
        assertEquals("https://www.blablacar.com.br/rides/offer?id=trip-123", target.href)
    }

    @Test
    fun tripTargetAcceptsStrongQueryIdTrip() {
        val target = externalTripTarget(uuid, "https://www.blablacar.com.br/trip?id=trip-456")
        assertNotNull(target)
        assertEquals("https://www.blablacar.com.br/trip?id=trip-456", target.href)
    }

    @Test
    fun tripTargetRejectsGenericPageWithoutStrongIdentity() {
        assertNull(externalTripTarget(uuid, "https://www.blablacar.com.br/rides/offer"))
        assertNull(externalTripTarget(uuid, "https://www.blablacar.com.br/trip"))
        assertNull(externalTripTarget(uuid, "https://www.blablacar.com.br/rides/offer/edit"))
    }

    @Test
    fun addressEditorPrefersSavedReservationAddress() {
        assertEquals(
            "Av. Industrial, 1000, Santo André",
            passengerAddressEditorInitialValue(
                " Av. Industrial, 1000, Santo André ",
                "Santo André",
            ),
        )
    }

    @Test
    fun addressEditorFallsBackToCollectedPlaceWithoutInventing() {
        assertEquals("Pouso Alegre", passengerAddressEditorInitialValue("", " Pouso Alegre "))
        assertEquals("", passengerAddressEditorInitialValue(null, null))
    }

    @Test
    fun externalTripEvidenceKeepsTripActionVisibleWhileHrefNeedsResync() {
        val entry = TripTimelineEntry(
            tripId = "x",
            profileId = uuid,
            profileLabel = "Conta",
            departureAtMillis = 1L,
            arrivalAtMillis = 2L,
            origin = "A",
            destination = "B",
            status = TripStatus.PUBLISHED,
            capacity = 4,
            minimumOccupiedSeats = 1,
            maximumOccupiedSeats = 1,
            sourcePassengerSeats = mapOf(BookingSource.BLABLACAR to 1),
        )
        assertTrue(hasExternalTripActionEvidence(entry))
    }
}
