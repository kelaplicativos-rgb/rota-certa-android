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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class WorkTrackingService : Service() {
    private lateinit var repository: WorkTrackingRepository
    private lateinit var locationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        repository = WorkTrackingRepository(applicationContext)
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
        super.onDestroy()
    }

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
            }
        }
        locationCallback = callback
        runCatching { locationClient.requestLocationUpdates(request, callback, mainLooper) }
            .onFailure {
                repository.markTrackingStopped()
                stopSelf()
            }
    }

    private fun stopTracking() {
        repository.markTrackingStopped()
        removeLocationUpdates()
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
        .setContentTitle("Rastreamento de trabalho ativo")
        .setContentText("O Rota Certa esta registrando o percurso neste aparelho.")
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
                description = "Mantem visivel quando o percurso esta sendo registrado."
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
