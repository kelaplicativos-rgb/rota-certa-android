package br.com.mapeiaia.rotacerta.trips

import br.com.mapeiaia.rotacerta.AppSettings
import br.com.mapeiaia.rotacerta.Coordinate
import br.com.mapeiaia.rotacerta.GeoDistance
import br.com.mapeiaia.rotacerta.SettingsRepository
import androidx.compose.runtime.collectAsState
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TripTimelineScreen(
    trips: List<Trip>,
    bookings: List<Booking>,
    store: TripStore,
    onChanged: (String) -> Unit,
    autoSyncToken: Int,
    onRequestBlaBlaSync: () -> Unit,
    onBack: () -> Unit,
    onManageLocal: (String) -> Unit,
) {
    val context = LocalContext.current
    val collectorStore = remember(context) { BlaBlaCollectorStateStore(context) }
    val archiveStore = remember(context) { TripTimelineArchiveStore(context) }
    var collectorResponse by remember { mutableStateOf(collectorStore.lastResponse()) }
    var archiveRevision by remember { mutableIntStateOf(0) }
    var showArchived by remember { mutableStateOf(false) }
    var showSync by remember { mutableStateOf(false) }
    var autoSyncProfileUuid by remember { mutableStateOf<String?>(null) }
    val settingsRepository = remember(context) { SettingsRepository(context) }
    val appSettings by settingsRepository.settings.collectAsState(initial = AppSettings())

    LaunchedEffect(autoSyncToken) {
        if (autoSyncToken > 0) showSync = true
    }
    var geo by remember { mutableStateOf<Map<String, TimelineGeoPoint>>(emptyMap()) }
    var geoReady by remember { mutableStateOf(false) }
    val localEntries = remember(trips, bookings) { TripTimelineEngine.fromLocalAgenda(trips, bookings) }
    val merged = remember(localEntries, collectorResponse) { BlaBlaTimelineAdapter.merge(localEntries, collectorResponse) }

    LaunchedEffect(merged.map { "${it.origin}|${it.destination}" }) {
        geoReady = merged.size < 2
        geo = TripTimelineGeoResolver.resolve(context, merged.flatMap { listOf(it.origin, it.destination) })
        geoReady = true
    }
    val physical = remember(merged, geo, geoReady) {
        if (geoReady) TripPhysicalRideConsolidator.consolidate(merged, geo) else emptyList()
    }
    val today = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val entries = remember(physical, archiveRevision, showArchived, today) {
        physical.filter { it.departureAtMillis >= today }
            .filter { archiveStore.isArchived(it) == showArchived }
            .sortedBy(TripTimelineEntry::departureAtMillis)
    }
    val formatter = remember { DateTimeFormatter.ofPattern("EEE, dd MMM yyyy • HH:mm", Locale.getDefault()) }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(if (showArchived) "Arquivadas" else "Próximas viagens", style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = onBack) { Text("Voltar") }
    }
    ResponsiveTripActions(listOf(
        ResponsiveTripAction(if (showSync) "Fechar sincronização" else "Sincronizar BlaBlaCar") { showSync = !showSync },
        ResponsiveTripAction(if (showArchived) "Ver próximas" else "Ver arquivadas") { showArchived = !showArchived },
    ))
    if (showSync) {
        BlaBlaCollectorPanel(
            trips = trips,
            stateStore = collectorStore,
            currentResponse = collectorResponse,
            onResult = { collectorResponse = it },
            onChanged = onChanged,
            autoSyncToken = autoSyncToken,
            autoSyncProfileUuid = autoSyncProfileUuid,
        )
    }

    if (!geoReady) {
        Text("Conferindo continuidade e rotas…")
        return
    }
    GlobalQuickPassengerPanel(
        entries = entries,
        trips = trips,
        store = store,
        formatter = formatter,
        onChanged = onChanged,
        onTargetSync = { profileUuid ->
            autoSyncProfileUuid = profileUuid
            onRequestBlaBlaSync()
        },
    )

    if (entries.isEmpty()) {
        Text(if (showArchived) "Nenhuma viagem arquivada." else "Nenhuma viagem futura.")
        return
    }

    LaunchedEffect(entries.map { it.tripId to it.issues }) {
        entries.firstOrNull { TripTimelineIssue.OVERBOOKING in it.issues }?.let {
            Toast.makeText(context, "URGENTE: há mais passageiros do que lugares em ${it.origin} → ${it.destination}.", Toast.LENGTH_LONG).show()
        }
    }

    entries.forEach { entry ->
        val trip = entry.localTripId?.let { id -> trips.firstOrNull { it.id == id } }
        val archived = archiveStore.isArchived(entry)
        TimelineEntryCard(
            entry = entry,
            trip = trip,
            store = store,
            formatter = formatter,
            archived = archived,
            onManageLocal = onManageLocal,
            onChanged = onChanged,
            onRequestBlaBlaSync = { profileUuid ->
                autoSyncProfileUuid = profileUuid
                onRequestBlaBlaSync()
            },
            homeCoordinate = appSettings.homeCoordinate,
            homeRadiusKm = appSettings.homeRadiusKm,
        ) {
            archiveStore.setArchived(entry, !archived)
            archiveRevision++
            onChanged(if (archived) "Viagem restaurada." else "Viagem arquivada sem cancelar a publicação.")
        }
    }
}

