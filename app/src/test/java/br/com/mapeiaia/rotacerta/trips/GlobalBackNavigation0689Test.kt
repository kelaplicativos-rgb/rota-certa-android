package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalBackNavigation0689Test {
    @Test
    fun deepLinkSeedsTimelineAsSafePreviousStage() {
        assertEquals(
            listOf("TIMELINE"),
            GlobalBackNavigation0689.initialHistory(
                initialScreen = "RESERVATIONS",
                defaultRoot = "TIMELINE",
            ),
        )
    }

    @Test
    fun rootStartsWithoutSyntheticHistory() {
        assertTrue(
            GlobalBackNavigation0689.initialHistory(
                initialScreen = "TIMELINE",
                defaultRoot = "TIMELINE",
            ).isEmpty(),
        )
    }

    @Test
    fun forwardStagesArePoppedOneAtATime() {
        val first = GlobalBackNavigation0689.recordForward(
            history = emptyList(),
            currentScreen = "TIMELINE",
            destinationScreen = "CENTRAL_DAY",
        )
        val second = GlobalBackNavigation0689.recordForward(
            history = first,
            currentScreen = "CENTRAL_DAY",
            destinationScreen = "SEARCH",
        )

        val backToCentral = GlobalBackNavigation0689.pop(second)
        assertEquals("CENTRAL_DAY", backToCentral.target)
        assertEquals(listOf("TIMELINE"), backToCentral.history)

        val backToTimeline = GlobalBackNavigation0689.pop(backToCentral.history)
        assertEquals("TIMELINE", backToTimeline.target)
        assertTrue(backToTimeline.history.isEmpty())
    }

    @Test
    fun sameDestinationDoesNotDuplicateHistory() {
        assertTrue(
            GlobalBackNavigation0689.recordForward(
                history = emptyList(),
                currentScreen = "TIMELINE",
                destinationScreen = "TIMELINE",
            ).isEmpty(),
        )
    }

    @Test
    fun rootBackIsDisabledButNestedStageKeepsBackAvailable() {
        assertFalse(
            GlobalBackNavigation0689.canNavigateBack(
                history = emptyList(),
                currentScreen = "TIMELINE",
                defaultRoot = "TIMELINE",
                nestedStageOpen = false,
            ),
        )
        assertTrue(
            GlobalBackNavigation0689.canNavigateBack(
                history = emptyList(),
                currentScreen = "TIMELINE",
                defaultRoot = "TIMELINE",
                nestedStageOpen = true,
            ),
        )
    }
}
