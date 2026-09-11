from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt"
ACTIVITY = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt"
GLOBAL_TEST = ROOT / "app/src/test/java/br/com/mapeiaia/rotacerta/trips/TimelineGlobalRadar0540Test.kt"
PASSENGER_UI = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt"
BUILD = ROOT / "app/build.gradle.kts"


def fail(message: str) -> None:
    raise SystemExit(message)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write(path: Path, content: str) -> None:
    path.write_text(content, encoding="utf-8")


def replace_once(content: str, old: str, new: str, label: str) -> str:
    count = content.count(old)
    if count != 1:
        fail(f"{label}: expected exactly one baseline anchor, found {count}")
    return content.replace(old, new, 1)


def replace_span(content: str, start: str, end: str, replacement: str, label: str) -> str:
    if content.count(start) != 1:
        fail(f"{label}: expected one start marker, found {content.count(start)}")
    start_index = content.index(start)
    end_index = content.find(end, start_index)
    if end_index < 0:
        fail(f"{label}: end marker not found")
    return content[:start_index] + replacement + content[end_index:]


build = read(BUILD)
ui = read(UI)
activity = read(ACTIVITY)
passenger_ui = read(PASSENGER_UI)

if 'versionCode = 5831' not in build or 'versionName = "0.1.539"' not in build:
    fail("Unexpected version baseline; refusing to materialize 0.1.540")

