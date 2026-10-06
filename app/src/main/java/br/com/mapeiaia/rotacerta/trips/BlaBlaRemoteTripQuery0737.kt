package br.com.mapeiaia.rotacerta.trips

import android.content.Context
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
import br.com.mapeiaia.rotacerta.AppBuildInfo
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val BLABLACAR_REMOTE_TRIP_QUERY_EVENT_0737 = "blablacar_trip_query_collect"
private const val BLABLACAR_REMOTE_TRIP_QUERY_JOB_ID_0737 = "blablacar_trip_query_job_id_0737"
private const val BLABLACAR_REMOTE_TRIP_QUERY_CACHE_DIR_0737 = "blablacar-trip-query-remote-0737"

internal fun isBlaBlaRemoteTripQueryEvent0737(event: String?): Boolean =
    event?.trim() == BLABLACAR_REMOTE_TRIP_QUERY_EVENT_0737

internal fun normalizeBlaBlaRemoteTripQueryJobId0737(raw: String?): String? {
    val value = raw?.trim()?.lowercase()?.takeIf(String::isNotBlank) ?: return null
    return runCatching { UUID.fromString(value).toString() }
        .getOrNull()
        ?.takeIf { it == value }
}

internal fun normalizeBlaBlaRemoteTripId0737(raw: String?): String? =
    raw?.trim()
        ?.take(160)
        ?.takeIf { it.matches(Regex("^[A-Za-z0-9_-]{8,160}$")) }

@Serializable
internal data class BlaBlaRemoteTripPassenger0737(
    val name: String = "",
    val seats: Int = 1,
    val boarding: String = "",
    val dropoff: String = "",
)

@Serializable
internal data class BlaBlaRemoteTripSnapshot0737(
    val schemaVersion: String = "rota-certa-blablacar-trip-query-v1",
    val kind: String = "BLABLACAR_TRIP_QUERY",
    val capturedAt: String = "",
    val sourceAppVersion: String = "",
    val sourceVersionCode: Int = 0,
    val sourceCommitSha: String = "",
    val sourceBranch: String = "",
    val profileUuid: String = "",
    val tripId: String = "",
    val dateIso: String = "",
    val departureTime: String = "",
    val arrivalTime: String = "",
    val origin: String = "",
    val destination: String = "",
    val price: String = "",
    val availability: String = "unknown",
    val bookedSeats: Int = 0,
    val publishedSeats: Int? = null,
    val passengerRosterComplete: Boolean = false,
    val itineraryAuthoritative: Boolean = false,
    val operationalComplete: Boolean = false,
    val passengerCount: Int = 0,
    val passengerSeatCount: Int = 0,
    val passengers: List<BlaBlaRemoteTripPassenger0737> = emptyList(),
    val itineraryStops: List<String> = emptyList(),
    val itineraryStopTimes: List<String> = emptyList(),
)

@Serializable
private data class BlaBlaRemoteTripCachedResult0737(
    val status: String,
    val payload: BlaBlaRemoteTripSnapshot0737? = null,
    val errorCode: String = "",
    val errorMessage: String = "",
)

