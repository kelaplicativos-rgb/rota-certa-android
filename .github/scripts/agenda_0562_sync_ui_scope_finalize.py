from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TIMELINE = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt"
HISTORY = ROOT / "app/src/main/assets/release_history.json"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


text = TIMELINE.read_text()

old_global_busy = '''    val globalRadarBusyFromStore0540 = remember(commandAuditsByCard0432, manualSyncUiScope0562) {
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
new_global_busy = '''    LaunchedEffect(commandRevision0407, manualSyncUiScope0562) {
        val globalScope0562 = manualSyncUiScope0562 as? AgendaSyncUiScope0562.AllTrips
            ?: return@LaunchedEffect
        if (globalScope0562.commandIds.isEmpty()) return@LaunchedEffect

        // Read durable command status again at the decision boundary. This avoids a stale Compose
        // snapshot prematurely completing ALL_TRIPS and also makes Activity recreation/foreground
        // restoration independent from the process-local revision counter.
        val statusStore0562 = BlaBlaTripCommandStatusStore0407(context)
        val freshGlobalAudits0562 = tripTargetsByCard0432.values
            .filterNotNull()
            .map(statusStore0562::get)
        val globalRadarBusyFromStore0540 = timelineGlobalRadarBatchBusy0540(
            scope = globalScope0562,
            audits = freshGlobalAudits0562,
        )
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
text = replace_once(text, old_global_busy, new_global_busy, "fresh durable global ownership")

old_card_scope = '''    val commandAudit0407 = passiveCommandAudit0407
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
'''
new_card_scope = '''    val commandAudit0407 = passiveCommandAudit0407
    var activeSingleTripOperationId0562 by androidx.compose.runtime.saveable.rememberSaveable(entry.tripId) {
        mutableStateOf("")
    }
    val cardSyncUiScope0562 = remember(
        tripTarget0407,
        commandAudit0407,
        activeSingleTripOperationId0562,
        activeGlobalCommandIds0562,
    ) {
        agendaSingleTripScope0562(
            target = tripTarget0407,
            audit = commandAudit0407,
            activeSingleTripOperationId = activeSingleTripOperationId0562,
            activeGlobalCommandIds = activeGlobalCommandIds0562,
        )
    }
    val singleTripRefreshPending0562 = cardSyncUiScope0562 is AgendaSyncUiScope0562.SingleTrip
    val reverifyPending0407 = commandAudit0407?.pending == true
    LaunchedEffect(
        activeSingleTripOperationId0562,
        commandAudit0407?.commandId,
        commandAudit0407?.pending,
        commandAudit0407?.status,
    ) {
        val operationId0562 = activeSingleTripOperationId0562
        val completedAudit0562 = commandAudit0407
        if (
            operationId0562.isNotBlank() &&
            completedAudit0562 != null &&
            completedAudit0562.commandId == operationId0562 &&
            !completedAudit0562.pending
        ) {
            UnifiedDebugEventStore.record(
                "AGENDA_SYNC_UI_STATE_CHANGED",
                context.packageName,
                "scope=IDLE trigger=EXACT_CARD_REFRESH " +
                    "operationId=${seatSyncDiagnosticKey(operationId0562)} " +
                    "durationMs=${(completedAudit0562.finishedAtMillis - completedAudit0562.requestedAtMillis).coerceAtLeast(0L)} " +
                    "result=${completedAudit0562.status.name} privateValuesLogged=false",
            )
            activeSingleTripOperationId0562 = ""
        }
    }
'''
text = replace_once(text, old_card_scope, new_card_scope, "explicit single-trip ownership")

old_exact_started = '''            ) {
                UnifiedDebugEventStore.record(
                    "AGENDA_EXACT_CARD_SYNC_STARTED",
                    context.packageName,
                    "directTarget=true scope=SINGLE_TRIP tripIdentityPresent=true " +
                        "tripKey=${seatSyncDiagnosticKey(target.strongIdentityKey)} " +
                        "operationId=${seatSyncDiagnosticKey(command.commandId)} trigger=EXACT_CARD_REFRESH privateValuesLogged=false",
                )
                onChanged("📡 Agenda buscando somente esta viagem na BlaBlaCar em segundo plano.")
            } else {
'''
new_exact_started = '''            ) {
                activeSingleTripOperationId0562 = command.commandId
                UnifiedDebugEventStore.record(
                    "AGENDA_EXACT_CARD_SYNC_STARTED",
                    context.packageName,
                    "directTarget=true scope=SINGLE_TRIP tripIdentityPresent=true " +
                        "tripKey=${seatSyncDiagnosticKey(target.strongIdentityKey)} " +
                        "operationId=${seatSyncDiagnosticKey(command.commandId)} trigger=EXACT_CARD_REFRESH privateValuesLogged=false",
                )
                UnifiedDebugEventStore.record(
                    "AGENDA_SYNC_UI_STATE_CHANGED",
                    context.packageName,
                    "scope=SINGLE_TRIP tripIdentityPresent=true " +
                        "tripKey=${seatSyncDiagnosticKey(target.strongIdentityKey)} " +
                        "trigger=EXACT_CARD_REFRESH operationId=${seatSyncDiagnosticKey(command.commandId)} " +
                        "result=STARTED privateValuesLogged=false",
                )
                onChanged("📡 Agenda buscando somente esta viagem na BlaBlaCar em segundo plano.")
            } else {
'''
text = replace_once(text, old_exact_started, new_exact_started, "single-trip accepted ownership")

text = replace_once(
    text,
    'Text(if (reverifyPending0407) "📡 …" else "📡")',
    'Text(if (singleTripRefreshPending0562) "📡 …" else "📡")',
    "single-trip card indicator",
)

TIMELINE.write_text(text)

history = HISTORY.read_text()
if '"version": "0.1.562"' in history:
    raise SystemExit("release history already contains 0.1.562")
anchor = '  "releases": [\n'
entry = '''    {
      "version": "0.1.562",
      "build": 5853,
      "commit": null,
      "branch": "agent/agenda-sync-visual-scope-0.1.562",
      "generatedAt": null,
      "status": "EM_VALIDACAO",
      "implemented": [
        "Estado visual tipado de sincronização da Agenda distingue NONE, SINGLE_TRIP e ALL_TRIPS sem inferir escopo por tela, quantidade de cards ou atividade de rede.",
        "Atualização global passa a possuir explicitamente somente os commandIds aceitos pela ação Atualizar todas; atualização individual possui o commandId exato iniciado pelo card."
      ],
      "fixed": [
        "Comando pendente de uma única viagem deixa de promover o cabeçalho da Timeline para 📡 Atualizando todas….",
        "Polling, refresh canônico, reconciliação e atividade de backend deixam de controlar o indicador manual global apenas por estarem pendentes."
      ],
      "improved": [
        "Conclusão global consulta o status durável dos commandIds pertencentes à operação e ignora comandos concorrentes não pertencentes ao lote.",
        "Logs sanitizados correlacionam início e término do escopo manual por operationId e hash da identidade forte, sem registrar dados privados."
      ],
      "regressions": [],
      "resolvedRegressions": [
        "0.1.561: atualizar um único card podia exibir incorretamente 📡 Atualizando todas… no cabeçalho."
      ],
      "modulesAffected": ["Agenda", "Timeline", "Sincronização", "Observabilidade", "Build"],
      "detailsComplete": true
    },
'''
if history.count(anchor) != 1:
    raise SystemExit("release history anchor is not unique")
HISTORY.write_text(history.replace(anchor, anchor + entry, 1))
