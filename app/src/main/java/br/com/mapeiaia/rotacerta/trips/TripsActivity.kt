package br.com.mapeiaia.rotacerta.trips

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.com.mapeiaia.rotacerta.AppSettings
import br.com.mapeiaia.rotacerta.MainActivity
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry
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

private enum class TripScreen { LIST, TIMELINE, ASSISTANT, NOTIFICATIONS, PUBLIC_SEARCH, CREATE, SETTINGS, APP_SETTINGS, EXTRA_SEATS, PASSENGERS, AUTO_SYNC }

private fun TripScreen.isAgendaRoot0396(): Boolean =
    this == TripScreen.TIMELINE ||
        this == TripScreen.ASSISTANT ||
        this == TripScreen.PUBLIC_SEARCH ||
        this == TripScreen.PASSENGERS ||
        this == TripScreen.SETTINGS ||
        this == TripScreen.APP_SETTINGS ||
        this == TripScreen.AUTO_SYNC

private fun TripScreen.agendaRootSection0396(): AgendaRootSection0396 = when (this) {
    TripScreen.ASSISTANT -> AgendaRootSection0396.ASSISTANT
    TripScreen.AUTO_SYNC -> AgendaRootSection0396.AUTOMATIC_SYNC
    TripScreen.PUBLIC_SEARCH -> AgendaRootSection0396.PUBLIC_SEARCH
    TripScreen.PASSENGERS -> AgendaRootSection0396.PASSENGERS
    TripScreen.SETTINGS -> AgendaRootSection0396.INTEGRATIONS
    TripScreen.APP_SETTINGS -> AgendaRootSection0396.APP_SETTINGS
    else -> AgendaRootSection0396.ALL_TRIPS
}

