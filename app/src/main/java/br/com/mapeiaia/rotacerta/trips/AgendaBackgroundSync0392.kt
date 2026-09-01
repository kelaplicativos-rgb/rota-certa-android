package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry
import br.com.mapeiaia.rotacerta.SettingsRepository
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class AgendaBackgroundSyncRun0392(
    val bookingImports: Int = 0,
    val outboxDelivered: Int = 0,
    val publicLocalPublished: Int = 0,
    val publicExternalPublished: Int = 0,
    val failures: Int = 0,
)

internal data class TenantSeatAllocationFanOut0395(
    val configVersion: Long,
    val localCanonicalUpdated: Int = 0,
    val localPublicationQueued: Int = 0,
    val externalPublicationQueued: Int = 0,
    val externalRetryPending: Int = 0,
)

internal enum class AgendaBackgroundSyncMode0392 {
    FULL_RECONCILE,
    BOOKING_EVENT,
    COLLECTOR_RECONCILE,
    DELTA_ONLY,
}

internal fun agendaBackgroundSyncIntervalMinutes0392(requestedMinutes: Long? = null): Long =
    (requestedMinutes ?: AgendaBackgroundSyncConfig0392.DEFAULT_INTERVAL_MINUTES)
        .coerceIn(
            AgendaBackgroundSyncConfig0392.MIN_INTERVAL_MINUTES,
            AgendaBackgroundSyncConfig0392.MAX_INTERVAL_MINUTES,
        )

internal fun agendaBackgroundSyncShowsUiStatus0392(): Boolean = false

internal fun agendaBackgroundSyncMode0392(reason: String): AgendaBackgroundSyncMode0392 = when {
    reason == "periodic" -> AgendaBackgroundSyncMode0392.FULL_RECONCILE
    reason == "manual" -> AgendaBackgroundSyncMode0392.FULL_RECONCILE
    reason == "recovery" -> AgendaBackgroundSyncMode0392.FULL_RECONCILE
    reason == "timeline_open" -> AgendaBackgroundSyncMode0392.FULL_RECONCILE
    reason == "timeline_pull_refresh" -> AgendaBackgroundSyncMode0392.FULL_RECONCILE
    reason.startsWith("booking_push:") -> AgendaBackgroundSyncMode0392.BOOKING_EVENT
    reason == "blablacar_collection_result" -> AgendaBackgroundSyncMode0392.COLLECTOR_RECONCILE
    else -> AgendaBackgroundSyncMode0392.DELTA_ONLY
}

internal fun agendaBackgroundSyncTrigger0397(reason: String): String = when {
    reason == "periodic" -> "PERIODIC"
    reason == "manual" -> "MANUAL"
    reason == "timeline_pull_refresh" -> "PULL_TO_REFRESH"
    reason == "recovery" || reason == "timeline_open" -> "RECOVERY"
    reason.startsWith("booking_push:") -> "EVENT_DELTA"
    reason == "blablacar_collection_result" -> "EVENT_DELTA"
    else -> "EVENT_DELTA"
}

internal data class AgendaBackgroundSyncStatus0397(
    val enabled: Boolean,
    val intervalMinutes: Long,
    val scheduledAtMillis: Long,
    val lastStartedAtMillis: Long,
    val lastFinishedAtMillis: Long,
    val lastPeriodicFinishedAtMillis: Long,
    val lastTrigger: String,
    val lastResult: String,
    val retryPending: Boolean,
    val retryAttempt: Int,
    val lastFailures: Int,
) {
    fun nextExecutionEstimateMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        if (!enabled || scheduledAtMillis <= 0L) return 0L
        val anchor = maxOf(scheduledAtMillis, lastPeriodicFinishedAtMillis)
        val candidate = anchor + intervalMinutes.coerceAtLeast(1L) * 60_000L
        return maxOf(candidate, nowMillis)
    }
}

