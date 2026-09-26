package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlaBlaDirectCollector0263Test {
    @Test
    fun stablePositiveRosterCompletesWithoutBrittleContainerMarker() {
        assertFalse(
            blaBlaDirectRosterCompleteAfterStableProbe(
                passengerCount = 4,
                structurallyComplete = false,
                explicitEmpty = false,
                hasMore = false,
                terminalEvidence = true,
                stablePasses = 1,
            ),
        )
        assertTrue(
            blaBlaDirectRosterCompleteAfterStableProbe(
                passengerCount = 4,
                structurallyComplete = false,
                explicitEmpty = false,
                hasMore = false,
                terminalEvidence = true,
                stablePasses = 2,
            ),
        )
    }

    @Test
    fun expansionControlKeepsRosterOpenEvenWhenRowsAreStable() {
        assertFalse(
            blaBlaDirectRosterCompleteAfterStableProbe(
                passengerCount = 4,
                structurallyComplete = true,
                explicitEmpty = false,
                hasMore = true,
                terminalEvidence = true,
                stablePasses = 6,
            ),
        )
    }

    @Test
    fun unmarkedEmptyRosterNeedsThreeTerminalObservations() {
        assertFalse(
            blaBlaDirectRosterCompleteAfterStableProbe(
                passengerCount = 0,
                structurallyComplete = false,
                explicitEmpty = false,
                hasMore = false,
                terminalEvidence = true,
                stablePasses = 2,
            ),
        )
        assertTrue(
            blaBlaDirectRosterCompleteAfterStableProbe(
                passengerCount = 0,
                structurallyComplete = false,
                explicitEmpty = false,
                hasMore = false,
                terminalEvidence = true,
                stablePasses = 3,
            ),
        )
    }

    @Test
    fun explicitEmptyRosterAlsoNeedsThreeStableTerminalObservations() {
        assertFalse(
            blaBlaDirectRosterCompleteAfterStableProbe(
                passengerCount = 0,
                structurallyComplete = true,
                explicitEmpty = true,
                hasMore = false,
                terminalEvidence = true,
                stablePasses = 2,
            ),
        )
        assertTrue(
            blaBlaDirectRosterCompleteAfterStableProbe(
                passengerCount = 0,
                structurallyComplete = true,
                explicitEmpty = true,
                hasMore = false,
                terminalEvidence = true,
                stablePasses = 3,
            ),
        )
    }

    @Test
    fun everyDomEmptyRosterWaitsForNetworkBeforeFinalizing() {
        assertTrue(
            BlaBlaCollectorPassengerModule.shouldAwaitNetworkBeforeEmptyRoster(
                networkResolved = false,
                passengerCount = 0,
                readAttempts = 0,
                maxReadAttempts = 5,
            ),
        )
        assertFalse(
            BlaBlaCollectorPassengerModule.shouldAwaitNetworkBeforeEmptyRoster(
                networkResolved = true,
                passengerCount = 0,
                readAttempts = 0,
                maxReadAttempts = 5,
            ),
        )
        assertFalse(
            BlaBlaCollectorPassengerModule.shouldAwaitNetworkBeforeEmptyRoster(
                networkResolved = false,
                passengerCount = 3,
                readAttempts = 0,
                maxReadAttempts = 5,
            ),
        )
    }
}
