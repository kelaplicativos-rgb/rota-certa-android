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
import android.speech.tts.TextToSpeech
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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

class StableBubbleAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val screenshotInProgress = AtomicBoolean(false)
    private val diagnosticEvents = mutableListOf<String>()

    private var serviceReady = false
    private var scanStarted = false
    private var proximityStarted = false
    private var analyzing = false
    private var screenGeneration = 0L
    private var activeRidePackage: String? = null
    private var lastTextPackage: String? = null
    private var lastAccessibilityText = ""
    private var lastOcrText = ""
    private var lastSnapshotHash: Int? = null
    private var lastAnalyzedHash: Int? = null
    private var lastSavedReadHash: Int? = null
    private var lastDiagnosticSignature: String? = null
    private var analyzeJob: Job? = null
    private var lastScreenshotMillis = 0L

    private var overlayView: TextView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var overlayMenuView: LinearLayout? = null
    private var overlayMenuParams: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null
    private var currentBubbleColor = BubbleColor.Idle
    private var currentBubbleDistanceKm: Double? = null

    private var currentSettings = AppSettings()
    private var currentCardTemplates = emptyList<RideCardTemplate>()
    private var currentSavedPlaces = emptyList<SavedPlace>()
    private var currentImportedRadars = emptyList<ImportedRadar>()
    private var textToSpeech: TextToSpeech? = null
    private var textToSpeechReady = false

    private lateinit var repository: SettingsRepository
    private lateinit var geocodingService: GeocodingService
    private lateinit var gpsAddressResolver: GpsAddressResolver
    private lateinit var locationService: DeviceLocationService
    private lateinit var googleMapsService: GoogleMapsService
    private lateinit var ocrService: OcrService
    private lateinit var parser: RideTextParser
    private lateinit var decisionEngine: DecisionEngine
    private lateinit var bubblePrefs: SharedPreferences
    private lateinit var proximityAlertEngine: ProximityAlertEngine
    private lateinit var speechEngine: LiveSpeechEngine

    override fun onCreate() {
        super.onCreate()
        repository = SettingsRepository(applicationContext)
        geocodingService = GeocodingService(applicationContext)
        gpsAddressResolver = GpsAddressResolver(applicationContext)
        locationService = DeviceLocationService(applicationContext)
        googleMapsService = GoogleMapsService()
        ocrService = OcrService(applicationContext)
        parser = RideTextParser()
        decisionEngine = DecisionEngine()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        bubblePrefs = getSharedPreferences(BUBBLE_PREFS, Context.MODE_PRIVATE)
        textToSpeech = TextToSpeech(applicationContext) { status ->
            textToSpeechReady = status == TextToSpeech.SUCCESS
            if (textToSpeechReady) textToSpeech?.language = Locale("pt", "BR")
        }
        speechEngine = LiveSpeechEngine(
            textToSpeechProvider = { textToSpeech },
            isReady = { textToSpeechReady },
            trace = ::traceEvent,
        )
        proximityAlertEngine = ProximityAlertEngine(speechEngine)
        traceEvent("stable.service.create")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceReady = true
        traceEvent("stable.service.connected")
        scope.launch { repository.settings.collect { currentSettings = it } }
        scope.launch { repository.cardTemplates.collect { currentCardTemplates = it } }
        scope.launch { repository.savedPlaces.collect { currentSavedPlaces = it } }
        scope.launch { repository.importedRadars.collect { currentImportedRadars = it } }
        scope.launch {
            currentSettings = repository.settings.first()
            currentCardTemplates = repository.cardTemplates.first()
            showBubble(BubbleColor.Idle)
            recordDiagnostic(
                stage = "service_connected",
                color = BubbleColor.Idle,
                reason = "Servico conectado; bolinha cinza em espera ate aparecer app de corrida monitorado.",
            )
            startScanLoop()
            startProximityLoop()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!serviceReady || event == null) return
        val eventPackage = normalizePackage(event.packageName?.toString())
        val windowPackage = eventPackage ?: currentRootPackageName()
        traceEvent("event package=${windowPackage.orEmpty()} type=${event.eventType}")
        handleWindowPackage(windowPackage, reason = blockReason(windowPackage), recordIdle = true)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        traceEvent("stable.service.destroy")
        serviceReady = false
        analyzeJob?.cancel()
        screenshotInProgress.set(false)
        removeBubble()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        textToSpeechReady = false
        scope.cancel()
        super.onDestroy()
    }

    private fun startScanLoop() {
        if (scanStarted || !serviceReady) return
        scanStarted = true
        scope.launch {
            while (serviceReady) {
                handleWindowPackage(currentWindowPackageName(), reason = "Varredura continua.", recordIdle = false)
                delay(SCAN_LOOP_MS)
            }
        }
    }

    private fun startProximityLoop() {
        if (proximityStarted || !serviceReady) return
        proximityStarted = true
        scope.launch {
            while (serviceReady) {
                val alerts = currentSavedPlaces.filter { it.type == SavedPlaceType.ProximityAlert }
                val radars = currentImportedRadars
                if (alerts.isNotEmpty() || radars.isNotEmpty()) {
                    locationService.currentCoordinate()?.let { coordinate ->
                        proximityAlertEngine.check(
                            alerts = alerts,
                            radars = radars,
                            coordinate = coordinate,
                            settings = currentSettings,
                        ) { diagnostic ->
                            recordDiagnostic(stage = diagnostic.stage, reason = diagnostic.reason)
                        }
                    }
                }
                delay(PROXIMITY_LOOP_MS)
            }
        }
    }

    private fun handleWindowPackage(packageName: String?, reason: String, recordIdle: Boolean) {
        val normalized = normalizePackage(packageName)
        if (!shouldScanPackage(normalized)) {
            moveToIdle(reason, recordIdle)
            return
        }

        if (activeRidePackage != normalized) {
            activeRidePackage = normalized
            screenGeneration += 1
            clearRememberedText()
            lastSnapshotHash = null
            lastAnalyzedHash = null
            traceEvent("ride.package.active package=${normalized.orEmpty()} generation=$screenGeneration")
        }
        showBubble(BubbleColor.Default)
        scheduleAccessibilityAnalysis(normalized, screenGeneration)
        requestOcrAnalysis(normalized, screenGeneration)
    }

    private fun scheduleAccessibilityAnalysis(packageName: String?, generation: Long) {
        if (analyzing || analyzeJob?.isActive == true) return
        analyzeJob = scope.launch {
            delay(ACCESSIBILITY_DELAY_MS)
            if (!isCurrentRideWindow(packageName, generation)) {
                traceEvent("accessibility.discard stale generation=$generation")
                return@launch
            }
            val text = collectVisibleText()
            traceEvent("accessibility.collect package=${packageName.orEmpty()} length=${text.length}")
            processRideText(text, TextSource.Accessibility, packageName, generation)
        }
    }

    private fun requestOcrAnalysis(packageName: String?, generation: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (!isCurrentRideWindow(packageName, generation)) return
        val now = System.currentTimeMillis()
        if (now - lastScreenshotMillis < SCREENSHOT_INTERVAL_MS) return
        if (!screenshotInProgress.compareAndSet(false, true)) return
        lastScreenshotMillis = now
        traceEvent("screenshot.request package=${packageName.orEmpty()} generation=$generation")
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        scope.launch {
                            runCatching {
                                if (!isCurrentRideWindow(packageName, generation)) {
                                    traceEvent("screenshot.discard before_ocr generation=$generation")
                                    return@runCatching
                                }
                                val bitmap = screenshot.toSoftwareBitmap() ?: return@runCatching
                                val text = ocrService.extractText(bitmap)
                                if (!isCurrentRideWindow(packageName, generation)) {
                                    traceEvent("screenshot.discard after_ocr generation=$generation length=${text.length}")
                                    return@runCatching
                                }
                                traceEvent("screenshot.ocr success length=${text.length}")
                                processRideText(text, TextSource.Ocr, packageName, generation)
                            }.onFailure { error ->
                                traceEvent("screenshot.ocr error=${error::class.java.simpleName}: ${error.message.orEmpty()}")
                                recordDiagnostic(
                                    stage = "screenshot_ocr_error",
                                    reason = "Falha ao ler texto do print da tela.",
                                    error = error,
                                )
                            }
                            screenshotInProgress.set(false)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        traceEvent("screenshot.request failed code=$errorCode")
                        screenshotInProgress.set(false)
                    }
                },
            )
        }.onFailure { error ->
            traceEvent("screenshot.request error=${error::class.java.simpleName}: ${error.message.orEmpty()}")
            screenshotInProgress.set(false)
        }
    }

    private suspend fun processRideText(text: String, source: TextSource, packageName: String?, generation: Long) {
        if (!isCurrentRideWindow(packageName, generation)) return
        rememberSourceText(packageName, source, text)
        val snapshotText = mergeTexts(lastAccessibilityText, lastOcrText).ifBlank { text.trim() }
        if (snapshotText.isBlank()) {
            resetToDefault("Texto visivel vazio; aguardando card cadastrado.", record = false)
            return
        }

        val snapshotHash = snapshotText.snapshotHash()
        if (snapshotHash != lastSnapshotHash) {
            lastSnapshotHash = snapshotHash
            lastAnalyzedHash = null
            showBubble(BubbleColor.Default)
            recordDiagnostic(
                stage = "screen_changed",
                color = BubbleColor.Default,
                reason = "Tela mudou; aguardando confirmar card cadastrado.",
                text = snapshotText,
            )
        }

        RideScreenTextClassifier.ignoreReason(snapshotText)?.let { reason ->
            saveInsufficient(snapshotText, RideFields(), snapshotHash, reason)
            resetToDefault(reason, text = snapshotText, record = true)
            return
        }

        val parseResult = parser.parseWithMetadata(snapshotText, packageName)
        val fields = parseResult.fields
        traceEvent("parser.name=${parseResult.parserName} pickup=${fields.pickup.diagnosticValue()} destination=${fields.destination.diagnosticValue()} fare=${fields.fare.orEmpty()}")

        if (!RideOfferDetector.looksLikeRideOffer(snapshotText, fields, packageName)) {
            val reason = RideOfferDetector.rejectReason(fields)
            saveInsufficient(snapshotText, fields, snapshotHash, reason)
            resetToDefault(reason, text = snapshotText, fields = fields, record = true)
            return
        }

        val match = RideCardTemplateMatcher.match(snapshotText, packageName, currentCardTemplates)
        if (match == null) {
            val reason = "Tela parece corrida, mas nao bate com modelo cadastrado. Bolinha fica amarela ate cadastrar este card."
            repository.addCapturedScreen(
                CapturedRideScreen(
                    createdAtMillis = System.currentTimeMillis(),
                    packageName = packageName,
                    textHash = snapshotHash,
                    textPreview = snapshotText.take(DIAGNOSTIC_TEXT_LIMIT),
                    parserName = parseResult.parserName,
                    pickup = fields.pickup,
                    destination = fields.destination,
                    fare = fields.fare,
                ),
            )
            saveInsufficient(snapshotText, fields, snapshotHash, reason)
            resetToDefault(reason, text = snapshotText, fields = fields, record = true)
            return
        }

        if (snapshotHash == lastAnalyzedHash || analyzing) return
        analyzeRide(snapshotText, fields, snapshotHash, match, packageName, generation)
    }

    private suspend fun analyzeRide(
        text: String,
        fields: RideFields,
        snapshotHash: Int,
        match: RideCardTemplateMatch,
        packageName: String?,
        generation: Long,
    ) {
        if (!isCurrentRideWindow(packageName, generation)) return
        analyzing = true
        traceEvent("analysis.start hash=$snapshotHash card=${match.template.name}")
        try {
            val settings = repository.settings.first()
            currentSettings = settings
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
            lastSavedReadHash = snapshotHash
            if (!isCurrentRideWindow(packageName, generation)) {
                moveToIdle("Tela mudou antes de aplicar a decisao; descartei leitura antiga.", record = true)
                return
            }
            lastAnalyzedHash = snapshotHash
            val color = when (result.recommendation) {
                Recommendation.GoodRide -> BubbleColor.Green
                Recommendation.OutsideRadius -> BubbleColor.Red
                Recommendation.InsufficientData -> BubbleColor.Default
            }
            showBubble(color, result.nearestDistanceKm())
            recordDiagnostic(
                stage = "analysis_result",
                color = color,
                reason = result.reason,
                text = text,
                fields = fields,
                result = result,
                cardTemplateMatch = match,
            )
        } catch (error: Exception) {
            showBubble(BubbleColor.Default)
            recordDiagnostic(
                stage = "analysis_error",
                color = BubbleColor.Default,
                reason = "Erro durante analise; bolinha mantida amarela.",
                text = text,
                fields = fields,
                error = error,
                cardTemplateMatch = match,
            )
        } finally {
            analyzing = false
            traceEvent("analysis.finish hash=$snapshotHash")
        }
    }

    private suspend fun saveInsufficient(text: String, fields: RideFields, snapshotHash: Int, reason: String) {
        if (snapshotHash == lastSavedReadHash) return
        lastSavedReadHash = snapshotHash
        repository.addAnalysis(
            AnalysisResult(
                createdAtMillis = System.currentTimeMillis(),
                extractedText = text,
                fields = fields,
                recommendation = Recommendation.InsufficientData,
                reason = "Leitura capturada: $reason",
            ),
        )
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

    private fun moveToIdle(reason: String, record: Boolean) {
        if (currentBubbleColor == BubbleColor.Idle && activeRidePackage == null && !record) return
        screenGeneration += 1
        activeRidePackage = null
        screenshotInProgress.set(false)
        analyzeJob?.cancel()
        clearRememberedText()
        lastSnapshotHash = null
        lastAnalyzedHash = null
        showBubble(BubbleColor.Idle)
        traceEvent("bubble.idle reason=$reason generation=$screenGeneration")
        if (record) {
            recordDiagnostic(stage = "idle", color = BubbleColor.Idle, reason = reason)
        }
    }

    private fun resetToDefault(
        reason: String,
        text: String? = null,
        fields: RideFields? = null,
        record: Boolean,
    ) {
        lastAnalyzedHash = null
        showBubble(BubbleColor.Default)
        if (record) {
            recordDiagnostic(stage = "default", color = BubbleColor.Default, reason = reason, text = text, fields = fields)
        }
    }

    private fun isCurrentRideWindow(packageName: String?, generation: Long): Boolean =
        serviceReady && generation == screenGeneration && shouldScanPackage(packageName) && currentWindowPackageName() == packageName

    private fun collectVisibleText(): String {
        val root = rootInActiveWindow ?: return ""
        val lines = mutableListOf<String>()
        collectNodeText(root, lines)
        return lines.map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString("\n")
    }

    private fun collectNodeText(node: AccessibilityNodeInfo?, lines: MutableList<String>) {
        if (node == null) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { lines += it }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { lines += it }
        for (index in 0 until node.childCount) {
            collectNodeText(runCatching { node.getChild(index) }.getOrNull(), lines)
        }
    }

    private fun rememberSourceText(packageName: String?, source: TextSource, text: String) {
        if (lastTextPackage != packageName) {
            lastTextPackage = packageName
            lastAccessibilityText = ""
            lastOcrText = ""
        }
        when (source) {
            TextSource.Accessibility -> lastAccessibilityText = text.trim()
            TextSource.Ocr -> lastOcrText = text.trim()
        }
    }

    private fun clearRememberedText() {
        lastTextPackage = null
        lastAccessibilityText = ""
        lastOcrText = ""
    }

    private fun mergeTexts(accessibilityText: String, ocrText: String): String =
        listOf(accessibilityText, ocrText)
            .flatMap { it.lines() }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")

    private fun currentWindowPackageName(): String? = currentRootPackageName() ?: activeRidePackage

    private fun currentRootPackageName(): String? =
        normalizePackage(rootInActiveWindow?.packageName?.toString())

    private fun shouldScanPackage(packageName: String?): Boolean {
        val normalized = normalizePackage(packageName) ?: return false
        if (normalized == this.packageName) return false
        if (normalized in PASSIVE_PACKAGES) return false
        if (normalized in IGNORED_PACKAGES) return false
        return normalized in selectedRidePackages(currentSettings)
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

    private fun blockReason(packageName: String?): String {
        val normalized = normalizePackage(packageName)
        return when {
            normalized.isNullOrBlank() -> "Pacote ativo nao informado pelo Android; bolinha em espera."
            normalized == this.packageName -> "Rota Certa em primeiro plano; leitura pausada e bolinha cinza."
            normalized in PASSIVE_PACKAGES -> "Pacote passivo em primeiro plano; bolinha cinza: $normalized."
            normalized in IGNORED_PACKAGES -> "Pacote ignorado; bolinha cinza: $normalized."
            normalized !in selectedRidePackages(currentSettings) -> "Pacote fora dos apps monitorados; bolinha cinza: $normalized."
            else -> "Pacote monitorado: $normalized."
        }
    }

    private fun normalizePackage(packageName: String?): String? =
        packageName?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }

    private fun saveCurrentRideCardFromBubble() {
        scope.launch {
            val packageName = activeRidePackage ?: RideCardTemplateMatcher.inferPackageName(mergeTexts(lastAccessibilityText, lastOcrText))
            val text = mergeTexts(lastAccessibilityText, lastOcrText).ifBlank { collectVisibleText() }
            if (text.isBlank()) {
                toast("Abra o card de corrida e tente salvar novamente.")
                return@launch
            }
            val template = RideCardTemplateMatcher.createTemplate(packageName, text)
            repository.addCardTemplate(template)
            toast("Card de corrida salvo.")
            recordDiagnostic(
                stage = "bubble_save_card",
                color = currentBubbleColor,
                reason = "Card de corrida salvo pela bolinha: ${template.name}.",
                text = text,
            )
        }
    }

    private fun saveCurrentPlaceFromBubble(type: SavedPlaceType) {
        scope.launch {
            val coordinate = locationService.currentCoordinate()
            if (coordinate == null) {
                toast("Autorize a localizacao para salvar este local.")
                return@launch
            }
            val resolved = gpsAddressResolver.resolve(coordinate)
            val createdAt = System.currentTimeMillis()
            val isAlert = type == SavedPlaceType.ProximityAlert
            val place = SavedPlace(
                id = "place-$createdAt-${coordinate.latitude}-${coordinate.longitude}",
                name = if (isAlert) "Alerta de proximidade" else "Local salvo",
                type = type,
                address = resolved.addressLine,
                coordinate = coordinate,
                alertDistanceMeters = if (isAlert) currentSettings.proximityAlertDistanceMeters else null,
                createdAtMillis = createdAt,
            )
            repository.addSavedPlace(place)
            openApp()
            toast(if (isAlert) "Alerta criado. Renomeie em Configuracoes." else "Local salvo.")
        }
    }

    private fun showBubble(color: BubbleColor, distanceKm: Double? = null) {
        if (!serviceReady) return
        val manager = windowManager ?: return
        currentBubbleColor = color
        currentBubbleDistanceKm = distanceKm
        val view = overlayView ?: TextView(this).also { newView ->
            val params = overlayLayoutParams()
            newView.contentDescription = "Rota Certa"
            newView.gravity = Gravity.CENTER
            newView.includeFontPadding = false
            newView.setTextColor(Color.BLACK)
            newView.setTypeface(Typeface.DEFAULT_BOLD)
            newView.setOnClickListener { toggleActionMenu() }
            newView.setOnTouchListener(BubbleTouchListener())
            if (!runCatching { manager.addView(newView, params) }.isSuccess) return
            overlayView = newView
            overlayParams = params
        }
        view.text = formatBubbleDistanceKm(currentBubbleDistanceKm)
        view.textSize = bubbleTextSizeSp(view.text.toString())
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color.argb(currentSettings))
            setStroke(dp(3), Color.argb((currentSettings.bubbleOpacity.coerceIn(0.25, 1.0) * 255).roundToInt(), 255, 255, 255))
        }
    }

    private fun removeBubble() {
        hideActionMenu()
        overlayView?.let { runCatching { windowManager?.removeView(it) } }
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

    private fun toggleActionMenu() {
        if (overlayMenuView != null) hideActionMenu() else showActionMenu()
    }

    private fun showActionMenu() {
        val manager = windowManager ?: return
        val bubbleParams = overlayParams ?: return
        if (overlayMenuView != null) return
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.argb(238, 32, 32, 32))
                setStroke(dp(1), Color.argb(220, 255, 255, 255))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(actionMenuItem("Abrir Rota Certa") { openApp() })
            addView(actionMenuItem("Salvar card de corrida") {
                hideActionMenu()
                saveCurrentRideCardFromBubble()
            })
            addView(actionMenuItem("Salvar este local") {
                hideActionMenu()
                saveCurrentPlaceFromBubble(SavedPlaceType.Place)
            })
            addView(actionMenuItem("Criar alerta de proximidade") {
                hideActionMenu()
                saveCurrentPlaceFromBubble(SavedPlaceType.ProximityAlert)
            })
        }
        val params = WindowManager.LayoutParams(
            dp(260),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleParams.x + dp(76)
            y = bubbleParams.y
        }
        if (runCatching { manager.addView(menu, params) }.isSuccess) {
            overlayMenuView = menu
            overlayMenuParams = params
        }
    }

    private fun actionMenuItem(label: String, action: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 15f
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        minHeight = dp(42)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), 0, dp(10), 0)
        setOnClickListener { action() }
    }

    private fun hideActionMenu() {
        overlayMenuView?.let { runCatching { windowManager?.removeView(it) } }
        overlayMenuView = null
        overlayMenuParams = null
    }

    private fun updateActionMenuPosition() {
        val view = overlayMenuView ?: return
        val params = overlayMenuParams ?: return
        val bubbleParams = overlayParams ?: return
        params.x = bubbleParams.x + dp(76)
        params.y = bubbleParams.y
        runCatching { windowManager?.updateViewLayout(view, params) }
    }

    private fun openApp() {
        hideActionMenu()
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }

    private fun toast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun recordDiagnostic(
        stage: String,
        color: BubbleColor? = null,
        reason: String,
        text: String? = null,
        fields: RideFields? = null,
        result: AnalysisResult? = null,
        error: Throwable? = null,
        cardTemplateMatch: RideCardTemplateMatch? = null,
    ) {
        val diagnosticColor = color ?: currentBubbleColor
        val packageName = activeRidePackage ?: lastTextPackage ?: currentWindowPackageName()
        val hash = text?.snapshotHash()
        val signature = listOf(stage, diagnosticColor.label, reason, packageName.orEmpty(), hash?.toString().orEmpty()).joinToString("|")
        if (signature == lastDiagnosticSignature) return
        lastDiagnosticSignature = signature
        val diagnostic = LiveDiagnostic(
            createdAtMillis = System.currentTimeMillis(),
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE,
            packageName = packageName,
            stage = stage,
            bubbleColor = diagnosticColor.label,
            reason = reason.withDiagnosticEvents(),
            restrictToSelectedRideApps = currentSettings.restrictToSelectedRideApps,
            selectedPackages = selectedRidePackages(currentSettings).toList().sorted(),
            registeredCardRequired = true,
            registeredCardMatched = cardTemplateMatch?.template?.name,
            textLength = text?.length ?: 0,
            textHash = hash,
            textPreview = text?.trim().orEmpty().take(DIAGNOSTIC_TEXT_LIMIT),
            pickup = fields?.pickup ?: result?.fields?.pickup,
            destination = fields?.destination ?: result?.fields?.destination,
            recommendation = result?.recommendation,
            homeDistanceKm = result?.pickupToHomeKm,
            alternativeDistanceKm = result?.pickupToAlternativeKm,
            error = error?.let { "${it::class.java.simpleName}: ${it.message.orEmpty()}" },
        )
        scope.launch { runCatching { repository.saveDiagnostic(diagnostic) } }
    }

    private fun traceEvent(message: String) {
        diagnosticEvents += "${System.currentTimeMillis()} $message"
        while (diagnosticEvents.size > DIAGNOSTIC_EVENT_LIMIT) diagnosticEvents.removeAt(0)
    }

    private fun String.withDiagnosticEvents(): String {
        if (diagnosticEvents.isEmpty()) return this
        return buildString {
            appendLine(this@withDiagnosticEvents)
            appendLine("--- LOGS ---")
            diagnosticEvents.forEach { appendLine(it) }
        }.trimEnd()
    }

    private fun String?.diagnosticValue(maxLength: Int = 80): String =
        this?.replace(Regex("""\s+"""), " ")?.trim()?.take(maxLength) ?: "null"

    private fun AnalysisResult.nearestDistanceKm(): Double? =
        listOfNotNull(pickupToHomeKm, pickupToAlternativeKm).minOrNull()

    private fun formatBubbleDistanceKm(distanceKm: Double?): String = when {
        distanceKm == null -> ""
        distanceKm < 1.0 -> String.format(Locale("pt", "BR"), "%.1f", distanceKm).removeSuffix(",0")
        else -> distanceKm.roundToInt().coerceAtMost(99).toString()
    }

    private fun bubbleTextSizeSp(text: String): Float = when {
        text.isBlank() -> 14f
        text.length <= 1 -> 25f
        text.length <= 2 -> 23f
        text.length <= 3 -> 20f
        else -> 18f
    }

    private fun String.snapshotHash(): Int =
        lines().map { it.trim() }.filter { it.isNotBlank() }.joinToString("\n").hashCode()

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
                    updateActionMenuPosition()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    bubblePrefs.edit().putInt(KEY_BUBBLE_X, params.x).putInt(KEY_BUBBLE_Y, params.y).apply()
                    if (!moved) view.performClick()
                    return true
                }
            }
            return false
        }
    }

    private enum class TextSource { Accessibility, Ocr }

    private enum class BubbleColor(
        private val normalArgb: Int,
        private val darkArgb: Int,
        val label: String,
    ) {
        Idle(Color.rgb(117, 117, 117), Color.rgb(66, 66, 66), "cinza"),
        Default(Color.rgb(241, 196, 15), Color.rgb(133, 100, 4), "amarelo"),
        Green(Color.rgb(46, 204, 113), Color.rgb(24, 106, 59), "verde"),
        Red(Color.rgb(231, 76, 60), Color.rgb(127, 29, 29), "vermelho");

        fun argb(settings: AppSettings): Int {
            val base = if (settings.bubbleDarkMode) darkArgb else normalArgb
            val alpha = (settings.bubbleOpacity.coerceIn(0.25, 1.0) * 255).roundToInt()
            return Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
        }
    }

    private companion object {
        const val SCAN_LOOP_MS = 550L
        const val ACCESSIBILITY_DELAY_MS = 30L
        const val SCREENSHOT_INTERVAL_MS = 500L
        const val PROXIMITY_LOOP_MS = 15_000L
        const val DIAGNOSTIC_TEXT_LIMIT = 1200
        const val DIAGNOSTIC_EVENT_LIMIT = 80
        const val BUBBLE_PREFS = "rota_certa_bubble"
        const val KEY_BUBBLE_X = "bubble_x"
        const val KEY_BUBBLE_Y = "bubble_y"
        const val PACKAGE_99_DRIVER = "com.app99.driver"
        const val PACKAGE_UBER_DRIVER = "com.ubercab.driver"
        const val PACKAGE_INDRIVE_DRIVER = "sinet.startup.indriver"
        val IGNORED_PACKAGES = setOf(
            "android",
            "com.android.settings",
            "com.android.systemui",
            "com.google.android.inputmethod.latin",
            "com.openai.chatgpt",
            "com.samsung.android.app.settings",
            "com.samsung.android.honeyboard",
        )
        val PASSIVE_PACKAGES = setOf(
            "com.android.launcher",
            "com.android.systemui",
            "com.google.android.apps.maps",
            "com.waze",
            "com.google.android.apps.nexuslauncher",
            "com.google.android.inputmethod.latin",
            "com.openai.chatgpt",
            "com.sec.android.app.launcher",
            "com.android.settings",
            "com.samsung.android.app.settings",
            "com.samsung.android.honeyboard",
        )
    }
}
