package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import android.os.Handler
import android.os.Looper
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

internal object BlaBlaCollectorTimelineEvents0400 {
    private val counter = AtomicLong(0L)
    private val mutableRevision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = mutableRevision.asStateFlow()
    fun notifyChanged() { mutableRevision.value = counter.incrementAndGet() }
}

internal fun nextAutomaticCollectorAccountId0400(
    targetAccountIds: List<String>,
    completedAccountIds: Set<String>,
    failedAccountIds: Set<String>,
    pendingAuthAccountIds: Set<String> = emptySet(),
): String? = targetAccountIds.firstOrNull { id ->
    id.isNotBlank() && id !in completedAccountIds && id !in failedAccountIds && id !in pendingAuthAccountIds
}

internal fun automaticCollectorTerminalStatus0400(
    response: BlaBlaCollectorMonthResponse,
    failedAccounts: Int,
    targetAccounts: Int,
    pendingAuthAccounts: Int = 0,
): String = when {
    targetAccounts <= 0 -> "NO_ACCOUNTS"
    pendingAuthAccounts > 0 -> "PENDING_AUTH"
    failedAccounts == 0 && response.coverage.complete_for_scope && response.status in setOf("success", "validated") -> "COMPLETE"
    response.trips.isNotEmpty() -> "PARTIAL"
    else -> "FAILED"
}

/** Central automatic-sync coordinator. Automatic collection never launches an Activity. */
internal object BlaBlaAutomaticCollectionCoordinator0400 {
    suspend fun runPendingHeadless(context: Context, origin: String): AgendaAutomaticCollectorState0400 {
        val appContext = context.applicationContext
        var state = AgendaBackgroundSyncConfig0392.recoverStaleCollectorHost0401(appContext)
        if (!state.pending) return state
        val registry = BlaBlaDynamicAccountRegistry(appContext)
        while (state.pending) {
            val accountId = nextAutomaticCollectorAccountId0400(
                state.targetAccountIds, state.completedAccountIds.toSet(), state.failedAccountIds.toSet(), state.pendingAuthAccountIds.toSet(),
            ) ?: return finishRun(appContext, state.generation, "all_accounts_terminal")
            val account = registry.get(accountId)
            if (account == null) {
                state = AgendaBackgroundSyncConfig0392.recordCollectorAccountFinished0400(appContext, state.generation, accountId, "FAILED", "configured_account_missing")
                continue
            }
            UnifiedDebugEventStore.record(
                "BLABLACAR_AUTOMATIC_COLLECTION_HEADLESS_START_0401", appContext.packageName,
                "generation=${state.generation} accountKey=${seatSyncDiagnosticKey(accountId)} origin=${origin.take(80)} collector=existing_dynamic_session executionHost=worker_headless_webview activityLaunch=false windowAttached=false browserOpened=false",
            )
            try {
                runAccountHeadless(appContext, state.generation, account, origin)
            } catch (cancelled: CancellationException) {
                onAccountInterrupted(appContext, state.generation, accountId, "worker_cancelled")
                throw cancelled
            } catch (error: Throwable) {
                val live = AgendaBackgroundSyncConfig0392.collectorState0400(appContext)
                if (live.pending && accountId !in live.completedAccountIds && accountId !in live.failedAccountIds && accountId !in live.pendingAuthAccountIds) {
                    AgendaBackgroundSyncConfig0392.recordCollectorAccountFinished0400(appContext, state.generation, accountId, "FAILED", error.message ?: error::class.java.name)
                }
                UnifiedDebugEventStore.record(
                    "BLABLACAR_AUTOMATIC_COLLECTION_HEADLESS_FAILED_0401", appContext.packageName,
                    "generation=${state.generation} accountKey=${seatSyncDiagnosticKey(accountId)} origin=${origin.take(80)} exceptionMessage=${error.message.orEmpty().take(300)} rootCause=${rootCause0400(error).take(300)} browserOpened=false",
                )
            }
            state = AgendaBackgroundSyncConfig0392.collectorState0400(appContext)
            if (state.activeAccountId == accountId) {
                state = AgendaBackgroundSyncConfig0392.recordCollectorAccountFinished0400(appContext, state.generation, accountId, "FAILED", "headless_host_finished_without_terminal_state")
            }
        }
        return state
    }

