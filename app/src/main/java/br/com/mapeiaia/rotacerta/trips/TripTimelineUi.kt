package br.com.mapeiaia.rotacerta.trips

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import br.com.mapeiaia.rotacerta.AppSettings
import br.com.mapeiaia.rotacerta.Coordinate
import br.com.mapeiaia.rotacerta.DeviceLocationService
import br.com.mapeiaia.rotacerta.GeoDistance
import br.com.mapeiaia.rotacerta.SettingsRepository
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TripTimelineScreen(
    trips: List<Trip>,
    bookings: List<Booking>,
    store: TripStore,
    onChanged: (String) -> Unit,
    autoSyncToken: Int,
    onRequestBlaBlaSync: () -> Unit,
    onCreateTrip: () -> Unit,
    onPinShortcut: () -> Unit,
    onOpenOnlineSettings: () -> Unit,
    onBack: () -> Unit,
    onManageLocal: (String) -> Unit,
) {
    val context = LocalContext.current
    val collectorStore = remember(context) { BlaBlaCollectorStateStore(context) }
    val archiveStore = remember(context) { TripTimelineArchiveStore(context) }
    val referenceStore = remember(context) { TripReferenceOriginStore(context) }
    val locationService = remember(context) { DeviceLocationService(context) }
    var collectorResponse by remember { mutableStateOf(collectorStore.lastResponse()) }
    var archiveRevision by remember { mutableIntStateOf(0) }
    var showArchived by remember { mutableStateOf(false) }
    var showSync by remember { mutableStateOf(false) }
    var autoSyncProfileUuid by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var referenceOrigin by remember { mutableStateOf(referenceStore.read()) }
    var currentCoordinate by remember { mutableStateOf<Coordinate?>(null) }
    val settingsRepository = remember(context) { SettingsRepository(context) }
    val appSettings by settingsRepository.settings.collectAsState(initial = AppSettings())

    LaunchedEffect(autoSyncToken) {
        if (autoSyncToken > 0) showSync = true
    }
    LaunchedEffect(Unit) {
        while (true) {
            currentCoordinate = runCatching { locationService.currentCoordinate() }.getOrNull()
            delay(30_000L)
        }
    }

    val localEntries = remember(trips, bookings) { TripTimelineEngine.fromLocalAgenda(trips, bookings) }
    val mergedRaw = remember(localEntries, collectorResponse) { BlaBlaTimelineAdapter.merge(localEntries, collectorResponse) }
    val merged = remember(mergedRaw, appSettings.vehicleCapacity) {
        applyConfiguredVehicleCapacity(mergedRaw, appSettings.vehicleCapacity)
    }
    val directionGeo = remember(merged, trips, appSettings) {
        TripTimelineGeoResolver.resolveTrustedStops(
            places = merged.flatMap { listOf(it.origin, it.destination) },
            trustedStops = timelineTrustedDirectionStops(trips, appSettings),
        )
    }
    val physical = remember(merged, directionGeo) {
        TripPhysicalRideConsolidator.consolidate(merged, directionGeo)
    }
    val today = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val entries = remember(physical, archiveRevision, showArchived, today) {
        physical.filter { it.departureAtMillis >= today }
            .filter { archiveStore.isArchived(it) == showArchived }
            .sortedBy(TripTimelineEntry::departureAtMillis)
    }
    val visibleEntries = remember(entries, trips, bookings, searchQuery) {
        filterTimelineEntries(entries, trips, bookings, searchQuery)
    }
    val formatter = remember { DateTimeFormatter.ofPattern("EEE, dd MMM yyyy • HH:mm", Locale.getDefault()) }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(if (showArchived) "Arquivadas" else "Próximas viagens", style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = onBack) { Text("Voltar") }
    }
    TripDriverDefaultsCard(
        settings = appSettings,
        repository = settingsRepository,
        referenceOrigin = referenceOrigin,
        onReferenceChanged = { origin ->
            referenceOrigin = origin
            onChanged("Origem de referência definida pelo GPS. Os cards foram reclassificados.")
        },
        onChanged = onChanged,
    )
    ResponsiveTripActions(listOf(
        ResponsiveTripAction("Nova viagem", onClick = onCreateTrip),
        ResponsiveTripAction("Fixar atalho", onClick = onPinShortcut),
        ResponsiveTripAction("Integração online", onClick = onOpenOnlineSettings),
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

    OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        label = { Text("Buscar na Timeline") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
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

    GlobalQuickPassengerPanel(
        entries = visibleEntries,
        trips = trips,
        store = store,
        formatter = formatter,
        onChanged = onChanged,
        onTargetSync = { profileUuid ->
            autoSyncProfileUuid = profileUuid
            onRequestBlaBlaSync()
        },
    )

    if (visibleEntries.isEmpty()) {
        Text("Nenhuma viagem corresponde à busca.")
        return
    }

    visibleEntries.forEach { entry ->
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
            referenceCoordinate = referenceOrigin?.coordinate,
            referenceRadiusKm = referenceOrigin?.radiusKm ?: TripReferenceOrigin.DEFAULT_RADIUS_KM,
            directionGeo = directionGeo,
            currentCoordinate = currentCoordinate,
        ) {
            archiveStore.setArchived(entry, !archived)
            archiveRevision++
            onChanged(if (archived) "Viagem restaurada." else "Viagem arquivada sem cancelar a publicação.")
        }
    }
}

@Composable
private fun TripDriverDefaultsCard(
    settings: AppSettings,
    repository: SettingsRepository,
    referenceOrigin: TripReferenceOrigin?,
    onReferenceChanged: (TripReferenceOrigin) -> Unit,
    onChanged: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val referenceStore = remember(context) { TripReferenceOriginStore(context) }
    val locationService = remember(context) { DeviceLocationService(context) }
    var capacity by remember(settings.vehicleCapacity) {
        mutableStateOf(settings.vehicleCapacity.takeIf { it in 1..999 }?.toString().orEmpty())
    }
    var locating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun captureReferenceOrigin() {
        if (locating) return
        locating = true
        error = null
        scope.launch {
            val fix = runCatching { locationService.freshReferenceFix() }.getOrNull()
            if (fix == null) {
                error = "Não foi possível obter uma localização recente e confiável. Tente novamente com o GPS ativo."
            } else {
                val origin = TripReferenceOrigin(
                    latitude = fix.coordinate.latitude,
                    longitude = fix.coordinate.longitude,
                    accuracyMeters = fix.accuracyMeters,
                    capturedAtMillis = fix.capturedAtMillis,
                    radiusKm = referenceOrigin?.radiusKm ?: TripReferenceOrigin.DEFAULT_RADIUS_KM,
                )
                referenceStore.save(origin)
                onReferenceChanged(origin)
                UnifiedDebugEventStore.record(
                    "TRIP_REFERENCE_ORIGIN_CAPTURED",
                    context.packageName,
                    "accuracy=${fix.accuracyMeters ?: -1f} cached=${fix.fromCachedLocation} coordinate_saved=true",
                )
            }
            locating = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) captureReferenceOrigin()
        else error = "Permissão de localização negada. A origem anterior foi preservada."
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Dados do veículo", style = MaterialTheme.typography.titleSmall)
            Text(
                if (referenceOrigin == null) "Origem de referência ainda não definida." else "📍 Origem definida por GPS",
                style = MaterialTheme.typography.bodyMedium,
            )
            referenceOrigin?.let { origin ->
                val precision = origin.accuracyMeters?.let { " • precisão aproximada ${it.toInt()} m" }.orEmpty()
                Text("Referência fixa$precision", style = MaterialTheme.typography.bodySmall)
            }
            Button(
                enabled = !locating,
                onClick = {
                    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
                        captureReferenceOrigin()
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (locating) "Obtendo GPS…" else if (referenceOrigin == null) "📍 DEFINIR ORIGEM" else "📍 REDEFINIR ORIGEM") }

            OutlinedTextField(
                value = capacity,
                onValueChange = { capacity = it.filter(Char::isDigit).take(3) },
                label = { Text("Capacidade do veículo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "A origem de referência é fixa até ser redefinida. O GPS atual continua separado e serve apenas para progresso da rota e próximo embarque.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "A capacidade física informada aqui é a referência do Rota Certa. A leitura externa de lugares publicados não define essa capacidade.",
                style = MaterialTheme.typography.bodySmall,
            )
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Button(
                onClick = {
                    val parsed = capacity.toIntOrNull()
                    if (parsed == null || parsed !in 1..999) {
                        error = "Informe uma capacidade entre 1 e 999 lugares."
                        return@Button
                    }
                    error = null
                    scope.launch {
                        repository.saveSettings(settings.copy(vehicleCapacity = parsed))
                        UnifiedDebugEventStore.record(
                            "TRIP_DRIVER_DEFAULTS_SAVED",
                            context.packageName,
                            "vehicleCapacity=$parsed externalSeatAuthority=false referenceOriginConfigured=${referenceOrigin != null}",
                        )
                        onChanged("Capacidade do veículo salva.")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Salvar capacidade") }
        }
    }
}

internal fun applyConfiguredVehicleCapacity(
    entries: List<TripTimelineEntry>,
    vehicleCapacity: Int,
): List<TripTimelineEntry> {
    if (vehicleCapacity !in 1..999) return entries
    return entries.map { entry ->
        if (entry.capacity > 0) entry else entry.copy(capacity = vehicleCapacity)
    }
}

internal enum class TimelineOccupancyReadState {
    CAPACITY_CONFIGURED,
    CAPACITY_CONFIGURED_ROSTER_PENDING,
    RESERVED,
    COMPLETE_EMPTY,
    PENDING,
}

private fun hasExternalPublication(entry: TripTimelineEntry): Boolean =
    !entry.blablaTripId.isNullOrBlank() ||
        !entry.blablaTripHref.isNullOrBlank() ||
        !entry.blablaProfileUuid.isNullOrBlank()

internal fun timelineOccupancyReadState(entry: TripTimelineEntry): TimelineOccupancyReadState = when {
    entry.capacity > 0 && hasExternalPublication(entry) && entry.blablaPassengerRosterComplete != true && entry.maximumOccupiedSeats <= 0 ->
        TimelineOccupancyReadState.CAPACITY_CONFIGURED_ROSTER_PENDING
    entry.capacity > 0 -> TimelineOccupancyReadState.CAPACITY_CONFIGURED
    entry.maximumOccupiedSeats > 0 -> TimelineOccupancyReadState.RESERVED
    entry.blablaPassengerRosterComplete == true -> TimelineOccupancyReadState.COMPLETE_EMPTY
    else -> TimelineOccupancyReadState.PENDING
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
    referenceCoordinate: Coordinate?,
    referenceRadiusKm: Double,
    directionGeo: Map<String, TimelineGeoPoint>,
    currentCoordinate: Coordinate?,
    onArchive: () -> Unit,
) {
    val context = LocalContext.current
    var quickOpen by remember(entry.tripId) { mutableStateOf(false) }
    val direction = timelineDirectionState(
        entry = entry,
        trip = trip,
        trustedGeo = directionGeo,
        reference = referenceCoordinate,
        radiusKm = referenceRadiusKm,
    )
    val dark = isSystemInDarkTheme()
    val cardColor = when (direction) {
        TimelineDirectionState.OUTBOUND -> if (dark) Color(0xFF17351F) else Color(0xFFEAF7ED)
        TimelineDirectionState.INBOUND -> if (dark) Color(0xFF3B291F) else Color(0xFFFFF0E6)
        TimelineDirectionState.NEUTRAL,
        TimelineDirectionState.UNKNOWN,
        -> MaterialTheme.colorScheme.surface
    }

    fun openCard() {
        when {
            !entry.blablaTripHref.isNullOrBlank() -> if (!openBlaBlaHref(context, entry, entry.blablaTripHref!!)) {
                Toast.makeText(context, "Conta BlaBlaCar não conectada.", Toast.LENGTH_LONG).show()
            }
            entry.localTripId != null -> onManageLocal(entry.localTripId)
        }
    }

    Card(
        onClick = ::openCard,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
    ) {
        Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val date = formatter.format(Instant.ofEpochMilli(entry.departureAtMillis).atZone(ZoneId.systemDefault()))
            Text(date.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }, style = MaterialTheme.typography.labelLarge)
            timelineDirectionDisplayLabel(direction)?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
            Text("${entry.origin} → ${entry.destination}", style = MaterialTheme.typography.titleMedium)

            val meta = listOfNotNull(entry.profileLabel.takeIf(String::isNotBlank), entry.blablaPrice).joinToString(" • ")
            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall)

            val occupied = entry.maximumOccupiedSeats
            when (timelineOccupancyReadState(entry)) {
                TimelineOccupancyReadState.CAPACITY_CONFIGURED -> {
                    val free = (entry.capacity - occupied).coerceAtLeast(0)
                    Text("$occupied/${entry.capacity} ocupadas • $free livre(s) ${statusMark(entry)}")
                }
                TimelineOccupancyReadState.CAPACITY_CONFIGURED_ROSTER_PENDING ->
                    Text("Capacidade: ${entry.capacity} • reservas aguardando leitura ⏳")
                TimelineOccupancyReadState.RESERVED ->
                    Text("BlaBlaCar: $occupied lugar(es) reservado(s) ${statusMark(entry)}")
                TimelineOccupancyReadState.COMPLETE_EMPTY ->
                    Text("BlaBlaCar: 0 lugares reservados ${statusMark(entry)}")
                TimelineOccupancyReadState.PENDING ->
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
                TripTimelineIssue.EXTERNAL_IDENTITY_CONFLICT in entry.issues -> Text("⚠️ Identidade externa em conflito; confira esta publicação.")
                TripTimelineIssue.VALIDATION_PENDING in entry.issues -> Text("⏳ Falta confirmar a origem dos dados.")
            }

            EnhancedPassengerTimelineSection(
                entry = entry,
                trip = trip,
                store = store,
                currentCoordinate = currentCoordinate,
                onChanged = onChanged,
            )

            ResponsiveTripActions(listOf(
                ResponsiveTripAction("+ Passageiro") { quickOpen = !quickOpen },
                ResponsiveTripAction(if (archived) "Restaurar" else "Arquivar") { onArchive() },
            ))
            if (trip != null && quickOpen) {
                QuickPassengerPanel(trip, store, onChanged) {
                    onRequestBlaBlaSync(canonicalTimelineProfileUuid(entry))
                }
            }
            if (trip == null && quickOpen) {
                Text(
                    if (entry.capacity > 0) {
                        "Capacidade padrão do veículo: ${entry.capacity}. Vincule esta publicação à agenda interna para controlar passageiros por trecho."
                    } else {
                        "A publicação foi coletada, mas a capacidade física ainda não está configurada no Rota Certa."
                    },
                )
                ExternalTripCapacitySetup(entry, store, onChanged, entry.capacity)
            }
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

internal data class TimelineQuickPassengerOption(
    val entry: TripTimelineEntry,
    val localTrip: Trip?,
)

internal fun timelineQuickPassengerOptions(
    entries: List<TripTimelineEntry>,
    trips: List<Trip>,
): List<TimelineQuickPassengerOption> = entries
    .filter { entry ->
        entry.status in setOf(TripStatus.PUBLISHED, TripStatus.FULL) ||
            !entry.blablaProfileUuid.isNullOrBlank() ||
            !entry.blablaTripId.isNullOrBlank() ||
            !entry.blablaTripHref.isNullOrBlank()
    }
    .map { entry ->
        val localTrip = entry.localTripId
            ?.let { id -> trips.firstOrNull { it.id == id } }
            ?: trips.firstOrNull { it.id == entry.tripId }
            ?: findExistingTimelineBackingTrip(entry, trips)
        TimelineQuickPassengerOption(entry, localTrip)
    }
    .distinctBy { it.entry.tripId }

internal fun canonicalTimelineProfileUuid(entry: TripTimelineEntry): String? =
    entry.blablaProfileUuid?.takeIf(::looksCanonicalProfileUuid)
        ?: entry.profileId.takeIf(::looksCanonicalProfileUuid)

internal fun findExistingTimelineBackingTrip(entry: TripTimelineEntry, trips: List<Trip>): Trip? = trips.firstOrNull { trip ->
    kotlin.math.abs(trip.departureAtMillis - entry.departureAtMillis) <= 45L * 60L * 1000L &&
        trip.stops.sortedBy(TripStop::order).let { stops ->
            val first = stops.firstOrNull()?.name ?: return@let false
            val last = stops.lastOrNull()?.name ?: return@let false
            sameTimelinePlace(first, entry.origin) && sameTimelinePlace(last, entry.destination)
        }
}

internal fun buildTimelineBackingTrip(entry: TripTimelineEntry, capacity: Int): Trip {
    require(capacity > 0) { "Informe uma capacidade física válida." }
    val origin = entry.origin.trim()
    val destination = entry.destination.trim()
    require(origin.isNotBlank() && destination.isNotBlank()) { "Origem e destino precisam estar disponíveis." }
    return Trip(
        title = "$origin → $destination",
        departureAtMillis = entry.departureAtMillis,
        capacity = capacity,
        status = TripStatus.DRAFT,
        stops = listOf(
            TripStop(
                order = 0,
                name = origin,
                address = origin,
                plannedDepartureMillis = entry.departureAtMillis,
            ),
            TripStop(
                order = 1,
                name = destination,
                address = destination,
                plannedArrivalMillis = entry.arrivalAtMillis,
            ),
        ),
    )
}

@Composable
private fun ExternalTripCapacitySetup(
    entry: TripTimelineEntry,
    store: TripStore,
    onChanged: (String) -> Unit,
    defaultCapacity: Int = 0,
) {
    var capacityText by remember(entry.tripId, defaultCapacity) {
        mutableStateOf(defaultCapacity.takeIf { it in 1..999 }?.toString().orEmpty())
    }
    var error by remember(entry.tripId) { mutableStateOf<String?>(null) }
    OutlinedTextField(
        value = capacityText,
        onValueChange = { capacityText = it.filter(Char::isDigit).take(3) },
        label = { Text("Capacidade física") },
        modifier = Modifier.fillMaxWidth(),
    )
    error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    OutlinedButton(
        onClick = {
            runCatching {
                val capacity = capacityText.toIntOrNull()
                    ?: throw IllegalArgumentException("Informe a capacidade física.")
                require(capacity in 1..999) { "Informe uma capacidade entre 1 e 999 lugares." }
                val alreadyExists = findExistingTimelineBackingTrip(entry, store.trips())
                require(alreadyExists == null) { "Esta viagem já possui capacidade interna. Atualize a Timeline antes de tentar novamente." }
                val backingTrip = buildTimelineBackingTrip(entry, capacity)
                store.saveTrip(backingTrip)
                UnifiedDebugEventStore.record(
                    "TIMELINE_EXTERNAL_CAPACITY_CONFIGURED",
                    "br.com.mapeiaia.rotacerta",
                    "timelineTripId=${entry.tripId} capacity=$capacity external_seat_write_claimed=false",
                )
                backingTrip
            }.onSuccess {
                error = null
                onChanged("Capacidade interna configurada. A publicação externa não foi alterada.")
            }.onFailure {
                error = it.message ?: "Não foi possível configurar a capacidade."
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Vincular capacidade à viagem") }
}

@Composable
private fun GlobalQuickPassengerPanel(
    entries: List<TripTimelineEntry>,
    trips: List<Trip>,
    store: TripStore,
    formatter: DateTimeFormatter,
    onChanged: (String) -> Unit,
    onTargetSync: (String?) -> Unit,
) {
    val options = timelineQuickPassengerOptions(entries, trips)
    if (options.isEmpty()) return

    var open by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var selectedEntryId by remember(options.map { it.entry.tripId }) { mutableStateOf(options.first().entry.tripId) }
    val selected = options.firstOrNull { it.entry.tripId == selectedEntryId } ?: options.first()
    val entry = selected.entry
    val trip = selected.localTrip
    val date = formatter.format(Instant.ofEpochMilli(entry.departureAtMillis).atZone(ZoneId.systemDefault()))

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { open = !open }, modifier = Modifier.fillMaxWidth()) {
                Text(if (open) "Fechar + Passageiro rápido" else "+ Passageiro rápido")
            }
            if (open) {
                TextButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Perfil/viagem: ${entry.profileLabel} • $date")
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    options.forEach { option ->
                        val optionEntry = option.entry
                        val optionDate = formatter.format(
                            Instant.ofEpochMilli(optionEntry.departureAtMillis).atZone(ZoneId.systemDefault()),
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("${optionEntry.profileLabel} • $optionDate • ${optionEntry.origin} → ${optionEntry.destination}") },
                            onClick = {
                                selectedEntryId = optionEntry.tripId
                                menuOpen = false
                            },
                        )
                    }
                }
                Text("${entry.origin} → ${entry.destination}")
                if (trip != null && trip.capacity > 0) {
                    val available = SeatAvailabilityEngine.remainingSeatsForWholeTrip(trip, store.bookingsFor(trip.id))
                    Text("Data/hora: $date • vagas Rota Certa: $available/${trip.capacity}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Ao salvar, a capacidade interna muda imediatamente e a conta selecionada é conferida em seguida.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    QuickPassengerPanel(trip, store, onChanged) {
                        onTargetSync(canonicalTimelineProfileUuid(entry))
                    }
                } else {
                    Text("Data/hora: $date", style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (entry.capacity > 0) {
                            "Capacidade padrão do veículo: ${entry.capacity}. Vincule a publicação à agenda interna antes de incluir passageiros por trecho."
                        } else {
                            "Capacidade física ainda não configurada no Rota Certa. Nenhum passageiro será incluído até essa capacidade ser informada."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ExternalTripCapacitySetup(entry, store, onChanged, entry.capacity)
                }
            }
        }
    }
}

private fun sameTimelinePlace(left: String, right: String): Boolean {
    val a = placeIdentityKey(left)
    val b = placeIdentityKey(right)
    if (a.isBlank() || b.isBlank()) return false
    if (a == b) return true
    val shorter = if (a.length <= b.length) a else b
    val longer = if (a.length <= b.length) b else a
    return shorter.length >= 5 && longer.contains(shorter)
}

private fun looksCanonicalProfileUuid(value: String): Boolean = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
).matches(value.trim())

internal fun timelineBaseDirection(trip: Trip?, home: Coordinate?, radiusKm: Double): String? {
    if (trip == null || home == null || radiusKm < 0.0) return null
    val stops = trip.stops.sortedBy(TripStop::order)
    val origin = stops.firstOrNull()?.asCoordinate() ?: return null
    val destination = stops.lastOrNull()?.asCoordinate() ?: return null
    val radiusMeters = radiusKm * 1_000.0
    val originInside = GeoDistance.meters(home, origin) <= radiusMeters
    val destinationInside = GeoDistance.meters(home, destination) <= radiusMeters
    return when {
        originInside && !destinationInside -> "↑"
        !originInside && destinationInside -> "↓"
        else -> "↔"
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
