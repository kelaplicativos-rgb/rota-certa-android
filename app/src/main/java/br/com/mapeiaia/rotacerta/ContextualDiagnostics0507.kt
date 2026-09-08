package br.com.mapeiaia.rotacerta

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DiagnosticModule0507(val label: String) {
    ALL_TRIPS("Todas as viagens"),
    BLABLACAR("BlaBlaCar"),
    SCRIPTS("Scripts"),
    PUBLIC_QUERY("Consulta pública"),
    PASSENGERS("Passageiros"),
    INTEGRATIONS("Integrações"),
    SETTINGS("Configurações"),
    ASSISTANT("Assistente Rota Certa"),
    PUBLIC_AGENDA("Agenda Pública"),
    UNKNOWN("Desconhecido"),
}

enum class DiagnosticSeverity0507 {
    DEBUG,
    INFO,
    WARNING,
    ERROR,
}

data class DiagnosticEventContext0507(
    val parentModule: DiagnosticModule0507,
    val originModule: DiagnosticModule0507 = parentModule,
    val executorModule: DiagnosticModule0507 = originModule,
    val submodule: String = "",
    val component: String = "",
    val operation: String = "",
    val severity: DiagnosticSeverity0507 = DiagnosticSeverity0507.INFO,
    val correlationId: String = "",
    val traceId: String = "",
    val operationId: String = "",
    val parentOperationId: String = "",
    val entityType: String = "",
    val entityId: String = "",
    val result: String = "",
    val errorCode: String = "",
    val reason: String = "",
    val durationMs: Long? = null,
    val attempt: Int? = null,
    val retryable: Boolean? = null,
) {
    fun sanitized0507(): DiagnosticEventContext0507 = copy(
        submodule = safe0507(submodule, 96),
        component = safe0507(component, 120),
        operation = safe0507(operation, 120),
        correlationId = safe0507(correlationId, 160),
        traceId = safe0507(traceId, 160),
        operationId = safe0507(operationId, 160),
        parentOperationId = safe0507(parentOperationId, 160),
        entityType = safe0507(entityType, 80),
        entityId = safe0507(entityId, 220),
        result = safe0507(result, 80),
        errorCode = safe0507(errorCode, 120),
        reason = safe0507(reason, 320),
    )

    private fun safe0507(value: String, max: Int): String =
        UnifiedDebugEventStore.sanitizeForExport(value).take(max)
}

data class ContextualDiagnosticFilter0507(
    val sinceMillis: Long? = null,
    val severities: Set<DiagnosticSeverity0507> = DiagnosticSeverity0507.entries.toSet(),
    val operationQuery: String = "",
    val technicalIdQuery: String = "",
)

data class ContextualDiagnosticEvent0507(
    val atMillis: Long,
    val monotonicNs: Long,
    val stage: String,
    val details: String,
    val threadName: String,
    val context: DiagnosticEventContext0507,
)

object ContextualDebugReport0507 {
    fun select(
        snapshot: UnifiedDebugEventStore.Snapshot,
        module: DiagnosticModule0507,
        filter: ContextualDiagnosticFilter0507 = ContextualDiagnosticFilter0507(),
    ): List<UnifiedDebugEventStore.SnapshotEvent> {
        if (module == DiagnosticModule0507.UNKNOWN) return emptyList()
        val periodCandidates = snapshot.events.filter { event ->
            val diagnostic = event.diagnosticContext ?: return@filter false
            (filter.sinceMillis == null || event.atMillis >= filter.sinceMillis) &&
                diagnostic.severity in filter.severities
        }
        val seed = periodCandidates.filter { event ->
            val diagnostic = event.diagnosticContext ?: return@filter false
            diagnostic.parentModule == module || diagnostic.originModule == module
        }
        val causalIds = seed.flatMap { event ->
            val diagnostic = event.diagnosticContext ?: return@flatMap emptyList()
            listOf(diagnostic.correlationId, diagnostic.traceId)
        }.filter(String::isNotBlank).toSet()
        val operationIds = seed.flatMap { event ->
            val diagnostic = event.diagnosticContext ?: return@flatMap emptyList()
            listOf(diagnostic.operationId, diagnostic.parentOperationId)
        }.filter(String::isNotBlank).toSet()

        val contextual = periodCandidates.filter { event ->
            val diagnostic = event.diagnosticContext ?: return@filter false
            diagnostic.parentModule == module ||
                diagnostic.originModule == module ||
                diagnostic.correlationId.takeIf(String::isNotBlank) in causalIds ||
                diagnostic.traceId.takeIf(String::isNotBlank) in causalIds ||
                diagnostic.operationId.takeIf(String::isNotBlank) in operationIds ||
                diagnostic.parentOperationId.takeIf(String::isNotBlank) in operationIds
        }

        val operationNeedle = filter.operationQuery.trim().lowercase(Locale.ROOT)
        val idNeedle = filter.technicalIdQuery.trim().lowercase(Locale.ROOT)
        return contextual.filter { event ->
            val diagnostic = event.diagnosticContext ?: return@filter false
            val operationMatch = operationNeedle.isBlank() ||
                diagnostic.operation.lowercase(Locale.ROOT).contains(operationNeedle) ||
                event.stage.lowercase(Locale.ROOT).contains(operationNeedle)
            val idHaystack = listOf(
                diagnostic.correlationId,
                diagnostic.traceId,
                diagnostic.operationId,
                diagnostic.parentOperationId,
                diagnostic.entityId,
                event.details,
            ).joinToString(" ").lowercase(Locale.ROOT)
            operationMatch && (idNeedle.isBlank() || idHaystack.contains(idNeedle))
        }.sortedWith(
            compareByDescending<UnifiedDebugEventStore.SnapshotEvent> { it.atMillis }
                .thenByDescending { it.monotonicNs },
        )
    }

