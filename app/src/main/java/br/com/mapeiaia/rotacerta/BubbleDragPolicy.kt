package br.com.mapeiaia.rotacerta

import kotlin.math.max

/**
 * Regras puras usadas pelo gesto da bolinha. Mantidas fora do Service para que
 * limiar e limites de tela possam ser testados sem Android/emulador.
 */
object BubbleDragPolicy {
    const val ANALYSIS_RESUME_DELAY_MS = 32L

    fun hasExceededTouchSlop(
        deltaX: Float,
        deltaY: Float,
        touchSlopPx: Int,
    ): Boolean {
        val safeSlop = max(1, touchSlopPx).toFloat()
        return deltaX * deltaX + deltaY * deltaY >= safeSlop * safeSlop
    }

    fun clampCoordinate(value: Int, maximum: Int): Int =
        value.coerceIn(0, maximum.coerceAtLeast(0))
}
