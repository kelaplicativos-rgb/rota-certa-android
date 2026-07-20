package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleShortcutModulesTest {
    @Test
    fun catalogContainsEightIndependentModulesInDisplayOrder() {
        BubbleShortcutCatalog.requireValid()

        assertEquals(
            listOf("alert", "saved_place", "ride_card", "destination", "reading", "whatsapp", "settings", "stop_app"),
            BubbleShortcutCatalog.modules.map { it.spec.id },
        )
        assertEquals(8, BubbleShortcutCatalog.modules.map { it::class }.distinct().size)
        assertEquals(8, BubbleShortcutCatalog.modules.map { it.spec.action }.distinct().size)
    }

    @Test
    fun alertAndSavedPlaceOpenEditableEditorWithSafeDefaults() {
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
    fun readingWhatsAppAndStopAreDirectActions() {
        val reading = ReadingBubbleShortcutModule.spec
        assertEquals(BubbleShortcutAction.ToggleReading, reading.action)
        assertEquals("access", reading.targetGroup)

        val whatsapp = WhatsAppBubbleShortcutModule.spec
        assertEquals(BubbleShortcutAction.OpenScreenWhatsApp, whatsapp.action)
        assertNull(whatsapp.targetGroup)

        val stop = StopBubbleShortcutModule.spec
        assertEquals(BubbleShortcutAction.StopApplication, stop.action)
        assertEquals("Encerrar", stop.displayLabel)
        assertNull(stop.targetGroup)
    }

    @Test
    fun onlyNavigationModulesDeclareDestinations() {
        listOf(
            DestinationBubbleShortcutModule.spec,
            SettingsBubbleShortcutModule.spec,
        ).forEach { spec ->
            assertTrue(!spec.targetGroup.isNullOrBlank())
            assertTrue(!spec.targetTab.isNullOrBlank())
        }
        assertNull(RideCardBubbleShortcutModule.spec.targetGroup)
    }
}
