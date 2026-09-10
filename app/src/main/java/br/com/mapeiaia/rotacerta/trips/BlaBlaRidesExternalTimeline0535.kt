package br.com.mapeiaia.rotacerta.trips

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal const val EXTERNAL_TIMELINE_SCHEMA_0535 = "rota-certa-blablacar-rides-external-timeline-v1"
internal const val EXTERNAL_TIMELINE_KIND_0535 = "BLABLACAR_SUAS_VIAGENS_EXTERNAL_TIMELINE"

@Serializable
internal data class BlaBlaExternalTimelineEvidence0535(
    val html: Boolean = false,
    val mhtml: Boolean = false,
    val dateText: String = "",
    val dateYearExplicit: Boolean = false,
    val dateResolution: String = "",
)

@Serializable
internal data class BlaBlaExternalTimelineRide0535(
    val tripId: String,
    val listPosition: Int = -1,
    val date: String = "",
    val departureTime: String = "",
    val arrivalTime: String = "",
    val arrivalTimeKnown: Boolean = false,
    val origin: String = "",
    val destination: String = "",
    val status: String = "",
    val administrativeUrl: String = "",
    val normalizedEvidenceText: String = "",
    val source: String = "RIDE_LIST",
    val cardFound: Boolean = false,
    val routeParsed: Boolean = false,
    val dateParsed: Boolean = false,
    val parseStatus: String = "PARTIAL",
    val evidence: BlaBlaExternalTimelineEvidence0535 = BlaBlaExternalTimelineEvidence0535(),
    val inconsistencies: List<String> = emptyList(),
)

@Serializable
internal data class BlaBlaExternalTimelineContinuity0535(
    val previousTripId: String,
    val nextTripId: String,
    val previousDestination: String = "",
    val nextOrigin: String = "",
    val classification: String,
)

@Serializable
internal data class BlaBlaExternalTimelineSchedule0535(
    val strategy: String,
    val classification: String,
    val observedDates: List<String> = emptyList(),
)

@Serializable
internal data class BlaBlaExternalTimelineReconciliation0535(
    val inventoryCount: Int,
    val timelineCount: Int,
    val sameTripSet: Boolean,
    val missingCards: List<String> = emptyList(),
    val unexpectedCards: List<String> = emptyList(),
    val formatInconsistencyCount: Int = 0,
)

@Serializable
internal data class BlaBlaExternalTimelineProfile0535(
    val profileUuid: String,
    val accountKey: String,
    val displayName: String = "",
    val identityConfirmed: Boolean,
    val rideCount: Int,
    val timeline: List<BlaBlaExternalTimelineRide0535>,
    val reconciliation: BlaBlaExternalTimelineReconciliation0535,
    val continuity: List<BlaBlaExternalTimelineContinuity0535> = emptyList(),
    val alternating: BlaBlaExternalTimelineSchedule0535,
)

@Serializable
internal data class BlaBlaExternalTimelineProjection0535(
    val schemaVersion: String = EXTERNAL_TIMELINE_SCHEMA_0535,
    val kind: String = EXTERNAL_TIMELINE_KIND_0535,
    val captureId: String,
    val captureCompletedAt: String,
    val sourceAppVersion: String,
    val sourceVersionCode: Int,
    val sourceCommitSha: String,
    val sourceBranch: String,
    val result: String,
    val projectionStatus: String,
    val totalProfiles: Int,
    val totalRides: Int,
    val profiles: List<BlaBlaExternalTimelineProfile0535>,
)

internal data class BlaBlaExternalTimelineDownloadResult0535(
    val displayName: String,
    val relativeLocation: String,
    val bytes: Long,
)

internal data class BlaBlaExternalScheduleCycle0535(
    val workDays: Int,
    val restDays: Int,
    val baseDate: LocalDate,
) {
    init {
        require(workDays > 0) { "workDays precisa ser positivo" }
        require(restDays > 0) { "restDays precisa ser positivo" }
    }

    val cycleDays: Int get() = workDays + restDays

    fun isWorkDay(date: LocalDate): Boolean {
        val offset = ChronoUnit.DAYS.between(baseDate, date)
        val index = Math.floorMod(offset, cycleDays.toLong()).toInt()
        return index < workDays
    }

    fun nextWorkDay(from: LocalDate): LocalDate {
        var candidate = from.plusDays(1)
        repeat(cycleDays + 1) {
            if (isWorkDay(candidate)) return candidate
            candidate = candidate.plusDays(1)
        }
        error("Ciclo de escala inválido")
    }
}

