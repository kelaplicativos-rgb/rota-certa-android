package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleDragPolicyTest {
    @Test
    fun firstMeaningfulMovementStartsDragWithoutTimeDelay() {
        assertFalse(BubbleDragPolicy.hasExceededTouchSlop(2f, 2f, 8))
        assertTrue(BubbleDragPolicy.hasExceededTouchSlop(8f, 0f, 8))
        assertTrue(BubbleDragPolicy.hasExceededTouchSlop(6f, 6f, 8))
    }

    @Test
    fun coordinatesStayInsideVisibleScreen() {
        assertEquals(0, BubbleDragPolicy.clampCoordinate(-40, 900))
        assertEquals(420, BubbleDragPolicy.clampCoordinate(420, 900))
        assertEquals(900, BubbleDragPolicy.clampCoordinate(1_200, 900))
        assertEquals(0, BubbleDragPolicy.clampCoordinate(100, -1))
    }

    @Test
    fun analysisResumeDelayRemainsBelowOneFramePair() {
        assertTrue(BubbleDragPolicy.ANALYSIS_RESUME_DELAY_MS <= 32L)
    }
}