internal object AgendaBackgroundSyncConfig0392 {
    const val DEFAULT_INTERVAL_MINUTES = 15L
    const val MIN_INTERVAL_MINUTES = 15L
    const val MAX_INTERVAL_MINUTES = 24L * 60L
    const val DEFAULT_ENABLED = true

    private const val PREFS = "rota_certa_agenda_background_sync_0392"
    private const val KEY_ENABLED = "automatic_sync_enabled_0397"
    private const val KEY_INTERVAL_MINUTES = "periodic_interval_minutes"
    private const val KEY_SCHEDULED_AT = "periodic_scheduled_at_0397"
    private const val KEY_SCHEDULED_INTERVAL = "periodic_scheduled_interval_0397"
    private const val KEY_LAST_STARTED = "last_started_at_0397"
    private const val KEY_LAST_FINISHED = "last_finished_at_0397"
    private const val KEY_LAST_PERIODIC_FINISHED = "last_periodic_finished_at_0397"
    private const val KEY_LAST_TRIGGER = "last_trigger_0397"
    private const val KEY_LAST_RESULT = "last_result_0397"
    private const val KEY_RETRY_PENDING = "retry_pending_0397"
    private const val KEY_RETRY_ATTEMPT = "retry_attempt_0397"
    private const val KEY_LAST_FAILURES = "last_failures_0397"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun scope(context: Context) =
        RotaCertaTenantRegistry(context.applicationContext).activeScope()

    fun isEnabled(context: Context): Boolean {
        val prefs = prefs(context)
        val scope = scope(context)
        return prefs.getBoolean(scope.key(KEY_ENABLED), DEFAULT_ENABLED)
    }

    fun configuredIntervalMinutes(context: Context): Long {
        val prefs = prefs(context)
        val scope = scope(context)
        val raw = prefs.getLong(scope.key(KEY_INTERVAL_MINUTES), DEFAULT_INTERVAL_MINUTES)
        return agendaBackgroundSyncIntervalMinutes0392(raw)
    }

    fun status(context: Context): AgendaBackgroundSyncStatus0397 {
        val prefs = prefs(context)
        val scope = scope(context)
        val interval = agendaBackgroundSyncIntervalMinutes0392(
            prefs.getLong(scope.key(KEY_INTERVAL_MINUTES), DEFAULT_INTERVAL_MINUTES),
        )
        return AgendaBackgroundSyncStatus0397(
            enabled = prefs.getBoolean(scope.key(KEY_ENABLED), DEFAULT_ENABLED),
            intervalMinutes = interval,
            scheduledAtMillis = prefs.getLong(scope.key(KEY_SCHEDULED_AT), 0L),
            lastStartedAtMillis = prefs.getLong(scope.key(KEY_LAST_STARTED), 0L),
            lastFinishedAtMillis = prefs.getLong(scope.key(KEY_LAST_FINISHED), 0L),
            lastPeriodicFinishedAtMillis = prefs.getLong(scope.key(KEY_LAST_PERIODIC_FINISHED), 0L),
            lastTrigger = prefs.getString(scope.key(KEY_LAST_TRIGGER), "").orEmpty(),
            lastResult = prefs.getString(scope.key(KEY_LAST_RESULT), "Ainda não executada").orEmpty(),
            retryPending = prefs.getBoolean(scope.key(KEY_RETRY_PENDING), false),
            retryAttempt = prefs.getInt(scope.key(KEY_RETRY_ATTEMPT), 0),
            lastFailures = prefs.getInt(scope.key(KEY_LAST_FAILURES), 0),
        )
    }

    fun updateEnabled(context: Context, enabled: Boolean): Boolean {
        val appContext = context.applicationContext
        val prefs = prefs(appContext)
        val scope = scope(appContext)
        require(
            prefs.edit().putBoolean(scope.key(KEY_ENABLED), enabled).commit(),
        ) { "Falha ao persistir estado da sincronização automática da Agenda." }
        if (enabled) {
            AgendaBackgroundSync0392.ensureScheduled(appContext)
            AgendaBackgroundSync0392.enqueueRecoveryIfNeeded(appContext)
        } else {
            AgendaBackgroundSync0392.cancelPeriodic(appContext, "config_disabled")
        }
        return enabled
    }

