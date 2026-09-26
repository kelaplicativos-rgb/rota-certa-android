package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NavigationIdentityAtomic0578Test {
    private val tripId = "admin-trip-0578"
    private val exactHref = "https://www.blablacar.com.br/rides/offer/$tripId"
    private val profileUuid = "11111111-1111-4111-8111-111111111111"

    private fun source(href: String? = null) = BlaBlaCollectorTrip(
        profile_uuid = profileUuid,
        date = "2030-09-18",
        trip_id = tripId,
        trip_href = href,
    )

    private fun canonical(href: String? = exactHref) = Trip(
        id = "canonical-0578",
        title = "Origem → Destino",
        departureAtMillis = 4_000_000_000_000L,
        stops = listOf(
            TripStop(id = "a", order = 0, name = "Origem"),
            TripStop(id = "b", order = 1, name = "Destino"),
        ),
        recordOrigin = TripRecordOrigin.EXTERNAL_BACKING,
        blablaProfileUuid = profileUuid,
        blablaTripId = tripId,
        blablaManageUrl = href,
    )

    @Test
    fun exactObservedHrefIsAccepted() {
        val resolved = reconciledCollectorNavigationIdentity0578(source(exactHref), existing = null)
        assertEquals(exactHref, resolved?.trip_href)
        assertEquals(tripId, resolved?.trip_id)
    }

    @Test
    fun missingObservedHrefIsRepairedOnlyFromSameCanonicalIdentity() {
        val resolved = reconciledCollectorNavigationIdentity0578(source(), canonical())
        assertEquals(exactHref, resolved?.trip_href)
        assertEquals(tripId, resolved?.trip_id)
    }

    @Test
    fun missingHrefWithoutCanonicalProofIsBlocked() {
        assertNull(reconciledCollectorNavigationIdentity0578(source(), canonical(href = null)))
    }

    @Test
    fun hrefForDifferentTripIsBlocked() {
        assertNull(
            reconciledCollectorNavigationIdentity0578(
                source("https://www.blablacar.com.br/rides/offer/other-trip-0578"),
                existing = null,
            ),
        )
    }
}
