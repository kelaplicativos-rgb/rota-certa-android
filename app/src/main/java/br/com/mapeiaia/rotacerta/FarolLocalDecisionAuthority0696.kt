package br.com.mapeiaia.rotacerta

/**
 * 0.1.696 — local distance owns the FAROL color.
 *
 * Remote road routing may refine the displayed kilometres for the same semantic card,
 * but it is never allowed to change the Green/Red decision produced from the local
 * coordinate + Haversine radius calculation.
 */
object FarolLocalDecisionAuthority0696 {
    const val CONTRACT_MARKER = "FAROL_LOCAL_DECISION_AUTHORITY_0696"
    const val LOCAL_COMMIT_MARKER = "FAROL_LOCAL_DECISION_COMMITTED_0696"
    const val REMOTE_STARTED_MARKER = "FAROL_REMOTE_REFINEMENT_STARTED_0696"
    const val REMOTE_APPLIED_MARKER = "FAROL_REMOTE_REFINEMENT_APPLIED_0696"
    const val REMOTE_STALE_MARKER = "FAROL_REMOTE_REFINEMENT_DROPPED_STALE_0696"
    const val NO_FINAL_PAINT_MARKER = "FAROL_VALID_ADDRESS_WITHOUT_FINAL_PAINT_0696"
    const val WATCHDOG_REPAINT_MARKER = "FAROL_LOCAL_WATCHDOG_REPAINT_0696"
    const val SAME_ADDRESS_CHURN_PRESERVED_MARKER = "FAROL_SAME_ADDRESS_CHURN_PRESERVED_0696"
    const val ROAD_CANNOT_CHANGE_COLOR_MARKER = "ROAD_REFINEMENT_CANNOT_CHANGE_LOCAL_COLOR_0696"

    fun isFinalLocalDecision(result: AnalysisResult): Boolean {
        if (result.recommendation == Recommendation.InsufficientData) return false
        val distance = nearestDistanceKm(result) ?: return false
        return distance.isFinite() && distance >= 0.0
    }

    fun preserveLocalRecommendation(
        local: Recommendation,
        remoteProposal: Recommendation,
    ): Recommendation = if (local == Recommendation.InsufficientData) {
        Recommendation.InsufficientData
    } else {
        local
    }

    fun nearestDistanceKm(result: AnalysisResult): Double? =
        listOfNotNull(result.pickupToHomeKm, result.pickupToAlternativeKm)
            .filter { it.isFinite() && it >= 0.0 }
            .minOrNull()
}