@Composable
private fun TimelineEntryCard(
    entry: TripTimelineEntry,
    trip: Trip?,
    store: TripStore,
    formatter: DateTimeFormatter,
    archived: Boolean,
    onManageLocal: (String) -> Unit,
    onChanged: (String) -> Unit,
    onRequestBlaBlaSync: (String?) -> Unit,
    homeCoordinate: Coordinate?,
    homeRadiusKm: Double,
    onArchive: () -> Unit,
) {
    val context = LocalContext.current
    var quickOpen by remember(entry.tripId) { mutableStateOf(false) }

    fun openCard() {
        when {
            !entry.blablaTripHref.isNullOrBlank() -> if (!openBlaBlaHref(context, entry, entry.blablaTripHref!!)) {
                Toast.makeText(context, "Conta BlaBlaCar não conectada.", Toast.LENGTH_LONG).show()
            }
            entry.localTripId != null -> onManageLocal(entry.localTripId)
        }
    }

    Card(onClick = ::openCard, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val date = formatter.format(Instant.ofEpochMilli(entry.departureAtMillis).atZone(ZoneId.systemDefault()))
            Text(date.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }, style = MaterialTheme.typography.labelLarge)
            val baseDirection = timelineBaseDirection(trip, homeCoordinate, homeRadiusKm)
            val directionPrefix = baseDirection?.let { "$it " }.orEmpty()
            Text("$directionPrefix${entry.origin} → ${entry.destination}", style = MaterialTheme.typography.titleMedium)

            val meta = listOfNotNull(entry.profileLabel.takeIf(String::isNotBlank), entry.blablaPrice).joinToString(" • ")
            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall)

            val occupied = entry.maximumOccupiedSeats
            if (entry.capacity > 0) {
                val free = (entry.capacity - occupied).coerceAtLeast(0)
                Text("$occupied/${entry.capacity} ocupadas • $free livre(s) ${statusMark(entry)}")
            } else if (occupied > 0) {
                Text("BlaBlaCar: $occupied lugar(es) reservado(s) ${statusMark(entry)}")
            } else {
                Text("Ocupação aguardando leitura ${statusMark(entry)}")
            }

            val sourceLine = entry.sourcePassengerSeats.filterValues { it > 0 }.entries.joinToString(" • ") { (source, seats) ->
                "${sourceShort(source)} $seats"
            }
            if (sourceLine.isNotBlank()) Text(sourceLine, style = MaterialTheme.typography.bodySmall)

            when {
                TripTimelineIssue.OVERBOOKING in entry.issues -> Text("❌ URGENTE: passageiros acima da capacidade física.")
                TripTimelineIssue.PHYSICAL_CONFLICT in entry.issues -> Text("❌ Conflito real de horário/local.")
                TripTimelineIssue.PROFILE_CONTINUITY in entry.issues -> Text("⚠️ Próxima origem não bate com a chegada anterior.")
                TripTimelineIssue.VALIDATION_PENDING in entry.issues -> Text("⏳ Falta confirmar a origem dos dados.")
            }

            val passengerRows = passengerCardRows(entry, trip, store)
            if (passengerRows.isNotEmpty()) {
                passengerRows.forEach { passenger ->
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        val label = buildString {
                            append(passenger.name)
                            passenger.phone?.let { append(" • ").append(displayPhone(it)) }
                            if (passenger.seats > 1) append(" • ").append(passenger.seats).append(" lugares")
                        }
                        if (!passenger.phone.isNullOrBlank()) {
                            TextButton(
                                onClick = {
                                    UnifiedDebugEventStore.record(
                                        "PASSENGER_WHATSAPP_OPEN",
                                        context.packageName,
                                        "timeline=true phone_present=true",
                                    )
                                    openWhatsApp(context, passenger.phone!!)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("💬 $label") }
                        } else {
                            OutlinedButton(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("$label • telefone não exposto") }
                        }
                        val source = passenger.sources.joinToString(" + ") { sourceShort(it) }
                        val identity = when {
                            passenger.matchedByPhone -> " • ✓ telefone"
                            passenger.probableMatch -> " • ⚠️ conferir vínculo"
                            passenger.phone.isNullOrBlank() -> " • telefone pendente"
                            else -> ""
                        }
                        Text(source + identity, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            ResponsiveTripActions(listOf(
                ResponsiveTripAction("+ Passageiro") { quickOpen = !quickOpen },
                ResponsiveTripAction(if (archived) "Restaurar" else "Arquivar") { onArchive() },
            ))
            if (trip != null && quickOpen) {
                QuickPassengerPanel(trip, store, onChanged) {
                    onRequestBlaBlaSync(entry.profileId.takeIf(::looksCanonicalProfileUuid))
                }
            }
            if (trip == null && quickOpen) Text("Defina a capacidade física desta viagem na Agenda antes de incluir passageiro particular.")
        }
    }
}

private data class PassengerCardRow(
    val name: String,
    val phone: String?,
    val seats: Int,
    val boarding: String? = null,
    val dropoff: String? = null,
    val sources: Set<BookingSource>,
    val matchedByPhone: Boolean = false,
    val probableMatch: Boolean = false,
)

private fun passengerCardRows(entry: TripTimelineEntry, trip: Trip?, store: TripStore): List<PassengerCardRow> {
    val rows = entry.blablaPassengers.map { passenger ->
        PassengerCardRow(
            name = passenger.name.trim(),
            phone = passenger.phone?.trim()?.takeIf(String::isNotEmpty),
            seats = passenger.seats.coerceAtLeast(1),
            boarding = passenger.boarding,
            dropoff = passenger.dropoff,
            sources = setOf(BookingSource.BLABLACAR),
        )
    }.toMutableList()

    if (trip == null) return rows
    val stops = trip.stops.associateBy(TripStop::id)
    val local = store.bookingsFor(trip.id)
        .filter { it.capacityClaimType == CapacityClaimType.PASSENGER }
        .filter { it.status == BookingStatus.CONFIRMED || it.status == BookingStatus.HELD }
        .filter { it.seats > 0 }

    local.forEach { booking ->
        val phone = booking.passengerContact.trim().takeIf(String::isNotEmpty)
        val boarding = stops[booking.boardingStopId]?.name
        val dropoff = stops[booking.dropoffStopId]?.name
        val phoneKey = normalizePhone(phone)
        val candidateIndex = rows.indexOfFirst { current ->
            val currentPhone = normalizePhone(current.phone)
            phoneKey.isNotBlank() && currentPhone.isNotBlank() && phoneKey == currentPhone
        }
        val secondaryIndex = if (candidateIndex >= 0) -1 else rows.indexOfFirst { current ->
            normalizePassengerName(current.name) == normalizePassengerName(booking.passengerName) &&
                current.seats == booking.seats &&
                routeEvidenceMatches(current.boarding, current.dropoff, boarding, dropoff)
        }
        val index = if (candidateIndex >= 0) candidateIndex else secondaryIndex
        if (index >= 0) {
            val current = rows[index]
            rows[index] = current.copy(
                name = current.name.ifBlank { booking.passengerName.trim() },
                phone = current.phone?.takeIf(String::isNotBlank) ?: phone,
                seats = maxOf(current.seats, booking.seats),
                boarding = current.boarding ?: boarding,
                dropoff = current.dropoff ?: dropoff,
                sources = current.sources + booking.source,
                matchedByPhone = candidateIndex >= 0,
                probableMatch = candidateIndex < 0,
            )
        } else {
            rows += PassengerCardRow(
                name = booking.passengerName.trim(),
                phone = phone,
                seats = booking.seats,
                boarding = boarding,
                dropoff = dropoff,
                sources = setOf(booking.source),
            )
        }
    }
    return rows.filter { it.name.isNotBlank() }
}

internal fun normalizePhone(raw: String?): String {
    val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return ""
    val digits = value.filter(Char::isDigit)
    if (digits.length !in 8..15) return ""
    return if (value.startsWith("+")) "+$digits" else "local:$digits"
}

private fun displayPhone(raw: String): String = raw.trim()

internal fun whatsappRecipient(raw: String): String? {
    val value = raw.trim().takeIf(String::isNotEmpty) ?: return null
    val digits = value.filter(Char::isDigit)
    if (digits.length !in 8..15) return null
    return digits
}

private fun normalizePassengerName(raw: String): String = java.text.Normalizer.normalize(raw.trim(), java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase()
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

private fun routeEvidenceMatches(aBoard: String?, aDrop: String?, bBoard: String?, bDrop: String?): Boolean {
    if (aBoard.isNullOrBlank() || aDrop.isNullOrBlank() || bBoard.isNullOrBlank() || bDrop.isNullOrBlank()) return false
    return placeIdentityKey(aBoard) == placeIdentityKey(bBoard) && placeIdentityKey(aDrop) == placeIdentityKey(bDrop)
}

private fun placeIdentityKey(raw: String): String = java.text.Normalizer.normalize(raw.substringBefore(',').trim(), java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase()
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

@Composable
private fun GlobalQuickPassengerPanel(
    entries: List<TripTimelineEntry>,
    trips: List<Trip>,
    store: TripStore,
    formatter: DateTimeFormatter,
    onChanged: (String) -> Unit,
    onTargetSync: (String?) -> Unit,
) {
    val options = entries.mapNotNull { entry ->
        trips.firstOrNull { it.id == entry.tripId }
            ?.takeIf { it.status in setOf(TripStatus.PUBLISHED, TripStatus.FULL) && it.capacity > 0 }
            ?.let { trip -> entry to trip }
    }.distinctBy { it.second.id }
    if (options.isEmpty()) return

    var open by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var selectedTripId by remember(options.map { it.second.id }) { mutableStateOf(options.first().second.id) }
    val selected = options.firstOrNull { it.second.id == selectedTripId } ?: options.first()
    val entry = selected.first
    val trip = selected.second
    val date = formatter.format(Instant.ofEpochMilli(entry.departureAtMillis).atZone(ZoneId.systemDefault()))
    val available = SeatAvailabilityEngine.remainingSeatsForWholeTrip(trip, store.bookingsFor(trip.id))

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { open = !open }, modifier = Modifier.fillMaxWidth()) {
                Text(if (open) "Fechar + Passageiro rápido" else "+ Passageiro rápido")
            }
            if (open) {
                androidx.compose.material3.TextButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Perfil/viagem: ${entry.profileLabel} • $date")
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    options.forEach { (optionEntry, optionTrip) ->
                        val optionDate = formatter.format(
                            Instant.ofEpochMilli(optionEntry.departureAtMillis).atZone(ZoneId.systemDefault())
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("${optionEntry.profileLabel} • $optionDate • ${optionEntry.origin} → ${optionEntry.destination}") },
                            onClick = {
                                selectedTripId = optionTrip.id
                                menuOpen = false
                            },
                        )
                    }
                }
                Text("${entry.origin} → ${entry.destination}")
                Text("Data/hora: $date • vagas Rota Certa: $available/${trip.capacity}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Ao salvar, a capacidade interna muda imediatamente e a conta selecionada é conferida em seguida.",
                    style = MaterialTheme.typography.bodySmall,
                )
                QuickPassengerPanel(trip, store, onChanged) {
                    onTargetSync(entry.profileId.takeIf(::looksCanonicalProfileUuid))
                }
            }
        }
    }
}

