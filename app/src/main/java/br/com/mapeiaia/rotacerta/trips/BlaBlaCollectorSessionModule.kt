package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.io.File
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class BlaBlaSourceAccessStatus0426 {
    AVAILABLE,
    TEMPORARILY_RESTRICTED,
}

internal object BlaBlaAcquisitionAuthority0607 {
    const val HTML_DIRECT = "HTML_DIRECT_0607"
    const val LEGACY_NON_HTML = "LEGACY_NON_HTML"
    fun isHtml(value: String?): Boolean = value?.trim() == HTML_DIRECT
}

internal data class BlaBlaHtmlCaptureTransactionState0610(
    val captureId: String,
    val generation: Long,
    val startedAtMillis: Long,
)

internal object BlaBlaHtmlCaptureTransaction0610 {
    private const val PREFS = "rota_certa_html_capture_transaction_0610"
    private const val KEY_CAPTURE_ID = "capture_id"
    private const val KEY_GENERATION = "generation"
    private const val KEY_STARTED_AT = "started_at"
    private const val STALE_AFTER_MILLIS = 15L * 60L * 1000L

    fun begin(context: Context, captureId: String): BlaBlaHtmlCaptureTransactionState0610 {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val generation = maxOf(now, prefs.getLong(KEY_GENERATION, 0L) + 1L)
        require(
            prefs.edit()
                .putString(KEY_CAPTURE_ID, captureId)
                .putLong(KEY_GENERATION, generation)
                .putLong(KEY_STARTED_AT, now)
                .commit(),
        ) { "Falha ao abrir transação HTML." }
        return BlaBlaHtmlCaptureTransactionState0610(captureId, generation, now)
    }

    fun active(context: Context): BlaBlaHtmlCaptureTransactionState0610? {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val captureId = prefs.getString(KEY_CAPTURE_ID, "").orEmpty().trim()
        val generation = prefs.getLong(KEY_GENERATION, 0L)
        val startedAt = prefs.getLong(KEY_STARTED_AT, 0L)
        if (captureId.isBlank() || generation <= 0L || startedAt <= 0L) return null
        if (System.currentTimeMillis() - startedAt > STALE_AFTER_MILLIS) {
            prefs.edit().remove(KEY_CAPTURE_ID).remove(KEY_STARTED_AT).commit()
            return null
        }
        return BlaBlaHtmlCaptureTransactionState0610(captureId, generation, startedAt)
    }

    fun end(context: Context, captureId: String) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_CAPTURE_ID, "").orEmpty() != captureId) return
        require(
            prefs.edit().remove(KEY_CAPTURE_ID).remove(KEY_STARTED_AT).commit(),
        ) { "Falha ao encerrar transação HTML." }
    }
}

internal data class BlaBlaHtmlSessionReplacement0610(
    val account: BlaBlaDynamicAccount,
    val trips: List<BlaBlaCollectorTrip>,
    val lastUrl: String,
)

internal fun effectiveSkippedTrips0610(
    authoritativeComplete: Boolean,
    exactTarget: Boolean,
    dateScoped: Boolean,
    currentSkipped: Int,
    previousSkipped: Int?,
): Int = when {
    authoritativeComplete -> currentSkipped
    exactTarget -> previousSkipped ?: maxOf(currentSkipped, 1)
    !dateScoped -> currentSkipped
    else -> maxOf(currentSkipped, previousSkipped ?: 0)
}

@Serializable
data class BlaBlaDynamicSessionSnapshot(
    val accountId: String,
    val profileUuid: String? = null,
    val profileLabel: String = "",
    val identityVerified: Boolean = false,
    val lastUrl: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val trips: List<BlaBlaCollectorTrip> = emptyList(),
    val skippedTrips: Int = 0,
    val sourceAccessStatus0426: BlaBlaSourceAccessStatus0426 = BlaBlaSourceAccessStatus0426.AVAILABLE,
    val sourceAccessSinceMillis0426: Long = 0L,
    val sourceAccessDetector0426: String = "",
    val sourceAccessIncidentReference0426: String = "",
    val sourceAccessHttpStatus0426: Int = 0,
    val lastValidSyncAtMillis0426: Long = 0L,
    val sourceRestrictionCount0426: Int = 0,
    /** Only HTML_DIRECT_0607 is allowed to feed Agenda/Timeline canonical state. */
    val acquisitionAuthority0607: String = "",
)

