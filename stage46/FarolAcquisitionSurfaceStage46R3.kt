package br.com.mapeiaia.rotacerta

/**
 * Stage46 R3: acquisition is allowed to follow the currently observed foreground/popup surface
 * without granting that surface final authority. A target becomes confirmed only after the
 * existing Stage21 semantic validation accepts a two-address candidate.
 */
object FarolAcquisitionSurfaceStage46R3 {
    const val CONTRACT_MARKER = "FAROL_ACQUISITION_SURFACE_STAGE46_R3"
    const val SEPARATION_MARKER = "ACQUISITION_SURFACE_DIFFERS_FROM_CONFIRMED_TARGET_STAGE46_R3"
    const val RELEASE_MARKER = "CONFIRMED_TARGET_RELEASED_WHEN_FINAL_REVOKED_STAGE46_R3"
    const val FOREGROUND_MARKER = "FOREGROUND_SURFACE_CAN_ACQUIRE_AFTER_OLD_TARGET_STAGE46_R3"
    const val OCR_MARKER = "OCR_ACQUISITION_NOT_PINNED_TO_STALE_CONFIRMED_TARGET_STAGE46_R3"
    const val PROMOTION_MARKER = "TARGET_PROMOTED_ONLY_AFTER_SEMANTIC_VALIDATION_STAGE46_R3"
    const val FOREIGN_MARKER = "FOREIGN_OVERLAY_CANNOT_STEAL_INTERACTIVE_TARGET_STAGE46_R3"
    const val HANDOFF_MARKER = "PROVEN_FOREGROUND_HANDOFF_CLEARS_OLD_FINAL_STAGE46_R3"
    const val OFF_MARKER = "MANUAL_OFF_RELEASES_CONFIRMED_TARGET_STAGE46_R3"
    const val NO_POLLING_MARKER = "EVENT_DRIVEN_NO_POLLING_STAGE46_R3"

    private const val WINDOW_STATE_CHANGED = 32
    private const val WINDOWS_CHANGED = 4_194_304

    data class SurfacePresence(
        val windowId: Int = 0,
        val active: Boolean = false,
        val focused: Boolean = false,
        val layer: Int = Int.MIN_VALUE,
    ) {
        val interactive: Boolean get() = windowId > 0 && (active || focused)
    }

    data class AcquisitionDecision(
        val packageName: String?,
        val reason: String,
    )

    fun normalizePackage(value: String?): String? =
        value?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    fun isSystemOrOwn(packageName: String?, ownPackageName: String): Boolean {
        val value = normalizePackage(packageName) ?: return false
        val own = normalizePackage(ownPackageName)
        return value == own || value == "com.android.systemui"
    }

    fun isPassiveHost(packageName: String?, ownPackageName: String): Boolean {
        val value = normalizePackage(packageName) ?: return true
        if (isSystemOrOwn(value, ownPackageName)) return true
        return value == "com.sec.android.app.launcher" ||
            value == "com.android.launcher3" ||
            value == "com.google.android.apps.nexuslauncher" ||
            value.endsWith(".launcher") || value.contains("launcher3")
    }

    /**
     * Acquisition policy:
     * 1) a confirmed popup that is still active/focused remains the acquisition surface, even when
     *    it overlays another foreground application;
     * 2) if the old target is merely listed in the background, the real foreground application wins
     *    acquisition immediately (physical com.comuto -> com.app99.driver case);
     * 3) on passive hosts without an interactive confirmed popup, a meaningful popup event can acquire;
     * 4) final authority is NOT granted here; Stage21 still decides promotion later.
     */
    fun chooseAcquisitionPackage(
        confirmedTargetPackage: String?,
        currentRootPackage: String?,
        eventPackage: String?,
        ownPackageName: String,
        confirmedPresence: SurfacePresence,
    ): AcquisitionDecision {
        val target = normalizePackage(confirmedTargetPackage)
        val root = normalizePackage(currentRootPackage)
        val event = normalizePackage(eventPackage)
        val targetInteractive = target != null &&
            (confirmedPresence.interactive || (root == target && confirmedPresence.windowId > 0))

        if (targetInteractive) {
            return AcquisitionDecision(
                target,
                if (root == target) "same_foreground_target" else "interactive_confirmed_popup",
            )
        }

        if (root != null && !isPassiveHost(root, ownPackageName)) {
            return AcquisitionDecision(
                packageName = root,
                reason = if (root == target) "same_foreground_target" else "foreground_root_acquisition",
            )
        }

        if (event != null && !isSystemOrOwn(event, ownPackageName) && !isPassiveHost(event, ownPackageName)) {
            return AcquisitionDecision(event, "popup_event_acquisition")
        }

        if (root != null && !isSystemOrOwn(root, ownPackageName)) {
            return AcquisitionDecision(root, "passive_root_acquisition")
        }

        return AcquisitionDecision(null, "no_acquisition_surface")
    }

    /**
     * Fast old-final revocation is allowed only on a structural window handoff and only when the
     * confirmed target is no longer interactive. A popup still active/focused over another app is
     * therefore preserved. Ordinary CONTENT_CHANGED never enters this path; R2 semantic/empty proof
     * continues to handle same-window card disappearance.
     */
    fun provesForegroundSurfaceHandoff(
        eventType: Int,
        heavyCollect: Boolean,
        ownOverlay: Boolean,
        activeFinal: Boolean,
        confirmedTargetPackage: String?,
        currentRootPackage: String?,
        ownPackageName: String,
        confirmedPresence: SurfacePresence,
    ): Boolean {
        if (!heavyCollect || ownOverlay || !activeFinal) return false
        if (eventType != WINDOW_STATE_CHANGED && eventType != WINDOWS_CHANGED) return false
        val target = normalizePackage(confirmedTargetPackage) ?: return false
        val root = normalizePackage(currentRootPackage) ?: return false
        if (root == target) return false
        if (isPassiveHost(root, ownPackageName)) return false
        if (confirmedPresence.interactive) return false
        return true
    }
}
