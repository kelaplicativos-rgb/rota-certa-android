package br.com.mapeiaia.rotacerta.trips

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import br.com.mapeiaia.rotacerta.BuildConfig
import br.com.mapeiaia.rotacerta.RotaCertaTenantIdentity
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable data class BlaBlaAuditTenant(val id: String)
@Serializable data class BlaBlaAuditPeriod(val startDate: String, val endDate: String)
@Serializable data class BlaBlaAuditPlace(val name: String)
@Serializable data class BlaBlaAuditRoute(val origin: BlaBlaAuditPlace, val destination: BlaBlaAuditPlace)

@Serializable
data class BlaBlaAuditQueryEvidence(
    val requestedDateConfirmed: Boolean,
    val requestedRouteConfirmed: Boolean,
    val terminalEvidence: Boolean,
    val stableAtBottom: Boolean,
    val zeroResultsConfirmed: Boolean,
)

@Serializable
data class BlaBlaAuditError(
    val stage: String,
    val exceptionClass: String? = null,
    val exceptionMessage: String? = null,
    val rootCauseClass: String? = null,
    val rootCauseMessage: String? = null,
)

@Serializable
data class BlaBlaAuditQuery(
    val queryId: String,
    val date: String,
    val direction: String,
    val origin: BlaBlaAuditPlace,
    val destination: BlaBlaAuditPlace,
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val status: String,
    val cardsFound: Int,
    val evidence: BlaBlaAuditQueryEvidence,
    val error: BlaBlaAuditError? = null,
)

@Serializable
data class BlaBlaAuditOwnership(
    val ownership: String,
    val profileUuid: String? = null,
    val matchedBy: List<String> = emptyList(),
)

@Serializable
data class BlaBlaAuditPublicCard(
    val provider: String = "BLABLACAR",
    val queryIds: List<String>,
    val date: String,
    val direction: String,
    val searchedOrigin: BlaBlaAuditPlace,
    val searchedDestination: BlaBlaAuditPlace,
    val departureTime: String? = null,
    val arrivalTime: String? = null,
    val presentedOrigin: String? = null,
    val presentedDestination: String? = null,
    val publicDriverName: String? = null,
    val price: String? = null,
    val currency: String? = null,
    val availableSeats: Int? = null,
    val availability: String? = null,
    val demandBusySegment: Boolean? = null,
    val reservedPercentage: Int? = null,
    val demandMessage: String? = null,
    val tripHref: String? = null,
    val tripId: String? = null,
    val profileUuid: String? = null,
    val identifierEvidence: List<String> = emptyList(),
    val ownership: BlaBlaAuditOwnership,
    val capturedAt: String? = null,
    val identityKind: String,
)

@Serializable
data class BlaBlaAuditDriverProfile(
    val profileUuid: String,
    val displayName: String,
    val provider: String = "BLABLACAR",
)

@Serializable
data class BlaBlaAuditTripStop(
    val id: String,
    val order: Int,
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val plannedArrivalAt: String? = null,
    val plannedDepartureAt: String? = null,
    val priceToNextMinorUnits: Long? = null,
)

@Serializable
data class BlaBlaAuditSegmentAvailability(
    val fromStopId: String,
    val toStopId: String,
    val occupiedSeats: Int,
    val passengerSeats: Int,
    val blockedSeats: Int,
    val availableSeats: Int,
    val overbookingSeats: Int,
)

@Serializable
data class BlaBlaAuditInventory(
    val blablaQuotaSeats: Int,
    val rotaCertaQuotaSeats: Int,
    val operationalInventorySeats: Int,
    val confirmedPassengerSeats: Int,
    val blockedSeats: Int,
    val availableSeats: Int,
    val totalAvailableSeats: Int,
    val overbookingSeats: Int,
    val reliable: Boolean,
    val source: String = "OPERATIONAL_SEAT_SUMMARY",
)