internal data class BlaBlaSourceAccessProbe0426(
    val finalUrl: String,
    val title: String = "",
    val bodyText: String = "",
    val httpStatus: Int = 0,
)

internal data class BlaBlaSourceAccessDetection0426(
    val status: BlaBlaSourceAccessStatus0426,
    val detector: String = "",
    val incidentReference: String = "",
    val httpStatus: Int = 0,
) {
    val temporarilyRestricted: Boolean
        get() = status == BlaBlaSourceAccessStatus0426.TEMPORARILY_RESTRICTED
}

internal object BlaBlaSourceAccessDetector0426 {
    private val incidentLabeled = Regex(
        "(?i)(?:incident(?:e)?|reference|refer[eê]ncia)[^0-9a-f]{0,48}" +
            "([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})",
    )
    private val uuidLike = Regex(
        "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b",
    )

    fun detect(probe: BlaBlaSourceAccessProbe0426): BlaBlaSourceAccessDetection0426 {
        val combined = listOf(probe.title, probe.bodyText).joinToString(" ")
        val normalized = normalize(combined)
        val url = probe.finalUrl.lowercase()
        val temporaryPhrase = listOf(
            "acesso esta temporariamente restrito",
            "acesso temporariamente restrito",
            "access is temporarily restricted",
            "access temporarily restricted",
            "temporarily restricted",
            "temporarily blocked",
        ).any(normalized::contains)
        val protectionHints = listOf(
            "javascript",
            "comportamento automatizado",
            "automated behavior",
            "automated traffic",
            "velocidade excessiva",
            "too many requests",
            "cliques",
            "clicks",
            "incident",
            "incidente",
            "reference",
            "referencia",
        ).count(normalized::contains)
        val labeledIncident = incidentLabeled.find(combined)?.groupValues?.getOrNull(1).orEmpty()
        val anyIncident = labeledIncident.ifBlank { uuidLike.find(combined)?.value.orEmpty() }
        val challengeUrl = listOf("/challenge", "/captcha", "/blocked", "/access-denied")
            .any(url::contains)

        val detector = when {
            probe.httpStatus == 429 -> "main_frame_http_429"
            temporaryPhrase && anyIncident.isNotBlank() -> "temporary_restriction_text+incident_reference"
            temporaryPhrase && protectionHints > 0 -> "temporary_restriction_text+protection_context"
            probe.httpStatus == 403 && anyIncident.isNotBlank() && protectionHints > 0 ->
                "main_frame_http_403+incident_reference+protection_context"
            challengeUrl && anyIncident.isNotBlank() -> "challenge_url+incident_reference"
            else -> ""
        }
        return if (detector.isNotBlank()) {
            BlaBlaSourceAccessDetection0426(
                status = BlaBlaSourceAccessStatus0426.TEMPORARILY_RESTRICTED,
                detector = detector,
                incidentReference = anyIncident.take(120),
                httpStatus = probe.httpStatus,
            )
        } else {
            BlaBlaSourceAccessDetection0426(
                status = BlaBlaSourceAccessStatus0426.AVAILABLE,
                httpStatus = probe.httpStatus,
            )
        }
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
}

internal data class BlaBlaExternalFlightLease0426(
    val key: String,
    val token: String,
)

internal data class BlaBlaHarvestSessionMergeResult(
    val trips: List<BlaBlaCollectorTrip>,
    val enrichedTrips: Int,
    val ignoredStaleTrips: Int,
)

/**
 * Pure reconciliation boundary for MHTML enrichment. Harvest data is never an
 * authoritative trip list: it may add evidence to a trip that still exists in
 * the latest direct snapshot, but it cannot remove passengers, resurrect a trip
 * removed by a newer direct sync, or discard a newer trip it never observed.
 */
internal object BlaBlaCollectorSessionModule {
    fun mergeHarvestTrips(
        latest: List<BlaBlaCollectorTrip>,
        harvested: List<BlaBlaCollectorTrip>,
    ): BlaBlaHarvestSessionMergeResult {
        val harvestedByIdentity = harvested.associateBy { BlaBlaTripIdentity.evidence(it).key }.toMutableMap()
        var enrichedTrips = 0
        val merged = latest.map { current ->
            val key = BlaBlaTripIdentity.evidence(current).key
            val enrichment = harvestedByIdentity.remove(key) ?: return@map current
            enrichedTrips++

            // Keep the latest direct trip fields and merge only passenger evidence.
            val passengerEnrichment = current.copy(
                passengers = enrichment.passengers,
                booked_seats = enrichment.booked_seats,
                passenger_roster_complete = false,
            )
            BlaBlaPassengerRosterReconciler.reconcile(current, passengerEnrichment).copy(
                passenger_roster_complete =
                    current.passenger_roster_complete || enrichment.passenger_roster_complete,
            )
        }
        return BlaBlaHarvestSessionMergeResult(
            trips = merged,
            enrichedTrips = enrichedTrips,
            ignoredStaleTrips = harvestedByIdentity.size,
        )
    }
}

/**
 * Single persistence authority for a dynamic collector session.
 *
 * All store instances share the same per-account lock, so every read/modify/write
 * sequence is atomic inside the app process. No collector flow writes the JSON
 * file directly.
 */
class BlaBlaDynamicSessionStore(context: Context) {
    private val appContext = context.applicationContext
    private val tenantScope = RotaCertaTenantRegistry(appContext).activeScope()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun read(account: BlaBlaDynamicAccount): BlaBlaDynamicSessionSnapshot? =
        withAccountLock(account.id) { readUnlocked(account) }

