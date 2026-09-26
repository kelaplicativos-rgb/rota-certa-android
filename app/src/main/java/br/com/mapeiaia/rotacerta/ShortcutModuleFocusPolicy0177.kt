package br.com.mapeiaia.rotacerta

object ShortcutModuleFocusPolicy0177 {
    const val CONTRACT_MARKER = "SHORTCUT_MODULE_IDENTITY_FOCUS_0177"

    fun routesByModuleIdentity(action: BubbleShortcutAction): Boolean = when (action) {
        BubbleShortcutAction.OpenRoute,
        BubbleShortcutAction.OpenDestination,
        BubbleShortcutAction.OpenAlerts,
        BubbleShortcutAction.OpenSavedPlaces,
        BubbleShortcutAction.OpenRadars,
        BubbleShortcutAction.OpenAppearance,
        BubbleShortcutAction.OpenPermissions,
        BubbleShortcutAction.OpenBackup,
        BubbleShortcutAction.OpenReports,
        BubbleShortcutAction.OpenSettings,
        BubbleShortcutAction.OpenTextCorrection,
        -> true
        else -> false
    }
}
