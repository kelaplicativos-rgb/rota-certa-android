package br.com.mapeiaia.rotacerta.trips

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// No Firebase registration is required. This service must be started explicitly
// by the driver and cannot bypass Android background execution limitations.
internal const val REMOTE_COVERS_POLL_0758 = "REMOTE_COVERS_POLL_0758"

internal fun shouldOfferRemoteConsent0758(
    job: StandaloneCoversPendingJob0758,
    seenJobId: String,
    nowMillis: Long,
): Boolean = job.pending &&
    normalizeStandaloneCoversRemoteJobId0736(job.jobId) != null &&
    job.jobId != seenJobId &&
    job.expiresAtMillis > nowMillis

internal fun shouldOfferRemoteTripConsent0760(
    job: StandaloneCoversPendingJob0758,
    seenJobId: String,
    nowMillis: Long,
): Boolean = job.pending &&
    normalizeBlaBlaRemoteTripQueryJobId0737(job.jobId) != null &&
    job.jobId != seenJobId &&
    job.expiresAtMillis > nowMillis

internal fun shouldOfferRemoteTechnicalConsent0761(
    job: StandaloneCoversPendingJob0758,
    seenJobId: String,
    nowMillis: Long,
): Boolean = job.pending &&
    normalizeRemoteHealthJobId0747(job.jobId) != null &&
    job.jobId != seenJobId &&
    job.expiresAtMillis > nowMillis

internal fun shouldOfferRemoteHealthConsent0762(
    job: StandaloneCoversPendingJob0758,
    seenJobId: String,
    nowMillis: Long,
): Boolean = job.pending &&
    job.mode == "HEALTH_SNAPSHOT" &&
    normalizeRemoteHealthJobId0747(job.jobId) != null &&
    job.jobId != seenJobId &&
    job.expiresAtMillis > nowMillis

