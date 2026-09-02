package br.com.mapeiaia.rotacerta.trips

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
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
import java.time.Instant
import java.time.ZoneId
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
    val collectorGeneration: Long = 0L,
    val collectorStatus: String = "NOT_REQUESTED",
    val collectorPending: Boolean = false,
    val collectorChangedTrips: Int = 0,
    val collectorSkippedTrips: Int = 0,
    val collectorPublicationQueued: Int = 0,
    val collectorMissingPreserved: Int = 0,
    val collectorTombstonedTrips: Int = 0,
    val collectorOrphanProjectionTombstones: Int = 0,
    val collectorStaleResultsRejected: Int = 0,
    val projectionMissingAgenda: Int = 0,
    val projectionDuplicates: Int = 0,
    val projectionRevisionMismatch: Int = 0,
    val projectionHashMismatch: Int = 0,
    val projectionCapacityMismatch: Int = 0,
    val projectionStatusMismatch: Int = 0,
    val projectionRevisionRegression: Int = 0,
    val projectionOrphans: Int = 0,
    val projectionFailures: Int = 0,
    val projectionValidated0411: Int = 0,
    val projectionPending0411: Int = 0,
    val projectionDivergent0411: Int = 0,
    val projectionInvalidIdentity0411: Int = 0,
    val projectionInvalidLink0411: Int = 0,
    val projectionStaleRevision0411: Int = 0,
    val projectionReadbackFailures0411: Int = 0,
    val projectionReadbackLatencyMillis0411: Long = 0L,
)

internal data class AgendaAutomaticCollectorState0400(
    val generation: Long = 0L,
    val completedGeneration: Long = 0L,
    val requestedAtMillis: Long = 0L,
    val status: String = "IDLE",
    val targetAccountIds: List<String> = emptyList(),
    val completedAccountIds: List<String> = emptyList(),
    val failedAccountIds: List<String> = emptyList(),
    val pendingAuthAccountIds: List<String> = emptyList(),
    val activeAccountId: String = "",
    val lastError: String = "",
) {
    val pending: Boolean
        get() = generation > completedGeneration && targetAccountIds.isNotEmpty()
}

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

internal fun agendaBackgroundSyncRefreshesCoverageCheckpoint0403(reason: String): Boolean =
    reason == "periodic" ||
        reason == "blablacar_collection_result" ||
        agendaBackgroundSyncMode0392(reason) == AgendaBackgroundSyncMode0392.FULL_RECONCILE

internal fun agendaBackgroundSyncForegroundInfo0402(context: Context, reason: String): ForegroundInfo {
    val appContext = context.applicationContext
    val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        manager.createNotificationChannel(
            NotificationChannel(
                "rota_certa_automatic_sync_0402",
                "Sincronização automática",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Mantém a sincronização da Agenda de Viagens em andamento sem abrir telas."
                setShowBadge(false)
            },
        )
    }
    val notification = NotificationCompat.Builder(appContext, "rota_certa_automatic_sync_0402")
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setContentTitle("Rota Certa · sincronização automática")
        .setContentText("Atualizando viagens em segundo plano")
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .build()
    val tenantKey = seatSyncDiagnosticKey(RotaCertaTenantRegistry(appContext).activeScope().tenantId)
    val notificationId = 4020 + (tenantKey.hashCode() and 0x3ff)
    UnifiedDebugEventStore.record(
        "AGENDA_BACKGROUND_SYNC_FOREGROUND_0402",
        appContext.packageName,
        "reason=${reason.take(80)} serviceType=dataSync notificationOnly=true activityLaunch=false browserOpened=false",
    )
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
        ForegroundInfo(notificationId, notification)
    }
}

internal fun agendaBackgroundSyncMode0392(reason: String): AgendaBackgroundSyncMode0392 = when {
    reason == "periodic" -> AgendaBackgroundSyncMode0392.COLLECTOR_RECONCILE
    reason == "manual" -> AgendaBackgroundSyncMode0392.FULL_RECONCILE
    reason == "recovery" -> AgendaBackgroundSyncMode0392.FULL_RECONCILE
    reason == "timeline_open" -> AgendaBackgroundSyncMode0392.DELTA_ONLY
    reason == "timeline_pull_refresh" -> AgendaBackgroundSyncMode0392.DELTA_ONLY
    reason.startsWith("booking_push:") -> AgendaBackgroundSyncMode0392.BOOKING_EVENT
    reason == "blablacar_collection_result" -> AgendaBackgroundSyncMode0392.COLLECTOR_RECONCILE
    reason == "trip_reverify" -> AgendaBackgroundSyncMode0392.COLLECTOR_RECONCILE
    else -> AgendaBackgroundSyncMode0392.DELTA_ONLY
}

internal fun agendaBackgroundSyncTrigger0397(reason: String): String = when {
    reason == "periodic" -> "PERIODIC"
    reason == "manual" -> "MANUAL"
    reason == "timeline_pull_refresh" -> "PULL_TO_REFRESH"
    reason == "recovery" || reason == "timeline_open" -> "RECOVERY"
    reason.startsWith("booking_push:") -> "EVENT_DELTA"
    reason == "blablacar_collection_result" -> "AUTOMATIC_COLLECTOR"
    reason == "trip_reverify" -> "TRIP_REVERIFY"
    else -> "EVENT_DELTA"
}

