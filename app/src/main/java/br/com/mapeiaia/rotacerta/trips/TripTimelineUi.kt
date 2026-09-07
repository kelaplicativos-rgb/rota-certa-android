package br.com.mapeiaia.rotacerta.trips

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import br.com.mapeiaia.rotacerta.AppSettings
import br.com.mapeiaia.rotacerta.BuildConfig
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripTimelineScreen(
    trips: List<Trip>,
    bookings: List<Booking>,
    store: TripStore,
    onChanged: (String) -> Unit,
    onCreateTripForPassenger: (String) -> Unit,
    addPassengerResumeToken: Int,
    addPassengerResumePassengerId: String?,
    addPassengerResumeTripId: String?,
    onManageLocal: (String) -> Unit,
    uiCommand0396: AgendaTimelineCommand0396? = null,
    uiCommandToken0396: Int = 0,
    focusedTripId: String? = null,
    focusedBookingId: String? = null,
    reservationPendingOnly: Boolean = false,
    listState: LazyListState,
    listModifier: Modifier = Modifier,
    onFirstUsableFrame: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val incrementalPublishScope = rememberCoroutineScope()
    val incrementalPublishMutex = remember { Mutex() }
    val tripMutationCoordinator = remember(context, store) { TripMutationCoordinator0387(context, store) }
    val passengerIdentityStore = remember(context) { PassengerIdentityStore(context) }
    val seatSyncStateStore = remember(context) { BlaBlaPublicationSeatSyncStateStore(context) }
    val archiveStore = remember(context) {
        TripTimelineArchiveStore(context).also { it.clearLegacyBulkHiddenOnce0398() }
    }
    val referenceStore = remember(context) { TripReferenceOriginStore(context) }
    val locationService = remember(context) { DeviceLocationService(context) }
    var archiveRevision by remember { mutableIntStateOf(0) }
    var showArchived by remember { mutableStateOf(false) }
    var passengerAddRequestToken by remember { mutableIntStateOf(0) }
    var downloadRequestToken0399 by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var syncPendingOnly by remember { mutableStateOf(false) }
    var referenceOrigin by remember { mutableStateOf<TripReferenceOrigin?>(null) }
    var currentCoordinate by remember { mutableStateOf<Coordinate?>(null) }
    val settingsRepository = remember(context) { SettingsRepository(context) }
    val appSettingsState by settingsRepository.settings.collectAsState(initial = null)
    val settingsLoaded = appSettingsState != null
    val appSettings = appSettingsState ?: AppSettings()

    LaunchedEffect(uiCommandToken0396, uiCommand0396) {
        if (uiCommandToken0396 <= 0) return@LaunchedEffect
        when (uiCommand0396) {
            AgendaTimelineCommand0396.ADD_PASSENGER -> passengerAddRequestToken++
            AgendaTimelineCommand0396.TOGGLE_ARCHIVED -> showArchived = !showArchived
            AgendaTimelineCommand0396.TOGGLE_SYNC_PENDING -> {
                syncPendingOnly = !syncPendingOnly
                if (syncPendingOnly) searchQuery = ""
            }
            AgendaTimelineCommand0396.DOWNLOAD_TIMELINE -> downloadRequestToken0399++
            null -> Unit
        }
    }

    LaunchedEffect(Unit) {
        referenceOrigin = withContext(Dispatchers.IO) { referenceStore.read() }
        while (true) {
            currentCoordinate = runCatching { locationService.currentCoordinate() }.getOrNull()
            delay(30_000L)
        }
    }

    val onlineSettings0494 = remember(store) { store.onlineSettings() }
    var canonicalResponse0494 by remember(store) {
        mutableStateOf(store.timelineCanonicalCache0494())
    }
    var canonicalBackendStale0494 by remember(store) {
        mutableStateOf(canonicalResponse0494 != null)
    }
    var canonicalBackendFailure0494 by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(onlineSettings0494.apiBaseUrl, onlineSettings0494.driverUsername) {
        while (true) {
            if (!onlineSettings0494.configured) {
                canonicalBackendStale0494 = canonicalResponse0494 != null
                canonicalBackendFailure0494 = "Integração online não configurada."
            } else {
                runCatching {
                    TripRemoteApi(onlineSettings0494).loadCanonicalTimelineState0494(
                        includePastForVerification0429 = true,
                    )
                }.onSuccess { response ->
                    val cached = withContext(Dispatchers.IO) {
                        store.saveTimelineCanonicalCache0494(response)
                    }
                    canonicalResponse0494 = cached
                    canonicalBackendStale0494 = false
                    canonicalBackendFailure0494 = null
                    val revisions = cached.trips.map(DriverTripSyncState0402::canonicalRevision)
                    UnifiedDebugEventStore.record(
                        "TIMELINE_CANONICAL_BACKEND_READ_0494",
                        context.packageName,
                        "source=CANONICAL_BACKEND trips=${cached.trips.size} minRevision=${revisions.minOrNull() ?: 0L} maxRevision=${revisions.maxOrNull() ?: 0L} collectorRead=false",
                    )
                }.onFailure { error ->
                    canonicalBackendStale0494 = true
                    canonicalBackendFailure0494 = error.message ?: error.javaClass.simpleName
                    UnifiedDebugEventStore.record(
                        "TIMELINE_CANONICAL_BACKEND_OFFLINE_0494",
                        context.packageName,
                        "cachePresent=${canonicalResponse0494 != null} collectorFallback=false reason=" +
                            UnifiedDebugEventStore.sanitizeForExport(canonicalBackendFailure0494.orEmpty()).take(160),
                    )
                }
            }
            delay(10_000L)
        }
    }

    val traceId = AgendaTrace.currentTraceId()
    val canonicalProjection0494 = remember(canonicalResponse0494, onlineSettings0494.driverDisplayName) {
        canonicalTimelineProjection0494(
            response = canonicalResponse0494,
            fallbackProfileLabel = onlineSettings0494.driverDisplayName.ifBlank { "Rota Certa" },
        )
    }
    val canonicalTrips0494 = canonicalProjection0494.trips
    val canonicalBookings0494 = canonicalProjection0494.bookings
    val directionGeo = remember(canonicalTrips0494, appSettings) {
        TripTimelineGeoResolver.resolveTrustedStops(
            places = canonicalProjection0494.entries.flatMap { listOf(it.origin, it.destination) },
            trustedStops = timelineTrustedDirectionStops(canonicalTrips0494, appSettings),
        )
    }
    val directionReference = remember(referenceOrigin, appSettings.homeCoordinate, appSettings.homeRadiusKm) {
        timelineDirectionReference(referenceOrigin, appSettings)
    }
    val entries = remember(canonicalProjection0494.entries, archiveRevision, showArchived) {
        val operation = AgendaTrace.operationStart(context, "TIMELINE_CANONICAL_SORT_0494", "TripTimelineScreen", traceId)
        try {
            canonicalProjection0494.entries
                .filter { archiveStore.isArchived(it) == showArchived }
                .sortedBy(TripTimelineEntry::departureAtMillis)
                .also { AgendaTrace.operationEnd(context, operation, processedCount = it.size) }
        } catch (error: Throwable) {
            AgendaTrace.operationError(context, operation, error)
            throw error
        }
    }
    val timelineTripByEntryId = remember(entries, canonicalTrips0494) {
        val byId = canonicalTrips0494.associateBy(Trip::id)
        entries.associate { entry -> entry.tripId to byId[entry.tripId] }
    }

    val capacityAuditKey = remember(entries) {
        entries.joinToString("|") { entry ->
            listOf(
                entry.tripId,
                entry.blablaProfileUuid.orEmpty(),
                entry.blablaTripId.orEmpty(),
                entry.capacity.toString(),
                (entry.blablaPublishedSeats ?: -1).toString(),
                entry.maximumOccupiedSeats.toString(),
                entry.sourcePassengerSeats.entries.sortedBy { it.key.name }
                    .joinToString(",") { item -> item.key.name + ":" + item.value },
            ).joinToString("~")
        }
    }
    LaunchedEffect(capacityAuditKey) {
        withContext(Dispatchers.Default) {
            val audited = entries.filter { entry ->
                !entry.blablaTripId.isNullOrBlank() ||
                    !entry.blablaTripHref.isNullOrBlank() ||
                    !entry.blablaProfileUuid.isNullOrBlank()
            }
            var overbookingCount = 0
            var unresolvedCount = 0
            var minimumAvailable: Int? = null
            val sourceCounts = linkedMapOf<String, Int>()
            audited.forEachIndexed { index, entry ->
                val resolution = timelinePublicCapacityResolution(entry)
                val tripKey = seatSyncDiagnosticKey(
                    entry.blablaProfileUuid.orEmpty() + "|" + (entry.blablaTripId ?: entry.tripId),
                )
                val available = resolution.availableSeats
                if (available == null) unresolvedCount++
                else minimumAvailable = minOf(minimumAvailable ?: available, available)
                if (resolution.overbookingSeats > 0) overbookingCount++
                sourceCounts[resolution.capacitySource] = (sourceCounts[resolution.capacitySource] ?: 0) + 1

                // Keep one representative healthy resolution for the existing forensic contract.
                // Per-card events are retained only for anomalies; the rest is summarized once.
                if (index == 0) {
                    UnifiedDebugEventStore.record(
                        "TIMELINE_CAPACITY_RESOLVED",
                        context.packageName,
                        "tripKey=$tripKey profileUuidPresent=${!entry.blablaProfileUuid.isNullOrBlank()} blablaTripIdPresent=${!entry.blablaTripId.isNullOrBlank()} publishedSeats=${resolution.blablaQuota ?: -1} passengerSeats=${resolution.passengerSeats} blockedSeats=${resolution.blockedSeats} consumedSeats=${entry.maximumOccupiedSeats} operationalInventory=${resolution.operationalInventory ?: -1} availableSeats=${available ?: -1} overbookingSeats=${resolution.overbookingSeats} capacitySource=${resolution.capacitySource} representative=true",
                    )
                }
                if (resolution.overbookingSeats > 0 || available == null) {
                    UnifiedDebugEventStore.record(
                        "TIMELINE_CAPACITY_ANOMALY_0493",
                        context.packageName,
                        "tripKey=$tripKey overbookingSeats=${resolution.overbookingSeats} availableSeats=${available ?: -1} operationalInventory=${resolution.operationalInventory ?: -1} consumedSeats=${entry.maximumOccupiedSeats} capacitySource=${resolution.capacitySource}",
                    )
                }
            }
            UnifiedDebugEventStore.record(
                "TIMELINE_CAPACITY_RESOLVED_BATCH_0493",
                context.packageName,
                "items=${audited.size} overbooking=$overbookingCount unresolved=$unresolvedCount minimumAvailable=${minimumAvailable ?: -1} sources=" +
                    sourceCounts.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" },
            )
        }
    }

    val seatSyncStates = remember(entries) {
        seatSyncStateStore.snapshot().associateBy { state ->
            state.profileUuid.trim().lowercase() + "|" + state.tripId
        }
    }
    val pendingSyncEntries = entries.filter { entry ->
        val profileUuid = entry.blablaProfileUuid?.trim().orEmpty()
        val tripId = entry.blablaTripId?.trim().orEmpty()
        if (profileUuid.isBlank() || tripId.isBlank()) false
        else externalSyncStateIsPending(seatSyncStates[profileUuid.lowercase() + "|" + tripId]?.state)
    }
    val searchedEntries = remember(entries, canonicalTrips0494, canonicalBookings0494, searchQuery) {
        filterTimelineEntries(entries, canonicalTrips0494, canonicalBookings0494, searchQuery)
    }
    val focusedBookingTripId = focusedBookingId
        ?.let { targetId -> canonicalBookings0494.firstOrNull { it.id == targetId } }
        ?.tripId
    val reservationTripIds = canonicalBookings0494
        .filter { it.status == BookingStatus.REQUESTED }
        .map(Booking::tripId)
        .toSet()
    val reservationFilteredEntries = when {
        focusedTripId != null || focusedBookingTripId != null -> {
            val targets = setOfNotNull(focusedTripId, focusedBookingTripId)
            searchedEntries.filter { candidate ->
                candidate.tripId in targets || candidate.localTripId in targets
            }
        }
        reservationPendingOnly -> searchedEntries.filter { candidate ->
            candidate.tripId in reservationTripIds || candidate.localTripId in reservationTripIds
        }
        else -> searchedEntries
    }
    val visibleEntries = if (syncPendingOnly) {
        reservationFilteredEntries.filter { candidate -> pendingSyncEntries.any { it.tripId == candidate.tripId } }
    } else {
        reservationFilteredEntries
    }
    // Consulta pública tem Timeline própria; nunca misturar cards de pesquisa
    // com a Timeline operacional sincronizada.
    val publicResponseForTimeline: BlaBlaPublicSearchResponse? = null
    val publicTimelineCards: List<BlaBlaPublicSearchCard> = emptyList()
    val timelineCalendarDays = remember(visibleEntries, publicResponseForTimeline, publicTimelineCards) {
        combinedTimelineCalendarDays(
            entries = visibleEntries,
            publicResponse = publicResponseForTimeline,
            publicCards = publicTimelineCards,
        )
    }
    val registeredAccounts0432 = remember(entries) {
        BlaBlaDynamicAccountRegistry(context).list()
    }
    val registeredProfileUuids = remember(registeredAccounts0432) {
        registeredAccounts0432.mapNotNull { it.profileUuid }
    }
    val commandRevision0407 by BlaBlaTripControlEvents0407.revision.collectAsState()
    val tripTargetsByCard0432 = remember(entries, registeredAccounts0432) {
        entries.associate { entry ->
            timelineLazyItemKey0380(entry) to resolveBlaBlaTripTarget0407(
                context = context,
                entry = entry,
                accounts = registeredAccounts0432,
            )
        }
    }
    val commandAuditsByCard0432 = remember(tripTargetsByCard0432, commandRevision0407) {
        val statusStore = BlaBlaTripCommandStatusStore0407(context)
        tripTargetsByCard0432.mapValues { (_, target) -> target?.let(statusStore::get) }
    }
    val profileColorSlots = remember(entries, registeredProfileUuids) {
        timelineProfileColorSlots(
            registeredProfileUuids = registeredProfileUuids,
            observedProfileIdentities = entries.map(::timelineProfileIdentity),
        )
    }
    val formatter = remember { DateTimeFormatter.ofPattern("EEE, dd MMM yyyy • HH:mm", Locale.getDefault()) }
    val renderOperation = remember {
        AgendaTrace.operationStart(context, "TIMELINE_RENDER", "TripTimelineScreen", traceId)
    }
    val renderEnded = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    androidx.compose.runtime.SideEffect {
        if (renderEnded.compareAndSet(false, true)) {
            AgendaTrace.operationEnd(context, renderOperation, processedCount = visibleEntries.size)
            onFirstUsableFrame(visibleEntries.size + publicTimelineCards.size)
        }
    }
    LaunchedEffect(visibleEntries.size, publicTimelineCards.size, settingsLoaded, appSettings.rotaCertaSeatAllocation) {
        AgendaTrace.event(
            context,
            "TIMELINE_RENDER_STATE",
            "loading=false empty=${visibleEntries.isEmpty() && publicTimelineCards.isEmpty()} items=${visibleEntries.size + publicTimelineCards.size} inventorySettingsLoaded=$settingsLoaded rotaCertaAllocation=${appSettings.rotaCertaSeatAllocation} syncRunning=false",
            traceId,
        )
        AgendaTrace.event(
            context,
            "INVENTORY_RENDER_STATE",
            "loading=${!settingsLoaded} settingsLoaded=$settingsLoaded rotaCertaAllocation=${appSettings.rotaCertaSeatAllocation} source=${if (settingsLoaded) "rota_certa_allocation" else "awaiting_local_settings"}",
            traceId,
        )
    }

    AgendaTimelineDownloadAction0399(
        entries = visibleEntries,
        triggerToken = downloadRequestToken0399,
        onChanged = onChanged,
    )

    GlobalPassengerFlowPanel(
        entries = entries,
        store = store,
        openRequestToken = passengerAddRequestToken,
        formatter = formatter,
        onChanged = onChanged,
        onNewTrip = onCreateTripForPassenger,
        onTargetSync = { entry, _ ->
            UnifiedDebugEventStore.record(
                "EXTERNAL_SEAT_WRITE_SKIPPED",
                context.packageName,
                "reason=independent_channel_inventory source=automatic_global_passenger_change trip=${entry.tripId}",
            )
            onChanged("Passageiro atualizado. As vagas do Rota Certa foram recalculadas sem alterar a cota BlaBlaCar.")
        },
        resumeRequestToken = addPassengerResumeToken,
        resumePassengerId = addPassengerResumePassengerId,
        resumeTripId = addPassengerResumeTripId,
        canonicalTrips0494 = canonicalTrips0494,
        canonicalBookings0494 = canonicalBookings0494,
    )

    if (canonicalBackendStale0494) {
        Text(
            if (canonicalResponse0494 != null) {
                "⚠️ Backend canônico indisponível: exibindo o último snapshot canônico conhecido. O coletor não será usado como fallback."
            } else {
                "⚠️ Backend canônico indisponível. A Timeline não consultará o coletor como fallback."
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }

    OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        label = { Text("Buscar na Timeline") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    LaunchedEffect(entries.map { it.tripId to it.issues }) {
        entries.firstOrNull { TripTimelineIssue.OVERBOOKING in it.issues }?.let {
            Toast.makeText(
                context,
                "URGENTE: há mais passageiros do que lugares em ${it.origin} → ${it.destination}.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    val timelineEmptyMessage = when {
        entries.isEmpty() && publicResponseForTimeline == null ->
            if (showArchived) "Nenhuma viagem canônica arquivada." else "Nenhuma viagem canônica disponível."
        syncPendingOnly && visibleEntries.isEmpty() ->
            "Nenhum card está com sincronização externa pendente."
        visibleEntries.isEmpty() && publicResponseForTimeline == null ->
            "Nenhuma viagem corresponde à busca."
        visibleEntries.isEmpty() && publicTimelineCards.isEmpty() && searchQuery.isNotBlank() ->
            "Nenhum card corresponde à busca."
        else -> null
    }

    Box(
        modifier = listModifier.fillMaxWidth(),
    ) {
        if (timelineEmptyMessage != null) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(timelineEmptyMessage)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
        timelineCalendarDays.forEach { day ->
            val dayPublicCards = publicTimelineCards.filter { card ->
                runCatching { LocalDate.parse(card.date) }.getOrNull() == day.date
            }
            item(key = "day:${day.date}") {
                AgendaCalendarDayLine(day.date)
            }
            var publicCardIndex = 0
            day.items.forEach { entry ->
                while (
                    publicCardIndex < dayPublicCards.size &&
                    publicSearchCardDepartureSortMillis(dayPublicCards[publicCardIndex]) <= entry.departureAtMillis
                ) {
                    val publicCard = dayPublicCards[publicCardIndex]
                    item {
                        BlaBlaPublicTimelineCard(
                            card = publicCard,
                            response = publicResponseForTimeline,
                        )
                    }
                    publicCardIndex++
                }
                item(key = timelineLazyItemKey0380(entry)) {
                    val trip = timelineTripByEntryId[entry.tripId]
                    val archived = showArchived
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
                        bookingsSnapshot0432 = canonicalBookings0494,
                        passiveTripTarget0407 = tripTargetsByCard0432[timelineLazyItemKey0380(entry)],
                        passiveSeatCapabilityState0407 = seatSyncStates[
                            entry.blablaProfileUuid?.trim()?.lowercase().orEmpty() + "|" + entry.blablaTripId?.trim().orEmpty()
                        ],
                        passiveCommandAudit0407 = commandAuditsByCard0432[timelineLazyItemKey0380(entry)],
                        focusedBookingId = focusedBookingId,
                    ) {
                        archiveStore.setArchived(entry, !archived)
                        archiveRevision++
                        onChanged(if (archived) "Viagem restaurada." else "Viagem arquivada sem cancelar a publicação.")
                    }
                }
            }
            while (publicCardIndex < dayPublicCards.size) {
                val publicCard = dayPublicCards[publicCardIndex]
                item {
                    BlaBlaPublicTimelineCard(
                        card = publicCard,
                        response = publicResponseForTimeline,
                    )
                }
                publicCardIndex++
            }
        }
            }
        }
    }
}

internal fun timelineLazyItemKey0380(entry: TripTimelineEntry): String =
    if (entry.canonicalBackendAuthoritative0494) {
        "timeline:canonical:" + entry.tripId
    } else {
        "timeline:" + (
            canonicalExternalTripIdentityKey(
                entry.blablaProfileUuid,
                entry.blablaTripId,
                entry.blablaTripHref,
            ) ?: entry.localTripId ?: entry.tripId
        )
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
    publicCards: List<BlaBlaPublicSearchCard> = publicResponse?.cards.orEmpty(),
): List<AgendaCalendarDay<TripTimelineEntry>> {
    val entriesByDate = entries.groupBy { entry ->
        Instant.ofEpochMilli(entry.departureAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    }
    val dates = entriesByDate.keys.toCollection(linkedSetOf())
    publicCards.mapNotNullTo(dates) { card ->
        runCatching { LocalDate.parse(card.date) }.getOrNull()
    }
    return dates.sorted().map { date -> AgendaCalendarDay(date, entriesByDate[date].orEmpty()) }
}

@Composable
internal fun BlaBlaPublicTimelineCard(
    card: BlaBlaPublicSearchCard,
    response: BlaBlaPublicSearchResponse?,
) {
    val context = LocalContext.current
    val publicDateLabel = runCatching { LocalDate.parse(card.date) }
        .getOrNull()
        ?.let(::agendaCalendarDayLabel)
        ?: card.date
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
            Text("Consulta pública • $publicDateLabel", style = MaterialTheme.typography.labelLarge, color = Color.Black)
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
internal fun TripReferenceOriginSettingsCard0416(
    referenceOrigin: TripReferenceOrigin?,
    onReferenceChanged: (TripReferenceOrigin) -> Unit,
    onChanged: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val referenceStore = remember(context) { TripReferenceOriginStore(context) }
    val locationService = remember(context) { DeviceLocationService(context) }
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
                onChanged("Origem operacional de referência atualizada.")
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
            Text("Origem operacional de referência", style = MaterialTheme.typography.titleMedium)
            Text(
                if (referenceOrigin == null) "Origem ainda não definida." else "Origem definida por GPS.",
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
                Text(if (locating) "Obtendo GPS…" else if (referenceOrigin == null) "DEFINIR ORIGEM" else "REDEFINIR ORIGEM")
            }
            Text(
                "Esta referência é uma preferência operacional persistente. O GPS atual da rota continua separado e serve ao progresso e aos embarques.",
                style = MaterialTheme.typography.bodySmall,
            )
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** Compatibility entry point for callers compiled against the pre-0.1.416 editor. */
@Composable
internal fun TripDriverDefaultsCard(
    settings: AppSettings,
    repository: SettingsRepository,
    referenceOrigin: TripReferenceOrigin?,
    onReferenceChanged: (TripReferenceOrigin) -> Unit,
    onChanged: (String) -> Unit,
) {
    // settings/repository remain intentionally unused: manual seat allocation is trip-scoped now.
    TripReferenceOriginSettingsCard0416(
        referenceOrigin = referenceOrigin,
        onReferenceChanged = onReferenceChanged,
        onChanged = onChanged,
    )
}

internal fun applyPublicExternalBookingsToTimeline(
    entries: List<TripTimelineEntry>,
    bindings: List<PublicExternalTripBinding>,
    bookings: List<Booking>,
    nowMillis: Long = System.currentTimeMillis(),
): List<TripTimelineEntry> = entries.map { entry ->
    val binding = bindings.firstOrNull { it.matches(entry) } ?: return@map entry
    val enriched = entry.copy(
        localTripId = binding.bookingTripId.takeIf(String::isNotBlank) ?: entry.localTripId,
        blablaTripId = entry.blablaTripId ?: binding.blablaTripId.takeIf(String::isNotBlank),
        blablaTripHref = entry.blablaTripHref ?: binding.blablaTripHref.takeIf(String::isNotBlank),
        blablaPublicHref = entry.blablaPublicHref
            ?: canonicalBoundBlaBlaPublicUrl0423(binding.blablaPublicHref, binding.blablaTripId),
        blablaProfileUuid = entry.blablaProfileUuid ?: binding.profileUuid.takeIf(String::isNotBlank),
    )
    val active = bookings
        .filter { it.tripId == binding.bookingTripId }
        .filter { it.source == BookingSource.ROTA_CERTA || it.sourceReference.startsWith("PUBLIC_LINK:") }
        .filter { booking ->
            booking.seats > 0 && when (booking.status) {
                BookingStatus.REQUESTED,
                BookingStatus.CONFIRMED,
                -> true
                BookingStatus.HELD -> booking.holdExpiresAtMillis == null || booking.holdExpiresAtMillis > nowMillis
                BookingStatus.REJECTED,
                BookingStatus.CANCELLED,
                BookingStatus.EXPIRED,
                -> false
            }
        }

    if (active.isEmpty()) return@map enriched

    val publicTrip = binding.asTrip()
    val publicLoads = SeatAvailabilityEngine.segmentLoads(publicTrip, active, nowMillis)
    val publicConsumed = publicLoads.maxOfOrNull(SegmentLoad::occupiedSeats)
        ?: active.sumOf(Booking::seats)
    val publicOperational = operationalSeatSummary(publicTrip, active, nowMillis)
    val publicPassengers = publicOperational.confirmedPassengerSeats
    val publicBlocked = publicOperational.blockedSeats
    val externalRosterSeats = enriched.blablaPassengers.sumOf { it.seats.coerceAtLeast(1) }
    val combinedPhysical = externalRosterSeats + publicConsumed
    val sources = enriched.sourcePassengerSeats.toMutableMap().apply {
        this[BookingSource.ROTA_CERTA] = publicPassengers
    }

    enriched.copy(
        minimumOccupiedSeats = maxOf(enriched.minimumOccupiedSeats, combinedPhysical),
        maximumOccupiedSeats = maxOf(enriched.maximumOccupiedSeats, combinedPhysical),
        sourcePassengerSeats = sources.filterValues { it > 0 },
        operationalBlockedSeats = maxOf(enriched.operationalBlockedSeats, publicBlocked),
    )
}

/**
 * Legacy function name retained for binary/source compatibility with existing tests and callers.
 * vehicleCapacity is intentionally ignored. The operational inventory is exactly:
 * synchronized BlaBlaCar quota + configured Rota Certa quota. Occupancy is subtracted later.
 */
internal fun applyCanonicalTripCapacity0406(
    entries: List<TripTimelineEntry>,
    canonicalTrips: List<Trip>,
    fallbackRotaCertaSeatAllocation: Int = 0,
): List<TripTimelineEntry> {
    val byStrongIdentity = canonicalTrips.asSequence()
        .filter { resolvedTripRecordOrigin(it) == TripRecordOrigin.EXTERNAL_BACKING && !it.deleted }
        .filter { !it.blablaProfileUuid.isNullOrBlank() && !it.blablaTripId.isNullOrBlank() }
        .associateBy { trip ->
            trip.blablaProfileUuid.orEmpty().trim().lowercase() + "|" + trip.blablaTripId.orEmpty().trim()
        }
    return entries.map { entry ->
        val key = entry.blablaProfileUuid.orEmpty().trim().lowercase() + "|" + entry.blablaTripId.orEmpty().trim()
        val canonical = byStrongIdentity[key]
        if (canonical != null) {
            entry.copy(
                capacity = canonical.capacity.coerceIn(0, 999),
                rotaCertaSeatAllocation = canonical.rotaCertaSeatAllocation?.coerceIn(0, 999) ?: 0,
                status = canonical.status,
                localTripId = canonical.id,
            )
        } else {
            val allocation = fallbackRotaCertaSeatAllocation.takeIf { it in 0..999 } ?: 0
            entry.copy(rotaCertaSeatAllocation = allocation)
        }
    }
}

internal fun applyConfiguredVehicleCapacity(
    entries: List<TripTimelineEntry>,
    @Suppress("UNUSED_PARAMETER") vehicleCapacity: Int,
    rotaCertaSeatAllocation: Int = 0,
): List<TripTimelineEntry> {
    val localAllocation = rotaCertaSeatAllocation.takeIf { it in 0..999 } ?: 0
    return entries.map { entry ->
        val blablaQuota = entry.blablaPublishedSeats?.takeIf { it in 0..999 } ?: 0
        val operationalInventory = (blablaQuota + localAllocation).coerceIn(0, 999)
        entry.copy(
            capacity = operationalInventory,
            rotaCertaSeatAllocation = localAllocation,
        )
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
        !entry.blablaPublicHref.isNullOrBlank() ||
        !entry.blablaProfileUuid.isNullOrBlank()

internal fun timelineOccupancyReadState(entry: TripTimelineEntry): TimelineOccupancyReadState {
    val blablaQuotaKnown = entry.blablaPublishedSeats?.let { it in 0..999 } == true
    val rotaCertaQuotaKnown = entry.rotaCertaSeatAllocation?.let { it in 0..999 } == true
    val inventoryKnown = if (hasExternalPublication(entry)) {
        // For an external trip the synchronized BlaBlaCar quota is essential.
        // Explicit zero is known; absence is still pending even if Rota Certa is zero.
        blablaQuotaKnown && rotaCertaQuotaKnown
    } else {
        blablaQuotaKnown || rotaCertaQuotaKnown
    }
    return when {
        hasExternalPublication(entry) && !inventoryKnown -> TimelineOccupancyReadState.PENDING
        inventoryKnown && hasExternalPublication(entry) && entry.blablaPassengerRosterComplete != true && entry.maximumOccupiedSeats <= 0 ->
            TimelineOccupancyReadState.CAPACITY_CONFIGURED_ROSTER_PENDING
        inventoryKnown -> TimelineOccupancyReadState.CAPACITY_CONFIGURED
        entry.maximumOccupiedSeats > 0 -> TimelineOccupancyReadState.RESERVED
        entry.blablaPassengerRosterComplete == true -> TimelineOccupancyReadState.COMPLETE_EMPTY
        else -> TimelineOccupancyReadState.PENDING
    }
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

private val PublicAgendaMirrorBlue0417 = Color(0xFF1769D2)
private val PublicAgendaMirrorGreen0465 = Color(0xFF15803D)
private val PublicAgendaMirrorOrange0417 = Color(0xFFF59E0B)
private val PublicAgendaMirrorRed0417 = Color(0xFFD32F2F)
private val PublicAgendaMirrorGray0417 = Color(0xFF9E9E9E)

private fun publicMirrorDotColor0417(trip: Trip?): Color = when {
    trip == null -> PublicAgendaMirrorGray0417
    trip.publicMirrorAttestationCurrent0411() -> PublicAgendaMirrorBlue0417
    trip.publicMirrorPublishedWithoutBlaBlaUrl0465() -> PublicAgendaMirrorGreen0465
    trip.publicMirrorAttestationState0411 == PublicMirrorAttestationState0411.PENDING -> PublicAgendaMirrorOrange0417
    trip.publicMirrorAttestationState0411 == PublicMirrorAttestationState0411.DIVERGENT -> PublicAgendaMirrorRed0417
    else -> PublicAgendaMirrorGray0417
}

private fun publicMirrorDiagnosticTitle0417(trip: Trip?): String = when {
    trip == null -> "Agenda ainda não verificada"
    trip.publicMirrorAttestationCurrent0411() -> "MATCH confirmado"
    trip.publicMirrorPublishedWithoutBlaBlaUrl0465() -> "Publicado na Agenda • URL BlaBlaCar pendente"
    trip.publicMirrorAttestationState0411 == PublicMirrorAttestationState0411.PENDING -> "Sincronizando / pendente"
    trip.publicMirrorAttestationState0411 == PublicMirrorAttestationState0411.DIVERGENT -> "Divergência ou erro"
    else -> "Agenda ainda não verificada"
}

private fun publicMirrorMismatchLabel0417(field: String): String = when (field) {
    "identity" -> "identidade"
    "revision", "canonicalRevision" -> "revisão canônica"
    "canonicalStateHash" -> "estado canônico"
    "title" -> "título"
    "departureAtMillis", "timezoneId" -> "data/hora"
    "status" -> "status"
    "capacity", "availability", "publishedSeats", "rotaCertaSeatAllocation" -> "vagas"
    "stops" -> "paradas/itinerário"
    "segmentLoads", "segmentPassengerLoads", "segmentBlockedLoads" -> "ocupação por trecho"
    "publicBookingEnabled" -> "reserva pública"
    "capacityReliable" -> "confiabilidade da capacidade"
    "itineraryAuthoritative" -> "confiabilidade do itinerário"
    "publicUrl" -> "link público Rota Certa"
    "blablaPublicUrl" -> "link público BlaBlaCar"
    "serverHash", "publicHash" -> "hash público"
    "projectionMissing" -> "card público ausente"
    else -> field
}

private fun publicMirrorDiagnosticBody0417(trip: Trip?): String {
    if (trip == null) return "Este card ainda não possui uma viagem canônica vinculada."
    val mismatches = trip.publicMirrorMismatchFields0411.map(::publicMirrorMismatchLabel0417).distinct()
    val agendaFound = trip.publicMirrorPublicIdentity0421.isNotBlank() && trip.publicMirrorLastReadbackAtMillis0421 > 0L
    val identityMatches = "identity" !in trip.publicMirrorMismatchFields0411 && agendaFound
    val contentMatches = trip.publicMirrorAttestationCurrent0411()
    val specificBlaBla = canonicalBoundBlaBlaPublicUrl0423(trip.blablaPublicUrl, trip.blablaTripId)
    return buildString {
        appendLine("Estado: " + when {
            trip.publicMirrorAttestationCurrent0411() -> "MATCH"
            trip.publicMirrorPublishedWithoutBlaBlaUrl0465() -> "PUBLICADO / URL BLABLACAR PENDENTE"
            trip.publicMirrorAttestationState0411 == PublicMirrorAttestationState0411.PENDING -> "PENDENTE"
            trip.publicMirrorAttestationState0411 == PublicMirrorAttestationState0411.DIVERGENT -> "DIVERGENTE / ERRO"
            else -> "NÃO VERIFICADO"
        })
        appendLine("Identidade canônica: " + trip.tripKey.ifBlank { trip.id })
        appendLine("Identidade pública: " + trip.publicMirrorPublicIdentity0421.ifBlank { "não comprovada" })
        appendLine("Revisão canônica: " + trip.canonicalRevision)
        appendLine("Revisão enviada (transporte): " + (trip.publicMirrorAttemptedPublicationRevision0421.takeIf { it > 0L } ?: trip.publicationRevision))
        appendLine("Revisão relida (canônica): " + trip.publicMirrorReadbackCanonicalRevision0421)
        appendLine("Revisão relida (transporte): " + trip.publicMirrorReadbackPublicationRevision0421)
        appendLine("Hash canônico: " + trip.publicMirrorExpectedHash0411.ifBlank { trip.canonicalStateHash.ifBlank { "indisponível" } })
        appendLine("Hash público: " + trip.publicMirrorReadbackHash0411.ifBlank { "indisponível" })
        appendLine("Agenda encontrada: " + if (agendaFound) "sim" else "não comprovada")
        appendLine("Identidade confere: " + if (identityMatches) "sim" else "não")
        appendLine("Conteúdo confere: " + if (contentMatches || trip.publicMirrorPublishedWithoutBlaBlaUrl0465()) "sim" else "não")
        appendLine("Link BlaBlaCar presente: " + if (trip.blablaPublicUrl.isNullOrBlank()) "não" else "sim")
        appendLine("Link BlaBlaCar validado: " + if (trip.blablaTripId.isNullOrBlank()) "não aplicável" else if (specificBlaBla == null) "não" else "sim")
        appendLine("Último readback: " + (trip.publicMirrorLastReadbackAtMillis0421.takeIf { it > 0L }?.toString() ?: "ainda não realizado"))
        appendLine("Última atestação: " + (trip.publicMirrorAttestedAtMillis0411.takeIf { it > 0L }?.toString() ?: "ainda não atestado"))
        appendLine("Duração do readback: " + trip.publicMirrorReadbackLatencyMillis0411 + " ms")
        if (trip.publicMirrorHttpStatus0421 > 0) appendLine("HTTP status: " + trip.publicMirrorHttpStatus0421)
        if (trip.publicMirrorBackendErrorCode0421.isNotBlank()) appendLine("Error code: " + trip.publicMirrorBackendErrorCode0421)
        appendLine("Estágio exato da falha: " + trip.publicMirrorFailedStage0421.ifBlank { "nenhuma falha na última comparação" })
        if (trip.publicMirrorNetworkCallId0421.isNotBlank()) appendLine("Network call ID: " + trip.publicMirrorNetworkCallId0421)
        if (trip.publicMirrorRequestBytes0421 > 0 || trip.publicMirrorResponseBytes0421 > 0) {
            appendLine("Bytes HTTP request/response: " + trip.publicMirrorRequestBytes0421 + "/" + trip.publicMirrorResponseBytes0421)
        }
        if (trip.publicMirrorRequestHash0421.isNotBlank()) appendLine("Hash request: " + trip.publicMirrorRequestHash0421)
        if (trip.publicMirrorResponseHash0421.isNotBlank()) appendLine("Hash response: " + trip.publicMirrorResponseHash0421)
        appendLine("Evidence ID: " + trip.publicMirrorEvidenceId0421.ifBlank { "não gerado" })
        appendLine("Trace ID: " + trip.publicMirrorTraceId0421.ifBlank { "não gerado" })
        if (trip.publicMirrorExpectedBytes0421 > 0 || trip.publicMirrorActualBytes0421 > 0) {
            appendLine("Bytes canônicos/públicos: " + trip.publicMirrorExpectedBytes0421 + "/" + trip.publicMirrorActualBytes0421)
        }
        if (trip.publicMirrorFirstDifferentByteOffset0421 >= 0) {
            appendLine("Primeiro byte divergente: offset " + trip.publicMirrorFirstDifferentByteOffset0421)
        }
        if (trip.publicMirrorDifferentByteRanges0421.isNotEmpty()) {
            appendLine("Ranges divergentes: " + trip.publicMirrorDifferentByteRanges0421.joinToString(", "))
        }
        if (mismatches.isNotEmpty()) appendLine("Diferenças: " + mismatches.joinToString(", "))
        append("Motivo/estágio final: " + trip.publicMirrorAttestationReason0411.ifBlank { "evidência insuficiente" })
    }
}

private fun publicMirrorEvidenceJson0421(
    trip: Trip?,
    commandAudit0407: BlaBlaCommandAuditSnapshot0407?,
): String {
    if (trip == null) return "{}"
    fun q(value: String): String = JSONObject.quote(
        runCatching { UnifiedDebugEventStore.sanitizeForExport(value) }
            .getOrDefault("[sanitization failed]"),
    )
    fun detail(raw: String, key: String): String {
        val match = Regex("(?:^|\\s)" + Regex.escape(key) + "=(?:\"([^\"]*)\"|([^\\s]*))")
            .find(raw) ?: return ""
        return match.groupValues.getOrNull(1).orEmpty()
            .ifBlank { match.groupValues.getOrNull(2).orEmpty() }
    }
    val evidenceKey = trip.publicMirrorEvidenceId0421
    val correlated = if (evidenceKey.isBlank()) emptyList() else UnifiedDebugEventStore.snapshot().events
        .asSequence()
        .filter { event -> event.stage == "PUBLIC_EVIDENCE_0421" && event.details.contains("evidenceId=$evidenceKey") }
        .sortedBy { it.monotonicNs }
        .toList()
    val maxStages = 64
    val stages = correlated.takeLast(maxStages)
    val truncated = correlated.size > stages.size

    // A retry reuses the same evidenceId/traceId. Scope causal fields to the
    // newest OUTBOX_DEQUEUE so persisted failure metadata from an older attempt
    // cannot masquerade as the result of the attempt currently on screen.
    val latestOutboxDequeueIndex0459 = correlated.indexOfLast { event ->
        detail(event.details, "stage") == "OUTBOX_DEQUEUE"
    }
    val currentAttemptEvents0459 = if (latestOutboxDequeueIndex0459 >= 0) {
        correlated.drop(latestOutboxDequeueIndex0459)
    } else {
        emptyList()
    }
    val currentAttemptStarted0459 = currentAttemptEvents0459.isNotEmpty()
    val currentAttemptNumber0459 = currentAttemptEvents0459.firstOrNull()
        ?.let { detail(it.details, "attempt").toIntOrNull() }
        ?: 0
    val currentAttemptStartMonotonicNs0459 = currentAttemptEvents0459.firstOrNull()?.monotonicNs ?: Long.MAX_VALUE
    val currentAttemptLastEvent0459 = currentAttemptEvents0459.lastOrNull()
    val currentAttemptLastStage0459 = currentAttemptLastEvent0459
        ?.let { detail(it.details, "stage") }
        .orEmpty()
    val currentAttemptNextExpectedStage0459 = currentAttemptLastEvent0459
        ?.let { detail(it.details, "nextStage") }
        .orEmpty()
    val failureScope0459 = currentAttemptEvents0459.ifEmpty { stages }

    fun lastStageDetail(vararg keys: String): String {
        stages.asReversed().forEach { event ->
            keys.forEach { key ->
                val value = detail(event.details, key)
                if (value.isNotBlank()) return value
            }
        }
        return ""
    }
    fun lastAttemptDetail0459(vararg keys: String): String {
        failureScope0459.asReversed().forEach { event ->
            keys.forEach { key ->
                val value = detail(event.details, key)
                if (value.isNotBlank()) return value
            }
        }
        return ""
    }
    fun failedEvidenceEvents0458(): List<UnifiedDebugEventStore.SnapshotEvent> =
        failureScope0459.asReversed().filter { event ->
            detail(event.details, "status").uppercase() in setOf("FAILED", "MISMATCH", "DENIED", "ERROR")
        }

    val failedEvidenceEvents0458 = failedEvidenceEvents0458()
    val causalFailureEvent0458 = failedEvidenceEvents0458
        .firstOrNull { detail(it.details, "stage") != "OUTBOX_FAILURE" }
        ?: failedEvidenceEvents0458.firstOrNull()

    fun derivedFailedStage(): String =
        causalFailureEvent0458?.let { detail(it.details, "stage") }.orEmpty()

    val currentAttemptConfirmed0459 = currentAttemptEvents0459.any { event ->
        detail(event.details, "stage") == "ATTESTATION" &&
            detail(event.details, "status").uppercase() == "CONFIRMED"
    }
    val attemptState0459 = when {
        currentAttemptStarted0459 && causalFailureEvent0458 != null -> "FAILED"
        currentAttemptStarted0459 && currentAttemptConfirmed0459 -> "SUCCEEDED"
        currentAttemptStarted0459 -> "IN_PROGRESS"
        causalFailureEvent0458 != null -> "FAILED"
        else -> "HISTORICAL"
    }

    val httpStatus = if (currentAttemptStarted0459) {
        lastAttemptDetail0459("httpStatus").toIntOrNull() ?: 0
    } else {
        trip.publicMirrorHttpStatus0421.takeIf { it > 0 }
            ?: lastStageDetail("httpStatus").toIntOrNull()
            ?: 0
    }
    val networkCallId = if (currentAttemptStarted0459) {
        lastAttemptDetail0459("networkCallId")
    } else {
        trip.publicMirrorNetworkCallId0421.ifBlank { lastStageDetail("networkCallId") }
    }
    val requestBytes = if (currentAttemptStarted0459) {
        lastAttemptDetail0459("requestBytes").toIntOrNull() ?: 0
    } else {
        trip.publicMirrorRequestBytes0421.takeIf { it > 0 }
            ?: lastStageDetail("requestBytes").toIntOrNull()
            ?: 0
    }
    val responseBytes = if (currentAttemptStarted0459) {
        lastAttemptDetail0459("responseBytes").toIntOrNull() ?: 0
    } else {
        trip.publicMirrorResponseBytes0421.takeIf { it > 0 }
            ?: lastStageDetail("responseBytes").toIntOrNull()
            ?: 0
    }
    val requestHash = if (currentAttemptStarted0459) {
        lastAttemptDetail0459("requestHash", "requestSha256")
    } else {
        trip.publicMirrorRequestHash0421.ifBlank { lastStageDetail("requestHash", "requestSha256") }
    }
    val responseHash = if (currentAttemptStarted0459) {
        lastAttemptDetail0459("responseHash", "responseSha256")
    } else {
        trip.publicMirrorResponseHash0421.ifBlank { lastStageDetail("responseHash", "responseSha256") }
    }
    val errorCode = if (currentAttemptStarted0459) {
        lastAttemptDetail0459("errorCode", "backendErrorCode")
    } else {
        trip.publicMirrorBackendErrorCode0421.ifBlank { lastStageDetail("errorCode", "backendErrorCode") }
    }
    val observedFailedStage0458 = derivedFailedStage()
    val failedStage = if (currentAttemptStarted0459) {
        observedFailedStage0458
    } else {
        observedFailedStage0458.ifBlank { trip.publicMirrorFailedStage0421 }
    }
    val causalFailureReason0458 = causalFailureEvent0458?.let { detail(it.details, "reasonCode") }.orEmpty()
    val reasonCode = if (currentAttemptStarted0459) {
        causalFailureReason0458
    } else {
        causalFailureReason0458.ifBlank {
            trip.publicMirrorAttestationReason0411.ifBlank { lastStageDetail("reasonCode") }
        }
    }
    val stalePersistedFailureIgnored0459 =
        currentAttemptStarted0459 &&
            observedFailedStage0458.isBlank() &&
            trip.publicMirrorFailedStage0421.isNotBlank()
    val failureExceptionClass0458 = causalFailureEvent0458?.let { detail(it.details, "exceptionClass") }.orEmpty()
    val failureExceptionMessage0458 = causalFailureEvent0458?.let { detail(it.details, "exceptionMessage") }.orEmpty()
    val failureRootClass0458 = causalFailureEvent0458?.let { detail(it.details, "rootCauseClass") }.orEmpty()
    val failureRootMessage0458 = causalFailureEvent0458?.let { detail(it.details, "rootCauseMessage") }.orEmpty()
    val failureSource0458 = causalFailureEvent0458?.let { detail(it.details, "exceptionSource") }.orEmpty()

    return buildString {
        append('{')
        append("\"schemaVersion\":\"public-evidence-v2\",")
        append("\"evidenceId\":").append(q(trip.publicMirrorEvidenceId0421)).append(',')
        append("\"traceId\":").append(q(trip.publicMirrorTraceId0421)).append(',')
        append("\"canonicalTripId\":").append(q(trip.tripKey.ifBlank { trip.id })).append(',')
        append("\"publicIdentity\":").append(q(trip.publicMirrorPublicIdentity0421)).append(',')
        append("\"logicalRevisionExpected\":").append(trip.canonicalRevision).append(',')
        append("\"logicalRevisionActual\":").append(trip.publicMirrorReadbackCanonicalRevision0421).append(',')
        append("\"transportRevisionSent\":").append(trip.publicMirrorAttemptedPublicationRevision0421.takeIf { it > 0L } ?: trip.publicationRevision).append(',')
        append("\"transportRevisionReadback\":").append(trip.publicMirrorReadbackPublicationRevision0421).append(',')
        append("\"expectedHash\":").append(q(trip.publicMirrorExpectedHash0411)).append(',')
        append("\"actualHash\":").append(q(trip.publicMirrorReadbackHash0411)).append(',')
        append("\"expectedLength\":").append(trip.publicMirrorExpectedBytes0421).append(',')
        append("\"actualLength\":").append(trip.publicMirrorActualBytes0421).append(',')
        append("\"firstDifferentByteOffset\":").append(trip.publicMirrorFirstDifferentByteOffset0421).append(',')
        append("\"differentByteRanges\":").append(q(trip.publicMirrorDifferentByteRanges0421.joinToString(","))).append(',')
        append("\"fieldDiffs\":[")
        trip.publicMirrorFieldDiffs0422.forEachIndexed { index, raw ->
            if (index > 0) append(',')
            val validJson = runCatching {
                JSONObject(raw)
                raw
            }.getOrNull()
            if (validJson != null) append(validJson) else append(q(raw))
        }
        append("],")
        append("\"httpStatus\":").append(httpStatus).append(',')
        append("\"errorCode\":").append(q(errorCode)).append(',')
        append("\"failedStage\":").append(q(failedStage)).append(',')
        append("\"networkCallId\":").append(q(networkCallId)).append(',')
        append("\"requestBytes\":").append(requestBytes).append(',')
        append("\"responseBytes\":").append(responseBytes).append(',')
        append("\"requestHash\":").append(q(requestHash)).append(',')
        append("\"responseHash\":").append(q(responseHash)).append(',')
        append("\"reasonCode\":").append(q(reasonCode)).append(',')
        append("\"control\":{")
        append("\"status\":").append(q(commandAudit0407?.status?.name.orEmpty())).append(',')
        append("\"pending\":").append(commandAudit0407?.pending == true).append(',')
        append("\"requestedAtMillis\":").append(commandAudit0407?.requestedAtMillis ?: 0L).append(',')
        append("\"finishedAtMillis\":").append(commandAudit0407?.finishedAtMillis ?: 0L).append(',')
        append("\"errorCode\":").append(q(commandAudit0407?.errorCode.orEmpty())).append(',')
        append("\"queuedVersionCode\":").append(commandAudit0407?.queuedVersionCode ?: 0L).append(',')
        append("\"currentVersionCode\":").append(BuildConfig.VERSION_CODE)
        append("},")
        append("\"attempt\":").append(currentAttemptNumber0459).append(',')
        append("\"attemptState\":").append(q(attemptState0459)).append(',')
        append("\"lastObservedStage\":").append(q(currentAttemptLastStage0459)).append(',')
        append("\"nextExpectedStage\":").append(q(currentAttemptNextExpectedStage0459)).append(',')
        append("\"attemptStartedAtMillis\":").append(currentAttemptEvents0459.firstOrNull()?.atMillis ?: 0L).append(',')
        append("\"lastStageAtMillis\":").append(currentAttemptLastEvent0459?.atMillis ?: 0L).append(',')
        append("\"currentAttemptStageCount\":").append(currentAttemptEvents0459.size).append(',')
        append("\"stalePersistedFailureIgnored\":").append(stalePersistedFailureIgnored0459).append(',')
        append("\"failure\":{")
        append("\"stage\":").append(q(failedStage)).append(',')
        append("\"reasonCode\":").append(q(causalFailureReason0458)).append(',')
        append("\"exceptionClass\":").append(q(failureExceptionClass0458)).append(',')
        append("\"exceptionMessage\":").append(q(failureExceptionMessage0458)).append(',')
        append("\"rootCauseClass\":").append(q(failureRootClass0458)).append(',')
        append("\"rootCauseMessage\":").append(q(failureRootMessage0458)).append(',')
        append("\"source\":").append(q(failureSource0458))
        append("},")
        append("\"readbackAtMillis\":").append(trip.publicMirrorLastReadbackAtMillis0421).append(',')
        append("\"attestedAtMillis\":").append(trip.publicMirrorAttestedAtMillis0411).append(',')
        append("\"truncated\":").append(truncated).append(',')
        append("\"totalStageCount\":").append(correlated.size).append(',')
        append("\"stageCount\":").append(stages.size).append(',')
        append("\"stages\":[")
        stages.forEachIndexed { index, event ->
            if (index > 0) append(',')
            append('{')
            append("\"atMillis\":").append(event.atMillis).append(',')
            val stageAttempt0459 = detail(event.details, "attempt").toIntOrNull()
                ?: if (
                    currentAttemptStarted0459 &&
                    event.monotonicNs >= currentAttemptStartMonotonicNs0459
                ) {
                    currentAttemptNumber0459
                } else {
                    0
                }
            append("\"attempt\":").append(stageAttempt0459).append(',')
            append("\"stage\":").append(q(detail(event.details, "stage"))).append(',')
            append("\"status\":").append(q(detail(event.details, "status"))).append(',')
            append("\"reasonCode\":").append(q(detail(event.details, "reasonCode"))).append(',')
            append("\"durationMs\":").append(detail(event.details, "durationMs").toLongOrNull() ?: 0L).append(',')
            append("\"exceptionClass\":").append(q(detail(event.details, "exceptionClass"))).append(',')
            append("\"exceptionMessage\":").append(q(detail(event.details, "exceptionMessage"))).append(',')
            append("\"rootCauseClass\":").append(q(detail(event.details, "rootCauseClass"))).append(',')
            append("\"rootCauseMessage\":").append(q(detail(event.details, "rootCauseMessage"))).append(',')
            append("\"exceptionSource\":").append(q(detail(event.details, "exceptionSource"))).append(',')
            append("\"transportPhase\":").append(q(detail(event.details, "transportPhase"))).append(',')
            append("\"requestKind\":").append(q(detail(event.details, "requestKind"))).append(',')
            append("\"rawBytes\":").append(detail(event.details, "rawBytes").toIntOrNull() ?: 0).append(',')
            append("\"sanitizedBytes\":").append(detail(event.details, "sanitizedBytes").toIntOrNull() ?: 0).append(',')
            append("\"rawSha256\":").append(q(detail(event.details, "rawSha256"))).append(',')
            append("\"sanitizedSha256\":").append(q(detail(event.details, "sanitizedSha256"))).append(',')
            append("\"utf8RoundTrip\":").append(detail(event.details, "utf8RoundTrip").toBooleanStrictOrNull() ?: false).append(',')
            append("\"sanitizerSucceeded\":").append(detail(event.details, "sanitizerSucceeded").toBooleanStrictOrNull() ?: false).append(',')
            append("\"sanitizationChanged\":").append(detail(event.details, "sanitizationChanged").toBooleanStrictOrNull() ?: false).append(',')
            append("\"changedByteCount\":").append(detail(event.details, "changedByteCount").toIntOrNull() ?: 0).append(',')
            append("\"firstSanitizedDiffOffset\":").append(detail(event.details, "firstSanitizedDiffOffset").toIntOrNull() ?: -1).append(',')
            append("\"sanitizedDiffRanges\":").append(q(detail(event.details, "sanitizedDiffRanges"))).append(',')
            append("\"nulByteCount\":").append(detail(event.details, "nulByteCount").toIntOrNull() ?: 0).append(',')
            append("\"controlByteCount\":").append(detail(event.details, "controlByteCount").toIntOrNull() ?: 0).append(',')
            append("\"requestBytes\":").append(detail(event.details, "requestBytes").toIntOrNull() ?: 0).append(',')
            append("\"responseBytes\":").append(detail(event.details, "responseBytes").toIntOrNull() ?: 0).append(',')
            append("\"requestSha256\":").append(q(detail(event.details, "requestSha256"))).append(',')
            append("\"responseSha256\":").append(q(detail(event.details, "responseSha256"))).append(',')
            append("\"details\":").append(q(event.details.take(1200)))
            append('}')
        }
        append(']')
        append('}')
    }
}

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
    bookingsSnapshot0432: List<Booking>,
    passiveTripTarget0407: BlaBlaTripTarget0407?,
    passiveSeatCapabilityState0407: BlaBlaPublicationSeatSyncState?,
    passiveCommandAudit0407: BlaBlaCommandAuditSnapshot0407?,
    focusedBookingId: String? = null,
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
    val mutationCoordinator = remember(context, store) { TripMutationCoordinator0387(context, store) }
    val dark = isSystemInDarkTheme()
    val profileColors = timelineProfileCardColors(profileColorSlot, dark)
    val seatPlan = remember(entry, trip, bookingsSnapshot0432) {
        if (entry.canonicalBackendAuthoritative0494) null
        else timelineDesiredSeatSyncPlan(entry, trip, bookingsSnapshot0432)
    }
    val canonicalSegmentLoads0494 = remember(entry, trip) {
        canonicalTimelineSegmentLoads0494(entry, trip)
    }
    var directPassengerTrip by remember(entry.tripId) { mutableStateOf<Trip?>(null) }
    var showSeatDetails by remember(entry.tripId) { mutableStateOf(false) }
    var showMirrorDiagnostic0417 by remember(
        entry.tripId,
        trip?.canonicalRevision,
        trip?.publicationRevision,
        trip?.publicMirrorAttestationState0411,
    ) { mutableStateOf(false) }
    val tripTarget0407 = passiveTripTarget0407
    val seatCapabilityState0407 = passiveSeatCapabilityState0407
    val capabilitySnapshot0407 = remember(tripTarget0407, seatCapabilityState0407, trip?.lastObservedAtMillis) {
        BlaBlaCapabilityRegistry0407.snapshot(
            target = tripTarget0407,
            seatSyncState = seatCapabilityState0407,
            lastVerifiedAtMillis = trip?.lastObservedAtMillis ?: 0L,
        )
    }
    val canonicalPublicationHref0490 = remember(entry.blablaPublicHref, entry.blablaTripId) {
        canonicalTimelineBlaBlaPublicHref0490(entry)
    }
    val actionPalette0407 = remember(capabilitySnapshot0407, canonicalPublicationHref0490) {
        buildBlaBlaTripActionPalette0407(
            snapshot = capabilitySnapshot0407,
            hasPublicationHref = canonicalPublicationHref0490 != null,
        )
    }
    var actionMenuExpanded0407 by remember(entry.tripId) { mutableStateOf(false) }
    val commandAudit0407 = passiveCommandAudit0407
    val reverifyPending0407 = commandAudit0407?.pending == true
    val lastObservedAt0407 = trip?.lastObservedAtMillis ?: 0L
    val queueReverify0407: () -> Unit = {
        val target = tripTarget0407
        if (target == null) {
            onChanged("Não foi possível resolver conta, perfil e tripId desta viagem com identidade forte.")
        } else if (reverifyPending0407) {
            onChanged("Esta viagem já está sendo verificada em segundo plano.")
        } else {
            val command = BlaBlaCommand0407.forTarget(
                target = target,
                operation = BlaBlaTripCapability0407.REVERIFY_TRIP,
                origin = "CARD",
            )
            if (
                AgendaBackgroundSync0392.enqueueTripReverify0407(
                    context = context,
                    target = target,
                    commandId = command.commandId,
                    requestedAtMillis = command.requestedAtMillis,
                )
            ) {
                onChanged("Verificação desta viagem enfileirada em segundo plano.")
            } else {
                onChanged("Verificação bloqueada: a identidade forte desta viagem não pôde ser confirmada.")
            }
        }
    }

    if (showMirrorDiagnostic0417) {
        AlertDialog(
            onDismissRequest = { showMirrorDiagnostic0417 = false },
            title = { Text(publicMirrorDiagnosticTitle0417(trip)) },
            text = { Text(publicMirrorDiagnosticBody0417(trip)) },
            confirmButton = {
                TextButton(onClick = { showMirrorDiagnostic0417 = false }) {
                    Text("Fechar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText(
                                "Rota Certa Evidence Bundle",
                                publicMirrorEvidenceJson0421(trip, commandAudit0407),
                            ),
                        )
                        Toast.makeText(context, "Evidence Bundle JSON copiado.", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text("Copiar evidência JSON")
                }
            },
        )
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    date.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = "●",
                    color = publicMirrorDotColor0417(trip),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .clickable { showMirrorDiagnostic0417 = true }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
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

            val allocation = tripChannelAllocationBreakdown(
                entry.capacity,
                entry.blablaPublishedSeats,
                entry.rotaCertaSeatAllocation,
            )
            val passengers = entry.sourcePassengerSeats.values.sumOf { it.coerceAtLeast(0) }
            val blocked = entry.operationalBlockedSeats.coerceAtLeast(0)

            if (allocation.blablaQuota != null || allocation.rotaCertaQuota != null) {
                Text(
                    "Cota BlaBlaCar: ${allocation.blablaQuota ?: 0} • Cota Rota Certa: ${allocation.rotaCertaQuota ?: 0} • Inventário operacional: ${allocation.operationalInventory ?: entry.capacity}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            val publicCapacity = timelinePublicCapacityResolution(entry)
            val publicLoads = if (entry.canonicalBackendAuthoritative0494) {
                canonicalSegmentLoads0494
            } else {
                seatPlan?.let { plan -> timelinePublicSegmentLoads(entry, plan.loads) }.orEmpty()
            }
            val segmentFree = publicLoads.minOfOrNull(SegmentLoad::availableSeats)
                ?: publicCapacity.availableSeats
            val free = segmentFree
            val operationalInventory = publicCapacity.operationalInventory
            when (timelineOccupancyReadState(entry)) {
                TimelineOccupancyReadState.CAPACITY_CONFIGURED -> {
                    val availabilityLabel = if (free == 0) "LOTADO" else statusMark(entry)
                    Text("👥 Passageiros confirmados: $passengers • 🪑 Vagas disponíveis: ${free ?: 0} $availabilityLabel")
                    if (blocked > 0) Text("🚫 Vagas bloqueadas: $blocked", style = MaterialTheme.typography.bodySmall)
                }
                TimelineOccupancyReadState.CAPACITY_CONFIGURED_ROSTER_PENDING ->
                    Text("Inventário da viagem: ${operationalInventory ?: entry.capacity} • passageiros aguardando leitura ⏳")
                TimelineOccupancyReadState.RESERVED -> {
                    val availabilityLabel = if (free == 0) "LOTADO" else statusMark(entry)
                    Text("👥 Passageiros confirmados: $passengers • 🪑 Vagas disponíveis: ${free ?: 0} $availabilityLabel")
                }
                TimelineOccupancyReadState.COMPLETE_EMPTY -> {
                    val emptyFree = free ?: operationalInventory ?: 0
                    val availabilityLabel = if (emptyFree == 0) "LOTADO" else statusMark(entry)
                    Text("👥 Passageiros confirmados: 0 • 🪑 Vagas disponíveis: $emptyFree $availabilityLabel")
                }
                TimelineOccupancyReadState.PENDING ->
                    Text("Ocupação aguardando leitura ${statusMark(entry)}")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val verificationLabel0407 = blaBlaVerificationLabel0407(
                    audit = commandAudit0407,
                    lastObservedAtMillis = lastObservedAt0407,
                    strongTargetAvailable = tripTarget0407 != null,
                )
                Text(verificationLabel0407, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (BlaBlaTripAction0407.REVERIFY in actionPalette0407.primary) {
                        TextButton(
                            enabled = !reverifyPending0407,
                            onClick = queueReverify0407,
                        ) { Text("🔄 Verificar") }
                    }
                    Box {
                        TextButton(onClick = { actionMenuExpanded0407 = true }) { Text("⋮") }
                        DropdownMenu(
                            expanded = actionMenuExpanded0407,
                            onDismissRequest = { actionMenuExpanded0407 = false },
                        ) {
                            if (BlaBlaTripAction0407.REVERIFY in actionPalette0407.overflow) {
                                DropdownMenuItem(
                                    text = { Text("🔄 Verificar agora") },
                                    enabled = !reverifyPending0407,
                                    onClick = {
                                        actionMenuExpanded0407 = false
                                        queueReverify0407()
                                    },
                                )
                            }
                            if (BlaBlaTripAction0407.SEAT_DETAILS in actionPalette0407.overflow) {
                                DropdownMenuItem(
                                    text = { Text("🪑 Vagas por trecho") },
                                    onClick = {
                                        actionMenuExpanded0407 = false
                                        showSeatDetails = true
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Agenda pública: online/offline") },
                                enabled = trip?.remoteId?.isNotBlank() == true || trip?.publicToken?.isNotBlank() == true,
                                onClick = {
                                    actionMenuExpanded0407 = false
                                    val targetTrip0491 = trip
                                    val settings0491 = store.onlineSettings()
                                    val remoteId0491 = targetTrip0491?.remoteId?.takeIf(String::isNotBlank)
                                        ?: targetTrip0491?.publicToken?.takeIf(String::isNotBlank)
                                    when {
                                        targetTrip0491 == null || remoteId0491 == null ->
                                            onChanged("Esta viagem ainda não possui identidade remota para alterar a publicação.")
                                        !settings0491.configured ->
                                            onChanged("Integração online necessária para alterar a publicação.")
                                        else -> scope.launch {
                                            runCatching {
                                                val api0491 = TripRemoteApi(settings0491)
                                                val state0491 = api0491
                                                    .listDriverTripSyncStates0402(includePastForVerification0429 = true)
                                                    .trips
                                                    .firstOrNull { it.remoteTripId == remoteId0491 }
                                                    ?: error("Estado remoto da viagem não encontrado.")
                                                api0491.updateDriverTripPublicVisibility0491(
                                                    remoteTripId = remoteId0491,
                                                    online = !state0491.publicAgendaOnline0471,
                                                    expectedVisibilityRevision0471 = state0491.publicAgendaVisibilityRevision0471,
                                                )
                                            }.onSuccess { response0491 ->
                                                onChanged(
                                                    if (response0491.online) {
                                                        "🟢 Viagem online na Agenda Pública."
                                                    } else {
                                                        "⚪ Viagem offline na Agenda Pública; o estado canônico foi preservado."
                                                    },
                                                )
                                            }.onFailure { error0491 ->
                                                onChanged(
                                                    "Não foi possível alterar online/offline: " +
                                                        (error0491.message ?: error0491.javaClass.simpleName),
                                                )
                                            }
                                        }
                                    }
                                },
                            )
                            if (BlaBlaTripAction0407.OPEN_PUBLICATION in actionPalette0407.overflow) {
                                DropdownMenuItem(
                                    text = { Text("Ver publicação BlaBlaCar") },
                                    onClick = {
                                        actionMenuExpanded0407 = false
                                        val href = canonicalPublicationHref0490
                                        if (href == null || !openBlaBlaHref(context, entry, href)) {
                                            onChanged("A URL pública canônica desta viagem ainda não está disponível.")
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (BlaBlaTripAction0407.SEAT_DETAILS in actionPalette0407.primary) {
                TextButton(onClick = { showSeatDetails = true }) {
                    Text(if (free != null) "💺 $free" else "💺 ⏳")
                }
            }

            val sourceLine = entry.sourcePassengerSeats.filterValues { it > 0 }.entries.joinToString(" • ") { (source, seats) ->
                "${sourceShort(source)} $seats"
            }
            if (sourceLine.isNotBlank()) Text(sourceLine, style = MaterialTheme.typography.bodySmall)

            when {
                TripTimelineIssue.OVERBOOKING in entry.issues -> Text("❌ URGENTE: passageiros confirmados + vagas bloqueadas ultrapassam o inventário operacional da viagem.")
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
                focusedBookingId = focusedBookingId,
                canonicalBookings0494 = bookingsSnapshot0432,
                onAddManualPassenger = {
                    when {
                        entry.canonicalBackendAuthoritative0494 && trip != null -> directPassengerTrip = trip
                        else -> runCatching { prepareTimelineTripForPassenger(entry, store) }
                            .onSuccess { preparation -> directPassengerTrip = preparation.trip }
                            .onFailure { error ->
                                onChanged(error.message ?: "Não foi possível preparar este card para adicionar passageiro.")
                            }
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
                                            val remoteId0494 = target.remoteId?.takeIf(String::isNotBlank)
                                                ?: error("Viagem sem identidade remota canônica.")
                                            TripRemoteApi(settings).update(
                                                target.copy(
                                                    remoteId = remoteId0494,
                                                    status = TripStatus.COMPLETED,
                                                ),
                                            )
                                        }.onSuccess { response0494 ->
                                            UnifiedDebugEventStore.record(
                                                "TIMELINE_CANONICAL_TRIP_COMPLETED_0494",
                                                context.packageName,
                                                "canonicalTripId=${seatSyncDiagnosticKey(target.id)} entityRevision=${response0494.entityRevision} authority=CANONICAL_BACKEND localBusinessWrite=false",
                                            )
                                            onChanged("Viagem concluída no backend canônico. Timeline e Agenda receberão a nova revisão.")
                                        }.onFailure { error ->
                                            onChanged("Nada foi alterado: ${error.message ?: "falha no backend canônico"}")
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
                    val publicLoads0494 = if (entry.canonicalBackendAuthoritative0494) {
                        canonicalSegmentLoads0494
                    } else {
                        seatPlan?.let { timelinePublicSegmentLoads(entry, it.loads) }.orEmpty()
                    }
                    if (publicLoads0494.isEmpty()) {
                        Text("Disponibilidade por trecho ainda não está presente no snapshot canônico. A Timeline não fará cálculo independente nem consultará o coletor.")
                    } else {
                        publicLoads0494.forEach { load ->
                            Text("${load.from.name} → ${load.to.name}    👥 ${load.passengerSeats} • 🚫 ${load.blockedSeats} • 🪑 ${load.availableSeats}")
                        }
                        entry.blablaPublishedSeats?.takeIf { it >= 0 }?.let { published ->
                            Text("Cota BlaBlaCar no estado canônico: $published vaga(s)", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("🪑 ${publicLoads0494.minOf(SegmentLoad::availableSeats)} = disponibilidade canônica mínima entre os trechos", style = MaterialTheme.typography.bodySmall)
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
            onTargetSync = {
                UnifiedDebugEventStore.record(
                    "TIMELINE_CANONICAL_EXTERNAL_SYNC_SKIPPED_0494",
                    context.packageName,
                    "canonicalTripId=${seatSyncDiagnosticKey(selectedTrip.id)} reason=canonical_backend_authority",
                )
            },
            onDismiss = { directPassengerTrip = null },
            canonicalBookings0494 = bookingsSnapshot0432,
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

internal fun findExistingTimelineBackingTrip(entry: TripTimelineEntry, trips: List<Trip>): Trip? {
    val strongKey = timelineStrongExternalTripKey(entry) ?: return null
    return trips.filter { trip ->
        canonicalExternalTripIdentityKey(
            trip.blablaProfileUuid,
            trip.blablaTripId,
            trip.blablaManageUrl,
        ) == strongKey
    }.singleOrNull()
}

/** Compatibility helper kept for older source-contract tests. New external flow uses buildTimelineExternalBackingTrip. */
internal fun buildTimelineBackingTrip(entry: TripTimelineEntry, capacity: Int): Trip {
    require(capacity > 0) { "Não há vagas disponíveis nesta viagem." }
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

    fun clearLegacyBulkHiddenOnce0398() {
        if (prefs.getBoolean(KEY_BULK_HIDE_MIGRATED_0398, false)) return
        prefs.edit()
            .clear()
            .putBoolean(KEY_BULK_HIDE_MIGRATED_0398, true)
            .commit()
    }

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

    fun archiveLocalVisualEntries(entries: List<TripTimelineEntry>): Int {
        val edit = prefs.edit()
        var hidden = 0
        entries.forEach { entry ->
            val localId = entry.localTripId?.trim()?.takeIf(String::isNotEmpty)
            when {
                localId != null -> {
                    edit.putBoolean("local:$localId", true)
                    hidden++
                }
                !hasExternalPublication(entry) -> {
                    edit.putBoolean("timeline:${entry.tripId}", true)
                    hidden++
                }
            }
        }
        edit.apply()
        return hidden
    }

    private fun aliases(entry: TripTimelineEntry): Set<String> = setOfNotNull(
        entry.localTripId?.let { "local:$it" },
        entry.blablaTripId?.let { "blabla-id:$it" },
        entry.blablaTripHref?.let { "blabla-href:${it.substringBefore("&search_uuid=")}" },
        "timeline:${entry.tripId}",
    )

    companion object {
        private const val PREFS = "rota_certa_timeline_archive_v1"
        private const val KEY_BULK_HIDE_MIGRATED_0398 = "bulk_hide_removed_0398"
    }
}