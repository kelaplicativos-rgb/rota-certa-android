package br.com.mapeiaia.rotacerta

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class LiveRideAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var analyzeJob: Job? = null
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var lastTextHash: Int? = null
    private var lastAnalysisMillis: Long = 0L
    private var analyzing = false

    private lateinit var repository: SettingsRepository
    private lateinit var geocodingService: GeocodingService
    private lateinit var googleMapsService: GoogleMapsService
    private lateinit var parser: RideTextParser
    private lateinit var decisionEngine: DecisionEngine

    override fun onCreate() {
        super.onCreate()
        repository = SettingsRepository(applicationContext)
        geocodingService = GeocodingService(applicationContext)
        googleMapsService = GoogleMapsService()
        parser = RideTextParser()
        decisionEngine = DecisionEngine()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName == packageName) return
        scheduleLiveAnalysis()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        removeOverlay()
        scope.cancel()
        super.onDestroy()
    }

    private fun scheduleLiveAnalysis() {
        analyzeJob?.cancel()
        analyzeJob = scope.launch {
            delay(180)
            val text = collectVisibleText()
            if (!looksLikeRideOffer(text)) return@launch

            val now = System.currentTimeMillis()
            val hash = text.hashCode()
            if (hash == lastTextHash && now - lastAnalysisMillis < 2500L) return@launch
            lastTextHash = hash
            lastAnalysisMillis = now

            analyzeLiveText(text)
        }
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

    private fun looksLikeRideOffer(text: String): Boolean {
        val normalized = text.lowercase(Locale.ROOT)
        val hasMoney = normalized.contains("r$")
        val hasTripMetric = normalized.contains("km") || normalized.contains("min")
        val hasAddressSignal = listOf("rua", "avenida", "av.", "travessa", "bairro", "jardim", "cidade", "parque")
            .any { normalized.contains(it) }
        val hasRideSignal = listOf("pedido de viagem", "aceitar", "corrida", "tarifa", "perfil premium", "preço justo")
            .any { normalized.contains(it) }

        return hasMoney && hasTripMetric && (hasAddressSignal || hasRideSignal)
    }

    private suspend fun analyzeLiveText(text: String) {
        if (analyzing) return
        analyzing = true
        showOverlay(RadarColor.Yellow)

        try {
            val settings = repository.settings.first()
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
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = dp(18)
        y = dp(90)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private enum class RadarColor(val argb: Int) {
        Green(Color.rgb(46, 204, 113)),
        Red(Color.rgb(231, 76, 60)),
        Yellow(Color.rgb(241, 196, 15)),
    }
}
