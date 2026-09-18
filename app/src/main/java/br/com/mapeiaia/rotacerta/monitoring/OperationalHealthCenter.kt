package br.com.mapeiaia.rotacerta.monitoring

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.mapeiaia.rotacerta.BuildConfig
import br.com.mapeiaia.rotacerta.DiagnosticSeverity0507
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import br.com.mapeiaia.rotacerta.trips.AgendaSyncCrashTraceStore
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

enum class OperationalHealthState { GREEN, YELLOW, RED }
enum class OperationalIncidentSeverity { WARNING, CRITICAL }
enum class OperationalValidationState { IMPROVED, STABLE, REGRESSION, INSUFFICIENT_DATA }

data class OperationalIncident(
    val id: String,
    val severity: OperationalIncidentSeverity,
    val module: String,
    val fingerprint: String,
    val firstSeenMillis: Long,
    val lastSeenMillis: Long,
    val count: Int,
    val errorCode: String,
    val symptom: String,
    val probableRootCause: String,
    val confidencePercent: Int,
    val suggestedCorrection: String,
)

data class OperationalOpportunity(
    val incidentId: String,
    val title: String,
    val proposal: String,
)

data class OperationalHealthSnapshot(
    val scannedAtMillis: Long,
    val state: OperationalHealthState,
    val sourceEventCount: Int,
    val droppedEvents: Long,
    val incidents: List<OperationalIncident>,
    val opportunities: List<OperationalOpportunity>,
    val validation: OperationalValidationState,
    val validationSummary: String,
)

object OperationalHealthEngine {
    private const val DAY = 24L * 60L * 60L * 1000L
    private const val SIX_HOURS = 6L * 60L * 60L * 1000L
    private val failureTokens = listOf(
        "ERROR", "FAILED", "FAILURE", "MISSING", "MISMATCH", "TIMEOUT",
        "STALE", "BLOCKED", "UNAVAILABLE", "CRASH", "REJECTED",
        "SLOW_OPERATION", "LONG_BLOCK", "JANK_FRAME",
    )
    private val criticalTokens = listOf(
        "CRASH", "IDENTITY", "PROFILE_MISMATCH", "EDIT_TARGET_MISSING",
        "CANONICAL", "CORRUPT", "AUTH", "SESSION",
    )

