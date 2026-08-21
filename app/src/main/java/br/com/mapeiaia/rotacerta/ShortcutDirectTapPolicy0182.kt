package br.com.mapeiaia.rotacerta

object ShortcutDirectTapPolicy0182 {
    const val CONTRACT_MARKER = "SHORTCUT_DIRECT_TAP_0182"
    const val MAX_TAPS_FROM_MAIN_BUBBLE_TO_ACTION = 2

    fun actionForTap(
        @Suppress("UNUSED_PARAMETER") persistedAction: ShortcutGestureAction0180,
    ): ShortcutGestureAction0180 = ShortcutGestureAction0180.PRIMARY_ACTION
}

// direct_shortcuts_two_taps_0_1_182
