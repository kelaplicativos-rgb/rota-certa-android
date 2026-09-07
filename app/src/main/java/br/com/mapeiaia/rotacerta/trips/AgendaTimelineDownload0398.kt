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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun agendaTimelineDownloadJson0398(
    entries: List<TripTimelineEntry>,
    generatedAtMillis: Long = System.currentTimeMillis(),
): String = buildJsonObject {
    put("schemaVersion", "2.0")
    put("kind", "rota_certa_timeline")
    put("source", "CANONICAL_BACKEND")
    put("generatedAtMillis", generatedAtMillis)
    put("collectorFallback", false)
    put("trips", buildJsonArray {
        entries.sortedBy(TripTimelineEntry::departureAtMillis).forEach { entry ->
            add(buildJsonObject {
                put("canonicalTripId", entry.tripId)
                put("canonicalRevision", entry.canonicalRevision0494)
                put("canonicalStateHash", entry.canonicalStateHash0494)
                put("remoteTripId", entry.remoteTripId0494)
                put("profileUuid", entry.blablaProfileUuid.orEmpty())
                put("blablaTripId", entry.blablaTripId.orEmpty())
                put("profileLabel", entry.profileLabel)
                put("departureAtMillis", entry.departureAtMillis)
                put("arrivalAtMillis", entry.arrivalAtMillis ?: 0L)
                put("origin", entry.origin)
                put("destination", entry.destination)
                put("status", entry.status.name)
                put("operationalInventory", entry.capacity)
                put("blablaPublishedSeats", entry.blablaPublishedSeats ?: -1)
                put("rotaCertaSeatAllocation", entry.rotaCertaSeatAllocation ?: -1)
                put("minimumOccupiedSeats", entry.minimumOccupiedSeats)
                put("maximumOccupiedSeats", entry.maximumOccupiedSeats)
                put("availableSeatsMinimum", entry.canonicalAvailableSeatsMinimum0494 ?: -1)
                put("availableSeatsMaximum", entry.canonicalAvailableSeatsMaximum0494 ?: -1)
                put("blockedSeats", entry.operationalBlockedSeats)
                put("overbookingSeats", entry.canonicalOverbookingSeats0494)
                put("canonicalBackendAuthoritative", entry.canonicalBackendAuthoritative0494)
                put("canonicalUpdatedAtMillis", entry.canonicalUpdatedAtMillis0494)
                put("issues", entry.issues.joinToString(",") { it.name })
                put("sourceSeatCounts", entry.sourcePassengerSeats.entries.joinToString(",") { (source, seats) -> "${source.name}:$seats" })
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
    triggerToken: Int,
    onChanged: (String) -> Unit,
) {
    val context = LocalContext.current
    val payload = remember(entries) { agendaTimelineDownloadJson0398(entries) }
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