private fun TripScreen.agendaHeaderLabel0396(): String = when (this) {
    TripScreen.TIMELINE -> "Timeline antiga · somente migração"
    TripScreen.ASSISTANT -> "Assistente Rota Certa"
    TripScreen.NOTIFICATIONS -> "Notificações"
    TripScreen.AUTO_SYNC -> "BlaBlaCar"
    TripScreen.APP_SETTINGS -> "Configurações"
    TripScreen.EXTRA_SEATS -> "Vagas extra"
    TripScreen.PUBLIC_SEARCH -> "Consulta pública"
    TripScreen.PASSENGERS -> "Passageiros"
    TripScreen.CREATE -> "Nova viagem"
    TripScreen.SETTINGS -> "Integrações"
    TripScreen.LIST -> "Gerenciar viagem"
}

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
    var localCapacityIncrementalBaseline by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val timelineListState = rememberLazyListState()
    var pendingCreateForPassengerId by remember { mutableStateOf("") }
    var addPassengerResumePassengerId by remember { mutableStateOf<String?>(null) }
    var addPassengerResumeTripId by remember { mutableStateOf<String?>(null) }
    var addPassengerResumeToken by remember { mutableStateOf(0) }
    val legacyTimelineDeepLink0468 =
        startCreating || openReservationRequests || initialBookingId != null || initialPendingOnly || initialTripId != null
    val initialScreen0396 = TripScreen.AUTO_SYNC
    var screen by rememberSaveable { mutableStateOf(initialScreen0396) }
    var parentRootScreen0396 by rememberSaveable { mutableStateOf(TripScreen.AUTO_SYNC) }
    var passengerSubscreenOpen0396 by rememberSaveable { mutableStateOf(false) }
    var passengerExternalBackToken0396 by remember { mutableStateOf(0) }
    var timelineUiCommand0396 by remember { mutableStateOf<AgendaTimelineCommand0396?>(null) }
    var timelineUiCommandToken0396 by remember { mutableStateOf(0) }
    var selectedId by remember { mutableStateOf(initialTripId) }
    var focusedTripId by remember { mutableStateOf(initialTripId.takeIf { openReservationRequests }) }
    var focusedRemoteTripId by remember { mutableStateOf(initialRemoteTripId) }
    var focusedBookingId by remember { mutableStateOf(initialBookingId) }
    var reservationPendingOnly by remember { mutableStateOf(initialPendingOnly) }
    var message by remember {
        mutableStateOf<String?>(
            if (legacyTimelineDeepLink0468) {
                "A Timeline local foi retirada da operação. Use BlaBlaCar para coletar e a Área Administrativa para operar esta viagem."
            } else {
                null
            },
        )
    }
    val notificationProjection0416 by DriverNotificationProjection0416.state.collectAsState()
    val activeNotificationTenant0416 = RotaCertaTenantRegistry(activity).activeScope().tenantId
    val driverNotifications = if (notificationProjection0416.tenantId == activeNotificationTenant0416) {
        notificationProjection0416.notifications
    } else {
        emptyList()
    }
    val driverUnreadCount = if (notificationProjection0416.tenantId == activeNotificationTenant0416) {
        notificationProjection0416.unreadCount.coerceAtLeast(0)
    } else {
        0
    }
    val shareScope = rememberCoroutineScope()

    val refreshDriverNotifications: suspend () -> Unit = {
        DriverNotificationProjection0416.refresh(activity)
        Unit
    }

    androidx.compose.runtime.SideEffect {
        AgendaTrace.markContentMounted(activity, loading = false)
        if (firstCompositionEnded.compareAndSet(false, true)) {
            AgendaTrace.operationEnd(activity, firstCompositionOperation, result = "content_mounted")
        }
    }

    androidx.compose.runtime.LaunchedEffect(
        settingsLoaded,
        appSettings.rotaCertaSeatAllocation,
        appSettings.rotaCertaSeatAllocationVersion,
    ) {
        if (!settingsLoaded) {
            AgendaTrace.event(
                activity,
                "INVENTORY_LOCAL_SETTINGS_WAITING",
                "source=awaiting_local_settings",
                traceId,
            )
            return@LaunchedEffect
        }
        val fanOut = AgendaBackgroundSync0392.reconcileTenantSeatAllocation0395(
            context = activity,
            rotaCertaSeatAllocation = appSettings.rotaCertaSeatAllocation,
            seatAllocationVersion = appSettings.rotaCertaSeatAllocationVersion,
        )
        trips = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { store.trips() }
        UnifiedDebugEventStore.record(
            "OPERATIONAL_INVENTORY_RECONCILED",
            activity.packageName,
            "rotaCertaSeatAllocation=" + appSettings.rotaCertaSeatAllocation +
                " configVersion=" + appSettings.rotaCertaSeatAllocationVersion +
                " localCanonicalUpdated=" + fanOut.localCanonicalUpdated +
                " localPublicationQueued=" + fanOut.localPublicationQueued +
                " externalPublicationQueued=" + fanOut.externalPublicationQueued +
                " externalRetryPending=" + fanOut.externalRetryPending +
                " fullSyncRequested=false legacyVehicleCapacityIgnored=true",
        )
        AgendaTrace.event(
            activity,
            "INVENTORY_LOCAL_SETTINGS_RECEIVED",
            "source=rota_certa_allocation value=" + appSettings.rotaCertaSeatAllocation +
                " configVersion=" + appSettings.rotaCertaSeatAllocationVersion,
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

    androidx.compose.runtime.LaunchedEffect(screen, trips.size, bookings.size, settingsLoaded, appSettings.rotaCertaSeatAllocation) {
        AgendaTrace.event(
            activity,
            "AGENDA_RENDER_STATE",
            "loading=false empty=${trips.isEmpty() && bookings.isEmpty()} items=${trips.size} capacityPresent=${settingsLoaded && appSettings.rotaCertaSeatAllocation in 0..999} settingsLoaded=$settingsLoaded syncRunning=false screen=${screen.name.lowercase()}",
            traceId,
        )
    }
    val refresh = {
        trips = store.trips()
        bookings = store.bookings()
        TripWidgetProvider.updateAll(activity)
    }
    // Records durable per-trip mutations only; delivery belongs to AgendaBackgroundSync0392.
    val tripMutationCoordinator = remember(activity, store) { TripMutationCoordinator0387(activity, store) }
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
                shareScope.launch { refreshDriverNotifications() }
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    val requestTimelineVisualReload = {
        refresh()
        message = null
        UnifiedDebugEventStore.record(
            "AGENDA_TIMELINE_VISUAL_RELOAD_0398",
            activity.packageName,
            "networkSync=false automaticSyncOnly=true",
        )
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        refreshDriverNotifications()
    }

    androidx.compose.runtime.LaunchedEffect(settingsLoaded, trips, bookings, appSettings.rotaCertaSeatAllocation) {
        if (!settingsLoaded) return@LaunchedEffect
        val current = trips
            .filter(Trip::isCanonicalLocalPublishSource)
            .associate { trip ->
                trip.id to PublicAgendaAutoSync0300.localCapacitySnapshotRevision(
                    trip = trip,
                    bookings = bookings.filter { it.tripId == trip.id },
                    rotaCertaSeatAllocation = trip.rotaCertaSeatAllocation?.takeIf { it in 0..999 } ?: 0,
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
                        rotaCertaSeatAllocation = trip.rotaCertaSeatAllocation?.takeIf { it in 0..999 } ?: 0,
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
                        configuredRotaCertaSeatAllocation = failureTrip?.rotaCertaSeatAllocation?.takeIf { it in 0..999 } ?: 0,
                    )
                    AgendaBackgroundSync0392.enqueueImmediate(activity, "trip_mutation")
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

    val sendTimelineCommand0396: (AgendaTimelineCommand0396) -> Unit = { command ->
        timelineUiCommand0396 = command
        timelineUiCommandToken0396 += 1
    }
    val openNotifications0396 = {
        if (screen != TripScreen.NOTIFICATIONS) {
            if (screen.isAgendaRoot0396()) {
                parentRootScreen0396 = screen
            }
            passengerSubscreenOpen0396 = false
            screen = TripScreen.NOTIFICATIONS
        }
        shareScope.launch { refreshDriverNotifications() }
        Unit
    }
    val headerActions0396 = when (screen) {
        TripScreen.TIMELINE -> listOf(
            AgendaHeaderAction0396("Nova viagem") {
                pendingCreateForPassengerId = ""
                parentRootScreen0396 = TripScreen.TIMELINE
                screen = TripScreen.CREATE
            },
            AgendaHeaderAction0396("Adicionar passageiro") {
                sendTimelineCommand0396(AgendaTimelineCommand0396.ADD_PASSENGER)
            },
            AgendaHeaderAction0396("Vagas extra") {
                parentRootScreen0396 = TripScreen.TIMELINE
                screen = TripScreen.EXTRA_SEATS
            },
            AgendaHeaderAction0396("Próximas / arquivadas") {
                sendTimelineCommand0396(AgendaTimelineCommand0396.TOGGLE_ARCHIVED)
            },
            AgendaHeaderAction0396("Baixar Timeline") {
                sendTimelineCommand0396(AgendaTimelineCommand0396.DOWNLOAD_TIMELINE)
            },
            AgendaHeaderAction0396("Fixar atalho") {
                val requested = TripShortcutInstaller.requestPinnedCreateShortcut(activity)
                message = if (requested) "Pedido de atalho enviado ao Android." else "O launcher não permite fixar atalhos automaticamente."
            },
        )
        else -> emptyList()
    }
    val passengerSubscreenActive0396 = screen == TripScreen.PASSENGERS && passengerSubscreenOpen0396
    val headerIsRoot0396 = screen.isAgendaRoot0396() && !passengerSubscreenActive0396
    val headerLabel0396 = if (passengerSubscreenActive0396) {
        "Histórico do passageiro"
    } else {
        screen.agendaHeaderLabel0396()
    }
    val currentRootScreen0396 = if (screen.isAgendaRoot0396()) screen else parentRootScreen0396

    AgendaModuleDrawer0396(
        currentSection = currentRootScreen0396.agendaRootSection0396(),
        onSelect = { section ->
            when (section) {
                AgendaRootSection0396.ALL_TRIPS -> {
                    parentRootScreen0396 = TripScreen.AUTO_SYNC
                    passengerSubscreenOpen0396 = false
                    screen = TripScreen.AUTO_SYNC
                }
                AgendaRootSection0396.ASSISTANT -> {
                    parentRootScreen0396 = TripScreen.ASSISTANT
                    passengerSubscreenOpen0396 = false
                    screen = TripScreen.ASSISTANT
                }
                AgendaRootSection0396.AUTOMATIC_SYNC -> {
                    parentRootScreen0396 = currentRootScreen0396
                    passengerSubscreenOpen0396 = false
                    screen = TripScreen.AUTO_SYNC
                }
                AgendaRootSection0396.PUBLIC_SEARCH -> {
                    parentRootScreen0396 = TripScreen.PUBLIC_SEARCH
                    passengerSubscreenOpen0396 = false
                    screen = TripScreen.PUBLIC_SEARCH
                }
                AgendaRootSection0396.PASSENGERS -> {
                    parentRootScreen0396 = TripScreen.PASSENGERS
                    passengerSubscreenOpen0396 = false
                    screen = TripScreen.PASSENGERS
                }
                AgendaRootSection0396.INTEGRATIONS -> {
                    parentRootScreen0396 = currentRootScreen0396
                    passengerSubscreenOpen0396 = false
                    screen = TripScreen.SETTINGS
                }
                AgendaRootSection0396.APP_SETTINGS -> {
                    parentRootScreen0396 = TripScreen.APP_SETTINGS
                    passengerSubscreenOpen0396 = false
                    screen = TripScreen.APP_SETTINGS
                }
            }
        },
    ) { openDrawer0396 ->
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                AgendaModuleHeader0396(
                    sectionLabel = headerLabel0396,
                    root = headerIsRoot0396,
                    onNavigationClick = {
                        when {
                            passengerSubscreenActive0396 -> passengerExternalBackToken0396 += 1
                            screen.isAgendaRoot0396() -> openDrawer0396()
                            else -> screen = parentRootScreen0396
                        }
                    },
                    overflowActions = headerActions0396,
                    notificationUnreadCount = driverUnreadCount,
                    onNotificationsClick = openNotifications0396,
                )
            },
        ) { padding ->
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
            message?.takeIf {
                screen != TripScreen.TIMELINE &&
                    screen != TripScreen.ASSISTANT &&
                    screen != TripScreen.NOTIFICATIONS
            }?.let {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(it, modifier = Modifier.padding(12.dp))
                }
            }
            when (screen) {
                TripScreen.CREATE -> TripEditor(
                    defaultOrigin = appSettings.tripDepartureAddress,
                    defaultRotaCertaSeatAllocation = 0,
                    onCancel = {
                        pendingCreateForPassengerId = ""
                        screen = parentRootScreen0396
                    },
                    onSave = { trip ->
                        store.saveTrip(trip)
                        refresh()
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
                        screen = parentRootScreen0396
                    },
                )
                TripScreen.TIMELINE -> TimelineRefreshGestureSurface0388(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    refreshing = false,
                    canRefreshAtGestureStart = { !timelineListState.canScrollBackward },
                    onRefresh = requestTimelineVisualReload,
                    onPointerDown = { position, canRefreshAtStart, refreshRunningAtStart ->
                        UnifiedDebugEventStore.record(
                            "AGENDA_PULL_GESTURE_DOWN_0390",
                            activity.packageName,
                            "xPx=${position.x.toInt()} yPx=${position.y.toInt()} canRefreshAtStart=$canRefreshAtStart " +
                                "refreshRunningAtStart=$refreshRunningAtStart firstVisibleItemIndex=${timelineListState.firstVisibleItemIndex} " +
                                "firstVisibleItemScrollOffset=${timelineListState.firstVisibleItemScrollOffset} canScrollBackward=${timelineListState.canScrollBackward}",
                        )
                    },
                    onPointerEnd = { position, accepted ->
                        UnifiedDebugEventStore.record(
                            "AGENDA_PULL_GESTURE_END_0390",
                            activity.packageName,
                            "xPx=${position.x.toInt()} yPx=${position.y.toInt()} accepted=$accepted " +
                                "firstVisibleItemIndex=${timelineListState.firstVisibleItemIndex} " +
                                "firstVisibleItemScrollOffset=${timelineListState.firstVisibleItemScrollOffset} canScrollBackward=${timelineListState.canScrollBackward}",
                        )
                    },
                    onDecision = { decision ->
                        UnifiedDebugEventStore.record(
                            "AGENDA_PULL_GESTURE_DECISION_0390",
                            activity.packageName,
                            "outcome=${decision.outcome.name} accepted=${decision.accepted} dyPx=${decision.deltaY.toInt()} dxPx=${decision.deltaX.toInt()} " +
                                "listAtTop=${decision.eligibleAtStart} blockedByRefresh=${decision.refreshingAtStart}",
                        )
                        if (
                            decision.accepted ||
                            decision.outcome == AgendaPullRefreshOutcome0388.BLOCKED_REFRESH_RUNNING
                        ) {
                            UnifiedDebugEventStore.record(
                                "AGENDA_PULL_GESTURE_RECOGNIZED",
                                activity.packageName,
                                "accepted=${decision.accepted} dyPx=${decision.deltaY.toInt()} dxPx=${decision.deltaX.toInt()} " +
                                    "listAtTop=${decision.eligibleAtStart} blockedByRefresh=${decision.refreshingAtStart}",
                            )
                        }
                    },
                ) {
                    TripTimelineScreen(
                    trips = trips,
                    bookings = bookings,
                    store = store,
                    onChanged = { text -> refresh(); message = text },
                    onCreateTripForPassenger = { passengerId ->
                        pendingCreateForPassengerId = passengerId
                        parentRootScreen0396 = TripScreen.TIMELINE
                        screen = TripScreen.CREATE
                    },
                    addPassengerResumeToken = addPassengerResumeToken,
                    addPassengerResumePassengerId = addPassengerResumePassengerId,
                    addPassengerResumeTripId = addPassengerResumeTripId,
                    onManageLocal = { tripId ->
                        selectedId = tripId
                        parentRootScreen0396 = TripScreen.TIMELINE
                        screen = TripScreen.LIST
                    },
                    uiCommand0396 = timelineUiCommand0396,
                    uiCommandToken0396 = timelineUiCommandToken0396,
                    focusedTripId = focusedTripId
                        ?: focusedRemoteTripId?.let { remote -> trips.firstOrNull { it.remoteId == remote }?.id },
                    focusedBookingId = focusedBookingId,
                    reservationPendingOnly = reservationPendingOnly,
                    listState = timelineListState,
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
                }
                TripScreen.ASSISTANT -> RotaCertaAssistantPanel0410(
                    trips = trips,
                    bookings = bookings,
                    store = store,
                    onChanged = { text -> refresh(); message = text },
                )
                TripScreen.NOTIFICATIONS -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Central de Notificações", style = MaterialTheme.typography.titleMedium)
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
                                            parentRootScreen0396 = TripScreen.AUTO_SYNC
                                            screen = TripScreen.AUTO_SYNC
                                            message = "Abra a Área Administrativa para operar a viagem desta notificação."
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
                TripScreen.PUBLIC_SEARCH -> AgendaPublicSearchRoot0396(
                    trips = trips,
                    onChanged = { text -> message = text },
                )
                TripScreen.PASSENGERS -> PassengerAdminScreen(
                    store = store,
                    onBack = { screen = TripScreen.AUTO_SYNC },
                    onChanged = { text -> refresh(); message = text },
                    showHeader = false,
                    externalBackToken = passengerExternalBackToken0396,
                    onHierarchyChanged = { passengerSubscreenOpen0396 = it },
                )
                TripScreen.AUTO_SYNC -> AgendaAutomaticSyncScreen0397(
                    trips = trips,
                    store = store,
                    onChanged = { text -> message = text },
                )
                TripScreen.APP_SETTINGS -> AgendaAppSettingsScreen0416(
                    initial = store.onlineSettings(),
                    onSave = { saved ->
                        store.saveOnlineSettings(saved)
                        message = "Configurações salvas."
                    },
                )
                TripScreen.EXTRA_SEATS -> TripExtraSeatsScreen0416(
                    activity = activity,
                    store = store,
                    trips = trips,
                    onChanged = { text ->
                        refresh()
                        message = text
                    },
                )
                TripScreen.SETTINGS -> OnlineSettingsEditor(
                    initial = store.onlineSettings(),
                    onSave = { saved ->
                        store.saveOnlineSettings(saved)
                        screen = parentRootScreen0396
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
                    onCancel = { screen = parentRootScreen0396 },
                )
                TripScreen.LIST -> {
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
                                onChanged = { text -> refresh(); message = text },
                                onRequestBlaBlaSync = {},
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun TripExtraSeatsScreen0416(
    activity: ComponentActivity,
    store: TripStore,
    trips: List<Trip>,
    onChanged: (String) -> Unit,
) {
    val mutationCoordinator = remember(activity, store) { TripMutationCoordinator0387(activity, store) }
    val scope = rememberCoroutineScope()
    val candidates = trips
        .filterNot { it.deleted || it.status == TripStatus.CANCELLED || it.status == TripStatus.COMPLETED }
        .sortedBy { it.departureAtMillis }

    Text("Vagas extra", style = MaterialTheme.typography.titleLarge)
    Text(
        "Cota manual por viagem. Alterar uma viagem não modifica as demais.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (candidates.isEmpty()) {
        Text("Nenhuma viagem ativa disponível.", style = MaterialTheme.typography.bodySmall)
    }
    candidates.forEach { trip ->
        var value by remember(trip.id, trip.rotaCertaSeatAllocation) {
            mutableStateOf((trip.rotaCertaSeatAllocation ?: 0).coerceIn(0, 999).toString())
        }
        var localError by remember(trip.id) { mutableStateOf<String?>(null) }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(trip.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                        .format(Instant.ofEpochMilli(trip.departureAtMillis).atZone(ZoneId.systemDefault())),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.filter(Char::isDigit).take(3) },
                    label = { Text("Vagas extra") },
                    supportingText = { Text("Única cota manual desta viagem. O valor 0 é válido.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        val parsed = value.toIntOrNull()
                        if (parsed == null || parsed !in 0..999) {
                            localError = "Informe um valor entre 0 e 999."
                            return@Button
                        }
                        localError = null
                        scope.launch {
                            val bookingsForTrip = store.bookingsFor(trip.id)
                            val allocated = trip.copy(
                                rotaCertaSeatAllocation = parsed,
                                updatedAtMillis = System.currentTimeMillis(),
                            )
                            val saved = store.saveTrip(
                                allocated.copy(
                                    capacity = operationalInventoryCapacity(allocated, bookingsForTrip),
                                ),
                            )
                            if (resolvedTripRecordOrigin(saved) == TripRecordOrigin.EXTERNAL_BACKING) {
                                saved.externalSnapshot?.let { external ->
                                    mutationCoordinator.recordExternalManualMutation(
                                        sourceTrip = external,
                                        configuredRotaCertaSeatAllocation = parsed,
                                        mutationType = "ROTA_CERTA_EXTRA_SEATS_CHANGED",
                                    )
                                }
                            } else {
                                mutationCoordinator.recordLocalMutation(
                                    canonicalTripId = saved.id,
                                    mutationType = "ROTA_CERTA_EXTRA_SEATS_CHANGED",
                                    source = "EXTRA_SEATS_SCREEN",
                                    configuredRotaCertaSeatAllocation = parsed,
                                )
                            }
                            AgendaBackgroundSync0392.enqueueImmediate(activity, "trip_mutation")
                            onChanged("Vagas extra atualizadas para esta viagem.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Salvar nesta viagem")
                }
                localError?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun AgendaPublicSearchRoot0396(
    trips: List<Trip>,
    onChanged: (String) -> Unit,
) {
    val context = LocalContext.current
    val publicSearchStore = remember(context) { BlaBlaPublicSearchStore(context) }
    var response by remember(context) { mutableStateOf(publicSearchStore.lastResponse()) }

    BlaBlaPublicSearchPanel(
        trips = trips,
        currentResponse = response,
        onResult = { response = it },
        onChanged = onChanged,
        showTitle = false,
        showCollectionActions = false,
    )
    response?.let { result ->
        Text(
            "Resultado desta consulta pública",
            style = MaterialTheme.typography.titleMedium,
        )
        if (result.cards.isEmpty()) {
            Text("Nenhum card público encontrado nesta consulta.", style = MaterialTheme.typography.bodySmall)
        } else {
            result.cards
                .sortedBy(::publicSearchCardDepartureSortMillis)
                .forEach { card ->
                    BlaBlaPublicTimelineCard(
                        card = card,
                        response = result,
                    )
                }
        }
        Text(
            "Esta consulta possui Timeline própria e não é misturada à Timeline operacional.",
            style = MaterialTheme.typography.bodySmall,
        )
        BlaBlaAuditableCollectionActions(
            snapshot = BlaBlaAuditableCollectionBuilder.build(context, result),
            onChanged = onChanged,
        )
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
    val mutationCoordinator = remember(activity, store) { TripMutationCoordinator0387(activity, store) }
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
                                runCatching {
                                    mutationCoordinator.recordLocalMutation(
                                        canonicalTripId = next.id,
                                        mutationType = "PUBLIC_BOOKING_TOGGLE",
                                        source = "TIMELINE_CARD",
                                    )
                                    AgendaBackgroundSync0392.enqueueImmediate(activity, "trip_mutation")
                                }
                                    .onSuccess { onChanged(if (next.publicBookingEnabled) "Reservas pelo link ativadas para esta viagem." else "Reservas pelo link desativadas para esta viagem.") }
                                    .onFailure { onChanged("Estado salvo no Rota Certa; o delta desta viagem ficou pendente: ${it.message}") }
                            }
                        } else {
                            onChanged(if (next.publicBookingEnabled) "Reservas pelo link ativadas localmente. A sincronização automática publicará a alteração quando a integração online estiver disponível." else "Reservas pelo link desativadas.")
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
                if (!settings.configured) {
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