    fun analyze(
        snapshot: UnifiedDebugEventStore.Snapshot,
        nowMillis: Long = System.currentTimeMillis(),
    ): OperationalHealthSnapshot {
        val candidates = snapshot.events.filter { event ->
            event.atMillis >= nowMillis - DAY && isPotentialProblem(event)
        }
        val groups = candidates.groupBy(::fingerprint)
        val incidents = groups.map { (fingerprint, grouped) ->
            val ordered = grouped.sortedBy { it.atMillis }
            val sample = ordered.last()
            val diagnostic = sample.diagnosticContext
            val errorCode = diagnostic?.errorCode.orEmpty()
            val module = diagnostic?.parentModule?.label ?: inferModule(sample.stage)
            val technicalKey = listOf(errorCode, sample.stage, diagnostic?.operation.orEmpty())
                .joinToString(" ")
                .uppercase(Locale.ROOT)
            val severity = if (
                diagnostic?.severity == DiagnosticSeverity0507.ERROR &&
                criticalTokens.any(technicalKey::contains)
            ) OperationalIncidentSeverity.CRITICAL
            else if (criticalTokens.any(technicalKey::contains)) OperationalIncidentSeverity.CRITICAL
            else OperationalIncidentSeverity.WARNING
            val diagnosis = diagnose(technicalKey, module)
            OperationalIncident(
                id = "INC-" + fingerprint.take(10).uppercase(Locale.ROOT),
                severity = severity,
                module = module,
                fingerprint = fingerprint,
                firstSeenMillis = ordered.first().atMillis,
                lastSeenMillis = sample.atMillis,
                count = grouped.size,
                errorCode = errorCode.ifBlank { normalizedStage(sample.stage) },
                symptom = sanitizeEvidence(sample),
                probableRootCause = diagnosis.rootCause,
                confidencePercent = diagnosis.confidence,
                suggestedCorrection = diagnosis.correction,
            )
        }.sortedWith(
            compareByDescending<OperationalIncident> { it.severity == OperationalIncidentSeverity.CRITICAL }
                .thenByDescending { it.lastSeenMillis },
        )

        val recentCritical = incidents.any {
            it.severity == OperationalIncidentSeverity.CRITICAL &&
                it.lastSeenMillis >= nowMillis - 60L * 60L * 1000L
        }
        val state = when {
            recentCritical -> OperationalHealthState.RED
            incidents.isNotEmpty() -> OperationalHealthState.YELLOW
            else -> OperationalHealthState.GREEN
        }
        val opportunities = incidents
            .filter { it.count >= 2 || it.severity == OperationalIncidentSeverity.CRITICAL }
            .take(12)
            .map(::opportunityFor)
        val recentCandidates = candidates.filter { it.atMillis >= nowMillis - SIX_HOURS }
        val previousCandidates = candidates.filter {
            it.atMillis >= nowMillis - (2L * SIX_HOURS) && it.atMillis < nowMillis - SIX_HOURS
        }
        val recentIncidentCount = recentCandidates.map(::fingerprint).distinct().size
        val previousIncidentCount = previousCandidates.map(::fingerprint).distinct().size
        val oldestRetainedAt = snapshot.events.minOfOrNull { it.atMillis }
        val hasFullComparisonWindow = oldestRetainedAt != null &&
            oldestRetainedAt <= nowMillis - (2L * SIX_HOURS)

        val validation = when {
            !hasFullComparisonWindow -> OperationalValidationState.INSUFFICIENT_DATA
            previousIncidentCount > 0 && recentIncidentCount == 0 -> OperationalValidationState.IMPROVED
            recentIncidentCount > previousIncidentCount -> OperationalValidationState.REGRESSION
            else -> OperationalValidationState.STABLE
        }
        val validationSummary = when (validation) {
            OperationalValidationState.IMPROVED ->
                "Incidentes únicos caíram de $previousIncidentCount para 0; eventos relacionados na janela anterior=${previousCandidates.size}."
            OperationalValidationState.REGRESSION ->
                "Incidentes únicos aumentaram de $previousIncidentCount para $recentIncidentCount; eventos relacionados na janela recente=${recentCandidates.size}."
            OperationalValidationState.STABLE ->
                "Incidentes únicos: anterior=$previousIncidentCount e recente=$recentIncidentCount; eventos recentes=${recentCandidates.size}."
            OperationalValidationState.INSUFFICIENT_DATA -> {
                val oldest = oldestRetainedAt?.let { nowMillis - it } ?: 0L
                val coverageHours = oldest / (60L * 60L * 1000L)
                "Comparação de 12h indisponível: o buffer retém aproximadamente ${coverageHours}h de histórico nesta sessão; removidos por limite=${snapshot.droppedEvents}. Não classificar como regressão sem janela anterior completa."
            }
        }
        return OperationalHealthSnapshot(
            scannedAtMillis = nowMillis,
            state = state,
            sourceEventCount = snapshot.events.size,
            droppedEvents = snapshot.droppedEvents,
            incidents = incidents.take(30),
            opportunities = opportunities,
            validation = validation,
            validationSummary = validationSummary,
        )
    }

    fun isPotentialProblem(event: UnifiedDebugEventStore.SnapshotEvent): Boolean {
        val diagnostic = event.diagnosticContext
        if (diagnostic?.severity == DiagnosticSeverity0507.ERROR) return true
        if (diagnostic?.errorCode?.isNotBlank() == true) return true
        val stage = event.stage.uppercase(Locale.ROOT)
        val result = diagnostic?.result.orEmpty().uppercase(Locale.ROOT)
        if (result in setOf("SUCCESS", "SUCCEEDED", "OK", "COMPLETED", "RESOLVED", "RECOVERED")) return false
        return failureTokens.any(stage::contains)
    }

    private data class Diagnosis(val rootCause: String, val confidence: Int, val correction: String)

