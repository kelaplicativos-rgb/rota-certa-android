package br.com.mapeiaia.rotacerta

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import br.com.mapeiaia.rotacerta.trips.TripOnlineSettings
import br.com.mapeiaia.rotacerta.trips.TripStore
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal enum class TrackingShareScope0668 { FAMILY, PASSENGER }

@Serializable
internal data class TrackingShareLocal0668(
    val token: String,
    val scope: TrackingShareScope0668,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val passengerKey: String = "",
    val passengerName: String = "",
    val tripId: String = "",
    val destinationLatitude: Double? = null,
    val destinationLongitude: Double? = null,
    val destinationLabel: String = "",
    val active: Boolean = true,
)

@Serializable
internal data class TrackingSessionLocal0668(
    val sessionId: String,
    val startedAtMillis: Long,
    val shares: List<TrackingShareLocal0668> = emptyList(),
    val lastUploadedAtMillis: Long = 0L,
    val lastHeartbeatAckAtMillis: Long = 0L,
    val serverRegistered: Boolean = false,
    val active: Boolean = true,
)

internal data class PassengerTrackingLinkRequest0668(
    val tripId: String,
    val passengerKey: String,
    val passengerName: String,
    val destinationLatitude: Double,
    val destinationLongitude: Double,
    val destinationLabel: String,
    val expiresAtMillis: Long,
)

internal data class TrackingLinkOutcome0668(
    val url: String,
    val reused: Boolean,
)

@Serializable
private data class TrackingSessionRequest0668(
    val sessionId: String,
    val startedAtMillis: Long,
)

@Serializable
private data class TrackingShareRequest0668(
    val sessionId: String,
    val token: String,
    val scope: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val passengerKey: String = "",
    val passengerName: String = "",
    val tripId: String = "",
    val destinationLatitude: Double? = null,
    val destinationLongitude: Double? = null,
    val destinationLabel: String = "",
)

@Serializable
private data class TrackingPointPayload0668(
    val latitude: Double,
    val longitude: Double,
    val recordedAtMillis: Long,
    val accuracyMeters: Float? = null,
    val speedMetersPerSecond: Float? = null,
)

@Serializable
private data class TrackingPointsRequest0668(
    val sessionId: String,
    val batteryPercent: Int? = null,
    val points: List<TrackingPointPayload0668>,
)

@Serializable
private data class TrackingHeartbeatRequest0670(
    val sessionId: String,
    val deviceHeartbeatAtMillis: Long,
    val lastGpsAtMillis: Long = 0L,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val speedMetersPerSecond: Float? = null,
    val batteryPercent: Int? = null,
    val trackingActive: Boolean = true,
)

@Serializable
private data class TrackingCloseRequest0668(
    val sessionId: String,
    val token: String = "",
)

@Serializable
private data class TrackingAck0668(
    val ok: Boolean = false,
    val acceptedThroughMillis: Long = 0L,
)

internal class LiveTrackingShareRepository0668(context: Context) {
    private val appContext = context.applicationContext
    private val tenantScope = RotaCertaTenantRegistry(appContext).activeScope()
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val key = tenantScope.key(KEY_SESSION)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Synchronized
    fun session(): TrackingSessionLocal0668? =
        prefs.getString(key, null)?.let { raw -> runCatching { json.decodeFromString<TrackingSessionLocal0668>(raw) }.getOrNull() }

    @Synchronized
    fun save(session: TrackingSessionLocal0668?) {
        val editor = prefs.edit()
        if (session == null) editor.remove(key) else editor.putString(key, json.encodeToString(session))
        check(editor.commit()) { "Falha ao persistir sessão de rastreamento." }
    }

    fun hasActiveShares(nowMillis: Long = System.currentTimeMillis()): Boolean =
        session()?.takeIf { it.active }?.shares.orEmpty().any { it.active && it.expiresAtMillis > nowMillis }

