package br.com.mapeiaia.rotacerta

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
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
import com.google.android.gms.location.CurrentLocationRequest
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class WorkTrackingService : Service() {
    private lateinit var repository: WorkTrackingRepository
    private lateinit var shareManager0668: LiveTrackingShareManager0668
    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var backgroundProximity0680: BackgroundProximityRuntime0680
    private var locationCallback: LocationCallback? = null
    private val uploadScope0668 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val uploadInFlight0668 = AtomicBoolean(false)
    private val watchdogInFlight0670 = AtomicBoolean(false)
    private var heartbeatJob0670: Job? = null
    @Volatile private var latestPoint0670: WorkTrackPoint? = null
    @Volatile private var lastGpsCallbackAtMillis0670: Long = 0L
    private var wakeLock0670: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        repository = WorkTrackingRepository(applicationContext)
        shareManager0668 = LiveTrackingShareManager0668(applicationContext)
        locationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
        backgroundProximity0680 = BackgroundProximityRuntime0680(this)
        createNotificationChannel()
        backgroundProximity0680.start(uploadScope0668)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_STOP -> stopTracking()
            ACTION_DISMISS_PROXIMITY_0680 -> {
                backgroundProximity0680.dismiss(intent?.getStringExtra(EXTRA_PROXIMITY_TARGET_0680))
            }
            else -> startTracking()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        heartbeatJob0670?.cancel()
        heartbeatJob0670 = null
        releaseWakeLock0670()
        removeLocationUpdates()
        backgroundProximity0680.stop()
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
        if (latestPoint0670 == null) {
            val floor0670 = repository.sessionStartedAtMillis() ?: System.currentTimeMillis()
            latestPoint0670 = repository.readAllPoints().lastOrNull { it.recordedAtMillis >= floor0670 }
        }
        startHeartbeatLoop0670()
        if (locationCallback != null) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .setMinUpdateDistanceMeters(0f)
            .setMaxUpdateDelayMillis(UPDATE_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                try {
                    result.locations.forEach(::acceptLocation0670)
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
            .onSuccess { task0670 ->
                task0670.addOnFailureListener { error0670 ->
                    UnifiedDebugEventStore.recordAlways(
                        "LIVE_GPS_TRACKER_REQUEST_FAILED_0670",
                        packageName,
                        "type=${error0670::class.java.simpleName}",
                    )
                }
            }
            .onFailure { error0670 ->
                UnifiedDebugEventStore.recordAlways(
                    "LIVE_GPS_TRACKER_REQUEST_THROWN_0670",
                    packageName,
                    "type=${error0670::class.java.simpleName}",
                )
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
            }
        }
    }

    private fun acceptLocation0670(location: Location) {
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return
        val point0670 = WorkTrackPoint(
            coordinate = Coordinate(location.latitude, location.longitude),
            recordedAtMillis = location.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
            accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() },
            speedMetersPerSecond = location.speed.takeIf { location.hasSpeed() },
        )
        val previous0670 = latestPoint0670
        if (previous0670 == null || point0670.recordedAtMillis > previous0670.recordedAtMillis) {
            repository.append(point0670)
            latestPoint0670 = point0670
        }
        lastGpsCallbackAtMillis0670 = System.currentTimeMillis()
        backgroundProximity0680.onLocation(location)
    }

    private fun startHeartbeatLoop0670() {
        if (heartbeatJob0670?.isActive == true) return
        heartbeatJob0670 = uploadScope0668.launch {
            while (isActive) {
                val sharing0670 = shareManager0668.hasActiveShares()
                updateWakeLock0670(sharing0670)
                if (sharing0670) {
                    val now0670 = System.currentTimeMillis()
                    if (
                        lastGpsCallbackAtMillis0670 <= 0L ||
                        now0670 - lastGpsCallbackAtMillis0670 >= GPS_WATCHDOG_AFTER_MS
                    ) {
                        requestWatchdogLocation0670()
                    }
                    runCatching {
                        shareManager0668.sendHeartbeat0670(
                            latestPoint = latestPoint0670,
                            nowMillis = System.currentTimeMillis(),
                        )
                    }
                    scheduleRemoteSync0668()
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun requestWatchdogLocation0670() {
        if (!hasLocationPermission()) return
        if (!watchdogInFlight0670.compareAndSet(false, true)) return
        try {
            val request0670 = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(5_000L)
                .setDurationMillis(GPS_WATCHDOG_REQUEST_TIMEOUT_MS)
                .build()
            val location0670 = locationClient.getCurrentLocation(request0670, null).await()
            if (location0670 != null) {
                acceptLocation0670(location0670)
                scheduleRemoteSync0668()
            }
        } catch (error0670: Exception) {
            UnifiedDebugEventStore.record(
                "LIVE_GPS_TRACKER_WATCHDOG_FAILURE_0670",
                packageName,
                "type=${error0670::class.java.simpleName}",
            )
        } finally {
            watchdogInFlight0670.set(false)
        }
    }

    private fun updateWakeLock0670(required0670: Boolean) {
        if (!required0670) {
            releaseWakeLock0670()
            return
        }
        val current0670 = wakeLock0670
        if (current0670?.isHeld == true) return
        val manager0670 = getSystemService(PowerManager::class.java) ?: return
        val lock0670 = manager0670.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:LiveGpsTracker0670",
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
        wakeLock0670 = lock0670
    }

    private fun releaseWakeLock0670() {
        wakeLock0670?.let { lock0670 ->
            if (lock0670.isHeld) runCatching { lock0670.release() }
        }
        wakeLock0670 = null
    }

    private fun stopTracking() {
        heartbeatJob0670?.cancel()
        heartbeatJob0670 = null
        releaseWakeLock0670()
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
        const val ACTION_DISMISS_PROXIMITY_0680 = "br.com.mapeiaia.rotacerta.action.DISMISS_PROXIMITY_0680"
        const val EXTRA_PROXIMITY_TARGET_0680 = "proximity_target_0680"
        private const val CHANNEL_ID = "work_tracking"
        private const val NOTIFICATION_ID = 12101
        private const val UPDATE_INTERVAL_MS = 5_000L
        private const val MIN_UPDATE_INTERVAL_MS = 5_000L
        private const val HEARTBEAT_INTERVAL_MS = 5_000L
        private const val GPS_WATCHDOG_AFTER_MS = 15_000L
        private const val GPS_WATCHDOG_REQUEST_TIMEOUT_MS = 10_000L
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