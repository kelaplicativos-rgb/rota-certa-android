from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> str:
    text = path.read_text()
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: {label} expected one anchor, found {count}")
    text = text.replace(old, new, 1)
    path.write_text(text)
    return text


# 1) Pure startup recovery policy.
timeline_module = Path("app/src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaCollectorTimelineModule.kt")
text = timeline_module.read_text()
if "fun recoverStartupResponse(" not in text:
    stripped = text.rstrip()
    if not stripped.endswith("}"):
        raise SystemExit("BlaBlaCollectorTimelineModule.kt: closing object brace not found")
    helper = '''

    fun recoverStartupResponse(
        persisted: BlaBlaCollectorMonthResponse?,
        dynamic: BlaBlaCollectorMonthResponse?,
    ): BlaBlaCollectorMonthResponse? {
        if (persisted?.status == "cleared") return persisted
        if (persisted?.trips?.isNotEmpty() == true) return persisted
        if (dynamic == null || dynamic.trips.isEmpty()) return persisted
        return dynamic
    }
'''
    timeline_module.write_text(stripped[:-1] + helper + "\n}\n")


# 2) Clear synchronized snapshots while preserving identity/login.
session = Path("app/src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaCollectorSessionModule.kt")
text = session.read_text()
if "fun clearTripsPreservingSessions(" not in text:
    marker = "    fun combinedResponse(accounts: List<BlaBlaDynamicAccount>): BlaBlaCollectorMonthResponse {"
    if text.count(marker) != 1:
        raise SystemExit(f"BlaBlaCollectorSessionModule.kt: combinedResponse anchor count={text.count(marker)}")
    method = '''    fun clearTripsPreservingSessions(accounts: List<BlaBlaDynamicAccount>): Pair<Int, Int> {
        var accountsTouched = 0
        var tripsRemoved = 0
        accounts.forEach { account ->
            withAccountLock(account.id) {
                val previous = readUnlocked(account) ?: return@withAccountLock
                tripsRemoved += previous.trips.size
                if (previous.trips.isNotEmpty() || previous.skippedTrips != 0) {
                    accountsTouched++
                    writeUnlocked(
                        account,
                        previous.copy(
                            updatedAtMillis = System.currentTimeMillis(),
                            trips = emptyList(),
                            skippedTrips = 0,
                        ),
                    )
                }
            }
        }
        UnifiedDebugEventStore.record(
            "TIMELINE_SESSION_SNAPSHOTS_CLEARED",
            appContext.packageName,
            "accounts=${accounts.size} accountsTouched=$accountsTouched tripsRemoved=$tripsRemoved identityPreserved=true loginPreserved=true",
        )
        return accountsTouched to tripsRemoved
    }

'''
    session.write_text(text.replace(marker, method + marker, 1))


# 3) State store recovery and synchronized clear authority.
collector = Path("app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripBlaBlaCollector.kt")
text = collector.read_text()
if "data class BlaBlaTimelineClearResult(" not in text:
    marker = "class BlaBlaCollectorStateStore(context: Context) {"
    if text.count(marker) != 1:
        raise SystemExit(f"TripBlaBlaCollector.kt: state store anchor count={text.count(marker)}")
    data_class = '''internal data class BlaBlaTimelineClearResult(
    val response: BlaBlaCollectorMonthResponse,
    val externalTripsRemoved: Int,
    val sessionAccountsTouched: Int,
)

'''
    text = text.replace(marker, data_class + marker, 1)

