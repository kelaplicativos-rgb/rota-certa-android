package br.com.mapeiaia.rotacerta.trips

import java.io.File
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteDnsConsentRecovery0771Test {
    private fun trips(name: String) =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/$name").readText()

    @Test fun transportFailuresNeverBecomeFinalZipFailures() {
        assertTrue(isTransientRemoteDiagnosticFailure0771(UnknownHostException("offline")))
        assertTrue(isTransientRemoteDiagnosticFailure0771(
            TripRemoteApiException("POST", "/v1/driver/remote-health", 0, "", "", "", "")
        ))
        assertTrue(isTransientRemoteDiagnosticFailure0771(
            TripRemoteApiException("POST", "/v1/driver/remote-health", 503, "", "", "", "")
        ))
        assertFalse(isTransientRemoteDiagnosticFailure0771(
            TripRemoteApiException("POST", "/v1/driver/remote-health", 401, "", "", "", "")
        ))
        assertFalse(isTransientRemoteDiagnosticFailure0771(
            TripRemoteApiException("POST", "/v1/driver/remote-health", 403, "", "", "", "")
        ))
        assertFalse(isTransientRemoteDiagnosticFailure0771(IllegalArgumentException("invalid ZIP")))
    }

    @Test fun activeConsentAndUserChoiceAreStoredSeparately() {
        val desired = trips("RemoteAccessDesiredState0771.kt")
        assertTrue(desired.contains("KEY_DESIRED"))
        assertTrue(desired.contains("KEY_DIRTY"))
        assertTrue(desired.contains("activateAfterServerAck"))
        assertTrue(desired.contains("if (!desired(app)) return@synchronized false"))
        assertTrue(desired.contains("RemoteSupportAutoAccess0763.setEnabled(app, false)"))
        assertTrue(desired.contains("RemoteSupportAutoAccess0763.setEnabled(app, true)"))
        assertTrue(desired.contains("setRemoteAccessState0764(desired)"))
        assertTrue(desired.contains("ExistingWorkPolicy.REPLACE"))
        assertFalse(desired.contains(".addTag(RemoteSupportAutoAccess0763.WORK_TAG)"))
    }

    @Test fun frontendCannotTurnToggleOffOnTemporaryServerFailure() {
        val screen = trips("StandaloneRemoteAccessActions0739.kt")
        assertTrue(screen.contains("RemoteAccessDesiredState0771.request(context, requested)"))
        assertTrue(screen.contains("autoAccess = requested"))
        assertFalse(screen.contains("Falha ao ligar a escuta: autorização local revogada."))
        assertFalse(screen.contains("Não foi possível ativar. O acesso continua desligado."))
    }

    @Test fun zipAndSnapshotFailuresRetryOnlyTransportOutages() {
        val worker = trips("RemoteHealthDiagnostic0747.kt")
        assertTrue(worker.contains("REMOTE_TECHNICAL_ZIP_RETRY_0771"))
        assertTrue(worker.contains("REMOTE_HEALTH_SNAPSHOT_RETRY_0771"))
        assertTrue(worker.contains("isTransientRemoteDiagnosticFailure0771(error)"))
        assertTrue(worker.contains("BackoffPolicy.EXPONENTIAL"))
        assertTrue(worker.contains("if (error is CancellationException) throw error"))
    }
}