    private fun diagnose(key: String, module: String): Diagnosis = when {
        key.contains("EDIT_TARGET_MISSING") ->
            Diagnosis(
                "A ação chegou ao executor sem um alvo operacional forte previamente resolvido.",
                95,
                "Resolver uma TripOperationalIdentity única antes de qualquer ação e bloquear execução quando a identidade estiver incompleta.",
            )
        (key.contains("CANONICAL") || key.contains("SEGMENT")) &&
            (key.contains("SEAT") || key.contains("AVAIL") || key.contains("MISMATCH")) ->
            Diagnosis(
                "A projeção consumida por uma superfície não contém o mesmo contrato canônico de disponibilidade usado pela origem.",
                91,
                "Publicar disponibilidade por trecho dentro do mesmo snapshot canônico e validar a invariante Agenda.segmentAvailability == Timeline.segmentAvailability.",
            )
        key.contains("REFRESH") && (key.contains("SCOPE") || key.contains("ALL")) ->
            Diagnosis(
                "O estado visual de sincronização mistura escopo global com operação de uma única viagem.",
                90,
                "Introduzir OperationScope explícito (GLOBAL, TRIP, PROFILE, PASSENGER) e derivar a UI exclusivamente desse escopo.",
            )
        key.contains("PASSENGER") && (key.contains("LOAD") || key.contains("TIMEOUT")) ->
            Diagnosis(
                "O ciclo assíncrono de passageiros não alcançou estado terminal confiável.",
                84,
                "Modelar carregamento com estados terminais explícitos, timeout observável e invalidação por versão da viagem.",
            )
        key.contains("PERMALINK") || key.contains("PUBLIC_LINK") ->
            Diagnosis(
                "O vínculo entre a viagem administrativa e o permalink público não possui autoridade forte em algum ponto do fluxo.",
                89,
                "Preservar somente bindings autoritativos observados e rejeitar reconstrução ou promoção de URL por heurística.",
            )
        key.contains("PROFILE_MISMATCH") || key.contains("SESSION") || key.contains("AUTH") ->
            Diagnosis(
                "A operação perdeu a atestação entre identidade esperada, sessão isolada e destino final.",
                88,
                "Manter validação fail-closed de profileUuid/sessão e exigir atestação positiva antes de marcar a operação como concluída.",
            )
        key.contains("STALE") || key.contains("SNAPSHOT") ->
            Diagnosis(
                "Há uma janela em que uma projeção antiga permanece elegível depois de uma mudança de autoridade.",
                80,
                "Versionar snapshots e invalidações com revisão monotônica e impedir regressão para revisão anterior.",
            )
        key.contains("SLOW_OPERATION") || key.contains("LONG_BLOCK") || key.contains("JANK_FRAME") ->
            Diagnosis(
                "Uma operação ou frame ultrapassou o orçamento de responsividade e bloqueou a experiência perceptível da tela.",
                92,
                "Retirar trabalho pesado da main thread, reduzir recomposição/serialização no caminho crítico e validar novamente os percentis e frames longos.",
            )
        else ->
            Diagnosis(
                "Falha recorrente ou terminal detectada no módulo $module; os eventos disponíveis ainda não isolam uma única causa.",
                60,
                "Correlacionar operationId/correlationId e comparar o primeiro desvio com o último estado saudável antes de alterar a lógica de negócio.",
            )
    }

    private fun opportunityFor(incident: OperationalIncident): OperationalOpportunity {
        val key = (incident.errorCode + " " + incident.probableRootCause).uppercase(Locale.ROOT)
        return when {
            key.contains("IDENT") || key.contains("TARGET") ->
                OperationalOpportunity(
                    incident.id,
                    "Identidade operacional única",
                    "Centralizar profileUuid + tripId + canonicalTripId + conta proprietária em um resolvedor único reutilizado por todas as ações.",
                )
            key.contains("CANON") || key.contains("SEGMENT") ->
                OperationalOpportunity(
                    incident.id,
                    "Contrato canônico verificável",
                    "Transformar paridade Agenda/Timeline em invariante automática de CI e de runtime, sem cálculos paralelos por superfície.",
                )
            key.contains("SCOPE") || key.contains("REFRESH") ->
                OperationalOpportunity(
                    incident.id,
                    "Escopo explícito de operação",
                    "Adotar um OperationScope tipado para eliminar estados globais representando ações locais.",
                )
            else ->
                OperationalOpportunity(
                    incident.id,
                    "Corrigir ${incident.errorCode.take(48)}",
                    "Causa provável (${incident.confidencePercent}%): ${incident.probableRootCause} Correção estrutural: ${incident.suggestedCorrection}",
                )
        }
    }

