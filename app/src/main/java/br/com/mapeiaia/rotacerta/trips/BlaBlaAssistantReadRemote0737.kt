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
import br.com.mapeiaia.rotacerta.AppBuildInfo
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val BLABLACAR_HTML_REMOTE_EVENT_0737 = "blablacar_html_trip_collect"
private const val BLABLACAR_HTML_REMOTE_JOB_ID_0737 = "blablacar_html_job_id_0737"
private const val BLABLACAR_HTML_REMOTE_PROFILE_UUID_0737 = "blablacar_html_profile_uuid_0737"
private const val BLABLACAR_HTML_REMOTE_TRIP_ID_0737 = "blablacar_html_trip_id_0737"
private const val BLABLACAR_HTML_REMOTE_CACHE_DIR_0737 = "blablacar-html-remote-0737"
private const val BLABLACAR_HTML_REMOTE_MAX_CACHE_BYTES_0737 = 512 * 1024

internal fun isBlaBlaHtmlRemoteEvent0737(event: String?): Boolean =
    event?.trim() == BLABLACAR_HTML_REMOTE_EVENT_0737

internal fun canonicalUuid0737(raw: String?): String? {
    val value = raw?.trim()?.lowercase()?.takeIf(String::isNotBlank) ?: return null
    return runCatching { UUID.fromString(value).toString() }
        .getOrNull()
        ?.takeIf { it == value }
}

@Serializable
internal data class BlaBlaHtmlRemotePassenger0737(
    val name: String = "",
    val seats: Int = 1,
    val boarding: String = "",
    val dropoff: String = "",
)

@Serializable
internal data class BlaBlaHtmlRemoteTripSnapshot0737(
    val profileUuid: String,
    val profileName: String = "",
    val tripId: String,
    val date: String,
    val departureTime: String = "",
    val arrivalTime: String = "",
    val origin: String = "",
    val destination: String = "",
    val price: String = "",
    val availability: String = "unknown",
    val passengers: List<BlaBlaHtmlRemotePassenger0737> = emptyList(),
    val bookedSeats: Int = 0,
    val publishedSeats: Int? = null,
    val passengerRosterComplete: Boolean = false,
    val itineraryStops: List<String> = emptyList(),
    val itineraryStopTimes: List<String> = emptyList(),
    val itineraryAuthoritative: Boolean = false,
    val publicTripUrl: String = "",
    val identityConflict: Boolean = false,
)

@Serializable
internal data class BlaBlaHtmlRemotePayload0737(
    val schemaVersion: String = "rota-certa-blablacar-html-trip-v1",
    val kind: String = "BLABLACAR_HTML_TRIP_QUERY",
    val capturedAt: String,
    val sourceAppVersion: String,
    val sourceVersionCode: Int,
    val sourceCommitSha: String,
    val sourceBranch: String,
    val profileUuid: String,
    val tripId: String,
    val result: String,
    val operationalComplete: Boolean,
    val errorCode: String = "",
    val snapshot: BlaBlaHtmlRemoteTripSnapshot0737? = null,
    val privacy: BlaBlaHtmlRemotePrivacy0737 = BlaBlaHtmlRemotePrivacy0737(),
)

@Serializable
internal data class BlaBlaHtmlRemotePrivacy0737(
    val rawHtmlUploaded: Boolean = false,
    val cookiesUploaded: Boolean = false,
    val blablaCredentialsUploaded: Boolean = false,
    val passengerPhoneUploaded: Boolean = false,
    val passengerBookingHrefUploaded: Boolean = false,
    val writesAgenda: Boolean = false,
    val writesTimeline: Boolean = false,
    val writesCollectorCanonicalState: Boolean = false,
)

@Serializable
internal data class BlaBlaHtmlRemoteAccessResponse0737(
    val enabled: Boolean = false,
    val tripRefreshPath: String = "",
    val tripLatestPath: String = "",
    val expiresAtMillis: Long = 0L,
)

@Serializable
internal data class BlaBlaHtmlRemoteJobAckRequest0737(
    val state: String = "RUNNING",
    val appVersion: String = "",
    val sourceCommitSha: String = "",
)

@Serializable
internal data class BlaBlaHtmlRemoteJobAckResponse0737(
    val accepted: Boolean = false,
    val jobId: String = "",
    val state: String = "",
)

@Serializable
internal data class BlaBlaHtmlRemoteResultRequest0737(
    val status: String,
    val payload: BlaBlaHtmlRemotePayload0737? = null,
    val errorCode: String = "",
    val errorMessage: String = "",
)