internal fun toBlaBlaRemoteTripSnapshot0737(
    result: BlaBlaTargetedHtmlRefreshResult0607,
    expectedProfileUuid: String,
    expectedTripId: String,
): BlaBlaRemoteTripSnapshot0737? {
    val profileUuid = expectedProfileUuid.trim().lowercase()
    val tripId = normalizeBlaBlaRemoteTripId0737(expectedTripId) ?: return null
    val trip = result.trip ?: return null
    if (
        trip.identity_conflict ||
        !trip.profile_uuid.trim().equals(profileUuid, ignoreCase = true) ||
        trip.trip_id?.trim() != tripId
    ) {
        return null
    }
    val passengers = trip.passengers.map { passenger ->
        BlaBlaRemoteTripPassenger0737(
            name = passenger.name.trim().take(160),
            seats = passenger.seats.coerceAtLeast(1),
            boarding = passenger.boarding.orEmpty().trim().take(240),
            dropoff = passenger.dropoff.orEmpty().trim().take(240),
        )
    }
    return BlaBlaRemoteTripSnapshot0737(
        capturedAt = Instant.now().toString(),
        sourceAppVersion = AppBuildInfo.versionName,
        sourceVersionCode = AppBuildInfo.versionCode,
        sourceCommitSha = AppBuildInfo.commit,
        sourceBranch = AppBuildInfo.branch,
        profileUuid = profileUuid,
        tripId = tripId,
        dateIso = trip.date.trim().take(20),
        departureTime = trip.departure_time.orEmpty().trim().take(40),
        arrivalTime = trip.arrival_time.orEmpty().trim().take(40),
        origin = trip.actual_departure.orEmpty().ifBlank { trip.search_from.orEmpty() }.trim().take(500),
        destination = trip.actual_arrival.orEmpty().ifBlank { trip.search_to.orEmpty() }.trim().take(500),
        price = trip.price.orEmpty().trim().take(120),
        availability = trip.availability.trim().take(80).ifBlank { "unknown" },
        bookedSeats = trip.booked_seats.coerceAtLeast(0),
        publishedSeats = trip.published_seats?.coerceAtLeast(0),
        passengerRosterComplete = trip.passenger_roster_complete,
        itineraryAuthoritative = trip.itinerary_authoritative,
        operationalComplete = result.operationalComplete,
        passengerCount = passengers.size,
        passengerSeatCount = passengers.sumOf { it.seats.coerceAtLeast(1) },
        passengers = passengers,
        itineraryStops = trip.itinerary_stops.map { it.trim().take(240) },
        itineraryStopTimes = trip.itinerary_stop_times.map { it.trim().take(40) },
    )
}

internal fun blaBlaRemoteTripQueryResultState0737(
    snapshot: BlaBlaRemoteTripSnapshot0737,
): String = if (
    snapshot.operationalComplete &&
    snapshot.passengerRosterComplete &&
    snapshot.itineraryAuthoritative &&
    snapshot.publishedSeats != null
) {
    "COMPLETE"
} else {
    "PARTIAL"
}

