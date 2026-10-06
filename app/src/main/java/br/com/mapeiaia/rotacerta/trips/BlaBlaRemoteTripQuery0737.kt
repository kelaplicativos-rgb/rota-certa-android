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

internal const val BLABLACAR_TRIP_QUERY_REMOTE_EVENT_0737 = "blablacar_trip_query"
private const val REMOTE_TRIP_QUERY_JOB_ID_0737 = "blablacar_trip_query_job_id_0737"
private const val REMOTE_TRIP_QUERY_PROFILE_UUID_0737 = "blablacar_trip_query_profile_uuid_0737"
private const val REMOTE_TRIP_QUERY_TRIP_ID_0737 = "blablacar_trip_query_trip_id_0737"
private const val REMOTE_TRIP_QUERY_TRIP_HREF_0737 = "blablacar_trip_query_trip_href_0737"
private const val REMOTE_TRIP_QUERY_CACHE_DIR_0737 = "blablacar-trip-query-remote-0737"

internal fun isBlaBlaRemoteTripQueryEvent0737(event: String?): Boolean =
    event?.trim() == BLABLACAR_TRIP_QUERY_REMOTE_EVENT_0737

internal fun normalizeRemoteTripQueryUuid0737(raw: String?): String? {
    val value = raw?.trim()?.lowercase()?.takeIf(String::isNotBlank) ?: return null
    return runCatching { UUID.fromString(value).toString() }.getOrNull()?.takeIf { it == value }
}

internal fun normalizeRemoteTripQueryHref0737(raw: String?, expectedTripId: String): String? {
    val canonical = BlaBlaCollectorUrlModule.canonical(raw.orEmpty())
    if (canonical.isBlank()) return null
    if (BlaBlaCollectorUrlModule.tripId(canonical) != expectedTripId) return null
    return canonical
}

@Serializable
internal data class BlaBlaRemoteTripPassenger0737(
    val name: String = "",
    val seats: Int = 1,
    val boarding: String = "",
    val dropoff: String = "",
)

@Serializable
internal data class BlaBlaRemoteTripQueryPayload0737(
    val schemaVersion: String = "rota-certa-blablacar-trip-query-v1",
    val kind: String = "BLABLACAR_TRIP_QUERY",
    val capturedAt: String = "",
    val sourceAppVersion: String = "",
    val sourceVersionCode: Long = 0L,
    val sourceCommitSha: String = "",
    val profileUuid: String = "",
    val tripId: String = "",
    val result: String = "PARTIAL",
    val operationalComplete: Boolean = false,
    val passengerRosterComplete: Boolean = false,
    val itineraryAuthoritative: Boolean = false,
    val date: String = "",
    val departureTime: String = "",
    val arrivalTime: String = "",
    val origin: String = "",
    val destination: String = "",
    val price: String = "",
    val availability: String = "unknown",
    val bookedSeats: Int = 0,
    val publishedSeats: Int? = null,
    val itineraryStops: List<String> = emptyList(),
    val itineraryStopTimes: List<String> = emptyList(),
    val passengers: List<BlaBlaRemoteTripPassenger0737> = emptyList(),
    val evidencePresent: Boolean = false,
    val errorCode: String = "",
)

@Serializable
internal data class BlaBlaRemoteTripQueryAckRequest0737(
    val state: String = "RUNNING",
    val appVersion: String = "",
    val sourceCommitSha: String = "",
)

@Serializable
internal data class BlaBlaRemoteTripQueryAckResponse0737(
    val accepted: Boolean = false,
    val jobId: String = "",
    val state: String = "",
)

@Serializable
internal data class BlaBlaRemoteTripQueryResultRequest0737(
    val status: String,
    val payload: BlaBlaRemoteTripQueryPayload0737? = null,
    val errorCode: String = "",
    val errorMessage: String = "",
)

@Serializable
internal data class BlaBlaRemoteTripQueryResultResponse0737(
    val accepted: Boolean = false,
    val jobId: String = "",
    val state: String = "",
)

