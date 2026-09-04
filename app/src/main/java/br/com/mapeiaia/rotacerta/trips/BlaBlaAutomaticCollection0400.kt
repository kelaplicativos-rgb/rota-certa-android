package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import android.os.Handler
import android.os.Looper
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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
    private val targetedTripMutexes0407 = ConcurrentHashMap<String, Mutex>()

    suspend fun reverifyTripHeadless0407(
        context: Context,
        target: BlaBlaTripTarget0407,
        commandId: String,
        origin: String,
        timeoutMillis: Long = HEADLESS_TARGET_TIMEOUT_MS_0407,
    ): BlaBlaCommandResult0407 {
        val appContext = context.applicationContext
        val startedAt = System.currentTimeMillis()
        val registry = BlaBlaDynamicAccountRegistry(appContext)
        val account = registry.get(target.accountId)
        if (account == null) {
            return BlaBlaCommandResult0407(
                commandId = commandId,
                target = target,
                capability = BlaBlaTripCapability0407.REVERIFY_TRIP,
                status = BlaBlaCommandStatus0407.ACCOUNT_NOT_AVAILABLE,
                errorCode = "ACCOUNT_NOT_AVAILABLE",
                startedAtMillis = startedAt,
                finishedAtMillis = System.currentTimeMillis(),
            )
        }
        val profileMatches = account.profileUuid?.trim()?.equals(target.profileUuid.trim(), ignoreCase = true) == true
        val hrefMatches = BlaBlaCollectorUrlModule.tripId(target.tripHref) == target.tripId
        if (!profileMatches || !hrefMatches) {
            UnifiedDebugEventStore.record(
                "TARGET_RESOLVED",
                appContext.packageName,
                "commandKey=${seatSyncDiagnosticKey(commandId)} accountKey=${seatSyncDiagnosticKey(target.accountId)} profileMatch=$profileMatches tripHrefMatch=$hrefMatches result=UNVERIFIED_TARGET",
            )
            return BlaBlaCommandResult0407(
                commandId = commandId,
                target = target,
                capability = BlaBlaTripCapability0407.REVERIFY_TRIP,
                status = BlaBlaCommandStatus0407.UNVERIFIED_TARGET,
                errorCode = "UNVERIFIED_TARGET",
                startedAtMillis = startedAt,
                finishedAtMillis = System.currentTimeMillis(),
            )
        }

        if (BlaBlaDynamicSessionStore(appContext).isSourceCircuitOpen0426(account)) {
            UnifiedDebugEventStore.record(
                "BLABLACAR_TARGET_REVERIFY_CIRCUIT_OPEN_0426",
                appContext.packageName,
                "targetKey=" + seatSyncDiagnosticKey(target.strongIdentityKey) +
                    " action=skip_external_navigation previousSnapshotPreserved=true",
            )
            return BlaBlaCommandResult0407(
                commandId = commandId,
                target = target,
                capability = BlaBlaTripCapability0407.REVERIFY_TRIP,
                transportUsed = BlaBlaTransport0407.HYBRID,
                status = BlaBlaCommandStatus0407.TEMPORARILY_RESTRICTED,
                errorCode = "TEMPORARILY_RESTRICTED",
                verification = "source_circuit_open",
                startedAtMillis = startedAt,
                finishedAtMillis = System.currentTimeMillis(),
            )
        }

        val singleFlightKey = target.strongIdentityKey + "|REVERIFY_TRIP"
        val mutex = targetedTripMutexes0407.computeIfAbsent(singleFlightKey) { Mutex() }
        return mutex.withLock {
            UnifiedDebugEventStore.record(
                "COMMAND_REQUESTED",
                appContext.packageName,
                "commandKey=${seatSyncDiagnosticKey(commandId)} targetKey=${seatSyncDiagnosticKey(target.strongIdentityKey)} capability=REVERIFY_TRIP origin=${origin.take(80)} mode=EXECUTE singleFlight=true",
            )
            val before = exactCollectorTrip0407(appContext, target)
            UnifiedDebugEventStore.record(
                "CURRENT_STATE_READ",
                appContext.packageName,
                "commandKey=${seatSyncDiagnosticKey(commandId)} targetKey=${seatSyncDiagnosticKey(target.strongIdentityKey)} found=${before != null} transport=HYBRID",
            )
            val effectiveTimeoutMillis = timeoutMillis.coerceIn(5_000L, HEADLESS_TARGET_TIMEOUT_MS_0407)
            val hostResult = try {
                withTimeout(effectiveTimeoutMillis) {
                    runTargetTripHeadless0407(appContext, account, target, origin)
                }
            } catch (timeout: TimeoutCancellationException) {
                UnifiedDebugEventStore.record(
                    "FAILED",
                    appContext.packageName,
                    "commandKey=${seatSyncDiagnosticKey(commandId)} targetKey=${seatSyncDiagnosticKey(target.strongIdentityKey)} capability=REVERIFY_TRIP error=TIMEOUT timeoutMs=$effectiveTimeoutMillis",
                )
                return@withLock BlaBlaCommandResult0407(
                    commandId = commandId,
                    target = target,
                    capability = BlaBlaTripCapability0407.REVERIFY_TRIP,
                    transportUsed = BlaBlaTransport0407.HYBRID,
                    before = if (before != null) "PRESENT" else "UNKNOWN",
                    status = BlaBlaCommandStatus0407.UNVERIFIED,
                    errorCode = "TIMEOUT",
                    verification = "readback_not_completed",
                    startedAtMillis = startedAt,
                    finishedAtMillis = System.currentTimeMillis(),
                )
            }
            val failure = hostResult.second.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_SYNC_FAILURE_0407).orEmpty()
            if (hostResult.first != android.app.Activity.RESULT_OK) {
                val status = when (failure) {
                    "AUTH_REQUIRED" -> BlaBlaCommandStatus0407.AUTH_REQUIRED
                    "BROKEN_FOR_VERSION" -> BlaBlaCommandStatus0407.BROKEN_FOR_VERSION
                    "TEMPORARILY_RESTRICTED" -> BlaBlaCommandStatus0407.TEMPORARILY_RESTRICTED
                    else -> BlaBlaCommandStatus0407.UNVERIFIED
                }
                return@withLock BlaBlaCommandResult0407(
                    commandId = commandId,
                    target = target,
                    capability = BlaBlaTripCapability0407.REVERIFY_TRIP,
                    transportUsed = BlaBlaTransport0407.HYBRID,
                    before = if (before != null) "PRESENT" else "UNKNOWN",
                    status = status,
                    errorCode = failure.ifBlank { "UNVERIFIED" },
                    verification = "headless_target_not_verified",
                    startedAtMillis = startedAt,
                    finishedAtMillis = System.currentTimeMillis(),
                )
            }
            val after = exactCollectorTrip0407(appContext, target)
            val verified = after != null &&
                after.profile_uuid.trim().equals(target.profileUuid.trim(), ignoreCase = true) &&
                after.trip_id?.trim() == target.tripId
            UnifiedDebugEventStore.record(
                if (verified) "VERIFIED" else "UNVERIFIED",
                appContext.packageName,
                "commandKey=${seatSyncDiagnosticKey(commandId)} targetKey=${seatSyncDiagnosticKey(target.strongIdentityKey)} capability=REVERIFY_TRIP readback=$verified transport=HYBRID networkFirst=true exactTrip=true",
            )
            BlaBlaCommandResult0407(
                commandId = commandId,
                target = target,
                capability = BlaBlaTripCapability0407.REVERIFY_TRIP,
                transportUsed = BlaBlaTransport0407.HYBRID,
                before = if (before != null) "PRESENT" else "UNKNOWN",
                after = if (after != null) "PRESENT" else "UNKNOWN",
                writeAttempted = false,
                verification = if (verified) "exact_trip_readback" else "readback_missing",
                status = if (verified) BlaBlaCommandStatus0407.VERIFIED_SUCCESS else BlaBlaCommandStatus0407.UNVERIFIED,
                errorCode = if (verified) "" else "TRIP_NOT_FOUND_AFTER_REVERIFY",
                startedAtMillis = startedAt,
                finishedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    private fun exactCollectorTrip0407(context: Context, target: BlaBlaTripTarget0407): BlaBlaCollectorTrip? {
        val response = BlaBlaCollectorStateStore(context.applicationContext).lastResponseRecoveringDynamicSessions()
        val matches = response?.trips.orEmpty().filter { trip ->
            trip.profile_uuid.trim().equals(target.profileUuid.trim(), ignoreCase = true) &&
                trip.trip_id?.trim() == target.tripId
        }
        return matches.singleOrNull()
    }

    private suspend fun runTargetTripHeadless0407(
        context: Context,
        account: BlaBlaDynamicAccount,
        target: BlaBlaTripTarget0407,
        origin: String,
    ): Pair<Int, android.content.Intent> = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            var controller: BlaBlaDynamicAccountSessionController0401? = null
            val payload = BlaBlaDynamicSessionIntents.syncPayload(account)
                .putExtra(BlaBlaDynamicSessionIntents.EXTRA_TARGET_TRIP_ID, target.tripId)
                .putExtra(BlaBlaDynamicSessionIntents.EXTRA_TARGET_URL, target.tripHref)
                .putExtra(BlaBlaDynamicSessionIntents.EXTRA_AUTOMATIC_COLLECTION_ORIGIN, origin.take(80))
            controller = BlaBlaDynamicAccountSessionController0401(
                baseContext = context,
                launchIntent = payload,
                visualHost = null,
                finishHost = { resultCode, data ->
                    controller?.destroy("targeted_headless_terminal_0407")
                    if (continuation.isActive) continuation.resume(resultCode to data)
                },
            )
            continuation.invokeOnCancellation {
                Handler(Looper.getMainLooper()).post { controller?.destroy("targeted_headless_cancelled_0407") }
            }
            try {
                UnifiedDebugEventStore.record(
                    "VERIFY_STARTED",
                    context.packageName,
                    "targetKey=${seatSyncDiagnosticKey(target.strongIdentityKey)} transport=HYBRID networkFirst=true browserOpened=false activityLaunch=false",
                )
                controller?.start()
            } catch (error: Throwable) {
                controller?.destroy("targeted_headless_start_failed_0407")
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    }
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
            val sessionStore = BlaBlaDynamicSessionStore(appContext)
            if (sessionStore.isSourceCircuitOpen0426(account)) {
                state = AgendaBackgroundSyncConfig0392.recordCollectorAccountFinished0400(
                    appContext,
                    state.generation,
                    accountId,
                    "RESTRICTED",
                    "TEMPORARILY_RESTRICTED:circuit_open_skip",
                )
                UnifiedDebugEventStore.record(
                    "BLABLACAR_AUTOMATIC_CIRCUIT_OPEN_SKIP_0426",
                    appContext.packageName,
                    "generation=" + state.generation +
                        " accountKey=" + seatSyncDiagnosticKey(accountId) +
                        " action=stop_profile_continue_batch externalNavigationStarted=false previousSnapshotPreserved=true",
                )
                continue
            }
            UnifiedDebugEventStore.record(
                "BLABLACAR_AUTOMATIC_COLLECTION_HEADLESS_START_0401", appContext.packageName,
                "generation=${state.generation} accountKey=${seatSyncDiagnosticKey(accountId)} origin=${origin.take(80)} collector=existing_dynamic_session executionHost=worker_headless_webview activityLaunch=false windowAttached=false browserOpened=false",
            )
            try {
                withTimeout(HEADLESS_ACCOUNT_TIMEOUT_MS_0404) {
                    runAccountHeadless(appContext, state.generation, account, origin)
                }
            } catch (timeout: TimeoutCancellationException) {
                UnifiedDebugEventStore.record(
                    "BLABLACAR_AUTOMATIC_COLLECTION_HEADLESS_TIMEOUT_0404", appContext.packageName,
                    "generation=${state.generation} accountKey=${seatSyncDiagnosticKey(accountId)} timeoutMs=$HEADLESS_ACCOUNT_TIMEOUT_MS_0404 origin=${origin.take(80)} previousSnapshotPreserved=true browserOpened=false",
                )
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
            if (BlaBlaDynamicSessionStore(appContext).isSourceCircuitOpen0426(account)) {
                UnifiedDebugEventStore.record(
                    "BLABLACAR_AUTOMATIC_RESTRICTION_ISOLATED_0426",
                    appContext.packageName,
                    "generation=" + state.generation +
                        " accountKey=" + seatSyncDiagnosticKey(accountId) +
                        " action=continue_other_profiles externalNavigationStopped=true previousSnapshotPreserved=true",
                )
                continue
            }
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
                continuation.invokeOnCancellation { cause ->
                    val reason = if (cause is TimeoutCancellationException) {
                        "headless_account_timeout_0404"
                    } else {
                        "worker_cancelled"
                    }
                    Handler(Looper.getMainLooper()).post { controller?.destroy(reason) }
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
        AgendaBackgroundSync0392.enqueueCollectorDelta0431(appContext, "account_${normalizedResult.lowercase()}")
        UnifiedDebugEventStore.record(
            "BLABLACAR_AUTOMATIC_ACCOUNT_END_0400", appContext.packageName,
            "generation=$generation accountKey=${seatSyncDiagnosticKey(accountId)} result=$normalizedResult completed=${state.completedAccountIds.size} failed=${state.failedAccountIds.size} pendingAuth=${state.pendingAuthAccountIds.size} target=${state.targetAccountIds.size} automaticChainOwnedByWorker=true",
        )
    }

    fun onAccountTemporarilyRestricted0426(
        context: Context,
        generation: Long,
        accountId: String,
        reason: String,
    ) {
        if (generation <= 0L) return
        val appContext = context.applicationContext
        AgendaBackgroundSyncConfig0392.recordCollectorAccountFinished0400(
            appContext,
            generation,
            accountId,
            "RESTRICTED",
            "TEMPORARILY_RESTRICTED:" + reason.take(180),
        )
        UnifiedDebugEventStore.record(
            "BLABLACAR_AUTOMATIC_RESTRICTED_TERMINAL_0426",
            appContext.packageName,
            "generation=" + generation +
                " accountKey=" + seatSyncDiagnosticKey(accountId) +
                " action=stop_without_publication previousSnapshotPreserved=true retryScheduled=false",
        )
    }

    fun onAccountTransientFailure0426(
        context: Context,
        generation: Long,
        accountId: String,
        reason: String,
    ) {
        if (generation <= 0L) return
        val appContext = context.applicationContext
        AgendaBackgroundSyncConfig0392.recordCollectorAccountFinished0400(
            appContext,
            generation,
            accountId,
            "FAILED",
            reason.take(180),
        )
        UnifiedDebugEventStore.record(
            "BLABLACAR_AUTOMATIC_TRANSIENT_FAILURE_0426",
            appContext.packageName,
            "generation=" + generation +
                " accountKey=" + seatSyncDiagnosticKey(accountId) +
                " action=stop_without_immediate_retry previousSnapshotPreserved=true",
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
        AgendaBackgroundSync0392.enqueueCollectorDelta0431(context, "run_terminal:$result")
        UnifiedDebugEventStore.record(
            "BLABLACAR_AUTOMATIC_COLLECTION_END_0401", context.packageName,
            "generation=$generation result=$result reason=${reason.take(80)} target=${finalState.targetAccountIds.size} completed=${finalState.completedAccountIds.size} failed=${finalState.failedAccountIds.size} pendingAuth=${finalState.pendingAuthAccountIds.size} trips=${response.trips.size} completeForScope=${response.coverage.complete_for_scope} executionHost=worker_headless_webview activityLaunch=false browserOpened=false",
        )
        return finalState
    }

    private const val HEADLESS_ACCOUNT_TIMEOUT_MS_0404 = 10L * 60L * 1000L
    private const val HEADLESS_TARGET_TIMEOUT_MS_0407 = 5L * 60L * 1000L

    private fun rootCause0400(error: Throwable): String {
        var current: Throwable = error
        val seen = HashSet<Throwable>()
        while (current.cause != null && seen.add(current)) current = current.cause ?: break
        return current.message ?: current::class.java.name
    }
}
