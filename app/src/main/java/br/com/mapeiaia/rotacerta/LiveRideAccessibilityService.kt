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

class LiveRideAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val screenshotInProgress = AtomicBoolean(false)
    private val diagnosticEvents = mutableListOf<String>()
    private var analyzeJob: Job? = null
    private var overlayView: TextView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var overlayMenuView: LinearLayout? = null
    private var overlayMenuParams: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null
    private var lastSnapshotHash: Int? = null
    private var lastAnalyzedHash: Int? = null
    private var lastSavedReadHash: Int? = null
    private var lastDiagnosticSignature: String? = null
    private var pendingAnalysis: PendingLiveAnalysis? = null
    private var lastScreenshotMillis: Long = 0L
    private var continuousScanStarted = false
    private var proximityAlertMonitorStarted = false
    private var serviceReady = false
    private var analyzing = false
    private var activePackageName: String? = null
    private var lastTextPackageName: String? = null
    private var lastAccessibilityText: String = ""
    private var lastOcrText: String = ""
    private var currentSettings = AppSettings()
    private var currentCardTemplates = emptyList<RideCardTemplate>()
    private var currentSavedPlaces = emptyList<SavedPlace>()
    private var currentImportedRadars = emptyList<ImportedRadar>()
    private var currentRadarColor = RadarColor.Idle
    private var currentDistanceKm: Double? = null
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
    private lateinit var speechEngine: LiveSpeechEngine
    private lateinit var proximityAlertEngine: ProximityAlertEngine
    private val registeredCardGate = RegisteredCardDecisionGate()

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
        traceEvent("service.onCreate initialized")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceReady = true
        traceEvent("service.onServiceConnected ready=true")
        scope.launch { repository.settings.collect { currentSettings = it } }
        scope.launch { repository.cardTemplates.collect { currentCardTemplates = it } }
        scope.launch { repository.savedPlaces.collect { currentSavedPlaces = it } }
        scope.launch { repository.importedRadars.collect { currentImportedRadars = it } }
        scope.launch {
            currentSettings = repository.settings.first()
            currentCardTemplates = repository.cardTemplates.first()
            showOverlay(RadarColor.Idle)
            recordDiagnostic(
                stage = "service_connected",
                reason = "Servico de acessibilidade conectado; bolinha em espera.",
            )
            startContinuousScan()
            startProximityAlertMonitor()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!serviceReady || event == null) return
        val eventPackageName = normalizePackageName(event.packageName?.toString())
        val packageName = eventPackageName ?: currentRootPackageName()
        if (eventPackageName != null) {
            activePackageName = if (isPassiveDiagnosticPackage(eventPackageName)) null else eventPackageName
        }
        traceEvent("event package=${packageName.orEmpty()} type=${event.eventType}")
        if (packageName == null) {
            traceEvent("event ignored package= reason=Pacote ativo nao informado pelo Android.")
            scheduleVisibleTextAnalysis(delayMs = 80L, allowPopupCandidate = true)
            requestScreenshotAnalysis(allowPopupCandidate = true)
            resetToDefaultForNonRideScreen("Pacote ativo nao informado pelo Android.")
            return
        }
        if (!shouldScanPackage(packageName)) {
            val reason = scanBlockReason(packageName)
            traceEvent("event blocked package=$packageName reason=$reason")
            scheduleVisibleTextAnalysis(delayMs = 80L, allowPopupCandidate = true)
            requestScreenshotAnalysis(allowPopupCandidate = true)
            if (isPassiveDiagnosticPackage(packageName)) {
                resetToDefaultForNonRideScreen(reason)
                return
            }
            resetToIdle(reason = reason, record = true)
            return
        }
        if (currentRadarColor == RadarColor.Idle) showOverlay(RadarColor.Default)
        scheduleVisibleTextAnalysis(delayMs = 80L)
        requestScreenshotAnalysis()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        traceEvent("service.onDestroy")
        serviceReady = false
        screenshotInProgress.set(false)
        analyzeJob?.cancel()
        removeOverlay()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        textToSpeechReady = false
        scope.cancel()
        super.onDestroy()
    }

    private fun startContinuousScan() {
        if (continuousScanStarted || !serviceReady) return
        continuousScanStarted = true
        traceEvent("scan.loop started interval=${SCAN_LOOP_MS}ms")
        scope.launch {
            while (serviceReady) {
                val packageName = currentWindowPackageName()
                if (shouldScanPackage(packageName)) {
                    if (currentRadarColor == RadarColor.Idle) showOverlay(RadarColor.Default)
                    scheduleVisibleTextAnalysis(delayMs = 0L)
                    requestScreenshotAnalysis()
                } else if (isPassiveDiagnosticPackage(packageName)) {
                    val visibleText = collectVisibleText(allowPopupCandidate = true)
                    if (visibleText.isNotBlank() && looksLikeRegisteredPopupCandidate(visibleText)) {
                        processRideText(visibleText, TextSource.Accessibility, allowPopupCandidate = true)
                        requestScreenshotAnalysis(allowPopupCandidate = true)
                    } else {
                        resetToDefaultForNonRideScreen("Tela passiva detectada fora do card cadastrado; bolinha voltou para amarelo.")
                    }
                } else if (!isPassiveDiagnosticPackage(packageName)) {
                    val visibleText = collectVisibleText(allowPopupCandidate = true)
                    if (visibleText.isNotBlank() && looksLikeRegisteredPopupCandidate(visibleText)) {
                        processRideText(visibleText, TextSource.Accessibility, allowPopupCandidate = true)
                        requestScreenshotAnalysis(allowPopupCandidate = true)
                    } else {
                        resetToIdle(reason = "Janela atual nao permitida para leitura.", record = false)
                    }
                }
                delay(SCAN_LOOP_MS)
            }
        }
    }

    private fun startProximityAlertMonitor() {
        if (proximityAlertMonitorStarted || !serviceReady) return
        proximityAlertMonitorStarted = true
        traceEvent("proximity.loop started interval=${PROXIMITY_ALERT_LOOP_MS}ms")
        scope.launch {
            while (serviceReady) {
                val alerts = currentSavedPlaces.filter { it.type == SavedPlaceType.ProximityAlert }
                val radars = currentImportedRadars
                if (alerts.isNotEmpty() || radars.isNotEmpty()) checkProximityAlerts(alerts, radars)
                delay(PROXIMITY_ALERT_LOOP_MS)
            }
        }
    }

    private suspend fun checkProximityAlerts(alerts: List<SavedPlace>, radars: List<ImportedRadar>) {
        val coordinate = locationService.currentCoordinate() ?: return
        proximityAlertEngine.check(
            alerts = alerts,
            radars = radars,
            coordinate = coordinate,
            settings = currentSettings,
        ) { diagnostic ->
            recordDiagnostic(stage = diagnostic.stage, reason = diagnostic.reason)
        }
    }

    private fun scheduleVisibleTextAnalysis(delayMs: Long, allowPopupCandidate: Boolean = false) {
        if (!serviceReady || (!allowPopupCandidate && !shouldScanCurrentWindow())) return
        if (analyzing) {
            traceEvent("accessibility.schedule skipped analyzing=true")
            return
        }
        if (analyzeJob?.isActive == true) {
            traceEvent("accessibility.schedule skipped active_job=true")
            return
        }
        traceEvent("accessibility.schedule delay=${delayMs}ms")
        analyzeJob = scope.launch {
            if (delayMs > 0L) delay(delayMs)
            val visibleText = collectVisibleText(allowPopupCandidate)
            traceEvent("accessibility.collect length=${visibleText.length}")
            processRideText(visibleText, TextSource.Accessibility, allowPopupCandidate)
        }
    }

    private fun requestScreenshotAnalysis(allowPopupCandidate: Boolean = false) {
        if (!serviceReady || (!allowPopupCandidate && !shouldScanCurrentWindow()) || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val now = System.currentTimeMillis()
        if (now - lastScreenshotMillis < SCREENSHOT_INTERVAL_MS) return
        if (!screenshotInProgress.compareAndSet(false, true)) {
            traceEvent("screenshot.request skipped in_progress=true")
            return
        }
        lastScreenshotMillis = now
        traceEvent("screenshot.request started")
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        scope.launch {
                            runCatching {
                                if (allowPopupCandidate || shouldScanCurrentWindow()) {
                                    val bitmap = screenshot.toSoftwareBitmap() ?: return@runCatching
                                    val ocrText = ocrService.extractText(bitmap)
                                    traceEvent("screenshot.ocr success length=${ocrText.length}")
                                    processRideText(ocrText, TextSource.Ocr, allowPopupCandidate)
                                }
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
                        recordDiagnostic(
                            stage = "screenshot_failed",
                            reason = "Android recusou o print da acessibilidade. Codigo: $errorCode.",
                        )
                        screenshotInProgress.set(false)
                    }
                },
            )
        }.onFailure { error ->
            traceEvent("screenshot.request error=${error::class.java.simpleName}: ${error.message.orEmpty()}")
            recordDiagnostic(
                stage = "screenshot_request_error",
                reason = "Nao consegui solicitar print da tela pela acessibilidade.",
                error = error,
            )
            screenshotInProgress.set(false)
        }
    }

    private fun collectVisibleText(allowPopupCandidate: Boolean = false): String {
        if (!allowPopupCandidate && !shouldScanCurrentWindow()) return ""
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

    private suspend fun processRideText(text: String, source: TextSource, allowPopupCandidate: Boolean = false) {
        if (!serviceReady) return
        val windowPackageName = currentWindowPackageName()
        if (!allowPopupCandidate && !shouldScanPackage(windowPackageName)) return
        val packageName = resolveRidePackageForText(windowPackageName, text, allowPopupCandidate)
        if (!shouldScanPackage(packageName)) {
            if (allowPopupCandidate && text.isNotBlank()) {
                traceEvent("popup.candidate ignored reason=package_not_identified raw_length=${text.length}")
            }
            return
        }
        traceEvent("process.start source=$source package=${packageName.orEmpty()} raw_length=${text.length}")
        if (!allowPopupCandidate) {
            rememberSourceText(packageName, source, text)
        } else {
            rememberPopupCandidatePackage(packageName)
        }
        val snapshotText = if (allowPopupCandidate) {
            text.trim()
        } else {
            mergeRideTexts(lastAccessibilityText, lastOcrText).ifBlank { text.trim() }
        }
        if (snapshotText.isBlank()) {
            traceEvent("process.empty_text source=$source")
            if (allowPopupCandidate) return
            registeredCardGate.clear()
            resetToDefault(reason = "Texto visivel vazio; nenhum card lido neste momento.", record = !isPassiveDiagnosticPackage(activePackageName))
            return
        }

        val snapshotHash = snapshotText.snapshotHash()
        traceEvent("process.snapshot length=${snapshotText.length} hash=$snapshotHash")
        RideScreenTextClassifier.ignoreReason(snapshotText)?.let { reason ->
            traceEvent("classifier.ignore=true reason=$reason hash=$snapshotHash")
            if (allowPopupCandidate) return
            lastSnapshotHash = snapshotHash
            lastAnalyzedHash = null
            registeredCardGate.clear()
            resetToDefault(reason = reason, text = snapshotText, record = !isPassiveDiagnosticPackage(activePackageName))
            return
        }

        if (snapshotHash != lastSnapshotHash) {
            lastSnapshotHash = snapshotHash
            lastAnalyzedHash = null
            showOverlay(RadarColor.Default)
            recordDiagnostic(
                stage = "screen_changed",
                reason = "A imagem/texto da tela mudou; aguardando confirmar o card cadastrado.",
                text = snapshotText,
            )
        }

        val parseResult = parser.parseWithMetadata(snapshotText, packageName)
        val fields = parseResult.fields
        traceEvent(
            "parser.name=${parseResult.parserName} pickup=${fields.pickup.diagnosticValue()} destination=${fields.destination.diagnosticValue()} fare=${fields.fare.orEmpty()}",
        )
        if (!RideOfferDetector.looksLikeRideOffer(snapshotText, fields, packageName)) {
            val reason = RideOfferDetector.rejectReason(fields)
            traceEvent("classifier.ride_offer=false reason=$reason")
            if (allowPopupCandidate) return
            registeredCardGate.clear()
            saveCapturedReadToHistory(snapshotText, fields, snapshotHash, reason)
            resetToDefault(reason = reason, text = snapshotText, fields = fields)
            return
        }

        val cardMatch = RideCardTemplateMatcher.match(snapshotText, packageName, currentCardTemplates)
        if (cardMatch == null) {
            val reason = "Tela parece card de corrida, mas ainda nao bate com nenhum card cadastrado. Salvei a amostra; cadastre o modelo para liberar o farol."
            traceEvent("card_model.missing package=${packageName.orEmpty()} templates=${currentCardTemplates.size}")
            if (allowPopupCandidate) return
            registeredCardGate.clear()
            saveCapturedCardScreen(snapshotText, fields, snapshotHash, parseResult.parserName, packageName)
            saveCapturedReadToHistory(snapshotText, fields, snapshotHash, reason)
            resetToDefault(reason = reason, text = snapshotText, fields = fields)
            return
        }
        registeredCardGate.markSeen()
        traceEvent("card_model.match name=${cardMatch.template.name} score=${cardMatch.score}")

        if (snapshotHash == lastAnalyzedHash) {
            traceEvent("analysis.skip duplicate_hash=$snapshotHash")
            return
        }
        if (analyzing) {
            pendingAnalysis = PendingLiveAnalysis(snapshotText, fields, snapshotHash, cardMatch, allowPopupCandidate)
            traceEvent("analysis.defer analyzing=true hash=$snapshotHash")
            return
        }
        analyzeLiveText(snapshotText, fields, snapshotHash, cardMatch, allowPopupCandidate)
    }

    private fun resolveRidePackageForText(
        windowPackageName: String?,
        text: String,
        allowPopupCandidate: Boolean,
    ): String? {
        val normalizedWindowPackage = normalizePackageName(windowPackageName)
        if (shouldScanPackage(normalizedWindowPackage)) return normalizedWindowPackage
        if (!allowPopupCandidate) return normalizedWindowPackage
        return RideCardTemplateMatcher.inferPackageName(text)
            ?.takeIf { inferred -> shouldScanPackage(inferred) }
    }

    private fun looksLikeRegisteredPopupCandidate(text: String): Boolean {
        val packageName = resolveRidePackageForText(currentWindowPackageName(), text, allowPopupCandidate = true)
            ?: return false
        val parseResult = parser.parseWithMetadata(text, packageName)
        if (!RideOfferDetector.looksLikeRideOffer(text, parseResult.fields, packageName)) return false
        return RideCardTemplateMatcher.match(text, packageName, currentCardTemplates) != null
    }

    private fun rememberSourceText(packageName: String?, source: TextSource, text: String) {
        val normalizedPackage = normalizePackageName(packageName)
        if (normalizedPackage != lastTextPackageName) {
            traceEvent("source.reset package=${normalizedPackage.orEmpty()}")
            lastTextPackageName = normalizedPackage
            lastAccessibilityText = ""
            lastOcrText = ""
        }
        when (source) {
            TextSource.Accessibility -> lastAccessibilityText = text.trim()
            TextSource.Ocr -> lastOcrText = text.trim()
        }
    }

    private fun rememberPopupCandidatePackage(packageName: String?) {
        val normalizedPackage = normalizePackageName(packageName)
        if (normalizedPackage != lastTextPackageName) {
            traceEvent("source.reset package=${normalizedPackage.orEmpty()}")
        }
        lastTextPackageName = normalizedPackage
        lastAccessibilityText = ""
        lastOcrText = ""
    }

    private fun mergeRideTexts(accessibilityText: String, ocrText: String): String {
        val lines = linkedSetOf<String>()
        listOf(accessibilityText, ocrText)
            .flatMap { it.lines() }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { lines += it }
        return lines.joinToString("\n")
    }

    private suspend fun saveCapturedReadToHistory(text: String, fields: RideFields, snapshotHash: Int, reason: String) {
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

    private suspend fun saveCapturedCardScreen(
        text: String,
        fields: RideFields,
        snapshotHash: Int,
        parserName: String,
        packageName: String?,
    ) {
        repository.addCapturedScreen(
            CapturedRideScreen(
                createdAtMillis = System.currentTimeMillis(),
                packageName = packageName?.lowercase(Locale.ROOT),
                textHash = snapshotHash,
                textPreview = text.trim().take(DIAGNOSTIC_TEXT_LIMIT),
                parserName = parserName,
                pickup = fields.pickup,
                destination = fields.destination,
                fare = fields.fare,
            ),
        )
    }

    private suspend fun analyzeLiveText(
        text: String,
        fields: RideFields,
        snapshotHash: Int,
        cardMatch: RideCardTemplateMatch?,
        allowPopupCandidate: Boolean = false,
    ) {
        if (!serviceReady || (!allowPopupCandidate && !shouldScanCurrentWindow()) || analyzing) return
        analyzing = true
        traceEvent("analysis.start hash=$snapshotHash destination=${fields.destination.diagnosticValue()}")
        currentSettings = repository.settings.first()
        try {
            val settings = currentSettings
            val region = DeviceRegion(country = "Brasil")
            val destinationCoordinate = fields.destination?.let { geocodeBest(it, region, settings) }
            traceEvent("geocode.destination ok=${destinationCoordinate != null}")
            val homeCoordinate = settings.homeCoordinate ?: geocodeBest(settings.homeAddress, region, settings)
            val alternativeCoordinate = settings.alternativeCoordinate ?: geocodeBest(settings.alternativeAddress, region, settings)
            traceEvent("geocode.config home=${homeCoordinate != null} alternative=${alternativeCoordinate != null}")
            val homeDistanceKm = routeDistanceKm(destinationCoordinate, homeCoordinate, settings)
            val alternativeDistanceKm = routeDistanceKm(destinationCoordinate, alternativeCoordinate, settings)
            traceEvent("route.distance home=${homeDistanceKm?.let(::formatDiagnosticKm) ?: "null"} alternative=${alternativeDistanceKm?.let(::formatDiagnosticKm) ?: "null"}")

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
            traceEvent("decision.result recommendation=${result.recommendation} reason=${result.reason}")
            repository.addAnalysis(result)
            lastSavedReadHash = snapshotHash
            if (!allowPopupCandidate && !shouldScanCurrentWindow()) {
                registeredCardGate.clear()
                resetToDefaultForNonRideScreen(
                    reason = "A tela saiu do card/app monitorado antes de aplicar a decisao.",
                    record = false,
                )
                recordDiagnostic(
                    stage = "window_changed_after_analysis",
                    reason = "A tela saiu do card/app monitorado antes de aplicar a decisao.",
                    text = text,
                    fields = fields,
                    result = result,
                    cardTemplateMatch = cardMatch,
                )
                return
            }
            if (allowPopupCandidate && !looksLikeRegisteredPopupCandidate(collectVisibleText(allowPopupCandidate = true))) {
                registeredCardGate.clear()
                resetToDefaultForNonRideScreen(
                    reason = "O pop-up de corrida nao esta mais visivel; bolinha voltou para amarelo.",
                    record = false,
                )
                return
            }

            lastAnalyzedHash = lastSnapshotHash ?: snapshotHash
            val radarColor = when (result.recommendation) {
                Recommendation.GoodRide -> RadarColor.Green
                Recommendation.OutsideRadius -> RadarColor.Red
                Recommendation.InsufficientData -> RadarColor.Default
            }
            traceEvent("overlay.apply color=${radarColor.diagnosticLabel} distance=${result.nearestConfiguredDistanceKm()?.let(::formatDiagnosticKm) ?: "null"}")
            showOverlay(color = radarColor, distanceKm = result.nearestConfiguredDistanceKm())
            recordDiagnostic(
                stage = "analysis_result",
                color = radarColor,
                reason = result.reason,
                text = text,
                fields = fields,
                result = result,
                cardTemplateMatch = cardMatch,
            )
        } catch (error: Exception) {
            traceEvent("analysis.error ${error::class.java.simpleName}: ${error.message.orEmpty()}")
            showOverlay(RadarColor.Default)
            recordDiagnostic(
                stage = "analysis_error",
                reason = "Erro durante analise do destino final; mantive a bolinha amarela.",
                text = text,
                fields = fields,
                error = error,
                cardTemplateMatch = cardMatch,
            )
        } finally {
            analyzing = false
            traceEvent("analysis.finish hash=$snapshotHash")
            val pending = pendingAnalysis
            pendingAnalysis = null
            if (pending != null && pending.snapshotHash != lastAnalyzedHash && shouldScanCurrentWindow()) {
                traceEvent("analysis.pending replay hash=${pending.snapshotHash}")
                scope.launch {
                    analyzeLiveText(
                        text = pending.text,
                        fields = pending.fields,
                        snapshotHash = pending.snapshotHash,
                        cardMatch = pending.cardMatch,
                        allowPopupCandidate = pending.allowPopupCandidate,
                    )
                }
            }
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

    private fun resetToDefault(
        reason: String,
        text: String? = null,
        fields: RideFields? = null,
        record: Boolean = true,
    ) {
        lastSnapshotHash = null
        lastAnalyzedHash = null
        registeredCardGate.clear()
        clearRememberedRideText()
        showOverlay(RadarColor.Default)
        if (record) {
            recordDiagnostic(stage = "default", reason = reason, text = text, fields = fields)
        }
    }

    private fun resetToDefaultForNonRideScreen(reason: String, record: Boolean = false) {
        resetToDefault(reason = reason, record = record)
    }

    private fun resetStaleRegisteredCardDecision() {
        val hasDecisionColor = currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red
        if (registeredCardGate.shouldResetStale(hasDecisionColor)) {
            registeredCardGate.clear()
            resetToDefault(
                reason = "Card cadastrado nao esta mais visivel; bolinha voltou para amarelo.",
                record = false,
            )
        }
    }

    private fun resetToIdle(
        reason: String,
        record: Boolean = false,
    ) {
        lastSnapshotHash = null
        lastAnalyzedHash = null
        registeredCardGate.clear()
        clearRememberedRideText()
        showOverlay(RadarColor.Idle)
        if (record) {
            recordDiagnostic(stage = "idle", color = RadarColor.Idle, reason = reason)
        }
    }

    private fun saveCurrentRideCardFromBubble() {
        scope.launch {
            val packageName = currentWindowPackageName() ?: activePackageName
            val text = mergeRideTexts(lastAccessibilityText, lastOcrText).ifBlank {
                collectVisibleTextForAction()
            }
            if (text.isBlank()) {
                toast("Abra o card de corrida e tente salvar novamente.")
                recordDiagnostic(
                    stage = "bubble_save_card_empty",
                    color = currentRadarColor,
                    reason = "Nao havia texto lido suficiente para salvar card de corrida.",
                )
                return@launch
            }

            val inferredPackage = packageName?.lowercase(Locale.ROOT)
                ?: RideCardTemplateMatcher.inferPackageName(text)
            val template = RideCardTemplateMatcher.createTemplate(inferredPackage, text)
            repository.addCardTemplate(template)
            val parseResult = parser.parseWithMetadata(text, inferredPackage)
            repository.addCapturedScreen(
                CapturedRideScreen(
                    createdAtMillis = System.currentTimeMillis(),
                    packageName = inferredPackage,
                    textHash = text.snapshotHash(),
                    textPreview = text.trim().take(DIAGNOSTIC_TEXT_LIMIT),
                    parserName = parseResult.parserName,
                    pickup = parseResult.fields.pickup,
                    destination = parseResult.fields.destination,
                    fare = parseResult.fields.fare,
                ),
            )
            toast("Card de corrida salvo.")
            recordDiagnostic(
                stage = "bubble_save_card",
                color = currentRadarColor,
                reason = "Card de corrida salvo pela bolinha: ${template.name}.",
                text = text,
                fields = parseResult.fields,
            )
        }
    }

    private fun clearRememberedRideText() {
        pendingAnalysis = null
        lastTextPackageName = null
        lastAccessibilityText = ""
        lastOcrText = ""
    }

    private fun saveCurrentPlaceFromBubble(type: SavedPlaceType) {
        scope.launch {
            val coordinate = locationService.currentCoordinate()
            if (coordinate == null) {
                toast("Autorize a localizacao para salvar este local.")
                recordDiagnostic(
                    stage = "bubble_save_place_no_gps",
                    color = currentRadarColor,
                    reason = "Nao foi possivel captar GPS para salvar o local.",
                )
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
            openSavedPlaceEditor(place)
            toast(if (isAlert) "Alerta criado. Informe o nome." else "Local salvo. Informe o nome.")
            recordDiagnostic(
                stage = if (isAlert) "bubble_save_proximity_alert" else "bubble_save_place",
                color = currentRadarColor,
                reason = if (isAlert) {
                    "Alerta de proximidade salvo pela bolinha a ${place.alertDistanceMeters ?: 200} metros."
                } else {
                    "Local salvo pela bolinha."
                },
            )
        }
    }

    private fun collectVisibleTextForAction(): String {
        val root = rootInActiveWindow ?: return ""
        val lines = mutableListOf<String>()
        collectNodeText(root, lines)
        return lines.map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString("\n")
    }

    private fun shouldScanCurrentWindow(): Boolean = shouldScanPackage(currentWindowPackageName())

    private fun currentWindowPackageName(): String? =
        currentRootPackageName() ?: activePackageName

    private fun currentRootPackageName(): String? =
        normalizePackageName(rootInActiveWindow?.packageName?.toString())

    private fun shouldScanPackage(packageName: String?): Boolean {
        val normalized = normalizePackageName(packageName) ?: return false
        if (normalized == this.packageName) return false
        if (normalized in PASSIVE_DIAGNOSTIC_PACKAGES) return false
        if (normalized in IGNORED_PACKAGES) return false
        val settings = currentSettings
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

    private fun scanBlockReason(packageName: String?): String {
        val normalized = normalizePackageName(packageName)
        if (normalized.isNullOrBlank()) return "Pacote ativo nao informado pelo Android."
        if (normalized == this.packageName) return "Rota Certa esta em primeiro plano; leitura pausada."
        if (normalized in PASSIVE_DIAGNOSTIC_PACKAGES) return "Pacote passivo ignorado sem apagar a ultima decisao: $normalized."
        if (normalized in IGNORED_PACKAGES) return "Pacote ignorado para evitar leitura fora do card: $normalized."
        if (normalized !in selectedRidePackages(currentSettings)) {
            return "Pacote fora dos apps monitorados; bolinha em espera: $normalized."
        }
        return "Pacote permitido: $normalized."
    }

    private fun recordDiagnostic(
        stage: String,
        color: RadarColor? = null,
        reason: String,
        text: String? = null,
        fields: RideFields? = null,
        result: AnalysisResult? = null,
        error: Throwable? = null,
        cardTemplateMatch: RideCardTemplateMatch? = null,
    ) {
        val diagnosticPackageName = activePackageName
            ?.takeUnless { isPassiveDiagnosticPackage(it) }
            ?: lastTextPackageName?.takeIf { shouldScanPackage(it) }
            ?: currentWindowPackageName()
        if (stage != "service_connected" && isPassiveDiagnosticPackage(diagnosticPackageName)) return
        val settings = currentSettings
        val diagnosticColor = color ?: currentRadarColor
        val hash = text?.snapshotHash()
        val signature = listOf(stage, diagnosticColor.diagnosticLabel, reason, diagnosticPackageName.orEmpty(), hash?.toString().orEmpty()).joinToString("|")
        if (signature == lastDiagnosticSignature) return
        lastDiagnosticSignature = signature

        val diagnostic = LiveDiagnostic(
            createdAtMillis = System.currentTimeMillis(),
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE,
            packageName = diagnosticPackageName,
            stage = stage,
            bubbleColor = diagnosticColor.diagnosticLabel,
            reason = reason.withDiagnosticEvents(),
            restrictToSelectedRideApps = settings.restrictToSelectedRideApps,
            selectedPackages = selectedRidePackages(settings).toList().sorted(),
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

    private fun formatDiagnosticKm(value: Double): String =
        String.format(Locale("pt", "BR"), "%.1fkm", value)

    private fun isPassiveDiagnosticPackage(packageName: String?): Boolean {
        val normalized = normalizePackageName(packageName) ?: return true
        return normalized == this.packageName || normalized in PASSIVE_DIAGNOSTIC_PACKAGES
    }

    private fun normalizePackageName(packageName: String?): String? =
        packageName?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }

    private fun showOverlay(color: RadarColor, distanceKm: Double? = null) {
        if (!serviceReady) return
        val manager = windowManager ?: return
        currentRadarColor = color
        currentDistanceKm = distanceKm
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
        view.text = formatBubbleDistanceKm(currentDistanceKm)
        view.textSize = bubbleTextSizeSp(view.text.toString())
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color.argb(currentSettings))
            setStroke(dp(3), Color.argb((currentSettings.bubbleOpacity.coerceIn(0.25, 1.0) * 255).roundToInt(), 255, 255, 255))
        }
    }

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

    private fun removeOverlay() {
        hideActionMenu()
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
        hideActionMenu()
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }

    private fun openSavedPlaceEditor(place: SavedPlace) {
        hideActionMenu()
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_OPEN_TAB, TAB_CONFIG)
                    .putExtra(EXTRA_SAVED_PLACE_ID, place.id),
            )
        }
    }

    private fun toggleActionMenu() {
        if (overlayMenuView != null) {
            hideActionMenu()
        } else {
            showActionMenu()
        }
    }

    private fun showActionMenu() {
        val manager = windowManager ?: return
        if (overlayMenuView != null) return
        val bubbleParams = overlayParams ?: return
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.argb(238, 32, 32, 32))
                setStroke(dp(1), Color.argb(220, 255, 255, 255))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(actionMenuItem("🏠  Abrir Rota Certa") { openApp() })
            addView(actionMenuItem("💾  Salvar card de corrida") {
                hideActionMenu()
                saveCurrentRideCardFromBubble()
            })
            addView(actionMenuItem("📍  Salvar este local") {
                hideActionMenu()
                saveCurrentPlaceFromBubble(SavedPlaceType.Place)
            })
            addView(actionMenuItem("🔔  Criar alerta de proximidade") {
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

    private fun actionMenuItem(label: String, action: () -> Unit): TextView =
        TextView(this).apply {
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
        val view = overlayMenuView ?: return
        runCatching { windowManager?.removeView(view) }
        overlayMenuView = null
        overlayMenuParams = null
    }

    private fun updateActionMenuPosition() {
        val manager = windowManager ?: return
        val view = overlayMenuView ?: return
        val params = overlayMenuParams ?: return
        val bubbleParams = overlayParams ?: return
        params.x = bubbleParams.x + dp(76)
        params.y = bubbleParams.y
        runCatching { manager.updateViewLayout(view, params) }
    }

    private fun toast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
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
        lines().map { it.trim() }.filter { it.isNotBlank() }.joinToString("\n").hashCode()

    private enum class TextSource { Accessibility, Ocr }

    private data class PendingLiveAnalysis(
        val text: String,
        val fields: RideFields,
        val snapshotHash: Int,
        val cardMatch: RideCardTemplateMatch?,
        val allowPopupCandidate: Boolean,
    )

    private enum class RadarColor(
        private val normalArgb: Int,
        private val darkArgb: Int,
        val diagnosticLabel: String,
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
        const val SCAN_LOOP_MS = 850L
        const val SCREENSHOT_INTERVAL_MS = 650L
        const val PROXIMITY_ALERT_LOOP_MS = 15_000L
        const val DIAGNOSTIC_TEXT_LIMIT = 1200
        const val DIAGNOSTIC_EVENT_LIMIT = 60
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
        val PASSIVE_DIAGNOSTIC_PACKAGES = setOf(
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
