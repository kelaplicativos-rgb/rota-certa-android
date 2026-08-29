package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BlaBlaPublicSearchRequest(
    val targetNames: List<String>,
    val from: String,
    val to: String,
    val period: String = "",
    val includeReverse: Boolean = true,
    val selectedDates: List<String> = emptyList(),
    val captureDemand: Boolean = false,
)

@Serializable
data class BlaBlaPublicSearchCard(
    val driverName: String,
    val date: String,
    val searchFrom: String,
    val searchTo: String,
    val departureTime: String? = null,
    val arrivalTime: String? = null,
    val actualDeparture: String? = null,
    val actualArrival: String? = null,
    val price: String? = null,
    val duration: String? = null,
    val driverRating: String? = null,
    val availableSeats: Int? = null,
    val flags: List<String> = emptyList(),
    val availability: String = "available_or_unspecified",
    val tripHref: String? = null,
)

@Serializable
data class BlaBlaPublicSearchQueryResult(
    val date: String,
    val from: String,
    val to: String,
    val status: String,
    val cardCount: Int = 0,
    val zeroResultsConfirmed: Boolean = false,
    val error: String? = null,
)

@Serializable
data class BlaBlaPublicSearchDemand(
    val date: String,
    val from: String,
    val to: String,
    val indicadorDemandaEncontrado: Boolean,
    val trechoConcorrido: Boolean? = null,
    val percentualReservado: Int? = null,
    val mensagemDemanda: String? = null,
    val dataHoraCaptura: Long,
)


@Serializable
data class BlaBlaPublicSearchResponse(
    val collectedAtMillis: Long = System.currentTimeMillis(),
    val status: String,
    val request: BlaBlaPublicSearchRequest,
    val cards: List<BlaBlaPublicSearchCard> = emptyList(),
    val queries: List<BlaBlaPublicSearchQueryResult> = emptyList(),
    val demands: List<BlaBlaPublicSearchDemand> = emptyList(),
) {
    val validatedQueries: Int get() = queries.count { it.status == "validated" }
    val failedQueries: Int get() = queries.count { it.status != "validated" }
}

data class BlaBlaPublicSearchTask(
    val date: LocalDate,
    val from: String,
    val to: String,
)

internal enum class BlaBlaPublicSearchDirection {
    PRIMARY,
    REVERSE,
    UNKNOWN,
}

object BlaBlaPublicSearchPlanner {
    private const val PROFILE_VISUAL_SLOTS = 8

    fun tasks(
        request: BlaBlaPublicSearchRequest,
        today: LocalDate = LocalDate.now(),
    ): List<BlaBlaPublicSearchTask> {
        val dates = datesFor(request, today)
        if (dates.isEmpty()) return emptyList()
        val routes = buildList {
            add(request.from.trim() to request.to.trim())
            if (request.includeReverse && normalizePlace(request.from) != normalizePlace(request.to)) {
                add(request.to.trim() to request.from.trim())
            }
        }.distinct()
        return dates.flatMap { date -> routes.map { (from, to) -> BlaBlaPublicSearchTask(date, from, to) } }
    }

    fun datesFor(
        request: BlaBlaPublicSearchRequest,
        today: LocalDate = LocalDate.now(),
    ): List<LocalDate> {
        val explicit = request.selectedDates
            .mapNotNull { raw -> runCatching { LocalDate.parse(raw.trim()) }.getOrNull() }
            .distinct()
            .sorted()
        if (explicit.isNotEmpty()) return explicit
        return datesFor(request.period, today)
    }

    fun datesFor(
        period: String,
        today: LocalDate = LocalDate.now(),
    ): List<LocalDate> {
        val value = period.trim()
        runCatching { LocalDate.parse(value) }.getOrNull()?.let { return listOf(it) }
        val month = if (value.isBlank()) YearMonth.from(today) else
            runCatching { YearMonth.parse(value) }.getOrNull() ?: return emptyList()
        val firstDay = month.atDay(1)
        val lastDay = month.atEndOfMonth()
        val startDay = maxOf(firstDay, today)
        if (startDay.isAfter(lastDay)) return emptyList()
        return generateSequence(startDay) { current ->
            current.plusDays(1).takeIf { next -> !next.isAfter(lastDay) }
        }.toList()
    }

