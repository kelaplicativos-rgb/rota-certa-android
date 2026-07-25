package br.com.mapeiaia.rotacerta

data class BubbleShortcutPosition(
    val x: Int,
    val y: Int,
)

object BubbleShortcutPositionPolicy {
    fun place(
        anchorX: Int,
        anchorY: Int,
        anchorWidth: Int,
        anchorHeight: Int,
        menuWidth: Int,
        menuHeight: Int,
        screenWidth: Int,
        screenHeight: Int,
        gap: Int,
        safeMargin: Int,
    ): BubbleShortcutPosition {
        val safe = safeMargin.coerceAtLeast(0)
        val horizontalGap = gap.coerceAtLeast(1)
        val validAnchorWidth = anchorWidth.coerceAtLeast(1)
        val validAnchorHeight = anchorHeight.coerceAtLeast(1)
        val maxX = (screenWidth - menuWidth - safe).coerceAtLeast(safe)
        val maxY = (screenHeight - menuHeight - safe).coerceAtLeast(safe)
        val alignedY = anchorY.coerceIn(safe, maxY)
        val rightX = anchorX + validAnchorWidth + horizontalGap
        val leftX = anchorX - menuWidth - horizontalGap

        return when {
            rightX + menuWidth <= screenWidth - safe -> BubbleShortcutPosition(rightX, alignedY)
            leftX >= safe -> BubbleShortcutPosition(leftX, alignedY)
            anchorY + validAnchorHeight + horizontalGap + menuHeight <= screenHeight - safe ->
                BubbleShortcutPosition(
                    x = anchorX.coerceIn(safe, maxX),
                    y = anchorY + validAnchorHeight + horizontalGap,
                )
            else -> BubbleShortcutPosition(
                x = anchorX.coerceIn(safe, maxX),
                y = (anchorY - menuHeight - horizontalGap).coerceIn(safe, maxY),
            )
        }
    }
}
