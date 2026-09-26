#!/usr/bin/env python3
from pathlib import Path
import sys

SOURCE = Path(sys.argv[1]).resolve()
TRIPS = SOURCE / "app/src/main/java/br/com/mapeiaia/rotacerta/trips"
TIMELINE = TRIPS / "TripTimelineUi.kt"
COLLECTOR_UI = TRIPS / "TripBlaBlaCollectorUi.kt"

for path in (TIMELINE, COLLECTOR_UI):
    if not path.is_file():
        raise SystemExit(f"missing materialized Stage47 source: {path}")


def once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one marker, got {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


# Timeline uses the SAME saved Home coordinate/radius configured by the existing
# Definir flow. It does not create a Base/Casa subsystem and it does not infer a
# physical direction from city text.
once(
    TIMELINE,
    "package br.com.mapeiaia.rotacerta.trips\n\n",
    "package br.com.mapeiaia.rotacerta.trips\n\n"
    "import br.com.mapeiaia.rotacerta.AppSettings\n"
    "import br.com.mapeiaia.rotacerta.Coordinate\n"
    "import br.com.mapeiaia.rotacerta.GeoDistance\n"
    "import br.com.mapeiaia.rotacerta.SettingsRepository\n"
    "import androidx.compose.runtime.collectAsState\n",
    "saved base imports",
)

# Keep a targeted profile UUID next to the existing monotonic auto-sync token.
# The UUID comes from the selected Timeline entry; names remain display-only.
once(
    TIMELINE,
'''    var showArchived by remember { mutableStateOf(false) }
    var showSync by remember { mutableStateOf(false) }
''',
'''    var showArchived by remember { mutableStateOf(false) }
    var showSync by remember { mutableStateOf(false) }
    var autoSyncProfileUuid by remember { mutableStateOf<String?>(null) }
    val settingsRepository = remember(context) { SettingsRepository(context) }
    val appSettings by settingsRepository.settings.collectAsState(initial = AppSettings())
''',
    "timeline saved base and target profile state",
)

# Target the authenticated account that owns the selected physical publication.
once(
    TIMELINE,
'''            autoSyncToken = autoSyncToken,
        )
''',
'''            autoSyncToken = autoSyncToken,
            autoSyncProfileUuid = autoSyncProfileUuid,
        )
''',
    "collector targeted profile argument",
)

# Header-level, compact add flow. Selecting one physical trip/profile pre-fills
# profile, route, date and time; the already-existing QuickPassengerPanel keeps
# name/WhatsApp/boarding/dropoff/quantity and the central overbooking engine.
anchor = '''    if (entries.isEmpty()) {
'''
insert = '''    GlobalQuickPassengerPanel(
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
'''
once(TIMELINE, anchor, insert, "global quick passenger panel call")

# Pass the saved coordinate/radius and a targeted sync callback into each card.
old_call = '''        TimelineEntryCard(entry, trip, store, formatter, archived, onManageLocal, onChanged, onRequestBlaBlaSync) {
'''
new_call = '''        TimelineEntryCard(
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
'''
once(TIMELINE, old_call, new_call, "timeline card saved base wiring")

once(
    TIMELINE,
'''    onChanged: (String) -> Unit,
    onRequestBlaBlaSync: () -> Unit,
    onArchive: () -> Unit,
) {
''',
'''    onChanged: (String) -> Unit,
    onRequestBlaBlaSync: (String?) -> Unit,
    homeCoordinate: Coordinate?,
    homeRadiusKm: Double,
    onArchive: () -> Unit,
) {
''',
    "timeline card targeted sync and base params",
)

# Prefix only when coordinate evidence exists. Unknown stays visually unclaimed;
# neutral is shown only when both trip stop coordinates exist and neither side is
# inside the saved base radius (or both are inside it).
once(
    TIMELINE,
'''            Text("$date — ${entry.origin} → ${entry.destination} $status", style = MaterialTheme.typography.titleSmall)
''',
'''            val baseDirection = timelineBaseDirection(trip, homeCoordinate, homeRadiusKm)
            val directionPrefix = baseDirection?.let { "$it " }.orEmpty()
            Text("$directionPrefix$date — ${entry.origin} → ${entry.destination} $status", style = MaterialTheme.typography.titleSmall)
''',
    "visible saved-base direction",
)

# Existing per-card +Passenger now requests only the profile that owns that
# merged external publication when a canonical UUID is available.
once(
    TIMELINE,
'''            if (trip != null && quickOpen) QuickPassengerPanel(trip, store, onChanged, onRequestBlaBlaSync)
''',
'''            if (trip != null && quickOpen) {
                QuickPassengerPanel(trip, store, onChanged) {
                    onRequestBlaBlaSync(entry.profileId.takeIf(::looksCanonicalProfileUuid))
                }
            }
''',
    "per-card targeted passenger sync",
)

# Every detected passenger is visibly a named button. WhatsApp only activates
# when a real phone exists; missing phone remains missing and never gets a country
# code invented.
once(
    TIMELINE,
'''                        } else {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
''',
'''                        } else {
                            OutlinedButton(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("$label • telefone não exposto") }
                        }
''',
    "named passenger button without invented phone",
)

# Add helpers just before the existing status helper added by the passenger-card
# patch. No route/city hardcode, no country hardcode, no new Casa/Base model.
helper_anchor = '''private fun statusMark(entry: TripTimelineEntry): String = when {
'''
helpers = r'''@Composable
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
'''
once(TIMELINE, helper_anchor, helpers, "visible capacity helpers")

# Targeted authenticated read: if a profile UUID was selected, only the matching
# configured account is queued. If it cannot be resolved, fail closed instead of
# silently syncing another driver's account.
once(
    COLLECTOR_UI,
'''    autoSyncToken: Int = 0,
) {
''',
'''    autoSyncToken: Int = 0,
    autoSyncProfileUuid: String? = null,
) {
''',
    "collector target profile parameter",
)
once(
    COLLECTOR_UI,
'''    LaunchedEffect(autoSyncToken, syncing, accounts.size) {
''',
'''    LaunchedEffect(autoSyncToken, autoSyncProfileUuid, syncing, accounts.size) {
''',
    "collector targeted autosync effect key",
)
once(
    COLLECTOR_UI,
'''        handledAutoSyncToken = autoSyncToken
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
''',
'''        val requestedProfile = autoSyncProfileUuid?.trim()?.takeIf(String::isNotEmpty)
        val selectedAccounts = if (requestedProfile == null) {
            accounts
        } else {
            accounts.filter { account -> account.profileUuid?.equals(requestedProfile, ignoreCase = true) == true }
        }
        if (requestedProfile != null && selectedAccounts.isEmpty()) {
            message = "Vagas internas atualizadas • perfil BlaBlaCar selecionado ainda não está vinculado."
            onChanged(message.orEmpty())
            UnifiedDebugEventStore.record(
                "AUTO_SYNC_PENDING",
                context.packageName,
                "reason=target_profile_unresolved requestedProfile=true token=$autoSyncToken",
            )
            return@LaunchedEffect
        }
        handledAutoSyncToken = autoSyncToken
        syncQueue = selectedAccounts.map { it.id }
        syncCursor = 0
        syncing = true
        message = "Vagas internas atualizadas • conferindo BlaBlaCar…"
        onChanged(message.orEmpty())
        UnifiedDebugEventStore.record(
            "AUTO_SYNC_REQUESTED",
            context.packageName,
            "reason=occupancy_change accounts=${selectedAccounts.size} targeted=${requestedProfile != null} token=$autoSyncToken",
        )
''',
    "target only selected external profile",
)

# Static safety/product guards.
timeline_text = TIMELINE.read_text(encoding="utf-8")
collector_text = COLLECTOR_UI.read_text(encoding="utf-8")
for marker in (
    "+ Passageiro rápido",
    "Perfil/viagem:",
    "vagas Rota Certa:",
    "SettingsRepository(context)",
    "appSettings.homeCoordinate",
    "GeoDistance.meters",
    "🟢⬆️",
    "🔴⬇️",
    "telefone não exposto",
    "onTargetSync(entry.profileId.takeIf(::looksCanonicalProfileUuid))",
):
    if marker not in timeline_text:
        raise SystemExit(f"visible capacity marker missing from Timeline: {marker!r}")
for marker in (
    "autoSyncProfileUuid",
    "target_profile_unresolved",
    "selectedAccounts",
    "Vagas internas atualizadas • conferindo BlaBlaCar",
):
    if marker not in collector_text:
        raise SystemExit(f"targeted collector marker missing: {marker!r}")

for forbidden in (
    '"55$digits"',
    'Locale("pt", "BR")',
    'Brasil',
    '7371f028-9c55-4903-8444-308015823efd',
    '175a7068-50d8-40c3-a27a-214b9c6e0461',
):
    if forbidden in timeline_text + collector_text:
        raise SystemExit(f"global/multi-tenant guard failed: {forbidden}")

print(
    "stage47_r4_step7_visible_capacity_flow=PASS "
    "global_quick_passenger=true selected_profile_trip_prefill=true internal_capacity_immediate=true "
    "targeted_account_reread=true external_seat_write_claimed=false passenger_named_buttons=true "
    "whatsapp_only_with_real_phone=true saved_home_coordinate_direction=true missing_coordinates_unknown=true "
    "farol_touched=false base_module_created=false"
)
