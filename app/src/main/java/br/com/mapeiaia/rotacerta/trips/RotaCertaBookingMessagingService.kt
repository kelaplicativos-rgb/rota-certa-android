package br.com.mapeiaia.rotacerta.trips

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import br.com.mapeiaia.rotacerta.BuildConfig
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal object BookingPushRegistration0304 {
    suspend fun ensureRegistered(context: Context, store: TripStore): Boolean = withContext(Dispatchers.IO) {
        val settings = store.onlineSettings()
        if (!settings.configured || settings.driverUsername.isBlank()) return@withContext false
        val token = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()?.trim().orEmpty()
        if (token.length < 32) return@withContext false
        val response = runCatching {
            TripRemoteApi(settings).registerPushToken(
                token = token,
                appVersion = BuildConfig.VERSION_NAME,
                deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            )
        }.getOrElse { error ->
            UnifiedDebugEventStore.record(
                "PUBLIC_BOOKING_PUSH_REGISTER_FAILED",
                context.packageName,
                "reason=${error.javaClass.simpleName}",
            )
            return@withContext false
        }
        if (response.registered) {
            UnifiedDebugEventStore.record(
                "PUBLIC_BOOKING_PUSH_REGISTERED",
                context.packageName,
                "fcm=true appVersion=${BuildConfig.VERSION_NAME}",
            )
        }
        response.registered
    }
}

class RotaCertaBookingMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        runBlocking {
            withTimeoutOrNull(8_000L) {
                BookingPushRegistration0304.ensureRegistered(this@RotaCertaBookingMessagingService, TripStore(this@RotaCertaBookingMessagingService))
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val event = message.data["event"].orEmpty()
        val remoteTripId = message.data["remoteTripId"].orEmpty()
        val bookingId = message.data["bookingId"].orEmpty()
        val seats = message.data["seats"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val tripTitle = message.data["tripTitle"].orEmpty()

        BookingNotificationCenter0304.show(
            context = this,
            event = event,
            tripTitle = tripTitle,
            seats = seats,
            bookingId = bookingId,
        )

        UnifiedDebugEventStore.record(
            "PUBLIC_BOOKING_PUSH_RECEIVED",
            packageName,
            "event=${event.take(40)} remoteTripPresent=${remoteTripId.isNotBlank()} bookingPresent=${bookingId.isNotBlank()} seats=$seats",
        )

        runBlocking {
            withTimeoutOrNull(10_000L) {
                PublicBookingRemoteSync0296.pullAndReconcile(
                    context = this@RotaCertaBookingMessagingService,
                    store = TripStore(this@RotaCertaBookingMessagingService),
                )
            }
        }
        TripWidgetProvider.updateAll(this)
    }
}

private object BookingNotificationCenter0304 {
    private const val CHANNEL_CREATED = "rota_certa_booking_created_v1"
    private const val CHANNEL_CHANGED = "rota_certa_booking_changed_v1"
    private const val CHANNEL_CANCELLED = "rota_certa_booking_cancelled_v1"

    fun show(
        context: Context,
        event: String,
        tripTitle: String,
        seats: Int,
        bookingId: String,
    ) {
        val spec = when (event) {
            "reservation_created" -> NotificationSpec(
                channelId = CHANNEL_CREATED,
                channelName = "Nova reserva",
                title = "🚨 NOVA RESERVA",
                body = buildBody(tripTitle, seats, "confirmada"),
                sound = Settings.System.DEFAULT_ALARM_ALERT_URI,
                vibration = longArrayOf(0, 900, 220, 900, 220, 1_200),
                usage = AudioAttributes.USAGE_ALARM,
            )
            "reservation_changed" -> NotificationSpec(
                channelId = CHANNEL_CHANGED,
                channelName = "Reserva alterada",
                title = "🔔 RESERVA ALTERADA",
                body = buildBody(tripTitle, seats, "alterada"),
                sound = Settings.System.DEFAULT_RINGTONE_URI,
                vibration = longArrayOf(0, 500, 180, 500, 180, 700),
                usage = AudioAttributes.USAGE_NOTIFICATION_RINGTONE,
            )
            "reservation_cancelled" -> NotificationSpec(
                channelId = CHANNEL_CANCELLED,
                channelName = "Reserva cancelada",
                title = "⚠️ RESERVA CANCELADA",
                body = buildBody(tripTitle, seats, "cancelada"),
                sound = Settings.System.DEFAULT_NOTIFICATION_URI,
                vibration = longArrayOf(0, 300, 140, 300, 140, 450),
                usage = AudioAttributes.USAGE_NOTIFICATION_EVENT,
            )
            else -> return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                spec.channelId,
                spec.channelName,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Alertas imediatos da Agenda Pública do Rota Certa"
                enableVibration(true)
                vibrationPattern = spec.vibration
                setSound(
                    spec.sound,
                    AudioAttributes.Builder()
                        .setUsage(spec.usage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(context, TripsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = "br.com.mapeiaia.rotacerta.trips.OPEN_PUBLIC_BOOKING"
            putExtra("remoteTripId", bookingId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (bookingId.ifBlank { event }).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, spec.channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(spec.title)
            .setContentText(spec.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(spec.body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(spec.vibration)
            .build()

        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) {
            UnifiedDebugEventStore.record(
                "PUBLIC_BOOKING_NOTIFICATION_PERMISSION_MISSING",
                context.packageName,
                "event=$event",
            )
            return
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(
                (event + bookingId + System.currentTimeMillis() / 60_000L).hashCode(),
                notification,
            )
        }.onFailure { error ->
            UnifiedDebugEventStore.record(
                "PUBLIC_BOOKING_NOTIFICATION_FAILED",
                context.packageName,
                "event=$event reason=${error.javaClass.simpleName}",
            )
        }
    }

    private fun buildBody(tripTitle: String, seats: Int, action: String): String {
        val trip = tripTitle.ifBlank { "Viagem da Agenda Pública" }
        val seatText = when {
            seats <= 0 -> ""
            seats == 1 -> " • 1 lugar"
            else -> " • $seats lugares"
        }
        return "$trip$seatText • reserva $action"
    }

    private data class NotificationSpec(
        val channelId: String,
        val channelName: String,
        val title: String,
        val body: String,
        val sound: android.net.Uri?,
        val vibration: LongArray,
        val usage: Int,
    )
}
