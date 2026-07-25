package br.com.mapeiaia.rotacerta

import java.util.Locale

enum class BubbleLifecycleState {
    Idle,
    WaitingForRegisteredCard,
    AnalyzingRegisteredCard,
    ShowingDecision,
}

class BubbleStateMachine {
    var state: BubbleLifecycleState = BubbleLifecycleState.Idle
        private set

    private var activeToken: BubbleAnalysisToken? = null

    fun markIdle() {
        state = BubbleLifecycleState.Idle
        activeToken = null
    }

    fun markWaitingForRegisteredCard() {
        state = BubbleLifecycleState.WaitingForRegisteredCard
        activeToken = null
    }

    fun markAnalyzing(token: BubbleAnalysisToken) {
        state = BubbleLifecycleState.AnalyzingRegisteredCard
        activeToken = token
    }

    fun markDecision(token: BubbleAnalysisToken) {
        if (activeToken == token) {
            state = BubbleLifecycleState.ShowingDecision
        }
    }

    fun clearCardDecision() {
        state = BubbleLifecycleState.WaitingForRegisteredCard
        activeToken = null
    }

    fun activeAnalysisToken(): BubbleAnalysisToken? = activeToken

    fun canApplyResult(
        token: BubbleAnalysisToken,
        currentPackageName: String?,
        currentSnapshotHash: Int?,
    ): Boolean {
        val normalizedPackage = currentPackageName
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
            ?: return false
        return activeToken == token &&
            token.packageName == normalizedPackage &&
            currentSnapshotHash == token.snapshotHash
    }
}