    fun markSeen(account: BlaBlaDynamicAccount, lastUrl: String) {
        withAccountLock(account.id) {
            val previous = readUnlocked(account)
            writeUnlocked(
                account,
                (previous ?: BlaBlaDynamicSessionSnapshot(account.id, account.profileUuid, account.displayLabel)).copy(
                    lastUrl = lastUrl.take(1000),
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun isSourceCircuitOpen0426(account: BlaBlaDynamicAccount): Boolean =
        read(account)?.sourceAccessStatus0426 == BlaBlaSourceAccessStatus0426.TEMPORARILY_RESTRICTED

    internal fun markTemporarilyRestricted0426(
        account: BlaBlaDynamicAccount,
        lastUrl: String,
        detection: BlaBlaSourceAccessDetection0426,
    ): BlaBlaDynamicSessionSnapshot = withAccountLock(account.id) {
        val now = System.currentTimeMillis()
        val previous = readUnlocked(account)
            ?: BlaBlaDynamicSessionSnapshot(
                accountId = account.id,
                profileUuid = account.profileUuid,
                profileLabel = account.displayLabel,
            )
        val replacement = previous.copy(
            profileUuid = account.profileUuid ?: previous.profileUuid,
            profileLabel = account.displayLabel,
            lastUrl = lastUrl.take(1000),
            updatedAtMillis = now,
            sourceAccessStatus0426 = BlaBlaSourceAccessStatus0426.TEMPORARILY_RESTRICTED,
            sourceAccessSinceMillis0426 = previous.sourceAccessSinceMillis0426
                .takeIf {
                    previous.sourceAccessStatus0426 == BlaBlaSourceAccessStatus0426.TEMPORARILY_RESTRICTED &&
                        it > 0L
                } ?: now,
            sourceAccessDetector0426 = detection.detector.take(160),
            sourceAccessIncidentReference0426 = detection.incidentReference.take(120),
            sourceAccessHttpStatus0426 = detection.httpStatus.coerceAtLeast(0),
            sourceRestrictionCount0426 = previous.sourceRestrictionCount0426 + 1,
        )
        writeUnlocked(account, replacement)
        replacement
    }

    fun markSourceAvailable0426(
        account: BlaBlaDynamicAccount,
        lastUrl: String,
    ): BlaBlaDynamicSessionSnapshot? = withAccountLock(account.id) {
        val previous = readUnlocked(account) ?: return@withAccountLock null
        if (previous.sourceAccessStatus0426 == BlaBlaSourceAccessStatus0426.AVAILABLE) return@withAccountLock previous
        val replacement = previous.copy(
            lastUrl = lastUrl.take(1000),
            updatedAtMillis = System.currentTimeMillis(),
            sourceAccessStatus0426 = BlaBlaSourceAccessStatus0426.AVAILABLE,
            sourceAccessSinceMillis0426 = 0L,
            sourceAccessDetector0426 = "",
            sourceAccessIncidentReference0426 = "",
            sourceAccessHttpStatus0426 = 0,
        )
        writeUnlocked(account, replacement)
        replacement
    }

    internal fun tryAcquireExternalFlight0426(
        account: BlaBlaDynamicAccount,
        token: String,
    ): BlaBlaExternalFlightLease0426? {
        val normalizedToken = token.trim().takeIf(String::isNotEmpty) ?: return null
        val identity = account.profileUuid?.trim()?.lowercase()?.takeIf(String::isNotEmpty)
            ?.let { "profile:" + it }
            ?: "account:" + account.id.trim()
        val key = tenantScope.tenantId.trim() + "|" + identity
        val existing = externalFlightOwners0426.putIfAbsent(key, normalizedToken)
        return if (existing == null || existing == normalizedToken) {
            BlaBlaExternalFlightLease0426(key, normalizedToken)
        } else {
            null
        }
    }

    internal fun releaseExternalFlight0426(lease: BlaBlaExternalFlightLease0426?) {
        lease ?: return
        externalFlightOwners0426.remove(lease.key, lease.token)
    }

    fun hasConcurrentExternalFlight0426(account: BlaBlaDynamicAccount): Boolean {
        val identity = account.profileUuid?.trim()?.lowercase()?.takeIf(String::isNotEmpty)
            ?.let { "profile:" + it }
            ?: "account:" + account.id.trim()
        return externalFlightOwners0426.containsKey(tenantScope.tenantId.trim() + "|" + identity)
    }

    fun saveSync(
        account: BlaBlaDynamicAccount,
        lastUrl: String,
        trips: List<BlaBlaCollectorTrip>,
        skippedTrips: Int,
        identityVerified: Boolean,
        dateScope: Collection<LocalDate>? = null,
        targetedTripId: String? = null,
        selectiveScriptSync0449: Boolean = false,
        acquisitionAuthority0607: String = BlaBlaAcquisitionAuthority0607.LEGACY_NON_HTML,
    ) {
        withAccountLock(account.id) {
            val previous = readUnlocked(account)
            if (
                previous != null &&
                previous.acquisitionAuthority0607 == BlaBlaAcquisitionAuthority0607.HTML_DIRECT &&
                acquisitionAuthority0607 != BlaBlaAcquisitionAuthority0607.HTML_DIRECT
            ) {
                UnifiedDebugEventStore.recordAlways(
                    "LEGACY_SESSION_WRITE_BLOCKED_0607",
                    appContext.packageName,
                    "accountKey=${seatSyncDiagnosticKey(account.id)} existingAuthority=HTML_DIRECT_0607 incomingAuthority=$acquisitionAuthority0607 action=PRESERVE_HTML_SNAPSHOT",
                )
                return@withAccountLock
            }
            val exactTargetId = targetedTripId?.trim()?.takeIf(String::isNotEmpty)
            val dateScopeKeys = dateScope
                ?.map(LocalDate::toString)
                ?.distinct()
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }
            val dateScopedTrips = dateScopeKeys?.let { keys -> trips.filter { it.date in keys } } ?: trips
            val scopedTrips = exactTargetId?.let { wanted ->
                dateScopedTrips.filter { trip ->
                    trip.trip_id?.trim() == wanted &&
                        BlaBlaCollectorUrlModule.tripId(trip.trip_href.orEmpty()) == wanted
                }.takeIf { it.size == 1 }.orEmpty()
            } ?: dateScopedTrips
            val droppedOutOfScope = trips.size - scopedTrips.size
            // A targeted trip read is never authoritative for the account universe.
            // It may replace that exact strong identity, but missing sibling cards are preserved.
            val authoritativeComplete =
                identityVerified && skippedTrips == 0 && exactTargetId == null && !selectiveScriptSync0449
            val sameAuthorityPrevious = previous
                ?.takeIf { it.acquisitionAuthority0607 == acquisitionAuthority0607 }
            val targetedHtmlReplace0615 =
                acquisitionAuthority0607 == BlaBlaAcquisitionAuthority0607.HTML_DIRECT && exactTargetId != null
            if (targetedHtmlReplace0615) {
                val exact = scopedTrips.singleOrNull()
                val operationallyComplete =
                    exact != null &&
                        exact.trip_id?.trim() == exactTargetId &&
                        BlaBlaCollectorUrlModule.tripId(exact.trip_href.orEmpty()) == exactTargetId &&
                        exact.passenger_roster_complete &&
                        exact.itinerary_authoritative &&
                        exact.published_seats != null &&
                        !exact.public_trip_href.isNullOrBlank()
                if (!identityVerified || skippedTrips != 0 || !operationallyComplete) {
                    UnifiedDebugEventStore.recordAlways(
                        "TARGETED_HTML_SESSION_WRITE_BLOCKED_0615",
                        appContext.packageName,
                        "accountKey=${seatSyncDiagnosticKey(account.id)} targetTripIdPresent=true identityVerified=$identityVerified skipped=$skippedTrips scopedTrips=${scopedTrips.size} operationallyComplete=$operationallyComplete action=PRESERVE_LAST_VALIDATED_HTML",
                    )
                    return@withAccountLock
                }
            }
            val merged = if (targetedHtmlReplace0615) {
                BlaBlaCollectorTimelineModule.replaceAuthoritativeHtmlTarget0615(
                    previous = sameAuthorityPrevious?.trips.orEmpty(),
                    current = scopedTrips,
                    targetTripId = requireNotNull(exactTargetId),
                )
            } else {
                BlaBlaCollectorTimelineModule.mergeSnapshotTrips(
                    previous = sameAuthorityPrevious?.trips.orEmpty(),
                    current = scopedTrips,
                    authoritativeComplete = authoritativeComplete,
                    authoritativeDateScope = dateScopeKeys,
                )
            }
            val preservedVerifiedIdentity =
                !authoritativeComplete &&
                    sameAuthorityPrevious?.identityVerified == true &&
                    sameAuthorityPrevious.profileUuid == account.profileUuid
            val effectiveIdentityVerified = identityVerified || preservedVerifiedIdentity
            val effectiveSkippedTrips = effectiveSkippedTrips0610(
                authoritativeComplete = authoritativeComplete,
                exactTarget = exactTargetId != null,
                dateScoped = dateScopeKeys != null,
                currentSkipped = skippedTrips,
                previousSkipped = sameAuthorityPrevious?.skippedTrips,
            )
            writeUnlocked(
                account,
                BlaBlaDynamicSessionSnapshot(
                    accountId = account.id,
                    profileUuid = account.profileUuid,
                    profileLabel = account.displayLabel,
                    identityVerified = effectiveIdentityVerified,
                    lastUrl = lastUrl.take(1000),
                    updatedAtMillis = System.currentTimeMillis(),
                    trips = merged.trips,
                    skippedTrips = effectiveSkippedTrips,
                    sourceAccessStatus0426 = if (authoritativeComplete) {
                        BlaBlaSourceAccessStatus0426.AVAILABLE
                    } else {
                        sameAuthorityPrevious?.sourceAccessStatus0426 ?: BlaBlaSourceAccessStatus0426.AVAILABLE
                    },
                    sourceAccessSinceMillis0426 = if (authoritativeComplete) 0L else sameAuthorityPrevious?.sourceAccessSinceMillis0426 ?: 0L,
                    sourceAccessDetector0426 = if (authoritativeComplete) "" else sameAuthorityPrevious?.sourceAccessDetector0426.orEmpty(),
                    sourceAccessIncidentReference0426 = if (authoritativeComplete) "" else sameAuthorityPrevious?.sourceAccessIncidentReference0426.orEmpty(),
                    sourceAccessHttpStatus0426 = if (authoritativeComplete) 0 else sameAuthorityPrevious?.sourceAccessHttpStatus0426 ?: 0,
                    lastValidSyncAtMillis0426 = if (authoritativeComplete) {
                        System.currentTimeMillis()
                    } else {
                        sameAuthorityPrevious?.lastValidSyncAtMillis0426 ?: 0L
                    },
                    sourceRestrictionCount0426 = sameAuthorityPrevious?.sourceRestrictionCount0426 ?: 0,
                    acquisitionAuthority0607 = acquisitionAuthority0607,
                ),
            )
            UnifiedDebugEventStore.record(
                "SNAPSHOT_SAVED",
                appContext.packageName,
                "account=${account.displayLabel} expectedUuid=${account.profileUuid.orEmpty()} trips=${merged.trips.size} rosterComplete=${merged.trips.count { it.passenger_roster_complete }} rosterIncomplete=${merged.trips.count { !it.passenger_roster_complete }} preservedIncomplete=${merged.preservedIncompleteRosters} preservedMissing=${merged.preservedMissingTrips} skipped=$effectiveSkippedTrips currentSkipped=$skippedTrips identityVerified=$effectiveIdentityVerified currentIdentityVerified=$identityVerified authoritativeComplete=$authoritativeComplete targetedHtmlStrictReplace0615=$targetedHtmlReplace0615 selectiveScriptSync0449=$selectiveScriptSync0449 targeted=${exactTargetId != null} targetTripIdPresent=${exactTargetId != null} dateScope=${dateScopeKeys?.sorted()?.joinToString(",") ?: "all"} droppedOutOfScope=$droppedOutOfScope authority=session_store",
            )
        }
    }

    /** Atomically reconciles possibly stale MHTML evidence with the latest direct snapshot. */
    fun saveHarvestTrips(
        account: BlaBlaDynamicAccount,
        trips: List<BlaBlaCollectorTrip>,
    ): BlaBlaDynamicSessionSnapshot? = withAccountLock(account.id) {
        val latest = readUnlocked(account) ?: return@withAccountLock null
        if (latest.acquisitionAuthority0607 == BlaBlaAcquisitionAuthority0607.HTML_DIRECT) {
            UnifiedDebugEventStore.recordAlways(
                "LEGACY_HARVEST_BLOCKED_0607",
                appContext.packageName,
                "accountKey=${seatSyncDiagnosticKey(account.id)} existingAuthority=HTML_DIRECT_0607 action=PRESERVE_HTML_SNAPSHOT",
            )
            return@withAccountLock latest
        }
        val merged = BlaBlaCollectorSessionModule.mergeHarvestTrips(latest.trips, trips)
        val replacement = latest.copy(
            updatedAtMillis = System.currentTimeMillis(),
            trips = merged.trips,
        )
        writeUnlocked(account, replacement)
        UnifiedDebugEventStore.record(
            "SNAPSHOT_SAVED",
            appContext.packageName,
            "account=${account.displayLabel} expectedUuid=${account.profileUuid.orEmpty()} trips=${replacement.trips.size} rosterComplete=${replacement.trips.count { it.passenger_roster_complete }} rosterIncomplete=${replacement.trips.count { !it.passenger_roster_complete }} skipped=${replacement.skippedTrips} identityVerified=${replacement.identityVerified} deterministicHarvest=true enrichedLatest=${merged.enrichedTrips} ignoredStale=${merged.ignoredStaleTrips} authority=session_store",
        )
        replacement
    }

    internal fun replaceHtmlSnapshotsAtomically0610(
        replacements: List<BlaBlaHtmlSessionReplacement0610>,
    ): BlaBlaCollectorMonthResponse = synchronized(htmlCommitLock0610) {
        val now = System.currentTimeMillis()
        replacements.forEach { replacement ->
            val account = replacement.account
            withAccountLock(account.id) {
                val previous = readUnlocked(account)
                val snapshot = BlaBlaDynamicSessionSnapshot(
                    accountId = account.id,
                    profileUuid = account.profileUuid,
                    profileLabel = account.displayLabel,
                    identityVerified = true,
                    lastUrl = replacement.lastUrl.take(1000),
                    updatedAtMillis = now,
                    trips = replacement.trips,
                    skippedTrips = 0,
                    sourceAccessStatus0426 = BlaBlaSourceAccessStatus0426.AVAILABLE,
                    sourceAccessSinceMillis0426 = 0L,
                    sourceAccessDetector0426 = "",
                    sourceAccessIncidentReference0426 = "",
                    sourceAccessHttpStatus0426 = 0,
                    lastValidSyncAtMillis0426 = now,
                    sourceRestrictionCount0426 = previous?.sourceRestrictionCount0426 ?: 0,
                    acquisitionAuthority0607 = BlaBlaAcquisitionAuthority0607.HTML_DIRECT,
                )
                writeUnlocked(account, snapshot)
                UnifiedDebugEventStore.recordAlways(
                    "HTML_SESSION_ATOMIC_REPLACED_0610",
                    appContext.packageName,
                    "accountKey=${seatSyncDiagnosticKey(account.id)} trips=${replacement.trips.size} skipped=0 authoritativeComplete=true authority=HTML_DIRECT_0607",
                )
            }
        }
        combinedResponse(replacements.map(BlaBlaHtmlSessionReplacement0610::account))
    }

    fun clearTripsPreservingSessions(accounts: List<BlaBlaDynamicAccount>): Pair<Int, Int> {
        var accountsTouched = 0
        var tripsRemoved = 0
        accounts.forEach { account ->
            withAccountLock(account.id) {
                val previous = readUnlocked(account) ?: return@withAccountLock
                tripsRemoved += previous.trips.size
                if (previous.trips.isNotEmpty() || previous.skippedTrips != 0) {
                    accountsTouched++
                    writeUnlocked(
                        account,
                        previous.copy(
                            updatedAtMillis = System.currentTimeMillis(),
                            trips = emptyList(),
                            skippedTrips = 0,
                        ),
                    )
                }
            }
        }
        UnifiedDebugEventStore.record(
            "TIMELINE_SESSION_SNAPSHOTS_CLEARED",
            appContext.packageName,
            "accounts=${accounts.size} accountsTouched=$accountsTouched tripsRemoved=$tripsRemoved identityPreserved=true loginPreserved=true",
        )
        return accountsTouched to tripsRemoved
    }

    fun combinedResponse(accounts: List<BlaBlaDynamicAccount>): BlaBlaCollectorMonthResponse {
        val snapshots = accounts.mapNotNull { account -> read(account)?.let { account to it } }
        val verified = snapshots.filter { (account, snapshot) ->
            snapshot.identityVerified &&
                !account.profileUuid.isNullOrBlank() &&
                snapshot.profileUuid == account.profileUuid &&
                BlaBlaAcquisitionAuthority0607.isHtml(snapshot.acquisitionAuthority0607)
        }
        val legacyBlocked0607 = snapshots.count { (_, snapshot) ->
            !BlaBlaAcquisitionAuthority0607.isHtml(snapshot.acquisitionAuthority0607)
        }
        val beforeDistinct = verified.flatMap { (_, snapshot) -> snapshot.trips }
        val resolution = BlaBlaTripIdentity.resolveDistinct(beforeDistinct)
        beforeDistinct.forEachIndexed { index, trip ->
            val identity = BlaBlaTripIdentity.evidence(trip)
            UnifiedDebugEventStore.record(
                "TRIP_IDENTITY",
                appContext.packageName,
                "index=${index + 1}/${beforeDistinct.size} tripId=${trip.trip_id.orEmpty()} core=${BlaBlaTripIdentity.physicalCoreKey(trip)} externalTripIdPresent=${identity.externalTripIdPresent} specificHrefPresent=${identity.specificHrefPresent} fallbackIdentityUsed=${identity.fallbackIdentityUsed} identityHash=${identity.identityHash} identityConflict=${identity.identityConflict}",
            )
        }
        resolution.conflicts.forEach { conflict ->
            UnifiedDebugEventStore.record(
                "TRIP_IDENTITY_CONFLICT",
                appContext.packageName,
                "identityHash=${conflict.identityHash} tripId=${conflict.externalTripId.orEmpty()} physicalCoreCount=${conflict.physicalCores.size} cores=${conflict.physicalCores.joinToString(" || ")} action=preserve_and_flag",
            )
        }
        val trips = resolution.trips
            .sortedWith(compareBy<BlaBlaCollectorTrip>({ it.date }, { it.departure_time.orEmpty() }))
        val identityConflictCount = resolution.conflicts.size
        val hasIdentityConflict = identityConflictCount > 0
        val skippedCount = verified.sumOf { (_, snapshot) -> snapshot.skippedTrips }
        val rosterIncompleteCount = trips.count { !it.passenger_roster_complete }
        val dataCoveragePartial = rosterIncompleteCount > 0 || skippedCount > 0
        val identityStatus = when {
            accounts.isEmpty() -> "empty"
            verified.size == accounts.size -> "validated"
            verified.isEmpty() -> "blocked"
            else -> "partial"
        }
        val dataCoverage = if (!dataCoveragePartial && !hasIdentityConflict) "complete" else "partial"
        val response = BlaBlaCollectorMonthResponse(
            collected_at = Instant.now().toString(),
            status = blaBlaDirectCollectorStatus(
                accountCount = accounts.size,
                verifiedAccountCount = verified.size,
                identityConflictCount = identityConflictCount,
                rosterIncompleteCount = rosterIncompleteCount,
                skippedCount = skippedCount,
            ),
            month = null,
            strategy = "html_direct_authority_0607",
            authority_source_0607 = BlaBlaAcquisitionAuthority0607.HTML_DIRECT,
            profiles = verified.map { (account, _) ->
                BlaBlaCollectorProfile(
                    uuid = account.profileUuid.orEmpty(),
                    name = account.displayLabel,
                    title = "Sessão autenticada local • perfil WebView isolado • UUID confirmado",
                )
            },
            trips = trips,
            coverage = BlaBlaCollectorCoverage(
                complete_for_scope = blaBlaDirectCoverageComplete(
                    accountCount = accounts.size,
                    verifiedAccountCount = verified.size,
                    identityConflictCount = identityConflictCount,
                    rosterIncompleteCount = rosterIncompleteCount,
                    skippedCount = skippedCount,
                ),
                global_profile_month_complete = false,
                reason = when {
                    hasIdentityConflict -> "Conflito de identidade externa detectado; viagens preservadas para conferência sem descarte silencioso."
                    dataCoveragePartial -> "Identidade das contas validada, mas a cobertura dos dados está parcial por roster incompleto ou cartão não resolvido."
                    else -> "Contas cadastradas pelo usuário; leitura autenticada local de Suas viagens com UUID confirmado."
                },
                requested_queries = accounts.size,
                validated_queries = verified.size,
                failed_or_mismatched_queries = (accounts.size - verified.size).coerceAtLeast(0),
                unresolved_target_cards = skippedCount,
                identity_conflicts = resolution.conflicts.size,
                past_dates_skipped = false,
            ),
        )
        UnifiedDebugEventStore.record(
            "COMBINED_RESPONSE",
            appContext.packageName,
            "accounts=${accounts.size} verifiedAccounts=${verified.size} legacyBlocked0607=$legacyBlocked0607 beforeDistinct=${beforeDistinct.size} tripCount=${trips.size} deduped=${resolution.dedupedCount} identityConflicts=$identityConflictCount rosterComplete=${trips.count { it.passenger_roster_complete }} rosterIncomplete=$rosterIncompleteCount skipped=$skippedCount identityStatus=$identityStatus dataCoverage=$dataCoverage status=${response.status}",
        )
        return response
    }

    fun delete(account: BlaBlaDynamicAccount) {
        withAccountLock(account.id) {
            file(account.id).delete()
            diagnosticDir(account.id).deleteRecursively()
        }
    }

    fun saveDiagnosticHtml(account: BlaBlaDynamicAccount, kind: String, html: String) {
        if (html.isBlank()) return
        withAccountLock(account.id) {
            val dir = diagnosticDir(account.id).apply { mkdirs() }
            File(dir, "$kind-latest.html").writeText(html.take(MAX_HTML_CHARS), Charsets.UTF_8)
        }
    }

    private fun readUnlocked(account: BlaBlaDynamicAccount): BlaBlaDynamicSessionSnapshot? = runCatching {
        val target = file(account.id)
        if (!target.isFile) null else json.decodeFromString<BlaBlaDynamicSessionSnapshot>(target.readText(Charsets.UTF_8))
    }.getOrNull()?.takeIf { it.accountId == account.id }

    private fun writeUnlocked(account: BlaBlaDynamicAccount, snapshot: BlaBlaDynamicSessionSnapshot) {
        val target = file(account.id)
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeText(json.encodeToString(snapshot), Charsets.UTF_8)
        if (!temp.renameTo(target)) {
            target.writeText(temp.readText(Charsets.UTF_8), Charsets.UTF_8)
            temp.delete()
        }
    }

    private fun file(id: String): File =
        File(appContext.filesDir, tenantScope.keyAlias("blablacar-dynamic-session-$id") + ".json")

    private fun diagnosticDir(id: String): File =
        File(appContext.filesDir, "blablacar-dom/${tenantScope.keyAlias(id)}")

    private fun <T> withAccountLock(accountId: String, block: () -> T): T =
        synchronized(lockFor(accountId), block)

    private fun lockFor(accountId: String): Any {
        val candidate = Any()
        return accountLocks.putIfAbsent(accountId, candidate) ?: candidate
    }

    companion object {
        private const val MAX_HTML_CHARS = 350_000
        private val accountLocks = ConcurrentHashMap<String, Any>()
        private val externalFlightOwners0426 = ConcurrentHashMap<String, String>()
        private val htmlCommitLock0610 = Any()
    }
}
