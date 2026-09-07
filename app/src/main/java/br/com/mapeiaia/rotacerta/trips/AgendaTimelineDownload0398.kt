package br.com.mapeiaia.rotacerta.trips

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

private val timelineExportJson0495 = Json {
    encodeDefaults = true
    explicitNulls = true
}

internal fun agendaTimelineDownloadJson0398(
    response: DriverTripSyncStateResponse0402?,
    projectedBookings: List<Booking> = emptyList(),
    selectedCanonicalTripIds: Set<String> = emptySet(),
    generatedAtMillis: Long = System.currentTimeMillis(),
): String = buildJsonObject {
    put("schemaVersion", "3.0")
    put("kind", "rota_certa_timeline")
    put("source", "CANONICAL_BACKEND")
    put("generatedAtMillis", generatedAtMillis)
    put("canonicalSnapshotAtMillis", response?.snapshotAtMillis ?: 0L)
    put("collectorFallback", false)
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
                    canonical.forEach { (key, value) -> put(key, value) }
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
        if (triggerToken > 0) {
            launcher.launch(agendaTimelineDownloadFileName0398())
        }
    }
}
