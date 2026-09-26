package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import br.com.mapeiaia.rotacerta.FarolMaximumForensicsStage38
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * Exporter for the Agenda black box. Normal operation only records compact
 * events into UnifiedDebugEventStore; expensive grouping, pairing, metrics and
 * text assembly happen here after the user explicitly asks for a report.
 */
internal object AgendaForensicReportBuilder {
    private val frozenSnapshot = AtomicReference<UnifiedDebugEventStore.Snapshot?>(null)

    fun freezeSnapshot() {
        frozenSnapshot.set(UnifiedDebugEventStore.snapshot())
    }

    fun clearFrozen() {
        frozenSnapshot.set(null)
    }

    fun build(context: Context): String {
        val snapshot = frozenSnapshot.getAndSet(null) ?: UnifiedDebugEventStore.snapshot()
        val agendaEvents = snapshot.events.filter(::isAgendaEvent)
        val traces = agendaEvents.groupBy { eventTrace(it) }
        val summarySelection = selectLatestAgendaTrace0493(agendaEvents)
        val summaryEvents = summarySelection.events
        val backgroundEvents = agendaEvents.filter { event ->
            val trace = eventTrace(event)
            trace == "sem-trace" || trace == "trace_unavailable" || !trace.startsWith("ag-")
        }
        val operationStarts = summaryEvents
            .filter { it.stage == "OPERATION_START" }
            .associateBy { detail(it, "operationId") }
            .filterKeys(String::isNotBlank)
        val operationTerminals = summaryEvents
            .filter { it.stage in setOf("OPERATION_END", "OPERATION_ERROR", "OPERATION_CANCELLED") }
            .associateBy { detail(it, "operationId") }
            .filterKeys(String::isNotBlank)

        val completed = operationStarts.mapNotNull { (operationId, start) ->
            val terminal = operationTerminals[operationId] ?: return@mapNotNull null
            val durationMs = detailLong(terminal, "durationMs")
                ?: ((terminal.monotonicNs - start.monotonicNs).coerceAtLeast(0L) / 1_000_000L)
            CompletedOperation(
                name = detail(start, "operation").ifBlank { "UNKNOWN_OPERATION" },
                durationMs = durationMs,
                terminal = terminal.stage,
            )
        }
        val longestOperation = completed.maxByOrNull { it.durationMs }
        val incompleteOperations = operationStarts.keys - operationTerminals.keys

        val openRequested = summarySelection.openRequested
        val firstInteractive = summarySelection.firstInteractive
        val openDurationMs = if (summarySelection.inconsistent) {
            null
        } else {
            summaryEvents
                .lastOrNull { it.stage == "AGENDA_OPEN_TOTAL_MS" }
                ?.let { detailLong(it, "value") }
                ?: if (openRequested != null && firstInteractive != null) {
                    ((firstInteractive.monotonicNs - openRequested.monotonicNs).coerceAtLeast(0L) / 1_000_000L)
                } else {
                    null
                }
        }

        val capacityInitial = summaryEvents.firstOrNull { it.stage == "CAPACITY_INITIAL_STATE" }
        val localSettingsCapacity = summaryEvents.firstOrNull {
            it.stage in setOf("INVENTORY_LOCAL_SETTINGS_RECEIVED", "CAPACITY_LOCAL_SETTINGS_RECEIVED")
        }
        val resolvedCapacity = summaryEvents.firstOrNull {
            it.stage == "TIMELINE_CAPACITY_RESOLVED" &&
                (detailLong(it, "operationalInventory") ?: -1L) >= 0L &&
                (detailLong(it, "availableSeats") ?: -1L) >= 0L
        }
        val capacityFirstValue = resolvedCapacity
        val capacityDelayMs = if (openRequested != null && resolvedCapacity != null) {
            ((resolvedCapacity.monotonicNs - openRequested.monotonicNs).coerceAtLeast(0L) / 1_000_000L)
        } else {
            null
        }

        val jankDurations = summaryEvents
            .filter { it.stage.startsWith("AGENDA_JANK_") }
            .mapNotNull { detailLong(it, "durationMs") }
        val worstFrame = jankDurations.maxOrNull() ?: 0L
        val over100 = jankDurations.count { it >= 100L }
        val over250 = jankDurations.count { it >= 250L }
        val over500 = jankDurations.count { it >= 500L }
        val emptyDurations = summaryEvents
            .filter { it.stage == "AGENDA_EMPTY_VISUAL_STATE_END" }
            .mapNotNull { detailLong(it, "durationMs") }
        val emptyWorstMs = emptyDurations.maxOrNull() ?: 0L
        val emptyVisual = summaryEvents.any { it.stage == "AGENDA_EMPTY_VISUAL_STATE" }
        val emptyVisualLong = summaryEvents.any { it.stage == "AGENDA_EMPTY_VISUAL_STATE_LONG" } || emptyWorstMs >= 500L
        val emptyVisualClass = when {
            !emptyVisual -> "NONE"
            emptyVisualLong -> "EMPTY_PROLONGED"
            else -> "EMPTY_TRANSIENT"
        }
        val errors = summaryEvents.count { it.stage == "OPERATION_ERROR" }
        val failureEvidenceEvents = dedupeFailureEvidence(summaryEvents.filter(::isFailureEvidenceEvent))
        val byteLevelFailures = failureEvidenceEvents.count { event ->
            structuredDetail(event, "requestBytes").isNotBlank() ||
                structuredDetail(event, "responseBytes").isNotBlank() ||
                structuredDetail(event, "networkCallId").isNotBlank()
        }
        val cancelled = summaryEvents.count { it.stage == "OPERATION_CANCELLED" }
        val retries = summaryEvents.count { "RETRY" in it.stage }
        val largestGapMs = summaryEvents.sortedBy { it.monotonicNs }.zipWithNext()
            .maxOfOrNull { (left, right) ->
                ((right.monotonicNs - left.monotonicNs).coerceAtLeast(0L) / 1_000_000L)
            } ?: 0L
        val crashEvidence = runCatching { AgendaSyncCrashTraceStore.export(context) }.getOrDefault("")
        val processTerminatedDuringOperation =
            crashEvidence.contains("OPERATION_INCOMPLETE_DUE_PROCESS_TERMINATION=true")

        val alerts = buildAlerts(
            agendaEvents = summaryEvents,
            longestOperation = longestOperation,
            capacityDelayMs = capacityDelayMs,
            over500 = over500,
            worstFrame = worstFrame,
            emptyVisual = emptyVisual,
            emptyVisualLong = emptyVisualLong,
            errors = errors,
            cancelled = cancelled,
            retries = retries,
            incompleteCount = incompleteOperations.size,
        )
        val stage38 = FarolMaximumForensicsStage38.snapshot()

        return buildString {
            appendLine("--- RESUMO FORENSE DA AGENDA ---")
            appendLine("Sessão Agenda:")
            appendLine("- summaryTraceId=${summarySelection.traceId.ifBlank { "não disponível" }}")
            appendLine("- traceComplete=${summarySelection.complete}")
            appendLine("- mixedTraceDetected=${summarySelection.inconsistent}")
            appendLine("- summaryStart=${summarySelection.openRequested?.let { formatWall(it.atMillis) } ?: "não registrado"}")
            appendLine("- summaryEnd=${summarySelection.lastEvent?.let { formatWall(it.atMillis) } ?: "não registrado"}")
            if (summarySelection.inconsistent) {
                appendLine("- FORENSIC_TRACE_INCONSISTENT: evento do trace possui monotonic timestamp anterior à abertura; duração cruzada não será calculada")
            }
            appendLine("- abertura solicitada: ${openRequested?.let { formatWall(it.atMillis) } ?: "não registrada"}")
            appendLine("- primeiro frame utilizável: ${firstInteractive?.let { formatWall(it.atMillis) } ?: "não registrado"}")
            appendLine("- duração de abertura: ${openDurationMs?.let { "$it ms" } ?: "não calculada"}")
            appendLine(
                "- maior operação interna: " +
                    (longestOperation?.let { "${it.name} — ${it.durationMs} ms" } ?: "não registrada"),
            )
            appendLine(
                "- capacidade inicial/settings: " +
                    if (capacityInitial == null) "não registrada"
                    else if (detail(capacityInitial, "valuePresent") == "true") detail(capacityInitial, "value").ifBlank { "presente" }
                    else "aguardando settings locais",
            )
            appendLine(
                "- settings locais carregados: " +
                    (localSettingsCapacity?.let { event ->
                        "sim • rotaCertaAllocation=" + detail(event, "value").ifBlank {
                            detail(event, "rotaCertaAllocation").ifBlank { "registrada" }
                        }
                    } ?: "não registrado"),
            )
            appendLine(
                "- capacidade/inventário operacional resolvido: " +
                    (resolvedCapacity?.let { event ->
                        "operationalInventory=" + detail(event, "operationalInventory") +
                            " • availableSeats=" + detail(event, "availableSeats") +
                            " • source=" + detail(event, "capacitySource").ifBlank { "canônica por viagem" }
                    } ?: "não registrado"),
            )
            appendLine(
                "- capacidade recebida: " +
                    (capacityFirstValue?.let { detail(it, "operationalInventory").ifBlank { "presente" } } ?: "não registrada"),
            )
            appendLine("- primeira capacidade válida: ${resolvedCapacity?.let { formatWall(it.atMillis) } ?: "não registrada"}")
            appendLine("- atraso desde AGENDA_OPEN_REQUESTED até capacidade válida: ${capacityDelayMs?.let { "$it ms" } ?: "não calculado"}")
            appendLine("- frames >100 ms: $over100")
            appendLine("- frames >250 ms: $over250")
            appendLine("- frames >500 ms: $over500")
            appendLine("- pior frame: $worstFrame ms")
            appendLine("- estado visual vazio: $emptyVisualClass" + if (emptyVisual) " • pior duração=${emptyWorstMs} ms" else "")
            appendLine("- tela vazia prolongada: ${if (emptyVisualLong) "sim" else "não"}")
            appendLine("- operações com erro: $errors")
            appendLine("- evidências estruturadas de falha: ${failureEvidenceEvents.size}")
            appendLine("- falhas com envelope byte a byte: $byteLevelFailures")
            appendLine("- operações canceladas: $cancelled")
            appendLine("- retries externos: $retries")
            appendLine("- operations START sem conclusão: ${incompleteOperations.size}")
            appendLine("- encerramento de processo durante operação: ${if (processTerminatedDuringOperation) "sim" else "não"}")
            appendLine("- maior intervalo causal sem checkpoint: $largestGapMs ms")
            appendLine()
            appendLine("Alertas automáticos:")
            if (alerts.isEmpty()) appendLine("- nenhum alerta diagnóstico") else alerts.forEach { appendLine("- $it") }
            appendLine()
            appendLine("--- EVIDÊNCIAS ESTRUTURADAS DE FALHA ---")
            if (failureEvidenceEvents.isEmpty()) {
                appendLine("(nenhuma falha estruturada registrada nesta sessão)")
            } else {
                failureEvidenceEvents.sortedBy { it.monotonicNs }.forEach { event ->
                    append(formatWall(event.atMillis))
                    append(" | ").append(event.stage)
                    val trace = structuredDetail(event, "traceId")
                    if (trace.isNotBlank()) append(" | trace=").append(trace)
                    val operationId = structuredDetail(event, "operationId")
                    if (operationId.isNotBlank()) append(" | op=").append(operationId)
                    append(" | ").append(event.details)
                    appendLine()
                }
            }
            appendLine()
            appendLine("Custo do próprio debug:")
            appendLine("debugEventsRecorded=${snapshot.recordCalls}")
            appendLine("debugEventsDropped=${snapshot.droppedEvents}")
            appendLine("debugBufferCapacity=${snapshot.bufferCapacity}")
            appendLine("debugRecordMedianNs=${snapshot.recordMedianNs}")
            appendLine("debugRecordP95Ns=${snapshot.recordP95Ns}")
            appendLine("debugRecordMaxNs=${snapshot.recordMaxNs}")
            appendLine("debugTotalOverheadNs=${snapshot.recordOverheadTotalNs}")
            appendLine("agendaEventsInSnapshot=${agendaEvents.size}")
            appendLine("Stage38.events=${stage38.events.size}")
            appendLine("Stage38.dropped=${stage38.dropped}")
            appendLine("Stage38.recordCalls=${stage38.recordCalls}")
            appendLine("Stage38.recordOverheadTotalNs=${stage38.recordOverheadTotalNs}")
            appendLine("Stage38.recordOverheadMaxNs=${stage38.recordOverheadMaxNs}")
            appendLine()
            appendLine("--- BACKGROUND / GLOBAL OPERATIONS ---")
            val backgroundStarts = backgroundEvents
                .filter { it.stage == "OPERATION_START" }
                .associateBy { detail(it, "operationId") }
                .filterKeys(String::isNotBlank)
            val backgroundTerminals = backgroundEvents
                .filter { it.stage in setOf("OPERATION_END", "OPERATION_ERROR", "OPERATION_CANCELLED") }
                .associateBy { detail(it, "operationId") }
                .filterKeys(String::isNotBlank)
            val backgroundCompleted = backgroundStarts.mapNotNull { (operationId, start) ->
                val terminal = backgroundTerminals[operationId] ?: return@mapNotNull null
                val duration = detailLong(terminal, "durationMs")
                    ?: ((terminal.monotonicNs - start.monotonicNs).coerceAtLeast(0L) / 1_000_000L)
                CompletedOperation(
                    name = detail(start, "operation").ifBlank { "UNKNOWN_OPERATION" },
                    durationMs = duration,
                    terminal = terminal.stage,
                )
            }.sortedByDescending { it.durationMs }
            if (backgroundCompleted.isEmpty()) {
                appendLine("(nenhuma operação global concluída)")
            } else {
                backgroundCompleted.take(20).forEach { operation ->
                    appendLine("- ${operation.name}: ${operation.durationMs} ms • ${operation.terminal}")
                }
            }
            val backgroundFailures = dedupeFailureEvidence(backgroundEvents.filter(::isFailureEvidenceEvent))
            appendLine("- backgroundFailureEvidence=${backgroundFailures.size}")
            appendLine()
            appendLine("--- CADEIA CAUSAL DA AGENDA ---")
            if (traces.isEmpty()) {
                appendLine("(sem eventos da Agenda no snapshot)")
            } else {
                traces.toSortedMap().forEach { (trace, events) ->
                    appendLine("TRACE $trace")
                    events.sortedBy { it.monotonicNs }.forEach { event ->
                        append(formatWall(event.atMillis))
                        append(" | ").append(event.stage)
                        val op = detail(event, "operationId")
                        if (op.isNotBlank()) append(" | op=").append(op)
                        val parent = detail(event, "parentOperationId")
                        if (parent.isNotBlank()) append(" | parent=").append(parent)
                        val duration = detail(event, "durationMs")
                        if (duration.isNotBlank()) append(" | durationMs=").append(duration)
                        if (isFailureEvidenceEvent(event)) {
                            val fingerprint = structuredDetail(event, "failureFingerprint")
                            val root = structuredDetail(event, "rootCauseClass")
                            val phase = structuredDetail(event, "transportPhase")
                            val http = structuredDetail(event, "httpStatus")
                            val requestBytes = structuredDetail(event, "requestBytes")
                            val responseBytes = structuredDetail(event, "responseBytes")
                            if (fingerprint.isNotBlank()) append(" | failure=").append(fingerprint)
                            if (root.isNotBlank()) append(" | root=").append(root)
                            if (phase.isNotBlank()) append(" | phase=").append(phase)
                            if (http.isNotBlank()) append(" | http=").append(http)
                            if (requestBytes.isNotBlank()) append(" | requestBytes=").append(requestBytes)
                            if (responseBytes.isNotBlank()) append(" | responseBytes=").append(responseBytes)
                        }
                        appendLine()
                    }
                }
            }
            appendLine()
            appendLine("--- EVENTOS DETALHADOS DA AGENDA ---")
            if (agendaEvents.isEmpty()) {
                appendLine("(sem eventos detalhados da Agenda)")
            } else {
                agendaEvents.sortedBy { it.monotonicNs }.forEach { event ->
                    append(formatWall(event.atMillis))
                    append(" | mono_ns=").append(event.monotonicNs)
                    append(" | thread=").append(event.threadName)
                    append(" | ").append(event.stage)
                    append(" | pacote=").append(event.packageName)
                    if (event.details.isNotBlank()) append(" | ").append(event.details)
                    appendLine()
                }
            }
        }.trimEnd()
    }

