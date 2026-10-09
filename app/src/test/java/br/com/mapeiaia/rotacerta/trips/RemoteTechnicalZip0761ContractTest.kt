package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteTechnicalZip0761ContractTest {
    private fun trips(name: String): String =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/$name").readText()

    @Test
    fun acceptMustPrecedeTechnicalZipGeneration() {
        val polling = trips("RemoteCoversPollService0758.kt")
        val consent = trips("RemoteSupportConsent0746.kt")
        val worker = trips("RemoteHealthDiagnostic0747.kt")
        assertTrue(polling.contains("pollRemoteTechnicalPending0761"))
        assertTrue(polling.contains("showConsent(\"remote_health_collect\", jobId)"))
        assertTrue(consent.contains("RemoteHealthScheduler0747.enqueue(context, jobId)"))
        assertTrue(worker.contains("if (ack.mode == \"TECHNICAL_ZIP\")"))
        assertTrue(worker.contains("OperationalHealthTechnicalPackage0575.generateAndSave"))
        assertFalse(polling.contains("RemoteHealthScheduler0747.enqueue"))
    }

    @Test
    fun technicalZipHasBoundedUploadAndSha256Evidence() {
        val worker = trips("RemoteHealthDiagnostic0747.kt")
        val remote = trips("TripRemoteApi.kt")
        assertTrue(worker.contains("640 * 1024"))
        assertTrue(worker.contains("MessageDigest.getInstance(\"SHA-256\")"))
        assertTrue(worker.contains("Base64.NO_WRAP"))
        assertTrue(worker.contains("technicalPackage = payload"))
        assertTrue(remote.contains("/v1/driver/remote-health/pending"))
        assertTrue(remote.contains("val remoteHealthVersion: Int = 2"))
    }
}