    fun matchesTarget(driverName: String?, targets: List<String>): Boolean {
        val driver = normalizePerson(driverName.orEmpty())
        if (driver.isBlank()) return false
        val normalizedTargets = targets.map(::normalizePerson).filter(String::isNotBlank)
        if (normalizedTargets.isEmpty()) return true
        return normalizedTargets.any { it == driver }
    }

    /**
     * Visual identity belongs to the monitored profile, never to the travel direction.
     * Sorting normalized targets makes the slot independent from the order typed by the user.
     */
    internal fun profileVisualSlot(driverName: String, targets: List<String>): Int {
        val driver = normalizePerson(driverName)
        val normalizedTargets = targets
            .map(::normalizePerson)
            .filter(String::isNotBlank)
            .distinct()
            .sorted()
        val index = normalizedTargets.indexOf(driver)
        if (index >= 0) return index % PROFILE_VISUAL_SLOTS
        return Math.floorMod(driver.hashCode(), PROFILE_VISUAL_SLOTS)
    }

    /** Primary means exactly the route entered by the user. Reverse means its inverse. */
    internal fun direction(
        searchFrom: String,
        searchTo: String,
        request: BlaBlaPublicSearchRequest,
    ): BlaBlaPublicSearchDirection {
        val from = normalizePlace(searchFrom)
        val to = normalizePlace(searchTo)
        val primaryFrom = normalizePlace(request.from)
        val primaryTo = normalizePlace(request.to)
        return when {
            from == primaryFrom && to == primaryTo -> BlaBlaPublicSearchDirection.PRIMARY
            request.includeReverse && from == primaryTo && to == primaryFrom -> BlaBlaPublicSearchDirection.REVERSE
            else -> BlaBlaPublicSearchDirection.UNKNOWN
        }
    }

