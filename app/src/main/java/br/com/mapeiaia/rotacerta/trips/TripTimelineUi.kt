package br.com.mapeiaia.rotacerta.trips

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
    onOpenPassengers: () -> Unit,
    onBack: () -> Unit,
    onManageLocal: (String) -> Unit,
) {
    val context = LocalContext.current
    val collectorStore = remember(context) { BlaBlaCollectorStateStore(context) }
    val passengerIdentityStore = remember(context) { PassengerIdentityStore(context) }
    val publicSearchStore = remember(context) { BlaBlaPublicSearchStore(context) }
    val seatSyncStateStore = remember(context) { BlaBlaPublicationSeatSyncStateStore(context) }
    val archiveStore = remember(context) { TripTimelineArchiveStore(context) }
    val referenceStore = remember(context) { TripReferenceOriginStore(context) }
    val locationService = remember(context) { DeviceLocationService(context) }
    var collectorResponse by remember { mutableStateOf(collectorStore.lastResponseRecoveringDynamicSessions()) }
    var publicSearchResponse by remember { mutableStateOf(publicSearchStore.lastResponse()) }
    var showTimelineClearDialog by remember { mutableStateOf(false) }
    var archiveRevision by remember { mutableIntStateOf(0) }
    var showArchived by remember { mutableStateOf(false) }
    var showSync by remember { mutableStateOf(false) }
    var autoSyncProfileUuid by remember { mutableStateOf<String?>(null) }
    var autoSyncTripId by remember { mutableStateOf<String?>(null) }
    var showPublisher by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var syncPendingOnly by remember { mutableStateOf(false) }
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

    val collectedIdentityKey = collectorResponse?.trips.orEmpty()
        .flatMap { collectedTrip ->
            collectedTrip.passengers.map { passenger ->
                listOf(
                    collectedTrip.profile_uuid.orEmpty(),
                    collectedTrip.trip_id.orEmpty(),
                    passenger.booking_href.orEmpty(),
                    passenger.name,
                    passenger.phone.orEmpty(),
                ).joinToString("~")
            }
        }
        .joinToString("|")
    LaunchedEffect(collectedIdentityKey) {
        collectorResponse?.trips.orEmpty().forEach { collectedTrip ->
            collectedTrip.passengers.forEach { passenger ->
                val externalId = stableExternalPassengerId(BlaBlaCollectorUrlModule.passengerIdentityKey(passenger.booking_href))
                passengerIdentityStore.observeExternalPassenger(
                    displayName = passenger.name,
                    whatsapp = passenger.phone,
                    externalPassengerId = externalId,
                    reservationKey = externalPassengerReservationKey(collectedTrip.profile_uuid, passenger.booking_href),
                    externalTripId = collectedTrip.trip_id,
                    driverProfileUuid = collectedTrip.profile_uuid,
                )
            }
        }
    }

    val localEntries = remember(trips, bookings) { TripTimelineEngine.fromLocalAgenda(trips, bookings) }
    val publicExternalBindings = store.publicExternalBindings()
    val mergedRaw = remember(localEntries, collectorResponse, bookings, publicExternalBindings) {
        applyPublicExternalBookingsToTimeline(
            entries = BlaBlaTimelineAdapter.merge(localEntries, collectorResponse),
            bindings = publicExternalBindings,
            bookings = bookings,
        )
    }
    val merged = remember(mergedRaw, appSettings.vehicleCapacity) {
        applyConfiguredVehicleCapacity(mergedRaw, appSettings.vehicleCapacity)
    }
    val directionGeo = remember(merged, trips, appSettings) {
        TripTimelineGeoResolver.resolveTrustedStops(
            places = merged.flatMap { listOf(it.origin, it.destination) },
            trustedStops = timelineTrustedDirectionStops(trips, appSettings),
        )
    }
    val directionReference = remember(referenceOrigin, appSettings.homeCoordinate, appSettings.homeRadiusKm) {
        timelineDirectionReference(referenceOrigin, appSettings)
    }
    val physical = remember(merged, directionGeo) {
        TripPhysicalRideConsolidator.consolidate(merged, directionGeo)
    }
    val entries = remember(physical, archiveRevision, showArchived) {
        physical.filter { archiveStore.isArchived(it) == showArchived }
            .sortedBy(TripTimelineEntry::departureAtMillis)
    }
    val pendingSyncEntries = entries.filter { entry ->
        val profileUuid = entry.blablaProfileUuid?.trim().orEmpty()
        val tripId = entry.blablaTripId?.trim().orEmpty()
        if (profileUuid.isBlank() || tripId.isBlank()) false
        else externalSyncStateIsPending(seatSyncStateStore.get(profileUuid, tripId)?.state)
    }
    val searchedEntries = remember(entries, trips, bookings, searchQuery) {
        filterTimelineEntries(entries, trips, bookings, searchQuery)
    }
    val visibleEntries = if (syncPendingOnly) {
        searchedEntries.filter { candidate -> pendingSyncEntries.any { it.tripId == candidate.tripId } }
    } else {
        searchedEntries
    }
    val publicResponseForTimeline = publicSearchResponse.takeIf { !showArchived && !syncPendingOnly }
    val publicTimelineCards = remember(publicResponseForTimeline, searchQuery) {
        publicResponseForTimeline?.cards.orEmpty()
            .filter { card -> publicSearchCardMatchesTimelineSearch(card, searchQuery) }
            .sortedBy(::publicSearchCardDepartureSortMillis)
    }
    val timelineCalendarDays = remember(visibleEntries, publicResponseForTimeline) {
        combinedTimelineCalendarDays(visibleEntries, publicResponseForTimeline)
    }
    val registeredProfileUuids = BlaBlaDynamicAccountRegistry(context).list().mapNotNull { it.profileUuid }
    val profileColorSlots = remember(entries, registeredProfileUuids) {
        timelineProfileColorSlots(
            registeredProfileUuids = registeredProfileUuids,
            observedProfileIdentities = entries.map(::timelineProfileIdentity),
        )
    }
    val formatter = remember { DateTimeFormatter.ofPattern("EEE, dd MMM yyyy • HH:mm", Locale.getDefault()) }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(if (showArchived) "Arquivadas" else "Todas as viagens", style = MaterialTheme.typography.titleLarge)
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

    GlobalPassengerFlowPanel(
        entries = entries,
        store = store,
        formatter = formatter,
        onChanged = onChanged,
        onNewTrip = onCreateTrip,
        onTargetSync = { entry, selectedTrip ->
            val result = BlaBlaReliableSeatSyncBridge.enqueueDesiredStateForTimeline(
                context = context,
                entry = entry,
                trip = selectedTrip,
                store = store,
                reason = "automatic_global_passenger_change",
            )
            onChanged(result.message)
            if (result.shouldSync) {
                autoSyncProfileUuid = canonicalTimelineProfileUuid(entry)
                autoSyncTripId = null
                onRequestBlaBlaSync()
            }
        },
    )

    val clearTimeline: (Boolean) -> Unit = { includeManualCards ->
        archiveStore.clearExternal(physical)
        if (includeManualCards) {
            physical.filterNot(::hasExternalPublication).forEach { archiveStore.setArchived(it, true) }
            archiveRevision++
        }
        val externalClear = collectorStore.clearSynchronizedTimelineData()
        collectorResponse = externalClear.response
        showArchived = false
        searchQuery = ""
        showTimelineClearDialog = false
        UnifiedDebugEventStore.record(
            "TIMELINE_VISUAL_CLEARED_BY_USER",
            context.packageName,
            "externalTripsRemoved=${externalClear.externalTripsRemoved} includeManualCards=$includeManualCards passengerHistoryPreserved=true localTripsPreserved=true localBookingsPreserved=true sessionAccountsTouched=${externalClear.sessionAccountsTouched}",
        )
        onChanged(
            if (includeManualCards) {
                "Visualização da Timeline limpa. Passageiros, histórico, UUIDs, viagens e reservas locais foram preservados."
            } else {
                "Cards sincronizados foram removidos da visualização. Passageiros e histórico permanente foram preservados."
            },
        )
    }

    ResponsiveTripActions(
        actions = listOf(
            ResponsiveTripAction("Nova viagem", onClick = onCreateTrip),
            ResponsiveTripAction(if (showPublisher) "Fechar publicação" else "Publicar agenda") { showPublisher = !showPublisher },
            ResponsiveTripAction("Fixar atalho", onClick = onPinShortcut),
            ResponsiveTripAction("👥 Passageiros", onClick = onOpenPassengers),
            ResponsiveTripAction("Integração online", onClick = onOpenOnlineSettings),
            ResponsiveTripAction(if (showSync) "Fechar sincronização" else "Sincronizar BlaBlaCar") { showSync = !showSync },
            ResponsiveTripAction("Limpar Timeline") { showTimelineClearDialog = true },
            ResponsiveTripAction(if (showArchived) "Ver próximas" else "Ver arquivadas") { showArchived = !showArchived },
        ),
        onPublicSearchResponse = { publicSearchResponse = it },
    )

    if (showTimelineClearDialog) {
        AlertDialog(
            onDismissRequest = { showTimelineClearDialog = false },
            title = { Text("Limpar Timeline") },
            text = {
                Text("Limpar aqui afeta somente a visualização. Nenhum passageiro, UUID, histórico ou reserva particular será apagado. Deseja ocultar também os cards manuais/por fora?")
            },
            confirmButton = {
                Button(onClick = { clearTimeline(false) }) { Text("Só sincronizadas") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showTimelineClearDialog = false }) { Text("Cancelar") }
                    TextButton(onClick = { clearTimeline(true) }) { Text("Tudo da tela") }
                }
            },
        )
    }

    if (showPublisher) {
        AgendaBatchPublisherPanel(onChanged = onChanged)
    }

    if (showSync) {
        BlaBlaCollectorPanel(
            trips = trips,
            stateStore = collectorStore,
            currentResponse = collectorResponse,
            onResult = { collectorResponse = it },
            onChanged = onChanged,
            autoSyncToken = autoSyncToken,
            autoSyncProfileUuid = autoSyncProfileUuid,
            autoSyncTripId = autoSyncTripId,
        )
    }

    OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        label = { Text("Buscar na Timeline") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    if (pendingSyncEntries.isNotEmpty() || syncPendingOnly) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = if (syncPendingOnly) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = {
                    syncPendingOnly = true
                    searchQuery = ""
                }) {
                    Text("Sincronização externa pendente ⚠️ • ${pendingSyncEntries.size}")
                }
                if (syncPendingOnly) {
                    TextButton(onClick = { syncPendingOnly = false }) { Text("✕ Limpar filtro") }
                }
            }
            if (syncPendingOnly) {
                Text(
                    "Filtro ativo • exibindo somente cards pendentes em todas as datas.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }

    if (entries.isEmpty() && publicResponseForTimeline == null) {
        Text(if (showArchived) "Nenhuma viagem arquivada." else "Nenhuma viagem sincronizada.")
        return
    }

    LaunchedEffect(entries.map { it.tripId to it.issues }) {
        entries.firstOrNull { TripTimelineIssue.OVERBOOKING in it.issues }?.let {
            Toast.makeText(
                context,
                "URGENTE: há mais passageiros do que lugares em ${it.origin} → ${it.destination}.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    if (syncPendingOnly && visibleEntries.isEmpty()) {
        Text("Nenhum card está com sincronização externa pendente.")
        return
    }
    if (visibleEntries.isEmpty() && publicResponseForTimeline == null) {
        Text("Nenhuma viagem corresponde à busca.")
        return
    }
    if (visibleEntries.isEmpty() && publicTimelineCards.isEmpty() && searchQuery.isNotBlank()) {
        Text("Nenhum card corresponde à busca; os dias continuam visíveis abaixo.")
    }

    timelineCalendarDays.forEach { day ->
        AgendaCalendarDayLine(day.date)
        val dayPublicCards = publicTimelineCards.filter { card ->
            runCatching { LocalDate.parse(card.date) }.getOrNull() == day.date
        }
        var publicCardIndex = 0
        day.items.forEach { entry ->
            while (
                publicCardIndex < dayPublicCards.size &&
                publicSearchCardDepartureSortMillis(dayPublicCards[publicCardIndex]) <= entry.departureAtMillis
            ) {
                BlaBlaPublicTimelineCard(
                    card = dayPublicCards[publicCardIndex],
                    response = publicResponseForTimeline,
                )
                publicCardIndex++
            }
        val trip = entry.localTripId?.let(store::getTrip)
            ?: store.getTrip(entry.tripId)
            ?: store.publicExternalBindingFor(entry)?.asTrip()
            ?: findExistingTimelineBackingTrip(entry, store.trips())
        val archived = archiveStore.isArchived(entry)
        TimelineEntryCard(
            entry = entry,
            trip = trip,
            store = store,
            formatter = formatter,
            profileColorSlot = profileColorSlots[timelineProfileIdentity(entry)] ?: 0,
            archived = archived,
            onManageLocal = onManageLocal,
            onChanged = onChanged,
            referenceCoordinate = directionReference.coordinate,
            referenceRadiusKm = directionReference.radiusKm,
            directionGeo = directionGeo,
            currentCoordinate = currentCoordinate,
            onManualSeatSyncRequested = {
                autoSyncProfileUuid = canonicalTimelineProfileUuid(entry)
                autoSyncTripId = null
                onRequestBlaBlaSync()
            },
            onSyncExactCard = {
                val profileUuid = entry.blablaProfileUuid?.trim().orEmpty()
                val tripId = entry.blablaTripId?.trim().orEmpty()
                if (profileUuid.isNotBlank() && tripId.isNotBlank()) {
                    autoSyncProfileUuid = profileUuid
                    autoSyncTripId = tripId
                    onRequestBlaBlaSync()
                } else {
                    onChanged("Sincronização individual indisponível: identidade forte da publicação ausente.")
                }
            },
        ) {
            archiveStore.setArchived(entry, !archived)
            archiveRevision++
            onChanged(if (archived) "Viagem restaurada." else "Viagem arquivada sem cancelar a publicação.")
        }
        }
        while (publicCardIndex < dayPublicCards.size) {
            BlaBlaPublicTimelineCard(
                card = dayPublicCards[publicCardIndex],
                response = publicResponseForTimeline,
            )
            publicCardIndex++
        }
    }
}

internal fun externalSyncStateIsPending(state: BlaBlaPublicationSeatSyncVisualState?): Boolean = state in setOf(
    BlaBlaPublicationSeatSyncVisualState.PENDING,
    BlaBlaPublicationSeatSyncVisualState.SYNCING,
    BlaBlaPublicationSeatSyncVisualState.ERROR,
)

internal fun publicSearchCardMatchesTimelineSearch(card: BlaBlaPublicSearchCard, query: String): Boolean {
    val needle = query.trim()
    if (needle.isBlank()) return true
    return listOf(
        card.driverName,
        card.date,
        card.searchFrom,
        card.searchTo,
        card.actualDeparture.orEmpty(),
        card.actualArrival.orEmpty(),
        card.price.orEmpty(),
        card.driverRating.orEmpty(),
        card.flags.joinToString(" "),
    ).any { it.contains(needle, ignoreCase = true) }
}

internal fun publicSearchCardDepartureSortMillis(card: BlaBlaPublicSearchCard): Long {
    val date = runCatching { LocalDate.parse(card.date) }.getOrNull() ?: return Long.MAX_VALUE
    val clock = Regex("(\\d{1,2}):(\\d{2})").find(card.departureTime.orEmpty())
    val hour = clock?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 23) ?: 0
    val minute = clock?.groupValues?.getOrNull(2)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    return date.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

internal fun combinedTimelineCalendarDays(
    entries: List<TripTimelineEntry>,
    publicResponse: BlaBlaPublicSearchResponse?,
): List<AgendaCalendarDay<TripTimelineEntry>> {
    val entriesByDate = entries.groupBy { entry ->
        Instant.ofEpochMilli(entry.departureAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    }
    val dates = agendaCalendarDaysForItems(entries) { entry ->
        Instant.ofEpochMilli(entry.departureAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    }.mapTo(linkedSetOf()) { it.date }

    if (publicResponse != null) {
        agendaCalendarDaysForPeriod<TripTimelineEntry>(
            period = publicResponse.request.period,
            items = emptyList(),
            dateOf = { null },
        ).forEach { dates += it.date }
        publicResponse.cards.mapNotNullTo(dates) { card -> runCatching { LocalDate.parse(card.date) }.getOrNull() }
    }
    return dates.sorted().map { date -> AgendaCalendarDay(date, entriesByDate[date].orEmpty()) }
}

@Composable
internal fun BlaBlaPublicTimelineCard(
    card: BlaBlaPublicSearchCard,
    response: BlaBlaPublicSearchResponse?,
) {
    val context = LocalContext.current
    val routeFrom = card.actualDeparture?.takeIf(String::isNotBlank) ?: card.searchFrom
    val routeTo = card.actualArrival?.takeIf(String::isNotBlank) ?: card.searchTo
    val status = when (card.availability) {
        "full" -> "Cheio"
        "scarce" -> "Poucas vagas"
        else -> card.availableSeats?.let { if (it == 1) "1 vaga disponível" else "$it vagas disponíveis" }
            ?: "Disponibilidade sem quantidade confirmada"
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = Color.Black),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("Consulta pública • Rota Certa", style = MaterialTheme.typography.labelLarge, color = Color.Black)
            Text(
                "${card.departureTime ?: "--:--"} • $routeFrom → $routeTo",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Black,
            )
            val timing = listOfNotNull(
                card.arrivalTime?.let { "chegada $it" },
                card.duration?.let { "duração $it" },
            ).joinToString(" • ")
            if (timing.isNotBlank()) Text(timing, style = MaterialTheme.typography.bodySmall, color = Color.Black)
            Text(
                "Motorista: ${card.driverName.ifBlank { "não identificado" }}${card.driverRating?.let { " • avaliação $it/5" }.orEmpty()}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black,
            )
            Text("Valor: ${card.price ?: "não informado"}", style = MaterialTheme.typography.bodyMedium, color = Color.Black)
            Text("Vagas/status: $status", style = MaterialTheme.typography.bodyMedium, color = Color.Black)
            if (card.flags.isNotEmpty()) {
                Text(card.flags.joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = Color.Black)
            }
            val direction = response?.let {
                when (BlaBlaPublicSearchPlanner.direction(card.searchFrom, card.searchTo, it.request)) {
                    BlaBlaPublicSearchDirection.PRIMARY -> "IDA pesquisada"
                    BlaBlaPublicSearchDirection.REVERSE -> "VOLTA pesquisada"
                    BlaBlaPublicSearchDirection.UNKNOWN -> "Sentido pesquisado"
                }
            }
            direction?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.Black) }
            Text(
                "Card público independente • passageiros, vagas e status não são mesclados com o card operacional.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Black,
            )
            card.tripHref?.let { href ->
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(href))) }
                }) { Text("Abrir publicação pública", color = Color.Black) }
            }
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
            ) {
                Text(if (locating) "Obtendo GPS…" else if (referenceOrigin == null) "📍 DEFINIR ORIGEM" else "📍 REDEFINIR ORIGEM")
            }

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

