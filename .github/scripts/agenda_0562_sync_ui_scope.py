from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TIMELINE = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt"
BUILD = ROOT / "app/build.gradle.kts"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


text = TIMELINE.read_text()

text = replace_once(
    text,
    '''internal fun timelineGlobalRadarBatchBusy0540(
    audits: Iterable<BlaBlaCommandAuditSnapshot0407?>,
): Boolean = audits.any { it?.pending == true }
''',
    '''/**
 * Legacy raw audit helper retained for source compatibility only. User-facing global state must
 * use the scoped overload below so an unrelated card command cannot become ALL_TRIPS.
 */
internal fun timelineGlobalRadarBatchBusy0540(
    audits: Iterable<BlaBlaCommandAuditSnapshot0407?>,
): Boolean = audits.any { it?.pending == true }

internal fun timelineGlobalRadarBatchBusy0540(
    scope: AgendaSyncUiScope0562,
    audits: Iterable<BlaBlaCommandAuditSnapshot0407?>,
): Boolean = agendaGlobalSyncBusy0562(scope, audits)
''',
    "scoped global busy helper",
)

text = replace_once(
    text,
    '''    val canonicalRefreshStateCallback0499 = androidx.compose.runtime.rememberUpdatedState(onCanonicalRefreshState0499)
    val globalRefreshStartedCallback0540 = androidx.compose.runtime.rememberUpdatedState(onGlobalRefreshStarted0540)
    val globalRefreshBusyCallback0540 = androidx.compose.runtime.rememberUpdatedState(onGlobalRefreshBusy0540)
''',
    '''    val canonicalRefreshStateCallback0499 = androidx.compose.runtime.rememberUpdatedState(onCanonicalRefreshState0499)
    val globalRefreshStartedCallback0540 = androidx.compose.runtime.rememberUpdatedState(onGlobalRefreshStarted0540)
    val globalRefreshBusyCallback0540 = androidx.compose.runtime.rememberUpdatedState(onGlobalRefreshBusy0540)

    // Persist only explicit USER ALL-TRIPS ownership. Network, canonical, polling and card-level
    // activity never write these fields. The typed scope is derived from saveable primitives so
    // Activity recreation cannot turn infrastructure activity into a global manual indicator.
    var activeGlobalOperationId0562 by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf("")
    }
    var activeGlobalCommandIds0562 by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(arrayListOf<String>())
    }
    var activeGlobalTrigger0562 by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf("")
    }
    var activeGlobalCommandRevisionAtStart0562 by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(0L)
    }
    val manualSyncUiScope0562: AgendaSyncUiScope0562 = if (activeGlobalOperationId0562.isBlank()) {
        AgendaSyncUiScope0562.None
    } else {
        AgendaSyncUiScope0562.AllTrips(
            operationId = activeGlobalOperationId0562,
            commandIds = activeGlobalCommandIds0562.toSet(),
            trigger = activeGlobalTrigger0562.ifBlank { "REFRESH_ALL" },
            commandRevisionAtStart = activeGlobalCommandRevisionAtStart0562,
        )
    }
''',
    "typed global scope state",
)