@Serializable
data class BlaBlaAuditExternalIdentity(
    val profileUuid: String? = null,
    val tripId: String? = null,
    val publicTripHref: String? = null,
)

@Serializable
data class BlaBlaAuditReconciliation(
    val publicCardTripId: String? = null,
    val matchedBy: List<String> = emptyList(),
    val state: String,
)

@Serializable
data class BlaBlaAuditReconciledTrip(
    val internalTripId: String,
    val remoteId: String? = null,
    val title: String,
    val departureAt: String,
    val status: String,
    val recordOrigin: String,
    val externalIdentity: BlaBlaAuditExternalIdentity,
    val publicationExisting: Boolean,
    val publicBookingEnabled: Boolean,
    val itineraryAuthoritative: Boolean,
    val stops: List<BlaBlaAuditTripStop>,
    val inventory: BlaBlaAuditInventory,
    val segmentAvailability: List<BlaBlaAuditSegmentAvailability>,
    val reconciliation: BlaBlaAuditReconciliation,
    val continuityPositionState: String,
    val source: String = "TRIP_STORE_CANONICAL",
)

@Serializable
data class BlaBlaAuditContinuityEvidence(
    val previousTripId: String,
    val previousProfileUuid: String? = null,
    val previousDestination: String? = null,
    val nextTripId: String,
    val nextProfileUuid: String? = null,
    val nextOrigin: String? = null,
    val state: String,
)

@Serializable
data class BlaBlaAuditSummary(
    val expectedQueries: Int,
    val completeQueries: Int,
    val partialQueries: Int,
    val pendingUnknownQueries: Int,
    val failedQueries: Int,
    val publicCardsFound: Int,
    val ownPublicTripsRecognized: Int,
    val reconciledLocalTrips: Int,
    val coverageComplete: Boolean,
)

@Serializable
data class BlaBlaAuditableCollectionSnapshot(
    val schemaVersion: String = "1.0",
    val generatedAt: String,
    val collectorVersion: String,
    val tenant: BlaBlaAuditTenant,
    val period: BlaBlaAuditPeriod,
    val route: BlaBlaAuditRoute,
    val directions: List<String> = listOf("OUTBOUND", "RETURN"),
    val queries: List<BlaBlaAuditQuery>,
    val publicCards: List<BlaBlaAuditPublicCard>,
    val driverProfiles: List<BlaBlaAuditDriverProfile>,
    val reconciledTrips: List<BlaBlaAuditReconciledTrip>,
    val continuityEvidence: List<BlaBlaAuditContinuityEvidence>,
    val summary: BlaBlaAuditSummary,
)

object BlaBlaAuditableCollectionBuilder {
    fun build(context: Context, response: BlaBlaPublicSearchResponse): BlaBlaAuditableCollectionSnapshot {
        val app = context.applicationContext
        val store = TripStore(app)
        return build(
            response = response,
            tenant = RotaCertaTenantRegistry(app).activeTenant(),
            profiles = BlaBlaDynamicAccountRegistry(app).list(),
            trips = store.trips(),
            bookings = store.bookings(),
            collectorVersion = "${BuildConfig.VERSION_NAME}+${BuildConfig.BUILD_GIT_SHA.take(12)}",
        )
    }

