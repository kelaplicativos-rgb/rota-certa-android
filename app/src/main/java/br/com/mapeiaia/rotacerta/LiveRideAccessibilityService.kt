package br.com.mapeiaia.rotacerta

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
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
    private var quickReplyTargetPackageNameChecklist3: String? = null
    private var quickReplyReceiverRegisteredChecklist3 = false
    private val quickReplyReceiverChecklist3 = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_APPLY_QUICK_REPLY) return
            val replyText = intent.getStringExtra(EXTRA_QUICK_REPLY_TEXT)?.trim().orEmpty()
            val expectedPackage = QuickReplyTargetPolicy.normalize(
                intent.getStringExtra(EXTRA_QUICK_REPLY_TARGET_PACKAGE),
            ) ?: quickReplyTargetPackageNameChecklist3
            if (replyText.isBlank() || expectedPackage == null) return
            scope.launch {
                delay(80L)
                QuickReplyAccessibilityFiller.apply(
                    service = this@LiveRideAccessibilityService,
                    replyText = replyText,
                    expectedPackageName = expectedPackage,
                )
            }
        }
    } // quick_reply_receiver_checklist_3
    private val screenshotInProgress = AtomicBoolean(false)
    private val tripConfirmationCopyInProgressChecklist8 = AtomicBoolean(false)
    private val fullScreenCopyInProgress138 = AtomicBoolean(false)
    private val manualCaptureInProgress138 = AtomicBoolean(false)
    private var farolCriticalStartedAtFinalChecklist6: Long = 0L // subsecond_fields_final_checklist_6
    private val phoneCaptureInProgress118 = AtomicBoolean(false)
    private var analyzeJob: Job? = null
    private var screenshotFallbackJob127: Job? = null // deferred_ocr_job_0_1_127
    private var lastAccessibilityAcceptedAtMillis127: Long = 0L // accessibility_first_timestamp_0_1_127
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
    private var lastScreenshotMillis: Long = 0L
    private var continuousScanStarted = false
    private var proximityAlertMonitorStarted = false
    private var serviceReady = false
    private var analyzing = false
    private var analysisSerial: Long = 0L
    private var liveAnalysisJob: Job? = null
    private var activePackageName: String? = null
    private var recentSelectedRidePackageChecklist11: String? = null
    private var recentSelectedRidePackageAtMillisChecklist11: Long = 0L
    private var lastRidePackageName: String? = null
    private var lastTextPackageName: String? = null
    private var lastAccessibilityText: String = ""
    private var lastOcrText: String = ""
    private var lastAccessibilityTextAtMillis: Long = 0L
    private var lastOcrTextAtMillis: Long = 0L
    private var lastUniversalAddressSeenAtMillis: Long = 0L
    private var lastUniversalAddressSignature: String? = null // universal_source_freshness_fields_0_1_94
    private var currentSettings = AppSettings()
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
    private lateinit var bubblePrefs: SharedPreferences
    private lateinit var speechEngine: LiveSpeechEngine
    private lateinit var proximityAlertEngine: ProximityAlertEngine
    private lateinit var preciseNavigationTrackerChecklist5: PreciseNavigationTracker
    private lateinit var directionalAlertEngineChecklist5: DirectionalProximityAlertEngine
    private lateinit var directionalAlertOverlayChecklist5: DirectionalAlertOverlayController
    private val directionalRadarSpatialIndexChecklist5 = ImportedRadarSpatialIndex()
    private var missingPreciseFixSinceChecklist5: Long = 0L
    // directional_alert_fields_checklist_5
    private lateinit var shortcutOverlayController: BubbleShortcutOverlayController
    private lateinit var radarDetectionCue: RadarDetectionCue
    private val universalRouteCache = LiveRideRouteCache()
    private var universalRouteJob: Job? = null
    private var universalScreenGeneration: Long = 0L
    private var universalWindowGeneration: Long = 0L // universal_ocr_window_generation_0_1_120
    private var universalLastActiveReadAtMillis: Long = 0L
    private var universalActiveRidePackageName: String? = null // universal_route_inflight_runtime_0_1_120
    private var universalActiveAddressSignature: String? = null // universal_two_address_fields_0_1_98
    private var lastImmediateScreenFingerprintChecklist13: Int? = null
    private var lastImmediateScreenPackageChecklist13: String? = null
    private var fastFarolStartedAtChecklist13: Long = 0L // simple_saved_app_fields_checklist_13
    private var lastStableFarolPackageChecklist14: String? = null
    private var lastStableFarolWindowIdChecklist14: Int? = null
    private var partialReadConfirmationJobChecklist14: Job? = null
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
    private var lastVisibleCardSignature: String? = null
    private val coreBubblePresenter = br.com.mapeiaia.rotacerta.core.CoreBubblePresenter

    override fun onCreate() {
        super.onCreate()
        if (!quickReplyReceiverRegisteredChecklist3) {
            ContextCompat.registerReceiver(
                this,
                quickReplyReceiverChecklist3,
                IntentFilter(ACTION_APPLY_QUICK_REPLY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            quickReplyReceiverRegisteredChecklist3 = true
        } // quick_reply_receiver_registration_checklist_3
        repository = SettingsRepository(applicationContext)
        DiagnosticRuntimeGate.setEnabled(DebugLogPreferenceStore.isEnabled(applicationContext))
        UnifiedDebugEventStore.record("SERVICE_CREATE", packageName, "serviço de acessibilidade criado")
        geocodingService = GeocodingService(applicationContext)
        gpsAddressResolver = GpsAddressResolver(applicationContext)
        locationService = DeviceLocationService(applicationContext)
        googleMapsService = GoogleMapsService(applicationContext) // persistent_maps_cache_context_0_1_128
        ocrService = OcrService(applicationContext)
        parser = RideTextParser()
        decisionEngine = DecisionEngine()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        bubblePrefs = getSharedPreferences(BUBBLE_PREFS, Context.MODE_PRIVATE)
        val restoredExactRoutes = universalRouteCache.importSnapshot(
            bubblePrefs.getString("persistent_exact_route_cache_v1", "").orEmpty(),
        )
        Unit /* diagnostics_off_checklist_4 */ // persistent_route_cache_restore_0_1_124
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
        preciseNavigationTrackerChecklist5 = PreciseNavigationTracker(applicationContext)
        directionalAlertEngineChecklist5 = DirectionalProximityAlertEngine(speechEngine)
        directionalAlertOverlayChecklist5 = DirectionalAlertOverlayController(
            context = applicationContext,
            windowManager = requireNotNull(windowManager),
        ) // directional_alert_init_checklist_5
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
        UnifiedDebugEventStore.record("SERVICE_CONNECTED", packageName, "serviço pronto=true")
        Unit
        scope.launch { repository.settings.collect { currentSettings = it } }
        scope.launch { repository.savedPlaces.collect { currentSavedPlaces = it } }
        scope.launch { repository.importedRadars.collect { currentImportedRadars = it } }
        scope.launch {
            currentSettings = repository.settings.first()
            val manualSelectionPrefs127 = getSharedPreferences("rota_certa_runtime_migrations", Context.MODE_PRIVATE)
            if (!manualSelectionPrefs127.getBoolean("manual_selection_storage_ready_0_1_127", false)) {
                if (!SelectedRideAppStore.hasExplicitSelection(applicationContext)) {
                    SelectedRideAppStore.save(applicationContext, emptySet())
                }
                currentSettings = currentSettings.copy(
                    restrictToSelectedRideApps = true,
                    extraMonitoredPackages = "",
                )
                repository.saveSettings(currentSettings)
                manualSelectionPrefs127.edit()
                    .putBoolean("manual_selection_storage_ready_0_1_127", true)
                    .apply()
                Unit /* diagnostics_off_checklist_4 */
            }
            if (!currentSettings.restrictToSelectedRideApps) {
                currentSettings = currentSettings.copy(
                    restrictToSelectedRideApps = true,
                    extraMonitoredPackages = "",
                )
                repository.saveSettings(currentSettings)
            } // manual_apps_and_cards_required_settings_0_1_127
            // pre_registered_runtime_cleanup_0_1_126 superseded_by_manual_selection_0_1_127
            showOverlay(RadarColor.Idle)
            // WhatsApp agora fica dentro da central da bolinha. // whatsapp_inside_grid_0_1_94
            Unit
            startContinuousScan()
            startProximityAlertMonitor()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        UnifiedDebugEventStore.record(
            stage = "ACCESSIBILITY_EVENT",
            packageName = event.packageName?.toString(),
            details = "type=${event.eventType}; class=${event.className}; window=${event.windowId}; serviceReady=$serviceReady",
        )
        if (!serviceReady) return
        if (!currentSettings.appEnabled || !currentSettings.liveReadingEnabled) {
            hardClearUniversalTwoAddress("Leitura universal desligada.")
            return
        }
        if (!AccessibilityEventFloodGate.isRelevantEventType(event.eventType)) {
            UnifiedDebugEventStore.record("BUBBLE_EVENT_IGNORED", event.packageName?.toString(), "motivo=tipo_irrelevante; type=${event.eventType}; window=${event.windowId}")
            return
        }

        val eventPackage = normalizePackageName(event.packageName?.toString())
        val rootPackage = currentRootPackageName()
        val candidatePackage = eventPackage ?: rootPackage
        UnifiedDebugEventStore.record(
            "BUBBLE_EVENT_RESOLVED",
            candidatePackage,
            "eventPackage=${eventPackage ?: "nao informado"}; rootPackage=${rootPackage ?: "nao informado"}; window=${event.windowId}",
        )
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
                universalForegroundPackageName = this.packageName
                activePackageName = this.packageName
                hardClearUniversalTwoAddress("Tela do proprio Rota Certa.")
            }
            return
        }

        val savedPackages = SelectedRideAppStore.read(applicationContext)
        var resolvedPackage = candidatePackage ?: lastExternalWindowPackageName ?: return
        SelectedRideOverlayWindowPolicy.resolve(
            rootPackageName = resolvedPackage,
            lastSelectedPackageName = recentSelectedRidePackageChecklist11,
            lastSelectedAtMillis = recentSelectedRidePackageAtMillisChecklist11,
            selectedPackages = savedPackages,
            nowMillis = System.currentTimeMillis(),
        )?.let { resolvedPackage = it }

        if (resolvedPackage !in savedPackages || !shouldScanPackage(resolvedPackage)) {
            UnifiedDebugEventStore.record(
                "BUBBLE_PACKAGE_BLOCKED",
                resolvedPackage,
                "selecionado=${resolvedPackage in savedPackages}; shouldScan=${shouldScanPackage(resolvedPackage)}; motivo=${scanBlockReason(resolvedPackage)}",
            )
            universalForegroundPackageName = resolvedPackage
            activePackageName = resolvedPackage
            lastExternalWindowPackageName = resolvedPackage
            lastImmediateScreenFingerprintChecklist13 = null
            lastImmediateScreenPackageChecklist13 = null
            hardClearUniversalTwoAddress(scanBlockReason(resolvedPackage))
            return
        }

        recentSelectedRidePackageChecklist11 = resolvedPackage
        recentSelectedRidePackageAtMillisChecklist11 = System.currentTimeMillis()
        universalForegroundPackageName = resolvedPackage
        activePackageName = resolvedPackage
        lastExternalWindowPackageName = resolvedPackage

        val immediateTextChecklist13 = collectImmediateVisibleTextChecklist13()
        val fingerprintChecklist13 = SimpleSavedAppFarolPolicy.screenFingerprint(
            packageName = resolvedPackage,
            text = immediateTextChecklist13,
            windowId = event.windowId,
        )
        UnifiedDebugEventStore.record(
            "BUBBLE_TEXT_COLLECTED",
            resolvedPackage,
            "fonte=acessibilidade_imediata; tamanho=${immediateTextChecklist13.length}; hash=${immediateTextChecklist13.hashCode()}; window=${event.windowId}; fingerprint=$fingerprintChecklist13",
        )
        val screenChangedChecklist13 = lastImmediateScreenPackageChecklist13 != null &&
            (lastImmediateScreenPackageChecklist13 != resolvedPackage ||
                SimpleSavedAppFarolPolicy.changed(lastImmediateScreenFingerprintChecklist13, fingerprintChecklist13))
        if (screenChangedChecklist13) {
            UnifiedDebugEventStore.record("BUBBLE_SCREEN_CHANGED", resolvedPackage, "fingerprintAnterior=$lastImmediateScreenFingerprintChecklist13; fingerprintAtual=$fingerprintChecklist13; window=${event.windowId}")
            hardClearUniversalTwoAddress(
                reason = "A tela mudou; cor e quilometros anteriores removidos imediatamente.",
                keepWaitingYellow = true,
            ) // immediate_screen_change_clear_checklist_13
            universalForegroundPackageName = resolvedPackage
            activePackageName = resolvedPackage
            lastExternalWindowPackageName = resolvedPackage
        }
        lastImmediateScreenPackageChecklist13 = resolvedPackage
        lastImmediateScreenFingerprintChecklist13 = fingerprintChecklist13

        if (immediateTextChecklist13.isBlank()) {
            UnifiedDebugEventStore.record("BUBBLE_TEXT_EMPTY", resolvedPackage, "coleta imediata vazia; OCR fallback agendado")
            hardClearUniversalTwoAddress(
                reason = "Tela alterada sem dois enderecos visiveis; resultado removido imediatamente.",
                keepWaitingYellow = true,
            )
            scheduleScreenshotFallback127(resolvedPackage)
            return
        }

        val quickEvaluationChecklist13 = SimpleSavedAppFarolPolicy.evaluate(
            packageName = resolvedPackage,
            savedPackages = savedPackages,
            text = immediateTextChecklist13,
        )
        if (analyzeJob?.isActive == true) {
            UnifiedDebugEventStore.record("BUBBLE_ANALYSIS_CANCELLED", resolvedPackage, "análise anterior cancelada por evento mais recente")
        }
        analyzeJob?.cancel()
        UnifiedDebugEventStore.record("BUBBLE_ANALYSIS_STARTED", resolvedPackage, "fonte=Accessibility; tamanho=${immediateTextChecklist13.length}; hash=${immediateTextChecklist13.hashCode()}")
        analyzeJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            processRideText(immediateTextChecklist13, TextSource.Accessibility, allowPopupCandidate = true)
        } // immediate_accessibility_process_checklist_13
        if (quickEvaluationChecklist13.active) {
            screenshotFallbackJob127?.cancel()
            screenshotFallbackJob127 = null
        } else {
            scheduleScreenshotFallback127(resolvedPackage)
        }
    } // simple_saved_app_event_contract_checklist_13
 // stable_farol_event_contract_checklist_14
 // simple_saved_app_event_contract_checklist_13
 // universal_overlay_event_guard_0_1_106

    override fun onInterrupt() {
        UnifiedDebugEventStore.record("SERVICE_INTERRUPT", packageName, "Android interrompeu o serviço")
    }

    override fun onDestroy() {
        UnifiedDebugEventStore.record("SERVICE_DESTROY", packageName, "serviço destruído")

        if (::preciseNavigationTrackerChecklist5.isInitialized) preciseNavigationTrackerChecklist5.stop()
        if (::directionalAlertOverlayChecklist5.isInitialized) directionalAlertOverlayChecklist5.hide()
        directionalRadarSpatialIndexChecklist5.clear()
        // directional_alert_destroy_checklist_5
        if (quickReplyReceiverRegisteredChecklist3) {
            runCatching { unregisterReceiver(quickReplyReceiverChecklist3) }
            quickReplyReceiverRegisteredChecklist3 = false
        } // quick_reply_receiver_unregister_checklist_3
        Unit
        serviceReady = false
        screenshotInProgress.set(false)
        coreLiveReadTriggerGate.reset() // gigu_inspired_gate_reset_0_1_89
        analyzeJob?.cancel()
        screenshotFallbackJob127?.cancel()
        screenshotFallbackJob127 = null // deferred_ocr_destroy_cancel_0_1_127
        liveAnalysisJob?.cancel() // latest_card_wins_destroy_0_1_91
        removeOverlay()
        radarDetectionCue.release()
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
        Unit /* diagnostics_off_checklist_4 */
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
                                strictSelectedRootPackageChecklist1()?.let(::scheduleScreenshotFallback127)
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
        scope.launch {
            while (serviceReady) {
                val alerts = currentSavedPlaces.filter { it.type == SavedPlaceType.ProximityAlert }
                val radars = currentImportedRadars
                val hasTargets = alerts.isNotEmpty() || radars.isNotEmpty()
                val enabled = currentSettings.appEnabled && currentSettings.proximityAlertsEnabled

                if (!enabled || !hasTargets) {
                    preciseNavigationTrackerChecklist5.stop()
                    directionalAlertOverlayChecklist5.hide()
                    missingPreciseFixSinceChecklist5 = 0L
                    if (radars.isEmpty()) directionalRadarSpatialIndexChecklist5.clear()
                    delay(DIRECTIONAL_ALERT_IDLE_LOOP_MILLIS_CHECKLIST_5)
                    continue
                }

                preciseNavigationTrackerChecklist5.start()
                checkDirectionalProximityAlertsChecklist5(alerts, radars)
                delay(DIRECTIONAL_ALERT_ACTIVE_LOOP_MILLIS_CHECKLIST_5)
            }
        }
    } // directional_alert_monitor_checklist_5

    private fun checkDirectionalProximityAlertsChecklist5(
        alerts: List<SavedPlace>,
        radars: List<ImportedRadar>,
    ) {
        if (!currentSettings.appEnabled || !currentSettings.proximityAlertsEnabled) {
            directionalAlertOverlayChecklist5.hide()
            return
        }

        val now = System.currentTimeMillis()
        val fix = preciseNavigationTrackerChecklist5.currentFix(now)
        if (fix == null) {
            if (missingPreciseFixSinceChecklist5 == 0L) missingPreciseFixSinceChecklist5 = now
            if (now - missingPreciseFixSinceChecklist5 >= PRECISE_FIX_OVERLAY_GRACE_MILLIS_CHECKLIST_5) {
                directionalAlertOverlayChecklist5.hide()
            }
            return
        }
        missingPreciseFixSinceChecklist5 = 0L

        val searchRadiusMeters = currentSettings.proximityAlertDistanceMeters
            .coerceIn(200, 1000)
            .toDouble() + DIRECTIONAL_RADAR_QUERY_BUFFER_METERS_CHECKLIST_5
        val nearbyRadars = directionalRadarSpatialIndexChecklist5.query(
            source = radars,
            center = fix.coordinate,
            radiusMeters = searchRadiusMeters,
        ).radars

        directionalAlertEngineChecklist5.check(
            alerts = alerts,
            radars = nearbyRadars,
            fix = fix,
            settings = currentSettings,
            onVisual = { visual ->
                if (visual == null) {
                    directionalAlertOverlayChecklist5.hide()
                } else {
                    directionalAlertOverlayChecklist5.showOrUpdate(
                        visual = visual,
                        actions = DirectionalAlertOverlayActions(
                            onEdit = { savedPlaceId ->
                                currentSavedPlaces.firstOrNull { it.id == savedPlaceId }
                                    ?.let(::openSavedPlaceEditor)
                            },
                            onDelete = { savedPlaceId ->
                                scope.launch {
                                    repository.removeSavedPlace(savedPlaceId)
                                    directionalAlertOverlayChecklist5.hide()
                                    toast("Alerta excluído.")
                                }
                            },
                        ),
                    )
                }
            },
        )
    } // directional_alert_check_checklist_5

    private fun scheduleVisibleTextAnalysis(delayMs: Long, allowPopupCandidate: Boolean = false) {
        if (!hasStrictSelectedRootChecklist1()) return // strict_schedule_gate_checklist_1

        if (!currentSettings.liveReadingEnabled) return // bubble_reading_schedule_gate_0_1_118
        if (bubbleGestureActive) {
            Unit /* diagnostics_off_checklist_4 */
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

    private fun scheduleScreenshotFallback127(expectedPackage: String) {
        screenshotFallbackJob127?.cancel()
        val scheduledAt127 = System.currentTimeMillis()
        screenshotFallbackJob127 = scope.launch {
            delay(FarolCriticalPathPolicy.OCR_FALLBACK_DELAY_MILLIS) // ocr_delay_final_checklist_6
            if (!serviceReady || !currentSettings.appEnabled || !currentSettings.liveReadingEnabled) return@launch
            if (expectedPackage != universalResolvedForegroundPackage()) return@launch
            if (!shouldScanPackage(expectedPackage)) return@launch
            if (lastAccessibilityAcceptedAtMillis127 >= scheduledAt127) return@launch
            requestScreenshotAnalysis(allowPopupCandidate = true)
        }
    } // deferred_ocr_fallback_90ms_0_1_127


    // subsecond_capture_helpers_final_checklist_6

// low_priority_capture_final_checklist_6
 // automatic_capture_nonblocking_0_1_129

    private fun requestScreenshotAnalysis(allowPopupCandidate: Boolean = false) {
        if (universalRouteJob?.isActive == true || (lastAnalyzedHash != null && lastAnalyzedHash == lastSnapshotHash)) return // ocr_outside_critical_path_final_checklist_6
        if (!hasStrictSelectedRootChecklist1()) return // strict_screenshot_gate_checklist_1

        if (!currentSettings.liveReadingEnabled) return // bubble_reading_ocr_gate_0_1_118
        if (bubbleGestureActive) {
            Unit /* diagnostics_off_checklist_4 */
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
            Unit /* diagnostics_off_checklist_4 */
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
                        if (!hasStrictSelectedRootChecklist1()) {
                            screenshotInProgress.set(false)
                            return // strict_screenshot_callback_before_ocr_checklist_1
                        }
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
                                    Unit /* diagnostics_off_checklist_4 */
                                    return@runCatching
                                }
                                if (FarolCriticalPathPolicy.shouldSkipOcr(
                                    screenshotRequestedAtMillis = lastScreenshotMillis,
                                    accessibilityAcceptedAtMillis = lastAccessibilityAcceptedAtMillis127,
                                )) return@runCatching // accessibility_won_skip_ocr_final_checklist_6
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
                                    Unit /* diagnostics_off_checklist_4 */
                                    return@runCatching
                                }
                                val fullScreenTriggerChecklist11 = withContext(Dispatchers.Default) {
                                    UniversalAddressTrigger.evaluate(ocrText)
                                }
                                val fullScreenFieldsChecklist11 = RideFields(
                                    pickup = fullScreenTriggerChecklist11.pickup,
                                    destination = fullScreenTriggerChecklist11.destination,
                                )
                                processRideText(ocrText, TextSource.Ocr, allowPopupCandidate = true)
                                bitmap.recycle()
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

    private fun collectVisibleText(allowPopupCandidate: Boolean = false): String {
        if (!hasStrictSelectedRootChecklist1()) return "" // strict_tree_gate_checklist_1

        if (!serviceReady || !isUniversalExternalWindowActive()) return ""
        val root = rootInActiveWindow ?: return ""
        val rootPackage = normalizePackageName(root.packageName?.toString())
        val expectedPackage = universalForegroundPackageName
        if (rootPackage == this.packageName || expectedPackage == this.packageName) return ""
        if (rootPackage != null && expectedPackage != null && rootPackage != expectedPackage && !SelectedRideOverlayWindowPolicy.isTransient(rootPackage)) return "" // selected_overlay_tree_bridge_checklist_11
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
        Unit /* diagnostics_off_checklist_4 */
    }

    private data class FastWorkRegionTargetsChecklist13(
        val homeCoordinate: Coordinate?,
        val pins: List<WorkRegionPin>,
    ) {
        val destinations: List<Coordinate>
            get() = buildList {
                homeCoordinate?.let(::add)
                pins.mapNotNull(WorkRegionPin::coordinate).forEach(::add)
            }
    }

    private fun collectImmediateVisibleTextChecklist13(): String {
        val root = rootInActiveWindow ?: return ""
        val lines = mutableListOf<String>()
        collectNodeText(root, lines)
        return lines.asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString("\n")
    }

    private fun fastWorkRegionTargetsChecklist13(settings: AppSettings): FastWorkRegionTargetsChecklist13 {
        val home = settings.homeCoordinate.takeIf { settings.homeTargetEnabled }
        val pins = if (settings.alternativeTargetEnabled) {
            WorkRegionTargetPolicy.editablePins(settings)
                .filter { it.enabled && it.coordinate != null }
        } else {
            emptyList()
        }
        return FastWorkRegionTargetsChecklist13(homeCoordinate = home, pins = pins)
    }

    private fun decideFastWorkRegionChecklist13(
        snapshotText: String,
        fields: RideFields,
        settings: AppSettings,
        targets: FastWorkRegionTargetsChecklist13,
        routeDistances: List<Double?>,
    ): AnalysisResult {
        var routeIndex = 0
        val homeDistanceKm = if (targets.homeCoordinate != null) routeDistances.getOrNull(routeIndex++) else null
        val pinRoutes = targets.pins.map { pin ->
            WorkRegionPinRoute(
                pin = pin,
                distanceKm = if (pin.coordinate != null) routeDistances.getOrNull(routeIndex++) else null,
            )
        }
        return decisionEngine.decideWorkRegion(
            fields = fields,
            settings = settings,
            fullText = snapshotText,
            homeTargetActive = targets.homeCoordinate != null,
            homeDistanceKm = homeDistanceKm,
            pinRoutes = pinRoutes,
        )
    }

    // simple_saved_app_helpers_checklist_13

    private fun stableWindowIdChecklist14(eventWindowId: Int): Int? =
        rootInActiveWindow?.windowId?.takeIf { it >= 0 }
            ?: eventWindowId.takeIf { it >= 0 }

    private fun schedulePartialReadConfirmationChecklist14(
        packageName: String,
        windowId: Int?,
    ) {
        @Suppress("UNUSED_VARIABLE") val ignoredWindowIdChecklist15 = windowId
        if (partialReadConfirmationJobChecklist14?.isActive == true) return
        partialReadConfirmationJobChecklist14 = scope.launch {
            delay(FarolDisplayStabilityPolicy.PARTIAL_ABSENCE_CONFIRM_MILLIS)
            val savedPackagesChecklist14 = SelectedRideAppStore.read(applicationContext)
            val currentPackageChecklist14 = strictSelectedRootPackageChecklist1()
                ?: normalizePackageName(universalResolvedForegroundPackage())?.takeIf { it in savedPackagesChecklist14 }
                ?: return@launch
            if (currentPackageChecklist14 != packageName) return@launch
            val confirmedTextChecklist14 = collectImmediateVisibleTextChecklist13()
            val confirmedEvaluationChecklist14 = withContext(Dispatchers.Default) {
                SimpleSavedAppFarolPolicy.evaluate(packageName, savedPackagesChecklist14, confirmedTextChecklist14)
            }
            partialReadConfirmationJobChecklist14 = null
            if (confirmedEvaluationChecklist14.active) {
                processRideText(confirmedTextChecklist14, TextSource.Accessibility, allowPopupCandidate = true)
            } else {
                hardClearUniversalTwoAddress(
                    reason = "O card saiu da tela; cor e quilometros removidos.",
                    keepWaitingYellow = true,
                )
                scheduleScreenshotFallback127(packageName)
            }
        }
    } // fixed_absence_confirmation_job_checklist_15
 // fixed_absence_confirmation_job_checklist_15
 // partial_read_confirmation_checklist_14

    private suspend fun processRideText(
        text: String,
        source: TextSource,
        allowPopupCandidate: Boolean = false,
    ) {
        @Suppress("UNUSED_VARIABLE") val ignoredPopupCandidateChecklist13 = allowPopupCandidate
        UnifiedDebugEventStore.record(
            "BUBBLE_PROCESS_ENTER",
            universalResolvedForegroundPackage(),
            "fonte=${source.name}; tamanho=${text.length}; hash=${text.hashCode()}; gesture=$bubbleGestureActive; ready=$serviceReady; appEnabled=${currentSettings.appEnabled}; live=${currentSettings.liveReadingEnabled}",
        )
        if (bubbleGestureActive || !serviceReady || !currentSettings.appEnabled || !currentSettings.liveReadingEnabled) return // bubble_drag_process_pause_0_1_116
        val savedPackagesChecklist13 = SelectedRideAppStore.read(applicationContext)
        val selectedPackageChecklist13 = strictSelectedRootPackageChecklist1()
            ?: normalizePackageName(universalResolvedForegroundPackage())
                ?.takeIf { it in savedPackagesChecklist13 }
            ?: run {
                hardClearUniversalTwoAddress("Aplicativo não selecionado; leitura e rota bloqueadas.")
                return
            }

        val snapshotTextChecklist13 = text.trim()
        val evaluationChecklist13 = withContext(Dispatchers.Default) {
            SimpleSavedAppFarolPolicy.evaluate(
                packageName = selectedPackageChecklist13,
                savedPackages = savedPackagesChecklist13,
                text = snapshotTextChecklist13,
            )
        }
        UnifiedDebugEventStore.record(
            "BUBBLE_ADDRESS_EVALUATION",
            selectedPackageChecklist13,
            "ativo=${evaluationChecklist13.active}; pickup=${evaluationChecklist13.pickup.orEmpty()}; destination=${evaluationChecklist13.destination.orEmpty()}; assinatura=${evaluationChecklist13.addressSignature}; screenHash=${evaluationChecklist13.screenHash}",
        )
        if (!evaluationChecklist13.active) {
            hardClearUniversalTwoAddress(
                reason = "Tela sem dois enderecos validos; cor e quilometros removidos imediatamente.",
                keepWaitingYellow = true,
            ) // simple_two_address_clear_checklist_13
            return
        }

        if (source == TextSource.Accessibility) {
            lastAccessibilityAcceptedAtMillis127 = System.currentTimeMillis()
            screenshotFallbackJob127?.cancel()
            screenshotFallbackJob127 = null
        }
        universalLastActiveReadAtMillis = System.currentTimeMillis()
        val fieldsChecklist13 = RideFields(
            pickup = evaluationChecklist13.pickup,
            destination = evaluationChecklist13.destination,
        )
        val cardChangedChecklist13 = universalActiveAddressSignature != evaluationChecklist13.addressSignature ||
            lastSnapshotHash != evaluationChecklist13.screenHash
        UnifiedDebugEventStore.record(
            "BUBBLE_CARD_STATE",
            selectedPackageChecklist13,
            "mudou=$cardChangedChecklist13; assinaturaAnterior=${universalActiveAddressSignature ?: "nenhuma"}; assinaturaAtual=${evaluationChecklist13.addressSignature}; hashAnterior=${lastSnapshotHash ?: 0}; hashAtual=${evaluationChecklist13.screenHash}",
        )
        if (cardChangedChecklist13 && (
                universalActiveAddressSignature != null ||
                    currentDistanceKm != null ||
                    currentRadarColor == RadarColor.Green ||
                    currentRadarColor == RadarColor.Red
            )
        ) {
            hardClearUniversalTwoAddress(
                reason = "Novo endereco detectado; resultado anterior removido imediatamente.",
                keepWaitingYellow = true,
            )
        }

        universalActiveRidePackageName = selectedPackageChecklist13
        universalActiveAddressSignature = evaluationChecklist13.addressSignature
        lastSnapshotHash = evaluationChecklist13.screenHash

        if (cardChangedChecklist13) {
            universalScreenGeneration += 1L
            universalRouteJob?.cancel()
            universalRouteJob = null
            lastAnalyzedHash = null
            currentDistanceKm = null
            fastFarolStartedAtChecklist13 = System.currentTimeMillis()
            bubblePrefs.edit()
                .putLong("fast_farol_started_at", fastFarolStartedAtChecklist13)
                .putString("fast_farol_last_destination", fieldsChecklist13.destination.orEmpty())
                .apply()
        } else if (lastAnalyzedHash == evaluationChecklist13.screenHash || universalRouteJob?.isActive == true) {
            UnifiedDebugEventStore.record(
                "BUBBLE_DUPLICATE_SKIPPED",
                selectedPackageChecklist13,
                "lastAnalyzedHash=$lastAnalyzedHash; screenHash=${evaluationChecklist13.screenHash}; routeActive=${universalRouteJob?.isActive == true}",
            )
            return
        }

        val settingsChecklist13 = currentSettings
        val targetsChecklist13 = fastWorkRegionTargetsChecklist13(settingsChecklist13)
        if (targetsChecklist13.destinations.isEmpty()) {
            rememberBubbleReason("work_region_missing", "Configure Casa ou pelo menos um alfinete com coordenada validada.")
            showOverlay(RadarColor.Default, distanceKm = null)
            return
        }

        val cachedDistancesChecklist13 = googleMapsService.cachedDrivingDistancesFromAddressKm(
            originAddress = fieldsChecklist13.destination.orEmpty(),
            destinations = targetsChecklist13.destinations,
        )
        val generationChecklist13 = universalScreenGeneration
        if (cachedDistancesChecklist13 != null) {
            UnifiedDebugEventStore.record("BUBBLE_CACHE_HIT", selectedPackageChecklist13, "destino=${fieldsChecklist13.destination.orEmpty()}; distancias=$cachedDistancesChecklist13")
            val cachedResultChecklist13 = decideFastWorkRegionChecklist13(
                snapshotText = snapshotTextChecklist13,
                fields = fieldsChecklist13,
                settings = settingsChecklist13,
                targets = targetsChecklist13,
                routeDistances = cachedDistancesChecklist13,
            )
            bubblePrefs.edit().putString("fast_farol_last_path", "cache_exato").apply()
            applyUniversalTwoAddressResult(
                cachedResultChecklist13,
                evaluationChecklist13.screenHash,
                evaluationChecklist13.addressSignature,
                generationChecklist13,
            ) // exact_cache_before_yellow_checklist_13
            return
        }

        UnifiedDebugEventStore.record("BUBBLE_ROUTE_REQUESTED", selectedPackageChecklist13, "destino=${fieldsChecklist13.destination.orEmpty()}; alvos=${targetsChecklist13.destinations.size}; generation=$generationChecklist13")
        rememberBubbleReason("universal_waiting", "Dois enderecos identificados; calculando o ultimo destino.")
        showOverlay(RadarColor.Default, distanceKm = null)
        bubblePrefs.edit().putString("fast_farol_last_path", "rota_google").apply()
        universalRouteJob = scope.launch {
            analyzeUniversalTwoAddress(
                snapshotText = snapshotTextChecklist13,
                fields = fieldsChecklist13,
                screenHash = evaluationChecklist13.screenHash,
                addressSignature = evaluationChecklist13.addressSignature,
                generation = generationChecklist13,
            )
        }
    } // simple_saved_app_process_checklist_13
 // stable_farol_process_contract_checklist_14
 // simple_saved_app_process_checklist_13
 // universal_stable_process_0_1_101

    //    private fun resolveRidePackageForText( compatibility_boundary_0_1_102

    private suspend fun analyzeUniversalTwoAddress(
        snapshotText: String,
        fields: RideFields,
        screenHash: Int,
        addressSignature: String,
        generation: Long,
    ) {
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return
        val settingsChecklist13 = currentSettings
        val apiKeyChecklist13 = GoogleMapsApiKeyPolicy.effective(
            settingsChecklist13.googleMapsApiKey,
            BuildConfig.GOOGLE_MAPS_API_KEY,
        )
        if (apiKeyChecklist13.isBlank()) {
            rememberBubbleReason("google_maps_api_required", "Configure a Chave Google Maps API para calcular a rota.")
            showOverlay(RadarColor.Default, distanceKm = null)
            return
        }
        val targetsChecklist13 = fastWorkRegionTargetsChecklist13(settingsChecklist13)
        if (targetsChecklist13.destinations.isEmpty()) {
            rememberBubbleReason("work_region_missing", "Configure Casa ou pelo menos um alfinete com coordenada validada.")
            showOverlay(RadarColor.Default, distanceKm = null)
            return
        }
        UnifiedDebugEventStore.record("BUBBLE_ROUTE_CALL_START", universalActiveRidePackageName, "destino=${fields.destination.orEmpty()}; alvos=${targetsChecklist13.destinations.size}; generation=$generation")
        val routeDistancesChecklist13 = googleMapsService.drivingDistancesFromAddressKm(
            originAddress = fields.destination.orEmpty(),
            destinations = targetsChecklist13.destinations,
            apiKey = apiKeyChecklist13,
        ) // single_exact_route_matrix_checklist_13
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return
        UnifiedDebugEventStore.record("BUBBLE_ROUTE_CALL_END", universalActiveRidePackageName, "distancias=$routeDistancesChecklist13; fresh=${isUniversalResultFresh(generation, screenHash, addressSignature)}")
        val resultChecklist13 = decideFastWorkRegionChecklist13(
            snapshotText = snapshotText,
            fields = fields,
            settings = settingsChecklist13,
            targets = targetsChecklist13,
            routeDistances = routeDistancesChecklist13,
        )
        applyUniversalTwoAddressResult(resultChecklist13, screenHash, addressSignature, generation)
    } // simple_saved_app_route_checklist_13
 // simple_saved_app_route_checklist_13


    private suspend fun applyUniversalTwoAddressResult(
        result: AnalysisResult,
        screenHash: Int,
        addressSignature: String,
        generation: Long,
    ) {
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return
        val colorChecklist13 = when (result.recommendation) {
            Recommendation.GoodRide -> RadarColor.Green
            Recommendation.OutsideRadius -> RadarColor.Red
            Recommendation.InsufficientData -> RadarColor.Default
        }
        val distanceChecklist13 = result.nearestConfiguredDistanceKm()
        lastAnalyzedHash = screenHash
        UnifiedDebugEventStore.record(
            "BUBBLE_DECISION_READY",
            universalActiveRidePackageName,
            "recomendacao=${result.recommendation}; cor=$colorChecklist13; distancia=$distanceChecklist13; destino=${result.fields.destination.orEmpty()}; generation=$generation; screenHash=$screenHash",
        )
        rememberBubbleReason("universal_result", result.reason)
        showOverlay(colorChecklist13, distanceChecklist13)
        UnifiedDebugEventStore.record("BUBBLE_DECISION_PAINTED", universalActiveRidePackageName, "cor=$colorChecklist13; distancia=$distanceChecklist13; stage=$lastBubbleStateStage; motivo=$lastBubbleStateReason")
        val finishedAtChecklist13 = System.currentTimeMillis()
        val elapsedChecklist13 = if (fastFarolStartedAtChecklist13 > 0L) {
            (finishedAtChecklist13 - fastFarolStartedAtChecklist13).coerceAtLeast(0L)
        } else {
            0L
        }
        bubblePrefs.edit()
            .putLong("fast_farol_last_elapsed_ms", elapsedChecklist13)
            .putLong("fast_farol_last_finished_at", finishedAtChecklist13)
            .putString("fast_farol_last_destination", result.fields.destination.orEmpty())
            .apply() // measured_end_to_end_farol_checklist_13
        val persistenceSignatureChecklist13 = listOf(
            addressSignature,
            result.recommendation.name,
            distanceChecklist13?.let { String.format(Locale.US, "%.3f", it) }.orEmpty(),
        ).joinToString("|")
        if (universalAnalysisDeduper.shouldPersist(persistenceSignatureChecklist13)) {
            scope.launch(Dispatchers.IO) { runCatching { repository.addAnalysis(result) } }
        }
    } // simple_saved_app_apply_checklist_13
 // simple_saved_app_apply_checklist_13


    private fun isUniversalResultFresh(
        generation: Long,
        screenHash: Int,
        addressSignature: String,
    ): Boolean {
        val activePackageChecklist13 = normalizePackageName(universalActiveRidePackageName)
            ?: normalizePackageName(universalResolvedForegroundPackage())
        return serviceReady &&
            currentSettings.appEnabled &&
            currentSettings.liveReadingEnabled &&
            generation == universalScreenGeneration &&
            screenHash == lastSnapshotHash &&
            addressSignature == universalActiveAddressSignature &&
            activePackageChecklist13 != null &&
            activePackageChecklist13 in SelectedRideAppStore.read(applicationContext) &&
            shouldScanPackage(activePackageChecklist13)
    } // simple_saved_app_freshness_checklist_13
 // simple_saved_app_freshness_checklist_13


    private fun hardClearUniversalTwoAddress(
        reason: String,
        keepWaitingYellow: Boolean = false,
    ) {
        UnifiedDebugEventStore.record(
            "BUBBLE_CLEAR_REQUEST",
            universalResolvedForegroundPackage(),
            "reason=$reason; keepWaitingYellow=$keepWaitingYellow; corAtual=$currentRadarColor; distanciaAtual=$currentDistanceKm; assinatura=${universalActiveAddressSignature ?: "nenhuma"}; hash=${lastSnapshotHash ?: 0}",
        )
        partialReadConfirmationJobChecklist14?.cancel()
        partialReadConfirmationJobChecklist14 = null
        val targetColor127 = if (keepWaitingYellow) RadarColor.Default else RadarColor.Idle
        val targetStage127 = if (keepWaitingYellow) "manual_waiting" else "universal_idle"
        val targetReason127 = if (keepWaitingYellow) {
            "Aplicativo salvo ativo; aguardando dois enderecos na tela."
        } else {
            reason
        }
        val hadDecisionColor127 = currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red
        val hadData = hadDecisionColor127 || // yellow_waiting_not_active_data_0_1_127
            currentDistanceKm != null ||
            lastSnapshotHash != null ||
            universalActiveAddressSignature != null
        if (
            !hadData &&
            currentRadarColor == targetColor127 &&
            lastBubbleStateStage == targetStage127 &&
            lastBubbleStateReason == targetReason127
        ) return
        val stateChanged = hadData ||
            currentRadarColor != targetColor127 ||
            lastBubbleStateStage != targetStage127 ||
            lastBubbleStateReason != targetReason127
        Unit /* diagnostics_off_checklist_4 */ // session_diagnostic_clear_v2
        universalScreenGeneration += 1L
        universalRouteJob?.cancel()
        universalRouteJob = null
        analyzeJob?.cancel()
        analyzeJob = null
        screenshotFallbackJob127?.cancel()
        screenshotFallbackJob127 = null
        universalActiveAddressSignature = null
        lastSnapshotHash = null
        lastAnalyzedHash = null
        analyzing = false
        currentDistanceKm = null
        lastAccessibilityText = ""
        lastOcrText = ""
        universalAccessibilityOwnsCard = false
        universalLastActiveReadAtMillis = 0L
        universalActiveRidePackageName = null
        universalLiveReadGate.reset()
        if (stateChanged) {
            clearRuntimeValidationTrigger()
            rememberBubbleReason(targetStage127, targetReason127)
            showOverlay(targetColor127, distanceKm = null) // atomic_hard_clear_single_paint_0_1_127
            UnifiedDebugEventStore.record("BUBBLE_CLEAR_PAINTED", universalResolvedForegroundPackage(), "cor=$targetColor127; stage=$targetStage127; motivo=$targetReason127")
            currentRadarColor = targetColor127
            currentDistanceKm = null
            if (BuildConfig.DEBUG) {
                bubblePrefs.edit()
                    .putString(
                        "runtime_validation_state",
                        (if (keepWaitingYellow) "amarelo" else "cinza") + "|",
                    )
                    .putLong("runtime_validation_state_at", System.currentTimeMillis())
                    .apply()
            }
        }
        if (hadData) Unit /* diagnostics_off_checklist_4 */
    } // universal_stable_clear_0_1_101
 // universal_stable_clear_0_1_101

    private fun shouldProtectLockedPopupSession128(incomingPackageName: String?): Boolean {
        val incoming = normalizePackageName(incomingPackageName)
        val transientSystemWindow = incoming == "android" ||
            incoming == "com.android.systemui" ||
            incoming == "com.samsung.android.systemui" ||
            incoming?.contains("launcher") == true ||
            incoming?.contains("keyguard") == true
        if (!transientSystemWindow) return false
        val activeRidePackage = normalizePackageName(universalActiveRidePackageName) ?: return false
        if (!shouldScanPackage(activeRidePackage)) return false
        if (universalActiveAddressSignature == null && currentRadarColor != RadarColor.Green && currentRadarColor != RadarColor.Red) return false
        val now = System.currentTimeMillis()
        return universalLastActiveReadAtMillis > 0L &&
            now >= universalLastActiveReadAtMillis &&
            now - universalLastActiveReadAtMillis <= 10_000L
    } // locked_popup_session_guard_0_1_128

    private fun universalResolvedForegroundPackage(): String? {
        val resolution = UniversalWindowPackageResolver.resolve(
            rootPackageName = currentRootPackageName(),
            activePackageName = universalForegroundPackageName ?: activePackageName,
            lastExternalPackageName = lastExternalWindowPackageName,
            ownPackageName = this.packageName,
        )
        val resolvedPackage128 = resolution.effectivePackageName
        if (shouldProtectLockedPopupSession128(resolvedPackage128)) {
            val protectedPackage128 = normalizePackageName(universalActiveRidePackageName)
            if (protectedPackage128 != null) {
                lastExternalWindowPackageName = protectedPackage128
                return protectedPackage128 // locked_popup_resolver_preserves_ride_package_0_1_128
            }
        }
        lastExternalWindowPackageName = resolution.lastExternalPackageName
        return resolvedPackage128
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
        Unit /* diagnostics_off_checklist_4 */
    } // universal_runtime_stability_guard_0_1_101

    private fun looksLikeTwoAddressCandidate(text: String): Boolean =
        UniversalAddressTrigger.evaluate(text).active // universal_two_address_candidate_0_1_98

    private fun rememberSourceText(packageName: String?, source: TextSource, text: String) {
        val normalizedPackage = normalizePackageName(packageName)
        if (normalizedPackage != lastTextPackageName) {
            Unit /* diagnostics_off_checklist_4 */
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

    private suspend fun geocodeBest(query: String, region: DeviceRegion, settings: AppSettings): Coordinate? {
        val apiKey = settings.googleMapsApiKey.ifBlank { BuildConfig.GOOGLE_MAPS_API_KEY }
        return googleMapsService.geocode(query, region, apiKey) ?: geocodingService.geocode(query, region)
    } // universal_two_address_geocode_0_1_98

    private suspend fun routeDistancesFromAddressKm(
        originAddress: String,
        destinations: List<Coordinate>,
        settings: AppSettings,
    ): List<Double?> {
        val apiKey = settings.googleMapsApiKey.ifBlank { BuildConfig.GOOGLE_MAPS_API_KEY }
        return if (originAddress.isNotBlank() && destinations.isNotEmpty() && apiKey.isNotBlank()) {
            googleMapsService.drivingDistancesFromAddressKm(originAddress, destinations, apiKey)
        } else {
            List(destinations.size) { null }
        }
    } // direct_address_route_helper_0_1_128

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
            .putBoolean(KEY_STATE_SERVICE_READY, serviceReady)
            .putBoolean(KEY_STATE_ANALYZING, analyzing)
            .putInt(KEY_STATE_ACCESSIBILITY_TEXT_LENGTH, lastAccessibilityText.length)
            .putString(KEY_STATE_ACCESSIBILITY_TEXT_HASH, lastAccessibilityText.takeIf { it.isNotBlank() }?.snapshotHash()?.toString().orEmpty())
            .putInt(KEY_STATE_OCR_TEXT_LENGTH, lastOcrText.length)
            .putString(KEY_STATE_OCR_TEXT_HASH, lastOcrText.takeIf { it.isNotBlank() }?.snapshotHash()?.toString().orEmpty())
            .apply()
    }

    private fun invalidateLiveAnalysis(reason: String) {
        @Suppress("UNUSED_VARIABLE") val ignoredReason = reason
        analysisSerial += 1L
        liveAnalysisJob?.cancel()
        liveAnalysisJob = null
        analyzeJob?.cancel()
        analyzeJob = null
        analyzing = false
    }

    private fun rememberBubbleReason(stage: String, reason: String) {
        lastBubbleStateStage = stage
        lastBubbleStateReason = reason
    }

    private fun resetToDefault(
        reason: String,
        text: String? = null,
        fields: RideFields? = null,
        record: Boolean = true,
    ) {
        invalidateLiveAnalysis("reset_default:$reason") // latest_card_wins_reset_default_0_1_91
        lastVisibleCardSignature = null
        Unit /* diagnostics_off_checklist_4 */ // core_visible_card_clear_0_1_95
        lastSnapshotHash = null
        lastAnalyzedHash = null
        lastVisibleCardSignature = null
        clearRememberedRideText()
        rememberBubbleReason("default", reason)
        Unit /* diagnostics_off_checklist_4 */
        showOverlay(RadarColor.Default, null)
        if (record) {
            Unit
        }
    }

    private fun resetToDefaultForNonRideScreen(reason: String, record: Boolean = false) {
        resetToIdle(reason = reason, record = record)
    }


    private fun resetToIdle(
        reason: String,
        record: Boolean = false,
    ) {
        invalidateLiveAnalysis("reset_idle:$reason") // latest_card_wins_reset_idle_0_1_91
        lastVisibleCardSignature = null
        Unit /* diagnostics_off_checklist_4 */ // core_visible_card_clear_0_1_95
        Unit // global_idle_never_guarded_0_1_124
        lastSnapshotHash = null
        lastAnalyzedHash = null
        lastVisibleCardSignature = null
        clearRememberedRideText()
        rememberBubbleReason("idle", reason)
        Unit /* diagnostics_off_checklist_4 */
        showOverlay(RadarColor.Idle, null)
        if (record) {
            Unit
        }
    }


    private fun clearRememberedRideText() {
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

    private fun saveCurrentPlaceFromBubble(type: SavedPlaceType, defaultName: String = if (type == SavedPlaceType.ProximityAlert) "Alerta" else "") {
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
            toast(if (isAlert) "Alerta salvo" else "Local salvo")
            showSaveConfirmationNotification(
                title = if (isAlert) "Alerta salvo" else "Local salvo",
                text = place.address,
            )
            Unit
        }
    }

    private fun collectVisibleTextForAction(): String {
        if (!hasStrictSelectedRootChecklist1()) return "" // strict_manual_tree_gate_checklist_1

        val root = rootInActiveWindow ?: return ""
        val lines = mutableListOf<String>()
        collectNodeText(root, lines)
        return lines.map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString("\n")
    }

    private fun strictSelectedRootPackageChecklist1(): String? {
        val nowChecklist11 = System.currentTimeMillis()
        val selectedChecklist11 = SelectedRideAppStore.read(applicationContext)
        val resolvedChecklist11 = SelectedRideOverlayWindowPolicy.resolve(
            rootPackageName = currentRootPackageName(),
            lastSelectedPackageName = recentSelectedRidePackageChecklist11,
            lastSelectedAtMillis = recentSelectedRidePackageAtMillisChecklist11,
            selectedPackages = selectedChecklist11,
            nowMillis = nowChecklist11,
        ) ?: return null
        if (currentRootPackageName() == resolvedChecklist11) {
            recentSelectedRidePackageChecklist11 = resolvedChecklist11
            recentSelectedRidePackageAtMillisChecklist11 = nowChecklist11
        }
        return resolvedChecklist11.takeIf { shouldScanPackage(it) }
    } // selected_overlay_root_bridge_checklist_11

    private fun hasStrictSelectedRootChecklist1(): Boolean =
        strictSelectedRootPackageChecklist1() != null // strict_selected_root_helper_checklist_1

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
            "configuração antiga",
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
        val overlayFallbackChecklist11 = SelectedRideOverlayWindowPolicy.resolve(
            rootPackageName = currentRootPackageName(),
            lastSelectedPackageName = recentSelectedRidePackageChecklist11,
            lastSelectedAtMillis = recentSelectedRidePackageAtMillisChecklist11,
            selectedPackages = SelectedRideAppStore.read(applicationContext),
            nowMillis = System.currentTimeMillis(),
        )
        return overlayFallbackChecklist11 ?: resolution.effectivePackageName
    } // universal_overlay_window_resolver_0_1_106

 // selected_overlay_window_bridge_checklist_11
    private fun currentRootPackageName(): String? =
        normalizePackageName(rootInActiveWindow?.packageName?.toString())

    private fun shouldScanPackage(packageName: String?): Boolean {
        if (!serviceReady || !currentSettings.appEnabled || !currentSettings.liveReadingEnabled) return false
        val normalized = normalizePackageName(packageName) ?: return false
        if (normalized == this.packageName) return false
        return normalized in SelectedRideAppStore.read(applicationContext)
    } // strict_selected_app_policy_checklist_1

    private fun selectedRidePackages(settings: AppSettings): Set<String> {
        @Suppress("UNUSED_VARIABLE")
        val ignoredLegacySettings = settings
        return SelectedRideAppStore.read(applicationContext)
    } // selected_packages_manual_only_checklist_1

    private fun scanBlockReason(packageName: String?): String {
        val normalized = normalizePackageName(packageName)
            ?: return "Pacote ativo não informado pelo Android."
        if (!currentSettings.appEnabled) return "Rota Certa desligado pelo usuário."
        if (!currentSettings.liveReadingEnabled) return "Leitura ao vivo desligada pelo usuário."
        if (normalized == this.packageName) return "Tela do próprio Rota Certa."
        return if (normalized in SelectedRideAppStore.read(applicationContext)) {
            "Aplicativo selecionado manualmente: $normalized."
        } else {
            "Aplicativo não selecionado pelo usuário: $normalized."
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
    ) = Unit

    private fun traceEvent(message: String) {
        Unit /* diagnostics_off_checklist_4 */ // session_diagnostic_trace_v2

        if (message.startsWith("event passive ignored")) {
            val now = System.currentTimeMillis()
            val passiveKey = message.substringBefore(" reason=")
            if (passiveKey == lastPassiveTraceKey && now - lastPassiveTraceAtMillis < 1_500L) return
            lastPassiveTraceKey = passiveKey
            lastPassiveTraceAtMillis = now
        }
        Unit /* diagnostics_off_checklist_4 */
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

    private fun showOverlay(color: RadarColor, distanceKm: Double? = null) {
        if (!serviceReady) return
        val manager = windowManager ?: return
        val nextTextChecklist15 = formatBubbleDistanceKm(distanceKm)
        val existingViewChecklist15 = overlayView
        if (existingViewChecklist15 != null && currentRadarColor == color &&
            existingViewChecklist15.text.toString() == nextTextChecklist15
        ) {
            currentDistanceKm = distanceKm
            return // overlay_idempotent_same_value_checklist_15
        }
        currentRadarColor = color
        currentDistanceKm = distanceKm
        val view = existingViewChecklist15 ?: TextView(this).also { newView ->
            val params = overlayLayoutParams()
            newView.contentDescription = "Rota Certa"
            newView.gravity = Gravity.CENTER
            newView.includeFontPadding = false
            newView.setTextColor(Color.BLACK)
            newView.setTypeface(Typeface.DEFAULT_BOLD)
            newView.setOnClickListener { toggleResourceShortcuts() } // resource_shortcut_click_preserved_checklist_15
            newView.setOnTouchListener(BubbleTouchListener())
            if (!runCatching { manager.addView(newView, params) }.isSuccess) return
            overlayView = newView
            overlayParams = params
        }
        view.text = nextTextChecklist15
        view.textSize = bubbleTextSizeSp(nextTextChecklist15)
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color.argb(currentSettings))
            setStroke(dp(3), Color.argb((currentSettings.bubbleOpacity.coerceIn(0.25, 1.0) * 255).roundToInt(), 255, 255, 255))
        }
    } // no_duplicate_overlay_render_checklist_15
 // no_duplicate_overlay_render_checklist_15


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
        shortcutOverlayController.toggleShortcuts(
            anchor = params,
            onShortcut = ::executeShortcutModule,
            onShortcutDoubleTap = ::executeShortcutDoubleTap,
        )
        persistResourceShortcutState()
        Unit /* diagnostics_off_checklist_4 */
        Unit /* diagnostics_off_checklist_4 */
    }

    private fun executeShortcutDoubleTap(spec: BubbleShortcutSpec) {
        when (spec.doubleTapAction) {
            BubbleShortcutQuickAction.CopyAllVisibleText -> copyAllVisibleTextFromBubble138()
            BubbleShortcutQuickAction.CreateQuickReply -> openQuickRepliesFromBubble(createNew = true)
            BubbleShortcutQuickAction.CreateRadarAtCurrentLocation -> createManualRadarFromBubble138()
            BubbleShortcutQuickAction.CreateNamedAlertAtCurrentLocation -> openNamedPlaceShortcut138(SavedPlaceType.ProximityAlert)
            BubbleShortcutQuickAction.CreateNamedSavedPlaceAtCurrentLocation -> openNamedPlaceShortcut138(SavedPlaceType.Place)
            BubbleShortcutQuickAction.DefineDestinationAtCurrentLocation -> openDestinationConfirmationFromBubble138()
            null -> executeShortcutModule(spec)
        }
    }

    private fun executeShortcutModule(spec: BubbleShortcutSpec) {
        Unit /* diagnostics_off_checklist_4 */
        Unit /* diagnostics_off_checklist_4 */
        when (spec.action) {
            BubbleShortcutAction.CopyTripConfirmation -> copyTripConfirmationFromBubbleChecklist8() // trip_confirmation_action_checklist_8
            BubbleShortcutAction.OpenQuickReplies -> openQuickRepliesFromBubble(createNew = false) // quick_reply_action_checklist_3
            BubbleShortcutAction.OpenRoute,
            BubbleShortcutAction.OpenDestination,
            BubbleShortcutAction.OpenAlerts,
            BubbleShortcutAction.OpenSavedPlaces,
            BubbleShortcutAction.OpenRadars,
            BubbleShortcutAction.OpenAppearance,
            BubbleShortcutAction.OpenPermissions,
            BubbleShortcutAction.OpenBackup,
            BubbleShortcutAction.OpenReports,
            BubbleShortcutAction.OpenSettings,
            -> openResourceGroup(requireNotNull(spec.targetGroup), requireNotNull(spec.targetTab))

            BubbleShortcutAction.OpenScreenWhatsApp -> capturePhoneAndOpenWhatsApp118()
            BubbleShortcutAction.OpenCollector -> openCollectorFromBubble()
            BubbleShortcutAction.ClearClipboard -> clearClipboardFromBubble()
            BubbleShortcutAction.ExportDiagnostic -> exportDiagnosticFromBubble()
            BubbleShortcutAction.StopApplication -> stopApplicationFromBubble()
            BubbleShortcutAction.CaptureCurrentAppAndScreen -> captureCurrentAppAndScreen138()
            BubbleShortcutAction.CreateAlert -> saveCurrentPlaceFromBubble(SavedPlaceType.ProximityAlert, requireNotNull(spec.defaultName))
            BubbleShortcutAction.CreateSavedPlace -> saveCurrentPlaceFromBubble(SavedPlaceType.Place, requireNotNull(spec.defaultName))
            BubbleShortcutAction.ToggleReading -> toggleLiveReadingFromBubble()
        }
    }


// capture_teaches_app_and_triggers_farol_checklist_13
 // capture_teaches_app_and_triggers_farol_checklist_13


    // manual_card_capture_complete_checklist_12

    private fun captureCurrentAppAndScreen138() {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        if (!manualCaptureInProgress138.compareAndSet(false, true)) {
            toast("A captura manual já está em andamento.")
            return
        }
        val externalPackage = listOf(
            currentRootPackageName(),
            currentWindowPackageName(),
            recentSelectedRidePackageChecklist11,
            lastExternalWindowPackageName,
        ).firstNotNullOfOrNull { candidate ->
            normalizePackageName(candidate)?.takeUnless { it == packageName }
        }
        if (externalPackage == null) {
            manualCaptureInProgress138.set(false)
            toast("Abra o aplicativo que deseja capturar e tente novamente.")
            return
        }
        val visibleText = collectAllVisibleTextForCopy138()
        SelectedRideAppStore.save(
            applicationContext,
            SelectedRideAppStore.read(applicationContext) + externalPackage,
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !screenshotInProgress.compareAndSet(false, true)) {
            ManualAppScreenCaptureStore.save(applicationContext, externalPackage, visibleText, null)
            manualCaptureInProgress138.set(false)
            toast("Aplicativo e texto capturados")
            showSaveConfirmationNotification("Captura salva", externalPackage)
            return
        }
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        scope.launch {
                            var bitmap: Bitmap? = null
                            try {
                                bitmap = screenshot.toSoftwareBitmap()
                                ManualAppScreenCaptureStore.save(
                                    applicationContext,
                                    externalPackage,
                                    visibleText,
                                    bitmap,
                                )
                                toast("Aplicativo e tela capturados")
                                showSaveConfirmationNotification("Captura salva", externalPackage)
                            } finally {
                                bitmap?.recycle()
                                screenshotInProgress.set(false)
                                manualCaptureInProgress138.set(false)
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        ManualAppScreenCaptureStore.save(applicationContext, externalPackage, visibleText, null)
                        screenshotInProgress.set(false)
                        manualCaptureInProgress138.set(false)
                        toast("Aplicativo e texto capturados")
                    }
                },
            )
        }.onFailure {
            ManualAppScreenCaptureStore.save(applicationContext, externalPackage, visibleText, null)
            screenshotInProgress.set(false)
            manualCaptureInProgress138.set(false)
            toast("Aplicativo e texto capturados")
        }
    }

    private fun createManualRadarFromBubble138() {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        scope.launch {
            val coordinate = locationService.currentCoordinate()
            if (coordinate == null) {
                toast("Não foi possível obter a localização")
                return@launch
            }
            val duplicate = currentImportedRadars.any { radar ->
                radar.source.equals("Manual", ignoreCase = true) &&
                    GeoDistance.meters(radar.coordinate, coordinate) < 8.0
            }
            if (duplicate) {
                toast("Já existe um radar manual neste local")
                return@launch
            }
            val now = System.currentTimeMillis()
            val radar = ImportedRadar(
                id = "manual-radar-$now-${coordinate.latitude}-${coordinate.longitude}",
                coordinate = coordinate,
                type = 0,
                source = "Manual",
                createdAtMillis = now,
            )
            repository.replaceImportedRadars(listOf(radar) + currentImportedRadars)
            toast("Radar salvo")
            showSaveConfirmationNotification("Radar salvo", "Radar manual criado no local atual")
        }
    }

    private fun openNamedPlaceShortcut138(type: SavedPlaceType) {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        val group = if (type == SavedPlaceType.ProximityAlert) "alerts" else "saved_places"
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_OPEN_TAB, TAB_CONFIG)
                    .putExtra(EXTRA_OPEN_BUBBLE_GROUP, group)
                    .putExtra(EXTRA_CREATE_SAVED_PLACE_TYPE_138, type.name),
            )
        }.onFailure { toast("Não consegui abrir o cadastro agora.") }
    }

    private fun openDestinationConfirmationFromBubble138() {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_OPEN_TAB, TAB_ANALYSIS)
                    .putExtra(EXTRA_OPEN_BUBBLE_GROUP, "destination")
                    .putExtra(EXTRA_CONFIRM_DESTINATION_GPS_138, true),
            )
        }.onFailure { toast("Não consegui abrir a confirmação do destino.") }
    }

    private fun copyAllVisibleTextFromBubble138() {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        if (!fullScreenCopyInProgress138.compareAndSet(false, true)) {
            toast("A cópia completa da tela já está em andamento.")
            return
        }
        val accessibilityText = collectAllVisibleTextForCopy138()
        if (accessibilityText.isNotBlank()) {
            copyAllVisibleTextToClipboard138(accessibilityText)
            fullScreenCopyInProgress138.set(false)
            return
        }
        requestFullScreenCopyOcr138()
    }

    private fun collectAllVisibleTextForCopy138(): String {
        val root = rootInActiveWindow ?: return ""
        val lines = mutableListOf<String>()
        collectNodeText(root, lines)
        return lines
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
    }

    private fun requestFullScreenCopyOcr138() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            fullScreenCopyInProgress138.set(false)
            toast("Esta tela não disponibilizou texto para copiar.")
            return
        }
        if (!screenshotInProgress.compareAndSet(false, true)) {
            fullScreenCopyInProgress138.set(false)
            toast("A leitura da tela está ocupada. Tente novamente.")
            return
        }
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        scope.launch {
                            var bitmap: Bitmap? = null
                            try {
                                bitmap = screenshot.toSoftwareBitmap()
                                val text = bitmap?.let { ocrService.extractText(it) }.orEmpty()
                                    .lines()
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                                    .distinct()
                                    .joinToString("\n")
                                if (text.isBlank()) {
                                    toast("Nenhum texto foi encontrado nesta tela.")
                                } else {
                                    copyAllVisibleTextToClipboard138(text)
                                }
                            } finally {
                                bitmap?.recycle()
                                screenshotInProgress.set(false)
                                fullScreenCopyInProgress138.set(false)
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        screenshotInProgress.set(false)
                        fullScreenCopyInProgress138.set(false)
                        toast("O Android não permitiu ler esta tela.")
                    }
                },
            )
        }.onFailure {
            screenshotInProgress.set(false)
            fullScreenCopyInProgress138.set(false)
            toast("Não consegui solicitar a leitura desta tela.")
        }
    }

    private fun copyAllVisibleTextToClipboard138(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Texto completo da tela", text))
        toast("Texto completo copiado")
        overlayView?.announceForAccessibility("Texto completo da tela copiado")
    }

    private fun copyTripConfirmationFromBubbleChecklist8() {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        if (!tripConfirmationCopyInProgressChecklist8.compareAndSet(false, true)) {
            toast("A confirmação da viagem já está sendo preparada.")
            return
        }

        val accessibilityText = collectTripConfirmationVisibleTextChecklist8()
        val immediateMessage = TripConfirmationFormatter.extractAndFormat(accessibilityText)
        if (immediateMessage != null) {
            copyTripConfirmationToClipboardChecklist8(immediateMessage)
            tripConfirmationCopyInProgressChecklist8.set(false)
            return
        }
        requestTripConfirmationOcrChecklist8(accessibilityText, attempt = 0)
    }

    /**
     * Leitura manual, executada somente após o toque em Copiar viagem.
     * Não altera shouldScanPackage, não alimenta o farol e não fica em loop.
     */
    private fun collectTripConfirmationVisibleTextChecklist8(): String {
        val root = rootInActiveWindow ?: return ""
        val lines = mutableListOf<String>()
        collectNodeText(root, lines)
        return lines
            .asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString("\n")
    } // manual_trip_tree_read_checklist_8

    private fun requestTripConfirmationOcrChecklist8(accessibilityText: String, attempt: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            tripConfirmationCopyInProgressChecklist8.set(false)
            toast("Abra a conversa no BlaBlaCar deixando rota, dia e horário visíveis.")
            return
        }
        if (!screenshotInProgress.compareAndSet(false, true)) {
            if (attempt < 5) {
                scope.launch {
                    delay(120L)
                    requestTripConfirmationOcrChecklist8(accessibilityText, attempt + 1)
                }
            } else {
                tripConfirmationCopyInProgressChecklist8.set(false)
                toast("A leitura da tela está ocupada. Tente novamente em um instante.")
            }
            return
        }

        toast("Lendo os dados da viagem...")
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        scope.launch {
                            var bitmap: Bitmap? = null
                            try {
                                bitmap = screenshot.toSoftwareBitmap()
                                val ocrText = bitmap?.let { ocrService.extractText(it) }.orEmpty()
                                val combinedText = listOf(accessibilityText, ocrText)
                                    .filter(String::isNotBlank)
                                    .joinToString("\n")
                                val message = TripConfirmationFormatter.extractAndFormat(combinedText)
                                if (message == null) {
                                    toast("Não encontrei rota, dia e horário. Abra a conversa do passageiro no BlaBlaCar e tente novamente.")
                                } else {
                                    copyTripConfirmationToClipboardChecklist8(message)
                                }
                            } catch (_: Throwable) {
                                toast("Não consegui preparar a confirmação desta tela.")
                            } finally {
                                bitmap?.recycle()
                                screenshotInProgress.set(false)
                                tripConfirmationCopyInProgressChecklist8.set(false)
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        screenshotInProgress.set(false)
                        tripConfirmationCopyInProgressChecklist8.set(false)
                        toast("O Android não permitiu ler esta tela. Deixe rota, dia e horário visíveis e tente novamente.")
                    }
                },
            )
        }.onFailure {
            screenshotInProgress.set(false)
            tripConfirmationCopyInProgressChecklist8.set(false)
            toast("Não consegui solicitar a leitura manual da tela.")
        }
    }

    private fun copyTripConfirmationToClipboardChecklist8(message: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText("Confirmação da viagem", message),
        )
        toast("Confirmação copiada. Abra o WhatsApp e cole.")
        overlayView?.announceForAccessibility("Confirmação da viagem copiada")
    }

    // trip_confirmation_copy_complete_checklist_8

    private fun openQuickRepliesFromBubble(createNew: Boolean = false) {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        val targetPackage = listOf(currentRootPackageName(), currentWindowPackageName())
            .firstNotNullOfOrNull { candidate ->
                QuickReplyTargetPolicy.normalize(candidate)
                    ?.takeUnless { normalized -> normalized == packageName }
            }
        if (targetPackage == null) {
            toast("Abra primeiro a conversa onde deseja inserir a resposta.")
            return
        }
        quickReplyTargetPackageNameChecklist3 = targetPackage
        runCatching {
            startActivity(
                Intent(this, QuickRepliesActivity::class.java)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    )
                    .putExtra(EXTRA_QUICK_REPLY_TARGET_PACKAGE, targetPackage)
                    .putExtra(EXTRA_QUICK_REPLY_CREATE, createNew),
            )
        }.onFailure {
            toast("Não foi possível abrir as respostas rápidas.")
        }
    } // open_quick_replies_checklist_3

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
        Unit /* diagnostics_off_checklist_4 */
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
        Unit /* diagnostics_off_checklist_4 */
        Unit /* diagnostics_off_checklist_4 */
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
        Unit /* diagnostics_off_checklist_4 */
        Unit /* diagnostics_off_checklist_4 */
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
        radarDetectionCue.play()
        shortcutOverlayController.showImportedRadarAlert(radar, distanceMeters)
        persistResourceShortcutState()
        Unit /* diagnostics_off_checklist_4 */
    }

    private fun showSavedAlertPopup(alert: SavedPlace, distanceMeters: Double) {
        shortcutOverlayController.showProximityAlert(
            alert,
            distanceMeters,
            ProximityAlertPopupActions(
                onEdit = ::openSavedPlaceEditor,
                onDismiss = { proximityAlertEngine.dismissSavedPlaceUntilExit(alert.id) },
                onDelete = { place ->
                    scope.launch {
                        repository.removeSavedPlace(place.id)
                        toast("Alerta excluido.")
                        Unit /* diagnostics_off_checklist_4 */
                    }
                },
            ),
        )
        persistResourceShortcutState()
    }

    private fun collectPhoneVisibleTextChecklist11(): String {
        val root = rootInActiveWindow ?: return ""
        val linesChecklist11 = mutableListOf<String>()
        collectNodeText(root, linesChecklist11)
        return linesChecklist11.asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString("\n")
    } // manual_phone_tree_read_checklist_11

    private fun capturePhoneAndOpenWhatsApp118() {
        if (!phoneCaptureInProgress118.compareAndSet(false, true)) return
        Unit /* diagnostics_off_checklist_4 */

        val phoneVisibleTextChecklist11 = collectPhoneVisibleTextChecklist11()
        val directTarget = ScreenPhoneLink.findBest(phoneVisibleTextChecklist11)
            ?: ScreenPhoneLink.findBest(mergeRideTexts(phoneVisibleTextChecklist11, mergeRideTexts(lastAccessibilityText, lastOcrText)))
        if (directTarget != null) {
            phoneCaptureInProgress118.set(false)
            openWhatsAppTarget118(directTarget)
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            phoneCaptureInProgress118.set(false)
            toast("Nenhum telefone brasileiro com DDD foi encontrado na tela.")
            Unit /* diagnostics_off_checklist_4 */
            return
        }

        toast("Lendo o telefone da tela...")
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
                Unit /* diagnostics_off_checklist_4 */
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
                                val bitmap = screenshot.toSoftwareBitmap() ?: return@runCatching null
                                try {
                                    val ocrPhoneTextChecklist11 = ocrService.extractText(bitmap)
                                    ScreenPhoneLink.findBest(
                                        mergeRideTexts(collectPhoneVisibleTextChecklist11(), ocrPhoneTextChecklist11),
                                    )
                                } finally {
                                    bitmap.recycle()
                                }
                            }.getOrNull()
                            screenshotInProgress.set(false)
                            phoneCaptureInProgress118.set(false)
                            if (target != null) {
                                Unit /* diagnostics_off_checklist_4 */
                                openWhatsAppTarget118(target)
                            } else {
                                toast("Nenhum telefone brasileiro com DDD foi encontrado na tela.")
                                Unit /* diagnostics_off_checklist_4 */
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        screenshotInProgress.set(false)
                        phoneCaptureInProgress118.set(false)
                        toast("O Android nao permitiu ler a tela agora. Codigo: " + errorCode)
                        Unit /* diagnostics_off_checklist_4 */
                    }
                },
            )
        }.onFailure { error ->
            screenshotInProgress.set(false)
            phoneCaptureInProgress118.set(false)
            toast("Nao consegui capturar o telefone da tela.")
            Unit /* diagnostics_off_checklist_4 */
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
        Unit /* diagnostics_off_checklist_4 */
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

    private fun showSaveConfirmationNotification(title: String, text: String) {
        val manager = getSystemService(android.app.NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                android.app.NotificationChannel(
                    "rota_certa_saves",
                    "Confirmações do Rota Certa",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        val notification = androidx.core.app.NotificationCompat.Builder(this, "rota_certa_saves")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify((System.currentTimeMillis() and 0x7fffffff).toInt(), notification) }
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
        private var lastTapUpMillis = 0L
        private var pendingSingleTapJob: kotlinx.coroutines.Job? = null
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
                    Unit /* diagnostics_off_checklist_4 */
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
                        Unit /* diagnostics_off_checklist_4 */
                    } else {
                        val tapAt = event.eventTime
                        val timeout = android.view.ViewConfiguration.getDoubleTapTimeout().toLong()
                        if (lastTapUpMillis > 0L && tapAt - lastTapUpMillis <= timeout) {
                            pendingSingleTapJob?.cancel()
                            pendingSingleTapJob = null
                            lastTapUpMillis = 0L
                            shortcutOverlayController.hideShortcuts()
                            persistResourceShortcutState()
                            saveCurrentPlaceFromBubble(SavedPlaceType.ProximityAlert, "Alerta")
                        } else {
                            lastTapUpMillis = tapAt
                            pendingSingleTapJob?.cancel()
                            pendingSingleTapJob = scope.launch {
                                delay(timeout)
                                if (lastTapUpMillis == tapAt) {
                                    lastTapUpMillis = 0L
                                    view.performClick()
                                }
                            }
                        }
                    }
                    scope.launch {
                        delay(BubbleDragPolicy.ANALYSIS_RESUME_DELAY_MS)
                        if (!bubbleGestureActive) scheduleVisibleTextAnalysis(delayMs = 0L)
                    }
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    bubbleGestureActive = false
                    Unit /* diagnostics_off_checklist_4 */
                    return true
                }
            }
            return true
        }
    } // bubble_instant_drag_0_1_116

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    @androidx.annotation.RequiresApi(30)
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
        const val DIRECTIONAL_ALERT_ACTIVE_LOOP_MILLIS_CHECKLIST_5 = 500L
        const val DIRECTIONAL_ALERT_IDLE_LOOP_MILLIS_CHECKLIST_5 = 1_500L
        const val PRECISE_FIX_OVERLAY_GRACE_MILLIS_CHECKLIST_5 = 1_800L
        const val DIRECTIONAL_RADAR_QUERY_BUFFER_METERS_CHECKLIST_5 = 220.0
        const val SCAN_LOOP_MS = 350L // adaptive_fallback_scan_0_1_127
        const val SCREENSHOT_INTERVAL_MS = 300L
        const val OCR_RESULT_MAX_AGE_MS = 1_400L
        const val LIVE_RESULT_MAX_AGE_MS = 7_500L
        const val PROXIMITY_ALERT_LOOP_MS = 2_000L
        const val LIVE_ANALYSIS_TIMEOUT_MS = 8_000L
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
        const val KEY_STATE_SERVICE_READY = "state_service_ready"
        const val KEY_STATE_ANALYZING = "state_analyzing"
        const val KEY_STATE_ACCESSIBILITY_TEXT_LENGTH = "state_accessibility_text_length"
        const val KEY_STATE_ACCESSIBILITY_TEXT_HASH = "state_accessibility_text_hash"
        const val KEY_STATE_OCR_TEXT_LENGTH = "state_ocr_text_length"
        const val KEY_STATE_OCR_TEXT_HASH = "state_ocr_text_hash"
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

