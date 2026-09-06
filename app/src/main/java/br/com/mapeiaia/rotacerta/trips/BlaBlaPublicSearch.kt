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
    /** Unique execution identity; generated for every new auditable collection. */
    val collectionId: String = "",
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
    val queryId: String = "",
    val direction: String = "",
    val tripId: String? = null,
    val profileUuid: String? = null,
    val profileUuidEvidence: String? = null,
    val currency: String? = null,
    val capturedAtMillis: Long? = null,
    val captureIndex: Int = -1,
)

@Serializable
data class BlaBlaPublicSearchQueryEvidence(
    val requestedDateConfirmed: Boolean = false,
    val requestedRouteConfirmed: Boolean = false,
    val terminalEvidence: Boolean = false,
    val stableAtBottom: Boolean = false,
)

@Serializable
data class BlaBlaPublicSearchErrorDetail(
    val stage: String = "",
    val exceptionClass: String? = null,
    val exceptionMessage: String? = null,
    val rootCauseClass: String? = null,
    val rootCauseMessage: String? = null,
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
    val queryId: String = "",
    val direction: String = "",
    val coverageStatus: String = "",
    val startedAtMillis: Long = 0L,
    val finishedAtMillis: Long = 0L,
    val evidence: BlaBlaPublicSearchQueryEvidence = BlaBlaPublicSearchQueryEvidence(),
    val errorDetail: BlaBlaPublicSearchErrorDetail? = null,
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
    /** User-facing result after applying the explicit profile filter. */
    val cards: List<BlaBlaPublicSearchCard> = emptyList(),
    /** Raw auditable collection. Kept separate so discovery never pollutes the requested result. */
    val rawCards: List<BlaBlaPublicSearchCard> = emptyList(),
    val queries: List<BlaBlaPublicSearchQueryResult> = emptyList(),
    val demands: List<BlaBlaPublicSearchDemand> = emptyList(),
) {
    val validatedQueries: Int get() = queries.count {
        it.coverageStatus == "COMPLETE" || (it.coverageStatus.isBlank() && it.status == "validated")
    }
    val completeQueries: Int get() = validatedQueries
    val failedQueries: Int get() = queries.count { it.coverageStatus == "FAILED" }
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


    data class KnownProfile(
        val name: String,
        val profileUuid: String?,
    )

    fun parseTargetNames(raw: String): List<String> {
        val unique = linkedMapOf<String, String>()
        raw.split(',').forEach { token ->
            val display = token.trim()
            val normalized = normalizePerson(display)
            if (normalized.isNotBlank() && normalized !in unique) unique[normalized] = display
        }
        return unique.values.toList()
    }

    fun filterRequestedCards(
        rawCards: List<BlaBlaPublicSearchCard>,
        request: BlaBlaPublicSearchRequest,
        knownProfiles: List<KnownProfile> = emptyList(),
    ): List<BlaBlaPublicSearchCard> {
        val targets = request.targetNames
            .mapNotNull { display -> normalizePerson(display).takeIf(String::isNotBlank)?.let { it to display } }
            .distinctBy { it.first }
        val targetKeys = targets.map { it.first }.toSet()
        if (targetKeys.isEmpty()) return dedupeUsefulCards(rawCards)

        val knownByName = knownProfiles
            .mapNotNull { profile ->
                val key = normalizePerson(profile.name)
                val uuid = profile.profileUuid?.trim()?.lowercase()?.takeIf(String::isNotBlank)
                key.takeIf(String::isNotBlank)?.let { it to uuid }
            }
            .groupBy({ it.first }, { it.second })

        val observedStrongUuids = rawCards
            .mapNotNull { card ->
                val key = normalizePerson(card.driverName)
                val uuid = card.profileUuid?.trim()?.lowercase()?.takeIf(String::isNotBlank)
                if (key.isNotBlank() && uuid != null) key to uuid else null
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, uuids) -> uuids.toSet() }

        val filtered = rawCards.filter { card ->
            val key = normalizePerson(card.driverName)
            if (key !in targetKeys) return@filter false
            val cardUuid = card.profileUuid?.trim()?.lowercase()?.takeIf(String::isNotBlank)
            val knownUuids = knownByName[key].orEmpty().filterNotNull().toSet()
            when {
                knownUuids.size > 1 -> false
                knownUuids.size == 1 -> cardUuid == knownUuids.single()
                observedStrongUuids[key].orEmpty().size > 1 -> false
                observedStrongUuids[key].orEmpty().size == 1 -> cardUuid == observedStrongUuids.getValue(key).single()
                else -> true
            }
        }
        return dedupeUsefulCards(filtered)
    }

    private fun dedupeUsefulCards(cards: List<BlaBlaPublicSearchCard>): List<BlaBlaPublicSearchCard> =
        cards.distinctBy { card ->
            card.tripId?.trim()?.takeIf(String::isNotBlank)?.let { "trip:$it" }
                ?: card.tripHref?.let(BlaBlaCollectorUrlModule::canonical)?.takeIf(String::isNotBlank)?.let { "href:$it" }
                ?: listOf(
                    card.date,
                    card.direction,
                    normalizePerson(card.driverName),
                    normalizePlace(card.searchFrom),
                    normalizePlace(card.searchTo),
                    card.departureTime.orEmpty(),
                ).joinToString("|")
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
        mensagemDemanda = messages.takeIf { it.isNotEmpty() }?.joinToString(" "),
        dataHoraCaptura = capturedAtMillis,
    )
}