    internal fun build(
        response: BlaBlaPublicSearchResponse,
        tenant: RotaCertaTenantIdentity,
        profiles: List<BlaBlaDynamicAccount>,
        trips: List<Trip>,
        bookings: List<Booking>,
        collectorVersion: String,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): BlaBlaAuditableCollectionSnapshot {
        val request = response.request.copy(includeReverse = true)
        val tasks = BlaBlaPublicSearchPlanner.tasks(request)
        val dates = tasks.map(BlaBlaPublicSearchTask::date).distinct().sorted()
        val queryById = response.queries.associateBy { q ->
            q.queryId.ifBlank {
                runCatching {
                    publicSearchQueryId(request, BlaBlaPublicSearchTask(LocalDate.parse(q.date), q.from, q.to))
                }.getOrDefault("q-unresolved-${q.date}")
            }
        }

        val auditQueries = tasks.map { task ->
            val id = publicSearchQueryId(request, task)
            val q = queryById[id]
            val coverage = normalizeCoverage(q)
            BlaBlaAuditQuery(
                queryId = id,
                date = task.date.toString(),
                direction = publicSearchDirectionName(request, task),
                origin = BlaBlaAuditPlace(task.from),
                destination = BlaBlaAuditPlace(task.to),
                startedAt = q?.startedAtMillis?.takeIf { it > 0 }?.let(::iso),
                finishedAt = q?.finishedAtMillis?.takeIf { it > 0 }?.let(::iso),
                status = coverage,
                cardsFound = q?.cardCount?.coerceAtLeast(0) ?: 0,
                evidence = BlaBlaAuditQueryEvidence(
                    requestedDateConfirmed = q?.evidence?.requestedDateConfirmed == true,
                    requestedRouteConfirmed = q?.evidence?.requestedRouteConfirmed == true,
                    terminalEvidence = q?.evidence?.terminalEvidence == true,
                    stableAtBottom = q?.evidence?.stableAtBottom == true,
                    zeroResultsConfirmed = coverage == "COMPLETE" && q?.zeroResultsConfirmed == true,
                ),
                error = q?.errorDetail?.let(::auditError),
            )
        }.sortedWith(compareBy(BlaBlaAuditQuery::date, BlaBlaAuditQuery::direction, BlaBlaAuditQuery::queryId))

        val driverProfiles = profiles.mapNotNull { account ->
            val uuid = strongUuid(account.profileUuid) ?: return@mapNotNull null
            BlaBlaAuditDriverProfile(uuid, account.profileName?.trim()?.takeIf(String::isNotBlank) ?: account.displayLabel)
        }.distinctBy(BlaBlaAuditDriverProfile::profileUuid)
            .sortedBy(BlaBlaAuditDriverProfile::profileUuid)

        val driverUuids = driverProfiles.map { it.profileUuid }.toSet()
        val demandByQuery = response.demands.mapNotNull { d ->
            runCatching {
                publicSearchQueryId(request, BlaBlaPublicSearchTask(LocalDate.parse(d.date), d.from, d.to)) to d
            }.getOrNull()
        }.toMap()

        data class Occurrence(val key: String, val value: BlaBlaAuditPublicCard)
        val auditCards = response.rawCards.ifEmpty { response.cards }
        val occurrences = auditCards.mapIndexed { occurrenceIndex, card ->
            val fallbackTask = runCatching { BlaBlaPublicSearchTask(LocalDate.parse(card.date), card.searchFrom, card.searchTo) }.getOrNull()
            val queryId = card.queryId.ifBlank { fallbackTask?.let { publicSearchQueryId(request, it) }.orEmpty() }
            val direction = card.direction.ifBlank { fallbackTask?.let { publicSearchDirectionName(request, it) }.orEmpty() }
            val href = card.tripHref?.let(BlaBlaCollectorUrlModule::canonical)?.takeIf(String::isNotBlank)
            val tripId = card.tripId?.trim()?.takeIf(String::isNotBlank) ?: BlaBlaCollectorUrlModule.tripId(href)
            val profileUuid = strongUuid(card.profileUuid)
            val own = profileUuid != null && profileUuid in driverUuids
            val demand = demandByQuery[queryId]
            val evidence = buildList {
                if (tripId != null) add("TRIP_ID")
                if (href != null) add("PUBLIC_TRIP_HREF")
                if (profileUuid != null) add(card.profileUuidEvidence ?: "PUBLIC_PROFILE_UUID")
            }.distinct().sorted()
            val key = tripId?.let { "BLABLACAR|$it" } ?: listOf(
                "FALLBACK_NON_CANONICAL", queryId, card.captureIndex.toString(), occurrenceIndex.toString(),
                card.departureTime.orEmpty(), card.driverName, href.orEmpty(),
            ).joinToString("|")
            Occurrence(
                key,
                BlaBlaAuditPublicCard(
                    queryIds = listOf(queryId),
                    date = card.date,
                    direction = direction,
                    searchedOrigin = BlaBlaAuditPlace(card.searchFrom),
                    searchedDestination = BlaBlaAuditPlace(card.searchTo),
                    departureTime = card.departureTime,
                    arrivalTime = card.arrivalTime,
                    presentedOrigin = card.actualDeparture,
                    presentedDestination = card.actualArrival,
                    publicDriverName = card.driverName.trim().takeIf(String::isNotBlank),
                    price = card.price,
                    currency = card.currency,
                    availableSeats = card.availableSeats,
                    availability = card.availability.takeIf(String::isNotBlank),
                    demandBusySegment = demand?.trechoConcorrido,
                    reservedPercentage = demand?.percentualReservado,
                    demandMessage = safeText(demand?.mensagemDemanda),
                    tripHref = href,
                    tripId = tripId,
                    profileUuid = profileUuid,
                    identifierEvidence = evidence,
                    ownership = if (own) BlaBlaAuditOwnership("CONFIRMED", profileUuid, listOf("PROFILE_UUID"))
                    else BlaBlaAuditOwnership("PENDING_UNKNOWN"),
                    capturedAt = card.capturedAtMillis?.takeIf { it > 0 }?.let(::iso),
                    identityKind = if (tripId != null) "CANONICAL_TRIP_ID" else "COMPOSITE_FALLBACK_NON_CANONICAL",
                ),
            )
        }

        val publicCards = occurrences.groupBy(Occurrence::key).map { (_, group) ->
            val first = group.first().value
            first.copy(
                queryIds = group.flatMap { it.value.queryIds }.filter(String::isNotBlank).distinct().sorted(),
                identifierEvidence = group.flatMap { it.value.identifierEvidence }.distinct().sorted(),
                capturedAt = group.mapNotNull { it.value.capturedAt }.minOrNull(),
            )
        }.sortedWith(
            compareBy<BlaBlaAuditPublicCard> { it.date }
                .thenBy { it.direction }
                .thenBy { it.departureTime.orEmpty() }
                .thenBy { it.tripId.orEmpty() }
                .thenBy { it.queryIds.joinToString("|") },
        )

        val dateSet = dates.toSet()
        val relevantTrips = trips.filter { trip ->
            Instant.ofEpochMilli(trip.departureAtMillis).atZone(zoneId).toLocalDate() in dateSet
        }.sortedWith(compareBy(Trip::departureAtMillis, Trip::id))

        val reconciledTrips = relevantTrips.map { trip ->
            val tripBookings = bookings.filter { it.tripId == trip.id }
            val operational = operationalSeatSummary(trip, tripBookings, response.collectedAtMillis)
            val inventoryTrip = trip.copy(capacity = operational.operationalInventorySeats)
            val segments = SeatAvailabilityEngine.segmentLoads(inventoryTrip, tripBookings, response.collectedAtMillis)
            val tripId = trip.blablaTripId?.trim()?.takeIf(String::isNotBlank)
            val profileUuid = strongUuid(trip.blablaProfileUuid)
            val match = publicCards.firstOrNull { card ->
                tripId != null && card.tripId == tripId &&
                    (card.profileUuid == null || profileUuid == null || card.profileUuid.equals(profileUuid, true))
            }
            val matchedBy = buildList {
                if (match != null && tripId != null) add("TRIP_ID")
                if (match?.profileUuid != null && profileUuid != null && match.profileUuid.equals(profileUuid, true)) add("PROFILE_UUID")
            }.sorted()
            val orderedStops = trip.stops.sortedBy(TripStop::order)
            BlaBlaAuditReconciledTrip(
                internalTripId = trip.id,
                remoteId = trip.remoteId?.trim()?.takeIf(String::isNotBlank),
                title = trip.title,
                departureAt = iso(trip.departureAtMillis),
                status = trip.status.name,
                recordOrigin = trip.recordOrigin.name,
                externalIdentity = BlaBlaAuditExternalIdentity(
                    profileUuid = profileUuid,
                    tripId = tripId,
                    publicTripHref = trip.blablaPublicUrl?.let(BlaBlaCollectorUrlModule::canonical)?.takeIf(String::isNotBlank),
                ),
                publicationExisting = tripId != null || !trip.blablaPublicUrl.isNullOrBlank(),
                publicBookingEnabled = trip.publicBookingEnabled,
                itineraryAuthoritative = trip.itineraryAuthoritative,
                stops = orderedStops.map { stop ->
                    BlaBlaAuditTripStop(
                        id = stop.id,
                        order = stop.order,
                        name = stop.name,
                        latitude = stop.latitude,
                        longitude = stop.longitude,
                        plannedArrivalAt = stop.plannedArrivalMillis?.let(::iso),
                        plannedDepartureAt = stop.plannedDepartureMillis?.let(::iso),
                        priceToNextMinorUnits = stop.priceToNextCents.takeIf { it > 0 },
                    )
                },
                inventory = BlaBlaAuditInventory(
                    blablaQuotaSeats = operational.blablaQuotaSeats,
                    rotaCertaQuotaSeats = operational.rotaCertaQuotaSeats,
                    operationalInventorySeats = operational.operationalInventorySeats,
                    confirmedPassengerSeats = operational.confirmedPassengerSeats,
                    blockedSeats = operational.blockedSeats,
                    availableSeats = operational.availableSeats,
                    totalAvailableSeats = operational.totalAvailableSeats,
                    overbookingSeats = operational.overbookingSeats,
                    reliable = trip.capacityReliable,
                ),
                segmentAvailability = segments.map { load ->
                    BlaBlaAuditSegmentAvailability(
                        fromStopId = load.from.id,
                        toStopId = load.to.id,
                        occupiedSeats = load.occupiedSeats,
                        passengerSeats = load.passengerSeats,
                        blockedSeats = load.blockedSeats,
                        availableSeats = load.availableSeats,
                        overbookingSeats = load.overbookingSeats,
                    )
                },
                reconciliation = if (match != null) {
                    BlaBlaAuditReconciliation(match.tripId, matchedBy, "CONFIRMED_STRONG_IDENTITY")
                } else BlaBlaAuditReconciliation(state = "NO_STRONG_PUBLIC_MATCH"),
                continuityPositionState = if (
                    orderedStops.firstOrNull()?.name?.isNotBlank() == true &&
                    orderedStops.lastOrNull()?.name?.isNotBlank() == true
                ) "ROUTE_ENDPOINTS_KNOWN" else "UNKNOWN",
            )
        }

        val continuity = reconciledTrips.zipWithNext().map { (previous, next) ->
            val previousDestination = previous.stops.maxByOrNull(BlaBlaAuditTripStop::order)?.name
            val nextOrigin = next.stops.minByOrNull(BlaBlaAuditTripStop::order)?.name
            BlaBlaAuditContinuityEvidence(
                previousTripId = previous.internalTripId,
                previousProfileUuid = previous.externalIdentity.profileUuid,
                previousDestination = previousDestination,
                nextTripId = next.internalTripId,
                nextProfileUuid = next.externalIdentity.profileUuid,
                nextOrigin = nextOrigin,
                state = if (!previousDestination.isNullOrBlank() && !nextOrigin.isNullOrBlank()) "EVIDENCE_ONLY" else "UNKNOWN",
            )
        }

        val expected = dates.size * 2
        val summary = BlaBlaAuditSummary(
            expectedQueries = expected,
            completeQueries = auditQueries.count { it.status == "COMPLETE" },
            partialQueries = auditQueries.count { it.status == "PARTIAL" },
            pendingUnknownQueries = auditQueries.count { it.status == "PENDING_UNKNOWN" },
            failedQueries = auditQueries.count { it.status == "FAILED" },
            publicCardsFound = publicCards.size,
            ownPublicTripsRecognized = publicCards.count { it.ownership.ownership == "CONFIRMED" },
            reconciledLocalTrips = reconciledTrips.size,
            coverageComplete = expected > 0 && auditQueries.size == expected && auditQueries.all { it.status == "COMPLETE" },
        )

        return BlaBlaAuditableCollectionSnapshot(
            generatedAt = iso(response.collectedAtMillis),
            collectorVersion = collectorVersion,
            tenant = BlaBlaAuditTenant(tenant.tenantId),
            period = BlaBlaAuditPeriod(dates.firstOrNull()?.toString().orEmpty(), dates.lastOrNull()?.toString().orEmpty()),
            route = BlaBlaAuditRoute(BlaBlaAuditPlace(request.from), BlaBlaAuditPlace(request.to)),
            queries = auditQueries,
            publicCards = publicCards,
            driverProfiles = driverProfiles,
            reconciledTrips = reconciledTrips,
            continuityEvidence = continuity,
            summary = summary,
        )
    }