    private fun fingerprint(event: UnifiedDebugEventStore.SnapshotEvent): String {
        val d = event.diagnosticContext
        val raw = listOf(
            d?.parentModule?.name.orEmpty(),
            d?.errorCode.orEmpty(),
            d?.operation.orEmpty(),
            normalizedStage(event.stage),
        ).joinToString("|").lowercase(Locale.ROOT)
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun normalizedStage(value: String): String =
        value.uppercase(Locale.ROOT)
            .replace(Regex("[0-9]{3,4}"), "#")
            .take(120)

    private fun inferModule(stage: String): String {
        val upper = stage.uppercase(Locale.ROOT)
        return when {
            upper.contains("AGENDA") -> "Agenda"
            upper.contains("TIMELINE") -> "Timeline"
            upper.contains("BLABLA") -> "BlaBlaCar"
            upper.contains("SCRIPT") -> "Scripts"
            upper.contains("PASSENGER") -> "Passageiros"
            else -> "Operação"
        }
    }

    private fun sanitizeEvidence(event: UnifiedDebugEventStore.SnapshotEvent): String {
        val diagnostic = event.diagnosticContext
        return buildString {
            append(event.stage.take(140))
            diagnostic?.operation?.takeIf(String::isNotBlank)?.let { append(" • operation=").append(it.take(80)) }
            diagnostic?.result?.takeIf(String::isNotBlank)?.let { append(" • result=").append(it.take(60)) }
            diagnostic?.reason?.takeIf(String::isNotBlank)?.let { append(" • reason=").append(it.take(180)) }
        }.let(UnifiedDebugEventStore::sanitizeForExport)
    }
}

object OperationalHealthRuntime {
    private val latestProblemAt = AtomicLong(0L)
    private val latestCriticalAt = AtomicLong(0L)

    fun observe(event: UnifiedDebugEventStore.SnapshotEvent) {
        if (!OperationalHealthEngine.isPotentialProblem(event)) return
        latestProblemAt.accumulateAndGet(event.atMillis, ::maxOf)
        val key = buildString {
            append(event.stage)
            append(' ')
            append(event.diagnosticContext?.errorCode.orEmpty())
        }.uppercase(Locale.ROOT)
        if (
            event.diagnosticContext?.severity == DiagnosticSeverity0507.ERROR ||
            listOf("CRASH", "IDENTITY", "PROFILE_MISMATCH", "EDIT_TARGET_MISSING", "CANONICAL").any(key::contains)
        ) {
            latestCriticalAt.accumulateAndGet(event.atMillis, ::maxOf)
        }
    }

    fun latestProblemAtMillis(): Long = latestProblemAt.get()
    fun latestCriticalAtMillis(): Long = latestCriticalAt.get()
}

object OperationalHealthStore {
    private const val PREFS = "rota_certa_operational_health_0572"
    private const val KEY = "snapshot"

    fun save(context: Context, snapshot: OperationalHealthSnapshot) {
        val json = JSONObject()
            .put("scannedAtMillis", snapshot.scannedAtMillis)
            .put("state", snapshot.state.name)
            .put("sourceEventCount", snapshot.sourceEventCount)
            .put("droppedEvents", snapshot.droppedEvents)
            .put("validation", snapshot.validation.name)
            .put("validationSummary", snapshot.validationSummary)
        val incidents = JSONArray()
        snapshot.incidents.forEach { incident ->
            incidents.put(
                JSONObject()
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
                    .put("suggestedCorrection", incident.suggestedCorrection),
            )
        }
        json.put("incidents", incidents)
        val opportunities = JSONArray()
        snapshot.opportunities.forEach { opportunity ->
            opportunities.put(
                JSONObject()
                    .put("incidentId", opportunity.incidentId)
                    .put("title", opportunity.title)
                    .put("proposal", opportunity.proposal),
            )
        }
        json.put("opportunities", opportunities)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, json.toString()).apply()
    }

    fun load(context: Context): OperationalHealthSnapshot? {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val incidentsJson = json.optJSONArray("incidents") ?: JSONArray()
            val incidents = buildList {
                for (index in 0 until incidentsJson.length()) {
                    val item = incidentsJson.getJSONObject(index)
                    add(
                        OperationalIncident(
                            id = item.optString("id"),
                            severity = enumValueOrDefault(item.optString("severity"), OperationalIncidentSeverity.WARNING),
                            module = item.optString("module"),
                            fingerprint = item.optString("fingerprint"),
                            firstSeenMillis = item.optLong("firstSeenMillis"),
                            lastSeenMillis = item.optLong("lastSeenMillis"),
                            count = item.optInt("count"),
                            errorCode = item.optString("errorCode"),
                            symptom = item.optString("symptom"),
                            probableRootCause = item.optString("probableRootCause"),
                            confidencePercent = item.optInt("confidencePercent"),
                            suggestedCorrection = item.optString("suggestedCorrection"),
                        ),
                    )
                }
            }
            val opportunitiesJson = json.optJSONArray("opportunities") ?: JSONArray()
            val opportunities = buildList {
                for (index in 0 until opportunitiesJson.length()) {
                    val item = opportunitiesJson.getJSONObject(index)
                    add(
                        OperationalOpportunity(
                            incidentId = item.optString("incidentId"),
                            title = item.optString("title"),
                            proposal = item.optString("proposal"),
                        ),
                    )
                }
            }
            OperationalHealthSnapshot(
                scannedAtMillis = json.optLong("scannedAtMillis"),
                state = enumValueOrDefault(json.optString("state"), OperationalHealthState.GREEN),
                sourceEventCount = json.optInt("sourceEventCount"),
                droppedEvents = json.optLong("droppedEvents"),
                incidents = incidents,
                opportunities = opportunities,
                validation = enumValueOrDefault(json.optString("validation"), OperationalValidationState.INSUFFICIENT_DATA),
                validationSummary = json.optString("validationSummary"),
            )
        }.getOrNull()
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback
}

