#!/usr/bin/env python3
from pathlib import Path
import sys

SOURCE = Path(sys.argv[1]).resolve()
TRIPS = SOURCE / "app/src/main/java/br/com/mapeiaia/rotacerta/trips"
TIMELINE_UI = TRIPS / "TripTimelineUi.kt"
QUICK_UI = TRIPS / "TripQuickPassengerUi.kt"
COLLECTOR_UI = TRIPS / "TripBlaBlaCollectorUi.kt"
ACTIVITY = TRIPS / "TripsActivity.kt"


def once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one marker, got {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


for path in (TIMELINE_UI, QUICK_UI, COLLECTOR_UI, ACTIVITY):
    if not path.is_file():
        raise SystemExit(f"missing materialized Stage47 source: {path}")

# Diagnostics live in the root app package, while Timeline/collector are in .trips.
for path in (TIMELINE_UI, COLLECTOR_UI):
    once(
        path,
        "package br.com.mapeiaia.rotacerta.trips\n\n",
        "package br.com.mapeiaia.rotacerta.trips\n\nimport br.com.mapeiaia.rotacerta.UnifiedDebugEventStore\n",
        f"UnifiedDebugEventStore import in {path.name}",
    )

# Quick passenger: every successful physical occupancy change emits an explicit
# request for the same authenticated multi-account sync used by the manual button.
once(
    QUICK_UI,
    '''fun QuickPassengerPanel(trip: Trip, store: TripStore, onChanged: (String) -> Unit) {\n''',
    '''fun QuickPassengerPanel(\n    trip: Trip,\n    store: TripStore,\n    onChanged: (String) -> Unit,\n    onBlaBlaSyncRequested: (() -> Unit)? = null,\n) {\n''',
    "quick passenger autosync callback",
)
once(
    QUICK_UI,
'''                    onChanged("Passageiro particular adicionado. Ocupação recalculada.")
''',
'''                    onChanged("Passageiro particular adicionado. Ocupação recalculada.")
                    onBlaBlaSyncRequested?.invoke()
''',
    "autosync after passenger save",
)
once(
    QUICK_UI,
'''                                .onSuccess { onChanged("Passageiro removido e vaga liberada.") }
''',
'''                                .onSuccess {
                                    onChanged("Passageiro removido e vaga liberada.")
                                    onBlaBlaSyncRequested?.invoke()
                                }
''',
    "autosync after passenger cancellation",
)

# TripsActivity owns a monotonic token so an occupancy change from either the
# normal Agenda management screen or the Timeline can immediately request a new
# authenticated account synchronization without duplicating orchestration code.
once(
    ACTIVITY,
'''    var trips by remember { mutableStateOf(store.trips()) }
    var bookings by remember { mutableStateOf(store.bookings()) }
    var screen by remember { mutableStateOf(if (startCreating) TripScreen.CREATE else TripScreen.LIST) }
''',
'''    var trips by remember { mutableStateOf(store.trips()) }
    var bookings by remember { mutableStateOf(store.bookings()) }
    var autoBlaBlaSyncToken by remember { mutableStateOf(0) }
    var screen by remember { mutableStateOf(if (startCreating) TripScreen.CREATE else TripScreen.LIST) }
''',
    "app autosync token state",
)
once(
    ACTIVITY,
'''                TripScreen.TIMELINE -> TripTimelineScreen(
                    trips = trips,
                    bookings = bookings,
                    store = store,
                    onChanged = { text -> refresh(); message = text },
                    onManageLocal = { tripId ->
                        selectedId = tripId
                        screen = TripScreen.LIST
                    },
                    onBack = { screen = TripScreen.LIST },
                )
''',
'''                TripScreen.TIMELINE -> TripTimelineScreen(
                    trips = trips,
                    bookings = bookings,
                    store = store,
                    onChanged = { text -> refresh(); message = text },
                    autoSyncToken = autoBlaBlaSyncToken,
                    onRequestBlaBlaSync = { autoBlaBlaSyncToken++ },
                    onManageLocal = { tripId ->
                        selectedId = tripId
                        screen = TripScreen.LIST
                    },
                    onBack = { screen = TripScreen.LIST },
                )
''',
    "timeline autosync wiring",
)
once(
    ACTIVITY,
'''                            TripCard(
                                activity = activity,
                                store = store,
                                trip = trip,
                                expanded = selectedId == trip.id,
                                onToggle = { selectedId = if (selectedId == trip.id) null else trip.id },
                                onChanged = { text -> refresh(); message = text },
                            )
''',
'''                            TripCard(
                                activity = activity,
                                store = store,
                                trip = trip,
                                expanded = selectedId == trip.id,
                                onToggle = { selectedId = if (selectedId == trip.id) null else trip.id },
                                onChanged = { text -> refresh(); message = text },
                                onRequestBlaBlaSync = {
                                    autoBlaBlaSyncToken++
                                    screen = TripScreen.TIMELINE
                                },
                            )
''',
    "agenda trip card autosync wiring",
)
once(
    ACTIVITY,
'''private fun TripCard(
    activity: ComponentActivity,
    store: TripStore,
    trip: Trip,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChanged: (String) -> Unit,
) {
''',
'''private fun TripCard(
    activity: ComponentActivity,
    store: TripStore,
    trip: Trip,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChanged: (String) -> Unit,
    onRequestBlaBlaSync: () -> Unit,
) {
''',
    "trip card autosync callback",
)
once(
    ACTIVITY,
'''                    QuickPassengerPanel(trip, store, onChanged)
''',
'''                    QuickPassengerPanel(trip, store, onChanged, onRequestBlaBlaSync)
''',
    "agenda quick passenger autosync call",
)

# Timeline: the outer physical card stays clickable, while a child TextButton is
# a full-width, explicit WhatsApp action. Compose child click handling consumes
# the passenger tap instead of falling through to the trip card action.
once(
    TIMELINE_UI,
'''    store: TripStore,
    onChanged: (String) -> Unit,
    onBack: () -> Unit,
    onManageLocal: (String) -> Unit,
) {
''',
'''    store: TripStore,
    onChanged: (String) -> Unit,
    autoSyncToken: Int,
    onRequestBlaBlaSync: () -> Unit,
    onBack: () -> Unit,
    onManageLocal: (String) -> Unit,
) {
''',
    "timeline autosync parameters",
)
once(
    TIMELINE_UI,
'''    var showArchived by remember { mutableStateOf(false) }
    var showSync by remember { mutableStateOf(false) }
''',
'''    var showArchived by remember { mutableStateOf(false) }
    var showSync by remember { mutableStateOf(false) }

    LaunchedEffect(autoSyncToken) {
        if (autoSyncToken > 0) showSync = true
    }
''',
    "show sync panel for automatic request",
)
once(
    TIMELINE_UI,
'''    if (showSync) {
        BlaBlaCollectorPanel(trips, collectorStore, collectorResponse, { collectorResponse = it }, onChanged)
    }
''',
'''    if (showSync) {
        BlaBlaCollectorPanel(
            trips = trips,
            stateStore = collectorStore,
            currentResponse = collectorResponse,
            onResult = { collectorResponse = it },
            onChanged = onChanged,
            autoSyncToken = autoSyncToken,
        )
    }
''',
    "collector receives autosync token",
)
once(
    TIMELINE_UI,
'''        TimelineEntryCard(entry, trip, store, formatter, archived, onManageLocal, onChanged) {
''',
'''        TimelineEntryCard(entry, trip, store, formatter, archived, onManageLocal, onChanged, onRequestBlaBlaSync) {
''',
    "timeline card autosync callback",
)
once(
    TIMELINE_UI,
'''    onManageLocal: (String) -> Unit,
    onChanged: (String) -> Unit,
    onArchive: () -> Unit,
) {
''',
'''    onManageLocal: (String) -> Unit,
    onChanged: (String) -> Unit,
    onRequestBlaBlaSync: () -> Unit,
    onArchive: () -> Unit,
) {
''',
    "timeline entry autosync callback parameter",
)
once(
    TIMELINE_UI,
'''                        if (!passenger.phone.isNullOrBlank()) {
                            TextButton(onClick = { openWhatsApp(context, passenger.phone!!) }) { Text(label) }
                        } else {
''',
'''                        if (!passenger.phone.isNullOrBlank()) {
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
''',
    "explicit passenger whatsapp child action",
)
once(
    TIMELINE_UI,
'''            if (trip != null && quickOpen) QuickPassengerPanel(trip, store, onChanged)
''',
'''            if (trip != null && quickOpen) QuickPassengerPanel(trip, store, onChanged, onRequestBlaBlaSync)
''',
    "timeline quick passenger autosync call",
)

# Collector: consume each auto-sync token once. If another occupancy change lands
# while a sync is already running, it is not lost; the effect retries the latest
# unhandled token immediately after the current sync finishes.
once(
    COLLECTOR_UI,
'''    onResult: (BlaBlaCollectorMonthResponse) -> Unit,
    onChanged: (String) -> Unit,
) {
''',
'''    onResult: (BlaBlaCollectorMonthResponse) -> Unit,
    onChanged: (String) -> Unit,
    autoSyncToken: Int = 0,
) {
''',
    "collector autosync token parameter",
)
once(
    COLLECTOR_UI,
'''    var syncCursor by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
''',
'''    var syncCursor by remember { mutableIntStateOf(0) }
    var handledAutoSyncToken by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
''',
    "collector handled autosync token",
)
once(
    COLLECTOR_UI,
'''    @Suppress("UNUSED_VARIABLE") val refreshKey = revision
    val accounts = registry.list()

    // Discard snapshots from the old hard-coded two-account candidate. The
''',
'''    @Suppress("UNUSED_VARIABLE") val refreshKey = revision
    val accounts = registry.list()

    LaunchedEffect(autoSyncToken, syncing, accounts.size) {
        if (autoSyncToken <= handledAutoSyncToken || syncing) return@LaunchedEffect
        if (accounts.isEmpty()) {
            message = "Passageiro salvo • conecte uma conta BlaBlaCar para sincronizar."
            onChanged(message.orEmpty())
            UnifiedDebugEventStore.record(
                "AUTO_SYNC_PENDING",
                context.packageName,
                "reason=occupancy_change accounts=0 token=$autoSyncToken",
            )
            return@LaunchedEffect
        }
        handledAutoSyncToken = autoSyncToken
        syncQueue = accounts.map { it.id }
        syncCursor = 0
        syncing = true
        message = "Sincronizando BlaBlaCar após alteração de passageiro…"
        onChanged(message.orEmpty())
        UnifiedDebugEventStore.record(
            "AUTO_SYNC_REQUESTED",
            context.packageName,
            "reason=occupancy_change accounts=${accounts.size} token=$autoSyncToken",
        )
    }

    // Discard snapshots from the old hard-coded two-account candidate. The
''',
    "automatic authenticated account synchronization",
)

# Static guards: no BlaBla seat +/- writer is claimed here. This patch only
# triggers a fresh authenticated read sync after local physical occupancy changes.
for path, markers in {
    QUICK_UI: ["onBlaBlaSyncRequested?.invoke()", "onBlaBlaSyncRequested: (() -> Unit)? = null"],
    TIMELINE_UI: ["PASSENGER_WHATSAPP_OPEN", 'Text("💬 $label")', "autoSyncToken = autoSyncToken", "import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore"],
    COLLECTOR_UI: ["AUTO_SYNC_REQUESTED", "AUTO_SYNC_PENDING", "handledAutoSyncToken", "import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore"],
    ACTIVITY: ["autoBlaBlaSyncToken++", "onRequestBlaBlaSync"],
}.items():
    text = path.read_text(encoding="utf-8")
    for marker in markers:
        if marker not in text:
            raise SystemExit(f"passenger autosync marker missing {marker!r} in {path.name}")

print(
    "stage47_r4_step7_passenger_autosync=PASS "
    "passenger_whatsapp_explicit_child=true auto_account_sync_after_save=true "
    "auto_account_sync_after_cancel=true queued_if_sync_busy=true "
    "seat_write_not_claimed=true farol_touched=false"
)
