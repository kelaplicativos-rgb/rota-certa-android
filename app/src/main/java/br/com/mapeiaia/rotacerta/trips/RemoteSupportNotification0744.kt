package br.com.mapeiaia.rotacerta.trips

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore

internal const val REMOTE_SUPPORT_NOTIFICATION_MARKER_0744 = "REMOTE_SUPPORT_NOTIFICATION_0744"

internal object RemoteSupportNotification0744 {
    private const val CHANNEL_ID = "rota_certa_remote_support_0744"
    private const val CHANNEL_NAME = "Suporte remoto"

    fun notificationId(jobId: String): Int = 744_000 + (jobId.hashCode() and 0x3FFF)

    fun show(
        context: Context,
        event: String,
        jobId: String,
    ): Boolean {
        val app = context.applicationContext
        val normalizedJobId = when (event) {
            "standalone_covers_collect" -> normalizeStandaloneCoversRemoteJobId0736(jobId)
            "blablacar_trip_query_collect" -> normalizeBlaBlaRemoteTripQueryJobId0737(jobId)
            "remote_health_collect" -> normalizeRemoteHealthJobId0747(jobId)
            else -> null
        } ?: return false

        // A deliberate on-device opt-in substitutes for repeated in-app popups.
        // The receiver checks authorization again, so revocation wins races.
        if (RemoteSupportAutoAccess0763.enabled(app, event)) {
            UnifiedDebugEventStore.recordAlways(
                "REMOTE_SUPPORT_AUTO_APPROVAL_REQUESTED_0763",
                app.packageName,
                "event=${event.take(48)} jobPresent=true completed=false",
            )
            app.sendBroadcast(Intent(app, RemoteSupportConsentReceiver0746::class.java).apply {
                action = RemoteSupportConsentReceiver0746.ACTION_ACCEPT
                putExtra(RemoteSupportConsentReceiver0746.EXTRA_EVENT, event)
                putExtra(RemoteSupportConsentReceiver0746.EXTRA_JOB_ID, normalizedJobId)
                putExtra(RemoteSupportConsentReceiver0746.EXTRA_AUTO_APPROVED, true)
            })
            return true
        }

        RemoteSupportConsentStore0746.add(app, event, normalizedJobId)
        RemoteSupportAttention0743.markRequestPending(
            app,
            "Solicitação de acesso remoto aguardando sua decisão.",
        )

        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Solicitações de acesso remoto do Rota Certa"
                    enableVibration(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                },
            )
        }

        val notificationId = notificationId(normalizedJobId)
        fun actionIntent(action: String, requestCode: Int): PendingIntent {
            val intent = Intent(app, RemoteSupportConsentReceiver0746::class.java).apply {
                this.action = action
                putExtra(RemoteSupportConsentReceiver0746.EXTRA_EVENT, event)
                putExtra(RemoteSupportConsentReceiver0746.EXTRA_JOB_ID, normalizedJobId)
                putExtra(RemoteSupportConsentReceiver0746.EXTRA_NOTIFICATION_ID, notificationId)
            }
            return PendingIntent.getBroadcast(
                app,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) {
            UnifiedDebugEventStore.recordAlways(
                "REMOTE_SUPPORT_NOTIFICATION_PERMISSION_MISSING_0744",
                app.packageName,
                "event=${event.take(48)} jobPresent=true consentRequired=true",
            )
            return false
        }

        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Solicitação de acesso remoto")
            .setContentText("ACEITAR ou RECUSAR")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(false)
            .setOngoing(true)
            .addAction(
                0,
                "ACEITAR",
                actionIntent(RemoteSupportConsentReceiver0746.ACTION_ACCEPT, notificationId * 2),
            )
            .addAction(
                0,
                "RECUSAR",
                actionIntent(RemoteSupportConsentReceiver0746.ACTION_DECLINE, notificationId * 2 + 1),
            )
            .setOnlyAlertOnce(false)
            .build()

        runCatching {
            NotificationManagerCompat.from(app).notify(notificationId, notification)
        }.onSuccess {
            UnifiedDebugEventStore.recordAlways(
                "REMOTE_SUPPORT_CONSENT_NOTIFICATION_SHOWN_0746",
                app.packageName,
                "event=${event.take(48)} jobPresent=true collectionStarted=false",
            )
        }.onFailure { error ->
            UnifiedDebugEventStore.recordAlways(
                "REMOTE_SUPPORT_NOTIFICATION_FAILED_0744",
                app.packageName,
                "event=${event.take(48)} error=${error.javaClass.simpleName.take(80)}",
            )
        }
        return false
    }
}
