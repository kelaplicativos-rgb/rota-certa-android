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
    private const val NOTIFICATION_ID = 74401

    fun show(
        context: Context,
        event: String,
        jobPresent: Boolean,
    ) {
        val app = context.applicationContext
        RemoteSupportAttention0743.markRequestPending(app)

        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Solicitações remotas de diagnóstico e consulta do Rota Certa"
                    enableVibration(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                },
            )
        }

        val intent = Intent(app, TripsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = TripActions.ACTION_OPEN_NOTIFICATIONS
        }
        val pendingIntent = PendingIntent.getActivity(
            app,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = "🟠 Solicitação remota recebida"
        val body = when (event) {
            "standalone_covers_collect" ->
                "Coleta de capas solicitada. Toque para abrir Suporte remoto."
            "blablacar_trip_query_collect" ->
                "Consulta detalhada de viagem solicitada. Toque para abrir Suporte remoto."
            else ->
                "O Rota Certa recebeu uma solicitação remota. Toque para abrir Suporte remoto."
        }

        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) {
            UnifiedDebugEventStore.recordAlways(
                "REMOTE_SUPPORT_NOTIFICATION_PERMISSION_MISSING_0744",
                app.packageName,
                "event=${event.take(48)} jobPresent=$jobPresent",
            )
            return
        }

        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(false)
            .build()

        runCatching {
            NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, notification)
        }.onSuccess {
            UnifiedDebugEventStore.recordAlways(
                "REMOTE_SUPPORT_NOTIFICATION_SHOWN_0744",
                app.packageName,
                "event=${event.take(48)} jobPresent=$jobPresent",
            )
        }.onFailure { error ->
            UnifiedDebugEventStore.recordAlways(
                "REMOTE_SUPPORT_NOTIFICATION_FAILED_0744",
                app.packageName,
                "event=${event.take(48)} error=${error.javaClass.simpleName.take(80)}",
            )
        }
    }
}
