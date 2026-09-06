from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text()
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: {label} expected one anchor, found {count}")
    path.write_text(text.replace(old, new, 1))


# 1) Pure date-scoping policy: the accepted trip.date is the authority, never the
# outer visual labels "Hoje/Amanhã/Ontem".
timeline = Path("app/src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaCollectorTimelineModule.kt")
text = timeline.read_text()
if "import java.time.LocalDate" not in text:
    anchor = "package br.com.mapeiaia.rotacerta.trips\n\n"
    if text.count(anchor) != 1:
        raise SystemExit("BlaBlaCollectorTimelineModule.kt: package anchor not unique")
    text = text.replace(anchor, anchor + "import java.time.LocalDate\n\n", 1)

if 'private const val DATE_SCOPE_PREFIX = "date_scope:"' not in text:
    anchor = "internal object BlaBlaCollectorTimelineModule {\n"
    if text.count(anchor) != 1:
        raise SystemExit("BlaBlaCollectorTimelineModule.kt: object anchor not unique")
    text = text.replace(anchor, anchor + '    private const val DATE_SCOPE_PREFIX = "date_scope:"\n\n', 1)

if "fun scopeResponseToDate(" not in text:
    anchor = "    fun recoverStartupResponse(\n"
    if text.count(anchor) != 1:
        raise SystemExit("BlaBlaCollectorTimelineModule.kt: recovery anchor not unique")
    helper = '''    fun scopeResponseToDate(
        response: BlaBlaCollectorMonthResponse,
        date: LocalDate,
    ): BlaBlaCollectorMonthResponse {
        val isoDate = date.toString()
        return response.copy(
            trips = response.trips.filter { trip -> trip.date == isoDate },
            coverage = response.coverage.copy(reason = "$DATE_SCOPE_PREFIX$isoDate"),
        )
    }

    fun isDateScoped(response: BlaBlaCollectorMonthResponse?): Boolean =
        response?.coverage?.reason?.startsWith(DATE_SCOPE_PREFIX) == true

'''
    text = text.replace(anchor, helper + anchor, 1)

old = '''        if (persisted?.status == "cleared") return persisted
        if (persisted?.trips?.isNotEmpty() == true) return persisted
'''
new = '''        if (persisted?.status == "cleared") return persisted
        if (isDateScoped(persisted)) return persisted
        if (persisted?.trips?.isNotEmpty() == true) return persisted
'''
if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    raise SystemExit("BlaBlaCollectorTimelineModule.kt: recovery policy anchor not found")
timeline.write_text(text)


# 2) Collector UI: keep the existing complete sync and add a second explicit
# "today only" publication mode. It reuses the same collector/session queue.
ui = Path("app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripBlaBlaCollectorUi.kt")
text = ui.read_text()
if "import java.time.LocalDate" not in text:
    anchor = "import java.time.Instant\n"
    if text.count(anchor) != 1:
        raise SystemExit("TripBlaBlaCollectorUi.kt: Instant import anchor not unique")
    text = text.replace(anchor, anchor + "import java.time.LocalDate\n", 1)

old_state = "    var targetedSyncTripId by remember { mutableStateOf<String?>(null) }\n"
new_state = old_state + "    var syncDateScope by remember { mutableStateOf<LocalDate?>(null) }\n"
if old_state in text:
    text = text.replace(old_state, new_state, 1)
elif "var syncDateScope by remember" not in text:
    raise SystemExit("TripBlaBlaCollectorUi.kt: sync date state anchor not found")