    fun updateIntervalMinutes(context: Context, requestedMinutes: Long): Long {
        val appContext = context.applicationContext
        val prefs = prefs(appContext)
        val scope = scope(appContext)
        val sanitized = agendaBackgroundSyncIntervalMinutes0392(requestedMinutes)
        require(
            prefs.edit()
                .putLong(scope.key(KEY_INTERVAL_MINUTES), sanitized)
                .commit(),
        ) { "Falha ao persistir intervalo da sincronização da Agenda." }
        if (isEnabled(appContext)) {
            AgendaBackgroundSync0392.ensureScheduled(appContext)
        }
        return sanitized
    }

    internal fun markScheduled(context: Context, intervalMinutes: Long, nowMillis: Long = System.currentTimeMillis()) {
        val prefs = prefs(context)
        val scope = scope(context)
        val scheduledAtKey = scope.key(KEY_SCHEDULED_AT)
        val scheduledIntervalKey = scope.key(KEY_SCHEDULED_INTERVAL)
        val previousInterval = prefs.getLong(scheduledIntervalKey, 0L)
        val previousScheduledAt = prefs.getLong(scheduledAtKey, 0L)
        val editor = prefs.edit().putLong(scheduledIntervalKey, intervalMinutes)
        if (previousScheduledAt <= 0L || previousInterval != intervalMinutes) {
            editor.putLong(scheduledAtKey, nowMillis)
        }
        editor.apply()
    }

    internal fun markUnscheduled(context: Context) {
        val prefs = prefs(context)
        val scope = scope(context)
        prefs.edit()
            .putLong(scope.key(KEY_SCHEDULED_AT), 0L)
            .putLong(scope.key(KEY_SCHEDULED_INTERVAL), 0L)
            .apply()
    }

    internal fun recordRunStarted(context: Context, reason: String, attempt: Int, nowMillis: Long = System.currentTimeMillis()) {
        val prefs = prefs(context)
        val scope = scope(context)
        prefs.edit()
            .putLong(scope.key(KEY_LAST_STARTED), nowMillis)
            .putString(scope.key(KEY_LAST_TRIGGER), agendaBackgroundSyncTrigger0397(reason))
            .putString(scope.key(KEY_LAST_RESULT), "RUNNING")
            .putBoolean(scope.key(KEY_RETRY_PENDING), false)
            .putInt(scope.key(KEY_RETRY_ATTEMPT), attempt)
            .apply()
    }

    internal fun recordRunFinished(
        context: Context,
        reason: String,
        result: String,
        failures: Int,
        retryPending: Boolean,
        attempt: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val prefs = prefs(context)
        val scope = scope(context)
        val editor = prefs.edit()
            .putLong(scope.key(KEY_LAST_FINISHED), nowMillis)
            .putString(scope.key(KEY_LAST_TRIGGER), agendaBackgroundSyncTrigger0397(reason))
            .putString(scope.key(KEY_LAST_RESULT), result)
            .putBoolean(scope.key(KEY_RETRY_PENDING), retryPending)
            .putInt(scope.key(KEY_RETRY_ATTEMPT), attempt)
            .putInt(scope.key(KEY_LAST_FAILURES), failures)
        if (reason == "periodic") {
            editor.putLong(scope.key(KEY_LAST_PERIODIC_FINISHED), nowMillis)
        }
        editor.apply()
    }
}

internal object AgendaBackgroundSync0392 {
    private const val PERIODIC_WORK = "agenda-background-sync-0392-periodic"
    private const val IMMEDIATE_WORK = "agenda-background-sync-0392-immediate"
    private const val INPUT_REASON = "reason"
    private const val INPUT_TENANT_ID = "tenant_id_0397"
    private const val WORK_BACKOFF_SECONDS = 30L
    private val tenantMutexes = ConcurrentHashMap<String, Mutex>()