    private suspend fun runAccountHeadless(context: Context, generation: Long, account: BlaBlaDynamicAccount, origin: String) =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine<Unit> { continuation ->
                var controller: BlaBlaDynamicAccountSessionController0401? = null
                val payload = BlaBlaDynamicSessionIntents.syncPayload(account)
                    .putExtra(BlaBlaDynamicSessionIntents.EXTRA_AUTOMATIC_COLLECTION_GENERATION, generation)
                    .putExtra(BlaBlaDynamicSessionIntents.EXTRA_AUTOMATIC_COLLECTION_ORIGIN, origin.take(80))
                controller = BlaBlaDynamicAccountSessionController0401(
                    baseContext = context,
                    launchIntent = payload,
                    visualHost = null,
                    finishHost = { _, _ ->
                        controller?.destroy("headless_terminal")
                        if (continuation.isActive) continuation.resume(Unit)
                    },
                )
                continuation.invokeOnCancellation {
                    Handler(Looper.getMainLooper()).post { controller?.destroy("worker_cancelled") }
                }
                try {
                    controller?.start()
                } catch (error: Throwable) {
                    controller?.destroy("headless_start_failed")
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        }

    fun claimHost(context: Context, generation: Long, accountId: String): Boolean =
        AgendaBackgroundSyncConfig0392.claimCollectorAccount0400(context.applicationContext, generation, accountId)

    fun publishCurrentSessions(context: Context, reason: String): BlaBlaCollectorMonthResponse {
        val appContext = context.applicationContext
        val accounts = BlaBlaDynamicAccountRegistry(appContext).list()
        val response = BlaBlaDynamicSessionStore(appContext).combinedResponse(accounts)
        val published = BlaBlaCollectorStateStore(appContext).saveResponse(response, preserveOnPartial = true)
        UnifiedDebugEventStore.record(
            "BLABLACAR_TIMELINE_PROGRESS_PUBLISHED_0400", appContext.packageName,
            "reason=${reason.take(80)} accounts=${accounts.size} trips=${published.trips.size} status=${published.status} completeForScope=${published.coverage.complete_for_scope}",
        )
        return published
    }

    fun onAccountFinished(context: Context, generation: Long, accountId: String, accountResult: String, error: String = "") {
        if (generation <= 0L) return
        val appContext = context.applicationContext
        val normalizedResult = if (accountResult == "success") "COMPLETE" else "PARTIAL"
        val state = AgendaBackgroundSyncConfig0392.recordCollectorAccountFinished0400(appContext, generation, accountId, normalizedResult, error)
        publishCurrentSessions(appContext, "account_${normalizedResult.lowercase()}")
        UnifiedDebugEventStore.record(
            "BLABLACAR_AUTOMATIC_ACCOUNT_END_0400", appContext.packageName,
            "generation=$generation accountKey=${seatSyncDiagnosticKey(accountId)} result=$normalizedResult completed=${state.completedAccountIds.size} failed=${state.failedAccountIds.size} pendingAuth=${state.pendingAuthAccountIds.size} target=${state.targetAccountIds.size} automaticChainOwnedByWorker=true",
        )
    }

    fun onAccountPendingAuth(context: Context, generation: Long, accountId: String, reason: String) {
        if (generation <= 0L) return
        val appContext = context.applicationContext
        AgendaBackgroundSyncConfig0392.recordCollectorAccountFinished0400(appContext, generation, accountId, "PENDING_AUTH", reason)
        publishCurrentSessions(appContext, "account_pending_auth")
        UnifiedDebugEventStore.record(
            "BLABLACAR_AUTOMATIC_AUTH_REQUIRED_0401", appContext.packageName,
            "generation=$generation accountKey=${seatSyncDiagnosticKey(accountId)} reason=${reason.take(120)} action=user_reconnect_required browserOpened=false previousSnapshotPreserved=true",
        )
    }

    fun onAccountInterrupted(context: Context, generation: Long, accountId: String, reason: String) {
        if (generation <= 0L) return
        val appContext = context.applicationContext
        AgendaBackgroundSyncConfig0392.recordCollectorAccountFinished0400(appContext, generation, accountId, "INTERRUPTED", reason)
        publishCurrentSessions(appContext, "account_interrupted")
    }

    private fun finishRun(context: Context, generation: Long, reason: String): AgendaAutomaticCollectorState0400 {
        val before = AgendaBackgroundSyncConfig0392.collectorState0400(context)
        if (before.generation != generation || !before.pending) return before
        val response = publishCurrentSessions(context, "run_terminal")
        val result = automaticCollectorTerminalStatus0400(response, before.failedAccountIds.size, before.targetAccountIds.size, before.pendingAuthAccountIds.size)
        val finalState = AgendaBackgroundSyncConfig0392.finishCollectorRun0400(context, generation, result, before.lastError)
        UnifiedDebugEventStore.record(
            "BLABLACAR_AUTOMATIC_COLLECTION_END_0401", context.packageName,
            "generation=$generation result=$result reason=${reason.take(80)} target=${finalState.targetAccountIds.size} completed=${finalState.completedAccountIds.size} failed=${finalState.failedAccountIds.size} pendingAuth=${finalState.pendingAuthAccountIds.size} trips=${response.trips.size} completeForScope=${response.coverage.complete_for_scope} executionHost=worker_headless_webview activityLaunch=false browserOpened=false",
        )
        return finalState
    }

    private fun rootCause0400(error: Throwable): String {
        var current: Throwable = error
        val seen = HashSet<Throwable>()
        while (current.cause != null && seen.add(current)) current = current.cause ?: break
        return current.message ?: current::class.java.name
    }
}