private fun looksCanonicalProfileUuid(value: String): Boolean = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"
).matches(value.trim())

private fun timelineBaseDirection(trip: Trip?, home: Coordinate?, radiusKm: Double): String? {
    if (trip == null || home == null || radiusKm < 0.0) return null
    val stops = trip.stops.sortedBy(TripStop::order)
    val origin = stops.firstOrNull()?.asCoordinate() ?: return null
    val destination = stops.lastOrNull()?.asCoordinate() ?: return null
    val radiusMeters = radiusKm * 1_000.0
    val originInside = GeoDistance.meters(home, origin) <= radiusMeters
    val destinationInside = GeoDistance.meters(home, destination) <= radiusMeters
    return when {
        originInside && !destinationInside -> "🟢⬆️"
        !originInside && destinationInside -> "🔴⬇️"
        else -> "↔️"
    }
}

private fun TripStop.asCoordinate(): Coordinate? {
    val lat = latitude ?: return null
    val lon = longitude ?: return null
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
    return Coordinate(lat, lon)
}

private fun statusMark(entry: TripTimelineEntry): String = when {
    TripTimelineIssue.OVERBOOKING in entry.issues || TripTimelineIssue.PHYSICAL_CONFLICT in entry.issues -> "❌"
    TripTimelineIssue.PROFILE_CONTINUITY in entry.issues || TripTimelineIssue.DUPLICATE in entry.issues -> "⚠️"
    TripTimelineIssue.VALIDATION_PENDING in entry.issues -> "⏳"
    else -> "✅"
}

