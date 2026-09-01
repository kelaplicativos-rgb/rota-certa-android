package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaPassengerMainThread0384Test {
    private fun source(name: String): String =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/$name").readText()

    @Test
    fun passengerAdminLoadsPersistentStateOffMainThread() {
        val ui = source("PassengerAdminUi.kt")
        assertTrue(ui.contains("withContext(Dispatchers.IO) { store.onlineSettings() }"))
        assertTrue(ui.contains("passengerStore.profiles() to collectorStore.lastResponseRecoveringDynamicSessions()?.trips.orEmpty()"))
        assertTrue(ui.contains("passengerStore.persistentHistorySnapshot(ids)"))
        assertTrue(ui.contains("newPassengerSuggestions = withContext(Dispatchers.IO)"))
    }

    @Test
    fun collectedPassengerIdentityReconcileRunsOnIoDispatcher() {
        val ui = source("PassengerAdminUi.kt")
        val start = ui.indexOf("LaunchedEffect(collectedIdentityKey)")
        val end = ui.indexOf("var canonicalSearchIds", start)
        assertTrue(start >= 0 && end > start)
        val block = ui.substring(start, end)
        assertTrue(block.contains("withContext(Dispatchers.IO)"))
        assertTrue(block.contains("passengerStore.observeExternalPassenger("))
    }

    @Test
    fun passengerCardsNeverReadOrMutatePersistentHistoryDuringComposition() {
        val ui = source("PassengerAdminUi.kt")
        assertFalse(ui.contains("val durableHistory = passengerStore.persistentHistory(profile.id)"))
        assertFalse(ui.contains("passengerStore.rideHistory(profile.id).totalRides"))
        assertFalse(ui.contains("candidate.localProfile ?: canonicalProfile(candidate)"))
        assertTrue(ui.contains("val durableHistory = passengerHistories[profile.id]"))
        assertTrue(ui.contains("val canonicalAccessProfile = candidate.localProfile"))
    }

    @Test
    fun persistentHistorySnapshotDecodesBackingCollectionsOncePerBatch() {
        val store = source("PassengerIdentityStore.kt")
        val start = store.indexOf("internal fun persistentHistorySnapshot(")
        val end = store.indexOf("fun persistentHistory(profileId:", start)
        assertTrue(start >= 0 && end > start)
        val block = store.substring(start, end)
        assertTrue(block.contains("observationsByProfile"))
        assertTrue(block.contains("ridesByProfile"))
        assertTrue(block.contains("PassengerPersistentHistory("))
        assertTrue(block.contains("profileIds.isEmpty()"))
    }
}