    private fun buildAlerts(
        agendaEvents: List<UnifiedDebugEventStore.SnapshotEvent>,
        longestOperation: CompletedOperation?,
        capacityDelayMs: Long?,
        over500: Int,
        worstFrame: Long,
        emptyVisual: Boolean,
        emptyVisualLong: Boolean,
        errors: Int,
        cancelled: Int,
        retries: Int,
        incompleteCount: Int,
    ): List<String> = buildList {
        if ((longestOperation?.durationMs ?: 0L) >= 1_000L) {
            add("SLOW_OPERATION operation=${longestOperation?.name} durationMs=${longestOperation?.durationMs}")
        }
        if (worstFrame >= 1_000L) add("UI_FREEZE durationMs=$worstFrame")
        if (emptyVisualLong) add("EMPTY_PROLONGED")
        if ((capacityDelayMs ?: 0L) >= 1_000L) {
            add("LATE_STATE_DELIVERY source=capacity delayMs=$capacityDelayMs")
            add("CAPACITY_LATE_RENDER delayMs=$capacityDelayMs")
        }
        if (incompleteCount > 0) add("START_WITHOUT_END count=$incompleteCount")
        if (retries > 1) add("REPEATED_RETRY count=$retries")
        if (agendaEvents.any { "SYNC" in it.stage && (it.stage.endsWith("_ERROR") || it.stage.endsWith("_FAILED")) }) {
            add("SYNC_ERROR")
        }
        if (cancelled > 0) add("CANCELLED_OPERATION count=$cancelled")
        if (over500 > 0) add("MAIN_THREAD_LONG_BLOCK framesOver500Ms=$over500")
        val creates = agendaEvents.count { it.stage == "TRIPS_ACTIVITY_ONCREATE_START" }
        if (creates > 1 && agendaEvents.any { it.stage == "AGENDA_FIRST_INTERACTIVE_FRAME" }) {
            add("ACTIVITY_RECREATED_DURING_OPEN creates=$creates")
        }
        val publicSyncDuration = agendaEvents
            .filter { it.stage == "PUBLIC_AGENDA_SYNC_END" }
            .mapNotNull { detailLong(it, "durationMs") }
            .maxOrNull() ?: 0L
        if (publicSyncDuration >= 5_000L) add("PUBLIC_AGENDA_SYNC_TOO_LONG durationMs=$publicSyncDuration")
        if (errors > 0) add("OPERATION_ERRORS count=$errors")
    }

