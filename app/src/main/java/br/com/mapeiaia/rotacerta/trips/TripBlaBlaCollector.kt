package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.net.URI
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
data class BlaBlaCollectorPassenger(
    val name: String = "",
    val seats: Int = 1,
    val boarding: String? = null,
    val dropoff: String? = null,
    val phone: String? = null,
    val booking_href: String? = null,
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
    val passengers: List<BlaBlaCollectorPassenger> = emptyList(),
    val booked_seats: Int = 0,
    val passenger_roster_complete: Boolean = false,
    val identity_conflict: Boolean = false,
)

internal data class BlaBlaTripIdentityEvidence(
val key: String,
val identityHash: String,
val externalTripIdPresent: Boolean,
val specificHrefPresent: Boolean,
val fallbackIdentityUsed: Boolean,
val identityConflict: Boolean,
)

internal data class BlaBlaTripIdentityConflict(
val identityHash: String,
val externalTripId: String?,
val physicalCores: List<String>,
)

internal data class BlaBlaTripIdentityResolution(
val trips: List<BlaBlaCollectorTrip>,
val dedupedCount: Int,
val conflicts: List<BlaBlaTripIdentityConflict>,
)

internal object BlaBlaTripIdentity {
fun evidence(trip: BlaBlaCollectorTrip): BlaBlaTripIdentityEvidence {
val externalId = trip.trip_id?.trim()?.takeIf(String::isNotEmpty)
val specificHref = stableSpecificHref(trip.trip_href)
val base = baseKey(trip)
val key = if (trip.identity_conflict) "$base|identity-conflict|${physicalCoreKey(trip)}" else base
return BlaBlaTripIdentityEvidence(
  key = key,
  identityHash = sha256Short(key),
  externalTripIdPresent = externalId != null,
  specificHrefPresent = specificHref != null,
  fallbackIdentityUsed = externalId == null && specificHref == null,
  identityConflict = trip.identity_conflict,
)
}

fun resolveDistinct(trips: List<BlaBlaCollectorTrip>): BlaBlaTripIdentityResolution {
if (trips.isEmpty()) return BlaBlaTripIdentityResolution(emptyList(), 0, emptyList())
val grouped = trips.groupBy(::baseKey)
val conflictKeys = grouped.mapNotNull { (key, group) ->
  val strong = group.any { trip ->
      !trip.trip_id.isNullOrBlank() || stableSpecificHref(trip.trip_href) != null
  }
  key.takeIf { strong && group.map(::physicalCoreKey).distinct().size > 1 }
}.toSet()
val conflicts = conflictKeys.map { key ->
  val group = grouped.getValue(key)
  BlaBlaTripIdentityConflict(
      identityHash = sha256Short(key),
      externalTripId = group.firstNotNullOfOrNull { it.trip_id?.trim()?.takeIf(String::isNotEmpty) },
      physicalCores = group.map(::physicalCoreKey).distinct(),
  )
}
val seen = mutableSetOf<String>()
var deduped = 0
val resolved = buildList {
  trips.forEach { trip ->
      val base = baseKey(trip)
      val conflict = base in conflictKeys
      val dedupeKey = if (conflict) "$base|${physicalCoreKey(trip)}" else base
      if (!seen.add(dedupeKey)) {
          deduped++
      } else {
          add(if (conflict) trip.copy(identity_conflict = true) else trip.copy(identity_conflict = false))
      }
  }
}
return BlaBlaTripIdentityResolution(resolved, deduped, conflicts)
}

fun physicalCoreKey(trip: BlaBlaCollectorTrip): String {
val origin = trip.actual_departure?.takeIf(String::isNotBlank) ?: trip.search_from.orEmpty()
val destination = trip.actual_arrival?.takeIf(String::isNotBlank) ?: trip.search_to.orEmpty()
return listOf(
  trip.date.trim(),
  trip.departure_time.orEmpty().take(5).trim(),
  normalizeCorePlace(origin),
  normalizeCorePlace(destination),
).joinToString("|")
}

fun externalTripIdFromHref(raw: String?): String? {
val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
runCatching { URI(value) }.getOrNull()?.let { uri ->
  uri.rawQuery.orEmpty().split('&').firstOrNull { it.substringBefore('=') == "id" }
      ?.substringAfter('=', "")?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
  Regex("/rides/offer/(?!edit(?:/|$)|passenger(?:/|$))([^/?#]+)", RegexOption.IGNORE_CASE)
      .find(uri.path.orEmpty())?.groupValues?.getOrNull(1)?.takeIf(String::isNotBlank)?.let { return it }
  Regex("/trip/([^/?#]+)", RegexOption.IGNORE_CASE)
      .find(uri.path.orEmpty())?.groupValues?.getOrNull(1)?.takeIf(String::isNotBlank)?.let { return it }
}
return null
}

private fun baseKey(trip: BlaBlaCollectorTrip): String {
val externalId = trip.trip_id?.trim()?.takeIf(String::isNotEmpty)
val specificHref = stableSpecificHref(trip.trip_href)
return when {
  externalId != null -> "id|${trip.profile_uuid.trim()}|$externalId"
  specificHref != null -> "href|${trip.profile_uuid.trim()}|$specificHref"
  else -> listOf(
      "fallback",
      trip.profile_uuid.trim(),
      trip.date.trim(),
      trip.departure_time.orEmpty().trim(),
      trip.arrival_time.orEmpty().trim(),
      trip.actual_departure.orEmpty().trim(),
      trip.actual_arrival.orEmpty().trim(),
      trip.search_from.orEmpty().trim(),
      trip.search_to.orEmpty().trim(),
      trip.price.orEmpty().trim(),
  ).joinToString("|")
}
}

private fun stableSpecificHref(raw: String?): String? {
val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
val withoutQuery = value.substringBefore('?').substringBefore('#').trimEnd('/')
val path = runCatching {
  if (withoutQuery.contains("://")) URI(withoutQuery).path else withoutQuery
}.getOrNull()?.trimEnd('/')?.takeIf(String::isNotEmpty) ?: return null
val normalized = if (path.startsWith('/')) path else "/$path"
if (normalized in setOf("/rides", "/rides/offer", "/trip")) return null
return normalized.takeIf { candidate ->
  candidate.startsWith("/rides/offer/") || candidate.startsWith("/trip/")
}
}

private fun normalizeCorePlace(value: String): String = Normalizer.normalize(value.substringBefore(',').trim(), Normalizer.Form.NFD)
.replace(Regex("\\p{M}+"), "")
.lowercase()
.replace(Regex("[^a-z0-9]+"), " ")
.trim()

private fun sha256Short(value: String): String = MessageDigest.getInstance("SHA-256")
.digest(value.toByteArray(Charsets.UTF_8))
.take(16)
.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

internal object BlaBlaPassengerRosterReconciler {
    fun reconcile(previous: BlaBlaCollectorTrip?, current: BlaBlaCollectorTrip): BlaBlaCollectorTrip {
        val currentSeats = current.passengers.sumOf { it.seats.coerceAtLeast(1) }
        if (current.passenger_roster_complete || previous == null) {
            return current.copy(booked_seats = maxOf(current.booked_seats, currentSeats))
        }

        val merged = previous.passengers.toMutableList()
        current.passengers.forEach { incoming ->
            val matchIndex = merged.indexOfFirst { existing -> matches(existing, incoming) }
            if (matchIndex < 0) {
                merged += incoming
            } else {
                val existing = merged[matchIndex]
                merged[matchIndex] = existing.copy(
                    name = incoming.name.ifBlank { existing.name },
                    seats = maxOf(existing.seats, incoming.seats),
                    boarding = incoming.boarding?.takeIf(String::isNotBlank) ?: existing.boarding,
                    dropoff = incoming.dropoff?.takeIf(String::isNotBlank) ?: existing.dropoff,
                    phone = incoming.phone?.takeIf(String::isNotBlank) ?: existing.phone,
                    booking_href = incoming.booking_href?.takeIf(String::isNotBlank) ?: existing.booking_href,
                )
            }
        }
        val mergedSeats = merged.sumOf { it.seats.coerceAtLeast(1) }
        return current.copy(
            passengers = merged,
            booked_seats = maxOf(current.booked_seats, previous.booked_seats, mergedSeats),
            passenger_roster_complete = false,
        )
    }

