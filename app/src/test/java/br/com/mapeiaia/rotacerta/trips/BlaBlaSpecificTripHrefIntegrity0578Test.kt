package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlaBlaSpecificTripHrefIntegrity0578Test {
    private val profileA = "11111111-1111-4111-8111-111111111111"
    private val profileB = "22222222-2222-4222-8222-222222222222"
    private val tripA = "trip-0578-a"
    private val tripB = "trip-0578-b"

    @Test
    fun exactSpecificHrefRequiresOfficialHostSpecificPathAndMatchingTripId() {
        assertEquals(
            "https://www.blablacar.com.br/rides/offer/$tripA",
            canonicalSpecificTripHref0578(
                "https://www.blablacar.com.br/rides/offer/$tripA?search_uuid=temporary",
                tripA,
            ),
        )
        assertNull(
            canonicalSpecificTripHref0578(
                "https://www.blablacar.com.br/rides/offer/$tripB",
                tripA,
            ),
        )
        assertNull(canonicalSpecificTripHref0578("https://www.blablacar.com.br/rides", tripA))
        assertNull(canonicalSpecificTripHref0578("https://blablacar.evil.com/rides/offer/$tripA", tripA))
    }

    @Test
    fun partialCollectorMayReuseOnlyPreviouslyProvenHrefFromSameStrongIdentity() {
        val existing = externalTrip(
            profileUuid = profileA,
            tripId = tripA,
            manageHref = "https://www.blablacar.com.br/rides/offer/$tripA",
        )
        val incoming = collectorTrip(profileA, tripA, null)

        val rebound = collectorSourceWithSpecificTripHref0578(incoming, existing)

        assertEquals("https://www.blablacar.com.br/rides/offer/$tripA", rebound?.trip_href)
        assertNull(
            collectorSourceWithSpecificTripHref0578(
                incoming.copy(profile_uuid = profileB),
                existing,
            ),
        )
        assertNull(collectorSourceWithSpecificTripHref0578(incoming, null))
    }

    @Test
    fun rideListRepairsLegacySessionBeforeDeepTraversalWithoutTouchingOtherTrips() {
        val publicHref = "https://www.blablacar.com.br/trip?id=public-token-a"
        val snapshot = BlaBlaDynamicSessionSnapshot(
            accountId = "account-a",
            profileUuid = profileA,
            identityVerified = true,
            trips = listOf(
                collectorTrip(profileA, tripA, null).copy(public_trip_href = publicHref),
                collectorTrip(profileA, tripB, "https://www.blablacar.com.br/rides/offer/$tripB"),
            ),
        )

        val repair = repairSpecificTripHrefsInSnapshot0578(
            snapshot = snapshot,
            expectedProfileUuid = profileA,
            candidates = listOf(
                BlaBlaDomRideCandidate(
                    href = "https://www.blablacar.com.br/rides/offer/$tripA?search_uuid=ride-list",
                ),
            ),
        )

        assertEquals(1, repair.repairedTrips)
        assertEquals(
            "https://www.blablacar.com.br/rides/offer/$tripA",
            repair.snapshot.trips.first { it.trip_id == tripA }.trip_href,
        )
        assertEquals(publicHref, repair.snapshot.trips.first { it.trip_id == tripA }.public_trip_href)
        assertEquals(
            "https://www.blablacar.com.br/rides/offer/$tripB",
            repair.snapshot.trips.first { it.trip_id == tripB }.trip_href,
        )
    }

    @Test
    fun rideListRepairFailsClosedWhenSessionProfileWasNotVerified() {
        val snapshot = BlaBlaDynamicSessionSnapshot(
            accountId = "account-a",
            profileUuid = profileA,
            identityVerified = false,
            trips = listOf(collectorTrip(profileA, tripA, null)),
        )
        val repair = repairSpecificTripHrefsInSnapshot0578(
            snapshot,
            profileA,
            listOf(BlaBlaDomRideCandidate(href = "https://www.blablacar.com.br/rides/offer/$tripA")),
        )

        assertEquals(0, repair.repairedTrips)
        assertNull(repair.snapshot.trips.single().trip_href)
    }

    private fun collectorTrip(
        profileUuid: String,
        tripId: String,
        href: String?,
    ) = BlaBlaCollectorTrip(
        profile_uuid = profileUuid,
        date = "2030-09-18",
        departure_time = "11:00",
        actual_departure = "Origem",
        actual_arrival = "Destino",
        trip_href = href,
        trip_id = tripId,
        published_seats = 3,
        passenger_roster_complete = true,
    )

    private fun externalTrip(
        profileUuid: String,
        tripId: String,
        manageHref: String,
    ) = Trip(
        id = "canonical-$tripId",
        title = "Origem → Destino",
        departureAtMillis = 4_000_000_000_000L,
        capacity = 3,
        status = TripStatus.PUBLISHED,
        stops = listOf(
            TripStop(id = "o-$tripId", order = 0, name = "Origem"),
            TripStop(id = "d-$tripId", order = 1, name = "Destino"),
        ),
        blablaProfileUuid = profileUuid,
        blablaTripId = tripId,
        blablaManageUrl = manageHref,
        recordOrigin = TripRecordOrigin.EXTERNAL_BACKING,
    )
}
