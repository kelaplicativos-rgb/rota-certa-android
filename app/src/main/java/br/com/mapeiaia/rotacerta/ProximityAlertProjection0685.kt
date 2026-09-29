package br.com.mapeiaia.rotacerta

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 0.1.685 — autoridade única do pop-up de radares/alertas.
 *
 * O núcleo compartilhado de localização publica a correção e o visual calculado.
 * O serviço de acessibilidade apenas renderiza esse estado; ele não executa outro
 * GPS nem outro DirectionalProximityAlertEngine.
 */
internal data class ProximityAlertProjectionState0685(
    val visual: DirectionalAlertVisual? = null,
    val latestFix: PreciseNavigationFix? = null,
    val popupTimeoutMillis: Long = 0L,
    val generation: Long = 0L,
)

internal object ProximityAlertProjection0685 {
    const val CONTRACT_MARKER = "SINGLE_CORE_POPUP_AUTHORITY_0685"

    private val mutableState = MutableStateFlow(ProximityAlertProjectionState0685())
    val state: StateFlow<ProximityAlertProjectionState0685> = mutableState.asStateFlow()

    @Synchronized
    fun publishFix(fix: PreciseNavigationFix) {
        val current = mutableState.value
        mutableState.value = current.copy(
            latestFix = fix,
            generation = current.generation + 1L,
        )
    }

    @Synchronized
    fun publishVisual(
        visual: DirectionalAlertVisual,
        fix: PreciseNavigationFix,
        popupTimeoutMillis: Long,
    ) {
        val current = mutableState.value
        mutableState.value = current.copy(
            visual = visual,
            latestFix = fix,
            popupTimeoutMillis = popupTimeoutMillis.coerceAtLeast(0L),
            generation = current.generation + 1L,
        )
    }

    @Synchronized
    fun clearVisual(targetId: String? = null) {
        val current = mutableState.value
        val activeTargetId = current.visual?.targetId
        if (targetId != null && activeTargetId != null && targetId != activeTargetId) return
        if (current.visual == null && current.popupTimeoutMillis == 0L) return
        mutableState.value = current.copy(
            visual = null,
            popupTimeoutMillis = 0L,
            generation = current.generation + 1L,
        )
    }

    @Synchronized
    fun clearAll() {
        val current = mutableState.value
        mutableState.value = ProximityAlertProjectionState0685(
            generation = current.generation + 1L,
        )
    }

    fun popupTimeoutMillis(settings: AppSettings): Long = when (settings.proximityPopupTimeoutSeconds) {
        15, 20, 30 -> settings.proximityPopupTimeoutSeconds * 1_000L
        else -> 0L
    }
}
