package br.com.mapeiaia.rotacerta

import java.util.Locale

/**
 * Stage 16 keeps the currently visible coherent card above weak event/package attribution.
 * It does not parse addresses and never authorizes a route on its own.
 */
object FarolVisibleCardPriorityStage16 {
    const val CONTRACT_MARKER = "VISIBLE_CARD_PRIORITY_AND_TRANSIENT_EMPTY_STAGE16"
    const val EXACT_ACCEPTED_GATE_CACHE_MARKER = "EXACT_ACCEPTED_VISUAL_GATE_CACHE_STAGE16"

    enum class WindowKind { APPLICATION, SYSTEM, INPUT_METHOD, ACCESSIBILITY_OVERLAY, OTHER }
    enum class WindowSelectionOutcome { AUTHORIZED_SELECTED_WINDOW, BLOCKED_BY_APPLICATION, NO_DECISIVE_WINDOW }
    enum class EmptyReadAction { CONFIRM_CURRENT_VISUAL, CLEAR_WITHOUT_PRESERVATION }
    enum class EmptyVisualConfirmation { SAME_CARD, DIFFERENT_CARD, CONFIRMED_ABSENT, AMBIGUOUS }

    data class WindowEvidence(
        val windowId: Int,
        val packageName: String?,
        val layer: Int,
        val kind: WindowKind,
        val hasRoot: Boolean,
    )

    data class VisibleWindowAuthority(
        val packageName: String,
        val windowId: Int,
        val layer: Int,
    )

    data class WindowSelection(
        val outcome: WindowSelectionOutcome,
        val authority: VisibleWindowAuthority? = null,
    )

    data class BlockEvidence(
        val id: String,
        val parentId: String?,
        val packageName: String,
        val windowId: Int,
        val windowLayer: Int,
        val depth: Int,
        val text: String,
        val source: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val syntheticRoot: Boolean,
    )

    data class GateSnapshotIdentity(
        val packageName: String,
        val sessionGeneration: Long,
        val expectedWindowId: Int,
        val screenGeneration: Long,
        val windowGeneration: Long,
        val blocks: List<BlockEvidence>,
    )

    data class ActiveCardBinding(
        val packageName: String,
        val sessionGeneration: Long,
        val windowId: Int,
        val screenGeneration: Long,
        val windowGeneration: Long,
        val screenHash: Int,
        val addressSignature: String,
    )

    fun selectVisibleAuthorizedWindow(
        windows: List<WindowEvidence>,
        selectedPackages: Set<String>,
    ): WindowSelection {
        val selected = selectedPackages.mapNotNull(::normalizePackage).toSet()
        if (selected.isEmpty()) return WindowSelection(WindowSelectionOutcome.NO_DECISIVE_WINDOW)
        for (window in windows.sortedWith(compareByDescending<WindowEvidence> { it.layer }.thenByDescending { it.windowId })) {
            if (!window.hasRoot) continue
            val pkg = normalizePackage(window.packageName)
            if (pkg != null && pkg in selected) {
                return WindowSelection(
                    outcome = WindowSelectionOutcome.AUTHORIZED_SELECTED_WINDOW,
                    authority = VisibleWindowAuthority(pkg, window.windowId, window.layer),
                )
            }
            // A real application above the selected ride window is positive visual evidence
            // that the selected card is not the current application visual authority. SystemUI,
            // IME and accessibility overlays are only transient wrappers and cannot block alone.
            if (window.kind == WindowKind.APPLICATION) {
                return WindowSelection(WindowSelectionOutcome.BLOCKED_BY_APPLICATION)
            }
        }
        return WindowSelection(WindowSelectionOutcome.NO_DECISIVE_WINDOW)
    }

    fun gateSnapshotIdentity(
        packageName: String,
        sessionGeneration: Long,
        expectedWindowId: Int,
        screenGeneration: Long,
        windowGeneration: Long,
        blocks: List<BlockEvidence>,
    ): GateSnapshotIdentity = GateSnapshotIdentity(
        packageName = normalizePackage(packageName).orEmpty(),
        sessionGeneration = sessionGeneration,
        expectedWindowId = expectedWindowId,
        screenGeneration = screenGeneration,
        windowGeneration = windowGeneration,
        // Equality is over the exact current visual structure/text, not a lossy hash.
        blocks = blocks.sortedWith(
            compareByDescending<BlockEvidence> { it.windowLayer }
                .thenBy { it.windowId }
                .thenBy { it.id },
        ),
    )

