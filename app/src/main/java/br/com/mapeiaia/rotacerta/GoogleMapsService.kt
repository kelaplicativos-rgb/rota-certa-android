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

class GoogleMapsService {
    private val json = Json { ignoreUnknownKeys = true }
    private val geocodeCache = mutableMapOf<String, Coordinate?>()
    private val routeCache = mutableMapOf<String, Double?>()

    suspend fun geocode(query: String, region: DeviceRegion, apiKey: String): Coordinate? = withContext(Dispatchers.IO) {
        if (query.isBlank() || apiKey.isBlank()) return@withContext null

        geocodeQueries(query, region).forEach { scopedQuery ->
            val cacheKey = scopedQuery.lowercase(Locale.ROOT)
            if (geocodeCache.containsKey(cacheKey)) {
                geocodeCache[cacheKey]?.let { return@withContext it }
                return@forEach
            }

            val coordinate = requestGeocode(scopedQuery, apiKey)
            geocodeCache[cacheKey] = coordinate
            if (coordinate != null) return@withContext coordinate
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

        val distanceKm = runCatching {
            val connection = (URL("https://routes.googleapis.com/directions/v2:computeRoutes").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = ROUTE_TIMEOUT_MS
                readTimeout = ROUTE_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Goog-Api-Key", apiKey.trim())
                setRequestProperty("X-Goog-FieldMask", "routes.distanceMeters")
            }

            try {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                if (connection.responseCode !in 200..299) return@runCatching null
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                parseDistanceKm(response)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()

        routeCache[cacheKey] = distanceKm
        distanceKm
    }

    private fun geocodeQueries(query: String, region: DeviceRegion): List<String> {
        val cleanQuery = query.trim().replace(Regex("""\s+"""), " ")
        if (cleanQuery.isBlank()) return emptyList()

        val country = region.country.ifBlank { "Brasil" }
        val regionCity = region.city.takeIf { it.isNotBlank() }
        val alreadyHasCity = cleanQuery.contains("sao paulo", ignoreCase = true) ||
            cleanQuery.contains("são paulo", ignoreCase = true)
        val defaultCity = if (alreadyHasCity) null else "São Paulo, SP"

        return buildList {
            if (regionCity != null && !alreadyHasCity) add("$cleanQuery, $regionCity, $country")
            defaultCity?.let { add("$cleanQuery, $it, $country") }
            if (!alreadyHasCity) add("$cleanQuery, São Paulo - SP, $country")
            add("$cleanQuery, $country")
            add(cleanQuery)
        }
            .map { it.trim().replace(Regex("""\s+"""), " ") }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
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

        return runCatching {
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = GEOCODE_TIMEOUT_MS
                readTimeout = GEOCODE_TIMEOUT_MS
            }

            try {
                if (connection.responseCode !in 200..299) return@runCatching null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                parseCoordinate(body)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
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

    private companion object {
        const val GEOCODE_TIMEOUT_MS = 900
        const val ROUTE_TIMEOUT_MS = 900
    }
}