@Serializable
internal data class BlaBlaHtmlRemoteResultResponse0737(
    val accepted: Boolean = false,
    val jobId: String = "",
    val state: String = "",
)

internal data class BlaBlaHtmlRemoteAccess0737(
    val tripRefreshUrl: String = "",
    val tripLatestUrl: String = "",
    val expiresAtMillis: Long = 0L,
) {
    val configured: Boolean
        get() = tripRefreshUrl.startsWith("https://") &&
            tripLatestUrl.startsWith("https://") &&
            expiresAtMillis > System.currentTimeMillis()
}

internal class BlaBlaHtmlRemoteAccessStore0737(context: Context) {
    private val app = context.applicationContext
    private val scope = RotaCertaTenantRegistry(app).activeScope()
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(): BlaBlaHtmlRemoteAccess0737 = BlaBlaHtmlRemoteAccess0737(
        tripRefreshUrl = prefs.getString(scope.key(KEY_REFRESH), "").orEmpty(),
        tripLatestUrl = prefs.getString(scope.key(KEY_LATEST), "").orEmpty(),
        expiresAtMillis = prefs.getLong(scope.key(KEY_EXPIRES), 0L),
    )

    fun save(publicBaseUrl: String, response: BlaBlaHtmlRemoteAccessResponse0737): BlaBlaHtmlRemoteAccess0737 {
        val base = publicBaseUrl.trim().trimEnd('/')
        require(base.startsWith("https://")) { "Base pública HTTPS não configurada" }
        require(response.enabled) { "Consulta HTML remota não habilitada pelo servidor" }
        require(response.tripRefreshPath.startsWith("/v1/public/blablacar-html/")) {
            "Caminho privado de atualização HTML inválido"
        }
        require(response.tripLatestPath.startsWith("/v1/public/blablacar-html/")) {
            "Caminho privado de leitura HTML inválido"
        }
        val value = BlaBlaHtmlRemoteAccess0737(
            tripRefreshUrl = base + response.tripRefreshPath,
            tripLatestUrl = base + response.tripLatestPath,
            expiresAtMillis = response.expiresAtMillis,
        )
        prefs.edit()
            .putString(scope.key(KEY_REFRESH), value.tripRefreshUrl)
            .putString(scope.key(KEY_LATEST), value.tripLatestUrl)
            .putLong(scope.key(KEY_EXPIRES), value.expiresAtMillis)
            .apply()
        return value
    }

    companion object {
        private const val PREFS = "rota_certa_blablacar_html_remote_0737"
        private const val KEY_REFRESH = "trip_refresh_url"
        private const val KEY_LATEST = "trip_latest_url"
        private const val KEY_EXPIRES = "expires_at"
    }
}

internal object BlaBlaHtmlRemoteScheduler0737 {
    fun enqueue(
        context: Context,
        rawJobId: String?,
        rawProfileUuid: String?,
        rawTripId: String?,
    ): Boolean {
        val jobId = canonicalUuid0737(rawJobId) ?: return false
        val profileUuid = canonicalUuid0737(rawProfileUuid) ?: return false
        val tripId = canonicalUuid0737(rawTripId) ?: return false
        val request = OneTimeWorkRequestBuilder<BlaBlaHtmlRemoteWorker0737>()
            .setInputData(
                Data.Builder()
                    .putString(BLABLACAR_HTML_REMOTE_JOB_ID_0737, jobId)
                    .putString(BLABLACAR_HTML_REMOTE_PROFILE_UUID_0737, profileUuid)
                    .putString(BLABLACAR_HTML_REMOTE_TRIP_ID_0737, tripId)
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
            "blablacar-html-remote-0737:$jobId",
            ExistingWorkPolicy.KEEP,
            request,
        )
        return true
    }
}