internal fun publicSearchDirectionName(
    request: BlaBlaPublicSearchRequest,
    task: BlaBlaPublicSearchTask,
): String = when (BlaBlaPublicSearchPlanner.direction(task.from, task.to, request)) {
    BlaBlaPublicSearchDirection.PRIMARY -> "OUTBOUND"
    BlaBlaPublicSearchDirection.REVERSE -> "RETURN"
    BlaBlaPublicSearchDirection.UNKNOWN -> "UNKNOWN"
}

internal fun publicSearchQueryId(
    request: BlaBlaPublicSearchRequest,
    task: BlaBlaPublicSearchTask,
): String = "q-${task.date}-${publicSearchDirectionName(request, task).lowercase()}"

object BlaBlaPublicPlaceDirectory {
    /**
     * Global route resolution: names come from user selection. BlaBlaCar may enrich
     * the loaded URL with provider-specific place IDs; Rota Certa never hardcodes
     * a personal corridor or promotes search/place IDs to canonical identity.
     */
    fun supported(address: String): Boolean =
        BlaBlaPublicSearchPlanner.normalizePlace(address).isNotBlank()

    fun searchUrl(task: BlaBlaPublicSearchTask): String? =
        searchUrl(task, BlaBlaCollectorUrlModule.ORIGIN)

    fun searchUrl(task: BlaBlaPublicSearchTask, providerOrigin: String?): String? {
        if (!supported(task.from) || !supported(task.to)) return null
        val origin = BlaBlaCollectorUrlModule.origin(providerOrigin) ?: return null
        fun encoded(value: String): String =
            java.net.URLEncoder.encode(value.trim(), Charsets.UTF_8.name())
        return buildString {
            append(origin).append("/search")
            append("?fn=").append(encoded(task.from))
            append("&tn=").append(encoded(task.to))
            append("&db=").append(task.date)
            append("&seats=1")
            append("&search_origin=SEARCH")
        }
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

    fun lastSnapshot(): BlaBlaAuditableCollectionSnapshot? = runCatching {
        prefs.getString(KEY_AUDIT_SNAPSHOT, null)?.let {
            json.decodeFromString<BlaBlaAuditableCollectionSnapshot>(it)
        }
    }.getOrNull()

    fun saveSnapshot(snapshot: BlaBlaAuditableCollectionSnapshot) {
        prefs.edit().putString(KEY_AUDIT_SNAPSHOT, json.encodeToString(snapshot)).apply()
    }

    fun clearResponse() {
        prefs.edit().remove(KEY_RESPONSE).remove(KEY_AUDIT_SNAPSHOT).apply()
    }

    companion object {
        private const val PREFS = "rota_certa_blablacar_public_search_v1"
        private const val KEY_REQUEST = "request"
        private const val KEY_RESPONSE = "response"
        private const val KEY_AUDIT_SNAPSHOT = "audit_snapshot_v1"
    }
}

internal fun exactPublicTripHrefForTrip(
    expectedTripId: String?,
    hrefs: List<String?>,
    providerOrigin: String? = null,
): String? {
    val expected = expectedTripId?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val origin = BlaBlaCollectorUrlModule.origin(providerOrigin)
    return hrefs.firstNotNullOfOrNull { raw ->
        val value = raw?.trim().orEmpty()
        val resolved = if (value.startsWith('/') && origin != null) "$origin$value" else value
        BlaBlaCollectorUrlModule.publicTrip(resolved, expected)
    }
}

internal fun publicSearchTripIdFromHref(raw: String?): String? {
    val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val uri = runCatching { URI(if (value.startsWith('/')) "https://placeholder.invalid$value" else value) }.getOrNull() ?: return null
    uri.rawQuery.orEmpty().split('&').firstOrNull { it.substringBefore('=') == "id" }
        ?.substringAfter('=', "")?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
    return Regex("/trip/([^/?#]+)", RegexOption.IGNORE_CASE)
        .find(uri.path.orEmpty())?.groupValues?.getOrNull(1)?.takeIf(String::isNotBlank)
}
