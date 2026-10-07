package br.com.mapeiaia.rotacerta.trips

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal const val REMOTE_SUPPORT_CONSENT_MARKER_0746 = "REMOTE_SUPPORT_CONSENT_0746"

internal object RemoteSupportConsentStore0746 {
    private const val PREFS = "rota_certa_remote_support_consent_0746"
    private const val KEY_PENDING = "pending_keys"

    fun add(context: Context, event: String, jobId: String) {
        val key = key(event, jobId) ?: return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = prefs.getStringSet(KEY_PENDING, emptySet()).orEmpty().toMutableSet().apply { add(key) }
        prefs.edit().putStringSet(KEY_PENDING, next).apply()
    }

    fun remove(context: Context, event: String, jobId: String): Boolean {
        val key = key(event, jobId) ?: return isEmpty(context)
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = prefs.getStringSet(KEY_PENDING, emptySet()).orEmpty().toMutableSet().apply { remove(key) }
        prefs.edit().putStringSet(KEY_PENDING, next).apply()
        return next.isEmpty()
    }

    fun isEmpty(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_PENDING, emptySet())
            .orEmpty()
            .isEmpty()

    private fun key(event: String, jobId: String): String? {
        if (event != "standalone_covers_collect" && event != "blablacar_trip_query_collect") return null
        val normalized = when (event) {
            "standalone_covers_collect" -> normalizeStandaloneCoversRemoteJobId0736(jobId)
            "blablacar_trip_query_collect" -> normalizeBlaBlaRemoteTripQueryJobId0737(jobId)
            else -> null
        } ?: return null
        return "$event|$normalized"
    }
}

class RemoteSupportConsentReceiver0746 : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action.orEmpty()
        if (action != ACTION_ACCEPT && action != ACTION_DECLINE) return

        val event = intent.getStringExtra(EXTRA_EVENT).orEmpty()
        val rawJobId = intent.getStringExtra(EXTRA_JOB_ID).orEmpty()
        val jobId = when (event) {
            "standalone_covers_collect" -> normalizeStandaloneCoversRemoteJobId0736(rawJobId)
            "blablacar_trip_query_collect" -> normalizeBlaBlaRemoteTripQueryJobId0737(rawJobId)
            else -> null
        } ?: return

        val app = context.applicationContext
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, RemoteSupportNotification0744.notificationId(jobId))
        (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(notificationId)

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (action == ACTION_ACCEPT) {
                    accept(app, event, jobId)
                } else {
                    decline(app, event, jobId)
                }
            } finally {
                val noPending = RemoteSupportConsentStore0746.remove(app, event, jobId)
                if (noPending) {
                    RemoteSupportAttention0743.markReady(
                        app,
                        if (action == ACTION_ACCEPT) "Acesso remoto aceito."
                        else "Solicitação de acesso remoto recusada.",
                    )
                } else {
                    RemoteSupportAttention0743.markRequestPending(
                        app,
                        "Existe outra solicitação de acesso remoto aguardando sua decisão.",
                    )
                }
                pendingResult.finish()
            }
        }
    }

    private suspend fun accept(context: Context, event: String, jobId: String) {
        val enqueued = when (event) {
            "standalone_covers_collect" ->
                StandaloneCoversRemoteScheduler0736.enqueue(context, jobId)
            "blablacar_trip_query_collect" ->
                BlaBlaRemoteTripQueryScheduler0737.enqueue(context, jobId)
            else -> false
        }
        UnifiedDebugEventStore.recordAlways(
            if (enqueued) "REMOTE_SUPPORT_ACCEPTED_0746" else "REMOTE_SUPPORT_ACCEPT_ENQUEUE_FAILED_0746",
            context.packageName,
            "event=${event.take(48)} jobPresent=true",
        )
    }

    private suspend fun decline(context: Context, event: String, jobId: String) {
        val settings = TripStore(context).onlineSettings()
        val reported = if (settings.configured && settings.driverToken.isNotBlank()) {
            runCatching {
                val api = TripRemoteApi(settings)
                when (event) {
                    "standalone_covers_collect" -> api.submitStandaloneCoversResult0736(
                        jobId = jobId,
                        status = "FAILED",
                        payload = null,
                        errorCode = "REMOTE_ACCESS_DECLINED_BY_USER",
                        errorMessage = "Solicitação de acesso remoto recusada pelo motorista.",
                    ).accepted
                    "blablacar_trip_query_collect" -> api.submitBlaBlaRemoteTripQueryResult0737(
                        jobId = jobId,
                        status = "FAILED",
                        payload = null,
                        errorCode = "REMOTE_ACCESS_DECLINED_BY_USER",
                        errorMessage = "Solicitação de acesso remoto recusada pelo motorista.",
                    ).accepted
                    else -> false
                }
            }.getOrDefault(false)
        } else {
            false
        }

        UnifiedDebugEventStore.recordAlways(
            "REMOTE_SUPPORT_DECLINED_0746",
            context.packageName,
            "event=${event.take(48)} jobPresent=true serverReported=$reported collectionStarted=false",
        )
    }

    companion object {
        const val ACTION_ACCEPT = "br.com.mapeiaia.rotacerta.action.REMOTE_SUPPORT_ACCEPT_0746"
        const val ACTION_DECLINE = "br.com.mapeiaia.rotacerta.action.REMOTE_SUPPORT_DECLINE_0746"
        const val EXTRA_EVENT = "remote_support_event_0746"
        const val EXTRA_JOB_ID = "remote_support_job_id_0746"
        const val EXTRA_NOTIFICATION_ID = "remote_support_notification_id_0746"
    }
}
