package br.com.mapeiaia.rotacerta.trips

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@kotlinx.serialization.Serializable
data class TripRouteLeg(
    val fromStopId: String,
    val toStopId: String,
    val distanceMeters: Int,
    val durationSeconds: Long,
)

@kotlinx.serialization.Serializable
data class TripRoutePlan(
    val stops: List<TripStop>,
    val legs: List<TripRouteLeg>,
    val totalDistanceMeters: Int,
    val totalDurationSeconds: Long,
)

object TripDurationParser {
    fun seconds(raw: String?): Long? {
        val value = raw?.trim()?.removeSuffix("s")?.toDoubleOrNull() ?: return null
        if (!value.isFinite() || value < 0.0) return null
        return kotlin.math.ceil(value).toLong()
    }
}

/**
 * Dedicated Stage47 route planner. It deliberately does not modify or share
 * mutable state with the FAROL Google route stack.
 */
class TripRoutePlanner {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun plan(
        stopNames: List<String>,
        departureAtMillis: Long,
        apiKey: String,
    ): TripRoutePlan = withContext(Dispatchers.IO) {
        require(stopNames.size in 2..24) { "A rota precisa ter entre 2 e 24 paradas." }
        require(apiKey.isNotBlank()) { "A chave do Google Maps não está configurada." }
        val cleanNames = stopNames.map { it.trim() }
        require(cleanNames.none(String::isBlank)) { "Todas as paradas precisam estar preenchidas." }

        val geocoded = cleanNames.mapIndexed { index, name ->
            val coordinate = geocodeAddress(name, apiKey)
                ?: throw IllegalStateException("Não consegui localizar a parada ${index + 1}: $name")
            TripStop(
                order = index,
                name = name,
                address = name,
                latitude = coordinate.first,
                longitude = coordinate.second,
            )
        }

        val route = computeRoute(geocoded, departureAtMillis, apiKey)
        require(route.legs.size == geocoded.size - 1) {
            "O Google retornou uma quantidade inesperada de trechos."
        }

        var cursorMillis = departureAtMillis
        val scheduled = geocoded.mapIndexed { index, stop ->
            if (index == 0) {
                stop.copy(
                    plannedArrivalMillis = departureAtMillis,
                    plannedDepartureMillis = departureAtMillis,
                )
            } else {
                cursorMillis += route.legs[index - 1].durationSeconds * 1000L
                stop.copy(
                    plannedArrivalMillis = cursorMillis,
                    plannedDepartureMillis = if (index == geocoded.lastIndex) null else cursorMillis,
                )
            }
        }
        val legsWithIds = route.legs.mapIndexed { index, leg ->
            leg.copy(
                fromStopId = scheduled[index].id,
                toStopId = scheduled[index + 1].id,
            )
        }
        TripRoutePlan(
            stops = scheduled,
            legs = legsWithIds,
            totalDistanceMeters = route.totalDistanceMeters,
            totalDurationSeconds = route.totalDurationSeconds,
        )
    }

    private fun geocodeAddress(address: String, apiKey: String): Pair<Double, Double>? {
        val encoded = URLEncoder.encode(address, "UTF-8")
        val key = URLEncoder.encode(apiKey.trim(), "UTF-8")
        val url = URL(
            "https://maps.googleapis.com/maps/api/geocode/json" +
                "?address=$encoded&region=br&language=pt-BR&key=$key",
        )
        return runCatching {
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3_500
                readTimeout = 3_500
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching null
                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val root = json.parseToJsonElement(body).jsonObject
                if (root["status"]?.jsonPrimitive?.content != "OK") return@runCatching null
                val location = root["results"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("geometry")?.jsonObject
                    ?.get("location")?.jsonObject
                    ?: return@runCatching null
                val lat = location["lat"]?.jsonPrimitive?.doubleOrNull ?: return@runCatching null
                val lng = location["lng"]?.jsonPrimitive?.doubleOrNull ?: return@runCatching null
                lat to lng
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private data class RawRoute(
        val legs: List<TripRouteLeg>,
        val totalDistanceMeters: Int,
        val totalDurationSeconds: Long,
    )

    private fun computeRoute(
        stops: List<TripStop>,
        departureAtMillis: Long,
        apiKey: String,
    ): RawRoute {
        val waypoint: (TripStop) -> JsonObject = { stop ->
            buildJsonObject {
                put("location", buildJsonObject {
                    put("latLng", buildJsonObject {
                        put("latitude", JsonPrimitive(requireNotNull(stop.latitude)))
                        put("longitude", JsonPrimitive(requireNotNull(stop.longitude)))
                    })
                })
            }
        }
        val request = buildJsonObject {
            put("origin", waypoint(stops.first()))
            put("destination", waypoint(stops.last()))
            if (stops.size > 2) {
                put("intermediates", buildJsonArray {
                    stops.subList(1, stops.lastIndex).forEach { add(waypoint(it)) }
                })
            }
            put("travelMode", "DRIVE")
            put("routingPreference", "TRAFFIC_AWARE")
            put("languageCode", "pt-BR")
            put("units", "METRIC")
            if (departureAtMillis > System.currentTimeMillis() + 60_000L) {
                put("departureTime", Instant.ofEpochMilli(departureAtMillis).toString())
            }
        }
        val connection = (URL("https://routes.googleapis.com/directions/v2:computeRoutes").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 5_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("X-Goog-Api-Key", apiKey.trim())
            setRequestProperty(
                "X-Goog-FieldMask",
                "routes.distanceMeters,routes.duration,routes.legs.distanceMeters,routes.legs.duration",
            )
        }
        try {
            connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException("Google Routes respondeu HTTP $status: ${response.take(180)}")
            }
            val root = json.parseToJsonElement(response).jsonObject
            val route = root["routes"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: throw IllegalStateException("O Google não retornou rota de carro para essas paradas.")
            val rawLegs = route["legs"]?.jsonArray.orEmpty()
            val legs = rawLegs.map { element ->
                val leg = element.jsonObject
                TripRouteLeg(
                    fromStopId = "",
                    toStopId = "",
                    distanceMeters = leg["distanceMeters"]?.jsonPrimitive?.intOrNull ?: 0,
                    durationSeconds = TripDurationParser.seconds(leg["duration"]?.jsonPrimitive?.content)
                        ?: throw IllegalStateException("Duração de trecho ausente na resposta do Google."),
                )
            }
            return RawRoute(
                legs = legs,
                totalDistanceMeters = route["distanceMeters"]?.jsonPrimitive?.intOrNull
                    ?: legs.sumOf(TripRouteLeg::distanceMeters),
                totalDurationSeconds = TripDurationParser.seconds(route["duration"]?.jsonPrimitive?.content)
                    ?: legs.sumOf(TripRouteLeg::durationSeconds),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
}
