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
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val BLABLACAR_OPERATIONAL_REMOTE_EVENT_0737 = "blablacar_operational_trip_collect"
private const val OPERATIONAL_REMOTE_JOB_ID_0737 = "operational_job_id_0737"
private const val OPERATIONAL_REMOTE_PROFILE_UUID_0737 = "operational_profile_uuid_0737"
private const val OPERATIONAL_REMOTE_TRIP_ID_0737 = "operational_trip_id_0737"
private const val OPERATIONAL_REMOTE_TRIP_HREF_0737 = "operational_trip_href_0737"
private const val OPERATIONAL_REMOTE_CALLBACK_TOKEN_0737 = "operational_callback_token_0737"
private const val OPERATIONAL_REMOTE_CACHE_DIR_0737 = "blablacar-operational-remote-0737"

internal fun isBlaBlaOperationalRemoteEvent0737(event: String?): Boolean =
    event?.trim() == BLABLACAR_OPERATIONAL_REMOTE_EVENT_0737

internal fun normalizeOperationalRemoteUuid0737(raw: String?): String? {
    val value = raw?.trim()?.lowercase()?.takeIf(String::isNotBlank) ?: return null
    return runCatching { UUID.fromString(value).toString() }
        .getOrNull()
        ?.takeIf { it == value }
}

internal fun normalizeOperationalCallbackToken0737(raw: String?): String? =
    raw?.trim()
        ?.takeIf { it.length in 32..180 }
        ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) }

@Serializable
internal data class BlaBlaOperationalIsolation0737(
    val writesTimeline: Boolean = false,
    val writesAgenda: Boolean = false,
    val writesCanonicalTrips: Boolean = false,
    val writesAvailability: Boolean = false,
    val writesCapacity: Boolean = false,
    val writesTodayState: Boolean = false,
    val uploadsRawHtml: Boolean = false,
    val uploadsSessionData: Boolean = false,
)

@Serializable
internal data class BlaBlaOperationalSegment0737(
    val boarding: String,
    val dropoff: String,
    val seats: Int,
)

@Serializable
internal data class BlaBlaOperationalPayload0737(
    val schemaVersion: String = "rota-certa-blablacar-operational-v1",
    val kind: String = "BLABLACAR_OPERATIONAL_QUERY",
    val capturedAt: String = "",
    val sourceAppVersion: String = BuildConfig.VERSION_NAME,
    val sourceVersionCode: Int = BuildConfig.VERSION_CODE,
    val sourceCommitSha: String = BuildConfig.BUILD_GIT_SHA,
    val sourceBranch: String = BuildConfig.BUILD_GIT_BRANCH,
    val result: String = "PARTIAL",
    val profileUuid: String = "",
    val tripId: String = "",
    val date: String = "",
    val departureTime: String = "",
    val arrivalTime: String = "",
    val origin: String = "",
    val destination: String = "",
    val price: String = "",
    val availability: String = "",
    val bookedSeats: Int = 0,
    val publishedSeats: Int? = null,
    val passengerCount: Int = 0,
    val passengerRosterComplete: Boolean = false,
    val itineraryAuthoritative: Boolean = false,
    val itineraryStops: List<String> = emptyList(),
    val itineraryStopTimes: List<String> = emptyList(),
    val segments: List<BlaBlaOperationalSegment0737> = emptyList(),
    val identityConflict: Boolean = false,
    val coreComplete: Boolean = false,
    val errorCode: String = "",
    val isolation: BlaBlaOperationalIsolation0737 = BlaBlaOperationalIsolation0737(),
)

internal fun blaBlaOperationalCoreComplete0737(
    trip: BlaBlaCollectorTrip,
    expectedProfileUuid: String,
    expectedTripId: String,
): Boolean =
    !trip.identity_conflict &&
        trip.profile_uuid.trim().equals(expectedProfileUuid.trim(), ignoreCase = true) &&
        trip.trip_id?.trim() == expectedTripId.trim() &&
        trip.passenger_roster_complete &&
        trip.itinerary_authoritative &&
        trip.published_seats != null &&
        !trip.public_trip_href.isNullOrBlank()

