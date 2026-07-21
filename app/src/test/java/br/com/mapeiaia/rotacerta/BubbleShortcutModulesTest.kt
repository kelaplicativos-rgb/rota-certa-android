package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleShortcutModulesTest {
    @Test
    fun catalogContainsSixteenIndependentModulesInDisplayOrder() {
        BubbleShortcutCatalog.requireValid()

        assertEquals(
            listOf(
                "route",
                "destination",
                "alerts",
                "appearance",
                "permissions",
                "backup",
                "reports",
                "whatsapp",
                "collector",
                "clear_clipboard",
                "diagnostic",
                "stop_app",
                "alert",
                "saved_place",
                "ride_card",
                "reading",
            ),
            BubbleShortcutCatalog.modules.map { it.spec.id },
        )
        assertEquals(16, BubbleShortcutCatalog.modules.map { it::class }.distinct().size)
        assertEquals(16, BubbleShortcutCatalog.modules.map { it.spec.action }.distinct().size)
        assertFalse(BubbleShortcutCatalog.modules.any { it.spec.id == "settings" })
    }

    @Test
    fun homeControlActionsWereTransferredToPopup() {
        val transferred = BubbleShortcutCatalog.modules.take(12).map { it.spec.displayLabel }
        assertEquals(
            listOf(
                "Rota",
                "Destino",
                "Alertas",
                "Aparência",
                "Permissão",
                "Backup",
                "Relatórios",
                "WhatsApp",
                "Coletor",
                "Limpar",
                "Depurar",
                "Encerrar",
            ),
            transferred,
        )
    }

    @Test
    fun alertAndSavedPlaceKeepEditableQuickActions() {
        val alert = AlertBubbleShortcutModule.spec
        assertEquals("Alerta", alert.defaultName)
        assertEquals("alerts", alert.targetGroup)
        assertEquals(BubbleShortcutAction.CreateAlert, alert.action)
        assertEquals("Alerta", alert.displayLabel)

        val local = SavedPlaceBubbleShortcutModule.spec
        assertEquals("Local salvo", local.defaultName)
        assertEquals("alerts", local.targetGroup)
        assertEquals(BubbleShortcutAction.CreateSavedPlace, local.action)
        assertEquals("Local", local.displayLabel)
    }

    @Test
    fun directActionsRemainAvailableWithoutOpeningHomeMenu() {
        assertEquals(BubbleShortcutAction.ToggleReading, ReadingBubbleShortcutModule.spec.action)
        assertEquals(BubbleShortcutAction.OpenScreenWhatsApp, WhatsAppBubbleShortcutModule.spec.action)
        assertEquals(BubbleShortcutAction.OpenCollector, CollectorBubbleShortcutModule.spec.action)
        assertEquals(BubbleShortcutAction.ClearClipboard, ClearClipboardBubbleShortcutModule.spec.action)
        assertEquals(BubbleShortcutAction.ExportDiagnostic, DiagnosticBubbleShortcutModule.spec.action)
        assertEquals(BubbleShortcutAction.StopApplication, StopBubbleShortcutModule.spec.action)
        assertNull(WhatsAppBubbleShortcutModule.spec.targetGroup)
        assertNull(StopBubbleShortcutModule.spec.targetGroup)
    }

    @Test
    fun navigationModulesDeclareTheirDestinationGroups() {
        listOf(
            RouteBubbleShortcutModule.spec,
            DestinationBubbleShortcutModule.spec,
            AlertsManagementBubbleShortcutModule.spec,
            AppearanceBubbleShortcutModule.spec,
            PermissionsBubbleShortcutModule.spec,
            BackupBubbleShortcutModule.spec,
            ReportsBubbleShortcutModule.spec,
        ).forEach { spec ->
            assertTrue(!spec.targetGroup.isNullOrBlank())
            assertTrue(!spec.targetTab.isNullOrBlank())
        }
        assertNull(RideCardBubbleShortcutModule.spec.targetGroup)
    }
}
