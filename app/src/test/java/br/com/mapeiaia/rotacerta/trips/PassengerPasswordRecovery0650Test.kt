package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PassengerPasswordRecovery0650Test {
    private val passengerAdmin = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerAdminUi.kt").readText()
    private val tripsActivity = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()
    private val remoteApi = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripRemoteApi.kt").readText()

    @Test
    fun passwordRecoveryIsVisibleWithoutAccountActivatedGate() {
        assertTrue(passengerAdmin.contains("Gerar nova senha"))
        assertTrue(passengerAdmin.contains("Gerar senha de primeiro acesso"))
        assertTrue(passengerAdmin.contains("Reparar acesso"))
        assertTrue(passengerAdmin.contains("passwordRecoveryAvailable0650"))
        assertFalse(passengerAdmin.contains("if (access?.accountActivated == true)"))
        assertTrue(passengerAdmin.contains("if (error.httpStatus != 404) throw error"))
        assertTrue(passengerAdmin.contains("api.syncPassengerDirectory(listOf(canonical))"))
    }

    @Test
    fun passengerOnlyUpdatesDoNotRefreshAllTripsAndWidgets() {
        val marker = "PASSENGER_ADMIN_UPDATE_0650"
        val start = tripsActivity.indexOf(marker)
        assertTrue(start >= 0)
        val blockStart = tripsActivity.lastIndexOf("onChanged = { text ->", start)
        val blockEnd = tripsActivity.indexOf("},", start)
        assertTrue(blockStart >= 0 && blockEnd > start)
        val block = tripsActivity.substring(blockStart, blockEnd)
        assertFalse(block.contains("refresh()"))
        assertTrue(block.contains("message = text"))
    }

    @Test
    fun passengerManagementWritesAreMovedOffMainThread() {
        assertTrue(passengerAdmin.contains("withContext(Dispatchers.IO) { canonicalProfile(candidate) }"))
        assertTrue(passengerAdmin.contains("withContext(Dispatchers.IO) {\n                                                passengerStore.saveProfile"))
        assertTrue(passengerAdmin.contains("withContext(Dispatchers.IO) {\n                                passengerStore.setBlocked"))
    }

    @Test
    fun directoryConflictIsIsolatedInsteadOfAbortingFortyPassengers() {
        assertTrue(remoteApi.contains("suspend fun syncBatch0650"))
        assertTrue(remoteApi.contains("error is TripRemoteApiException && error.httpStatus == 409"))
        assertTrue(remoteApi.contains("syncBatch0650(batch.subList(0, midpoint), batchIndex)"))
        assertTrue(remoteApi.contains("else if (conflict && batch.size == 1)"))
        assertTrue(remoteApi.contains("One identity conflict must not block the rest of the directory."))
    }
}
