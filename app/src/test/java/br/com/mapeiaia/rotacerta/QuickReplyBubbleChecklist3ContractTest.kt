package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickReplyBubbleChecklist3ContractTest {
    @Test
    fun `popup final possui um unico atalho de respostas rapidas`() {
        BubbleShortcutCatalog.requireValid()
        val shortcuts = BubbleShortcutCatalog.modules.filter {
            it.spec.action == BubbleShortcutAction.OpenQuickReplies
        }

        assertEquals(1, shortcuts.size)
        assertEquals("quick_replies", shortcuts.single().spec.id)
        assertEquals("Respostas", shortcuts.single().spec.displayLabel)
    }

    @Test
    fun `acao de respostas continua independente da leitura de corridas`() {
        val quickReplyAction = BubbleShortcutCatalog.modules.single {
            it.spec.id == "quick_replies"
        }.spec.action

        assertTrue(quickReplyAction == BubbleShortcutAction.OpenQuickReplies)
        assertTrue(quickReplyAction != BubbleShortcutAction.ToggleReading)
    }
}