    fun present(event: UnifiedDebugEventStore.SnapshotEvent): ContextualDiagnosticEvent0507? {
        val diagnostic = event.diagnosticContext?.sanitized0507() ?: return null
        return ContextualDiagnosticEvent0507(
            atMillis = event.atMillis,
            monotonicNs = event.monotonicNs,
            stage = UnifiedDebugEventStore.sanitizeForExport(event.stage).take(140),
            details = UnifiedDebugEventStore.sanitizeForExport(event.details).take(1_200),
            threadName = UnifiedDebugEventStore.sanitizeForExport(event.threadName).take(100),
            context = diagnostic,
        )
    }

    fun export(
        module: DiagnosticModule0507,
        snapshot: UnifiedDebugEventStore.Snapshot,
        filter: ContextualDiagnosticFilter0507,
        headerLines: List<String>,
    ): String {
        val events = select(snapshot, module, filter).mapNotNull(::present).sortedBy { it.monotonicNs }
        val raw = buildString {
            headerLines.forEach { appendLine(it) }
            appendLine("Módulo: ${module.label}")
            appendLine("Eventos: ${events.size}")
            appendLine("Buffer: ${snapshot.events.size}/${snapshot.bufferCapacity} • descartados=${snapshot.droppedEvents}")
            appendLine()
            appendLine("--- EVENTOS CORRELACIONADOS ---")
            if (events.isEmpty()) {
                appendLine("(sem eventos no filtro atual)")
            } else {
                events.forEach { event ->
                    val diagnostic = event.context
                    append(format0507(event.atMillis))
                    append(" | ").append(diagnostic.severity.name)
                    append(" | parent=").append(diagnostic.parentModule.name)
                    append(" | origin=").append(diagnostic.originModule.name)
                    append(" | executor=").append(diagnostic.executorModule.name)
                    append(" | ").append(event.stage)
                    if (diagnostic.operation.isNotBlank()) append(" | operation=").append(diagnostic.operation)
                    if (diagnostic.correlationId.isNotBlank()) append(" | correlationId=").append(diagnostic.correlationId)
                    if (diagnostic.traceId.isNotBlank()) append(" | traceId=").append(diagnostic.traceId)
                    if (diagnostic.operationId.isNotBlank()) append(" | operationId=").append(diagnostic.operationId)
                    if (diagnostic.parentOperationId.isNotBlank()) append(" | parentOperationId=").append(diagnostic.parentOperationId)
                    if (diagnostic.entityType.isNotBlank()) append(" | entityType=").append(diagnostic.entityType)
                    if (diagnostic.entityId.isNotBlank()) append(" | entityId=").append(diagnostic.entityId)
                    if (diagnostic.result.isNotBlank()) append(" | result=").append(diagnostic.result)
                    if (diagnostic.errorCode.isNotBlank()) append(" | errorCode=").append(diagnostic.errorCode)
                    if (diagnostic.reason.isNotBlank()) append(" | reason=").append(diagnostic.reason)
                    diagnostic.durationMs?.let { append(" | durationMs=").append(it) }
                    diagnostic.attempt?.let { append(" | attempt=").append(it) }
                    diagnostic.retryable?.let { append(" | retryable=").append(it) }
                    if (event.details.isNotBlank()) append(" | ").append(event.details)
                    appendLine()
                }
            }
        }
        return UnifiedDebugEventStore.sanitizeForExport(raw)
    }

    private fun format0507(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS", Locale("pt", "BR")).format(Date(millis))
}
