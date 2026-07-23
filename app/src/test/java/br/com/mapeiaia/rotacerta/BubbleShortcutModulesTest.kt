package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleShortcutModulesTest {
    @Test
    fun catalogContainsFourteenIndependentModulesInDisplayOrder() {
        BubbleShortcutCatalog.requireValid()

        assertEquals(
            listOf(
                "route",
                "destination",
                "alerts",
                "saved_places",
                "radars",
                "appearance",
                "backup",
                "whatsapp",
                "collector",
                "clear_clipboard",
                "diagnostic",
                "quick_replies",
                "stop_app",
                "cards",
            ),
            BubbleShortcutCatalog.modules.map { it.spec.id },
        )
        assertEquals(14, BubbleShortcutCatalog.modules.map { it::class }.distinct().size)
        assertEquals(14, BubbleShortcutCatalog.modules.map { it.spec.action }.distinct().size)
        assertFalse(BubbleShortcutCatalog.modules.any { it.spec.id == "permissions" })
        assertFalse(BubbleShortcutCatalog.modules.any { it.spec.id == "reading" })
        assertFalse(BubbleShortcutCatalog.modules.any { it.spec.id == "reports" })
        assertFalse(BubbleShortcutCatalog.modules.any { it.spec.id == "alert" })
        assertFalse(BubbleShortcutCatalog.modules.any { it.spec.id == "saved_place" })
        assertFalse(BubbleShortcutCatalog.modules.any { it.spec.id == "ride_card" })
    }

    @Test
    fun alertsPlacesRadarsAndCardsUseIndependentDestinations() {
        val alerts = AlertsManagementBubbleShortcutModule.spec
        assertEquals(BubbleShortcutAction.OpenAlerts, alerts.action)
        assertEquals("alerts", alerts.targetGroup)
        assertEquals("config", alerts.targetTab)

        val places = SavedPlacesManagementBubbleShortcutModule.spec
        assertEquals(BubbleShortcutAction.OpenSavedPlaces, places.action)
        assertEquals("saved_places", places.targetGroup)
        assertEquals("config", places.targetTab)

        val radars = RadarsManagementBubbleShortcutModule.spec
        assertEquals(BubbleShortcutAction.OpenRadars, radars.action)
        assertEquals("radars", radars.targetGroup)
        assertEquals("config", radars.targetTab)

        val cards = CardsManagementBubbleShortcutModule.spec
        assertEquals(BubbleShortcutAction.OpenCards, cards.action)
        assertEquals("cards", cards.targetGroup)
        assertEquals("config", cards.targetTab)
    }

    @Test
    fun routeAndDestinationDoNotOpenTheSameModule() {
        val route = RouteBubbleShortcutModule.spec
        assertEquals("general", route.targetGroup)
        assertEquals("config", route.targetTab)

        val destination = DestinationBubbleShortcutModule.spec
        assertEquals("destination", destination.targetGroup)
        assertEquals("analysis", destination.targetTab)
    }

    @Test
    fun directActionsRemainAvailableWithoutDuplicatingGeneralControls() {
        assertEquals(BubbleShortcutAction.OpenScreenWhatsApp, WhatsAppBubbleShortcutModule.spec.action)
        assertEquals(BubbleShortcutAction.OpenCollector, CollectorBubbleShortcutModule.spec.action)
        assertEquals(BubbleShortcutAction.ClearClipboard, ClearClipboardBubbleShortcutModule.spec.action)
        assertEquals(BubbleShortcutAction.ExportDiagnostic, DiagnosticBubbleShortcutModule.spec.action)
        assertEquals(BubbleShortcutAction.StopApplication, StopBubbleShortcutModule.spec.action)
        assertEquals(BubbleShortcutAction.OpenQuickReplies, QuickRepliesBubbleShortcutModule.spec.action)
        assertNull(WhatsAppBubbleShortcutModule.spec.targetGroup)
        assertNull(StopBubbleShortcutModule.spec.targetGroup)
    }

    @Test
    fun navigationModulesDeclareTheirDestinationGroups() {
        listOf(
            RouteBubbleShortcutModule.spec,
            DestinationBubbleShortcutModule.spec,
            AlertsManagementBubbleShortcutModule.spec,
            SavedPlacesManagementBubbleShortcutModule.spec,
            RadarsManagementBubbleShortcutModule.spec,
            AppearanceBubbleShortcutModule.spec,
            BackupBubbleShortcutModule.spec,
            CardsManagementBubbleShortcutModule.spec,
        ).forEach { spec ->
            assertTrue(!spec.targetGroup.isNullOrBlank())
            assertTrue(!spec.targetTab.isNullOrBlank())
        }
    }
}
