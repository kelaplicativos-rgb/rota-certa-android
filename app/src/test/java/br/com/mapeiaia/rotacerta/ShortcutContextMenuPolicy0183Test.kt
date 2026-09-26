package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutContextMenuPolicy0183Test {
    @Test
    fun importantQuickActionsUseExplicitUserFacingLabels() {
        assertEquals(
            "Criar alerta aqui",
            ShortcutContextMenuPolicy0183.quickActionLabel(
                "alerts",
                BubbleShortcutQuickAction.CreateNamedAlertAtCurrentLocation,
            ),
        )
        assertEquals(
            "Criar radar neste local",
            ShortcutContextMenuPolicy0183.quickActionLabel(
                "radars",
                BubbleShortcutQuickAction.CreateRadarAtCurrentLocation,
            ),
        )
        assertEquals(
            "Usar localização atual como destino",
            ShortcutContextMenuPolicy0183.quickActionLabel(
                "destination",
                BubbleShortcutQuickAction.DefineDestinationAtCurrentLocation,
            ),
        )
        assertEquals(
            "Capturar aplicativo e tela agora",
            ShortcutContextMenuPolicy0183.quickActionLabel(
                "manual_capture",
                BubbleShortcutQuickAction.CaptureCurrentAppAndScreen,
            ),
        )
        assertEquals(
            "Abrir link principal",
            ShortcutContextMenuPolicy0183.quickActionLabel(
                "quick_links",
                BubbleShortcutQuickAction.OpenPrimaryQuickLink,
            ),
        )
    }

    @Test
    fun clearUsesItsDedicatedActionsInsteadOfGenericQuickAction() {
        assertNull(
            ShortcutContextMenuPolicy0183.quickActionLabel(
                "clear_clipboard",
                BubbleShortcutQuickAction.ClearApplicationCache,
            ),
        )
    }

    @Test
    fun modulesHaveSpecificOpenLabels() {
        assertEquals("Abrir módulo Alertas", ShortcutContextMenuPolicy0183.primaryActionLabel("alerts"))
        assertEquals("Abrir módulo Radares", ShortcutContextMenuPolicy0183.primaryActionLabel("radars"))
        assertEquals("Abrir módulo Destino", ShortcutContextMenuPolicy0183.primaryActionLabel("destination"))
        assertEquals("Abrir aplicativos e cards", ShortcutContextMenuPolicy0183.primaryActionLabel("manual_capture"))
        assertEquals("Abrir módulo Links rápidos", ShortcutContextMenuPolicy0183.primaryActionLabel("quick_links"))
    }
}
