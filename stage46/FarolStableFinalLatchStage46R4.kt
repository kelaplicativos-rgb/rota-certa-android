package br.com.mapeiaia.rotacerta

/**
 * Stage46 R4 — stable final-decision latch.
 *
 * A proven Green/Red belongs to the confirmed card surface, not to every raw Accessibility event.
 * The final stays physically lit while that surface still owns the screen. Foreign/SystemUI churn
 * is ignored; ambiguous mutations from the same target may trigger verification without first
 * painting Yellow. Only proven surface/card replacement is allowed to revoke the final.
 */
object FarolStableFinalLatchStage46R4 {
    const val CONTRACT_MARKER = "FAROL_STABLE_FINAL_LATCH_STAGE46_R4"
    const val LATCH_MARKER = "FINAL_COLOR_STAYS_LIT_UNTIL_PROVEN_CHANGE_STAGE46_R4"
    const val FOREIGN_MARKER = "FOREIGN_CHURN_CANNOT_YELLOW_FINAL_STAGE46_R4"
    const val VERIFY_MARKER = "SAME_SURFACE_AMBIGUOUS_EVENT_VERIFIES_WITHOUT_BLINK_STAGE46_R4"
    const val CLEAR_MARKER = "PROVEN_SURFACE_CHANGE_CLEARS_IMMEDIATELY_STAGE46_R4"
    const val NEW_CARD_MARKER = "NEW_CARD_REPLACES_LATCH_ONLY_AFTER_STAGE21_STAGE46_R4"
    const val IDEMPOTENT_MARKER = "IDENTICAL_RENDER_IS_SUPPRESSED_STAGE46_R4"
    const val UNIVERSAL_MARKER = "ANY_VISIBLE_APP_OR_POPUP_CAN_ACQUIRE_STAGE46_R4"
    const val NO_POLLING_MARKER = "EVENT_DRIVEN_NO_POLLING_STAGE46_R4"

    enum class AmbiguousAction {
        NONE,
        PRESERVE_NO_VERIFY,
        PRESERVE_AND_VERIFY,
    }

    fun normalizePackage(value: String?): String? =
        value?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    fun isFinalDecision(color: String, distanceKm: Double?, signature: String?): Boolean {
        val normalized = color.trim().lowercase()
        return (normalized == "green" || normalized == "red") &&
            distanceKm?.isFinite() == true && signature?.trim()?.isNotEmpty() == true
    }

    /**
     * Decides what an ambiguous no-candidate collection may do to a final paint.
     *
     * - If the confirmed target no longer owns the visible surface, R3/R2 are free to revoke.
     * - If another package emits noise while the target still owns the screen, preserve and skip.
     * - If the target itself mutates but Accessibility cannot yet form a candidate, preserve the
     *   final while OCR verifies the current frame. This removes Red/Green -> Yellow -> Red/Green.
     */
    fun ambiguousAction(
        activeFinal: Boolean,
        evaluationPresent: Boolean,
        confirmedTargetPackage: String?,
        currentRootPackage: String?,
        eventPackage: String?,
        ownPackageName: String,
        confirmedPresence: FarolAcquisitionSurfaceStage46R3.SurfacePresence,
    ): AmbiguousAction {
        if (!activeFinal || evaluationPresent) return AmbiguousAction.NONE
        val target = normalizePackage(confirmedTargetPackage) ?: return AmbiguousAction.NONE
        val root = normalizePackage(currentRootPackage)
        val event = normalizePackage(eventPackage)
        val targetStillOwnsSurface = root == target || confirmedPresence.interactive
        if (!targetStillOwnsSurface) return AmbiguousAction.NONE

        if (event == target) return AmbiguousAction.PRESERVE_AND_VERIFY

        // Source-less churn while the target is the concrete root may represent an image-only
        // mutation; verify it without changing the public color first.
        if (event == null && root == target) return AmbiguousAction.PRESERVE_AND_VERIFY

        // Any foreign/SystemUI/own/host event cannot take away a final from a still-owned target.
        // The target app will emit its own mutation event if its card actually changes.
        @Suppress("UNUSED_VARIABLE") val universalNoWhitelist = ownPackageName
        return AmbiguousAction.PRESERVE_NO_VERIFY
    }

    fun sameRenderedDecision(
        currentColor: String,
        currentDistanceKm: Double?,
        requestedColor: String,
        requestedDistanceKm: Double?,
    ): Boolean {
        if (!currentColor.trim().equals(requestedColor.trim(), ignoreCase = true)) return false
        if (currentDistanceKm == null || requestedDistanceKm == null) {
            return currentDistanceKm == null && requestedDistanceKm == null
        }
        if (!currentDistanceKm.isFinite() || !requestedDistanceKm.isFinite()) return false
        return kotlin.math.abs(currentDistanceKm - requestedDistanceKm) <= 0.0005
    }
}
