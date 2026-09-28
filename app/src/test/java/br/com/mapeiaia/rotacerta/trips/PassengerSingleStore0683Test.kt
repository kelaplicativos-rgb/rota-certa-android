package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PassengerSingleStore0683Test {
    private val passengerAdmin =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerAdminUi.kt").readText()
    private val remoteApi =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripRemoteApi.kt").readText()
    private val gradle = File("build.gradle.kts").readText()

    @Test
    fun passwordAdministrationHasOnlyClearPasswordAction() {
        assertTrue(passengerAdmin.contains("Text(\"Limpar senha\")"))
        assertTrue(passengerAdmin.contains("Senha limpa. O passageiro deverá criar uma nova senha no próximo acesso."))
        assertFalse(passengerAdmin.contains("Salvar WhatsApp de acesso"))
        assertFalse(passengerAdmin.contains("Gerar nova senha"))
        assertFalse(passengerAdmin.contains("Gerar senha de primeiro acesso"))
        assertFalse(passengerAdmin.contains("Reparar acesso"))
        assertFalse(passengerAdmin.contains("Senha temporária gerada"))
        assertFalse(passengerAdmin.contains("sendTemporaryPasswordWhatsApp0651"))
    }

    @Test
    fun passengerAccessRequiresExplicitDriverApproval() {
        assertTrue(passengerAdmin.contains("Aprovar indicação"))
        assertTrue(passengerAdmin.contains("Convidar e aprovar"))
        assertTrue(passengerAdmin.contains("Indicação recusada. O passageiro continua sem acesso à Agenda."))
        assertTrue(passengerAdmin.contains("access?.approvalPolicyVersion ?: 0"))
        assertTrue(passengerAdmin.contains("access?.approvedAtMillis ?: 0L"))
        assertTrue(remoteApi.contains("path = \"/v1/driver/passengers/approve\""))
        assertTrue(remoteApi.contains("suspend fun approvePassenger("))
        assertTrue(remoteApi.contains("status = if (blocked) \"BLOCKED\" else \"LOCAL_ONLY\""))
    }

    @Test
    fun directorySyncIsIdentityOnlyAndDoesNotMeanAccess() {
        assertTrue(passengerAdmin.contains("Novo passageiro salvo no banco único. O acesso à Agenda continua fechado"))
        assertTrue(passengerAdmin.contains("O acesso à Agenda só muda por aprovação explícita do motorista."))
        assertTrue(remoteApi.contains("path = \"/v1/driver/passengers/sync\""))
    }

    @Test
    fun resetContractDoesNotReturnTemporaryPassword() {
        val resetStart = remoteApi.indexOf("data class DriverPassengerResetPasswordResponse")
        val resetEnd = remoteApi.indexOf("@Serializable", resetStart + 20)
        assertTrue(resetStart >= 0)
        val block = remoteApi.substring(resetStart, if (resetEnd > resetStart) resetEnd else remoteApi.length)
        assertTrue(block.contains("val cleared: Boolean = false"))
        assertTrue(block.contains("val invalidatedSessions: Int = 0"))
        assertFalse(block.contains("temporaryPassword"))
    }

    @Test
    fun packageIs0683() {
        assertTrue(gradle.contains("val releaseVersionCode = 5_974"))
        assertTrue(gradle.contains("val releaseVersionName = \"0.1.683\""))
    }
}
