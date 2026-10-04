package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgendaLocalRefreshCoordinator0726Test {
    @Test
    fun burstRequestsAreCoalescedIntoOneExecution0726() = runBlocking {
        val coordinator = AgendaLocalRefreshCoordinator0726(coalesceMillis = 20L)
        val executions = mutableListOf<String>()
        val job = launch {
            coordinator.run { reason -> executions += reason }
        }

        coordinator.request("initial")
        coordinator.request("resume")
        coordinator.request("ui_callback")

        withTimeout(1_000L) {
            while (executions.isEmpty()) delay(10L)
        }

        assertEquals(1, executions.size)
        assertTrue(executions.single().contains("initial"))
        assertTrue(executions.single().contains("resume"))
        assertTrue(executions.single().contains("ui_callback"))
        job.cancelAndJoin()
    }

    @Test
    fun requestsDuringExecutionBecomeOneFollowUp0726() = runBlocking {
        val coordinator = AgendaLocalRefreshCoordinator0726(coalesceMillis = 5L)
        val executions = mutableListOf<String>()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val job = launch {
            coordinator.run { reason ->
                executions += reason
                if (executions.size == 1) {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                }
            }
        }

        coordinator.request("booking_realtime")
        withTimeout(1_000L) { firstStarted.await() }
        coordinator.request("ui_callback")
        coordinator.request("resume")
        releaseFirst.complete(Unit)

        withTimeout(1_000L) {
            while (executions.size < 2) delay(10L)
        }

        assertEquals(2, executions.size)
        assertTrue(executions[1].contains("ui_callback"))
        assertTrue(executions[1].contains("resume"))
        job.cancelAndJoin()
    }
    @Test
    fun canonicalPassengerEventOwnsRefreshWithoutUiCallbackDuplicate0726() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/OperationalAllTripsBrowserUi0563.kt",
        ).readText()

        val activeOperations = source.substring(
            source.indexOf("items = activeRows"),
            source.indexOf("if (archivedRows.isNotEmpty())"),
        )
        assertTrue(activeOperations.contains("onOperationsChanged0654"))
        assertFalse(activeOperations.contains("onRefreshLocal()"))

        val directRefresh = source.substring(
            source.indexOf("OperationalTripCardRefreshMode0663.BLABLACAR_DIRECT_HTML"),
            source.indexOf("private fun", source.indexOf("OperationalTripCardRefreshMode0663.BLABLACAR_DIRECT_HTML")).takeIf { it > 0 }
                ?: source.length,
        )
        assertTrue(directRefresh.contains("CentralDayCommandBridge0552.refreshTripDirect0662"))
        assertFalse(
            directRefresh.substring(
                directRefresh.indexOf("CentralDayCommandBridge0552.refreshTripDirect0662"),
            ).contains("onRefreshLocal()"),
        )
    }

}
