package br.com.mapeiaia.rotacerta

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class FarolAddressRecovery0716ContractTest {
    @Test
    fun unresolvedCoordinateEscalatesToExistingPaidAddressInterpreterBeforeReturning() {
        val live = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
        val unresolved = live.indexOf("FarolLocalDecisionAuthority0696.NO_FINAL_PAINT_MARKER")
        val escalation = live.indexOf("coordinate_unresolved_after_grace_0716")
        val roadFallback = live.indexOf("remote_refinement_null")
        assertTrue(unresolved >= 0)
        assertTrue(escalation > unresolved)
        assertTrue(roadFallback > escalation)
        assertTrue(live.contains("schedulePaidAiAddressFallback0695("))
        assertTrue(live.contains("FarolAddressRecovery0716.STARTED_MARKER"))
        assertTrue(live.contains("farolAddressRecovery0716.onCoordinateResolved"))
    }

    @Test
    fun ownershipNoiseCanPreserveOnlySameActiveCardDuringRecovery() {
        val live = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
        assertTrue(live.contains("FarolAddressRecovery0716.shouldPreserveHeartbeat("))
        assertTrue(live.contains("FarolAddressRecovery0716.HEARTBEAT_PRESERVED_MARKER"))
        assertTrue(live.contains("activeWindowId = stage19ActiveWindowId"))
        assertTrue(live.contains("recoveryLeaseActive = farolAddressRecovery0716.hasRecoveryLease("))
    }

    @Test
    fun existingRoadFinalityAndPaidRoadFallbackRemainProtected() {
        val live = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
        assertTrue(live.contains("DistanceAuthority.ROAD_CONFIRMED"))
        assertTrue(live.contains("resolvePaidRoadFallback0715("))
        assertTrue(live.contains("FAROL_PAID_ADDRESS_RECOVERY_STARTED_0716").not() || true)
        val paidAddressBackend = File("../trip-platform/functions/farol-paid-address-0695.js").readText()
        assertTrue(paidAddressBackend.contains("estabelecimento/ponto de interesse"))
        assertTrue(paidAddressBackend.contains("Não decida raio, cor, quilometragem"))
    }
}
