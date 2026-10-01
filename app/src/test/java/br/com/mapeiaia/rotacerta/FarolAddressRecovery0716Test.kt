package br.com.mapeiaia.rotacerta

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FarolAddressRecovery0716Test {
    @Test
    fun transientCoordinateFailureWaitsForLocalRetriesBeforePaidEscalation() {
        val gate = FarolAddressRecovery0716()
        assertEquals(
            FarolAddressRecovery0716.CoordinateDecision.WAIT_LOCAL_RETRY,
            gate.onCoordinateFailure("addr:a", 1_000L),
        )
        assertEquals(
            FarolAddressRecovery0716.CoordinateDecision.WAIT_LOCAL_RETRY,
            gate.onCoordinateFailure("addr:a", 1_700L),
        )
        assertEquals(
            FarolAddressRecovery0716.CoordinateDecision.ESCALATE_PAID_ADDRESS,
            gate.onCoordinateFailure("addr:a", 2_400L),
        )
        assertEquals(
            FarolAddressRecovery0716.CoordinateDecision.ALREADY_ESCALATED,
            gate.onCoordinateFailure("addr:a", 3_100L),
        )
    }

    @Test
    fun aNewDestinationGetsItsOwnGraceWindow() {
        val gate = FarolAddressRecovery0716()
        gate.onCoordinateFailure("addr:a", 1_000L)
        gate.onCoordinateFailure("addr:a", 2_400L)
        assertEquals(
            FarolAddressRecovery0716.CoordinateDecision.WAIT_LOCAL_RETRY,
            gate.onCoordinateFailure("addr:b", 2_500L),
        )
    }

    @Test
    fun resolvedCoordinateClearsRecoveryLease() {
        val gate = FarolAddressRecovery0716()
        gate.onCoordinateFailure("addr:a", 1_000L)
        assertTrue(gate.hasRecoveryLease("addr:a", 1_500L))
        gate.onCoordinateResolved("addr:a")
        assertFalse(gate.hasRecoveryLease("addr:a", 1_600L))
    }

    @Test
    fun heartbeatPreservesOnlySamePackageWindowAndDestinationWhileWorkIsActive() {
        assertTrue(
            FarolAddressRecovery0716.shouldPreserveHeartbeat(
                activeAddressSignature = "pkg|rua itapura 395",
                observedAddressSignature = "pkg|rua itapura 395",
                activeWindowId = 7,
                observedWindowId = 7,
                samePackage = true,
                routeInFlight = false,
                recoveryLeaseActive = true,
            ),
        )
        assertFalse(
            FarolAddressRecovery0716.shouldPreserveHeartbeat(
                activeAddressSignature = "pkg|rua itapura 395",
                observedAddressSignature = "pkg|praca da se",
                activeWindowId = 7,
                observedWindowId = 7,
                samePackage = true,
                routeInFlight = true,
                recoveryLeaseActive = true,
            ),
        )
        assertFalse(
            FarolAddressRecovery0716.shouldPreserveHeartbeat(
                activeAddressSignature = "pkg|rua itapura 395",
                observedAddressSignature = "pkg|rua itapura 395",
                activeWindowId = 7,
                observedWindowId = 9,
                samePackage = true,
                routeInFlight = true,
                recoveryLeaseActive = true,
            ),
        )
    }
}
