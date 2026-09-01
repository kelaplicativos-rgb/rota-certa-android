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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Composable
fun TripTimelineScreen(
    trips: List<Trip>,
    bookings: List<Booking>,
    store: TripStore,
    onChanged: (String) -> Unit,
    autoSyncToken: Int,
    forceAllSyncToken: Int,
    onRequestBlaBlaSync: () -> Unit,
    onCreateTrip: () -> Unit,
    onCreateTripForPassenger: (String) -> Unit,
    addPassengerResumeToken: Int,
    addPassengerResumePassengerId: String?,
    addPassengerResumeTripId: String?,
    onPinShortcut: () -> Unit,
    onOpenOnlineSettings: () -> Unit,
    onOpenPassengers: () -> Unit,
    onBack: () -> Unit,
    onManageLocal: (String) -> Unit,
    focusedTripId: String? = null,
    focusedBookingId: String? = null,
    reservationPendingOnly: Boolean = false,
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    listModifier: Modifier = Modifier,
    onFirstUsableFrame: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val incrementalPublishScope = rememberCoroutineScope()
    val incrementalPublishMutex = remember { Mutex() }
    val collectorStore = remember(context) { BlaBlaCollectorStateStore(context) }
    val passengerIdentityStore = remember(context) { PassengerIdentityStore(context) }
    val publicSearchStore = remember(context) { BlaBlaPublicSearchStore(context) }
    val seatSyncStateStore = remember(context) { BlaBlaPublicationSeatSyncStateStore(context) }
    val archiveStore = remember(context) { TripTimelineArchiveStore(context) }
    val referenceStore = remember(context) { TripReferenceOriginStore(context) }
    val locationService = remember(context) { DeviceLocationService(context) }
    var collectorResponse by remember { mutableStateOf(collectorStore.lastResponseRecoveringDynamicSessions()) }
    var publicSearchResponse by remember { mutableStateOf(publicSearchStore.lastResponse()) }
    var publicSearchClearToken by remember { mutableIntStateOf(0) }
    var showTimelineClearDialog by remember { mutableStateOf(false) }
    var archiveRevision by remember { mutableIntStateOf(0) }
    var showArchived by remember { mutableStateOf(false) }
    var showSync by remember { mutableStateOf(false) }
    var autoSyncProfileUuid by remember { mutableStateOf<String?>(null) }
    var autoSyncTripId by remember { mutableStateOf<String?>(null) }
    var showPublisher by remember { mutableStateOf(false) }
    var passengerAddRequestToken by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var syncPendingOnly by remember { mutableStateOf(false) }
    var referenceOrigin by remember { mutableStateOf(referenceStore.read()) }
    var currentCoordinate by remember { mutableStateOf<Coordinate?>(null) }
    val settingsRepository = remember(context) { SettingsRepository(context) }
    val appSettingsState by settingsRepository.settings.collectAsState(initial = null)
    val settingsLoaded = appSettingsState != null
    val appSettings = appSettingsState ?: AppSettings()
    val forceAllSyncActive = autoSyncToken > 0 && forceAllSyncToken == autoSyncToken

    LaunchedEffect(autoSyncToken, forceAllSyncToken) {
        if (autoSyncToken > 0) {
            showSync = true
            if (forceAllSyncActive) {
                autoSyncProfileUuid = null
                autoSyncTripId = null
                UnifiedDebugEventStore.record(
                    "AGENDA_PULL_REFRESH_BLABLACAR_ALL_ARMED",
                    context.packageName,
                    "scope=all_accounts source=timeline_pull token=$autoSyncToken",
                )
            }
        }
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
        withContext(Dispatchers.IO) {
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
    }

    val traceId = AgendaTrace.currentTraceId()
    val localEntries = remember(trips, bookings) {
        val operation = AgendaTrace.operationStart(context, "TIMELINE_MERGE_LOCAL", "TripTimelineScreen", traceId)
        try {
            TripTimelineEngine.fromLocalAgenda(trips, bookings).also {
                AgendaTrace.operationEnd(context, operation, processedCount = it.size)
            }
        } catch (error: Throwable) {
            AgendaTrace.operationError(context, operation, error)
            throw error
        }
    }
    val publicExternalBindings = store.publicExternalBindings()
    val internallyCancelledExternalReservationKeys = passengerIdentityStore.internallyCancelledExternalReservationKeys()
    val collectorResponseForTimeline = remember(collectorResponse, internallyCancelledExternalReservationKeys) {
        applyInternalCancellationTombstones(collectorResponse, internallyCancelledExternalReservationKeys)
    }
    val mergedRaw = remember(localEntries, collectorResponseForTimeline, bookings, publicExternalBindings) {
        val operation = AgendaTrace.operationStart(context, "TIMELINE_MERGE", "TripTimelineScreen", traceId)
        try {
            applyPublicExternalBookingsToTimeline(
                entries = BlaBlaTimelineAdapter.merge(localEntries, collectorResponseForTimeline),
                bindings = publicExternalBindings,
                bookings = bookings,
            ).also {
                AgendaTrace.operationEnd(context, operation, processedCount = it.size)
            }
        } catch (error: Throwable) {
            AgendaTrace.operationError(context, operation, error)
            throw error
        }
    }
    val merged = remember(mergedRaw, appSettings.rotaCertaSeatAllocation) {
        applyConfiguredVehicleCapacity(
            entries = mergedRaw,
            vehicleCapacity = 0, // legacy argument intentionally ignored
            rotaCertaSeatAllocation = appSettings.rotaCertaSeatAllocation,
        )
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
        val operation = AgendaTrace.operationStart(context, "TIMELINE_PHYSICAL_CONSOLIDATION", "TripTimelineScreen", traceId)
        try {
            TripPhysicalRideConsolidator.consolidate(merged, directionGeo).also {
                AgendaTrace.operationEnd(context, operation, processedCount = it.size)
            }
        } catch (error: Throwable) {
            AgendaTrace.operationError(context, operation, error)
            throw error
        }
    }
    val entries = remember(physical, archiveRevision, showArchived) {
        val operation = AgendaTrace.operationStart(context, "TIMELINE_SORT", "TripTimelineScreen", traceId)
        try {
            physical.filter { archiveStore.isArchived(it) == showArchived }
                .sortedBy(TripTimelineEntry::departureAtMillis)
                .also { AgendaTrace.operationEnd(context, operation, processedCount = it.size) }
        } catch (error: Throwable) {
            AgendaTrace.operationError(context, operation, error)
            throw error
        }
    }
    val tripById = remember(trips) { trips.associateBy(Trip::id) }
    val timelineTripByEntryId = remember(entries, trips, publicExternalBindings) {
        entries.associate { entry ->
            val trip = entry.localTripId?.let(tripById::get)
                ?: tripById[entry.tripId]
                ?: publicExternalBindings.firstOrNull { it.matches(entry) }?.asTrip()
                ?: findExistingTimelineBackingTrip(entry, trips)
            entry.tripId to trip
        }
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
            entries.asSequence()
                .filter { entry ->
                    !entry.blablaTripId.isNullOrBlank() ||
                        !entry.blablaTripHref.isNullOrBlank() ||
                        !entry.blablaProfileUuid.isNullOrBlank()
                }
                .forEach { entry ->
                    val resolution = timelinePublicCapacityResolution(entry)
                    val tripKey = seatSyncDiagnosticKey(
                        entry.blablaProfileUuid.orEmpty() + "|" + (entry.blablaTripId ?: entry.tripId),
                    )
                    UnifiedDebugEventStore.record(
                        "TIMELINE_CAPACITY_RESOLVED",
                        context.packageName,
                        "tripKey=$tripKey profileUuidPresent=${!entry.blablaProfileUuid.isNullOrBlank()} blablaTripIdPresent=${!entry.blablaTripId.isNullOrBlank()} publishedSeats=${resolution.blablaQuota ?: -1} passengerSeats=${resolution.passengerSeats} blockedSeats=${resolution.blockedSeats} consumedSeats=${entry.maximumOccupiedSeats} operationalInventory=${resolution.operationalInventory ?: -1} availableSeats=${resolution.availableSeats ?: -1} overbookingSeats=${resolution.overbookingSeats} capacitySource=${resolution.capacitySource}",
                    )
                }
        }
    }

    val seatSyncStates = seatSyncStateStore.snapshot().associateBy { state ->
        state.profileUuid.trim().lowercase() + "|" + state.tripId
    }
    val pendingSyncEntries = entries.filter { entry ->
        val profileUuid = entry.blablaProfileUuid?.trim().orEmpty()
        val tripId = entry.blablaTripId?.trim().orEmpty()
        if (profileUuid.isBlank() || tripId.isBlank()) false
        else externalSyncStateIsPending(seatSyncStates[profileUuid.lowercase() + "|" + tripId]?.state)
    }
    val searchedEntries = remember(entries, trips, bookings, searchQuery) {
        filterTimelineEntries(entries, trips, bookings, searchQuery)
    }
    val focusedBookingTripId = focusedBookingId
        ?.let { targetId -> bookings.firstOrNull { it.id == targetId } }
        ?.tripId
    val reservationTripIds = bookings
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
    val publicResponseForTimeline = publicSearchResponse.takeIf {
        !showArchived && !syncPendingOnly && !reservationPendingOnly && focusedTripId == null && focusedBookingTripId == null
    }
    val publicTimelineCards = remember(publicResponseForTimeline, searchQuery) {
        publicResponseForTimeline?.cards.orEmpty()
            .filter { card -> publicSearchCardMatchesTimelineSearch(card, searchQuery) }
            .sortedBy(::publicSearchCardDepartureSortMillis)
    }
    val timelineCalendarDays = remember(visibleEntries, publicResponseForTimeline, publicTimelineCards) {
        combinedTimelineCalendarDays(
            entries = visibleEntries,
            publicResponse = publicResponseForTimeline,
            publicCards = publicTimelineCards,
        )
    }
    val registeredProfileUuids = remember(entries, collectorResponse) {
        BlaBlaDynamicAccountRegistry(context).list().mapNotNull { it.profileUuid }
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
    LaunchedEffect(visibleEntries.size, publicTimelineCards.size, showSync, settingsLoaded, appSettings.rotaCertaSeatAllocation) {
        AgendaTrace.event(
            context,
            "TIMELINE_RENDER_STATE",
            "loading=false empty=${visibleEntries.isEmpty() && publicTimelineCards.isEmpty()} items=${visibleEntries.size + publicTimelineCards.size} inventorySettingsLoaded=$settingsLoaded rotaCertaAllocation=${appSettings.rotaCertaSeatAllocation} syncRunning=$showSync",
            traceId,
        )
        AgendaTrace.event(
            context,
            "INVENTORY_RENDER_STATE",
            "loading=${!settingsLoaded} settingsLoaded=$settingsLoaded rotaCertaAllocation=${appSettings.rotaCertaSeatAllocation} source=${if (settingsLoaded) "rota_certa_allocation" else "awaiting_local_settings"}",
            traceId,
        )
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(if (showArchived) "Arquivadas" else "Todas as viagens", style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = {
            AgendaTrace.event(context, "USER_BACK", "source=timeline_header", traceId)
            onBack()
        }) { Text("Voltar") }
    }

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
    )

    val clearTimeline: (Boolean) -> Unit = { includeManualCards ->
        // First forget any old archive aliases tied to synchronized identities. When the
        // user chooses "Tudo da tela", re-archive only the persistent local/manual
        // identity so a hybrid local+BlaBla card cannot reappear after its external half
        // is removed.
        archiveStore.clearExternal(physical)
        val manualVisualHidden = if (includeManualCards) {
            archiveStore.archiveLocalVisualEntries(physical)
        } else {
            0
        }
        val externalBackingHistoryPreserved = store.trips().count {
            resolvedTripRecordOrigin(it) == TripRecordOrigin.EXTERNAL_BACKING
        }
        val externalClear = collectorStore.clearSynchronizedTimelineData()
        collectorResponse = externalClear.response

        if (includeManualCards) {
            publicSearchStore.clearResponse()
            publicSearchResponse = null
            publicSearchClearToken++
            syncPendingOnly = false
            showSync = false
            showPublisher = false
            archiveRevision++
        }

        showArchived = false
        searchQuery = ""
        showTimelineClearDialog = false
        UnifiedDebugEventStore.record(
            "TIMELINE_VISUAL_CLEARED_BY_USER",
            context.packageName,
            "externalTripsRemoved=${externalClear.externalTripsRemoved} includeManualCards=$includeManualCards manualVisualHidden=$manualVisualHidden publicSearchCleared=$includeManualCards passengerHistoryPreserved=true localTripsPreserved=true localBookingsPreserved=true externalBackingHistoryPreserved=$externalBackingHistoryPreserved externalBackingPublishableAsLocal=false sessionAccountsTouched=${externalClear.sessionAccountsTouched}",
        )
        onChanged(
            if (includeManualCards) {
                "Timeline zerada visualmente. Cards sincronizados, manuais, consulta pública e linhas de datas foram removidos da tela; os dados permanentes foram preservados."
            } else {
                "Cards sincronizados foram removidos da visualização. Passageiros e histórico permanente foram preservados."
            },
        )
    }

    ResponsiveTripActions(
        actions = listOf(
            ResponsiveTripAction("👥 Passageiros", onClick = onOpenPassengers),
            ResponsiveTripAction("➕ Adicionar a uma viagem") { passengerAddRequestToken++ },
            ResponsiveTripAction("🛣️ Nova viagem", onClick = onCreateTrip),
            ResponsiveTripAction(if (showPublisher) "Fechar publicação" else "Publicar agenda") { showPublisher = !showPublisher },
            ResponsiveTripAction("Fixar atalho", onClick = onPinShortcut),
            ResponsiveTripAction("Integração online", onClick = onOpenOnlineSettings),
            ResponsiveTripAction(if (showSync) "Fechar sincronização" else "Sincronizar BlaBlaCar") {
                showSync = !showSync
                UnifiedDebugEventStore.record(
                    if (showSync) "AGENDA_SYNC_PANEL_OPENED" else "AGENDA_SYNC_PANEL_CLOSED",
                    context.packageName,
                    "source=timeline_toolbar autoSyncStarted=false",
                )
            },
            ResponsiveTripAction("Limpar Timeline") { showTimelineClearDialog = true },
            ResponsiveTripAction(if (showArchived) "Ver próximas" else "Ver arquivadas") { showArchived = !showArchived },
        ),
        onPublicSearchResponse = { publicSearchResponse = it },
        publicSearchClearToken = publicSearchClearToken,
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
            onResult = { nextResponse ->
                val previousByIdentity = collectorResponse?.trips.orEmpty()
                    .mapNotNull { previous ->
                        canonicalExternalTripIdentityKey(
                            previous.profile_uuid,
                            previous.trip_id,
                            previous.trip_href,
                        )?.let { key ->
                            key to PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(
                                previous,
                                appSettings.rotaCertaSeatAllocation,
                            )
                        }
                    }
                    .toMap()
                collectorResponse = nextResponse
                if (settingsLoaded) {
                    val changed = nextResponse.trips
                        .asSequence()
                        .filterNot(BlaBlaCollectorTrip::identity_conflict)
                        .filter { current ->
                            val key = canonicalExternalTripIdentityKey(
                                current.profile_uuid,
                                current.trip_id,
                                current.trip_href,
                            ) ?: return@filter false
                            val revision = PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(
                                current,
                                appSettings.rotaCertaSeatAllocation,
                            )
                            previousByIdentity[key] != revision
                        }
                        .toList()
                    if (changed.isNotEmpty()) {
                        incrementalPublishScope.launch {
                            incrementalPublishMutex.withLock {
                                changed.forEach { source ->
                                    runCatching {
                                        PublicAgendaAutoSync0300.syncExternalTripIncremental(
                                            context = context,
                                            store = store,
                                            source = source,
                                            configuredRotaCertaSeatAllocation = appSettings.rotaCertaSeatAllocation,
                                        )
                                    }.onFailure { error ->
                                        val sourceFailureContext = AgendaFailureTripContext(
                                            tripKey = seatSyncDiagnosticKey(source.profile_uuid + "|" + source.trip_id.orEmpty()),
                                            canonicalIdentity = canonicalExternalTripIdentityKey(
                                                source.profile_uuid,
                                                source.trip_id,
                                                source.trip_href,
                                            ).orEmpty(),
                                            publicIdentity = "<unresolved>",
                                            origin = TripRecordOrigin.EXTERNAL_BACKING.name,
                                            route = listOfNotNull(
                                                source.actual_departure?.takeIf(String::isNotBlank) ?: source.search_from?.takeIf(String::isNotBlank),
                                                source.actual_arrival?.takeIf(String::isNotBlank) ?: source.search_to?.takeIf(String::isNotBlank),
                                            ).joinToString(" -> "),
                                            date = source.date,
                                            time = source.departure_time.orEmpty(),
                                            revision = PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(
                                                source,
                                                appSettings.rotaCertaSeatAllocation,
                                            ),
                                        )
                                        UnifiedDebugEventStore.record(
                                            "PUBLIC_AGENDA_INCREMENTAL_FAILED",
                                            context.packageName,
                                            "fullSyncRequested=false failClosed=true " +
                                                AgendaFailureEvidence.describe(
                                                    error = error,
                                                    operation = "PUBLISH_INCREMENTAL_CAPACITY",
                                                    component = "TripTimelineUi",
                                                    method = "syncExternalTripIncremental",
                                                    trip = sourceFailureContext,
                                                ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            onChanged = onChanged,
            autoSyncToken = autoSyncToken,
            autoSyncProfileUuid = if (forceAllSyncActive) null else autoSyncProfileUuid,
            autoSyncTripId = if (forceAllSyncActive) null else autoSyncTripId,
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
            if (showArchived) "Nenhuma viagem arquivada." else "Nenhuma viagem sincronizada."
        syncPendingOnly && visibleEntries.isEmpty() ->
            "Nenhum card está com sincronização externa pendente."
        visibleEntries.isEmpty() && publicResponseForTimeline == null ->
            "Nenhuma viagem corresponde à busca."
        visibleEntries.isEmpty() && publicTimelineCards.isEmpty() && searchQuery.isNotBlank() ->
            "Nenhum card corresponde à busca."
        else -> null
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = listModifier.fillMaxWidth(),
    ) {
        if (timelineEmptyMessage != null) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(timelineEmptyMessage)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
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
    "timeline:" + (
        canonicalExternalTripIdentityKey(
            entry.blablaProfileUuid,
            entry.blablaTripId,
            entry.blablaTripHref,
        ) ?: entry.localTripId ?: entry.tripId
    )


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
internal fun TripDriverDefaultsCard(
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
    val traceId = AgendaTrace.currentTraceId()
    var rotaCertaAllocation by remember(settings.rotaCertaSeatAllocation) {
        mutableStateOf(settings.rotaCertaSeatAllocation.takeIf { it in 0..999 }?.toString() ?: "0")
    }
    LaunchedEffect(Unit) {
        AgendaTrace.event(
            context,
            "ROTA_CERTA_SEAT_ALLOCATION_OPENED",
            "source=vehicle_settings value=${rotaCertaAllocation.toIntOrNull() ?: 0}",
            traceId,
        )
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
                value = rotaCertaAllocation,
                onValueChange = { raw -> rotaCertaAllocation = raw.filter(Char::isDigit).take(3) },
                label = { Text("Vagas disponibilizadas no Rota Certa") },
                supportingText = {
                    Text("Única cota manual desta viagem. O valor 0 é válido.")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "A origem de referência é fixa até ser redefinida. O GPS atual continua separado e serve apenas para progresso da rota e próximo embarque.",
                style = MaterialTheme.typography.bodySmall,
            )
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Button(
                onClick = {
                    val parsedRotaCerta = rotaCertaAllocation.toIntOrNull()
                    if (parsedRotaCerta == null || parsedRotaCerta !in 0..999) {
                        error = "Informe as vagas do Rota Certa entre 0 e 999."
                        return@Button
                    }
                    error = null
                    AgendaTrace.event(
                        context,
                        "ROTA_CERTA_SEAT_ALLOCATION_SAVE_REQUESTED",
                        "source=user value=$parsedRotaCerta",
                        traceId,
                    )
                    scope.launch {
                        val saveOperation = AgendaTrace.operationStart(
                            context,
                            "ROTA_CERTA_SEAT_ALLOCATION_SAVE",
                            "TripDriverDefaultsCard",
                            traceId,
                        )
                        try {
                            repository.saveSettings(
                                settings.copy(rotaCertaSeatAllocation = parsedRotaCerta),
                            )
                            AgendaTrace.operationEnd(context, saveOperation, result = "saved", processedCount = 1)
                            UnifiedDebugEventStore.record(
                                "TRIP_DRIVER_DEFAULTS_SAVED",
                                context.packageName,
                                "rotaCertaSeatAllocation=$parsedRotaCerta legacyVehicleCapacityIgnored=true externalSeatAuthority=false referenceOriginConfigured=${referenceOrigin != null}",
                            )
                            onChanged("Vagas disponibilizadas no Rota Certa salvas.")
                        } catch (failure: kotlinx.coroutines.CancellationException) {
                            AgendaTrace.operationCancelled(context, saveOperation)
                            throw failure
                        } catch (failure: Throwable) {
                            AgendaTrace.operationError(context, saveOperation, failure)
                            throw failure
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Salvar veículo e vagas") }
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
    val enriched = entry.copy(
        blablaTripId = entry.blablaTripId ?: binding.blablaTripId.takeIf(String::isNotBlank),
        blablaTripHref = entry.blablaTripHref ?: binding.blablaTripHref.takeIf(String::isNotBlank),
        blablaPublicHref = entry.blablaPublicHref
            ?: BlaBlaCollectorUrlModule.publicTrip(binding.blablaPublicHref, binding.blablaTripId),
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
    val dark = isSystemInDarkTheme()
    val profileColors = timelineProfileCardColors(profileColorSlot, dark)
    val seatPlan = timelineDesiredSeatSyncPlan(entry, trip, store)
    var directPassengerTrip by remember(entry.tripId) { mutableStateOf<Trip?>(null) }
    var showSeatDetails by remember(entry.tripId) { mutableStateOf(false) }

    fun requestSeatOnlySync(@Suppress("UNUSED_PARAMETER") selectedTrip: Trip?, reason: String) {
        UnifiedDebugEventStore.record(
            "EXTERNAL_SEAT_WRITE_SKIPPED",
            context.packageName,
            "reason=independent_channel_inventory source=$reason trip=${entry.tripId}",
        )
        onChanged("Sincronizando a leitura BlaBlaCar sem alterar vagas externas.")
        onManualSeatSyncRequested()
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
            val publicLoads = seatPlan?.let { plan -> timelinePublicSegmentLoads(entry, plan.loads) }
            val segmentFree = publicLoads?.minOfOrNull(SegmentLoad::availableSeats)
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

            TextButton(onClick = { showSeatDetails = true }) {
                Text(if (free != null) "💺 $free" else "💺 ⏳")
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
                onSyncExactCard = onSyncExactCard,
                onSyncSeatsOnly = { requestSeatOnlySync(trip, "manual_card_shortcut") },
                focusedBookingId = focusedBookingId,
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
                        val publicLoads = timelinePublicSegmentLoads(entry, seatPlan.loads)
                        publicLoads.forEach { load ->
                            Text("${load.from.name} → ${load.to.name}    👥 ${load.passengerSeats} • 🚫 ${load.blockedSeats} • 🪑 ${load.availableSeats}")
                        }
                        entry.blablaPublishedSeats?.takeIf { it >= 0 }?.let { published ->
                            Text("Observado no editor BlaBlaCar: $published vaga(s) publicadas", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("🪑 ${publicLoads.minOf(SegmentLoad::availableSeats)} = disponibilidade física mínima entre os trechos", style = MaterialTheme.typography.bodySmall)
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
    }
}