internal fun buildBlaBlaRemoteTripQueryPayload0737(
    profileUuid: String,
    tripId: String,
    result: BlaBlaTargetedHtmlRefreshResult0607,
): BlaBlaRemoteTripQueryPayload0737 {
    val trip = result.trip
    val complete = result.operationalComplete &&
        trip != null &&
        trip.passenger_roster_complete &&
        trip.itinerary_authoritative &&
        trip.published_seats != null
    return BlaBlaRemoteTripQueryPayload0737(
        capturedAt = Instant.now().toString(),
        sourceAppVersion = AppBuildInfo.versionName,
        sourceVersionCode = AppBuildInfo.versionCode.toLong(),
        sourceCommitSha = AppBuildInfo.commit,
        profileUuid = profileUuid,
        tripId = tripId,
        result = if (complete) "COMPLETE" else "PARTIAL",
        operationalComplete = complete,
        passengerRosterComplete = trip?.passenger_roster_complete == true,
        itineraryAuthoritative = trip?.itinerary_authoritative == true,
        date = trip?.date.orEmpty(),
        departureTime = trip?.departure_time.orEmpty(),
        arrivalTime = trip?.arrival_time.orEmpty(),
        origin = trip?.actual_departure?.takeIf(String::isNotBlank)
            ?: trip?.search_from.orEmpty(),
        destination = trip?.actual_arrival?.takeIf(String::isNotBlank)
            ?: trip?.search_to.orEmpty(),
        price = trip?.price.orEmpty(),
        availability = trip?.availability.orEmpty().ifBlank { "unknown" },
        bookedSeats = trip?.booked_seats?.coerceAtLeast(0) ?: 0,
        publishedSeats = trip?.published_seats?.coerceAtLeast(0),
        itineraryStops = trip?.itinerary_stops.orEmpty(),
        itineraryStopTimes = trip?.itinerary_stop_times.orEmpty(),
        passengers = trip?.passengers.orEmpty().map { passenger ->
            BlaBlaRemoteTripPassenger0737(
                name = passenger.name.trim(),
                seats = passenger.seats.coerceAtLeast(1),
                boarding = passenger.boarding.orEmpty().trim(),
                dropoff = passenger.dropoff.orEmpty().trim(),
            )
        },
        evidencePresent = result.evidencePath.isNotBlank(),
        errorCode = result.errorCode.trim().take(120),
    )
}

internal object BlaBlaRemoteTripQueryScheduler0737 {
    fun enqueue(
        context: Context,
        rawJobId: String?,
        rawProfileUuid: String?,
        rawTripId: String?,
        rawTripHref: String?,
    ): Boolean {
        val jobId = normalizeRemoteTripQueryUuid0737(rawJobId) ?: return false
        val profileUuid = normalizeRemoteTripQueryUuid0737(rawProfileUuid) ?: return false
        val tripId = normalizeRemoteTripQueryUuid0737(rawTripId) ?: return false
        val tripHref = normalizeRemoteTripQueryHref0737(rawTripHref, tripId) ?: return false
        val request = OneTimeWorkRequestBuilder<BlaBlaRemoteTripQueryWorker0737>()
            .setInputData(
                Data.Builder()
                    .putString(REMOTE_TRIP_QUERY_JOB_ID_0737, jobId)
                    .putString(REMOTE_TRIP_QUERY_PROFILE_UUID_0737, profileUuid)
                    .putString(REMOTE_TRIP_QUERY_TRIP_ID_0737, tripId)
                    .putString(REMOTE_TRIP_QUERY_TRIP_HREF_0737, tripHref)
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
            "blablacar-trip-query-remote-0737:$jobId",
            ExistingWorkPolicy.KEEP,
            request,
        )
        return true
    }
}

