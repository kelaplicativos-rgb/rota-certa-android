package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleShortcutModulesTest {
    @Test
    fun catalogContainsSixIndependentModulesInDisplayOrder() {
        BubbleShortcutCatalog.requireValid()

        assertEquals(
            listOf("alert", "saved_place", "ride_card", "destination", "reading", "settings"),
            BubbleShortcutCatalog.modules.map { it.spec.id },
        )
        assertEquals(6, BubbleShortcutCatalog.modules.map { it::class }.distinct().size)
    }

    @Test
    fun alertAndSavedPlaceOpenEditableEditorWithSafeDefaults() {
        val alert = AlertBubbleShortcutModule.spec
        assertEquals("Alerta", alert.defaultName)
        assertEquals("alerts", alert.targetGroup)
        assertEquals(BubbleShortcutAction.CreateAlert, alert.action)

        val local = SavedPlaceBubbleShortcutModule.spec
        assertEquals("Local salvo", local.defaultName)
        assertEquals("alerts", local.targetGroup)
        assertEquals(BubbleShortcutAction.CreateSavedPlace, local.action)
    }

    @Test
    fun navigationModulesDeclareTheirOwnDestination() {
        listOf(
            DestinationBubbleShortcutModule.spec,
            ReadingBubbleShortcutModule.spec,
            SettingsBubbleShortcutModule.spec,
        ).forEach { spec ->
            assertNotNull(spec.targetGroup)
            assertNotNull(spec.targetTab)
        }
        assertTrue(RideCardBubbleShortcutModule.spec.targetGroup == null)
    }
}
