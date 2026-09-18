package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavigationCurrentManageHref0579Test {
    private val tripId = "admin-trip-0579"
    private val otherTripId = "other-trip-0579"
    private val profileUuid = "11111111-1111-4111-8111-111111111111"
    private val currentHref = "https://www.blablacar.com.br/ride-plan/trip-edit/$tripId"

    private fun source(href: String? = currentHref) = BlaBlaCollectorTrip(
        profile_uuid = profileUuid,
        date = "2030-09-18",
        trip_id = tripId,
        trip_href = href,
    )

    @Test
    fun currentRidePlanHrefCarriesStrongTripIdentity() {
        assertEquals(tripId, BlaBlaTripIdentity.externalTripIdFromHref(currentHref))
        assertEquals(tripId, BlaBlaCollectorUrlModule.tripId(currentHref))
        assertTrue(BlaBlaCollectorUrlModule.isManageTarget(currentHref))

        val evidence = BlaBlaTripIdentity.evidence(source())
        assertTrue(evidence.externalTripIdPresent)
        assertTrue(evidence.specificHrefPresent)
        assertFalse(evidence.fallbackIdentityUsed)
        assertFalse(evidence.identityConflict)
    }

    @Test
    fun currentRidePlanHrefIsAcceptedOnlyForTheSameObservedTrip() {
        val resolved = reconciledCollectorNavigationIdentity0578(source(), existing = null)
        assertEquals(currentHref, resolved?.trip_href)
        assertEquals(tripId, resolved?.trip_id)

        assertNull(
            reconciledCollectorNavigationIdentity0578(
                source("https://www.blablacar.com.br/ride-plan/trip-edit/$otherTripId"),
                existing = null,
            ),
        )
    }
}
