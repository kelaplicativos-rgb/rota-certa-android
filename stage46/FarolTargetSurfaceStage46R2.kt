package br.com.mapeiaia.rotacerta

/**
 * Stage46 R2: raw Accessibility events are acquisition triggers, not visual-card authority.
 * Async freshness follows the concrete target surface (package/window) that supplied the card.
 */
object FarolTargetSurfaceStage46R2 {
    const val CONTRACT_MARKER = "FAROL_TARGET_SURFACE_AUTHORITY_STAGE46_R2"
    const val EVENT_TRIGGER_MARKER = "ACCESSIBILITY_EVENT_IS_TRIGGER_NOT_CARD_AUTHORITY_STAGE46_R2"
    const val FOREIGN_OVERLAY_MARKER = "FOREIGN_OVERLAY_CANNOT_REVOKE_TARGET_STAGE46_R2"
    const val WINDOW_ID_MARKER = "TARGET_WINDOW_ID_PARTICIPATES_IN_FRESHNESS_STAGE46_R2"
    const val EMPTY_TARGET_MARKER = "TARGET_EMPTY_CONTENT_REVOKES_FINAL_STAGE46_R2"
    const val SAME_TARGET_MARKER = "SAME_TARGET_WINDOW_PRESERVES_STAGE44_FINAL_STAGE46_R2"
    const val UNIVERSAL_MARKER = "TARGET_SELECTION_IS_VISUAL_NOT_PACKAGE_WHITELIST_STAGE46_R2"
    const val NO_POLLING_MARKER = "EVENT_DRIVEN_NO_POLLING_STAGE46_R2"

    private const val WINDOWS_CHANGED = 4_194_304
    private const val WINDOW_CONTENT_CHANGED = 2_048

    fun normalizePackage(value: String?): String? =
        value?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    fun isOwnOrSystemPackage(packageName: String?, ownPackageName: String): Boolean {
        val value = normalizePackage(packageName) ?: return false
        val own = normalizePackage(ownPackageName)
        return value == own || value == "com.android.systemui"
    }

    /**
     * A meaningful event package may identify a popup/overlay even when the root remains Launcher.
     * Own/SystemUI events never replace a previously known target.
     */
    fun chooseTargetPackage(
        existingTargetPackage: String?,
        currentRootPackage: String?,
        eventPackage: String?,
        ownPackageName: String,
    ): String? {
        val event = normalizePackage(eventPackage)
        if (event != null && !isOwnOrSystemPackage(event, ownPackageName)) return event
        val existing = normalizePackage(existingTargetPackage)
        if (existing != null && !isOwnOrSystemPackage(existing, ownPackageName)) return existing
        val root = normalizePackage(currentRootPackage)
        return root?.takeUnless { isOwnOrSystemPackage(it, ownPackageName) }
    }

    /**
     * WINDOWS_CHANGED is only a destructive boundary when the concrete target window previously
     * existed and is now gone/replaced. A foreign window entering/leaving is therefore harmless.
     */
    fun isTargetWindowReplacement(
        eventType: Int,
        structuralSignature: String,
        ownOverlay: Boolean,
        heavyCollect: Boolean,
        targetPackage: String?,
        previousTargetWindowId: Int,
        currentTargetWindowId: Int,
    ): Boolean {
        if (eventType != WINDOWS_CHANGED || ownOverlay || !heavyCollect) return false
        if (!structuralSignature.trim().startsWith("window-transition:")) return false
        if (normalizePackage(targetPackage) == null) return false
        if (previousTargetWindowId <= 0) return false
        return currentTargetWindowId <= 0 || currentTargetWindowId != previousTargetWindowId
    }

    /**
     * Proven card disappearance inside the same window: the event belongs to the target surface,
     * a final is currently leased, and the heavy collection sees no visual block at all.
     * This is stronger evidence than Stage44 raw-duplicate preservation.
     */
    fun provesCurrentTargetEmpty(
        eventType: Int,
        eventPackage: String?,
        currentRootPackage: String?,
        targetPackage: String?,
        ownPackageName: String,
        ownOverlay: Boolean,
        activeFinal: Boolean,
        collectedBlockCount: Int,
    ): Boolean {
        if (eventType != WINDOW_CONTENT_CHANGED || ownOverlay || !activeFinal || collectedBlockCount != 0) return false
        val event = normalizePackage(eventPackage) ?: return false
        if (isOwnOrSystemPackage(event, ownPackageName)) return false
        val target = normalizePackage(targetPackage) ?: normalizePackage(currentRootPackage) ?: return false
        return event == target
    }

    /**
     * Overlay targets are allowed to differ from the root package. Presence is proven by their
     * observed window id. Root targets may remain fresh through ordinary content churn.
     */
    fun surfaceFresh(
        token: FarolVisualEpochNoResultStage46.SurfaceToken,
        currentRootPackage: String?,
        currentTargetWindowId: Int,
        currentVisualEpoch: Long,
    ): Boolean {
        if (token.visualEpoch != currentVisualEpoch) return false
        val expected = normalizePackage(token.packageName) ?: return false
        val root = normalizePackage(currentRootPackage)
        val targetStillVisible = expected == root || currentTargetWindowId > 0
        if (!targetStillVisible) return false
        if (token.windowId > 0 && currentTargetWindowId > 0 && token.windowId != currentTargetWindowId) return false
        return true
    }
}
