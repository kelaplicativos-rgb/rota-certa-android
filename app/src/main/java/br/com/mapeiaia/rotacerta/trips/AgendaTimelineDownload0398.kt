package br.com.mapeiaia.rotacerta.trips

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

private val timelineExportJson0495 = Json {
    encodeDefaults = true
}

private val timelineExportForbiddenKeys0500 = setOf(
    "blablaProfileUuid",
    "blablaTripId",
    "blablaPublicUrl",
    "publishedSeats",
    "privateMirrorAvailable0499",
    "privateMirrorCurrent0499",
    "privateMirrorRevision0499",
    "privateStateHash0499",
)

internal fun localAgendaTimelineDownloadResponse0516(
    projection: CanonicalTimelineProjection0494,
): DriverTripSyncStateResponse0402 {
    val bookingsByTrip = projection.bookings.groupBy(Booking::tripId)
    val entriesByTrip = projection.entries.associateBy(TripTimelineEntry::tripId)
    val states = projection.trips.map { trip ->
        val tripBookings = bookingsByTrip[trip.id].orEmpty()
        val loads = SeatAvailabilityEngine.segmentLoads(
            trip = trip,
            bookings = tripBookings,
            nowMillis = projection.snapshotAtMillis,
        )
        val entry = entriesByTrip[trip.id]
        DriverTripSyncState0402(
            remoteTripId = trip.remoteId ?: trip.id,
            status = trip.status.name,
            departureAtMillis = trip.departureAtMillis,
            arrivalAtMillis = trip.stops.maxOfOrNull { stop ->
                stop.plannedArrivalMillis ?: stop.plannedDepartureMillis ?: trip.departureAtMillis
            } ?: trip.departureAtMillis,
            stops = trip.stops,
            capacityReliable = trip.capacityReliable,
            publicationRevision = trip.publicationRevision,
            canonicalRevision = trip.canonicalRevision,
            canonicalTripId = trip.id,
            canonicalStateHash = trip.canonicalStateHash,
            bookingsCount = tripBookings.size,
            tripKey = trip.tripKey,
            driverDisplayName = "",
            title = trip.title,
            publicUrl = trip.publicUrl.orEmpty(),
            publicBookingEnabled = trip.publicBookingEnabled,
            itineraryAuthoritative = trip.itineraryAuthoritative,
            capacity = trip.capacity,
            rotaCertaSeatAllocation = trip.rotaCertaSeatAllocation,
            operationalAvailableSeats = loads.minOfOrNull(SegmentLoad::availableSeats),
            availableSeatsMinimum = loads.minOfOrNull(SegmentLoad::availableSeats),
            availableSeatsMaximum = loads.maxOfOrNull(SegmentLoad::availableSeats),
            minimumOccupiedSeats = loads.minOfOrNull(SegmentLoad::occupiedSeats) ?: 0,
            maximumOccupiedSeats = loads.maxOfOrNull(SegmentLoad::occupiedSeats) ?: 0,
            operationalBlockedSeats = loads.maxOfOrNull(SegmentLoad::blockedSeats) ?: 0,
            operationalOverbookingSeats = loads.maxOfOrNull(SegmentLoad::overbookingSeats) ?: 0,
            segmentLoads = loads.map(SegmentLoad::occupiedSeats),
            segmentPassengerLoads = loads.map(SegmentLoad::passengerSeats),
            segmentBlockedLoads = loads.map(SegmentLoad::blockedSeats),
            segmentAvailableSeats = loads.map(SegmentLoad::availableSeats),
            sourceSeatCounts = entry?.sourcePassengerSeats
                .orEmpty()
                .mapKeys { (source, _) -> source.name },
            notes0499 = trip.notes,
            timezoneId0499 = trip.publicTimezoneId0411,
            bookings = tripBookings.map { booking ->
                RemoteBooking(
                    id = booking.id,
                    tripId = trip.id,
                    passengerId = booking.passengerId,
                    passengerName = booking.passengerName,
                    passengerContact = booking.passengerContact,
                    boardingStopId = booking.boardingStopId,
                    dropoffStopId = booking.dropoffStopId,
                    seats = booking.seats,
                    status = booking.status.name,
                    operationalStatus = booking.operationalStatus,
                    paymentStatus = booking.paymentStatus,
                    lastDriverSelection = booking.lastDriverSelection,
                    createdAtMillis = booking.createdAtMillis,
                    updatedAtMillis = booking.updatedAtMillis,
                    source = booking.source,
                    capacityClaimType = booking.capacityClaimType,
                    sourceReference = booking.sourceReference,
                    occupancyGroupId = booking.occupancyGroupId,
                    fareMinorUnits = booking.fareMinorUnits,
                    fareCurrencyCode = booking.fareCurrencyCode,
                    boardingAddress = booking.boardingAddress,
                    dropoffAddress = booking.dropoffAddress,
                    boardingLatitude = booking.boardingLatitude,
                    boardingLongitude = booking.boardingLongitude,
                    dropoffLatitude = booking.dropoffLatitude,
                    dropoffLongitude = booking.dropoffLongitude,
                    holdExpiresAtMillis = booking.holdExpiresAtMillis,
                )
            },
            updatedAtMillis = trip.updatedAtMillis,
        )
    }
    return DriverTripSyncStateResponse0402(
        trips = states,
        source = "CANONICAL_AGENDA_LOCAL_0516",
        provenancePolicy0500 = "AGENDA_CANONICAL_LOCAL_FALLBACK_0516",
        collectorRead = false,
        collectorFallback = false,
        collectorDerivedData = false,
        snapshotAtMillis = projection.snapshotAtMillis,
    )
}