internal data class ParsedExternalRide0535(
    val tripId: String,
    val listPosition: Int,
    val date: String,
    val dateText: String,
    val dateYearExplicit: Boolean,
    val dateResolution: String,
    val departureTime: String,
    val arrivalTime: String,
    val origin: String,
    val destination: String,
    val status: String,
    val administrativeUrl: String,
)

internal data class ParsedExternalRideSet0535(
    val rides: List<ParsedExternalRide0535>,
    val duplicateTripIds: List<String>,
)

private val CARD_START_0535 = Regex(
    """(?is)<article\b[^>]*data-testid\s*=\s*["']e2e-your-rides-trip-card-(\d+)["'][^>]*>""",
)
private val TRIP_ID_FROM_URL_0535 = Regex(
    """(?i)(?:[?&](?:amp;)?id=|/ride-plan/trip-edit/)([A-Za-z0-9_-]{8,160})""",
)
private val HREF_0535 = Regex("""(?is)href\s*=\s*["']([^"']{1,1500})["']""")
private val H2_0535 = Regex("""(?is)<h2\b[^>]*>(.*?)</h2>""")
private val DATE_0535 = Regex(
    """(?iu)(\d{1,2})\s+(jan|fev|mar|abr|mai|jun|jul|ago|set|out|nov|dez)\.?(?:\s+(\d{4}))?""",
)
private val TIME_0535 = Regex("""^([01]?\d|2[0-3]):[0-5]\d$""")
private val TAG_0535 = Regex("""(?is)<[^>]+>""")
private val SPACE_0535 = Regex("""\s+""")
private val BRAZIL_STATE_SUFFIX_0535 = Regex(
    """\s(?:ac|al|ap|am|ba|ce|df|es|go|ma|mt|ms|mg|pa|pb|pr|pe|pi|rj|rn|rs|ro|rr|sc|sp|se|to)$""",
)

private val MONTHS_0535 = mapOf(
    "jan" to 1, "fev" to 2, "mar" to 3, "abr" to 4,
    "mai" to 5, "jun" to 6, "jul" to 7, "ago" to 8,
    "set" to 9, "out" to 10, "nov" to 11, "dez" to 12,
)

private fun decodeHtml0535(raw: String): String = raw
    .replace("&amp;", "&", ignoreCase = true)
    .replace("&quot;", "\"", ignoreCase = true)
    .replace("&#39;", "'", ignoreCase = true)
    .replace("&lt;", "<", ignoreCase = true)
    .replace("&gt;", ">", ignoreCase = true)
    .replace("&nbsp;", " ", ignoreCase = true)

private fun visibleText0535(raw: String): String = decodeHtml0535(raw)
    .replace(TAG_0535, " ")
    .replace(SPACE_0535, " ")
    .trim()

private fun testIdText0535(card: String, id: String): String {
    val escaped = Regex.escape(id)
    val match = Regex(
        """(?is)<[^>]+data-testid\s*=\s*["']$escaped["'][^>]*>(.*?)(?:</p>|</div>|</span>|</time>)""",
    ).find(card) ?: return ""
    return visibleText0535(match.groupValues[1]).take(300)
}

private fun parseCardDate0535(raw: String, captureDate: LocalDate): Triple<String, Boolean, String> {
    val text = visibleText0535(raw)
    val match = DATE_0535.find(text.lowercase()) ?: return Triple("", false, "UNPARSED")
    val day = match.groupValues[1].toIntOrNull() ?: return Triple("", false, "UNPARSED")
    val month = MONTHS_0535[match.groupValues[2].lowercase()] ?: return Triple("", false, "UNPARSED")
    val explicitYear = match.groupValues[3].toIntOrNull()
    val year = explicitYear ?: if (month < captureDate.monthValue) captureDate.year + 1 else captureDate.year
    val resolved = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return Triple("", false, "UNPARSED")
    return Triple(
        resolved.toString(),
        explicitYear != null,
        if (explicitYear != null) "EXPLICIT_YEAR" else "CAPTURE_YEAR_UI_CONTEXT",
    )
}

