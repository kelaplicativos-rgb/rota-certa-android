package br.com.mapeiaia.rotacerta.trips

import android.app.Activity
import android.content.Context
import android.content.Intent
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object BlaBlaCollectorTimelineEvents0400 {
    private val counter = AtomicLong(0L)
    private val mutableRevision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = mutableRevision.asStateFlow()

    fun notifyChanged() {
        mutableRevision.value = counter.incrementAndGet()
    }
}

internal fun nextAutomaticCollectorAccountId0400(
    targetAccountIds: List<String>,
    completedAccountIds: Set<String>,
    failedAccountIds: Set<String>,
): String? = targetAccountIds.firstOrNull { id ->
    id.isNotBlank() && id !in completedAccountIds && id !in failedAccountIds
}

internal fun automaticCollectorTerminalStatus0400(
    response: BlaBlaCollectorMonthResponse,
    failedAccounts: Int,
    targetAccounts: Int,
): String = when {
    targetAccounts <= 0 -> "NO_ACCOUNTS"
    failedAccounts == 0 && response.coverage.complete_for_scope && response.status in setOf("success", "validated") -> "COMPLETE"
    response.trips.isNotEmpty() -> "PARTIAL"
    else -> "FAILED"
}

/**
 * Central automatic-sync coordinator.
 *
 * It does not scrape or parse anything. It only schedules the already-existing
 * BlaBlaDynamicAccountSessionActivity collector one account at a time and
 * publishes the canonical dynamic-session snapshots. The actual collector,
 * parsers, pagination/scroll traversal and dedupe remain single-authority.
 */
internal object BlaBlaAutomaticCollectionCoordinator0400 {
    fun tryLaunchPending(context: Context, origin: String): Boolean {
        val appContext = context.applicationContext
        var state = AgendaBackgroundSyncConfig0392.collectorState0400(appContext)
        if (!state.pending || state.activeAccountId.isNotBlank()) return false

        val registry = BlaBlaDynamicAccountRegistry(appContext)
        while (true) {
            val accountId = nextAutomaticCollectorAccountId0400(
                targetAccountIds = state.targetAccountIds,
                completedAccountIds = state.completedAccountIds.toSet(),
                failedAccountIds = state.failedAccountIds.toSet(),
            ) ?: run {
                finishRun(appContext, state.generation, "no_unresolved_account")
                return false
            }
            val account = registry.get(accountId)
            if (account == null) {
                state = AgendaBackgroundSyncConfig0392.recordCollectorAccountFinished0400(
                    context = appContext,
                    generation = state.generation,
                    accountId = accountId,
                    result = "INTERRUPTED",
                    error = "configured_account_missing",
                )
                continue
            }

            val intent = BlaBlaDynamicSessionIntents.sync(appContext, account)
                .putExtra(BlaBlaDynamicSessionIntents.EXTRA_AUTOMATIC_COLLECTION_GENERATION, state.generation)
                .putExtra(BlaBlaDynamicSessionIntents.EXTRA_AUTOMATIC_COLLECTION_ORIGIN, origin.take(80))
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return runCatching {
                context.startActivity(intent)
                UnifiedDebugEventStore.record(
                    "BLABLACAR_AUTOMATIC_COLLECTION_LAUNCH_0400",
                    appContext.packageName,
                    "generation=${state.generation} accountKey=${seatSyncDiagnosticKey(accountId)} origin=${origin.take(80)} collector=existing_dynamic_session backgroundRequest=${context !is Activity}",
                )
                true
            }.getOrElse { error ->
                AgendaBackgroundSyncConfig0392.markCollectorLaunchInterrupted0400(
                    context = appContext,
                    generation = state.generation,
                    error = error.message ?: error::class.java.name,
                )
                UnifiedDebugEventStore.record(
                    "BLABLACAR_AUTOMATIC_COLLECTION_LAUNCH_FAILED_0400",
                    appContext.packageName,
                    "generation=${state.generation} accountKey=${seatSyncDiagnosticKey(accountId)} origin=${origin.take(80)} exceptionMessage=${error.message.orEmpty().take(300)} rootCause=${rootCause0400(error).take(300)} pendingPreserved=true",
                )
                false
            }
        }
    }

    fun claimActivity(
        context: Context,
        generation: Long,
        accountId: String,
    ): Boolean = AgendaBackgroundSyncConfig0392.claimCollectorAccount0400(
        context = context.applicationContext,
        generation = generation,
        accountId = accountId,
    )

