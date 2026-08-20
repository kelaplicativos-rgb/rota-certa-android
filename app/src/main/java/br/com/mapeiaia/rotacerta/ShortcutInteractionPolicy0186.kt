package br.com.mapeiaia.rotacerta

/** Contrato determinístico dos gestos da grade, sem temporizador concorrente. */
data class ShortcutPressState0186(
    val moved: Boolean = false,
    val longConsumed: Boolean = false,
)

enum class ShortcutReleaseAction0186 {
    None,
    Quick,
}

object ShortcutInteractionPolicy0186 {
    const val CONTRACT_MARKER = "SHORTCUT_INTERACTION_0186"
    const val HOLD_MILLIS: Long = 1_500L

    fun moved(state: ShortcutPressState0186): ShortcutPressState0186 =
        state.copy(moved = true)

    fun consumeLong(state: ShortcutPressState0186): ShortcutPressState0186 =
        if (state.moved) state else state.copy(longConsumed = true)

    fun release(state: ShortcutPressState0186): ShortcutReleaseAction0186 = when {
        state.moved -> ShortcutReleaseAction0186.None
        state.longConsumed -> ShortcutReleaseAction0186.None
        else -> ShortcutReleaseAction0186.Quick
    }
}

object HomeLaunchPolicy0186 {
    const val CONTRACT_MARKER = "EXPLICIT_HOME_LAUNCH_0186"
    const val MODE_COLLAPSED = "collapsed"
    const val MODE_MODULE = "module"

    fun requestedModule(mode: String?, requestedId: String?): String? = when (mode) {
        MODE_COLLAPSED -> null
        MODE_MODULE -> requestedId?.takeIf { it.isNotBlank() }
        else -> requestedId?.takeIf { it.isNotBlank() } // compatibilidade com atalhos antigos
    }
}
