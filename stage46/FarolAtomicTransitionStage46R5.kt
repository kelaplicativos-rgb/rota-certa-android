package br.com.mapeiaia.rotacerta

/**
 * Stage46 R5 — atomic visual transition.
 *
 * Once a real card/surface change is proven, the old final must lose authority immediately and the
 * same accessibility event must already arm acquisition of the current surface. No second event is
 * required to start OCR. R4 still owns stable Green/Red latching while the same semantic card stays
 * valid; R5 only acts after R2/R3/R4 have proved a real replacement/disappearance.
 */
object FarolAtomicTransitionStage46R5 {
    const val CONTRACT_MARKER = "FAROL_ATOMIC_TRANSITION_STAGE46_R5"
    const val CLEAR_MARKER = "PROVEN_CHANGE_CLEARS_OLD_FINAL_SAME_EVENT_STAGE46_R5"
    const val REARM_MARKER = "CLEAR_AND_REARM_ARE_ATOMIC_STAGE46_R5"
    const val TARGET_EMPTY_MARKER = "TARGET_EMPTY_REQUESTS_CURRENT_SURFACE_OCR_SAME_EVENT_STAGE46_R5"
    const val CANDIDATE_MARKER = "PROVEN_NEW_CANDIDATE_CONTINUES_SAME_CYCLE_STAGE46_R5"
    const val NO_SECOND_EVENT_MARKER = "NO_SECOND_ACCESSIBILITY_EVENT_REQUIRED_FOR_REARM_STAGE46_R5"
    const val EPOCH_MARKER = "NEXT_ACQUISITION_STARTS_AFTER_OLD_EPOCH_INVALIDATION_STAGE46_R5"
    const val STALE_MARKER = "OLD_OCR_ROUTE_PAINT_CANNOT_CROSS_ATOMIC_TRANSITION_STAGE46_R5"
    const val LATCH_MARKER = "R4_FINAL_LATCH_SURVIVES_NON_SEMANTIC_CHURN_STAGE46_R5"
    const val UNIVERSAL_MARKER = "CURRENT_VISIBLE_SURFACE_REARMS_WITHOUT_PACKAGE_WHITELIST_STAGE46_R5"
    const val NO_POLLING_MARKER = "EVENT_DRIVEN_SINGLE_SHOT_REARM_NO_POLLING_STAGE46_R5"

    enum class RearmAction {
        NONE,
        PROCESS_CANDIDATE_SAME_CYCLE,
        REQUEST_SINGLE_SHOT_OCR_NOW,
    }

    /**
     * Called only after another stage has already proved a real transition and cleared old authority.
     * A candidate already produced by Accessibility continues synchronously; otherwise one immediate
     * OCR acquisition is requested from the current surface. This is a one-shot consequence of the
     * event, never a periodic scanner.
     */
    fun actionAfterProvenClear(
        readingEnabled: Boolean,
        serviceReady: Boolean,
        bubbleGestureActive: Boolean,
        candidatePresent: Boolean,
    ): RearmAction {
        if (!readingEnabled || !serviceReady || bubbleGestureActive) return RearmAction.NONE
        return if (candidatePresent) {
            RearmAction.PROCESS_CANDIDATE_SAME_CYCLE
        } else {
            RearmAction.REQUEST_SINGLE_SHOT_OCR_NOW
        }
    }

    fun nextEpochIsFresh(previousEpoch: Long, currentEpoch: Long): Boolean =
        currentEpoch > previousEpoch
}