    fun normalizePlace(value: String): String = Normalizer.normalize(value.substringBefore(',').trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    fun normalizePerson(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

internal fun publicSearchDemandFor(
    request: BlaBlaPublicSearchRequest,
    task: BlaBlaPublicSearchTask,
    bodyText: String,
    capturedAtMillis: Long = System.currentTimeMillis(),
): BlaBlaPublicSearchDemand? {
    if (!request.captureDemand) return null
    val normalized = bodyText.replace(Regex("\\s+"), " ").trim()
    val busy = Regex(
        "Trecho\\s+concorrido!?\\s*(?:É|E)\\s+bom\\s+reservar\\s+logo\\.?",
        RegexOption.IGNORE_CASE,
    ).find(normalized)
    val reserved = Regex(
        "(\\d{1,3})\\s*%\\s*das\\s+viagens\\s+(?:já\\s+)?estão\\s+reservadas\\.?",
        RegexOption.IGNORE_CASE,
    ).find(normalized)
    val percentage = reserved?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..100 }
    val messages = listOfNotNull(
        busy?.value?.trim()?.takeIf(String::isNotEmpty),
        reserved?.value?.trim()?.takeIf { percentage != null && it.isNotEmpty() },
    ).distinct()
    return BlaBlaPublicSearchDemand(
        date = task.date.toString(),
        from = task.from,
        to = task.to,
        indicadorDemandaEncontrado = messages.isNotEmpty(),
        trechoConcorrido = busy?.let { true },
        percentualReservado = percentage,
        mensagemDemanda = messages.takeIf(List<String>::isNotEmpty)?.joinToString(" "),
        dataHoraCaptura = capturedAtMillis,
    )
}

object BlaBlaPublicPlaceDirectory {
    private val placeIds = mapOf(
        "santo andre" to "eyJpIjoiQ2hJSjczNGRoM2hDenBRUjNrN2JLb2JLcXA0IiwicCI6MSwidiI6MSwidCI6WzRdfQ==",
        "sao paulo" to "eyJpIjoiQ2hJSjBXR2tnNEZFenBRUnJsc3pfd2hMcVpzIiwicCI6MSwidiI6MSwidCI6WzRdfQ==",
        "extrema" to "eyJpIjoiQ2hJSkc5Q1pfWG1yenBRUkg5N0xSejRQVkV3IiwicCI6MSwidiI6MSwidCI6WzRdfQ==",
        "camanducaia" to "eyJpIjoiQ2hJSm1kSFpyYjhBekpRUmYwSnpYYWt6T184IiwicCI6MSwidiI6MSwidCI6WzRdfQ==",
        "pouso alegre" to "eyJpIjoiQ2hJSlYyNFBheDdIeTVRUlV0ZU5EWDhWN0wwIiwicCI6MSwidiI6MSwidCI6WzRdfQ==",
        "tres coracoes" to "eyJpIjoiQ2hJSm00VnEtLURjeXBRUnBVYXZXTU5QeGtjIiwicCI6MSwidiI6MSwidCI6WzRdfQ==",
        "varginha" to "eyJpIjoiQ2hJSlBUM2hEME9OeXBRUl9ad3hYeFEwTmVNIiwicCI6MSwidiI6MSwidCI6WzRdfQ==",
        "campanha" to "eyJpIjoiQ2hJSkhaclc1TExqeXBRUk9qVWt6ZEFkRFVjIiwicCI6MSwidiI6MSwidCI6WzRdfQ==",
        "cambuquira" to "eyJpIjoiQ2hJSlJfOTNLaGdneTVRUmJ6QkU3emJ6X25vIiwicCI6MSwidiI6MSwidCI6WzRdfQ==",
        "sao thome das letras" to "eyJpIjoiQ2hJSkEteldrMEhWbndBUkM0TGhEX2dUNzcwIiwicCI6MSwidiI6MSwidCI6WzRdfQ==",
        "sao tome das letras" to "eyJpIjoiQ2hJSkEteldrMEhWbndBUkM0TGhEX2dUNzcwIiwicCI6MSwidiI6MSwidCI6WzRdfQ==",
    )

    fun placeId(address: String): String? = placeIds[BlaBlaPublicSearchPlanner.normalizePlace(address)]

    fun supported(address: String): Boolean = placeId(address) != null

    fun searchUrl(task: BlaBlaPublicSearchTask): String? {
        val fromId = placeId(task.from) ?: return null
        val toId = placeId(task.to) ?: return null
        fun enc(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
        return "https://www.blablacar.com.br/search" +
            "?fn=${enc(task.from)}" +
            "&tn=${enc(task.to)}" +
            "&db=${task.date}" +
            "&seats=1" +
            "&search_origin=SEARCH" +
            "&from_place_id=${enc(fromId)}" +
            "&to_place_id=${enc(toId)}" +
            "&p0%5Bac%5D=adult"
    }
}

class BlaBlaPublicSearchStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun lastRequest(): BlaBlaPublicSearchRequest? = runCatching {
        prefs.getString(KEY_REQUEST, null)?.let { json.decodeFromString<BlaBlaPublicSearchRequest>(it) }
    }.getOrNull()

    fun saveRequest(request: BlaBlaPublicSearchRequest) {
        prefs.edit().putString(KEY_REQUEST, json.encodeToString(request)).apply()
    }

    fun lastResponse(): BlaBlaPublicSearchResponse? = runCatching {
        prefs.getString(KEY_RESPONSE, null)?.let { json.decodeFromString<BlaBlaPublicSearchResponse>(it) }
    }.getOrNull()

    fun saveResponse(response: BlaBlaPublicSearchResponse) {
        prefs.edit().putString(KEY_RESPONSE, json.encodeToString(response)).apply()
    }

    fun clearResponse() {
        prefs.edit().remove(KEY_RESPONSE).apply()
    }

    companion object {
        private const val PREFS = "rota_certa_blablacar_public_search_v1"
        private const val KEY_REQUEST = "request"
        private const val KEY_RESPONSE = "response"
    }
}

internal fun publicSearchTripIdFromHref(raw: String?): String? {
    val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val uri = runCatching { URI(if (value.startsWith('/')) "https://www.blablacar.com.br$value" else value) }.getOrNull() ?: return null
    uri.rawQuery.orEmpty().split('&').firstOrNull { it.substringBefore('=') == "id" }
        ?.substringAfter('=', "")?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
    return Regex("/trip/([^/?#]+)", RegexOption.IGNORE_CASE)
        .find(uri.path.orEmpty())?.groupValues?.getOrNull(1)?.takeIf(String::isNotBlank)
}
