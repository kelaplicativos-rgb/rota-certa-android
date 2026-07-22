package br.com.mapeiaia.rotacerta

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
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Resultado de uma unica chamada do Routes API usando o endereco do card. */
data class AddressRouteResult(
    val distanceKm: Double,
    val originCoordinate: Coordinate? = null,
)

class GoogleMapsService {
    private val json = Json { ignoreUnknownKeys = true }
    private val geocodeCache = ConcurrentHashMap<String, Coordinate>()
    private val routeCache = ConcurrentHashMap<String, Double>()
    private val addressRouteCache = ConcurrentHashMap<String, AddressRouteResult>()

    suspend fun geocode(query: String, region: DeviceRegion, apiKey: String): Coordinate? = withContext(Dispatchers.IO) {
        if (query.isBlank() || apiKey.isBlank()) return@withContext null

        geocodeQueries(query, region).forEach { scopedQuery ->
            val cacheKey = scopedQuery.lowercase(Locale.ROOT)
            geocodeCache[cacheKey]?.let { return@withContext it }

            val coordinate = requestWithRetry { requestGeocode(scopedQuery, apiKey) }
            if (coordinate != null) {
                geocodeCache[cacheKey] = coordinate
                return@withContext coordinate
            }
        }

        null
    }

    suspend fun drivingDistanceKm(origin: Coordinate, destination: Coordinate, apiKey: String): Double? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null
        val cacheKey = listOf(origin.latitude, origin.longitude, destination.latitude, destination.longitude)
            .joinToString("|")
        routeCache[cacheKey]?.let { return@withContext it }

        val body = String.format(
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

        val distanceKm = requestWithRetry { requestDrivingDistance(body, apiKey) }
        if (distanceKm != null) routeCache[cacheKey] = distanceKm
        distanceKm
    }

    /**
     * Caminho rapido: entrega o endereco textual diretamente ao Routes API.
     *
     * O proprio endpoint resolve o endereco e calcula a rota na mesma chamada. Isso evita
     * esperar primeiro o Geocoding API e somente depois iniciar o Routes API, removendo uma
     * viagem de rede do primeiro calculo de cada destino.
     */
    suspend fun drivingDistanceFromAddress(
        originAddress: String,
        destination: Coordinate,
        apiKey: String,
    ): AddressRouteResult? = withContext(Dispatchers.IO) {
        val cleanAddress = originAddress.trim().replace(Regex("\\s+"), " ")
        if (cleanAddress.isBlank() || apiKey.isBlank()) return@withContext null
        val cacheKey = listOf(
            cleanAddress.lowercase(Locale.ROOT),
            String.format(Locale.US, "%.6f", destination.latitude),
            String.format(Locale.US, "%.6f", destination.longitude),
        ).joinToString("|")
        addressRouteCache[cacheKey]?.let { return@withContext it }

        val body = String.format(
            Locale.US,
            """
            {
              "origin": {"address": %s},
              "destination": {"location": {"latLng": {"latitude": %.7f, "longitude": %.7f}}},
              "travelMode": "DRIVE",
              "routingPreference": "TRAFFIC_UNAWARE",
              "languageCode": "pt-BR",
              "units": "METRIC"
            }
            """.trimIndent(),
            cleanAddress.asJsonString(),
            destination.latitude,
            destination.longitude,
        )

        val result = requestWithRetry(attempts = FAST_REQUEST_ATTEMPTS) {
            requestAddressRoute(body, apiKey)
        }
        if (result != null) addressRouteCache[cacheKey] = result
        result
    }

    private fun requestDrivingDistance(body: String, apiKey: String): Double? =
        requestRouteResponse(
            body = body,
            apiKey = apiKey,
            fieldMask = "routes.distanceMeters",
            connectTimeoutMillis = CONNECT_TIMEOUT_MS,
            readTimeoutMillis = READ_TIMEOUT_MS,
        )?.let(::parseDistanceKm)

    private fun requestAddressRoute(body: String, apiKey: String): AddressRouteResult? =
        requestRouteResponse(
            body = body,
            apiKey = apiKey,
            fieldMask = "routes.distanceMeters,routes.legs.startLocation",
            connectTimeoutMillis = FAST_CONNECT_TIMEOUT_MS,
            readTimeoutMillis = FAST_READ_TIMEOUT_MS,
        )?.let(::parseAddressRoute)

    private fun requestRouteResponse(
        body: String,
        apiKey: String,
        fieldMask: String,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int,
    ): String? {
        val connection = (URL("https://routes.googleapis.com/directions/v2:computeRoutes").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Goog-Api-Key", apiKey.trim())
            setRequestProperty("X-Goog-FieldMask", fieldMask)
            setRequestProperty("X-Android-Package", BuildConfig.APPLICATION_ID)
            setRequestProperty("Connection", "keep-alive")
        }

        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
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
            setRequestProperty("X-Android-Package", BuildConfig.APPLICATION_ID)
            setRequestProperty("Connection", "keep-alive")
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
        val route = json.parseToJsonElement(body).jsonObject["routes"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?: return null
        val distanceMeters = route["distanceMeters"]?.jsonPrimitive?.intOrNull ?: return null
        return distanceMeters / 1000.0
    }

    private fun parseAddressRoute(body: String): AddressRouteResult? {
        val route = json.parseToJsonElement(body).jsonObject["routes"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?: return null
        val distanceMeters = route["distanceMeters"]?.jsonPrimitive?.intOrNull ?: return null
        val startLatLng = route["legs"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("startLocation")?.jsonObject
            ?.get("latLng")?.jsonObject
        val latitude = startLatLng?.get("latitude")?.jsonPrimitive?.doubleOrNull
        val longitude = startLatLng?.get("longitude")?.jsonPrimitive?.doubleOrNull
        val originCoordinate = if (latitude != null && longitude != null) Coordinate(latitude, longitude) else null
        return AddressRouteResult(
            distanceKm = distanceMeters / 1000.0,
            originCoordinate = originCoordinate,
        )
    }

    private fun String.asJsonString(): String = buildString(length + 2) {
        append('"')
        for (character in this@asJsonString) {
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }

    private fun <T> requestWithRetry(
        attempts: Int = REQUEST_ATTEMPTS,
        block: () -> T?,
    ): T? {
        repeat(attempts.coerceAtLeast(1)) { attempt ->
            val result = runCatching(block).getOrNull()
            if (result != null) return result
            if (attempt < attempts - 1) Thread.sleep(RETRY_DELAY_MS)
        }
        return null
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 2_500
        const val READ_TIMEOUT_MS = 4_000
        const val FAST_CONNECT_TIMEOUT_MS = 1_800
        const val FAST_READ_TIMEOUT_MS = 2_800
        const val REQUEST_ATTEMPTS = 2
        const val FAST_REQUEST_ATTEMPTS = 1
        const val RETRY_DELAY_MS = 120L
    }
}
