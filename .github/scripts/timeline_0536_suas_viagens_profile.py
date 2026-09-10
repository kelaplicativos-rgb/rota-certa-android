from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TIMELINE = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimeline.kt"
UI = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt"
PASSENGER_UI = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt"
BUILD = ROOT / "app/build.gradle.kts"
TEST = ROOT / "app/src/test/java/br/com/mapeiaia/rotacerta/trips/TripTimelineProfile0536Test.kt"


def fail(message: str) -> None:
    raise SystemExit(message)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def replace_once(content: str, old: str, new: str, label: str) -> str:
    count = content.count(old)
    if count != 1:
        fail(f"{label}: expected exactly one baseline anchor, found {count}")
    return content.replace(old, new, 1)


build = read(BUILD)
if 'versionCode = 5827' not in build or 'versionName = "0.1.535"' not in build:
    fail("Unexpected version baseline; refusing to materialize 0.1.536")

passenger_ui = read(PASSENGER_UI)
if "Carregando passageiros" in passenger_ui:
    fail("Visible passenger loading state is present in baseline; this task must not hide a structural regression")
if 'contentDescription = "GPS embarque ${passenger.name}"' not in passenger_ui:
    fail("Passenger pickup GPS baseline not found")
if 'contentDescription = "GPS desembarque ${passenger.name}"' not in passenger_ui:
    fail("Passenger dropoff GPS baseline not found")

# 1) Preserve the canonical BlaBlaCar profile UUID per local trip instead of
# assigning one current/global profile identity to every Timeline entry.
timeline = read(TIMELINE)
old_projection = '''                TripTimelineEntry(
                    tripId = trip.id,
                    profileId = localProfileId,
                    profileLabel = localProfileLabel,
                    departureAtMillis = trip.departureAtMillis,'''
new_projection = '''                val canonicalProfileUuid0536 = trip.blablaProfileUuid
                    ?.trim()
                    ?.lowercase()
                    ?.takeIf(String::isNotEmpty)
                TripTimelineEntry(
                    tripId = trip.id,
                    profileId = canonicalProfileUuid0536 ?: localProfileId,
                    profileLabel = if (canonicalProfileUuid0536 != null) "Perfil BlaBlaCar" else localProfileLabel,
                    departureAtMillis = trip.departureAtMillis,'''
timeline = replace_once(
    timeline,
    old_projection,
    new_projection,
    "TripTimelineEngine per-trip canonical profile identity",
)
write(TIMELINE, timeline)

# 2) Keep expansion state at screen scope. Empty initial set means every card
# is born closed; mutations/refetches do not overwrite this user-owned state.
ui = read(UI)
ui = replace_once(
    ui,
    '''    var searchQuery by remember { mutableStateOf("") }
    var syncPendingOnly by remember { mutableStateOf(false) }
    var referenceOrigin by remember { mutableStateOf<TripReferenceOrigin?>(null) }''',
    '''    var searchQuery by remember { mutableStateOf("") }
    var syncPendingOnly by remember { mutableStateOf(false) }
    var expandedTripIds0536 by remember { mutableStateOf<Set<String>>(emptySet()) }
    var referenceOrigin by remember { mutableStateOf<TripReferenceOrigin?>(null) }''',
    "Timeline screen expansion state",
)

# 3) Feed each existing card a canonical driver display name and stable
# canonical trip-keyed expansion control. No new Timeline/store is introduced.
old_call = '''                item(key = timelineLazyItemKey0380(entry)) {
                    val trip = timelineTripByEntryId[entry.tripId]
                    val archived = showArchived
                    TimelineEntryCard(
                        entry = entry,
                        trip = trip,
                        store = store,
                        formatter = formatter,
                        profileColorSlot = profileColorSlots[timelineProfileIdentity(entry)] ?: 0,
                        archived = archived,'''
new_call = '''                item(key = timelineLazyItemKey0380(entry)) {
                    val trip = timelineTripByEntryId[entry.tripId]
                    val archived = showArchived
                    val expansionKey0536 = timelineExpansionKey0536(entry.tripId)
                    TimelineEntryCard(
                        entry = entry,
                        trip = trip,
                        store = store,
                        formatter = formatter,
                        profileDisplayLabel = timelineDriverProfileLabel0536(
                            profileUuid = entry.blablaProfileUuid,
                            profileId = entry.profileId,
                            accounts = registeredAccounts0432,
                        ),
                        profileColorSlot = profileColorSlots[timelineProfileIdentity(entry)] ?: 0,
                        expanded = expansionKey0536 in expandedTripIds0536,
                        onToggleExpanded = {
                            expandedTripIds0536 = if (expansionKey0536 in expandedTripIds0536) {
                                expandedTripIds0536 - expansionKey0536
                            } else {
                                expandedTripIds0536 + expansionKey0536
                            }
                        },
                        archived = archived,'''
ui = replace_once(ui, old_call, new_call, "Timeline card call")