private fun findAdministrativeUrl0535(card: String): Pair<String, String>? {
    HREF_0535.findAll(card).forEach { hrefMatch ->
        val href = decodeHtml0535(hrefMatch.groupValues[1]).trim()
        val trip = TRIP_ID_FROM_URL_0535.find(href)?.groupValues?.getOrNull(1).orEmpty()
        if (trip.isNotBlank()) return trip to href.take(1500)
    }
    return null
}

internal fun parseExternalRideCards0535(
    raw: String,
    captureDate: LocalDate,
    source: String,
): ParsedExternalRideSet0535 {
    if (raw.isBlank()) return ParsedExternalRideSet0535(emptyList(), emptyList())
    val starts = CARD_START_0535.findAll(raw).toList()
    if (starts.isEmpty()) return ParsedExternalRideSet0535(emptyList(), emptyList())
    val parsed = starts.mapIndexedNotNull { index, start ->
        val end = starts.getOrNull(index + 1)?.range?.first ?: raw.length
        val card = raw.substring(start.range.first, end)
        val idAndUrl = findAdministrativeUrl0535(card) ?: return@mapIndexedNotNull null
        val dateText = H2_0535.find(card)?.groupValues?.getOrNull(1)?.let(::visibleText0535).orEmpty().take(120)
        val (date, yearExplicit, resolution) = parseCardDate0535(dateText, captureDate)
        val departureTime = testIdText0535(card, "e2e-itinerary-departure-time").takeIf { TIME_0535.matches(it) }.orEmpty()
        val arrivalTime = testIdText0535(card, "e2e-itinerary-arrival-time").takeIf { TIME_0535.matches(it) }.orEmpty()
        val origin = testIdText0535(card, "e2e-itinerary-departure-station")
        val destination = testIdText0535(card, "e2e-itinerary-arrival-station")
        val status = listOf(
            "e2e-your-rides-trip-status",
            "e2e-trip-card-status",
            "e2e-ride-status",
        ).firstNotNullOfOrNull { key -> testIdText0535(card, key).takeIf(String::isNotBlank) }.orEmpty()
        ParsedExternalRide0535(
            tripId = idAndUrl.first,
            listPosition = start.groupValues[1].toIntOrNull() ?: index,
            date = date,
            dateText = dateText,
            dateYearExplicit = yearExplicit,
            dateResolution = resolution,
            departureTime = departureTime,
            arrivalTime = arrivalTime,
            origin = origin,
            destination = destination,
            status = status,
            administrativeUrl = idAndUrl.second,
        )
    }

    val grouped = parsed.groupBy { it.tripId }
    val duplicates = grouped.filter { (_, values) ->
        if (source == "MHTML") values.distinctBy { it.copy(listPosition = -1) }.size > 1 else values.size > 1
    }.keys.sorted()
    val unique = grouped.values.map { values -> values.first() }.sortedBy { it.listPosition }
    return ParsedExternalRideSet0535(unique, duplicates)
}