internal class RemoteCoversPollService0758 : Service() {
    companion object {
        @Volatile var isRunning: Boolean = false
            private set
        private const val CHANNEL = "rota_certa_remote_covers_poll_0758"
        private const val NOTIFICATION_ID = 758_008
        const val ACTION_STOP = "br.com.mapeiaia.rotacerta.REMOTE_POLL_STOP_0758"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private var polling: Job? = null
    private var seenCoverJobId: String = ""
    private var seenTripJobId: String = ""
    private var seenTechnicalJobId: String = ""
    private var seenHealthJobId: String = ""
    private var activePopupEvent: String = ""
    private var activePopup: View? = null
    private var overlayManager: WindowManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (polling?.isActive == true) return START_NOT_STICKY
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL, "Consulta remota BlaBlaCar", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Escuta ativada pelo motorista; nenhuma viagem é lida sem autorização." })
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Rota Certa — escuta remota ativa")
            .setContentText("Consultas remotas conforme a autorização definida no aplicativo.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isRunning = true
        polling = scope.launch {
            while (isActive) {
                try {
                    val settings = TripStore(applicationContext).onlineSettings()
                    if (settings.configured && settings.driverUsername.isNotBlank() &&
                        settings.driverToken.isNotBlank()) {
                        val api = TripRemoteApi(settings)
                        val now = System.currentTimeMillis()
                        var authenticatedPollSucceeded = false
                        if (!RemoteSupportAutoAccess0763.enabled(applicationContext, "standalone_covers_collect")) {
                            seenCoverJobId = ""
                            seenTripJobId = ""
                            seenTechnicalJobId = ""
                            seenHealthJobId = ""
                            main.post { closePopup() }
                        }
                        // Independent polls: failure of one source must not suppress the other.
                        runCatching { api.pollStandaloneCoversPending0758() }
                            .onSuccess { pending ->
                                authenticatedPollSucceeded = true
                                if (RemoteSupportAutoAccess0763.enabled(applicationContext, "standalone_covers_collect") &&
                                    shouldOfferRemoteConsent0758(pending, seenCoverJobId, now)) {
                                    seenCoverJobId = pending.jobId
                                    val jobId = pending.jobId
                                    main.post { showConsent("standalone_covers_collect", jobId) }
                                } else if (!pending.pending) {
                                    seenCoverJobId = ""
                                    main.post { closePopupFor("standalone_covers_collect") }
                                }
                            }.onFailure { error ->
                                UnifiedDebugEventStore.record(
                                    "REMOTE_COVERS_POLL_FAILED_0758", packageName,
                                    "source=covers reason=${error.javaClass.simpleName.take(64)}"
                                )
                            }
                        runCatching { api.pollRemoteTechnicalPending0761() }
                            .onSuccess { pending ->
                                authenticatedPollSucceeded = true
                                if (RemoteSupportAutoAccess0763.enabled(applicationContext, "remote_health_collect") &&
                                    shouldOfferRemoteTechnicalConsent0761(pending, seenTechnicalJobId, now)) {
                                    seenTechnicalJobId = pending.jobId
                                    val jobId = pending.jobId
                                    main.post { showConsent("remote_health_collect", jobId) }
                                } else if (!pending.pending) {
                                    seenTechnicalJobId = ""
                                    // A separate health snapshot popup must not be dismissed.
                                }
                            }.onFailure { error ->
                                UnifiedDebugEventStore.record(
                                    "REMOTE_TECHNICAL_POLL_FAILED_0761", packageName,
                                    "source=technical_zip reason=${error.javaClass.simpleName.take(64)}"
                                )
                            }
                        runCatching { api.pollRemoteHealthPending0762() }
                            .onSuccess { pending ->
                                authenticatedPollSucceeded = true
                                if (RemoteSupportAutoAccess0763.enabled(applicationContext, "remote_health_collect") &&
                                    shouldOfferRemoteHealthConsent0762(pending, seenHealthJobId, now)) {
                                    seenHealthJobId = pending.jobId
                                    val jobId = pending.jobId
                                    main.post { showConsent("remote_health_collect", jobId) }
                                } else if (!pending.pending) {
                                    seenHealthJobId = ""
                                    // Keep separate ZIP technical consent open.
                                }
                            }.onFailure { error ->
                                UnifiedDebugEventStore.record(
                                    "REMOTE_HEALTH_POLL_FAILED_0762", packageName,
                                    "source=health_snapshot reason=${error.javaClass.simpleName.take(64)}"
                                )
                            }
                        runCatching { api.pollBlaBlaTripQueryPending0760() }
                            .onSuccess { pending ->
                                authenticatedPollSucceeded = true
                                if (RemoteSupportAutoAccess0763.enabled(applicationContext, "blablacar_trip_query_collect") &&
                                    shouldOfferRemoteTripConsent0760(pending, seenTripJobId, now)) {
                                    seenTripJobId = pending.jobId
                                    val jobId = pending.jobId
                                    main.post { showConsent("blablacar_trip_query_collect", jobId) }
                                } else if (!pending.pending) {
                                    seenTripJobId = ""
                                    main.post { closePopupFor("blablacar_trip_query_collect") }
                                }
                            }.onFailure { error ->
                                UnifiedDebugEventStore.record(
                                    "REMOTE_TRIP_QUERY_POLL_FAILED_0760", packageName,
                                    "source=trip_query reason=${error.javaClass.simpleName.take(64)}"
                                )
                            }
                        // A successful authenticated read proves the device polling
                        // channel is alive. Recover obsolete FCM-only warnings,
                        // without dismissing consent or genuine collection failures.
                        if (authenticatedPollSucceeded &&
                            RemoteSupportAutoAccess0763.enabled(applicationContext, "remote_health_collect")) {
                            val attention = RemoteSupportAttention0743.current(applicationContext)
                            if (shouldRecoverRemoteTransportAttention0769(
                                    status = attention.status,
                                    reasonCode = attention.reasonCode,
                                    remoteAccessEnabled = true,
                                )) {
                                RemoteSupportAttention0743.markReady(
                                    applicationContext,
                                    "Escuta remota confirmada por polling autenticado; FCM opcional.",
                                )
                            }
                        }
                    }
                } catch (error: Exception) {
                    UnifiedDebugEventStore.record(
                        "REMOTE_COVERS_POLL_FAILED_0758", packageName,
                        "reason=${error.javaClass.simpleName.take(64)}"
                    )
                }
                delay(10_000)
            }
        }
        return START_NOT_STICKY
    }

    private fun showConsent(event: String, jobId: String) {
        // OFF is a hard stop for *new device collection*. Even if the backend
        // holds an old request or a push arrives, no prompt or worker is started.
        if (!RemoteSupportAutoAccess0763.enabled(this, event)) return
        // Every job remains actionable via a local notification even if a
        // different consent popup is currently shown.
        // Automatic approval is possible only after the driver opted in locally.
        // This covers both health/ZIP and BlaBlaCar requests; Android OS prompts remain authoritative.
        if (RemoteSupportNotification0744.show(this, event, jobId)) return
        if (!isRunning || !Settings.canDrawOverlays(this) || activePopup != null) return
        val context = this
        val dp = resources.displayMetrics.density
        fun d(value: Int): Int = (value * dp).toInt()
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(18), d(16), d(18), d(14))
            background = GradientDrawable().apply {
                setColor(Color.rgb(32, 34, 47))
                cornerRadius = d(18).toFloat()
                setStroke(d(2), Color.rgb(113, 88, 183))
            }
            elevation = d(12).toFloat()
        }
        panel.addView(TextView(context).apply {
            text = "Rota Certa • consulta remota"
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
        })
        panel.addView(TextView(context).apply {
            text = when (event) {
                "blablacar_trip_query_collect" ->
                    "Permitir consulta detalhada de UMA viagem BlaBlaCar? Nenhuma viagem será alterada."
                "remote_health_collect" ->
                    "Permitir consultar a Central de Saúde e enviar o relatório sanitizado ou ZIP técnico solicitado? Nenhuma viagem será alterada."
                else ->
                    "Permitir leitura SOMENTE das capas BlaBlaCar? Nenhuma viagem será alterada."
            }
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(0, d(10), 0, d(12))
        })
        val buttons = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        fun consentButton(title: String, action: String): Button = Button(context).apply {
            text = title
            isAllCaps = false
            setOnClickListener {
                val request = Intent(context, RemoteSupportConsentReceiver0746::class.java).apply {
                    this.action = action
                    putExtra(RemoteSupportConsentReceiver0746.EXTRA_EVENT, event)
                    putExtra(RemoteSupportConsentReceiver0746.EXTRA_JOB_ID, jobId)
                    putExtra(RemoteSupportConsentReceiver0746.EXTRA_NOTIFICATION_ID,
                        RemoteSupportNotification0744.notificationId(jobId))
                }
                context.sendBroadcast(request)
                closePopup()
            }
        }
        buttons.addView(consentButton("Recusar", RemoteSupportConsentReceiver0746.ACTION_DECLINE),
            LinearLayout.LayoutParams(0, d(52), 1f))
        buttons.addView(consentButton("Aceitar", RemoteSupportConsentReceiver0746.ACTION_ACCEPT),
            LinearLayout.LayoutParams(0, d(52), 1f))
        panel.addView(buttons)
        try {
            val window = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            window.addView(panel, WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_SECURE,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = d(52) })
            overlayManager = window
            activePopupEvent = event
            activePopup = panel
        } catch (error: Exception) {
            UnifiedDebugEventStore.record(
                "REMOTE_COVERS_POPUP_FAILED_0758", packageName,
                "reason=${error.javaClass.simpleName.take(64)}"
            )
        }
        // After 30 s the request remains actionable from the local notification.
        main.postDelayed({ if (activePopup === panel) closePopup() }, 30_000)
    }

    private fun closePopupFor(event: String) {
        if (activePopupEvent == event) closePopup()
    }

    private fun closePopup() {
        val view = activePopup ?: return
        activePopup = null
        activePopupEvent = ""
        runCatching { overlayManager?.removeViewImmediate(view) }
        overlayManager = null
    }

    override fun onDestroy() {
        isRunning = false
        polling?.cancel()
        main.post { closePopup() }
        scope.cancel()
        super.onDestroy()
    }
}
