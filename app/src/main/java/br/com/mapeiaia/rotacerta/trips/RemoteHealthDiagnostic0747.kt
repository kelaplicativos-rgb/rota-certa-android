package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.mapeiaia.rotacerta.BuildConfig
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import br.com.mapeiaia.rotacerta.monitoring.OperationalHealthCoordinator
import br.com.mapeiaia.rotacerta.monitoring.OperationalHealthTechnicalPackage0575
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

internal const val REMOTE_HEALTH_MARKER_0747 = "REMOTE_HEALTH_0747"
internal const val REMOTE_HEALTH_JOB_ID_0747 = "remote_health_job_id_0747"

internal fun normalizeRemoteHealthJobId0747(raw: String?): String? {
    val value = raw?.trim()?.lowercase().orEmpty()
    return value.takeIf {
        Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
            .matches(it)
    }
}

@Serializable
internal data class RemoteHealthIncident0747(
    val id: String,
    val severity: String,
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
    val lifecycle: String,
)

@Serializable
internal data class RemoteHealthEvent0747(
    val atMillis: Long,
    val stage: String,
    val packageName: String,
    val threadName: String,
    val details: String,
    val parentModule: String = "",
    val originModule: String = "",
    val executorModule: String = "",
    val component: String = "",
    val operation: String = "",
    val severity: String = "",
    val result: String = "",
    val errorCode: String = "",
    val reason: String = "",
    val durationMs: Long? = null,
)

@Serializable
internal data class RemoteHealthBuffer0747(
    val eventsInBuffer: Int,
    val bufferCapacity: Int,
    val recordCalls: Long,
    val recordMedianNs: Long,
    val recordP95Ns: Long,
    val recordMaxNs: Long,
)

@Serializable
internal data class RemoteHealthPayload0747(
    val schemaVersion: String = "rota-certa-remote-health-v1",
    val kind: String = "ROTA_CERTA_REMOTE_HEALTH",
    val capturedAtMillis: Long,
    val sourceAppVersion: String,
    val sourceVersionCode: Int,
    val sourceCommitSha: String,
    val sourceBranch: String,
    val state: String,
    val validation: String,
    val validationSummary: String,
    val sourceEventCount: Int,
    val droppedEvents: Long,
    val incidents: List<RemoteHealthIncident0747>,
    val events: List<RemoteHealthEvent0747>,
    val buffer: RemoteHealthBuffer0747,
)

internal object RemoteHealthScheduler0747 {
    fun enqueue(context: Context, rawJobId: String?): Boolean {
        val jobId = normalizeRemoteHealthJobId0747(rawJobId) ?: return false
        val request = OneTimeWorkRequestBuilder<RemoteHealthWorker0747>()
            .setInputData(
                Data.Builder()
                    .putString(REMOTE_HEALTH_JOB_ID_0747, jobId)
                    .build(),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "remote-health-0747:$jobId",
            ExistingWorkPolicy.KEEP,
            request,
        )
        return true
    }
}