internal fun applyPublicExternalBookingsToTimeline(
    entries: List<TripTimelineEntry>,
    bindings: List<PublicExternalTripBinding>,
    bookings: List<Booking>,
    nowMillis: Long = System.currentTimeMillis(),
): List<TripTimelineEntry> = entries.map { entry ->
    val binding = bindings.firstOrNull { it.matches(entry) } ?: return@map entry
    val active = bookings
        .filter { it.tripId == binding.bookingTripId }
        .filter { it.source == BookingSource.ROTA_CERTA || it.sourceReference.startsWith("PUBLIC_LINK:") }
        .filter { booking ->
            booking.seats > 0 && when (booking.status) {
                BookingStatus.CONFIRMED -> true
                BookingStatus.HELD -> booking.holdExpiresAtMillis == null || booking.holdExpiresAtMillis > nowMillis
                BookingStatus.REQUESTED,
                BookingStatus.CANCELLED,
                BookingStatus.EXPIRED,
                -> false
            }
        }

    if (active.isEmpty()) return@map entry

    val publicTrip = binding.asTrip()
    val publicLoads = SeatAvailabilityEngine.segmentLoads(publicTrip, active, nowMillis)
    val publicOccupied = publicLoads.maxOfOrNull(SegmentLoad::occupiedSeats)
        ?: active.sumOf(Booking::seats)
    val externalRosterSeats = entry.blablaPassengers.sumOf { it.seats.coerceAtLeast(1) }
    val combinedPhysical = externalRosterSeats + publicOccupied
    val sources = entry.sourcePassengerSeats.toMutableMap().apply {
        this[BookingSource.ROTA_CERTA] = publicOccupied
    }

    entry.copy(
        minimumOccupiedSeats = maxOf(entry.minimumOccupiedSeats, combinedPhysical),
        maximumOccupiedSeats = maxOf(entry.maximumOccupiedSeats, combinedPhysical),
        sourcePassengerSeats = sources.filterValues { it > 0 },
    )
}

