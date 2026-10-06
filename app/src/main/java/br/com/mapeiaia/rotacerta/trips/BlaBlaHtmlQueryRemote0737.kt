package br.com.mapeiaia.rotacerta.trips

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val BLABLACAR_HTML_QUERY_REMOTE_EVENT_0737 = "blablacar_html_query"
private const val QUERY_JOB_ID_0737 = "blablacar_html_query_job_id_0737"
private const val QUERY_MODE_0737 = "blablacar_html_query_mode_0737"
private const val QUERY_PROFILE_UUID_0737 = "blablacar_html_query_profile_uuid_0737"
private const val QUERY_TRIP_ID_0737 = "blablacar_html_query_trip_id_0737"
private const val QUERY_DATE_ISO_0737 = "blablacar_html_query_date_iso_0737"
private const val QUERY_CACHE_DIR_0737 = "blablacar-html-query-remote-0737"

internal enum class BlaBlaHtmlQueryMode0737 { TRIP, DATE, FULL }

@Serializable
internal data class BlaBlaHtmlQueryPassenger0737(
    val name: String = "",
    val seats: Int = 1,
    val boarding: String = "",
    val dropoff: String = "",
)

@Serializable
internal data class BlaBlaHtmlQueryTrip0737(
    val profileUuid: String,
    val profileName: String = "",
    val tripId: String,
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
    val operationalComplete: Boolean = false,
    val itineraryAuthoritative: Boolean = false,
    val itineraryStops: List<String> = emptyList(),
    val itineraryStopTimes: List<String> = emptyList(),
    val passengers: List<BlaBlaHtmlQueryPassenger0737> = emptyList(),
)

@Serializable
internal data class BlaBlaHtmlQueryFailure0737(
    val profileUuid: String = "",
    val tripId: String = "",
    val errorCode: String,
)

@Serializable
internal data class BlaBlaHtmlQueryIsolation0737(
    val writesAgenda: Boolean = false,
    val writesTimeline: Boolean = false,
    val writesCanonicalTrips: Boolean = false,
    val writesAvailability: Boolean = false,
    val writesCapacity: Boolean = false,
    val uploadsHtml: Boolean = false,
    val uploadsCookies: Boolean = false,
    val uploadsPhone: Boolean = false,
    val uploadsBookingHref: Boolean = false,
    val opensTripDetails: Boolean = true,
    val readsPassengers: Boolean = true,
)

@Serializable
internal data class BlaBlaHtmlQueryPayload0737(
    val schemaVersion: String = "rota-certa-blablacar-html-query-v1",
    val kind: String = "BLABLACAR_HTML_QUERY",
    val capturedAt: String,
    val sourceAppVersion: String,
    val sourceVersionCode: Int,
    val sourceCommitSha: String,
    val sourceBranch: String,
    val mode: String,
    val requestedProfileUuid: String = "",
    val requestedTripId: String = "",
    val requestedDateIso: String = "",
    val result: String,
    val indexComplete: Boolean,
    val totalTargets: Int,
    val completeTrips: Int,
    val partialTrips: Int,
    val trips: List<BlaBlaHtmlQueryTrip0737> = emptyList(),
    val failures: List<BlaBlaHtmlQueryFailure0737> = emptyList(),
    val isolation: BlaBlaHtmlQueryIsolation0737 = BlaBlaHtmlQueryIsolation0737(),
)

internal data class BlaBlaHtmlQueryRemoteAccess0737(
    val queryUrl: String = "",
    val expiresAtMillis: Long = 0L,
) {
    val configured: Boolean
        get() = queryUrl.startsWith("https://") && expiresAtMillis > System.currentTimeMillis()
}

internal class BlaBlaHtmlQueryRemoteAccessStore0737(context: Context) {
    private val app = context.applicationContext
    private val scope = RotaCertaTenantRegistry(app).activeScope()
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(): BlaBlaHtmlQueryRemoteAccess0737 = BlaBlaHtmlQueryRemoteAccess0737(
        queryUrl = prefs.getString(scope.key(KEY_QUERY_URL), "").orEmpty(),
        expiresAtMillis = prefs.getLong(scope.key(KEY_EXPIRES), 0L),
    )

