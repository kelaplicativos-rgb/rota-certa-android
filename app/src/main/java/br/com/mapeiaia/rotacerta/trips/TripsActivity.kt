package br.com.mapeiaia.rotacerta.trips

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.com.mapeiaia.rotacerta.AppSettings
import br.com.mapeiaia.rotacerta.SettingsRepository
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import br.com.mapeiaia.rotacerta.date.RotaCertaDateSelection
import br.com.mapeiaia.rotacerta.date.RotaCertaDateSelectionMode
import br.com.mapeiaia.rotacerta.ui.RotaCertaDatePickerDialog
import br.com.mapeiaia.rotacerta.ui.RotaCertaDateSelectionField
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.coroutines.launch

class TripsActivity : ComponentActivity() {
    private var agendaTimelineCrashGuard: AgendaTimelineCrashGuard? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val createStartedNs = android.os.SystemClock.elapsedRealtimeNanos()
        val createStartedWall = System.currentTimeMillis()
        val traceId = AgendaTrace.adoptTrace(intent)
        val openStartedNs = AgendaTrace.openStartNs(intent, traceId)
        super.onCreate(savedInstanceState)
        AgendaTrace.event(
            this,
            "TRIPS_ACTIVITY_ONCREATE_START",
            "savedInstanceStatePresent=${savedInstanceState != null} launchAction=${intent?.action?.take(80).orEmpty()} coldWarm=unknown",
            traceId,
            wallMs = createStartedWall,
            monotonicNs = createStartedNs,
        )
        agendaTimelineCrashGuard = AgendaTimelineCrashGuard.install(this)
        AgendaSyncCrashTraceStore.checkpoint(this, "timeline_activity_created")
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 304)
        }
        TripShortcutInstaller.installDynamic(this)
        AgendaSyncCrashTraceStore.checkpoint(this, "timeline_before_set_content")
        AgendaTrace.event(this, "TRIPS_ACTIVITY_BEFORE_SET_CONTENT", "savedInstanceStatePresent=${savedInstanceState != null}", traceId)
        AgendaTrace.installFirstRenderObservers(this, traceId, openStartedNs)
        val contentOperation = AgendaTrace.operationStart(this, "AGENDA_SET_CONTENT", "TripsActivity.onCreate", traceId)
        try {
            setContent {
                MaterialTheme {
                    TripApp(
                        activity = this,
                        startCreating = intent?.action == TripActions.ACTION_NEW_TRIP,
                        initialTripId = intent?.getStringExtra(TripActions.EXTRA_TRIP_ID),
                        initialRemoteTripId = intent?.getStringExtra(TripActions.EXTRA_REMOTE_TRIP_ID),
                        initialBookingId = intent?.getStringExtra(TripActions.EXTRA_BOOKING_ID),
                        initialPendingOnly = intent?.getBooleanExtra(TripActions.EXTRA_PENDING_ONLY, false) == true,
                        openReservationRequests = intent?.action == TripActions.ACTION_OPEN_RESERVATION_REQUESTS,
                    )
                }
            }
            AgendaTrace.event(this, "TRIPS_ACTIVITY_AFTER_SET_CONTENT", "result=returned", traceId, contentOperation.operationId)
            AgendaTrace.operationEnd(this, contentOperation)
        } catch (error: Throwable) {
            AgendaTrace.operationError(this, contentOperation, error)
            throw error
        }
        AgendaSyncCrashTraceStore.checkpoint(this, "timeline_after_set_content")
        val createDurationMs = ((android.os.SystemClock.elapsedRealtimeNanos() - createStartedNs).coerceAtLeast(0L)) / 1_000_000L
        AgendaTrace.event(this, "TRIPS_ACTIVITY_ONCREATE_END", "durationMs=$createDurationMs", traceId)
    }

    override fun onDestroy() {
        AgendaTrace.event(
            this,
            "TRIPS_ACTIVITY_DESTROY",
            "changingConfigurations=$isChangingConfigurations finishing=$isFinishing",
        )
        AgendaSyncCrashTraceStore.checkpoint(this, "timeline_activity_destroy")
        super.onDestroy()
        agendaTimelineCrashGuard?.close()
    }
}

