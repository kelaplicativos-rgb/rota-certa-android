package br.com.mapeiaia.rotacerta

/** Mantém o fechamento manual limitado à aproximação atual e somente aos alvos ativos. */
class ApproachDismissGate0178 {
    private val dismissedTargetIds = mutableSetOf<String>()

    fun dismissUntilExit(targetId: String) {
        if (targetId.isNotBlank()) dismissedTargetIds += targetId
    }

    fun isDismissed(targetId: String): Boolean = targetId in dismissedTargetIds

    fun clearAfterExit(targetId: String) {
        dismissedTargetIds -= targetId
    }

    fun retainActive(activeTargetIds: Set<String>) {
        dismissedTargetIds.retainAll(activeTargetIds)
    }
}
