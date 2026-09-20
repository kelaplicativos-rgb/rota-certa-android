package br.com.mapeiaia.rotacerta.trips

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanonicalItinerarySegmentPreservation0597Test {
    private val profile = "11111111-1111-4111-8111-111111111111"
    private val fullRoute = listOf(
        "Santo André",
        "São Paulo",
        "Pouso Alegre",
        "Três Corações",
        "São Tomé das Letras",
    )

    private fun collector(
        stops: List<String>,
        authoritative: Boolean = false,
        passengers: List<BlaBlaCollectorPassenger> = emptyList(),
    ) = BlaBlaCollectorTrip(
        profile_uuid = profile,
        profile_name = "Motorista",
        date = "2030-09-20",
        departure_time = "10:30",
        arrival_time = "16:30",
        actual_departure = "Santo André",
        actual_arrival = "São Tomé das Letras",
        trip_href = "https://www.blablacar.com.br/rides/offer/trip-0597-abcd",
        trip_id = "trip-0597-abcd",
        itinerary_stops = stops,
        itinerary_authoritative = authoritative,
        passengers = passengers,
        booked_seats = passengers.sumOf { it.seats.coerceAtLeast(1) },
        published_seats = 4,
        passenger_roster_complete = true,
    )

    @Test
    fun exactTripNetworkWaypointsEnrichEndpointOnlyDomWithoutClaimingAuthority() {
        val reconciled = BlaBlaCollectorNetworkSourceModule.reconcileOperationalItinerary0597(
            origin = "Santo André",
            destination = "São Tomé das Letras",
            domItinerary = listOf("Santo André", "São Tomé das Letras"),
            networkItinerary = fullRoute,
        )

        assertEquals(fullRoute, reconciled)
    }

    @Test
    fun incompatibleNetworkOrderNeverOverridesStructuralDomRoute() {
        val dom = listOf("Santo André", "São Paulo", "Pouso Alegre", "São Tomé das Letras")
        val incompatible = listOf("Santo André", "Pouso Alegre", "São Paulo", "São Tomé das Letras")

        val reconciled = BlaBlaCollectorNetworkSourceModule.reconcileOperationalItinerary0597(
            origin = "Santo André",
            destination = "São Tomé das Letras",
            domItinerary = dom,
            networkItinerary = incompatible,
        )

        assertEquals(dom, reconciled)
    }

    @Test
    fun normalFullRefreshCannotErasePreviouslyObservedIntermediateStops() {
        val previous = collector(fullRoute, authoritative = false)
        val endpointOnlyFresh = collector(
            listOf("Santo André", "São Tomé das Letras"),
            authoritative = false,
        )

        val merged = mergeSelectiveCollectorTrip0449(
            previous = previous,
            fresh = endpointOnlyFresh,
            selection = BlaBlaDateScopeScriptSelection0449.legacyAll(),
        )

        assertEquals(fullRoute, merged?.itinerary_stops)
        assertFalse(merged?.itinerary_authoritative ?: true)
    }

    @Test
    fun newAuthoritativeItineraryMayReplaceOlderTopology() {
        val previous = collector(fullRoute, authoritative = false)
        val authoritativeFresh = collector(
            listOf("Santo André", "Extrema", "São Tomé das Letras"),
            authoritative = true,
        )

        val merged = mergeSelectiveCollectorTrip0449(
            previous = previous,
            fresh = authoritativeFresh,
            selection = BlaBlaDateScopeScriptSelection0449.legacyAll(),
        )

        assertEquals(authoritativeFresh.itinerary_stops, merged?.itinerary_stops)
        assertTrue(merged?.itinerary_authoritative == true)
    }

    @Test
    fun canonicalIngestRefusesCompatibleTopologyShrinkFromNonAuthoritativeSnapshot() {
        val previous = Trip(
            id = "canonical-0597",
            title = "Santo André → São Tomé das Letras",
            departureAtMillis = 2_000_000_000_000L,
            capacity = 4,
            status = TripStatus.PUBLISHED,
            stops = fullRoute.mapIndexed { index, name ->
                TripStop(id = "old-$index", order = index, name = name)
            },
            itineraryAuthoritative = false,
        )
        val observed = previous.copy(
            stops = listOf(
                TripStop(id = "new-0", order = 0, name = "Santo André"),
                TripStop(id = "new-1", order = 1, name = "São Tomé das Letras"),
            ),
        )

        val preserved = preserveCanonicalRouteTopologyOnPartialRefresh0597(
            existing = previous,
            observed = observed,
            source = collector(observed.stops.map(TripStop::name), authoritative = false),
        )

        assertEquals(fullRoute, preserved.stops.sortedBy(TripStop::order).map(TripStop::name))
    }

    @Test
    fun recoveredTopologyProducesIndependentSeatAvailabilityForEveryConsecutiveSegment() {
        val passengers = listOf(
            BlaBlaCollectorPassenger(
                name = "A",
                seats = 2,
                boarding = "Santo André",
                dropoff = "São Paulo",
            ),
            BlaBlaCollectorPassenger(
                name = "B",
                seats = 4,
                boarding = "São Paulo",
                dropoff = "Pouso Alegre",
            ),
            BlaBlaCollectorPassenger(
                name = "C",
                seats = 3,
                boarding = "Pouso Alegre",
                dropoff = "Três Corações",
            ),
        )
        val source = collector(fullRoute, authoritative = false, passengers = passengers)
        val projected = PublicAgendaAutoSync0300.toPublicTrip(
            source = source,
            capacity = 4,
            nowMillis = 0L,
            zoneId = ZoneId.of("America/Sao_Paulo"),
        ) ?: error("projection missing")

        val loads = SeatAvailabilityEngine.segmentLoads(projected.trip, projected.capacityClaims)
        assertEquals(
            listOf(
                "Santo André → São Paulo",
                "São Paulo → Pouso Alegre",
                "Pouso Alegre → Três Corações",
                "Três Corações → São Tomé das Letras",
            ),
            loads.map { "${it.from.name} → ${it.to.name}" },
        )
        assertEquals(listOf(2, 0, 1, 4), loads.map(SegmentLoad::availableSeats))
    }
}