    internal fun matches(left: BlaBlaCollectorPassenger, right: BlaBlaCollectorPassenger): Boolean {
        val leftHref = left.booking_href?.trim().orEmpty()
        val rightHref = right.booking_href?.trim().orEmpty()
        if (leftHref.isNotBlank() && rightHref.isNotBlank()) return leftHref == rightHref

        val leftPhone = left.phone?.filter(Char::isDigit).orEmpty()
        val rightPhone = right.phone?.filter(Char::isDigit).orEmpty()
        if (leftPhone.length >= 8 && rightPhone.length >= 8) return leftPhone == rightPhone

        val leftName = normalizePassengerEvidence(left.name)
        val rightName = normalizePassengerEvidence(right.name)
        if (leftName.isBlank() || rightName.isBlank()) return false
        return leftName == rightName &&
            left.seats == right.seats &&
            normalizePassengerEvidence(left.boarding.orEmpty()) == normalizePassengerEvidence(right.boarding.orEmpty()) &&
            normalizePassengerEvidence(left.dropoff.orEmpty()) == normalizePassengerEvidence(right.dropoff.orEmpty())
    }

    private fun normalizePassengerEvidence(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}

@Serializable
data class BlaBlaCollectorCoverage(
    val complete_for_scope: Boolean = false,
    val global_profile_month_complete: Boolean = false,
    val reason: String = "",
    val requested_queries: Int = 0,
    val validated_queries: Int = 0,
    val failed_or_mismatched_queries: Int = 0,
    val unresolved_target_cards: Int = 0,
    val identity_conflicts: Int = 0,
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

internal data class BlaBlaTimelineClearResult(
    val response: BlaBlaCollectorMonthResponse,
    val externalTripsRemoved: Int,
    val sessionAccountsTouched: Int,
)

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

    fun lastResponseRecoveringDynamicSessions(): BlaBlaCollectorMonthResponse? {
        val persisted = lastResponse()
        if (persisted?.status == "cleared" || persisted?.trips?.isNotEmpty() == true) return persisted
        val accounts = BlaBlaDynamicAccountRegistry(appContext).list()
        if (accounts.isEmpty()) return persisted
        val dynamic = BlaBlaDynamicSessionStore(appContext).combinedResponse(accounts)
        val recovered = BlaBlaCollectorTimelineModule.recoverStartupResponse(persisted, dynamic)
        if (recovered != null && recovered != persisted) {
            prefs.edit().putString(KEY_RESPONSE, json.encodeToString(recovered)).apply()
            UnifiedDebugEventStore.record(
                "TIMELINE_RECOVERED_FROM_SESSION_SNAPSHOTS",
                appContext.packageName,
                "persistedTrips=${persisted?.trips?.size ?: 0} sessionTrips=${dynamic.trips.size} recoveredTrips=${recovered.trips.size} accounts=${accounts.size} explicitClear=false",
            )
        }
        return recovered
    }

    internal fun clearSynchronizedTimelineData(): BlaBlaTimelineClearResult {
        val previous = lastResponse()
        val accounts = BlaBlaDynamicAccountRegistry(appContext).list()
        val sessionClear = BlaBlaDynamicSessionStore(appContext).clearTripsPreservingSessions(accounts)
        val cleared = saveResponse(
            BlaBlaCollectorMonthResponse(
                status = "cleared",
                month = previous?.month,
                strategy = previous?.strategy,
                profiles = previous?.profiles.orEmpty(),
                routes = previous?.routes.orEmpty(),
                coverage = BlaBlaCollectorCoverage(
                    complete_for_scope = true,
                    global_profile_month_complete = true,
                    reason = "cleared_by_user",
                    past_dates_skipped = previous?.coverage?.past_dates_skipped ?: true,
                ),
            ),
            preserveOnPartial = false,
        )
        val removed = maxOf(previous?.trips?.size ?: 0, sessionClear.second)
        UnifiedDebugEventStore.record(
            "TIMELINE_SYNCHRONIZED_DATA_CLEARED",
            appContext.packageName,
            "externalTripsRemoved=$removed sessionAccountsTouched=${sessionClear.first} localTripsTouched=false identityPreserved=true loginPreserved=true",
        )
        return BlaBlaTimelineClearResult(
            response = cleared,
            externalTripsRemoved = removed,
            sessionAccountsTouched = sessionClear.first,
        )
    }

    fun saveResponse(
        response: BlaBlaCollectorMonthResponse,
        preserveOnPartial: Boolean = true,
    ): BlaBlaCollectorMonthResponse {
        val effective = BlaBlaCollectorTimelineModule.mergePublishedResponse(
            previous = lastResponse(),
            incoming = response,
            preserveOnPartial = preserveOnPartial,
        )
        if (effective.trips != response.trips) {
            UnifiedDebugEventStore.record(
                "TIMELINE_PARTIAL_RESPONSE_PRESERVED",
                appContext.packageName,
                "status=${response.status} incomingTrips=${response.trips.size} publishedTrips=${effective.trips.size} incomingPassengers=${response.trips.sumOf { it.passengers.size }} publishedPassengers=${effective.trips.sumOf { it.passengers.size }}",
            )
        }
        prefs.edit().putString(KEY_RESPONSE, json.encodeToString(effective)).apply()
        return effective
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
    private data class PublicEntry(val entry: TripTimelineEntry, val searchFrom: String?, val searchTo: String?, val externalKey: String)

    fun merge(
        localEntries: List<TripTimelineEntry>,
        response: BlaBlaCollectorMonthResponse?,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<TripTimelineEntry> {
        if (response == null) return localEntries
        val remainingLocal = localEntries.toMutableList()
        val public = response.trips.mapNotNull { trip ->
            toEntry(trip, zoneId)?.let { PublicEntry(it, trip.search_from, trip.search_to, strongExternalIdentity(trip)) }
        }.distinctBy { item -> "${item.entry.profileId}|${item.externalKey}" }
        val merged = mutableListOf<TripTimelineEntry>()
        public.forEach { item ->
            val external = item.entry
            val localIndex = remainingLocal.indexOfFirst { local -> samePhysicalTrip(local, external, item.searchFrom, item.searchTo) }
            if (localIndex >= 0) {
                val local = remainingLocal.removeAt(localIndex)
                merged += external.copy(
                    tripId = local.tripId,
                    localTripId = local.localTripId ?: local.tripId,
                    origin = local.origin,
                    destination = local.destination,
                    status = if (external.status == TripStatus.FULL) TripStatus.FULL else local.status,
                    capacity = local.capacity,
                    minimumOccupiedSeats = maxOf(local.minimumOccupiedSeats, external.minimumOccupiedSeats),
                    maximumOccupiedSeats = maxOf(local.maximumOccupiedSeats, external.maximumOccupiedSeats),
                    sourcePassengerSeats = mergeSourceSeats(local.sourcePassengerSeats, external.sourcePassengerSeats),
                    issues = local.issues + external.issues,
                )
            } else {
                merged += external
            }
        }
        merged += remainingLocal
        val annotated = TripTimelineEngine.annotate(merged)
        UnifiedDebugEventStore.record(
            "TIMELINE_MERGE",
            "br.com.mapeiaia.rotacerta",
            "local=${localEntries.size} public=${public.size} merged=${annotated.size} dedup=${(localEntries.size + public.size - annotated.size).coerceAtLeast(0)}",
        )
        return annotated
    }

    private fun toEntry(trip: BlaBlaCollectorTrip, zoneId: ZoneId): TripTimelineEntry? {
        val departure = parseDateTime(trip.date, trip.departure_time, zoneId) ?: return null
        var arrival = parseDateTime(trip.date, trip.arrival_time, zoneId)
        if (arrival != null && arrival < departure) arrival += 24L * 60L * 60L * 1000L
        val verified = trip.uuid_validation in setOf(
            "verified_from_trip_detail_profile_link",
            "verified_from_authenticated_profile_session",
        )
        val entry = TripTimelineEntry(
            tripId = "blablacar:${BlaBlaTripIdentity.evidence(trip).identityHash}",
            profileId = trip.profile_uuid,
            profileLabel = trip.profile_name.ifBlank { "BlaBlaCar" },
            departureAtMillis = departure,
            arrivalAtMillis = arrival,
            origin = trip.actual_departure?.takeIf(String::isNotBlank) ?: trip.search_from?.takeIf(String::isNotBlank) ?: "Origem não exposta",
            destination = trip.actual_arrival?.takeIf(String::isNotBlank) ?: trip.search_to?.takeIf(String::isNotBlank) ?: "Destino não exposto",
            status = if (trip.availability == "full" || trip.flags.any { it.equals("Cheio", true) }) TripStatus.FULL else TripStatus.PUBLISHED,
            capacity = 0,
            minimumOccupiedSeats = trip.booked_seats,
            maximumOccupiedSeats = trip.booked_seats,
            sourcePassengerSeats = if (trip.booked_seats > 0) mapOf(BookingSource.BLABLACAR to trip.booked_seats) else emptyMap(),
            blablaTripId = trip.trip_id?.trim()?.takeIf(String::isNotEmpty),
            blablaTripHref = canonicalManageHref(trip.trip_href),
            blablaProfileUuid = trip.profile_uuid.trim().takeIf(String::isNotEmpty),
            blablaPrice = trip.price?.trim()?.takeIf(String::isNotEmpty),
            blablaAvailability = trip.availability.trim().takeIf(String::isNotEmpty),
            blablaPassengers = trip.passengers,
            blablaPassengerRosterComplete = trip.passenger_roster_complete,
            issues = buildSet {
                if (!verified) add(TripTimelineIssue.VALIDATION_PENDING)
                if (trip.identity_conflict) add(TripTimelineIssue.EXTERNAL_IDENTITY_CONFLICT)
            },
        )
        val identity = BlaBlaTripIdentity.evidence(trip)
        UnifiedDebugEventStore.record(
            "TIMELINE_EXTERNAL_ENTRY",
            "br.com.mapeiaia.rotacerta",
            "identityHash=${identity.identityHash} bookedSeats=${trip.booked_seats} passengerCount=${trip.passengers.size} rosterComplete=${trip.passenger_roster_complete} sourceBlaBlaSeats=${entry.sourcePassengerSeats[BookingSource.BLABLACAR] ?: 0} phonesPresent=${trip.passengers.count { !it.phone.isNullOrBlank() }}",
        )
        return entry
    }

    private fun strongExternalIdentity(trip: BlaBlaCollectorTrip): String = BlaBlaTripIdentity.evidence(trip).key

    private fun mergeSourceSeats(
        local: Map<BookingSource, Int>,
        external: Map<BookingSource, Int>,
    ): Map<BookingSource, Int> = (local.keys + external.keys).associateWith { source ->
        maxOf(local[source] ?: 0, external[source] ?: 0)
    }.filterValues { it > 0 }

    private fun canonicalManageHref(raw: String?): String? {
        val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (value.startsWith('/')) return null
        val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return null
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        val path = uri.path.orEmpty()
        return value.takeIf { path.contains("/trip") || path.contains("/rides/offer") }
    }

    private fun samePlace(left: String, right: String): Boolean {
        val a = normalizeWholePlace(left)
        val b = normalizeWholePlace(right)
        if (a.isBlank() || b.isBlank()) return false
        if (a == b) return true
        val shorter = if (a.length <= b.length) a else b
        val longer = if (a.length <= b.length) b else a
        return shorter.length >= 5 && longer.contains(shorter)
    }

    private fun normalizeWholePlace(value: String): String = java.text.Normalizer.normalize(value.trim(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

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
        if (kotlin.math.abs(local.departureAtMillis - external.departureAtMillis) > 45L * 60L * 1000L) return false
        val actualMatches = samePlace(local.origin, external.origin) && samePlace(local.destination, external.destination)
        val searchMatches = !searchFrom.isNullOrBlank() && !searchTo.isNullOrBlank() &&
            samePlace(local.origin, searchFrom) && samePlace(local.destination, searchTo)
        return actualMatches || searchMatches
    }

    private fun placeKey(value: String): String = Normalizer.normalize(value.substringBefore(',').trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
}