if "fun lastResponseRecoveringDynamicSessions()" not in text:
    anchor = '''    fun lastResponse(): BlaBlaCollectorMonthResponse? = runCatching {
        prefs.getString(KEY_RESPONSE, null)?.let { json.decodeFromString<BlaBlaCollectorMonthResponse>(it) }
    }.getOrNull()

'''
    if text.count(anchor) != 1:
        raise SystemExit(f"TripBlaBlaCollector.kt: lastResponse anchor count={text.count(anchor)}")
    methods = '''    fun lastResponseRecoveringDynamicSessions(): BlaBlaCollectorMonthResponse? {
        val persisted = lastResponse()
        if (persisted?.status == "cleared" || persisted?.trips?.isNotEmpty() == true) return persisted
        val accounts = BlaBlaDynamicAccountRegistry(appContext).list()
        if (accounts.isEmpty()) return persisted
        val dynamic = BlaBlaDynamicSessionStore(appContext).combinedResponse(accounts)
        val recovered = BlaBlaCollectorTimelineModule.recoverStartupResponse(persisted, dynamic)
        if (recovered != null && recovered != persisted) {
            prefs.edit().putString(KEY_RESPONSE, json.encodeToString(recovered)).apply()
            UnifiedDebugEventStore.record(
                "TIMELINE_RECOVERED_FROM_SESSION_SNAPSHOTS",
                appContext.packageName,
                "persistedTrips=${persisted?.trips?.size ?: 0} sessionTrips=${dynamic.trips.size} recoveredTrips=${recovered.trips.size} accounts=${accounts.size} explicitClear=false",
            )
        }
        return recovered
    }

    internal fun clearSynchronizedTimelineData(): BlaBlaTimelineClearResult {
        val previous = lastResponse()
        val accounts = BlaBlaDynamicAccountRegistry(appContext).list()
        val sessionClear = BlaBlaDynamicSessionStore(appContext).clearTripsPreservingSessions(accounts)
        val cleared = saveResponse(
            BlaBlaCollectorMonthResponse(
                status = "cleared",
                month = previous?.month,
                strategy = previous?.strategy,
                profiles = previous?.profiles.orEmpty(),
                routes = previous?.routes.orEmpty(),
                coverage = BlaBlaCollectorCoverage(
                    complete_for_scope = true,
                    global_profile_month_complete = true,
                    reason = "cleared_by_user",
                    past_dates_skipped = previous?.coverage?.past_dates_skipped ?: true,
                ),
            ),
            preserveOnPartial = false,
        )
        val removed = maxOf(previous?.trips?.size ?: 0, sessionClear.second)
        UnifiedDebugEventStore.record(
            "TIMELINE_SYNCHRONIZED_DATA_CLEARED",
            appContext.packageName,
            "externalTripsRemoved=$removed sessionAccountsTouched=${sessionClear.first} localTripsTouched=false identityPreserved=true loginPreserved=true",
        )
        return BlaBlaTimelineClearResult(
            response = cleared,
            externalTripsRemoved = removed,
            sessionAccountsTouched = sessionClear.first,
        )
    }

'''
    text = text.replace(anchor, anchor + methods, 1)
collector.write_text(text)


# 4) UI: explicit dialog; default keeps local/manual trips.
ui = Path("app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt")
text = ui.read_text()
if "import androidx.compose.material3.AlertDialog" not in text:
    anchor = "import androidx.compose.material3.Button\n"
    if text.count(anchor) != 1:
        raise SystemExit("TripTimelineUi.kt: Button import anchor not unique")
    text = text.replace(anchor, "import androidx.compose.material3.AlertDialog\n" + anchor, 1)

old_init = "    var collectorResponse by remember { mutableStateOf(collectorStore.lastResponse()) }\n"
new_init = "    var collectorResponse by remember { mutableStateOf(collectorStore.lastResponseRecoveringDynamicSessions()) }\n"
if old_init in text:
    text = text.replace(old_init, new_init, 1)
elif new_init not in text:
    raise SystemExit("TripTimelineUi.kt: collectorResponse startup anchor not found")

if "var showTimelineClearDialog by remember" not in text:
    if text.count(new_init) != 1:
        raise SystemExit("TripTimelineUi.kt: collector state anchor not unique")
    text = text.replace(
        new_init,
        new_init + "    var showTimelineClearDialog by remember { mutableStateOf(false) }\n",
        1,
    )

old_action_start = '            ResponsiveTripAction("Limpar Timeline") {\n'
next_action = '            ResponsiveTripAction(if (showArchived) "Ver próximas" else "Ver arquivadas") { showArchived = !showArchived },\n'
if old_action_start in text:
    start = text.index(old_action_start)
    end = text.index(next_action, start)
    text = text[:start] + '            ResponsiveTripAction("Limpar Timeline") { showTimelineClearDialog = true },\n' + text[end:]
elif 'ResponsiveTripAction("Limpar Timeline") { showTimelineClearDialog = true }' not in text:
    raise SystemExit("TripTimelineUi.kt: Timeline clear action not found")

