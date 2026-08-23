package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlaBlaDirectCollector0262Test {
    @Test
    fun visualPassengerCardWinsOverDirectHref() {
        assertEquals(
            BlaBlaDirectPassengerStep.PASSENGER_CARD,
            blaBlaDirectPassengerStep(
                passengerPresent = true,
                hasBookingHref = true,
                needsReservationPage = true,
                hasPassengerCard = true,
            ),
        )
    }

    @Test
    fun firstUncompletedCardKeepsExactUiOrder() {
        val visible = listOf("past-trip", "today-trip", "year-2027-trip")
        assertEquals("past-trip", blaBlaFirstUncompletedVisibleKey(visible, emptySet()))
        assertEquals("today-trip", blaBlaFirstUncompletedVisibleKey(visible, setOf("past-trip")))
        assertEquals("year-2027-trip", blaBlaFirstUncompletedVisibleKey(visible, setOf("past-trip", "today-trip")))
    }

    @Test
    fun blockedOrIncompleteCardCannotReleaseNextCard() {
        assertFalse(blaBlaCanAdvanceToNextCard(currentCardComplete = false, currentCardBlocked = false))
        assertFalse(blaBlaCanAdvanceToNextCard(currentCardComplete = false, currentCardBlocked = true))
        assertFalse(blaBlaCanAdvanceToNextCard(currentCardComplete = true, currentCardBlocked = true))
        assertTrue(blaBlaCanAdvanceToNextCard(currentCardComplete = true, currentCardBlocked = false))
    }

    @Test
    fun scrollOnlyAfterVisibleCardsAreResolved() {
        assertFalse(blaBlaShouldScrollForMore(unresolvedVisibleCardExists = true, atBottom = false))
        assertTrue(blaBlaShouldScrollForMore(unresolvedVisibleCardExists = false, atBottom = false))
        assertFalse(blaBlaShouldScrollForMore(unresolvedVisibleCardExists = false, atBottom = true))
    }

    @Test
    fun unknownRosterStillNeverFinalizes() {
        assertEquals(
            BlaBlaDirectRosterState.UNKNOWN,
            blaBlaDirectRosterState(passengerCount = 0, rosterComplete = false, explicitEmpty = false),
        )
    }
}
