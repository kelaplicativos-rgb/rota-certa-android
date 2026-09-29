package br.com.mapeiaia.rotacerta.trips

internal object GlobalBackNavigation0689 {
    const val CONTRACT_MARKER = "GLOBAL_FIXED_BACK_NAVIGATION_0689"
    const val MAX_HISTORY = 64

    data class PopResult(
        val history: List<String>,
        val target: String?,
    )

    fun initialHistory(
        initialScreen: String,
        defaultRoot: String,
    ): List<String> =
        if (initialScreen == defaultRoot) emptyList() else listOf(defaultRoot)

    fun recordForward(
        history: List<String>,
        currentScreen: String,
        destinationScreen: String,
    ): List<String> {
        if (currentScreen == destinationScreen) return history
        return (history + currentScreen).takeLast(MAX_HISTORY)
    }

    fun pop(history: List<String>): PopResult =
        if (history.isEmpty()) {
            PopResult(history = emptyList(), target = null)
        } else {
            PopResult(
                history = history.dropLast(1),
                target = history.last(),
            )
        }

    fun canNavigateBack(
        history: List<String>,
        currentScreen: String,
        defaultRoot: String,
        nestedStageOpen: Boolean,
    ): Boolean =
        nestedStageOpen || history.isNotEmpty() || currentScreen != defaultRoot
}
