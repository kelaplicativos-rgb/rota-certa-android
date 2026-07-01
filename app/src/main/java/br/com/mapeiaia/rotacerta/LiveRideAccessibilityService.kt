package br.com.mapeiaia.rotacerta

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.roundToInt

class LiveRideAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val screenshotInProgress = AtomicBoolean(false)
    private var analyzeJob: Job? = null
    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null
    private var lastTextHash: Int? = null
    private var lastAnalysisMillis: Long = 0L
    private var lastScreenshotMillis: Long = 0L
    private var continuousScanStarted = false
    private var analyzing = false
    private var currentSettings = AppSettings()

    private lateinit var repository: SettingsRepository
    private lateinit var geocodingService: GeocodingService
    private lateinit var googleMapsService: GoogleMapsService
    private lateinit var ocrService: OcrService
    private lateinit var parser: RideTextParser
    private lateinit var decisionEngine: DecisionEngine
    private lateinit var bubblePrefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        repository = SettingsRepository(applicationContext)
        geocodingService = GeocodingService(applicationContext)
        googleMapsService = GoogleMapsService()
        ocrService = OcrService(applicationContext)
        parser = RideTextParser()
        decisionEngine = DecisionEngine()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        bubblePrefs = getSharedPreferences(BUBBLE_PREFS, Context.MODE_PRIVATE)
        startContinuousScan()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName == packageName) return
        scheduleVisibleTextAnalysis(delayMs = 80L)
        requestScreenshotAnalysis()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        removeOverlay()
        scope.cancel()
        super.onDestroy()
    }

    private fun startContinuousScan() {
        if (continuousScanStarted) return
        continuousScanStarted = true
        scope.launch {
            while (true) {
                scheduleVisibleTextAnalysis(delayMs = 0L)
                requestScreenshotAnalysis()
                delay(SCAN_LOOP_MS)
            }
        }
    }

    private fun scheduleVisibleTextAnalysis(delayMs: Long) {
        analyzeJob?.cancel()
        analyzeJob = scope.launch {
            if (delayMs > 0L) delay(delayMs)
            processRideText(collectVisibleText())
        }
    }

    private fun requestScreenshotAnalysis() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val now = System.currentTimeMillis()
        if (now - lastScreenshotMillis < SCREENSHOT_INTERVAL_MS) return
        if (!screenshotInProgress.compareAndSet(false, true)) return
        lastScreenshotMillis = now

        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    scope.launch {
                        runCatching {
                            val bitmap = screenshot.toSoftwareBitmap() ?: return@runCatching
                            processRideText(ocrService.extractText(bitmap))
                        }
                        screenshotInProgress.set(false)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    screenshotInProgress.set(false)
                }
            },
        )
    }

    private fun collectVisibleText(): String {
        val root = rootInActiveWindow ?: return ""
        val lines = mutableListOf<String>()
        collectNodeText(root, lines)
        return lines
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
    }

    private fun collectNodeText(node: AccessibilityNodeInfo?, lines: MutableList<String>) {
        if (node == null) return

        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { lines += it }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { lines += it }

        for (index in 0 until node.childCount) {
            collectNodeText(node.getChild(index), lines)
        }
    }

    private suspend fun processRideText(text: String) {
        if (!looksLikeRideOffer(text)) return

        val now = System.currentTimeMillis()
        val hash = text.normalizedHash()
        if (hash == lastTextHash && now - lastAnalysisMillis < DUPLICATE_WINDOW_MS) return
        lastTextHash = hash
        lastAnalysisMillis = now

        analyzeLiveText(text)
    }

    private fun looksLikeRideOffer(text: String): Boolean {
        val normalized = text.lowercase(Locale.ROOT)
        val hasMoney = normalized.contains("r$")
        val hasTripMetric = normalized.contains("km") || normalized.contains("min") || normalized.contains("minuto")
        val hasAddressSignal = listOf("rua", "r.", "avenida", "av.", "travessa", "bairro", "jardim", "cidade", "parque", "tatuape", "tatuapé")
            .any { normalized.contains(it) }
        val hasRideSignal = listOf("pedido de viagem", "aceitar", "corrida", "corridas", "tarifa", "perfil premium", "preço justo", "exclusivo", "uber", "dinheiro", "viagem longa")
            .any { normalized.contains(it) }

        return hasMoney && hasTripMetric && (hasAddressSignal || hasRideSignal)
    }

    private suspend fun analyzeLiveText(text: String) {
        if (analyzing) return
        analyzing = true
        currentSettings = repository.settings.first()
        showOverlay(RadarColor.Yellow)

        try {
            val settings = currentSettings
            val region = DeviceRegion(country = "Brasil")
            val fields = parser.parse(text)
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
                fullText = text,
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

    private fun showOverlay(color: RadarColor) {
        val manager = windowManager ?: return
        val view = overlayView ?: View(this).also { newView ->
            val params = overlayLayoutParams()
            newView.contentDescription = "Rota Certa"
            newView.setOnTouchListener(BubbleTouchListener())
            overlayView = newView
            overlayParams = params
            manager.addView(newView, params)
        }
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color.argb(currentSettings))
            setStroke(dp(3), Color.argb((currentSettings.bubbleOpacity.coerceIn(0.25, 1.0) * 255).roundToInt(), 255, 255, 255))
        }
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        runCatching { windowManager?.removeView(view) }
        overlayView = null
        overlayParams = null
    }

    private fun overlayLayoutParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        dp(58),
        dp(58),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = bubblePrefs.getInt(KEY_BUBBLE_X, dp(18))
        y = bubblePrefs.getInt(KEY_BUBBLE_Y, dp(90))
    }

    private fun openApp() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }

    private inner class BubbleTouchListener : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = overlayParams ?: return false
            val manager = windowManager ?: return false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    if (abs(deltaX) > dp(4) || abs(deltaY) > dp(4)) moved = true
                    params.x = (startX + deltaX).roundToInt().coerceAtLeast(0)
                    params.y = (startY + deltaY).roundToInt().coerceAtLeast(0)
                    runCatching { manager.updateViewLayout(view, params) }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    bubblePrefs.edit()
                        .putInt(KEY_BUBBLE_X, params.x)
                        .putInt(KEY_BUBBLE_Y, params.y)
                        .apply()
                    if (!moved) {
                        view.performClick()
                        openApp()
                    }
                    return true
                }
            }

            return false
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    @RequiresApi(Build.VERSION_CODES.R)
    private fun ScreenshotResult.toSoftwareBitmap(): Bitmap? {
        val buffer = hardwareBuffer
        return try {
            val hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, colorSpace) ?: return null
            hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
        } finally {
            buffer.close()
        }
    }

    private fun String.normalizedHash(): Int =
        lowercase(Locale.ROOT)
            .replace(Regex("""\s+"""), " ")
            .trim()
            .hashCode()

    private enum class RadarColor(
        private val normalArgb: Int,
        private val darkArgb: Int,
    ) {
        Green(Color.rgb(46, 204, 113), Color.rgb(24, 106, 59)),
        Red(Color.rgb(231, 76, 60), Color.rgb(127, 29, 29)),
        Yellow(Color.rgb(241, 196, 15), Color.rgb(133, 100, 4));

        fun argb(settings: AppSettings): Int {
            val base = if (settings.bubbleDarkMode) darkArgb else normalArgb
            val alpha = (settings.bubbleOpacity.coerceIn(0.25, 1.0) * 255).roundToInt()
            return Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
        }
    }

    private companion object {
        const val SCAN_LOOP_MS = 850L
        const val SCREENSHOT_INTERVAL_MS = 650L
        const val DUPLICATE_WINDOW_MS = 2500L
        const val BUBBLE_PREFS = "rota_certa_bubble"
        const val KEY_BUBBLE_X = "bubble_x"
        const val KEY_BUBBLE_Y = "bubble_y"
    }
}
