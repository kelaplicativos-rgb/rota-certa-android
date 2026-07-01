package br.com.mapeiaia.rotacerta

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class RadarService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handledImageIds = mutableSetOf<Long>()
    private var observer: ContentObserver? = null
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var serviceStartSeconds: Long = 0L
    private var analyzing = false

    private lateinit var repository: SettingsRepository
    private lateinit var ocrService: OcrService
    private lateinit var geocodingService: GeocodingService
    private lateinit var googleMapsService: GoogleMapsService
    private lateinit var parser: RideTextParser
    private lateinit var decisionEngine: DecisionEngine

    override fun onCreate() {
        super.onCreate()
        serviceStartSeconds = System.currentTimeMillis() / 1000L
        repository = SettingsRepository(applicationContext)
        ocrService = OcrService(applicationContext)
        geocodingService = GeocodingService(applicationContext)
        googleMapsService = GoogleMapsService()
        parser = RideTextParser()
        decisionEngine = DecisionEngine()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()
        showOverlay(RadarColor.Yellow)
        registerScreenshotObserver()
        return START_STICKY
    }

    override fun onDestroy() {
        observer?.let { contentResolver.unregisterContentObserver(it) }
        removeOverlay()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerScreenshotObserver() {
        if (observer != null) return

        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                scheduleLatestScreenshotAnalysis()
            }
        }

        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer!!,
        )
    }

    private fun scheduleLatestScreenshotAnalysis() {
        if (analyzing || !hasMediaPermission()) return
        scope.launch {
            delay(250)
            analyzeLatestScreenshot()
        }
    }

    private suspend fun analyzeLatestScreenshot() {
        if (analyzing) return
        analyzing = true
        showOverlay(RadarColor.Yellow)

        try {
            val image = latestScreenshot() ?: return
            if (!handledImageIds.add(image.id)) return

            val settings = repository.settings.first()
            val region = DeviceRegion(country = "Brasil")
            val extractedText = ocrService.extractText(image.uri)
            val fields = parser.parse(extractedText)
            val destinationCoordinate = fields.destination?.let { geocodeBest(it, region, settings) }
            val homeCoordinate = settings.homeCoordinate ?: geocodeBest(settings.homeAddress, region, settings)
            val alternativeCoordinate = settings.alternativeCoordinate ?: geocodeBest(settings.alternativeAddress, region, settings)
            val homeDistanceKm = routeDistanceKm(destinationCoordinate, homeCoordinate, settings)
            val alternativeDistanceKm = routeDistanceKm(destinationCoordinate, alternativeCoordinate, settings)

            val result = decisionEngine.decide(
                fields = fields,
                settings = settings,
                destinationCoordinate = destinationCoordinate,
                homeCoordinate = homeCoordinate,
                alternativeCoordinate = alternativeCoordinate,
                fullText = extractedText,
                homeDistanceKm = homeDistanceKm,
                alternativeDistanceKm = alternativeDistanceKm,
            )

            repository.addAnalysis(result)
            showOverlay(
                when (result.recommendation) {
                    Recommendation.GoodRide -> RadarColor.Green
                    Recommendation.OutsideRadius -> RadarColor.Red
                    Recommendation.InsufficientData -> RadarColor.Yellow
                },
            )
        } catch (_: Exception) {
            showOverlay(RadarColor.Yellow)
        } finally {
            analyzing = false
        }
    }

    private suspend fun geocodeBest(query: String, region: DeviceRegion, settings: AppSettings): Coordinate? =
        googleMapsService.geocode(query, region, settings.googleMapsApiKey)
            ?: geocodingService.geocode(query, region)

    private suspend fun routeDistanceKm(origin: Coordinate?, destination: Coordinate?, settings: AppSettings): Double? =
        if (origin != null && destination != null && settings.googleMapsApiKey.isNotBlank()) {
            googleMapsService.drivingDistanceKm(origin, destination, settings.googleMapsApiKey)
        } else {
            null
        }

    private suspend fun latestScreenshot(): ScreenshotImage? = withContext(Dispatchers.IO) {
        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DATE_ADDED)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.Images.Media.RELATIVE_PATH)
        }.toTypedArray()

        runCatching {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)).orEmpty()
                    val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)).orEmpty()
                    } else {
                        ""
                    }

                    if (dateAdded < serviceStartSeconds - 3L) return@use null
                    if (isScreenshot(name, path)) {
                        return@use ScreenshotImage(
                            id = id,
                            uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                        )
                    }
                }
                null
            }
        }.getOrNull()
    }

    private fun isScreenshot(name: String, path: String): Boolean {
        val value = "$name $path".lowercase(Locale.ROOT)
        return value.contains("screenshot") ||
            value.contains("screen_shot") ||
            value.contains("captura") ||
            value.contains("print") ||
            value.contains("screenshots")
    }

    private fun hasMediaPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun showOverlay(color: RadarColor) {
        if (!Settings.canDrawOverlays(this)) return
        val manager = windowManager ?: return
        val view = overlayView ?: View(this).also { newView ->
            overlayView = newView
            manager.addView(newView, overlayLayoutParams())
        }
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color.argb)
            setStroke(dp(3), Color.WHITE)
        }
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        runCatching { windowManager?.removeView(view) }
        overlayView = null
    }

    private fun overlayLayoutParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        dp(58),
        dp(58),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = dp(18)
        y = dp(160)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun startAsForeground() {
        val notification = notification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Radar Rota Certa",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Rota Certa ativo")
            .setContentText("Monitorando novos prints da galeria")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    private data class ScreenshotImage(
        val id: Long,
        val uri: Uri,
    )

    private enum class RadarColor(val argb: Int) {
        Green(Color.rgb(46, 204, 113)),
        Red(Color.rgb(231, 76, 60)),
        Yellow(Color.rgb(241, 196, 15)),
    }

    companion object {
        const val ACTION_START = "br.com.mapeiaia.rotacerta.RADAR_START"
        const val ACTION_STOP = "br.com.mapeiaia.rotacerta.RADAR_STOP"
        private const val CHANNEL_ID = "rota_certa_radar"
        private const val NOTIFICATION_ID = 701
    }
}
