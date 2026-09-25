package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PassengerComposeCancellation0652Test {
    private val passengerAdmin =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerAdminUi.kt").readText()

    @Test
    fun composeCancellationIsNotReportedAsPassengerRemoteFailure() {
        assertTrue(passengerAdmin.contains("catch (error: CancellationException)"))
        assertTrue(passengerAdmin.contains("PASSENGERS_DIRECTORY_SYNC_CANCELLED_0652"))
        assertTrue(passengerAdmin.contains("PASSENGERS_REMOTE_LOAD_CANCELLED_0652"))
        assertTrue(passengerAdmin.contains("throw error"))
        assertTrue(passengerAdmin.contains("PASSENGERS_REMOTE_LOAD_ERROR"))
        assertTrue(passengerAdmin.contains("Não foi possível carregar acessos dos passageiros"))
    }

    @Test
    fun cancellationIsSeparatedFromRealThrowableErrors() {
        val directoryStart = passengerAdmin.indexOf("PASSENGERS_DIRECTORY_SYNC_START_0630")
        val directoryCancelled = passengerAdmin.indexOf("PASSENGERS_DIRECTORY_SYNC_CANCELLED_0652", directoryStart)
        val directoryError = passengerAdmin.indexOf("PASSENGERS_DIRECTORY_SYNC_ERROR_0630", directoryStart)
        assertTrue(directoryStart >= 0 && directoryCancelled > directoryStart && directoryError > directoryCancelled)

        val remoteStart = passengerAdmin.indexOf("PASSENGERS_REMOTE_LOAD_START")
        val remoteCancelled = passengerAdmin.indexOf("PASSENGERS_REMOTE_LOAD_CANCELLED_0652", remoteStart)
        val remoteError = passengerAdmin.indexOf("PASSENGERS_REMOTE_LOAD_ERROR", remoteStart)
        assertTrue(remoteStart >= 0 && remoteCancelled > remoteStart && remoteError > remoteCancelled)

        val remoteBlock = passengerAdmin.substring(remoteStart, passengerAdmin.indexOf("withContext(Dispatchers.IO)", remoteStart))
        assertFalse(remoteBlock.contains("runCatching { api.listDriverPassengers() }"))
    }
}
