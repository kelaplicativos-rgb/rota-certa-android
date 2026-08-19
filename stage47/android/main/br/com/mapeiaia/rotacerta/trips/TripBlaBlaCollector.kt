package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BlaBlaCollectorProfileRequest(val uuid: String)

@Serializable
data class BlaBlaCollectorRouteRequest(
    val from: String,
    val to: String,
)

@Serializable
data class BlaBlaCollectorMonthRequest(
    val profiles: List<BlaBlaCollectorProfileRequest>,
    val month: String,
    val routes: List<BlaBlaCollectorRouteRequest>,
    val include_past: Boolean = false,
)

@Serializable
data class BlaBlaCollectorProfile(
    val uuid: String,
    val name: String = "",
    val profile_url: String = "",
    val title: String = "",
)

@Serializable
data class BlaBlaCollectorTrip(
    val profile_uuid: String,
    val profile_name: String = "",
    val date: String,
    val departure_time: String? = null,
    val arrival_time: String? = null,
    val search_from: String? = null,
    val search_to: String? = null,
    val actual_departure: String? = null,
    val actual_arrival: String? = null,
    val price: String? = null,
    val flags: List<String> = emptyList(),
    val availability: String = "unknown",
    val trip_href: String? = null,
    val trip_id: String? = null,
    val uuid_validation: String = "unknown",
)

@Serializable
data class BlaBlaCollectorCoverage(
    val complete_for_scope: Boolean = false,
    val global_profile_month_complete: Boolean = false,
    val reason: String = "",
    val requested_queries: Int = 0,
    val validated_queries: Int = 0,
    val failed_or_mismatched_queries: Int = 0,
    val unresolved_target_cards: Int = 0,
    val past_dates_skipped: Boolean = true,
)

@Serializable
data class BlaBlaCollectorMonthResponse(
    val schema_version: Int = 1,
    val collected_at: String? = null,
    val status: String,
    val month: String? = null,
    val strategy: String? = null,
    val profiles: List<BlaBlaCollectorProfile> = emptyList(),
    val routes: List<BlaBlaCollectorRouteRequest> = emptyList(),
    val trips: List<BlaBlaCollectorTrip> = emptyList(),
    val coverage: BlaBlaCollectorCoverage = BlaBlaCollectorCoverage(),
    val error: String? = null,
)

@Serializable
data class BlaBlaCollectorSettings(
    val baseUrl: String = "",
    val token: String = "",
) {
    val configured: Boolean
        get() = baseUrl.startsWith("https://")
}

class BlaBlaCollectorApi(private val settings: BlaBlaCollectorSettings) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun search(request: BlaBlaCollectorMonthRequest): BlaBlaCollectorMonthResponse = withContext(Dispatchers.IO) {
        check(settings.configured) { "Serviço do coletor não configurado." }
        val connection = (URL(settings.baseUrl.trimEnd('/') + "/v1/blablacar/profile-month").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 12 * 60_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (settings.token.isNotBlank()) setRequestProperty("X-Rota-Certa-Collector-Token", settings.token)
            doOutput = true
            outputStream.use { it.write(json.encodeToString(request).toByteArray(Charsets.UTF_8)) }
        }
        try {
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val parsed = runCatching { json.decodeFromString<BlaBlaCollectorMonthResponse>(text) }.getOrNull()
            if (status !in 200..299) {
                throw IllegalStateException(parsed?.error ?: parsed?.coverage?.reason?.takeIf(String::isNotBlank) ?: "Coletor respondeu HTTP $status")
            }
            parsed ?: throw IllegalStateException("Resposta inválida do coletor.")
        } finally {
            connection.disconnect()
        }
    }
}

