package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutModuleFocusPolicy0177Test {
    @Test
    fun inlineHomeModulesRouteByTheirOwnIdentity() {
        val routed = listOf(
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
        )
        routed.forEach { action ->
            assertTrue(action.name, ShortcutModuleFocusPolicy0177.routesByModuleIdentity(action))
        }
        assertFalse(ShortcutModuleFocusPolicy0177.routesByModuleIdentity(BubbleShortcutAction.OpenFinance))
        assertFalse(ShortcutModuleFocusPolicy0177.routesByModuleIdentity(BubbleShortcutAction.ClearClipboard))
    }
}