internal fun applyConfiguredVehicleCapacity(
    entries: List<TripTimelineEntry>,
    vehicleCapacity: Int,
): List<TripTimelineEntry> {
    if (vehicleCapacity !in 1..999) return entries
    return entries.map { entry -> if (entry.capacity == vehicleCapacity) entry else entry.copy(capacity = vehicleCapacity) }
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

internal fun timelineProfileIdentity(entry: TripTimelineEntry): String =
    entry.blablaProfileUuid?.trim()?.lowercase()?.takeIf(String::isNotEmpty)
        ?: entry.profileId.trim().lowercase().takeIf(String::isNotEmpty)
        ?: entry.profileLabel.trim().lowercase()

internal fun timelineProfileColorSlots(
    registeredProfileUuids: List<String>,
    observedProfileIdentities: List<String>,
): Map<String, Int> {
    val ordered = linkedSetOf<String>()
    registeredProfileUuids
        .map { it.trim().lowercase() }
        .filter(String::isNotEmpty)
        .forEach { ordered += it }
    observedProfileIdentities
        .map { it.trim().lowercase() }
        .filter(String::isNotEmpty)
        .forEach { ordered += it }
    return ordered.withIndex().associate { indexed -> indexed.value to indexed.index }
}

private data class TimelineProfileCardColors(
    val background: Color,
    val border: Color,
)

private fun timelineProfileCardColors(slot: Int, dark: Boolean): TimelineProfileCardColors = when (slot % 12) {
    0 -> if (dark) TimelineProfileCardColors(Color(0xFF172A46), Color(0xFF6EA0E8)) else TimelineProfileCardColors(Color(0xFFE7F0FF), Color(0xFF4F7FC7))
    1 -> if (dark) TimelineProfileCardColors(Color(0xFF183221), Color(0xFF6CAE7C)) else TimelineProfileCardColors(Color(0xFFE3F4E8), Color(0xFF4F8A62))
    2 -> if (dark) TimelineProfileCardColors(Color(0xFF2D2140), Color(0xFFA886DD)) else TimelineProfileCardColors(Color(0xFFF0E8FF), Color(0xFF7A5DB4))
    3 -> if (dark) TimelineProfileCardColors(Color(0xFF3B2A14), Color(0xFFD5A052)) else TimelineProfileCardColors(Color(0xFFFFF0D9), Color(0xFFB47728))
    4 -> if (dark) TimelineProfileCardColors(Color(0xFF3A1F2A), Color(0xFFD47E9D)) else TimelineProfileCardColors(Color(0xFFFCE7EE), Color(0xFFAD5C7A))
    5 -> if (dark) TimelineProfileCardColors(Color(0xFF163432), Color(0xFF65AAA4)) else TimelineProfileCardColors(Color(0xFFDFF4F2), Color(0xFF4B8B86))
    6 -> if (dark) TimelineProfileCardColors(Color(0xFF222640), Color(0xFF858ED6)) else TimelineProfileCardColors(Color(0xFFE8E9FF), Color(0xFF626BB5))
    7 -> if (dark) TimelineProfileCardColors(Color(0xFF2B3019), Color(0xFFA3B665)) else TimelineProfileCardColors(Color(0xFFEFF3DA), Color(0xFF7B8A44))
    8 -> if (dark) TimelineProfileCardColors(Color(0xFF15313A), Color(0xFF63A9BE)) else TimelineProfileCardColors(Color(0xFFE0F3F8), Color(0xFF4E8798))
    9 -> if (dark) TimelineProfileCardColors(Color(0xFF39251D), Color(0xFFC88E72)) else TimelineProfileCardColors(Color(0xFFF8EAE3), Color(0xFFA96D50))
    10 -> if (dark) TimelineProfileCardColors(Color(0xFF30223A), Color(0xFFB184C9)) else TimelineProfileCardColors(Color(0xFFF3E8F8), Color(0xFF8D5EA5))
    else -> if (dark) TimelineProfileCardColors(Color(0xFF29302F), Color(0xFF8FA7A3)) else TimelineProfileCardColors(Color(0xFFE9F0EF), Color(0xFF6E8984))
}

@Composable
private fun TimelineEntryCard(
    entry: TripTimelineEntry,
    trip: Trip?,
    store: TripStore,
    formatter: DateTimeFormatter,
    profileColorSlot: Int,
    archived: Boolean,
    onManageLocal: (String) -> Unit,
    onChanged: (String) -> Unit,
    referenceCoordinate: Coordinate?,
    referenceRadiusKm: Double,
    directionGeo: Map<String, TimelineGeoPoint>,
    currentCoordinate: Coordinate?,
    onManualSeatSyncRequested: () -> Unit,
    onSyncExactCard: () -> Unit,
    onArchive: () -> Unit,
) {
    val direction = timelineDirectionState(
        entry = entry,
        trip = trip,
        trustedGeo = directionGeo,
        reference = referenceCoordinate,
        radiusKm = referenceRadiusKm,
    )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dark = isSystemInDarkTheme()
    val profileColors = timelineProfileCardColors(profileColorSlot, dark)
    val seatPlan = timelineDesiredSeatSyncPlan(entry, trip, store)
    var directPassengerTrip by remember(entry.tripId) { mutableStateOf<Trip?>(null) }
    var showSeatDetails by remember(entry.tripId) { mutableStateOf(false) }

    fun requestSeatOnlySync(selectedTrip: Trip?, reason: String) {
        val result = BlaBlaReliableSeatSyncBridge.enqueueDesiredStateForTimeline(
            context = context,
            entry = entry,
            trip = selectedTrip,
            store = store,
            reason = reason,
        )
        onChanged(result.message)
        if (result.shouldSync) onManualSeatSyncRequested()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = profileColors.background),
        border = BorderStroke(1.dp, profileColors.border),
    ) {
        Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val date = formatter.format(Instant.ofEpochMilli(entry.departureAtMillis).atZone(ZoneId.systemDefault()))
            Text(
                date.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                style = MaterialTheme.typography.labelLarge,
            )
            timelineDirectionDisplayLabel(direction)?.let { label ->
                val chipColor = when (direction) {
                    TimelineDirectionState.OUTBOUND -> if (dark) Color(0xFF285A34) else Color(0xFFB8E6C4)
                    TimelineDirectionState.INBOUND -> if (dark) Color(0xFF6A3A23) else Color(0xFFFFD1B8)
                    TimelineDirectionState.NEUTRAL,
                    TimelineDirectionState.UNKNOWN,
                    -> MaterialTheme.colorScheme.surfaceVariant
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .background(chipColor, RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Text("${entry.origin} → ${entry.destination}", style = MaterialTheme.typography.titleMedium)

            val meta = listOfNotNull(entry.profileLabel.takeIf(String::isNotBlank), entry.blablaPrice).joinToString(" • ")
            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall)

            val occupied = seatPlan?.loads?.maxOfOrNull(SegmentLoad::occupiedSeats) ?: entry.maximumOccupiedSeats
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

            TextButton(onClick = { showSeatDetails = true }) {
                Text(if (seatPlan != null) "💺 ${seatPlan.desiredPublishedSeats}" else "💺 ⏳")
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
                onSyncExactCard = onSyncExactCard,
                onSyncSeatsOnly = { requestSeatOnlySync(trip, "manual_card_shortcut") },
                onAddManualPassenger = {
                    runCatching { prepareTimelineTripForPassenger(entry, store) }
                        .onSuccess { preparation -> directPassengerTrip = preparation.trip }
                        .onFailure { error ->
                            onChanged(error.message ?: "Não foi possível preparar este card para adicionar passageiro.")
                        }
                },
            )

            val canCompleteRemoteTrip =
                trip?.remoteId != null &&
                    entry.departureAtMillis <= System.currentTimeMillis() &&
                    trip.status !in setOf(TripStatus.COMPLETED, TripStatus.CANCELLED)
            ResponsiveTripActions(
                buildList {
                    add(ResponsiveTripAction(if (archived) "Restaurar" else "Arquivar") { onArchive() })
                    if (canCompleteRemoteTrip) {
                        add(
                            ResponsiveTripAction("Concluir viagem") {
                                val target = trip
                                val settings = store.onlineSettings()
                                when {
                                    target == null -> onChanged("Viagem não identificada para conclusão.")
                                    !settings.configured -> onChanged("Integração online necessária para concluir a viagem.")
                                    else -> scope.launch {
                                        runCatching {
                                            val completed = target.copy(status = TripStatus.COMPLETED)
                                            TripRemoteApi(settings).update(completed)
                                            if (store.getTrip(target.id) != null) store.saveTrip(completed)
                                        }.onSuccess {
                                            onChanged("Viagem concluída. Créditos de indicações elegíveis foram processados.")
                                        }.onFailure { error ->
                                            onChanged("Não foi possível concluir a viagem: ${error.message ?: "erro de conexão"}")
                                        }
                                    }
                                }
                            },
                        )
                    }
                },
            )
        }
    }

    if (showSeatDetails) {
        AlertDialog(
            onDismissRequest = { showSeatDetails = false },
            title = { Text("VAGAS POR TRECHO") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (seatPlan == null) {
                        Text("Leitura por trecho pendente. O Rota Certa não vai inventar disponibilidade enquanto os passageiros externos não estiverem completos.")
                    } else {
                        seatPlan.loads.forEach { load ->
                            Text("${load.from.name} → ${load.to.name}    ${load.availableSeats} vaga(s)")
                        }
                        Text("💺 ${seatPlan.desiredPublishedSeats} = menor disponibilidade física relevante", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSeatDetails = false }) { Text("Fechar") } },
        )
    }

    directPassengerTrip?.let { selectedTrip ->
        TimelineCardQuickPassengerDialog(
            entry = entry,
            trip = selectedTrip,
            store = store,
            onChanged = onChanged,
            onTargetSync = { requestSeatOnlySync(selectedTrip, "automatic_after_passenger_change") },
            onDismiss = { directPassengerTrip = null },
        )
    }
}

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

internal fun findExistingTimelineBackingTrip(entry: TripTimelineEntry, trips: List<Trip>): Trip? =
    timelinePhysicalTripMatches(entry, trips).singleOrNull()

/** Compatibility helper kept for older source-contract tests. New external flow uses buildTimelineExternalBackingTrip. */
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

internal fun normalizePhone(raw: String?): String {
    val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return ""
    val digits = value.filter(Char::isDigit)
    if (digits.length !in 8..15) return ""
    return if (value.startsWith("+")) "+$digits" else "local:$digits"
}

internal fun whatsappRecipient(raw: String): String? {
    val value = raw.trim().takeIf(String::isNotEmpty) ?: return null
    val digits = value.filter(Char::isDigit)
    if (digits.length !in 8..15) return null
    return digits
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
    val account = BlaBlaDynamicAccountRegistry(context).list()
        .firstOrNull { it.profileUuid?.trim()?.lowercase() == profileUuid }
        ?: return false
    context.startActivity(
        BlaBlaDynamicSessionIntents.manage(context, account, href)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    return true
}

private class TripTimelineArchiveStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isArchived(entry: TripTimelineEntry): Boolean = aliases(entry).any { key -> prefs.getBoolean(key, false) }

    fun setArchived(entry: TripTimelineEntry, archived: Boolean) {
        val edit = prefs.edit()
        aliases(entry).forEach { edit.putBoolean(it, archived) }
        edit.apply()
    }

    fun clearExternal(entries: List<TripTimelineEntry>) {
        val edit = prefs.edit()
        entries
            .filter(::hasExternalPublication)
            .flatMap(::aliases)
            .distinct()
            .forEach(edit::remove)
        edit.apply()
    }

    private fun aliases(entry: TripTimelineEntry): Set<String> = setOfNotNull(
        entry.localTripId?.let { "local:$it" },
        entry.blablaTripId?.let { "blabla-id:$it" },
        entry.blablaTripHref?.let { "blabla-href:${it.substringBefore("&search_uuid=")}" },
        "timeline:${entry.tripId}",
    )

    companion object {
        private const val PREFS = "rota_certa_timeline_archive_v1"
    }
}