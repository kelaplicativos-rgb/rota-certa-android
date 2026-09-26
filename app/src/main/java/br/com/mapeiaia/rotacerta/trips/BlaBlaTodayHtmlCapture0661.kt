package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import br.com.mapeiaia.rotacerta.SettingsRepository
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal data class BlaBlaTodayHtmlCaptureResult0661(
    val captureId: String,
    val targetDate: LocalDate,
    val totalAccounts: Int,
    val completeAccounts: Int,
    val failedAccounts: Int,
    val discoveredTrips: Int,
    val capturedTrips: Int,
    val completeTrips: Int,
    val incompleteTrips: Int,
    val canonicalCommitAccepted: Boolean,
    val manifestResult: String,
)

/**
 * Direct HTML-only TODAY_ONLY coordinator for Central do Dia.
 *
 * It deliberately reuses the same acquisition classes as the global HTML button and never
 * enters the legacy account-sync/controller path.
 */
internal object BlaBlaTodayHtmlCaptureCoordinator0661 {
    suspend fun capture(
        context: Context,
        targetDate: LocalDate,
        onProgress: (String) -> Unit = {},
    ): BlaBlaTodayHtmlCaptureResult0661 {
        val app = context.applicationContext
        val registry = BlaBlaDynamicAccountRegistry(app)
        val store = BlaBlaRidesSnapshotStore0526(app)
        val accounts = registry.list()
        var manifest = store.start(accounts)
        UnifiedDebugEventStore.recordAlways(
            "CENTRAL_TODAY_HTML_CAPTURE_STARTED_0661",
            app.packageName,
            "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(manifest.captureId)} " +
                "scope=TODAY_ONLY targetDate=$targetDate profiles=${accounts.size} " +
                "authority=HTML_DIRECT_0607 legacySync=false modeSync=false",
        )
        if (accounts.isEmpty()) {
            return BlaBlaTodayHtmlCaptureResult0661(
                captureId = manifest.captureId, targetDate = targetDate, totalAccounts = 0,
                completeAccounts = 0, failedAccounts = 0, discoveredTrips = 0, capturedTrips = 0,
                completeTrips = 0, incompleteTrips = 0, canonicalCommitAccepted = true,
                manifestResult = "EMPTY_ACCOUNTS",
            )
        }

        val transaction = BlaBlaHtmlCaptureTransaction0610.begin(app, manifest.captureId)
        val stagedByAccount0661 = linkedMapOf<String, BlaBlaUnifiedProfileCaptureResult0605>()
        val accountResults0661 = mutableListOf<Pair<BlaBlaDynamicAccount, BlaBlaDirectAccountCaptureResult0608>>()
        return try {
            accounts.forEachIndexed { index0661, account0661 ->
                onProgress("HTML de hoje • perfil ${index0661 + 1}/${accounts.size} • ${account0661.displayLabel}")
                UnifiedDebugEventStore.recordAlways(
                    "CENTRAL_TODAY_HTML_PROFILE_SERIAL_0661",
                    app.packageName,
                    "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(manifest.captureId)} " +
                        "scope=TODAY_ONLY targetDate=$targetDate profile=${index0661 + 1}/${accounts.size} " +
                        "accountKey=${store.accountKey(account0661.id)}",
                )
                val result0661 = BlaBlaDirectAccountCapture0608.capture(
                    context = app, store = store, account = account0661, captureId = manifest.captureId,
                    onProgress = onProgress, targetDate0661 = targetDate,
                )
                accountResults0661 += account0661 to result0661
                result0661.privateStage0610?.let { stagedByAccount0661[account0661.id] = it }
            }

            manifest = store.read(manifest.captureId) ?: manifest
            val finalized0661 = store.finish(manifest.captureId) ?: manifest
            val commitAccepted0661 = commitPresenceOnly(
                context = app, accounts = accounts, captureId = manifest.captureId,
                targetDate = targetDate, stagedByAccount = stagedByAccount0661,
            )
            val completeAccounts0661 = finalized0661.profiles.count {
                it.status == BlaBlaRidesSnapshotStatus0526.COMPLETE
            }
            val failedAccounts0661 = finalized0661.profiles.size - completeAccounts0661
            val discovered0661 = accountResults0661.sumOf { it.second.futureTrips }
            val captured0661 = accountResults0661.sumOf { it.second.capturedTrips }
            val completeTrips0661 = accountResults0661.sumOf { it.second.completeTrips }
            val incompleteTrips0661 = accountResults0661.sumOf { it.second.incompleteTrips }
            UnifiedDebugEventStore.recordAlways(
                "CENTRAL_TODAY_HTML_CAPTURE_COMPLETE_0661",
                app.packageName,
                "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(manifest.captureId)} " +
                    "scope=TODAY_ONLY targetDate=$targetDate profilesComplete=$completeAccounts0661/${accounts.size} " +
                    "discovered=$discovered0661 captured=$captured0661 completeTrips=$completeTrips0661 " +
                    "incompleteTrips=$incompleteTrips0661 canonicalCommit=$commitAccepted0661 " +
                    "authority=${BlaBlaAcquisitionAuthority0607.HTML_DIRECT} legacySync=false modeSync=false fullTraversal=false",
            )
            BlaBlaTodayHtmlCaptureResult0661(
                captureId = manifest.captureId, targetDate = targetDate, totalAccounts = accounts.size,
                completeAccounts = completeAccounts0661, failedAccounts = failedAccounts0661,
                discoveredTrips = discovered0661, capturedTrips = captured0661,
                completeTrips = completeTrips0661, incompleteTrips = incompleteTrips0661,
                canonicalCommitAccepted = commitAccepted0661, manifestResult = finalized0661.result,
            )
        } finally {
            BlaBlaHtmlCaptureTransaction0610.end(app, manifest.captureId)
            UnifiedDebugEventStore.recordAlways(
                "CENTRAL_TODAY_HTML_TRANSACTION_FINISHED_0661",
                app.packageName,
                "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(manifest.captureId)} " +
                    "generation=${transaction.generation} scope=TODAY_ONLY activeAfter=false",
            )
        }
    }