internal fun agendaTimelineDownloadJson0398(
    response: DriverTripSyncStateResponse0402?,
    projectedBookings: List<Booking> = emptyList(),
    selectedCanonicalTripIds: Set<String> = emptySet(),
    generatedAtMillis: Long = System.currentTimeMillis(),
): String = buildJsonObject {
    put("schemaVersion", "3.0")
    put("kind", "rota_certa_timeline")
    put("source", response?.source?.takeIf(String::isNotBlank) ?: "CANONICAL_NATIVE_FIREWALL")
    put("provenancePolicy", response?.provenancePolicy0500.orEmpty())
    put("generatedAtMillis", generatedAtMillis)
    put("canonicalSnapshotAtMillis", response?.snapshotAtMillis ?: 0L)
    put("collectorRead", false)
    put("collectorFallback", false)
    put("collectorDerivedData", false)
    put("trips", buildJsonArray {
        response?.trips
            .orEmpty()
            .asSequence()
            .filter { state ->
                val canonicalId = state.canonicalTripId.ifBlank { state.remoteTripId }
                selectedCanonicalTripIds.isEmpty() || canonicalId in selectedCanonicalTripIds
            }
            .sortedBy(DriverTripSyncState0402::departureAtMillis)
            .forEach { state ->
                val canonicalId = state.canonicalTripId.ifBlank { state.remoteTripId }
                val canonical = timelineExportJson0495
                    .encodeToJsonElement(DriverTripSyncState0402.serializer(), state)
                    .jsonObject
                add(buildJsonObject {
                    canonical.forEach { (key, value) ->
                        if (key !in timelineExportForbiddenKeys0500) put(key, value)
                    }
                    put("localMetadata", buildJsonObject {
                        put("authority", "LOCAL_METADATA")
                        put("bookings", buildJsonArray {
                            projectedBookings
                                .asSequence()
                                .filter { it.tripId == canonicalId }
                                .filter { booking ->
                                    booking.fareMinorUnits != null ||
                                        booking.fareCurrencyCode.isNotBlank() ||
                                        booking.boardingAddress.isNotBlank() ||
                                        booking.dropoffAddress.isNotBlank() ||
                                        booking.localMetadataTouched
                                }
                                .sortedBy(Booking::id)
                                .forEach { booking ->
                                    add(buildJsonObject {
                                        put("bookingId", booking.id)
                                        booking.fareMinorUnits?.let { put("fareMinorUnits", it) }
                                        put("fareCurrencyCode", booking.fareCurrencyCode)
                                        put("boardingAddress", booking.boardingAddress)
                                        put("dropoffAddress", booking.dropoffAddress)
                                        put("localMetadataTouched", booking.localMetadataTouched)
                                        put("hasCancellationToken", booking.cancellationToken != null)
                                    })
                                }
                        })
                    })
                })
            }
    })
}.toString()

