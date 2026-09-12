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
    private val platformGeocodingService0547: GeocodingService? = context
        ?.applicationContext
        ?.let(::GeocodingService)
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
        if (originAddress.isBlank() || destinations.isEmpty()) {
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

        // 0.1.546: free road-routing is the primary authority for card distance.
        // Google remains a secondary contingency only for entries the free provider
        // could not resolve. The decision engine still receives road distance in km;
        // no green/red threshold or rendering rule is changed here.
        val osmFetched = requestOpenStreetMapAddressRoutes(
            originAddress = originAddress,
            destinations = missingDestinations,
        )
        val unresolvedIndexes = missingDestinations.indices
            .filter { index -> osmFetched?.getOrNull(index) == null }
        val googleFallbackByMissingIndex = mutableMapOf<Int, Double?>()
        if (unresolvedIndexes.isNotEmpty() && apiKey.isNotBlank()) {
            val googleDestinations = unresolvedIndexes.map(missingDestinations::get)
            val body = addressRouteMatrixBody(originAddress, googleDestinations)
            val matrixFetched = requestWithRetry(ROUTE_REQUEST_ATTEMPTS) {
                requestAddressRouteMatrix(body, apiKey, googleDestinations.size)
            }
            val googleFetched = matrixFetched ?: requestAddressRouteFallback(
                originAddress = originAddress,
                destinations = googleDestinations,
                apiKey = apiKey,
            )
            unresolvedIndexes.forEachIndexed { googleIndex, originalMissingIndex ->
                googleFallbackByMissingIndex[originalMissingIndex] = googleFetched?.getOrNull(googleIndex)
            }
        }
        val fetched = missingDestinations.indices.map { index ->
            osmFetched?.getOrNull(index) ?: googleFallbackByMissingIndex[index]
        }
        FarolFlightRecorder0163.record(
            stage = "ROUTE_PROVIDER_SELECTION_0546",
            packageName = null,
            details = "osmResolved=${osmFetched?.count { it != null } ?: 0}; googleFallbackRequested=${unresolvedIndexes.size}; googleKeyPresent=${apiKey.isNotBlank()}; finalResolved=${fetched.count { it != null }}; destinations=${missingDestinations.size}",
        )

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
                val errorBody0163 = runCatching {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                }.getOrDefault("")
                FarolFlightRecorder0163.record(
                    stage = "ROUTE_MATRIX_HTTP_ERROR",
                    packageName = null,
                    details = "code=$responseCode0163; destinations=$destinationCount; body=${sanitizeMapsErrorBody(errorBody0163)}",
                )
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



    private suspend fun requestOpenStreetMapAddressRoutes(
        originAddress: String,
        destinations: List<Coordinate>,
    ): List<Double?>? {
        if (originAddress.isBlank() || destinations.isEmpty()) return null
        val origin = resolveFreePrimaryOrigin0547(originAddress, destinations)

        if (origin == null) {
            FarolFlightRecorder0163.record(
                stage = "OSM_PRIMARY_GEOCODE_FAILED_0546",
                packageName = null,
                details = "destinations=${destinations.size}; resolver=0547",
            )
            FarolFlightRecorder0163.record(
                stage = "GEOCODE_RESOLUTION_FAILED_0547",
                packageName = null,
                details = "query=${originAddress.take(160)}; destinations=${destinations.size}",
            )
            return null
        }

        val values = destinations.map { destination ->
            requestWithRetry(OSM_ROUTE_REQUEST_ATTEMPTS) {
                requestOsrmDrivingDistance(origin, destination)
            }
        }
        FarolFlightRecorder0163.record(
            stage = "OSM_PRIMARY_ROUTE_RESULT_0546",
            packageName = null,
            details = "resolved=${values.count { it != null }}; destinations=${destinations.size}; geocodeResolver=0547",
        )
        return values.takeIf { list -> list.any { it != null } }
    }

    private suspend fun resolveFreePrimaryOrigin0547(
        originAddress: String,
        destinations: List<Coordinate>,
    ): Coordinate? {
        val normalizedOrigin = normalizeAddress(originAddress)
        val originCacheKey = "osm_origin|$normalizedOrigin"
        geocodeCache[originCacheKey]?.let { coordinate ->
            FarolFlightRecorder0163.record(
                stage = "GEOCODE_CACHE_HIT_0547",
                packageName = null,
                details = "layer=memory",
            )
            return coordinate
        }
        readPersistentCoordinate(originCacheKey)?.let { coordinate ->
            geocodeCache[originCacheKey] = coordinate
            FarolFlightRecorder0163.record(
                stage = "GEOCODE_CACHE_HIT_0547",
                packageName = null,
                details = "layer=persistent",
            )
            return coordinate
        }

        val queries = geocodeQueries0547(originAddress)
        FarolFlightRecorder0163.record(
            stage = "GEOCODE_RESOLUTION_START_0547",
            packageName = null,
            details = "queries=${queries.size}; destinations=${destinations.size}; malformedParenthesis=${originAddress.count { it == '(' } != originAddress.count { it == ')' }}",
        )

        queries.forEachIndexed { index, query ->
            val candidates = requestWithRetry(OSM_GEOCODE_REQUEST_ATTEMPTS) {
                requestNominatimGeocodeCandidates0547(query)
            }.orEmpty()
            val selected = selectNearestGeocodeCandidate0547(candidates, destinations)
            FarolFlightRecorder0163.record(
                stage = "GEOCODE_QUERY_RESULT_0547",
                packageName = null,
                details = "provider=nominatim; index=$index; candidates=${candidates.size}; selected=${selected != null}; query=${query.take(160)}",
            )
            if (selected != null) {
                geocodeCache[originCacheKey] = selected
                persistCoordinate(originCacheKey, selected)
                return selected
            }
        }

        val platformGeocoder = platformGeocodingService0547
        if (platformGeocoder != null) {
            queries.forEachIndexed { index, query ->
                val selected = platformGeocoder.geocode(
                    query = query,
                    region = DeviceRegion(city = "", country = ""),
                )
                FarolFlightRecorder0163.record(
                    stage = "GEOCODE_ANDROID_FALLBACK_0547",
                    packageName = null,
                    details = "index=$index; selected=${selected != null}; query=${query.take(160)}",
                )
                if (selected != null) {
                    geocodeCache[originCacheKey] = selected
                    persistCoordinate(originCacheKey, selected)
                    return selected
                }
            }
        }

        return null
    }

    /**
     * 0.1.547: corrige texto de card antes da geocodificacao sem inventar localidade.
     * O inDrive pode entregar um parenteses de bairro aberto e sem fechamento, como
     * "Rua Flores da Primavera, 263 (Conjunto Promorar Rio Claro, São Paulo - SP".
     * A consulta preserva rua/numero/cidade/UF e oferece uma segunda forma removendo
     * apenas o bairro. Nunca faz fallback para uma rua nacional sem cidade/UF.
     */
    internal fun geocodeQueries0547(originAddress: String): List<String> {
        val compact = originAddress.trim().replace(Regex("""\s+"""), " ")
        if (compact.isBlank()) return emptyList()

        val punctuationNormalized = compact
            .replace('(', ',')
            .replace(')', ' ')
            .replace(Regex("""\s*,\s*"""), ", ")
            .replace(Regex(""",\s*,+"""), ", ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trimEnd(',')

        val simplified = Regex(
            """^(.+?,\s*\d+[A-Za-z]?)\s*,?.*?,\s*([^,]+?)\s*-\s*([A-Za-z]{2})\s*$""",
        ).matchEntire(punctuationNormalized)?.let { match ->
            val streetAndNumber = match.groupValues[1].trim().trimEnd(',')
            val city = match.groupValues[2].trim()
            val state = match.groupValues[3].uppercase(Locale.ROOT)
            "$streetAndNumber, $city - $state"
        }

        return linkedSetOf(punctuationNormalized, simplified)
            .filterNotNull()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
    }

    internal fun selectNearestGeocodeCandidate0547(
        candidates: List<Coordinate>,
        destinations: List<Coordinate>,
    ): Coordinate? {
        if (candidates.isEmpty()) return null
        if (destinations.isEmpty()) return candidates.first()
        return candidates.minByOrNull { candidate ->
            destinations.minOf { destination -> straightLineKm0547(candidate, destination) }
        }
    }

    private fun straightLineKm0547(a: Coordinate, b: Coordinate): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val deltaLat = lat2 - lat1
        val deltaLon = Math.toRadians(b.longitude - a.longitude)
        val sinLat = kotlin.math.sin(deltaLat / 2.0)
        val sinLon = kotlin.math.sin(deltaLon / 2.0)
        val haversine = sinLat * sinLat +
            kotlin.math.cos(lat1) * kotlin.math.cos(lat2) * sinLon * sinLon
        return 2.0 * 6371.0088 * kotlin.math.asin(kotlin.math.sqrt(haversine.coerceIn(0.0, 1.0)))
    }

    private fun requestNominatimGeocode(query: String): Coordinate? =
        requestNominatimGeocodeCandidates0547(query)?.firstOrNull()

    private fun requestNominatimGeocodeCandidates0547(query: String): List<Coordinate>? {
        val encodedAddress = URLEncoder.encode(query.trim(), "UTF-8")
        val url = URL(
            "$OSM_NOMINATIM_URL?format=jsonv2&limit=5&countrycodes=br&accept-language=pt-BR&q=$encodedAddress",
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = OSM_CONNECT_TIMEOUT_MS
            readTimeout = OSM_READ_TIMEOUT_MS
            useCaches = false
            setRequestProperty("Connection", "keep-alive")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "RotaCerta/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                FarolFlightRecorder0163.record(
                    stage = "OSM_PRIMARY_GEOCODE_HTTP_0546",
                    packageName = null,
                    details = "code=$code; resolver=0547",
                )
                return null
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            json.parseToJsonElement(body).jsonArray.mapNotNull { item ->
                val objectValue = item.jsonObject
                val latitude = objectValue["lat"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    ?: return@mapNotNull null
                val longitude = objectValue["lon"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    ?: return@mapNotNull null
                Coordinate(latitude, longitude)
            }
        } catch (error: Throwable) {
            FarolFlightRecorder0163.record(
                stage = "OSM_PRIMARY_GEOCODE_ERROR_0546",
                packageName = null,
                details = "error=${error::class.java.simpleName}:${error.message}; resolver=0547",
            )
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun requestOsrmDrivingDistance(origin: Coordinate, destination: Coordinate): Double? {
        val routeUrl = String.format(
            Locale.US,
            "%s/route/v1/driving/%.7f,%.7f;%.7f,%.7f?overview=false&alternatives=false&steps=false",
            OSRM_ROUTE_URL,
            origin.longitude,
            origin.latitude,
            destination.longitude,
            destination.latitude,
        )
        val connection = (URL(routeUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = OSM_CONNECT_TIMEOUT_MS
            readTimeout = OSM_READ_TIMEOUT_MS
            useCaches = false
            setRequestProperty("Connection", "keep-alive")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "RotaCerta/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                FarolFlightRecorder0163.record(
                    stage = "OSM_PRIMARY_ROUTE_HTTP_0546",
                    packageName = null,
                    details = "code=$code",
                )
                return null
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = json.parseToJsonElement(body).jsonObject
            if (root["code"]?.jsonPrimitive?.content != "Ok") return null
            val distanceMeters = root["routes"]?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("distance")?.jsonPrimitive
                ?.doubleOrNull
                ?: return null
            (distanceMeters / 1000.0).takeIf { it >= 0.0 }
        } catch (error: Throwable) {
            FarolFlightRecorder0163.record(
                stage = "OSM_PRIMARY_ROUTE_ERROR_0546",
                packageName = null,
                details = "error=${error::class.java.simpleName}:${error.message}",
            )
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun requestAddressRouteFallback(
        originAddress: String,
        destinations: List<Coordinate>,
        apiKey: String,
    ): List<Double?>? {
        if (originAddress.isBlank() || destinations.isEmpty() || apiKey.isBlank()) return null
        FarolFlightRecorder0163.record(
            stage = "ROUTE_MATRIX_FALLBACK_GEOCODE_STARTED",
            packageName = null,
            details = "destinations=${destinations.size}",
        )
        val origin = requestWithRetry(GEOCODE_REQUEST_ATTEMPTS) {
            requestGeocode(originAddress, apiKey)
        } ?: run {
            FarolFlightRecorder0163.record(
                stage = "ROUTE_MATRIX_FALLBACK_GEOCODE_FAILED",
                packageName = null,
                details = "originResolved=false; destinations=${destinations.size}",
            )
            return null
        }
        val values = destinations.map { destination ->
            requestWithRetry(ROUTE_REQUEST_ATTEMPTS) {
                requestDrivingDistance(coordinateRouteBody(origin, destination), apiKey)
            }
        }
        FarolFlightRecorder0163.record(
            stage = "ROUTE_MATRIX_FALLBACK_RESOLVED",
            packageName = null,
            details = "returned=${values.count { it != null }}; destinations=${destinations.size}",
        )
        return values
    }

    private fun sanitizeMapsErrorBody(raw: String): String = raw
        .replace(Regex("AIza[0-9A-Za-z_-]+"), "<redacted-api-key>")
        .replace(Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+"), "Bearer <redacted>")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(600)

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
        const val OSM_NOMINATIM_URL = "https://nominatim.openstreetmap.org/search"
        const val OSRM_ROUTE_URL = "https://router.project-osrm.org"
        const val OSM_CONNECT_TIMEOUT_MS = 900
        const val OSM_READ_TIMEOUT_MS = 1_200
        const val OSM_GEOCODE_REQUEST_ATTEMPTS = 1
        const val OSM_ROUTE_REQUEST_ATTEMPTS = 1
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