private fun normalizedLocation0535(raw: String): String = Normalizer.normalize(raw, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase()
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()
    .replace(BRAZIL_STATE_SUFFIX_0535, "")
    .trim()

private fun evidenceText0535(ride: BlaBlaExternalTimelineRide0535): String = listOfNotNull(
    ride.tripId.takeIf { it.isNotBlank() }?.let { "tripId=$it" },
    ride.evidence.dateText.takeIf { it.isNotBlank() }?.let { "date=$it" },
    ride.departureTime.takeIf { it.isNotBlank() }?.let { "departure=$it" },
    ride.arrivalTime.takeIf { it.isNotBlank() }?.let { "arrival=$it" },
    ride.origin.takeIf { it.isNotBlank() }?.let { "origin=$it" },
    ride.destination.takeIf { it.isNotBlank() }?.let { "destination=$it" },
    ride.status.takeIf { it.isNotBlank() }?.let { "status=$it" },
    ride.administrativeUrl.takeIf { it.isNotBlank() }?.let { "adminUrl=$it" },
).joinToString(" | ").take(2400)

private fun mergeRide0535(
    tripId: String,
    html: ParsedExternalRide0535?,
    mhtml: ParsedExternalRide0535?,
): BlaBlaExternalTimelineRide0535 {
    val inconsistencies = mutableListOf<String>()
    fun merge(field: String, a: String, b: String): String {
        if (a.isBlank()) return b
        if (b.isBlank()) return a
        if (a == b) return a
        inconsistencies += "HTML_MHTML_${field.uppercase()}_MISMATCH"
        return ""
    }
    val listPosition = when {
        html == null -> mhtml?.listPosition ?: -1
        mhtml == null -> html.listPosition
        html.listPosition == mhtml.listPosition -> html.listPosition
        else -> {
            inconsistencies += "HTML_MHTML_LIST_POSITION_MISMATCH"
            minOf(html.listPosition, mhtml.listPosition)
        }
    }
    val date = merge("date", html?.date.orEmpty(), mhtml?.date.orEmpty())
    val dateText = merge("date_text", html?.dateText.orEmpty(), mhtml?.dateText.orEmpty())
    val departureTime = merge("departure_time", html?.departureTime.orEmpty(), mhtml?.departureTime.orEmpty())
    val arrivalTime = merge("arrival_time", html?.arrivalTime.orEmpty(), mhtml?.arrivalTime.orEmpty())
    val origin = merge("origin", html?.origin.orEmpty(), mhtml?.origin.orEmpty())
    val destination = merge("destination", html?.destination.orEmpty(), mhtml?.destination.orEmpty())
    val status = merge("status", html?.status.orEmpty(), mhtml?.status.orEmpty())
    val adminUrl = merge("administrative_url", html?.administrativeUrl.orEmpty(), mhtml?.administrativeUrl.orEmpty())
    val resolution = merge("date_resolution", html?.dateResolution.orEmpty(), mhtml?.dateResolution.orEmpty())
    val cardFound = html != null || mhtml != null
    val routeParsed = origin.isNotBlank() && destination.isNotBlank()
    val dateParsed = date.isNotBlank()
    val yearExplicit = listOfNotNull(html?.dateYearExplicit, mhtml?.dateYearExplicit).any { it }
    val base = BlaBlaExternalTimelineRide0535(
        tripId = tripId,
        listPosition = listPosition,
        date = date,
        departureTime = departureTime,
        arrivalTime = arrivalTime,
        arrivalTimeKnown = arrivalTime.isNotBlank(),
        origin = origin,
        destination = destination,
        status = status,
        administrativeUrl = adminUrl,
        cardFound = cardFound,
        routeParsed = routeParsed,
        dateParsed = dateParsed,
        parseStatus = if (cardFound && routeParsed && dateParsed) "COMPLETE" else "PARTIAL",
        evidence = BlaBlaExternalTimelineEvidence0535(
            html = html != null,
            mhtml = mhtml != null,
            dateText = dateText,
            dateYearExplicit = yearExplicit,
            dateResolution = resolution,
        ),
        inconsistencies = inconsistencies.distinct().sorted(),
    )
    return base.copy(normalizedEvidenceText = evidenceText0535(base))
}

internal fun analyzeContinuity0535(
    timeline: List<BlaBlaExternalTimelineRide0535>,
): List<BlaBlaExternalTimelineContinuity0535> = timeline.zipWithNext { previous, next ->
    val previousLocation = normalizedLocation0535(previous.destination)
    val nextLocation = normalizedLocation0535(next.origin)
    val classification = when {
        previousLocation.isBlank() || nextLocation.isBlank() -> "CONTINUITY_UNKNOWN"
        previousLocation == nextLocation -> "CONTINUITY_CONFIRMED"
        else -> "CONTINUITY_CONFLICT"
    }
    BlaBlaExternalTimelineContinuity0535(
        previousTripId = previous.tripId,
        nextTripId = next.tripId,
        previousDestination = previous.destination,
        nextOrigin = next.origin,
        classification = classification,
    )
}

internal fun analyzeAlternating0535(
    timeline: List<BlaBlaExternalTimelineRide0535>,
): BlaBlaExternalTimelineSchedule0535 {
    val dates = timeline.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.distinct().sorted()
    val classification = if (dates.size < 2) {
        "ALTERNATING_UNKNOWN"
    } else {
        val gaps = dates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }
        when {
            gaps.any { it == 1L } -> "ALTERNATING_BREAK"
            gaps.all { it == 2L } -> "ALTERNATING_MATCH"
            else -> "ALTERNATING_UNKNOWN"
        }
    }
    return BlaBlaExternalTimelineSchedule0535(
        strategy = "WORK_1_REST_1",
        classification = classification,
        observedDates = dates.map(LocalDate::toString),
    )
}