    private fun normalizeCoverage(q: BlaBlaPublicSearchQueryResult?): String {
        val explicit = q?.coverageStatus.orEmpty().uppercase(Locale.ROOT)
        if (explicit in FINAL_STATES) return explicit
        return if (q?.status == "validated") "COMPLETE" else "PENDING_UNKNOWN"
    }

    private fun auditError(error: BlaBlaPublicSearchErrorDetail) = BlaBlaAuditError(
        stage = safeText(error.stage).orEmpty().ifBlank { "UNKNOWN" },
        exceptionClass = safeText(error.exceptionClass),
        exceptionMessage = safeText(error.exceptionMessage),
        rootCauseClass = safeText(error.rootCauseClass),
        rootCauseMessage = safeText(error.rootCauseMessage),
    )

    internal fun strongUuid(raw: String?): String? =
        raw?.trim()?.lowercase(Locale.ROOT)?.takeIf(UUID_REGEX::matches)

    internal fun safeText(raw: String?): String? {
        var value = raw?.trim()?.takeIf(String::isNotBlank) ?: return null
        BlaBlaAuditableCollectionJson.forbiddenTerms.forEach { forbidden ->
            value = value.replace(Regex(Regex.escape(forbidden), RegexOption.IGNORE_CASE), "[redacted]")
        }
        return value.take(500)
    }

