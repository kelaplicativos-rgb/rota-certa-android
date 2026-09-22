package br.com.mapeiaia.rotacerta.trips

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationalTimelineDownload0616Test {
    @Test
    fun visibleAllTripsSurfaceOwnsDownloadTrigger0616() {
        val activity = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt",
        ).readText()
        val operational = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/OperationalAllTripsBrowserUi0563.kt",
        ).readText()

        assertTrue(activity.contains("operationalTimelineDownloadToken0616 += 1"))
        assertTrue(activity.contains("downloadTriggerToken0616 = operationalTimelineDownloadToken0616"))
        assertFalse(
            activity.substringAfter("AgendaHeaderAction0396(\"Baixar Timeline\")")
                .substringBefore("},")
                .contains("AgendaTimelineCommand0396.DOWNLOAD_TIMELINE"),
        )
        assertTrue(operational.contains("AgendaTimelineDownloadAction0399("))
        assertTrue(operational.contains("localAgendaTimelineDownloadResponse0516(projectedTimeline0602)"))
    }

    @Test
    fun modernAndroidDownloadUsesMediaStoreDownloads0616() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaTimelineDownload0398.kt",
        ).readText()

        assertTrue(source.contains("MediaStore.Downloads.EXTERNAL_CONTENT_URI"))
        assertTrue(source.contains("Environment.DIRECTORY_DOWNLOADS"))
        assertTrue(source.contains("TIMELINE_DOWNLOAD_COMPLETED_0616"))
        assertTrue(source.contains("TIMELINE_DOWNLOAD_FAILED_0616"))
        assertTrue(source.contains("withContext(Dispatchers.IO)"))
    }
}
