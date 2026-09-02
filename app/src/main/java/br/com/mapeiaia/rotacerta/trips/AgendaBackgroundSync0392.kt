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
    else -> AgendaBackgroundSyncMode0392.DELTA_ONLY
}

internal fun agendaBackgroundSyncTrigger0397(reason: String): String = when {
    reason == "periodic" -> "PERIODIC"
    reason == "manual" -> "MANUAL"
    reason == "timeline_pull_refresh" -> "PULL_TO_REFRESH"
    reason == "recovery" || reason == "timeline_open" -> "RECOVERY"
    reason.startsWith("booking_push:") -> "EVENT_DELTA"
    reason == "blablacar_collection_result" -> "AUTOMATIC_COLLECTOR"
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
)

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
    ): ExternalCollectorCanonicalBatch0403 {
        if (response == null) return ExternalCollectorCanonicalBatch0403()
        val allocation = rotaCertaSeatAllocation.coerceIn(0, 999)
        val coordinator = TripMutationCoordinator0387(context, store)
        var changedTrips = 0
        var skippedTrips = 0
        var publicationQueued = 0
        var blockedTrips = 0

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
                    existing
                }
                ExternalCollectorDeltaDecision0403.PRESERVE_PARTIAL -> {
                    skippedTrips++
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
                                publicBookingEnabled = existing?.publicBookingEnabled ?: false,
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
                            ),
                        )
                        changedTrips++
                        UnifiedDebugEventStore.record(
                            "EXTERNAL_CANONICAL_INGEST_0403",
                            context.packageName,
                            "internalTripId=${seatSyncDiagnosticKey(saved.id)} profileUuidPresent=true tripIdPresent=true oldFingerprint=${existing?.externalSnapshotFingerprint.orEmpty().takeLast(12)} newFingerprint=${incomingFingerprint.takeLast(12)} sourceComplete=$incomingComplete canonicalRevision=${saved.canonicalRevision} result=UPDATE",
                        )
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
        val missingPreserved = canonicalExternal.count { trip ->
            canonicalExternalTripIdentityKey(trip.blablaProfileUuid, trip.blablaTripId, trip.blablaManageUrl)
                ?.let { it !in observedStrongKeys } == true
        }
        if (missingPreserved > 0) {
            UnifiedDebugEventStore.record(
                "EXTERNAL_CANONICAL_MISSING_PRESERVED_0403",
                context.packageName,
                "missing=$missingPreserved observed=${observedStrongKeys.size} canonical=${canonicalExternal.size} collectionStatus=${response.status} completeForScope=${response.coverage.complete_for_scope} action=preserve_no_cancel",
            )
        }
        return ExternalCollectorCanonicalBatch0403(
            changedTrips = changedTrips,
            skippedTrips = skippedTrips,
            publicationQueued = publicationQueued,
            blockedTrips = blockedTrips,
            missingPreserved = missingPreserved,
        )
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
        var collectorCanonical = ExternalCollectorCanonicalBatch0403()
        val tenantSettings = SettingsRepository(appContext).settings.first()

        UnifiedDebugEventStore.record(
            "AGENDA_BACKGROUND_SYNC_START_0392",
            appContext.packageName,
            "tenantKey=${seatSyncDiagnosticKey(tenantId)} reason=${reason.take(80)} trigger=${agendaBackgroundSyncTrigger0397(reason)} mode=${mode.name} silentUi=true singleFlight=true",
        )

        var collectorState = AgendaBackgroundSyncConfig0392.collectorState0400(appContext)
        val collectorRequested = reason == "periodic" || mode == AgendaBackgroundSyncMode0392.FULL_RECONCILE
        if (collectorRequested) {
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

        // External collection is normalized into the existing canonical TripStore before
        // booking pull/fan-out. This also migrates legacy external bookingTripId bindings
        // so every later mutation in this cycle uses the same internal trip identity.
        if (mode in setOf(AgendaBackgroundSyncMode0392.FULL_RECONCILE, AgendaBackgroundSyncMode0392.COLLECTOR_RECONCILE)) {
            collectorCanonical = reconcileCollectedExternalTrips0403(
                context = appContext,
                store = store,
                response = BlaBlaCollectorStateStore(appContext).lastResponseRecoveringDynamicSessions(),
                rotaCertaSeatAllocation = tenantSettings.rotaCertaSeatAllocation,
                seatAllocationVersion = tenantSettings.rotaCertaSeatAllocationVersion,
            )
        }

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

        runCatching {
            BookingPushRegistration0304.ensureRegistered(appContext, store)
        }

        BookingRealtimeEvents0356.notifyChanged()
        TripWidgetProvider.updateAll(appContext)

        collectorState = AgendaBackgroundSyncConfig0392.collectorState0400(appContext)
        UnifiedDebugEventStore.record(
            "AGENDA_BACKGROUND_SYNC_END_0392",
            appContext.packageName,
            "tenantKey=${seatSyncDiagnosticKey(tenantId)} reason=${reason.take(80)} trigger=${agendaBackgroundSyncTrigger0397(reason)} mode=${mode.name} bookingImports=$bookingImports outboxDelivered=$outboxDelivered localPublished=$publicLocalPublished externalPublished=$publicExternalPublished failures=$failures collectorGeneration=${collectorState.generation} collectorStatus=${collectorState.status} collectorPending=${collectorState.pending} collectorChanged=${collectorCanonical.changedTrips} collectorSkipped=${collectorCanonical.skippedTrips} collectorQueued=${collectorCanonical.publicationQueued} missingPreserved=${collectorCanonical.missingPreserved} silentUi=true",
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

        if (reason == "periodic" || agendaBackgroundSyncMode0392(reason) == AgendaBackgroundSyncMode0392.FULL_RECONCILE) {
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
            val cycle = AgendaBackgroundSync0392.runCycle(
                context = applicationContext,
                reason = reason,
            )
            val collectorState = AgendaBackgroundSyncConfig0392.collectorState0400(applicationContext)
            val collectorWasRequested =
                reason == "periodic" || agendaBackgroundSyncMode0392(reason) == AgendaBackgroundSyncMode0392.FULL_RECONCILE
            val collectorTerminalProblem =
                collectorWasRequested &&
                    collectorState.status in setOf("PARTIAL", "INTERRUPTED", "FAILED", "PENDING_AUTH")
            val collectorAuthRequired = collectorState.status == "PENDING_AUTH"
            val retryPending = cycle.failures > 0 && runAttemptCount < 5
            val reportedFailures = cycle.failures + if (collectorTerminalProblem) {
                maxOf(1, collectorState.failedAccountIds.size + collectorState.pendingAuthAccountIds.size)
            } else {
                0
            }
            val resultLabel = when {
                retryPending -> "RETRY"
                cycle.collectorPending -> "COLLECTOR_PENDING"
                collectorAuthRequired -> "PENDING_AUTH"
                collectorTerminalProblem -> "PARTIAL"
                cycle.failures > 0 -> "PARTIAL_AFTER_MAX_RETRIES"
                else -> "SUCCESS"
            }
            val fullReconcileComplete = when {
                cycle.collectorPending -> false
                reason == "blablacar_collection_result" -> collectorState.status == "COMPLETE"
                collectorWasRequested ->
                    collectorState.status in setOf("COMPLETE", "NO_ACCOUNTS")
                else -> false
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
                "phase=END workId=$id trigger=${agendaBackgroundSyncTrigger0397(reason)} result=$resultLabel durationMs=${android.os.SystemClock.elapsedRealtime() - startedElapsed} failures=$reportedFailures retry=$retryPending attempt=$runAttemptCount collectorGeneration=${collectorState.generation} collectorStatus=${collectorState.status} collectorPending=${collectorState.pending}",
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
