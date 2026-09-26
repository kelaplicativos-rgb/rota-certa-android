package br.com.mapeiaia.rotacerta.trips

import java.io.File
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ItineraryBoundarySerialization0599Test {
    private val networkRoute = listOf(
        "Rodoviária de Santo André - Avenida Industrial",
        "São Paulo",
        "Extrema",
        "Pouso Alegre",
        "Três Corações",
        "Gruta do Sobradinho, São Tomé das Letras",
    )

    @Test
    fun completeExactTripWaypointsUseCanonicalCardBoundariesAndKeepEveryIntermediateStop() {
        val aligned = BlaBlaItineraryRecovery0598.alignExactTripWaypoints0599(
            origin = "Santo André",
            destination = "São Tomé das Letras",
            rawStops = networkRoute,
        )

        assertEquals(
            listOf(
                "Santo André",
                "São Paulo",
                "Extrema",
                "Pouso Alegre",
                "Três Corações",
                "São Tomé das Letras",
            ),
            aligned,
        )
    }

    @Test
    fun networkFirstReconciliationNoLongerCollapsesWhenDomHasNoRouteLabels() {
        val reconciled = BlaBlaCollectorNetworkSourceModule.reconcileOperationalItinerary0597(
            origin = "Santo André",
            destination = "São Tomé das Letras",
            domItinerary = emptyList(),
            networkItinerary = networkRoute,
        )

        assertEquals(6, reconciled.size)
        assertEquals("Santo André", reconciled.first())
        assertEquals("São Tomé das Letras", reconciled.last())
        assertEquals(listOf("São Paulo", "Extrema", "Pouso Alegre", "Três Corações"), reconciled.drop(1).dropLast(1))
    }

    @Test
    fun detailedPassengerBoundaryLabelsMapToUniqueCanonicalStopsInsteadOfWholeTripFallback() {
        val passengers = listOf(
            BlaBlaCollectorPassenger(
                name = "A",
                seats = 2,
                boarding = "Rodoviária de Santo André - Avenida Industrial",
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
        val source = BlaBlaCollectorTrip(
            profile_uuid = "11111111-1111-4111-8111-111111111111",
            profile_name = "Motorista",
            date = "2030-09-20",
            departure_time = "10:30",
            arrival_time = "16:30",
            actual_departure = "Santo André",
            actual_arrival = "São Tomé das Letras",
            trip_href = "https://www.blablacar.com.br/rides/offer/trip-0599-abcd",
            trip_id = "trip-0599-abcd",
            itinerary_stops = listOf(
                "Santo André",
                "São Paulo",
                "Extrema",
                "Pouso Alegre",
                "Três Corações",
                "São Tomé das Letras",
            ),
            itinerary_authoritative = false,
            passengers = passengers,
            booked_seats = passengers.sumOf { it.seats },
            published_seats = 4,
            passenger_roster_complete = true,
        )
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
                "São Paulo → Extrema",
                "Extrema → Pouso Alegre",
                "Pouso Alegre → Três Corações",
                "Três Corações → São Tomé das Letras",
            ),
            loads.map { "${it.from.name} → ${it.to.name}" },
        )
        assertEquals(listOf(2, 0, 0, 1, 4), loads.map(SegmentLoad::availableSeats))
    }

    @Test
    fun itineraryCaptureOwnsASeparatePhaseSoDelayedTripDetailCannotInvalidateItsBrowserToken() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()
        val phaseEnter = source.indexOf("enterBrowserPhase(\n            Phase.ITINERARY")
        val itineraryRequest = source.indexOf(
            "evaluateRequest<DynamicTripItinerary0598>(BlaBlaBrowserRequest.TRIP_ITINERARY)",
            phaseEnter,
        )
        val currentGuard = source.indexOf("itineraryCaptureIsCurrent0599(", itineraryRequest)

        assertTrue(source.contains("DETAIL, ITINERARY, PUBLIC_SHARE"))
        assertTrue(source.contains("Phase.ITINERARY -> Unit"))
        assertTrue(phaseEnter >= 0)
        assertTrue(itineraryRequest > phaseEnter)
        assertTrue(currentGuard > itineraryRequest)
    }

    @Test
    fun exactCandidateHydratesMissingDetailBoundariesBeforeNetworkAndDedicatedItineraryRecovery() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()

        assertTrue(source.contains("origin = result.detail.origin.ifBlank { candidate.origin.trim() }"))
        assertTrue(source.contains("destination = result.detail.destination.ifBlank { candidate.destination.trim() }"))
        assertTrue(source.contains("val effectiveOrigin0599 = pending.detail.origin.ifBlank { candidate.origin.trim() }"))
        assertTrue(source.contains("val effectiveDestination0599 = pending.detail.destination.ifBlank { candidate.destination.trim() }"))
        assertTrue(source.contains("TRIP_ITINERARY_CAPTURED_0599"))
    }

    @Test
    fun dedicatedNetworkRouteIsOnlyEndpointRealignedWhenNetworkDeclaresCompleteness() {
        val merged = BlaBlaItineraryRecovery0598.merge(
            origin = "Santo André",
            destination = "São Tomé das Letras",
            currentStops = emptyList(),
            currentAuthoritative = false,
            dedicatedDomStops = emptyList(),
            dedicatedDomAuthoritative = false,
            dedicatedNetworkStops = networkRoute,
            dedicatedNetworkComplete = true,
            passengerStops = emptyList(),
        )

        assertEquals(6, merged.stops.size)
        assertEquals("trip_itinerary_network_exact_complete", merged.source)
    }
}
