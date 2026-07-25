package br.com.mapeiaia.rotacerta

/**
 * Distingue mudança real de tela de uma leitura parcial da mesma janela.
 * Eventos parciais são comuns enquanto preço, cronômetro, mapa ou animações
 * atualizam; eles não podem apagar uma decisão válida e fazê-la piscar.
 */
object FarolDisplayStabilityPolicy {
    const val PARTIAL_ABSENCE_CONFIRM_MILLIS = 90L

    enum class Action {
        KeepCurrent,
        ProcessCurrent,
        ConfirmAbsence,
        ClearImmediately,
        ClearThenProcess,
    }

    fun decide(
        previousPackageName: String?,
        previousWindowId: Int?,
        activeAddressSignature: String?,
        currentPackageName: String?,
        currentWindowId: Int?,
        currentAddressSignature: String?,
        hasTwoAddresses: Boolean,
        eventType: Int,
    ): Action {
        val packageChanged = previousPackageName != null &&
            currentPackageName != null &&
            previousPackageName != currentPackageName
        val windowChanged = previousWindowId != null &&
            currentWindowId != null &&
            previousWindowId != currentWindowId
        val definitiveWindowEvent = eventType == AccessibilityEventFloodGate.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEventFloodGate.TYPE_WINDOWS_CHANGED
        val scrolled = eventType == AccessibilityEventFloodGate.TYPE_VIEW_SCROLLED

        if (hasTwoAddresses) {
            val destinationChanged = activeAddressSignature != null &&
                currentAddressSignature != null &&
                activeAddressSignature != currentAddressSignature
            return if (packageChanged || windowChanged || destinationChanged) {
                Action.ClearThenProcess
            } else {
                Action.ProcessCurrent
            }
        }

        if (packageChanged || (windowChanged && definitiveWindowEvent) || scrolled) {
            return Action.ClearImmediately
        }
        if (definitiveWindowEvent && activeAddressSignature != null) {
            return Action.ClearImmediately
        }
        if (activeAddressSignature != null) {
            return Action.ConfirmAbsence
        }
        return Action.KeepCurrent
    }

    fun stableScreenHash(packageName: String?, addressSignature: String): Int =
        listOf(packageName.orEmpty().trim().lowercase(), addressSignature.trim())
            .joinToString("|")
            .hashCode()
}
