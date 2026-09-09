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
        assertTrue(automaticSync.contains("O servidor é a fonte canônica"))
        assertTrue(automaticSync.contains("central de coleta BlaBlaCar do Android"))
        assertTrue(automaticSync.contains("ABRIR AGENDA PÚBLICA"))
        assertFalse(timeline.contains("AgendaAutomaticSyncTimelineStatus0398("))
        assertTrue(activity.contains("TripScreen.AUTO_SYNC -> AgendaAutomaticSyncScreen0397("))
        assertTrue(activity.contains("networkSync=false automaticSyncOnly=true"))
        assertFalse(automaticSync.contains("Sincronizar agora"))
        assertFalse(activity.contains("AgendaHeaderAction0396(\"Sincronizar agora\")"))
        assertFalse(activity.contains("AgendaHeaderAction0396(\"Sincronizar BlaBlaCar\")"))
        assertFalse(activity.contains("AgendaHeaderAction0396(\"Publicar agenda\")"))
        assertFalse(activity.contains("reason = \"timeline_open\""))
        assertFalse(activity.contains("enqueueImmediate(activity, \"timeline_resume\")"))
        assertFalse(timeline.contains("autoSyncToken"))
        assertFalse(timeline.contains("recordExternalManualMutation"))
        assertFalse(passengerTimeline.contains("Sincronizar somente as vagas deste card"))
    }

    @Test
    fun publicQueryHasItsOwnTimelineAndIsNotMergedIntoOperationalTimeline() {
        assertTrue(timeline.contains("val publicResponseForTimeline: BlaBlaPublicSearchResponse? = null"))
        assertTrue(activity.contains("Resultado desta consulta pública"))
        assertTrue(activity.contains("Esta consulta possui Timeline própria e não é misturada à Timeline operacional."))
        assertTrue(activity.contains("BlaBlaPublicTimelineCard("))
        assertTrue(activity.contains("showCollectionActions = false"))
        assertTrue(activity.indexOf("BlaBlaPublicTimelineCard(") < activity.lastIndexOf("BlaBlaAuditableCollectionActions("))
    }

    @Test
    fun bothTimelinesOfferDownloadableAuditFiles() {
        assertTrue(download.contains("ActivityResultContracts.CreateDocument(\"application/json\")"))
        assertTrue(activity.contains("AgendaHeaderAction0396(\"Baixar Timeline\")"))
        assertTrue(activity.contains("AgendaTimelineCommand0396.DOWNLOAD_TIMELINE"))
        assertTrue(download.contains("AgendaTimelineDownloadAction0399("))
        assertTrue(timeline.contains("AgendaTimelineDownloadAction0399("))
        assertFalse(timeline.contains("AgendaTimelineDownloadButton0398("))
        assertFalse(timeline.contains("timeline-download-0398"))
        assertTrue(collection.contains("Text(\"⬇️ Baixar coleta\")"))
        assertTrue(collection.contains("ActivityResultContracts.CreateDocument"))
        assertFalse(collection.contains("Text(\"📤 Compartilhar coleta\")"))
    }

    @Test
    fun permanentSyncReconcilesStaleRotaCertaAllocationWithoutInventingBlaBlaSeats() {
        assertTrue(api.contains("/v1/driver/agenda/seat-allocation"))
        assertTrue(background.contains("PUBLIC_AGENDA_SEAT_ALLOCATION_RECONCILE_SKIPPED_0416"))
        assertTrue(background.contains("globalFanOut=false"))
        assertTrue(background.contains("per_trip_allocation_is_canonical"))
        assertTrue(backend.contains("async function reconcileDriverAgendaSeatAllocation"))
        assertTrue(backend.contains("/v1/driver/agenda/seat-allocation"))
        assertTrue(background.contains("trip.rotaCertaSeatAllocation"))
    }
    @Test
    fun offlineDownloadExportsTheCanonicalAgendaProjectionActuallyRendered0516() {
        val trip = Trip(
            id = "canonical-local-0516",
            title = "Santo André → São Thomé das Letras",
            departureAtMillis = 1_000L,
            capacity = 4,
            status = TripStatus.PUBLISHED,
            stops = listOf(
                TripStop(
                    id = "origin-0516",
                    order = 0,
                    name = "Santo André",
                    plannedDepartureMillis = 1_000L,
                ),
                TripStop(
                    id = "destination-0516",
                    order = 1,
                    name = "São Thomé das Letras",
                    plannedArrivalMillis = 2_000L,
                ),
            ),
            canonicalRevision = 12L,
            canonicalStateHash = "canonical-state-0516",
        )
        val booking = Booking(
            id = "booking-local-0516",
            tripId = trip.id,
            passengerName = "Passageiro canônico",
            passengerContact = "+55 11 99999-0000",
            boardingStopId = "origin-0516",
            dropoffStopId = "destination-0516",
            status = BookingStatus.CONFIRMED,
            source = BookingSource.BLABLACAR,
            capacityClaimType = CapacityClaimType.EXTERNAL_OCCUPANCY,
            fareMinorUnits = 9_300L,
            fareCurrencyCode = "BRL",
            boardingAddress = "Embarque privado",
            dropoffAddress = "Desembarque privado",
            boardingLatitude = -23.6639,
            boardingLongitude = -46.5383,
            dropoffLatitude = -21.7218,
            dropoffLongitude = -44.9849,
        )
        val projection = localAgendaTimelineProjection0515(
            trips = listOf(trip),
            bookings = listOf(booking),
            nowMillis = 3_000L,
        )
        val localResponse = localAgendaTimelineDownloadResponse0516(projection)
        val payload = agendaTimelineDownloadJson0398(
            response = localResponse,
            projectedBookings = projection.bookings,
            selectedCanonicalTripIds = setOf(trip.id),
            generatedAtMillis = 4_000L,
        )

        assertTrue(payload.contains("\"source\":\"CANONICAL_AGENDA_LOCAL_0516\""))
        assertTrue(payload.contains("\"provenancePolicy\":\"AGENDA_CANONICAL_LOCAL_FALLBACK_0516\""))
        assertTrue(payload.contains("\"canonicalTripId\":\"canonical-local-0516\""))
        assertTrue(payload.contains("\"passengerContact\":\"+55 11 99999-0000\""))
        assertTrue(payload.contains("\"boardingLatitude\":-23.6639"))
        assertFalse(payload.contains("\"trips\":[]"))
        assertTrue(timeline.contains("effectiveTimelineDownloadResponse0516"))
        assertTrue(timeline.contains("localAgendaTimelineDownloadResponse0516(canonicalProjection0494)"))
    }


}
