package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteToggleOnlyRecovery0770ContractTest {
    private fun source(file: String): String =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/$file").readText()

    @Test fun oneToggleSchedulesPersistentNetworkConstrainedFallback() {
        val optIn = source("RemoteSupportAutoAccess0763.kt")
        val recovery = source("RemotePollingRecovery0770.kt")
        assertTrue(optIn.contains("RemotePollingRecovery0770.reconcile(app, immediate = value)"))
        assertTrue(optIn.contains("cancelAllWorkByTag(WORK_TAG)"))
        assertTrue(recovery.contains("PeriodicWorkRequestBuilder<RemotePollingRecoveryWorker0770>"))
        assertTrue(recovery.contains("15, TimeUnit.MINUTES"))
        assertTrue(recovery.contains("NetworkType.CONNECTED"))
        assertTrue(recovery.contains("ExistingPeriodicWorkPolicy.KEEP"))
        assertTrue(recovery.contains("Intent.ACTION_MY_PACKAGE_REPLACED"))
        assertTrue(recovery.contains("Intent.ACTION_BOOT_COMPLETED"))
    }

    @Test fun readingStillRequiresLocalConsentAndCanonicalWorkflows() {
        val recovery = source("RemotePollingRecovery0770.kt")
        assertTrue(recovery.contains("RemoteSupportAutoAccess0763.enabled(app, event)"))
        assertTrue(recovery.contains("RemoteSupportNotification0744.show(app, event, pending.jobId)"))
        assertTrue(recovery.contains("shouldOfferRemoteConsent0758"))
        assertTrue(recovery.contains("shouldOfferRemoteTripConsent0760"))
        assertTrue(recovery.contains("shouldOfferRemoteTechnicalConsent0761"))
        assertTrue(recovery.contains("shouldOfferRemoteHealthConsent0762"))
        assertFalse(recovery.contains("setRemoteAccessState0764(true)"))
        assertFalse(recovery.contains("startForegroundService("))
    }

    @Test fun androidForegroundLimitAndTransientOfflineDoNotSilentlyRevokeOptIn() {
        val foreground = source("RemoteCoversPollService0758.kt")
        val ui = source("StandaloneRemoteAccessActions0739.kt")
        assertTrue(foreground.contains("override fun onTimeout(startId: Int, fgsType: Int)"))
        assertTrue(foreground.contains("return START_STICKY"))
        assertTrue(foreground.contains("RemotePollingRecovery0770.reconcile(this)"))
        assertTrue(ui.contains("Toggle ON preservado"))
        assertFalse(ui.contains("Falha ao ligar a escuta: autorização local revogada."))
    }

    @Test fun manifestBootReceiverDoesNotExposeRemoteCollection() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains(".trips.RemotePollingRecoveryReceiver0770"))
        assertTrue(manifest.contains("android:exported=\"false\""))
        assertTrue(manifest.contains("android.intent.action.MY_PACKAGE_REPLACED"))
    }
}
