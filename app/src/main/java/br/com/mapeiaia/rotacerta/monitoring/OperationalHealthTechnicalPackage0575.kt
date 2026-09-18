package br.com.mapeiaia.rotacerta.monitoring

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import br.com.mapeiaia.rotacerta.BuildConfig
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import br.com.mapeiaia.rotacerta.trips.AgendaSyncCrashTraceStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

data class OperationalHealthSavedPackage0575(
    val displayName: String,
    val uri: Uri,
)

object OperationalHealthTechnicalPackage0575 {
    private const val DOWNLOAD_SUBDIRECTORY = "Rota Certa/Diagnosticos"
    private const val CONTEXT_RADIUS = 24
    private const val MAX_FULL_EVENTS = 2_500
    private const val MAX_INCIDENT_EVENTS = 800

    fun generateAndSave(
        context: Context,
        health: OperationalHealthSnapshot,
        source: UnifiedDebugEventStore.Snapshot,
        incidentId: String? = null,
    ): Result<OperationalHealthSavedPackage0575> = runCatching {
        val appContext = context.applicationContext
        val generatedAt = System.currentTimeMillis()
        val selectedIncidents = if (incidentId.isNullOrBlank()) {
            health.incidents
        } else {
            health.incidents.filter { it.id == incidentId }
        }
        val maxEvents = if (incidentId.isNullOrBlank()) MAX_FULL_EVENTS else MAX_INCIDENT_EVENTS
        val evidenceEvents = selectEvidenceEvents0575(
            events = source.events,
            incidents = selectedIncidents,
            contextRadius = CONTEXT_RADIUS,
            maxEvents = maxEvents,
        )

        val entries = linkedMapOf(
            "README.txt" to readme0575(incidentId),
            "manifest.json" to manifest0575(appContext, generatedAt, incidentId, selectedIncidents.size, evidenceEvents.size),
            "health-snapshot.json" to healthSnapshot0575(health),
            "incident-evidence.json" to incidentEvidence0575(selectedIncidents, evidenceEvents),
            "events.ndjson" to eventsNdjson0575(evidenceEvents),
            "persisted-evidence-capsules.json" to OperationalHealthEvidenceCapsuleStore0576.export(appContext, incidentId),
            "buffer-stats.json" to bufferStats0575(source),
            "agenda-crash-evidence.txt" to AgendaSyncCrashTraceStore.export(appContext),
        )
        val zipBytes = zipSanitized0575(entries)
        saveToDownloads0575(appContext, zipBytes, incidentId, generatedAt)
    }

    internal fun selectEvidenceEvents0575(
        events: List<UnifiedDebugEventStore.SnapshotEvent>,
        incidents: List<OperationalIncident>,
        contextRadius: Int = CONTEXT_RADIUS,
        maxEvents: Int = MAX_FULL_EVENTS,
    ): List<UnifiedDebugEventStore.SnapshotEvent> {
        if (events.isEmpty() || maxEvents <= 0) return emptyList()
        if (incidents.isEmpty()) return events.takeLast(minOf(200, maxEvents))

        val targetFingerprints = incidents.mapTo(mutableSetOf()) { it.fingerprint }
        val matches = events.withIndex().filter { indexed ->
            OperationalHealthEngine.fingerprintForEvidence0575(indexed.value) in targetFingerprints
        }
        if (matches.isEmpty()) return events.takeLast(minOf(200, maxEvents))

        val selectedIndices = linkedSetOf<Int>()
        val technicalIds = linkedSetOf<String>()

        matches.forEach { indexed ->
            val start = (indexed.index - contextRadius).coerceAtLeast(0)
            val end = (indexed.index + contextRadius).coerceAtMost(events.lastIndex)
            for (index in start..end) selectedIndices += index
            indexed.value.diagnosticContext?.let { diagnostic ->
                listOf(
                    diagnostic.operationId,
                    diagnostic.parentOperationId,
                    diagnostic.correlationId,
                    diagnostic.traceId,
                ).filter(String::isNotBlank).forEach(technicalIds::add)
            }
        }

        if (technicalIds.isNotEmpty()) {
            events.forEachIndexed { index, event ->
                val diagnostic = event.diagnosticContext ?: return@forEachIndexed
                val eventIds = listOf(
                    diagnostic.operationId,
                    diagnostic.parentOperationId,
                    diagnostic.correlationId,
                    diagnostic.traceId,
                )
                if (eventIds.any { it.isNotBlank() && it in technicalIds }) selectedIndices += index
            }
        }

        val sorted = selectedIndices.sorted()
        val bounded = if (sorted.size <= maxEvents) {
            sorted
        } else {
            val firstHalf = maxEvents / 2
            val lastHalf = maxEvents - firstHalf
            (sorted.take(firstHalf) + sorted.takeLast(lastHalf)).distinct().sorted()
        }
        return bounded.map(events::get)
    }