object OperationalHealthCoordinator {
    private const val INCIDENT_RETENTION_MS = 24L * 60L * 60L * 1000L
    private const val RECENT_CRITICAL_MS = 60L * 60L * 1000L

    fun scan(context: Context): OperationalHealthSnapshot {
        val fresh = OperationalHealthEngine.analyze(UnifiedDebugEventStore.snapshot())
        return mergeWithStored(context, fresh).also { OperationalHealthStore.save(context, it) }
    }

    fun current(context: Context): OperationalHealthSnapshot {
        val fresh = OperationalHealthEngine.analyze(UnifiedDebugEventStore.snapshot())
        return mergeWithStored(context, fresh).also { OperationalHealthStore.save(context, it) }
    }

    private fun mergeWithStored(
        context: Context,
        fresh: OperationalHealthSnapshot,
    ): OperationalHealthSnapshot {
        val previous = OperationalHealthStore.load(context) ?: return fresh
        val cutoff = fresh.scannedAtMillis - INCIDENT_RETENTION_MS
        val mergedByFingerprint = linkedMapOf<String, OperationalIncident>()

        previous.incidents
            .filter { it.lastSeenMillis >= cutoff }
            .forEach { mergedByFingerprint[it.fingerprint] = it }

        fresh.incidents.forEach { incoming ->
            val existing = mergedByFingerprint[incoming.fingerprint]
            mergedByFingerprint[incoming.fingerprint] = if (existing == null) {
                incoming
            } else {
                val newest = if (incoming.lastSeenMillis >= existing.lastSeenMillis) incoming else existing
                newest.copy(
                    firstSeenMillis = minOf(existing.firstSeenMillis, incoming.firstSeenMillis),
                    lastSeenMillis = maxOf(existing.lastSeenMillis, incoming.lastSeenMillis),
                    count = maxOf(existing.count, incoming.count),
                )
            }
        }

        val incidents = mergedByFingerprint.values
            .sortedWith(
                compareByDescending<OperationalIncident> { it.severity == OperationalIncidentSeverity.CRITICAL }
                    .thenByDescending { it.lastSeenMillis },
            )
            .take(30)

        val state = when {
            incidents.any {
                it.severity == OperationalIncidentSeverity.CRITICAL &&
                    it.lastSeenMillis >= fresh.scannedAtMillis - RECENT_CRITICAL_MS
            } -> OperationalHealthState.RED
            incidents.isNotEmpty() -> OperationalHealthState.YELLOW
            else -> OperationalHealthState.GREEN
        }

        val activeIds = incidents.mapTo(mutableSetOf()) { it.id }
        val opportunities = (fresh.opportunities + previous.opportunities)
            .filter { it.incidentId in activeIds }
            .distinctBy { "${it.incidentId}|${it.title}" }
            .take(12)

        val validation = if (
            fresh.sourceEventCount == 0 &&
            fresh.validation == OperationalValidationState.INSUFFICIENT_DATA &&
            incidents.isNotEmpty()
        ) {
            previous.validation
        } else {
            fresh.validation
        }

        val validationSummary = if (
            fresh.sourceEventCount == 0 &&
            incidents.isNotEmpty()
        ) {
            "Sem novos eventos no processo atual; incidentes sanitizados ainda válidos foram preservados da projeção anterior."
        } else {
            fresh.validationSummary
        }

        return fresh.copy(
            state = state,
            droppedEvents = maxOf(previous.droppedEvents, fresh.droppedEvents),
            incidents = incidents,
            opportunities = opportunities,
            validation = validation,
            validationSummary = validationSummary,
        )
    }
}

class OperationalHealthWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runCatching {
        OperationalHealthCoordinator.scan(applicationContext)
        Result.success()
    }.getOrElse { error ->
        UnifiedDebugEventStore.recordAlways(
            stage = "OPERATIONAL_HEALTH_SCAN_FAILED_0574",
            packageName = applicationContext.packageName,
            details = "errorClass=${error.javaClass.simpleName}",
        )
        Result.failure()
    }
}

object OperationalHealthScheduler {
    private const val PERIODIC_NAME = "operational-health-hourly-0572"
    private const val IMMEDIATE_NAME = "operational-health-startup-0572"

    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<OperationalHealthWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun enqueueImmediate(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            IMMEDIATE_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<OperationalHealthWorker>().build(),
        )
    }
}

class OperationalHealthInitializerProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val appContext = context?.applicationContext ?: return false
        runCatching {
            AgendaSyncCrashTraceStore.recoverPersistedCrashIntoUnifiedDebug(appContext)
            OperationalHealthScheduler.ensureScheduled(appContext)
            OperationalHealthScheduler.enqueueImmediate(appContext)
        }
        return true
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

class OperationalHealthActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        OperationalHealthScheduler.ensureScheduled(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                OperationalHealthScreen()
            }
        }
    }

    @Composable
    private fun OperationalHealthScreen() {
        var snapshot by remember { mutableStateOf(OperationalHealthCoordinator.current(this)) }
        val formatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR")) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Central de Saúde e Evolução", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                when (snapshot.state) {
                    OperationalHealthState.GREEN -> "🟢 Operação sem incidente detectado na janela atual"
                    OperationalHealthState.YELLOW -> "🟡 Há incidentes que exigem acompanhamento"
                    OperationalHealthState.RED -> "🔴 Há incidente crítico recente"
                },
                fontWeight = FontWeight.Bold,
            )
            Text("Versão ${BuildConfig.VERSION_NAME} • build ${BuildConfig.VERSION_CODE}")
            Text("Commit ${BuildConfig.BUILD_GIT_SHA.take(12)} • branch ${BuildConfig.BUILD_GIT_BRANCH}")
            Text("Última auditoria: ${formatter.format(Date(snapshot.scannedAtMillis))}")
            Text("Eventos sanitizados no buffer: ${snapshot.sourceEventCount} • removidos por limite: ${snapshot.droppedEvents}")
            OperationalHealthRuntime.latestCriticalAtMillis().takeIf { it > 0L }?.let {
                Text("Último crítico observado em tempo real: ${formatter.format(Date(it))}")
            }
            Button(
                onClick = {
                    snapshot = OperationalHealthCoordinator.scan(this@OperationalHealthActivity)
                    OperationalHealthScheduler.enqueueImmediate(this@OperationalHealthActivity)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Executar auditoria agora") }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Validação antes/depois", fontWeight = FontWeight.Bold)
                    Text(snapshot.validation.name)
                    Text(snapshot.validationSummary, style = MaterialTheme.typography.bodySmall)
                }
            }

            Text("Incidentes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (snapshot.incidents.isEmpty()) {
                Text("Nenhum incidente detectado na janela sanitizada disponível.")
            } else {
                snapshot.incidents.forEach { incident ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(incident.id, fontWeight = FontWeight.Bold)
                                Text(if (incident.severity == OperationalIncidentSeverity.CRITICAL) "CRÍTICO" else "ATENÇÃO")
                            }
                            Text("${incident.module} • ocorrências ${incident.count}")
                            Text("Código: ${incident.errorCode}")
                            Text("Sintoma: ${incident.symptom}", style = MaterialTheme.typography.bodySmall)
                            Text("Causa provável (${incident.confidencePercent}%): ${incident.probableRootCause}")
                            Text("Correção estrutural: ${incident.suggestedCorrection}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Primeiro: ${formatter.format(Date(incident.firstSeenMillis))} • Último: ${formatter.format(Date(incident.lastSeenMillis))}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            if (snapshot.opportunities.isNotEmpty()) {
                Text("Oportunidades de evolução", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                snapshot.opportunities.forEach { opportunity ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(opportunity.title, fontWeight = FontWeight.Bold)
                            Text("Origem: ${opportunity.incidentId}", style = MaterialTheme.typography.bodySmall)
                            Text(opportunity.proposal)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Privacidade: esta Central usa somente eventos já sanitizados pelo flight recorder. Não executa patch, deploy, login, cancelamento, reserva ou edição de viagem automaticamente.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