    fun canReuseAcceptedAuthorization(
        cached: GateSnapshotIdentity?,
        current: GateSnapshotIdentity,
        cachedPackageName: String?,
        cachedWindowId: Int?,
        cachedAddressSignature: String?,
        cachedScreenHash: Int?,
        activePackageName: String?,
        activeAddressSignature: String?,
        activeScreenHash: Int?,
        routeInFlight: Boolean,
        stableDecision: Boolean,
        transientEmptyPending: Boolean,
    ): Boolean {
        if (transientEmptyPending) return false
        if (!routeInFlight && !stableDecision) return false
        if (cached == null || cached != current) return false
        val currentPackage = normalizePackage(current.packageName)
        if (normalizePackage(cachedPackageName) != currentPackage) return false
        if (cachedWindowId != current.expectedWindowId) return false
        if (normalizePackage(activePackageName) != currentPackage) return false
        if (cachedAddressSignature.isNullOrBlank() || cachedAddressSignature != activeAddressSignature) return false
        if (cachedScreenHash == null || cachedScreenHash != activeScreenHash) return false
        return true
    }

    fun emptyReadAction(activeCardBinding: ActiveCardBinding?): EmptyReadAction =
        if (activeCardBinding == null) EmptyReadAction.CLEAR_WITHOUT_PRESERVATION
        else EmptyReadAction.CONFIRM_CURRENT_VISUAL

    fun pendingMatches(binding: ActiveCardBinding?, routeBinding: ActiveCardBinding): Boolean =
        binding == routeBinding

    fun classifyEmptyVisualConfirmation(
        active: ActiveCardBinding,
        selection: WindowSelection,
        confirmedCard: ActiveCardBinding?,
    ): EmptyVisualConfirmation {
        when (selection.outcome) {
            WindowSelectionOutcome.BLOCKED_BY_APPLICATION -> return EmptyVisualConfirmation.CONFIRMED_ABSENT
            WindowSelectionOutcome.NO_DECISIVE_WINDOW -> return EmptyVisualConfirmation.AMBIGUOUS
            WindowSelectionOutcome.AUTHORIZED_SELECTED_WINDOW -> Unit
        }
        val authority = selection.authority ?: return EmptyVisualConfirmation.AMBIGUOUS
        if (normalizePackage(authority.packageName) != normalizePackage(active.packageName) ||
            authority.windowId != active.windowId
        ) {
            return EmptyVisualConfirmation.DIFFERENT_CARD
        }
        val confirmed = confirmedCard ?: return EmptyVisualConfirmation.AMBIGUOUS
        return if (normalizePackage(confirmed.packageName) == normalizePackage(active.packageName) &&
            confirmed.windowId == active.windowId &&
            confirmed.sessionGeneration == active.sessionGeneration &&
            confirmed.screenGeneration == active.screenGeneration &&
            confirmed.windowGeneration == active.windowGeneration &&
            confirmed.screenHash == active.screenHash &&
            confirmed.addressSignature == active.addressSignature
        ) {
            EmptyVisualConfirmation.SAME_CARD
        } else {
            EmptyVisualConfirmation.DIFFERENT_CARD
        }
    }

    fun routeResultMayPaint(bindingFresh: Boolean, transientEmptyPendingForBinding: Boolean): Boolean =
        bindingFresh && !transientEmptyPendingForBinding

    fun hasCoherentAbsenceEvidence(
        expectedPackageName: String,
        expectedWindowId: Int,
        blocks: List<BlockEvidence>,
    ): Boolean {
        val expected = normalizePackage(expectedPackageName) ?: return false
        return blocks.any { block ->
            normalizePackage(block.packageName) == expected &&
                block.windowId == expectedWindowId &&
                block.text.isNotBlank()
        }
    }

    private fun normalizePackage(value: String?): String? = value
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf(String::isNotBlank)
}
