package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleShortcutModulesTest {
    @Test
    fun homeCatalogContainsAllMainModulesAndActionCatalogIsClosed() {
        BubbleShortcutCatalog.requireValid()
        val ids = BubbleShortcutCatalog.modules.map { it.spec.id }

        assertTrue(ids.size >= 21)
        assertTrue("manual_capture" in ids)
        assertTrue("passenger_value" in ids)
        assertTrue("finance" in ids)
        assertTrue("quick_links" in ids)
        assertTrue("permissions" in ids)
        assertTrue("reports" in ids)
        assertTrue("message_templates" in ids)
        assertTrue("work_tracking" in ids)
        assertFalse("manual_card_capture" in ids)
        assertEquals(ids.size, ids.distinct().size)
        ShortcutActionCatalog0184.requireValid()
    }
}
