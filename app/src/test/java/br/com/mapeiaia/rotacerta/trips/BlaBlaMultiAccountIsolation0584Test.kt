package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlaBlaMultiAccountIsolation0584Test {
    private val background =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaBackgroundSync0392.kt").readText()
    private val coordinator =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaAutomaticCollection0400.kt").readText()

    @Test
    fun staleTerminalFromPreviousAccountCannotClearTheNextActiveAccount() {
        assertEquals(
            "account-b",
            collectorActiveAfterTerminal0584(
                currentActiveAccountId = "account-b",
                terminalAccountId = "account-a",
            ),
        )
        assertEquals(
            "RUNNING",
            collectorStatusAfterTerminal0584(
                survivingActiveAccountId = "account-b",
                result = "INTERRUPTED",
            ),
        )
        assertEquals(
            "",
            collectorActiveAfterTerminal0584(
                currentActiveAccountId = "account-a",
                terminalAccountId = "account-a",
            ),
        )
        assertEquals(
            "PENDING",
            collectorStatusAfterTerminal0584(
                survivingActiveAccountId = "",
                result = "FAILED",
            ),
        )
    }

    @Test
    fun failedAccountDoesNotPreventTheNextConfiguredAccountFromRunning() {
        assertEquals(
            "account-b",
            nextAutomaticCollectorAccountId0400(
                targetAccountIds = listOf("account-a", "account-b"),
                completedAccountIds = emptySet(),
                failedAccountIds = setOf("account-a"),
                pendingAuthAccountIds = emptySet(),
            ),
        )
    }

    @Test
    fun timeoutIsPersistedBeforeTheBatchContinues() {
        assertTrue(coordinator.contains("BLABLACAR_AUTOMATIC_TIMEOUT_TERMINAL_0584"))
        assertTrue(coordinator.contains("\"headless_account_timeout_0404\""))
        assertTrue(coordinator.contains("publishCurrentSessions(appContext, \"account_timeout_0584\")"))
        assertTrue(background.contains("BLABLACAR_AUTOMATIC_STALE_TERMINAL_IGNORED_0584"))
        assertTrue(background.contains("collectorActiveAfterTerminal0584"))
        assertTrue(background.contains("collectorStatusAfterTerminal0584"))
    }
}
