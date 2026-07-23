package br.com.mapeiaia.rotacerta
// global_single_passenger_gate_0_1_124
// global_passenger_and_addresses_card_0_1_124
// global_inactive_clear_now_0_1_124
// global_full_screen_hash_0_1_124
// global_screen_change_clear_0_1_124
// instant_farol_cached_settings_0_1_124
// persistent_route_cache_save_0_1_124
// global_overlay_idle_allowed_0_1_124
// primary_visible_card_scope_0_1_125
// stable_card_signature_route_0_1_127

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.roundToInt

class LiveRideAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val screenshotInProgress = AtomicBoolean(false)
    private val phoneCaptureInProgress118 = AtomicBoolean(false)
    private var analyzeJob: Job? = null
    private var overlayView: TextView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    @Volatile private var bubbleGestureActive = false
    private var bubbleDragStartedAtMillis = 0L
    private var overlayMenuView: View? = null
    private var overlayMenuParams: WindowManager.LayoutParams? = null
    private var whatsappShortcutView: TextView? = null
    private var whatsappShortcutParams: WindowManager.LayoutParams? = null
    private val phoneCaptureInProgress = AtomicBoolean(false)
    private var windowManager: WindowManager? = null
    private var lastSnapshotHash: Int? = null
    private var lastAnalyzedHash: Int? = null
    private var lastSavedReadHash: Int? = null
    private var pendingAnalysis: PendingLiveAnalysis? = null
    private var lastScreenshotMillis: Long = 0L
    private var continuousScanStarted = false
    private var proximityAlertMonitorStarted = false
    private var serviceReady = false
    private var analyzing = false
    private var analysisSerial: Long = 0L
    private var liveAnalysisJob: Job? = null
    private val coreCardAnalysisCoalescer = br.com.mapeiaia.rotacerta.core.CoreCardAnalysisCoalescer()
    private var activePackageName: String? = null
    private var lastRidePackageName: String? = null
    private var lastTextPackageName: String? = null
    private var lastAccessibilityText: String = ""
    private var lastOcrText: String = ""
    private var lastAccessibilityTextAtMillis: Long = 0L
    private var lastOcrTextAtMillis: Long = 0L
    private var lastUniversalAddressSeenAtMillis: Long = 0L
    private var lastUniversalAddressSignature: String? = null // universal_source_freshness_fields_0_1_94
    private var currentSettings = AppSettings()
    private var currentCardTemplates = emptyList<RideCardTemplate>()
    private var currentSavedPlaces = emptyList<SavedPlace>()
    private var currentImportedRadars = emptyList<ImportedRadar>()
    private var currentRadarColor = RadarColor.Idle
    private var currentDistanceKm: Double? = null
    private var lastBubbleStateStage: String = "created"
    private var lastBubbleStateReason: String = "Servico criado; aguardando conexao da acessibilidade."
    private var lastDecisionOverlayAtMillis: Long = 0L
    private var lastPassiveTraceKey: String = ""
    private var lastPassiveTraceAtMillis: Long = 0L
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
    private val bubbleDecisionPolicy = LiveRideBubbleDecisionPolicy()
    private lateinit var bubblePrefs: SharedPreferences
    private lateinit var speechEngine: LiveSpeechEngine
    private lateinit var proximityAlertEngine: ProximityAlertEngine
    private lateinit var shortcutOverlayController: BubbleShortcutOverlayController
    private lateinit var radarDetectionCue: RadarDetectionCue
    private val registeredCardGate = RegisteredCardDecisionGate()
    private val universalRouteCache = LiveRideRouteCache()
    private var universalRouteJob: Job? = null
    private var universalScreenGeneration: Long = 0L
    private var universalWindowGeneration: Long = 0L // universal_ocr_window_generation_0_1_120
    private var universalLastActiveReadAtMillis: Long = 0L
    private var universalActiveRidePackageName: String? = null // universal_route_inflight_runtime_0_1_120
    private var universalActiveAddressSignature: String? = null // universal_two_address_fields_0_1_98
    private val accessibilityEventFloodGate = AccessibilityEventFloodGate()
    private val importedRadarSpatialIndex = ImportedRadarSpatialIndex()
    private var lastExternalWindowPackageName: String? = null
    private var universalAccessibilityOwnsCard: Boolean = false // universal_fast_read_field_0_1_108
    private val universalLiveReadGate = UniversalLiveReadGate()
    private val universalAnalysisDeduper = UniversalAnalysisDeduper()
    private var universalForegroundPackageName: String? = null
    private var universalLastTriggerTraceSignature: String? = null
    private var universalLastTriggerTraceAtMillis: Long = 0L // universal_runtime_stability_fields_0_1_101
    private val coreLiveReadTriggerGate = br.com.mapeiaia.rotacerta.core.CoreLiveReadTriggerGate(duplicateWindowMs = 180L)
    private val coreVisibleCardLifecycle = br.com.mapeiaia.rotacerta.core.CoreVisibleCardLifecycle()
    private var lastVisibleCardSignature: String? = null
    private val coreLivePipeline = br.com.mapeiaia.rotacerta.core.CoreLiveAnalysisPipeline()
    private val coreBubbleState = br.com.mapeiaia.rotacerta.core.CoreBubbleStateController()
    private val coreBubblePresenter = br.com.mapeiaia.rotacerta.core.CoreBubblePresenter
    private val bubbleRenderCoordinator = br.com.mapeiaia.rotacerta.core.BubbleRenderCoordinator()
    private val cardExitConfirmationGate = CardExitConfirmationGate()
    private val primaryCardFocusLock = PrimaryCardFocusLock()
    private val liveGeocodeCache = LiveGeocodeCache()
    private lateinit var rideCardImageStore: RideCardImageStore
    private val automaticCardCaptureSignatures = LinkedHashMap<String, Long>()
    private var quickReplyTargetPackageName: String? = null
    private var activeRadarPopupId: String? = null
    private val quickReplyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_APPLY_QUICK_REPLY) return
            val text = intent.getStringExtra(EXTRA_QUICK_REPLY_TEXT)?.trim().orEmpty()
            if (text.isNotBlank()) scope.launch { QuickReplyAccessibilityFiller.apply(this@LiveRideAccessibilityService, text) }
        }
    }

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
        rideCardImageStore = RideCardImageStore(applicationContext)
        DiagnosticRuntimeGate.setEnabled(false)
        liveGeocodeCache.importSnapshot(bubblePrefs.getString("persistent_geocode_cache_v1", "").orEmpty())
        val quickReplyFilter = IntentFilter(ACTION_APPLY_QUICK_REPLY)
        ContextCompat.registerReceiver(
            this,
            quickReplyReceiver,
            quickReplyFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        ) // quick_reply_receiver_not_exported_0_1_128
        // quick_replies_accessibility_fill_0_1_128: o receptor encaminha o texto ao QuickReplyAccessibilityFiller.
        val restoredExactRoutes = universalRouteCache.importSnapshot(
            bubblePrefs.getString("persistent_exact_route_cache_v1", "").orEmpty(),
        )
        traceEvent("universal.route.cache restored=$restoredExactRoutes") // persistent_route_cache_restore_0_1_124
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
        radarDetectionCue = RadarDetectionCue()
        shortcutOverlayController = BubbleShortcutOverlayController(
            context = applicationContext,
            windowManager = requireNotNull(windowManager),
            trace = ::traceEvent,
        )
        Unit
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceReady = true
        Unit
        scope.launch {
            repository.settings.collect { updated ->
                val wasEnabled = currentSettings.diagnosticsEnabled
                currentSettings = updated
                DiagnosticRuntimeGate.setEnabled(updated.diagnosticsEnabled)
                if (wasEnabled && !updated.diagnosticsEnabled) {
                    DiagnosticLogStore.clear()
                    LiveFailureTraceStore.clear()
                }
            }
        }
        scope.launch { repository.savedPlaces.collect { currentSavedPlaces = it } }
        scope.launch { repository.cardTemplates.collect { currentCardTemplates = it } } // manual_cards_observer_0_1_127
        scope.launch { repository.importedRadars.collect { currentImportedRadars = it } }
        scope.launch {
            currentSettings = repository.settings.first()
            if (currentSettings.requireRegisteredRideCard || currentSettings.restrictToSelectedRideApps) {
                currentSettings = currentSettings.copy(
                    requireRegisteredRideCard = false,
                    restrictToSelectedRideApps = false,
                )
                repository.saveSettings(currentSettings)
            } // universal_cards_optional_settings_0_1_120
            DiagnosticRuntimeGate.setEnabled(currentSettings.diagnosticsEnabled)
            val manualSelectionPrefs127 = getSharedPreferences("rota_certa_runtime_migrations", Context.MODE_PRIVATE)
            if (!manualSelectionPrefs127.getBoolean("manual_selection_storage_ready_0_1_127", false)) {
                if (!SelectedRideAppStore.hasExplicitSelection(applicationContext)) {
                    SelectedRideAppStore.save(applicationContext, emptySet())
                }
                currentSettings = currentSettings.copy(
                    requireRegisteredRideCard = false,
                    restrictToSelectedRideApps = true,
                    monitor99 = false,
                    monitorUber = false,
                    monitorInDrive = false,
                    extraMonitoredPackages = "",
                )
                repository.saveSettings(currentSettings)
                manualSelectionPrefs127.edit()
                    .putBoolean("manual_selection_storage_ready_0_1_127", true)
                    .apply()
                DiagnosticLogStore.record(
                    "migration",
                    "manual_selection.ready selected=${SelectedRideAppStore.read(applicationContext).size} cards=${repository.cardTemplates.first().size}",
                )
            }
            currentCardTemplates = repository.cardTemplates.first() // universal_optional_card_model_migration_0_1_101 // manual_cards_preserved_0_1_127
            // pre_registered_runtime_cleanup_0_1_126 superseded_by_manual_selection_0_1_127
            showOverlay(RadarColor.Idle)
            // WhatsApp agora fica dentro da central da bolinha. // whatsapp_inside_grid_0_1_94
            Unit
            startContinuousScan()
            startProximityAlertMonitor()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!serviceReady || event == null) return
        if (!currentSettings.liveReadingEnabled) return // bubble_reading_gate_0_1_118
        if (!currentSettings.appEnabled || !currentSettings.liveReadingEnabled) {
            hardClearUniversalTwoAddress("Leitura universal desligada.")
            return
        }

        val eventPackage = normalizePackageName(event.packageName?.toString())
        val rootPackage = currentRootPackageName()
        val candidatePackage = rootPackage?.takeIf { shouldScanPackage(it) } ?: eventPackage ?: rootPackage
        if (!AccessibilityEventFloodGate.isRelevantEventType(event.eventType)) return
        val ownMainActivityEvent = UniversalWindowPackageResolver.isOwnMainActivityEvent(
            eventPackageName = candidatePackage,
            eventClassName = event.className?.toString(),
            eventType = event.eventType,
            ownPackageName = this.packageName,
            mainActivityClassName = MainActivity::class.java.name,
            windowStateChangedType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
        )

        if (candidatePackage == this.packageName) {
            if (ownMainActivityEvent) {
                if (universalForegroundPackageName != this.packageName) universalWindowGeneration += 1L
                universalForegroundPackageName = this.packageName
                activePackageName = this.packageName
                analyzeJob?.cancel()
                analyzeJob = null
                hardClearUniversalTwoAddress("Tela do proprio Rota Certa.")
            } else {
                Unit // selected_apps_overlay_quiet_0_1_122
            }
            return
        }

        val resolvedPackage = candidatePackage ?: lastExternalWindowPackageName ?: return
        if (!shouldScanPackage(resolvedPackage)) {
            val activeRidePackage = universalActiveRidePackageName ?: lastExternalWindowPackageName
            val transientOverlayEvent = universalActiveAddressSignature != null &&
                activeRidePackage?.let { shouldScanPackage(it) } == true &&
                (resolvedPackage == "com.android.systemui" || isPassiveIgnoredPackage(resolvedPackage))
            if (transientOverlayEvent) {
                traceEvent("universal.foreground transient_systemui_preserved=true incoming=$resolvedPackage active=${activeRidePackage.orEmpty()}")
                scheduleVisibleTextAnalysis(delayMs = 90L, allowPopupCandidate = true)
                return
            }
            if (universalForegroundPackageName != resolvedPackage) universalWindowGeneration += 1L
            universalForegroundPackageName = resolvedPackage
            activePackageName = resolvedPackage
            lastExternalWindowPackageName = resolvedPackage
            hardClearUniversalTwoAddress(scanBlockReason(resolvedPackage)) // universal_package_block_reason_0_1_126
            return
        } // false_clear_confirmation_0_1_128
        if (accessibilityEventFloodGate.classify(
                packageName = resolvedPackage,
                eventType = event.eventType,
                monitoredPackage = true,
            ) == AccessibilityEventMode.Ignore
        ) return // selected_apps_event_gate_0_1_122
        val protectActiveRoute = UniversalFastReadPolicy.shouldProtectRouteFromForeignEvent(
            hasActiveAddressSignature = universalActiveAddressSignature != null,
            routeInFlight = universalRouteJob?.isActive == true,
            lastActiveReadAtMillis = universalLastActiveReadAtMillis,
            nowMillis = System.currentTimeMillis(),
            activeRidePackageName = universalActiveRidePackageName,
            incomingPackageName = resolvedPackage,
        )
        if (protectActiveRoute) {
            traceEvent("universal.foreground ignored_foreign_event_during_route=true incoming=$resolvedPackage active=${universalActiveRidePackageName.orEmpty()}")
            return
        }
        val previousObservedPackage = universalForegroundPackageName
        val previousExternalPackage = previousObservedPackage?.takeUnless { it == this.packageName }
        if (previousObservedPackage != resolvedPackage) universalWindowGeneration += 1L
        universalForegroundPackageName = resolvedPackage
        activePackageName = resolvedPackage
        lastExternalWindowPackageName = resolvedPackage
        if (previousExternalPackage != null && previousExternalPackage != resolvedPackage) {
            hardClearUniversalTwoAddress("Aplicativo ou janela alterada; resultado anterior removido.")
            universalForegroundPackageName = resolvedPackage
            activePackageName = resolvedPackage
            lastExternalWindowPackageName = resolvedPackage
        }

        traceEvent("universal.event package=" + resolvedPackage + " type=" + event.eventType) // universal_two_address_event_0_1_98 universal_stable_foreground_event_0_1_101
        scheduleVisibleTextAnalysis(delayMs = 0L, allowPopupCandidate = true)
        requestScreenshotAnalysis(allowPopupCandidate = true)
    } // universal_overlay_event_guard_0_1_106

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        Unit
        serviceReady = false
        screenshotInProgress.set(false)
        coreLiveReadTriggerGate.reset() // gigu_inspired_gate_reset_0_1_89
        analyzeJob?.cancel()
        liveAnalysisJob?.cancel() // latest_card_wins_destroy_0_1_91
        removeOverlay()
        radarDetectionCue.release()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        textToSpeechReady = false
        runCatching { unregisterReceiver(quickReplyReceiver) }
        DiagnosticRuntimeGate.setEnabled(false)
        scope.cancel()
        super.onDestroy()
    }

    private fun startContinuousScan() {
        if (continuousScanStarted || !serviceReady) return
        continuousScanStarted = true
        traceEvent("universal.scan.loop interval=$SCAN_LOOP_MS")
        scope.launch {
            while (serviceReady) {
                if (!currentSettings.liveReadingEnabled) {
                    delay(SCAN_LOOP_MS)
                    continue
                } // bubble_reading_scan_gate_0_1_118
                if (bubbleGestureActive) {
                    delay(BubbleDragPolicy.ANALYSIS_RESUME_DELAY_MS)
                    continue
                } // bubble_drag_scan_pause_0_1_116
                when {
                    !currentSettings.appEnabled || !currentSettings.liveReadingEnabled ->
                        hardClearUniversalTwoAddress("Leitura universal desligada.")
                    !isUniversalExternalWindowActive() ->
                        hardClearUniversalTwoAddress("Tela do proprio Rota Certa ou janela sem leitura valida.")
                    else -> {
                        val expectedPackage = universalResolvedForegroundPackage()
                        if (!shouldScanPackage(expectedPackage) ||
                            !UniversalFastReadPolicy.shouldScanLivePackage(
                                packageName = expectedPackage,
                                ownPackageName = this@LiveRideAccessibilityService.packageName,
                            )
                        ) { // selected_apps_scan_loop_0_1_122
                            hardClearUniversalTwoAddress("Pacote passivo; leitura e OCR suspensos.")
                        } else {
                            val visibleText = collectVisibleText(allowPopupCandidate = true)
                            if (expectedPackage == universalResolvedForegroundPackage() && isUniversalExternalWindowActive()) {
                                processRideText(
                                    visibleText,
                                    TextSource.Accessibility,
                                    allowPopupCandidate = true,
                                ) // global_continuous_empty_clear_0_1_124
                                requestScreenshotAnalysis(allowPopupCandidate = true)
                            }
                        }
                    }
                }
                val accessibilityScanDelayMillis = UniversalFastReadPolicy.minimumAccessibilityScanIntervalMillis(
                    accessibilityOwnsCard = universalAccessibilityOwnsCard,
                    hasActiveAddressSignature = universalActiveAddressSignature != null,
                )
                delay(accessibilityScanDelayMillis)
            }
        }
    } // universal_stable_scan_0_1_101

    private fun startProximityAlertMonitor() {
        if (proximityAlertMonitorStarted || !serviceReady) return
        proximityAlertMonitorStarted = true
        Unit
        scope.launch {
            while (serviceReady) {
                if (!currentSettings.appEnabled || !currentSettings.proximityAlertsEnabled) {
                    delay(PROXIMITY_ALERT_LOOP_MS)
                    continue
                }
                val alerts = currentSavedPlaces.filter { it.type == SavedPlaceType.ProximityAlert }
                val radars = currentImportedRadars
                if (alerts.isNotEmpty() || radars.isNotEmpty()) checkProximityAlerts(alerts, radars)
                delay(PROXIMITY_ALERT_LOOP_MS)
            }
        }
    }

    private suspend fun checkProximityAlerts(alerts: List<SavedPlace>, radars: List<ImportedRadar>) {
        if (!currentSettings.appEnabled || !currentSettings.proximityAlertsEnabled) return
        val coordinate = locationService.currentCoordinate() ?: return
        val radarSearchRadiusMeters = currentSettings.proximityAlertDistanceMeters.coerceAtLeast(200).toDouble() + 1_000.0
        val nearbyRadarQuery = importedRadarSpatialIndex.query(
            source = radars,
            center = coordinate,
            radiusMeters = radarSearchRadiusMeters,
        ) // radar_spatial_index_0_1_122
        val nearbyRadars = nearbyRadarQuery.radars
        proximityAlertEngine.check(
            alerts = alerts,
            radars = nearbyRadars,
            coordinate = coordinate,
            settings = currentSettings,
            onSavedPlacePopup = { alert, distanceMeters -> showSavedAlertPopup(alert, distanceMeters) },
            onSavedPlacePopupState = { popupState ->
                when (popupState) {
                    is ProximityAlertPopupState.Visible -> showSavedAlertPopup(
                        alert = popupState.alert,
                        distanceMeters = popupState.distanceMeters,
                        firstAlertDistanceMeters = popupState.firstAlertDistanceMeters,
                    )
                    is ProximityAlertPopupState.Hidden -> {
                        shortcutOverlayController.hideProximityAlert(popupState.alertId)
                        persistResourceShortcutState()
                    }
                }
            },
            onImportedRadarDetected = { radar, distanceMeters -> showImportedRadarPopup(radar, distanceMeters) },
            onImportedRadarPassed = { radarId ->
                shortcutOverlayController.hideProximityAlert(radarId)
                if (activeRadarPopupId == radarId) activeRadarPopupId = null
                persistResourceShortcutState()
            },
            onDiagnostic = { diagnostic -> recordDiagnostic(stage = diagnostic.stage, reason = diagnostic.reason) },
        )
    }

    private fun scheduleVisibleTextAnalysis(delayMs: Long, allowPopupCandidate: Boolean = false) {
        if (!currentSettings.liveReadingEnabled) return // bubble_reading_schedule_gate_0_1_118
        if (bubbleGestureActive) {
            traceEvent("bubble.drag.analysis_paused source=accessibility")
            return
        } // bubble_drag_accessibility_pause_0_1_116
        if (!serviceReady || !currentSettings.appEnabled || !currentSettings.liveReadingEnabled) return
        if (!isUniversalExternalWindowActive()) return
        val expectedPackage = universalResolvedForegroundPackage() ?: return
        if (!shouldScanPackage(expectedPackage)) return // selected_apps_schedule_0_1_122
        if (!UniversalFastReadPolicy.shouldScanLivePackage(
                packageName = expectedPackage,
                ownPackageName = this.packageName,
            )
        ) return
        analyzeJob?.cancel()
        analyzeJob = scope.launch {
            if (delayMs > 0L) delay(delayMs)
            if (!isUniversalExternalWindowActive() || expectedPackage != universalResolvedForegroundPackage()) return@launch
            val visibleText = collectVisibleText(allowPopupCandidate = true)
            if (!isUniversalExternalWindowActive() || expectedPackage != universalResolvedForegroundPackage()) return@launch
            processRideText(
                visibleText,
                TextSource.Accessibility,
                allowPopupCandidate = true,
            ) // global_scheduled_empty_clear_0_1_124
        }
    } // universal_stable_schedule_0_1_101

    private fun requestScreenshotAnalysis(allowPopupCandidate: Boolean = false) {
        if (!currentSettings.liveReadingEnabled) return // bubble_reading_ocr_gate_0_1_118
        if (bubbleGestureActive) {
            traceEvent("bubble.drag.analysis_paused source=screenshot")
            return
        } // bubble_drag_screenshot_pause_0_1_116
        if (!serviceReady || !isUniversalExternalWindowActive() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val resolvedOcrPackage = universalResolvedForegroundPackage() ?: return
        val ocrRequestToken = UniversalFastReadPolicy.createOcrRequestToken(
            observedPackageName = universalForegroundPackageName ?: activePackageName,
            resolvedPackageName = resolvedOcrPackage,
            ownPackageName = this.packageName,
            screenGeneration = universalScreenGeneration,
            windowGeneration = universalWindowGeneration,
        ) ?: run {
            traceEvent("universal.ocr request_blocked_observed_window=true")
            return
        }
        val requestedPackage = ocrRequestToken.observedPackageName
        if (!shouldScanPackage(requestedPackage)) return // selected_apps_ocr_gate_0_1_122
        if (!UniversalFastReadPolicy.shouldScanLivePackage(
                packageName = requestedPackage,
                ownPackageName = this.packageName,
            )
        ) return
        if (!UniversalFastReadPolicy.shouldRequestOcr(
                accessibilityOwnsCard = universalAccessibilityOwnsCard,
                hasActiveAddressSignature = universalActiveAddressSignature != null,
            )
        ) return
        val now = System.currentTimeMillis()
        val minimumOcrIntervalMillis = UniversalFastReadPolicy.minimumOcrIntervalMillis(
            hasActiveAddressSignature = universalActiveAddressSignature != null,
        )
        if (now - lastScreenshotMillis < minimumOcrIntervalMillis) return
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
                                if (!UniversalFastReadPolicy.isOcrRequestFresh(
                                        token = ocrRequestToken,
                                        observedPackageName = universalForegroundPackageName ?: activePackageName,
                                        resolvedPackageName = universalResolvedForegroundPackage(),
                                        ownPackageName = this@LiveRideAccessibilityService.packageName,
                                        screenGeneration = universalScreenGeneration,
                                        windowGeneration = universalWindowGeneration,
                                    )
                                ) {
                                    traceEvent("universal.ocr discarded_stale_window=true")
                                    return@runCatching
                                }
                                val bitmap = screenshot.toSoftwareBitmap() ?: return@runCatching
                                val ocrText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                        ocrService.extractText(bitmap)
                                    } // bubble_drag_ocr_background_0_1_116
                                if (!UniversalFastReadPolicy.isOcrRequestFresh(
                                        token = ocrRequestToken,
                                        observedPackageName = universalForegroundPackageName ?: activePackageName,
                                        resolvedPackageName = universalResolvedForegroundPackage(),
                                        ownPackageName = this@LiveRideAccessibilityService.packageName,
                                        screenGeneration = universalScreenGeneration,
                                        windowGeneration = universalWindowGeneration,
                                    )
                                ) {
                                    traceEvent("universal.ocr discarded_after_extract=true generation_or_window_changed=true")
                                    return@runCatching
                                }
                                processRideText(ocrText, TextSource.Ocr, allowPopupCandidate = true)
                                if (currentSettings.automaticCardCaptureEnabled) {
                                    scope.launch {
                                        captureAutomaticRideCard(
                                            bitmap = bitmap,
                                            extractedText = ocrText,
                                            packageName = requestedPackage,
                                        )
                                    }
                                }
                            }.onFailure { error ->
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
                        screenshotInProgress.set(false)
                    }
                },
            )
        }.onFailure {
            screenshotInProgress.set(false)
        }
    } // universal_stable_screenshot_0_1_101

    private suspend fun captureAutomaticRideCard(
        bitmap: Bitmap,
        extractedText: String,
        packageName: String?,
    ) {
        if (!currentSettings.automaticCardCaptureEnabled || extractedText.isBlank()) return
        val selection = PrimaryVisibleRideCardSelector.select(extractedText)
        val cardText = selection.selectedText.trim()
        val trigger = UniversalAddressTrigger.evaluate(cardText)
        val passenger = RidePassengerIdentityPolicy.evaluate(cardText)
        val evidence = UniversalRideCardEvidencePolicy.evaluate(
            text = cardText,
            addresses = trigger.addresses,
            destination = trigger.destination,
            packageName = packageName,
        )
        if (!trigger.active || trigger.addresses.size < 2 || !passenger.accepted || !evidence.accepted) return
        val signature = selection.cardSignature.takeIf(String::isNotBlank)
            ?: "${passenger.candidates.single()}|${trigger.addressSignature}"
        val now = System.currentTimeMillis()
        synchronized(automaticCardCaptureSignatures) {
            automaticCardCaptureSignatures.entries.removeAll { now - it.value > AUTOMATIC_CARD_CAPTURE_DEDUPE_MS }
            if (automaticCardCaptureSignatures.containsKey(signature)) return
            automaticCardCaptureSignatures[signature] = now
            while (automaticCardCaptureSignatures.size > 80) {
                automaticCardCaptureSignatures.remove(automaticCardCaptureSignatures.keys.first())
            }
        }
        val bounds = findRideCardBounds(trigger)
        val stored = rideCardImageStore.save(
            bitmap = bitmap,
            requestedBounds = bounds,
            packageName = packageName,
            signature = signature,
        ) ?: return
        val template = RideCardTemplateMatcher.createTemplate(packageName, cardText)
        repository.addCardTemplate(template)
        repository.addCapturedScreen(
            CapturedRideScreen(
                createdAtMillis = now,
                packageName = packageName,
                textHash = trigger.screenHash,
                textPreview = cardText.take(1_600),
                parserName = "automatic-screenshot",
                pickup = trigger.pickup,
                destination = trigger.destination,
                imagePath = stored.path,
                autoCaptured = true,
                cropLeft = stored.bounds.left,
                cropTop = stored.bounds.top,
                cropRight = stored.bounds.right,
                cropBottom = stored.bounds.bottom,
            ),
        )
        traceEvent("card.capture automatic=true template=${template.name} bounds=${stored.bounds}")
    } // automatic_card_capture_0_1_128

    private fun findRideCardBounds(trigger: UniversalAddressTriggerDecision): Rect? {
        val root = rootInActiveWindow ?: return null
        val targets = listOfNotNull(trigger.pickup, trigger.destination)
            .map { it.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim() }
            .filter(String::isNotBlank)
        if (targets.isEmpty()) return null
        val matches = mutableListOf<Rect>()
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null) return
            val value = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
                .joinToString(" ")
                .lowercase(Locale.ROOT)
                .replace(Regex("\\s+"), " ")
            if (targets.any { target -> value.contains(target) || target.contains(value).takeIf { value.length >= 12 } == true }) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0) matches += rect
            }
            for (index in 0 until node.childCount) visit(runCatching { node.getChild(index) }.getOrNull())
        }
        visit(root)
        if (matches.isEmpty()) return null
        return Rect(matches.first()).apply {
            matches.drop(1).forEach { union(it) }
            inset(-CARD_CROP_HORIZONTAL_PADDING_PX, -CARD_CROP_VERTICAL_PADDING_PX)
        }
    }

    private fun collectVisibleText(allowPopupCandidate: Boolean = false): String {
        if (!serviceReady || !isUniversalExternalWindowActive()) return ""
        val root = rootInActiveWindow ?: return ""
        val rootPackage = normalizePackageName(root.packageName?.toString())
        val expectedPackage = universalForegroundPackageName
        if (rootPackage == this.packageName || expectedPackage == this.packageName) return ""
        if (rootPackage != null && expectedPackage != null && rootPackage != expectedPackage) return ""
        val lines = mutableListOf<String>()
        collectNodeText(root, lines)
        return lines.map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString("\n")
    } // universal_stable_collect_0_1_101

    private fun collectNodeText(node: AccessibilityNodeInfo?, lines: MutableList<String>) {
        if (node == null) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { lines += it }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { lines += it }
        for (index in 0 until node.childCount) {
            collectNodeText(runCatching { node.getChild(index) }.getOrNull(), lines)
        }
    }

    private fun forceClearUniversalResult(reason: String) {
        invalidateLiveAnalysis("universal_clear:" + reason)
        registeredCardGate.clear()
        coreCardAnalysisCoalescer.invalidate()
        lastUniversalAddressSignature = null
        lastVisibleCardSignature = null
        lastSnapshotHash = null
        lastAnalyzedHash = null
        lastDecisionOverlayAtMillis = 0L
        lastAccessibilityText = ""
        lastOcrText = ""
        lastAccessibilityTextAtMillis = 0L
        lastOcrTextAtMillis = 0L
        currentRadarColor = RadarColor.Idle
        currentDistanceKm = null
        showOverlay(RadarColor.Idle, null)
        traceEvent("universal.clear reason=" + reason)
    }

    private suspend fun processRideText(
        text: String,
        source: TextSource,
        allowPopupCandidate: Boolean = false,
    ) {
        // global_single_passenger_gate_0_1_124
        // global_passenger_and_addresses_card_0_1_124
        // global_inactive_clear_now_0_1_124
        // global_full_screen_hash_0_1_124
        if (bubbleGestureActive) {
            traceEvent("bubble.drag.analysis_paused source=process")
            return
        } // bubble_drag_process_pause_0_1_116
        if (!serviceReady || !currentSettings.appEnabled || !currentSettings.liveReadingEnabled) return
        val processWindowPackage = currentRootPackageName() ?: currentWindowPackageName()
        val transientProcessWindow = universalActiveAddressSignature != null &&
            (processWindowPackage == "com.android.systemui" || isPassiveIgnoredPackage(processWindowPackage))
        if (!isUniversalExternalWindowActive()) {
            if (transientProcessWindow) {
                traceEvent("universal.process transient_window_preserved=true package=${processWindowPackage.orEmpty()}")
                return
            }
            hardClearUniversalTwoAddress("Janela atual nao permite leitura universal.")
            return
        }

        if (!shouldScanCurrentWindow()) {
            if (transientProcessWindow) return
            hardClearUniversalTwoAddress(scanBlockReason(currentWindowPackageName())) // universal_process_block_reason_0_1_126
            return // selected_apps_process_gate_0_1_122
        }

        val fullSnapshotText = text.trim()
        val focusDecision = if (currentSettings.multiCardFocusLockEnabled) {
            primaryCardFocusLock.select(fullSnapshotText)
        } else {
            FocusedRideCardDecision(
                selection = PrimaryVisibleRideCardSelector.select(fullSnapshotText),
                holdPrevious = false,
                reason = "trava_desligada",
            )
        }
        if (focusDecision.holdPrevious) {
            traceEvent("universal.card.focus hold_previous=true reason=${focusDecision.reason}")
            return
        }
        val primaryCardSelection = focusDecision.selection ?: PrimaryVisibleRideCardSelector.select(fullSnapshotText)
        val snapshotText = primaryCardSelection.selectedText
        if (primaryCardSelection.cardCount > 1) {
            traceEvent(
                "universal.card.scope selected_index=${primaryCardSelection.selectedIndex} cards=${primaryCardSelection.cardCount} passenger=${primaryCardSelection.passengerName.orEmpty()} reason=${focusDecision.reason}",
            )
        } // multi_card_focus_lock_0_1_128
        val trigger = UniversalAddressTrigger.evaluate(snapshotText)
        LiveFailureTraceStore.recordRead(
            source = source.toString(),
            packageName = currentWindowPackageName(),
            text = snapshotText,
            addresses = trigger.addresses,
            destination = trigger.destination,
            active = trigger.active,
            screenHash = trigger.screenHash,
            generation = if (lastSnapshotHash != trigger.screenHash) universalScreenGeneration + 1L else universalScreenGeneration,
        ) // session_diagnostic_read_v2
        traceUniversalTrigger(source, trigger)
        val rideEvidence = UniversalRideCardEvidencePolicy.evaluate(
            text = snapshotText,
            addresses = trigger.addresses,
            destination = trigger.destination,
            packageName = universalResolvedForegroundPackage(),
        )
        if (trigger.addresses.size >= 2 && !rideEvidence.accepted) {
            traceEvent("universal.card.evidence accepted=false score=${rideEvidence.score} reason=${rideEvidence.reason}")
        }
        val liveSource = when (source) {
            TextSource.Accessibility -> UniversalLiveReadSource.Accessibility
            TextSource.Ocr -> UniversalLiveReadSource.Ocr
        }
        val passengerIdentity = RidePassengerIdentityPolicy.evaluate(snapshotText)
        if (!passengerIdentity.accepted && trigger.addresses.size >= 2) {
            traceEvent("universal.passenger accepted=false count=${passengerIdentity.candidates.size} reason=${passengerIdentity.reason}")
        }
        val activeTrigger = trigger.addresses.size >= 2 && trigger.active &&
            !trigger.destination.isNullOrBlank() && rideEvidence.accepted && passengerIdentity.accepted
        val readNowMillis = System.currentTimeMillis()
        if (!activeTrigger) {
            val clearReason = when {
                passengerIdentity.candidates.size > 1 -> "Mais de um passageiro permaneceu dentro do mesmo bloco; aguardando confirmar a troca do card."
                passengerIdentity.candidates.isEmpty() && trigger.addresses.size >= 2 -> "Passageiro unico nao identificado; aguardando confirmar a saida real do card."
                else -> "Card saiu, mudou ou nao possui embarque e destino; aguardando confirmacao curta."
            }
            if (!cardExitConfirmationGate.shouldClear(readNowMillis)) {
                traceEvent("universal.clear deferred=true reason=$clearReason")
                return
            }
            hardClearUniversalTwoAddress(clearReason)
            return
        }
        val passenger = passengerIdentity.candidates.single()
        val stableCardSignature = primaryCardSelection.cardSignature.takeIf(String::isNotBlank)
            ?: "$passenger|${trigger.addressSignature}"
        val cardDecisionSignature = "$passenger|${trigger.addressSignature}|$stableCardSignature"
        cardExitConfirmationGate.observeActive(cardDecisionSignature)
        universalLastActiveReadAtMillis = readNowMillis
        universalActiveRidePackageName = universalResolvedForegroundPackage()
        when (universalLiveReadGate.submit(liveSource, activeTrigger)) {
            UniversalLiveReadAction.Ignore -> {
                traceEvent("universal.source ignored=$source active=$activeTrigger")
                return
            }
            UniversalLiveReadAction.Clear -> {
                if (cardExitConfirmationGate.shouldClear(readNowMillis)) {
                    hardClearUniversalTwoAddress("Menos de dois enderecos numerados na fonte ativa; saida confirmada.")
                }
                return
            }
            UniversalLiveReadAction.Analyze -> {
                universalAccessibilityOwnsCard = liveSource == UniversalLiveReadSource.Accessibility
            }
        }

        val analysisHash = trigger.screenHash
        val fields = RideFields(pickup = trigger.pickup, destination = trigger.destination)
        val cardChanged = universalActiveAddressSignature != cardDecisionSignature
        if (cardChanged) {
            universalScreenGeneration += 1L
            universalRouteJob?.cancel()
            universalActiveAddressSignature = cardDecisionSignature
            lastSnapshotHash = analysisHash
            lastAnalyzedHash = null
            pendingAnalysis = null
            analyzing = false
            currentDistanceKm = null
            publishRuntimeValidationTrigger(trigger)

            val cacheKey = LiveRideRouteCache.keyFor(
                fields = fields,
                settings = currentSettings,
                packageName = null,
                cardSignature = null,
            )
            val cached = universalRouteCache.get(cacheKey)
            if (cached != null) {
                traceEvent("universal.route.cache_first hit=true age=${cached.ageMillis}ms")
                val cachedResult = decisionEngine.decide(
                    fields = fields,
                    settings = currentSettings,
                    destinationCoordinate = cached.destinationCoordinate,
                    homeCoordinate = cached.homeCoordinate,
                    alternativeCoordinate = cached.alternativeCoordinate,
                    fullText = snapshotText,
                    homeDistanceKm = cached.homeDistanceKm,
                    alternativeDistanceKm = cached.alternativeDistanceKm,
                )
                applyUniversalTwoAddressResult(
                    result = cachedResult,
                    screenHash = analysisHash,
                    addressSignature = cardDecisionSignature,
                    generation = universalScreenGeneration,
                )
                return
            } // cache_first_before_yellow_0_1_128

            rememberBubbleReason("universal_waiting", "Novo card identificado; destino em calculo.")
            showOverlay(RadarColor.Default, distanceKm = null)
            traceEvent("universal.card.changed hash=$analysisHash yellow=true signature=${cardDecisionSignature.hashCode()}")
        } else {
            if (lastSnapshotHash != analysisHash) {
                traceEvent("universal.screen.cosmetic_change ignored=true hash=$analysisHash signature=${cardDecisionSignature.hashCode()}")
            }
            if (lastAnalyzedHash != null || universalRouteJob?.isActive == true) return
            lastSnapshotHash = analysisHash
        }

        val generation = universalScreenGeneration
        universalRouteJob = scope.launch {
            analyzeUniversalTwoAddress(
                snapshotText = snapshotText,
                fields = fields,
                screenHash = analysisHash,
                addressSignature = cardDecisionSignature,
                generation = generation,
            )
        }
    } // universal_stable_process_0_1_101

    //    private fun resolveRidePackageForText( compatibility_boundary_0_1_102

    private suspend fun analyzeUniversalTwoAddress(
        snapshotText: String,
        fields: RideFields,
        screenHash: Int,
        addressSignature: String,
        generation: Long,
    ) {
        // instant_farol_cached_settings_0_1_124
        val settings = currentSettings
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return
        val region = DeviceRegion(country = "Brasil")
        val cacheKey = LiveRideRouteCache.keyFor(
            fields = fields,
            settings = settings,
            packageName = null,
            cardSignature = null,
        )
        universalRouteCache.get(cacheKey)?.let { cached ->
            traceEvent("universal.route.cache hit=true age=${cached.ageMillis}ms")
            val cachedResult = decisionEngine.decide(
                fields = fields,
                settings = settings,
                destinationCoordinate = cached.destinationCoordinate,
                homeCoordinate = cached.homeCoordinate,
                alternativeCoordinate = cached.alternativeCoordinate,
                fullText = snapshotText,
                homeDistanceKm = cached.homeDistanceKm,
                alternativeDistanceKm = cached.alternativeDistanceKm,
            )
            applyUniversalTwoAddressResult(cachedResult, screenHash, addressSignature, generation)
            return
        }
        traceEvent("universal.route.cache hit=false")

        val geocodeStartedAt = System.currentTimeMillis()
        val coordinates = coroutineScope {
            val destinationDeferred = async {
                fields.destination?.let { geocodeBest(it, region, settings) }
            }
            val homeDeferred = async {
                if (settings.homeTargetEnabled) {
                    settings.homeCoordinate ?: settings.homeAddress.takeIf(String::isNotBlank)?.let { geocodeBest(it, region, settings) }
                } else null
            }
            val alternativeDeferred = async {
                if (settings.alternativeTargetEnabled) {
                    settings.alternativeCoordinate ?: settings.alternativeAddress.takeIf(String::isNotBlank)?.let { geocodeBest(it, region, settings) }
                } else null
            }
            Triple(destinationDeferred.await(), homeDeferred.await(), alternativeDeferred.await())
        }
        val destinationCoordinate = coordinates.first
        val homeCoordinate = coordinates.second
        val alternativeCoordinate = coordinates.third
        val geocodeElapsed = System.currentTimeMillis() - geocodeStartedAt
        LiveFailureTraceStore.recordGeocode(
            label = "destination",
            query = fields.destination.orEmpty(),
            coordinate = destinationCoordinate?.let { "${it.latitude},${it.longitude}" },
            elapsedMillis = geocodeElapsed,
            packageName = currentWindowPackageName(),
            generation = generation,
            screenHash = screenHash,
        )
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return
        LiveFailureTraceStore.recordStep(
            stage = "geocode.targets",
            details = "parallel=true; elapsed=${geocodeElapsed}ms; destination=${destinationCoordinate != null}; home=${homeCoordinate != null}; alternative=${alternativeCoordinate != null}",
            packageName = currentWindowPackageName(),
            generation = generation,
            screenHash = screenHash,
        )

        val exactLowerBound = ExactRadiusLowerBoundPolicy.evaluate(
            destinationCoordinate = destinationCoordinate,
            settings = settings,
            homeCoordinate = homeCoordinate,
            alternativeCoordinate = alternativeCoordinate,
        )
        if (exactLowerBound.definitelyOutside) {
            val fastOutsideResult = AnalysisResult(
                createdAtMillis = System.currentTimeMillis(),
                extractedText = snapshotText,
                fields = fields,
                recommendation = Recommendation.OutsideRadius,
                reason = "Destino certamente fora dos raios: a distancia minima possivel ja ultrapassa o limite configurado.",
            )
            rememberBubbleReason("universal_fast_red", fastOutsideResult.reason)
            // showOverlay(RadarColor.Red, distanceKm = null)
            showOverlay(RadarColor.Red, distanceKm = null, reason = fastOutsideResult.reason)
            traceEvent("universal.fast_red provisional=true exact_route_continues=true")
        } // subsecond_exact_red_lower_bound_0_1_125 fast_red_continues_exact_route_0_1_127

        // val homeRouteStartedAt
        val routeStartedAt = System.currentTimeMillis()
        val routes = coroutineScope {
            val homeDeferred = async { routeDistanceKm(destinationCoordinate, homeCoordinate, settings) }
            val alternativeDeferred = async { routeDistanceKm(destinationCoordinate, alternativeCoordinate, settings) }
            homeDeferred.await() to alternativeDeferred.await()
        }
        val routeElapsed = System.currentTimeMillis() - routeStartedAt
        val homeDistanceKm = routes.first
        val alternativeDistanceKm = routes.second
        LiveFailureTraceStore.recordRoute(
            label = "home",
            distanceKm = homeDistanceKm,
            elapsedMillis = routeElapsed,
            packageName = currentWindowPackageName(),
            generation = generation,
            screenHash = screenHash,
        )
        LiveFailureTraceStore.recordRoute(
            label = "alternative",
            distanceKm = alternativeDistanceKm,
            elapsedMillis = routeElapsed,
            packageName = currentWindowPackageName(),
            generation = generation,
            screenHash = screenHash,
        )
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return

        if (destinationCoordinate != null) {
            universalRouteCache.put(
                cacheKey,
                LiveRideRouteCache.CachedRoute(
                    destinationCoordinate = destinationCoordinate,
                    homeCoordinate = homeCoordinate,
                    alternativeCoordinate = alternativeCoordinate,
                    homeDistanceKm = homeDistanceKm,
                    alternativeDistanceKm = alternativeDistanceKm,
                ),
            )
            scope.launch(Dispatchers.IO) {
                bubblePrefs.edit()
                    .putString("persistent_exact_route_cache_v1", universalRouteCache.exportSnapshot())
                    .apply()
            }
        }

        val result = decisionEngine.decide(
            fields = fields,
            settings = settings,
            destinationCoordinate = destinationCoordinate,
            homeCoordinate = homeCoordinate,
            alternativeCoordinate = alternativeCoordinate,
            fullText = snapshotText,
            homeDistanceKm = homeDistanceKm,
            alternativeDistanceKm = alternativeDistanceKm,
        )
        applyUniversalTwoAddressResult(result, screenHash, addressSignature, generation)
    } // parallel_geocode_route_0_1_128

    private suspend fun applyUniversalTwoAddressResult(
        result: AnalysisResult,
        screenHash: Int,
        addressSignature: String,
        generation: Long,
    ) {
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) {
            traceEvent("universal.result discarded_stale=true")
            return
        }
        val color = when (result.recommendation) {
            Recommendation.GoodRide -> RadarColor.Green
            Recommendation.OutsideRadius -> RadarColor.Red
            Recommendation.InsufficientData -> RadarColor.Default
        }
        val distanceKm = result.nearestConfiguredDistanceKm()
        lastAnalyzedHash = screenHash
        val persistenceSignature = listOf(
            addressSignature,
            result.recommendation.name,
            distanceKm?.let { String.format(Locale.US, "%.3f", it) }.orEmpty(),
        ).joinToString("|")
        val shouldPersistHistory = universalAnalysisDeduper.shouldPersist(persistenceSignature)
        rememberBubbleReason("universal_result", result.reason)
        showOverlay(color, distanceKm)
        traceEvent("universal.result applied color=${color.diagnosticLabel} km=${distanceKm?.toString().orEmpty()} instant=true") // instant_farol_paint_before_history_0_1_124
        if (shouldPersistHistory) {
            scope.launch {
                runCatching { repository.addAnalysis(result) }
                    .onFailure { error ->
                        traceEvent("universal.history async_failure=${error::class.java.simpleName}")
                    }
            }
        } else {
            traceEvent("universal.history duplicate_skipped=true")
        }
    }

    private fun isUniversalResultFresh(
        generation: Long,
        screenHash: Int,
        addressSignature: String,
    ): Boolean =
        serviceReady &&
            currentSettings.appEnabled &&
            currentSettings.liveReadingEnabled &&
            generation == universalScreenGeneration &&
            screenHash == lastSnapshotHash &&
            addressSignature == universalActiveAddressSignature &&
            isUniversalExternalWindowActive() &&
            shouldScanCurrentWindow() // selected_apps_freshness_gate_0_1_122

    private fun hardClearUniversalTwoAddress(reason: String) {
        val hadData = currentRadarColor != RadarColor.Idle ||
            currentDistanceKm != null ||
            lastSnapshotHash != null ||
            universalActiveAddressSignature != null
        if (!hadData && currentRadarColor == RadarColor.Idle) return // universal_clear_idempotent_0_1_106
        val stateChanged = hadData || lastBubbleStateStage != "universal_idle" || lastBubbleStateReason != reason
        LiveFailureTraceStore.recordStep(
            stage = "session.clear",
            details = "reason=$reason; had_data=$hadData; generation_before=$universalScreenGeneration; color=${currentRadarColor.diagnosticLabel}; km=${currentDistanceKm?.toString() ?: "none"}",
            packageName = currentWindowPackageName(),
            generation = universalScreenGeneration,
            screenHash = lastSnapshotHash,
        ) // session_diagnostic_clear_v2
        universalScreenGeneration += 1L
        universalRouteJob?.cancel()
        universalRouteJob = null
        analyzeJob?.cancel()
        analyzeJob = null
        universalActiveAddressSignature = null
        lastSnapshotHash = null
        lastAnalyzedHash = null
        pendingAnalysis = null
        analyzing = false
        currentDistanceKm = null
        lastAccessibilityText = ""
        lastOcrText = ""
        universalAccessibilityOwnsCard = false
        universalLastActiveReadAtMillis = 0L
        universalActiveRidePackageName = null
        universalLiveReadGate.reset()
        registeredCardGate.clear()
        cardExitConfirmationGate.reset()
        primaryCardFocusLock.reset()
        if (stateChanged) {
            clearRuntimeValidationTrigger()
            rememberBubbleReason("universal_idle", reason)
            showOverlay(RadarColor.Idle, distanceKm = null, reason = reason, force = true)
            // showOverlay(RadarColor.Idle, distanceKm = null)
            // universal_immediate_gray_clear_0_1_100
            currentRadarColor = RadarColor.Idle
            currentDistanceKm = null
            overlayView?.let { view ->
                view.text = ""
                view.textSize = bubbleTextSizeSp("")
                view.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(RadarColor.Idle.argb(currentSettings))
                    setStroke(
                        dp(3),
                        Color.argb(
                            (currentSettings.bubbleOpacity.coerceIn(0.25, 1.0) * 255).roundToInt(),
                            255,
                            255,
                            255,
                        ),
                    )
                }
                view.contentDescription = "Rota Certa ${RadarColor.Idle.diagnosticLabel}"
            }
            if (BuildConfig.DEBUG) {
                bubblePrefs.edit()
                    .putString("runtime_validation_state", "cinza|")
                    .putLong("runtime_validation_state_at", System.currentTimeMillis())
                    .apply()
            }
        }
        if (hadData) traceEvent("universal.clear immediate=true reason=$reason")
    } // universal_stable_clear_0_1_101

    private fun universalResolvedForegroundPackage(): String? {
        val resolution = UniversalWindowPackageResolver.resolve(
            rootPackageName = currentRootPackageName(),
            activePackageName = universalForegroundPackageName ?: activePackageName,
            lastExternalPackageName = lastExternalWindowPackageName,
            ownPackageName = this.packageName,
        )
        lastExternalWindowPackageName = resolution.lastExternalPackageName
        return resolution.effectivePackageName
    }

    private fun isUniversalExternalWindowActive(): Boolean {
        if (!serviceReady || !currentSettings.appEnabled || !currentSettings.liveReadingEnabled) return false
        return universalResolvedForegroundPackage()?.let { it != this.packageName } == true
    } // universal_overlay_external_window_guard_0_1_106

    private fun traceUniversalTrigger(source: TextSource, trigger: UniversalAddressTriggerDecision) {
        val now = System.currentTimeMillis()
        val signature = listOf(source.name, trigger.active.toString(), trigger.addressSignature).joinToString("|")
        if (signature == universalLastTriggerTraceSignature && now - universalLastTriggerTraceAtMillis < 1_000L) return
        universalLastTriggerTraceSignature = signature
        universalLastTriggerTraceAtMillis = now
        traceEvent(
            "universal.trigger source=$source addresses=${trigger.addresses.size} active=${trigger.active} destination=${trigger.destination?.take(100).orEmpty()}",
        )
    } // universal_runtime_stability_guard_0_1_101

    private fun looksLikeRegisteredPopupCandidate(text: String): Boolean =
        UniversalAddressTrigger.evaluate(text).active // universal_two_address_candidate_0_1_98

    private fun rememberSourceText(packageName: String?, source: TextSource, text: String) {
        val normalizedPackage = normalizePackageName(packageName)
        if (normalizedPackage != lastTextPackageName) {
            traceEvent("source.reset package=${normalizedPackage.orEmpty()}")
            lastTextPackageName = normalizedPackage
            lastAccessibilityText = ""
            lastOcrText = ""
            lastAccessibilityTextAtMillis = 0L
            lastOcrTextAtMillis = 0L
        }
        val now = System.currentTimeMillis()
        when (source) {
            TextSource.Accessibility -> {
                lastAccessibilityText = text.trim()
                lastAccessibilityTextAtMillis = now
            }
            TextSource.Ocr -> {
                lastOcrText = text.trim()
                lastOcrTextAtMillis = now
            }
        }
    } // universal_source_timestamps_0_1_94

    private fun rememberPopupCandidatePackage(packageName: String?) {
        val normalizedPackage = normalizePackageName(packageName)
        if (normalizedPackage != lastTextPackageName) {
            Unit
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

    private suspend fun saveCapturedReadToHistory(
        text: String,
        fields: RideFields,
        snapshotHash: Int,
        reason: String,
    ) = Unit

    private suspend fun saveCapturedCardScreen(
        text: String,
        fields: RideFields,
        snapshotHash: Int,
        parserName: String,
        packageName: String?,
    ) = Unit

    private fun invalidateLiveAnalysis(reason: String) {
        val previousJob = liveAnalysisJob
        if (previousJob?.isActive == true) {
            traceEvent("analysis.latest_card_wins cancel reason=$reason serial=$analysisSerial") // latest_card_wins_invalidate_0_1_91
        }
        analysisSerial += 1L
        previousJob?.cancel()
        liveAnalysisJob = null
        pendingAnalysis = null
        coreCardAnalysisCoalescer.invalidate() // same_card_coalesce_invalidate_0_1_93
        analyzing = false
    }

    private suspend fun analyzeLiveText(
        text: String,
        fields: RideFields,
        snapshotHash: Int,
        cardMatch: RideCardTemplateMatch?,
        allowPopupCandidate: Boolean = false,
        analysisToken: Long = analysisSerial,
        analysisCardSignature: String? = lastVisibleCardSignature,
    ) {
        if (!serviceReady || (!allowPopupCandidate && !shouldScanCurrentWindow())) return
        analyzing = true
        traceEvent("analysis.latest_card_wins start token=$analysisToken hash=$snapshotHash") // latest_card_wins_analysis_start_0_1_91
        Unit
        currentSettings = repository.settings.first()
        try {
            val settings = currentSettings
            val region = DeviceRegion(country = "Brasil")
            val destinationCoordinate = fields.destination?.let { geocodeBest(it, region, settings) }
            Unit
            val homeCoordinate = if (settings.homeTargetEnabled) settings.homeCoordinate ?: geocodeBest(settings.homeAddress, region, settings) else null // functional_bubble_target_gate_0_1_95
            val alternativeCoordinate = if (settings.alternativeTargetEnabled) settings.alternativeCoordinate ?: geocodeBest(settings.alternativeAddress, region, settings) else null
            Unit
            val quickResult = decisionEngine.decide(
                fields = fields,
                settings = settings,
                destinationCoordinate = destinationCoordinate,
                homeCoordinate = homeCoordinate,
                alternativeCoordinate = alternativeCoordinate,
                fullText = text,
            )
            if (quickResult.recommendation != Recommendation.InsufficientData) {
                val quickColor = when (quickResult.recommendation) {
                    Recommendation.GoodRide -> RadarColor.Green
                    Recommendation.OutsideRadius -> RadarColor.Red
                    Recommendation.InsufficientData -> RadarColor.Default
                }
                Unit
                showOverlay(color = quickColor, distanceKm = quickResult.nearestConfiguredDistanceKm())
                Unit
            }
            val homeDistanceKm = routeDistanceKm(destinationCoordinate, homeCoordinate, settings)
            val alternativeDistanceKm = routeDistanceKm(destinationCoordinate, alternativeCoordinate, settings)
            Unit

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
            Unit
            val analyzedCardSignature = cardMatch?.let { match ->
                buildVisibleCardSignature(lastTextPackageName ?: currentWindowPackageName(), fields, match)
            }
            if (analyzedCardSignature != null &&
                lastVisibleCardSignature != null &&
                analyzedCardSignature != lastVisibleCardSignature
            ) {
                lastAnalyzedHash = snapshotHash
                traceEvent("analysis.discard stale_card analyzed=$analyzedCardSignature visible=$lastVisibleCardSignature") // stale_result_guard_0_1_83
                return
            }
            lastSavedReadHash = snapshotHash
            if (!allowPopupCandidate && !shouldScanCurrentWindow()) {
                registeredCardGate.clear()
                lastVisibleCardSignature = null // bubble_render_stability_clear_signature_0_1_81
                resetToDefaultForNonRideScreen(
                    reason = "A tela saiu do card/app monitorado antes de aplicar a decisao.",
                    record = false,
                )
                Unit
                return
            }
            if (allowPopupCandidate && !looksLikeRegisteredPopupCandidate(collectVisibleText(allowPopupCandidate = true))) {
                registeredCardGate.clear()
                lastVisibleCardSignature = null // bubble_render_stability_clear_signature_0_1_81
                resetToDefaultForNonRideScreen(
                    reason = "O pop-up de corrida nao esta mais visivel; bolinha voltou para cinza.",
                    record = false,
                )
                return
            }

            if (!allowPopupCandidate && snapshotHash != lastSnapshotHash) {
                registeredCardGate.clear()
                lastVisibleCardSignature = null // bubble_render_stability_clear_signature_0_1_81
                if (shouldScanCurrentWindow()) {
                    resetToDefault("Analise antiga ignorada porque a tela mudou antes do resultado.", record = false)
                } else {
                    resetToIdle("Analise antiga ignorada porque o card/app saiu da tela.", record = false)
                }
                return
            }
            lastAnalyzedHash = snapshotHash // analysis_hash_bound_to_transaction_0_1_83
            val coreClassificationForBubble = br.com.mapeiaia.rotacerta.core.RotaCertaCore.classifyScreen(
                packageName = currentWindowPackageName(),
                text = text,
                fields = fields,
            )
            val coreBubbleDecision = br.com.mapeiaia.rotacerta.core.CoreBubbleDecisionEngine.fromAnalysis(
                classification = coreClassificationForBubble,
                result = result,
                distanceKm = result.nearestConfiguredDistanceKm(),
            )
            val computedRadarColor = when (coreBubbleDecision.mode) {
                br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Good -> RadarColor.Green
                br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Bad -> RadarColor.Red
                br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Waiting -> RadarColor.Default
                br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Hidden -> RadarColor.Idle
            }
            val keepActiveDecisionForTransientInsufficient = false // global_no_transient_decision_keep_0_1_124
            if (keepActiveDecisionForTransientInsufficient) {
                traceEvent("core.bubble transient_keep mode=${coreBubbleDecision.mode} hash=$snapshotHash reason=${coreBubbleDecision.reason}") // core_bubble_decision_0_1_88
                recordDiagnostic(
                    stage = "analysis_result",
                    reason = "Core classificou leitura transitoria/insuficiente dentro do app monitorado; mantive a decisao verde/vermelha anterior ate confirmar novo card real.",
                    text = text,
                    fields = fields,
                    result = result,
                    cardTemplateMatch = cardMatch,
                )
            } else {
                val radarColor = computedRadarColor
                val corePipelineDecision = coreLivePipeline.decisionReady(
                transaction = coreLivePipeline.transactionFor(snapshotHash) ?: coreLivePipeline.readReady(coreLivePipeline.begin(packageName, "analysis", text.length, allowPopupCandidate), snapshotHash, text.length),
                recommendation = result.recommendation,
                distanceKm = coreBubbleDecision.distanceKm,
            )
            traceEvent("core.pipeline.decision ${corePipelineDecision.traceSummary()}") // core_live_pipeline_decision_0_1_96
            traceEvent("core.bubble apply mode=${coreBubbleDecision.mode} color=${radarColor.diagnosticLabel} distance=${coreBubbleDecision.distanceKm?.toString() ?: "null"} reason=${coreBubbleDecision.reason}") // core_bubble_decision_0_1_88
                val coreFreshnessDecision = br.com.mapeiaia.rotacerta.core.CoreFreshnessGuard.evaluate(
                transaction = coreLivePipeline.transactionFor(snapshotHash),
                currentPackageName = packageName,
                currentSnapshotHash = snapshotHash,
                currentVisibleCardSignature = lastVisibleCardSignature,
            )
            if (!coreFreshnessDecision.fresh) {
                traceEvent("core.freshness stale reason=${coreFreshnessDecision.reason}") // core_freshness_guard_0_1_97
                recordDiagnostic(
                    stage = "stale_result",
                    reason = coreFreshnessDecision.reason,
                    text = text,
                    fields = fields,
                    result = result,
                    cardTemplateMatch = cardMatch,
                )
                return
            }
            traceEvent("core.freshness fresh reason=${coreFreshnessDecision.reason}") // core_freshness_guard_0_1_97
            val corePipelineVisual = coreLivePipeline.visualApplied(
                transaction = coreLivePipeline.transactionFor(snapshotHash) ?: coreLivePipeline.readReady(coreLivePipeline.begin(packageName, "analysis", text.length, allowPopupCandidate), snapshotHash, text.length),
                mode = when (radarColor) {
                    RadarColor.Green -> br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Good
                    RadarColor.Red -> br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Bad
                    RadarColor.Default -> br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Waiting
                    RadarColor.Idle -> br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Hidden
                },
            )
            traceEvent("core.pipeline.visual ${corePipelineVisual.traceSummary()}") // core_live_pipeline_visual_0_1_96
            if (analysisToken != analysisSerial || !coreCardAnalysisCoalescer.isCurrent(analysisCardSignature)) { // same_card_coalesce_current_guard_0_1_93
                traceEvent("analysis.drop_stale_result phase=visual token=$analysisToken current=$analysisSerial hash=$snapshotHash") // latest_card_wins_drop_before_visual_0_1_91
                return
            }
            showOverlay(color = radarColor, distanceKm = coreBubbleDecision.distanceKm)
            if (radarColor == RadarColor.Green || radarColor == RadarColor.Red) {
                coreCardAnalysisCoalescer.complete(analysisCardSignature)
            } else {
                coreCardAnalysisCoalescer.finish(analysisCardSignature)
            } // same_card_coalesce_visual_complete_0_1_93
                recordDiagnostic(
                    stage = "analysis_result",
                    color = radarColor,
                    reason = coreBubbleDecision.reason,
                    text = text,
                    fields = fields,
                    result = result,
                    cardTemplateMatch = cardMatch,
                )
            }

        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error // latest_card_wins_cancel_rethrow_0_1_91
            if (analysisToken != analysisSerial || !coreCardAnalysisCoalescer.isCurrent(analysisCardSignature)) { // same_card_coalesce_current_guard_0_1_93
                traceEvent("analysis.drop_stale_result phase=error token=$analysisToken current=$analysisSerial hash=$snapshotHash")
                return
            }
            Unit
            showOverlay(RadarColor.Default)
            Unit
        } finally {
            if (analysisToken == analysisSerial) {
                analyzing = false
                liveAnalysisJob = null
                coreCardAnalysisCoalescer.finish(analysisCardSignature)
            } // latest_card_wins_finish_0_1_91 same_card_coalesce_finish_0_1_93
            Unit
            val pending = pendingAnalysis
            pendingAnalysis = null
            if (pending != null && pending.snapshotHash != lastAnalyzedHash && (pending.allowPopupCandidate || shouldScanCurrentWindow())) {
                Unit
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

    private suspend fun geocodeBest(query: String, region: DeviceRegion, settings: AppSettings): Coordinate? {
        liveGeocodeCache.get(query)?.let { return it }
        val apiKey = settings.googleMapsApiKey.ifBlank { BuildConfig.GOOGLE_MAPS_API_KEY }
        val coordinate = googleMapsService.geocode(query, region, apiKey) ?: geocodingService.geocode(query, region)
        if (coordinate != null) {
            liveGeocodeCache.put(query, coordinate)
            scope.launch(Dispatchers.IO) {
                bubblePrefs.edit()
                    .putString("persistent_geocode_cache_v1", liveGeocodeCache.exportSnapshot())
                    .apply()
            }
        }
        return coordinate
    } // universal_two_address_geocode_0_1_98 geocode_cache_0_1_128

    private suspend fun routeDistanceKm(
        origin: Coordinate?,
        destination: Coordinate?,
        settings: AppSettings,
    ): Double? {
        val apiKey = settings.googleMapsApiKey.ifBlank { BuildConfig.GOOGLE_MAPS_API_KEY }
        return if (origin != null && destination != null && apiKey.isNotBlank()) {
            googleMapsService.drivingDistanceKm(origin, destination, apiKey)
        } else null
    } // universal_two_address_route_0_1_98

    private fun AnalysisResult.nearestConfiguredDistanceKm(): Double? =
        listOfNotNull(pickupToHomeKm, pickupToAlternativeKm).minOrNull()

    private fun buildVisibleCardSignature(
        packageName: String?,
        fields: RideFields,
        cardMatch: RideCardTemplateMatch,
    ): String = listOf(
        normalizePackageName(packageName).orEmpty(),
        cardMatch.template.id,
        fields.destination.stableSignaturePart(),
        fields.fare.stableSignaturePart(),
    ).joinToString("|")

    private fun String?.stableSignaturePart(): String =
        this.orEmpty()
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase(Locale.ROOT)

    private fun hasActiveRegisteredDecision(): Boolean =
        (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red) &&
            registeredCardGate.hasSeenRecently(DECISION_OVERLAY_STICKY_MS)

    private fun rememberBubbleReason(stage: String, reason: String) {
        lastBubbleStateStage = stage
        lastBubbleStateReason = reason
    }

    private fun persistBubbleState() {
        val now = System.currentTimeMillis()
        bubblePrefs.edit()
            .putLong(KEY_STATE_UPDATED_AT, now)
            .putString(KEY_STATE_STAGE, lastBubbleStateStage)
            .putString(KEY_STATE_REASON, lastBubbleStateReason)
            .putString(KEY_STATE_COLOR, currentRadarColor.diagnosticLabel)
            .putString(KEY_STATE_DISTANCE_KM, currentDistanceKm?.let { value -> String.format(Locale("pt", "BR"), "%.1fkm", value) }.orEmpty())
            .putString(KEY_STATE_WINDOW_PACKAGE, currentWindowPackageName().orEmpty())
            .putString(KEY_STATE_ACTIVE_PACKAGE, activePackageName.orEmpty())
            .putString(KEY_STATE_TEXT_PACKAGE, lastTextPackageName.orEmpty())
            .putString(KEY_STATE_LAST_SNAPSHOT_HASH, lastSnapshotHash?.toString().orEmpty())
            .putString(KEY_STATE_LAST_ANALYZED_HASH, lastAnalyzedHash?.toString().orEmpty())
            .putString(KEY_STATE_PENDING_HASH, pendingAnalysis?.snapshotHash?.toString().orEmpty())
            .putBoolean(KEY_STATE_SERVICE_READY, serviceReady)
            .putBoolean(KEY_STATE_ANALYZING, analyzing)
            .putInt(KEY_STATE_ACCESSIBILITY_TEXT_LENGTH, lastAccessibilityText.length)
            .putString(KEY_STATE_ACCESSIBILITY_TEXT_HASH, lastAccessibilityText.takeIf { it.isNotBlank() }?.snapshotHash()?.toString().orEmpty())
            .putInt(KEY_STATE_OCR_TEXT_LENGTH, lastOcrText.length)
            .putString(KEY_STATE_OCR_TEXT_HASH, lastOcrText.takeIf { it.isNotBlank() }?.snapshotHash()?.toString().orEmpty())
            .putInt(KEY_STATE_TEMPLATE_COUNT, 0)
            .apply()
    }

    private fun resetToDefault(
        reason: String,
        text: String? = null,
        fields: RideFields? = null,
        record: Boolean = true,
    ) {
        invalidateLiveAnalysis("reset_default:$reason") // latest_card_wins_reset_default_0_1_91
        val visibleCardClearEvent = coreVisibleCardLifecycle.clear(reason)
        lastVisibleCardSignature = null
        traceEvent("core.visible_card clear action=${visibleCardClearEvent.action} previous=${visibleCardClearEvent.previousSignature ?: "null"} reason=${visibleCardClearEvent.reason}") // core_visible_card_clear_0_1_95
        lastSnapshotHash = null
        lastAnalyzedHash = null
        registeredCardGate.clear()
        lastVisibleCardSignature = null // bubble_render_stability_clear_signature_0_1_81
        clearRememberedRideText()
        val hardClearUnregisteredCardDefault = reason.contains("ainda nao bate com nenhum card cadastrado", ignoreCase = true) ||
            reason.contains("cadastre o modelo para liberar o farol", ignoreCase = true)
        if (hardClearUnregisteredCardDefault) {
            lastDecisionOverlayAtMillis = 0L
            registeredCardGate.clear()
            lastVisibleCardSignature = null // bubble_render_stability_clear_signature_0_1_81
        }
        rememberBubbleReason("default", reason)
        val coreState = coreBubbleState.waiting(reason) // core_bubble_state_reset_default_0_1_91
        traceEvent("core.state waiting changed=${coreState.changed} reason=${coreState.reason}")
        showOverlay(RadarColor.Default, coreState.distanceKm)
        if (record) {
            Unit
        }
    }

    private fun resetToDefaultForNonRideScreen(reason: String, record: Boolean = false) {
        resetToIdle(reason = reason, record = record)
    }

    private fun resetStaleRegisteredCardDecision() {
        val hasDecisionColor = currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red
        if (registeredCardGate.shouldResetStale(hasDecisionColor)) {
            registeredCardGate.clear()
            lastVisibleCardSignature = null // bubble_render_stability_clear_signature_0_1_81
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
        invalidateLiveAnalysis("reset_idle:$reason") // latest_card_wins_reset_idle_0_1_91
        val visibleCardClearEvent = coreVisibleCardLifecycle.clear(reason)
        lastVisibleCardSignature = null
        traceEvent("core.visible_card clear action=${visibleCardClearEvent.action} previous=${visibleCardClearEvent.previousSignature ?: "null"} reason=${visibleCardClearEvent.reason}") // core_visible_card_clear_0_1_95
        Unit // global_idle_never_guarded_0_1_124
        lastSnapshotHash = null
        lastAnalyzedHash = null
        registeredCardGate.clear()
        lastVisibleCardSignature = null // bubble_render_stability_clear_signature_0_1_81
        clearRememberedRideText()
        rememberBubbleReason("idle", reason)
        val coreState = coreBubbleState.hidden(reason) // core_bubble_state_reset_idle_0_1_91
        traceEvent("core.state hidden changed=${coreState.changed} reason=${coreState.reason}")
        showOverlay(RadarColor.Idle, coreState.distanceKm)
        if (record) {
            Unit
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
                Unit
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
                    textPreview = text.trim().take(CARD_TEXT_PREVIEW_LIMIT),
                    parserName = parseResult.parserName,
                    pickup = parseResult.fields.pickup,
                    destination = parseResult.fields.destination,
                    fare = parseResult.fields.fare,
                ),
            )
            toast("Card de corrida salvo.")
            Unit
        }
    }

    private fun clearRememberedRideText() {
        pendingAnalysis = null
        lastTextPackageName = null
        lastAccessibilityText = ""
        lastOcrText = ""
    }

    private fun openDecisionAddressSettingsFromBubble() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_OPEN_TAB, TAB_TOOLS),
            )
        }.onFailure {
            toast("Nao consegui abrir a regiao do farol agora.")
        }
    }

    private fun saveCurrentPlaceFromBubble(type: SavedPlaceType, defaultName: String = if (type == SavedPlaceType.ProximityAlert) "Alerta" else "Local salvo") {
        scope.launch {
            val coordinate = locationService.currentCoordinate()
            if (coordinate == null) {
                toast("Autorize a localizacao para salvar este local.")
                Unit
                return@launch
            }

            val resolved = gpsAddressResolver.resolve(coordinate)
            val createdAt = System.currentTimeMillis()
            val isAlert = type == SavedPlaceType.ProximityAlert
            val place = SavedPlace(
                id = "place-$createdAt-${coordinate.latitude}-${coordinate.longitude}",
                name = defaultName,
                type = type,
                address = resolved.addressLine,
                coordinate = coordinate,
                alertDistanceMeters = if (isAlert) currentSettings.proximityAlertDistanceMeters else null,
                createdAtMillis = createdAt,
            )
            repository.addSavedPlace(place)
            openSavedPlaceEditor(place)
            toast(if (isAlert) "Alerta criado. Informe o nome." else "Local salvo. Informe o nome.")
            Unit
        }
    }

    private fun collectVisibleTextForAction(): String {
        val root = rootInActiveWindow ?: return ""
        val lines = mutableListOf<String>()
        collectNodeText(root, lines)
        return lines.map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString("\n")
    }

    private fun shouldScanCurrentWindow(): Boolean = shouldScanPackage(currentWindowPackageName())

    private fun isOwnAppMainWindowVisible(): Boolean {
        val root = rootInActiveWindow ?: return false
        if (normalizePackageName(root.packageName?.toString()) != this.packageName) return false
        val lines = mutableListOf<String>()
        collectNodeText(root, lines)
        val normalized = lines.joinToString("\n")
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()
        val strongMarkers = listOf(
            "leitura ao vivo",
            "ferramentas",
            "configuracoes",
            "configurações",
            "modelos de cards",
            "assinaturas de cards",
            "casa/ponto principal",
            "alertas de proximidade",
            "gerar relatorio",
            "gerar relatório",
            "definir regiao de trabalho",
            "definir região de trabalho",
            "aparencia da bolinha",
            "aparência da bolinha",
            "rota certa ligado",
        )
        return root.childCount > 0 && normalized.length >= 40 && strongMarkers.any { marker -> marker in normalized }
    }

    private fun currentWindowPackageName(): String? {
        val resolution = UniversalWindowPackageResolver.resolve(
            rootPackageName = currentRootPackageName(),
            activePackageName = universalForegroundPackageName ?: activePackageName,
            lastExternalPackageName = lastExternalWindowPackageName,
            ownPackageName = this.packageName,
        )
        lastExternalWindowPackageName = resolution.lastExternalPackageName
        return resolution.effectivePackageName
    } // universal_overlay_window_resolver_0_1_106

    private fun currentRootPackageName(): String? =
        normalizePackageName(rootInActiveWindow?.packageName?.toString())

    private fun shouldScanPackage(packageName: String?): Boolean {
        val normalized = normalizePackageName(packageName) ?: return false
        if (!serviceReady || !currentSettings.appEnabled || !currentSettings.liveReadingEnabled) return false
        val passiveProbeSettings = currentSettings.copy(
            restrictToSelectedRideApps = false,
            monitor99 = false,
            monitorUber = false,
            monitorInDrive = false,
            extraMonitoredPackages = "",
        )
        val classification = br.com.mapeiaia.rotacerta.core.CorePackageMonitor.classify(
            packageName = normalized,
            ownPackageName = this.packageName,
            settings = passiveProbeSettings,
        )
        val selectedPackages = SelectedRideAppStore.read(applicationContext)
        return classification.canScan && normalized in selectedPackages
    } // universal_package_content_gate_0_1_126 manual_selected_apps_gate_0_1_127
 // universal_package_content_gate_0_1_126

    private fun selectedRidePackages(settings: AppSettings): Set<String> {
        @Suppress("UNUSED_VARIABLE")
        val ignoredLegacySettings = settings
        return SelectedRideAppStore.read(applicationContext)
    } // manual_selected_packages_diagnostic_0_1_127
 // universal_no_packages_v2_0_1_95 manual_selected_packages_anchor_0_1_127

    private fun scanBlockReason(packageName: String?): String {
        val normalized = normalizePackageName(packageName)
            ?: return "Pacote ativo nao informado pelo Android."
        val passiveProbeSettings = currentSettings.copy(
            restrictToSelectedRideApps = false,
            monitor99 = false,
            monitorUber = false,
            monitorInDrive = false,
            extraMonitoredPackages = "",
        )
        val classification = br.com.mapeiaia.rotacerta.core.CorePackageMonitor.classify(
            packageName = normalized,
            ownPackageName = this.packageName,
            settings = passiveProbeSettings,
        )
        if (!classification.canScan) return classification.reason
        return if (normalized !in SelectedRideAppStore.read(applicationContext)) {
            "Aplicativo nao selecionado pelo usuario: $normalized."
        } else {
            classification.reason
        }
    } // manual_selected_apps_reason_0_1_127
 // manual_scan_reason_anchor_0_1_127

    private fun recordDiagnostic(
        stage: String,
        color: RadarColor? = null,
        reason: String,
        text: String? = null,
        fields: RideFields? = null,
        result: AnalysisResult? = null,
        error: Throwable? = null,
        cardTemplateMatch: RideCardTemplateMatch? = null,
    ) = Unit

    private fun traceEvent(message: String) {
        if (!currentSettings.diagnosticsEnabled || !DiagnosticRuntimeGate.isEnabled()) return
        LiveFailureTraceStore.recordTrace(
            message = message,
            packageName = currentWindowPackageName(),
            generation = universalScreenGeneration,
            screenHash = lastSnapshotHash,
        ) // session_diagnostic_trace_v2

        if (message.startsWith("event passive ignored")) {
            val now = System.currentTimeMillis()
            val passiveKey = message.substringBefore(" reason=")
            if (passiveKey == lastPassiveTraceKey && now - lastPassiveTraceAtMillis < 1_500L) return
            lastPassiveTraceKey = passiveKey
            lastPassiveTraceAtMillis = now
        }
        DiagnosticLogStore.record("bubble", message)
    }

    private fun String.withDiagnosticEvents(): String = this



    private fun isPassiveDiagnosticPackage(packageName: String?): Boolean =
        br.com.mapeiaia.rotacerta.core.CorePackageMonitor.isPassive(
            packageName = packageName,
            ownPackageName = this.packageName,
        ) // core_package_monitor_passive_0_1_93

    private fun isPassiveIgnoredPackage(packageName: String?): Boolean =
        br.com.mapeiaia.rotacerta.core.CorePackageMonitor.isPassive(
            packageName = packageName,
            ownPackageName = this.packageName,
        ) // final_passive_ignored_dedup_0_1_93

    private fun normalizePackageName(packageName: String?): String? =
        packageName?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }

    private fun showOverlay(
        color: RadarColor,
        distanceKm: Double? = null,
        reason: String = lastBubbleStateReason,
        force: Boolean = false,
    ) {
        if (!serviceReady) return
        val manager = windowManager ?: return
        val requestedMode = when (color) {
            RadarColor.Green -> br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Good
            RadarColor.Red -> br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Bad
            RadarColor.Default -> br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Waiting
            RadarColor.Idle -> br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Hidden
        }
        val renderDecision = bubbleRenderCoordinator.request(
            generation = universalScreenGeneration,
            mode = requestedMode,
            distanceKm = distanceKm,
            reason = reason,
            force = force,
        )
        if (!renderDecision.accepted && overlayView != null) {
            traceEvent("bubble.render rejected=${renderDecision.reason} mode=$requestedMode")
            return
        }
        val renderedColor = when (renderDecision.state.mode) {
            br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Good -> RadarColor.Green
            br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Bad -> RadarColor.Red
            br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Waiting -> RadarColor.Default
            br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Hidden -> RadarColor.Idle
        }
        val renderedDistance = renderDecision.state.distanceKm
        val now = System.currentTimeMillis()
        if (renderedColor == RadarColor.Green || renderedColor == RadarColor.Red) lastDecisionOverlayAtMillis = now
        val nextText = formatBubbleDistanceKm(renderedDistance)
        if (!force && currentRadarColor == renderedColor && currentDistanceKm == renderedDistance && overlayView?.text?.toString() == nextText) return

        val coreRenderState = coreBubbleState.render(
            mode = renderDecision.state.mode,
            distanceKm = renderedDistance,
            reason = reason,
        ) // core_bubble_state_render_0_1_91
        currentRadarColor = renderedColor
        currentDistanceKm = coreRenderState.distanceKm
        persistBubbleState()
        val view = overlayView ?: TextView(this).also { newView ->
            val params = overlayLayoutParams()
            newView.contentDescription = "Rota Certa"
            newView.gravity = Gravity.CENTER
            newView.includeFontPadding = false
            newView.setTextColor(Color.BLACK)
            newView.setTypeface(Typeface.DEFAULT_BOLD)
            newView.setOnClickListener { toggleResourceShortcuts() }
            newView.setOnTouchListener(BubbleTouchListener())
            if (!runCatching { manager.addView(newView, params) }.isSuccess) return
            overlayView = newView
            overlayParams = params
        }
        val presentation = coreBubblePresenter.present(renderDecision.state.mode, currentDistanceKm)
        traceEvent("core.presenter mode=${presentation.mode} text=${presentation.text}") // core_bubble_presenter_0_1_90
        view.text = presentation.text
        view.textSize = presentation.textSizeSp
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(currentRadarColor.argb(currentSettings))
            setStroke(
                dp(3),
                Color.argb(
                    (currentSettings.bubbleOpacity.coerceIn(0.25, 1.0) * 255).roundToInt(),
                    255,
                    255,
                    255,
                ),
            )
        }
        view.contentDescription = buildString {
            append("Rota Certa ")
            append(currentRadarColor.diagnosticLabel)
            currentDistanceKm?.let { append(" ").append(formatBubbleDistanceKm(it)).append(" km") }
        }
        publishRuntimeValidationState(currentRadarColor, currentDistanceKm)
        overlayView?.contentDescription = buildString {
            append("Rota Certa ")
            append(color.diagnosticLabel)
            currentDistanceKm?.let { append(" ").append(formatBubbleDistanceKm(it)).append(" km") }
        } // universal_overlay_runtime_metadata_0_1_98

    } // single_bubble_render_coordinator_0_1_128

    private fun formatBubbleDistanceKm(distanceKm: Double?): String = when {
        distanceKm == null -> ""
        distanceKm < 1.0 -> String.format(Locale("pt", "BR"), "%.1f", distanceKm).removeSuffix(",0")
        else -> String.format(Locale("pt", "BR"), "%.1f", distanceKm).removeSuffix(",0")
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
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
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

    private fun clearClipboardFromBubble() {
        hideActionMenu()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
            }
        }.onSuccess {
            Unit
            toast("Area de transferencia limpa.")
        }.onFailure { error ->
            Unit
            toast("Nao foi possivel limpar a area de transferencia.")
        }
    }

    private fun openApp(tab: String? = null, expander: String? = null) {
        hideActionMenu()
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (tab != null) intent.putExtra(EXTRA_OPEN_TAB, tab)
        runCatching { startActivity(intent) }
            .onFailure {
                toast("Nao consegui abrir o Rota Certa agora.")
            }
    }

    private fun persistResourceShortcutState() {
        val visible = shortcutOverlayController.shortcutsVisible
        val labels = BubbleShortcutCatalog.modules.joinToString("|") { it.spec.label }
        bubblePrefs.edit()
            .putBoolean(KEY_RUNTIME_SHORTCUTS_OPEN, visible)
            .putInt(KEY_RUNTIME_SHORTCUT_COUNT, if (visible) BubbleShortcutCatalog.modules.size else 0)
            .putString(KEY_RUNTIME_SHORTCUT_LABELS, if (visible) labels else "")
            .apply()
    }

    private fun closeResourceShortcuts() {
        shortcutOverlayController.hideShortcuts()
        persistResourceShortcutState()
    }

    private fun toggleResourceShortcuts() {
        val params = overlayParams ?: return
        shortcutOverlayController.toggleShortcuts(anchor = params, onShortcut = ::executeShortcutModule)
        persistResourceShortcutState()
        traceEvent("bubble.shortcuts.toggle visible=" + shortcutOverlayController.shortcutsVisible)
        DiagnosticLogStore.record("bubble_action", "shortcuts visible=" + shortcutOverlayController.shortcutsVisible)
    }

    private fun executeShortcutModule(spec: BubbleShortcutSpec) {
        traceEvent("bubble.shortcut.execute id=" + spec.id)
        DiagnosticLogStore.record("bubble_action", "shortcut id=" + spec.id + " label=" + spec.label)
        when (spec.action) {
            BubbleShortcutAction.OpenRoute,
            BubbleShortcutAction.OpenDestination,
            BubbleShortcutAction.OpenAlerts,
            BubbleShortcutAction.OpenSavedPlaces,
            BubbleShortcutAction.OpenRadars,
            BubbleShortcutAction.OpenAppearance,
            BubbleShortcutAction.OpenPermissions,
            BubbleShortcutAction.OpenBackup,
            BubbleShortcutAction.OpenReports,
            BubbleShortcutAction.OpenCards,
            BubbleShortcutAction.OpenSettings,
            -> openResourceGroup(requireNotNull(spec.targetGroup), requireNotNull(spec.targetTab))

            BubbleShortcutAction.OpenScreenWhatsApp -> capturePhoneAndOpenWhatsApp118()
            BubbleShortcutAction.OpenCollector -> openCollectorFromBubble()
            BubbleShortcutAction.ClearClipboard -> clearClipboardFromBubble()
            BubbleShortcutAction.ExportDiagnostic -> exportDiagnosticFromBubble()
            BubbleShortcutAction.StopApplication -> stopApplicationFromBubble()
            BubbleShortcutAction.CreateAlert -> saveCurrentPlaceFromBubble(SavedPlaceType.ProximityAlert, requireNotNull(spec.defaultName))
            BubbleShortcutAction.CreateSavedPlace -> saveCurrentPlaceFromBubble(SavedPlaceType.Place, requireNotNull(spec.defaultName))
            BubbleShortcutAction.SaveRideCard -> saveCurrentRideCardFromBubble()
            BubbleShortcutAction.ToggleReading -> toggleLiveReadingFromBubble()
        }
    }

    private fun openCollectorFromBubble() {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        runCatching {
            startActivity(
                Intent(this@LiveRideAccessibilityService, BlaBlaCarCollectorActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { toast("Nao foi possivel abrir o Coletor.") }
    }

    private fun exportDiagnosticFromBubble() {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        DiagnosticLogStore.record("bubble_action", "diagnostic export requested")
        runCatching {
            startActivity(
                Intent(this@LiveRideAccessibilityService, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_OPEN_TAB, TAB_HISTORY)
                    .putExtra(EXTRA_OPEN_BUBBLE_GROUP, "reports")
                    .putExtra("auto_export_report", true),
            )
        }.onFailure { toast("Nao foi possivel abrir a exportacao do relatorio.") }
    }

    private fun toggleLiveReadingFromBubble() {
        shortcutOverlayController.hideShortcuts()
        persistResourceShortcutState()
        val enabled = !currentSettings.liveReadingEnabled
        val updated = currentSettings.copy(liveReadingEnabled = enabled)
        currentSettings = updated
        scope.launch { runCatching { repository.saveSettings(updated) } }
        if (enabled) {
            showOverlay(RadarColor.Default)
            scheduleVisibleTextAnalysis(delayMs = 0L)
            requestScreenshotAnalysis()
        } else {
            analyzeJob?.cancel()
            analyzeJob = null
            screenshotInProgress.set(false)
            lastAccessibilityText = ""
            lastOcrText = ""
            resetToIdle("Leitura pausada pelo atalho da bolinha.", record = false)
        }
        val message = if (enabled) "Leitura ao vivo ATIVADA" else "Leitura ao vivo PAUSADA"
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        overlayView?.announceForAccessibility(message)
        bubblePrefs.edit().putString("runtime_reading_status", if (enabled) "active" else "paused").apply()
        traceEvent("bubble.reading.toggle enabled=" + enabled + " feedback=long")
        DiagnosticLogStore.record("bubble_action", "reading enabled=" + enabled + " feedback=visible")
    } // reading_visible_feedback_0_1_120

    private fun stopApplicationFromBubble() {
        val updated = currentSettings.copy(
            appEnabled = false,
            liveReadingEnabled = false,
            proximityAlertsEnabled = false,
        )
        currentSettings = updated
        analyzeJob?.cancel()
        analyzeJob = null
        screenshotInProgress.set(false)
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        traceEvent("bubble.stop.requested open_app_details=true")
        DiagnosticLogStore.record("bubble_action", "stop requested; reading=false alerts=false")
        scope.launch {
            runCatching { repository.saveSettings(updated) }
            toast("Rota Certa pausado. Confirme Forcar interrupcao para encerrar totalmente.")
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure {
                startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            delay(220L)
            removeOverlay()
            disableSelf()
        }
    }

    private fun openResourceGroup(group: String, tab: String) {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_OPEN_TAB, tab)
                    .putExtra(EXTRA_OPEN_BUBBLE_GROUP, group),
            )
        }
    }

    private fun showImportedRadarPopup(radar: ImportedRadar, distanceMeters: Double) {
        if (activeRadarPopupId != radar.id) {
            activeRadarPopupId = radar.id
            radarDetectionCue.play()
        }
        shortcutOverlayController.showImportedRadarAlert(
            radar = radar,
            distanceMeters = distanceMeters,
            firstAlertDistanceMeters = currentSettings.proximityAlertDistanceMeters,
        )
        persistResourceShortcutState()
        traceEvent("imported_radar.signal popup_updated=true id=${radar.id} distance=${distanceMeters.roundToInt()}")
    }

    private fun showSavedAlertPopup(
        alert: SavedPlace,
        distanceMeters: Double,
        firstAlertDistanceMeters: Int = currentSettings.proximityAlertDistanceMeters,
    ) {
        shortcutOverlayController.showOrUpdateProximityAlert(
            alert = alert,
            distanceMeters = distanceMeters,
            firstAlertDistanceMeters = firstAlertDistanceMeters,
            actions = ProximityAlertPopupActions(
                onEdit = ::openSavedPlaceEditor,
                onDelete = { place ->
                    scope.launch {
                        repository.removeSavedPlace(place.id)
                        shortcutOverlayController.hideProximityAlert(place.id)
                        toast("Alerta excluido.")
                        traceEvent("proximity.popup.deleted id=" + place.id)
                    }
                },
            ),
        )
        persistResourceShortcutState()
    } // proximity_auto_close_runtime_0_1_128

    private fun capturePhoneAndOpenWhatsApp118() {
        if (!phoneCaptureInProgress118.compareAndSet(false, true)) return
        DiagnosticLogStore.record("bubble_action", "whatsapp.capture.started")

        val directTarget = ScreenPhoneLink.findBest(collectVisibleTextForAction())
            ?: ScreenPhoneLink.findBest(mergeRideTexts(lastAccessibilityText, lastOcrText))
        if (directTarget != null) {
            phoneCaptureInProgress118.set(false)
            openWhatsAppTarget118(directTarget)
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            phoneCaptureInProgress118.set(false)
            toast("Nenhum telefone brasileiro com DDD foi encontrado na tela.")
            DiagnosticLogStore.record("bubble_action", "whatsapp.capture.no_number sdk_too_old=true")
            return
        }

        scope.launch {
            var acquiredScreenshot = false
            var attempts = 0
            while (!acquiredScreenshot && attempts < 6) {
                acquiredScreenshot = screenshotInProgress.compareAndSet(false, true)
                if (!acquiredScreenshot) delay(120L)
                attempts += 1
            }
            if (!acquiredScreenshot) {
                phoneCaptureInProgress118.set(false)
                toast("Nao consegui capturar o telefone agora. Toque em WhatsApp novamente.")
                DiagnosticLogStore.record("bubble_action", "whatsapp.capture.busy")
                return@launch
            }
            requestPhoneScreenshot118()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun requestPhoneScreenshot118() {
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        scope.launch {
                            val target = runCatching {
                                val buffer = screenshot.hardwareBuffer
                                val wrapped = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                                val bitmap = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                                buffer.close()
                                if (bitmap == null) return@runCatching null
                                try {
                                    ScreenPhoneLink.findBest(ocrService.extractText(bitmap))
                                } finally {
                                    bitmap.recycle()
                                }
                            }.getOrNull()
                            screenshotInProgress.set(false)
                            phoneCaptureInProgress118.set(false)
                            if (target != null) {
                                DiagnosticLogStore.record("bubble_action", "whatsapp.capture.ocr_success number=" + target.displayNumber)
                                openWhatsAppTarget118(target)
                            } else {
                                toast("Nenhum telefone brasileiro com DDD foi encontrado na tela.")
                                DiagnosticLogStore.record("bubble_action", "whatsapp.capture.no_number")
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        screenshotInProgress.set(false)
                        phoneCaptureInProgress118.set(false)
                        toast("O Android nao permitiu ler a tela agora. Codigo: " + errorCode)
                        DiagnosticLogStore.record("bubble_action", "whatsapp.capture.failed code=" + errorCode)
                    }
                },
            )
        }.onFailure { error ->
            screenshotInProgress.set(false)
            phoneCaptureInProgress118.set(false)
            toast("Nao consegui capturar o telefone da tela.")
            DiagnosticLogStore.record("bubble_action", "whatsapp.capture.error=" + error::class.java.simpleName)
        }
    }

    private fun openWhatsAppTarget118(target: ScreenPhoneTarget) {
        val uri = Uri.parse(target.url)
        val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
        val opened = packages.any { packageName ->
            runCatching {
                startActivity(
                    Intent(Intent.ACTION_VIEW, uri)
                        .setPackage(packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                true
            }.getOrDefault(false)
        }
        if (!opened) {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure {
                toast("WhatsApp nao encontrado no celular.")
            }
        }
        DiagnosticLogStore.record("bubble_action", "whatsapp.open number=" + target.displayNumber + " package_opened=" + opened)
    } // bubble_whatsapp_capture_0_1_118

    private fun openSavedPlaceEditor(place: SavedPlace) {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_OPEN_TAB, TAB_CONFIG)
                    .putExtra(EXTRA_SAVED_PLACE_ID, place.id)
                    .putExtra(EXTRA_OPEN_BUBBLE_GROUP, if (place.type == SavedPlaceType.ProximityAlert) "alerts" else "saved_places"),
            )
        }
    }

    private fun onMainBubbleClick() {
        toggleResourceShortcuts()
    } // bubble_shortcut_legacy_click_compat_0_1_117

    private fun toggleActionMenu() {
        onMainBubbleClick()
    }

    private fun showActionMenu() {
        publishRuntimeValidationMenu(false) // universal_runtime_probe_menu_open_0_1_98 popup_removed
        onMainBubbleClick()
    } // floating_bubble_popup_removed_0_1_114

    private fun hideActionMenu() {
        val view = overlayMenuView ?: return
        runCatching { windowManager?.removeView(view) }
        overlayMenuView = null
        overlayMenuParams = null
        publishRuntimeValidationMenu(false) // universal_runtime_probe_menu_close_0_1_98
    }

    private fun updateActionMenuPosition() {
        val manager = windowManager ?: return
        val view = overlayMenuView ?: return
        val params = overlayMenuParams ?: return
        val bubbleParams = overlayParams ?: return
        val panelWidth = dp(252)
        val panelHeight = dp(340)
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        params.x = if (bubbleParams.x + dp(76) + panelWidth <= screenWidth) {
            bubbleParams.x + dp(76)
        } else {
            (bubbleParams.x - panelWidth - dp(10)).coerceAtLeast(0)
        }
        params.y = bubbleParams.y.coerceIn(0, (screenHeight - panelHeight).coerceAtLeast(0))
        runCatching { manager.updateViewLayout(view, params) }
    } // unified_bubble_grid_position_0_1_94

    private fun publishRuntimeValidationState(color: RadarColor, distanceKm: Double?) {
        if (!BuildConfig.DEBUG) return
        val state = color.diagnosticLabel + "|" + (distanceKm?.let(::formatBubbleDistanceKm) ?: "")
        if (bubblePrefs.getString("runtime_validation_state", null) == state) return
        bubblePrefs.edit()
            .putString("runtime_validation_state", state)
            .putLong("runtime_validation_state_at", System.currentTimeMillis())
            .apply()
    }

    private fun publishRuntimeValidationTrigger(trigger: UniversalAddressTriggerDecision) {
        if (!BuildConfig.DEBUG) return
        bubblePrefs.edit()
            .putInt("runtime_visible_addresses", trigger.addresses.size)
            .putString("runtime_last_destination", trigger.destination.orEmpty())
            .putString("runtime_address_signature", trigger.addressSignature)
            .putInt("runtime_screen_hash", trigger.screenHash)
            .putLong("runtime_trigger_at", System.currentTimeMillis())
            .apply()
    }

    private fun clearRuntimeValidationTrigger() {
        if (!BuildConfig.DEBUG) return
        bubblePrefs.edit()
            .putInt("runtime_visible_addresses", 0)
            .remove("runtime_last_destination")
            .remove("runtime_address_signature")
            .remove("runtime_screen_hash")
            .putLong("runtime_clear_at", System.currentTimeMillis())
            .apply()
    }

    private fun publishRuntimeValidationMenu(open: Boolean) {
        if (!BuildConfig.DEBUG) return
        bubblePrefs.edit()
            .putBoolean("runtime_menu_open", open)
            .putLong("runtime_menu_state_at", System.currentTimeMillis())
            .apply()
    } // universal_runtime_probe_functions_0_1_98

    private fun toast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private inner class BubbleTouchListener : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false
        private val touchSlop: Int by lazy {
            android.view.ViewConfiguration.get(this@LiveRideAccessibilityService).scaledTouchSlop.coerceAtLeast(1)
        }

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = overlayParams ?: return false
            val manager = windowManager ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    bubbleGestureActive = (true)
                    bubbleDragStartedAtMillis = event.eventTime
                    analyzeJob?.cancel()
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    view.animate().cancel()
                    traceEvent("bubble.drag.down immediate=true")
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    if (!moved && BubbleDragPolicy.hasExceededTouchSlop(deltaX, deltaY, touchSlop)) {
                        moved = true
                        closeResourceShortcuts() // popup_close_only_on_drag_0_1_120
                    }

                    val maxX = (resources.displayMetrics.widthPixels - view.width).coerceAtLeast(0)
                    val maxY = (resources.displayMetrics.heightPixels - view.height).coerceAtLeast(0)
                    params.x = BubbleDragPolicy.clampCoordinate((startX + deltaX).roundToInt(), maxX)
                    params.y = BubbleDragPolicy.clampCoordinate((startY + deltaY).roundToInt(), maxY)
                    runCatching { manager.updateViewLayout(view, params) }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    val elapsedMillis = (event.eventTime - bubbleDragStartedAtMillis).coerceAtLeast(0L)
                    bubbleGestureActive = false
                    if (moved) {
                        bubblePrefs.edit()
                            .putInt(KEY_BUBBLE_X, params.x)
                            .putInt(KEY_BUBBLE_Y, params.y)
                            .apply()
                        traceEvent("bubble.drag.up moved=true elapsed_ms=" + elapsedMillis)
                    } else {
                        traceEvent("bubble.drag.up moved=false elapsed_ms=" + elapsedMillis)
                        view.performClick()
                    }
                    scope.launch {
                        delay(BubbleDragPolicy.ANALYSIS_RESUME_DELAY_MS)
                        if (!bubbleGestureActive) scheduleVisibleTextAnalysis(delayMs = 0L)
                    }
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    bubbleGestureActive = false
                    traceEvent("bubble.drag.cancel")
                    return true
                }
            }
            return true
        }
    } // bubble_instant_drag_0_1_116

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
        const val SCAN_LOOP_MS = 120L
        const val SCREENSHOT_INTERVAL_MS = 300L
        const val OCR_RESULT_MAX_AGE_MS = 1_400L
        const val LIVE_RESULT_MAX_AGE_MS = 7_500L
        const val PROXIMITY_ALERT_LOOP_MS = 2_000L
        const val LIVE_ANALYSIS_TIMEOUT_MS = 8_000L
        const val AUTOMATIC_CARD_CAPTURE_DEDUPE_MS = 6L * 60L * 60L * 1000L
        const val CARD_CROP_HORIZONTAL_PADDING_PX = 28
        const val CARD_CROP_VERTICAL_PADDING_PX = 80
        const val CARD_TEXT_PREVIEW_LIMIT = 1200
        const val DECISION_OVERLAY_STICKY_MS = 2_800L
        const val BUBBLE_PREFS = "rota_certa_bubble"
        const val KEY_RUNTIME_SHORTCUTS_OPEN = "runtime_shortcuts_open"
        const val KEY_RUNTIME_SHORTCUT_COUNT = "runtime_shortcut_count"
        const val KEY_RUNTIME_SHORTCUT_LABELS = "runtime_shortcut_labels"
        const val EXTRA_OPEN_BUBBLE_GROUP = "open_bubble_group"
        const val BUBBLE_GROUP_GENERAL_VALUE = "general"
        const val BUBBLE_GROUP_READING_VALUE = "reading"
        const val BUBBLE_GROUP_DESTINATION_VALUE = "destination"
        const val BUBBLE_GROUP_ALERTS_VALUE = "alerts"
        const val KEY_BUBBLE_X = "bubble_x"
        const val KEY_BUBBLE_Y = "bubble_y"
        const val KEY_STATE_UPDATED_AT = "state_updated_at"
        const val KEY_STATE_STAGE = "state_stage"
        const val KEY_STATE_REASON = "state_reason"
        const val KEY_STATE_COLOR = "state_color"
        const val KEY_STATE_DISTANCE_KM = "state_distance_km"
        const val KEY_STATE_WINDOW_PACKAGE = "state_window_package"
        const val KEY_STATE_ACTIVE_PACKAGE = "state_active_package"
        const val KEY_STATE_TEXT_PACKAGE = "state_text_package"
        const val KEY_STATE_LAST_SNAPSHOT_HASH = "state_last_snapshot_hash"
        const val KEY_STATE_LAST_ANALYZED_HASH = "state_last_analyzed_hash"
        const val KEY_STATE_PENDING_HASH = "state_pending_hash"
        const val KEY_STATE_SERVICE_READY = "state_service_ready"
        const val KEY_STATE_ANALYZING = "state_analyzing"
        const val KEY_STATE_ACCESSIBILITY_TEXT_LENGTH = "state_accessibility_text_length"
        const val KEY_STATE_ACCESSIBILITY_TEXT_HASH = "state_accessibility_text_hash"
        const val KEY_STATE_OCR_TEXT_LENGTH = "state_ocr_text_length"
        const val KEY_STATE_OCR_TEXT_HASH = "state_ocr_text_hash"
        const val KEY_STATE_TEMPLATE_COUNT = "state_template_count"
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
            "com.samsung.android.app.smartcapture", // smart_capture_passive_overlay_0_1_84
            "com.samsung.android.capture",
            "com.samsung.android.honeyboard",
        )
        val PASSIVE_IGNORED_PACKAGES = setOf(
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
            "com.samsung.android.app.smartcapture", // smart_capture_passive_overlay_0_1_84
            "com.samsung.android.capture",
            "com.samsung.android.honeyboard",
        )
    }
}
// unified_bubble_grid_0_1_94 preserved_by_functional_bubbles