    fun ensureScheduled(context: Context) {
        val appContext = context.applicationContext
        if (!AgendaBackgroundSyncConfig0392.isEnabled(appContext)) {
            cancelPeriodic(appContext, "disabled_guard")
            return
        }
        val tenantId = RotaCertaTenantRegistry(appContext).activeScope().tenantId
        val intervalMinutes = AgendaBackgroundSyncConfig0392.configuredIntervalMinutes(appContext)
        val periodic = PeriodicWorkRequestBuilder<AgendaBackgroundSyncWorker0392>(
            intervalMinutes,
            TimeUnit.MINUTES,
        )
            .setConstraints(networkConstraints())
            .setInputData(workDataOf(INPUT_REASON to "periodic", INPUT_TENANT_ID to tenantId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WORK_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            tenantScopedWorkName(tenantId, PERIODIC_WORK),
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic,
        )
        AgendaBackgroundSyncConfig0392.markScheduled(appContext, intervalMinutes)
        UnifiedDebugEventStore.record(
            "AGENDA_BACKGROUND_SYNC_SCHEDULED_0392",
            appContext.packageName,
            "tenantKey=${seatSyncDiagnosticKey(tenantId)} periodMinutes=$intervalMinutes silentUi=true durable=true tenantScoped=true configurable=true enabled=true scheduler=WorkManager periodicMin=15",
        )
    }

    fun cancelPeriodic(context: Context, reason: String) {
        val appContext = context.applicationContext
        val tenantId = RotaCertaTenantRegistry(appContext).activeScope().tenantId
        WorkManager.getInstance(appContext).cancelUniqueWork(tenantScopedWorkName(tenantId, PERIODIC_WORK))
        AgendaBackgroundSyncConfig0392.markUnscheduled(appContext)
        UnifiedDebugEventStore.record(
            "AGENDA_BACKGROUND_SYNC_CANCELLED_0397",
            appContext.packageName,
            "tenantKey=${seatSyncDiagnosticKey(tenantId)} reason=${reason.take(80)} periodicOnly=true immediateEventsPreserved=true",
        )
    }

    fun enqueueRecoveryIfNeeded(context: Context) {
        val appContext = context.applicationContext
        val status = AgendaBackgroundSyncConfig0392.status(appContext)
        if (!status.enabled) return
        val now = System.currentTimeMillis()
        val staleAfterMillis = status.intervalMinutes.coerceAtLeast(AgendaBackgroundSyncConfig0392.MIN_INTERVAL_MINUTES) * 60_000L
        val stale = status.lastFinishedAtMillis <= 0L || now - status.lastFinishedAtMillis >= staleAfterMillis
        if (stale) {
            enqueueImmediate(appContext, "recovery")
            UnifiedDebugEventStore.record(
                "AGENDA_BACKGROUND_SYNC_RECOVERY_0397",
                appContext.packageName,
                "trigger=RECOVERY stale=true lastFinishedAt=${status.lastFinishedAtMillis} intervalMinutes=${status.intervalMinutes}",
            )
        }
    }

    fun enqueueImmediate(context: Context, reason: String) {
        val appContext = context.applicationContext
        val tenantId = RotaCertaTenantRegistry(appContext).activeScope().tenantId
        val request = OneTimeWorkRequestBuilder<AgendaBackgroundSyncWorker0392>()
            .setConstraints(networkConstraints())
            .setInputData(workDataOf(INPUT_REASON to reason.take(80), INPUT_TENANT_ID to tenantId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WORK_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            tenantScopedWorkName(tenantId, IMMEDIATE_WORK),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
        UnifiedDebugEventStore.record(
            "AGENDA_BACKGROUND_SYNC_ENQUEUED_0392",
            appContext.packageName,
            "tenantKey=${seatSyncDiagnosticKey(tenantId)} reason=${reason.take(80)} trigger=${agendaBackgroundSyncTrigger0397(reason)} mode=${agendaBackgroundSyncMode0392(reason).name} workId=${request.id} silentUi=true tenantScoped=true periodicEnabled=${AgendaBackgroundSyncConfig0392.isEnabled(appContext)}",
        )
    }

    internal suspend fun reconcileTenantSeatAllocation0395(
        context: Context,
        rotaCertaSeatAllocation: Int,
        seatAllocationVersion: Long,
    ): TenantSeatAllocationFanOut0395 = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val store = TripStore(appContext)
        val nowMillis = System.currentTimeMillis()
        val changedLocalIds = store.reconcileOperationalInventoryTripIds(
            rotaCertaSeatAllocation = rotaCertaSeatAllocation,
            seatAllocationVersion = seatAllocationVersion,
            nowMillis = nowMillis,
        )
        val coordinator = TripMutationCoordinator0387(appContext, store)
        var localQueued = 0
        val recoverableLocalTrips = store.trips().filter { trip ->
            trip.isCanonicalLocalPublishSource() &&
                trip.status in setOf(TripStatus.PUBLISHED, TripStatus.FULL, TripStatus.STARTING, TripStatus.ACTIVE) &&
                (trip.departureAtMillis >= nowMillis || trip.status in setOf(TripStatus.STARTING, TripStatus.ACTIVE)) &&
                trip.seatAllocationVersionUsed == seatAllocationVersion
        }
        if (seatAllocationVersion > 0L || changedLocalIds.isNotEmpty()) {
            recoverableLocalTrips.forEach { trip ->
                if (coordinator.recordLocalMutation(
                        canonicalTripId = trip.id,
                        mutationType = "TENANT_SEAT_ALLOCATION_CHANGED",
                        source = "TENANT_CONFIG_CANONICAL_FANOUT",
                        configuredRotaCertaSeatAllocation = rotaCertaSeatAllocation,
                        reconcileBookingInventory = false,
                    ) != null
                ) {
                    localQueued++
                }
            }
        }

        var externalQueued = 0
        var externalRetryPending = 0
        if (seatAllocationVersion > 0L) {
            val cachedExternalTrips = BlaBlaCollectorStateStore(appContext)
                .lastResponseRecoveringDynamicSessions()?.trips.orEmpty()
                .filterNot(BlaBlaCollectorTrip::identity_conflict)
            val pendingBindings = store.publicExternalBindings().filter { binding ->
                binding.departureAtMillis >= nowMillis &&
                    binding.seatAllocationVersionUsed < seatAllocationVersion &&
                    binding.profileUuid.isNotBlank() && binding.blablaTripId.isNotBlank()
            }
            pendingBindings.forEach { binding ->
                val exactMatches = cachedExternalTrips.filter { source ->
                    source.profile_uuid.trim().equals(binding.profileUuid.trim(), ignoreCase = true) &&
                        source.trip_id?.trim() == binding.blablaTripId.trim()
                }
                if (exactMatches.size == 1) {
                    if (coordinator.recordExternalTenantMutation(
                            sourceTrip = exactMatches.single(),
                            configuredRotaCertaSeatAllocation = rotaCertaSeatAllocation,
                            seatAllocationVersion = seatAllocationVersion,
                        ) != null
                    ) {
                        externalQueued++
                    }
                } else {
                    externalRetryPending++
                    UnifiedDebugEventStore.record(
                        "TENANT_SEAT_ALLOCATION_FANOUT_0395",
                        appContext.packageName,
                        "tenantKey=" + seatSyncDiagnosticKey(RotaCertaTenantRegistry(appContext).activeScope().tenantId) +
                            " internalTripId=" + seatSyncDiagnosticKey(binding.bookingTripId) +
                            " configVersion=" + seatAllocationVersion +
                            " result=RETRY_PENDING reason=external_snapshot_unavailable exactMatches=" + exactMatches.size,
                    )
                }
            }
        }
        BookingRealtimeEvents0356.notifyChanged()
        val result = TenantSeatAllocationFanOut0395(
            configVersion = seatAllocationVersion,
            localCanonicalUpdated = changedLocalIds.size,
            localPublicationQueued = localQueued,
            externalPublicationQueued = externalQueued,
            externalRetryPending = externalRetryPending,
        )
        UnifiedDebugEventStore.record(
            "TENANT_SEAT_ALLOCATION_FANOUT_0395",
            appContext.packageName,
            "tenantKey=" + seatSyncDiagnosticKey(RotaCertaTenantRegistry(appContext).activeScope().tenantId) +
                " configVersion=" + seatAllocationVersion +
                " allocation=" + rotaCertaSeatAllocation +
                " localCanonicalUpdated=" + result.localCanonicalUpdated +
                " localPublicationQueued=" + result.localPublicationQueued +
                " externalPublicationQueued=" + result.externalPublicationQueued +
                " externalRetryPending=" + result.externalRetryPending +
                " result=" + when {
                    result.externalRetryPending > 0 -> "RETRY_PENDING"
                    result.localCanonicalUpdated > 0 || result.localPublicationQueued > 0 || result.externalPublicationQueued > 0 -> "UPDATE"
                    else -> "SKIP_ALREADY_CURRENT"
                } +
                " fullSyncRequested=false",
        )
        result
    }

    internal suspend fun runCycle(context: Context, reason: String): AgendaBackgroundSyncRun0392 {
        val appContext = context.applicationContext
        val tenantId = RotaCertaTenantRegistry(appContext).activeScope().tenantId
        val mutex = tenantMutexes.computeIfAbsent(tenantId) { Mutex() }
        return mutex.withLock {
            runTenantCycle(appContext, reason, tenantId)
        }
    }

    private suspend fun runTenantCycle(
        appContext: Context,
        reason: String,
        tenantId: String,
    ): AgendaBackgroundSyncRun0392 {
        val mode = agendaBackgroundSyncMode0392(reason)
        val store = TripStore(appContext)
        var failures = 0
        var bookingImports = 0
        var outboxDelivered = 0
        var publicLocalPublished = 0
        var publicExternalPublished = 0
        val tenantSettings = SettingsRepository(appContext).settings.first()

        UnifiedDebugEventStore.record(
            "AGENDA_BACKGROUND_SYNC_START_0392",
            appContext.packageName,
            "tenantKey=${seatSyncDiagnosticKey(tenantId)} reason=${reason.take(80)} trigger=${agendaBackgroundSyncTrigger0397(reason)} mode=${mode.name} silentUi=true singleFlight=true",
        )

        val pullBookings = mode in setOf(
            AgendaBackgroundSyncMode0392.FULL_RECONCILE,
            AgendaBackgroundSyncMode0392.BOOKING_EVENT,
        )
        if (pullBookings) {
            try {
                val booking = PublicBookingRemoteSync0296.pullAndReconcile(appContext, store)
                bookingImports = booking.importedCount
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                failures++
                UnifiedDebugEventStore.record(
                    "AGENDA_BACKGROUND_SYNC_BOOKINGS_FAILED_0392",
                    appContext.packageName,
                    AgendaFailureEvidence.describe(
                        error = error,
                        operation = "BACKGROUND_BOOKING_RECONCILE",
                        component = "AgendaBackgroundSync0392",
                        method = "runTenantCycle",
                    ),
                )
            }
        }

        try {
            reconcileTenantSeatAllocation0395(
                context = appContext,
                rotaCertaSeatAllocation = tenantSettings.rotaCertaSeatAllocation,
                seatAllocationVersion = tenantSettings.rotaCertaSeatAllocationVersion,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            failures++
            UnifiedDebugEventStore.record(
                "TENANT_SEAT_ALLOCATION_FANOUT_FAILED_0395",
                appContext.packageName,
                AgendaFailureEvidence.describe(
                    error = error,
                    operation = "TENANT_SEAT_ALLOCATION_FANOUT",
                    component = "AgendaBackgroundSync0392",
                    method = "runTenantCycle",
                ),
            )
        }

        try {
            outboxDelivered = TripMutationCoordinator0387(appContext, store).drainPending()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            failures++
            UnifiedDebugEventStore.record(
                "AGENDA_BACKGROUND_SYNC_OUTBOX_FAILED_0392",
                appContext.packageName,
                AgendaFailureEvidence.describe(
                    error = error,
                    operation = "BACKGROUND_OUTBOX_DRAIN",
                    component = "AgendaBackgroundSync0392",
                    method = "runTenantCycle",
                ),
            )
        }

        val reconcileAllCanonicalTrips = mode in setOf(
            AgendaBackgroundSyncMode0392.FULL_RECONCILE,
            AgendaBackgroundSyncMode0392.COLLECTOR_RECONCILE,
        )
        if (reconcileAllCanonicalTrips) {
            try {
                val allocation = tenantSettings.rotaCertaSeatAllocation
                val publicResult = PublicAgendaAutoSync0300.sync(
                    context = appContext,
                    store = store,
                    configuredVehicleCapacity = 0,
                    configuredRotaCertaSeatAllocation = allocation,
                )
                publicLocalPublished = publicResult.localPublished
                publicExternalPublished = publicResult.externalPublished
                failures += publicResult.failures
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                failures++
                UnifiedDebugEventStore.record(
                    "AGENDA_BACKGROUND_SYNC_PUBLIC_FAILED_0392",
                    appContext.packageName,
                    AgendaFailureEvidence.describe(
                        error = error,
                        operation = "BACKGROUND_PUBLIC_AGENDA_SYNC",
                        component = "AgendaBackgroundSync0392",
                        method = "runTenantCycle",
                    ),
                )
            }
        }

        runCatching {
            BookingPushRegistration0304.ensureRegistered(appContext, store)
        }

        BookingRealtimeEvents0356.notifyChanged()
        TripWidgetProvider.updateAll(appContext)

        UnifiedDebugEventStore.record(
            "AGENDA_BACKGROUND_SYNC_END_0392",
            appContext.packageName,
            "tenantKey=${seatSyncDiagnosticKey(tenantId)} reason=${reason.take(80)} trigger=${agendaBackgroundSyncTrigger0397(reason)} mode=${mode.name} bookingImports=$bookingImports outboxDelivered=$outboxDelivered localPublished=$publicLocalPublished externalPublished=$publicExternalPublished failures=$failures silentUi=true",
        )

        return AgendaBackgroundSyncRun0392(
            bookingImports = bookingImports,
            outboxDelivered = outboxDelivered,
            publicLocalPublished = publicLocalPublished,
            publicExternalPublished = publicExternalPublished,
            failures = failures,
        )
    }

    internal fun reason(workerParameters: WorkerParameters): String =
        workerParameters.inputData.getString(INPUT_REASON)?.takeIf(String::isNotBlank) ?: "periodic"

    internal fun scheduledTenantId(workerParameters: WorkerParameters): String =
        workerParameters.inputData.getString(INPUT_TENANT_ID)?.trim().orEmpty()

    internal fun currentTenantId(context: Context): String =
        RotaCertaTenantRegistry(context.applicationContext).activeScope().tenantId

    internal fun cancelStaleTenantPeriodic(context: Context, scheduledTenantId: String) {
        if (scheduledTenantId.isBlank()) return
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(tenantScopedWorkName(scheduledTenantId, PERIODIC_WORK))
        UnifiedDebugEventStore.record(
            "AGENDA_BACKGROUND_SYNC_STALE_TENANT_CANCELLED_0397",
            context.applicationContext.packageName,
            "scheduledTenantKey=${seatSyncDiagnosticKey(scheduledTenantId)} activeTenantKey=${seatSyncDiagnosticKey(currentTenantId(context))}",
        )
    }

    private fun tenantScopedWorkName(tenantId: String, base: String): String =
        "$base-${sha256TripPublication0387(tenantId).take(12)}"

    private fun networkConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}

class AgendaBackgroundSyncWorker0392(
    appContext: Context,
    private val parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val reason = AgendaBackgroundSync0392.reason(parameters)
        val scheduledTenantId = AgendaBackgroundSync0392.scheduledTenantId(parameters)
        val activeTenantId = AgendaBackgroundSync0392.currentTenantId(applicationContext)

        if (scheduledTenantId.isNotBlank() && scheduledTenantId != activeTenantId) {
            if (reason == "periodic") {
                AgendaBackgroundSync0392.cancelStaleTenantPeriodic(applicationContext, scheduledTenantId)
            }
            UnifiedDebugEventStore.record(
                "AGENDA_BACKGROUND_SYNC_TENANT_MISMATCH_0397",
                applicationContext.packageName,
                "workId=$id trigger=${agendaBackgroundSyncTrigger0397(reason)} scheduledTenantKey=${seatSyncDiagnosticKey(scheduledTenantId)} activeTenantKey=${seatSyncDiagnosticKey(activeTenantId)} result=SKIPPED",
            )
            return Result.success()
        }

        if (reason == "periodic" && !AgendaBackgroundSyncConfig0392.isEnabled(applicationContext)) {
            AgendaBackgroundSync0392.cancelPeriodic(applicationContext, "worker_disabled_guard")
            return Result.success()
        }

        val startedElapsed = android.os.SystemClock.elapsedRealtime()
        AgendaBackgroundSyncConfig0392.recordRunStarted(
            context = applicationContext,
            reason = reason,
            attempt = runAttemptCount,
        )
        UnifiedDebugEventStore.record(
            "AGENDA_BACKGROUND_SYNC_WORK_0397",
            applicationContext.packageName,
            "phase=START workId=$id trigger=${agendaBackgroundSyncTrigger0397(reason)} reason=${reason.take(80)} attempt=$runAttemptCount",
        )

        return try {
            val cycle = AgendaBackgroundSync0392.runCycle(
                context = applicationContext,
                reason = reason,
            )
            val retryPending = cycle.failures > 0 && runAttemptCount < 5
            val resultLabel = when {
                retryPending -> "RETRY"
                cycle.failures > 0 -> "PARTIAL_AFTER_MAX_RETRIES"
                else -> "SUCCESS"
            }
            AgendaBackgroundSyncConfig0392.recordRunFinished(
                context = applicationContext,
                reason = reason,
                result = resultLabel,
                failures = cycle.failures,
                retryPending = retryPending,
                attempt = runAttemptCount,
            )
            UnifiedDebugEventStore.record(
                "AGENDA_BACKGROUND_SYNC_WORK_0397",
                applicationContext.packageName,
                "phase=END workId=$id trigger=${agendaBackgroundSyncTrigger0397(reason)} result=$resultLabel durationMs=${android.os.SystemClock.elapsedRealtime() - startedElapsed} failures=${cycle.failures} retry=$retryPending attempt=$runAttemptCount",
            )
            if (retryPending) Result.retry() else Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val retryPending = runAttemptCount < 5
            AgendaBackgroundSyncConfig0392.recordRunFinished(
                context = applicationContext,
                reason = reason,
                result = if (retryPending) "RETRY_EXCEPTION" else "FAILED_AFTER_MAX_RETRIES",
                failures = 1,
                retryPending = retryPending,
                attempt = runAttemptCount,
            )
            UnifiedDebugEventStore.record(
                "AGENDA_BACKGROUND_SYNC_WORK_FAILED_0397",
                applicationContext.packageName,
                "workId=$id trigger=${agendaBackgroundSyncTrigger0397(reason)} retry=$retryPending attempt=$runAttemptCount " +
                    AgendaFailureEvidence.describe(
                        error = error,
                        operation = "BACKGROUND_WORKER",
                        component = "AgendaBackgroundSyncWorker0392",
                        method = "doWork",
                    ),
            )
            if (retryPending) Result.retry() else Result.success()
        }
    }
}
