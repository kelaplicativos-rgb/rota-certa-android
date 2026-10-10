package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Separate the driver's *desired* toggle position from the acknowledged server
 * authorization. A lost network must never silently turn ON into OFF, and a
 * user-requested OFF must block reads before a remote revocation is retried.
 *
 * A pending ON is not permission to collect: enabled() remains false until the
 * driver-authenticated server explicitly acknowledges the grant.
 */
internal object RemoteAccessDesiredState0771 {
    private const val PREFS = "rota_certa_remote_desired_0771"
    private const val KEY_DESIRED = "remote_requested_on"
    private const val KEY_DIRTY = "remote_server_sync_pending"
    private const val UNIQUE = "remote-access-server-reconcile-0771"
    private val lock = Any()

    fun desired(context: Context): Boolean = synchronized(lock) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val tenant = RotaCertaTenantRegistry(app).activeScope()
        prefs.getBoolean(
            tenant.key(KEY_DESIRED),
            RemoteSupportAutoAccess0763.enabled(app, "remote_health_collect"),
        )
    }

    fun pending(context: Context): Boolean = synchronized(lock) {
        val app = context.applicationContext
        val tenant = RotaCertaTenantRegistry(app).activeScope()
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(tenant.key(KEY_DIRTY), false)
    }

    fun request(context: Context, on: Boolean) {
        val app = context.applicationContext
        synchronized(lock) {
            val tenant = RotaCertaTenantRegistry(app).activeScope()
            check(app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(tenant.key(KEY_DESIRED), on)
                .putBoolean(tenant.key(KEY_DIRTY), true)
                .commit()) { "Nao foi possivel salvar a escolha de acesso remoto." }
            if (!on) {
                // Local OFF immediately invalidates every pending automatic
                // approval. Server revocation is retried until acknowledged.
                RemoteSupportAutoAccess0763.setEnabled(app, false)
                app.stopService(android.content.Intent(app, RemoteCoversPollService0758::class.java))
            }
        }
        schedule(app)
        UnifiedDebugEventStore.recordAlways(
            if (on) "REMOTE_ACCESS_ON_REQUESTED_0771" else "REMOTE_ACCESS_OFF_REQUESTED_0771",
            app.packageName, "tenantScoped=true transportIndependent=true",
        )
    }

    fun activateAfterServerAck(context: Context): Boolean = synchronized(lock) {
        val app = context.applicationContext
        if (!desired(app)) return@synchronized false
        RemoteSupportAutoAccess0763.setEnabled(app, true)
        markSynced(app, true)
        true
    }

    fun markSynced(context: Context, serverEnabled: Boolean) = synchronized(lock) {
        val app = context.applicationContext
        if (desired(app) != serverEnabled) return@synchronized
        val tenant = RotaCertaTenantRegistry(app).activeScope()
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(tenant.key(KEY_DIRTY), false).commit()
    }

    fun schedule(context: Context) {
        val app = context.applicationContext
        if (!pending(app)) return
        val request = OneTimeWorkRequestBuilder<RemoteAccessReconcileWorker0771>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            // Intentionally NOT tagged WORK_TAG: OFF cancels remote reads, not
            // the revocation retry responsible for disabling the public link.
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.REPLACE, request)
    }
}

internal class RemoteAccessReconcileWorker0771(
    context: Context, params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext
        if (!RemoteAccessDesiredState0771.pending(app)) return@withContext Result.success()
        val desired = RemoteAccessDesiredState0771.desired(app)
        val settings = runCatching { TripStore(app).onlineSettings() }.getOrNull()
        if (settings == null || !settings.configured ||
            settings.driverToken.isBlank() || settings.driverUsername.isBlank()) {
            UnifiedDebugEventStore.record(
                "REMOTE_ACCESS_RECONCILE_CONFIG_PENDING_0771", app.packageName,
                "desiredOn=$desired",
            )
            return@withContext Result.retry()
        }
        val api = TripRemoteApi(settings)
        try {
            if (desired) api.ensureStandaloneCoversAccess0736()
            val acknowledged = api.setRemoteAccessState0764(desired).enabled
            if (acknowledged != desired) return@withContext Result.retry()
            if (desired) {
                if (RemoteAccessDesiredState0771.activateAfterServerAck(app)) {
                    RemotePollingRecovery0770.reconcile(app, immediate = true)
                } else {
                    // OFF raced the response to the ON request. Revoke again.
                    RemoteAccessDesiredState0771.schedule(app)
                }
            } else {
                RemoteAccessDesiredState0771.markSynced(app, false)
            }
            UnifiedDebugEventStore.recordAlways(
                "REMOTE_ACCESS_RECONCILED_0771", app.packageName,
                "desiredOn=$desired localConsent=${RemoteSupportAutoAccess0763.enabled(app, "remote_health_collect")}",
            )
            Result.success()
        } catch (error: Exception) {
            UnifiedDebugEventStore.record(
                "REMOTE_ACCESS_RECONCILE_RETRY_0771", app.packageName,
                "desiredOn=$desired errorClass=${error.javaClass.simpleName.take(60)}",
            )
            Result.retry()
        }
    }
}