    fun familyShare(nowMillis: Long = System.currentTimeMillis()): TrackingShareLocal0668? =
        session()?.shares.orEmpty().firstOrNull {
            it.scope == TrackingShareScope0668.FAMILY && it.active && it.expiresAtMillis > nowMillis
        }

    fun passengerShare(passengerKey: String, nowMillis: Long = System.currentTimeMillis()): TrackingShareLocal0668? =
        session()?.shares.orEmpty().firstOrNull {
            it.scope == TrackingShareScope0668.PASSENGER &&
                it.passengerKey == passengerKey &&
                it.active &&
                it.expiresAtMillis > nowMillis
        }

    companion object {
        private const val PREFS = "rota_certa_live_tracking_0668"
        private const val KEY_SESSION = "session"
    }
}

internal class LiveTrackingShareManager0668(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val repository = LiveTrackingShareRepository0668(appContext)
    private val workRepository = WorkTrackingRepository(appContext)

    fun hasActiveShares(): Boolean = repository.hasActiveShares()

    fun hasPendingPoints(): Boolean {
        val session = repository.session() ?: return false
        if (!session.active || !repository.hasActiveShares()) return false
        return workRepository.readAllPoints().any {
            it.recordedAtMillis >= session.startedAtMillis && it.recordedAtMillis > session.lastUploadedAtMillis
        }
    }

    fun familyShareUrl(): String? {
        val settings = TripStore(appContext).onlineSettings()
        val share = repository.familyShare() ?: return null
        return trackingSharePublicUrl0668(settings.publicBaseUrl, share.token)
    }

    suspend fun createFamilyLink(): TrackingLinkOutcome0668 = withContext(Dispatchers.IO) {
        val settings = validatedSettings()
        val existing = repository.familyShare()
        if (existing != null) {
            ensureSessionAndShareRemote(settings, existing)
            return@withContext TrackingLinkOutcome0668(
                url = trackingSharePublicUrl0668(settings.publicBaseUrl, existing.token),
                reused = true,
            )
        }

        val now = System.currentTimeMillis()
        val session = ensureLocalSession(now)
        val share = TrackingShareLocal0668(
            token = secureTrackingToken0668(),
            scope = TrackingShareScope0668.FAMILY,
            createdAtMillis = now,
            expiresAtMillis = now + FAMILY_SHARE_DURATION_MILLIS,
        )
        repository.save(session.copy(shares = session.shares + share))
        ensureSessionAndShareRemote(settings, share)
        TrackingLinkOutcome0668(
            url = trackingSharePublicUrl0668(settings.publicBaseUrl, share.token),
            reused = false,
        )
    }

    suspend fun createPassengerLink(request: PassengerTrackingLinkRequest0668): TrackingLinkOutcome0668 =
        withContext(Dispatchers.IO) {
            require(request.passengerKey.isNotBlank()) { "Passageiro sem identidade canônica." }
            require(request.destinationLatitude in -90.0..90.0 && request.destinationLongitude in -180.0..180.0) {
                "Destino exato do passageiro indisponível."
            }
            val settings = validatedSettings()
            val existing = repository.passengerShare(request.passengerKey)
            if (existing != null) {
                ensureSessionAndShareRemote(settings, existing)
                return@withContext TrackingLinkOutcome0668(
                    url = trackingSharePublicUrl0668(settings.publicBaseUrl, existing.token),
                    reused = true,
                )
            }

            val now = System.currentTimeMillis()
            val session = ensureLocalSession(now)
            val expires = request.expiresAtMillis
                .coerceAtLeast(now + MIN_PASSENGER_SHARE_DURATION_MILLIS)
                .coerceAtMost(now + MAX_PASSENGER_SHARE_DURATION_MILLIS)
            val share = TrackingShareLocal0668(
                token = secureTrackingToken0668(),
                scope = TrackingShareScope0668.PASSENGER,
                createdAtMillis = now,
                expiresAtMillis = expires,
                passengerKey = request.passengerKey,
                passengerName = request.passengerName.take(120),
                tripId = request.tripId.take(160),
                destinationLatitude = request.destinationLatitude,
                destinationLongitude = request.destinationLongitude,
                destinationLabel = request.destinationLabel.take(220),
            )
            repository.save(session.copy(shares = session.shares + share))
            ensureSessionAndShareRemote(settings, share)
            TrackingLinkOutcome0668(
                url = trackingSharePublicUrl0668(settings.publicBaseUrl, share.token),
                reused = false,
            )
        }

    suspend fun syncPendingPoints(): Boolean = withContext(Dispatchers.IO) {
        val session = repository.session() ?: return@withContext false
        if (!session.active || !repository.hasActiveShares()) return@withContext false
        val settings = runCatching { validatedSettings() }.getOrNull() ?: return@withContext false
        val pending = workRepository.readAllPoints()
            .asSequence()
            .filter { it.recordedAtMillis >= session.startedAtMillis && it.recordedAtMillis > session.lastUploadedAtMillis }
            .sortedBy { it.recordedAtMillis }
            .take(MAX_UPLOAD_BATCH)
            .toList()
        if (pending.isEmpty()) return@withContext false

        ensureSessionRemote(settings, session)
        val response = TrackingRemoteClient0668(settings).postPoints(
            TrackingPointsRequest0668(
                sessionId = session.sessionId,
                batteryPercent = batteryPercent(),
                points = pending.map {
                    TrackingPointPayload0668(
                        latitude = it.coordinate.latitude,
                        longitude = it.coordinate.longitude,
                        recordedAtMillis = it.recordedAtMillis,
                        accuracyMeters = it.accuracyMeters,
                        speedMetersPerSecond = it.speedMetersPerSecond,
                    )
                },
            ),
        )
        if (!response.ok) return@withContext false
        val acceptedThrough = max(response.acceptedThroughMillis, pending.last().recordedAtMillis)
        repository.session()?.let { current ->
            if (current.sessionId == session.sessionId) {
                repository.save(current.copy(lastUploadedAtMillis = max(current.lastUploadedAtMillis, acceptedThrough), serverRegistered = true))
            }
        }
        UnifiedDebugEventStore.recordAlways(
            "LIVE_TRACKING_POINTS_SYNCED_0668",
            appContext.packageName,
            "count=${pending.size} through=$acceptedThrough",
        )
        true
    }

    suspend fun sendHeartbeat0670(
        latestPoint: WorkTrackPoint?,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean = withContext(Dispatchers.IO) {
        val session = repository.session() ?: return@withContext false
        if (!session.active || !repository.hasActiveShares(nowMillis)) return@withContext false
        val settings = runCatching { validatedSettings() }.getOrNull() ?: return@withContext false
        ensureSessionRemote(settings, session)

        val point = latestPoint?.takeIf {
            it.recordedAtMillis >= session.startedAtMillis - 60_000L &&
                it.coordinate.latitude in -90.0..90.0 &&
                it.coordinate.longitude in -180.0..180.0
        }
        val response = TrackingRemoteClient0668(settings).postHeartbeat0670(
            TrackingHeartbeatRequest0670(
                sessionId = session.sessionId,
                deviceHeartbeatAtMillis = nowMillis,
                lastGpsAtMillis = point?.recordedAtMillis ?: 0L,
                latitude = point?.coordinate?.latitude,
                longitude = point?.coordinate?.longitude,
                accuracyMeters = point?.accuracyMeters,
                speedMetersPerSecond = point?.speedMetersPerSecond,
                batteryPercent = batteryPercent(),
                trackingActive = true,
            ),
        )
        if (!response.ok) return@withContext false
        repository.session()?.let { current ->
            if (current.sessionId == session.sessionId) {
                repository.save(
                    current.copy(
                        lastHeartbeatAckAtMillis = max(current.lastHeartbeatAckAtMillis, nowMillis),
                        serverRegistered = true,
                    ),
                )
            }
        }
        true
    }

    suspend fun closeFamilyShare(): Boolean = withContext(Dispatchers.IO) {
        val session = repository.session() ?: return@withContext false
        val share = repository.familyShare() ?: return@withContext false
        val settings = validatedSettings()
        TrackingRemoteClient0668(settings).closeShare(TrackingCloseRequest0668(session.sessionId, share.token))
        repository.save(
            session.copy(
                shares = session.shares.map { if (it.token == share.token) it.copy(active = false) else it },
            ),
        )
        true
    }

    suspend fun closePassengerShare(passengerKey: String): Boolean = withContext(Dispatchers.IO) {
        val session = repository.session() ?: return@withContext false
        val share = repository.passengerShare(passengerKey) ?: return@withContext false
        val settings = validatedSettings()
        TrackingRemoteClient0668(settings).closeShare(TrackingCloseRequest0668(session.sessionId, share.token))
        repository.save(
            session.copy(
                shares = session.shares.map { if (it.token == share.token) it.copy(active = false) else it },
            ),
        )
        true
    }

    suspend fun closeActiveSession(): Boolean = withContext(Dispatchers.IO) {
        val session = repository.session() ?: return@withContext false
        runCatching { syncPendingPoints() }
        val settings = runCatching { validatedSettings() }.getOrNull()
        if (session.serverRegistered) {
            check(settings != null) { "Servidor indisponível para encerrar os links ativos." }
            TrackingRemoteClient0668(settings).closeSession(TrackingCloseRequest0668(session.sessionId))
        }
        val current = repository.session() ?: session
        repository.save(
            current.copy(
                active = false,
                shares = current.shares.map { it.copy(active = false) },
            ),
        )
        true
    }

    private fun ensureLocalSession(now: Long): TrackingSessionLocal0668 {
        val current = repository.session()
        if (current != null && current.active) return current
        return TrackingSessionLocal0668(
            sessionId = secureTrackingToken0668(24),
            startedAtMillis = workRepository.sessionStartedAtMillis() ?: now,
        ).also(repository::save)
    }

    private suspend fun ensureSessionAndShareRemote(settings: TripOnlineSettings, share: TrackingShareLocal0668) {
        val session = repository.session() ?: error("Sessão de rastreamento não disponível.")
        ensureSessionRemote(settings, session)
        TrackingRemoteClient0668(settings).createShare(
            TrackingShareRequest0668(
                sessionId = session.sessionId,
                token = share.token,
                scope = share.scope.name,
                createdAtMillis = share.createdAtMillis,
                expiresAtMillis = share.expiresAtMillis,
                passengerKey = share.passengerKey,
                passengerName = share.passengerName,
                tripId = share.tripId,
                destinationLatitude = share.destinationLatitude,
                destinationLongitude = share.destinationLongitude,
                destinationLabel = share.destinationLabel,
            ),
        )
    }

    private suspend fun ensureSessionRemote(settings: TripOnlineSettings, session: TrackingSessionLocal0668) {
        if (session.serverRegistered) return
        val response = TrackingRemoteClient0668(settings).createSession(
            TrackingSessionRequest0668(session.sessionId, session.startedAtMillis),
        )
        check(response.ok) { "Servidor não confirmou a sessão de rastreamento." }
        repository.session()?.let { current ->
            if (current.sessionId == session.sessionId) repository.save(current.copy(serverRegistered = true))
        }
    }

    private fun validatedSettings(): TripOnlineSettings {
        val settings = TripStore(appContext).onlineSettings()
        check(settings.apiBaseUrl.startsWith("https://")) { "Servidor do Viagem Certa não está configurado." }
        check(settings.publicBaseUrl.startsWith("https://")) { "Endereço público do Viagem Certa não está configurado." }
        check(settings.driverToken.isNotBlank()) { "Chave do motorista não está configurada." }
        return settings
    }

    private fun batteryPercent(): Int? {
        val manager = appContext.getSystemService(BatteryManager::class.java) ?: return null
        return manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
    }

    companion object {
        private const val FAMILY_SHARE_DURATION_MILLIS = 36L * 60L * 60L * 1000L
        private const val MIN_PASSENGER_SHARE_DURATION_MILLIS = 30L * 60L * 1000L
        private const val MAX_PASSENGER_SHARE_DURATION_MILLIS = 24L * 60L * 60L * 1000L
        private const val MAX_UPLOAD_BATCH = 100
    }
}

private class TrackingRemoteClient0668(
    private val settings: TripOnlineSettings,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun createSession(body: TrackingSessionRequest0668): TrackingAck0668 =
        post("/v1/driver/tracking/sessions", json.encodeToString(body))

    suspend fun createShare(body: TrackingShareRequest0668): TrackingAck0668 =
        post("/v1/driver/tracking/shares", json.encodeToString(body))

    suspend fun postPoints(body: TrackingPointsRequest0668): TrackingAck0668 =
        post("/v1/driver/tracking/points", json.encodeToString(body))

    suspend fun postHeartbeat0670(body: TrackingHeartbeatRequest0670): TrackingAck0668 =
        post("/v1/driver/tracking/heartbeat", json.encodeToString(body))

    suspend fun closeShare(body: TrackingCloseRequest0668): TrackingAck0668 =
        post("/v1/driver/tracking/shares/close", json.encodeToString(body))

    suspend fun closeSession(body: TrackingCloseRequest0668): TrackingAck0668 =
        post("/v1/driver/tracking/sessions/close", json.encodeToString(body))

    private suspend inline fun <reified T> post(path: String, body: String): T = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val opened = URL(settings.apiBaseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
            connection = opened
            opened.requestMethod = "POST"
            opened.connectTimeout = 12_000
            opened.readTimeout = 12_000
            opened.doOutput = true
            opened.setRequestProperty("Accept", "application/json")
            opened.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            opened.setRequestProperty("X-Rota-Certa-Driver-Token", settings.driverToken)
            if (settings.driverUsername.isNotBlank()) {
                opened.setRequestProperty("X-Rota-Certa-Driver-Username", settings.driverUsername)
            }
            opened.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = opened.responseCode
            val response = (if (status in 200..299) opened.inputStream else opened.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            check(status in 200..299) {
                "Servidor de rastreamento respondeu HTTP $status."
            }
            json.decodeFromString<T>(response)
        } finally {
            connection?.disconnect()
        }
    }
}

internal fun passengerTrackingExpiry0668(arrivalAtMillis: Long?, nowMillis: Long = System.currentTimeMillis()): Long {
    val desired = (arrivalAtMillis ?: (nowMillis + 6L * 60L * 60L * 1000L)) + 2L * 60L * 60L * 1000L
    return desired.coerceAtLeast(nowMillis + 30L * 60L * 1000L).coerceAtMost(nowMillis + 24L * 60L * 60L * 1000L)
}

internal fun secureTrackingToken0668(bytes: Int = 32): String {
    val raw = ByteArray(bytes.coerceIn(16, 64))
    SecureRandom().nextBytes(raw)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
}

internal fun trackingSharePublicUrl0668(publicBaseUrl: String, token: String): String {
    require(publicBaseUrl.startsWith("https://")) { "Endereço público inválido." }
    require(token.length >= 22) { "Token de rastreamento inválido." }
    return publicBaseUrl.trimEnd('/') + "/tracking.html#" + token
}

internal fun shareTrackingLink0668(
    context: Context,
    title: String,
    message: String,
    url: String,
) {
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message.trim() + "\n" + url)
    }
    context.startActivity(Intent.createChooser(share, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

internal fun trackingTokenDigest0668(token: String): String =
    MessageDigest.getInstance("SHA-256").digest(("tracking:" + token).toByteArray())
        .joinToString("") { "%02x".format(it) }