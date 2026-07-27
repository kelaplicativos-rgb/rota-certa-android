package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BubbleShortcutModulesTest {
    @Test
    fun catalogContainsFourteenIndependentModulesWithoutCardModels() {
        BubbleShortcutCatalog.requireValid()
        val ids = BubbleShortcutCatalog.modules.map { it.spec.id }
        assertEquals(14, ids.size)
        assertFalse("manual_card_capture" in ids)
        assertFalse("cards" in ids)
        assertEquals(ids.size, BubbleShortcutCatalog.modules.map { it.spec.action }.distinct().size)
    }
}