private enum class TripScreen { LIST, TIMELINE, CREATE, SETTINGS, PASSENGERS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripApp(
    activity: ComponentActivity,
    startCreating: Boolean,
    initialTripId: String?,
    initialRemoteTripId: String?,
    initialBookingId: String?,
    initialPendingOnly: Boolean,
    openReservationRequests: Boolean,
) {
    val traceId = AgendaTrace.currentTraceId()
    val firstCompositionOperation = remember {
        AgendaTrace.operationStart(activity, "AGENDA_FIRST_COMPOSITION", "TripApp", traceId)
    }
    val firstCompositionEnded = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val timelineStartupOperation = remember {
        AgendaTrace.operationStart(activity, "TIMELINE_STARTUP", "TripApp", traceId)
    }
    val timelineStartupEnded = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    androidx.compose.runtime.DisposableEffect(timelineStartupOperation) {
        onDispose {
            if (timelineStartupEnded.compareAndSet(false, true)) {
                AgendaTrace.operationCancelled(
                    activity,
                    timelineStartupOperation,
                    result = "activity_disposed_before_visual_ready",
                )
            }
        }
    }
    val store = remember { TripStore(activity) }
    val settingsRepository = remember(activity) { SettingsRepository(activity) }
    val settingsObservationStartedNs = remember {
        AgendaTrace.event(activity, "CAPACITY_LOCAL_SETTINGS_REQUEST", "source=local_settings", traceId)
        android.os.SystemClock.elapsedRealtimeNanos()
    }
    val appSettingsState by settingsRepository.settings.collectAsState(initial = null)
    val settingsLoaded = appSettingsState != null
    val appSettings = appSettingsState ?: AppSettings()
    val capacityFirstValueReported = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val capacityInitialReported = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    if (capacityInitialReported.compareAndSet(false, true)) {
        val present = settingsLoaded && appSettings.rotaCertaSeatAllocation in 0..999
        val source = when {
            !settingsLoaded -> "awaiting_local_settings"
            present -> "local_settings"
            else -> "local_settings_unconfigured"
        }
        AgendaTrace.event(
            activity,
            "CAPACITY_INITIAL_STATE",
            "source=$source valuePresent=$present value=${appSettings.rotaCertaSeatAllocation.takeIf { present } ?: 0}",
            traceId,
        )
    }
    var trips by remember {
        val operation = AgendaTrace.operationStart(activity, "TIMELINE_LOCAL_TRIPS_LOAD", "TripApp", traceId)
        try {
            val loaded = store.trips()
            AgendaTrace.operationEnd(activity, operation, processedCount = loaded.size)
            mutableStateOf(loaded)
        } catch (error: Throwable) {
            AgendaTrace.operationError(activity, operation, error)
            throw error
        }
    }
    var bookings by remember {
        val operation = AgendaTrace.operationStart(activity, "TIMELINE_LOCAL_BOOKINGS_LOAD", "TripApp", traceId)
        try {
            val loaded = store.bookings()
            AgendaTrace.operationEnd(activity, operation, processedCount = loaded.size)
            mutableStateOf(loaded)
        } catch (error: Throwable) {
            AgendaTrace.operationError(activity, operation, error)
            throw error
        }
    }
    var autoBlaBlaSyncToken by remember { mutableStateOf(0) }
    var forceAllBlaBlaSyncToken by remember { mutableStateOf(0) }
    // Global Agenda revision is reserved for an explicit full rebuild/maintenance request.
    // Normal mutations flow through TripMutationCoordinator0387 by canonicalTripId.
    var publicAgendaSyncRevision by remember { mutableStateOf(-1) }
    var localCapacityIncrementalBaseline by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var refreshAllRunning by remember { mutableStateOf(false) }
    var pendingCreateForPassengerId by remember { mutableStateOf("") }
    var addPassengerResumePassengerId by remember { mutableStateOf<String?>(null) }
    var addPassengerResumeTripId by remember { mutableStateOf<String?>(null) }
    var addPassengerResumeToken by remember { mutableStateOf(0) }
    var screen by remember {
        mutableStateOf(
            when {
                startCreating -> TripScreen.CREATE
                openReservationRequests || initialBookingId != null || initialPendingOnly -> TripScreen.TIMELINE
                initialTripId != null -> TripScreen.LIST
                else -> TripScreen.TIMELINE
            },
        )
    }
    var selectedId by remember { mutableStateOf(initialTripId) }
    var focusedTripId by remember { mutableStateOf(initialTripId.takeIf { openReservationRequests }) }
    var focusedRemoteTripId by remember { mutableStateOf(initialRemoteTripId) }
    var focusedBookingId by remember { mutableStateOf(initialBookingId) }
    var reservationPendingOnly by remember { mutableStateOf(initialPendingOnly) }
    var message by remember { mutableStateOf<String?>(null) }
    var driverNotifications by remember { mutableStateOf<List<DriverNotificationItem>>(emptyList()) }
    var driverUnreadCount by remember { mutableStateOf(0) }
    var notificationsExpanded by remember { mutableStateOf(false) }
    val shareScope = rememberCoroutineScope()

    val refreshDriverNotifications: suspend () -> Unit = {
        val online = store.onlineSettings()
        if (!online.configured) {
            driverNotifications = emptyList()
            driverUnreadCount = 0
        } else {
            runCatching { TripRemoteApi(online).listDriverNotifications() }
                .onSuccess { response ->
                    driverNotifications = response.notifications
                    driverUnreadCount = response.unreadCount.coerceAtLeast(0)
                }
                .onFailure { error ->
                    UnifiedDebugEventStore.record(
                        "DRIVER_NOTIFICATION_CENTER_REFRESH_FAILED",
                        activity.packageName,
                        AgendaFailureEvidence.describe(
                            error = error,
                            operation = "DRIVER_NOTIFICATION_CENTER_REFRESH",
                            component = "TripsActivity",
                            method = "refreshDriverNotifications",
                        ),
                    )
                }
        }
    }

    androidx.compose.runtime.SideEffect {
        AgendaTrace.markContentMounted(activity, loading = refreshAllRunning)
        if (firstCompositionEnded.compareAndSet(false, true)) {
            AgendaTrace.operationEnd(activity, firstCompositionOperation, result = "content_mounted")
        }
    }

    androidx.compose.runtime.LaunchedEffect(settingsLoaded, appSettings.rotaCertaSeatAllocation) {
        if (!settingsLoaded) {
            AgendaTrace.event(
                activity,
                "INVENTORY_LOCAL_SETTINGS_WAITING",
                "source=awaiting_local_settings",
                traceId,
            )
            return@LaunchedEffect
        }
        val beforeTrips = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { store.trips() }
        val (changedTrips, _) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            store.reconcileOperationalInventory(
                rotaCertaSeatAllocation = appSettings.rotaCertaSeatAllocation,
            )
        }
        if (changedTrips > 0) {
            val reconciledTrips = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { store.trips() }
            trips = reconciledTrips
            val beforeById = beforeTrips.associateBy(Trip::id)
            val changedLocalIds = reconciledTrips
                .filter(Trip::isCanonicalLocalPublishSource)
                .filter { trip ->
                    val before = beforeById[trip.id]
                    before == null || before.capacity != trip.capacity || before.rotaCertaSeatAllocation != trip.rotaCertaSeatAllocation
                }
                .map(Trip::id)
            val mutationCoordinator = TripMutationCoordinator0387(activity, store)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                changedLocalIds.forEach { tripId ->
                    mutationCoordinator.recordLocalMutation(
                        canonicalTripId = tripId,
                        mutationType = "TENANT_SEAT_ALLOCATION_CHANGED",
                        source = "ROTA_CERTA_SETTINGS",
                        configuredRotaCertaSeatAllocation = appSettings.rotaCertaSeatAllocation,
                        reconcileBookingInventory = false,
                    )
                }
                mutationCoordinator.drainPending()
            }
            UnifiedDebugEventStore.record(
                "OPERATIONAL_INVENTORY_RECONCILED",
                activity.packageName,
                "rotaCertaSeatAllocation=${appSettings.rotaCertaSeatAllocation} trips=$changedTrips incrementalLocal=${changedLocalIds.size} incrementalExternal=false fullSyncRequested=false legacyVehicleCapacityIgnored=true",
            )
        }
        AgendaTrace.event(
            activity,
            "INVENTORY_LOCAL_SETTINGS_RECEIVED",
            "source=rota_certa_allocation value=${appSettings.rotaCertaSeatAllocation}",
            traceId,
        )
    }

    androidx.compose.runtime.LaunchedEffect(screen) {
        if (screen != TripScreen.TIMELINE && timelineStartupEnded.compareAndSet(false, true)) {
            AgendaTrace.operationEnd(
                activity,
                timelineStartupOperation,
                result = "non_timeline_destination",
                processedCount = trips.size + bookings.size,
            )
        }
    }

    androidx.compose.runtime.LaunchedEffect(screen, trips.size, bookings.size, refreshAllRunning, settingsLoaded, appSettings.rotaCertaSeatAllocation) {
        AgendaTrace.event(
            activity,
            "AGENDA_RENDER_STATE",
            "loading=$refreshAllRunning empty=${trips.isEmpty() && bookings.isEmpty()} items=${trips.size} capacityPresent=${settingsLoaded && appSettings.rotaCertaSeatAllocation in 0..999} settingsLoaded=$settingsLoaded syncRunning=$refreshAllRunning screen=${screen.name.lowercase()}",
            traceId,
        )
    }
    val refresh = {
        trips = store.trips()
        bookings = store.bookings()
        TripWidgetProvider.updateAll(activity)
    }
    val publicAgendaSyncCoordinator = remember(activity, store, shareScope) {
        createPublicAgendaSyncCoordinator0373(activity, store, shareScope)
    }
    val tripMutationCoordinator = remember(activity, store) { TripMutationCoordinator0387(activity, store) }
    androidx.compose.runtime.LaunchedEffect(publicAgendaSyncCoordinator) {
        publicAgendaSyncCoordinator.completions.collect { completion ->
            val result = completion.result
            if (result == null) {
                message = "Não foi possível sincronizar a Agenda Pública. A próxima mudança real tentará novamente."
                return@collect
            }
            runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    BookingPushRegistration0304.ensureRegistered(activity, store)
                }
            }
            AgendaSyncCrashTraceStore.checkpoint(
                activity,
                "timeline_public_agenda_coordinator_result local=${result.localPublished} external=${result.externalPublished} failures=${result.failures} durationMs=${completion.durationMs}",
            )
            if (result.localPublished + result.externalPublished > 0) {
                refresh()
                // Successful background publication is intentionally silent in the UI.
                // The coordinator checkpoint above remains the audit/diagnostic evidence.
                message = null
            } else if (result.failures > 0) {
                message = "Não foi possível enviar as viagens para a Agenda Pública. Tente abrir a Agenda novamente."
            } else {
                message = null
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        BookingRealtimeEvents0356.changes.collect {
            refresh()
            refreshDriverNotifications()
        }
    }
    androidx.compose.runtime.DisposableEffect(activity) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refresh()
                // Resume only retries durable per-trip deltas. It never triggers a tenant-wide sync.
                shareScope.launch { tripMutationCoordinator.drainPending() }
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    val requestFullTimelineRefresh = {
        if (screen == TripScreen.TIMELINE && !refreshAllRunning) {
            AgendaTrace.event(activity, "USER_SYNC_ALL", "source=pull_to_refresh", traceId)
            AgendaSyncCrashTraceStore.arm(activity)
            AgendaSyncCrashTraceStore.checkpoint(activity, "timeline_pull_requested")
            refreshAllRunning = true
            message = "Sincronizando tudo: contas BlaBlaCar, reservas e Agenda Pública…"
            UnifiedDebugEventStore.record(
                "AGENDA_PULL_REFRESH_ALL_REQUESTED",
                activity.packageName,
                "scope=all_accounts public_bookings=true public_agenda=true source=timeline_pull",
            )
            shareScope.launch {
                AgendaSyncCrashTraceStore.checkpoint(activity, "timeline_pull_before_public_booking_reconcile")
                AgendaTrace.event(activity, "TIMELINE_PUBLIC_BOOKING_RECONCILE_START", "source=pull_refresh", traceId)
                val bookingSync = runCatching {
                    PublicBookingRemoteSync0296.pullAndReconcile(activity, store)
                }
                bookingSync.exceptionOrNull()?.let { error ->
                    UnifiedDebugEventStore.record(
                        "PUBLIC_BOOKING_RECONCILE_FAILED",
                        activity.packageName,
                        AgendaFailureEvidence.describe(
                            error = error,
                            operation = "BOOKING_RECONCILE",
                            component = "TripsActivity",
                            method = "requestFullTimelineRefresh",
                        ),
                    )
                }
                AgendaTrace.event(
                    activity,
                    "TIMELINE_PUBLIC_BOOKING_RECONCILE_END",
                    "source=pull_refresh success=${bookingSync.isSuccess} imported=${bookingSync.getOrNull()?.importedCount ?: 0}",
                    traceId,
                )
                AgendaSyncCrashTraceStore.checkpoint(
                    activity,
                    "timeline_pull_after_public_booking_reconcile success=${bookingSync.isSuccess}",
                )
                refresh()
                AgendaSyncCrashTraceStore.checkpoint(activity, "timeline_pull_after_local_refresh")

                val nextSyncToken = autoBlaBlaSyncToken + 1
                AgendaSyncCrashTraceStore.checkpoint(activity, "timeline_pull_before_blabla_token token=$nextSyncToken")
                forceAllBlaBlaSyncToken = nextSyncToken
                autoBlaBlaSyncToken = nextSyncToken
                AgendaSyncCrashTraceStore.checkpoint(activity, "timeline_pull_after_blabla_token token=$nextSyncToken")
                publicAgendaSyncRevision++
                AgendaSyncCrashTraceStore.checkpoint(activity, "timeline_pull_public_agenda_revision_incremented")

                refreshAllRunning = false
                AgendaSyncCrashTraceStore.checkpoint(activity, "timeline_pull_dispatch_complete")
                val imported = bookingSync.getOrNull()?.importedCount ?: 0
                message = if (bookingSync.isFailure) {
                    "Sincronização geral iniciada. BlaBlaCar e Agenda Pública continuam; a leitura das reservas públicas falhou e será tentada novamente no próximo ciclo."
                } else {
                    "Sincronização geral iniciada • todas as contas BlaBlaCar • Agenda Pública • $imported reserva(s) pública(s) recebida(s)."
                }
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            refreshDriverNotifications()
            kotlinx.coroutines.delay(15_000L)
        }
    }

    androidx.compose.runtime.LaunchedEffect(tripMutationCoordinator) {
        while (true) {
            runCatching { tripMutationCoordinator.drainPending() }
                .onFailure { error ->
                    UnifiedDebugEventStore.record(
                        "TRIP_MUTATION_OUTBOX_RETRY_FAILED",
                        activity.packageName,
                        failureSummary0387(error),
                    )
                }
            kotlinx.coroutines.delay(30_000L)
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        AgendaSyncCrashTraceStore.checkpoint(activity, "timeline_startup_booking_reconcile_begin")
        AgendaTrace.event(activity, "TIMELINE_PUBLIC_BOOKING_RECONCILE_START", "source=startup background=true", traceId)
        try {
            val result = PublicBookingRemoteSync0296.pullAndReconcile(activity, store)
            AgendaTrace.event(
                activity,
                "TIMELINE_PUBLIC_BOOKING_RECONCILE_END",
                "source=startup result=ok imported=${result.importedCount} seatSyncQueued=${result.seatSyncQueued} background=true",
                traceId,
            )
            AgendaSyncCrashTraceStore.checkpoint(activity, "timeline_startup_booking_reconcile_end imported=${result.importedCount}")
            if (result.importedCount > 0) {
                refresh()
                message = "${result.importedCount} reserva(s) recebida(s) pelo link público."
                if (result.seatSyncQueued > 0) {
                    autoBlaBlaSyncToken++
                }
                // PublicBookingRemoteSync0296 already enqueued/drained exact changedTripIds.
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            AgendaTrace.event(
                activity,
                "TIMELINE_PUBLIC_BOOKING_RECONCILE_END",
                "source=startup result=caller_cancelled sharedFlightMayContinue=true background=true",
                traceId,
            )
            throw cancelled
        } catch (error: Throwable) {
            val failureEvidence = AgendaFailureEvidence.describe(
                error = error,
                operation = "BOOKING_RECONCILE",
                component = "TripsActivity",
                method = "startupBackgroundReconcile",
            )
            AgendaTrace.event(
                activity,
                "TIMELINE_PUBLIC_BOOKING_RECONCILE_END",
                "source=startup result=error background=true failureEvidence=" + failureEvidence,
                traceId,
            )
            UnifiedDebugEventStore.record(
                "PUBLIC_BOOKING_RECONCILE_FAILED",
                activity.packageName,
                failureEvidence,
            )
            message = "Agenda local pronta. A atualização de reservas públicas falhou em background e poderá ser repetida."
        }
    }

    androidx.compose.runtime.LaunchedEffect(settingsLoaded, trips, bookings, appSettings.rotaCertaSeatAllocation) {
        if (!settingsLoaded) return@LaunchedEffect
        val current = trips
            .filter(Trip::isCanonicalLocalPublishSource)
            .associate { trip ->
                trip.id to PublicAgendaAutoSync0300.localCapacitySnapshotRevision(
                    trip = trip,
                    bookings = bookings.filter { it.tripId == trip.id },
                    rotaCertaSeatAllocation = appSettings.rotaCertaSeatAllocation,
                )
            }
        val previous = localCapacityIncrementalBaseline
        localCapacityIncrementalBaseline = current
        if (previous.isNotEmpty()) {
            val changedIds = current.entries
                .filter { (tripId, revision) -> previous[tripId] != revision }
                .map { it.key }
            changedIds.forEach { tripId ->
                val failureTrip = trips.firstOrNull { it.id == tripId }
                val failureBookings = bookings.filter { it.tripId == tripId }
                val failureContext = failureTrip?.let { trip ->
                    val withAllocation = trip.copy(
                        rotaCertaSeatAllocation = appSettings.rotaCertaSeatAllocation,
                    )
                    AgendaFailureEvidence.tripContext(
                        trip = withAllocation.copy(
                            capacity = operationalInventoryCapacity(withAllocation, failureBookings),
                        ),
                        bookings = failureBookings,
                        tripKey = seatSyncDiagnosticKey(tripId),
                        publicIdentity = trip.remoteId,
                        origin = resolvedTripRecordOrigin(trip).name,
                        revision = current[tripId].orEmpty(),
                    )
                }
                runCatching {
                    tripMutationCoordinator.recordLocalMutation(
                        canonicalTripId = tripId,
                        mutationType = "LOCAL_TRIP_SEMANTIC_CHANGE",
                        source = "TIMELINE_STORE_OBSERVER",
                        configuredRotaCertaSeatAllocation = appSettings.rotaCertaSeatAllocation,
                    )
                    tripMutationCoordinator.drainPending()
                }.onFailure { error ->
                    UnifiedDebugEventStore.record(
                        "PUBLIC_LOCAL_CAPACITY_INCREMENTAL_FAILED",
                        activity.packageName,
                        "fullSyncRequested=false failClosed=true " +
                            AgendaFailureEvidence.describe(
                                error = error,
                                operation = "PUBLISH_INCREMENTAL_CAPACITY",
                                component = "TripsActivity",
                                method = "TripMutationCoordinator0387",
                                trip = failureContext,
                            ),
                    )
                }
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(
        settingsLoaded,
        appSettings.rotaCertaSeatAllocation,
        publicAgendaSyncRevision,
    ) {
        if (publicAgendaSyncRevision < 0) return@LaunchedEffect
        if (!settingsLoaded) {
            AgendaTrace.event(
                activity,
                "CAPACITY_PUBLIC_SYNC_DEFERRED",
                "reason=local_settings_not_loaded revision=$publicAgendaSyncRevision",
                traceId,
            )
            return@LaunchedEffect
        }
        val online = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { store.onlineSettings() }
        if (online.configured) {
            val reason = "public_agenda_effect_revision_$publicAgendaSyncRevision"
            AgendaSyncCrashTraceStore.checkpoint(
                activity,
                "timeline_public_agenda_effect_begin reason=$reason singleFlight=true enqueueOnly=true",
            )
            AgendaTrace.event(
                activity,
                "CAPACITY_PUBLIC_SYNC_REQUESTED",
                "reason=$reason rotaCertaAllocation=${appSettings.rotaCertaSeatAllocation} singleFlight=true",
                traceId,
            )
            AgendaTrace.event(
                activity,
                "CAPACITY_PUBLIC_SYNC_TRIGGERED",
                "reason=$reason mode=single_flight_enqueue rotaCertaAllocation=${appSettings.rotaCertaSeatAllocation}",
                traceId,
            )
            publicAgendaSyncCoordinator.request(
                rotaCertaSeatAllocation = appSettings.rotaCertaSeatAllocation,
                reason = reason,
            )
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = if (screen == TripScreen.TIMELINE) {
                Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxSize()
            } else {
                Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Agenda de Viagens", style = MaterialTheme.typography.headlineSmall)
                    Text("Rota Certa • viagens, vagas por trecho e calendário", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(
                    onClick = {
                        notificationsExpanded = !notificationsExpanded
                        shareScope.launch { refreshDriverNotifications() }
                    },
                ) {
                    BadgedBox(
                        badge = {
                            if (driverUnreadCount > 0) {
                                Badge { Text(if (driverUnreadCount > 99) "99+" else driverUnreadCount.toString()) }
                            }
                        },
                    ) {
                        Text("🔔")
                    }
                }
            }
            if (notificationsExpanded) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Notificações", style = MaterialTheme.typography.titleMedium)
                            if (driverUnreadCount > 0) {
                                TextButton(onClick = {
                                    shareScope.launch {
                                        val online = store.onlineSettings()
                                        if (online.configured) {
                                            runCatching { TripRemoteApi(online).markAllDriverNotificationsRead() }
                                            refreshDriverNotifications()
                                        }
                                    }
                                }) { Text("Marcar todas como lidas") }
                            }
                        }
                        if (driverNotifications.isEmpty()) {
                            Text("Nenhuma notificação.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            driverNotifications.take(20).forEach { item ->
                                TextButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        shareScope.launch {
                                            val online = store.onlineSettings()
                                            if (online.configured && item.id.isNotBlank()) {
                                                runCatching { TripRemoteApi(online).markDriverNotificationRead(item.id) }
                                            }
                                            val localTrip = trips.firstOrNull {
                                                it.remoteId == item.tripId || it.id == item.tripId
                                            }
                                            if (localTrip != null) {
                                                focusedTripId = localTrip.id
                                                focusedRemoteTripId = item.tripId
                                                focusedBookingId = item.bookingId.takeIf(String::isNotBlank)
                                                reservationPendingOnly = false
                                                screen = TripScreen.TIMELINE
                                            }
                                            refreshDriverNotifications()
                                        }
                                    },
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            (if (!item.read) "● " else "") + item.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Text(item.message, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            message?.let {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(it, modifier = Modifier.padding(12.dp))
                }
            }
            when (screen) {
                TripScreen.CREATE -> TripEditor(
                    defaultOrigin = appSettings.tripDepartureAddress,
                    defaultRotaCertaSeatAllocation = appSettings.rotaCertaSeatAllocation,
                    onCancel = {
                        pendingCreateForPassengerId = ""
                        screen = TripScreen.TIMELINE
                    },
                    onSave = { trip ->
                        store.saveTrip(trip)
                        refresh()
                        publicAgendaSyncRevision++
                        selectedId = trip.id
                        val resumePassengerId = pendingCreateForPassengerId.takeIf(String::isNotBlank)
                        pendingCreateForPassengerId = ""
                        if (resumePassengerId != null) {
                            addPassengerResumePassengerId = resumePassengerId
                            addPassengerResumeTripId = trip.id
                            addPassengerResumeToken++
                            message = "Viagem criada. Continue a inclusão do passageiro já selecionado."
                        } else {
                            message = "Viagem criada. Publique quando estiver pronta."
                        }
                        screen = TripScreen.TIMELINE
                    },
                )
                TripScreen.TIMELINE -> TripTimelineScreen(
                    trips = trips,
                    bookings = bookings,
                    store = store,
                    onChanged = { text -> refresh(); publicAgendaSyncRevision++; message = text },
                    autoSyncToken = autoBlaBlaSyncToken,
                    forceAllSyncToken = forceAllBlaBlaSyncToken,
                    onRequestBlaBlaSync = { autoBlaBlaSyncToken++ },
                    onCreateTrip = {
                        pendingCreateForPassengerId = ""
                        screen = TripScreen.CREATE
                    },
                    onCreateTripForPassenger = { passengerId ->
                        pendingCreateForPassengerId = passengerId
                        screen = TripScreen.CREATE
                    },
                    addPassengerResumeToken = addPassengerResumeToken,
                    addPassengerResumePassengerId = addPassengerResumePassengerId,
                    addPassengerResumeTripId = addPassengerResumeTripId,
                    onPinShortcut = {
                        val requested = TripShortcutInstaller.requestPinnedCreateShortcut(activity)
                        message = if (requested) "Pedido de atalho enviado ao Android." else "O launcher não permite fixar atalhos automaticamente."
                    },
                    onOpenOnlineSettings = { screen = TripScreen.SETTINGS },
                    onOpenPassengers = { screen = TripScreen.PASSENGERS },
                    onManageLocal = { tripId ->
                        selectedId = tripId
                        screen = TripScreen.LIST
                    },
                    onBack = { activity.finish() },
                    focusedTripId = focusedTripId
                        ?: focusedRemoteTripId?.let { remote -> trips.firstOrNull { it.remoteId == remote }?.id },
                    focusedBookingId = focusedBookingId,
                    reservationPendingOnly = reservationPendingOnly,
                    refreshing = refreshAllRunning,
                    onRefresh = requestFullTimelineRefresh,
                    listModifier = Modifier.weight(1f),
                    onFirstUsableFrame = { renderedItems ->
                        AgendaTrace.reportTimelineFirstUsableFrame(
                            activity = activity,
                            traceId = traceId,
                            renderedItems = renderedItems,
                        ) {
                            if (timelineStartupEnded.compareAndSet(false, true)) {
                                AgendaTrace.operationEnd(
                                    activity,
                                    timelineStartupOperation,
                                    result = "visual_ready",
                                    processedCount = renderedItems,
                                )
                            }
                        }
                    },
                )
                TripScreen.PASSENGERS -> PassengerAdminScreen(
                    store = store,
                    onBack = { screen = TripScreen.TIMELINE },
                    onChanged = { text -> refresh(); publicAgendaSyncRevision++; message = text },
                )
                TripScreen.SETTINGS -> OnlineSettingsEditor(
                    initial = store.onlineSettings(),
                    onSave = { saved ->
                        store.saveOnlineSettings(saved)
                        screen = TripScreen.TIMELINE
                        if (saved.configured) {
                            message = "Salvando Integração online…"
                            shareScope.launch {
                                runCatching {
                                    val resolvedProfile = PublicDriverProfileResolver(activity).resolve(saved)
                                    val response = TripRemoteApi(saved).ensurePublicAgenda(saved.publicCalendarToken, resolvedProfile)
                                    val validated = saved.copy(
                                        driverDisplayName = response.displayName.ifBlank { saved.driverDisplayName },
                                        driverUsername = response.username.ifBlank { saved.driverUsername },
                                    )
                                    store.saveOnlineSettings(validated)
                                    validated
                                }.onSuccess {
                                    message = "Integração online salva e perfil público atualizado."
                                }.onFailure {
                                    message = "Configuração salva no aparelho, mas o perfil público ainda não sincronizou: ${it.message ?: "erro de conexão"}"
                                }
                            }
                        } else {
                            message = "Configuração salva; modo online ainda desativado."
                        }
                    },
                    onRotateLink = { expected, replacement ->
                        store.replacePublicAgendaLinkAfterConfirmedRotation(expected, replacement)
                    },
                    onCancel = { screen = TripScreen.TIMELINE },
                )
                TripScreen.LIST -> {
                    OutlinedButton(onClick = { screen = TripScreen.TIMELINE }) {
                        Text("Voltar à Timeline")
                    }
                    val onlineSettings = store.onlineSettings()
                    if (onlineSettings.publicAgendaUrl != null) {
                        OutlinedButton(onClick = {
                            if (!onlineSettings.configured) {
                                message = "A integração online precisa da chave privada do motorista antes de compartilhar."
                            } else {
                                message = "Validando seu link público…"
                                shareScope.launch {
                                    runCatching {
                                        val resolvedProfile = PublicDriverProfileResolver(activity).resolve(onlineSettings)
                                        val response = TripRemoteApi(onlineSettings).ensurePublicAgenda(onlineSettings.publicCalendarToken, resolvedProfile)
                                        val validated = onlineSettings.copy(
                                            driverDisplayName = response.displayName.ifBlank { onlineSettings.driverDisplayName },
                                            driverUsername = response.username.ifBlank { onlineSettings.driverUsername },
                                        )
                                        store.saveOnlineSettings(validated)
                                        response to validated
                                    }.onSuccess { (response, validated) ->
                                        if (TripCalendarBridge.sharePublicAgenda(activity, validated)) {
                                            message = "Link da Agenda Pública validado e pronto para compartilhar."
                                        } else {
                                            message = "Não foi possível montar o link público validado."
                                        }
                                    }.onFailure {
                                        message = "Não foi possível validar o link público: ${it.message ?: "erro de conexão"}"
                                    }
                                }
                            }
                        }) { Text("Compartilhar minha agenda") }
                    }
                    if (onlineSettings.googleCalendarMirrorUrl != null) {
                        OutlinedButton(onClick = {
                            if (TripCalendarBridge.shareGoogleCalendarFallback(activity, onlineSettings)) message = "Link do Google Agenda pronto para compartilhar."
                        }) { Text("Compartilhar Google Agenda") }
                    }
                    if (trips.isEmpty()) {
                        Text("Nenhuma viagem local neste aparelho. A Timeline continua exibindo publicações sincronizadas.")
                    } else {
                        trips.sortedBy { it.departureAtMillis }.forEach { trip ->
                            TripCard(
                                activity = activity,
                                store = store,
                                trip = trip,
                                expanded = selectedId == trip.id,
                                onToggle = { selectedId = if (selectedId == trip.id) null else trip.id },
                                onChanged = { text -> refresh(); publicAgendaSyncRevision++; message = text },
                                onRequestBlaBlaSync = {
                                    autoBlaBlaSyncToken++
                                    screen = TripScreen.TIMELINE
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun tripEditorDepartureMillis(
    selection: RotaCertaDateSelection,
    timeText: String,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long? {
    val date = selection.normalizedDates.singleOrNull() ?: return null
    val time = runCatching {
        LocalTime.parse(timeText.trim(), DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrNull() ?: return null
    return date.atTime(time).atZone(zoneId).toInstant().toEpochMilli()
}

@Composable
private fun TripEditor(
    defaultOrigin: String,
    defaultRotaCertaSeatAllocation: Int,
    onCancel: () -> Unit,
    onSave: (Trip) -> Unit,
) {
    val initialDeparture = remember {
        val tomorrow = LocalDate.now().plusDays(1)
        val hour = LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
        tomorrow to hour
    }
    var origin by remember(defaultOrigin) { mutableStateOf(defaultOrigin.trim()) }
    var destination by remember { mutableStateOf("") }
    var intermediate by remember { mutableStateOf("") }
    var departureDate by remember {
        mutableStateOf(
            RotaCertaDateSelection(
                mode = RotaCertaDateSelectionMode.SINGLE,
                dates = listOf(initialDeparture.first),
            ),
        )
    }
    var departureTime by remember { mutableStateOf(initialDeparture.second.format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var showDepartureDatePicker by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var segmentPrices by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var routePlan by remember { mutableStateOf<TripRoutePlan?>(null) }

    Text("Criar viagem", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(origin, { origin = it }, label = { Text("Origem") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(destination, { destination = it }, label = { Text("Destino") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(
        intermediate,
        { intermediate = it },
        label = { Text("Paradas intermediárias — uma por linha") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
    )
    OutlinedTextField(
        segmentPrices,
        { segmentPrices = it },
        label = { Text("Valores por trecho em R$ — uma linha por trecho") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
    )
    Text("Ex.: origem → parada = 20,00; parada → destino = 25,00. Deixe vazio para não publicar valor.", style = MaterialTheme.typography.bodySmall)
    RotaCertaDateSelectionField(
        selection = departureDate,
        onClick = { showDepartureDatePicker = true },
        label = "Data da saída",
        emptySummary = "Selecione a data da saída",
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = departureTime,
        onValueChange = { raw ->
            departureTime = raw.filter { it.isDigit() || it == ':' }.take(5)
        },
        label = { Text("Horário da saída — HH:mm") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(notes, { notes = it }, label = { Text("Observações públicas opcionais") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    val planningNames = buildList {
        if (origin.isNotBlank()) add(origin.trim())
        addAll(intermediate.lines().map(String::trim).filter(String::isNotBlank))
        if (destination.isNotBlank()) add(destination.trim())
    }
    val planningDepartureMillis = tripEditorDepartureMillis(departureDate, departureTime)
    TripRoutePlannerControl(
        stopNames = planningNames,
        departureAtMillis = planningDepartureMillis,
        onPlan = { routePlan = it },
    )
    error?.let { Text(it) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            runCatching {
                require(origin.isNotBlank()) { "Informe a origem." }
                require(destination.isNotBlank()) { "Informe o destino." }
                val allocatedSeats = defaultRotaCertaSeatAllocation.coerceIn(0, 999)
                val departureMillis = tripEditorDepartureMillis(departureDate, departureTime)
                    ?: throw IllegalArgumentException("Selecione a data e informe o horário da saída no formato HH:mm.")
                val names = buildList {
                    add(origin.trim())
                    addAll(intermediate.lines().map(String::trim).filter(String::isNotBlank))
                    add(destination.trim())
                }
                require(names.size >= 2) { "A viagem precisa de origem e destino." }
                val rawPrices = segmentPrices.lines().map(String::trim).filter(String::isNotBlank)
                val prices = if (rawPrices.isEmpty()) List(names.size - 1) { 0L } else {
                    require(rawPrices.size == names.size - 1) { "Informe exatamente ${names.size - 1} valor(es), um para cada trecho." }
                    rawPrices.map { raw -> parseFareCents(raw) ?: throw IllegalArgumentException("Valor inválido: $raw") }
                }
                val planned = routePlan?.takeIf { plan ->
                    plan.stops.map(TripStop::name) == names &&
                        plan.stops.firstOrNull()?.plannedDepartureMillis == departureMillis
                }
                val stops = (planned?.stops ?: names.mapIndexed { index, name ->
                    TripStop(
                        order = index,
                        name = name,
                        address = name,
                        plannedDepartureMillis = if (index == 0) departureMillis else null,
                        plannedArrivalMillis = if (index == 0) departureMillis else null,
                    )
                }).mapIndexed { index, stop ->
                    stop.copy(priceToNextCents = prices.getOrElse(index) { 0L })
                }
                Trip(
                    title = "${origin.trim()} → ${destination.trim()}",
                    departureAtMillis = departureMillis,
                    capacity = allocatedSeats,
                    rotaCertaSeatAllocation = allocatedSeats,
                    stops = stops,
                    notes = notes.trim(),
                )
            }.onSuccess(onSave).onFailure { error = it.message ?: "Não foi possível criar a viagem." }
        }) { Text("Salvar rascunho") }
        TextButton(onClick = onCancel) { Text("Cancelar") }
    }

    if (showDepartureDatePicker) {
        RotaCertaDatePickerDialog(
            selection = departureDate,
            onDismiss = { showDepartureDatePicker = false },
            onConfirm = {
                departureDate = it
                showDepartureDatePicker = false
            },
            minDate = LocalDate.now(),
            allowedModes = setOf(RotaCertaDateSelectionMode.SINGLE),
            allowEmptySelection = false,
            emptyConfirmLabel = "Selecione uma data",
            title = "Data da saída",
            description = "Escolha a data da viagem. Dias passados ficam indisponíveis.",
        )
    }
}

@Composable
private fun TripCard(
    activity: ComponentActivity,
    store: TripStore,
    trip: Trip,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChanged: (String) -> Unit,
    onRequestBlaBlaSync: () -> Unit,
) {
    val formatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm") }
    val scope = rememberCoroutineScope()
    val bookings = store.bookingsFor(trip.id)
    val seatRange = SeatAvailabilityEngine.availableSeatRange(trip, bookings)
    val availabilityText = if (seatRange.variesBySegment) {
        "vagas por trecho ${seatRange.minimum}–${seatRange.maximum}/${trip.capacity}"
    } else {
        "${seatRange.maximum}/${trip.capacity} vagas livres"
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(trip.title, style = MaterialTheme.typography.titleMedium)
            Text("${formatter.format(Instant.ofEpochMilli(trip.departureAtMillis).atZone(ZoneId.systemDefault()))} • ${trip.status} • $availabilityText")
            OutlinedButton(onClick = onToggle) { Text(if (expanded) "Fechar" else "Gerenciar") }
            if (expanded) {
                HorizontalDivider()
                trip.stops.sortedBy(TripStop::order).forEachIndexed { index, stop ->
                    Text("${index + 1}. ${stop.name}")
                }
                val loads = SeatAvailabilityEngine.segmentLoads(trip, bookings)
                if (loads.isNotEmpty()) {
                    Text("Ocupação por trecho", style = MaterialTheme.typography.titleSmall)
                    loads.forEach { load ->
                        val price = load.from.priceToNextCents
                        Text(buildString {
                            append("${load.from.name} → ${load.to.name}: ${load.occupiedSeats}/${trip.capacity} ocupadas")
                            if (price > 0L) append(" • ${formatFare(price)} por pessoa")
                        })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (trip.status == TripStatus.DRAFT) {
                        Button(onClick = {
                            store.saveTrip(trip.copy(status = TripStatus.PUBLISHED))
                            onChanged("Viagem publicada localmente.")
                        }) { Text("Publicar") }
                    }
                    if (trip.status !in setOf(TripStatus.CANCELLED, TripStatus.COMPLETED)) {
                        OutlinedButton(onClick = {
                            store.saveTrip(trip.copy(status = TripStatus.CANCELLED))
                            onChanged("Viagem cancelada.")
                        }) { Text("Cancelar viagem") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { TripCalendarBridge.shareTrip(activity, trip) }) { Text("Compartilhar") }
                    OutlinedButton(onClick = { TripCalendarBridge.addToDeviceCalendar(activity, trip) }) { Text("Google/Agenda") }
                    OutlinedButton(onClick = { TripCalendarBridge.shareIcs(activity, trip) }) { Text("ICS") }
                }
                val settings = store.onlineSettings()
                if (trip.status !in setOf(TripStatus.CANCELLED, TripStatus.COMPLETED)) {
                    OutlinedButton(onClick = {
                        val next = trip.copy(publicBookingEnabled = !trip.publicBookingEnabled)
                        store.saveTrip(next)
                        if (settings.configured && next.remoteId != null) {
                            scope.launch {
                                runCatching { TripRemoteApi(settings).update(next) }
                                    .onSuccess { onChanged(if (next.publicBookingEnabled) "Reservas pelo link ativadas para esta viagem." else "Reservas pelo link desativadas para esta viagem.") }
                                    .onFailure { onChanged("Estado salvo no Rota Certa, mas ainda não sincronizado online: ${it.message}") }
                            }
                        } else {
                            onChanged(if (next.publicBookingEnabled) "Reservas pelo link ativadas localmente. Publique/sincronize online para compartilhar." else "Reservas pelo link desativadas.")
                        }
                    }) { Text(if (trip.publicBookingEnabled) "Reservas pelo link: ATIVADAS" else "Reservas pelo link: DESATIVADAS") }
                    if (trip.publicBookingEnabled && !trip.publicUrl.isNullOrBlank()) {
                        OutlinedButton(onClick = {
                            if (!TripPublicBookingLink0296.share(activity, trip.publicUrl.orEmpty())) {
                                onChanged("Link público ainda não está disponível.")
                            }
                        }) { Text("📲 Compartilhar reservas") }
                    }
                }
                if (settings.configured && trip.status != TripStatus.DRAFT && trip.status != TripStatus.CANCELLED) {
                    Button(onClick = {
                        scope.launch {
                            runCatching {
                                val response = if (trip.remoteId == null) TripRemoteApi(settings).publish(trip) else TripRemoteApi(settings).update(trip)
                                store.saveTrip(trip.copy(remoteId = response.tripId, publicToken = response.publicToken, publicUrl = response.publicUrl))
                            }.onSuccess { onChanged("Viagem sincronizada com a agenda pública.") }
                                .onFailure { onChanged("Falha online: ${it.message}") }
                        }
                    }) { Text(if (trip.remoteId == null) "Publicar online" else "Sincronizar online") }
                    if (trip.remoteId != null) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                runCatching {
                                    TripRemoteApi(settings).listBookings(trip.remoteId).bookings
                                }.onSuccess { remoteBookings ->
                                    remoteBookings.forEach { remote ->
                                        store.saveBooking(remote.toLocalBooking(trip.id))
                                    }
                                    onChanged("Reservas online atualizadas: ${remoteBookings.size}.")
                                }.onFailure {
                                    onChanged("Falha ao atualizar reservas: ${it.message}")
                                }
                            }
                        }) { Text("Atualizar reservas online") }
                    }
                } else if (!settings.configured) {
                    Text("Modo online não configurado. Compartilhamento local, Google Agenda e ICS continuam funcionando.", style = MaterialTheme.typography.bodySmall)
                }
                if (trip.status in setOf(TripStatus.PUBLISHED, TripStatus.FULL)) {
                    QuickPassengerPanel(trip, store, onChanged, onRequestBlaBlaSync)
                }
                if (bookings.isNotEmpty()) {
                    Text("Reservas locais", style = MaterialTheme.typography.titleSmall)
                    bookings.forEach { booking ->
                        val from = trip.stops.firstOrNull { it.id == booking.boardingStopId }?.name.orEmpty()
                        val to = trip.stops.firstOrNull { it.id == booking.dropoffStopId }?.name.orEmpty()
                        Text("${booking.passengerName}: $from → $to • ${booking.seats} vaga(s) • ${booking.status}")
                    }
                }
                TextButton(onClick = {
                    store.deleteTrip(trip.id)
                    onChanged("Viagem excluída do aparelho.")
                }) { Text("Excluir viagem") }
            }
        }
    }
}

@Composable
private fun ManualBookingEditor(
    trip: Trip,
    store: TripStore,
    onChanged: (String) -> Unit,
) {
    val stops = trip.stops.sortedBy(TripStop::order)
    if (stops.size < 2) return
    var name by remember(trip.id) { mutableStateOf("") }
    var contact by remember(trip.id) { mutableStateOf("") }
    var seatsText by remember(trip.id) { mutableStateOf("1") }
    var fromIndex by remember(trip.id) { mutableStateOf(0) }
    var toIndex by remember(trip.id) { mutableStateOf(stops.lastIndex) }
    val requested = seatsText.toIntOrNull()?.coerceIn(1, trip.capacity) ?: 1
    val availability = runCatching {
        SeatAvailabilityEngine.availability(trip, store.bookingsFor(trip.id), stops[fromIndex].id, stops[toIndex].id, requested)
    }.getOrNull()

    HorizontalDivider()
    Text("Adicionar passageiro manualmente", style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(name, { name = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(contact, { contact = it }, label = { Text("Contato opcional") }, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            fromIndex = (fromIndex + 1).coerceAtMost(stops.lastIndex - 1)
            if (toIndex <= fromIndex) toIndex = fromIndex + 1
        }) { Text("Embarque: ${stops[fromIndex].name}") }
        OutlinedButton(onClick = {
            toIndex++
            if (toIndex > stops.lastIndex) toIndex = fromIndex + 1
        }) { Text("Desce: ${stops[toIndex].name}") }
    }
    OutlinedTextField(seatsText, { seatsText = it.filter(Char::isDigit).take(3) }, label = { Text("Lugares reservados") })
    Text("Disponíveis nesse trecho: ${availability?.availableSeats ?: 0}")
    val farePerSeat = runCatching { TripFareEngine.farePerSeatCents(trip, stops[fromIndex].id, stops[toIndex].id) }.getOrDefault(0L)
    if (farePerSeat > 0L) Text("Valor: ${formatFare(farePerSeat)} por pessoa • total ${formatFare(farePerSeat * requested.toLong())}")
    Button(
        enabled = name.isNotBlank() && availability?.canBook == true,
        onClick = {
            store.saveBooking(
                Booking(
                    tripId = trip.id,
                    passengerName = name.trim(),
                    passengerContact = contact.trim(),
                    boardingStopId = stops[fromIndex].id,
                    dropoffStopId = stops[toIndex].id,
                    seats = requested,
                    status = BookingStatus.CONFIRMED,
                ),
            )
            name = ""
            contact = ""
            seatsText = "1"
            onChanged("Passageiro adicionado sem ultrapassar a capacidade do trecho.")
        },
    ) { Text("Confirmar reserva") }
}

private fun parseFareCents(value: String): Long? {
    val normalized = value.trim().replace("R$", "", ignoreCase = true).replace(" ", "").replace(".", "").replace(",", ".")
    val amount = normalized.toDoubleOrNull() ?: return null
    if (!amount.isFinite() || amount < 0.0 || amount > 1_000_000.0) return null
    return (amount * 100.0).roundToLong()
}

private fun formatFare(cents: Long): String = String.format(Locale("pt", "BR"), "R$ %.2f", cents.coerceAtLeast(0L) / 100.0)