internal fun agendaTimelineDownloadFileName0398(nowMillis: Long = System.currentTimeMillis()): String {
    val date = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")
        .format(Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()))
    return "rota-certa-timeline-$date.json"
}

internal fun agendaTimelineWriteToDownloads0616(
    context: Context,
    payload: String,
    fileName: String,
): String {
    require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        "Download direto requer Android 10 ou superior."
    }
    val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/Rota Certa"
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val resolver = context.applicationContext.contentResolver
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: error("Android não disponibilizou a pasta Downloads.")
    try {
        resolver.openOutputStream(uri, "w")
            ?.bufferedWriter(Charsets.UTF_8)
            ?.use { writer ->
                writer.write(payload)
                writer.flush()
            }
            ?: error("Não foi possível gravar a Timeline.")
        ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            .also { resolver.update(uri, it, null, null) }
        return "$relativePath/$fileName"
    } catch (error: Throwable) {
        resolver.delete(uri, null, null)
        throw error
    }
}

@Composable
internal fun AgendaTimelineDownloadAction0399(
    entries: List<TripTimelineEntry>,
    canonicalResponse0494: DriverTripSyncStateResponse0402?,
    canonicalBookings0494: List<Booking>,
    triggerToken: Int,
    onChanged: (String) -> Unit,
) {
    val context = LocalContext.current
    val selectedCanonicalTripIds = remember(entries) { entries.map(TripTimelineEntry::tripId).toSet() }
    val payload = remember(canonicalResponse0494, canonicalBookings0494, selectedCanonicalTripIds) {
        agendaTimelineDownloadJson0398(
            response = canonicalResponse0494,
            projectedBookings = canonicalBookings0494,
            selectedCanonicalTripIds = selectedCanonicalTripIds,
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")
                ?.bufferedWriter(Charsets.UTF_8)
                ?.use { it.write(payload) }
                ?: error("Não foi possível abrir o arquivo de destino.")
        }.onSuccess {
            onChanged("Download da Timeline concluído.")
        }.onFailure { error ->
            onChanged("Falha ao baixar a Timeline: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    LaunchedEffect(triggerToken) {
        if (triggerToken <= 0) return@LaunchedEffect
        val fileName = agendaTimelineDownloadFileName0398()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                withContext(Dispatchers.IO) {
                    agendaTimelineWriteToDownloads0616(
                        context = context,
                        payload = payload,
                        fileName = fileName,
                    )
                }
            }.onSuccess { location ->
                UnifiedDebugEventStore.recordAlways(
                    "TIMELINE_DOWNLOAD_COMPLETED_0616",
                    context.packageName,
                    "directDownloads=true fileName=$fileName bytes=${payload.toByteArray(Charsets.UTF_8).size} piiLogged=false",
                )
                onChanged("Timeline baixada em $location.")
            }.onFailure { error ->
                UnifiedDebugEventStore.recordAlways(
                    "TIMELINE_DOWNLOAD_FAILED_0616",
                    context.packageName,
                    "directDownloads=true error=${error.javaClass.simpleName} piiLogged=false",
                )
                onChanged("Falha ao baixar a Timeline: ${error.message ?: error.javaClass.simpleName}")
            }
        } else {
            launcher.launch(fileName)
        }
    }
}
