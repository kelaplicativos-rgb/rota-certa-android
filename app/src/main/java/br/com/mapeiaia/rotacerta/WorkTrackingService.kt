package br.com.mapeiaia.rotacerta

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WorkTrackingService : Service() {
    private lateinit var repository: WorkTrackingRepository
    private lateinit var shareManager0668: LiveTrackingShareManager0668
    private lateinit var locationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private val uploadScope0668 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val uploadInFlight0668 = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        repository = WorkTrackingRepository(applicationContext)
        shareManager0668 = LiveTrackingShareManager0668(applicationContext)
        locationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_STOP -> stopTracking()
            else -> startTracking()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeLocationUpdates()
        uploadScope0668.cancel()
        super.onDestroy()
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun startTracking() {
        if (!hasLocationPermission()) {
            repository.markTrackingStopped()
            stopSelf()
            return
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!repository.isTrackingActive()) repository.markTrackingStarted()
        if (locationCallback != null) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_METERS)
            .setWaitForAccurateLocation(false)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                try {
                    result.locations.forEach { location ->
                        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return@forEach
                        repository.append(
                            WorkTrackPoint(
                                coordinate = Coordinate(location.latitude, location.longitude),
                                recordedAtMillis = location.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
                                accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() },
                                speedMetersPerSecond = location.speed.takeIf { location.hasSpeed() },
                            ),
                        )
                    }
                    scheduleRemoteSync0668()
                } catch (error0172: Exception) {
                    UnifiedDebugEventStore.record(
                        "WORK_TRACKING_LOCATION_FAILURE_CONTAINED_0172",
                        packageName,
                        "type=${error0172::class.java.simpleName}; points=${result.locations.size}",
                    )
                }
            }
        }
        locationCallback = callback
        runCatching { locationClient.requestLocationUpdates(request, callback, mainLooper) }
            .onFailure {
                repository.markTrackingStopped()
                stopSelf()
            }
        scheduleRemoteSync0668()
    }

    private fun scheduleRemoteSync0668() {
        if (!shareManager0668.hasActiveShares()) return
        if (!uploadInFlight0668.compareAndSet(false, true)) return
        uploadScope0668.launch {
            try {
                do {
                    val synced = runCatching { shareManager0668.syncPendingPoints() }.getOrDefault(false)
                } while (synced && shareManager0668.hasPendingPoints())
            } finally {
                uploadInFlight0668.set(false)
                if (shareManager0668.hasPendingPoints()) scheduleRemoteSync0668()
            }
        }
    }

    private fun stopTracking() {
        repository.markTrackingStopped()
        removeLocationUpdates()
        val hadSharedSession0668 = shareManager0668.hasActiveShares()
        if (hadSharedSession0668) {
            enqueueTrackingClose0668(applicationContext)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun removeLocationUpdates() {
        locationCallback?.let { callback -> runCatching { locationClient.removeLocationUpdates(callback) } }
        locationCallback = null
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setContentTitle(
            if (shareManager0668.hasActiveShares()) "Acompanhamento ao vivo ativo" else "Rastreamento de trabalho ativo",
        )
        .setContentText(
            if (shareManager0668.hasActiveShares()) {
                "O percurso está sendo gravado e sincronizado com os links que você ativou."
            } else {
                "O Rota Certa está registrando o percurso somente neste aparelho."
            },
        )
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, WorkTrackingActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .addAction(
            android.R.drawable.ic_media_pause,
            "Parar",
            PendingIntent.getService(
                this,
                1,
                Intent(this, WorkTrackingService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Rastreamento de trabalho",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Mantém visível quando o percurso está sendo registrado ou compartilhado."
            },
        )
    }

    companion object {
        const val ACTION_START = "br.com.mapeiaia.rotacerta.action.START_WORK_TRACKING"
        const val ACTION_STOP = "br.com.mapeiaia.rotacerta.action.STOP_WORK_TRACKING"
        private const val CHANNEL_ID = "work_tracking"
        private const val NOTIFICATION_ID = 12101
        private const val UPDATE_INTERVAL_MS = 5_000L
        private const val MIN_UPDATE_INTERVAL_MS = 2_000L
        private const val MIN_UPDATE_DISTANCE_METERS = 8f
    }
}

class LiveTrackingCloseWorker0668(
    appContext: android.content.Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val manager = LiveTrackingShareManager0668(applicationContext)
        if (!manager.hasActiveShares()) return@withContext Result.success()
        runCatching { manager.closeActiveSession() }
            .fold(
                onSuccess = { Result.success() },
                onFailure = { Result.retry() },
            )
    }
}

internal fun enqueueTrackingClose0668(context: android.content.Context) {
    val request = OneTimeWorkRequestBuilder<LiveTrackingCloseWorker0668>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
        .build()
    WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
        "live-tracking-close-0668",
        ExistingWorkPolicy.REPLACE,
        request,
    )
}