    fun save(publicBaseUrl: String, response: BlaBlaHtmlQueryAccessResponse0737): BlaBlaHtmlQueryRemoteAccess0737 {
        val base = publicBaseUrl.trim().trimEnd('/')
        require(base.startsWith("https://")) { "Base pública HTTPS não configurada" }
        require(response.enabled) { "Consulta HTML remota não foi habilitada pelo servidor" }
        require(response.queryPath.startsWith("/v1/public/blablacar-query/")) {
            "Caminho privado da consulta HTML inválido"
        }
        val value = BlaBlaHtmlQueryRemoteAccess0737(
            queryUrl = base + response.queryPath,
            expiresAtMillis = response.expiresAtMillis,
        )
        prefs.edit()
            .putString(scope.key(KEY_QUERY_URL), value.queryUrl)
            .putLong(scope.key(KEY_EXPIRES), value.expiresAtMillis)
            .apply()
        return value
    }

    private companion object {
        const val PREFS = "rota_certa_blablacar_html_query_remote_0737"
        const val KEY_QUERY_URL = "query_url"
        const val KEY_EXPIRES = "expires_at"
    }
}

internal fun isBlaBlaHtmlQueryRemoteEvent0737(event: String?): Boolean =
    event?.trim() == BLABLACAR_HTML_QUERY_REMOTE_EVENT_0737

internal fun normalizeBlaBlaHtmlQueryJobId0737(raw: String?): String? {
    val value = raw?.trim()?.lowercase()?.takeIf(String::isNotBlank) ?: return null
    return runCatching { UUID.fromString(value).toString() }.getOrNull()?.takeIf { it == value }
}

internal fun normalizeBlaBlaHtmlQueryMode0737(raw: String?): BlaBlaHtmlQueryMode0737? =
    runCatching { BlaBlaHtmlQueryMode0737.valueOf(raw?.trim()?.uppercase().orEmpty()) }.getOrNull()

internal fun validateBlaBlaHtmlQueryPayload0737(payload: BlaBlaHtmlQueryPayload0737): BlaBlaHtmlQueryPayload0737 {
    require(payload.schemaVersion == "rota-certa-blablacar-html-query-v1")
    require(payload.kind == "BLABLACAR_HTML_QUERY")
    require(payload.sourceAppVersion.isNotBlank() && payload.sourceVersionCode > 0)
    require(payload.sourceCommitSha.isNotBlank())
    require(payload.mode in BlaBlaHtmlQueryMode0737.entries.map { it.name })
    require(payload.result == "COMPLETE" || payload.result == "PARTIAL")
    require(payload.totalTargets >= 0)
    require(payload.completeTrips >= 0 && payload.partialTrips >= 0)
    require(payload.completeTrips + payload.partialTrips == payload.totalTargets)
    require(payload.trips.size == payload.completeTrips)
    require(payload.failures.size == payload.partialTrips)
    if (payload.result == "COMPLETE") {
        require(payload.indexComplete)
        require(payload.partialTrips == 0)
    }
    payload.trips.forEach { trip ->
        require(BlaBlaRidesSnapshotStore0526.strongUuid(trip.profileUuid) != null)
        require(canonicalUuid0737(trip.tripId) != null)
        require(trip.passengerRosterComplete)
        require(trip.bookedSeats >= 0)
        require(trip.publishedSeats == null || trip.publishedSeats >= 0)
        require(trip.passengers.all { it.seats > 0 })
    }
    payload.failures.forEach { require(it.errorCode.isNotBlank()) }
    return payload
}

internal object BlaBlaHtmlQueryRemoteScheduler0737 {
    fun enqueue(
        context: Context,
        rawJobId: String?,
        rawMode: String?,
        rawProfileUuid: String?,
        rawTripId: String?,
        rawDateIso: String?,
    ): Boolean {
        val jobId = normalizeBlaBlaHtmlQueryJobId0737(rawJobId) ?: return false
        val mode = normalizeBlaBlaHtmlQueryMode0737(rawMode) ?: return false
        val request = OneTimeWorkRequestBuilder<BlaBlaHtmlQueryRemoteWorker0737>()
            .setInputData(
                Data.Builder()
                    .putString(QUERY_JOB_ID_0737, jobId)
                    .putString(QUERY_MODE_0737, mode.name)
                    .putString(QUERY_PROFILE_UUID_0737, rawProfileUuid.orEmpty().trim())
                    .putString(QUERY_TRIP_ID_0737, rawTripId.orEmpty().trim())
                    .putString(QUERY_DATE_ISO_0737, rawDateIso.orEmpty().trim())
                    .build(),
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "blablacar-html-query-remote-0737",
            ExistingWorkPolicy.KEEP,
            request,
        )
        return true
    }
}

