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
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val STANDALONE_COVERS_REMOTE_EVENT_0736 = "standalone_covers_collect"
private const val STANDALONE_COVERS_REMOTE_JOB_ID_0736 = "standalone_covers_job_id_0736"
private const val STANDALONE_COVERS_REMOTE_CACHE_DIR_0736 = "standalone-covers-remote-0736"

internal fun isStandaloneCoversRemoteEvent0736(event: String?): Boolean =
    event?.trim() == STANDALONE_COVERS_REMOTE_EVENT_0736

internal fun normalizeStandaloneCoversRemoteJobId0736(raw: String?): String? {
    val value = raw?.trim()?.lowercase()?.takeIf(String::isNotBlank) ?: return null
    return runCatching { UUID.fromString(value).toString() }
        .getOrNull()
        ?.takeIf { it == value }
}

internal fun standaloneCoversRemoteResultState0736(
    payload: BlaBlaStandaloneRideCoversPayload0734,
): String = when (payload.result) {
    RESULT_COMPLETE_0734 -> RESULT_COMPLETE_0734
    RESULT_PARTIAL_0734 -> RESULT_PARTIAL_0734
    else -> RESULT_PARTIAL_0734
}

internal fun isStandaloneCoversRemoteTerminalState0736(state: String?): Boolean =
    state?.trim()?.uppercase() in setOf(
        RESULT_COMPLETE_0734,
        RESULT_PARTIAL_0734,
        "FAILED",
        "EXPIRED",
    )

internal data class StandaloneCoversRemoteAccess0736(
    val refreshUrl: String = "",
    val latestUrl: String = "",
    val tripQueryBaseUrl: String = "",
    val expiresAtMillis: Long = 0L,
) {
    val configured: Boolean
        get() = refreshUrl.startsWith("https://") &&
            latestUrl.startsWith("https://") &&
            expiresAtMillis > System.currentTimeMillis()
}

internal class StandaloneCoversRemoteAccessStore0736(context: Context) {
    private val app = context.applicationContext
    private val scope = RotaCertaTenantRegistry(app).activeScope()
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(): StandaloneCoversRemoteAccess0736 = StandaloneCoversRemoteAccess0736(
        refreshUrl = prefs.getString(scope.key(KEY_REFRESH), "").orEmpty(),
        latestUrl = prefs.getString(scope.key(KEY_LATEST), "").orEmpty(),
        tripQueryBaseUrl = prefs.getString(scope.key(KEY_TRIP_QUERY_BASE), "").orEmpty(),
        expiresAtMillis = prefs.getLong(scope.key(KEY_EXPIRES), 0L),
    )

    fun save(
        publicBaseUrl: String,
        response: StandaloneCoversAccessResponse0736,
    ): StandaloneCoversRemoteAccess0736 {
        val base = publicBaseUrl.trim().trimEnd('/')
        require(base.startsWith("https://")) { "Base pública HTTPS não configurada" }
        // A valid capability may be provisioned but currently revoked by the OFF toggle.
        // Saving a disabled access URL does not reactivate it; only the authenticated state
        // endpoint can grant access again.
        require(response.refreshPath.startsWith("/v1/public/standalone-covers/")) {
            "Caminho privado de coleta remota inválido"
        }
        require(response.latestPath.startsWith("/v1/public/standalone-covers/")) {
            "Caminho privado de leitura remota inválido"
        }
        val tripQueryBaseUrl = response.tripQueryBasePath
            .takeIf { it.startsWith("/v1/public/blablacar-query/") }
            ?.let { base + it }
            .orEmpty()
        val value = StandaloneCoversRemoteAccess0736(
            refreshUrl = base + response.refreshPath,
            latestUrl = base + response.latestPath,
            tripQueryBaseUrl = tripQueryBaseUrl,
            expiresAtMillis = response.expiresAtMillis,
        )
        prefs.edit()
            .putString(scope.key(KEY_REFRESH), value.refreshUrl)
            .putString(scope.key(KEY_LATEST), value.latestUrl)
            .putString(scope.key(KEY_TRIP_QUERY_BASE), value.tripQueryBaseUrl)
            .putLong(scope.key(KEY_EXPIRES), value.expiresAtMillis)
            .apply()
        return value
    }

    companion object {
        private const val PREFS = "rota_certa_standalone_covers_remote_0736"
        private const val KEY_REFRESH = "refresh_url"
        private const val KEY_LATEST = "latest_url"
        private const val KEY_TRIP_QUERY_BASE = "trip_query_base_url"
        private const val KEY_EXPIRES = "expires_at"
    }
}

internal object StandaloneCoversRemoteScheduler0736 {
    fun enqueue(context: Context, rawJobId: String?): Boolean {
        val jobId = normalizeStandaloneCoversRemoteJobId0736(rawJobId) ?: return false
        val request = OneTimeWorkRequestBuilder<StandaloneCoversRemoteWorker0736>()
            .setInputData(
                Data.Builder()
                    .putString(STANDALONE_COVERS_REMOTE_JOB_ID_0736, jobId)
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
            "standalone-covers-remote-0736:$jobId",
            ExistingWorkPolicy.KEEP,
            request,
        )
        return true
    }
}

