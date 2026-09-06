package br.com.mapeiaia.rotacerta

/**
 * Distingue mudança real de tela de uma leitura parcial da mesma janela.
 * Eventos parciais são comuns enquanto preço, cronômetro, mapa ou animações
 * atualizam; eles não podem apagar uma decisão válida e fazê-la piscar.
 */
object FarolDisplayStabilityPolicy {
    const val PARTIAL_ABSENCE_CONFIRM_MILLIS = 500L // fixed_absence_window_checklist_15

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
        @Suppress("UNUSED_VARIABLE") val ignoredWindowIds = previousWindowId to currentWindowId
        @Suppress("UNUSED_VARIABLE") val ignoredVariableEvent = eventType
        val packageChanged = previousPackageName != null && currentPackageName != null &&
            previousPackageName != currentPackageName
        if (hasTwoAddresses) {
            if (packageChanged) return Action.ClearThenProcess
            if (activeAddressSignature.isNullOrBlank() || currentAddressSignature.isNullOrBlank()) {
                return Action.ProcessCurrent
            }
            return if (DestinationAddressIdentityPolicy.sameDestinationSignatures(
                    activeAddressSignature,
                    currentAddressSignature,
                )
            ) {
                Action.KeepCurrent
            } else {
                Action.ClearThenProcess
            }
        }
        if (packageChanged) return Action.ClearImmediately
        if (activeAddressSignature != null) return Action.ConfirmAbsence
        return Action.KeepCurrent
    } // compatible_partial_destination_checklist_16
 // destination_only_stability_checklist_15
 // compatible_partial_destination_checklist_16
 // destination_only_stability_checklist_15


    fun stableScreenHash(packageName: String?, addressSignature: String): Int =
        listOf(packageName.orEmpty().trim().lowercase(), addressSignature.trim())
            .joinToString("|")
            .hashCode()
}
