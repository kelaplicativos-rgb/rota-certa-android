package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleShortcutModulesTest {
    @Test
    fun catalogProgressesFromFourteenToFifteenModulesWithoutCardModels() {
        BubbleShortcutCatalog.requireValid()
        val ids = BubbleShortcutCatalog.modules.map { it.spec.id }
        val hasManualCapture = "manual_capture" in ids

        assertEquals(if (hasManualCapture) 15 else 14, ids.size)
        if (hasManualCapture) {
            assertTrue("A captura manual de aplicativo e tela precisa estar disponível", "manual_capture" in ids)
        }
        assertFalse("A captura antiga de modelo não pode voltar", "manual_card_capture" in ids)
        assertFalse("A tela de modelos não pode voltar", "cards" in ids)
        assertEquals(ids.size, BubbleShortcutCatalog.modules.map { it.spec.action }.distinct().size)
    }
}
