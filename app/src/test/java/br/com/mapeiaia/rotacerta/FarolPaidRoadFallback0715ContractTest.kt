package br.com.mapeiaia.rotacerta

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FarolPaidRoadFallback0715ContractTest {
    @Test
    fun terminalFallbackOnlyRunsAfterNormalRoadProvidersAreExhausted() {
        val live = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
        val nullTrigger = live.indexOf("trigger0715 = \"remote_refinement_null\"")
        val allNullTrigger = live.indexOf("trigger0715 = \"remote_refinement_all_null\"")
        val normalStart = live.indexOf("FarolLocalDecisionAuthority0696.REMOTE_STARTED_MARKER")
        assertTrue(normalStart >= 0)
        assertTrue(nullTrigger > normalStart)
        assertTrue(allNullTrigger > normalStart)
        assertTrue(live.contains("resolveFarolPaidRoad0715("))
        assertTrue(live.contains("FarolPaidRoadGate0715.STALE_DROPPED_MARKER"))
        assertTrue(live.contains("DistanceAuthority.ROAD_CONFIRMED"))
        assertTrue(live.contains("pickupToHomeKm = exactHome0715"))
        assertTrue(live.contains("pickupToAlternativeKm = exactPin0715"))
        assertFalse(live.contains("FAROL_COORDINATE_ALL_PROVIDERS_FAILED_0697\""))
    }

    @Test
    fun paidRoadContractNeverUsesOpenAiAsDistanceAuthority() {
        val backend = File("../trip-platform/functions/farol-paid-road-0715.js").readText()
        assertTrue(backend.contains("Não calcule, estime nem responda quilometragem"))
        assertTrue(backend.contains("nominatim.openstreetmap.org"))
        assertTrue(backend.contains("router.project-osrm.org"))
        assertTrue(backend.contains("FAROL_PAID_ROAD_FALLBACK_0715"))
        assertTrue(backend.contains("targetIndex"))
    }

    @Test
    fun remoteClientAndGateExposeSingleTerminalPath() {
        val api = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripRemoteApi.kt").readText()
        val gate = File("src/main/java/br/com/mapeiaia/rotacerta/FarolPaidRoadGate0715.kt").readText()
        assertTrue(api.contains("path = \"/v1/assistant/farol-road\""))
        assertTrue(gate.contains("already_in_flight"))
        assertTrue(gate.contains("failure_cooldown"))
        assertTrue(gate.contains("FAROL_PAID_ROAD_CACHE_HIT_0715"))
    }
}
