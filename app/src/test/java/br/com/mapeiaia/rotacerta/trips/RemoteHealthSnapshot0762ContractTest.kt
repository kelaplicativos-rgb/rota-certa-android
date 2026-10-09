package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteHealthSnapshot0762ContractTest {
    private fun trips(name: String): String =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/$name").readText()

    @Test
    fun healthSnapshotRequiresDriverConsentBeforeReadingLogs() {
        val poller = trips("RemoteCoversPollService0758.kt")
        val consent = trips("RemoteSupportConsent0746.kt")
        val api = trips("TripRemoteApi.kt")
        assertTrue(poller.contains("pollRemoteHealthPending0762()"))
        assertTrue(poller.contains("shouldOfferRemoteHealthConsent0762"))
        assertTrue(poller.contains("showConsent(\"remote_health_collect\", jobId)"))
        assertFalse(poller.contains("RemoteHealthScheduler0747.enqueue"))
        assertTrue(consent.contains("RemoteHealthScheduler0747.enqueue(context, jobId)"))
        assertTrue(api.contains("/v1/driver/remote-health/pending-snapshot"))
        assertTrue(api.contains("requireDriverToken = true"))
    }

    @Test
    fun technicalZipAndHealthPopupMustRemainIndependent() {
        val poller = trips("RemoteCoversPollService0758.kt")
        assertTrue(poller.contains("seenTechnicalJobId"))
        assertTrue(poller.contains("seenHealthJobId"))
        assertTrue(poller.contains("job.mode == \"HEALTH_SNAPSHOT\""))
        assertFalse(poller.contains("seenTechnicalJobId = \"\"\n                                    main.post { closePopupFor(\"remote_health_collect\") }"))
    }
}
