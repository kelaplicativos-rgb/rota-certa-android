package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BubbleShortcutDoubleTapContract138Test {
    @Test fun `atalhos principais possuem acao dupla distinta`() {
        val byId = BubbleShortcutCatalog.modules.associateBy { it.spec.id }
        assertEquals(BubbleShortcutQuickAction.CopyAllVisibleText, byId.getValue("copy_trip_confirmation").spec.doubleTapAction)
        assertEquals(BubbleShortcutQuickAction.CreateQuickReply, byId.getValue("quick_replies").spec.doubleTapAction)
        assertEquals(BubbleShortcutQuickAction.CreateRadarAtCurrentLocation, byId.getValue("radars").spec.doubleTapAction)
        assertEquals(BubbleShortcutQuickAction.CreateNamedAlertAtCurrentLocation, byId.getValue("alerts").spec.doubleTapAction)
        assertEquals(BubbleShortcutQuickAction.CreateNamedSavedPlaceAtCurrentLocation, byId.getValue("saved_places").spec.doubleTapAction)
        assertEquals(BubbleShortcutQuickAction.DefineDestinationAtCurrentLocation, byId.getValue("destination").spec.doubleTapAction)
        assertNull(byId.getValue("appearance").spec.doubleTapAction)
    }
}
