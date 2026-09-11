package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineGlobalPullSync0538Test {
    private fun target(
        tenant: String = "tenant",
        account: String = "account",
        profile: String,
        trip: String,
        href: String = "https://www.blablacar.com.br/ride-plan/trip-edit/$trip",
    ) = BlaBlaTripTarget0407(
        tenantId = tenant,
        accountId = account,
        profileUuid = profile,
        tripId = trip,
        tripHref = href,
    )

    @Test
    fun deduplicatesByStrongIdentityAcrossTimelineCards() {
        val first = target(profile = "PROFILE-A", trip = "trip-001")
        val duplicate = target(profile = "profile-a", trip = "trip-001", href = "https://www.blablacar.com.br/ride-plan/trip-edit/trip-001?source=duplicate")
        val second = target(profile = "profile-b", trip = "trip-002")

        val result = distinctTimelineGlobalPullTargets0538(listOf(first, null, duplicate, second))

        assertEquals(2, result.size)
        assertEquals(listOf(first.strongIdentityKey, second.strongIdentityKey), result.map { it.strongIdentityKey })
    }

    @Test
    fun keepsSameTripIdSeparatedAcrossProfiles() {
        val a = target(profile = "profile-a", trip = "same-trip")
        val b = target(profile = "profile-b", trip = "same-trip")

        val result = distinctTimelineGlobalPullTargets0538(listOf(a, b))

        assertEquals(2, result.size)
    }
}