old_global = '''    LaunchedEffect(manualRefreshToken0499) {
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
new_global = '''    LaunchedEffect(manualRefreshToken0499) {
        if (
            manualRefreshToken0499 <= 0 ||
            manualRefreshToken0499 <= lastHandledGlobalRefreshToken0540
        ) return@LaunchedEffect

        val operationId0562 = "all-${manualRefreshToken0499}-${System.nanoTime().toString(36)}"
        val revisionAtStart0562 = commandRevision0407
        activeGlobalOperationId0562 = operationId0562
        activeGlobalCommandIds0562 = arrayListOf()
        activeGlobalTrigger0562 = "REFRESH_ALL"
        activeGlobalCommandRevisionAtStart0562 = revisionAtStart0562
        globalRefreshStartedCallback0540.value(manualRefreshToken0499)
        globalRefreshBusyCallback0540.value(true)

        val resolvedTargets0540 = tripTargetsByCard0432.values.filterNotNull()
        val targets0540 = distinctTimelineGlobalPullTargets0538(resolvedTargets0540)
        val statusStore0540 = BlaBlaTripCommandStatusStore0407(context)
        val alreadyPending0540 = targets0540.count { statusStore0540.get(it)?.pending == true }

        fun finishWithoutGlobalWork0562(result: String) {
            activeGlobalOperationId0562 = ""
            activeGlobalCommandIds0562 = arrayListOf()
            activeGlobalTrigger0562 = ""
            activeGlobalCommandRevisionAtStart0562 = 0L
            globalRefreshBusyCallback0540.value(false)
            UnifiedDebugEventStore.record(
                "AGENDA_SYNC_UI_STATE_CHANGED",
                context.packageName,
                "scope=IDLE trigger=REFRESH_ALL operationId=${seatSyncDiagnosticKey(operationId0562)} result=$result privateValuesLogged=false",
            )
        }

        if (targets0540.isEmpty()) {
            UnifiedDebugEventStore.record(
                "TIMELINE_GLOBAL_RADAR_EMPTY_0540",
                context.packageName,
                "cards=${tripTargetsByCard0432.size} resolvedTargets=0 strongTargets=0 noCollectorWork=true",
            )
            finishWithoutGlobalWork0562("EMPTY")
            return@LaunchedEffect
        }

        if (alreadyPending0540 > 0) {
            UnifiedDebugEventStore.record(
                "TIMELINE_GLOBAL_RADAR_BLOCKED_0540",
                context.packageName,
                "strongTargets=${targets0540.size} pendingTargets=$alreadyPending0540 reason=previous_target_refresh_in_progress",
            )
            // A pre-existing SINGLE_TRIP operation must never be absorbed into a new ALL_TRIPS
            // visual operation. The global request is rejected instead of exaggerating its scope.
            finishWithoutGlobalWork0562("BLOCKED_BY_EXISTING_TARGET")
            return@LaunchedEffect
        }

        var acceptedTargets0540 = 0
        val acceptedCommandIds0562 = linkedSetOf<String>()
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
                acceptedCommandIds0562 += command0540.commandId
            }
        }
        activeGlobalCommandIds0562 = ArrayList(acceptedCommandIds0562)
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
            finishWithoutGlobalWork0562("NO_TARGET_ACCEPTED")
            return@LaunchedEffect
        }
        UnifiedDebugEventStore.record(
            "AGENDA_SYNC_UI_STATE_CHANGED",
            context.packageName,
            "scope=ALL_TRIPS trigger=REFRESH_ALL operationId=${seatSyncDiagnosticKey(operationId0562)} commandCount=${acceptedCommandIds0562.size} result=STARTED privateValuesLogged=false",
        )
        invalidateCanonicalTimeline0495("USER_GLOBAL_RADAR_REFRESH_0540")
    }