    private fun manifest0575(
        context: Context,
        generatedAt: Long,
        incidentId: String?,
        incidentCount: Int,
        evidenceEventCount: Int,
    ): String = JSONObject()
        .put("schema", "operational-health-evidence-v1")
        .put("generatedAtMillis", generatedAt)
        .put("generatedAt", format0575(generatedAt))
        .put("versionName", BuildConfig.VERSION_NAME)
        .put("versionCode", BuildConfig.VERSION_CODE)
        .put("commit", BuildConfig.BUILD_GIT_SHA)
        .put("branch", BuildConfig.BUILD_GIT_BRANCH)
        .put("packageName", context.packageName)
        .put("androidRelease", Build.VERSION.RELEASE)
        .put("androidApi", Build.VERSION.SDK_INT)
        .put("device", listOf(Build.MANUFACTURER, Build.MODEL).joinToString(" ").trim())
        .put("scope", incidentId ?: "FULL")
        .put("incidentCount", incidentCount)
        .put("evidenceEventCount", evidenceEventCount)
        .put("privacy", "Conteudo sanitizado pelo UnifiedDebugEventStore; sem nomes, telefones, e-mails, mensagens, cookies, tokens, senhas, URLs ou enderecos privados intencionais.")
        .toString(2)

    private fun healthSnapshot0575(health: OperationalHealthSnapshot): String {
        val incidents = JSONArray()
        health.incidents.forEach { incidents.put(incidentJson0575(it)) }
        val opportunities = JSONArray()
        health.opportunities.forEach { opportunity ->
            opportunities.put(
                JSONObject()
                    .put("incidentId", opportunity.incidentId)
                    .put("title", opportunity.title)
                    .put("proposal", opportunity.proposal),
            )
        }
        return JSONObject()
            .put("scannedAtMillis", health.scannedAtMillis)
            .put("state", health.state.name)
            .put("sourceEventCount", health.sourceEventCount)
            .put("droppedEvents", health.droppedEvents)
            .put("validation", health.validation.name)
            .put("validationSummary", health.validationSummary)
            .put("incidents", incidents)
            .put("opportunities", opportunities)
            .toString(2)
    }

    private fun incidentEvidence0575(
        incidents: List<OperationalIncident>,
        evidenceEvents: List<UnifiedDebugEventStore.SnapshotEvent>,
    ): String {
        val items = JSONArray()
        incidents.forEach { incident ->
            val matching = evidenceEvents.count {
                OperationalHealthEngine.fingerprintForEvidence0575(it) == incident.fingerprint
            }
            items.put(
                incidentJson0575(incident)
                    .put("matchingEventsInArchive", matching),
            )
        }
        return JSONObject()
            .put("incidents", items)
            .put("selectionPolicy", "primeiro/ultimo desvio +/-24 eventos + toda correlacao por operationId/parentOperationId/correlationId/traceId; limite global preserva inicio e fim")
            .toString(2)
    }

    private fun incidentJson0575(incident: OperationalIncident): JSONObject = JSONObject()
        .put("id", incident.id)
        .put("severity", incident.severity.name)
        .put("module", incident.module)
        .put("fingerprint", incident.fingerprint)
        .put("firstSeenMillis", incident.firstSeenMillis)
        .put("lastSeenMillis", incident.lastSeenMillis)
        .put("count", incident.count)
        .put("errorCode", incident.errorCode)
        .put("symptom", incident.symptom)
        .put("probableRootCause", incident.probableRootCause)
        .put("confidencePercent", incident.confidencePercent)
        .put("suggestedCorrection", incident.suggestedCorrection)

    private fun eventsNdjson0575(events: List<UnifiedDebugEventStore.SnapshotEvent>): String =
        events.joinToString("\n") { event ->
            val json = JSONObject()
                .put("atMillis", event.atMillis)
                .put("monotonicNs", event.monotonicNs)
                .put("stage", event.stage)
                .put("packageName", event.packageName)
                .put("threadName", event.threadName)
                .put("details", event.details)
            event.diagnosticContext?.let { diagnostic ->
                json.put(
                    "diagnostic",
                    JSONObject()
                        .put("parentModule", diagnostic.parentModule.name)
                        .put("originModule", diagnostic.originModule.name)
                        .put("executorModule", diagnostic.executorModule.name)
                        .put("submodule", diagnostic.submodule)
                        .put("component", diagnostic.component)
                        .put("operation", diagnostic.operation)
                        .put("severity", diagnostic.severity.name)
                        .put("correlationId", diagnostic.correlationId)
                        .put("traceId", diagnostic.traceId)
                        .put("operationId", diagnostic.operationId)
                        .put("parentOperationId", diagnostic.parentOperationId)
                        .put("entityType", diagnostic.entityType)
                        .put("entityId", diagnostic.entityId)
                        .put("result", diagnostic.result)
                        .put("errorCode", diagnostic.errorCode)
                        .put("reason", diagnostic.reason)
                        .put("durationMs", diagnostic.durationMs ?: JSONObject.NULL)
                        .put("attempt", diagnostic.attempt ?: JSONObject.NULL)
                        .put("retryable", diagnostic.retryable ?: JSONObject.NULL),
                )
            }
            json.toString()
        }