if "val clearTimeline: (Boolean) -> Unit" not in text:
    anchor = '''    ResponsiveTripActions(
        listOf(
            ResponsiveTripAction("Nova viagem", onClick = onCreateTrip),
'''
    if text.count(anchor) != 1:
        raise SystemExit(f"TripTimelineUi.kt: main toolbar anchor count={text.count(anchor)}")
    clear_lambda = '''    val clearTimeline: (Boolean) -> Unit = { includeManual ->
        archiveStore.clearExternal(physical)
        val externalClear = collectorStore.clearSynchronizedTimelineData()
        val localClear = if (includeManual) store.clearTimelineLocalData() else 0 to 0
        collectorResponse = externalClear.response
        showArchived = false
        searchQuery = ""
        showTimelineClearDialog = false
        UnifiedDebugEventStore.record(
            "TIMELINE_CLEARED_BY_USER",
            context.packageName,
            "externalTripsRemoved=${externalClear.externalTripsRemoved} externalArchiveStateReset=true includeManual=$includeManual localTripsRemoved=${localClear.first} localBookingsRemoved=${localClear.second} sessionAccountsTouched=${externalClear.sessionAccountsTouched} settingsPreserved=true loginPreserved=true",
        )
        onChanged(
            if (includeManual) {
                "Timeline limpa por completo. Viagens BlaBlaCar, viagens manuais e reservas locais foram excluídas; configurações e login foram preservados."
            } else {
                "Viagens sincronizadas da BlaBlaCar foram removidas. Viagens manuais e reservas locais foram preservadas."
            },
        )
    }

'''
    text = text.replace(anchor, clear_lambda + anchor, 1)

if 'title = { Text("Limpar Timeline") }' not in text:
    anchor = "    if (showPublisher) {\n"
    if text.count(anchor) != 1:
        raise SystemExit(f"TripTimelineUi.kt: showPublisher anchor count={text.count(anchor)}")
    dialog = '''    if (showTimelineClearDialog) {
        AlertDialog(
            onDismissRequest = { showTimelineClearDialog = false },
            title = { Text("Limpar Timeline") },
            text = {
                Text("Deseja apagar também as viagens feitas manualmente/por fora? Por padrão, somente as viagens sincronizadas da BlaBlaCar serão removidas.")
            },
            confirmButton = {
                Button(onClick = { clearTimeline(false) }) { Text("Só BlaBlaCar") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showTimelineClearDialog = false }) { Text("Cancelar") }
                    TextButton(onClick = { clearTimeline(true) }) { Text("BlaBlaCar + manuais") }
                }
            },
        )
    }

'''
    text = text.replace(anchor, dialog + anchor, 1)
ui.write_text(text)


# 5) Focused pure regression tests.
test = Path("app/src/test/java/br/com/mapeiaia/rotacerta/trips/AgendaTimelineRecovery0279Test.kt")
test.write_text('''package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Test

class AgendaTimelineRecovery0279Test {
    private val trip = BlaBlaCollectorTrip(
        profile_uuid = "profile-1",
        date = "2026-08-25",
        departure_time = "11:00",
        actual_departure = "Origem",
        actual_arrival = "Destino",
        trip_id = "trip-1",
    )

    @Test
    fun explicitClearNeverResurrectsSessionTrips() {
        val persisted = BlaBlaCollectorMonthResponse(status = "cleared", trips = emptyList())
        val dynamic = BlaBlaCollectorMonthResponse(status = "success", trips = listOf(trip))
        assertEquals(persisted, BlaBlaCollectorTimelineModule.recoverStartupResponse(persisted, dynamic))
    }

    @Test
    fun emptyPersistedTimelineRecoversVerifiedSessionTrips() {
        val persisted = BlaBlaCollectorMonthResponse(status = "partial", trips = emptyList())
        val dynamic = BlaBlaCollectorMonthResponse(status = "success", trips = listOf(trip))
        assertEquals(dynamic, BlaBlaCollectorTimelineModule.recoverStartupResponse(persisted, dynamic))
    }

    @Test
    fun existingPersistedTimelineRemainsStartupAuthority() {
        val persisted = BlaBlaCollectorMonthResponse(status = "success", trips = listOf(trip.copy(trip_id = "persisted")))
        val dynamic = BlaBlaCollectorMonthResponse(status = "success", trips = listOf(trip.copy(trip_id = "dynamic")))
        assertEquals(persisted, BlaBlaCollectorTimelineModule.recoverStartupResponse(persisted, dynamic))
    }
}
''')


# 6) Version bump.
build = Path("app/build.gradle.kts")
build_text = build.read_text()
old = '        versionCode = 5571\n        versionName = "0.1.278"'
new = '        versionCode = 5572\n        versionName = "0.1.279"'
if old in build_text:
    build.write_text(build_text.replace(old, new, 1))
elif new not in build_text:
    raise SystemExit("app/build.gradle.kts: 0.1.278 baseline not found")