internal data class AgendaBackgroundSyncStatus0397(
    val enabled: Boolean,
    val intervalMinutes: Long,
    val scheduledAtMillis: Long,
    val lastStartedAtMillis: Long,
    val lastFinishedAtMillis: Long,
    val lastPeriodicFinishedAtMillis: Long,
    val lastFullReconcileFinishedAtMillis: Long,
    val lastTrigger: String,
    val lastResult: String,
    val retryPending: Boolean,
    val retryAttempt: Int,
    val lastFailures: Int,
    val runId: String = "",
    val runState: String = "IDLE",
    val heartbeatAtMillis: Long = 0L,
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
    private const val KEY_LAST_FULL_RECONCILE_FINISHED = "last_full_reconcile_finished_at_0397"
    private const val KEY_LAST_TRIGGER = "last_trigger_0397"
    private const val KEY_LAST_RESULT = "last_result_0397"
    private const val KEY_RETRY_PENDING = "retry_pending_0397"
    private const val KEY_RETRY_ATTEMPT = "retry_attempt_0397"
    private const val KEY_LAST_FAILURES = "last_failures_0397"
    private const val KEY_RUN_ID = "run_id_0406"
    private const val KEY_RUN_STATE = "run_state_0406"
    private const val KEY_HEARTBEAT = "heartbeat_at_0406"
    internal const val RUN_LEASE_MILLIS_0406 = 45L * 60L * 1000L
    private const val KEY_COLLECTOR_GENERATION = "collector_generation_0400"
    private const val KEY_COLLECTOR_COMPLETED_GENERATION = "collector_completed_generation_0400"
    private const val KEY_COLLECTOR_REQUESTED_AT = "collector_requested_at_0400"
    private const val KEY_COLLECTOR_STATUS = "collector_status_0400"
    private const val KEY_COLLECTOR_TARGETS = "collector_targets_0400"
    private const val KEY_COLLECTOR_COMPLETED = "collector_completed_0400"
    private const val KEY_COLLECTOR_FAILED = "collector_failed_0400"
    private const val KEY_COLLECTOR_PENDING_AUTH = "collector_pending_auth_0401"
    private const val KEY_COLLECTOR_ACTIVE = "collector_active_0400"
    private const val KEY_COLLECTOR_LAST_ERROR = "collector_last_error_0400"

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
        recoverStalledRun0406(context)
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
            lastFullReconcileFinishedAtMillis = prefs.getLong(scope.key(KEY_LAST_FULL_RECONCILE_FINISHED), 0L),
            lastTrigger = prefs.getString(scope.key(KEY_LAST_TRIGGER), "").orEmpty(),
            lastResult = prefs.getString(scope.key(KEY_LAST_RESULT), "Ainda não executada").orEmpty(),
            retryPending = prefs.getBoolean(scope.key(KEY_RETRY_PENDING), false),
            retryAttempt = prefs.getInt(scope.key(KEY_RETRY_ATTEMPT), 0),
            lastFailures = prefs.getInt(scope.key(KEY_LAST_FAILURES), 0),
            runId = prefs.getString(scope.key(KEY_RUN_ID), "").orEmpty(),
            runState = prefs.getString(scope.key(KEY_RUN_STATE), "IDLE").orEmpty(),
            heartbeatAtMillis = prefs.getLong(scope.key(KEY_HEARTBEAT), 0L),
        )
    }

    internal fun recoverStalledRun0406(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean = synchronized(this) {
        val prefs = prefs(context)
        val scope = scope(context)
        val result = prefs.getString(scope.key(KEY_LAST_RESULT), "").orEmpty()
        val state = prefs.getString(scope.key(KEY_RUN_STATE), "").orEmpty()
        val heartbeat = prefs.getLong(scope.key(KEY_HEARTBEAT), 0L)
        val started = prefs.getLong(scope.key(KEY_LAST_STARTED), 0L)
        if (!syncRunIsStalled0406(result, state, heartbeat, started, nowMillis, RUN_LEASE_MILLIS_0406)) {
            return@synchronized false
        }
        require(
            prefs.edit()
                .putString(scope.key(KEY_LAST_RESULT), "STALLED")
                .putString(scope.key(KEY_RUN_STATE), "RECOVERING")
                .putBoolean(scope.key(KEY_RETRY_PENDING), true)
                .putInt(scope.key(KEY_LAST_FAILURES), maxOf(1, prefs.getInt(scope.key(KEY_LAST_FAILURES), 0)))
                .putLong(scope.key(KEY_HEARTBEAT), nowMillis)
                .commit(),
        ) { "Falha ao recuperar sincronização abandonada." }
        UnifiedDebugEventStore.record(
            "AGENDA_BACKGROUND_SYNC_WATCHDOG_0406",
            context.applicationContext.packageName,
            "runId=" + prefs.getString(scope.key(KEY_RUN_ID), "").orEmpty().take(80) +
                " previousState=" + state + " previousResult=" + result +
                " action=MARK_STALLED_RECOVERING leaseMs=" + RUN_LEASE_MILLIS_0406,
        )
        true
    }

    internal fun recordRunHeartbeat0406(
        context: Context,
        state: String,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val prefs = prefs(context)
        val scope = scope(context)
        prefs.edit()
            .putString(scope.key(KEY_RUN_STATE), state.take(40))
            .putLong(scope.key(KEY_HEARTBEAT), nowMillis)
            .apply()
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

    internal fun collectorState0400(context: Context): AgendaAutomaticCollectorState0400 {
        val prefs = prefs(context)
        val scope = scope(context)
        return AgendaAutomaticCollectorState0400(
            generation = prefs.getLong(scope.key(KEY_COLLECTOR_GENERATION), 0L),
            completedGeneration = prefs.getLong(scope.key(KEY_COLLECTOR_COMPLETED_GENERATION), 0L),
            requestedAtMillis = prefs.getLong(scope.key(KEY_COLLECTOR_REQUESTED_AT), 0L),
            status = prefs.getString(scope.key(KEY_COLLECTOR_STATUS), "IDLE").orEmpty(),
            targetAccountIds = parseCollectorIds0400(prefs.getString(scope.key(KEY_COLLECTOR_TARGETS), null)),
            completedAccountIds = parseCollectorIds0400(prefs.getString(scope.key(KEY_COLLECTOR_COMPLETED), null)),
            failedAccountIds = parseCollectorIds0400(prefs.getString(scope.key(KEY_COLLECTOR_FAILED), null)),
            pendingAuthAccountIds = parseCollectorIds0400(prefs.getString(scope.key(KEY_COLLECTOR_PENDING_AUTH), null)),
            activeAccountId = prefs.getString(scope.key(KEY_COLLECTOR_ACTIVE), "").orEmpty(),
            lastError = prefs.getString(scope.key(KEY_COLLECTOR_LAST_ERROR), "").orEmpty(),
        )
    }

    internal fun requestAutomaticCollector0400(
        context: Context,
        accountIds: Collection<String>,
        nowMillis: Long = System.currentTimeMillis(),
    ): AgendaAutomaticCollectorState0400 = synchronized(this) {
        val current = collectorState0400(context)
        if (current.pending) return@synchronized current
        val targets = accountIds.map(String::trim).filter(String::isNotBlank).distinct()
        val generation = maxOf(current.generation, current.completedGeneration) + 1L
        val status = if (targets.isEmpty()) "NO_ACCOUNTS" else "PENDING"
        val completedGeneration = if (targets.isEmpty()) generation else current.completedGeneration
        val prefs = prefs(context)
        val scope = scope(context)
        require(
            prefs.edit()
                .putLong(scope.key(KEY_COLLECTOR_GENERATION), generation)
                .putLong(scope.key(KEY_COLLECTOR_COMPLETED_GENERATION), completedGeneration)
                .putLong(scope.key(KEY_COLLECTOR_REQUESTED_AT), nowMillis)
                .putString(scope.key(KEY_COLLECTOR_STATUS), status)
                .putString(scope.key(KEY_COLLECTOR_TARGETS), encodeCollectorIds0400(targets))
                .putString(scope.key(KEY_COLLECTOR_COMPLETED), "")
                .putString(scope.key(KEY_COLLECTOR_FAILED), "")
                .putString(scope.key(KEY_COLLECTOR_PENDING_AUTH), "")
                .putString(scope.key(KEY_COLLECTOR_ACTIVE), "")
                .putString(scope.key(KEY_COLLECTOR_LAST_ERROR), "")
                .commit(),
        ) { "Falha ao persistir pedido da coleta BlaBlaCar automática." }
        collectorState0400(context)
    }

    internal fun claimCollectorAccount0400(
        context: Context,
        generation: Long,
        accountId: String,
    ): Boolean = synchronized(this) {
        val current = collectorState0400(context)
        val target = accountId.trim()
        if (
            !current.pending ||
            current.generation != generation ||
            current.activeAccountId.isNotBlank() ||
            target !in current.targetAccountIds ||
            target in current.completedAccountIds ||
            target in current.failedAccountIds ||
            target in current.pendingAuthAccountIds
        ) return@synchronized false
        val prefs = prefs(context)
        val scope = scope(context)
        prefs.edit()
            .putString(scope.key(KEY_COLLECTOR_ACTIVE), target)
            .putString(scope.key(KEY_COLLECTOR_STATUS), "RUNNING")
            .commit()
    }

    internal fun recordCollectorAccountFinished0400(
        context: Context,
        generation: Long,
        accountId: String,
        result: String,
        error: String = "",
    ): AgendaAutomaticCollectorState0400 = synchronized(this) {
        val current = collectorState0400(context)
        if (current.generation != generation || !current.pending) return@synchronized current
        val id = accountId.trim()
        val completed = current.completedAccountIds.toMutableSet()
        val failed = current.failedAccountIds.toMutableSet()
        val pendingAuth = current.pendingAuthAccountIds.toMutableSet()
        when (result) {
            "COMPLETE" -> { failed.remove(id); pendingAuth.remove(id); completed += id }
            "PENDING_AUTH" -> { completed.remove(id); failed.remove(id); pendingAuth += id }
            else -> { completed.remove(id); pendingAuth.remove(id); failed += id }
        }
        val prefs = prefs(context)
        val scope = scope(context)
        require(
            prefs.edit()
                .putString(scope.key(KEY_COLLECTOR_COMPLETED), encodeCollectorIds0400(completed))
                .putString(scope.key(KEY_COLLECTOR_FAILED), encodeCollectorIds0400(failed))
                .putString(scope.key(KEY_COLLECTOR_PENDING_AUTH), encodeCollectorIds0400(pendingAuth))
                .putString(scope.key(KEY_COLLECTOR_ACTIVE), "")
                .putString(scope.key(KEY_COLLECTOR_STATUS), when (result) { "INTERRUPTED" -> "INTERRUPTED"; "PENDING_AUTH" -> "PENDING_AUTH"; else -> "PENDING" })
                .putString(scope.key(KEY_COLLECTOR_LAST_ERROR), error.take(500))
                .commit(),
        ) { "Falha ao persistir avanço da coleta BlaBlaCar automática." }
        collectorState0400(context)
    }

    internal fun recoverStaleCollectorHost0401(context: Context): AgendaAutomaticCollectorState0400 = synchronized(this) {
        val current = collectorState0400(context)
        if (!current.pending || current.activeAccountId.isBlank()) return@synchronized current
        val prefs = prefs(context)
        val scope = scope(context)
        require(
            prefs.edit()
                .putString(scope.key(KEY_COLLECTOR_ACTIVE), "")
                .putString(scope.key(KEY_COLLECTOR_STATUS), "PENDING")
                .putString(scope.key(KEY_COLLECTOR_LAST_ERROR), "previous_headless_host_not_alive_recovered")
                .commit(),
        ) { "Falha ao recuperar coleta BlaBlaCar interrompida." }
        UnifiedDebugEventStore.record(
            "BLABLACAR_AUTOMATIC_HOST_RECOVERED_0401", context.applicationContext.packageName,
            "generation=${current.generation} accountKey=${seatSyncDiagnosticKey(current.activeAccountId)} processDeathRecovery=true rerunSameAccount=true browserOpened=false",
        )
        collectorState0400(context)
    }

    internal fun markCollectorLaunchInterrupted0400(
        context: Context,
        generation: Long,
        error: String,
    ): AgendaAutomaticCollectorState0400 = synchronized(this) {
        val current = collectorState0400(context)
        if (current.generation != generation || !current.pending) return@synchronized current
        val prefs = prefs(context)
        val scope = scope(context)
        prefs.edit()
            .putString(scope.key(KEY_COLLECTOR_ACTIVE), "")
            .putString(scope.key(KEY_COLLECTOR_STATUS), "INTERRUPTED")
            .putString(scope.key(KEY_COLLECTOR_LAST_ERROR), error.take(500))
            .commit()
        collectorState0400(context)
    }

    internal fun finishCollectorRun0400(
        context: Context,
        generation: Long,
        result: String,
        error: String = "",
    ): AgendaAutomaticCollectorState0400 = synchronized(this) {
        val current = collectorState0400(context)
        if (current.generation != generation) return@synchronized current
        val prefs = prefs(context)
        val scope = scope(context)
        require(
            prefs.edit()
                .putLong(scope.key(KEY_COLLECTOR_COMPLETED_GENERATION), generation)
                .putString(scope.key(KEY_COLLECTOR_ACTIVE), "")
                .putString(scope.key(KEY_COLLECTOR_STATUS), result.take(80))
                .putString(scope.key(KEY_COLLECTOR_LAST_ERROR), error.take(500))
                .commit(),
        ) { "Falha ao persistir término da coleta BlaBlaCar automática." }
        collectorState0400(context)
    }

    private fun parseCollectorIds0400(raw: String?): List<String> =
        raw.orEmpty().split(',').map(String::trim).filter(String::isNotBlank).distinct()

    private fun encodeCollectorIds0400(ids: Collection<String>): String =
        ids.map(String::trim).filter(String::isNotBlank).distinct().joinToString(",")

    internal fun recordRunStarted(context: Context, reason: String, attempt: Int, nowMillis: Long = System.currentTimeMillis()) {
        recoverStalledRun0406(context, nowMillis)
        val prefs = prefs(context)
        val scope = scope(context)
        val runId = "sync-" + nowMillis + "-" + attempt
        prefs.edit()
            .putLong(scope.key(KEY_LAST_STARTED), nowMillis)
            .putString(scope.key(KEY_LAST_TRIGGER), agendaBackgroundSyncTrigger0397(reason))
            .putString(scope.key(KEY_LAST_RESULT), "RUNNING")
            .putString(scope.key(KEY_RUN_ID), runId)
            .putString(scope.key(KEY_RUN_STATE), "COLLECTING")
            .putLong(scope.key(KEY_HEARTBEAT), nowMillis)
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
        fullReconcileComplete: Boolean = true,
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
            .putString(scope.key(KEY_RUN_STATE), when {
                result == "VERIFIED" -> "COMPLETE"
                result.startsWith("FAILED") || result == "STALLED" -> "FAILED"
                result.startsWith("RETRY") || retryPending -> "RECOVERING"
                else -> "COMPLETE"
            })
            .putLong(scope.key(KEY_HEARTBEAT), nowMillis)
        if (reason == "periodic") {
            editor.putLong(scope.key(KEY_LAST_PERIODIC_FINISHED), nowMillis)
        }
        if (
            fullReconcileComplete &&
            failures == 0 &&
            !retryPending &&
            agendaBackgroundSyncRefreshesCoverageCheckpoint0403(reason)
        ) {
            editor.putLong(scope.key(KEY_LAST_FULL_RECONCILE_FINISHED), nowMillis)
        }
        editor.apply()
    }
}

internal enum class ExternalCollectorDeltaDecision0403 {
    UPDATE_CANONICAL,
    SKIP_UNCHANGED,
    PRESERVE_PARTIAL,
}

internal data class ExternalCollectorCanonicalBatch0403(
    val changedTrips: Int = 0,
    val skippedTrips: Int = 0,
    val publicationQueued: Int = 0,
    val blockedTrips: Int = 0,
    val missingPreserved: Int = 0,
    val tombstonedTrips: Int = 0,
    val orphanProjectionTombstones: Int = 0,
    val staleResultsRejected: Int = 0,
)

internal fun syncRunIsStalled0406(
    lastResult: String,
    runState: String,
    heartbeatAtMillis: Long,
    startedAtMillis: Long,
    nowMillis: Long,
    leaseMillis: Long,
): Boolean {
    if (lastResult != "RUNNING" && runState !in setOf("COLLECTING", "NORMALIZING", "RECONCILING", "COMMITTING", "PROJECTING", "VERIFYING")) {
        return false
    }
    val anchor = maxOf(heartbeatAtMillis, startedAtMillis)
    return anchor > 0L && nowMillis - anchor > leaseMillis.coerceAtLeast(1L)
}

internal fun externalCollectorAllowsTombstones0406(response: BlaBlaCollectorMonthResponse?): Boolean {
    if (response == null) return false
    val status = response.status.trim().lowercase()
    return response.coverage.complete_for_scope &&
        response.coverage.global_profile_month_complete &&
        status in setOf("success", "validated", "complete")
}

internal fun completeCollectorProfileUuids0408(
    context: Context,
    state: AgendaAutomaticCollectorState0400,
): Set<String> {
    if (state.completedAccountIds.isEmpty()) return emptySet()
    val accounts = BlaBlaDynamicAccountRegistry(context.applicationContext).list().associateBy { it.id }
    val sessions = BlaBlaDynamicSessionStore(context.applicationContext)
    return state.completedAccountIds.mapNotNull { accountId ->
        val account = accounts[accountId] ?: return@mapNotNull null
        val profileUuid = account.profileUuid?.trim()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        val snapshot = sessions.read(account) ?: return@mapNotNull null
        if (
            snapshot.identityVerified &&
            snapshot.profileUuid?.trim()?.equals(profileUuid, ignoreCase = true) == true &&
            snapshot.skippedTrips == 0
        ) profileUuid.lowercase() else null
    }.toSet()
}

internal fun externalCanonicalTripWithinCompleteScope0408(
    trip: Trip,
    response: BlaBlaCollectorMonthResponse,
    completeProfileUuids: Set<String>,
): Boolean {
    val tripProfile = trip.blablaProfileUuid.orEmpty().trim().lowercase()
    if (tripProfile.isNotBlank() && tripProfile in completeProfileUuids) return true
    return externalCollectorAllowsTombstones0406(response) &&
        externalCanonicalTripWithinCompleteScope0406(trip, response)
}

internal fun externalCanonicalTripWithinCompleteScope0406(
    trip: Trip,
    response: BlaBlaCollectorMonthResponse,
): Boolean {
    val month = response.month.orEmpty().trim()
    val profileScope = response.profiles.map { it.uuid.trim().lowercase() }.filter(String::isNotBlank).toSet()
    val tripProfile = trip.blablaProfileUuid.orEmpty().trim().lowercase()
    val date = trip.externalSnapshot?.date.orEmpty()
    return month.isNotBlank() && profileScope.isNotEmpty() &&
        tripProfile in profileScope && date.startsWith(month)
}

internal data class ProjectionIntegrity0406(
    val canonicalActive: Int = 0,
    val agendaProjections: Int = 0,
    val missingAgenda: Int = 0,
    val duplicates: Int = 0,
    val revisionMismatch: Int = 0,
    val hashMismatch: Int = 0,
    val capacityMismatch: Int = 0,
    val statusMismatch: Int = 0,
    val revisionRegression: Int = 0,
    val orphans: Int = 0,
    val repairQueued: Int = 0,
    val failures: Int = 0,
    val attestationValidated0411: Int = 0,
    val attestationPending0411: Int = 0,
    val attestationDivergent0411: Int = 0,
    val attestationInvalidIdentity0411: Int = 0,
    val attestationInvalidLink0411: Int = 0,
    val attestationStaleRevision0411: Int = 0,
    val attestationReadbackFailures0411: Int = 0,
    val attestationReadbackLatencyMillis0411: Long = 0L,
) {
    val verified: Boolean
        get() = failures == 0 &&
            missingAgenda == 0 &&
            duplicates == 0 &&
            revisionMismatch == 0 &&
            hashMismatch == 0 &&
            capacityMismatch == 0 &&
            statusMismatch == 0 &&
            revisionRegression == 0 &&
            orphans == 0 &&
            attestationPending0411 == 0 &&
            attestationDivergent0411 == 0 &&
            attestationInvalidIdentity0411 == 0 &&
            attestationInvalidLink0411 == 0 &&
            attestationStaleRevision0411 == 0 &&
            attestationReadbackFailures0411 == 0 &&
            attestationValidated0411 == canonicalActive
}

internal fun remoteMatchesCanonicalProjection0408(
    canonical: Trip,
    remote: DriverTripSyncState0402,
): Boolean {
    if (remote.canonicalTripId.isNotBlank() && remote.canonicalTripId == canonical.id) return true
    if (canonical.tripKey.isNotBlank() && remote.tripKey.isNotBlank() && remote.tripKey == canonical.tripKey) return true
    val profileUuid = canonical.blablaProfileUuid.orEmpty().trim()
    val blablaTripId = canonical.blablaTripId.orEmpty().trim()
    return profileUuid.isNotBlank() &&
        blablaTripId.isNotBlank() &&
        remote.blablaProfileUuid.trim().equals(profileUuid, ignoreCase = true) &&
        remote.blablaTripId.trim() == blablaTripId
}

internal fun chooseProjectionWinner0408(
    canonical: Trip,
    preferredRemoteId: String?,
    candidates: List<DriverTripSyncState0402>,
): DriverTripSyncState0402? = candidates.maxWithOrNull(
    compareBy<DriverTripSyncState0402> {
        canonical.canonicalStateHash.isNotBlank() && it.canonicalStateHash == canonical.canonicalStateHash
    }
        .thenBy { it.publicationRevision }
        .thenBy { it.occupancyRevision }
        .thenBy { it.canonicalTripId == canonical.id }
        .thenBy { it.remoteTripId == preferredRemoteId }
        .thenBy { it.remoteTripId },
)

internal fun canonicalProjectionAvailabilityRange0408(
    trip: Trip,
    bookings: List<Booking>,
    nowMillis: Long,
): SeatAvailabilityRange {
    val capacity = operationalInventoryCapacity(trip, bookings)
    val loads = SeatAvailabilityEngine.segmentLoads(
        trip.copy(capacity = capacity),
        bookings,
        nowMillis,
    )
    return SeatAvailabilityRange(
        minimum = loads.minOfOrNull(SegmentLoad::availableSeats) ?: capacity,
        maximum = loads.maxOfOrNull(SegmentLoad::availableSeats) ?: capacity,
    )
}

internal fun expectedProjectionStatus0408(
    trip: Trip,
    bookings: List<Booking>,
    nowMillis: Long,
): String {
    if (trip.status !in setOf(TripStatus.PUBLISHED, TripStatus.FULL)) return trip.status.name
    val capacity = operationalInventoryCapacity(trip, bookings)
    val loads = SeatAvailabilityEngine.segmentLoads(
        trip.copy(capacity = capacity),
        bookings,
        nowMillis,
    )
    val globallyFull = loads.isNotEmpty() && loads.all { it.occupiedSeats >= capacity }
    return if (globallyFull) TripStatus.FULL.name else TripStatus.PUBLISHED.name
}

internal fun projectionCapacityMatches0408(
    trip: Trip,
    bookings: List<Booking>,
    remote: DriverTripSyncState0402,
    nowMillis: Long,
): Boolean {
    if (!trip.capacityReliable) return true
    val expectedCapacity = operationalInventoryCapacity(trip, bookings)
    val expectedRange = canonicalProjectionAvailabilityRange0408(trip, bookings, nowMillis)
    val baseMatches = remote.capacityReliable && remote.capacity == expectedCapacity
    val extendedAvailabilityPresent =
        remote.rotaCertaSeatAllocation != null ||
            remote.operationalAvailableSeats != null ||
            remote.availableSeatsMinimum != null ||
            remote.availableSeatsMaximum != null ||
            remote.occupancyRevision != null
    if (!extendedAvailabilityPresent) return baseMatches
    val expectedPublishedSeats = trip.publishedSeats
    val expectedRotaCertaSeats = trip.rotaCertaSeatAllocation?.takeIf { it in 0..999 } ?: 0
    return baseMatches &&
        remote.publishedSeats == expectedPublishedSeats &&
        remote.rotaCertaSeatAllocation == expectedRotaCertaSeats &&
        remote.operationalAvailableSeats == expectedRange.minimum &&
        remote.availableSeatsMinimum == expectedRange.minimum &&
        remote.availableSeatsMaximum == expectedRange.maximum
}

internal fun remoteProjectionWithinCompleteScope0408(
    remote: DriverTripSyncState0402,
    response: BlaBlaCollectorMonthResponse?,
    completeProfileUuids: Set<String>,
): Boolean {
    val profileUuid = remote.blablaProfileUuid.trim().lowercase()
    if (profileUuid.isNotBlank() && profileUuid in completeProfileUuids) return true
    if (!externalCollectorAllowsTombstones0406(response) || response == null) return false
    val month = response.month.orEmpty().trim()
    val profileScope = response.profiles
        .map { it.uuid.trim().lowercase() }
        .filter(String::isNotBlank)
        .toSet()
    val remoteMonth = runCatching {
        Instant.ofEpochMilli(remote.departureAtMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()
            .take(7)
    }.getOrDefault("")
    return month.isNotBlank() &&
        remoteMonth == month &&
        profileUuid in profileScope
}

internal fun externalCollectorDeltaDecision0403(
    existingFingerprint: String,
    incomingFingerprint: String,
    existingComplete: Boolean,
    incomingComplete: Boolean,
): ExternalCollectorDeltaDecision0403 = when {
    incomingFingerprint.isNotBlank() && incomingFingerprint == existingFingerprint ->
        ExternalCollectorDeltaDecision0403.SKIP_UNCHANGED
    existingComplete && !incomingComplete ->
        ExternalCollectorDeltaDecision0403.PRESERVE_PARTIAL
    else -> ExternalCollectorDeltaDecision0403.UPDATE_CANONICAL
}

internal fun targetedCollectorResponse0407(
    response: BlaBlaCollectorMonthResponse?,
    target: BlaBlaTripTarget0407?,
): BlaBlaCollectorMonthResponse? {
    if (response == null || target == null) return response
    val exact = response.trips.filter { source ->
        source.profile_uuid.trim().equals(target.profileUuid.trim(), ignoreCase = true) &&
            source.trip_id?.trim() == target.tripId &&
            BlaBlaCollectorUrlModule.tripId(source.trip_href.orEmpty()) == target.tripId
    }
    return response.copy(
        status = if (exact.size == 1) "validated" else "partial",
        trips = exact.takeIf { it.size == 1 }.orEmpty(),
        coverage = response.coverage.copy(
            complete_for_scope = false,
            global_profile_month_complete = false,
            reason = if (exact.size == 1) "targeted_trip_reverify" else "targeted_trip_missing_or_ambiguous",
            unresolved_target_cards = if (exact.size == 1) 0 else 1,
        ),
    )
}

internal object AgendaBackgroundSync0392 {
    private const val PERIODIC_WORK = "agenda-background-sync-0392-periodic"
    private const val IMMEDIATE_WORK = "agenda-background-sync-0392-immediate"
    private const val TRIP_REVERIFY_WORK_0407 = "agenda-background-sync-0407-trip-reverify"
    private const val INPUT_REASON = "reason"
    private const val INPUT_TENANT_ID = "tenant_id_0397"
    private const val INPUT_COMMAND_ID_0407 = "command_id_0407"
    private const val INPUT_ACCOUNT_ID_0407 = "account_id_0407"
    private const val INPUT_PROFILE_UUID_0407 = "profile_uuid_0407"
    private const val INPUT_TRIP_ID_0407 = "trip_id_0407"
    private const val INPUT_TRIP_HREF_0407 = "trip_href_0407"
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

    internal data class TargetedTripWork0407(
        val commandId: String,
        val target: BlaBlaTripTarget0407,
    )

    fun enqueueTripReverify0407(
        context: Context,
        target: BlaBlaTripTarget0407,
        commandId: String,
        requestedAtMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val appContext = context.applicationContext
        val activeTenantId = RotaCertaTenantRegistry(appContext).activeScope().tenantId
        if (activeTenantId != target.tenantId || commandId.isBlank()) return false
        if (BlaBlaCollectorUrlModule.tripId(target.tripHref) != target.tripId) return false

        val commandStore = BlaBlaTripCommandStatusStore0407(appContext)
        if (!commandStore.tryMarkQueued(target, commandId, requestedAtMillis)) {
            UnifiedDebugEventStore.record(
                "NO_OP",
                appContext.packageName,
                "commandKey=${seatSyncDiagnosticKey(commandId)} targetKey=${seatSyncDiagnosticKey(target.strongIdentityKey)} capability=REVERIFY_TRIP reason=single_flight_already_pending",
            )
            return true
        }

        val request = OneTimeWorkRequestBuilder<AgendaBackgroundSyncWorker0392>()
            .setConstraints(networkConstraints())
            .setInputData(workDataOf(
                INPUT_REASON to "trip_reverify",
                INPUT_TENANT_ID to target.tenantId,
                INPUT_COMMAND_ID_0407 to commandId,
                INPUT_ACCOUNT_ID_0407 to target.accountId,
                INPUT_PROFILE_UUID_0407 to target.profileUuid,
                INPUT_TRIP_ID_0407 to target.tripId,
                INPUT_TRIP_HREF_0407 to target.tripHref,
            ))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WORK_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        val workName = tenantScopedWorkName(
            target.tenantId,
            TRIP_REVERIFY_WORK_0407 + "-" + sha256TripPublication0387(target.strongIdentityKey).take(16),
        )
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.KEEP,
            request,
        )
        UnifiedDebugEventStore.record(
            "COMMAND_REQUESTED",
            appContext.packageName,
            "commandKey=${seatSyncDiagnosticKey(commandId)} targetKey=${seatSyncDiagnosticKey(target.strongIdentityKey)} capability=REVERIFY_TRIP status=QUEUED workId=${request.id} centralWorker=true uniqueTargetWork=true",
        )
        return true
    }

    internal fun targetedTripWork0407(workerParameters: WorkerParameters): TargetedTripWork0407? {
        if (reason(workerParameters) != "trip_reverify") return null
        val tenantId = scheduledTenantId(workerParameters)
        val commandId = workerParameters.inputData.getString(INPUT_COMMAND_ID_0407)?.trim().orEmpty()
        val accountId = workerParameters.inputData.getString(INPUT_ACCOUNT_ID_0407)?.trim().orEmpty()
        val profileUuid = workerParameters.inputData.getString(INPUT_PROFILE_UUID_0407)?.trim()?.lowercase().orEmpty()
        val tripId = workerParameters.inputData.getString(INPUT_TRIP_ID_0407)?.trim().orEmpty()
        val tripHref = workerParameters.inputData.getString(INPUT_TRIP_HREF_0407)?.trim().orEmpty()
        if (tenantId.isBlank() || commandId.isBlank() || accountId.isBlank() || profileUuid.isBlank() || tripId.isBlank() || tripHref.isBlank()) return null
        if (BlaBlaCollectorUrlModule.tripId(tripHref) != tripId) return null
        return TargetedTripWork0407(
            commandId = commandId,
            target = BlaBlaTripTarget0407(tenantId, accountId, profileUuid, tripId, tripHref),
        )
    }
    fun enqueueRecoveryIfNeeded(context: Context) {
        val appContext = context.applicationContext
        val status = AgendaBackgroundSyncConfig0392.status(appContext)
        if (!status.enabled) return
        val now = System.currentTimeMillis()
        val staleAfterMillis = status.intervalMinutes.coerceAtLeast(AgendaBackgroundSyncConfig0392.MIN_INTERVAL_MINUTES) * 60_000L
        val lastValidFullReconcile = status.lastFullReconcileFinishedAtMillis
        val stale = lastValidFullReconcile <= 0L || now - lastValidFullReconcile >= staleAfterMillis
        if (stale) {
            enqueueImmediate(appContext, "recovery")
            UnifiedDebugEventStore.record(
                "AGENDA_BACKGROUND_SYNC_RECOVERY_0397",
                appContext.packageName,
                "trigger=RECOVERY stale=true lastValidFullReconcileAt=$lastValidFullReconcile intervalMinutes=${status.intervalMinutes}",
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

    internal fun reconcileCollectedExternalTrips0403(
        context: Context,
        store: TripStore,
        response: BlaBlaCollectorMonthResponse?,
        rotaCertaSeatAllocation: Int,
        seatAllocationVersion: Long,
        nowMillis: Long = System.currentTimeMillis(),
        collectionRunId: String = response?.collected_at.orEmpty(),
        collectionGeneration: Long = 0L,
        completeProfileUuids: Set<String> = emptySet(),
    ): ExternalCollectorCanonicalBatch0403 {
        if (response == null) return ExternalCollectorCanonicalBatch0403()
        val allocation = rotaCertaSeatAllocation.coerceIn(0, 999)
        val coordinator = TripMutationCoordinator0387(context, store)
        var changedTrips = 0
        var skippedTrips = 0
        var publicationQueued = 0
        var blockedTrips = 0
        var staleResultsRejected = 0
        var tombstonedTrips = 0
        var orphanProjectionTombstones = 0

        val observedStrongKeys = linkedSetOf<String>()
        response.trips.forEach { source ->
            val profileUuid = source.profile_uuid.trim()
            val blablaTripId = source.trip_id?.trim().orEmpty()
            val strongKey = canonicalExternalTripIdentityKey(profileUuid, blablaTripId, source.trip_href)
            if (source.identity_conflict || profileUuid.isBlank() || blablaTripId.isBlank() || strongKey == null) {
                blockedTrips++
                UnifiedDebugEventStore.record(
                    "EXTERNAL_CANONICAL_INGEST_BLOCKED_0403",
                    context.packageName,
                    "profileUuidPresent=${profileUuid.isNotBlank()} tripIdPresent=${blablaTripId.isNotBlank()} identityConflict=${source.identity_conflict} reason=strong_identity_required",
                )
                return@forEach
            }
            observedStrongKeys += strongKey

            val existing = store.trips().firstOrNull { trip ->
                resolvedTripRecordOrigin(trip) == TripRecordOrigin.EXTERNAL_BACKING &&
                    trip.blablaProfileUuid?.trim()?.equals(profileUuid, ignoreCase = true) == true &&
                    trip.blablaTripId?.trim() == blablaTripId
            }
            if (existing != null && collectionGeneration > 0L && existing.lastCollectionGeneration > collectionGeneration) {
                staleResultsRejected++
                UnifiedDebugEventStore.record(
                    "EXTERNAL_CANONICAL_STALE_RESULT_REJECTED_0406",
                    context.packageName,
                    "internalTripId=" + seatSyncDiagnosticKey(existing.id) +
                        " incomingGeneration=" + collectionGeneration +
                        " currentGeneration=" + existing.lastCollectionGeneration +
                        " result=SKIP_STALE_RESULT",
                )
                return@forEach
            }
            val canonicalTripId = existing?.id
                ?: externalBackingTripIdFor(profileUuid, blablaTripId, source.trip_href)
                ?: run {
                    blockedTrips++
                    return@forEach
                }
            val incomingFingerprint = PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(source, allocation)
            val incomingComplete = source.published_seats != null && source.passenger_roster_complete
            val decision = externalCollectorDeltaDecision0403(
                existingFingerprint = existing?.externalSnapshotFingerprint.orEmpty(),
                incomingFingerprint = incomingFingerprint,
                existingComplete = existing?.externalSnapshotComplete == true,
                incomingComplete = incomingComplete,
            )
            val binding = store.publicExternalBindingForStrongIdentity(profileUuid, blablaTripId)

            val canonicalTrip = when (decision) {
                ExternalCollectorDeltaDecision0403.SKIP_UNCHANGED -> {
                    skippedTrips++
                    existing?.let { current ->
                        store.saveTrip(
                            current.copy(
                                lastCollectionRunId = collectionRunId.take(160),
                                lastCollectionGeneration = maxOf(current.lastCollectionGeneration, collectionGeneration),
                                lastObservedAtMillis = maxOf(current.lastObservedAtMillis, nowMillis),
                            ),
                        )
                    }
                }
                ExternalCollectorDeltaDecision0403.PRESERVE_PARTIAL -> {
                    skippedTrips++
                    existing?.let { current ->
                        store.saveTrip(
                            current.copy(
                                lastCollectionRunId = collectionRunId.take(160),
                                lastCollectionGeneration = maxOf(current.lastCollectionGeneration, collectionGeneration),
                                lastObservedAtMillis = maxOf(current.lastObservedAtMillis, nowMillis),
                            ),
                        )
                    }
                    UnifiedDebugEventStore.record(
                        "EXTERNAL_CANONICAL_PARTIAL_PRESERVED_0403",
                        context.packageName,
                        "internalTripId=${seatSyncDiagnosticKey(canonicalTripId)} fingerprint=${incomingFingerprint.takeLast(12)} coverage=${response.status} completeForScope=${response.coverage.complete_for_scope} action=preserve_last_complete",
                    )
                    existing
                }
                ExternalCollectorDeltaDecision0403.UPDATE_CANONICAL -> {
                    val blablaQuota = source.published_seats?.takeIf { it in 0..999 } ?: 0
                    val synthesized = PublicAgendaAutoSync0300.toPublicTrip(
                        source = source,
                        capacity = (blablaQuota + allocation).coerceIn(0, 999),
                        nowMillis = Long.MIN_VALUE,
                        rotaCertaSeatAllocation = allocation,
                    )
                    if (synthesized == null) {
                        blockedTrips++
                        null
                    } else {
                        val observed = synthesized.trip
                        val saved = store.saveTrip(
                            observed.copy(
                                id = canonicalTripId,
                                status = if (source.availability.equals("full", ignoreCase = true)) TripStatus.FULL else TripStatus.PUBLISHED,
                                recordOrigin = TripRecordOrigin.EXTERNAL_BACKING,
                                remoteId = existing?.remoteId ?: binding?.remoteTripId,
                                publicToken = existing?.publicToken ?: binding?.publicToken ?: observed.publicToken,
                                publicUrl = existing?.publicUrl,
                                publicBookingEnabled = existing?.publicBookingEnabled ?: true,
                                capacityReliable = incomingComplete,
                                createdAtMillis = existing?.createdAtMillis ?: nowMillis,
                                canonicalRevision = existing?.canonicalRevision ?: 0L,
                                seatAllocationVersionUsed = maxOf(existing?.seatAllocationVersionUsed ?: 0L, seatAllocationVersion),
                                publicationRevision = existing?.publicationRevision ?: 0L,
                                publicationTombstone = existing?.publicationTombstone ?: false,
                                publicationEventId = existing?.publicationEventId.orEmpty(),
                                notes = existing?.notes.orEmpty(),
                                externalSnapshot = source,
                                externalSnapshotFingerprint = incomingFingerprint,
                                externalSnapshotComplete = incomingComplete,
                                lastCollectionRunId = collectionRunId.take(160),
                                lastCollectionGeneration = maxOf(existing?.lastCollectionGeneration ?: 0L, collectionGeneration),
                                lastObservedAtMillis = nowMillis,
                                deleted = false,
                                deletedAtMillis = 0L,
                            ),
                        )
                        if (saved.externalSnapshotFingerprint == incomingFingerprint) {
                            changedTrips++
                            UnifiedDebugEventStore.record(
                                "EXTERNAL_CANONICAL_INGEST_0403",
                                context.packageName,
                                "internalTripId=${seatSyncDiagnosticKey(saved.id)} profileUuidPresent=true tripIdPresent=true oldFingerprint=${existing?.externalSnapshotFingerprint.orEmpty().takeLast(12)} newFingerprint=${incomingFingerprint.takeLast(12)} sourceComplete=$incomingComplete canonicalRevision=${saved.canonicalRevision} result=UPDATE",
                            )
                        } else {
                            skippedTrips++
                            UnifiedDebugEventStore.record(
                                "EXTERNAL_CANONICAL_WRITE_DEFERRED_0403",
                                context.packageName,
                                "internalTripId=${seatSyncDiagnosticKey(saved.id)} committedFingerprint=${saved.externalSnapshotFingerprint.takeLast(12)} incomingFingerprint=${incomingFingerprint.takeLast(12)} result=DEFERRED reason=canonical_revision_race retry=next_cycle",
                            )
                        }
                        saved
                    }
                }
            }

            if (
                canonicalTrip != null &&
                binding != null &&
                binding.bookingTripId.isNotBlank() &&
                binding.bookingTripId != canonicalTripId
            ) {
                val previousBookingTripId = binding.bookingTripId
                val migratedBookings = store.bookingsFor(previousBookingTripId)
                    .map { booking -> booking.copy(tripId = canonicalTripId) }
                if (migratedBookings.isNotEmpty()) {
                    store.saveBookingsBatch(
                        bookingsToSave = migratedBookings,
                        preserveSourceUpdatedAt = true,
                    )
                }
                store.savePublicExternalBinding(
                    binding.copy(
                        bookingTripId = canonicalTripId,
                        canonicalRevision = maxOf(binding.canonicalRevision, canonicalTrip.canonicalRevision),
                    ),
                )
                UnifiedDebugEventStore.record(
                    "EXTERNAL_CANONICAL_BOOKING_ID_MIGRATED_0403",
                    context.packageName,
                    "oldInternalTripId=${seatSyncDiagnosticKey(previousBookingTripId)} newInternalTripId=${seatSyncDiagnosticKey(canonicalTripId)} migratedBookings=${migratedBookings.size} profileUuidPresent=true tripIdPresent=true",
                )
            }

            if (
                incomingComplete &&
                canonicalTrip != null &&
                canonicalTrip.externalSnapshotFingerprint == incomingFingerprint &&
                canonicalTrip.departureAtMillis > nowMillis &&
                binding?.externalFingerprint != incomingFingerprint
            ) {
                if (
                    coordinator.recordExternalCollectionMutation(
                        sourceTrip = source,
                        configuredRotaCertaSeatAllocation = allocation,
                        seatAllocationVersion = seatAllocationVersion,
                    ) != null
                ) {
                    publicationQueued++
                }
            } else if (decision == ExternalCollectorDeltaDecision0403.SKIP_UNCHANGED) {
                UnifiedDebugEventStore.record(
                    "EXTERNAL_CANONICAL_SKIP_0403",
                    context.packageName,
                    "internalTripId=${seatSyncDiagnosticKey(canonicalTripId)} fingerprint=${incomingFingerprint.takeLast(12)} result=UNCHANGED_SKIP publicationAlreadyCurrent=${binding?.externalFingerprint == incomingFingerprint}",
                )
            }
        }

        val canonicalExternal = store.trips().filter {
            resolvedTripRecordOrigin(it) == TripRecordOrigin.EXTERNAL_BACKING &&
                !it.blablaProfileUuid.isNullOrBlank() && !it.blablaTripId.isNullOrBlank()
        }
        val missingActive = canonicalExternal.filter { trip ->
            !trip.deleted && trip.status != TripStatus.CANCELLED &&
                canonicalExternalTripIdentityKey(trip.blablaProfileUuid, trip.blablaTripId, trip.blablaManageUrl)
                    ?.let { it !in observedStrongKeys } == true
        }
        val scopedMissing = missingActive.filter {
            externalCanonicalTripWithinCompleteScope0408(it, response, completeProfileUuids)
        }
        scopedMissing.forEach { missing ->
            val tombstoned = store.tombstoneExternalTrip0406(
                canonicalTripId = missing.id,
                collectionRunId = collectionRunId,
                collectionGeneration = collectionGeneration,
                nowMillis = nowMillis,
            )
            if (tombstoned?.deleted == true) {
                tombstonedTrips++
                coordinator.recordTombstone(
                    canonicalTripId = tombstoned.id,
                    mutationType = "BLABLACAR_COMPLETE_SCOPE_DELETE",
                    source = "EXTERNAL_COLLECTION",
                )?.let { publicationQueued++ }
            }
        }
        val legacyProfileScope = response.profiles
            .map { it.uuid.trim().lowercase() }
            .filter(String::isNotBlank)
            .toSet()
        val legacyMonth = response.month.orEmpty().trim()
        val canonicalKeys = canonicalExternal.map(Trip::tripKey).filter(String::isNotBlank).toSet()
        val tenantId = RotaCertaTenantRegistry(context.applicationContext).activeScope().tenantId
        store.publicExternalBindings().forEach { binding ->
            val key = canonicalBlaBlaTripKey0406(tenantId, binding.profileUuid, binding.blablaTripId)
                ?: return@forEach
            val observedKey = canonicalExternalTripIdentityKey(
                binding.profileUuid,
                binding.blablaTripId,
                binding.blablaTripHref,
            ) ?: return@forEach
            val bindingProfile = binding.profileUuid.trim().lowercase()
            val bindingMonth = runCatching {
                Instant.ofEpochMilli(binding.departureAtMillis)
                    .atZone(ZoneId.systemDefault()).toLocalDate().toString().take(7)
            }.getOrDefault("")
            val profileComplete =
                bindingProfile in completeProfileUuids ||
                    (
                        externalCollectorAllowsTombstones0406(response) &&
                            bindingProfile in legacyProfileScope &&
                            legacyMonth.isNotBlank() &&
                            bindingMonth == legacyMonth
                    )
            if (
                profileComplete &&
                key !in canonicalKeys &&
                observedKey !in observedStrongKeys
            ) {
                if (
                    coordinator.recordExternalTombstone(
                        binding = binding,
                        mutationType = "BLABLACAR_COMPLETE_SCOPE_ORPHAN",
                        source = "PROJECTION_RECONCILER",
                        outboxCanonicalTripId = "projection-cleanup:" +
                            sha256TripPublication0387(binding.remoteTripId).take(24),
                    ) != null
                ) {
                    orphanProjectionTombstones++
                    publicationQueued++
                }
            }
        }
        val missingPreserved = (missingActive.size - scopedMissing.size).coerceAtLeast(0)
        if (missingPreserved > 0) {
            UnifiedDebugEventStore.record(
                "EXTERNAL_CANONICAL_MISSING_PRESERVED_0403",
                context.packageName,
                "missing=" + missingPreserved +
                    " observed=" + observedStrongKeys.size +
                    " canonical=" + canonicalExternal.size +
                    " collectionStatus=" + response.status +
                    " completeForScope=" + response.coverage.complete_for_scope +
                    " globalProfileMonthComplete=" + response.coverage.global_profile_month_complete +
                    " action=preserve_unproven_absence",
            )
        }
        if (tombstonedTrips > 0 || orphanProjectionTombstones > 0) {
            UnifiedDebugEventStore.record(
                "EXTERNAL_CANONICAL_TOMBSTONES_0406",
                context.packageName,
                "canonical=" + tombstonedTrips +
                    " orphanProjection=" + orphanProjectionTombstones +
                    " collectionRunId=" + collectionRunId.take(80) +
                    " generation=" + collectionGeneration +
                    " coverage=COMPLETE",
            )
        }
        return ExternalCollectorCanonicalBatch0403(
            changedTrips = changedTrips,
            skippedTrips = skippedTrips,
            publicationQueued = publicationQueued,
            blockedTrips = blockedTrips,
            missingPreserved = missingPreserved,
            tombstonedTrips = tombstonedTrips,
            orphanProjectionTombstones = orphanProjectionTombstones,
            staleResultsRejected = staleResultsRejected,
        )
    }

    internal suspend fun reconcileProjectionIntegrity0406(
        context: Context,
        store: TripStore,
        rotaCertaSeatAllocation: Int,
        seatAllocationVersion: Long,
        repair: Boolean,
        completeCoverage: BlaBlaCollectorMonthResponse? = null,
        completeProfileUuids: Set<String> = emptySet(),
        nowMillis: Long = System.currentTimeMillis(),
    ): ProjectionIntegrity0406 = withContext(Dispatchers.IO) {
        val settings = store.onlineSettings()
        if (!settings.configured) return@withContext ProjectionIntegrity0406()
        val remoteStates = try {
            TripRemoteApi(settings).listDriverTripSyncStates0402().trips
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            UnifiedDebugEventStore.record(
                "PROJECTION_RECONCILER_REMOTE_READ_FAILED_0406",
                context.applicationContext.packageName,
                AgendaFailureEvidence.describe(
                    error = error,
                    operation = "PROJECTION_VERIFY",
                    component = "AgendaBackgroundSync0392",
                    method = "reconcileProjectionIntegrity0406",
                ),
            )
            return@withContext ProjectionIntegrity0406(failures = 1)
        }
        val publicStatuses = setOf(
            TripStatus.PUBLISHED,
            TripStatus.FULL,
            TripStatus.STARTING,
            TripStatus.ACTIVE,
        )
        val canonical = store.trips().filter {
            !it.deleted && it.departureAtMillis > nowMillis && it.status in publicStatuses
        }
        val bindings = store.publicExternalBindings()
        val coordinator = TripMutationCoordinator0387(context, store)
        var missing = 0
        var duplicates = 0
        var revisionMismatch = 0
        var hashMismatch = 0
        var capacityMismatch = 0
        var statusMismatch = 0
        var revisionRegression = 0
        var repairQueued = 0

        fun queueRepair(trip: Trip): Boolean {
            return if (resolvedTripRecordOrigin(trip) == TripRecordOrigin.EXTERNAL_BACKING) {
                val source = trip.externalSnapshot ?: return false
                if (!trip.externalSnapshotComplete) return false
                coordinator.recordExternalCollectionMutation(
                    sourceTrip = source,
                    configuredRotaCertaSeatAllocation = trip.rotaCertaSeatAllocation
                        ?: rotaCertaSeatAllocation,
                    seatAllocationVersion = maxOf(trip.seatAllocationVersionUsed, seatAllocationVersion),
                    remoteProjectionDivergenceObserved = true,
                ) != null
            } else {
                coordinator.recordLocalMutation(
                    canonicalTripId = trip.id,
                    mutationType = "PROJECTION_RECONCILER",
                    source = "CANONICAL_VERIFY",
                    configuredRotaCertaSeatAllocation = trip.rotaCertaSeatAllocation
                        ?: rotaCertaSeatAllocation,
                ) != null
            }
        }

        canonical.forEach { trip ->
            val binding = if (resolvedTripRecordOrigin(trip) == TripRecordOrigin.EXTERNAL_BACKING) {
                store.publicExternalBindingForStrongIdentity(
                    trip.blablaProfileUuid.orEmpty(),
                    trip.blablaTripId.orEmpty(),
                )
            } else null
            val preferredRemoteId = binding?.remoteTripId
                ?: trip.remoteId?.takeIf(String::isNotBlank)
                ?: trip.publicToken.takeIf(String::isNotBlank)
            val candidates = remoteStates
                .filter { remote -> remoteMatchesCanonicalProjection0408(trip, remote) }
                .distinctBy(DriverTripSyncState0402::remoteTripId)
            val remote = chooseProjectionWinner0408(trip, preferredRemoteId, candidates)
            val duplicateRemotes = remote?.let { winner ->
                candidates.filterNot { it.remoteTripId == winner.remoteTripId }
            }.orEmpty()
            if (duplicateRemotes.isNotEmpty()) {
                duplicates += duplicateRemotes.size
                if (repair) {
                    duplicateRemotes.forEach { duplicate ->
                        if (
                            coordinator.recordProjectionTombstone0408(
                                remote = duplicate,
                                mutationType = "CANONICAL_DUPLICATE_PROJECTION",
                            ) != null
                        ) repairQueued++
                    }
                }
                UnifiedDebugEventStore.record(
                    "PROJECTION_DUPLICATE_DETECTED_0408",
                    context.applicationContext.packageName,
                    "canonicalTripId=" + trip.id +
                        " profileUuid=" + trip.blablaProfileUuid.orEmpty() +
                        " blablaTripId=" + trip.blablaTripId.orEmpty() +
                        " duplicateCount=" + duplicateRemotes.size,
                )
            }
            var needsRepair = false
            if (remote == null) {
                missing++
                needsRepair = true
            } else {
                val bookings = store.bookingsFor(trip.id)
                if (trip.canonicalStateHash.isNotBlank() && remote.canonicalStateHash != trip.canonicalStateHash) {
                    hashMismatch++
                    needsRepair = true
                }
                val expectedRevision = binding?.canonicalRevision?.takeIf { it > 0L }
                    ?: trip.publicationRevision.takeIf { it > 0L }
                if (expectedRevision != null && remote.publicationRevision != expectedRevision) {
                    revisionMismatch++
                    if (remote.publicationRevision < expectedRevision) revisionRegression++
                    needsRepair = true
                }
                if (!projectionCapacityMatches0408(trip, bookings, remote, nowMillis)) {
                    val expectedRange = canonicalProjectionAvailabilityRange0408(trip, bookings, nowMillis)
                    capacityMismatch++
                    needsRepair = true
                    UnifiedDebugEventStore.record(
                        "PROJECTION_CAPACITY_MISMATCH_0408",
                        context.applicationContext.packageName,
                        "canonicalTripId=" + trip.id +
                            " profileUuid=" + trip.blablaProfileUuid.orEmpty() +
                            " blablaTripId=" + trip.blablaTripId.orEmpty() +
                            " expectedMin=" + expectedRange.minimum +
                            " expectedMax=" + expectedRange.maximum +
                            " remoteMin=" + remote.availableSeatsMinimum +
                            " remoteMax=" + remote.availableSeatsMaximum,
                    )
                }
                val expectedStatus = expectedProjectionStatus0408(trip, bookings, nowMillis)
                if (remote.status != expectedStatus) {
                    statusMismatch++
                    needsRepair = true
                }
            }
            if (repair && needsRepair && queueRepair(trip)) repairQueued++
        }
        val orphanStates = remoteStates.filter { remote ->
            val attributable =
                remote.canonicalTripId.isNotBlank() ||
                    remote.tripKey.isNotBlank() ||
                    (remote.blablaProfileUuid.isNotBlank() && remote.blablaTripId.isNotBlank())
            attributable && canonical.none { trip -> remoteMatchesCanonicalProjection0408(trip, remote) }
        }
        orphanStates.forEach { orphan ->
            val destructiveAllowed = remoteProjectionWithinCompleteScope0408(
                orphan,
                completeCoverage,
                completeProfileUuids,
            )
            UnifiedDebugEventStore.record(
                "PROJECTION_ORPHAN_DETECTED_0408",
                context.applicationContext.packageName,
                "remoteTripId=" + orphan.remoteTripId +
                    " canonicalTripId=" + orphan.canonicalTripId +
                    " profileUuid=" + orphan.blablaProfileUuid +
                    " blablaTripId=" + orphan.blablaTripId +
                    " coverage=" + (completeCoverage?.status ?: "UNKNOWN") +
                    " destructiveAllowed=" + destructiveAllowed,
            )
            if (
                repair &&
                destructiveAllowed &&
                coordinator.recordProjectionTombstone0408(
                    remote = orphan,
                    mutationType = "COMPLETE_SCOPE_ORPHAN_PROJECTION",
                ) != null
            ) repairQueued++
        }
        val report = ProjectionIntegrity0406(
            canonicalActive = canonical.size,
            agendaProjections = remoteStates.size,
            missingAgenda = missing,
            duplicates = duplicates,
            revisionMismatch = revisionMismatch,
            hashMismatch = hashMismatch,
            capacityMismatch = capacityMismatch,
            statusMismatch = statusMismatch,
            revisionRegression = revisionRegression,
            orphans = orphanStates.size,
            repairQueued = repairQueued,
        )
        UnifiedDebugEventStore.record(
            "PROJECTION_RECONCILER_0408",
            context.applicationContext.packageName,
            "canonical=" + report.canonicalActive +
                " agenda=" + report.agendaProjections +
                " missingAgenda=" + report.missingAgenda +
                " duplicates=" + report.duplicates +
                " revisionMismatch=" + report.revisionMismatch +
                " hashMismatch=" + report.hashMismatch +
                " capacityMismatch=" + report.capacityMismatch +
                " statusMismatch=" + report.statusMismatch +
                " revisionRegression=" + report.revisionRegression +
                " orphans=" + report.orphans +
                " repairQueued=" + report.repairQueued +
                " coverage=" + (completeCoverage?.status ?: "UNKNOWN") +
                " repair=" + repair,
        )
        report
    }

    internal suspend fun runCycle(
        context: Context,
        reason: String,
        collectorTarget0407: BlaBlaTripTarget0407? = null,
    ): AgendaBackgroundSyncRun0392 {
        val appContext = context.applicationContext
        val tenantId = RotaCertaTenantRegistry(appContext).activeScope().tenantId
        val mutex = tenantMutexes.computeIfAbsent(tenantId) { Mutex() }
        return mutex.withLock {
            runTenantCycle(appContext, reason, tenantId, collectorTarget0407)
        }
    }

    private suspend fun runTenantCycle(
        appContext: Context,
        reason: String,
        tenantId: String,
        collectorTarget0407: BlaBlaTripTarget0407?,
    ): AgendaBackgroundSyncRun0392 {
        val mode = agendaBackgroundSyncMode0392(reason)
        val store = TripStore(appContext)
        val integrityMigration = store.reconcileCanonicalIntegrity0406()
        UnifiedDebugEventStore.record(
            "CANONICAL_INTEGRITY_MIGRATION_0406",
            appContext.packageName,
            "canonical=" + integrityMigration.canonicalTrips +
                " duplicates=" + integrityMigration.duplicateCanonicalTrips +
                " migratedBookings=" + integrityMigration.migratedBookings +
                " duplicateAgendaBindings=" + integrityMigration.duplicateAgendaBindings +
                " orphanAgendaBindings=" + integrityMigration.orphanAgendaBindings +
                " unresolvedIdentity=" + integrityMigration.unresolvedExternalIdentity,
        )
        val migrationCoordinator = TripMutationCoordinator0387(appContext, store)
        var migrationProjectionCleanupQueued = 0
        integrityMigration.duplicateAgendaBindingsForCleanup.forEach { duplicate ->
            if (
                migrationCoordinator.recordExternalTombstone(
                    binding = duplicate,
                    mutationType = "MIGRATION_DUPLICATE_PROJECTION",
                    source = "CANONICAL_MIGRATION",
                    outboxCanonicalTripId = "projection-cleanup:" +
                        sha256TripPublication0387(duplicate.remoteTripId).take(24),
                ) != null
            ) {
                migrationProjectionCleanupQueued++
            }
        }
        integrityMigration.orphanAgendaBindingsForCleanup.forEach { orphan ->
            if (
                migrationCoordinator.recordExternalTombstone(
                    binding = orphan,
                    mutationType = "MIGRATION_ORPHAN_PROJECTION",
                    source = "CANONICAL_MIGRATION",
                    outboxCanonicalTripId = "projection-cleanup:" +
                        sha256TripPublication0387(orphan.remoteTripId).take(24),
                ) != null
            ) {
                migrationProjectionCleanupQueued++
            }
        }
        if (migrationProjectionCleanupQueued > 0) {
            UnifiedDebugEventStore.record(
                "CANONICAL_DUPLICATE_PROJECTION_CLEANUP_0406",
                appContext.packageName,
                "queued=" + migrationProjectionCleanupQueued +
                    " duplicateBindings=" + integrityMigration.duplicateAgendaBindings +
                    " orphanBindings=" + integrityMigration.orphanAgendaBindings,
            )
        }
        AgendaBackgroundSyncConfig0392.recordRunHeartbeat0406(appContext, "NORMALIZING")
        var failures = 0
        var bookingImports = 0
        var outboxDelivered = 0
        var publicLocalPublished = 0
        var publicExternalPublished = 0
        var collectorCanonical = ExternalCollectorCanonicalBatch0403()
        var projectionIntegrity = ProjectionIntegrity0406()
        val tenantSettings = SettingsRepository(appContext).settings.first()

        UnifiedDebugEventStore.record(
            "AGENDA_BACKGROUND_SYNC_START_0392",
            appContext.packageName,
            "tenantKey=${seatSyncDiagnosticKey(tenantId)} reason=${reason.take(80)} trigger=${agendaBackgroundSyncTrigger0397(reason)} mode=${mode.name} silentUi=true singleFlight=true",
        )

        var collectorState = AgendaBackgroundSyncConfig0392.collectorState0400(appContext)
        val reconcileCollectorSnapshot = mode in setOf(
            AgendaBackgroundSyncMode0392.FULL_RECONCILE,
            AgendaBackgroundSyncMode0392.COLLECTOR_RECONCILE,
        )
        fun collectorResponseForThisCycle0407(): BlaBlaCollectorMonthResponse? =
            targetedCollectorResponse0407(
                response = BlaBlaCollectorStateStore(appContext).lastResponseRecoveringDynamicSessions(),
                target = collectorTarget0407,
            )
        if (reconcileCollectorSnapshot && collectorTarget0407 == null) {
            collectorCanonical = reconcileCollectedExternalTrips0403(
                context = appContext,
                store = store,
                response = collectorResponseForThisCycle0407(),
                rotaCertaSeatAllocation = tenantSettings.rotaCertaSeatAllocation,
                seatAllocationVersion = tenantSettings.rotaCertaSeatAllocationVersion,
                collectionRunId = "collector:" + collectorState.completedGeneration,
                collectionGeneration = collectorState.completedGeneration,
                completeProfileUuids = completeCollectorProfileUuids0408(appContext, collectorState),
            )
            if (collectorCanonical.changedTrips > 0) {
                BookingRealtimeEvents0356.notifyChanged()
                UnifiedDebugEventStore.record(
                    "EXTERNAL_CANONICAL_CACHE_MATERIALIZED_0404",
                    appContext.packageName,
                    "changed=${collectorCanonical.changedTrips} skipped=${collectorCanonical.skippedTrips} queued=${collectorCanonical.publicationQueued} source=last_known_snapshot beforeHeadlessCollection=true",
                )
            }
        }

        val collectorRequested = reason == "periodic" || mode == AgendaBackgroundSyncMode0392.FULL_RECONCILE
        if (collectorRequested) {
            AgendaBackgroundSyncConfig0392.recordRunHeartbeat0406(appContext, "COLLECTING")
            val accountIds = BlaBlaDynamicAccountRegistry(appContext).list().map { it.id }
            collectorState = AgendaBackgroundSyncConfig0392.requestAutomaticCollector0400(
                context = appContext,
                accountIds = accountIds,
            )
            collectorState = if (collectorState.pending) {
                BlaBlaAutomaticCollectionCoordinator0400.runPendingHeadless(appContext, "background_${reason.take(60)}")
            } else {
                collectorState
            }
            UnifiedDebugEventStore.record(
                "BLABLACAR_AUTOMATIC_COLLECTION_REQUEST_0401", appContext.packageName,
                "tenantKey=${seatSyncDiagnosticKey(tenantId)} generation=${collectorState.generation} status=${collectorState.status} pending=${collectorState.pending} accounts=${collectorState.targetAccountIds.size} executionHost=worker_headless_webview activityLaunch=false browserOpened=false source=AgendaBackgroundSync0392",
            )
        }

        // Reconcile again after collection so the same canonical TripStore receives only
        // the fresh per-card deltas. Timeline and public Agenda keep one shared identity.
        if (reconcileCollectorSnapshot) {
            val freshCanonical = reconcileCollectedExternalTrips0403(
                context = appContext,
                store = store,
                response = collectorResponseForThisCycle0407(),
                rotaCertaSeatAllocation = tenantSettings.rotaCertaSeatAllocation,
                seatAllocationVersion = tenantSettings.rotaCertaSeatAllocationVersion,
                collectionRunId = if (collectorTarget0407 != null) "trip-reverify" else "collector:" + collectorState.generation,
                collectionGeneration = if (collectorTarget0407 != null) 0L else collectorState.generation,
                completeProfileUuids = if (collectorTarget0407 == null) {
                    completeCollectorProfileUuids0408(appContext, collectorState)
                } else {
                    emptySet()
                },
            )
            collectorCanonical = freshCanonical
            if (freshCanonical.changedTrips > 0) {
                BookingRealtimeEvents0356.notifyChanged()
            }
        }

        AgendaBackgroundSyncConfig0392.recordRunHeartbeat0406(appContext, "RECONCILING")
        val pullBookings = reason == "periodic" || mode in setOf(
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

        if (mode == AgendaBackgroundSyncMode0392.FULL_RECONCILE) {
            val online = store.onlineSettings()
            if (online.configured) {
                try {
                    val remoteSeatReconcile = TripRemoteApi(online).reconcileAgendaSeatAllocation(
                        rotaCertaSeatAllocation = tenantSettings.rotaCertaSeatAllocation,
                        configVersion = tenantSettings.rotaCertaSeatAllocationVersion,
                    )
                    UnifiedDebugEventStore.record(
                        "PUBLIC_AGENDA_SEAT_ALLOCATION_RECONCILED_0398",
                        appContext.packageName,
                        "tenantKey=${seatSyncDiagnosticKey(tenantId)} allocation=${tenantSettings.rotaCertaSeatAllocation} configVersion=${tenantSettings.rotaCertaSeatAllocationVersion} processed=${remoteSeatReconcile.processed} updated=${remoteSeatReconcile.updated} failClosed=${remoteSeatReconcile.failClosed}",
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    val backendUpgradePending = error is TripRemoteApiException && error.httpStatus == 404
                    if (!backendUpgradePending) failures++
                    UnifiedDebugEventStore.record(
                        if (backendUpgradePending) {
                            "PUBLIC_AGENDA_SEAT_ALLOCATION_BACKEND_PENDING_0398"
                        } else {
                            "PUBLIC_AGENDA_SEAT_ALLOCATION_RECONCILE_FAILED_0398"
                        },
                        appContext.packageName,
                        "backendUpgradePending=$backendUpgradePending " +
                            AgendaFailureEvidence.describe(
                                error = error,
                                operation = "PUBLIC_AGENDA_SEAT_ALLOCATION_RECONCILE",
                                component = "AgendaBackgroundSync0392",
                                method = "runTenantCycle",
                            ),
                    )
                }
            }
        }

        AgendaBackgroundSyncConfig0392.recordRunHeartbeat0406(appContext, "PROJECTING")
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

        val reconcileAllCanonicalTrips = mode == AgendaBackgroundSyncMode0392.FULL_RECONCILE
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

        AgendaBackgroundSyncConfig0392.recordRunHeartbeat0406(appContext, "VERIFYING")
        projectionIntegrity = reconcileProjectionIntegrity0406(
            context = appContext,
            store = store,
            rotaCertaSeatAllocation = tenantSettings.rotaCertaSeatAllocation,
            seatAllocationVersion = tenantSettings.rotaCertaSeatAllocationVersion,
            repair = true,
            completeCoverage = collectorResponseForThisCycle0407(),
            completeProfileUuids = completeCollectorProfileUuids0408(appContext, collectorState),
        )
        if (projectionIntegrity.repairQueued > 0) {
            try {
                outboxDelivered += TripMutationCoordinator0387(appContext, store).drainPending(limit = 128)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                failures++
                UnifiedDebugEventStore.record(
                    "PROJECTION_REPAIR_OUTBOX_FAILED_0406",
                    appContext.packageName,
                    AgendaFailureEvidence.describe(
                        error = error,
                        operation = "PROJECTION_REPAIR_OUTBOX",
                        component = "AgendaBackgroundSync0392",
                        method = "runTenantCycle",
                    ),
                )
            }
            projectionIntegrity = reconcileProjectionIntegrity0406(
                context = appContext,
                store = store,
                rotaCertaSeatAllocation = tenantSettings.rotaCertaSeatAllocation,
                seatAllocationVersion = tenantSettings.rotaCertaSeatAllocationVersion,
                repair = false,
                completeCoverage = collectorResponseForThisCycle0407(),
                completeProfileUuids = completeCollectorProfileUuids0408(appContext, collectorState),
            )
        }
        failures += projectionIntegrity.failures
        runCatching {
            BookingPushRegistration0304.ensureRegistered(appContext, store)
        }

        BookingRealtimeEvents0356.notifyChanged()
        TripWidgetProvider.updateAll(appContext)

        collectorState = AgendaBackgroundSyncConfig0392.collectorState0400(appContext)
        UnifiedDebugEventStore.record(
            "AGENDA_BACKGROUND_SYNC_END_0392",
            appContext.packageName,
            "tenantKey=${seatSyncDiagnosticKey(tenantId)} reason=${reason.take(80)} trigger=${agendaBackgroundSyncTrigger0397(reason)} mode=${mode.name} bookingImports=$bookingImports outboxDelivered=$outboxDelivered localPublished=$publicLocalPublished externalPublished=$publicExternalPublished failures=$failures collectorGeneration=${collectorState.generation} collectorStatus=${collectorState.status} collectorPending=${collectorState.pending} collectorChanged=${collectorCanonical.changedTrips} collectorSkipped=${collectorCanonical.skippedTrips} collectorQueued=${collectorCanonical.publicationQueued} missingPreserved=${collectorCanonical.missingPreserved} tombstoned=${collectorCanonical.tombstonedTrips} orphanTombstones=${collectorCanonical.orphanProjectionTombstones} staleRejected=${collectorCanonical.staleResultsRejected} projectionMissing=${projectionIntegrity.missingAgenda} projectionDuplicates=${projectionIntegrity.duplicates} capacityMismatch=${projectionIntegrity.capacityMismatch} statusMismatch=${projectionIntegrity.statusMismatch} revisionMismatch=${projectionIntegrity.revisionMismatch} revisionRegression=${projectionIntegrity.revisionRegression} projectionOrphans=${projectionIntegrity.orphans} projectionFailures=${projectionIntegrity.failures} projectionVerified=${projectionIntegrity.verified} silentUi=true",
        )

        return AgendaBackgroundSyncRun0392(
            bookingImports = bookingImports,
            outboxDelivered = outboxDelivered,
            publicLocalPublished = publicLocalPublished,
            publicExternalPublished = publicExternalPublished,
            failures = failures,
            collectorGeneration = collectorState.generation,
            collectorStatus = collectorState.status,
            collectorPending = collectorState.pending,
            collectorChangedTrips = collectorCanonical.changedTrips,
            collectorSkippedTrips = collectorCanonical.skippedTrips,
            collectorPublicationQueued = collectorCanonical.publicationQueued,
            collectorMissingPreserved = collectorCanonical.missingPreserved,
            collectorTombstonedTrips = collectorCanonical.tombstonedTrips,
            collectorOrphanProjectionTombstones = collectorCanonical.orphanProjectionTombstones,
            collectorStaleResultsRejected = collectorCanonical.staleResultsRejected,
            projectionMissingAgenda = projectionIntegrity.missingAgenda,
            projectionDuplicates = projectionIntegrity.duplicates,
            projectionRevisionMismatch = projectionIntegrity.revisionMismatch,
            projectionHashMismatch = projectionIntegrity.hashMismatch,
            projectionCapacityMismatch = projectionIntegrity.capacityMismatch,
            projectionStatusMismatch = projectionIntegrity.statusMismatch,
            projectionRevisionRegression = projectionIntegrity.revisionRegression,
            projectionOrphans = projectionIntegrity.orphans,
            projectionFailures = projectionIntegrity.failures,
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

        if (reason == "periodic" || reason == "trip_reverify" || agendaBackgroundSyncMode0392(reason) == AgendaBackgroundSyncMode0392.FULL_RECONCILE) {
            setForeground(agendaBackgroundSyncForegroundInfo0402(applicationContext, reason))
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
            val targetedWork = AgendaBackgroundSync0392.targetedTripWork0407(parameters)
            if (reason == "trip_reverify" && targetedWork == null) {
                UnifiedDebugEventStore.record(
                    "FAILED",
                    applicationContext.packageName,
                    "workId=$id capability=REVERIFY_TRIP result=UNVERIFIED_TARGET failClosed=true fullSyncFallback=false",
                )
                AgendaBackgroundSyncConfig0392.recordRunFinished(
                    context = applicationContext,
                    reason = reason,
                    result = "UNVERIFIED_TARGET",
                    failures = 1,
                    retryPending = false,
                    attempt = runAttemptCount,
                    fullReconcileComplete = false,
                )
                return Result.success()
            }
            val targetedResult = targetedWork?.let { work ->
                BlaBlaAutomaticCollectionCoordinator0400.reverifyTripHeadless0407(
                    context = applicationContext,
                    target = work.target,
                    commandId = work.commandId,
                    origin = "timeline_card_worker",
                )
            }
            val cycle = if (targetedWork != null) {
                if (targetedResult?.status == BlaBlaCommandStatus0407.VERIFIED_SUCCESS) {
                    AgendaBackgroundSync0392.runCycle(
                        context = applicationContext,
                        reason = reason,
                        collectorTarget0407 = targetedWork.target,
                    )
                } else {
                    AgendaBackgroundSyncRun0392()
                }
            } else {
                AgendaBackgroundSync0392.runCycle(
                    context = applicationContext,
                    reason = reason,
                )
            }
            val collectorState = AgendaBackgroundSyncConfig0392.collectorState0400(applicationContext)
            val collectorWasRequested =
                reason == "periodic" || agendaBackgroundSyncMode0392(reason) == AgendaBackgroundSyncMode0392.FULL_RECONCILE
            val collectorTerminalProblem =
                collectorWasRequested &&
                    collectorState.status in setOf("PARTIAL", "INTERRUPTED", "FAILED", "PENDING_AUTH")
            val collectorAuthRequired = collectorState.status == "PENDING_AUTH"
            val targetedRetryable = targetedResult?.status in setOf(
                BlaBlaCommandStatus0407.UNVERIFIED,
                BlaBlaCommandStatus0407.FAILED,
            )
            val targetedAuthRequired = targetedResult?.status == BlaBlaCommandStatus0407.AUTH_REQUIRED
            val targetedFailure = targetedResult != null && targetedResult.status != BlaBlaCommandStatus0407.VERIFIED_SUCCESS
            val retryPending = (cycle.failures > 0 && runAttemptCount < 5) || (targetedRetryable && runAttemptCount < 3)
            val reportedFailures = cycle.failures + if (collectorTerminalProblem) {
                maxOf(1, collectorState.failedAccountIds.size + collectorState.pendingAuthAccountIds.size)
            } else {
                0
            } + if (targetedFailure) 1 else 0
            val fullReconcileComplete = when {
                cycle.collectorPending -> false
                reason == "blablacar_collection_result" -> collectorState.status == "COMPLETE"
                collectorWasRequested ->
                    collectorState.status in setOf("COMPLETE", "NO_ACCOUNTS")
                else -> false
            }
            val resultLabel = when {
                targetedAuthRequired -> "PENDING_AUTH"
                retryPending -> "RETRY"
                cycle.collectorPending -> "COLLECTOR_PENDING"
                collectorAuthRequired -> "PENDING_AUTH"
                collectorTerminalProblem -> "PARTIAL"
                cycle.failures > 0 -> "PARTIAL_AFTER_MAX_RETRIES"
                (fullReconcileComplete || targetedResult?.status == BlaBlaCommandStatus0407.VERIFIED_SUCCESS) &&
                    cycle.projectionMissingAgenda == 0 &&
                    cycle.projectionDuplicates == 0 &&
                    cycle.projectionRevisionMismatch == 0 &&
                    cycle.projectionHashMismatch == 0 &&
                    cycle.projectionCapacityMismatch == 0 &&
                    cycle.projectionStatusMismatch == 0 &&
                    cycle.projectionRevisionRegression == 0 &&
                    cycle.projectionOrphans == 0 &&
                    cycle.projectionFailures == 0 -> "VERIFIED"
                else -> "SUCCESS"
            }
            if (targetedResult != null && !retryPending) {
                BlaBlaTripCommandStatusStore0407(applicationContext).recordResult(targetedResult)
            }
            AgendaBackgroundSyncConfig0392.recordRunFinished(
                context = applicationContext,
                reason = reason,
                result = resultLabel,
                failures = reportedFailures,
                retryPending = retryPending,
                attempt = runAttemptCount,
                fullReconcileComplete = fullReconcileComplete,
            )
            UnifiedDebugEventStore.record(
                "AGENDA_BACKGROUND_SYNC_WORK_0397",
                applicationContext.packageName,
                "phase=END workId=$id trigger=${agendaBackgroundSyncTrigger0397(reason)} result=$resultLabel durationMs=${android.os.SystemClock.elapsedRealtime() - startedElapsed} failures=$reportedFailures retry=$retryPending attempt=$runAttemptCount collectorGeneration=${collectorState.generation} collectorStatus=${collectorState.status} collectorPending=${collectorState.pending} targetedStatus=${targetedResult?.status?.name ?: "NONE"}",
            )
            if (retryPending) Result.retry() else Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val retryPending = runAttemptCount < 5
            if (!retryPending) {
                AgendaBackgroundSync0392.targetedTripWork0407(parameters)?.let { work ->
                    BlaBlaTripCommandStatusStore0407(applicationContext).recordResult(
                        BlaBlaCommandResult0407(
                            commandId = work.commandId,
                            target = work.target,
                            capability = BlaBlaTripCapability0407.REVERIFY_TRIP,
                            transportUsed = BlaBlaTransport0407.HYBRID,
                            status = BlaBlaCommandStatus0407.FAILED,
                            errorCode = "WORKER_EXCEPTION",
                            verification = "readback_not_completed",
                            exceptionMessage = error.message.orEmpty().take(300),
                            rootCause = error.cause?.message.orEmpty().take(300),
                            finishedAtMillis = System.currentTimeMillis(),
                        ),
                    )
                }
            }
            AgendaBackgroundSyncConfig0392.recordRunFinished(
                context = applicationContext,
                reason = reason,
                result = if (retryPending) "RETRY_EXCEPTION" else "FAILED_AFTER_MAX_RETRIES",
                failures = 1,
                retryPending = retryPending,
                attempt = runAttemptCount,
                fullReconcileComplete = false,
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
