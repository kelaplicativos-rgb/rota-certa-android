package br.com.mapeiaia.rotacerta.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TripTimelineScreen(
    trips: List<Trip>,
    bookings: List<Booking>,
    store: TripStore,
    onChanged: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val collectorStore = remember(context) { BlaBlaCollectorStateStore(context) }
    var collectorResponse by remember { mutableStateOf(collectorStore.lastResponse()) }
    val localEntries = remember(trips, bookings) {
        TripTimelineEngine.fromLocalAgenda(trips, bookings)
    }
    val entries = remember(localEntries, collectorResponse) {
        BlaBlaTimelineAdapter.merge(localEntries, collectorResponse)
    }
    val formatter = remember { DateTimeFormatter.ofPattern("dd/MM HH:mm") }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Linha do tempo", style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = onBack) { Text("Voltar") }
    }

    BlaBlaCollectorPanel(
        trips = trips,
        stateStore = collectorStore,
        currentResponse = collectorResponse,
        onResult = { collectorResponse = it },
        onChanged = onChanged,
    )

    if (entries.isEmpty()) {
        Text("Nenhuma viagem para organizar.")
        return
    }

    entries.forEach { entry ->
        val trip = trips.firstOrNull { it.id == entry.tripId }
        TimelineEntryCard(entry, trip, store, formatter, onChanged)
    }
}

@Composable
private fun TimelineEntryCard(
    entry: TripTimelineEntry,
    trip: Trip?,
    store: TripStore,
    formatter: DateTimeFormatter,
    onChanged: (String) -> Unit,
) {
    var quickOpen by remember(entry.tripId) { mutableStateOf(false) }
    val status = timelineStatus(entry)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val date = formatter.format(Instant.ofEpochMilli(entry.departureAtMillis).atZone(ZoneId.systemDefault()))
            Text("$date — ${entry.origin} → ${entry.destination} $status", style = MaterialTheme.typography.titleSmall)
            if (entry.capacity > 0) {
                val occupancy = if (entry.minimumOccupiedSeats == entry.maximumOccupiedSeats) {
                    "${entry.maximumOccupiedSeats}/${entry.capacity}"
                } else {
                    "${entry.minimumOccupiedSeats}–${entry.maximumOccupiedSeats}/${entry.capacity}"
                }
                Text("${entry.profileLabel} • ocupação $occupancy")
            } else {
                Text("${entry.profileLabel} • BlaBlaCar")
            }

            val sources = entry.sourcePassengerSeats
                .filterValues { it > 0 }
                .entries
                .sortedBy { it.key.ordinal }
                .joinToString(" • ") { (source, seats) -> "${sourceLabel(source)} $seats" }
            if (sources.isNotBlank()) Text(sources, style = MaterialTheme.typography.bodySmall)

            timelineIssueText(entry)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (trip != null && trip.status in setOf(TripStatus.PUBLISHED, TripStatus.FULL)) {
                OutlinedButton(onClick = { quickOpen = !quickOpen }) {
                    Text(if (quickOpen) "Fechar passageiros" else "+ Passageiro")
                }
                if (quickOpen) QuickPassengerPanel(trip, store, onChanged)
            }
        }
    }
}

private fun timelineStatus(entry: TripTimelineEntry): String = when {
    TripTimelineIssue.OVERBOOKING in entry.issues -> "❌"
    TripTimelineIssue.PHYSICAL_CONFLICT in entry.issues -> "❌"
    TripTimelineIssue.DUPLICATE in entry.issues -> "🔁"
    TripTimelineIssue.PROFILE_CONTINUITY in entry.issues -> "⚠️"
    TripTimelineIssue.VALIDATION_PENDING in entry.issues -> "⏳"
    else -> "✅"
}

private fun timelineIssueText(entry: TripTimelineEntry): String? {
    val labels = buildList {
        if (TripTimelineIssue.OVERBOOKING in entry.issues) add("capacidade excedida")
        if (TripTimelineIssue.PHYSICAL_CONFLICT in entry.issues) add("conflito físico")
        if (TripTimelineIssue.DUPLICATE in entry.issues) add("possível duplicidade")
        if (TripTimelineIssue.PROFILE_CONTINUITY in entry.issues) add("continuidade do perfil")
        if (TripTimelineIssue.VALIDATION_PENDING in entry.issues) add("UUID ainda não confirmado no detalhe")
    }
    return labels.takeIf { it.isNotEmpty() }?.joinToString(" • ")
}

private fun sourceLabel(source: BookingSource): String = when (source) {
    BookingSource.ROTA_CERTA -> "Rota Certa"
    BookingSource.BLABLACAR -> "BlaBlaCar"
    BookingSource.PRIVATE -> "Particular"
    BookingSource.OTHER -> "Outro"
}
