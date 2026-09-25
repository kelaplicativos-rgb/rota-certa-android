package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PassengerPasswordRecoveryE2e0651Test {
    private val passengerAdmin = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerAdminUi.kt").readText()
    private val remoteApi = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripRemoteApi.kt").readText()
    private val tripsActivity = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()
    private val messaging = File("src/main/java/br/com/mapeiaia/rotacerta/trips/RotaCertaBookingMessagingService.kt").readText()
    private val entryPoints = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripAndroidEntryPoints.kt").readText()
    private val gradle = File("build.gradle.kts").readText()

    @Test
    fun driverProjectionCarriesRecoveryLifecycleWithoutNewPassengerIdentity() {
        assertTrue(remoteApi.contains("val passwordRecoveryStatus: String = \"\""))
        assertTrue(remoteApi.contains("val passwordRecoveryRequestedAtMillis: Long = 0L"))
        assertTrue(remoteApi.contains("val passwordRecoveryIssuedAtMillis: Long = 0L"))
        assertTrue(remoteApi.contains("val passwordRecoveryCompletedAtMillis: Long = 0L"))
        assertTrue(remoteApi.contains("val accountMustChangePassword: Boolean = false"))
        assertTrue(remoteApi.contains("val recoveryStatus: String = \"\""))
    }

    @Test
    fun requestedRecoveryIsVisiblePrioritizedAndActionable() {
        assertTrue(passengerAdmin.contains("\"REQUESTED\" -> Text(\"🔑 Recuperação de senha solicitada\""))
        assertTrue(passengerAdmin.contains("\"ISSUED\" -> Text(\"🟠 Senha temporária emitida • troca obrigatória pendente\""))
        assertTrue(passengerAdmin.contains("\"COMPLETED\" -> Text(\"✅ Recuperação de senha concluída\""))
        assertTrue(passengerAdmin.contains("access?.passwordRecoveryStatus == \"REQUESTED\" -> \"Gerar senha solicitada\""))
        assertTrue(passengerAdmin.contains("it.remoteAccess?.passwordRecoveryStatus == \"REQUESTED\" -> 0"))
    }

    @Test
    fun generatedTemporaryPasswordCanBeSentByWhatsappAndRequiresReplacement() {
        assertTrue(passengerAdmin.contains("Enviar WhatsApp"))
        assertTrue(passengerAdmin.contains("sendTemporaryPasswordWhatsApp0651"))
        assertTrue(passengerAdmin.contains("O portal exigirá a criação de uma nova senha antes de liberar a Área VIP."))
        assertTrue(passengerAdmin.contains("https://wa.me/"))
        assertTrue(passengerAdmin.contains("Sua senha temporária da Área VIP é"))
    }

    @Test
    fun recoveryPushAvoidsDirectoryPollingAndOpensPassengerManagement() {
        assertFalse(passengerAdmin.contains("delay(20_000)"))
        assertTrue(entryPoints.contains("ACTION_OPEN_PASSENGERS"))
        assertTrue(messaging.contains("\"password_recovery_requested\""))
        assertTrue(messaging.contains("TripActions.ACTION_OPEN_PASSENGERS"))
        assertTrue(tripsActivity.contains("openPassengers -> TripScreen.PASSENGERS"))
        val marker = "PASSENGER_ADMIN_UPDATE_0650"
        val start = tripsActivity.indexOf(marker)
        assertTrue(start >= 0)
        val blockStart = tripsActivity.lastIndexOf("onChanged = { text ->", start)
        val blockEnd = tripsActivity.indexOf("},", start)
        assertTrue(blockStart >= 0 && blockEnd > start)
        assertFalse(tripsActivity.substring(blockStart, blockEnd).contains("refresh()"))
    }

    @Test
    fun packageMetadataPreserves0651OrNewer() {
        val versionCode = requireNotNull(
            Regex("""val releaseVersionCode = ([0-9_]+)""").find(gradle),
        ).groupValues[1].replace("_", "").toInt()
        val patchVersion = requireNotNull(
            Regex("""val releaseVersionName = "0\\.1\\.([0-9]+)"""").find(gradle),
        ).groupValues[1].toInt()
        assertTrue(versionCode >= 5942)
        assertTrue(patchVersion >= 651)
    }
}