# 4) Pure helpers make profile resolution deterministic and regression-testable.
# Matching uses only persistent canonical identity; an unresolved UUID never
# borrows the name of another account.
helper_anchor = '''internal fun externalSyncStateIsPending(state: BlaBlaPublicationSeatSyncVisualState?): Boolean = state in setOf('''
helpers = '''internal fun timelineExpansionKey0536(tripId: String): String = tripId.trim()

internal fun timelineDriverProfileLabel0536(
    profileUuid: String?,
    profileId: String?,
    accounts: List<BlaBlaDynamicAccount>,
): String {
    val canonicalUuid = profileUuid
        ?.trim()
        ?.lowercase()
        ?.takeIf(String::isNotEmpty)
    if (canonicalUuid != null) {
        val names = accounts.asSequence()
            .filter { account ->
                account.profileUuid?.trim()?.lowercase()?.takeIf(String::isNotEmpty) == canonicalUuid
            }
            .mapNotNull { account -> BlaBlaDriverProfileNamePolicy.normalize(account.profileName) }
            .distinct()
            .toList()
        return names.singleOrNull() ?: "Perfil BlaBlaCar"
    }

    val canonicalAccountId = profileId
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.isNotEmpty() && it != "local" }
    if (canonicalAccountId != null) {
        val names = accounts.asSequence()
            .filter { account -> account.id.trim().lowercase() == canonicalAccountId }
            .mapNotNull { account -> BlaBlaDriverProfileNamePolicy.normalize(account.profileName) }
            .distinct()
            .toList()
        return names.singleOrNull() ?: "Perfil não identificado"
    }
    return "Agenda"
}

internal fun timelineDurationLabel0536(startMillis: Long, endMillis: Long?): String {
    val totalMinutes = endMillis
        ?.minus(startMillis)
        ?.takeIf { it > 0L }
        ?.div(60_000L)
        ?: return "—"
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours}h${minutes.toString().padStart(2, '0')}" else "${minutes}min"
}

'''
ui = replace_once(ui, helper_anchor, helpers + helper_anchor, "Timeline 0.1.536 pure helpers")

# 5) Extend the existing card only. Operational content/dialogs remain the same.
ui = replace_once(
    ui,
    '''    store: TripStore,
    formatter: DateTimeFormatter,
    profileColorSlot: Int,
    archived: Boolean,''',
    '''    store: TripStore,
    formatter: DateTimeFormatter,
    profileDisplayLabel: String,
    profileColorSlot: Int,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    archived: Boolean,''',
    "TimelineEntryCard signature",
)

old_header = '''        Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

            val allocation = tripChannelAllocationBreakdown('''
new_header = '''        Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val departureDateTime0536 = Instant.ofEpochMilli(entry.departureAtMillis).atZone(ZoneId.systemDefault())
            val arrivalDateTime0536 = entry.arrivalAtMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
            val date0536 = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.getDefault()).format(departureDateTime0536)
            val clockFormatter0536 = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
            val startTime0536 = clockFormatter0536.format(departureDateTime0536)
            val endTime0536 = arrivalDateTime0536?.let(clockFormatter0536::format) ?: "—"
            val duration0536 = timelineDurationLabel0536(entry.departureAtMillis, entry.arrivalAtMillis)
            val passengerCount0536 = maxOf(
                entry.maximumOccupiedSeats,
                entry.sourcePassengerSeats.values.sumOf { it.coerceAtLeast(0) },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    date0536.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
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
            Text("👤 $profileDisplayLabel", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$startTime0536  saída", style = MaterialTheme.typography.bodyMedium)
                Text("$duration0536  duração", style = MaterialTheme.typography.bodySmall)
                Text("$endTime0536  chegada", style = MaterialTheme.typography.bodyMedium)
            }
            Text("●  ${entry.origin}", style = MaterialTheme.typography.titleMedium)
            Text("│", style = MaterialTheme.typography.bodyMedium, color = profileColors.accent)
            Text("●  ${entry.destination}", style = MaterialTheme.typography.titleMedium)
            entry.blablaPrice?.takeIf(String::isNotBlank)?.let { price0536 ->
                Text(price0536, style = MaterialTheme.typography.bodySmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("👥 $passengerCount0536 passageiro(s)", style = MaterialTheme.typography.bodySmall)
                Text("🚗", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onToggleExpanded) {
                    Text(if (expanded) "▲ Fechar" else "▼ Abrir")
                }
            }

            if (expanded) {
                Text("RESUMO DA VIAGEM", style = MaterialTheme.typography.labelLarge, color = profileColors.accent)

            val allocation = tripChannelAllocationBreakdown('''
ui = replace_once(ui, old_header, new_header, "Suas viagens closed-card header")

# Close only the expanded operational block immediately after the existing
# ResponsiveTripActions; all dialogs remain outside and unchanged.
old_tail = '''            ResponsiveTripActions(
                buildList {
                    add(ResponsiveTripAction(if (archived) "Restaurar" else "Arquivar") { onArchive() })'''