    private fun isAgendaEvent(event: UnifiedDebugEventStore.SnapshotEvent): Boolean {
        val stage = event.stage
        return event.packageName.startsWith("br.com.mapeiaia.rotacerta.trips") ||
            stage.startsWith("AGENDA_") ||
            stage.startsWith("TIMELINE_") ||
            stage.startsWith("CAPACITY_") ||
            stage.startsWith("BOOKING_") ||
            stage.startsWith("BROWSER_") ||
            stage.startsWith("HARVEST_") ||
            stage.startsWith("MHTML_") ||
            stage.startsWith("PASSENGER_") ||
            stage.startsWith("DRIVER_NOTIFICATION_") ||
            stage.startsWith("PUBLIC_SEARCH_") ||
            stage.startsWith("PUBLIC_AGENDA_") ||
            stage.startsWith("PUBLIC_BOOKING_") ||
            stage.startsWith("PUBLIC_DRIVER_") ||
            stage.startsWith("PUBLIC_LOCAL_") ||
            stage.startsWith("PUBLIC_EXTERNAL_") ||
            stage.startsWith("PUBLIC_CAPACITY_") ||
            stage.startsWith("PUBLIC_LINK_REMOTE_") ||
            stage.startsWith("PUBLIC_ACCESS_") ||
            stage.startsWith("PUBLIC_PRIVATE_AUTH_") ||
            stage.startsWith("PUBLIC_PASSENGER_PORTAL_") ||
            stage.startsWith("PROFILE_SYNC_") ||
            stage.startsWith("PASSENGER_DIRECTORY_SYNC_") ||
            stage.startsWith("LOCAL_TRIP_") ||
            stage.startsWith("LOCAL_CAPACITY_") ||
            stage.startsWith("CONNECTED_ACCOUNTS_") ||
            stage.startsWith("EXTERNAL_TRIP_") ||
            stage.startsWith("EXTERNAL_CAPACITY_") ||
            stage.startsWith("PUBLIC_EXTERNAL_BINDING_") ||
            stage.startsWith("USER_") ||
            stage.startsWith("OPERATION_") ||
            stage == "SLOW_OPERATION"
    }