'''
text = replace_once(text, old_global, new_global, "global refresh ownership")

old_busy = '''    val globalRadarBusyFromStore0540 = remember(commandAuditsByCard0432) {
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
new_busy = '''    val globalRadarBusyFromStore0540 = remember(commandAuditsByCard0432, manualSyncUiScope0562) {
        timelineGlobalRadarBatchBusy0540(
            scope = manualSyncUiScope0562,
            audits = commandAuditsByCard0432.values,
        )
    }
    LaunchedEffect(commandRevision0407, manualSyncUiScope0562) {
        val globalScope0562 = manualSyncUiScope0562 as? AgendaSyncUiScope0562.AllTrips
            ?: return@LaunchedEffect
        if (
            globalScope0562.commandIds.isEmpty() ||
            commandRevision0407 <= globalScope0562.commandRevisionAtStart
        ) return@LaunchedEffect

        globalRefreshBusyCallback0540.value(globalRadarBusyFromStore0540)
        if (!globalRadarBusyFromStore0540) {
            UnifiedDebugEventStore.record(
                "TIMELINE_GLOBAL_RADAR_BATCH_COMPLETE_0540",
                context.packageName,
                "strongTargets=${distinctTimelineGlobalPullTargets0538(tripTargetsByCard0432.values).size} pendingTargets=0 collectorToAgenda=true directTimelineCollectorRead=false",
            )
            UnifiedDebugEventStore.record(
                "AGENDA_SYNC_UI_STATE_CHANGED",
                context.packageName,
                "scope=IDLE trigger=${globalScope0562.trigger} operationId=${seatSyncDiagnosticKey(globalScope0562.operationId)} result=SUCCESS privateValuesLogged=false",
            )
            activeGlobalOperationId0562 = ""
            activeGlobalCommandIds0562 = arrayListOf()
            activeGlobalTrigger0562 = ""
            activeGlobalCommandRevisionAtStart0562 = 0L
            invalidateCanonicalTimeline0495("USER_GLOBAL_RADAR_REFRESH_0540_COMPLETE")
        }
    }
'''
text = replace_once(text, old_busy, new_busy, "global completion membership")

text = replace_once(
    text,
    '''                        passiveCommandAudit0407 = commandAuditsByCard0432[timelineLazyItemKey0380(entry)],
                        focusedBookingId = focusedBookingId,
''',
    '''                        passiveCommandAudit0407 = commandAuditsByCard0432[timelineLazyItemKey0380(entry)],
                        activeGlobalCommandIds0562 = (manualSyncUiScope0562 as? AgendaSyncUiScope0562.AllTrips)
                            ?.commandIds
                            .orEmpty(),
                        focusedBookingId = focusedBookingId,
''',
    "card global membership argument",
)

text = replace_once(
    text,
    '''    passiveSeatCapabilityState0407: BlaBlaPublicationSeatSyncState?,
    passiveCommandAudit0407: BlaBlaCommandAuditSnapshot0407?,
    focusedBookingId: String? = null,
''',
    '''    passiveSeatCapabilityState0407: BlaBlaPublicationSeatSyncState?,
    passiveCommandAudit0407: BlaBlaCommandAuditSnapshot0407?,
    activeGlobalCommandIds0562: Set<String> = emptySet(),
    focusedBookingId: String? = null,
''',
    "card signature global membership",
)

text = replace_once(
    text,
    '''    val commandAudit0407 = passiveCommandAudit0407
    val reverifyPending0407 = commandAudit0407?.pending == true
    val lastObservedAt0407 = trip?.lastObservedAtMillis ?: 0L
''',
    '''    val commandAudit0407 = passiveCommandAudit0407
    val cardSyncUiScope0562 = remember(tripTarget0407, commandAudit0407, activeGlobalCommandIds0562) {
        agendaSingleTripScope0562(
            target = tripTarget0407,
            audit = commandAudit0407,
            activeGlobalCommandIds = activeGlobalCommandIds0562,
        )
    }
    val reverifyPending0407 = commandAudit0407?.pending == true
    var lastSingleTripUiOperation0562 by remember(entry.tripId) { mutableStateOf<String?>(null) }
    LaunchedEffect(cardSyncUiScope0562, commandAudit0407?.status) {
        when (val scope0562 = cardSyncUiScope0562) {
            is AgendaSyncUiScope0562.SingleTrip -> {
                if (lastSingleTripUiOperation0562 != scope0562.operationId) {
                    lastSingleTripUiOperation0562 = scope0562.operationId
                    UnifiedDebugEventStore.record(
                        "AGENDA_SYNC_UI_STATE_CHANGED",
                        context.packageName,
                        "scope=SINGLE_TRIP tripIdentityPresent=true " +
                            "tripKey=${seatSyncDiagnosticKey(scope0562.tripIdentity.strongIdentityKey)} " +
                            "trigger=EXACT_CARD_REFRESH operationId=${seatSyncDiagnosticKey(scope0562.operationId)} " +
                            "result=STARTED privateValuesLogged=false",
                    )
                }
            }
            AgendaSyncUiScope0562.None -> {
                val previousOperation0562 = lastSingleTripUiOperation0562
                if (
                    previousOperation0562 != null &&
                    commandAudit0407?.commandId == previousOperation0562 &&
                    commandAudit0407.pending != true
                ) {
                    UnifiedDebugEventStore.record(
                        "AGENDA_SYNC_UI_STATE_CHANGED",
                        context.packageName,
                        "scope=IDLE trigger=EXACT_CARD_REFRESH " +
                            "operationId=${seatSyncDiagnosticKey(previousOperation0562)} " +
                            "durationMs=${(commandAudit0407.finishedAtMillis - commandAudit0407.requestedAtMillis).coerceAtLeast(0L)} " +
                            "result=${commandAudit0407.status.name} privateValuesLogged=false",
                    )
                    lastSingleTripUiOperation0562 = null
                }
            }
            is AgendaSyncUiScope0562.AllTrips -> Unit
        }
    }
    val lastObservedAt0407 = trip?.lastObservedAtMillis ?: 0L
''',
    "single-trip typed card state",
)

text = replace_once(
    text,
    '''            ) {
                onChanged("📡 Agenda buscando somente esta viagem na BlaBlaCar em segundo plano.")
            } else {
''',
    '''            ) {
                UnifiedDebugEventStore.record(
                    "AGENDA_EXACT_CARD_SYNC_STARTED",
                    context.packageName,
                    "directTarget=true scope=SINGLE_TRIP tripIdentityPresent=true " +
                        "tripKey=${seatSyncDiagnosticKey(target.strongIdentityKey)} " +
                        "operationId=${seatSyncDiagnosticKey(command.commandId)} trigger=EXACT_CARD_REFRESH privateValuesLogged=false",
                )
                onChanged("📡 Agenda buscando somente esta viagem na BlaBlaCar em segundo plano.")
            } else {
''',
    "exact card start evidence",
)

TIMELINE.write_text(text)

build = BUILD.read_text()
build = replace_once(build, 'val releaseVersionCode = 5_852\n', 'val releaseVersionCode = 5_853\n', "version code")
build = replace_once(build, 'val releaseVersionName = "0.1.561"\n', 'val releaseVersionName = "0.1.562"\n', "version name")
BUILD.write_text(build)
