package br.com.mapeiaia.rotacerta.trips

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android-owned recovery for the driver's explicit opt-in. It never turns the
 * toggle on, creates credentials, starts a background dataSync foreground
 * service, or bypasses Android's background restrictions.
 *
 * The foreground listener is fast when available; this periodic worker ensures
 * there is still a chance to process read-only requests after process death,
 * a service timeout, a reboot, or an app update. WorkManager scheduling is
 * inexact and cannot promise immediate remote collection.
 */
internal const val REMOTE_POLLING_RECOVERY_0770 = "REMOTE_POLLING_RECOVERY_0770"

internal object RemotePollingRecovery0770 {
    private const val PERIODIC = "remote-polling-recovery-0770"
    private const val STARTUP = "remote-polling-bootstrap-0770"

    fun reconcile(context: Context, immediate: Boolean = false) {
        val app = context.applicationContext
        val manager = WorkManager.getInstance(app)
        if (!RemoteSupportAutoAccess0763.enabled(app, "remote_health_collect")) {
            manager.cancelUniqueWork(PERIODIC)
            manager.cancelUniqueWork(STARTUP)
            return
        }
        val network = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        manager.enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RemotePollingRecoveryWorker0770>(
                15, TimeUnit.MINUTES,
            ).setConstraints(network)
                .addTag(RemoteSupportAutoAccess0763.WORK_TAG)
                .build(),
        )
        if (immediate) {
            manager.enqueueUniqueWork(
                STARTUP,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<RemotePollingRecoveryWorker0770>()
                    .setConstraints(network)
                    .addTag(RemoteSupportAutoAccess0763.WORK_TAG)
                    .build(),
            )
        }
    }
}

/** The system may invoke this without ever reopening the Rota Certa UI. */
internal class RemotePollingRecoveryReceiver0770 : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
            )) return
        // Scheduling is fast and never performs network I/O on the receiver.
        runCatching { RemotePollingRecovery0770.reconcile(context, immediate = true) }
            .onFailure {
                UnifiedDebugEventStore.record(
                    "REMOTE_RECOVERY_SCHEDULE_FAILED_0770",
                    context.packageName,
                    "reason=${it.javaClass.simpleName.take(64)}",
                )
            }
    }
}

internal class RemotePollingRecoveryWorker0770(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext
        val enabled = RemoteSupportAutoAccess0763.enabled(app, "remote_health_collect")
        if (!enabled) return@withContext Result.success()

        val settings = runCatching { TripStore(app).onlineSettings() }.getOrElse {
            UnifiedDebugEventStore.record(
                "REMOTE_RECOVERY_SETTINGS_FAILED_0770", app.packageName,
                "reason=${it.javaClass.simpleName.take(64)}",
            )
            return@withContext Result.retry()
        }
        if (!settings.configured || settings.driverToken.isBlank() ||
            settings.driverUsername.isBlank()) return@withContext Result.success()

        val api = TripRemoteApi(settings)
        var successes = 0
        var errors = 0
        val now = System.currentTimeMillis()

        suspend fun collect(
            event: String,
            fetch: suspend () -> StandaloneCoversPendingJob0758,
            check: (StandaloneCoversPendingJob0758) -> Boolean,
        ) {
            if (!RemoteSupportAutoAccess0763.enabled(app, event)) return
            val pending = try {
                fetch()
            } catch (error: Exception) {
                errors++
                UnifiedDebugEventStore.record(
                    "REMOTE_RECOVERY_POLL_FAILED_0770", app.packageName,
                    "event=$event reason=${error.javaClass.simpleName.take(64)}",
                )
                return
            }
            successes++
            if (check(pending) && RemoteSupportAutoAccess0763.enabled(app, event)) {
                // Uses the same auto-approved consent receiver and worker as the
                // fast foreground listener. Job IDs are validated before dispatch.
                RemoteSupportNotification0744.show(app, event, pending.jobId)
            }
        }

        collect(
            "standalone_covers_collect",
            { api.pollStandaloneCoversPending0758() },
            { shouldOfferRemoteConsent0758(it, "", now) },
        )
        collect(
            "blablacar_trip_query_collect",
            { api.pollBlaBlaTripQueryPending0760() },
            { shouldOfferRemoteTripConsent0760(it, "", now) },
        )
        collect(
            "remote_health_collect",
            { api.pollRemoteTechnicalPending0761() },
            { shouldOfferRemoteTechnicalConsent0761(it, "", now) },
        )
        collect(
            "remote_health_collect",
            { api.pollRemoteHealthPending0762() },
            { shouldOfferRemoteHealthConsent0762(it, "", now) },
        )

        if (successes > 0 && RemoteSupportAutoAccess0763.enabled(app, "remote_health_collect")) {
            val attention = RemoteSupportAttention0743.current(app)
            if (shouldRecoverRemoteTransportAttention0769(
                    attention.status, attention.reasonCode, true,
                )) RemoteSupportAttention0743.markReady(
                    app, "Consultas remotas confirmadas em segundo plano, sem FCM.",
                )
        }
        // Periodic jobs will run again regardless. Retry all-network failures
        // with WorkManager backoff, without looping or causing battery drain.
        if (successes == 0 && errors > 0) Result.retry() else Result.success()
    }
}