internal class BlaBlaHtmlRemoteWorker0737(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val json0737 = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun doWork(): Result {
        val jobId = canonicalUuid0737(inputData.getString(BLABLACAR_HTML_REMOTE_JOB_ID_0737))
            ?: return Result.failure()
        val profileUuid = canonicalUuid0737(inputData.getString(BLABLACAR_HTML_REMOTE_PROFILE_UUID_0737))
            ?: return Result.failure()
        val tripId = canonicalUuid0737(inputData.getString(BLABLACAR_HTML_REMOTE_TRIP_ID_0737))
            ?: return Result.failure()

        val store = TripStore(applicationContext)
        val settings = withContext(Dispatchers.IO) { store.onlineSettings() }
        if (!settings.configured || settings.driverToken.isBlank() || settings.driverUsername.isBlank()) {
            return Result.failure()
        }
        val api = TripRemoteApi(settings)
        val cache = cacheFile0737(jobId)

        val ack = try {
            api.ackBlaBlaHtmlRemoteJob0737(jobId)
        } catch (error: Throwable) {
            UnifiedDebugEventStore.record(
                "BLABLACAR_HTML_REMOTE_ACK_FAILED_0737",
                applicationContext.packageName,
                "jobPresent=true error=${error.javaClass.simpleName.take(80)}",
            )
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
        if (ack.state.trim().uppercase() in setOf("COMPLETE", "PARTIAL", "FAILED", "EXPIRED")) {
            runCatching { cache.delete() }
            return Result.success()
        }

        readCachedPayload0737(cache)?.let { cached ->
            return submit0737(api, jobId, cached, cache)
        }

        val matchingAccounts = BlaBlaDynamicAccountRegistry(applicationContext).list().filter { account ->
            account.profileUuid?.trim()?.lowercase() == profileUuid
        }
        if (matchingAccounts.size != 1) {
            reportFailure0737(
                api,
                jobId,
                "HTML_REMOTE_PROFILE_IDENTITY_UNRESOLVED",
                "O UUID do perfil não corresponde exatamente a uma conta autenticada no aparelho.",
            )
            return Result.success()
        }
        val account = matchingAccounts.single()
        val tenantId = RotaCertaTenantRegistry(applicationContext).activeScope().tenantId.trim()
        if (tenantId.isBlank()) {
            reportFailure0737(api, jobId, "HTML_REMOTE_TENANT_UNAVAILABLE", "Tenant ativo não disponível.")
            return Result.success()
        }

        val tripHref = BlaBlaCollectorUrlModule.canonical(
            "${BlaBlaCollectorUrlModule.ORIGIN}/rides/offer/$tripId",
        )
        if (BlaBlaCollectorUrlModule.tripId(tripHref) != tripId) {
            reportFailure0737(api, jobId, "HTML_REMOTE_TRIP_URL_INVALID", "TripId não pôde ser vinculado à URL administrativa.")
            return Result.success()
        }

        val target = BlaBlaTripTarget0407(
            tenantId = tenantId,
            accountId = account.id,
            profileUuid = profileUuid,
            tripId = tripId,
            tripHref = tripHref,
        )

        return try {
            val captured = BlaBlaUnifiedHtmlCapture0605.captureSingleTrip0607(
                context = applicationContext,
                target = target,
                existingSource = null,
                scopedStateIsolation0662 = true,
            )
            val trip = captured.trip
            if (trip == null) {
                reportFailure0737(
                    api,
                    jobId,
                    captured.errorCode.ifBlank { "HTML_REMOTE_TRIP_CAPTURE_FAILED" },
                    "A captura HTML direcionada não comprovou a viagem solicitada.",
                )
                return Result.success()
            }

            if (
                trip.profile_uuid.trim().lowercase() != profileUuid ||
                trip.trip_id?.trim()?.lowercase() != tripId ||
                trip.identity_conflict
            ) {
                reportFailure0737(
                    api,
                    jobId,
                    "HTML_REMOTE_STRONG_IDENTITY_MISMATCH",
                    "O HTML retornado não corresponde ao UUID + tripId solicitados.",
                )
                return Result.success()
            }

            val complete = captured.operationalComplete &&
                trip.passenger_roster_complete &&
                trip.itinerary_authoritative &&
                trip.published_seats != null

            val payload = BlaBlaHtmlRemotePayload0737(
                capturedAt = Instant.now().toString(),
                sourceAppVersion = AppBuildInfo.versionName,
                sourceVersionCode = AppBuildInfo.versionCode,
                sourceCommitSha = AppBuildInfo.commit,
                sourceBranch = AppBuildInfo.branch,
                profileUuid = profileUuid,
                tripId = tripId,
                result = if (complete) "COMPLETE" else "PARTIAL",
                operationalComplete = complete,
                errorCode = if (complete) "" else captured.errorCode.ifBlank { "HTML_REMOTE_OPERATIONALLY_INCOMPLETE" },
                snapshot = trip.toRemoteSnapshot0737(),
            )
            writeCachedPayload0737(cache, payload)
            submit0737(api, jobId, payload, cache)
        } catch (error: Throwable) {
            val reported = reportFailure0737(
                api,
                jobId,
                "HTML_REMOTE_DEVICE_COLLECTION_FAILED",
                (error.message ?: error.javaClass.simpleName).take(220),
            )
            if (reported) Result.success() else if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun submit0737(
        api: TripRemoteApi,
        jobId: String,
        payload: BlaBlaHtmlRemotePayload0737,
        cache: File,
    ): Result = try {
        val response = api.submitBlaBlaHtmlRemoteResult0737(
            jobId = jobId,
            status = payload.result,
            payload = payload,
        )
        check(response.accepted) { "Servidor não confirmou o snapshot HTML remoto" }
        runCatching { cache.delete() }
        UnifiedDebugEventStore.record(
            "BLABLACAR_HTML_REMOTE_RESULT_SENT_0737",
            applicationContext.packageName,
            "jobPresent=true result=${payload.result} profileUuidPresent=true tripIdPresent=true rawHtmlUploaded=false",
        )
        Result.success()
    } catch (error: Throwable) {
        UnifiedDebugEventStore.record(
            "BLABLACAR_HTML_REMOTE_RESULT_SEND_FAILED_0737",
            applicationContext.packageName,
            "jobPresent=true error=${error.javaClass.simpleName.take(80)}",
        )
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }

    private suspend fun reportFailure0737(
        api: TripRemoteApi,
        jobId: String,
        errorCode: String,
        errorMessage: String,
    ): Boolean = runCatching {
        api.submitBlaBlaHtmlRemoteResult0737(
            jobId = jobId,
            status = "FAILED",
            payload = null,
            errorCode = errorCode,
            errorMessage = errorMessage,
        ).accepted
    }.getOrDefault(false)

    private fun cacheFile0737(jobId: String): File {
        val dir = File(applicationContext.cacheDir, BLABLACAR_HTML_REMOTE_CACHE_DIR_0737)
        if (!dir.exists()) dir.mkdirs()
        dir.listFiles()?.forEach { file ->
            if (System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000L) {
                runCatching { file.delete() }
            }
        }
        return File(dir, "$jobId.json")
    }

    private suspend fun readCachedPayload0737(file: File): BlaBlaHtmlRemotePayload0737? =
        withContext(Dispatchers.IO) {
            if (!file.isFile || file.length() <= 0L || file.length() > BLABLACAR_HTML_REMOTE_MAX_CACHE_BYTES_0737) {
                return@withContext null
            }
            runCatching { json0737.decodeFromString<BlaBlaHtmlRemotePayload0737>(file.readText(Charsets.UTF_8)) }
                .getOrNull()
        }

    private suspend fun writeCachedPayload0737(file: File, payload: BlaBlaHtmlRemotePayload0737) =
        withContext(Dispatchers.IO) {
            val bytes = json0737.encodeToString(payload).toByteArray(Charsets.UTF_8)
            require(bytes.isNotEmpty() && bytes.size <= BLABLACAR_HTML_REMOTE_MAX_CACHE_BYTES_0737) {
                "Snapshot HTML remoto excede o limite local"
            }
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
        }
}

private fun BlaBlaCollectorTrip.toRemoteSnapshot0737(): BlaBlaHtmlRemoteTripSnapshot0737 =
    BlaBlaHtmlRemoteTripSnapshot0737(
        profileUuid = profile_uuid.trim().lowercase(),
        profileName = profile_name.trim().take(120),
        tripId = trip_id.orEmpty().trim().lowercase(),
        date = date.trim().take(20),
        departureTime = departure_time.orEmpty().trim().take(16),
        arrivalTime = arrival_time.orEmpty().trim().take(16),
        origin = actual_departure.orEmpty().ifBlank { search_from.orEmpty() }.trim().take(240),
        destination = actual_arrival.orEmpty().ifBlank { search_to.orEmpty() }.trim().take(240),
        price = price.orEmpty().trim().take(80),
        availability = availability.trim().take(80),
        passengers = passengers.take(12).map { passenger ->
            BlaBlaHtmlRemotePassenger0737(
                name = passenger.name.trim().take(120),
                seats = passenger.seats.coerceIn(1, 8),
                boarding = passenger.boarding.orEmpty().trim().take(180),
                dropoff = passenger.dropoff.orEmpty().trim().take(180),
            )
        },
        bookedSeats = booked_seats.coerceAtLeast(0),
        publishedSeats = published_seats?.coerceAtLeast(0),
        passengerRosterComplete = passenger_roster_complete,
        itineraryStops = itinerary_stops.take(32).map { it.trim().take(180) },
        itineraryStopTimes = itinerary_stop_times.take(32).map { it.trim().take(16) },
        itineraryAuthoritative = itinerary_authoritative,
        publicTripUrl = public_trip_href.orEmpty().trim().take(500),
        identityConflict = identity_conflict,
    )
