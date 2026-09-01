package br.com.mapeiaia.rotacerta.trips

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun agendaTimelineDownloadJson0398(
    entries: List<TripTimelineEntry>,
    status: AgendaBackgroundSyncStatus0397,
    generatedAtMillis: Long = System.currentTimeMillis(),
): String = buildJsonObject {
    put("schemaVersion", "1.0")
    put("kind", "rota_certa_timeline")
    put("generatedAtMillis", generatedAtMillis)
    put("automaticSyncEnabled", status.enabled)
    put("automaticSyncIntervalMinutes", status.intervalMinutes)
    put("automaticSyncLastFinishedAtMillis", status.lastFinishedAtMillis)
    put("automaticSyncLastTrigger", status.lastTrigger)
    put("automaticSyncLastResult", status.lastResult)
    put("automaticSyncRetryPending", status.retryPending)
    put("automaticSyncLastFailures", status.lastFailures)
    put("trips", buildJsonArray {
        entries.sortedBy(TripTimelineEntry::departureAtMillis).forEach { entry ->
            add(buildJsonObject {
                put("internalTripId", entry.localTripId ?: entry.tripId)
                put("timelineTripId", entry.tripId)
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
                put("blockedSeats", entry.operationalBlockedSeats)
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
internal fun AgendaTimelineDownloadButton0398(
    entries: List<TripTimelineEntry>,
    onChanged: (String) -> Unit,
) {
    val context = LocalContext.current
    val status = AgendaBackgroundSyncConfig0392.status(context)
    val payload = remember(entries, status) { agendaTimelineDownloadJson0398(entries, status) }
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

    Button(
        onClick = { launcher.launch(agendaTimelineDownloadFileName0398()) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("⬇️ Baixar Timeline")
    }
}
