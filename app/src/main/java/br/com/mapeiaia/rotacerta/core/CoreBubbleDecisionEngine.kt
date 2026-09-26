package br.com.mapeiaia.rotacerta.core

import br.com.mapeiaia.rotacerta.AnalysisResult
import br.com.mapeiaia.rotacerta.Recommendation

/**
 * Decide apenas o estado visual que deve ser pedido para a bolinha.
 * A bolinha nao decide regra de negocio; ela so apresenta este resultado.
 */
object CoreBubbleDecisionEngine {
    fun fromAnalysis(
        classification: RideScreenClassification,
        result: AnalysisResult,
        distanceKm: Double?,
    ): CoreBubbleRenderDecision {
        if (!classification.canAnalyzeRoute) {
            return CoreBubbleRenderDecision(
                mode = when (classification.kind) {
                    RideScreenKind.NotRideApp,
                    RideScreenKind.PassiveOverlay -> CoreBubbleMode.Hidden
                    RideScreenKind.RideListing,
                    RideScreenKind.PartialRideCard,
                    RideScreenKind.UnknownRideScreen -> CoreBubbleMode.Waiting
                    RideScreenKind.OpenRideCard -> CoreBubbleMode.Waiting
                },
                distanceKm = null,
                reason = classification.reason,
            )
        }

        return when (result.recommendation) {
            Recommendation.GoodRide -> CoreBubbleRenderDecision(
                mode = CoreBubbleMode.Good,
                distanceKm = distanceKm,
                reason = result.reason,
            )
            Recommendation.OutsideRadius -> CoreBubbleRenderDecision(
                mode = CoreBubbleMode.Bad,
                distanceKm = distanceKm,
                reason = result.reason,
            )
            Recommendation.InsufficientData -> CoreBubbleRenderDecision(
                mode = CoreBubbleMode.Waiting,
                distanceKm = null,
                reason = result.reason,
            )
        }
    }
}

data class CoreBubbleRenderDecision(
    val mode: CoreBubbleMode,
    val distanceKm: Double?,
    val reason: String,
)
