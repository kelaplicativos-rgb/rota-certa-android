package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import androidx.work.BackoffPolicy
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
import br.com.mapeiaia.rotacerta.versioncenter.ReleaseHistoryStore
import br.com.mapeiaia.rotacerta.versioncenter.VersionHistoryLogic
import java.io.IOException
import java.net.SocketException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

internal const val REMOTE_HEALTH_MARKER_0747 = "REMOTE_HEALTH_0747"

// A missing DNS record, timeout or HTTP 5xx means the evidence has NOT been
// rejected by the server. Keep the same server job pending and retry the
// submission; never mark it FAILED while the network is temporarily down.
internal fun isTransientRemoteDiagnosticFailure0771(error: Throwable): Boolean {
    if (error is CancellationException) throw error
    if (error is TripRemoteApiException) {
        return error.httpStatus <= 0 || error.httpStatus in setOf(408, 425, 429) ||
            error.httpStatus >= 500
    }
    if (error is IOException || error is SocketException) return true
    return error.cause?.let(::isTransientRemoteDiagnosticFailure0771) ?: false
}

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

/**
 * Installed release evidence, never a claim about what is already installed on the device.
 * Entries are sourced from the embedded Version Center manifest and kept bounded.
 */
@Serializable
internal data class RemoteReleaseAudit0765(
    val version: String,
    val build: Int,
    val status: String,
    val implemented: List<String>,
    val fixed: List<String>,
    val improved: List<String>,
    val modulesAffected: List<String>,
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
    val recentReleases: List<RemoteReleaseAudit0765> = emptyList(),
)

@Serializable
internal data class RemoteTechnicalZipPayload0761(
    val schemaVersion: String = "rota-certa-remote-technical-zip-v1",
    val archiveBase64: String,
    val archiveSha256: String,
    val archiveBytes: Int,
    val capturedAtMillis: Long,
    val sourceAppVersion: String,
    val sourceCommitSha: String,
    val fileName: String,
)

internal object RemoteHealthScheduler0747 {
    fun enqueue(context: Context, rawJobId: String?): Boolean {
        val jobId = normalizeRemoteHealthJobId0747(rawJobId) ?: return false
        val request = OneTimeWorkRequestBuilder<RemoteHealthWorker0747>()
            .addTag(RemoteSupportAutoAccess0763.WORK_TAG)
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
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
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
    private suspend fun uploadTechnicalZip0761(api: TripRemoteApi, jobId: String): Result {
        return runCatching {
            val app = applicationContext
            val source = UnifiedDebugEventStore.snapshot()
            val health = OperationalHealthCoordinator.scan(app)
            // Reuse the same generator and sanitization used by the manual ZIP action.
            val saved = withContext(Dispatchers.IO) {
                OperationalHealthTechnicalPackage0575.generateAndSave(
                    context = app, health = health, source = source,
                ).getOrThrow()
            }
            val bytes = withContext(Dispatchers.IO) {
                app.contentResolver.openInputStream(saved.uri)?.use { it.readBytes() }
                    ?: error("ZIP criado, mas nao pode ser lido para envio.")
            }
            check(bytes.size in 22..(640 * 1024)) {
                "ZIP tecnico excedeu limite seguro de 640 KiB para envio; arquivo local preservado."
            }
            val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            val payload = RemoteTechnicalZipPayload0761(
                archiveBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                archiveSha256 = sha,
                archiveBytes = bytes.size,
                capturedAtMillis = System.currentTimeMillis(),
                sourceAppVersion = BuildConfig.VERSION_NAME,
                sourceCommitSha = BuildConfig.BUILD_GIT_SHA,
                fileName = saved.displayName,
            )
            val response = api.submitRemoteHealthResult0747(
                jobId = jobId, status = "COMPLETE", technicalPackage = payload,
            )
            check(response.accepted) { "Servidor nao confirmou o recebimento do ZIP tecnico." }
            UnifiedDebugEventStore.recordAlways(
                "REMOTE_TECHNICAL_ZIP_SENT_0761", app.packageName,
                "jobPresent=true archiveBytes=${bytes.size} sha256=$sha",
            )
            Result.success()
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            if (isTransientRemoteDiagnosticFailure0771(error)) {
                UnifiedDebugEventStore.recordAlways(
                    "REMOTE_TECHNICAL_ZIP_RETRY_0771", applicationContext.packageName,
                    "jobPresent=true attempt=$runAttemptCount error=${error.javaClass.simpleName.take(80)}"
                )
                // WorkManager keeps the authorized job queued for network
                // recovery; the backend independently enforces its 30min TTL.
                return@getOrElse Result.retry()
            }
            // Non-transport failures may be reported to the server as FAILED.
            val reported = runCatching {
                api.submitRemoteHealthResult0747(
                    jobId = jobId, status = "FAILED",
                    errorCode = "REMOTE_TECHNICAL_ZIP_COLLECTION_FAILED",
                    errorMessage = error.javaClass.simpleName.take(120),
                ).accepted
            }.getOrDefault(false)
            UnifiedDebugEventStore.recordAlways(
                "REMOTE_TECHNICAL_ZIP_FAILED_0761", applicationContext.packageName,
                "jobPresent=true reported=$reported error=${error.javaClass.simpleName.take(80)}",
            )
            if (reported) Result.success() else if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    override suspend fun doWork(): Result {
        if (!RemoteSupportAutoAccess0763.enabled(applicationContext, "remote_health_collect")) {
            return Result.failure()
        }
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
            if (error is CancellationException) throw error
            return if (isTransientRemoteDiagnosticFailure0771(error)) Result.retry()
                else Result.failure()
        }
        if (ack.state in setOf("COMPLETE", "FAILED", "EXPIRED")) return Result.success()
        if (ack.mode == "TECHNICAL_ZIP") {
            return uploadTechnicalZip0761(api, jobId)
        }
        check(ack.mode == "HEALTH_SNAPSHOT") { "Modo de coleta desconhecido: ${ack.mode.take(32)}" }

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
                recentReleases = VersionHistoryLogic
                    .sortedReleases(ReleaseHistoryStore.load(applicationContext).releases)
                    .take(8)
                    .map { release ->
                        fun strings(items: List<String>): List<String> = items.take(10)
                            .map { safe(it, 240) }
                        RemoteReleaseAudit0765(
                            version = safe(release.version, 40),
                            build = release.build,
                            status = safe(release.status.orEmpty(), 40),
                            implemented = strings(release.implemented),
                            fixed = strings(release.fixed),
                            improved = strings(release.improved),
                            modulesAffected = strings(release.modulesAffected),
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
            if (error is CancellationException) throw error
            if (isTransientRemoteDiagnosticFailure0771(error)) {
                UnifiedDebugEventStore.recordAlways(
                    "REMOTE_HEALTH_SNAPSHOT_RETRY_0771", applicationContext.packageName,
                    "jobPresent=true attempt=$runAttemptCount error=${error.javaClass.simpleName.take(80)}"
                )
                return@getOrElse Result.retry()
            }
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
