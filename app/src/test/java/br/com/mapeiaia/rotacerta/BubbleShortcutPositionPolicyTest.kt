package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleShortcutPositionPolicyTest {
    @Test
    fun placesMenuToRightWhenThereIsSpace() {
        val position = BubbleShortcutPositionPolicy.place(
            anchorX = 20,
            anchorY = 100,
            anchorWidth = 66,
            anchorHeight = 66,
            menuWidth = 206,
            menuHeight = 212,
            screenWidth = 1080,
            screenHeight = 1920,
            gap = 8,
            safeMargin = 4,
        )

        assertTrue(position.x >= 94)
        assertFalse(intersects(20, 100, 86, 166, position.x, position.y, position.x + 206, position.y + 212))
    }

    @Test
    fun placesMenuToLeftNearRightEdge() {
        val position = BubbleShortcutPositionPolicy.place(
            anchorX = 980,
            anchorY = 120,
            anchorWidth = 66,
            anchorHeight = 66,
            menuWidth = 206,
            menuHeight = 212,
            screenWidth = 1080,
            screenHeight = 1920,
            gap = 8,
            safeMargin = 4,
        )

        assertTrue(position.x + 206 <= 972)
        assertFalse(intersects(980, 120, 1046, 186, position.x, position.y, position.x + 206, position.y + 212))
    }

    @Test
    fun narrowScreenUsesVerticalPositionWithoutOverlap() {
        val position = BubbleShortcutPositionPolicy.place(
            anchorX = 130,
            anchorY = 300,
            anchorWidth = 90,
            anchorHeight = 90,
            menuWidth = 260,
            menuHeight = 230,
            screenWidth = 360,
            screenHeight = 800,
            gap = 8,
            safeMargin = 4,
        )

        assertTrue(position.y >= 398 || position.y + 230 <= 292)
        assertFalse(intersects(130, 300, 220, 390, position.x, position.y, position.x + 260, position.y + 230))
    }

    private fun intersects(
        leftA: Int,
        topA: Int,
        rightA: Int,
        bottomA: Int,
        leftB: Int,
        topB: Int,
        rightB: Int,
        bottomB: Int,
    ): Boolean = maxOf(leftA, leftB) < minOf(rightA, rightB) &&
        maxOf(topA, topB) < minOf(bottomA, bottomB)
}
