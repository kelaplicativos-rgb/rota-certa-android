package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlaBlaHarvestAssociationTest {
    @Test
    fun passengerEvidenceKeyIsScopedByAccountTripAndPassenger() {
        val base = BlaBlaHarvestAssociation.passengerEvidenceKey("account-a", "trip-a", "passenger-a")
        assertNotEquals(base, BlaBlaHarvestAssociation.passengerEvidenceKey("account-b", "trip-a", "passenger-a"))
        assertNotEquals(base, BlaBlaHarvestAssociation.passengerEvidenceKey("account-a", "trip-b", "passenger-a"))
        assertNotEquals(base, BlaBlaHarvestAssociation.passengerEvidenceKey("account-a", "trip-a", "passenger-b"))
    }

    @Test
    fun tripPageMustMatchExpectedTripId() {
        assertTrue(
            BlaBlaHarvestAssociation.tripPageMatches(
                "trip-a",
                "https://www.blablacar.com.br/rides/offer/trip-a",
            ),
        )
        assertFalse(
            BlaBlaHarvestAssociation.tripPageMatches(
                "trip-a",
                "https://www.blablacar.com.br/rides/offer/trip-b",
            ),
        )
    }

    @Test
    fun editAndOptionsPagesMustMatchExpectedTripId() {
        assertTrue(
            BlaBlaHarvestAssociation.editPageMatches(
                "trip-a",
                "https://www.blablacar.com.br/rides/offer/edit/trip-a",
            ),
        )
        assertFalse(
            BlaBlaHarvestAssociation.editPageMatches(
                "trip-a",
                "https://www.blablacar.com.br/rides/offer/edit/trip-b",
            ),
        )
        assertTrue(
            BlaBlaHarvestAssociation.optionsPageMatches(
                "trip-a",
                "https://www.blablacar.com.br/rides/offer/edit/trip-a/options",
            ),
        )
        assertFalse(
            BlaBlaHarvestAssociation.optionsPageMatches(
                "trip-a",
                "https://www.blablacar.com.br/rides/offer/edit/trip-b/options",
            ),
        )
    }

    @Test
    fun passengerPageMustMatchExpectedPassengerKey() {
        assertTrue(
            BlaBlaHarvestAssociation.passengerPageMatches(
                "booking-a",
                "https://www.blablacar.com.br/rides/offer/passenger/booking-a",
            ),
        )
        assertFalse(
            BlaBlaHarvestAssociation.passengerPageMatches(
                "booking-a",
                "https://www.blablacar.com.br/rides/offer/passenger/booking-b",
            ),
        )
    }

    @Test
    fun ridesIdentityRejectsOtherAuthenticatedPages() {
        assertTrue(BlaBlaHarvestAssociation.ridesPageMatches("https://www.blablacar.com.br/rides"))
        assertFalse(BlaBlaHarvestAssociation.ridesPageMatches("https://www.blablacar.com.br/rides/offer/trip-a"))
    }
}