for marker in [
    'TIMELINE_PULL_REFRESH_ALL_COLLECTOR_0538',
    'AgendaBackgroundSync0392.enqueueTripCollectorRefresh0517(',
    'origin = BlaBlaCommandOrigin0407.CARD',
    'origin = BlaBlaCommandOrigin0407.SYSTEM_RECONCILIATION',
    'TimelineRefreshGestureSurface0388(',
    'Puxe para atualizar',
    'Solte para atualizar',
]:
    haystack = activity if marker == 'TimelineRefreshGestureSurface0388(' else ui
    if marker in ['Puxe para atualizar', 'Solte para atualizar']:
        haystack = read(ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaPullRefreshGesture0388.kt")
    if marker not in haystack:
        fail(f"Required 0.1.539 invariant missing: {marker}")
if "Carregando passageiros" in passenger_ui:
    fail("Visible passenger loading regression exists in baseline")

helper_anchor = '''internal fun distinctTimelineGlobalPullTargets0538(
    targets: Iterable<BlaBlaTripTarget0407?>,
): List<BlaBlaTripTarget0407> = targets
    .filterNotNull()
    .distinctBy(BlaBlaTripTarget0407::strongIdentityKey)
'''
helper_new = helper_anchor + '''
internal fun timelineGlobalRadarBatchBusy0540(
    audits: Iterable<BlaBlaCommandAuditSnapshot0407?>,
): Boolean = audits.any { it?.pending == true }
'''
ui = replace_once(ui, helper_anchor, helper_new, "global radar pending helper")

signature_old = '''    manualRefreshToken0499: Int = 0,
    onCanonicalRefreshState0499: (Boolean, String?) -> Unit = { _, _ -> },
) {'''
signature_new = '''    manualRefreshToken0499: Int = 0,
    lastHandledGlobalRefreshToken0540: Int = 0,
    onGlobalRefreshStarted0540: (Int) -> Unit = {},
    onGlobalRefreshBusy0540: (Boolean) -> Unit = {},
    onCanonicalRefreshState0499: (Boolean, String?) -> Unit = { _, _ -> },
) {'''
ui = replace_once(ui, signature_old, signature_new, "global radar callbacks")

callback_anchor = '''    val canonicalRefreshStateCallback0499 = androidx.compose.runtime.rememberUpdatedState(onCanonicalRefreshState0499)
'''
callback_new = callback_anchor + '''    val globalRefreshStartedCallback0540 = androidx.compose.runtime.rememberUpdatedState(onGlobalRefreshStarted0540)
    val globalRefreshBusyCallback0540 = androidx.compose.runtime.rememberUpdatedState(onGlobalRefreshBusy0540)
'''
ui = replace_once(ui, callback_anchor, callback_new, "remember global radar callbacks")

ui = replace_once(
    ui,
    '                val manualPull0499 = reason == "USER_PULL_REFRESH"\n',
    '                val manualPull0499 = reason == "USER_PULL_REFRESH" || reason.startsWith("USER_GLOBAL_RADAR_REFRESH_0540")\n',
    "manual refresh reason compatibility",
)

effect_start = '''    LaunchedEffect(manualRefreshToken0499) {
        if (manualRefreshToken0499 <= 0) return@LaunchedEffect
'''
effect_end = '''    val commandAuditsByCard0432 = remember(tripTargetsByCard0432, commandRevision0407) {
'''
new_effect = '''    LaunchedEffect(manualRefreshToken0499) {
        if (
            manualRefreshToken0499 <= 0 ||
            manualRefreshToken0499 <= lastHandledGlobalRefreshToken0540
        ) return@LaunchedEffect

        globalRefreshStartedCallback0540.value(manualRefreshToken0499)
        globalRefreshBusyCallback0540.value(true)

        val resolvedTargets0540 = tripTargetsByCard0432.values.filterNotNull()
        val targets0540 = distinctTimelineGlobalPullTargets0538(resolvedTargets0540)
        val statusStore0540 = BlaBlaTripCommandStatusStore0407(context)
        val alreadyPending0540 = targets0540.count { statusStore0540.get(it)?.pending == true }

        if (targets0540.isEmpty()) {
            UnifiedDebugEventStore.record(
                "TIMELINE_GLOBAL_RADAR_EMPTY_0540",
                context.packageName,
                "cards=${tripTargetsByCard0432.size} resolvedTargets=0 strongTargets=0 noCollectorWork=true",
            )
            globalRefreshBusyCallback0540.value(false)
            return@LaunchedEffect
        }

        if (alreadyPending0540 > 0) {
            UnifiedDebugEventStore.record(
                "TIMELINE_GLOBAL_RADAR_BLOCKED_0540",
                context.packageName,
                "strongTargets=${targets0540.size} pendingTargets=$alreadyPending0540 reason=previous_target_refresh_in_progress",
            )
            globalRefreshBusyCallback0540.value(true)
            return@LaunchedEffect
        }

        var acceptedTargets0540 = 0
        targets0540.forEach { target0540 ->
            val command0540 = BlaBlaCommand0407.forTarget(
                target = target0540,
                operation = BlaBlaTripCapability0407.REVERIFY_TRIP,
                origin = BlaBlaCommandOrigin0407.SYSTEM_RECONCILIATION,
            )
            if (
                AgendaBackgroundSync0392.enqueueTripCollectorRefresh0517(
                    context = context,
                    target = target0540,
                    commandId = command0540.commandId,
                    requestedAtMillis = command0540.requestedAtMillis,
                )
            ) {
                acceptedTargets0540++
            }
        }
        UnifiedDebugEventStore.record(
            "TIMELINE_GLOBAL_RADAR_BATCH_STARTED_0540",
            context.packageName,
            "cards=${tripTargetsByCard0432.size} resolvedTargets=${resolvedTargets0540.size} " +
                "strongTargets=${targets0540.size} acceptedTargets=$acceptedTargets0540 " +
                "skippedIdentity=${tripTargetsByCard0432.size - resolvedTargets0540.size} " +
                "deduplicated=${resolvedTargets0540.size - targets0540.size} " +
                "collectorToAgenda=true directTimelineCollectorRead=false trigger=TOP_FIXED_RADAR",
        )
        if (acceptedTargets0540 == 0) {
            globalRefreshBusyCallback0540.value(false)
            return@LaunchedEffect
        }
        invalidateCanonicalTimeline0495("USER_GLOBAL_RADAR_REFRESH_0540")
    }
'''
ui = replace_span(ui, effect_start, effect_end, new_effect, "replace pull batch with fixed radar batch")

command_audits_anchor = '''    val commandAuditsByCard0432 = remember(tripTargetsByCard0432, commandRevision0407) {
        val statusStore = BlaBlaTripCommandStatusStore0407(context)
        tripTargetsByCard0432.mapValues { (_, target) -> target?.let(statusStore::get) }
    }
'''
command_audits_new = command_audits_anchor + '''    val globalRadarBusyFromStore0540 = remember(commandAuditsByCard0432) {
        timelineGlobalRadarBatchBusy0540(commandAuditsByCard0432.values)
    }
    LaunchedEffect(commandRevision0407) {
        if (manualRefreshToken0499 <= lastHandledGlobalRefreshToken0540) {
            globalRefreshBusyCallback0540.value(globalRadarBusyFromStore0540)
            if (!globalRadarBusyFromStore0540 && manualRefreshToken0499 > 0) {
                UnifiedDebugEventStore.record(
                    "TIMELINE_GLOBAL_RADAR_BATCH_COMPLETE_0540",
                    context.packageName,
                    "strongTargets=${distinctTimelineGlobalPullTargets0538(tripTargetsByCard0432.values).size} pendingTargets=0 collectorToAgenda=true directTimelineCollectorRead=false",
                )
                invalidateCanonicalTimeline0495("USER_GLOBAL_RADAR_REFRESH_0540_COMPLETE")
            }
        }
    }
'''
ui = replace_once(ui, command_audits_anchor, command_audits_new, "durable global radar completion lock")

state_old = '''    var timelinePullRefreshToken0499 by remember { mutableStateOf(0) }
    var timelinePullRefreshing0499 by remember { mutableStateOf(false) }
'''
state_new = '''    var timelineGlobalRefreshToken0540 by rememberSaveable { mutableStateOf(0) }
    var timelineGlobalRefreshHandledToken0540 by rememberSaveable { mutableStateOf(0) }
    var timelineGlobalRefreshBusy0540 by rememberSaveable { mutableStateOf(false) }
'''
activity = replace_once(activity, state_old, state_new, "global radar activity state")

request_old = '''    val requestTimelineCanonicalPullRefresh0499 = {
        if (!timelinePullRefreshing0499) {
            timelinePullRefreshing0499 = true
            timelinePullRefreshToken0499 += 1
            message = "Atualizando todas as viagens: BlaBlaCar → Agenda → Timeline..."
            UnifiedDebugEventStore.record(
                "AGENDA_TIMELINE_GLOBAL_PULL_REFRESH_0538",
                activity.packageName,
                "collectorBatch=true collectorToAgenda=true directTimelineCollectorRead=false canonicalSecondaryRefresh=true collectorFallback=false",
            )
        }
    }
'''
request_new = '''    val requestTimelineGlobalRadarRefresh0540 = {
        if (!timelineGlobalRefreshBusy0540) {
            timelineGlobalRefreshBusy0540 = true
            timelineGlobalRefreshToken0540 += 1
            message = "📡 Atualizando todas as viagens: BlaBlaCar → Agenda → Timeline..."
            UnifiedDebugEventStore.record(
                "AGENDA_TIMELINE_GLOBAL_RADAR_REFRESH_0540",
                activity.packageName,
                "collectorBatch=true collectorToAgenda=true directTimelineCollectorRead=false trigger=TOP_FIXED_RADAR repeatedRequestBlocked=true",
            )
        } else {
            UnifiedDebugEventStore.record(
                "AGENDA_TIMELINE_GLOBAL_RADAR_REFRESH_BLOCKED_0540",
                activity.packageName,
                "reason=previous_global_or_target_refresh_pending",
            )
        }
    }
'''
activity = replace_once(activity, request_old, request_new, "fixed radar request")

branch_start = '''                TripScreen.TIMELINE -> TimelineRefreshGestureSurface0388(
'''
branch_end = '''                TripScreen.ASSISTANT -> RotaCertaAssistantPanel0410(
'''
branch_new = '''                TripScreen.TIMELINE -> Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            enabled = !timelineGlobalRefreshBusy0540,
                            onClick = requestTimelineGlobalRadarRefresh0540,
                        ) {
                            Text(
                                if (timelineGlobalRefreshBusy0540) {
                                    "📡 Atualizando todas…"
                                } else {
                                    "📡 Atualizar todas"
                                },
                            )
                        }
                    }
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
                        manualRefreshToken0499 = timelineGlobalRefreshToken0540,
                        lastHandledGlobalRefreshToken0540 = timelineGlobalRefreshHandledToken0540,
                        onGlobalRefreshStarted0540 = { token0540 ->
                            timelineGlobalRefreshHandledToken0540 = maxOf(
                                timelineGlobalRefreshHandledToken0540,
                                token0540,
                            )
                        },
                        onGlobalRefreshBusy0540 = { busy0540 ->
                            val wasBusy0540 = timelineGlobalRefreshBusy0540
                            timelineGlobalRefreshBusy0540 = busy0540
                            if (wasBusy0540 && !busy0540) {
                                refresh()
                                message = "📡 Atualização global finalizada."
                            }
                        },
                        onCanonicalRefreshState0499 = { refreshing0499, error0499 ->
                            if (!refreshing0499 && !error0499.isNullOrBlank()) {
                                message = "Atualização BlaBlaCar → Agenda concluída; sincronização canônica secundária indisponível: $error0499"
                            }
                        },
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
'''
activity = replace_span(activity, branch_start, branch_end, branch_new, "remove pull gesture and install fixed radar")

build = replace_once(build, 'versionCode = 5831', 'versionCode = 5832', "versionCode")
build = replace_once(build, 'versionName = "0.1.539"', 'versionName = "0.1.540"', "versionName")

write(UI, ui)
write(ACTIVITY, activity)
write(BUILD, build)

GLOBAL_TEST.write_text('''package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimelineGlobalRadar0540Test {
    private fun audit(
        pending: Boolean,
        status: BlaBlaCommandStatus0407 = if (pending) {
            BlaBlaCommandStatus0407.QUEUED
        } else {
            BlaBlaCommandStatus0407.VERIFIED_SUCCESS
        },
    ) = BlaBlaCommandAuditSnapshot0407(
        commandId = if (pending) "pending-command" else "terminal-command",
        status = status,
        requestedAtMillis = 1L,
        finishedAtMillis = if (pending) 0L else 2L,
        pending = pending,
    )

    @Test
    fun globalRadarStaysLockedWhileAnyTargetIsPending() {
        assertTrue(
            timelineGlobalRadarBatchBusy0540(
                listOf(audit(pending = false), audit(pending = true), audit(pending = false)),
            ),
        )
    }

    @Test
    fun globalRadarUnlocksOnlyWhenEveryTargetIsTerminal() {
        assertFalse(
            timelineGlobalRadarBatchBusy0540(
                listOf(audit(pending = false), audit(pending = false)),
            ),
        )
        assertFalse(timelineGlobalRadarBatchBusy0540(emptyList()))
    }
}
''', encoding="utf-8")

final_ui = read(UI)
final_activity = read(ACTIVITY)
final_passenger = read(PASSENGER_UI)

for marker in [
    'TIMELINE_GLOBAL_RADAR_BATCH_STARTED_0540',
    'TIMELINE_GLOBAL_RADAR_BATCH_COMPLETE_0540',
    'timelineGlobalRadarBatchBusy0540',
    'origin = BlaBlaCommandOrigin0407.SYSTEM_RECONCILIATION',
    'origin = BlaBlaCommandOrigin0407.CARD',
    'AgendaBackgroundSync0392.enqueueTripCollectorRefresh0517(',
]:
    if marker not in final_ui:
        fail(f"Final Timeline invariant missing: {marker}")
for marker in [
    '📡 Atualizar todas',
    '📡 Atualizando todas…',
    'AGENDA_TIMELINE_GLOBAL_RADAR_REFRESH_0540',
    'lastHandledGlobalRefreshToken0540 = timelineGlobalRefreshHandledToken0540',
]:
    if marker not in final_activity:
        fail(f"Fixed global radar marker missing: {marker}")
if 'TimelineRefreshGestureSurface0388(' in final_activity:
    fail("Pull-to-refresh gesture is still active in Timeline")
if 'AGENDA_PULL_GESTURE_DOWN_0390' in final_activity or 'AGENDA_PULL_GESTURE_DECISION_0390' in final_activity:
    fail("Pull gesture diagnostics still wired into active Timeline")
if final_ui.count('AgendaBackgroundSync0392.enqueueTripCollectorRefresh0517(') < 2:
    fail("Per-card and global radar collector paths are not both present")
if "Carregando passageiros" in final_passenger:
    fail("Passenger loading text regression detected")

print("Timeline 0.1.540 fixed global radar with durable completion lock materialized successfully")