internal fun buildBlaBlaOperationalSegments0737(
    trip: BlaBlaCollectorTrip,
): List<BlaBlaOperationalSegment0737> =
    trip.passengers
        .groupBy { passenger ->
            passenger.boarding.orEmpty().trim() to passenger.dropoff.orEmpty().trim()
        }
        .map { (pair, passengers) ->
            BlaBlaOperationalSegment0737(
                boarding = pair.first,
                dropoff = pair.second,
                seats = passengers.sumOf { it.seats.coerceAtLeast(1) },
            )
        }
        .sortedWith(compareBy(BlaBlaOperationalSegment0737::boarding, BlaBlaOperationalSegment0737::dropoff))

internal fun buildBlaBlaOperationalPayload0737(
    trip: BlaBlaCollectorTrip,
    expectedProfileUuid: String,
    expectedTripId: String,
    errorCode: String = "",
): BlaBlaOperationalPayload0737 {
    val coreComplete = blaBlaOperationalCoreComplete0737(
        trip = trip,
        expectedProfileUuid = expectedProfileUuid,
        expectedTripId = expectedTripId,
    )
    return BlaBlaOperationalPayload0737(
        capturedAt = Instant.now().toString(),
        result = if (coreComplete) "COMPLETE" else "PARTIAL",
        profileUuid = trip.profile_uuid.trim().lowercase(),
        tripId = trip.trip_id.orEmpty().trim(),
        date = trip.date.trim(),
        departureTime = trip.departure_time.orEmpty().trim(),
        arrivalTime = trip.arrival_time.orEmpty().trim(),
        origin = trip.actual_departure.orEmpty().ifBlank { trip.search_from.orEmpty() }.trim(),
        destination = trip.actual_arrival.orEmpty().ifBlank { trip.search_to.orEmpty() }.trim(),
        price = trip.price.orEmpty().trim(),
        availability = trip.availability.trim(),
        bookedSeats = trip.booked_seats.coerceAtLeast(0),
        publishedSeats = trip.published_seats,
        passengerCount = trip.passengers.size,
        passengerRosterComplete = trip.passenger_roster_complete,
        itineraryAuthoritative = trip.itinerary_authoritative,
        itineraryStops = trip.itinerary_stops.map(String::trim),
        itineraryStopTimes = trip.itinerary_stop_times.map(String::trim),
        segments = buildBlaBlaOperationalSegments0737(trip),
        identityConflict = trip.identity_conflict,
        coreComplete = coreComplete,
        errorCode = errorCode.trim().take(160),
    )
}

internal object BlaBlaOperationalRemoteScheduler0737 {
    fun enqueue(
        context: Context,
        rawJobId: String?,
        rawProfileUuid: String?,
        rawTripId: String?,
        rawTripHref: String?,
        rawCallbackToken: String?,
    ): Boolean {
        val jobId = normalizeOperationalRemoteUuid0737(rawJobId) ?: return false
        val profileUuid = normalizeOperationalRemoteUuid0737(rawProfileUuid) ?: return false
        val tripId = normalizeOperationalRemoteUuid0737(rawTripId) ?: return false
        val tripHref = rawTripHref?.trim()?.takeIf(String::isNotBlank) ?: return false
        val callbackToken = normalizeOperationalCallbackToken0737(rawCallbackToken) ?: return false
        if (BlaBlaCollectorUrlModule.tripId(tripHref) != tripId) return false
        val request = OneTimeWorkRequestBuilder<BlaBlaOperationalRemoteWorker0737>()
            .setInputData(
                Data.Builder()
                    .putString(OPERATIONAL_REMOTE_JOB_ID_0737, jobId)
                    .putString(OPERATIONAL_REMOTE_PROFILE_UUID_0737, profileUuid)
                    .putString(OPERATIONAL_REMOTE_TRIP_ID_0737, tripId)
                    .putString(OPERATIONAL_REMOTE_TRIP_HREF_0737, tripHref)
                    .putString(OPERATIONAL_REMOTE_CALLBACK_TOKEN_0737, callbackToken)
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
            "blablacar-operational-remote-0737:" + jobId,
            ExistingWorkPolicy.KEEP,
            request,
        )
        return true
    }
}

