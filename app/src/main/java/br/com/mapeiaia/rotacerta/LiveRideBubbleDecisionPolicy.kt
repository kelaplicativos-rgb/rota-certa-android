package br.com.mapeiaia.rotacerta

/**
 * Regra central da bolinha.
 *
 * Este modulo existe para impedir que cor/km sejam decididos dentro do servico de
 * acessibilidade por varias condicoes espalhadas. A bolinha so pode mostrar uma
 * decisao final quando o fluxo minimo foi confirmado:
 * app monitorado ativo + card cadastrado confirmado + destino identificado + resultado calculado.
 */
class LiveRideBubbleDecisionPolicy {
    fun decide(input: LiveRideBubbleDecisionInput): LiveRideBubbleDecision {
        if (!input.monitoredPackageActive) {
            return LiveRideBubbleDecision(
                signal = LiveRideBubbleSignal.Idle,
                distanceKm = null,
                reason = "Nenhum app monitorado ativo; bolinha em espera.",
                shouldClearActiveDecision = true,
            )
        }

        if (!input.registeredCardMatched) {
            return LiveRideBubbleDecision(
                signal = LiveRideBubbleSignal.WaitingForRegisteredCard,
                distanceKm = null,
                reason = "Tela nao confirmada por card cadastrado; farol bloqueado.",
                shouldClearActiveDecision = true,
            )
        }

        if (!input.destinationIdentified) {
            return LiveRideBubbleDecision(
                signal = LiveRideBubbleSignal.WaitingForData,
                distanceKm = null,
                reason = "Card cadastrado visivel, mas destino final ainda nao foi identificado.",
                shouldClearActiveDecision = true,
            )
        }

        val result = input.result ?: return LiveRideBubbleDecision(
            signal = LiveRideBubbleSignal.WaitingForData,
            distanceKm = null,
            reason = "Card cadastrado visivel, mas analise ainda nao retornou resultado.",
            shouldClearActiveDecision = true,
        )

        return when (result.recommendation) {
            Recommendation.GoodRide -> LiveRideBubbleDecision(
                signal = LiveRideBubbleSignal.Accept,
                distanceKm = input.nearestConfiguredDistanceKm,
                reason = result.reason,
                shouldClearActiveDecision = false,
            )
            Recommendation.OutsideRadius -> LiveRideBubbleDecision(
                signal = LiveRideBubbleSignal.Reject,
                distanceKm = input.nearestConfiguredDistanceKm,
                reason = result.reason,
                shouldClearActiveDecision = false,
            )
            Recommendation.InsufficientData -> LiveRideBubbleDecision(
                signal = LiveRideBubbleSignal.WaitingForData,
                distanceKm = null,
                reason = result.reason,
                shouldClearActiveDecision = true,
            )
        }
    }
}

data class LiveRideBubbleDecisionInput(
    val monitoredPackageActive: Boolean,
    val registeredCardMatched: Boolean,
    val destinationIdentified: Boolean,
    val result: AnalysisResult?,
    val nearestConfiguredDistanceKm: Double?,
)

data class LiveRideBubbleDecision(
    val signal: LiveRideBubbleSignal,
    val distanceKm: Double?,
    val reason: String,
    val shouldClearActiveDecision: Boolean,
)

enum class LiveRideBubbleSignal {
    Idle,
    WaitingForRegisteredCard,
    WaitingForData,
    Accept,
    Reject,
}
