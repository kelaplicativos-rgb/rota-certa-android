package br.com.mapeiaia.rotacerta.core

/**
 * Controlador central de estado da bolinha.
 * Ele define a verdade atual da bolinha: esconder, aguardar, aceitar ou rejeitar.
 * O serviço Android apenas reflete este estado na tela.
 */
class CoreBubbleStateController(
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private var state = CoreBubbleStateSnapshot(
        mode = CoreBubbleMode.Hidden,
        distanceKm = null,
        reason = "Estado inicial da bolinha.",
        updatedAtMillis = nowMillis(),
    )

    @Synchronized
    fun current(): CoreBubbleStateSnapshot = state

    @Synchronized
    fun render(mode: CoreBubbleMode, distanceKm: Double?, reason: String): CoreBubbleStateSnapshot {
        val sanitizedDistance = when (mode) {
            CoreBubbleMode.Good,
            CoreBubbleMode.Bad -> distanceKm
            CoreBubbleMode.Waiting,
            CoreBubbleMode.Hidden -> null
        }
        return update(mode = mode, distanceKm = sanitizedDistance, reason = reason)
    }

    @Synchronized
    fun waiting(reason: String): CoreBubbleStateSnapshot =
        update(mode = CoreBubbleMode.Waiting, distanceKm = null, reason = reason)

    @Synchronized
    fun hidden(reason: String): CoreBubbleStateSnapshot =
        update(mode = CoreBubbleMode.Hidden, distanceKm = null, reason = reason)

    @Synchronized
    fun clear(reason: String): CoreBubbleStateSnapshot = hidden(reason)

    private fun update(mode: CoreBubbleMode, distanceKm: Double?, reason: String): CoreBubbleStateSnapshot {
        val previous = state
        val next = CoreBubbleStateSnapshot(
            mode = mode,
            distanceKm = distanceKm,
            reason = reason,
            updatedAtMillis = nowMillis(),
            changed = previous.mode != mode || previous.distanceKm != distanceKm,
        )
        state = next
        return next
    }
}

data class CoreBubbleStateSnapshot(
    val mode: CoreBubbleMode,
    val distanceKm: Double?,
    val reason: String,
    val updatedAtMillis: Long,
    val changed: Boolean = true,
)