// registeredCardRequired = false (modelo opcional no runtime universal)

// universal_two_address_process_0_1_98 compatibility_preserved_by_0_1_101

// universal_no_card_runtime_0_1_102
// currentCardTemplates = emptyList() // universal_runtime_marker_0_1_120
// cards_repository_preserved_0_1_120 // marcador de compatibilidade legado

// universal_overlay_self_window_fix_0_1_106

// universal_fast_read_runtime_0_1_108

// universal_fast_read_runtime_0_1_109

// universal_fast_read_runtime_0_1_110

// bubble_resource_shortcuts_runtime_0_1_117

// popup_only_service_actions_0_1_119

// popup_only_compile_cleanup_0_1_119

// popup_navigation_service_0_1_120

// popup_navigation_compile_service_0_1_120

// BubbleShortcutAction.OpenScreenWhatsApp -> capturePhoneAndOpenWhatsApp118() // workflow_legacy_marker_0_1_118

// popup_gesture_validator_compat_0_1_120

// universal_ocr_freshness_runtime_0_1_120

// universal_route_inflight_runtime_0_1_120

// UniversalFastReadPolicy.shouldIgnoreTransientEmptyAccessibilityRead(
// universal.accessibility transient_overlay_empty_ignored=true
// universal_ride_evidence_gate_0_1_112
// transient_empty_ignored_route_inflight=true
// resetToIdle guarded active_ride_window
// fast_read_legacy_marker_compat_0_1_124

// manual_optional_contract_finalizer_0_1_127

// BubbleShortcutAction.OpenScreenWhatsApp -> capturePhoneAndOpenWhatsApp() // workflow_legacy_marker_0_1_118
