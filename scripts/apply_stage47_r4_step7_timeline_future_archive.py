#!/usr/bin/env python3
from pathlib import Path
import sys

SOURCE = Path(sys.argv[1]).resolve()
TRIPS = SOURCE / "app/src/main/java/br/com/mapeiaia/rotacerta/trips"
TIMELINE_UI = TRIPS / "TripTimelineUi.kt"


def once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one marker, got {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


if not TIMELINE_UI.is_file():
    raise SystemExit(f"missing materialized Timeline UI: {TIMELINE_UI}")

# Full Brazilian date and balanced three-button actions on compact phones.
once(
    TIMELINE_UI,
    '''import androidx.compose.foundation.layout.padding\n''',
    '''import androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.layout.weight\n''',
    "timeline weight import",
)
once(
    TIMELINE_UI,
    '''import java.time.Instant\nimport java.time.ZoneId\nimport java.time.format.DateTimeFormatter\n''',
    '''import java.time.Instant\nimport java.time.LocalDate\nimport java.time.ZoneId\nimport java.time.format.DateTimeFormatter\nimport java.util.Locale\n''',
    "timeline full date imports",
)

# The main Timeline is operational: today + future only, nearest departure first.
# Archived items are locally hidden, never cancelled/deleted on BlaBlaCar or Agenda.
once(
    TIMELINE_UI,
    '''    val collectorStore = remember(context) { BlaBlaCollectorStateStore(context) }\n    var collectorResponse by remember { mutableStateOf(collectorStore.lastResponse()) }\n    val localEntries = remember(trips, bookings) { TripTimelineEngine.fromLocalAgenda(trips, bookings) }\n    val entries = remember(localEntries, collectorResponse) { BlaBlaTimelineAdapter.merge(localEntries, collectorResponse) }\n    val formatter = remember { DateTimeFormatter.ofPattern("dd/MM HH:mm") }\n''',
    '''    val collectorStore = remember(context) { BlaBlaCollectorStateStore(context) }\n    val archiveStore = remember(context) { TripTimelineArchiveStore(context) }\n    var collectorResponse by remember { mutableStateOf(collectorStore.lastResponse()) }\n    var archiveRevision by remember { mutableStateOf(0) }\n    var showArchived by remember { mutableStateOf(false) }\n    val localEntries = remember(trips, bookings) { TripTimelineEngine.fromLocalAgenda(trips, bookings) }\n    val mergedEntries = remember(localEntries, collectorResponse) { BlaBlaTimelineAdapter.merge(localEntries, collectorResponse) }\n    val todayStartMillis = LocalDate.now(ZoneId.systemDefault())\n        .atStartOfDay(ZoneId.systemDefault())\n        .toInstant()\n        .toEpochMilli()\n    val entries = remember(mergedEntries, archiveRevision, showArchived, todayStartMillis) {\n        mergedEntries.asSequence()\n            .filter { it.departureAtMillis >= todayStartMillis }\n            .filter { archiveStore.isArchived(it) == showArchived }\n            .sortedBy(TripTimelineEntry::departureAtMillis)\n            .toList()\n    }\n    val formatter = remember {\n        DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM 'de' yyyy • HH:mm", Locale("pt", "BR"))\n    }\n''',
    "future chronological timeline",
)

once(
    TIMELINE_UI,
    '''    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {\n        Text("Linha do tempo", style = MaterialTheme.typography.titleLarge)\n        OutlinedButton(onClick = onBack) { Text("Voltar") }\n    }\n''',
    '''    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {\n        Text(if (showArchived) "Viagens arquivadas" else "Próximas viagens", style = MaterialTheme.typography.titleLarge)\n        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {\n            OutlinedButton(onClick = { showArchived = !showArchived }) {\n                Text(if (showArchived) "Futuras" else "Arquivadas")\n            }\n            OutlinedButton(onClick = onBack) { Text("Voltar") }\n        }\n    }\n''',
    "archive view toggle",
)

once(
    TIMELINE_UI,
    '''    if (entries.isEmpty()) {\n        Text("Nenhuma viagem para organizar.")\n        return\n    }\n\n    entries.forEach { entry ->\n        val trip = entry.localTripId?.let { localId -> trips.firstOrNull { it.id == localId } }\n        TimelineEntryCard(entry, trip, store, formatter, onChanged, onManageLocal)\n    }\n''',
    '''    if (entries.isEmpty()) {\n        Text(if (showArchived) "Nenhuma viagem futura arquivada." else "Nenhuma viagem de hoje ou futura para organizar.")\n        return\n    }\n\n    entries.forEach { entry ->\n        val trip = entry.localTripId?.let { localId -> trips.firstOrNull { it.id == localId } }\n        val archived = archiveStore.isArchived(entry)\n        TimelineEntryCard(\n            entry = entry,\n            trip = trip,\n            store = store,\n            formatter = formatter,\n            onChanged = onChanged,\n            onManageLocal = onManageLocal,\n            archived = archived,\n            onArchive = {\n                archiveStore.setArchived(entry, !archived)\n                archiveRevision++\n                onChanged(if (archived) "Viagem restaurada na linha do tempo." else "Viagem arquivada sem cancelar a publicação.")\n            },\n        )\n    }\n''',
    "future archived card routing",
)

once(
    TIMELINE_UI,
    '''    formatter: DateTimeFormatter,\n    onChanged: (String) -> Unit,\n    onManageLocal: (String) -> Unit,\n) {\n''',
    '''    formatter: DateTimeFormatter,\n    onChanged: (String) -> Unit,\n    onManageLocal: (String) -> Unit,\n    archived: Boolean,\n    onArchive: () -> Unit,\n) {\n''',
    "timeline archive card arguments",
)

# Keep all three requested actions on the same row while respecting compact width.
once(
    TIMELINE_UI,
    '''            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                OutlinedButton(onClick = { manage() }) { Text("Gerenciar") }\n                OutlinedButton(onClick = {\n                    if (trip != null) {\n                        quickOpen = !quickOpen\n                        capacitySetupOpen = false\n                    } else {\n                        capacitySetupOpen = !capacitySetupOpen\n                        quickOpen = false\n                    }\n                }) { Text("+ Passageiro") }\n            }\n''',
    '''            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {\n                OutlinedButton(onClick = { manage() }, modifier = Modifier.weight(1f)) { Text("Gerenciar") }\n                OutlinedButton(\n                    onClick = {\n                        if (trip != null) {\n                            quickOpen = !quickOpen\n                            capacitySetupOpen = false\n                        } else {\n                            capacitySetupOpen = !capacitySetupOpen\n                            quickOpen = false\n                        }\n                    },\n                    modifier = Modifier.weight(1f),\n                ) { Text("+ Passageiro") }\n                OutlinedButton(onClick = onArchive, modifier = Modifier.weight(1f)) {\n                    Text(if (archived) "Restaurar" else "Arquivar")\n                }\n            }\n''',
    "timeline archive action",
)

# Persist archive aliases locally.  Aliases survive a local-only card later being
# merged with the exact BlaBlaCar trip and do not mutate either source of truth.
text = TIMELINE_UI.read_text(encoding="utf-8")
anchor = '''private fun openExactBlaBlaTrip(context: Context, entry: TripTimelineEntry): Boolean {\n'''
if text.count(anchor) != 1:
    raise SystemExit(f"timeline archive store anchor: expected one marker, got {text.count(anchor)}")
archive_store = r'''private class TripTimelineArchiveStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isArchived(entry: TripTimelineEntry): Boolean {
        val archived = prefs.getStringSet(KEY_ARCHIVED, emptySet()).orEmpty()
        return aliases(entry).any(archived::contains)
    }

    fun setArchived(entry: TripTimelineEntry, value: Boolean) {
        val archived = prefs.getStringSet(KEY_ARCHIVED, emptySet()).orEmpty().toMutableSet()
        val aliases = aliases(entry)
        if (value) archived.addAll(aliases) else archived.removeAll(aliases)
        prefs.edit().putStringSet(KEY_ARCHIVED, archived).apply()
    }

    private fun aliases(entry: TripTimelineEntry): Set<String> = buildSet {
        entry.localTripId?.trim()?.takeIf(String::isNotEmpty)?.let { add("local:$it") }
        entry.blablaTripId?.trim()?.takeIf(String::isNotEmpty)?.let { add("blabla-id:$it") }
        entry.blablaTripHref?.trim()?.takeIf(String::isNotEmpty)?.let { add("blabla-href:$it") }
        add("entry:${entry.tripId}")
    }

    companion object {
        private const val PREFS = "rota_certa_trip_timeline_archive_stage47"
        private const val KEY_ARCHIVED = "archived_trip_aliases"
    }
}

'''
TIMELINE_UI.write_text(text.replace(anchor, archive_store + anchor, 1), encoding="utf-8")

ui = TIMELINE_UI.read_text(encoding="utf-8")
for marker in (
    "Próximas viagens",
    "Viagens arquivadas",
    "EEEE, dd 'de' MMMM 'de' yyyy • HH:mm",
    "sortedBy(TripTimelineEntry::departureAtMillis)",
    "TripTimelineArchiveStore",
    'Text(if (archived) "Restaurar" else "Arquivar")',
    "Viagem arquivada sem cancelar a publicação.",
):
    if marker not in ui:
        raise SystemExit(f"missing future/archive Timeline marker {marker!r}")

print(
    "stage47_r4_step7_timeline_future_archive=PASS "
    "today_future_only=true chronological_ascending=true full_ptbr_date=true archive_local_only=true restore=true"
)
