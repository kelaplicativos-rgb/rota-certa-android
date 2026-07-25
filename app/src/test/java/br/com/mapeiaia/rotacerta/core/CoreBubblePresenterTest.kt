package br.com.mapeiaia.rotacerta.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreBubblePresenterTest {
    @Test
    fun goodRideShowsFormattedDistance() {
        val presentation = CoreBubblePresenter.present(CoreBubbleMode.Good, 6.8)

        assertEquals(CoreBubbleMode.Good, presentation.mode)
        assertEquals("6,8", presentation.text)
        assertEquals(20f, presentation.textSizeSp)
        assertTrue(presentation.shouldShow)
    }

    @Test
    fun badRideShowsFormattedDistance() {
        val presentation = CoreBubblePresenter.present(CoreBubbleMode.Bad, 10.3)

        assertEquals(CoreBubbleMode.Bad, presentation.mode)
        assertEquals("10,3", presentation.text)
        assertEquals(18f, presentation.textSizeSp)
        assertTrue(presentation.shouldShow)
    }

    @Test
    fun waitingNeverShowsDistance() {
        val presentation = CoreBubblePresenter.present(CoreBubbleMode.Waiting, 7.2)

        assertEquals(CoreBubbleMode.Waiting, presentation.mode)
        assertEquals("", presentation.text)
        assertEquals(14f, presentation.textSizeSp)
        assertTrue(presentation.shouldShow)
    }

    @Test
    fun hiddenNeverShowsDistance() {
        val presentation = CoreBubblePresenter.present(CoreBubbleMode.Hidden, 7.2)

        assertEquals(CoreBubbleMode.Hidden, presentation.mode)
        assertEquals("", presentation.text)
        assertEquals(14f, presentation.textSizeSp)
        assertTrue(presentation.shouldShow)
    }

    @Test
    fun missingRouteDoesNotShowNumber() {
        val presentation = CoreBubblePresenter.present(CoreBubbleMode.Good, null)

        assertEquals(CoreBubbleMode.Good, presentation.mode)
        assertEquals("", presentation.text)
        assertEquals(14f, presentation.textSizeSp)
    }

    @Test
    fun negativeDistanceDoesNotShowNumber() {
        val presentation = CoreBubblePresenter.present(CoreBubbleMode.Bad, -1.0)

        assertEquals(CoreBubbleMode.Bad, presentation.mode)
        assertEquals("", presentation.text)
        assertEquals(14f, presentation.textSizeSp)
    }
}
