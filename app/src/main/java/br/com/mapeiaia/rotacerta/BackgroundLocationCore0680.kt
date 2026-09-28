package br.com.mapeiaia.rotacerta

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 0.1.680 — núcleo comum de localização.
 *
 * O WorkTrackingService é a única autoridade de GPS quando está ativo. Esta
 * camada consome a mesma correção para radar/alerta e mantém o compartilhamento
 * familiar explícito até revogação do motorista.
 */
internal object PersistentTrackingPolicy0680 {
    const val FAMILY_PERSISTENT_EXPIRY_MILLIS = 253_402_300_799_000L

    fun isShareActive(share: TrackingShareLocal0668, nowMillis: Long): Boolean =
        share.active && (
            share.scope == TrackingShareScope0668.FAMILY ||
                share.expiresAtMillis > nowMillis
            )

    fun familyExpiryMillis(): Long = FAMILY_PERSISTENT_EXPIRY_MILLIS
}

internal object LocationCoreRuntime0680 {
    const val CONTRACT_MARKER = "UNIFIED_LOCATION_CORE_0680"

    suspend fun reconcilePersistedIntent(context: Context): Boolean {
        val app = context.applicationContext
        val work = WorkTrackingRepository(app)
        val sharing = LiveTrackingShareManager0668(app).hasActiveShares()
        if (work.isTrackingActive() || sharing) return ensureStarted(app)

        val repository = SettingsRepository(app)
        val currentSettings = repository.settings.first()
        if (!currentSettings.proximityAlertsEnabled) return false
        val hasTargets =
            repository.savedPlaces.first().any { it.type == SavedPlaceType.ProximityAlert } ||
                repository.importedRadars.first().isNotEmpty()
        return hasTargets && ensureStarted(app)
    }

    fun ensureStarted(context: Context): Boolean {
        val app = context.applicationContext
        val granted =
            ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) return false
        return runCatching {
            ContextCompat.startForegroundService(
                app,
                Intent(app, WorkTrackingService::class.java).setAction(WorkTrackingService.ACTION_ENSURE_LOCATION_CORE_0681),
            )
            true
        }.getOrDefault(false)
    }
}

/**
 * Reinicia somente uma intenção de rastreamento que já estava explicitamente
 * ativa. Forçar parada, permissão revogada e controles do Android continuam
 * sendo autoridade final.
 */
class LocationCoreBootReceiver0680 : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                LocationCoreRuntime0680.reconcilePersistedIntent(context)
            } finally {
                pending.finish()
            }
        }
    }
}