internal object BlaBlaRidesExternalTimeline0535 {
    private const val MAX_JSON_BYTES_0535 = 4 * 1024 * 1024
    private val JSON_0535 = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
        prettyPrint = true
    }

    internal fun projectProfile0535(
        profileUuid: String,
        accountKey: String,
        displayName: String,
        identityConfirmed: Boolean,
        inventoryTripIds: List<String>,
        htmlRaw: String,
        mhtmlRaw: String,
        captureDate: LocalDate,
    ): BlaBlaExternalTimelineProfile0535 {
        require(BlaBlaRidesSnapshotStore0526.strongUuid(profileUuid) == profileUuid) { "profileUuid inválido" }
        require(identityConfirmed) { "Identidade do perfil não confirmada" }
        val canonicalInventory = canonicalTripIds0528(inventoryTripIds)
        require(canonicalInventory.size == inventoryTripIds.size) { "Inventário contém tripId duplicado ou inválido" }

        val html = parseExternalRideCards0535(htmlRaw, captureDate, "HTML")
        val mhtmlCorpus = if (mhtmlRaw.isBlank()) "" else mhtmlSearchCorpus0528(mhtmlRaw)
        val mhtml = parseExternalRideCards0535(mhtmlCorpus, captureDate, "MHTML")
        val duplicates = (html.duplicateTripIds + mhtml.duplicateTripIds).distinct().sorted()
        require(duplicates.isEmpty()) { "DUPLICATE_TRIP_ID: ${duplicates.joinToString()}" }

        val htmlById = html.rides.associateBy { it.tripId }
        val mhtmlById = mhtml.rides.associateBy { it.tripId }
        val observed = htmlById.keys + mhtmlById.keys
        val unexpected = (observed - canonicalInventory.toSet()).sorted()
        require(unexpected.isEmpty()) { "CARD_NOT_IN_INVENTORY: ${unexpected.joinToString()}" }

        val merged = canonicalInventory.map { tripId ->
            mergeRide0535(tripId, htmlById[tripId], mhtmlById[tripId])
        }
        val sorted = merged.sortedWith(
            compareBy<BlaBlaExternalTimelineRide0535> { it.date.isBlank() }
                .thenBy { it.date }
                .thenBy { it.departureTime.ifBlank { "99:99" } }
                .thenBy { if (it.listPosition < 0) Int.MAX_VALUE else it.listPosition },
        )
        val missing = sorted.filterNot { it.cardFound }.map { it.tripId }
        val formatInconsistencies = sorted.sumOf { it.inconsistencies.size }
        val timelineSet = sorted.map { it.tripId }.toSet()
        val sameTripSet = timelineSet == canonicalInventory.toSet() && sorted.size == canonicalInventory.size
        require(sameTripSet) { "TIMELINE_INVENTORY_RECONCILIATION_FAILED" }

        return BlaBlaExternalTimelineProfile0535(
            profileUuid = profileUuid,
            accountKey = accountKey,
            displayName = displayName,
            identityConfirmed = true,
            rideCount = canonicalInventory.size,
            timeline = sorted,
            reconciliation = BlaBlaExternalTimelineReconciliation0535(
                inventoryCount = canonicalInventory.size,
                timelineCount = sorted.size,
                sameTripSet = sameTripSet,
                missingCards = missing,
                unexpectedCards = unexpected,
                formatInconsistencyCount = formatInconsistencies,
            ),
            continuity = analyzeContinuity0535(sorted),
            alternating = analyzeAlternating0535(sorted),
        )
    }

    fun build(
        context: Context,
        manifest: BlaBlaRidesSnapshotManifest0526,
    ): BlaBlaExternalTimelineProjection0535 {
        val app = context.applicationContext
        val portable = BlaBlaRidesPortableJson0531.build(app, manifest)
        val captureDate = runCatching {
            Instant.parse(manifest.completedAt).atZone(ZoneOffset.UTC).toLocalDate()
        }.getOrElse { error("Data da captura inválida") }
        val store = BlaBlaRidesSnapshotStore0526(app)

        val profiles = portable.profiles.map { portableProfile ->
            val sourceProfile = manifest.profiles.single { it.authenticatedProfileUuid == portableProfile.authenticatedProfileUuid }
            val htmlFile = store.resolveArtifact0528(manifest.captureId, sourceProfile.htmlFile)
                ?: error("HTML de ${sourceProfile.displayName} ausente")
            val mhtmlFile = if (sourceProfile.mhtmlCaptured) {
                store.resolveArtifact0528(manifest.captureId, sourceProfile.mhtmlFile)
                    ?: error("MHTML de ${sourceProfile.displayName} ausente")
            } else null
            require(verifySnapshotArtifact0528(htmlFile, sourceProfile.htmlBytes, sourceProfile.htmlSha256)) {
                "HTML não corresponde à evidência verificada"
            }
            if (mhtmlFile != null) {
                require(verifySnapshotArtifact0528(mhtmlFile, sourceProfile.mhtmlBytes, sourceProfile.mhtmlSha256)) {
                    "MHTML não corresponde à evidência verificada"
                }
            }
            projectProfile0535(
                profileUuid = portableProfile.authenticatedProfileUuid,
                accountKey = portableProfile.accountKey,
                displayName = portableProfile.displayName,
                identityConfirmed = portableProfile.identityConfirmed,
                inventoryTripIds = portableProfile.tripIds,
                htmlRaw = htmlFile.readText(Charsets.UTF_8),
                mhtmlRaw = mhtmlFile?.readBytes()?.toString(Charsets.ISO_8859_1).orEmpty(),
                captureDate = captureDate,
            )
        }
        val status = when {
            profiles.any { !it.reconciliation.sameTripSet } -> "RECONCILIATION_ERROR"
            profiles.any { it.reconciliation.missingCards.isNotEmpty() } -> "PARTIAL"
            profiles.any { it.reconciliation.formatInconsistencyCount > 0 } -> "COMPLETE_WITH_INCONSISTENCIES"
            else -> "COMPLETE"
        }
        return validate(
            BlaBlaExternalTimelineProjection0535(
                captureId = manifest.captureId,
                captureCompletedAt = manifest.completedAt,
                sourceAppVersion = manifest.appVersion,
                sourceVersionCode = manifest.versionCode,
                sourceCommitSha = manifest.commitSha,
                sourceBranch = manifest.branch,
                result = manifest.result,
                projectionStatus = status,
                totalProfiles = profiles.size,
                totalRides = profiles.sumOf { it.rideCount },
                profiles = profiles,
            ),
        )
    }

    fun validate(value: BlaBlaExternalTimelineProjection0535): BlaBlaExternalTimelineProjection0535 {
        require(value.schemaVersion == EXTERNAL_TIMELINE_SCHEMA_0535) { "Schema de timeline externa inválido" }
        require(value.kind == EXTERNAL_TIMELINE_KIND_0535) { "Tipo de timeline externa inválido" }
        require(value.result == BlaBlaRidesSnapshotStatus0526.COMPLETE) { "Captura de origem não está COMPLETE" }
        require(value.totalProfiles == value.profiles.size && value.profiles.isNotEmpty()) { "Perfis inconsistentes" }
        require(value.profiles.map { it.profileUuid }.distinct().size == value.profiles.size) { "Perfis duplicados" }
        value.profiles.forEach { profile ->
            require(BlaBlaRidesSnapshotStore0526.strongUuid(profile.profileUuid) == profile.profileUuid) { "UUID inválido" }
            require(profile.identityConfirmed) { "Identidade não confirmada" }
            require(profile.rideCount == profile.timeline.size) { "Contagem da timeline inconsistente" }
            require(profile.reconciliation.sameTripSet) { "Inventário e timeline divergem" }
            require(profile.reconciliation.inventoryCount == profile.rideCount) { "Inventário inconsistente" }
            require(profile.reconciliation.timelineCount == profile.timeline.size) { "Timeline inconsistente" }
            require(profile.timeline.map { it.tripId }.distinct().size == profile.timeline.size) { "tripId duplicado na timeline" }
            profile.timeline.forEach { ride ->
                require(ride.normalizedEvidenceText.length <= 2400) { "Evidência textual excede limite" }
                require(!ride.normalizedEvidenceText.contains("whatsapp", ignoreCase = true)) { "Dado privado indevido" }
                require(!ride.normalizedEvidenceText.contains("telefone", ignoreCase = true)) { "Dado privado indevido" }
                require(!ride.normalizedEvidenceText.contains("email", ignoreCase = true)) { "Dado privado indevido" }
                require(ride.arrivalTimeKnown == ride.arrivalTime.isNotBlank()) { "Estado de chegada inconsistente" }
            }
        }
        require(value.totalRides == value.profiles.sumOf { it.rideCount }) { "Total de viagens inconsistente" }
        return value
    }

    fun encode(value: BlaBlaExternalTimelineProjection0535): String {
        val raw = JSON_0535.encodeToString(BlaBlaExternalTimelineProjection0535.serializer(), validate(value))
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_JSON_BYTES_0535) { "JSON da timeline excede limite" }
        require(sensitiveArtifactMarker0528(raw) == null) { "JSON da timeline contém material de autenticação" }
        return raw
    }

    fun decode(raw: String): BlaBlaExternalTimelineProjection0535 {
        require(raw.toByteArray(Charsets.UTF_8).size in 1..MAX_JSON_BYTES_0535) { "JSON vazio ou grande demais" }
        require(sensitiveArtifactMarker0528(raw) == null) { "JSON da timeline contém material de autenticação" }
        return validate(JSON_0535.decodeFromString(BlaBlaExternalTimelineProjection0535.serializer(), raw))
    }
}