    private fun isFailureEvidenceEvent(event: UnifiedDebugEventStore.SnapshotEvent): Boolean {
        val stage = event.stage
        return event.details.contains("failureFingerprint=") ||
            stage == "AGENDA_FAILURE_EVIDENCE" ||
            stage == "OPERATION_ERROR" ||
            stage.endsWith("_FAILED") ||
            stage.contains("_FAILED_") ||
            stage == "PUBLIC_CAPACITY_FAIL_CLOSED"
    }

    private fun dedupeFailureEvidence(
        events: List<UnifiedDebugEventStore.SnapshotEvent>,
    ): List<UnifiedDebugEventStore.SnapshotEvent> {
        val selected = linkedMapOf<String, UnifiedDebugEventStore.SnapshotEvent>()
        events.sortedBy { it.monotonicNs }.forEach { event ->
            val fingerprint = structuredDetail(event, "failureFingerprint")
            val networkCallId = structuredDetail(event, "networkCallId")
            val key = when {
                fingerprint.isNotBlank() -> "fp:${fingerprint}"
                networkCallId.isNotBlank() -> "net:${networkCallId}"
                else -> "event:${event.stage}:${event.monotonicNs}"
            }
            val existing = selected[key]
            if (existing == null || event.details.length > existing.details.length) {
                selected[key] = event
            }
        }
        return selected.values.toList()
    }

