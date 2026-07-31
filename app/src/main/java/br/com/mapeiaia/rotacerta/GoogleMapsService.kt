package br.com.mapeiaia.rotacerta

import android.content.Context
import android.os.SystemClock
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class GoogleMapsService(context: Context? = null) {
    private val json = Json { ignoreUnknownKeys = true }
    private val geocodeCache = ConcurrentHashMap<String, Coordinate>()
    private val routeCache = ConcurrentHashMap<String, Double>()
    private val addressRouteCache = ConcurrentHashMap<String, Double>()
    private val cachePrefs: SharedPreferences? = context
        ?.applicationContext
        ?.getSharedPreferences(PERSISTENT_CACHE_PREFS, Context.MODE_PRIVATE)
    private var writesSincePrune = 0

    suspend fun geocode(query: String, region: DeviceRegion, apiKey: String): Coordinate? = withContext(Dispatchers.IO) {
        if (query.isBlank() || apiKey.isBlank()) return@withContext null

        geocodeQueries(query, region).forEach { scopedQuery ->
            val cacheKey = scopedQuery.lowercase(Locale.ROOT)
            geocodeCache[cacheKey]?.let { return@withContext it }
            readPersistentCoordinate(cacheKey)?.let { coordinate ->
                geocodeCache[cacheKey] = coordinate
                return@withContext coordinate
            }

            val coordinate = requestWithRetry(GEOCODE_REQUEST_ATTEMPTS) { requestGeocode(scopedQuery, apiKey) }
            if (coordinate != null) {
                geocodeCache[cacheKey] = coordinate
                persistCoordinate(cacheKey, coordinate)
                return@withContext coordinate
            }
        }

        null
    }

    suspend fun drivingDistanceKm(origin: Coordinate, destination: Coordinate, apiKey: String): Double? =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext null
            val cacheKey = coordinateRouteKey(origin, destination)
            routeCache[cacheKey]?.let { return@withContext it }
            readPersistentDistance(PERSISTENT_COORD_ROUTE_PREFIX, cacheKey, ROUTE_CACHE_TTL_MS)?.let { distance ->
                routeCache[cacheKey] = distance
                return@withContext distance
            }

            val body = coordinateRouteBody(origin, destination)
            val distanceKm = requestWithRetry(ROUTE_REQUEST_ATTEMPTS) { requestDrivingDistance(body, apiKey) }
            if (distanceKm != null) {
                routeCache[cacheKey] = distanceKm
                persistDistance(PERSISTENT_COORD_ROUTE_PREFIX, cacheKey, distanceKm)
            }
            distanceKm
        }

    /**
     * Caminho rapido 0.1.128: o Routes API aceita o endereco legivel diretamente
     * como origem. Assim, a primeira decisao deixa de esperar uma chamada separada
     * de geocodificacao antes de iniciar a rota.
     */
    suspend fun drivingDistancesFromAddressKm(
        originAddress: String,
        destinations: List<Coordinate>,
        apiKey: String,
    ): List<Double?> = withContext(Dispatchers.IO) {
        if (originAddress.isBlank() || destinations.isEmpty() || apiKey.isBlank()) {
            return@withContext List(destinations.size) { null }
        }

        val routeStartedElapsedNanos0163 = SystemClock.elapsedRealtimeNanos()
        FarolFlightRecorder0163.record(
            stage = "MAPS_ROUTE_MATRIX_ENTER",
            packageName = null,
            details = "origin=$originAddress; destinations=${destinations.size}; apiKeyPresent=${apiKey.isNotBlank()}",
            elapsedRealtimeNanos = routeStartedElapsedNanos0163,
        )
        val normalizedOrigin = normalizeAddress(originAddress)
        val result = MutableList<Double?>(destinations.size) { null }
        val missingIndexes = mutableListOf<Int>()

        destinations.forEachIndexed { index, destination ->
            val cacheKey = addressRouteKey(normalizedOrigin, destination)
            val cached = addressRouteCache[cacheKey]
                ?: readPersistentDistance(PERSISTENT_ADDRESS_ROUTE_PREFIX, cacheKey, ROUTE_CACHE_TTL_MS)
            if (cached != null) {
                addressRouteCache[cacheKey] = cached
                result[index] = cached
            } else {
                missingIndexes += index
            }
        }

        FarolFlightRecorder0163.record(
            stage = "MAPS_ROUTE_CACHE_EVALUATED",
            packageName = null,
            details = "origin=$originAddress; hits=${destinations.size - missingIndexes.size}; misses=${missingIndexes.size}; destinations=${destinations.size}",
        )
        if (missingIndexes.isEmpty()) {
            FarolFlightRecorder0163.record(
                stage = "MAPS_ROUTE_MATRIX_COMPLETE",
                packageName = null,
                details = "path=cache_only; distances=$result; elapsed_us=${(SystemClock.elapsedRealtimeNanos() - routeStartedElapsedNanos0163).coerceAtLeast(0L) / 1_000L}",
            )
            return@withContext result
        }

        val missingDestinations = missingIndexes.map(destinations::get)
        val body = addressRouteMatrixBody(originAddress, missingDestinations)
        val fetched = requestWithRetry(ROUTE_REQUEST_ATTEMPTS) {
            requestAddressRouteMatrix(body, apiKey, missingDestinations.size)
        }

        FarolFlightRecorder0163.record(
            stage = "MAPS_ROUTE_NETWORK_RESULT",
            packageName = null,
            details = "requested=${missingDestinations.size}; returned=${fetched?.size ?: 0}; values=$fetched; elapsed_us=${(SystemClock.elapsedRealtimeNanos() - routeStartedElapsedNanos0163).coerceAtLeast(0L) / 1_000L}",
        )
        fetched?.forEachIndexed { fetchedIndex, distanceKm ->
            if (distanceKm == null) return@forEachIndexed
            val originalIndex = missingIndexes[fetchedIndex]
            val destination = destinations[originalIndex]
            val cacheKey = addressRouteKey(normalizedOrigin, destination)
            result[originalIndex] = distanceKm
            addressRouteCache[cacheKey] = distanceKm
            persistDistance(PERSISTENT_ADDRESS_ROUTE_PREFIX, cacheKey, distanceKm)
        }

        FarolFlightRecorder0163.record(
            stage = "MAPS_ROUTE_MATRIX_COMPLETE",
            packageName = null,
            details = "path=cache_and_network; distances=$result; elapsed_us=${(SystemClock.elapsedRealtimeNanos() - routeStartedElapsedNanos0163).coerceAtLeast(0L) / 1_000L}",
        )
        result
    } // direct_address_route_matrix_0_1_128

    fun cachedDrivingDistancesFromAddressKm(
        originAddress: String,
        destinations: List<Coordinate>,
    ): List<Double?>? {
        if (originAddress.isBlank() || destinations.isEmpty()) return null
        val normalizedOrigin = normalizeAddress(originAddress)
        val result = MutableList<Double?>(destinations.size) { null }
        destinations.forEachIndexed { index, destination ->
            val cacheKey = addressRouteKey(normalizedOrigin, destination)
            val cached = addressRouteCache[cacheKey]
                ?: readPersistentDistance(PERSISTENT_ADDRESS_ROUTE_PREFIX, cacheKey, ROUTE_CACHE_TTL_MS)
                ?: return null
            addressRouteCache[cacheKey] = cached
            result[index] = cached
        }
        return result
    } // simple_cached_route_peek_checklist_13

    private fun requestDrivingDistance(body: String, apiKey: String): Double? {
        val connection = (URL(ROUTES_COMPUTE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            useCaches = false
            setRequestProperty("Connection", "keep-alive")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Goog-Api-Key", apiKey.trim())
            setRequestProperty("X-Goog-FieldMask", "routes.distanceMeters")
            setRequestProperty("X-Android-Package", BuildConfig.APPLICATION_ID)
        }

        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) return null
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            parseDistanceKm(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun requestAddressRouteMatrix(body: String, apiKey: String, destinationCount: Int): List<Double?>? {
        val connection = (URL(ROUTE_MATRIX_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            useCaches = false
            setRequestProperty("Connection", "keep-alive")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Goog-Api-Key", apiKey.trim())
            setRequestProperty(
                "X-Goog-FieldMask",
                "originIndex,destinationIndex,distanceMeters,status,condition",
            )
            setRequestProperty("X-Android-Package", BuildConfig.APPLICATION_ID)
        }

        val requestStartedElapsedNanos0163 = SystemClock.elapsedRealtimeNanos()
        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val responseCode0163 = connection.responseCode
            FarolFlightRecorder0163.record(
                stage = "MAPS_HTTP_RESPONSE",
                packageName = null,
                details = "endpoint=route_matrix; code=$responseCode0163; destinations=$destinationCount; elapsed_us=${(SystemClock.elapsedRealtimeNanos() - requestStartedElapsedNanos0163).coerceAtLeast(0L) / 1_000L}",
            )
            if (responseCode0163 !in 200..299) {
                null
            } else {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val parsed0163 = parseRouteMatrixDistances(response, destinationCount)
                FarolFlightRecorder0163.record(
                    stage = "MAPS_HTTP_PARSED",
                    packageName = null,
                    details = "endpoint=route_matrix; body_len=${response.length}; distances=$parsed0163",
                )
                parsed0163
            }
        } catch (error: Throwable) {
            FarolFlightRecorder0163.record(
                stage = "MAPS_HTTP_ERROR",
                packageName = null,
                details = "endpoint=route_matrix; error=${error::class.java.simpleName}:${error.message}; elapsed_us=${(SystemClock.elapsedRealtimeNanos() - requestStartedElapsedNanos0163).coerceAtLeast(0L) / 1_000L}",
            )
            throw error
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Monta consultas sem inventar uma cidade fixa.
     *
     * Antes, qualquer endereco sem cidade era tentado primeiro em Sao Paulo, o que
     * podia gravar coordenadas erradas no cache quando o motorista estava em outro
     * municipio ou estado. Agora a cidade do aparelho/configuracao tem prioridade;
     * quando ela nao existe, a busca permanece nacional e conserva o texto original.
     */
    internal fun geocodeQueries(query: String, region: DeviceRegion): List<String> {
        val cleanQuery = query.trim().replace(Regex("""\s+"""), " ")
        if (cleanQuery.isBlank()) return emptyList()

        val country = region.country.trim().ifBlank { "Brasil" }
        val regionCity = region.city.trim().takeIf { it.isNotBlank() }
        val queryAlreadyContainsRegion = containsExplicitLocality(cleanQuery)

        return buildList {
            if (regionCity != null && !queryAlreadyContainsRegion) {
                add("$cleanQuery, $regionCity, $country")
            }
            add("$cleanQuery, $country")
            add(cleanQuery)
        }
            .map { it.trim().replace(Regex("""\s+"""), " ") }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
    }

    private fun containsExplicitLocality(query: String): Boolean {
        val normalized = query.lowercase(Locale.ROOT)
        val statePattern = Regex("""(?:^|[,\s-])(?:ac|al|ap|am|ba|ce|df|es|go|ma|mt|ms|mg|pa|pb|pr|pe|pi|rj|rn|rs|ro|rr|sc|sp|se|to)(?:$|[,\s-])""", RegexOption.IGNORE_CASE)
        return statePattern.containsMatchIn(normalized) ||
            Regex("""\b\d{5}-?\d{3}\b""").containsMatchIn(normalized) ||
            normalized.contains(" brasil")
    }

    private fun requestGeocode(scopedQuery: String, apiKey: String): Coordinate? {
        val encodedAddress = URLEncoder.encode(scopedQuery, "UTF-8")
        val encodedKey = URLEncoder.encode(apiKey.trim(), "UTF-8")
        val url = URL(
            "https://maps.googleapis.com/maps/api/geocode/json" +
                "?address=$encodedAddress" +
                "&region=br" +
                "&language=pt-BR" +
                "&key=$encodedKey",
        )

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            setRequestProperty("Connection", "keep-alive")
            setRequestProperty("X-Android-Package", BuildConfig.APPLICATION_ID)
        }

        return try {
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseCoordinate(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseCoordinate(body: String): Coordinate? {
        val root = json.parseToJsonElement(body).jsonObject
        val status = root["status"]?.jsonPrimitive?.content.orEmpty()
        if (status != "OK") return null

        val firstResult = root["results"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val location = firstResult["geometry"]?.jsonObject
            ?.get("location")?.jsonObject
            ?: return null

        val latitude = location["lat"]?.jsonPrimitive?.doubleOrNull ?: return null
        val longitude = location["lng"]?.jsonPrimitive?.doubleOrNull ?: return null
        return Coordinate(latitude, longitude)
    }

    private fun parseDistanceKm(body: String): Double? {
        val root = json.parseToJsonElement(body).jsonObject
        val distanceMeters = root["routes"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("distanceMeters")?.jsonPrimitive
            ?.intOrNull
            ?: return null
        return distanceMeters / 1000.0
    }

    private fun parseRouteMatrixDistances(body: String, destinationCount: Int): List<Double?> {
        val result = MutableList<Double?>(destinationCount.coerceAtLeast(0)) { null }
        val elements = json.parseToJsonElement(body).jsonArray
        elements.forEach { element ->
            val objectValue = element.jsonObject
            val destinationIndex = objectValue["destinationIndex"]?.jsonPrimitive?.intOrNull ?: return@forEach
            if (destinationIndex !in result.indices) return@forEach
            val distanceMeters = objectValue["distanceMeters"]?.jsonPrimitive?.intOrNull ?: return@forEach
            result[destinationIndex] = distanceMeters / 1000.0
        }
        return result
    }

    private fun coordinateRouteBody(origin: Coordinate, destination: Coordinate): String = String.format(
        Locale.US,
        """
        {
          "origin": {"location": {"latLng": {"latitude": %.7f, "longitude": %.7f}}},
          "destination": {"location": {"latLng": {"latitude": %.7f, "longitude": %.7f}}},
          "travelMode": "DRIVE",
          "routingPreference": "TRAFFIC_UNAWARE",
          "languageCode": "pt-BR",
          "units": "METRIC"
        }
        """.trimIndent(),
        origin.latitude,
        origin.longitude,
        destination.latitude,
        destination.longitude,
    )

    private fun addressRouteMatrixBody(originAddress: String, destinations: List<Coordinate>): String {
        val destinationJson = destinations.joinToString(",") { destination ->
            String.format(
                Locale.US,
                """{"waypoint":{"location":{"latLng":{"latitude":%.7f,"longitude":%.7f}}}}""",
                destination.latitude,
                destination.longitude,
            )
        }
        return """
            {
              "origins": [{"waypoint": {"address": "${jsonEscape(originAddress)}"}}],
              "destinations": [$destinationJson],
              "travelMode": "DRIVE",
              "routingPreference": "TRAFFIC_UNAWARE",
              "languageCode": "pt-BR"
            }
        """.trimIndent()
    }

    private fun normalizeAddress(value: String): String =
        value.lowercase(Locale.ROOT).replace(Regex("""\s+"""), " ").trim()

    private fun coordinateRouteKey(origin: Coordinate, destination: Coordinate): String =
        listOf(origin.latitude, origin.longitude, destination.latitude, destination.longitude).joinToString("|")

    private fun addressRouteKey(originAddress: String, destination: Coordinate): String =
        listOf(originAddress, destination.latitude, destination.longitude).joinToString("|")

    private fun jsonEscape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", " ")
        .replace("\n", " ")

    private fun readPersistentCoordinate(cacheKey: String): Coordinate? {
        val value = cachePrefs?.getString(persistentKey(PERSISTENT_GEOCODE_PREFIX, cacheKey), null) ?: return null
        val parts = value.split('|')
        if (parts.size != 3) return null
        val timestamp = parts[0].toLongOrNull() ?: return null
        if (isExpired(timestamp, GEOCODE_CACHE_TTL_MS)) return null
        val latitude = parts[1].toDoubleOrNull() ?: return null
        val longitude = parts[2].toDoubleOrNull() ?: return null
        return Coordinate(latitude, longitude)
    }

    private fun persistCoordinate(cacheKey: String, coordinate: Coordinate) {
        cachePrefs?.edit()?.putString(
            persistentKey(PERSISTENT_GEOCODE_PREFIX, cacheKey),
            "${System.currentTimeMillis()}|${coordinate.latitude}|${coordinate.longitude}",
        )?.apply()
        prunePersistentCacheEventually()
    }

    private fun readPersistentDistance(prefix: String, cacheKey: String, ttlMillis: Long): Double? {
        val key = persistentKey(prefix, cacheKey)
        val value = cachePrefs?.getString(key, null) ?: return null
        val parts = value.split('|')
        if (parts.size != 2) return null
        val timestamp = parts[0].toLongOrNull() ?: return null
        if (isExpired(timestamp, ttlMillis)) {
            cachePrefs.edit().remove(key).apply()
            return null
        }
        return parts[1].toDoubleOrNull()
    }

    private fun persistDistance(prefix: String, cacheKey: String, distanceKm: Double) {
        cachePrefs?.edit()?.putString(
            persistentKey(prefix, cacheKey),
            "${System.currentTimeMillis()}|$distanceKm",
        )?.apply()
        prunePersistentCacheEventually()
    }

    @Synchronized
    private fun prunePersistentCacheEventually() {
        writesSincePrune += 1
        if (writesSincePrune < PRUNE_EVERY_WRITES) return
        writesSincePrune = 0
        val prefs = cachePrefs ?: return
        val now = System.currentTimeMillis()
        val entries = prefs.all.mapNotNull { (key, rawValue) ->
            if (!key.startsWith(PERSISTENT_CACHE_KEY_PREFIX)) return@mapNotNull null
            val timestamp = (rawValue as? String)?.substringBefore('|')?.toLongOrNull() ?: return@mapNotNull null
            key to timestamp
        }
        val editor = prefs.edit()
        entries.filter { (_, timestamp) -> now - timestamp > MAX_CACHE_TTL_MS }
            .forEach { (key, _) -> editor.remove(key) }
        entries.sortedByDescending { it.second }
            .drop(MAX_PERSISTENT_ENTRIES)
            .forEach { (key, _) -> editor.remove(key) }
        editor.apply()
    }

    private fun persistentKey(prefix: String, rawKey: String): String =
        PERSISTENT_CACHE_KEY_PREFIX + prefix + sha256(rawKey)

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun isExpired(timestamp: Long, ttlMillis: Long): Boolean {
        val now = System.currentTimeMillis()
        return timestamp <= 0L || now < timestamp || now - timestamp > ttlMillis
    }

    private fun <T> requestWithRetry(attempts: Int, block: () -> T?): T? {
        repeat(attempts.coerceAtLeast(1)) { attempt ->
            val result = runCatching(block).getOrNull()
            if (result != null) return result
            if (attempt < attempts - 1) Thread.sleep(RETRY_DELAY_MS)
        }
        return null
    }

    private companion object {
        const val ROUTES_COMPUTE_URL = "https://routes.googleapis.com/directions/v2:computeRoutes"
        const val ROUTE_MATRIX_URL = "https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix"
        const val CONNECT_TIMEOUT_MS = 350 // subsecond_connect_budget_checklist_6
        const val READ_TIMEOUT_MS = 600 // subsecond_read_budget_checklist_6
        const val ROUTE_REQUEST_ATTEMPTS = 1 // single_route_attempt_checklist_6
        const val GEOCODE_REQUEST_ATTEMPTS = 1
        const val RETRY_DELAY_MS = 80L

        const val PERSISTENT_CACHE_PREFS = "maps_fast_cache_v128"
        const val PERSISTENT_CACHE_KEY_PREFIX = "maps128_"
        const val PERSISTENT_GEOCODE_PREFIX = "geocode_"
        const val PERSISTENT_COORD_ROUTE_PREFIX = "coord_route_"
        const val PERSISTENT_ADDRESS_ROUTE_PREFIX = "address_route_"
        const val MAX_PERSISTENT_ENTRIES = 500
        const val PRUNE_EVERY_WRITES = 20
        const val ROUTE_CACHE_TTL_MS = 30L * 24L * 60L * 60L * 1_000L
        const val GEOCODE_CACHE_TTL_MS = 90L * 24L * 60L * 60L * 1_000L
        const val MAX_CACHE_TTL_MS = GEOCODE_CACHE_TTL_MS
    }
}
