package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteHealth0747ContractTest {
    private fun source(name: String): String =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/$name").readText()

    @Test
    fun healthPushRequiresConsentBeforeAnyWorkerIsScheduled() {
        val messaging = source("RotaCertaBookingMessagingService.kt")
        val block = messaging.substringAfter("if (event == \"remote_health_collect\")")
            .substringBefore("val remoteTripId")
        assertTrue(block.contains("RemoteSupportNotification0744.show"))
        assertTrue(block.contains("collectionStarted=false"))
        assertFalse(block.contains("RemoteHealthScheduler0747.enqueue"))
    }

    @Test
    fun acceptIsTheOnlyPathThatStartsRemoteHealthCollection() {
        val consent = source("RemoteSupportConsent0746.kt")
        assertTrue(consent.contains("\"remote_health_collect\" ->"))
        assertTrue(consent.contains("RemoteHealthScheduler0747.enqueue(context, jobId)"))
        assertTrue(consent.contains("submitRemoteHealthResult0747"))
        assertTrue(consent.contains("REMOTE_ACCESS_DECLINED_BY_USER"))
    }

    @Test
    fun workerCollectsOnlySanitizedBoundedHealthEvidence() {
        val health = source("RemoteHealthDiagnostic0747.kt")
        assertTrue(health.contains("OperationalHealthCoordinator.scan(applicationContext)"))
        assertTrue(health.contains("UnifiedDebugEventStore.snapshot()"))
        assertTrue(health.contains("sanitizeForExport"))
        assertTrue(health.contains("maxEvents = 240"))
        assertTrue(health.contains("schemaVersion: String = \"rota-certa-remote-health-v1\""))
        assertFalse(health.contains("generateAndShare"))
        assertFalse(health.contains("saveToDownloads"))
        assertFalse(health.contains("MediaStore"))
    }

    @Test
    fun pushRegistrationAndApiDeclareRemoteHealthCapability() {
        val api = source("TripRemoteApi.kt")
        assertTrue(api.contains("val remoteHealthVersion: Int = 1"))
        assertTrue(api.contains("/v1/driver/remote-health/jobs/"))
        assertTrue(api.contains("ackRemoteHealthJob0747"))
        assertTrue(api.contains("submitRemoteHealthResult0747"))
    }
}