    private suspend fun commitPresenceOnly(
        context: Context,
        accounts: List<BlaBlaDynamicAccount>,
        captureId: String,
        targetDate: LocalDate,
        stagedByAccount: Map<String, BlaBlaUnifiedProfileCaptureResult0605>,
    ): Boolean = withContext(Dispatchers.IO) {
        val transaction0661 = BlaBlaHtmlCaptureTransaction0610.active(context)
            ?.takeIf { it.captureId == captureId } ?: return@withContext false
        if (stagedByAccount.size != accounts.size || stagedByAccount.values.any { it.incompleteTrips != 0 }) {
            UnifiedDebugEventStore.recordAlways(
                "CENTRAL_TODAY_HTML_FINALIZER_PARTIAL_0661", context.packageName,
                "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(captureId)} " +
                    "targetDate=$targetDate stagedProfiles=${stagedByAccount.size}/${accounts.size} " +
                    "action=PRESERVE_LIVE_COMMITS preserveSiblings=true tombstone=false",
            )
            return@withContext false
        }
        val accountById0661 = accounts.associateBy(BlaBlaDynamicAccount::id)
        val stagedPairs0661 = stagedByAccount.flatMap { (accountId0661, result0661) ->
            val account0661 = accountById0661[accountId0661] ?: return@flatMap emptyList()
            result0661.stagedTrips0610.map { trip0661 -> account0661 to trip0661 }
        }
        if (stagedPairs0661.any { (_, trip0661) ->
                runCatching { LocalDate.parse(trip0661.date) }.getOrNull() != targetDate
            }) {
            UnifiedDebugEventStore.recordAlways(
                "CENTRAL_TODAY_HTML_SCOPE_LEAK_BLOCKED_0661", context.packageName,
                "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(captureId)} targetDate=$targetDate " +
                    "staged=${stagedPairs0661.size} action=BLOCK_OUT_OF_SCOPE preserveSiblings=true tombstone=false",
            )
            return@withContext false
        }

        val response0661 = BlaBlaCollectorMonthResponse(
            collected_at = Instant.now().toString(),
            status = "validated",
            strategy = "html_today_scope_0661",
            authority_source_0607 = BlaBlaAcquisitionAuthority0607.HTML_DIRECT,
            profiles = accounts.mapNotNull { account0661 ->
                val uuid0661 = account0661.profileUuid?.trim()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                BlaBlaCollectorProfile(uuid = uuid0661, name = account0661.displayLabel, title = "HTML direto • hoje")
            },
            trips = stagedPairs0661.map { it.second },
            coverage = BlaBlaCollectorCoverage(
                complete_for_scope = false, global_profile_month_complete = false,
                reason = "html_today_scope_presence_only_0661", requested_queries = stagedPairs0661.size,
                validated_queries = stagedPairs0661.size, failed_or_mismatched_queries = 0,
                unresolved_target_cards = 0, past_dates_skipped = true,
            ),
        )
        val settings0661 = SettingsRepository(context).settings.first()
        val tripStore0661 = TripStore(context)
        val batch0661 = AgendaBackgroundSync0392.reconcileCollectedExternalTrips0403(
            context = context, store = tripStore0661, response = response0661,
            rotaCertaSeatAllocation = settings0661.rotaCertaSeatAllocation,
            seatAllocationVersion = settings0661.rotaCertaSeatAllocationVersion,
            collectionRunId = "html-today-0661:" + captureId.take(48),
            collectionGeneration = transaction0661.generation, completeProfileUuids = emptySet(),
            htmlTransactionCaptureId0610 = captureId, evaluateAbsentTrips0618 = false,
            skipPresentAlreadyCommittedGeneration0618 = true,
        )
        if (batch0661.blockedTrips != 0 || batch0661.staleResultsRejected != 0) return@withContext false

        val sessionStore0661 = BlaBlaDynamicSessionStore(context)
        stagedPairs0661.forEach { (account0661, trip0661) ->
            val tripId0661 = trip0661.trip_id?.trim().orEmpty()
            if (tripId0661.isNotBlank()) {
                sessionStore0661.saveSync(
                    account = account0661,
                    lastUrl = stagedByAccount[account0661.id]?.stagedLastUrl0610.orEmpty(),
                    trips = listOf(trip0661), skippedTrips = 0, identityVerified = true,
                    targetedTripId = tripId0661, selectiveScriptSync0449 = false,
                    acquisitionAuthority0607 = BlaBlaAcquisitionAuthority0607.HTML_DIRECT,
                )
            }
        }
        BlaBlaCollectorStateStore(context).saveResponse(
            response = sessionStore0661.combinedResponse(BlaBlaDynamicAccountRegistry(context).list()),
            preserveOnPartial = true,
        )
        if (batch0661.publicationCanonicalTripIds0431.isNotEmpty()) {
            runCatching {
                TripMutationCoordinator0387(context, tripStore0661).drainPending(
                    limit = batch0661.publicationCanonicalTripIds0431.size.coerceAtLeast(1),
                    canonicalTripIds = batch0661.publicationCanonicalTripIds0431,
                )
            }
        }
        BookingRealtimeEvents0356.notifyChanged()
        TripWidgetProvider.updateAll(context)
        UnifiedDebugEventStore.recordAlways(
            "CENTRAL_TODAY_HTML_CANONICAL_COMMITTED_0661", context.packageName,
            "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(captureId)} targetDate=$targetDate " +
                "trips=${stagedPairs0661.size} changed=${batch0661.changedTrips} unchanged=${batch0661.skippedTrips} " +
                "authority=HTML_DIRECT_0607 preserveSiblings=true tombstone=false " +
                "evaluateAbsence=false legacySync=false modeSync=false",
        )
        true
    }
}
