package br.com.mapeiaia.rotacerta.core

/**
 * Porta única de renderização da bolinha. Rejeita comandos antigos e não deixa
 * um evento transitório apagar uma decisão válida da sessão atual.
 */
class BubbleRenderCoordinator(
    private val transientProtectionMillis: Long = 450L,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private var latestGeneration: Long = 0L
    private var state = BubbleRenderState(CoreBubbleMode.Hidden, null, 0L, "estado_inicial")

    @Synchronized
    fun request(
        generation: Long,
        mode: CoreBubbleMode,
        distanceKm: Double?,
        reason: String,
        force: Boolean = false,
    ): BubbleRenderDecision {
        if (!force && generation < latestGeneration) {
            return BubbleRenderDecision(false, state, "comando_antigo")
        }
        latestGeneration = maxOf(latestGeneration, generation)
        val now = nowMillis()
        val hasDecision = state.mode == CoreBubbleMode.Good || state.mode == CoreBubbleMode.Bad
        val requestedClearing = mode == CoreBubbleMode.Hidden || mode == CoreBubbleMode.Waiting
        val transientReason = reason.contains("systemui", true) ||
            reason.contains("texto vazio", true) ||
            reason.contains("vazio transitório", true) ||
            reason.contains("janela transitória", true)
        if (!force && hasDecision && requestedClearing && transientReason && now - state.updatedAtMillis <= transientProtectionMillis) {
            return BubbleRenderDecision(false, state, "decisao_preservada_contra_evento_transitorio")
        }
        val sanitizedDistance = when (mode) {
            CoreBubbleMode.Good, CoreBubbleMode.Bad -> distanceKm
            CoreBubbleMode.Waiting, CoreBubbleMode.Hidden -> null
        }
        val next = BubbleRenderState(mode, sanitizedDistance, now, reason)
        val changed = next.mode != state.mode || next.distanceKm != state.distanceKm
        state = next
        return BubbleRenderDecision(changed || force, state, if (changed) "estado_atualizado" else "estado_igual")
    }

    @Synchronized
    fun current(): BubbleRenderState = state
}

data class BubbleRenderState(
    val mode: CoreBubbleMode,
    val distanceKm: Double?,
    val updatedAtMillis: Long,
    val reason: String,
)

data class BubbleRenderDecision(
    val accepted: Boolean,
    val state: BubbleRenderState,
    val reason: String,
)