    private fun iso(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()
    private val FINAL_STATES = setOf("COMPLETE", "PARTIAL", "PENDING_UNKNOWN", "FAILED")
    private val UUID_REGEX = Regex("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
}

object BlaBlaAuditableCollectionJson {
    internal val forbiddenTerms = listOf(
        "password", "cookie", "authorization", "bearer",
        "sessionToken", "refreshToken", "accessToken", "secret",
    )
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    fun encode(snapshot: BlaBlaAuditableCollectionSnapshot): String {
        val encoded = json.encodeToString(snapshot)
        val hits = forbiddenHits(encoded)
        require(hits.isEmpty()) { "Export blocked by forbidden fields: ${hits.joinToString()}" }
        return encoded
    }

    fun decode(raw: String): BlaBlaAuditableCollectionSnapshot {
        val snapshot = json.decodeFromString<BlaBlaAuditableCollectionSnapshot>(raw)
        require(snapshot.schemaVersion in SUPPORTED_SCHEMAS) {
            "Unsupported auditable collection schema: ${snapshot.schemaVersion}"
        }
        return snapshot
    }

    fun forbiddenHits(raw: String): List<String> = forbiddenTerms.filter { raw.contains(it, ignoreCase = true) }
    private val SUPPORTED_SCHEMAS = setOf("1.0")
}

object BlaBlaAuditableCollectionShare {
    const val MIME_TYPE = "application/json"
    const val PROVIDER_SUFFIX = ".tripfiles"
    const val CACHE_DIRECTORY = "trip_calendar"

    fun writeJsonFile(directory: File, snapshot: BlaBlaAuditableCollectionSnapshot): File {
        directory.mkdirs()
        return File(directory, fileName(snapshot)).also {
            it.writeBytes(BlaBlaAuditableCollectionJson.encode(snapshot).toByteArray(Charsets.UTF_8))
        }
    }

    fun share(context: Context, snapshot: BlaBlaAuditableCollectionSnapshot) {
        val file = writeJsonFile(File(context.cacheDir, CACHE_DIRECTORY), snapshot)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}$PROVIDER_SUFFIX", file)
        check(uri.scheme == "content") { "FileProvider must return content:// URI" }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "Rota Certa coleta JSON", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Compartilhar coleta").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun fileName(snapshot: BlaBlaAuditableCollectionSnapshot): String {
        fun safe(raw: String) = raw.replace(Regex("[^A-Za-z0-9._-]"), "-").trim('-').take(60).ifBlank { "coleta" }
        val start = safe(snapshot.period.startDate.ifBlank { "sem-data" })
        val end = safe(snapshot.period.endDate.ifBlank { start })
        return "rota-certa-blablacar-coleta-${start}_${end}.json"
    }
}

@Composable
fun BlaBlaAuditableCollectionActions(
    snapshot: BlaBlaAuditableCollectionSnapshot?,
    onChanged: (String) -> Unit,
) {
    if (snapshot == null) return
    val context = LocalContext.current
    var showSummary by remember(snapshot.generatedAt) { mutableStateOf(false) }
    val downloadPayload = remember(snapshot.generatedAt) { BlaBlaAuditableCollectionJson.encode(snapshot) }
    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BlaBlaAuditableCollectionShare.MIME_TYPE),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")
                ?.bufferedWriter(Charsets.UTF_8)
                ?.use { it.write(downloadPayload) }
                ?: error("Não foi possível abrir o arquivo de destino.")
        }.onSuccess {
            onChanged("Download da coleta concluído.")
        }.onFailure { error ->
            onChanged("Não foi possível baixar a coleta: ${error.message ?: error.javaClass.simpleName}")
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { showSummary = true }, modifier = Modifier.fillMaxWidth()) {
            Text("📄 Ver resumo")
        }
        Button(
            onClick = { downloadLauncher.launch(BlaBlaAuditableCollectionShare.fileName(snapshot)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("⬇️ Baixar coleta") }
    }
    if (showSummary) {
        AlertDialog(
            onDismissRequest = { showSummary = false },
            title = { Text("Resumo da coleta") },
            text = {
                Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("${snapshot.route.origin.name} → ${snapshot.route.destination.name}")
                    Text("Período: ${snapshot.period.startDate} a ${snapshot.period.endDate}")
                    Text("Datas: ${snapshot.queries.map(BlaBlaAuditQuery::date).distinct().size} • consultas previstas: ${snapshot.summary.expectedQueries}")
                    Text("COMPLETE: ${snapshot.summary.completeQueries}")
                    Text("PARTIAL: ${snapshot.summary.partialQueries}")
                    Text("PENDING_UNKNOWN: ${snapshot.summary.pendingUnknownQueries}")
                    Text("FAILED: ${snapshot.summary.failedQueries}")
                    Text("Cards públicos: ${snapshot.summary.publicCardsFound}")
                    Text("Viagens próprias reconhecidas: ${snapshot.summary.ownPublicTripsRecognized}")
                    Text("Viagens locais reconciliadas: ${snapshot.summary.reconciledLocalTrips}")
                    if (!snapshot.summary.coverageComplete) {
                        Text("⚠️ A coleta possui cobertura incompleta e não deve ser usada para concluir que datas sem cards estão livres.")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSummary = false }) { Text("Fechar") } },
        )
    }
}