internal class StandaloneCoversRemoteWorker0736(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val jobId = normalizeStandaloneCoversRemoteJobId0736(
            inputData.getString(STANDALONE_COVERS_REMOTE_JOB_ID_0736),
        ) ?: return Result.failure()

        val store = TripStore(applicationContext)
        val settings = withContext(Dispatchers.IO) { store.onlineSettings() }
        if (!settings.configured || settings.driverToken.isBlank() || settings.driverUsername.isBlank()) {
            UnifiedDebugEventStore.record(
                "STANDALONE_COVERS_REMOTE_CONFIGURATION_MISSING_0736",
                applicationContext.packageName,
                "jobPresent=true",
            )
            return Result.failure()
        }

        val api = TripRemoteApi(settings)
        val cache = cacheFile0736(jobId)

        val ack = try {
            api.ackStandaloneCoversJob0736(jobId = jobId, state = "RUNNING")
        } catch (error: Throwable) {
            UnifiedDebugEventStore.record(
                "STANDALONE_COVERS_REMOTE_ACK_FAILED_0736",
                applicationContext.packageName,
                "jobPresent=true error=${error.javaClass.simpleName.take(80)}",
            )
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
        if (isStandaloneCoversRemoteTerminalState0736(ack.state)) {
            runCatching { cache.delete() }
            UnifiedDebugEventStore.record(
                "STANDALONE_COVERS_REMOTE_ALREADY_TERMINAL_0736",
                applicationContext.packageName,
                "jobPresent=true state=${ack.state.take(20)} recollect=false",
            )
            return Result.success()
        }

        val cached = readCachedPayload0736(cache)
        if (cached != null) {
            return submitPayload0736(api, jobId, cached, cache)
        }

        val accounts = BlaBlaDynamicAccountRegistry(applicationContext).list()
        if (accounts.isEmpty()) {
            reportFailure0736(
                api = api,
                jobId = jobId,
                errorCode = "NO_BLABLACAR_PROFILES_CONFIGURED",
                errorMessage = "Nenhum perfil BlaBlaCar está configurado no aparelho.",
            )
            return Result.success()
        }

        return try {
            val payload = BlaBlaStandaloneRideCoversExport0734.collectPayload0736(
                context = applicationContext,
                accounts = accounts,
                onProgress = { progress ->
                    UnifiedDebugEventStore.record(
                        "STANDALONE_COVERS_REMOTE_PROGRESS_0736",
                        applicationContext.packageName,
                        progress.take(180),
                    )
                },
            )
            val raw = encodeStandaloneRideCoversPayload0734(payload)
            writeCachedPayload0736(cache, raw)
            submitPayload0736(api, jobId, payload, cache)
        } catch (error: Throwable) {
            val reported = reportFailure0736(
                api = api,
                jobId = jobId,
                errorCode = "REMOTE_DEVICE_COLLECTION_FAILED",
                errorMessage = (error.message ?: error.javaClass.simpleName).take(220),
            )
            UnifiedDebugEventStore.record(
                "STANDALONE_COVERS_REMOTE_COLLECTION_FAILED_0736",
                applicationContext.packageName,
                "jobPresent=true reported=$reported error=${error.javaClass.simpleName.take(80)}",
            )
            if (reported) Result.success() else if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun submitPayload0736(
        api: TripRemoteApi,
        jobId: String,
        payload: BlaBlaStandaloneRideCoversPayload0734,
        cache: File,
    ): Result = try {
        val response = api.submitStandaloneCoversResult0736(
            jobId = jobId,
            status = standaloneCoversRemoteResultState0736(payload),
            payload = payload,
        )
        check(response.accepted) { "Servidor não confirmou o resultado avulso" }
        runCatching { cache.delete() }
        UnifiedDebugEventStore.record(
            "STANDALONE_COVERS_REMOTE_RESULT_SENT_0736",
            applicationContext.packageName,
            "jobPresent=true result=${payload.result} profiles=${payload.totalProfiles} cards=${payload.totalCards}",
        )
        Result.success()
    } catch (error: Throwable) {
        UnifiedDebugEventStore.record(
            "STANDALONE_COVERS_REMOTE_RESULT_SEND_FAILED_0736",
            applicationContext.packageName,
            "jobPresent=true error=${error.javaClass.simpleName.take(80)}",
        )
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }

    private suspend fun reportFailure0736(
        api: TripRemoteApi,
        jobId: String,
        errorCode: String,
        errorMessage: String,
    ): Boolean = runCatching {
        api.submitStandaloneCoversResult0736(
            jobId = jobId,
            status = "FAILED",
            payload = null,
            errorCode = errorCode,
            errorMessage = errorMessage,
        ).accepted
    }.getOrDefault(false)

    private fun cacheFile0736(jobId: String): File {
        val dir = File(applicationContext.cacheDir, STANDALONE_COVERS_REMOTE_CACHE_DIR_0736)
        if (!dir.exists()) dir.mkdirs()
        dir.listFiles()?.forEach { file ->
            if (System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000L) {
                runCatching { file.delete() }
            }
        }
        return File(dir, "$jobId.json")
    }

    private suspend fun readCachedPayload0736(
        file: File,
    ): BlaBlaStandaloneRideCoversPayload0734? = withContext(Dispatchers.IO) {
        if (!file.isFile || file.length() <= 0L || file.length() > 2 * 1024 * 1024L) return@withContext null
        runCatching {
            decodeStandaloneRideCoversPayload0734(file.readText(Charsets.UTF_8))
        }.getOrNull()
    }

    private suspend fun writeCachedPayload0736(
        file: File,
        raw: String,
    ) = withContext(Dispatchers.IO) {
        val bytes = raw.toByteArray(Charsets.UTF_8)
        require(bytes.isNotEmpty() && bytes.size <= 2 * 1024 * 1024) {
            "Snapshot remoto avulso excede o limite local"
        }
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }
}