old_publish = '''    fun publishCombined(messagePrefix: String) {
        val accounts = registry.list()
        val response = sessionStore.combinedResponse(accounts)
        val published = stateStore.saveResponse(response)
        onResult(published)
        refresh()
        message = "$messagePrefix • ${published.coverage.validated_queries}/${accounts.size} contas UUID-confirmadas • ${published.trips.size} viagens."
        onChanged(message.orEmpty())
    }
'''
new_publish = '''    fun publishCombined(messagePrefix: String) {
        val accounts = registry.list()
        val response = sessionStore.combinedResponse(accounts)
        val scopeDate = syncDateScope
        val scopedResponse = scopeDate?.let { date ->
            BlaBlaCollectorTimelineModule.scopeResponseToDate(response, date)
        } ?: response
        val published = stateStore.saveResponse(
            scopedResponse,
            preserveOnPartial = scopeDate == null,
        )
        onResult(published)
        refresh()
        val scopeLabel = scopeDate?.let { date ->
            " • somente ${date.format(DateTimeFormatter.ofPattern("dd/MM"))}"
        }.orEmpty()
        message = "$messagePrefix$scopeLabel • ${published.coverage.validated_queries}/${accounts.size} contas UUID-confirmadas • ${published.trips.size} viagens."
        onChanged(message.orEmpty())
        UnifiedDebugEventStore.record(
            "AGENDA_SYNC_SCOPE_PUBLISHED",
            context.packageName,
            "scope=${if (scopeDate == null) "all" else "today"} targetDate=${scopeDate ?: "none"} source=normalized_trip_date publishedTrips=${published.trips.size}",
        )
        syncDateScope = null
    }
'''
if old_publish in text:
    text = text.replace(old_publish, new_publish, 1)
elif "AGENDA_SYNC_SCOPE_PUBLISHED" not in text:
    raise SystemExit("TripBlaBlaCollectorUi.kt: publishCombined anchor not found")

# A failed queue must not leak the previous date scope into the next sync.
old_failure = '''            } else {
                syncing = false
                archiving = false
                val account = registry.get(accountId)
'''
new_failure = '''            } else {
                syncing = false
                archiving = false
                syncDateScope = null
                val account = registry.get(accountId)
'''
if old_failure in text:
    text = text.replace(old_failure, new_failure, 1)
elif new_failure not in text:
    raise SystemExit("TripBlaBlaCollectorUi.kt: failure reset anchor not found")

old_seat_refresh = '''            // refresh only the authenticated account that owns that publication.
            syncQueue = listOf(accountId)
'''
new_seat_refresh = '''            // refresh only the authenticated account that owns that publication.
            syncDateScope = null
            syncQueue = listOf(accountId)
'''
if old_seat_refresh in text:
    text = text.replace(old_seat_refresh, new_seat_refresh, 1)
elif new_seat_refresh not in text:
    raise SystemExit("TripBlaBlaCollectorUi.kt: seat refresh reset anchor not found")

old_auto = '''        handledAutoSyncToken = autoSyncToken
        targetedSyncTripId = autoSyncTripId?.trim()?.takeIf(String::isNotEmpty)
'''
new_auto = '''        handledAutoSyncToken = autoSyncToken
        syncDateScope = null
        targetedSyncTripId = autoSyncTripId?.trim()?.takeIf(String::isNotEmpty)
'''
if old_auto in text:
    text = text.replace(old_auto, new_auto, 1)
elif new_auto not in text:
    raise SystemExit("TripBlaBlaCollectorUi.kt: auto sync reset anchor not found")

old_exact_fail = '''                if (href.isNullOrBlank()) {
                    syncing = false
                    targetedSyncTripId = null
'''
new_exact_fail = '''                if (href.isNullOrBlank()) {
                    syncing = false
                    syncDateScope = null
                    targetedSyncTripId = null
'''
if old_exact_fail in text:
    text = text.replace(old_exact_fail, new_exact_fail, 1)
elif new_exact_fail not in text:
    raise SystemExit("TripBlaBlaCollectorUi.kt: exact sync reset anchor not found")

old_regular = '''                    if (!launchPendingSeatSync("manual_sync_button")) {
                        targetedSyncTripId = null
                        syncQueue = accounts.map { it.id }
'''
new_regular = '''                    if (!launchPendingSeatSync("manual_sync_button")) {
                        targetedSyncTripId = null
                        syncDateScope = null
                        syncQueue = accounts.map { it.id }
'''
if old_regular in text:
    text = text.replace(old_regular, new_regular, 1)