internal object BlaBlaRemoteTripQueryScheduler0737 {
    fun enqueue(context: Context, rawJobId: String?): Boolean {
        val jobId = normalizeBlaBlaRemoteTripQueryJobId0737(rawJobId) ?: return false
        val request = OneTimeWorkRequestBuilder<BlaBlaRemoteTripQueryWorker0737>()
            .setInputData(
                Data.Builder()
                    .putString(BLABLACAR_REMOTE_TRIP_QUERY_JOB_ID_0737, jobId)
                    .build(),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, 20, TimeUnit.SECONDS)
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
    private val json0737 = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun doWork(): Result {
        val jobId = normalizeBlaBlaRemoteTripQueryJobId0737(
            inputData.getString(BLABLACAR_REMOTE_TRIP_QUERY_JOB_ID_0737),
        ) ?: return Result.failure()

        val store = TripStore(applicationContext)
        val settings = withContext(Dispatchers.IO) { store.onlineSettings() }
        if (!settings.configured || settings.driverToken.isBlank() || settings.driverUsername.isBlank()) {
            return Result.failure()
        }
        val api = TripRemoteApi(settings)
        val cache = cacheFile0737(jobId)

        val ack = try {
            api.ackBlaBlaRemoteTripQueryJob0737(jobId)
        } catch (error: Throwable) {
            UnifiedDebugEventStore.record(
                "BLABLACAR_REMOTE_TRIP_QUERY_ACK_FAILED_0737",
                applicationContext.packageName,
                "jobPresent=true error=${error.javaClass.simpleName.take(80)}",
            )
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }

        if (ack.state.trim().uppercase() in setOf("COMPLETE", "PARTIAL", "FAILED", "EXPIRED")) {
            runCatching { cache.delete() }
            return Result.success()
        }

        readCache0737(cache)?.let { cached ->
            return submit0737(api, jobId, cached, cache)
        }

        val profileUuid = runCatching {
            UUID.fromString(ack.profileUuid.trim().lowercase()).toString()
        }.getOrNull()?.takeIf { it == ack.profileUuid.trim().lowercase() }
            ?: return reportFailure0737(api, jobId, "REMOTE_TRIP_PROFILE_UUID_INVALID", "UUID remoto inválido.")

        val tripId = normalizeBlaBlaRemoteTripId0737(ack.tripId)
            ?: return reportFailure0737(api, jobId, "REMOTE_TRIP_ID_INVALID", "tripId remoto inválido.")

        val administrativeHref = BlaBlaCollectorUrlModule.absolute(ack.administrativeHref)
        if (BlaBlaCollectorUrlModule.tripId(administrativeHref) != tripId) {
            return reportFailure0737(
                api,
                jobId,
                "REMOTE_TRIP_HREF_IDENTITY_MISMATCH",
                "URL administrativa não corresponde ao tripId.",
            )
        }

        val matchingAccounts = BlaBlaDynamicAccountRegistry(applicationContext).list().filter {
            it.profileUuid?.trim()?.equals(profileUuid, ignoreCase = true) == true
        }
        if (matchingAccounts.size != 1) {
            return reportFailure0737(
                api,
                jobId,
                "REMOTE_TRIP_ACCOUNT_IDENTITY_NOT_UNIQUE",
                "Perfil BlaBlaCar não está configurado de forma única.",
            )
        }
        val account = matchingAccounts.single()
        val target = BlaBlaTripTarget0407(
            tenantId = RotaCertaTenantRegistry(applicationContext).activeScope().tenantId,
            accountId = account.id,
            profileUuid = profileUuid,
            tripId = tripId,
            tripHref = administrativeHref,
        )

        val existing = BlaBlaCollectorStateStore(applicationContext)
            .lastResponse()
            ?.trips
            ?.singleOrNull {
                it.profile_uuid.trim().equals(profileUuid, ignoreCase = true) &&
                    it.trip_id?.trim() == tripId
            }

        val seed = existing ?: BlaBlaCollectorTrip(
            profile_uuid = profileUuid,
            profile_name = account.displayLabel,
            date = ack.dateIso.trim().takeIf { it.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$")) }
                ?: LocalDate.now().toString(),
            departure_time = ack.departureTime.trim().takeIf(String::isNotBlank),
            arrival_time = ack.arrivalTime.trim().takeIf(String::isNotBlank),
            search_from = ack.origin.trim().takeIf(String::isNotBlank),
            search_to = ack.destination.trim().takeIf(String::isNotBlank),
            actual_departure = ack.origin.trim().takeIf(String::isNotBlank),
            actual_arrival = ack.destination.trim().takeIf(String::isNotBlank),
            price = ack.price.trim().takeIf(String::isNotBlank),
            trip_href = administrativeHref,
            trip_id = tripId,
            uuid_validation = "confirmed",
        )

        val result = try {
            BlaBlaUnifiedHtmlCapture0605.captureSingleTrip0607(
                context = applicationContext,
                target = target,
                existingSource = seed,
                scopedStateIsolation0662 = true,
                persistPrivateMetadata0653 = false,
            )
        } catch (error: Throwable) {
            UnifiedDebugEventStore.record(
                "BLABLACAR_REMOTE_TRIP_QUERY_CAPTURE_FAILED_0737",
                applicationContext.packageName,
                "targetKey=${seatSyncDiagnosticKey(profileUuid + "|" + tripId)} error=${error.javaClass.simpleName.take(80)}",
            )
            return if (runAttemptCount < 3) Result.retry() else reportFailure0737(
                api,
                jobId,
                "REMOTE_TRIP_CAPTURE_FAILED",
                error.message ?: error.javaClass.simpleName,
            )
        }

        if (result.errorCode == "HTML_TARGET_SINGLE_FLIGHT_BUSY" && runAttemptCount < 3) {
            return Result.retry()
        }

        val snapshot = toBlaBlaRemoteTripSnapshot0737(
            result = result,
            expectedProfileUuid = profileUuid,
            expectedTripId = tripId,
        ) ?: return reportFailure0737(
            api,
            jobId,
            result.errorCode.ifBlank { "REMOTE_TRIP_NORMALIZATION_FAILED" },
            "A captura HTML não produziu snapshot com identidade forte.",
        )

        val status = blaBlaRemoteTripQueryResultState0737(snapshot)
        val cached = BlaBlaRemoteTripCachedResult0737(
            status = status,
            payload = snapshot,
            errorCode = if (status == "PARTIAL") {
                result.errorCode.ifBlank { "REMOTE_TRIP_OPERATIONALLY_INCOMPLETE" }
            } else {
                ""
            },
        )
        writeCache0737(cache, cached)
        return submit0737(api, jobId, cached, cache)
    }

    private suspend fun submit0737(
        api: TripRemoteApi,
        jobId: String,
        cached: BlaBlaRemoteTripCachedResult0737,
        cache: File,
    ): Result = try {
        val response = api.submitBlaBlaRemoteTripQueryResult0737(
            jobId = jobId,
            status = cached.status,
            payload = cached.payload,
            errorCode = cached.errorCode,
            errorMessage = cached.errorMessage,
        )
        check(response.accepted) { "Servidor não confirmou snapshot HTML remoto" }
        runCatching { cache.delete() }
        UnifiedDebugEventStore.record(
            "BLABLACAR_REMOTE_TRIP_QUERY_RESULT_SENT_0737",
            applicationContext.packageName,
            "jobPresent=true status=${cached.status} roster=${cached.payload?.passengerRosterComplete == true} passengers=${cached.payload?.passengerCount ?: 0}",
        )
        Result.success()
    } catch (error: Throwable) {
        UnifiedDebugEventStore.record(
            "BLABLACAR_REMOTE_TRIP_QUERY_RESULT_SEND_FAILED_0737",
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
    ): Result {
        val accepted = runCatching {
            api.submitBlaBlaRemoteTripQueryResult0737(
                jobId = jobId,
                status = "FAILED",
                payload = null,
                errorCode = errorCode.take(120),
                errorMessage = errorMessage.take(240),
            ).accepted
        }.getOrDefault(false)
        return if (accepted) Result.success() else if (runAttemptCount < 3) Result.retry() else Result.failure()
    }

    private fun cacheFile0737(jobId: String): File {
        val dir = File(applicationContext.cacheDir, BLABLACAR_REMOTE_TRIP_QUERY_CACHE_DIR_0737)
        if (!dir.exists()) dir.mkdirs()
        dir.listFiles()?.forEach { file ->
            if (System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000L) {
                runCatching { file.delete() }
            }
        }
        return File(dir, "$jobId.json")
    }

    private suspend fun readCache0737(file: File): BlaBlaRemoteTripCachedResult0737? =
        withContext(Dispatchers.IO) {
            if (!file.isFile || file.length() <= 0L || file.length() > 512 * 1024L) {
                return@withContext null
            }
            runCatching {
                json0737.decodeFromString<BlaBlaRemoteTripCachedResult0737>(
                    file.readText(Charsets.UTF_8),
                )
            }.getOrNull()
        }

    private suspend fun writeCache0737(
        file: File,
        cached: BlaBlaRemoteTripCachedResult0737,
    ) = withContext(Dispatchers.IO) {
        val bytes = json0737.encodeToString(cached).toByteArray(Charsets.UTF_8)
        require(bytes.isNotEmpty() && bytes.size <= 512 * 1024) {
            "Snapshot HTML remoto excede limite local"
        }
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }
}
