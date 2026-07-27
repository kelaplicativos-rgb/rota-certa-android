package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleShortcutModulesTest {
    @Test
    fun catalogContainsFifteenIndependentModulesWithoutCardModels() {
        BubbleShortcutCatalog.requireValid()
        val ids = BubbleShortcutCatalog.modules.map { it.spec.id }
        assertEquals(15, ids.size)
        assertTrue("A captura manual de aplicativo e tela precisa estar disponível", "capture_app_screen" in ids)
        assertFalse("A captura antiga de modelo não pode voltar", "manual_card_capture" in ids)
        assertFalse("A tela de modelos não pode voltar", "cards" in ids)
        assertEquals(ids.size, BubbleShortcutCatalog.modules.map { it.spec.action }.distinct().size)
    }
}
