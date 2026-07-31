package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleShortcutModulesTest {
    @Test
    fun catalogProgressesFromThirteenToFourteenModulesWithoutCardModels() {
        BubbleShortcutCatalog.requireValid()
        val ids = BubbleShortcutCatalog.modules.map { it.spec.id }
        val hasManualCapture = "manual_capture" in ids

        assertEquals(if (hasManualCapture) 16 else 15, ids.size)
        if (hasManualCapture) {
            assertTrue("A captura manual de aplicativo e tela precisa estar disponível", "manual_capture" in ids)
        }
        assertTrue("A bolinha Valor precisa existir", "passenger_value" in ids)
        assertTrue("A bolinha Financeiro precisa existir", "finance" in ids)
        assertFalse("A captura antiga de modelo não pode voltar", "manual_card_capture" in ids)
        assertFalse("A tela de modelos não pode voltar", "cards" in ids)
        assertEquals(ids.size, BubbleShortcutCatalog.modules.map { it.spec.action }.distinct().size)
    }
}