internal class BlaBlaOperationalRemoteWorker0737(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun doWork(): Result {
        val jobId = normalizeOperationalRemoteUuid0737(inputData.getString(OPERATIONAL_REMOTE_JOB_ID_0737))
            ?: return Result.failure()
        val profileUuid = normalizeOperationalRemoteUuid0737(inputData.getString(OPERATIONAL_REMOTE_PROFILE_UUID_0737))
            ?: return Result.failure()
        val tripId = normalizeOperationalRemoteUuid0737(inputData.getString(OPERATIONAL_REMOTE_TRIP_ID_0737))
            ?: return Result.failure()
        val tripHref = inputData.getString(OPERATIONAL_REMOTE_TRIP_HREF_0737)?.trim().orEmpty()
        val callbackToken = normalizeOperationalCallbackToken0737(
            inputData.getString(OPERATIONAL_REMOTE_CALLBACK_TOKEN_0737),
        ) ?: return Result.failure()
        if (tripHref.isBlank() || BlaBlaCollectorUrlModule.tripId(tripHref) != tripId) return Result.failure()

        val store = TripStore(applicationContext)
        val settings = withContext(Dispatchers.IO) { store.onlineSettings() }
        if (!settings.apiBaseUrl.startsWith("https://")) return Result.failure()
        val api = BlaBlaOperationalCallbackClient0737(
            apiBaseUrl = settings.apiBaseUrl,
            jobId = jobId,
            callbackToken = callbackToken,
        )
        val cache = cacheFile0737(jobId)

        val ack = runCatching { api.ack() }.getOrElse {
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
        if (ack.state.trim().uppercase() in setOf("COMPLETE", "PARTIAL", "FAILED", "EXPIRED")) {
            runCatching { cache.delete() }
            return Result.success()
        }

        readCachedPayload0737(cache)?.let { cached ->
            return submitPayload0737(api, jobId, cached, cache)
        }

        val accounts = BlaBlaDynamicAccountRegistry(applicationContext).list().filter { account ->
            account.profileUuid?.trim()?.equals(profileUuid, ignoreCase = true) == true
        }
        if (accounts.size != 1) {
            reportFailure0737(
                api = api,
                jobId = jobId,
                code = if (accounts.isEmpty()) "OPERATIONAL_PROFILE_NOT_CONFIGURED" else "OPERATIONAL_PROFILE_AMBIGUOUS",
            )
            return Result.success()
        }

        val tenantId = RotaCertaTenantRegistry(applicationContext).activeScope().tenantId
        val canonicalMatches = withContext(Dispatchers.IO) {
            store.trips().filter { trip ->
                !trip.deleted &&
                    trip.blablaProfileUuid?.trim()?.equals(profileUuid, ignoreCase = true) == true &&
                    trip.blablaTripId?.trim() == tripId
            }
        }
        if (canonicalMatches.size > 1) {
            reportFailure0737(api, jobId, "OPERATIONAL_CANONICAL_AMBIGUOUS")
            return Result.success()
        }

        val target = BlaBlaTripTarget0407(
            tenantId = tenantId,
            accountId = accounts.single().id,
            profileUuid = profileUuid,
            tripId = tripId,
            tripHref = tripHref,
        )

        return try {
            val capture = BlaBlaUnifiedHtmlCapture0605.captureSingleTrip0607(
                context = applicationContext,
                target = target,
                existingSource = canonicalMatches.singleOrNull()?.externalSnapshot,
                scopedStateIsolation0662 = true,
            )
            val trip = capture.trip
            if (trip == null) {
                reportFailure0737(
                    api = api,
                    jobId = jobId,
                    code = capture.errorCode.ifBlank { "OPERATIONAL_CAPTURE_FAILED" },
                )
                return Result.success()
            }
            if (
                !trip.profile_uuid.trim().equals(profileUuid, ignoreCase = true) ||
                trip.trip_id?.trim() != tripId
            ) {
                reportFailure0737(api, jobId, "OPERATIONAL_IDENTITY_MISMATCH")
                return Result.success()
            }
            val payload = buildBlaBlaOperationalPayload0737(
                trip = trip,
                expectedProfileUuid = profileUuid,
                expectedTripId = tripId,
                errorCode = capture.errorCode,
            )
            writeCachedPayload0737(cache, payload)
            submitPayload0737(api, jobId, payload, cache)
        } catch (error: Throwable) {
            val reported = reportFailure0737(
                api = api,
                jobId = jobId,
                code = "OPERATIONAL_DEVICE_COLLECTION_FAILED",
            )
            UnifiedDebugEventStore.record(
                "BLABLACAR_OPERATIONAL_REMOTE_FAILED_0737",
                applicationContext.packageName,
                "jobPresent=true reported=" + reported + " type=" + error.javaClass.simpleName.take(80),
            )
            if (reported) Result.success() else if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun submitPayload0737(
        api: BlaBlaOperationalCallbackClient0737,
        jobId: String,
        payload: BlaBlaOperationalPayload0737,
        cache: File,
    ): Result = runCatching {
        val response = api.submit(
            status = payload.result,
            payload = payload,
        )
        check(response.accepted)
        runCatching { cache.delete() }
        UnifiedDebugEventStore.record(
            "BLABLACAR_OPERATIONAL_REMOTE_RESULT_SENT_0737",
            applicationContext.packageName,
            "result=" + payload.result +
                " tripIdPresent=" + payload.tripId.isNotBlank() +
                " rosterComplete=" + payload.passengerRosterComplete +
                " passengerCount=" + payload.passengerCount,
        )
        Result.success()
    }.getOrElse {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }

    private suspend fun reportFailure0737(
        api: BlaBlaOperationalCallbackClient0737,
        jobId: String,
        code: String,
    ): Boolean = runCatching {
        api.submit(
            status = "FAILED",
            payload = null,
            errorCode = code,
        ).accepted
    }.getOrDefault(false)

    private fun cacheFile0737(jobId: String): File {
        val dir = File(applicationContext.cacheDir, OPERATIONAL_REMOTE_CACHE_DIR_0737)
        if (!dir.exists()) dir.mkdirs()
        dir.listFiles()?.forEach { file ->
            if (System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000L) {
                runCatching { file.delete() }
            }
        }
        return File(dir, jobId + ".json")
    }

    private suspend fun readCachedPayload0737(file: File): BlaBlaOperationalPayload0737? =
        withContext(Dispatchers.IO) {
            if (!file.isFile || file.length() <= 0L || file.length() > 384 * 1024L) return@withContext null
            runCatching { json.decodeFromString<BlaBlaOperationalPayload0737>(file.readText(Charsets.UTF_8)) }.getOrNull()
        }

    private suspend fun writeCachedPayload0737(
        file: File,
        payload: BlaBlaOperationalPayload0737,
    ) = withContext(Dispatchers.IO) {
        val raw = json.encodeToString(BlaBlaOperationalPayload0737.serializer(), payload)
        val bytes = raw.toByteArray(Charsets.UTF_8)
        require(bytes.isNotEmpty() && bytes.size <= 384 * 1024)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }
}


@Serializable
private data class BlaBlaOperationalCallbackAck0737(
    val appVersion: String = BuildConfig.VERSION_NAME,
    val sourceCommitSha: String = BuildConfig.BUILD_GIT_SHA,
)

@Serializable
private data class BlaBlaOperationalCallbackResult0737(
    val status: String,
    val payload: BlaBlaOperationalPayload0737? = null,
    val errorCode: String = "",
)

@Serializable
private data class BlaBlaOperationalCallbackResponse0737(
    val accepted: Boolean = false,
    val jobId: String = "",
    val state: String = "",
)

private class BlaBlaOperationalCallbackClient0737(
    apiBaseUrl: String,
    private val jobId: String,
    private val callbackToken: String,
) {
    private val base = apiBaseUrl.trim().trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun ack(): BlaBlaOperationalCallbackResponse0737 =
        request(
            method = "POST",
            suffix = "ack",
            body = json.encodeToString(BlaBlaOperationalCallbackAck0737()),
        )

    suspend fun submit(
        status: String,
        payload: BlaBlaOperationalPayload0737?,
        errorCode: String = "",
    ): BlaBlaOperationalCallbackResponse0737 =
        request(
            method = "PUT",
            suffix = "result",
            body = json.encodeToString(
                BlaBlaOperationalCallbackResult0737(
                    status = status.trim().uppercase(),
                    payload = payload,
                    errorCode = errorCode.trim().take(160),
                ),
            ),
        )

    private suspend fun request(
        method: String,
        suffix: String,
        body: String,
    ): BlaBlaOperationalCallbackResponse0737 = withContext(Dispatchers.IO) {
        check(base.startsWith("https://"))
        check(jobId == normalizeOperationalRemoteUuid0737(jobId))
        check(callbackToken == normalizeOperationalCallbackToken0737(callbackToken))
        val path = "/v1/public/blablacar-operational/jobs/" +
            jobId + "/" + callbackToken + "/callback/" + suffix
        val connection = URL(base + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 12_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.doOutput = true
            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }
            val statusCode = connection.responseCode
            val responseText = (if (statusCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            check(statusCode in 200..299) { "HTTP_" + statusCode }
            json.decodeFromString<BlaBlaOperationalCallbackResponse0737>(responseText)
        } finally {
            connection.disconnect()
        }
    }
}
