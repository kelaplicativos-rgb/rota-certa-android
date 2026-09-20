package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlaBlaMultiAccountAdoption0585Test {
    private val background =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaBackgroundSync0392.kt").readText()
    private val coordinator =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaAutomaticCollection0400.kt").readText()
    private val dynamicAccounts =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()

    @Test
    fun partialAccountResultIsTerminalButNotExecutionFailure() {
        assertEquals("COMPLETE", automaticCollectorAccountTerminalResult0585("success"))
        assertEquals("COMPLETE", automaticCollectorAccountTerminalResult0585("partial"))
        assertEquals("FAILED", automaticCollectorAccountTerminalResult0585("failed"))
    }

    @Test
    fun singleFlightBusyKeepsAccountPendingInsteadOfFailingIt() {
        assertTrue(background.contains("releaseCollectorAccountClaim0585"))
        assertTrue(background.contains("BLABLACAR_AUTOMATIC_SINGLE_FLIGHT_DEFERRED_0585"))
        assertTrue(coordinator.contains("SINGLE_FLIGHT_RECHECK_MS_0585"))
        assertTrue(coordinator.contains("BLABLACAR_AUTOMATIC_SINGLE_FLIGHT_WAIT_0585"))
        assertTrue(dynamicAccounts.contains("onAccountSingleFlightBusy0585"))
        assertTrue(dynamicAccounts.contains("SINGLE_FLIGHT_BUSY"))
    }

    @Test
    fun onlyFullExistingSyncCanBeAdoptedByPendingAutomaticGeneration() {
        assertTrue(dynamicAccounts.contains("automaticCollectionGeneration <= 0L"))
        assertTrue(dynamicAccounts.contains("!targeted"))
        assertTrue(dynamicAccounts.contains("targetDates.isEmpty()"))
        assertTrue(dynamicAccounts.contains("onCompatibleExternalSyncFinished0585"))
        assertTrue(coordinator.contains("BLABLACAR_AUTOMATIC_EXTERNAL_SYNC_ADOPTED_0585"))
    }

    @Test
    fun adoptionDoesNotStealAnAlreadyActiveAutomaticHost() {
        assertTrue(coordinator.contains("current.activeAccountId.isNotBlank()"))
        assertTrue(coordinator.contains("id in current.completedAccountIds"))
        assertTrue(coordinator.contains("id in current.failedAccountIds"))
        assertTrue(coordinator.contains("id in current.pendingAuthAccountIds"))
    }
}