internal class RemoteHealthWorker0747(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val jobId = normalizeRemoteHealthJobId0747(
            inputData.getString(REMOTE_HEALTH_JOB_ID_0747),
        ) ?: return Result.failure()

        val store = TripStore(applicationContext)
        val settings = withContext(Dispatchers.IO) { store.onlineSettings() }
        if (!settings.configured || settings.driverToken.isBlank() || settings.driverUsername.isBlank()) {
            UnifiedDebugEventStore.recordAlways(
                "REMOTE_HEALTH_CONFIGURATION_MISSING_0747",
                applicationContext.packageName,
                "jobPresent=true",
            )
            return Result.failure()
        }

        val api = TripRemoteApi(settings)
        val ack = runCatching {
            api.ackRemoteHealthJob0747(jobId)
        }.getOrElse { error ->
            UnifiedDebugEventStore.recordAlways(
                "REMOTE_HEALTH_ACK_FAILED_0747",
                applicationContext.packageName,
                "jobPresent=true error=${error.javaClass.simpleName.take(80)}",
            )
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
        if (ack.state in setOf("COMPLETE", "FAILED", "EXPIRED")) return Result.success()

        return runCatching {
            val source = UnifiedDebugEventStore.snapshot()
            val health = OperationalHealthCoordinator.scan(applicationContext)
            val evidence = OperationalHealthTechnicalPackage0575.selectEvidenceEvents0575(
                events = source.events,
                incidents = health.incidents,
                maxEvents = 240,
            )
            val safe: (String, Int) -> String = { value, max ->
                UnifiedDebugEventStore.sanitizeForExport(value).take(max)
            }
            val payload = RemoteHealthPayload0747(
                capturedAtMillis = System.currentTimeMillis(),
                sourceAppVersion = BuildConfig.VERSION_NAME,
                sourceVersionCode = BuildConfig.VERSION_CODE,
                sourceCommitSha = BuildConfig.BUILD_GIT_SHA.take(80),
                sourceBranch = BuildConfig.BUILD_GIT_BRANCH.take(160),
                state = health.state.name,
                validation = health.validation.name,
                validationSummary = safe(health.validationSummary, 1_600),
                sourceEventCount = health.sourceEventCount,
                droppedEvents = health.droppedEvents,
                incidents = health.incidents.take(40).map { incident ->
                    RemoteHealthIncident0747(
                        id = safe(incident.id, 80),
                        severity = incident.severity.name,
                        module = safe(incident.module, 100),
                        fingerprint = safe(incident.fingerprint, 100),
                        firstSeenMillis = incident.firstSeenMillis,
                        lastSeenMillis = incident.lastSeenMillis,
                        count = incident.count,
                        errorCode = safe(incident.errorCode, 140),
                        symptom = safe(incident.symptom, 1_200),
                        probableRootCause = safe(incident.probableRootCause, 1_200),
                        confidencePercent = incident.confidencePercent.coerceIn(0, 100),
                        suggestedCorrection = safe(incident.suggestedCorrection, 1_200),
                        lifecycle = incident.lifecycle.name,
                    )
                },
                events = evidence.takeLast(240).map { event ->
                    val diagnostic = event.diagnosticContext
                    RemoteHealthEvent0747(
                        atMillis = event.atMillis,
                        stage = safe(event.stage, 160),
                        packageName = safe(event.packageName, 160),
                        threadName = safe(event.threadName, 100),
                        details = safe(event.details, 700),
                        parentModule = diagnostic?.parentModule?.name.orEmpty(),
                        originModule = diagnostic?.originModule?.name.orEmpty(),
                        executorModule = diagnostic?.executorModule?.name.orEmpty(),
                        component = safe(diagnostic?.component.orEmpty(), 120),
                        operation = safe(diagnostic?.operation.orEmpty(), 120),
                        severity = diagnostic?.severity?.name.orEmpty(),
                        result = safe(diagnostic?.result.orEmpty(), 60),
                        errorCode = safe(diagnostic?.errorCode.orEmpty(), 140),
                        reason = safe(diagnostic?.reason.orEmpty(), 700),
                        durationMs = diagnostic?.durationMs,
                    )
                },
                buffer = RemoteHealthBuffer0747(
                    eventsInBuffer = source.events.size,
                    bufferCapacity = source.bufferCapacity,
                    recordCalls = source.recordCalls,
                    recordMedianNs = source.recordMedianNs,
                    recordP95Ns = source.recordP95Ns,
                    recordMaxNs = source.recordMaxNs,
                ),
            )
            val response = api.submitRemoteHealthResult0747(
                jobId = jobId,
                status = "COMPLETE",
                payload = payload,
            )
            check(response.accepted) { "Servidor nao confirmou snapshot remoto de saude." }
            UnifiedDebugEventStore.recordAlways(
                "REMOTE_HEALTH_RESULT_SENT_0747",
                applicationContext.packageName,
                "jobPresent=true state=${health.state.name} incidents=${health.incidents.size} evidence=${evidence.size}",
            )
            Result.success()
        }.getOrElse { error ->
            val reported = runCatching {
                api.submitRemoteHealthResult0747(
                    jobId = jobId,
                    status = "FAILED",
                    payload = null,
                    errorCode = "REMOTE_HEALTH_DEVICE_COLLECTION_FAILED",
                    errorMessage = (error.message ?: error.javaClass.simpleName).take(220),
                ).accepted
            }.getOrDefault(false)
            UnifiedDebugEventStore.recordAlways(
                "REMOTE_HEALTH_COLLECTION_FAILED_0747",
                applicationContext.packageName,
                "jobPresent=true reported=$reported error=${error.javaClass.simpleName.take(80)}",
            )
            if (reported) Result.success() else if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