    private fun structuredDetail(event: UnifiedDebugEventStore.SnapshotEvent, key: String): String {
        val pattern = Regex("(?:^|\\s)${Regex.escape(key)}=(?:\\\"([^\\\"]*)\\\"|([^\\s|]+))")
        val match = pattern.find(event.details) ?: return ""
        return match.groupValues.getOrNull(1).orEmpty().ifBlank {
            match.groupValues.getOrNull(2).orEmpty()
        }
    }

    private fun eventTrace(event: UnifiedDebugEventStore.SnapshotEvent): String =
        detail(event, "traceId").ifBlank { "sem-trace" }

    private fun detail(event: UnifiedDebugEventStore.SnapshotEvent, key: String): String {
        val match = Regex("(?:^|\\s)${Regex.escape(key)}=([^\\s|]+)").find(event.details) ?: return ""
        return match.groupValues[1]
    }

    private fun detailLong(event: UnifiedDebugEventStore.SnapshotEvent, key: String): Long? =
        detail(event, key).toLongOrNull()

    private fun formatWall(value: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS", Locale("pt", "BR")).format(Date(value))

    private data class CompletedOperation(
        val name: String,
        val durationMs: Long,
        val terminal: String,
    )
}

internal data class AgendaTraceSelection0493(
    val traceId: String = "",
    val events: List<UnifiedDebugEventStore.SnapshotEvent> = emptyList(),
    val openRequested: UnifiedDebugEventStore.SnapshotEvent? = null,
    val firstInteractive: UnifiedDebugEventStore.SnapshotEvent? = null,
    val lastEvent: UnifiedDebugEventStore.SnapshotEvent? = null,
    val complete: Boolean = false,
    val inconsistent: Boolean = false,
)

internal fun selectLatestAgendaTrace0493(
    events: List<UnifiedDebugEventStore.SnapshotEvent>,
): AgendaTraceSelection0493 {
    fun traceOf(event: UnifiedDebugEventStore.SnapshotEvent): String {
        val match = Regex("(?:^|\\s)traceId=([^\\s|]+)").find(event.details)
        return match?.groupValues?.getOrNull(1).orEmpty().ifBlank { "sem-trace" }
    }

    val candidates = events
        .groupBy(::traceOf)
        .mapNotNull { (traceId, traceEvents) ->
            if (traceId == "sem-trace" || traceId == "trace_unavailable" || !traceId.startsWith("ag-")) {
                return@mapNotNull null
            }
            val ordered = traceEvents.sortedBy { it.monotonicNs }
            val open = ordered.firstOrNull { it.stage == "AGENDA_OPEN_REQUESTED" } ?: return@mapNotNull null
            val interactive = ordered.firstOrNull {
                it.stage == "AGENDA_FIRST_INTERACTIVE_FRAME" && it.monotonicNs >= open.monotonicNs
            }
            val inconsistent = traceEvents.any { it.monotonicNs < open.monotonicNs }
            AgendaTraceSelection0493(
                traceId = traceId,
                events = ordered.filter { it.monotonicNs >= open.monotonicNs },
                openRequested = open,
                firstInteractive = interactive,
                lastEvent = ordered.lastOrNull(),
                complete = interactive != null,
                inconsistent = inconsistent,
            )
        }
    val selected = candidates
        .filter(AgendaTraceSelection0493::complete)
        .maxByOrNull { it.openRequested?.monotonicNs ?: Long.MIN_VALUE }
        ?: candidates.maxByOrNull { it.openRequested?.monotonicNs ?: Long.MIN_VALUE }
        ?: return AgendaTraceSelection0493()

    if (selected.inconsistent) {
        return selected.copy(
            firstInteractive = null,
            complete = false,
        )
    }
    return selected
}
