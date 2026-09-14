package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgendaScriptEditorPolicy0561Test {
    private fun active(hash: String = "hash-old"): AgendaTripExecution0558 {
        val instruction = AgendaTripInstruction0558(
            index = 0,
            profileUuid = "profile-test",
            date = "2026-09-20",
            departureTime = "11:30",
            origin = "Origem",
            destination = "Destino",
            seats = 4,
            publish = true,
        )
        return AgendaTripExecution0558(
            executionId = "execution-old",
            scriptId = "script-old",
            scriptHash = hash,
            startedAtMillis = 1L,
            updatedAtMillis = 2L,
            cancelled = false,
            rawScript = "{\"old\":true}",
            items = listOf(
                AgendaTripExecutionItem0558(
                    instruction = instruction,
                    fingerprint = "fp-old",
                    state = AgendaTripScriptState0558.PUBLISHED_AMBIGUOUS,
                    accountId = "account-test",
                ),
            ),
        )
    }

    @Test
    fun editorAlwaysStartsBlankEvenWhenExecutionExists() {
        assertEquals("", AgendaScriptEditorPolicy0561.initialEditorText(active()))
        assertEquals("", AgendaScriptEditorPolicy0561.initialEditorText(null))
    }

    @Test
    fun recoveredMessageExplainsPendingIsSeparateFromEditor() {
        val message = AgendaScriptEditorPolicy0561.recoveredExecutionMessage(active())
        assertTrue(message.contains("editor foi aberto vazio"))
        assertTrue(message.contains("Carregar execução pendente"))
    }

    @Test
    fun newExecutionIsAllowedWithoutActiveExecution() {
        assertEquals(
            AgendaScriptExecutionGate0561.ALLOW_NEW,
            AgendaScriptEditorPolicy0561.executionGate(null, "hash-new"),
        )
    }

    @Test
    fun samePendingScriptMustResumeInsteadOfDuplicating() {
        assertEquals(
            AgendaScriptExecutionGate0561.RESUME_SAME,
            AgendaScriptEditorPolicy0561.executionGate(active("hash-same"), "hash-same"),
        )
    }

    @Test
    fun differentCurrentJsonCanBeEditedButLiveExecutionRemainsSerialized() {
        assertEquals(
            AgendaScriptExecutionGate0561.BLOCKED_BY_OTHER_ACTIVE,
            AgendaScriptEditorPolicy0561.executionGate(active("hash-old"), "hash-new"),
        )
    }
}