internal object BlaBlaRidesExternalTimelineDownload0535 {
    suspend fun download(
        context: Context,
        projection: BlaBlaExternalTimelineProjection0535,
    ): BlaBlaExternalTimelineDownloadResult0535 = withContext(Dispatchers.IO) {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { "Download automático requer Android 10 ou superior" }
        val app = context.applicationContext
        val raw = BlaBlaRidesExternalTimeline0535.encode(projection)
        require(BlaBlaRidesExternalTimeline0535.decode(raw) == projection) { "Readback da timeline externa falhou" }
        val bytes = raw.toByteArray(Charsets.UTF_8)
        val displayName = "rota-certa-linha-do-tempo-externa-${BlaBlaRidesSnapshotStore0526.safeCaptureId(projection.captureId)}.json"
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/Rota Certa"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = app.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Android não disponibilizou destino em Downloads")
        try {
            resolver.openOutputStream(uri, "w")?.use { it.write(bytes); it.flush() }
                ?: error("Não foi possível gravar a timeline externa")
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                .also { resolver.update(uri, it, null, null) }
            UnifiedDebugEventStore.recordAlways(
                "BLABLACAR_RIDES_EXTERNAL_TIMELINE_EXPORTED_0535",
                app.packageName,
                "captureId=${projection.captureId} profiles=${projection.totalProfiles} rides=${projection.totalRides} status=${projection.projectionStatus} bytes=${bytes.size} source=RIDE_LIST deepTripOpen=false privatePassengerData=false",
            )
            BlaBlaExternalTimelineDownloadResult0535(
                displayName = displayName,
                relativeLocation = "$relativePath/$displayName",
                bytes = bytes.size.toLong(),
            )
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
}
