package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgendaScriptsHub0560Test {
    @Test
    fun scriptsHubExposesApprovedSections() {
        assertEquals(
            listOf("Executor", "Em andamento", "Histórico", "Navegador", "Diagnóstico"),
            agendaScriptsSections0560().map { it.label },
        )
    }

    @Test
    fun stateSummaryIsDeterministicAndCountsEachState() {
        val instruction = AgendaTripInstruction0558(
            index = 0,
            profileUuid = "profile-test",
            date = "2026-09-20",
            departureTime = "11:30",
            origin = "Origem teste",
            destination = "Destino teste",
            seats = 4,
            publish = true,
        )
        val execution = AgendaTripExecution0558(
            executionId = "execution-test",
            scriptId = "script-test",
            scriptHash = "hash-test",
            startedAtMillis = 1L,
            updatedAtMillis = 2L,
            cancelled = false,
            rawScript = "{}",
            items = listOf(
                AgendaTripExecutionItem0558(instruction, "fp-1", AgendaTripScriptState0558.READY, "account-test"),
                AgendaTripExecutionItem0558(instruction.copy(index = 1), "fp-2", AgendaTripScriptState0558.SYNCED, "account-test"),
                AgendaTripExecutionItem0558(instruction.copy(index = 2), "fp-3", AgendaTripScriptState0558.READY, "account-test"),
            ),
        )

        val summary = execution.stateSummary0560()
        assertEquals("2 READY • 1 SYNCED", summary)
        assertTrue(summary.indexOf("READY") < summary.indexOf("SYNCED"))
    }
}
