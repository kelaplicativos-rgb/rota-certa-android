package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DedicatedItineraryRecovery0598Test {
    private fun passenger(from: String, to: String, seats: Int = 1) =
        BlaBlaCollectorPassenger(
            name = "P",
            seats = seats,
            boarding = from,
            dropoff = to,
        )

    @Test
    fun passengerSegmentsRecoverUniqueOrderedRouteWithoutInventingOrder() {
        val recovered = BlaBlaItineraryRecovery0598.recoverFromPassengerSegments(
            origin = "Santo André",
            destination = "São Tomé das Letras",
            passengers = listOf(
                passenger("Santo André", "São Paulo", 2),
                passenger("São Paulo", "Pouso Alegre", 4),
                passenger("Pouso Alegre", "Três Corações", 3),
                passenger("Três Corações", "São Tomé das Letras", 1),
            ),
        )

        assertEquals(
            listOf(
                "Santo André",
                "São Paulo",
                "Pouso Alegre",
                "Três Corações",
                "São Tomé das Letras",
            ),
            recovered,
        )
    }

    @Test
    fun ambiguousPassengerSegmentsFailClosed() {
        val recovered = BlaBlaItineraryRecovery0598.recoverFromPassengerSegments(
            origin = "A",
            destination = "D",
            passengers = listOf(
                passenger("A", "B"),
                passenger("A", "C"),
                passenger("B", "D"),
                passenger("C", "D"),
            ),
        )

        assertTrue(recovered.isEmpty())
    }

    @Test
    fun compatibleDedicatedEvidenceEnrichesEndpointOnlyTrip() {
        val merged = BlaBlaItineraryRecovery0598.merge(
            origin = "Santo André",
            destination = "São Tomé das Letras",
            currentStops = listOf("Santo André", "São Tomé das Letras"),
            currentAuthoritative = false,
            dedicatedDomStops = listOf("Santo André", "São Tomé das Letras"),
            dedicatedDomAuthoritative = true,
            dedicatedNetworkStops = listOf(
                "Santo André",
                "São Paulo",
                "Pouso Alegre",
                "Três Corações",
                "São Tomé das Letras",
            ),
            passengerStops = emptyList(),
        )

        assertEquals(5, merged.stops.size)
        assertTrue(merged.authoritative)
    }

    @Test
    fun dynamicCollectorActuallyExecutesTripItineraryBeforeLeavingDetail() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()
        val capture = source.indexOf("captureDedicatedTripItinerary0598(")
        val request = source.indexOf(
            "evaluateRequest<DynamicTripItinerary0598>(BlaBlaBrowserRequest.TRIP_ITINERARY)",
        )
        val continueCall = source.indexOf("continueAfterTripItinerary0598(expectedSync, expectedCandidate)", request)

        assertTrue(capture >= 0)
        assertTrue(request > capture)
        assertTrue(continueCall > request)
        assertTrue(source.contains("TRIP_ITINERARY_CAPTURED_0598"))
        assertTrue(source.contains("recoverFromPassengerSegments("))
    }

    @Test
    fun dedicatedScriptBindsEvidenceToExactAdministrativeTrip() {
        val script = File("src/main/assets/blablacar/scripts/trip_itinerary.js").readText()

        assertTrue(script.contains("__rotaCertaNetworkTripSource"))
        assertTrue(script.contains("tripId:tripId"))
        assertTrue(script.contains("domStops:domStops"))
        assertTrue(script.contains("networkStops:networkStops"))
        assertTrue(script.contains("authoritative:authoritative"))
    }

    @Test
    fun centralNeverReportsSeatSegmentsCoherentWhenTopologyIsUnresolved() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/CentralDoDia0552.kt").readText()

        assertTrue(source.contains("Topologia de trechos incompleta"))
        assertTrue(source.contains("Vagas por trecho aguardam topologia completa"))
        assertTrue(source.contains("externalPassengerSegmentsResolved(external, trip)"))
    }
}