elif new_regular not in text:
    raise SystemExit("TripBlaBlaCollectorUi.kt: regular sync anchor not found")

if 'Text("Sincronizar só hoje")' not in text:
    anchor = '''            Text("A leitura usa somente a interface oficial logada. Senha não é capturada nem enviada ao Railway.")
'''
    if text.count(anchor) != 1:
        raise SystemExit("TripBlaBlaCollectorUi.kt: today button insertion anchor not unique")
    button = '''            OutlinedButton(
                enabled = !syncing && !archiving && !manualSeatSyncing && accounts.isNotEmpty(),
                onClick = {
                    if (!launchPendingSeatSync("manual_sync_today_button")) {
                        val today = LocalDate.now()
                        targetedSyncTripId = null
                        syncDateScope = today
                        syncQueue = accounts.map { it.id }
                        syncCursor = 0
                        syncing = true
                        archiving = false
                        message = "Sincronizando ${accounts.size} conta(s) • Timeline somente de ${today.format(DateTimeFormatter.ofPattern("dd/MM"))}…"
                        onChanged(message.orEmpty())
                        UnifiedDebugEventStore.record(
                            "AGENDA_TODAY_ONLY_SYNC_REQUESTED",
                            context.packageName,
                            "accounts=${accounts.size} targetDate=$today authority=normalized_trip_date outerRelativeLabelIgnored=true",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sincronizar só hoje")
            }

'''
    text = text.replace(anchor, button + anchor, 1)
ui.write_text(text)


# 3) Focused regression tests.
test = Path("app/src/test/java/br/com/mapeiaia/rotacerta/trips/AgendaTodayOnlySync0280Test.kt")
test.write_text('''package br.com.mapeiaia.rotacerta.trips

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AgendaTodayOnlySync0280Test {
    private fun trip(id: String, date: String) = BlaBlaCollectorTrip(
        profile_uuid = "profile-1",
        date = date,
        departure_time = "10:00",
        actual_departure = "Origem",
        actual_arrival = "Destino",
        trip_id = id,
    )

    @Test
    fun todayScopeKeepsOnlyTripsWhoseNormalizedDateMatches() {
        val response = BlaBlaCollectorMonthResponse(
            status = "success",
            trips = listOf(
                trip("yesterday", "2026-08-24"),
                trip("today-a", "2026-08-25"),
                trip("today-b", "2026-08-25"),
                trip("tomorrow", "2026-08-26"),
            ),
        )
        val scoped = BlaBlaCollectorTimelineModule.scopeResponseToDate(
            response,
            LocalDate.of(2026, 8, 25),
        )
        assertEquals(listOf("today-a", "today-b"), scoped.trips.map { it.trip_id })
        assertEquals("date_scope:2026-08-25", scoped.coverage.reason)
    }

    @Test
    fun emptyTodayScopeDoesNotResurrectOtherDatesOnStartup() {
        val persisted = BlaBlaCollectorTimelineModule.scopeResponseToDate(
            BlaBlaCollectorMonthResponse(status = "success", trips = listOf(trip("tomorrow", "2026-08-26"))),
            LocalDate.of(2026, 8, 25),
        )
        val dynamic = BlaBlaCollectorMonthResponse(
            status = "success",
            trips = listOf(trip("tomorrow", "2026-08-26")),
        )
        assertSame(persisted, BlaBlaCollectorTimelineModule.recoverStartupResponse(persisted, dynamic))
    }
}
''')


# 4) Version bump.
build = Path("app/build.gradle.kts")
build_text = build.read_text()
old = '        versionCode = 5572\n        versionName = "0.1.279"'
new = '        versionCode = 5573\n        versionName = "0.1.280"'
if old in build_text:
    build.write_text(build_text.replace(old, new, 1))
elif new not in build_text:
    raise SystemExit("app/build.gradle.kts: expected 0.1.279 baseline not found")
