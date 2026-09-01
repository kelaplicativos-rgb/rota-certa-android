package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaTimelineAutoSync0398Test {
    private val activity = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()
    private val timeline = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()
    private val passengerTimeline = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt").readText()
    private val automaticSync = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaAutomaticSyncUi0397.kt").readText()
    private val download = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaTimelineDownload0398.kt").readText()
    private val collection = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaAuditableCollection.kt").readText()
    private val background = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaBackgroundSync0392.kt").readText()
    private val api = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripRemoteApi.kt").readText()
    private val backend = File("../trip-platform/functions/index.js").readText()

    @Test
    fun timelineRestoresLegacyBulkHiddenCardsWithoutReintroducingClearUi() {
        assertTrue(timeline.contains("clearLegacyBulkHiddenOnce0398"))
        assertTrue(timeline.contains("KEY_BULK_HIDE_MIGRATED_0398"))
        assertFalse(activity.contains("AgendaHeaderAction0396(\"Limpar Timeline\")"))
        assertFalse(timeline.contains("title = { Text(\"Limpar Timeline\") }"))
        assertFalse(timeline.contains("TIMELINE_VISUAL_CLEARED_BY_USER"))
    }

    @Test
    fun synchronizationIsAutomaticAndReadOnlyInTimeline() {
        assertTrue(automaticSync.contains("Esta é a única central de sincronização"))
        assertTrue(timeline.contains("AgendaAutomaticSyncTimelineStatus0398"))
        assertTrue(activity.contains("networkSync=false automaticSyncOnly=true"))
        assertFalse(automaticSync.contains("Sincronizar agora"))
        assertFalse(activity.contains("AgendaHeaderAction0396(\"Sincronizar agora\")"))
        assertFalse(activity.contains("AgendaHeaderAction0396(\"Sincronizar BlaBlaCar\")"))
        assertFalse(activity.contains("AgendaHeaderAction0396(\"Publicar agenda\")"))
        assertFalse(passengerTimeline.contains("Sincronizar somente as vagas deste card"))
    }

    @Test
    fun publicQueryHasItsOwnTimelineAndIsNotMergedIntoOperationalTimeline() {
        assertTrue(timeline.contains("val publicResponseForTimeline: BlaBlaPublicSearchResponse? = null"))
        assertTrue(activity.contains("Resultado desta consulta pública"))
        assertTrue(activity.contains("Esta consulta possui Timeline própria e não é misturada à Timeline operacional."))
        assertTrue(activity.contains("BlaBlaPublicTimelineCard("))
    }

    @Test
    fun bothTimelinesOfferDownloadableAuditFiles() {
        assertTrue(download.contains("ActivityResultContracts.CreateDocument(\"application/json\")"))
        assertTrue(download.contains("Text(\"⬇️ Baixar Timeline\")"))
        assertTrue(timeline.contains("AgendaTimelineDownloadButton0398("))
        assertTrue(collection.contains("Text(\"⬇️ Baixar coleta\")"))
        assertTrue(collection.contains("ActivityResultContracts.CreateDocument"))
        assertFalse(collection.contains("Text(\"📤 Compartilhar coleta\")"))
    }

    @Test
    fun permanentSyncReconcilesStaleRotaCertaAllocationWithoutInventingBlaBlaSeats() {
        assertTrue(api.contains("/v1/driver/agenda/seat-allocation"))
        assertTrue(background.contains("PUBLIC_AGENDA_SEAT_ALLOCATION_RECONCILED_0398"))
        assertTrue(backend.contains("async function reconcileDriverAgendaSeatAllocation"))
        assertTrue(backend.contains("rotaCertaSeatAllocation: allocation"))
        assertTrue(backend.contains("capacityKnown = false"))
        assertTrue(backend.contains("operationalAvailableSeats: 0"))
        assertTrue(backend.contains("publishedSeats + allocation"))
        assertTrue(backend.contains("/v1/driver/agenda/seat-allocation"))
    }
}