private fun sourceShort(source: BookingSource): String = when (source) {
    BookingSource.BLABLACAR -> "BlaBlaCar"
    BookingSource.PRIVATE -> "Particular"
    BookingSource.ROTA_CERTA -> "Rota Certa"
    BookingSource.OTHER -> "Outro"
}

private fun openBlaBlaHref(context: Context, entry: TripTimelineEntry, href: String): Boolean {
    val profileUuid = entry.blablaProfileUuid?.trim()?.lowercase() ?: return false
    val account = BlaBlaDynamicAccountRegistry(context).list().firstOrNull { it.profileUuid?.trim()?.lowercase() == profileUuid } ?: return false
    context.startActivity(BlaBlaDynamicSessionIntents.manage(context, account, href).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    return true
}

private fun openWhatsApp(context: Context, raw: String) {
    val digits = whatsappRecipient(raw) ?: return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private class TripTimelineArchiveStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun isArchived(entry: TripTimelineEntry): Boolean = aliases(entry).any { key -> prefs.getBoolean(key, false) }
    fun setArchived(entry: TripTimelineEntry, archived: Boolean) {
        val edit = prefs.edit()
        aliases(entry).forEach { edit.putBoolean(it, archived) }
        edit.apply()
    }
    private fun aliases(entry: TripTimelineEntry): Set<String> = setOfNotNull(
        entry.localTripId?.let { "local:$it" },
        entry.blablaTripId?.let { "blabla-id:$it" },
        entry.blablaTripHref?.let { "blabla-href:${it.substringBefore("&search_uuid=")}" },
        "timeline:${entry.tripId}",
    )
    companion object { private const val PREFS = "rota_certa_timeline_archive_v1" }
}
