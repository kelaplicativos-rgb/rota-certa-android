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
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
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
    private var overlayView: TextView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null
    private var lastSnapshotHash: Int? = null
    private var lastAnalyzedHash: Int? = null
    private var lastScreenshotMillis: Long = 0L
    private var continuousScanStarted = false
    private var serviceReady = false
    private var analyzing = false
    private var activePackageName: String? = null
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
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceReady = true
        scope.launch {
            repository.settings.collect { currentSettings = it }
        }
        scope.launch {
            currentSettings = repository.settings.first()
            showOverlay(RadarColor.Default)
            startContinuousScan()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!serviceReady || event == null) return
        activePackageName = event.packageName?.toString()
        if (!shouldScanPackage(activePackageName)) {
            resetToDefault()
            return
        }
        scheduleVisibleTextAnalysis(delayMs = 80L)
        requestScreenshotAnalysis()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        serviceReady = false
        screenshotInProgress.set(false)
        analyzeJob?.cancel()
        removeOverlay()
        scope.cancel()
        super.onDestroy()
    }

    private fun startContinuousScan() {
        if (continuousScanStarted || !serviceReady) return
        continuousScanStarted = true
        scope.launch {
            while (serviceReady) {
                if (shouldScanCurrentWindow()) {
                    scheduleVisibleTextAnalysis(delayMs = 0L)
                    requestScreenshotAnalysis()
                } else {
                    resetToDefault()
                }
                delay(SCAN_LOOP_MS)
            }
        }
    }

    private fun scheduleVisibleTextAnalysis(delayMs: Long) {
        if (!serviceReady || !shouldScanCurrentWindow()) return
        analyzeJob?.cancel()
        analyzeJob = scope.launch {
            if (delayMs > 0L) delay(delayMs)
            processRideText(collectVisibleText())
        }
    }

    private fun requestScreenshotAnalysis() {
        if (!serviceReady || !shouldScanCurrentWindow() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val now = System.currentTimeMillis()
        if (now - lastScreenshotMillis < SCREENSHOT_INTERVAL_MS) return
        if (!screenshotInProgress.compareAndSet(false, true)) return
        lastScreenshotMillis = now

        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        scope.launch {
                            runCatching {
                                if (shouldScanCurrentWindow()) {
                                    val bitmap = screenshot.toSoftwareBitmap() ?: return@runCatching
                                    processRideText(ocrService.extractText(bitmap))
                                }
                            }
                            screenshotInProgress.set(false)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        screenshotInProgress.set(false)
                    }
                },
            )
        }.onFailure {
            screenshotInProgress.set(false)
        }
    }

    private fun collectVisibleText(): String {
        if (!shouldScanCurrentWindow()) return ""
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
            collectNodeText(runCatching { node.getChild(index) }.getOrNull(), lines)
        }
    }

    private suspend fun processRideText(text: String) {
        if (!serviceReady || !shouldScanCurrentWindow()) return
        val snapshotText = text.trim()
        if (snapshotText.isBlank()) {
            resetToDefault()
            return
        }

        val snapshotHash = snapshotText.snapshotHash()
        if (snapshotHash != lastSnapshotHash) {
            lastSnapshotHash = snapshotHash
            lastAnalyzedHash = null
            showOverlay(RadarColor.Default)
        }

        val fields = parser.parse(snapshotText)
        if (!looksLikeRideOffer(snapshotText, fields)) {
            resetToDefault()
            return
        }

        if (snapshotHash == lastAnalyzedHash || analyzing) return
        analyzeLiveText(snapshotText, fields, snapshotHash)
    }

    private fun looksLikeRideOffer(text: String, fields: RideFields): Boolean {
        val destination = fields.destination?.lowercase(Locale.ROOT).orEmpty()
        if (destination.isBlank()) return false

        val normalized = text.lowercase(Locale.ROOT)
        val hasDestinationAddressSignal = listOf(
            "rua",
            "r.",
            "avenida",
            "av.",
            "travessa",
            "bairro",
            "jardim",
            "cidade",
            "parque",
            "tatuape",
            "tatuapé",
        ).any { destination.contains(it) } || Regex("""\b\d{1,5}\b""").containsMatchIn(destination)
        val hasRideCardSignal = listOf(
            "pedido de viagem",
            "pedidos de viagem",
            "aceitar",
            "aceitar por",
            "selecionar",
            "negocia",
            "perfil premium",
            "perfil essencial",
            "uberx",
            "pop expresso",
            "exclusivo",
            "viagem longa",
            "radar de viagens",
            "ofereça sua tarifa",
            "ofereca sua tarifa",
            "preço justo",
            "preco justo",
        ).any { normalized.contains(it) }
        val hasMapPointSignal = Regex("""(?m)^\s*[ab]\s+""", RegexOption.IGNORE_CASE).containsMatchIn(text)

        return hasDestinationAddressSignal && (hasRideCardSignal || hasMapPointSignal)
    }

    private suspend fun analyzeLiveText(text: String, fields: RideFields, snapshotHash: Int) {
        if (!serviceReady || !shouldScanCurrentWindow() || analyzing) return
        analyzing = true
        currentSettings = repository.settings.first()

        try {
            val settings = currentSettings
            val region = DeviceRegion(country = "Brasil")
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
            if (snapshotHash != lastSnapshotHash || !shouldScanCurrentWindow()) {
                showOverlay(RadarColor.Default)
                return
            }

            lastAnalyzedHash = snapshotHash
            showOverlay(
                color = when (result.recommendation) {
                    Recommendation.GoodRide -> RadarColor.Green
                    Recommendation.OutsideRadius -> RadarColor.Red
                    Recommendation.InsufficientData -> RadarColor.Default
                },
                distanceKm = result.nearestConfiguredDistanceKm(),
            )
        } catch (_: Exception) {
            showOverlay(RadarColor.Default)
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

    private fun AnalysisResult.nearestConfiguredDistanceKm(): Double? =
        listOfNotNull(pickupToHomeKm, pickupToAlternativeKm).minOrNull()

    private fun resetToDefault() {
        lastSnapshotHash = null
        lastAnalyzedHash = null
        showOverlay(RadarColor.Default)
    }

    private fun shouldScanCurrentWindow(): Boolean {
        val rootPackageName = rootInActiveWindow?.packageName?.toString()
        return shouldScanPackage(rootPackageName ?: activePackageName)
    }

    private fun shouldScanPackage(packageName: String?): Boolean {
        val normalized = packageName?.lowercase(Locale.ROOT) ?: return true
        if (normalized == this.packageName) return false
        if (normalized in IGNORED_PACKAGES) return false

        val settings = currentSettings
        if (!settings.restrictToSelectedRideApps) return true

        return normalized in selectedRidePackages(settings)
    }

    private fun selectedRidePackages(settings: AppSettings): Set<String> {
        val packages = mutableSetOf<String>()
        if (settings.monitor99) packages += PACKAGE_99_DRIVER
        if (settings.monitorUber) packages += PACKAGE_UBER_DRIVER
        if (settings.monitorInDrive) packages += PACKAGE_INDRIVE_DRIVER
        packages += settings.extraMonitoredPackages
            .split(Regex("[,;\\s]+"))
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
        return packages
    }

    private fun showOverlay(color: RadarColor, distanceKm: Double? = null) {
        if (!serviceReady) return
        val manager = windowManager ?: return
        val view = overlayView ?: TextView(this).also { newView ->
            val params = overlayLayoutParams()
            newView.contentDescription = "Rota Certa"
            newView.gravity = Gravity.CENTER
            newView.includeFontPadding = false
            newView.setTextColor(Color.BLACK)
            newView.setTypeface(Typeface.DEFAULT_BOLD)
            newView.setOnClickListener { openApp() }
            newView.setOnTouchListener(BubbleTouchListener())
            val added = runCatching { manager.addView(newView, params) }.isSuccess
            if (!added) {
                overlayView = null
                overlayParams = null
                return
            }
            overlayView = newView
            overlayParams = params
        }
        view.text = formatBubbleDistanceKm(distanceKm)
        view.textSize = if ((distanceKm ?: 0.0) >= 100.0) 11f else 14f
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color.argb(currentSettings))
            setStroke(dp(3), Color.argb((currentSettings.bubbleOpacity.coerceIn(0.25, 1.0) * 255).roundToInt(), 255, 255, 255))
        }
    }

    private fun formatBubbleDistanceKm(distanceKm: Double?): String {
        if (distanceKm == null) return ""
        val compactValue = if (distanceKm >= 10.0) {
            distanceKm.roundToInt().toString()
        } else {
            String.format(Locale("pt", "BR"), "%.1f", distanceKm).removeSuffix(",0")
        }
        return "${compactValue}km"
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        runCatching { windowManager?.removeView(view) }
        overlayView = null
        overlayParams = null
    }

    private fun overlayLayoutParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        dp(66),
        dp(66),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = bubblePrefs.getInt(KEY_BUBBLE_X, dp(18))
        y = bubblePrefs.getInt(KEY_BUBBLE_Y, dp(90))
    }

    private fun openApp() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
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

    private fun String.snapshotHash(): Int =
        lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .hashCode()

    private enum class RadarColor(
        private val normalArgb: Int,
        private val darkArgb: Int,
    ) {
        Default(Color.rgb(241, 196, 15), Color.rgb(133, 100, 4)),
        Green(Color.rgb(46, 204, 113), Color.rgb(24, 106, 59)),
        Red(Color.rgb(231, 76, 60), Color.rgb(127, 29, 29));

        fun argb(settings: AppSettings): Int {
            val base = if (settings.bubbleDarkMode) darkArgb else normalArgb
            val alpha = (settings.bubbleOpacity.coerceIn(0.25, 1.0) * 255).roundToInt()
            return Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
        }
    }

    private companion object {
        const val SCAN_LOOP_MS = 850L
        const val SCREENSHOT_INTERVAL_MS = 650L
        const val BUBBLE_PREFS = "rota_certa_bubble"
        const val KEY_BUBBLE_X = "bubble_x"
        const val KEY_BUBBLE_Y = "bubble_y"
        const val PACKAGE_99_DRIVER = "com.app99.driver"
        const val PACKAGE_UBER_DRIVER = "com.ubercab.driver"
        const val PACKAGE_INDRIVE_DRIVER = "sinet.startup.indriver"
        val IGNORED_PACKAGES = setOf(
            "com.android.settings",
            "com.google.android.apps.maps",
            "com.samsung.android.app.settings",
        )
    }
}