    private fun bufferStats0575(source: UnifiedDebugEventStore.Snapshot): String {
        val oldest = source.events.minOfOrNull { it.atMillis }
        val newest = source.events.maxOfOrNull { it.atMillis }
        return JSONObject()
            .put("eventsInBuffer", source.events.size)
            .put("bufferCapacity", source.bufferCapacity)
            .put("removedByLimit", source.droppedEvents)
            .put("recordCalls", source.recordCalls)
            .put("recordOverheadTotalNs", source.recordOverheadTotalNs)
            .put("recordMedianNs", source.recordMedianNs)
            .put("recordP95Ns", source.recordP95Ns)
            .put("recordMaxNs", source.recordMaxNs)
            .put("oldestEventAtMillis", oldest ?: JSONObject.NULL)
            .put("newestEventAtMillis", newest ?: JSONObject.NULL)
            .put("coverageMillis", if (oldest != null && newest != null) (newest - oldest).coerceAtLeast(0L) else 0L)
            .toString(2)
    }

    private fun readme0575(incidentId: String?): String = buildString {
        appendLine("ROTA CERTA — PACOTE TECNICO DA CENTRAL DE SAUDE")
        appendLine("Escopo: ${incidentId ?: "todos os incidentes atuais"}")
        appendLine()
        appendLine("Arquivos:")
        appendLine("- manifest.json: versao/build/SHA/aparelho e escopo.")
        appendLine("- health-snapshot.json: estado calculado da Central.")
        appendLine("- incident-evidence.json: incidentes, fingerprints, causa provavel e politica de selecao.")
        appendLine("- events.ndjson: contexto cronologico selecionado ao redor dos desvios e IDs correlacionados.")
        appendLine("- persisted-evidence-capsules.json: evidência sanitizada persistida por fingerprint, inclusive após troca de processo.")
        appendLine("- buffer-stats.json: cobertura real do buffer e custo de observabilidade.")
        appendLine("- agenda-crash-evidence.txt: crash/checkpoints persistidos da Agenda, quando existentes.")
        appendLine()
        appendLine("Privacidade: todo conteudo textual passa novamente por UnifiedDebugEventStore.sanitizeForExport antes de entrar no ZIP.")
        appendLine("Este pacote e diagnostico. Ele nao executa patch, deploy, login, reserva, cancelamento nem edicao de viagem.")
    }

    internal fun zipSanitized0575(entries: Map<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, raw) ->
                val safe = if (name.endsWith(".ndjson", ignoreCase = true)) {
                    raw.lineSequence()
                        .map(UnifiedDebugEventStore::sanitizeForExport)
                        .joinToString("\n")
                } else {
                    UnifiedDebugEventStore.sanitizeForExport(raw)
                }
                zip.putNextEntry(ZipEntry(name))
                zip.write(safe.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun saveToDownloads0575(
        context: Context,
        bytes: ByteArray,
        incidentId: String?,
        generatedAt: Long,
    ): OperationalHealthSavedPackage0575 {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(generatedAt))
        val scope = incidentId
            ?.replace(Regex("[^A-Za-z0-9_-]"), "-")
            ?.take(40)
            ?.ifBlank { "incidente" }
            ?: "completo"
        val displayName = "rota-certa-saude-${BuildConfig.VERSION_NAME}-$scope-$stamp.zip"

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + File.separator + DOWNLOAD_SUBDIRECTORY,
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val created = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Nao foi possivel criar o pacote tecnico em Downloads.")
            runCatching {
                resolver.openOutputStream(created, "w")?.use { it.write(bytes) }
                    ?: error("Nao foi possivel escrever o pacote tecnico.")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(created, values, null, null)
            }.onFailure {
                resolver.delete(created, null, null)
            }.getOrThrow()
            created
        } else {
            @Suppress("DEPRECATION")
            val directory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                DOWNLOAD_SUBDIRECTORY,
            ).apply { mkdirs() }
            val file = File(directory, displayName)
            file.writeBytes(bytes)
            Uri.fromFile(file)
        }
        return OperationalHealthSavedPackage0575(displayName, uri)
    }

    private fun format0575(value: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS", Locale("pt", "BR")).format(Date(value))
}