internal class BackgroundProximityRuntime0680(
    private val service: Service,
) {
    private val context = service.applicationContext
    private val repository = SettingsRepository(context)
    private val speech = BackgroundProximitySpeech0680(context)
    private val engine = DirectionalProximityAlertEngine(speech)
    private val spatialIndex = ImportedRadarSpatialIndex()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    @Volatile private var settings: AppSettings = AppSettings()
    @Volatile private var savedPlaces: List<SavedPlace> = emptyList()
    @Volatile private var radars: List<ImportedRadar> = emptyList()
    private var configJob: Job? = null
    private var activeVisual: DirectionalAlertVisual? = null

    fun isLocationRequired0681(): Boolean {
        val currentSettings = settings
        val hasTargets =
            savedPlaces.any { it.type == SavedPlaceType.ProximityAlert } || radars.isNotEmpty()
        return AlertRuntimePolicy0644.shouldTrack(currentSettings, hasTargets)
    }

    fun start(scope: CoroutineScope) {
        createChannel()
        speech.start()
        if (configJob?.isActive == true) return
        configJob = scope.launch {
            combine(repository.settings, repository.savedPlaces, repository.importedRadars) { nextSettings, nextPlaces, nextRadars ->
                Triple(nextSettings, nextPlaces, nextRadars)
            }.collect { (nextSettings, nextPlaces, nextRadars) ->
                settings = nextSettings
                savedPlaces = nextPlaces
                radars = nextRadars
                if (!AlertRuntimePolicy0644.shouldTrack(
                        nextSettings,
                        nextPlaces.any { it.type == SavedPlaceType.ProximityAlert } || nextRadars.isNotEmpty(),
                    )
                ) {
                    activeVisual = null
                    notificationManager.cancel(ALERT_NOTIFICATION_ID)
                }
            }
        }
    }

    fun stop() {
        configJob?.cancel()
        configJob = null
        activeVisual = null
        notificationManager.cancel(ALERT_NOTIFICATION_ID)
        speech.stop()
        spatialIndex.clear()
    }

    fun dismiss(targetId: String?) {
        targetId?.takeIf(String::isNotBlank)?.let(engine::dismissUntilExit)
        activeVisual = null
        notificationManager.cancel(ALERT_NOTIFICATION_ID)
    }

    fun onLocation(location: Location) {
        val currentSettings = settings
        val alerts = savedPlaces.filter { it.type == SavedPlaceType.ProximityAlert }
        val currentRadars = radars
        if (!AlertRuntimePolicy0644.shouldTrack(currentSettings, alerts.isNotEmpty() || currentRadars.isNotEmpty())) {
            return
        }
        if (!location.latitude.isFinite() || !location.longitude.isFinite()) return
        if (location.hasAccuracy() && (location.accuracy <= 0f || location.accuracy > 80f)) return

        val now = System.currentTimeMillis()
        val recordedAt = location.time.takeIf { it > 0L } ?: now
        val fix = PreciseNavigationFix(
            coordinate = Coordinate(location.latitude, location.longitude),
            accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() }?.toDouble() ?: 35.0,
            speedMetersPerSecond = location.speed.takeIf { location.hasSpeed() }?.toDouble() ?: 0.0,
            headingDegrees = location.bearing.takeIf { location.hasBearing() }?.toDouble(),
            headingSource = if (location.hasBearing()) NavigationHeadingSource.GpsBearing else NavigationHeadingSource.Unavailable,
            timestampMillis = recordedAt,
            provider = location.provider.orEmpty(),
            altitudeMeters = location.altitude.takeIf { location.hasAltitude() },
        )
        val radius = currentSettings.proximityAlertDistanceMeters.coerceIn(200, 1000).toDouble() + 220.0
        val nearby = spatialIndex.query(currentRadars, fix.coordinate, radius).radars
        engine.check(
            alerts = alerts,
            radars = nearby,
            fix = fix,
            settings = currentSettings,
            onVisual = { visual ->
                if (visual == null) {
                    // O aviso já exibido permanece até reconhecimento/timeout do motor;
                    // não pisca com jitter de GPS.
                    return@check
                }
                activeVisual = visual
                showAlertNotification(visual)
            },
            onDiagnostic = { diagnostic ->
                UnifiedDebugEventStore.record(
                    "BACKGROUND_PROXIMITY_0680",
                    context.packageName,
                    "stage=${diagnostic.stage}; reason=${diagnostic.reason.take(180)}",
                )
            },
        )
    }

    private fun showAlertNotification(visual: DirectionalAlertVisual) {
        val dismiss = PendingIntent.getService(
            context,
            visual.targetId.hashCode(),
            Intent(context, WorkTrackingService::class.java)
                .setAction(WorkTrackingService.ACTION_DISMISS_PROXIMITY_0680)
                .putExtra(WorkTrackingService.EXTRA_PROXIMITY_TARGET_0680, visual.targetId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = when (visual.kind) {
            DirectionalAlertKind.ImportedRadar -> "📡 ${visual.title}"
            DirectionalAlertKind.SavedPlace -> "⚠️ ${visual.title}"
        }
        val distance = if (visual.distanceMeters >= 995.0) {
            String.format(Locale("pt", "BR"), "%.1f km", visual.distanceMeters / 1000.0)
        } else {
            "${visual.distanceMeters.roundToInt().coerceAtLeast(0)} m"
        }
        val details = buildString {
            append(distance)
            visual.speedLimitKmh?.let { append(" • limite ").append(it).append(" km/h") }
            append(" • ").append(visual.status)
        }
        notificationManager.notify(
            ALERT_NOTIFICATION_ID,
            NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(details)
                .setStyle(NotificationCompat.BigTextStyle().bigText(details))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setOnlyAlertOnce(false)
                .setAutoCancel(false)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Reconhecer", dismiss)
                .build(),
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL_ID,
                "Radares e alertas de aproximação",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Avisos de radar e alertas calculados pelo núcleo de localização em segundo plano."
            },
        )
    }

    private companion object {
        const val ALERT_CHANNEL_ID = "background_proximity_0680"
        const val ALERT_NOTIFICATION_ID = 12102
    }
}

internal class BackgroundProximitySpeech0680(
    private val context: Context,
) : ProximitySpeech {
    private val outputStore = SpeechOutputPreferenceStore0186(context)
    @Volatile private var ready = false
    private var tts: TextToSpeech? = null

    fun start() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) runCatching { tts?.language = Locale("pt", "BR") }
        }
    }

    fun stop() {
        ready = false
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
    }

    override fun speakImportedRadar(radar: ImportedRadar, distanceMeters: Double): Boolean {
        val limit = radar.speedKmh?.let { ", limite ${it} quilômetros por hora" }.orEmpty()
        return speak("Radar a ${distanceMeters.roundToInt().coerceAtLeast(0)} metros$limit")
    }

    override fun speakProximityAlert(place: SavedPlace): Boolean = speak(proximityAlertSpeech(place))

    override fun proximityAlertSpeech(place: SavedPlace): String =
        "${place.name.trim().ifBlank { "Alerta de proximidade" }} se aproximando"

    private fun speak(text: String): Boolean {
        val mode = outputStore.read()
        if (!SpeechOutputPolicy0186.shouldProduceAudio(mode)) return true
        val engine = tts ?: return false
        if (!ready) return false
        runCatching { engine.setAudioAttributes(SpeechOutputPolicy0186.audioAttributes(mode)) }
        return engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "background-proximity-0680-${System.currentTimeMillis()}",
        ) == TextToSpeech.SUCCESS
    }
}
