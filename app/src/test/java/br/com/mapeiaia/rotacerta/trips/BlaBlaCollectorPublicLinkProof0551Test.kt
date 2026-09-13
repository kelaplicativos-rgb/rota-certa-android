package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlaBlaCollectorPublicLinkProof0551Test {
    private val adminId = "admin-trip-0551-a"
    private val differentPublicId = "public-trip-0551-b"
    private val profileUuid = "7371f028-9c55-4903-8444-308015823efd"

    @Test
    fun exactPublicSearchCannotCanonicallyBindDifferentPublicToken() {
        val resolved = validatedCollectorPublicTripHref0551(
            raw = publicHref(differentPublicId),
            expectedTripId = adminId,
            source = "exact_public_search",
            binding = BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_ORCHESTRATOR_NAVIGATION,
        )

        assertNull(resolved)
    }

    @Test
    fun exactPublicSearchMaySurviveWhenUrlItselfProvesSameTripId() {
        val resolved = validatedCollectorPublicTripHref0551(
            raw = publicHref(adminId),
            expectedTripId = adminId,
            source = "exact_public_search",
            binding = BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_ORCHESTRATOR_NAVIGATION,
        )

        assertEquals(publicHref(adminId), resolved)
    }

    @Test
    fun authoritativeNetworkBindingKeepsLegitimateDifferentPublicToken() {
        val resolved = validatedCollectorPublicTripHref0551(
            raw = publicHref(differentPublicId),
            expectedTripId = adminId,
            source = "network_authoritative",
            binding = BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_NETWORK_AUTHORITATIVE,
        )

        assertEquals(publicHref(differentPublicId), resolved)
    }

    @Test
    fun staleWeakExactSearchLinkIsNotPreservedAcrossMonotonicMerge() {
        val previous = trip(
            publicHref = publicHref(differentPublicId),
            source = "exact_public_search",
            binding = BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_ORCHESTRATOR_NAVIGATION,
        )
        val current = trip(publicHref = null, source = "", binding = "")

        val merged = BlaBlaCollectorPassengerModule.mergeMonotonic(previous, current)

        assertNull(merged.public_trip_href)
        assertEquals("", merged.public_trip_href_source)
        assertEquals("", merged.public_trip_href_binding)
    }

    @Test
    fun previousAuthoritativeNetworkLinkIsPreservedWhenCurrentReadDoesNotObserveOne() {
        val previous = trip(
            publicHref = publicHref(differentPublicId),
            source = "network_authoritative",
            binding = BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_NETWORK_AUTHORITATIVE,
        )
        val current = trip(publicHref = null, source = "", binding = "")

        val merged = BlaBlaCollectorPassengerModule.mergeMonotonic(previous, current)

        assertEquals(publicHref(differentPublicId), merged.public_trip_href)
        assertEquals("network_authoritative", merged.public_trip_href_source)
        assertEquals(BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_NETWORK_AUTHORITATIVE, merged.public_trip_href_binding)
    }

    private fun trip(
        publicHref: String?,
        source: String,
        binding: String,
    ): BlaBlaCollectorTrip = BlaBlaCollectorTrip(
        profile_uuid = profileUuid,
        date = "2026-09-12",
        trip_id = adminId,
        public_trip_href = publicHref,
        public_trip_href_source = source,
        public_trip_href_binding = binding,
        passenger_roster_complete = true,
    )

    private fun publicHref(id: String): String = "https://www.blablacar.com.br/trip/$id"
}
