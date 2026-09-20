package br.com.mapeiaia.rotacerta.trips

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlaBlaAutomaticCollection0585Test {

    @Test
    fun batchSingleFlightSerializesOverlappingWorkers() = runBlocking {
        val gate = AutomaticCollectorBatchSingleFlight0585()
        val active = AtomicInteger(0)
        val maximumActive = AtomicInteger(0)

        val jobs = (1..12).map {
            launch(Dispatchers.Default) {
                gate.run {
                    val nowActive = active.incrementAndGet()
                    maximumActive.updateAndGet { previous -> maxOf(previous, nowActive) }
                    delay(5)
                    active.decrementAndGet()
                }
            }
        }
        jobs.joinAll()

        assertEquals(1, maximumActive.get())
        assertEquals(0, active.get())
    }

    @Test
    fun unresolvedAccountsRemainNonTerminalUntilEveryTargetHasOutcome() {
        val partial = AgendaAutomaticCollectorState0400(
            generation = 206L,
            completedGeneration = 205L,
            targetAccountIds = listOf("account-a", "account-b"),
            completedAccountIds = listOf("account-a"),
        )

        assertEquals(listOf("account-b"), unresolvedAutomaticCollectorAccountIds0585(partial))
        assertEquals(
            "account-b",
            nextAutomaticCollectorAccountId0400(
                targetAccountIds = partial.targetAccountIds,
                completedAccountIds = partial.completedAccountIds.toSet(),
                failedAccountIds = partial.failedAccountIds.toSet(),
                pendingAuthAccountIds = partial.pendingAuthAccountIds.toSet(),
            ),
        )
    }

    @Test
    fun failedAndPendingAuthAccountsAreTerminalForBatchTraversal() {
        val terminal = AgendaAutomaticCollectorState0400(
            generation = 207L,
            completedGeneration = 206L,
            targetAccountIds = listOf("account-a", "account-b", "account-c"),
            completedAccountIds = listOf("account-a"),
            failedAccountIds = listOf("account-b"),
            pendingAuthAccountIds = listOf("account-c"),
        )

        assertTrue(unresolvedAutomaticCollectorAccountIds0585(terminal).isEmpty())
        assertEquals(
            null,
            nextAutomaticCollectorAccountId0400(
                targetAccountIds = terminal.targetAccountIds,
                completedAccountIds = terminal.completedAccountIds.toSet(),
                failedAccountIds = terminal.failedAccountIds.toSet(),
                pendingAuthAccountIds = terminal.pendingAuthAccountIds.toSet(),
            ),
        )
    }
}