if ui.count(old_tail) != 1:
    fail(f"ResponsiveTripActions start: expected one anchor, found {ui.count(old_tail)}")

close_anchor = '''                },
            )
        }
    }

    if (showSeatDetails) {'''
close_replacement = '''                },
            )
            }
        }
    }

    if (showSeatDetails) {'''
ui = replace_once(ui, close_anchor, close_replacement, "Close expanded operational block")

write(UI, ui)

# 6) Focused pure regression tests: two persistent profiles cannot swap;
# unresolved identity stays neutral; expansion is keyed only by canonical tripId.
test_content = '''package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripTimelineProfile0536Test {
    private val accounts = listOf(
        BlaBlaDynamicAccount(
            id = "account-a",
            label = "Conta operacional A",
            webProfileName = "web-a",
            profileUuid = "uuid-a",
            profileName = "Motorista Alfa",
        ),
        BlaBlaDynamicAccount(
            id = "account-b",
            label = "Conta operacional B",
            webProfileName = "web-b",
            profileUuid = "uuid-b",
            profileName = "Motorista Beta",
        ),
    )

    @Test
    fun canonicalTripProfileIdsRemainDistinct() {
        assertEquals("uuid-a", canonicalTimelineProfileId0536(" UUID-A ", "local"))
        assertEquals("uuid-b", canonicalTimelineProfileId0536("uuid-b", "local"))
        assertFalse(
            canonicalTimelineProfileId0536("uuid-a", "local") ==
                canonicalTimelineProfileId0536("uuid-b", "local"),
        )
    }

    @Test
    fun driverDisplayNameUsesOnlyMatchingPersistentIdentity() {
        assertEquals("Motorista Alfa", timelineDriverProfileLabel0536("UUID-A", "account-b", accounts))
        assertEquals("Motorista Beta", timelineDriverProfileLabel0536("uuid-b", "account-a", accounts))
    }

    @Test
    fun unresolvedCanonicalUuidNeverBorrowsAnotherDriver() {
        assertEquals("Perfil BlaBlaCar", timelineDriverProfileLabel0536("uuid-missing", "account-a", accounts))
    }

    @Test
    fun stableTripIdKeepsExplicitExpansionAcrossEntryRefreshes() {
        val expanded = setOf(timelineExpansionKey0536(" trip-canonical-1 "))
        assertTrue(timelineExpansionKey0536("trip-canonical-1") in expanded)
        assertFalse(timelineExpansionKey0536("trip-canonical-2") in expanded)
    }

    @Test
    fun durationIsRenderedFromCanonicalTimes() {
        assertEquals("2h05", timelineDurationLabel0536(1_000L, 1_000L + 125L * 60_000L))
        assertEquals("—", timelineDurationLabel0536(1_000L, null))
    }
}
'''
if TEST.exists():
    fail("TripTimelineProfile0536Test.kt already exists; refusing to overwrite unknown work")
write(TEST, test_content)

# Add tiny pure helper used by the local projection and test it independently.
timeline = read(TIMELINE)
engine_anchor = '''object TripTimelineEngine {
    fun fromLocalAgenda('''
engine_helpers = '''internal fun canonicalTimelineProfileId0536(blablaProfileUuid: String?, fallbackProfileId: String): String =
    blablaProfileUuid?.trim()?.lowercase()?.takeIf(String::isNotEmpty) ?: fallbackProfileId

object TripTimelineEngine {
    fun fromLocalAgenda('''
timeline = replace_once(timeline, engine_anchor, engine_helpers, "Canonical profile identity helper")
timeline = replace_once(
    timeline,
    '''                    profileId = canonicalProfileUuid0536 ?: localProfileId,''',
    '''                    profileId = canonicalTimelineProfileId0536(canonicalProfileUuid0536, localProfileId),''',
    "Projection uses canonical profile identity helper",
)
write(TIMELINE, timeline)

build = build.replace('versionCode = 5827', 'versionCode = 5828', 1)
build = build.replace('versionName = "0.1.535"', 'versionName = "0.1.536"', 1)
write(BUILD, build)

# Final source-level invariants before Gradle compilation.
final_ui = read(UI)
final_timeline = read(TIMELINE)
if "expandedTripIds0536" not in final_ui:
    fail("Expansion state was not materialized")
if "timelineDriverProfileLabel0536" not in final_ui:
    fail("Canonical profile label resolver was not materialized")
if 'Text("RESUMO DA VIAGEM"' not in final_ui:
    fail("Expanded operational summary marker missing")
if "Carregando passageiros" in read(PASSENGER_UI):
    fail("Passenger loading text regression detected")
if "canonicalTimelineProfileId0536" not in final_timeline:
    fail("Per-trip canonical profile identity helper missing")
print("Timeline 0.1.536 materialized successfully")
