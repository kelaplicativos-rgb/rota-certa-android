package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlaBlaDirectCollector0261Test {
    @Test
    fun incompleteEmptyRosterRemainsUnknown() {
        assertEquals(
            BlaBlaDirectRosterState.UNKNOWN,
            blaBlaDirectRosterState(0, rosterComplete = false, explicitEmpty = false),
        )
    }

    @Test
    fun explicitEmptyRosterCanFinalize() {
        assertEquals(
            BlaBlaDirectRosterState.COMPLETE_EMPTY,
            blaBlaDirectRosterState(0, rosterComplete = true, explicitEmpty = true),
        )
    }

    @Test
    fun visibleEmptyMarkerCannotFinalizeBeforeProbeConfirmation() {
        assertEquals(
            BlaBlaDirectRosterState.UNKNOWN,
            blaBlaDirectRosterState(0, rosterComplete = false, explicitEmpty = true),
        )
    }

    @Test
    fun positiveRosterMustBeReportedComplete() {
        assertEquals(
            BlaBlaDirectRosterState.UNKNOWN,
            blaBlaDirectRosterState(2, rosterComplete = false, explicitEmpty = false),
        )
        assertEquals(
            BlaBlaDirectRosterState.COMPLETE_WITH_PASSENGERS,
            blaBlaDirectRosterState(2, rosterComplete = true, explicitEmpty = false),
        )
    }

    @Test
    fun bookingHrefStartsIndividualReservationStep() {
        assertEquals(
            BlaBlaDirectPassengerStep.RESERVATION_URL,
            blaBlaDirectPassengerStep(true, hasBookingHref = true, needsReservationPage = true, hasPassengerCard = false),
        )
    }

    @Test
    fun passengerCardIsUsedWhenHrefIsNotExposed() {
        assertEquals(
            BlaBlaDirectPassengerStep.PASSENGER_CARD,
            blaBlaDirectPassengerStep(true, hasBookingHref = false, needsReservationPage = false, hasPassengerCard = true),
        )
    }

    @Test
    fun staleCallbackCannotMatchNextCandidate() {
        assertFalse(
            blaBlaDirectCallbackMatches(7, 11, 2, "trip-a", 7, 12, 3, "trip-b"),
        )
        assertTrue(
            blaBlaDirectCallbackMatches(7, 11, 2, "trip-a", 7, 11, 2, "trip-a"),
        )
    }

    @Test
    fun validatedIdentityWithIncompleteDataIsPartial() {
        assertEquals(
            "partial",
            blaBlaDirectCollectorStatus(2, 2, 0, rosterIncompleteCount = 3, skippedCount = 2),
        )
        assertFalse(
            blaBlaDirectCoverageComplete(2, 2, 0, rosterIncompleteCount = 3, skippedCount = 2),
        )
    }
}