    fun publishCurrentSessions(context: Context, reason: String): BlaBlaCollectorMonthResponse {
        val appContext = context.applicationContext
        val accounts = BlaBlaDynamicAccountRegistry(appContext).list()
        val response = BlaBlaDynamicSessionStore(appContext).combinedResponse(accounts)
        val published = BlaBlaCollectorStateStore(appContext).saveResponse(
            response = response,
            preserveOnPartial = true,
        )
        UnifiedDebugEventStore.record(
            "BLABLACAR_TIMELINE_PROGRESS_PUBLISHED_0400",
            appContext.packageName,
            "reason=${reason.take(80)} accounts=${accounts.size} trips=${published.trips.size} status=${published.status} completeForScope=${published.coverage.complete_for_scope}",
        )
        return published
    }

    fun onAccountFinished(
        context: Context,
        generation: Long,
        accountId: String,
        accountResult: String,
        error: String = "",
    ) {
        val appContext = context.applicationContext
        if (generation <= 0L) return
        val normalizedResult = if (accountResult == "success") "COMPLETE" else "PARTIAL"
        var state = AgendaBackgroundSyncConfig0392.recordCollectorAccountFinished0400(
            context = appContext,
            generation = generation,
            accountId = accountId,
            result = normalizedResult,
            error = error,
        )
        publishCurrentSessions(appContext, "account_${normalizedResult.lowercase()}")
        UnifiedDebugEventStore.record(
            "BLABLACAR_AUTOMATIC_ACCOUNT_END_0400",
            appContext.packageName,
            "generation=$generation accountKey=${seatSyncDiagnosticKey(accountId)} result=$normalizedResult completed=${state.completedAccountIds.size} failed=${state.failedAccountIds.size} target=${state.targetAccountIds.size}",
        )

        val next = nextAutomaticCollectorAccountId0400(
            targetAccountIds = state.targetAccountIds,
            completedAccountIds = state.completedAccountIds.toSet(),
            failedAccountIds = state.failedAccountIds.toSet(),
        )
        if (next == null) {
            finishRun(appContext, generation, "all_accounts_terminal")
        } else {
            state = AgendaBackgroundSyncConfig0392.collectorState0400(appContext)
            if (state.pending && state.activeAccountId.isBlank()) {
                tryLaunchPending(context, "automatic_chain")
            }
        }
    }

    fun onAccountInterrupted(
        context: Context,
        generation: Long,
        accountId: String,
        reason: String,
    ) {
        if (generation <= 0L) return
        val appContext = context.applicationContext
        val state = AgendaBackgroundSyncConfig0392.recordCollectorAccountFinished0400(
            context = appContext,
            generation = generation,
            accountId = accountId,
            result = "INTERRUPTED",
            error = reason,
        )
        publishCurrentSessions(appContext, "account_interrupted")
        if (nextAutomaticCollectorAccountId0400(
                state.targetAccountIds,
                state.completedAccountIds.toSet(),
                state.failedAccountIds.toSet(),
            ) == null
        ) {
            finishRun(appContext, generation, "interrupted_terminal")
        }
    }

    private fun finishRun(context: Context, generation: Long, reason: String) {
        val before = AgendaBackgroundSyncConfig0392.collectorState0400(context)
        if (before.generation != generation || !before.pending) return
        val response = publishCurrentSessions(context, "run_terminal")
        val result = automaticCollectorTerminalStatus0400(
            response = response,
            failedAccounts = before.failedAccountIds.size,
            targetAccounts = before.targetAccountIds.size,
        )
        val finalState = AgendaBackgroundSyncConfig0392.finishCollectorRun0400(
            context = context,
            generation = generation,
            result = result,
            error = before.lastError,
        )
        UnifiedDebugEventStore.record(
            "BLABLACAR_AUTOMATIC_COLLECTION_END_0400",
            context.packageName,
            "generation=$generation result=$result reason=${reason.take(80)} target=${finalState.targetAccountIds.size} completed=${finalState.completedAccountIds.size} failed=${finalState.failedAccountIds.size} trips=${response.trips.size} completeForScope=${response.coverage.complete_for_scope}",
        )
        AgendaBackgroundSync0392.enqueueImmediate(context, "blablacar_collection_result")
    }

    private fun rootCause0400(error: Throwable): String {
        var current: Throwable = error
        val seen = HashSet<Throwable>()
        while (current.cause != null && seen.add(current)) {
            current = current.cause ?: break
        }
        return current.message ?: current::class.java.name
    }
}