object BlaBlaCollectorScope {
    fun fromAgenda(
        trips: List<Trip>,
        month: String,
        maxRoutes: Int = 3,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<BlaBlaCollectorRouteRequest> {
        val selected = runCatching { YearMonth.parse(month) }.getOrNull() ?: return emptyList()
        val monthTrips = trips.filter { trip ->
            runCatching { YearMonth.from(Instant.ofEpochMilli(trip.departureAtMillis).atZone(zoneId)) }.getOrNull() == selected &&
                trip.status != TripStatus.CANCELLED
        }
        val source = (monthTrips.ifEmpty { trips.filterNot { it.status == TripStatus.CANCELLED } })
            .sortedByDescending(Trip::departureAtMillis)
        val directed = LinkedHashMap<String, BlaBlaCollectorRouteRequest>()
        source.forEach { trip ->
            val stops = trip.stops.sortedBy(TripStop::order)
            if (stops.size < 2) return@forEach
            val from = stops.first().name.trim()
            val to = stops.last().name.trim()
            if (from.isBlank() || to.isBlank() || placeKey(from) == placeKey(to)) return@forEach
            directed.putIfAbsent("${placeKey(from)}|${placeKey(to)}", BlaBlaCollectorRouteRequest(from, to))
        }
        val original = directed.values.toList()
        original.forEach { route ->
            if (directed.size >= maxRoutes) return@forEach
            directed.putIfAbsent("${placeKey(route.to)}|${placeKey(route.from)}", BlaBlaCollectorRouteRequest(route.to, route.from))
        }
        return directed.values.take(maxRoutes)
    }

    private fun placeKey(value: String): String = Normalizer.normalize(value.substringBefore(',').trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
}

class BlaBlaCollectorStateStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val secret = BlaBlaCollectorSecretStore(appContext)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun settings(): BlaBlaCollectorSettings {
        val public = runCatching { json.decodeFromString<BlaBlaCollectorSettings>(prefs.getString(KEY_SETTINGS, "") ?: "") }
            .getOrNull() ?: BlaBlaCollectorSettings()
        return public.copy(token = secret.token())
    }

    fun saveSettings(settings: BlaBlaCollectorSettings) {
        secret.save(settings.token)
        prefs.edit().putString(KEY_SETTINGS, json.encodeToString(settings.copy(token = ""))).apply()
    }

    fun lastResponse(): BlaBlaCollectorMonthResponse? = runCatching {
        prefs.getString(KEY_RESPONSE, null)?.let { json.decodeFromString<BlaBlaCollectorMonthResponse>(it) }
    }.getOrNull()

    fun saveResponse(response: BlaBlaCollectorMonthResponse) {
        prefs.edit().putString(KEY_RESPONSE, json.encodeToString(response)).apply()
    }

    fun lastProfile1(): String = prefs.getString(KEY_PROFILE1, "").orEmpty()
    fun lastProfile2(): String = prefs.getString(KEY_PROFILE2, "").orEmpty()
    fun lastMonth(): String = prefs.getString(KEY_MONTH, "").orEmpty()

    fun saveQuery(profile1: String, profile2: String, month: String) {
        prefs.edit().putString(KEY_PROFILE1, profile1.trim()).putString(KEY_PROFILE2, profile2.trim()).putString(KEY_MONTH, month.trim()).apply()
    }

    companion object {
        private const val PREFS = "rota_certa_blablacar_collector_stage47"
        private const val KEY_SETTINGS = "settings"
        private const val KEY_RESPONSE = "last_response"
        private const val KEY_PROFILE1 = "profile_1"
        private const val KEY_PROFILE2 = "profile_2"
        private const val KEY_MONTH = "month"
    }
}

private class BlaBlaCollectorSecretStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(valueRaw: String) {
        val value = valueRaw.trim()
        if (value.isBlank()) {
            prefs.edit().clear().apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        prefs.edit()
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun token(): String {
        val encrypted = prefs.getString(KEY_CIPHERTEXT, null)?.let(::decode) ?: return ""
        val iv = prefs.getString(KEY_IV, null)?.let(::decode) ?: return ""
        return runCatching {
            val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            val key = store.getKey(KEY_ALIAS, null) as? SecretKey ?: return@runCatching ""
            Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                doFinal(encrypted).toString(Charsets.UTF_8)
            }
        }.getOrDefault("")
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build())
            generateKey()
        }
    }

    private fun decode(value: String): ByteArray? = runCatching { Base64.decode(value, Base64.NO_WRAP) }.getOrNull()

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "rota_certa_stage47_blablacar_collector_token"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFS = "rota_certa_blablacar_collector_secret_stage47"
        private const val KEY_CIPHERTEXT = "ciphertext"
        private const val KEY_IV = "iv"
    }
}

object BlaBlaTimelineAdapter {
    private data class PublicEntry(val entry: TripTimelineEntry, val searchFrom: String?, val searchTo: String?)

