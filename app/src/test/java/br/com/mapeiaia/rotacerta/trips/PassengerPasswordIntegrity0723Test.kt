package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class PassengerPasswordIntegrity0723Test {
    private val passengerAdmin = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerAdminUi.kt").readText()
    private val remoteApi = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripRemoteApi.kt").readText()

    @Test
    fun driverOnlyConfirmsPasswordClearAfterBackendVerification() {
        assertTrue(remoteApi.contains("val verified: Boolean = false"))
        assertTrue(remoteApi.contains("val passwordStateVersion0723: Long = 0L"))
        assertTrue(remoteApi.contains("val passwordClearedAtMillis0723: Long = 0L"))
        assertTrue(passengerAdmin.contains("if (response.cleared && response.verified)"))
        assertTrue(passengerAdmin.contains("PASSENGER_PASSWORD_CLEAR_CONFIRMED_0723"))
        assertTrue(passengerAdmin.contains("A limpeza da senha não foi confirmada pelo servidor."))
    }

    @Test
    fun successMessageDoesNotDependOnTheRemoteReload() {
        val marker = "if (response.cleared && response.verified)"
        val start = passengerAdmin.indexOf(marker)
        assertTrue(start >= 0)
        val end = passengerAdmin.indexOf(".onFailure", start)
        assertTrue(end > start)
        val block = passengerAdmin.substring(start, end)
        val successMessage = block.indexOf("Senha limpa. O passageiro deverá criar uma nova senha no próximo acesso.")
        val backgroundRefresh = block.indexOf("reloadRemote(syncDirectory = false, silentErrors = true)")
        assertTrue(successMessage >= 0)
        assertTrue(backgroundRefresh > successMessage)
        assertTrue(block.contains("remotePassengers = remotePassengers.map"))
    }

    @Test
    fun backgroundRefreshCannotOverwriteConfirmedResetWithAReadError() {
        assertTrue(passengerAdmin.contains("suspend fun reloadRemote(syncDirectory: Boolean = true, silentErrors: Boolean = false)"))
        assertTrue(passengerAdmin.contains("if (!silentErrors)"))
    }
}
