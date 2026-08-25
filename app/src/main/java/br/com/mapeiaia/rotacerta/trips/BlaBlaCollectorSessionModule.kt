package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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

    fun saveSync(
        account: BlaBlaDynamicAccount,
        lastUrl: String,
        trips: List<BlaBlaCollectorTrip>,
        skippedTrips: Int,
        identityVerified: Boolean,
    ) {
        withAccountLock(account.id) {
            val previous = readUnlocked(account)
            val authoritativeComplete = identityVerified && skippedTrips == 0
            val merged = BlaBlaCollectorTimelineModule.mergeSnapshotTrips(
                previous = previous?.trips.orEmpty(),
                current = trips,
                authoritativeComplete = authoritativeComplete,
            )
            val preservedVerifiedIdentity =
                !authoritativeComplete &&
                    previous?.identityVerified == true &&
                    previous.profileUuid == account.profileUuid
            val effectiveIdentityVerified = identityVerified || preservedVerifiedIdentity
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
                    skippedTrips = skippedTrips,
                ),
            )
            UnifiedDebugEventStore.record(
                "SNAPSHOT_SAVED",
                appContext.packageName,
                "account=${account.displayLabel} expectedUuid=${account.profileUuid.orEmpty()} trips=${merged.trips.size} rosterComplete=${merged.trips.count { it.passenger_roster_complete }} rosterIncomplete=${merged.trips.count { !it.passenger_roster_complete }} preservedIncomplete=${merged.preservedIncompleteRosters} preservedMissing=${merged.preservedMissingTrips} skipped=$skippedTrips identityVerified=$effectiveIdentityVerified currentIdentityVerified=$identityVerified authoritativeComplete=$authoritativeComplete authority=session_store",
            )
        }
    }

    /** Atomically reconciles possibly stale MHTML evidence with the latest direct snapshot. */
    fun saveHarvestTrips(
        account: BlaBlaDynamicAccount,
        trips: List<BlaBlaCollectorTrip>,
    ): BlaBlaDynamicSessionSnapshot? = withAccountLock(account.id) {
        val latest = readUnlocked(account) ?: return@withAccountLock null
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
            snapshot.identityVerified && !account.profileUuid.isNullOrBlank() && snapshot.profileUuid == account.profileUuid
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
        val skippedCount = snapshots.sumOf { (_, snapshot) -> snapshot.skippedTrips }
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
            strategy = "authenticated_on_device_batch_first_dynamic_multi_profile",
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
            "accounts=${accounts.size} verifiedAccounts=${verified.size} beforeDistinct=${beforeDistinct.size} tripCount=${trips.size} deduped=${resolution.dedupedCount} identityConflicts=$identityConflictCount rosterComplete=${trips.count { it.passenger_roster_complete }} rosterIncomplete=$rosterIncompleteCount skipped=$skippedCount identityStatus=$identityStatus dataCoverage=$dataCoverage status=${response.status}",
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

    private fun file(id: String): File = File(appContext.filesDir, "blablacar-dynamic-session-$id.json")
    private fun diagnosticDir(id: String): File = File(appContext.filesDir, "blablacar-dom/$id")

    private fun <T> withAccountLock(accountId: String, block: () -> T): T =
        synchronized(lockFor(accountId), block)

    private fun lockFor(accountId: String): Any {
        val candidate = Any()
        return accountLocks.putIfAbsent(accountId, candidate) ?: candidate
    }

    companion object {
        private const val MAX_HTML_CHARS = 350_000
        private val accountLocks = ConcurrentHashMap<String, Any>()
    }
}