    fun merge(
        localEntries: List<TripTimelineEntry>,
        response: BlaBlaCollectorMonthResponse?,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<TripTimelineEntry> {
        if (response == null) return localEntries
        val remainingLocal = localEntries.toMutableList()
        val public = response.trips.mapNotNull { trip -> toEntry(trip, zoneId)?.let { PublicEntry(it, trip.search_from, trip.search_to) } }
            .distinctBy { item -> "${item.entry.profileId}|${item.entry.departureAtMillis}|${placeKey(item.entry.origin)}|${placeKey(item.entry.destination)}" }
        val merged = mutableListOf<TripTimelineEntry>()
        public.forEach { item ->
            val external = item.entry
            val localIndex = remainingLocal.indexOfFirst { local -> samePhysicalTrip(local, external, item.searchFrom, item.searchTo) }
            if (localIndex >= 0) {
                val local = remainingLocal.removeAt(localIndex)
                merged += external.copy(
                    tripId = local.tripId,
                    origin = local.origin,
                    destination = local.destination,
                    status = if (external.status == TripStatus.FULL) TripStatus.FULL else local.status,
                    capacity = local.capacity,
                    minimumOccupiedSeats = local.minimumOccupiedSeats,
                    maximumOccupiedSeats = local.maximumOccupiedSeats,
                    sourcePassengerSeats = local.sourcePassengerSeats,
                    issues = local.issues + external.issues,
                )
            } else {
                merged += external
            }
        }
        merged += remainingLocal
        return TripTimelineEngine.annotate(merged)
    }

    private fun toEntry(trip: BlaBlaCollectorTrip, zoneId: ZoneId): TripTimelineEntry? {
        val departure = parseDateTime(trip.date, trip.departure_time, zoneId) ?: return null
        var arrival = parseDateTime(trip.date, trip.arrival_time, zoneId)
        if (arrival != null && arrival < departure) arrival += 24L * 60L * 60L * 1000L
        val verified = trip.uuid_validation == "verified_from_trip_detail_profile_link"
        return TripTimelineEntry(
            tripId = "blablacar:${trip.profile_uuid}:${trip.trip_id ?: departure}",
            profileId = trip.profile_uuid,
            profileLabel = trip.profile_name.ifBlank { "BlaBlaCar" },
            departureAtMillis = departure,
            arrivalAtMillis = arrival,
            origin = trip.actual_departure?.takeIf(String::isNotBlank) ?: trip.search_from?.takeIf(String::isNotBlank) ?: "Origem não exposta",
            destination = trip.actual_arrival?.takeIf(String::isNotBlank) ?: trip.search_to?.takeIf(String::isNotBlank) ?: "Destino não exposto",
            status = if (trip.availability == "full" || trip.flags.any { it.equals("Cheio", true) }) TripStatus.FULL else TripStatus.PUBLISHED,
            capacity = 0,
            minimumOccupiedSeats = 0,
            maximumOccupiedSeats = 0,
            sourcePassengerSeats = emptyMap(),
            issues = if (verified) emptySet() else setOf(TripTimelineIssue.VALIDATION_PENDING),
        )
    }

    private fun parseDateTime(date: String, time: String?, zoneId: ZoneId): Long? = runCatching {
        if (time.isNullOrBlank()) return@runCatching null
        LocalDate.parse(date).atTime(LocalTime.parse(time.trim())).atZone(zoneId).toInstant().toEpochMilli()
    }.getOrNull()

    private fun samePhysicalTrip(
        local: TripTimelineEntry,
        external: TripTimelineEntry,
        searchFrom: String?,
        searchTo: String?,
    ): Boolean {
        if (kotlin.math.abs(local.departureAtMillis - external.departureAtMillis) > 10L * 60L * 1000L) return false
        val actualMatches = placeKey(local.origin) == placeKey(external.origin) && placeKey(local.destination) == placeKey(external.destination)
        val searchMatches = !searchFrom.isNullOrBlank() && !searchTo.isNullOrBlank() &&
            placeKey(local.origin) == placeKey(searchFrom) && placeKey(local.destination) == placeKey(searchTo)
        return actualMatches || searchMatches
    }

    private fun placeKey(value: String): String = Normalizer.normalize(value.substringBefore(',').trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
}