internal class BlaBlaHtmlQueryRemoteWorker0737(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun doWork(): Result {
        val jobId = normalizeBlaBlaHtmlQueryJobId0737(inputData.getString(QUERY_JOB_ID_0737))
            ?: return Result.failure()
        val mode = normalizeBlaBlaHtmlQueryMode0737(inputData.getString(QUERY_MODE_0737))
            ?: return Result.failure()
        val profileUuid = inputData.getString(QUERY_PROFILE_UUID_0737).orEmpty().trim().lowercase()
        val tripId = inputData.getString(QUERY_TRIP_ID_0737).orEmpty().trim().lowercase()
        val dateIso = inputData.getString(QUERY_DATE_ISO_0737).orEmpty().trim()

        val settings = withContext(Dispatchers.IO) { TripStore(applicationContext).onlineSettings() }
        if (!settings.configured || settings.driverToken.isBlank() || settings.driverUsername.isBlank()) {
            return Result.failure()
        }
        val api = TripRemoteApi(settings)
        val cache = cacheFile0737(jobId)

        val ack = try {
            api.ackBlaBlaHtmlQueryJob0737(jobId)
        } catch (_: Throwable) {
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
        if (ack.state in setOf("COMPLETE", "PARTIAL", "FAILED", "EXPIRED")) {
            runCatching { cache.delete() }
            return Result.success()
        }

        readCache0737(cache)?.let { cached ->
            return submitPayload0737(api, jobId, cached, cache)
        }

        if (mode != BlaBlaHtmlQueryMode0737.TRIP) {
            setForeground(foregroundInfo0737("Consulta " + mode.name.lowercase() + " do BlaBlaCar"))
        }

        return try {
            val payload = when (mode) {
                BlaBlaHtmlQueryMode0737.TRIP -> queryTrip0737(profileUuid, tripId)
                BlaBlaHtmlQueryMode0737.DATE -> queryIndexed0737(mode, dateIso)
                BlaBlaHtmlQueryMode0737.FULL -> queryIndexed0737(mode, "")
            }
            validateBlaBlaHtmlQueryPayload0737(payload)
            writeCache0737(cache, payload)
            submitPayload0737(api, jobId, payload, cache)
        } catch (error: Throwable) {
            val reported = runCatching {
                api.submitBlaBlaHtmlQueryResult0737(
                    jobId = jobId,
                    status = "FAILED",
                    payload = null,
                    errorCode = "REMOTE_HTML_QUERY_FAILED",
                    errorMessage = (error.message ?: error.javaClass.simpleName).take(240),
                ).accepted
            }.getOrDefault(false)
            UnifiedDebugEventStore.record(
                "BLABLACAR_HTML_QUERY_REMOTE_FAILED_0737",
                applicationContext.packageName,
                "mode=" + mode.name + " reported=" + reported + " error=" + error.javaClass.simpleName.take(80),
            )
            if (reported) Result.success() else if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun queryTrip0737(profileUuidRaw: String, tripIdRaw: String): BlaBlaHtmlQueryPayload0737 {
        val profileUuid = BlaBlaRidesSnapshotStore0526.strongUuid(profileUuidRaw)
            ?: error("PROFILE_UUID_INVALID")
        val tripId = canonicalUuid0737(tripIdRaw) ?: error("TRIP_ID_INVALID")
        val account = BlaBlaDynamicAccountRegistry(applicationContext).list()
            .singleOrNull { it.profileUuid?.trim()?.equals(profileUuid, ignoreCase = true) == true }
            ?: error("PROFILE_ACCOUNT_NOT_UNIQUE")
        val existing = BlaBlaCollectorStateStore(applicationContext)
            .lastResponseRecoveringDynamicSessions()
            ?.trips
            ?.singleOrNull {
                it.profile_uuid.trim().equals(profileUuid, ignoreCase = true) &&
                    it.trip_id?.trim()?.equals(tripId, ignoreCase = true) == true
            }
        val href = existing?.trip_href
            ?.takeIf { BlaBlaCollectorUrlModule.tripId(it) == tripId }
            ?: (BlaBlaCollectorUrlModule.ORIGIN + "/rides/offer/" + tripId)
        val target = BlaBlaTripTarget0407(
            tenantId = RotaCertaTenantRegistry(applicationContext).activeScope().tenantId,
            accountId = account.id,
            profileUuid = profileUuid,
            tripId = tripId,
            tripHref = href,
        )
        val result = BlaBlaUnifiedHtmlCapture0605.captureSingleTrip0607(
            context = applicationContext,
            target = target,
            existingSource = existing,
            scopedStateIsolation0662 = true,
        )
        val trip = result.trip?.takeIf {
            it.profile_uuid.trim().equals(profileUuid, ignoreCase = true) &&
                it.trip_id?.trim()?.equals(tripId, ignoreCase = true) == true &&
                it.passenger_roster_complete
        }
        return if (trip != null) {
            payload0737(
                mode = BlaBlaHtmlQueryMode0737.TRIP,
                requestedProfileUuid = profileUuid,
                requestedTripId = tripId,
                indexComplete = true,
                trips = listOf(trip.toRemote0737(result.operationalComplete)),
                failures = emptyList(),
                totalTargets = 1,
            )
        } else {
            payload0737(
                mode = BlaBlaHtmlQueryMode0737.TRIP,
                requestedProfileUuid = profileUuid,
                requestedTripId = tripId,
                indexComplete = true,
                trips = emptyList(),
                failures = listOf(
                    BlaBlaHtmlQueryFailure0737(
                        profileUuid = profileUuid,
                        tripId = tripId,
                        errorCode = result.errorCode.ifBlank { "HTML_TARGET_ROSTER_INCOMPLETE" },
                    ),
                ),
                totalTargets = 1,
            )
        }
    }

    private suspend fun queryIndexed0737(mode: BlaBlaHtmlQueryMode0737, dateIso: String): BlaBlaHtmlQueryPayload0737 {
        val requestedDate = if (mode == BlaBlaHtmlQueryMode0737.DATE) {
            runCatching { LocalDate.parse(dateIso) }.getOrNull() ?: error("DATE_INVALID")
        } else {
            null
        }
        val accounts = BlaBlaDynamicAccountRegistry(applicationContext).list()
        if (accounts.isEmpty()) error("NO_BLABLACAR_PROFILES_CONFIGURED")
        val covers = BlaBlaStandaloneRideCoversExport0734.collectPayload0736(
            context = applicationContext,
            accounts = accounts,
            onProgress = { progress ->
                UnifiedDebugEventStore.record(
                    "BLABLACAR_HTML_QUERY_INDEX_PROGRESS_0737",
                    applicationContext.packageName,
                    "mode=" + mode.name + " progress=" + progress.take(160),
                )
            },
        )
        val indexComplete = covers.result == RESULT_COMPLETE_0734 &&
            covers.profiles.all { it.status == RESULT_COMPLETE_0734 && it.identityConfirmed }
        val cards = covers.profiles.flatMap { profile ->
            profile.cards
                .filter { requestedDate == null || it.dateIso == requestedDate.toString() }
                .map { card -> profile to card }
        }
        val completed = mutableListOf<BlaBlaHtmlQueryTrip0737>()
        val failures = mutableListOf<BlaBlaHtmlQueryFailure0737>()

        for ((profile, card) in cards) {
            val profileUuid = BlaBlaRidesSnapshotStore0526.strongUuid(profile.profileUuid)
            val tripId = canonicalUuid0737(card.tripId)
            val account = profileUuid?.let { uuid ->
                accounts.singleOrNull { it.profileUuid?.trim()?.equals(uuid, ignoreCase = true) == true }
            }
            if (profileUuid == null || tripId == null || account == null || !profile.identityConfirmed) {
                failures += BlaBlaHtmlQueryFailure0737(
                    profileUuid = profile.profileUuid.take(80),
                    tripId = card.tripId.take(80),
                    errorCode = "INDEX_IDENTITY_UNVERIFIED",
                )
                continue
            }
            val target = BlaBlaTripTarget0407(
                tenantId = RotaCertaTenantRegistry(applicationContext).activeScope().tenantId,
                accountId = account.id,
                profileUuid = profileUuid,
                tripId = tripId,
                tripHref = card.administrativeHref,
            )
            val existing = BlaBlaCollectorTrip(
                profile_uuid = profileUuid,
                profile_name = profile.displayName,
                date = card.dateIso,
                departure_time = card.departureTime,
                arrival_time = card.arrivalTime,
                actual_departure = card.origin,
                actual_arrival = card.destination,
                price = card.price,
                trip_href = card.administrativeHref,
                trip_id = tripId,
                uuid_validation = "confirmed",
            )
            val result = BlaBlaUnifiedHtmlCapture0605.captureSingleTrip0607(
                context = applicationContext,
                target = target,
                existingSource = existing,
                scopedStateIsolation0662 = true,
            )
            val trip = result.trip?.takeIf {
                it.profile_uuid.trim().equals(profileUuid, ignoreCase = true) &&
                    it.trip_id?.trim()?.equals(tripId, ignoreCase = true) == true &&
                    it.passenger_roster_complete
            }
            if (trip != null) {
                completed += trip.toRemote0737(result.operationalComplete)
            } else {
                failures += BlaBlaHtmlQueryFailure0737(
                    profileUuid = profileUuid,
                    tripId = tripId,
                    errorCode = result.errorCode.ifBlank { "HTML_TARGET_ROSTER_INCOMPLETE" },
                )
            }
        }

        if (!indexComplete) {
            covers.profiles
                .filter { it.status != RESULT_COMPLETE_0734 || !it.identityConfirmed }
                .forEach { profile ->
                    failures += BlaBlaHtmlQueryFailure0737(
                        profileUuid = profile.profileUuid.take(80),
                        errorCode = profile.errorCode.ifBlank { "COVER_INDEX_PARTIAL" },
                    )
                }
        }

        return payload0737(
            mode = mode,
            requestedDateIso = requestedDate?.toString().orEmpty(),
            indexComplete = indexComplete,
            trips = completed,
            failures = failures,
            totalTargets = cards.size + if (indexComplete) 0 else failures.count { it.tripId.isBlank() },
        )
    }

    private fun payload0737(
        mode: BlaBlaHtmlQueryMode0737,
        requestedProfileUuid: String = "",
        requestedTripId: String = "",
        requestedDateIso: String = "",
        indexComplete: Boolean,
        trips: List<BlaBlaHtmlQueryTrip0737>,
        failures: List<BlaBlaHtmlQueryFailure0737>,
        totalTargets: Int,
    ): BlaBlaHtmlQueryPayload0737 {
        val normalizedTargets = totalTargets.coerceAtLeast(trips.size + failures.size)
        val normalizedFailures = if (failures.size == normalizedTargets - trips.size) {
            failures
        } else {
            failures + List((normalizedTargets - trips.size - failures.size).coerceAtLeast(0)) {
                BlaBlaHtmlQueryFailure0737(errorCode = "UNKNOWN_INCOMPLETE_TARGET")
            }
        }
        val result = if (indexComplete && normalizedFailures.isEmpty() && trips.size == normalizedTargets) {
            "COMPLETE"
        } else {
            "PARTIAL"
        }
        return BlaBlaHtmlQueryPayload0737(
            capturedAt = Instant.now().toString(),
            sourceAppVersion = AppBuildInfo.versionName,
            sourceVersionCode = AppBuildInfo.versionCode,
            sourceCommitSha = AppBuildInfo.commit,
            sourceBranch = AppBuildInfo.branch,
            mode = mode.name,
            requestedProfileUuid = requestedProfileUuid,
            requestedTripId = requestedTripId,
            requestedDateIso = requestedDateIso,
            result = result,
            indexComplete = indexComplete,
            totalTargets = normalizedTargets,
            completeTrips = trips.size,
            partialTrips = normalizedFailures.size,
            trips = trips,
            failures = normalizedFailures,
        )
    }

    private fun BlaBlaCollectorTrip.toRemote0737(operationalComplete0737: Boolean): BlaBlaHtmlQueryTrip0737 =
        BlaBlaHtmlQueryTrip0737(
            profileUuid = profile_uuid.trim().lowercase(),
            profileName = profile_name.take(120),
            tripId = trip_id.orEmpty().trim().lowercase(),
            dateIso = date.take(16),
            departureTime = departure_time.orEmpty().take(8),
            arrivalTime = arrival_time.orEmpty().take(8),
            origin = (actual_departure?.takeIf(String::isNotBlank) ?: search_from.orEmpty()).take(180),
            destination = (actual_arrival?.takeIf(String::isNotBlank) ?: search_to.orEmpty()).take(180),
            price = price.orEmpty().take(80),
            availability = availability.take(80),
            bookedSeats = booked_seats.coerceAtLeast(0),
            publishedSeats = published_seats?.coerceAtLeast(0),
            passengerRosterComplete = passenger_roster_complete,
            operationalComplete = operationalComplete0737,
            itineraryAuthoritative = itinerary_authoritative,
            itineraryStops = itinerary_stops.map { it.take(180) }.take(32),
            itineraryStopTimes = itinerary_stop_times.map { it.take(8) }.take(32),
            passengers = passengers.map { passenger ->
                BlaBlaHtmlQueryPassenger0737(
                    name = passenger.name.take(120),
                    seats = passenger.seats.coerceAtLeast(1),
                    boarding = passenger.boarding.orEmpty().take(180),
                    dropoff = passenger.dropoff.orEmpty().take(180),
                )
            }.take(32),
        )

    private suspend fun submitPayload0737(
        api: TripRemoteApi,
        jobId: String,
        payload: BlaBlaHtmlQueryPayload0737,
        cache: File,
    ): Result = try {
        val response = api.submitBlaBlaHtmlQueryResult0737(jobId, payload.result, payload)
        check(response.accepted) { "Servidor não confirmou a consulta HTML" }
        runCatching { cache.delete() }
        Result.success()
    } catch (_: Throwable) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }

    private fun cacheFile0737(jobId: String): File {
        val dir = File(applicationContext.cacheDir, QUERY_CACHE_DIR_0737)
        if (!dir.exists()) dir.mkdirs()
        dir.listFiles()?.forEach { file ->
            if (System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000L) runCatching { file.delete() }
        }
        return File(dir, jobId + ".json")
    }

    private suspend fun readCache0737(file: File): BlaBlaHtmlQueryPayload0737? = withContext(Dispatchers.IO) {
        if (!file.isFile || file.length() <= 0L || file.length() > 2 * 1024 * 1024L) return@withContext null
        runCatching { json.decodeFromString<BlaBlaHtmlQueryPayload0737>(file.readText(Charsets.UTF_8)) }
            .getOrNull()
            ?.let(::validateBlaBlaHtmlQueryPayload0737)
    }

    private suspend fun writeCache0737(file: File, payload: BlaBlaHtmlQueryPayload0737) = withContext(Dispatchers.IO) {
        val bytes = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
        require(bytes.isNotEmpty() && bytes.size <= 2 * 1024 * 1024) { "HTML query payload too large" }
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    private fun foregroundInfo0737(text: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_0737,
                    "Consulta BlaBlaCar",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "Mantém consultas HTML detalhadas em execução." },
            )
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID_0737,
            Intent(applicationContext, TripsActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID_0737)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Rota Certa • Consultando BlaBlaCar")
            .setContentText(text.take(120))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(0, 0, true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID_0737, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID_0737, notification)
        }
    }

    private companion object {
        const val CHANNEL_ID_0737 = "blablacar_html_query_0737"
        const val NOTIFICATION_ID_0737 = 7371
    }
}

private fun canonicalUuid0737(raw: String?): String? {
    val value = raw?.trim()?.lowercase()?.takeIf(String::isNotBlank) ?: return null
    return runCatching { UUID.fromString(value).toString() }.getOrNull()?.takeIf { it == value }
}
