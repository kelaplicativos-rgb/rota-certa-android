package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlaBlaHarvestEfficiency0259Test {
    @Test
    fun queryFormTripIdsAreDifferentNavigationIdentities() {
        val first = "https://www.blablacar.com.br/rides/offer?id=trip-a&source=CARPOOLING"
        val second = "https://www.blablacar.com.br/rides/offer?id=trip-b&source=CARPOOLING"
        assertFalse(BlaBlaHarvestNavigationIdentity.same(first, second))
    }

    @Test
    fun nonIdentityQueryNoiseDoesNotSplitSameTrip() {
        val first = "https://www.blablacar.com.br/rides/offer?id=trip-a&source=CARPOOLING"
        val second = "https://www.blablacar.com.br/rides/offer?id=trip-a&source=OTHER&search_uuid=noise"
        assertTrue(BlaBlaHarvestNavigationIdentity.same(first, second))
    }

    @Test
    fun passengerNavigationAlsoKeepsParentTripIdentity() {
        val first = "https://www.blablacar.com.br/rides/offer/passenger/booking-a/0?id=trip-a&source=CARPOOLING"
        val second = "https://www.blablacar.com.br/rides/offer/passenger/booking-a/0?id=trip-b&source=CARPOOLING"
        assertFalse(BlaBlaHarvestNavigationIdentity.same(first, second))
    }

    @Test
    fun nonCanonicalHostCannotAliasCanonicalTripIdentity() {
        val canonical = "https://www.blablacar.com.br/rides/offer?id=trip-a"
        val foreign = "https://example.invalid/rides/offer?id=trip-a"
        assertFalse(BlaBlaHarvestNavigationIdentity.same(canonical, foreign))
    }

    @Test
    fun editAndOptionsFormsAreClassifiedAsNonAutomaticSeatWork() {
        assertTrue(
            BlaBlaHarvestNavigationIdentity.isEditOrOptionsHref(
                "https://www.blablacar.com.br/rides/offer/edit/trip-a",
            ),
        )
        assertTrue(
            BlaBlaHarvestNavigationIdentity.isEditOrOptionsHref(
                "https://www.blablacar.com.br/rides/offer/edit/trip-a/options",
            ),
        )
        assertTrue(
            BlaBlaHarvestNavigationIdentity.isEditOrOptionsHref(
                "https://www.blablacar.com.br/rides/offer/edit?id=trip-a",
            ),
        )
        assertFalse(
            BlaBlaHarvestNavigationIdentity.isEditOrOptionsHref(
                "https://www.blablacar.com.br/rides/offer?id=trip-a",
            ),
        )
    }

    @Test
    fun automaticPolicyKeepsSeatTraversalOutOfHotPathAndUsesFastProbe() {
        assertFalse(BlaBlaHarvestPolicy.AUTOMATIC_PUBLISHED_SEAT_LOOKUP)
        assertTrue(BlaBlaHarvestPolicy.AUTOMATIC_PAGE_SETTLE_MS in 1L..300L)
    }
}