internal class BlaBlaRemoteTripQueryWorker0737(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun doWork(): Result {
        val jobId = normalizeRemoteTripQueryUuid0737(inputData.getString(REMOTE_TRIP_QUERY_JOB_ID_0737))
            ?: return Result.failure()
        val profileUuid = normalizeRemoteTripQueryUuid0737(inputData.getString(REMOTE_TRIP_QUERY_PROFILE_UUID_0737))
            ?: return Result.failure()
        val tripId = normalizeRemoteTripQueryUuid0737(inputData.getString(REMOTE_TRIP_QUERY_TRIP_ID_0737))
            ?: return Result.failure()
        val tripHref = normalizeRemoteTripQueryHref0737(
            inputData.getString(REMOTE_TRIP_QUERY_TRIP_HREF_0737),
            tripId,
        ) ?: return Result.failure()

        val tripStore = TripStore(applicationContext)
        val settings = withContext(Dispatchers.IO) { tripStore.onlineSettings() }
        if (!settings.configured || settings.driverToken.isBlank() || settings.driverUsername.isBlank()) {
            return Result.failure()
        }
        val api = TripRemoteApi(settings)
        val cache = cacheFile0737(jobId)

        val ack = try {
            api.ackBlaBlaTripQueryJob0737(jobId)
        } catch (error: Throwable) {
            UnifiedDebugEventStore.record(
                "BLABLACAR_TRIP_QUERY_REMOTE_ACK_FAILED_0737",
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

        val accounts = BlaBlaDynamicAccountRegistry(applicationContext).list()
        val matching = accounts.filter {
            it.profileUuid?.trim()?.lowercase() == profileUuid
        }
        if (matching.size != 1) {
            val reported = reportFailure0737(
                api,
                jobId,
                "PROFILE_IDENTITY_NOT_UNIQUE",
                "O UUID do perfil não corresponde exatamente a uma conta autenticada.",
            )
            return if (reported) Result.success() else if (runAttemptCount < 3) Result.retry() else Result.failure()
        }

        val tenantId = RotaCertaTenantRegistry(applicationContext).activeScope().tenantId.trim()
        if (tenantId.isBlank()) {
            val reported = reportFailure0737(api, jobId, "TENANT_IDENTITY_MISSING", "Tenant ativo indisponível.")
            return if (reported) Result.success() else Result.failure()
        }

        val target = BlaBlaTripTarget0407(
            tenantId = tenantId,
            accountId = matching.single().id,
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
            val payload = buildBlaBlaRemoteTripQueryPayload0737(
                profileUuid = profileUuid,
                tripId = tripId,
                result = captured,
            )
            writeCachedPayload0737(cache, payload)
            submit0737(api, jobId, payload, cache)
        } catch (error: Throwable) {
            val reported = reportFailure0737(
                api,
                jobId,
                "REMOTE_TRIP_QUERY_FAILED",
                (error.message ?: error.javaClass.simpleName).take(220),
            )
            UnifiedDebugEventStore.record(
                "BLABLACAR_TRIP_QUERY_REMOTE_FAILED_0737",
                applicationContext.packageName,
                "jobPresent=true reported=$reported error=${error.javaClass.simpleName.take(80)}",
            )
            if (reported) Result.success() else if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun submit0737(
        api: TripRemoteApi,
        jobId: String,
        payload: BlaBlaRemoteTripQueryPayload0737,
        cache: File,
    ): Result = try {
        val response = api.submitBlaBlaTripQueryResult0737(
            jobId = jobId,
            status = payload.result,
            payload = payload,
        )
        check(response.accepted) { "Servidor não confirmou a consulta BlaBlaCar direcionada" }
        runCatching { cache.delete() }
        UnifiedDebugEventStore.record(
            "BLABLACAR_TRIP_QUERY_REMOTE_SENT_0737",
            applicationContext.packageName,
            "jobPresent=true result=${payload.result} passengers=${payload.passengers.size}",
        )
        Result.success()
    } catch (error: Throwable) {
        UnifiedDebugEventStore.record(
            "BLABLACAR_TRIP_QUERY_REMOTE_SEND_FAILED_0737",
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
        api.submitBlaBlaTripQueryResult0737(
            jobId = jobId,
            status = "FAILED",
            payload = null,
            errorCode = errorCode,
            errorMessage = errorMessage,
        ).accepted
    }.getOrDefault(false)

    private fun cacheFile0737(jobId: String): File {
        val dir = File(applicationContext.cacheDir, REMOTE_TRIP_QUERY_CACHE_DIR_0737)
        if (!dir.exists()) dir.mkdirs()
        dir.listFiles()?.forEach { file ->
            if (System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000L) {
                runCatching { file.delete() }
            }
        }
        return File(dir, "$jobId.json")
    }

    private suspend fun readCachedPayload0737(file: File): BlaBlaRemoteTripQueryPayload0737? =
        withContext(Dispatchers.IO) {
            if (!file.isFile || file.length() <= 0L || file.length() > 512 * 1024L) return@withContext null
            runCatching { json.decodeFromString<BlaBlaRemoteTripQueryPayload0737>(file.readText(Charsets.UTF_8)) }
                .getOrNull()
        }

    private suspend fun writeCachedPayload0737(
        file: File,
        payload: BlaBlaRemoteTripQueryPayload0737,
    ) = withContext(Dispatchers.IO) {
        val raw = json.encodeToString(payload)
        val bytes = raw.toByteArray(Charsets.UTF_8)
        require(bytes.isNotEmpty() && bytes.size <= 512 * 1024) {
            "Snapshot remoto direcionado excede o limite local"
        }
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }
}
