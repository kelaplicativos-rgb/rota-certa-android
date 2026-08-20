package br.com.mapeiaia.rotacerta

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.app.ActivityOptions
import android.app.PendingIntent
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
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.roundToInt

class LiveRideAccessibilityService : AccessibilityService() {
    private val serviceSupervisor0172 = SupervisorJob()
    private val coroutineExceptionHandler0172 = CoroutineExceptionHandler { _, error ->
        containUnexpectedFailure0172("root_coroutine_0172", error)
    }
    private val scope = CoroutineScope(serviceSupervisor0172 + Dispatchers.Main.immediate + coroutineExceptionHandler0172)
    private var intensiveDiagnosticJob0172: Job? = null
    private var intensiveDiagnosticReceiverRegistered0172 = false
    private val intensiveDiagnosticReceiver0172 = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_INTENSIVE_DIAGNOSTIC_CONTROL_0172) return
            startIntensiveDiagnosticLoop0172()
        }
    }
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
    private val notificationWakeGate0169 = FarolNotificationWakeGate0169()
    private var notificationWakeJob0169: Job? = null
    private val notificationFailureCircuit0170 = FarolNotificationFailureCircuit0170()
    // farol_notification_crash_containment_0_1_170
    // farol_notification_wakeup_0_1_169
    private val driverCardSessionGate0162 = DriverCardSessionGate0162()
    private var workModeRuntimeActive0162 = false
    private var workModeSettingsReady0162 = false
    private var lastRejectedForegroundPackage0162: String? = null
    private val externalPackageEventGate0187 = FarolExternalPackageEventGate0187()
    private val failedCardAutoCaptureGate0161 = FailedCardAutoCaptureGate0161()
    private lateinit var failedCardLayoutModelStore0161: FailedCardLayoutModelStore0161
    private var lastFailedCardNodes0161 = emptyList<FailedCardNodeLine0161>()
    private var lastFailedCardSignature0161: String? = null
    private var lastFailedCardAccessibilityHash0161: Int? = null
    private val tripConfirmationCopyInProgressChecklist8 = AtomicBoolean(false)
    private val passengerValueCaptureInProgress159 = AtomicBoolean(false)
    private val passengerValueCaptureGeneration160 = java.util.concurrent.atomic.AtomicLong(0L)
    private val passengerValueScreenshotOwner160 = java.util.concurrent.atomic.AtomicLong(0L)
    @Volatile private var passengerValueCaptureStartedAt160: Long = 0L
    private val fullScreenCopyInProgress138 = AtomicBoolean(false)
    private val manualCaptureInProgress138 = AtomicBoolean(false)
    private val shortcutActivityLaunchRequestCode0176 = AtomicInteger(17_600)
    private var farolCriticalStartedAtFinalChecklist6: Long = 0L // subsecond_fields_final_checklist_6
    private val phoneCaptureInProgress118 = AtomicBoolean(false)
    private var analyzeJob: Job? = null
    private var screenshotFallbackJob127: Job? = null // deferred_ocr_job_0_1_127
    private var stage19OcrSerial: Long = 0L
    private var stage19OcrRerunRequested: Boolean = false
    private var stage19VisualVerificationPending: Boolean = false
    private var stage19ActiveWindowId: Int? = null
    private var stage19ActiveBlockId: String? = null
    private var stage20LastCycleId: Long = 0L
    private var stage20ExpectedPaintToken: FarolForensicTraceStage20.PaintToken? = null
    private val stage21EventGate = FarolCausalCorrectionStage21.EventGate()
    private val stage21OcrGate = FarolCausalCorrectionStage21.OcrGate()
    private var stage21SelfEventSuppressionUntilNs: Long = 0L
    private val stage23VisualGate = FarolVisualIdentityStage23.VisualSnapshotGate()
    private val stage23ScheduleGate = FarolVisualIdentityStage23.ScheduledDemandGate()
    private val stage23OcrGate = FarolVisualIdentityStage23.OcrDemandGate()
    private val stage32SemanticGate = FarolSemanticCardStage32.SemanticGate()
    private val stage32ScreenshotRateGate = FarolSemanticCardStage32.ScreenshotRateGate()
    private val printInProgressStage32 = AtomicBoolean(false)
    // FAROL_SEMANTIC_CARD_GENERATION_STAGE32 — raw Accessibility churn cannot cancel useful OCR without semantic proof.
    // FAROL_FORENSIC_CARD_BLACK_BOX_STAGE32 — CASE provenance/outcome is diagnostic only, never visual authority.
    // FAROL_REAL_PRINT_MEDIASTORE_STAGE32 — explicit user Print is separate from OCR and forensic CASE capture.
    private val stage26ReadingActivation = FarolReadingActivationStage26.ActivationMachine()
    private val stage26PreCollectGate = FarolReadingActivationStage26.PreCollectGate()
    private lateinit var stage30PresenceState: SelectedAppPresenceStateStage30
    private lateinit var stage30PresenceAuthority: FarolPresenceAuthorityStage30.Authority
    private lateinit var stage36RuntimeAuthority: FarolRuntimeAuthorityStage36.Authority
    // FAROL_PRESENCE_AUTHORITY_STAGE30 — process state is shadow only; visual package never authorizes content.
    private val stage28RouteGate = FarolCausalLatencyStage28.RouteGate()
    private var stage28LastActivationEnabled = false
    // FAROL_CAUSAL_LATENCY_STALE_ACTIVATION_STAGE28 — cheap current authority, no visual package gate.
    private var stage26UsageInitialized = false
    private var stage26LastAppliedActivationGeneration = -1L
    private var stage26CurrentVisualGeneration = 0L
    private var stage26CandidateEventStartedNs = 0L
    private var stage26CandidateActivationGeneration = -1L
    private var stage26OcrActivationGeneration = -1L
    private var stage26RouteResponseNs = 0L
    private val stage36BindingWorkToken = LinkedHashMap<String, FarolRuntimeAuthorityStage36.WorkToken>()
    private val stage46BindingSurfaceToken = LinkedHashMap<String, FarolVisualEpochNoResultStage46.SurfaceToken>()
    private var stage46VisualEpoch = 0L
    private var stage46LastHardBoundaryGeneration = Long.MIN_VALUE
    private var stage46TargetSourcePackage: String? = null
    private var stage46TargetWindowId: Int = 0
    // FAROL_SINGLE_DESTINATION_FAST_PATH_STAGE46_R6 service integration
    // FAROL_IMMEDIATE_ADDRESS_ROUTE_STAGE46_R7 service integration
    // FAROL_POSITIVE_LOCATION_EVIDENCE_STAGE46_R8 service integration
    private val stage46AcquisitionSurfaceByWindowId = LinkedHashMap<Int, Pair<Long, String>>()
    // FAROL_READING_ACTIVATION_STAGE26 — selected apps gate infrastructure; package never authorizes card content
    // FAROL_VISUAL_IDENTITY_COALESCING_STAGE23 — retained as post-collect safety/freshness layer
    // FAROL_CAUSAL_CORRECTION_STAGE21 — semantic barrier/freshness predecessor retained
    // FAROL_FORENSIC_CAUSALITY_STAGE20 — diagnostic only, never authority
    // UNIVERSAL_VISUAL_AUTHORITY_STAGE19
    private var scheduledOcrIdentity0189: String? = null
    private var lastOcrAttemptIdentity0189: String? = null
    private var lastOcrAttemptAtMillis0189: Long = 0L
    private var lastAccessibilityAcceptedAtMillis127: Long = 0L // accessibility_first_timestamp_0_1_127
    private var overlayView: TextView? = null
    private var savedPlacePopupView: LinearLayout? = null
    private var shortcutModulePopupView0181: LinearLayout? = null
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
    private var currentSettings = WorkModePolicy0162.setEnabled(AppSettings(), false)
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
    private lateinit var speechOutputStore0186: SpeechOutputPreferenceStore0186
    private lateinit var proximityAlertEngine: ProximityAlertEngine
    private lateinit var preciseNavigationTrackerChecklist5: PreciseNavigationTracker
    private lateinit var directionalAlertEngineChecklist5: DirectionalProximityAlertEngine
    @Volatile private var lastDirectionalFix0184: PreciseNavigationFix? = null
    private lateinit var directionalAlertOverlayChecklist5: DirectionalAlertOverlayController
    private val directionalRadarSpatialIndexChecklist5 = ImportedRadarSpatialIndex()
    private var missingPreciseFixSinceChecklist5: Long = 0L
    // directional_alert_fields_checklist_5
    private lateinit var shortcutOverlayController: BubbleShortcutOverlayController
    private lateinit var shortcutGridStore0179: ShortcutGridPreferenceStore0179
    private lateinit var radarDetectionCue: RadarDetectionCue
    private val universalRouteCache = LiveRideRouteCache()
    private var universalRouteJob: Job? = null
    private var stage16TransientEmptyBinding: FarolVisibleCardPriorityStage16.ActiveCardBinding? = null
    private var stage16AcceptedGateSnapshot: FarolVisibleCardPriorityStage16.GateSnapshotIdentity? = null
    private var stage16AcceptedGateAuthorization: FarolRouteAuthorization0188? = null
    private var universalScreenGeneration: Long = 0L
    private var universalWindowGeneration: Long = 0L // universal_ocr_window_generation_0_1_120
    private var universalLastActiveReadAtElapsedMillis0187: Long = 0L
    private var universalActiveRidePackageName: String? = null // universal_route_inflight_runtime_0_1_120
    private var universalActiveAddressSignature: String? = null // universal_two_address_fields_0_1_98
    private var lastImmediateScreenFingerprintChecklist13: Int? = null
    private var lastImmediateScreenPackageChecklist13: String? = null
    private var fastFarolStartedAtChecklist13: Long = 0L // simple_saved_app_fields_checklist_13
    private var lastStableFarolPackageChecklist14: String? = null
    private var lastStableFarolWindowIdChecklist14: Int? = null
    private var partialReadConfirmationJobChecklist14: Job? = null
    private var activeAnalysisPackage143: String? = null
    private var activeAnalysisHash143: Int? = null
    private var activeAnalysisStartedAtElapsedMillis0187: Long = 0L
    private val farolRealtimeEventGate0167 = FarolRealtimeEventGate0167()
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
    private val STABLE_DECISION_ABSENCE_GRACE_MILLIS_141 = 3_000L
    private val coreBubblePresenter = br.com.mapeiaia.rotacerta.core.CoreBubblePresenter

    override fun onCreate() {
        super.onCreate()
        try {
            initializeService0172()
        } catch (error0172: Exception) {
            containLifecycleFailure0172("service_create_0172", error0172)
        }
    }

    private fun initializeService0172() {
        FarolFlightRecorder0163.initialize(applicationContext) // farol_flight_recorder_init_0_1_163
        FarolMaximumForensicsStage38.record(
            atNs = SystemClock.elapsedRealtimeNanos(), wallMs = System.currentTimeMillis(),
            stage = "S38_SERVICE_INITIALIZE", packageName = packageName,
            details = "version=${BuildConfig.VERSION_NAME}; code=${BuildConfig.VERSION_CODE}; diagnostic_only=true; no_timer=true",
        )
        if (!quickReplyReceiverRegisteredChecklist3) {
            ContextCompat.registerReceiver(
                this,
                quickReplyReceiverChecklist3,
                IntentFilter(ACTION_APPLY_QUICK_REPLY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            quickReplyReceiverRegisteredChecklist3 = true
        } // quick_reply_receiver_registration_checklist_3
        if (!intensiveDiagnosticReceiverRegistered0172) {
            ContextCompat.registerReceiver(
                this,
                intensiveDiagnosticReceiver0172,
                IntentFilter(ACTION_INTENSIVE_DIAGNOSTIC_CONTROL_0172),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            intensiveDiagnosticReceiverRegistered0172 = true
        }
        repository = SettingsRepository(applicationContext)
        failedCardLayoutModelStore0161 = FailedCardLayoutModelStore0161(applicationContext)
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
        ShortcutGridPolicy0173.clearLegacyPreferences(applicationContext)
        shortcutGridStore0179 = ShortcutGridPreferenceStore0179(applicationContext)
        val restoredExactRoutes = universalRouteCache.importSnapshot(
            bubblePrefs.getString("persistent_exact_route_cache_v1", "").orEmpty(),
        )
        Unit /* diagnostics_off_checklist_4 */ // persistent_route_cache_restore_0_1_124
        speechOutputStore0186 = SpeechOutputPreferenceStore0186(applicationContext)
        textToSpeech = TextToSpeech(applicationContext) { status ->
            textToSpeechReady = status == TextToSpeech.SUCCESS
            if (textToSpeechReady) textToSpeech?.language = Locale("pt", "BR")
        }
        speechEngine = LiveSpeechEngine(
            textToSpeechProvider = { textToSpeech },
            isReady = { textToSpeechReady },
            trace = ::traceEvent,
            outputModeProvider0186 = { speechOutputStore0186.read() },
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
        try {
            connectService0172()
        } catch (error0172: Exception) {
            containLifecycleFailure0172("service_connected_0172", error0172)
        }
    }

    private fun connectService0172() {
        serviceReady = true
        stage30PresenceState = SelectedAppPresenceStateStage30(applicationContext)
        stage30PresenceAuthority = FarolPresenceAuthorityStage30.Authority(stage30PresenceState.sessionStartWallMillis)
        stage36RuntimeAuthority = FarolRuntimeAuthorityStage36.Authority(stage30PresenceState.sessionStartWallMillis)
        UnifiedDebugEventStore.record("SERVICE_CONNECTED", packageName, "serviço pronto=true")
        persistBubbleState()
        startIntensiveDiagnosticLoop0172()
        Unit
        scope.launch {
            repository.settings.collect { updatedStage43 ->
                if (!workModeSettingsReady0162) return@collect
                applyPersistedManualReadingStage43(updatedStage43, "settings_flow")
            }
        }
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
            val selectedBefore0162 = SelectedRideAppStore.read(applicationContext)
            SelectedRideAppStore.save(applicationContext, selectedBefore0162)
            val migrationPrefs0162 = getSharedPreferences("rota_certa_runtime_migrations", Context.MODE_PRIVATE)
            if (!migrationPrefs0162.getBoolean("work_mode_default_off_0_1_162", false)) {
                currentSettings = WorkModePolicy0162.setEnabled(currentSettings, false)
                repository.saveSettings(currentSettings)
                migrationPrefs0162.edit().putBoolean("work_mode_default_off_0_1_162", true).apply()
            }
            workModeSettingsReady0162 = true
            // pre_registered_runtime_cleanup_0_1_126 superseded_by_manual_selection_0_1_127
            applyPersistedManualReadingStage43(currentSettings, "service_bootstrap", forceStage43 = true)
            // WhatsApp agora fica dentro da central da bolinha. // whatsapp_inside_grid_0_1_94
            startContinuousScan()
            startProximityAlertMonitor()
        }
    }

    private data class FarolRootHandle0187(
        val node: AccessibilityNodeInfo,
        val packageName: String?,
        val windowId: Int?,
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val safeEvent0172 = event ?: return
        try {
            handleAccessibilityEvent0172(safeEvent0172)
        } catch (error0172: Exception) {
            containUnexpectedFailure0172(
                stage0172 = "accessibility_event_0172",
                error0172 = error0172,
                packageName0172 = runCatching { safeEvent0172.packageName?.toString() }.getOrNull(),
            )
        }
    }

    private fun handleAccessibilityEvent0172(event: AccessibilityEvent) {
        val stage38EventNs = SystemClock.elapsedRealtimeNanos()
        val stage38EventPackage = normalizePackageName(runCatching { event.packageName?.toString() }.getOrNull())
        val stage38EventText = runCatching { event.text.joinToString(" || ") }.getOrDefault("")
        val stage38Source = runCatching { event.source }.getOrNull()
        FarolMaximumForensicsStage38.record(
            atNs = stage38EventNs, wallMs = System.currentTimeMillis(),
            stage = "S38_ACCESSIBILITY_EVENT_RECEIVED", packageName = stage38EventPackage,
            details = "type=${runCatching { event.eventType }.getOrDefault(0)}; window=${runCatching { event.windowId }.getOrDefault(0)}; contentChangeTypes=${runCatching { event.contentChangeTypes }.getOrDefault(0)}; action=${runCatching { event.action }.getOrDefault(0)}; eventTimeMs=${runCatching { event.eventTime }.getOrDefault(0L)}; class=${runCatching { event.className?.toString() }.getOrNull().orEmpty()}; sourcePackage=${runCatching { stage38Source?.packageName?.toString() }.getOrNull().orEmpty()}; sourceViewId=${runCatching { stage38Source?.viewIdResourceName }.getOrNull().orEmpty()}; eventText=${stage38EventText.take(900)}",
        )
        if (!serviceReady) {
            FarolMaximumForensicsStage38.record(SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_EVENT_REJECT", stage38EventPackage, details = "reason=service_not_ready")
            return
        }
        if (!WorkModePolicy0162.isEnabled(currentSettings)) {
            applyWorkModeRuntime0162(false)
            return
        }
        val eventType0187 = runCatching { event.eventType }.getOrDefault(0)
        if (!AccessibilityEventFloodGate.isRelevantEventType(eventType0187)) return
        val eventPackage = normalizePackageName(runCatching { event.packageName?.toString() }.getOrNull())
        // Stage19 owns the visual critical path before package/root/model gates.
        val eventWindowIdStage20 = runCatching { event.windowId }.getOrNull() ?: 0
        if (handleUniversalVisualEventStage19(eventPackage, eventType0187, eventWindowIdStage20, event)) return
        val eventClassName0187 = runCatching { event.className?.toString() }.getOrNull()
        val eventWindowId0187 = runCatching { event.windowId }.getOrDefault(0)
        val selectedPackages156 = SelectedRideAppStore.read(applicationContext)
        if (eventType0187 == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val now0170 = SystemClock.elapsedRealtime()
            if (!notificationFailureCircuit0170.canAttempt(now0170)) return
            try {
                handleNotificationWakeup0169(event, eventPackage, selectedPackages156)
            } catch (error0170: Exception) {
                notificationFailureCircuit0170.onFailure(now0170)
                containNotificationWakeupFailure0170(
                    stage0170 = "notification_event_entry_0170",
                    packageName0170 = eventPackage,
                    error0170 = error0170,
                )
            }
            return
        }
        val visibleRootResolutionStage16 = resolveVisibleAuthorizedRootStage16(selectedPackages156)
        val visibleSelectedRootStage16 = visibleRootResolutionStage16.rootHandle
        val activeSessionBeforeIdentityStage18 = driverCardSessionGate0162.current()
        val identityResolutionStage18 = FarolAppIdentityIsolationStage18.resolve(
            eventPackageName = eventPackage,
            visibleSelectedPackageName = visibleSelectedRootStage16?.packageName,
            selectedPackages = selectedPackages156,
            activeSessionPackageName = activeSessionBeforeIdentityStage18?.packageName,
        )
        if (identityResolutionStage18.failClosed) {
            UnifiedDebugEventStore.record(
                "BUBBLE_IDENTITY_FAIL_CLOSED_STAGE18",
                identityResolutionStage18.explicitSelectedPackageName ?: eventPackage.orEmpty(),
                "outcome=${identityResolutionStage18.outcome}; eventPackage=${eventPackage.orEmpty()}; visibleSelectedPackage=${visibleSelectedRootStage16?.packageName.orEmpty()}; activeSession=${activeSessionBeforeIdentityStage18?.packageName.orEmpty()}",
            )
            identityResolutionStage18.explicitSelectedPackageName?.let(::scheduleScreenshotFallback127)
            return
        }
        if (identityResolutionStage18.confirmedAppSwitch) {
            UnifiedDebugEventStore.record(
                "BUBBLE_APP_IDENTITY_SWITCH_STAGE18",
                identityResolutionStage18.authorityPackageName.orEmpty(),
                "from=${activeSessionBeforeIdentityStage18?.packageName.orEmpty()}; to=${identityResolutionStage18.authorityPackageName.orEmpty()}; old session/card/OCR/cache/route invalidated before new authority",
            )
            driverCardSessionGate0162.invalidate()
            clearStage16VisualProof()
            universalRouteJob?.cancel()
            hardClearUniversalTwoAddress(
                reason = "Troca confirmada de aplicativo de corrida; autoridade anterior invalidada antes da nova leitura.",
                keepWaitingYellow = true,
            )
        }
        val visualAuthorityOverridesEventStage16 = identityResolutionStage18.allowVisibleRootOverride &&
            visibleSelectedRootStage16 != null &&
            (normalizePackageName(eventPackage) != normalizePackageName(visibleSelectedRootStage16.packageName) ||
                eventWindowId0187 != visibleSelectedRootStage16.windowId)
        if (!visualAuthorityOverridesEventStage16 && ExplicitPackageTransitionPolicy0185.shouldReject(
                eventPackageName = eventPackage,
                selectedPackages = selectedPackages156,
                ownPackageName = packageName,
                isTransientOverlay = { candidate0185 ->
                    DriverAppPackagePolicy0162.isTransientOverlay(candidate0185, packageName)
                },
            )
        ) {
            val alreadyIdle0187 = currentRadarColor == RadarColor.Idle &&
                currentDistanceKm == null &&
                universalActiveAddressSignature == null &&
                driverCardSessionGate0162.current() == null
            if (externalPackageEventGate0187.shouldHandle(
                    packageName = eventPackage,
                    windowId = eventWindowId0187,
                    eventType = eventType0187,
                    alreadyIdle = alreadyIdle0187,
                    nowElapsedMillis = SystemClock.elapsedRealtime(),
                )
            ) {
                UnifiedDebugEventStore.record(
                    "EXPLICIT_EXTERNAL_PACKAGE_REJECTED_0185",
                    eventPackage,
                    "pacote explícito externo rejeitado antes de consultar raiz possivelmente antiga; event=$eventType0187; window=$eventWindowId0187",
                )
                handleRejectedForeground0162(eventPackage, eventPackage, eventType0187, eventWindowId0187)
            }
            return
        }
        val rootHandle0187 = visibleSelectedRootStage16 ?: captureRootHandle0187()
        val rootPackage = rootHandle0187?.packageName
        if (rootPackage != null && (selectedPackages156.contains(rootPackage) || rootPackage == packageName)) {
            cancelNotificationWakeup0169()
        }
        // Compatibilidade funcional: TransientOverlayPackagePolicy0161.shouldPreferSelectedRoot
        // agora é aplicada pelo resolvedor estrito da sessão imutável 0.1.162.
        val candidatePackage = visibleSelectedRootStage16?.packageName ?: DriverCardEventResolver0162.resolve(
            eventPackageName = eventPackage,
            rootPackageName = rootPackage,
            selectedPackages = selectedPackages156,
            ownPackageName = packageName,
        )
        val transientOverlayEvent151 = visualAuthorityOverridesEventStage16 || (eventPackage != null &&
            DriverAppPackagePolicy0162.isTransientOverlay(eventPackage, packageName) &&
            candidatePackage != null)
        if (candidatePackage == null) {
            handleRejectedForeground0162(eventPackage, rootPackage, eventType0187, eventWindowId0187)
            return
        }
        val activeSessionBeforeRootGate0187 = driverCardSessionGate0162.current()
        val visibleWindowTransitionStage16 = visibleSelectedRootStage16 != null &&
            activeSessionBeforeRootGate0187?.packageName == candidatePackage &&
            activeSessionBeforeRootGate0187.windowId != visibleSelectedRootStage16.windowId
        val admissionSessionStage16 = activeSessionBeforeRootGate0187.takeUnless { visibleWindowTransitionStage16 }
        val rootAdmission0187 = FarolRootSnapshotPolicy0187.evaluate(
            eventPackageName = if (visualAuthorityOverridesEventStage16) null else eventPackage,
            selectedPackageName = candidatePackage,
            rootPackageName = rootPackage,
            eventWindowId = if (visualAuthorityOverridesEventStage16) -1 else eventWindowId0187,
            rootWindowId = rootHandle0187?.windowId,
            transientOverlayEvent = transientOverlayEvent151,
            activeSessionPackageName = admissionSessionStage16?.packageName,
            activeSessionWindowId = admissionSessionStage16?.windowId,
        )
        if (!rootAdmission0187.accepted || rootHandle0187 == null) {
            val rejectionEffect0187Phase3 = FarolRejectedSnapshotPolicy0187Phase3.effect(rootAdmission0187.reason)
            UnifiedDebugEventStore.record(
                "BUBBLE_ROOT_SNAPSHOT_REJECTED_0187",
                candidatePackage,
                "reason=${rootAdmission0187.reason}; effect=$rejectionEffect0187Phase3; eventPackage=${eventPackage ?: "none"}; rootPackage=${rootPackage ?: "none"}; eventWindow=$eventWindowId0187; rootWindow=${rootHandle0187?.windowId ?: 0}",
            )
            when (rejectionEffect0187Phase3) {
                FarolRejectedSnapshotEffect0187Phase3.DISCARD_WITHOUT_EFFECT -> {
                    UnifiedDebugEventStore.record(
                        "BUBBLE_ROOT_SNAPSHOT_DISCARDED_0187_PHASE3",
                        candidatePackage,
                        "reason=${rootAdmission0187.reason}; visualPreserved=true; color=$currentRadarColor; distance=$currentDistanceKm",
                    )
                }

                FarolRejectedSnapshotEffect0187Phase3.INVALIDATE_READ_KEEP_VISUAL -> {
                    invalidateRejectedSnapshotRead0187Phase3(rootAdmission0187.reason, candidatePackage)
                }
            }
            return
        }
        lastRejectedForegroundPackage0162 = null
        externalPackageEventGate0187.reset()
        val ownMainActivityEvent = UniversalWindowPackageResolver.isOwnMainActivityEvent(
            eventPackageName = candidatePackage,
            eventClassName = eventClassName0187,
            eventType = eventType0187,
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
        if (!visualAuthorityOverridesEventStage16 && eventPackage == this.packageName && !ownMainActivityEvent) return

        val realtimeWindowId0167 = rootHandle0187.windowId ?: eventWindowId0187
        if (!farolRealtimeEventGate0167.shouldCollect(
                selectedPackageName = candidatePackage,
                sourcePackageName = if (visualAuthorityOverridesEventStage16) candidatePackage else eventPackage,
                windowId = realtimeWindowId0167,
                eventType = eventType0187,
                eventClassName = eventClassName0187,
                nowElapsedMillis = SystemClock.elapsedRealtime(),
            )
        ) return
        UnifiedDebugEventStore.record(
            stage = "ACCESSIBILITY_EVENT",
            packageName = eventPackage,
            details = "type=$eventType0187; class=${eventClassName0187 ?: "nao informado"}; window=$eventWindowId0187; serviceReady=$serviceReady; admitted0167=true",
        )
        UnifiedDebugEventStore.record(
            "BUBBLE_EVENT_RESOLVED",
            candidatePackage,
            "eventPackage=${eventPackage ?: "nao informado"}; rootPackage=${rootPackage ?: "nao informado"}; window=${eventWindowId0187}",
        )

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
            val now154 = System.currentTimeMillis()
            val stableSelectedPackage154 = universalActiveRidePackageName
                ?.takeIf { it in savedPackages }
                ?: recentSelectedRidePackageChecklist11?.takeIf { it in savedPackages }
            val transientOverlayOrLauncher154 =
                stableSelectedPackage154 != null &&
                    universalActiveAddressSignature != null &&
                    (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red) &&
                    now154 - recentSelectedRidePackageAtMillisChecklist11 in 0L..2_000L &&
                    (eventPackage == null ||
                        eventPackage == this.packageName ||
                        eventPackage == "com.android.systemui" ||
                        eventPackage == "com.samsung.android.app.smartcapture" ||
                        TransientOverlayPackagePolicy0161.isTransient(eventPackage) ||
                        resolvedPackage.contains("launcher", ignoreCase = true))
            if (transientOverlayOrLauncher154) {
                UnifiedDebugEventStore.record(
                    "BUBBLE_PASSIVE_TRANSITION_DEFERRED",
                    stableSelectedPackage154,
                    "evento transitório=$eventPackage; raiz=$rootPackage; resolvido=$resolvedPackage; decisão válida preservada",
                )
                universalForegroundPackageName = stableSelectedPackage154
                activePackageName = stableSelectedPackage154
                lastExternalWindowPackageName = stableSelectedPackage154
                return
            }
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

        val immediateTextChecklist13 = FarolLatencyProbeStage9.measureText(
            stage = "ACCESSIBILITY_IMMEDIATE_TEXT",
            source = "Accessibility",
        ) {
            collectImmediateVisibleTextChecklist13(rootHandle0187.node)
        }
        val cardEvidence0185 = RideCardConfirmationPolicy0185.prepare(
            packageName = resolvedPackage,
            rawText = immediateTextChecklist13,
        )
        if (cardEvidence0185.rejectedFeed) {
            UnifiedDebugEventStore.record(
                "BUBBLE_UNCONFIRMED_CARD_REJECTED_0185",
                resolvedPackage,
                "motivo=${cardEvidence0185.reason}; tamanho=${immediateTextChecklist13.length}; hash=${FarolUnifiedVisual0168.semanticHash(immediateTextChecklist13)}",
            )
            lastImmediateScreenPackageChecklist13 = resolvedPackage
            lastImmediateScreenFingerprintChecklist13 = FarolUnifiedVisual0168.semanticHash(immediateTextChecklist13)
            hardClearUniversalTwoAddress(
                reason = cardEvidence0185.reason,
                keepWaitingYellow = true,
            )
            return
        }
        val immediateAnalysisText0185 = cardEvidence0185.analysisText
        val activeRootWindowId0166 = rootHandle0187.windowId
        val stableWindowId151 = FarolSelectedAppInputPolicy0166.resolveStableWindowId(
            eventPackageName = if (visualAuthorityOverridesEventStage16) resolvedPackage else eventPackage,
            rootPackageName = rootPackage,
            selectedPackageName = resolvedPackage,
            eventWindowId = if (visualAuthorityOverridesEventStage16) activeRootWindowId0166 ?: eventWindowId0187 else eventWindowId0187,
            rootWindowId = activeRootWindowId0166,
            lastStableWindowId = lastStableFarolWindowIdChecklist14,
        )
        val selectedRootWindowIsStable0166 =
            (rootPackage == resolvedPackage && activeRootWindowId0166 != null) || eventPackage == resolvedPackage
        if (!transientOverlayEvent151 && selectedRootWindowIsStable0166) {
            lastStableFarolPackageChecklist14 = resolvedPackage
            lastStableFarolWindowIdChecklist14 = stableWindowId151
        }
        val sessionToken0162 = ensureDriverCardSession0162(resolvedPackage, stableWindowId151)
        val readBinding0187 = FarolReadBinding0187(
            packageName = resolvedPackage,
            sessionGeneration = sessionToken0162.generation,
            windowId = stableWindowId151,
            screenGeneration = universalScreenGeneration,
            windowGeneration = universalWindowGeneration,
        )
        val quickEvaluationChecklist13 = SimpleSavedAppFarolPolicy.evaluate(
            packageName = resolvedPackage,
            savedPackages = savedPackages,
            text = DriverCardTextSanitizer0162.prepare(resolvedPackage, immediateAnalysisText0185),
        )
        val fingerprintChecklist13 = DriverCardDisplayIdentity0162.fingerprint(
            packageName = resolvedPackage,
            windowId = stableWindowId151,
            activeAddressSignature = quickEvaluationChecklist13.addressSignature.takeIf { quickEvaluationChecklist13.active },
        )
        UnifiedDebugEventStore.record(
            "BUBBLE_TEXT_COLLECTED",
            resolvedPackage,
            "fonte=acessibilidade_imediata; tamanhoBruto=${immediateTextChecklist13.length}; tamanhoAnalisado=${immediateAnalysisText0185.length}; hash=${immediateAnalysisText0185.hashCode()}; window=${eventWindowId0187}; fingerprint=$fingerprintChecklist13; cardConfirmado=${cardEvidence0185.confirmedIndividualCard}",
        )
        val screenChangedChecklist13 = !transientOverlayEvent151 &&
            lastImmediateScreenPackageChecklist13 != null &&
            (lastImmediateScreenPackageChecklist13 != resolvedPackage ||
                SimpleSavedAppFarolPolicy.changed(lastImmediateScreenFingerprintChecklist13, fingerprintChecklist13))
        if (screenChangedChecklist13) {
            UnifiedDebugEventStore.record("BUBBLE_SCREEN_CHANGED", resolvedPackage, "fingerprintAnterior=$lastImmediateScreenFingerprintChecklist13; fingerprintAtual=$fingerprintChecklist13; window=${eventWindowId0187}")
            val preserveStableDecision141 =
                universalActiveRidePackageName == resolvedPackage &&
                    universalActiveAddressSignature != null &&
                    (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red)
            val preserveRouteInFlight143 =
                universalActiveRidePackageName == resolvedPackage &&
                    universalActiveAddressSignature != null &&
                    universalRouteJob?.isActive == true
            val recentValidatedCardAge144 = FarolElapsedTimePolicy0187.ageMillis(SystemClock.elapsedRealtime(), universalLastActiveReadAtElapsedMillis0187)
            val preserveRecentValidatedCard144 =
                universalActiveRidePackageName == resolvedPackage &&
                    universalActiveAddressSignature != null &&
                    recentValidatedCardAge144?.let { it <= 8_000L } == true
            if (preserveStableDecision141 || preserveRouteInFlight143 || preserveRecentValidatedCard144) {
                UnifiedDebugEventStore.record(
                    "BUBBLE_SCREEN_CHANGE_DEFERRED",
                    resolvedPackage,
                    "OCR/card recente preservado; apenas novo destino confirmado pode substituir o estado",
                )
                scheduleScreenshotFallback127(resolvedPackage)
            } else {
                hardClearUniversalTwoAddress(
                    reason = "A tela mudou; cor e quilometros anteriores removidos imediatamente.",
                    keepWaitingYellow = true,
                )
            } // stable_decision_survives_visual_noise_0_1_141
            universalForegroundPackageName = resolvedPackage
            activePackageName = resolvedPackage
            lastExternalWindowPackageName = resolvedPackage
        }
        lastImmediateScreenPackageChecklist13 = resolvedPackage
        lastImmediateScreenFingerprintChecklist13 = fingerprintChecklist13

        if (immediateAnalysisText0185.isBlank()) {
            UnifiedDebugEventStore.record("BUBBLE_TEXT_EMPTY", resolvedPackage, "coleta imediata vazia; confirmação visual Stage16 iniciada")
            val activeBindingStage16 = activeCardBindingStage16(resolvedPackage)
            when (FarolVisibleCardPriorityStage16.emptyReadAction(activeBindingStage16)) {
                FarolVisibleCardPriorityStage16.EmptyReadAction.CLEAR_WITHOUT_PRESERVATION -> {
                    hardClearUniversalTwoAddress(
                        reason = "Tela vazia sem card previamente vinculado; resultado removido.",
                        keepWaitingYellow = true,
                    )
                }
                FarolVisibleCardPriorityStage16.EmptyReadAction.CONFIRM_CURRENT_VISUAL -> {
                    stage16TransientEmptyBinding = activeBindingStage16
                    val confirmationStage16 = confirmTransientEmptyVisualStage16(
                        active = activeBindingStage16!!,
                        savedPackages = savedPackages,
                    )
                    when (confirmationStage16) {
                        FarolVisibleCardPriorityStage16.EmptyVisualConfirmation.SAME_CARD -> {
                            stage16TransientEmptyBinding = null
                            universalLastActiveReadAtElapsedMillis0187 = SystemClock.elapsedRealtime()
                            UnifiedDebugEventStore.record(
                                "BUBBLE_EMPTY_READ_RECONFIRMED_STAGE16", resolvedPackage,
                                "mesmo card visual confirmado; generation=$universalScreenGeneration; windowGeneration=$universalWindowGeneration",
                            )
                        }
                        FarolVisibleCardPriorityStage16.EmptyVisualConfirmation.DIFFERENT_CARD -> {
                            hardClearUniversalTwoAddress(
                                reason = "Mudança visual positiva de card, destino ou janela durante leitura vazia.",
                                keepWaitingYellow = true,
                            )
                        }
                        FarolVisibleCardPriorityStage16.EmptyVisualConfirmation.CONFIRMED_ABSENT -> {
                            hardClearUniversalTwoAddress(
                                reason = "Card confirmadamente ausente: outra aplicação possui a autoridade visual atual.",
                                keepWaitingYellow = true,
                            )
                        }
                        FarolVisibleCardPriorityStage16.EmptyVisualConfirmation.AMBIGUOUS -> {
                            UnifiedDebugEventStore.record(
                                "BUBBLE_EMPTY_READ_TRANSIENT_STAGE16", resolvedPackage,
                                "vazio isolado sem prova positiva de desaparecimento; geração preservada e resultado de rota bloqueado até nova evidência",
                            )
                        }
                    }
                }
            }
            scheduleScreenshotFallback127(resolvedPackage)
            return
        }

        val analysisHash143 = FarolUnifiedVisual0168.semanticHash(immediateAnalysisText0185)
        val sameAnalysisInFlight143 = analyzeJob?.isActive == true &&
            activeAnalysisPackage143 == resolvedPackage &&
            activeAnalysisHash143 == analysisHash143
        if (sameAnalysisInFlight143) {
            UnifiedDebugEventStore.record(
                "BUBBLE_DUPLICATE_EVENT_IGNORED",
                resolvedPackage,
                "mesmo texto já está em análise; hash=$analysisHash143; idade=${FarolElapsedTimePolicy0187.formatAge(FarolElapsedTimePolicy0187.ageMillis(SystemClock.elapsedRealtime(), activeAnalysisStartedAtElapsedMillis0187))}",
            )
            if (quickEvaluationChecklist13.active) {
                screenshotFallbackJob127?.cancel()
                screenshotFallbackJob127 = null
            }
            return
        }
        if (analyzeJob?.isActive == true) {
            UnifiedDebugEventStore.record(
                "BUBBLE_ANALYSIS_REPLACED",
                resolvedPackage,
                "conteúdo realmente mudou; hashAnterior=${activeAnalysisHash143 ?: 0}; hashAtual=$analysisHash143",
            )
            analyzeJob?.cancel()
        }
        activeAnalysisPackage143 = resolvedPackage
        activeAnalysisHash143 = analysisHash143
        activeAnalysisStartedAtElapsedMillis0187 = SystemClock.elapsedRealtime()
        UnifiedDebugEventStore.record("BUBBLE_ANALYSIS_STARTED", resolvedPackage, "fonte=Accessibility; tamanho=${immediateAnalysisText0185.length}; hash=$analysisHash143")
        analyzeJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                if (!driverCardSessionGate0162.isCurrent(sessionToken0162) || !isReadBindingFresh0187(readBinding0187)) return@launch
                processRideText(
                    immediateAnalysisText0185,
                    TextSource.Accessibility,
                    allowPopupCandidate = true,
                    packageHint152 = resolvedPackage,
                    readBinding0187 = readBinding0187,
                )
            } finally {
                if (activeAnalysisPackage143 == resolvedPackage && activeAnalysisHash143 == analysisHash143) {
                    activeAnalysisPackage143 = null
                    activeAnalysisHash143 = null
                    activeAnalysisStartedAtElapsedMillis0187 = 0L
                }
            }
        } // single_flight_accessibility_analysis_0_1_143
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

    private fun containUnexpectedFailure0172(
        stage0172: String,
        error0172: Throwable,
        packageName0172: String? = null,
    ) {
        runCatching {
            UnifiedDebugEventStore.record(
                "UNEXPECTED_FAILURE_CONTAINED_0172",
                packageName0172 ?: universalResolvedForegroundPackage(),
                "stage=$stage0172; type=${error0172::class.java.simpleName}; location=${FarolFailureLocation0187.describe(error0172, packageName)}",
            )
        }
        runCatching { FarolFlightRecorder0163.forceCheckpoint("FAILURE_CONTAINED_0172:$stage0172") }
        runCatching { analyzeJob?.cancel() }
        runCatching { liveAnalysisJob?.cancel() }
        runCatching { universalRouteJob?.cancel() }
        runCatching { screenshotFallbackJob127?.cancel() }
        runCatching { partialReadConfirmationJobChecklist14?.cancel() }
        screenshotInProgress.set(false)
        analyzing = false
        val failureReason0185 = "Falha isolada em $stage0172; estado transitório removido."
        val alreadyIdle0187 = currentRadarColor == RadarColor.Idle &&
            currentDistanceKm == null && universalActiveAddressSignature == null
        val visualClearApplied0185 = if (alreadyIdle0187) {
            rememberBubbleReason("failure_contained_0172", failureReason0185)
            true
        } else runCatching {
            hardClearUniversalTwoAddress(failureReason0185, keepWaitingYellow = false)
        }.isSuccess
        if (!visualClearApplied0185) {
            universalScreenGeneration += 1L
            currentRadarColor = RadarColor.Idle
            currentDistanceKm = null
            rememberBubbleReason("failure_contained_0172", failureReason0185)
            runCatching { showOverlay(RadarColor.Idle, null) }
        }
        if (::bubblePrefs.isInitialized) runCatching { persistBubbleState() }
    }

    private fun containLifecycleFailure0172(stage0172: String, error0172: Throwable) {
        runCatching {
            UnifiedDebugEventStore.record(
                "SERVICE_LIFECYCLE_FAILURE_CONTAINED_0172",
                packageName,
                "stage=$stage0172; type=${error0172::class.java.simpleName}",
            )
        }
        invalidateServiceRuntime0172("Falha contida durante $stage0172.")
    }

    private fun invalidateServiceRuntime0172(reason0172: String) {
        serviceReady = false
        workModeRuntimeActive0162 = false
        analyzing = false
        currentRadarColor = RadarColor.Idle
        currentDistanceKm = null
        lastAccessibilityText = ""
        lastOcrText = ""
        activePackageName = null
        lastTextPackageName = null
        rememberBubbleReason("service_unavailable_0172", reason0172)
        runCatching { driverCardSessionGate0162.invalidate() }
        runCatching { notificationWakeGate0169.invalidate() }
        runCatching { analyzeJob?.cancel() }
        runCatching { liveAnalysisJob?.cancel() }
        runCatching { universalRouteJob?.cancel() }
        runCatching { screenshotFallbackJob127?.cancel() }
        runCatching { partialReadConfirmationJobChecklist14?.cancel() }
        screenshotInProgress.set(false)
        if (::bubblePrefs.isInitialized) runCatching { persistBubbleState() }
        runCatching { removeOverlay() }
    }

    private fun startIntensiveDiagnosticLoop0172() {
        if (!IntensiveDiagnostics0172.isActive(applicationContext)) {
            intensiveDiagnosticJob0172?.cancel()
            intensiveDiagnosticJob0172 = null
            return
        }
        if (intensiveDiagnosticJob0172?.isActive == true) return
        intensiveDiagnosticJob0172 = scope.launch(Dispatchers.IO) {
            while (serviceReady && IntensiveDiagnostics0172.isActive(applicationContext)) {
                IntensiveDiagnostics0172.heartbeat(
                    applicationContext,
                    "ready=$serviceReady; stage=$lastBubbleStateStage; color=${currentRadarColor.diagnosticLabel}; analyzing=$analyzing; analyzeJob=${analyzeJob?.isActive == true}; routeJob=${universalRouteJob?.isActive == true}; screenshot=${screenshotInProgress.get()}; package=${activePackageName ?: "none"}; memKb=${Runtime.getRuntime().totalMemory() / 1024L - Runtime.getRuntime().freeMemory() / 1024L}",
                )
                delay(1_000L)
            }
            intensiveDiagnosticJob0172 = null
        }
    }

    override fun onInterrupt() {
        UnifiedDebugEventStore.record("SERVICE_INTERRUPT", packageName, "Android interrompeu o serviço")
        FarolFlightRecorder0163.forceCheckpoint("SERVICE_INTERRUPT")
        invalidateServiceRuntime0172("O Android interrompeu a acessibilidade.")
    }

    override fun onDestroy() {
        UnifiedDebugEventStore.record("SERVICE_DESTROY", packageName, "serviço destruído")
        FarolFlightRecorder0163.forceCheckpoint("SERVICE_DESTROY")
        invalidateServiceRuntime0172("O serviço de acessibilidade foi destruído.")
        intensiveDiagnosticJob0172?.cancel()
        intensiveDiagnosticJob0172 = null

        if (::preciseNavigationTrackerChecklist5.isInitialized) preciseNavigationTrackerChecklist5.stop()
        if (::directionalAlertOverlayChecklist5.isInitialized) directionalAlertOverlayChecklist5.hide()
        directionalRadarSpatialIndexChecklist5.clear()
        // directional_alert_destroy_checklist_5
        if (quickReplyReceiverRegisteredChecklist3) {
            runCatching { unregisterReceiver(quickReplyReceiverChecklist3) }
            quickReplyReceiverRegisteredChecklist3 = false
        } // quick_reply_receiver_unregister_checklist_3
        if (intensiveDiagnosticReceiverRegistered0172) {
            runCatching { unregisterReceiver(intensiveDiagnosticReceiver0172) }
            intensiveDiagnosticReceiverRegistered0172 = false
        }
        Unit
        serviceReady = false
        workModeRuntimeActive0162 = false
        workModeSettingsReady0162 = false
        driverCardSessionGate0162.invalidate()
        failedCardAutoCaptureGate0161.reset()
        notificationFailureCircuit0170.reset()
        lastFailedCardNodes0161 = emptyList()
        lastFailedCardSignature0161 = null
        lastFailedCardAccessibilityHash0161 = null
        screenshotInProgress.set(false)
        coreLiveReadTriggerGate.reset() // gigu_inspired_gate_reset_0_1_89
        analyzeJob?.cancel()
        screenshotFallbackJob127?.cancel()
        screenshotFallbackJob127 = null // deferred_ocr_destroy_cancel_0_1_127
        cancelNotificationWakeup0169()
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

    private fun applyPersistedManualReadingStage43(
        updatedStage43: AppSettings,
        sourceStage43: String,
        forceStage43: Boolean = false,
    ) {
        currentSettings = updatedStage43
        val enabledStage43 = FarolManualToggleRuntimeSyncStage43.enabled(updatedStage43)
        if (!forceStage43 && stage43LastAppliedManualReading == enabledStage43) return
        stage43LastAppliedManualReading = enabledStage43
        stage43ManualTransitionSerial += 1L
        traceEvent(
            "stage43.manual_runtime source=$sourceStage43 enabled=$enabledStage43 serial=$stage43ManualTransitionSerial",
        )
        applyWorkModeRuntime0162(enabledStage43, force0162 = true)
    }

    private fun applyManualReadingCommandStage43(enabledStage43: Boolean, sourceStage43: String) {
        val updatedStage43 = FarolManualToggleRuntimeSyncStage43.withEnabled(currentSettings, enabledStage43)
        // The live Farol changes synchronously; persistence follows. Its returning DataStore emission
        // is safely deduplicated by stage43LastAppliedManualReading.
        applyPersistedManualReadingStage43(updatedStage43, sourceStage43, forceStage43 = true)
        scope.launch { runCatching { repository.saveSettings(updatedStage43) } }
    }

    private fun applyWorkModeRuntime0162(enabled0162: Boolean, force0162: Boolean = false) {
        if (::stage36RuntimeAuthority.isInitialized) stage36RuntimeAuthority.setManualAuthority(enabled0162)
        stage26ReadingActivation.setManualAuthority(enabled0162)
        if (!force0162 && workModeRuntimeActive0162 == enabled0162) return
        workModeRuntimeActive0162 = enabled0162
        farolRealtimeEventGate0167.reset()
        if (enabled0162) {
            lastRejectedForegroundPackage0162 = null
            showOverlay(RadarColor.Idle, null)
            scheduleVisibleTextAnalysis(delayMs = 0L)
            return
        }
        driverCardSessionGate0162.invalidate()
        clearStage16VisualProof()
        universalScreenGeneration += 1L
        universalWindowGeneration += 1L
        universalRouteJob?.cancel()
        universalRouteJob = null
        analyzeJob?.cancel()
        analyzeJob = null
        screenshotFallbackJob127?.cancel()
        screenshotFallbackJob127 = null
        cancelNotificationWakeup0169()
        partialReadConfirmationJobChecklist14?.cancel()
        partialReadConfirmationJobChecklist14 = null
        liveAnalysisJob?.cancel()
        liveAnalysisJob = null
        failedCardAutoCaptureGate0161.reset()
        lastFailedCardNodes0161 = emptyList()
        lastFailedCardSignature0161 = null
        lastFailedCardAccessibilityHash0161 = null
        lastAccessibilityText = ""
        lastOcrText = ""
        universalActiveRidePackageName = null
        if (::stage36RuntimeAuthority.isInitialized) stage36RuntimeAuthority.markExplicitOff("work_mode_disabled")
        universalActiveAddressSignature = null
        // Stage36 work-mode OFF is distinct from visual-card disappearance.
        lastSnapshotHash = null
        lastAnalyzedHash = null
        shortcutOverlayController.hideAll()
        if (::preciseNavigationTrackerChecklist5.isInitialized) preciseNavigationTrackerChecklist5.stop()
        if (::directionalAlertOverlayChecklist5.isInitialized) directionalAlertOverlayChecklist5.hide()
        val offRenderBeforeStage43 = stage43OffRenderAppliedSerial
        showOverlay(RadarColor.Idle, null, forcePhysicalCommitStage43 = true)
        val offRenderAppliedStage43 = stage43OffRenderAppliedSerial > offRenderBeforeStage43 &&
            currentRadarColor == RadarColor.Idle && currentDistanceKm == null
        FarolManualOffVisualCommitStage43.recordAttempt(offRenderAppliedStage43)
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S43_MANUAL_OFF_RENDER_COMMIT", universalResolvedForegroundPackage(),
            details = "applied=$offRenderAppliedStage43; beforeSerial=$offRenderBeforeStage43; afterSerial=$stage43OffRenderAppliedSerial; currentColor=$currentRadarColor; currentDistance=${currentDistanceKm ?: -1.0}",
        )
        if (!offRenderAppliedStage43) {
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S43_MANUAL_OFF_RENDER_ANOMALY", universalResolvedForegroundPackage(),
                details = "logicalOff=true; expected=Idle/no-km/renderApplied; currentColor=$currentRadarColor; currentDistance=${currentDistanceKm ?: -1.0}; serviceReady=$serviceReady; overlayPresent=${overlayView != null}",
            )
            UnifiedDebugEventStore.record(
                "STAGE43_OFF_RENDER_ANOMALY", packageName,
                "logical_off_without_physical_idle_commit=true; color=$currentRadarColor; distance=$currentDistanceKm; serviceReady=$serviceReady; overlayPresent=${overlayView != null}",
            )
        }
        UnifiedDebugEventStore.record("WORK_MODE_0162", packageName, "enabled=false; farol_event_driven_paused=true")
    }

    private fun ensureDriverCardSession0162(packageName0162: String, windowId0162: Int): DriverCardSession0162 {
        val previous0162 = driverCardSessionGate0162.current()
        val current0162 = driverCardSessionGate0162.begin(packageName0162, windowId0162)
        if (previous0162 != current0162) {
            invalidateFarolAsyncWork0187Phase4(
                reason0187Phase4 = "driver_session_changed",
                invalidateSession0187Phase4 = false,
                advanceScreenGeneration0187Phase4 = true,
                advanceWindowGeneration0187Phase4 = true,
                clearReadEvidence0187Phase4 = true,
            )
            UnifiedDebugEventStore.record(
                "DRIVER_CARD_SESSION_0162",
                packageName0162,
                "window=${current0162.windowId}; generation=${current0162.generation}",
            )
        }
        return current0162
    }

    private fun invalidateFarolAsyncWork0187Phase4(
        reason0187Phase4: String,
        invalidateSession0187Phase4: Boolean,
        advanceScreenGeneration0187Phase4: Boolean,
        advanceWindowGeneration0187Phase4: Boolean,
        clearReadEvidence0187Phase4: Boolean,
    ) {
        if (invalidateSession0187Phase4) driverCardSessionGate0162.invalidate()
        clearStage16VisualProof()
        if (advanceScreenGeneration0187Phase4) universalScreenGeneration += 1L
        if (advanceWindowGeneration0187Phase4) universalWindowGeneration += 1L
        universalRouteJob?.cancel()
        universalRouteJob = null
        analyzeJob?.cancel()
        analyzeJob = null
        screenshotFallbackJob127?.cancel()
        screenshotFallbackJob127 = null
        partialReadConfirmationJobChecklist14?.cancel()
        partialReadConfirmationJobChecklist14 = null
        liveAnalysisJob?.cancel()
        liveAnalysisJob = null
        analyzing = false
        if (clearReadEvidence0187Phase4) {
            failedCardAutoCaptureGate0161.reset()
            lastFailedCardNodes0161 = emptyList()
            lastFailedCardSignature0161 = null
            lastFailedCardAccessibilityHash0161 = null
            lastAccessibilityText = ""
            lastAccessibilityTextAtMillis = 0L
            lastOcrText = ""
            lastOcrTextAtMillis = 0L
        }
        UnifiedDebugEventStore.record(
            "BUBBLE_ASYNC_WORK_INVALIDATED_0187_PHASE4",
            universalResolvedForegroundPackage(),
            "reason=$reason0187Phase4; sessionInvalidated=$invalidateSession0187Phase4; screenGeneration=$universalScreenGeneration; windowGeneration=$universalWindowGeneration; visualMutation=caller_owned",
        )
    }

    private fun invalidateRejectedSnapshotRead0187Phase3(
        reason0187Phase3: String,
        packageName0187Phase3: String,
    ) {
        invalidateFarolAsyncWork0187Phase4(
            reason0187Phase4 = "rejected_snapshot:$reason0187Phase3",
            invalidateSession0187Phase4 = true,
            advanceScreenGeneration0187Phase4 = false,
            advanceWindowGeneration0187Phase4 = true,
            clearReadEvidence0187Phase4 = true,
        )
        UnifiedDebugEventStore.record(
            "BUBBLE_ROOT_SNAPSHOT_READ_INVALIDATED_0187_PHASE3",
            packageName0187Phase3,
            "reason=$reason0187Phase3; visualPreserved=true; color=$currentRadarColor; distance=$currentDistanceKm",
        )
    }

    private fun handleRejectedForeground0162(
        eventPackage0162: String?,
        rootPackage0162: String?,
        eventType0187: Int,
        eventWindowId0187: Int,
    ) {
        val root0162 = DriverAppPackagePolicy0162.normalize(rootPackage0162)
        val eventPackageNormalized0162 = DriverAppPackagePolicy0162.normalize(eventPackage0162)
        if (notificationWakeGate0169.shouldDeferPassiveRejection(
                eventPackageName = eventPackageNormalized0162,
                rootPackageName = root0162,
                ownPackageName = packageName,
                nowElapsedMillis = SystemClock.elapsedRealtime(),
            )
        ) {
            UnifiedDebugEventStore.record(
                "NOTIFICATION_PASSIVE_REJECTION_DEFERRED_0169",
                universalResolvedForegroundPackage(),
                "event=${eventPackageNormalized0162 ?: "none"}; root=${root0162 ?: "none"}",
            )
            return
        }
        if (root0162 == packageName) {
            driverCardSessionGate0162.invalidate()
            hardClearUniversalTwoAddress("Tela do proprio Rota Certa.")
            return
        }
        val active0162 = driverCardSessionGate0162.current()
        val transientOnly0162 = active0162 != null &&
            DriverAppPackagePolicy0162.isTransientOverlay(eventPackageNormalized0162, packageName) &&
            (root0162 == null || DriverAppPackagePolicy0162.isTransientOverlay(root0162, packageName))
        if (transientOnly0162) {
            UnifiedDebugEventStore.record(
                "DRIVER_TRANSIENT_OVERLAY_0162",
                active0162.packageName,
                "event=${eventPackageNormalized0162 ?: "none"}; root=${root0162 ?: "none"}; preserved=true",
            )
            return
        }
        val rejected0162 = root0162 ?: eventPackageNormalized0162 ?: "unknown"
        if (lastRejectedForegroundPackage0162 == rejected0162 &&
            currentRadarColor == RadarColor.Idle && currentDistanceKm == null
        ) return
        lastRejectedForegroundPackage0162 = rejected0162
        driverCardSessionGate0162.invalidate()
        hardClearUniversalTwoAddress(
            reason = "Janela fora do aplicativo de corrida selecionado: $rejected0162.",
            keepWaitingYellow = false,
        )
        UnifiedDebugEventStore.record(
            "DRIVER_WINDOW_REJECTED_0162",
            rejected0162,
            "event=$eventType0187; window=$eventWindowId0187",
        )
    }


    private fun handleNotificationWakeup0169(
        event0169: AccessibilityEvent,
        eventPackage0169: String?,
        selectedPackages0169: Set<String>,
    ) {
        val token0169 = notificationWakeGate0169.begin(
            eventType = event0169.eventType,
            eventPackageName = eventPackage0169,
            selectedPackages = selectedPackages0169,
            ownPackageName = packageName,
            workModeEnabled = WorkModePolicy0162.isEnabled(currentSettings),
            liveReadingEnabled = currentSettings.liveReadingEnabled,
            serviceReady = serviceReady,
            bubbleGestureActive = bubbleGestureActive,
            nowElapsedMillis = SystemClock.elapsedRealtime(),
        ) ?: return

        notificationWakeJob0169?.cancel()
        universalScreenGeneration += 1L
        universalWindowGeneration += 1L
        universalRouteJob?.cancel()
        universalRouteJob = null
        analyzeJob?.cancel()
        analyzeJob = null
        screenshotFallbackJob127?.cancel()
        screenshotFallbackJob127 = null
        failedCardAutoCaptureGate0161.reset()
        lastFailedCardNodes0161 = emptyList()
        lastFailedCardSignature0161 = null
        lastFailedCardAccessibilityHash0161 = null
        lastAccessibilityText = ""
        lastAccessibilityTextAtMillis = 0L
        lastOcrText = ""
        lastOcrTextAtMillis = 0L

        recentSelectedRidePackageChecklist11 = token0169.packageName
        recentSelectedRidePackageAtMillisChecklist11 = System.currentTimeMillis()
        universalForegroundPackageName = token0169.packageName
        activePackageName = token0169.packageName
        lastExternalWindowPackageName = token0169.packageName
        val wakeWindow0169 = event0169.windowId.takeIf { it >= 0 } ?: 0
        ensureDriverCardSession0162(token0169.packageName, wakeWindow0169)
        if (currentRadarColor == RadarColor.Idle) {
            rememberBubbleReason(
                "notification_waiting_0169",
                "Aplicativo selecionado notificou uma oferta; confirmando o card visual.",
            )
            showOverlay(RadarColor.Default, null)
        }
        UnifiedDebugEventStore.record(
            "NOTIFICATION_WAKE_ACCEPTED_0169",
            token0169.packageName,
            "window=$wakeWindow0169; generation=${token0169.generation}; text=${event0169.text.joinToString(" ").take(240)}",
        )

        notificationWakeJob0169 = scope.launch {
            try {
                var recognized0169 = captureNotificationOverlay0169(token0169)
            if (!recognized0169) {
                delay(NOTIFICATION_INITIAL_RETRY_DELAY_MILLIS_0169)
                recognized0169 = captureNotificationOverlay0169(token0169)
            }
            if (!recognized0169) {
                if (notificationWakeGate0169.isCurrent(token0169, SystemClock.elapsedRealtime())) {
                    hardClearUniversalTwoAddress(
                        reason = "Notificacao do aplicativo selecionado sem card confirmado.",
                        keepWaitingYellow = false,
                    )
                }
                notificationWakeGate0169.invalidate(token0169)
                return@launch
            }

            delay(NOTIFICATION_VERIFY_DELAY_MILLIS_0169)
            if (!notificationWakeGate0169.isCurrent(token0169, SystemClock.elapsedRealtime())) return@launch
            recognized0169 = captureNotificationOverlay0169(token0169)
            if (!recognized0169) {
                notificationWakeGate0169.invalidate(token0169)
                return@launch
            }

            delay(NOTIFICATION_FINAL_VERIFY_DELAY_MILLIS_0169)
            if (notificationWakeGate0169.isCurrent(token0169, SystemClock.elapsedRealtime())) {
                captureNotificationOverlay0169(token0169)
            }
                notificationWakeGate0169.invalidate(token0169)
            } catch (cancelled0170: kotlinx.coroutines.CancellationException) {
                throw cancelled0170
            } catch (error0170: Exception) {
                notificationFailureCircuit0170.onFailure(SystemClock.elapsedRealtime())
                containNotificationWakeupFailure0170(
                    stage0170 = "notification_wake_job_0170",
                    packageName0170 = token0169.packageName,
                    error0170 = error0170,
                )
            }
        }
    }

    private suspend fun captureNotificationOverlay0169(
        token0169: FarolNotificationWakeToken0169,
    ): Boolean {
        val attempt0169 = notificationWakeGate0169.reserveCapture(
            token0169,
            SystemClock.elapsedRealtime(),
        ) ?: return false
        if (token0169.packageName !in SelectedRideAppStore.read(applicationContext) ||
            !shouldScanPackage(token0169.packageName) || Build.VERSION.SDK_INT < Build.VERSION_CODES.R
        ) return false
        if (!screenshotInProgress.compareAndSet(false, true)) return false

        val completion0169 = CompletableDeferred<Boolean>()
        UnifiedDebugEventStore.record(
            "NOTIFICATION_CAPTURE_STARTED_0169",
            token0169.packageName,
            "attempt=$attempt0169; generation=${token0169.generation}",
        )
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        scope.launch {
                            var bitmap0169: Bitmap? = null
                            var recognized0169 = false
                            try {
                                if (!notificationWakeGate0169.isCurrent(token0169, SystemClock.elapsedRealtime()) ||
                                    token0169.packageName !in SelectedRideAppStore.read(applicationContext)
                                ) return@launch
                                val session0169 = driverCardSessionGate0162.current()
                                    ?.takeIf { it.packageName == token0169.packageName }
                                    ?: return@launch
                                if (!driverCardSessionGate0162.isCurrent(session0169)) return@launch
                                bitmap0169 = screenshot.toSoftwareBitmap() ?: return@launch
                                val ocrText0169 = withContext(Dispatchers.Default) {
                                    ocrService.extractText(bitmap0169)
                                }
                                rememberSourceText(token0169.packageName, TextSource.Ocr, ocrText0169)
                                processRideText(
                                    ocrText0169,
                                    TextSource.Ocr,
                                    allowPopupCandidate = true,
                                    packageHint152 = token0169.packageName,
                                )
                                recognized0169 = universalActiveRidePackageName == token0169.packageName &&
                                    universalActiveAddressSignature != null
                                UnifiedDebugEventStore.record(
                                    "NOTIFICATION_CAPTURE_FINISHED_0169",
                                    token0169.packageName,
                                    "attempt=$attempt0169; text=${ocrText0169.length}; recognized=$recognized0169",
                                )
                            } catch (error0169: Throwable) {
                                recordDiagnostic(
                                    stage = "notification_capture_error_0169",
                                    reason = "Falha isolada ao confirmar visualmente a oferta notificada.",
                                    error = error0169,
                                )
                            } finally {
                                bitmap0169?.takeUnless(Bitmap::isRecycled)?.recycle()
                                screenshotInProgress.set(false)
                                completion0169.complete(recognized0169)
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        screenshotInProgress.set(false)
                        UnifiedDebugEventStore.record(
                            "NOTIFICATION_CAPTURE_FAILED_0169",
                            token0169.packageName,
                            "attempt=$attempt0169; code=$errorCode",
                        )
                        completion0169.complete(false)
                    }
                },
            )
        }.onFailure { error0169 ->
            screenshotInProgress.set(false)
            completion0169.complete(false)
            recordDiagnostic(
                stage = "notification_capture_request_error_0169",
                reason = "Android nao iniciou a captura pontual da oferta notificada.",
                error = error0169,
            )
        }
        return withTimeoutOrNull(NOTIFICATION_CAPTURE_TIMEOUT_MILLIS_0169) {
            completion0169.await()
        } ?: false
    }

    private fun containNotificationWakeupFailure0170(
        stage0170: String,
        packageName0170: String?,
        error0170: Exception,
    ) {
        notificationWakeJob0169?.cancel()
        notificationWakeJob0169 = null
        notificationWakeGate0169.invalidate()
        screenshotInProgress.set(false)
        runCatching {
            recordDiagnostic(
                stage = stage0170,
                reason = "Falha contida no despertar por notificacao; leitura ao vivo preservada.",
                error = error0170,
            )
        }
        runCatching {
            UnifiedDebugEventStore.record(
                "NOTIFICATION_WAKE_FAILURE_CONTAINED_0170",
                packageName0170,
                "stage=$stage0170; type=${error0170::class.java.simpleName}",
            )
        }
        runCatching {
            hardClearUniversalTwoAddress(
                reason = "Falha isolada ao confirmar oferta notificada; estado visual limpo.",
                keepWaitingYellow = false,
            )
        }
    }

    private fun cancelNotificationWakeup0169() {
        notificationWakeJob0169?.cancel()
        notificationWakeJob0169 = null
        notificationWakeGate0169.invalidate()
    }

    private data class Stage26AccessibilitySnapshot(
        val blocks: List<FarolUniversalVisualPipelineStage19.VisualBlock>,
        val snapshot: FarolVisualIdentityStage23.Snapshot,
        val stats: FarolVisualIdentityStage23.CollectionStats,
        val addressParserInvocations: Int,
        val duplicateSubtreesAvoided: Int,
    )

    private data class Stage26TreeResult(
        val lines: LinkedHashSet<String>,
        val completeBlock: FarolCardBlock0188?,
        val addressParserInvocations: Int,
        val duplicateSubtreesAvoided: Int,
    )

    private fun handleUniversalVisualEventStage19(
        eventPackageStage19: String?,
        eventTypeStage20: Int,
        eventWindowIdStage20: Int,
        eventStage26: AccessibilityEvent,
    ): Boolean {
        if (!serviceReady || !WorkModePolicy0162.isEnabled(currentSettings)) return false
        val eventStartedNsStage26 = SystemClock.elapsedRealtimeNanos()
        val activationStage26 = refreshReadingActivationStage26(eventPackageStage19, eventTypeStage20)
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_ACTIVATION_STATE", eventPackageStage19,
            details = "enabled=${activationStage26.enabled}; generation=${activationStage26.generation}; usageAccess=${activationStage26.usageAccessGranted}; selectedActive=${activationStage26.activeSelectedPackages.sorted().joinToString(",")}",
        )
        if (!activationStage26.enabled) {
            FarolCausalLatencyStage28.Metrics.increment("eventsReceived")
            FarolCausalLatencyStage28.Metrics.increment("eventsRejectedReadingOff")
            FarolCausalLatencyStage28.Metrics.increment("heavyCollectionsAvoided")
            applyReadingOffStage26(activationStage26)
            FarolReadingActivationStage26.Metrics.increment("eventsReceived")
            FarolReadingActivationStage26.Metrics.increment("eventsRejectedReadingOff")
            FarolReadingActivationStage26.Metrics.increment("heavyCollectionsAvoided")
            return true
        }
        stage36RuntimeAuthority.observeVisualEvidence()
        if (bubbleGestureActive) {
            FarolCausalLatencyStage28.Metrics.increment("eventsReceived")
            FarolCausalLatencyStage28.Metrics.increment("ownOverlayEventsIgnored")
            FarolCausalLatencyStage28.Metrics.increment("heavyCollectionsAvoided")
            FarolReadingActivationStage26.Metrics.increment("eventsReceived")
            FarolReadingActivationStage26.Metrics.increment("ownOverlayEventsIgnored")
            FarolReadingActivationStage26.Metrics.increment("heavyCollectionsAvoided")
            return true
        } // bubble_drag_accessibility_pause_0_1_116

        val semanticStartedNsStage32 = SystemClock.elapsedRealtimeNanos()
        val semanticSignalStage32 = buildSemanticSignalStage32(eventPackageStage19, eventTypeStage20, eventWindowIdStage20, eventStage26)
        val semanticDecisionStage32 = stage32SemanticGate.observe(semanticSignalStage32)
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_SEMANTIC_GATE", eventPackageStage19,
            details = "mutation=${semanticDecisionStage32.mutation}; generation=${semanticDecisionStage32.snapshot.generation}; fingerprint=${semanticDecisionStage32.snapshot.fingerprint}; sourcePackage=${semanticSignalStage32.sourcePackage.orEmpty()}; windowPackage=${semanticSignalStage32.windowPackage.orEmpty()}; sourceSlot=${semanticSignalStage32.sourceSlot}; sourceText=${semanticSignalStage32.sourceText.take(900)}",
        )
        FarolSemanticCardStage32.Metrics.sample("eventToSemantic", SystemClock.elapsedRealtimeNanos() - semanticStartedNsStage32)
        val provenanceStage32 = FarolSemanticCardStage32.resolveProvenance(
            eventPackageStage19, semanticSignalStage32.sourcePackage, semanticSignalStage32.windowPackage, activationStage26.selectedPackages,
        )
        FarolForensicCardBlackBoxStage32.observeEvent(
            elapsedNs = eventStartedNsStage26,
            wallMs = System.currentTimeMillis(),
            semanticDecision = semanticDecisionStage32,
            triggerPackage = eventPackageStage19,
            sourcePackage = semanticSignalStage32.sourcePackage,
            windowPackage = semanticSignalStage32.windowPackage,
            provenance = provenanceStage32,
            selectedAppsActive = activationStage26.activeSelectedPackages,
            rawVisualGeneration = stage26PreCollectGate.currentGeneration(),
        )

        val cheapSignalStage26 = buildCheapVisualSignalStage26(
            eventPackageStage19,
            eventTypeStage20,
            eventWindowIdStage20,
            eventStage26,
        )
        val admissionStage26 = stage26PreCollectGate.admit(true, cheapSignalStage26)
        val replacementProofStage46R8 = if (
            admissionStage26.reason == "stage40_address_evidence_changed" &&
            FarolStableFinalLatchStage46R4.isFinalDecision(
                currentRadarColor.name,
                currentDistanceKm,
                universalActiveAddressSignature,
            )
        ) {
            FarolRouteLocationEvidenceStage46R8.proveDestinationReplacement(
                cheapSignalStage26.sourceText,
                universalActiveAddressSignature,
            )
        } else {
            FarolRouteLocationEvidenceStage46R8.ReplacementProof(false, "not_strong_address_change")
        }
        val immediateAddressReplacementStage46R8 = replacementProofStage46R8.proven
        if (immediateAddressReplacementStage46R8) {
            invalidateOldVisualBeforeCollectStage26(admissionStage26.visualGeneration, eventStartedNsStage26)
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(),
                "S46_R8_PROVEN_DESTINATION_CHANGE_CLEARED_PRECOLLECT", eventPackageStage19,
                details = "reason=${replacementProofStage46R8.reason}; candidateSignature=${replacementProofStage46R8.candidateSignature.orEmpty()}; positiveLocations=${replacementProofStage46R8.positiveLocationCount}; previousFinalCleared=true; kmCleared=true; beforeHeavyCollect=true",
            )
            FarolCausalLatencyStage28.Metrics.increment("stage46R8ImmediateDestinationClear")
            FarolCausalLatencyStage28.Metrics.sample(
                "eventToStage46R8ImmediateDestinationClear",
                SystemClock.elapsedRealtimeNanos() - eventStartedNsStage26,
            )
        }
        // Stage46 R2: WINDOWS_CHANGED is only a trigger to re-observe the concrete target window.
        // A foreign overlay (e.g. inDrive while a 99 card is still visible) cannot revoke that target.
        val previousTargetWindowStage46 = stage46TargetWindowId
        val observedTargetWindowStage46 = observeTargetWindowIdStage46(stage46TargetSourcePackage)
        val targetReplacementStage46 = FarolTargetSurfaceStage46R2.isTargetWindowReplacement(
            eventTypeStage20,
            cheapSignalStage26.structuralSignature,
            cheapSignalStage26.ownOverlay,
            admissionStage26.heavyCollect,
            stage46TargetSourcePackage,
            previousTargetWindowStage46,
            observedTargetWindowStage46,
        )
        if (targetReplacementStage46 && admissionStage26.visualGeneration != stage46LastHardBoundaryGeneration) {
            advanceHardVisualEpochStage46(
                admissionStage26.visualGeneration,
                eventStartedNsStage26,
                eventPackageStage19,
                eventWindowIdStage20,
                cheapSignalStage26.structuralSignature,
            )
        } else if (observedTargetWindowStage46 > 0 && stage46TargetSourcePackage != null) {
            stage46TargetWindowId = observedTargetWindowStage46
            if (eventTypeStage20 == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                FarolMaximumForensicsStage38.record(
                    SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_R2_FOREIGN_WINDOW_PRESERVED", eventPackageStage19,
                    details = "target=${stage46TargetSourcePackage.orEmpty()}; targetWindow=$stage46TargetWindowId; eventWindow=$eventWindowIdStage20; structural=${cheapSignalStage26.structuralSignature.take(300)}; epoch=$stage46VisualEpoch",
                )
            }
        }
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_PRECOLLECT_ADMISSION", eventPackageStage19,
            details = "heavyCollect=${admissionStage26.heavyCollect}; reason=${admissionStage26.reason}; visualGeneration=${admissionStage26.visualGeneration}; ownOverlay=${cheapSignalStage26.ownOverlay}; windowSignature=${cheapSignalStage26.windowSignature}; sourceSlot=${cheapSignalStage26.sourceSlot}; eventType=${cheapSignalStage26.eventType}; contentChangeTypes=${cheapSignalStage26.contentChangeTypes}; bootstrapEligible=${cheapSignalStage26.bootstrapEligible}; structural=${cheapSignalStage26.structuralSignature.take(500)}; sourceText=${cheapSignalStage26.sourceText.take(900)}; bootstrapText=${cheapSignalStage26.bootstrapText.take(900)}",
        )
        val mutationDetectedNsStage26 = SystemClock.elapsedRealtimeNanos()
        FarolReadingActivationStage26.Metrics.sample(
            "eventToMutationDetected",
            mutationDetectedNsStage26 - eventStartedNsStage26,
        )
        FarolCausalLatencyStage28.Metrics.increment("eventsReceived")
        FarolCausalLatencyStage28.Metrics.sample(
            "eventToMutationDetected",
            mutationDetectedNsStage26 - eventStartedNsStage26,
        )
        if (!admissionStage26.heavyCollect) {
            FarolCausalLatencyStage28.Metrics.increment("preCollectDuplicateSkipped")
            FarolCausalLatencyStage28.Metrics.increment("eventsCoalesced")
            FarolCausalLatencyStage28.Metrics.increment("visualIdentityRepeated")
            FarolCausalLatencyStage28.Metrics.increment("heavyCollectionsAvoided")
            if (stage32ScreenshotRateGate.hasPending() &&
                stage32ScreenshotRateGate.pendingEligible(SystemClock.uptimeMillis(), stage32SemanticGate.snapshot().generation)
            ) {
                requestUniversalScreenshotStage19(eventPackageStage19, null)
            }
            return true
        }
        FarolCausalLatencyStage28.Metrics.increment("visualIdentityChanged")
        FarolCausalLatencyStage28.Metrics.increment("heavyCollectionsStarted")

        // Stage44: pre-collect activity is evidence to inspect, never authority to revoke a valid final.
        // Keep the currently painted Green/Red leased until the collected frame proves a real change.
        val handoffLeaseStage46R3 = FarolSemanticFinalLeaseStage44.capture(
            currentRadarColor.name,
            currentDistanceKm,
            universalActiveAddressSignature,
        )
        val handoffPresenceStage46R3 = observeTargetSurfaceStage46R3(stage46TargetSourcePackage)
        if (FarolAcquisitionSurfaceStage46R3.provesForegroundSurfaceHandoff(
                eventTypeStage20,
                admissionStage26.heavyCollect,
                cheapSignalStage26.ownOverlay,
                handoffLeaseStage46R3.activeFinal,
                stage46TargetSourcePackage,
                currentRootPackageName(),
                packageName,
                handoffPresenceStage46R3,
            )
        ) {
            revokeForegroundSurfaceHandoffStage46R3(
                eventStartedNsStage26,
                eventPackageStage19,
                eventWindowIdStage20,
                admissionStage26.visualGeneration,
                currentRootPackageName(),
            )
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_R5_HANDOFF_CONTINUES_SAME_CYCLE", eventPackageStage19,
                details = "root=${currentRootPackageName().orEmpty()}; epoch=$stage46VisualEpoch; yellowCommitted=true; inheritedR3SameCycle=true; noSecondEventRequired=true",
            )
        }

        val finalLeaseStage44 = FarolSemanticFinalLeaseStage44.capture(
            currentRadarColor.name,
            currentDistanceKm,
            universalActiveAddressSignature,
        )
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S44_FINAL_LEASE_HELD_PRECOLLECT", eventPackageStage19,
            details = "activeFinal=${finalLeaseStage44.activeFinal}; color=${finalLeaseStage44.color}; distance=${finalLeaseStage44.distanceKm ?: -1.0}; signature=${finalLeaseStage44.addressSignature.orEmpty()}; admissionReason=${admissionStage26.reason}; admissionGeneration=${admissionStage26.visualGeneration}",
        )

        val cycleIdStage20 = FarolForensicTraceStage20.beginCycle(
            nowNs = eventStartedNsStage26,
            packageName = eventPackageStage19,
            eventType = eventTypeStage20,
            eventWindowId = eventWindowIdStage20,
        )
        stage20LastCycleId = cycleIdStage20
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_CYCLE_LINK", eventPackageStage19, cycleId = cycleIdStage20,
            details = "eventStartedNs=$eventStartedNsStage26; eventType=$eventTypeStage20; eventWindow=$eventWindowIdStage20",
        )

        val collectStartedNsStage26 = SystemClock.elapsedRealtimeNanos()
        FarolForensicTraceStage20.accessibilityCollectStarted(cycleIdStage20, collectStartedNsStage26)
        val collectionStage26 = collectUniversalAccessibilitySnapshotStage28(eventStage26)
        val collectEndedNsStage26 = SystemClock.elapsedRealtimeNanos()
        FarolReadingActivationStage26.Metrics.sample("collect", collectEndedNsStage26 - collectStartedNsStage26)
        FarolCausalLatencyStage28.Metrics.sample("collect", collectEndedNsStage26 - collectStartedNsStage26)
        FarolCausalLatencyStage28.Metrics.increment("nodesVisited", collectionStage26.stats.blocksVisited.toLong())
        FarolCausalLatencyStage28.Metrics.increment("blocksEmitted", collectionStage26.stats.blocksEmitted.toLong())
        FarolCausalLatencyStage28.Metrics.increment("addressParserInvocations", collectionStage26.addressParserInvocations.toLong())
        FarolCausalLatencyStage28.Metrics.increment("duplicateSubtreesAvoided", collectionStage26.duplicateSubtreesAvoided.toLong())
        FarolReadingActivationStage26.Metrics.addTotal("nodesVisited", collectionStage26.stats.blocksVisited.toLong())
        FarolReadingActivationStage26.Metrics.addTotal("blocksEmitted", collectionStage26.stats.blocksEmitted.toLong())
        FarolReadingActivationStage26.Metrics.addTotal("addressParserInvocations", collectionStage26.addressParserInvocations.toLong())
        FarolReadingActivationStage26.Metrics.addTotal("duplicateSubtreesAvoided", collectionStage26.duplicateSubtreesAvoided.toLong())
        FarolForensicCardBlackBoxStage32.recordCollection(
            collectEndedNsStage26, collectionStage26.snapshot.hash, collectionStage26.stats.blocksEmitted,
            collectionStage26.stats.windowsTraversed, collectionStage26.stats.blocksVisited, collectionStage26.stats.earlyExitReason,
        )
        FarolMaximumForensicsStage38.record(
            collectEndedNsStage26, System.currentTimeMillis(), "S38_ACCESSIBILITY_COLLECTION_RESULT", eventPackageStage19, cycleId = cycleIdStage20,
            details = "duration_ns=${(collectEndedNsStage26 - collectStartedNsStage26).coerceAtLeast(0L)}; snapshotHash=${collectionStage26.snapshot.hash}; blocks=${collectionStage26.stats.blocksEmitted}; windowsTotal=${collectionStage26.stats.visibleWindowsTotal}; windowsTraversed=${collectionStage26.stats.windowsTraversed}; nodesVisited=${collectionStage26.stats.blocksVisited}; parserInvocations=${collectionStage26.addressParserInvocations}; duplicatesAvoided=${collectionStage26.duplicateSubtreesAvoided}; reason=${collectionStage26.stats.earlyExitReason}",
        )
        collectionStage26.blocks.forEachIndexed { stage38Index, stage38Block ->
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_ACCESSIBILITY_BLOCK", eventPackageStage19, cycleId = cycleIdStage20,
                details = "index=$stage38Index; id=${stage38Block.id}; parent=${stage38Block.parentId.orEmpty()}; window=${stage38Block.windowId}; layer=${stage38Block.windowLayer}; depth=${stage38Block.depth}; bounds=${stage38Block.left},${stage38Block.top},${stage38Block.right},${stage38Block.bottom}; text=${stage38Block.text.take(1200)}",
            )
        }

        if (!isReadingActivationGenerationFreshStage26(activationStage26.generation)) {
            FarolReadingActivationStage26.Metrics.increment("workCancelledOnReadingOff")
            return true
        }

        val visualDecisionStage23 = stage23VisualGate.observe(collectionStage26.snapshot.hash)
        FarolVisualIdentityStage23.Metrics.recordCollection(
            "Accessibility",
            collectEndedNsStage26 - collectStartedNsStage26,
            collectionStage26.stats,
            visualDecisionStage23.process,
        )
        FarolForensicTraceStage20.accessibilityCollectFinished(
            cycleIdStage20,
            collectEndedNsStage26,
            collectionStage26.stats.visibleWindowsTotal,
            collectionStage26.stats.blocksEmitted,
        )
        val targetEmptyProofStage46 = FarolTargetSurfaceStage46R2.provesCurrentTargetEmpty(
            eventTypeStage20,
            eventPackageStage19,
            currentRootPackageName(),
            stage46TargetSourcePackage,
            packageName,
            cheapSignalStage26.ownOverlay,
            finalLeaseStage44.activeFinal,
            collectionStage26.blocks.size,
        )
        if (targetEmptyProofStage46) {
            val previousEpochStage46R5 = stage46VisualEpoch
            revokeEmptyTargetStage46(
                eventStartedNsStage26,
                eventPackageStage19,
                eventWindowIdStage20,
                collectionStage26.snapshot.hash,
            )
            val rearmActionStage46R5 = FarolAtomicTransitionStage46R5.actionAfterProvenClear(
                readingEnabled = WorkModePolicy0162.isEnabled(currentSettings),
                serviceReady = serviceReady,
                bubbleGestureActive = bubbleGestureActive,
                candidatePresent = false,
            )
            if (rearmActionStage46R5 == FarolAtomicTransitionStage46R5.RearmAction.REQUEST_SINGLE_SHOT_OCR_NOW) {
                val screenshotAlreadyRunningStage46R5 = screenshotInProgress.get()
                stage19VisualVerificationPending = true
                FarolMaximumForensicsStage38.record(
                    SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_R5_ATOMIC_CLEAR_REARM_REQUESTED", eventPackageStage19, cycleId = cycleIdStage20,
                    details = "reason=target_empty; previousEpoch=$previousEpochStage46R5; currentEpoch=$stage46VisualEpoch; epochAdvanced=${FarolAtomicTransitionStage46R5.nextEpochIsFresh(previousEpochStage46R5, stage46VisualEpoch)}; root=${currentRootPackageName().orEmpty()}; eventWindow=$eventWindowIdStage20; yellowCommitted=true; oldTargetReleased=${stage46TargetSourcePackage == null}; screenshotBusy=$screenshotAlreadyRunningStage46R5; noSecondEventRequired=true",
                )
                requestUniversalScreenshotStage19(eventPackageStage19)
                FarolMaximumForensicsStage38.record(
                    SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_R5_ATOMIC_CLEAR_REARM_DISPATCHED", eventPackageStage19, cycleId = cycleIdStage20,
                    details = "reason=target_empty; currentEpoch=$stage46VisualEpoch; requestMode=${if (screenshotAlreadyRunningStage46R5) "coalesced_rerun" else "immediate_screenshot"}; verificationPending=$stage19VisualVerificationPending; noPolling=true",
                )
                FarolCausalLatencyStage28.Metrics.increment("stage46R5AtomicClearRearm")
                FarolCausalLatencyStage28.Metrics.sample(
                    "eventToStage46R5AtomicRearm",
                    SystemClock.elapsedRealtimeNanos() - eventStartedNsStage26,
                )
            }
            return true
        }

        if (!visualDecisionStage23.process) {
            // Stage44: exact raw duplicate proves that the structural event did not change this visual frame.
            // Never turn a valid Green/Red into Yellow before this branch.
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S44_RAW_DUPLICATE_FINAL_PRESERVED", eventPackageStage19, cycleId = cycleIdStage20,
                details = "activeFinal=${finalLeaseStage44.activeFinal}; color=${finalLeaseStage44.color}; distance=${finalLeaseStage44.distanceKm ?: -1.0}; signature=${finalLeaseStage44.addressSignature.orEmpty()}; snapshotHash=${collectionStage26.snapshot.hash}; semanticMutation=${semanticDecisionStage32.mutation}",
            )
            FarolVisualIdentityStage23.Metrics.increment("unchangedVisualSkipped")
            FarolForensicCardBlackBoxStage32.recordAccessibilityEvaluation(SystemClock.elapsedRealtimeNanos(), false)
            if (semanticDecisionStage32.mutation ||
                (stage32ScreenshotRateGate.hasPending() && stage32ScreenshotRateGate.pendingEligible(SystemClock.uptimeMillis(), stage32SemanticGate.snapshot().generation))
            ) {
                stage19VisualVerificationPending = true
                requestUniversalScreenshotStage19(eventPackageStage19, cycleIdStage20)
            }
            return true
        }

        val evaluateStartedNsStage26 = SystemClock.elapsedRealtimeNanos()
        FarolForensicTraceStage20.accessibilityEvaluateStarted(cycleIdStage20, evaluateStartedNsStage26)
        val evaluationStage19 = FarolLatencyProbeStage9.measureValue(
            stage = "STAGE26_UNIVERSAL_VISUAL_ACCESSIBILITY",
            source = "Accessibility",
        ) {
            FarolUniversalVisualPipelineStage19.evaluate(collectionStage26.blocks)
            ?: FarolRouteLocationEvidenceStage46R8.evaluate(collectionStage26.blocks)
            ?: FarolRouteLocationEvidenceStage46R8.evaluateImmediateText(
                cheapSignalStage26.sourceText,
                eventWindowIdStage20,
                FarolUniversalVisualPipelineStage19.Source.Accessibility,
            )
        }
        val evaluateEndedNsStage26 = SystemClock.elapsedRealtimeNanos()
        FarolReadingActivationStage26.Metrics.sample("evaluate", evaluateEndedNsStage26 - evaluateStartedNsStage26)
        FarolCausalLatencyStage28.Metrics.sample("evaluate", evaluateEndedNsStage26 - evaluateStartedNsStage26)
        FarolVisualIdentityStage23.Metrics.recordEvaluate("Accessibility", evaluationStage19 != null, evaluateEndedNsStage26 - evaluateStartedNsStage26)
        FarolForensicTraceStage20.accessibilityEvaluateFinished(cycleIdStage20, evaluateEndedNsStage26, evaluationStage19 != null)
        FarolForensicCardBlackBoxStage32.recordAccessibilityEvaluation(evaluateEndedNsStage26, evaluationStage19 != null)
        FarolMaximumForensicsStage38.record(
            evaluateEndedNsStage26, System.currentTimeMillis(), "S38_ACCESSIBILITY_EVALUATION_RESULT", eventPackageStage19, cycleId = cycleIdStage20,
            details = "candidate=${evaluationStage19 != null}; duration_ns=${(evaluateEndedNsStage26 - evaluateStartedNsStage26).coerceAtLeast(0L)}; pickup=${evaluationStage19?.pickup.orEmpty()}; destination=${evaluationStage19?.destination.orEmpty()}; signature=${evaluationStage19?.addressSignature.orEmpty()}",
        )
        if (evaluationStage19 == null) {
            FarolCausalCorrectionStage21.forensicExplainEvaluationStage38(collectionStage26.blocks).take(320).forEachIndexed { index38, step38 ->
                FarolMaximumForensicsStage38.record(
                    SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_ACCESSIBILITY_EVALUATION_RULE", eventPackageStage19, cycleId = cycleIdStage20,
                    details = "step=$index38; $step38",
                )
            }
        }
        stage23VisualGate.markProcessed(collectionStage26.snapshot.hash, visualDecisionStage23.generation)
        stage23ScheduleGate.satisfyDirect(visualDecisionStage23.generation, collectionStage26.snapshot.hash)

        if (!isReadingActivationGenerationFreshStage26(activationStage26.generation)) {
            FarolReadingActivationStage26.Metrics.increment("workCancelledOnReadingOff")
            return true
        }

        if (evaluationStage19 != null &&
            FarolSemanticFinalLeaseStage44.preservesSameSemanticCard(finalLeaseStage44, evaluationStage19.addressSignature)
        ) {
            // Raw text/layout may change (price, timer, animation) while pickup/destination still identify the same card.
            // Preserve the already-final Google decision and absorb the new raw snapshot as processed.
            stage19VisualVerificationPending = false
            stage19OcrSerial += 1L
            FarolReadingActivationStage26.Metrics.increment("ocrCancelled")
            stage23OcrGate.cancelBecauseAccessibilityWon(visualDecisionStage23.generation, collectionStage26.snapshot.hash)
            stage21OcrGate.cancelBecauseAccessibilityWon()
            stage19OcrRerunRequested = false
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S44_SEMANTIC_SAME_CARD_FINAL_PRESERVED", eventPackageStage19, cycleId = cycleIdStage20,
                details = "color=${finalLeaseStage44.color}; distance=${finalLeaseStage44.distanceKm ?: -1.0}; signature=${evaluationStage19.addressSignature}; snapshotHash=${collectionStage26.snapshot.hash}; admissionGeneration=${admissionStage26.visualGeneration}",
            )
            return true
        }

        val stablePresenceStage46R4 = observeTargetSurfaceStage46R3(stage46TargetSourcePackage)
        val stableActionStage46R4 = FarolStableFinalLatchStage46R4.ambiguousAction(
            finalLeaseStage44.activeFinal,
            evaluationStage19 != null,
            stage46TargetSourcePackage,
            currentRootPackageName(),
            eventPackageStage19,
            packageName,
            stablePresenceStage46R4,
        )
        if (stableActionStage46R4 == FarolStableFinalLatchStage46R4.AmbiguousAction.PRESERVE_NO_VERIFY) {
            // Foreign/SystemUI/host churn has zero authority over a confirmed final that still owns
            // the visible surface. Keep the exact Green/Red+km physically unchanged and do no OCR.
            stage19VisualVerificationPending = false
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_R4_FINAL_LATCH_PRESERVED_FOREIGN", eventPackageStage19, cycleId = cycleIdStage20,
                details = "color=${finalLeaseStage44.color}; distance=${finalLeaseStage44.distanceKm ?: -1.0}; signature=${finalLeaseStage44.addressSignature.orEmpty()}; target=${stage46TargetSourcePackage.orEmpty()}; targetWindow=${stablePresenceStage46R4.windowId}; root=${currentRootPackageName().orEmpty()}; active=${stablePresenceStage46R4.active}; focused=${stablePresenceStage46R4.focused}; noYellow=true; noOcr=true",
            )
            FarolCausalLatencyStage28.Metrics.increment("stage46R4FinalLatchPreservedForeign")
            return true
        }

        val verifyWithoutBlinkStage46R4 = stableActionStage46R4 ==
            FarolStableFinalLatchStage46R4.AmbiguousAction.PRESERVE_AND_VERIFY
        if (verifyWithoutBlinkStage46R4) {
            // The concrete confirmed surface itself changed but Accessibility has not yet proved a
            // different card. Keep the final visible while OCR verifies the current frame. If OCR
            // proves a new two-address card, processUniversalVisualStage19 replaces it; if R2/R3
            // prove disappearance/handoff, those paths already clear immediately.
            stage19VisualVerificationPending = true
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_R4_FINAL_LATCH_VERIFY_WITHOUT_BLINK", eventPackageStage19, cycleId = cycleIdStage20,
                details = "color=${finalLeaseStage44.color}; distance=${finalLeaseStage44.distanceKm ?: -1.0}; signature=${finalLeaseStage44.addressSignature.orEmpty()}; target=${stage46TargetSourcePackage.orEmpty()}; targetWindow=${stablePresenceStage46R4.windowId}; root=${currentRootPackageName().orEmpty()}; noYellow=true; ocrMayVerify=true",
            )
            FarolCausalLatencyStage28.Metrics.increment("stage46R4FinalLatchVerifyWithoutBlink")
        } else {
            // A different candidate or a surface no longer owned by the confirmed target is real
            // proof. Clear immediately to Yellow/no-km before processing the replacement.
            if (!immediateAddressReplacementStage46R8) {
                invalidateOldVisualBeforeCollectStage26(admissionStage26.visualGeneration, eventStartedNsStage26)
            }
            if (evaluationStage19 != null) {
                FarolMaximumForensicsStage38.record(
                    SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_R5_NEW_CANDIDATE_CONTINUES_SAME_CYCLE", eventPackageStage19, cycleId = cycleIdStage20,
                    details = "signature=${evaluationStage19.addressSignature}; window=${evaluationStage19.windowId}; root=${currentRootPackageName().orEmpty()}; yellowCommitted=true; noOcrNeeded=true; noSecondEventRequired=true",
                )
            }
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S44_PROVEN_CARD_CHANGE_INVALIDATE", eventPackageStage19, cycleId = cycleIdStage20,
                details = "candidate=${evaluationStage19 != null}; oldFinal=${finalLeaseStage44.activeFinal}; oldSignature=${finalLeaseStage44.addressSignature.orEmpty()}; newSignature=${evaluationStage19?.addressSignature.orEmpty()}; snapshotHash=${collectionStage26.snapshot.hash}; admissionGeneration=${admissionStage26.visualGeneration}; stage46R4=true",
            )
        }

        if (evaluationStage19 != null) {
            FarolReadingActivationStage26.Metrics.sample("eventToCandidate", SystemClock.elapsedRealtimeNanos() - eventStartedNsStage26)
            FarolCausalLatencyStage28.Metrics.sample("eventToCandidate", SystemClock.elapsedRealtimeNanos() - eventStartedNsStage26)
            FarolVisualIdentityStage23.Metrics.recordEventToCandidate("Accessibility", SystemClock.elapsedRealtimeNanos() - eventStartedNsStage26)
            stage26CandidateEventStartedNs = eventStartedNsStage26
            stage26CandidateActivationGeneration = activationStage26.generation
            stage32SemanticGate.observeCandidate(evaluationStage19.addressSignature)
            FarolForensicCardBlackBoxStage32.recordCandidate(
                SystemClock.elapsedRealtimeNanos(), "Accessibility", evaluationStage19.pickup, evaluationStage19.destination, evaluationStage19.addressSignature,
            )
            stage19VisualVerificationPending = false
            stage19OcrSerial += 1L
            FarolReadingActivationStage26.Metrics.increment("ocrCancelled")
            stage23OcrGate.cancelBecauseAccessibilityWon(visualDecisionStage23.generation, collectionStage26.snapshot.hash)
            stage21OcrGate.cancelBecauseAccessibilityWon()
            stage19OcrRerunRequested = false
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                processUniversalVisualStage19(evaluationStage19, "Accessibility", cycleIdStage20)
            }
        } else {
            stage19VisualVerificationPending = true
            requestUniversalScreenshotStage19(eventPackageStage19, cycleIdStage20)
        }
        return true
    }

    private var stage43LastAppliedManualReading: Boolean? = null
    private var stage43ManualTransitionSerial: Long = 0L
    private var stage43OffRenderAppliedSerial: Long = 0L

    private fun refreshReadingActivationStage26(
        eventPackageStage26: String?,
        eventTypeStage26: Int,
    ): FarolReadingActivationStage26.ActivationSnapshot {
        val startedStage42 = SystemClock.elapsedRealtimeNanos()
        @Suppress("UNUSED_VARIABLE") val provenanceOnlyStage42 = eventPackageStage26 to eventTypeStage26
        val manualEnabledStage42 = FarolManualReadingAuthorityStage42.isEnabled(currentSettings)

        // Stage42: no SelectedRideAppStore, UsageEvents, running processes or selected-window scan
        // is allowed to participate in functional ON/OFF. Stage30/40 presence remains shadow only.
        if (::stage36RuntimeAuthority.isInitialized) {
            stage36RuntimeAuthority.setManualAuthority(manualEnabledStage42)
        }
        val snapshotStage42 = stage26ReadingActivation.setManualAuthority(manualEnabledStage42)

        if (snapshotStage42.enabled != stage28LastActivationEnabled) {
            FarolCausalLatencyStage28.Metrics.increment(if (snapshotStage42.enabled) "activationOn" else "activationOff")
            stage28LastActivationEnabled = snapshotStage42.enabled
        }
        FarolCausalLatencyStage28.Metrics.setGauge("selectedAppsActiveCount", 0L)
        FarolCausalLatencyStage28.Metrics.setGauge("activationGeneration", snapshotStage42.generation)
        FarolCausalLatencyStage28.Metrics.sample(
            "eventToActivationState",
            SystemClock.elapsedRealtimeNanos() - startedStage42,
        )
        return snapshotStage42
    }

    private fun applyReadingOffStage26(snapshotStage26: FarolReadingActivationStage26.ActivationSnapshot) {
        if (stage26LastAppliedActivationGeneration == snapshotStage26.generation &&
            currentRadarColor == RadarColor.Idle && currentDistanceKm == null) return
        stage26LastAppliedActivationGeneration = snapshotStage26.generation
        analyzeJob?.cancel(); analyzeJob = null
        screenshotFallbackJob127?.cancel(); screenshotFallbackJob127 = null
        universalRouteJob?.cancel(); universalRouteJob = null
        stage19OcrSerial += 1L
        stage19OcrRerunRequested = false
        stage21OcrGate.cancelBecauseAccessibilityWon()
        stage23VisualGate.currentHash()?.let(stage23VisualGate::invalidateForExplicitRecovery)
        stage23OcrGate.cancelBecauseAccessibilityWon(stage23VisualGate.currentGeneration(), stage23VisualGate.currentHash() ?: Long.MIN_VALUE)
        lastAnalyzedHash = null
        currentDistanceKm = null
        universalActiveAddressSignature = null
        stage19VisualVerificationPending = true
        stage26PreCollectGate.invalidate()
        if (::stage36RuntimeAuthority.isInitialized) stage36RuntimeAuthority.markExplicitOff("stage26_apply_reading_off")
        stage32SemanticGate.markReadingOff()
        stage32ScreenshotRateGate.reset()
        FarolForensicCardBlackBoxStage32.markReadingOff(SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis())
        FarolForensicCaseStoreStage32.persistIfIntensive(applicationContext)
        stage28RouteGate.invalidateExcept(-1L, -1L)
        FarolCausalLatencyStage28.Metrics.increment("workCancelledOnReadingOff")
        FarolCausalLatencyStage28.Metrics.setGauge("selectedAppsActiveCount", 0L)
        FarolReadingActivationStage26.Metrics.increment("workCancelledOnReadingOff")
        if (currentRadarColor != RadarColor.Idle || currentDistanceKm != null) {
            showOverlay(RadarColor.Idle, distanceKm = null)
        }
        releaseConfirmedTargetStage46R3("reading_off", null)
    }

    private fun isReadingActivationGenerationFreshStage26(expectedGenerationStage26: Long): Boolean {
        @Suppress("UNUSED_VARIABLE") val legacyGenerationStage36 = expectedGenerationStage26
        if (!::stage36RuntimeAuthority.isInitialized) return false
        val runtimeStage36 = stage36RuntimeAuthority.snapshot()
        return runtimeStage36.enabled && runtimeStage36.usageAccessGranted && WorkModePolicy0162.isEnabled(currentSettings)
    }

    private fun buildSemanticSignalStage32(
        eventPackageStage32: String?,
        eventTypeStage32: Int,
        eventWindowIdStage32: Int,
        eventStage32: AccessibilityEvent,
    ): FarolSemanticCardStage32.Signal {
        val sourceStage32 = runCatching { eventStage32.source }.getOrNull()
        val sourcePackageStage32 = normalizePackageName(runCatching { sourceStage32?.packageName?.toString() }.getOrNull())
        val boundsStage32 = Rect()
        runCatching { sourceStage32?.getBoundsInScreen(boundsStage32) }
        val slotStage32 = buildString {
            append(eventWindowIdStage32); append(':')
            append(runCatching { sourceStage32?.viewIdResourceName }.getOrNull().orEmpty()); append(':')
            append(boundsStage32.left); append(':'); append(boundsStage32.top); append(':')
            append(boundsStage32.right); append(':'); append(boundsStage32.bottom)
        }
        val textStage32 = LinkedHashSet<String>(16)
        fun addStage32(value: CharSequence?) {
            value?.toString()?.trim()?.takeIf(String::isNotBlank)?.let { if (textStage32.size < 16) textStage32 += it.take(280) }
        }
        runCatching { eventStage32.text }.getOrDefault(emptyList()).take(8).forEach(::addStage32)
        addStage32(runCatching { sourceStage32?.text }.getOrNull())
        addStage32(runCatching { sourceStage32?.contentDescription }.getOrNull())
        val parentStage32 = runCatching { sourceStage32?.parent }.getOrNull()
        addStage32(runCatching { parentStage32?.text }.getOrNull())
        addStage32(runCatching { parentStage32?.contentDescription }.getOrNull())
        val countStage32 = runCatching { parentStage32?.childCount ?: 0 }.getOrDefault(0).coerceIn(0, 10)
        for (iStage32 in 0 until countStage32) {
            val childStage32 = runCatching { parentStage32?.getChild(iStage32) }.getOrNull() ?: continue
            addStage32(runCatching { childStage32.text }.getOrNull())
            addStage32(runCatching { childStage32.contentDescription }.getOrNull())
            if (textStage32.size >= 16) break
        }
        return FarolSemanticCardStage32.Signal(
            triggerPackage = eventPackageStage32,
            sourcePackage = sourcePackageStage32,
            windowPackage = currentWindowPackageName(),
            windowId = eventWindowIdStage32,
            sourceSlot = slotStage32,
            sourceText = textStage32.joinToString("\n").take(1800),
            eventType = eventTypeStage32,
        )
    }

    private fun buildCheapVisualSignalStage26(
        eventPackageStage26: String?,
        eventTypeStage26: Int,
        eventWindowIdStage26: Int,
        eventStage26: AccessibilityEvent,
    ): FarolReadingActivationStage26.CheapVisualSignal {
        val sourceStage40 = runCatching { eventStage26.source }.getOrNull()
        val parentStage40 = runCatching { sourceStage40?.parent }.getOrNull()
        val sourcePackageStage40 = normalizePackageName(runCatching { sourceStage40?.packageName?.toString() }.getOrNull())
        val parentPackageStage40 = normalizePackageName(runCatching { parentStage40?.packageName?.toString() }.getOrNull())
        val eventPackageNormalizedStage40 = normalizePackageName(eventPackageStage26)
        val ownPackageStage40 = normalizePackageName(packageName)
        val sourceBoundsStage40 = Rect()
        val parentBoundsStage40 = Rect()
        runCatching { sourceStage40?.getBoundsInScreen(sourceBoundsStage40) }
        runCatching { parentStage40?.getBoundsInScreen(parentBoundsStage40) }
        val screenWidthStage40 = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        val screenHeightStage40 = resources.displayMetrics.heightPixels.coerceAtLeast(1)
        fun largeSurfaceStage40(boundsStage40: Rect): Boolean =
            boundsStage40.width() * 100 >= screenWidthStage40 * 55 &&
                boundsStage40.height() * 100 >= screenHeightStage40 * 25
        val sourceLargeStage40 = largeSurfaceStage40(sourceBoundsStage40)
        val parentLargeStage40 = largeSurfaceStage40(parentBoundsStage40)
        val sourceSlotStage40 = buildString {
            append(eventWindowIdStage26); append(':')
            append(runCatching { sourceStage40?.viewIdResourceName }.getOrNull().orEmpty()); append(':')
            append(sourceBoundsStage40.left); append(':'); append(sourceBoundsStage40.top); append(':')
            append(sourceBoundsStage40.right); append(':'); append(sourceBoundsStage40.bottom)
        }
        val addressStage40 = LinkedHashSet<String>(8)
        val bootstrapStage40 = LinkedHashSet<String>(16)
        val structurePiecesStage40 = LinkedHashSet<String>(16)
        val sourceEditableStage40 = runCatching { sourceStage40?.isEditable == true }.getOrDefault(false)

        fun addTextStage40(valueStage40: CharSequence?, allowBootstrapStage40: Boolean = true) {
            val textStage40 = valueStage40?.toString()?.trim()?.takeIf(String::isNotBlank) ?: return
            if (FarolVisualIdentityStage23.countAddressLeads(textStage40) > 0) addressStage40 += textStage40.take(420)
            if (allowBootstrapStage40 && textStage40.length <= 120 && textStage40.count { it == '\n' } <= 1) {
                bootstrapStage40 += textStage40.take(120)
            }
        }
        fun addStructureStage40(nodeStage40: AccessibilityNodeInfo?) {
            nodeStage40 ?: return
            val boundsStage40 = Rect()
            runCatching { nodeStage40.getBoundsInScreen(boundsStage40) }
            val idStage40 = runCatching { nodeStage40.viewIdResourceName }.getOrNull().orEmpty()
            val classStage40 = runCatching { nodeStage40.className?.toString() }.getOrNull().orEmpty()
            val childrenStage40 = runCatching { nodeStage40.childCount }.getOrDefault(0).coerceAtLeast(0)
            structurePiecesStage40 += "$idStage40:$classStage40:${boundsStage40.left / 48}:${boundsStage40.top / 48}:${boundsStage40.right / 48}:${boundsStage40.bottom / 48}:$childrenStage40"
        }

        addTextStage40(runCatching { sourceStage40?.text }.getOrNull(), !sourceEditableStage40)
        addTextStage40(runCatching { sourceStage40?.contentDescription }.getOrNull(), !sourceEditableStage40)
        // Stage39 physical evidence proved an inDrive address can occur after the sixth fragment.
        runCatching { eventStage26.text }.getOrDefault(emptyList()).take(16).forEach { addTextStage40(it, !sourceEditableStage40) }
        addTextStage40(runCatching { parentStage40?.text }.getOrNull())
        addTextStage40(runCatching { parentStage40?.contentDescription }.getOrNull())

        val surfaceStage40 = if (sourceLargeStage40) sourceStage40 else if (parentLargeStage40) parentStage40 else sourceStage40
        addStructureStage40(surfaceStage40)
        val surfaceChildrenStage40 = runCatching { surfaceStage40?.childCount ?: 0 }.getOrDefault(0).coerceIn(0, 12)
        for (indexStage40 in 0 until surfaceChildrenStage40) {
            val childStage40 = runCatching { surfaceStage40?.getChild(indexStage40) }.getOrNull() ?: continue
            val childEditableStage40 = runCatching { childStage40.isEditable }.getOrDefault(false)
            addTextStage40(runCatching { childStage40.text }.getOrNull(), !childEditableStage40)
            addTextStage40(runCatching { childStage40.contentDescription }.getOrNull(), !childEditableStage40)
            addStructureStage40(childStage40)
            if (addressStage40.size >= 6 && bootstrapStage40.size >= 12 && structurePiecesStage40.size >= 12) break
        }

        val bootstrapEligibleStage40 = when (eventTypeStage26) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> true
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> sourceLargeStage40 || parentLargeStage40
            else -> false
        }
        val structuralPackageStage40 = normalizePackageName(
            runCatching { surfaceStage40?.packageName?.toString() }.getOrNull()
        ) ?: parentPackageStage40 ?: sourcePackageStage40 ?: eventPackageNormalizedStage40
        val surfaceBoundsStage40 = if (sourceLargeStage40) sourceBoundsStage40 else if (parentLargeStage40) parentBoundsStage40 else sourceBoundsStage40
        val structuralSignatureStage40 = when {
            !bootstrapEligibleStage40 -> ""
            eventTypeStage26 == AccessibilityEvent.TYPE_WINDOWS_CHANGED && sourceStage40 == null ->
                "window-transition:$eventWindowIdStage26"
            else -> buildString {
                append(structuralPackageStage40.orEmpty()); append(':')
                append(eventTypeStage26); append(':')
                append(surfaceBoundsStage40.left / 48); append(':'); append(surfaceBoundsStage40.top / 48); append(':')
                append(surfaceBoundsStage40.right / 48); append(':'); append(surfaceBoundsStage40.bottom / 48); append(':')
                append(structurePiecesStage40.sorted().joinToString("|").take(1200))
            }
        }
        val ownEventStage40 = eventPackageNormalizedStage40 == ownPackageStage40 &&
            (sourcePackageStage40 == ownPackageStage40 || sourcePackageStage40 == null)
        val ownOverlayStage40 = ownEventStage40 && addressStage40.isEmpty()
        return FarolReadingActivationStage26.CheapVisualSignal(
            ownOverlay = ownOverlayStage40,
            windowSignature = "$eventWindowIdStage26:${sourcePackageStage40.orEmpty()}",
            sourceText = addressStage40.sorted().joinToString("\n").take(1800),
            sourceSlot = sourceSlotStage40,
            contentChangeTypes = runCatching { eventStage26.contentChangeTypes }.getOrDefault(0),
            eventType = eventTypeStage26,
            structuralSignature = structuralSignatureStage40,
            bootstrapText = bootstrapStage40.sorted().joinToString("\n").take(1400),
            bootstrapEligible = bootstrapEligibleStage40,
        )
    }

    private fun collectUniversalAccessibilitySnapshotStage28(eventStage28: AccessibilityEvent): Stage26AccessibilitySnapshot {
        val collectStartedStage32 = SystemClock.elapsedRealtimeNanos()
        val sourceStage32 = runCatching { eventStage28.source }.getOrNull()
        val candidatesStage32 = ArrayList<AccessibilityNodeInfo>(4)
        var cursorStage32 = sourceStage32
        repeat(3) {
            val currentStage32 = cursorStage32 ?: return@repeat
            if (candidatesStage32.none { it === currentStage32 }) candidatesStage32 += currentStage32
            cursorStage32 = runCatching { currentStage32.parent }.getOrNull()
        }
        val activeRootStage32 = runCatching { rootInActiveWindow }.getOrNull()
        if (activeRootStage32 != null && candidatesStage32.none { it === activeRootStage32 }) candidatesStage32 += activeRootStage32

        fun snapshotFromStage32(
            rootStage32: AccessibilityNodeInfo,
            indexStage32: Int,
            reasonStage32: String,
        ): Stage26AccessibilitySnapshot? {
            val packageStage32 = safeNodePackageName0185(rootStage32) ?: "visual.unknown"
            if (normalizePackageName(packageStage32) == normalizePackageName(packageName)) return null
            val budgetStage32 = intArrayOf(0)
            val windowStage32 = runCatching { rootStage32.windowId }.getOrDefault(runCatching { eventStage28.windowId }.getOrDefault(-1))
            val treeStage32 = collectCompactSubtreeStage26(
                rootStage32, "stage32-source:$windowStage32:$indexStage32", null, 1,
                packageStage32, windowStage32, Int.MAX_VALUE - 1, budgetStage32,
            )
            val completeStage32 = treeStage32.completeBlock
            val textStage32 = completeStage32?.text ?: treeStage32.lines.joinToString("\n").take(MAX_ACCESSIBILITY_TEXT_CHARS_0167)
            if (textStage32.isBlank()) return null
            val boundsStage32 = Rect(); runCatching { rootStage32.getBoundsInScreen(boundsStage32) }
            val blockStage32 = FarolUniversalVisualPipelineStage19.VisualBlock(
                id = completeStage32?.id ?: "stage32-partial:$windowStage32:$indexStage32",
                parentId = completeStage32?.parentId,
                metadataPackageName = packageStage32,
                windowId = windowStage32,
                windowLayer = completeStage32?.windowLayer ?: Int.MAX_VALUE - 1,
                depth = completeStage32?.depth ?: 1,
                text = textStage32,
                source = FarolUniversalVisualPipelineStage19.Source.Accessibility,
                left = completeStage32?.left ?: boundsStage32.left,
                top = completeStage32?.top ?: boundsStage32.top,
                right = completeStage32?.right ?: boundsStage32.right,
                bottom = completeStage32?.bottom ?: boundsStage32.bottom,
                syntheticRoot = false,
            )
            val snapshotStage32 = FarolVisualIdentityStage23.snapshot(sequenceOf(FarolVisualIdentityStage23.VisualSeed(
                windowId=blockStage32.windowId, windowLayer=blockStage32.windowLayer, text=blockStage32.text,
                left=blockStage32.left, top=blockStage32.top, right=blockStage32.right, bottom=blockStage32.bottom, syntheticRoot=false,
            )))
            val complete = completeStage32 != null
            val statsStage32 = FarolVisualIdentityStage23.CollectionStats(
                visibleWindowsTotal=1, windowsTraversed=1, windowsSkippedSelf=0, windowsSkippedLowerLayer=0,
                blocksVisited=budgetStage32[0], blocksEmitted=1, earlyExitWindow=windowStage32,
                earlyExitReason=if (complete) "stage32_source_complete_fast_path" else reasonStage32,
                visualSnapshotHash=snapshotStage32.hash,
            )
            if (indexStage32 < 3) FarolSemanticCardStage32.Metrics.increment("sourceFastPath")
            else FarolSemanticCardStage32.Metrics.increment("activeWindowFallback")
            FarolSemanticCardStage32.Metrics.increment("globalFallbackAvoided")
            FarolSemanticCardStage32.Metrics.sample("sourceCollect", SystemClock.elapsedRealtimeNanos()-collectStartedStage32)
            return Stage26AccessibilitySnapshot(listOf(blockStage32),snapshotStage32,statsStage32,1,treeStage32.duplicateSubtreesAvoided)
        }

        candidatesStage32.forEachIndexed { indexStage32, rootStage32 ->
            val reasonStage32 = if (indexStage32 < 3) "stage32_source_partial_to_immediate_ocr" else "stage32_active_window_partial_to_immediate_ocr"
            snapshotFromStage32(rootStage32,indexStage32,reasonStage32)?.let { return it }
        }

        // Full all-window traversal is Stage32's last resort only when source + active window expose no useful text at all.
        FarolSemanticCardStage32.Metrics.increment("globalFallback")
        return collectUniversalAccessibilitySnapshotStage26()
    }

    // Stage44: historical name retained for patch compatibility; callers now invoke it only AFTER
    // collection/evaluation proves a different or ambiguous card. It is no longer a pre-collect action.
    private fun invalidateOldVisualBeforeCollectStage26(newGenerationStage26: Long, eventStartedNsStage26: Long) {
        // Stage36: raw visual mutation revokes the visible old paint immediately, but it is not
        // semantic proof that the current card/destination changed. OCR/route survive until a new
        // final destination, explicit visual disappearance, or true reading OFF proves staleness.
        universalScreenGeneration += 1L
        universalWindowGeneration += 1L
        analyzeJob?.cancel(); analyzeJob = null
        screenshotFallbackJob127?.cancel(); screenshotFallbackJob127 = null
        lastAnalyzedHash = null
        currentDistanceKm = null
        stage19VisualVerificationPending = true
        if (screenshotInProgress.get()) FarolSemanticCardStage32.Metrics.increment("ocrPreservedAcrossRawMutation")
        if (universalRouteJob?.isActive == true) FarolRuntimeAuthorityStage36.Metrics.increment("routePreservedAcrossRawMutation")
        fastFarolStartedAtChecklist13 = System.currentTimeMillis()
        rememberBubbleReason("stage36_visual_verification", "Mudança visual detectada; preservando trabalho do mesmo card até prova semântica.")
        showOverlay(RadarColor.Default, distanceKm = null)
        FarolCausalLatencyStage28.Metrics.increment("oldPaintInvalidated")
        FarolCausalLatencyStage28.Metrics.sample(
            "eventToOldPaintInvalidated",
            SystemClock.elapsedRealtimeNanos() - eventStartedNsStage26,
        )
        FarolReadingActivationStage26.Metrics.sample(
            "eventToOldPaintInvalidated",
            SystemClock.elapsedRealtimeNanos() - eventStartedNsStage26,
        )
        stage26CurrentVisualGeneration = newGenerationStage26
    }

    private fun collectUniversalAccessibilityBlocksStage19(): List<FarolUniversalVisualPipelineStage19.VisualBlock> =
        collectUniversalAccessibilitySnapshotStage26().blocks

    private fun collectUniversalAccessibilitySnapshotStage26(): Stage26AccessibilitySnapshot {
        val visibleWindowsStage26 = runCatching { windows }.getOrDefault(emptyList()).sortedByDescending { runCatching { it.layer }.getOrDefault(0) }
        val outputStage26 = ArrayList<FarolCardBlock0188>(6)
        val seedsStage26 = ArrayList<FarolVisualIdentityStage23.VisualSeed>(8)
        val budgetStage26 = intArrayOf(0)
        var windowsTraversedStage26 = 0
        var windowsSkippedSelfStage26 = 0
        var windowsSkippedLowerStage26 = 0
        var parserInvocationsStage26 = 0
        var duplicatesAvoidedStage26 = 0
        var chosenWindowStage26: Int? = null
        var reasonStage26 = "no_complete_context"

        for ((indexStage26, windowStage26) in visibleWindowsStage26.withIndex()) {
            if (budgetStage26[0] >= MAX_ACCESSIBILITY_NODES_0167) break
            val rootStage26 = runCatching { windowStage26.root }.getOrNull() ?: continue
            val packageStage26 = safeNodePackageName0185(rootStage26) ?: "visual.unknown"
            if (normalizePackageName(packageStage26) == normalizePackageName(packageName)) {
                windowsSkippedSelfStage26 += 1
                continue
            }
            windowsTraversedStage26 += 1
            val windowIdStage26 = runCatching { windowStage26.id }.getOrDefault(-1)
            val layerStage26 = runCatching { windowStage26.layer }.getOrDefault(0)
            val resultStage26 = collectCompactSubtreeStage26(
                rootStage26,
                "stage26:$windowIdStage26",
                null,
                0,
                packageStage26,
                windowIdStage26,
                layerStage26,
                budgetStage26,
            )
            parserInvocationsStage26 += resultStage26.addressParserInvocations
            duplicatesAvoidedStage26 += resultStage26.duplicateSubtreesAvoided
            val completeStage26 = resultStage26.completeBlock
            if (completeStage26 != null) {
                outputStage26 += completeStage26
                chosenWindowStage26 = windowIdStage26
                reasonStage26 = "first_complete_local_context_in_top_visual_window"
                windowsSkippedLowerStage26 += (visibleWindowsStage26.size - indexStage26 - 1).coerceAtLeast(0)
                break
            }
        }

        val blocksStage26 = outputStage26.take(6).map { blockStage26 ->
            FarolUniversalVisualPipelineStage19.VisualBlock(
                id = blockStage26.id,
                parentId = blockStage26.parentId,
                metadataPackageName = blockStage26.packageName,
                windowId = blockStage26.windowId,
                windowLayer = blockStage26.windowLayer,
                depth = blockStage26.depth,
                text = blockStage26.text,
                source = FarolUniversalVisualPipelineStage19.Source.Accessibility,
                left = blockStage26.left,
                top = blockStage26.top,
                right = blockStage26.right,
                bottom = blockStage26.bottom,
                syntheticRoot = false,
            )
        }
        blocksStage26.forEach { blockStage26 ->
            seedsStage26 += FarolVisualIdentityStage23.VisualSeed(
                windowId = blockStage26.windowId,
                windowLayer = blockStage26.windowLayer,
                text = blockStage26.text,
                left = blockStage26.left,
                top = blockStage26.top,
                right = blockStage26.right,
                bottom = blockStage26.bottom,
                syntheticRoot = false,
            )
        }
        if (blocksStage26.isEmpty()) {
            // Empty is still an identity: popup/card closure must invalidate prior visual authority.
            seedsStage26 += FarolVisualIdentityStage23.VisualSeed(-1, Int.MAX_VALUE, "stage26-empty", syntheticRoot = false)
        }
        val snapshotStage26 = FarolVisualIdentityStage23.snapshot(seedsStage26.asSequence())
        val statsStage26 = FarolVisualIdentityStage23.CollectionStats(
            visibleWindowsTotal = visibleWindowsStage26.size,
            windowsTraversed = windowsTraversedStage26,
            windowsSkippedSelf = windowsSkippedSelfStage26,
            windowsSkippedLowerLayer = windowsSkippedLowerStage26,
            blocksVisited = budgetStage26[0],
            blocksEmitted = blocksStage26.size,
            earlyExitWindow = chosenWindowStage26,
            earlyExitReason = reasonStage26,
            visualSnapshotHash = snapshotStage26.hash,
        )
        // Stage21 executes UniversalScreenAddressParser once per emitted block. Stage26 emits at most
        // one coherent block, so this metric is the real downstream parser budget, not cheap lead checks.
        val downstreamParserInvocationsStage26 = blocksStage26.size
        return Stage26AccessibilitySnapshot(blocksStage26, snapshotStage26, statsStage26, downstreamParserInvocationsStage26, duplicatesAvoidedStage26)
    }

    private fun collectCompactSubtreeStage26(
        nodeStage26: AccessibilityNodeInfo,
        idStage26: String,
        parentIdStage26: String?,
        depthStage26: Int,
        packageNameStage26: String,
        windowIdStage26: Int,
        windowLayerStage26: Int,
        budgetStage26: IntArray,
    ): Stage26TreeResult {
        if (budgetStage26[0] >= MAX_ACCESSIBILITY_NODES_0167) return Stage26TreeResult(linkedSetOf(), null, 0, 0)
        budgetStage26[0] += 1
        val linesStage26 = LinkedHashSet<String>(12)
        var parserStage26 = 0
        var duplicatesStage26 = 0
        fun addStage26(valueStage26: CharSequence?) {
            val lineStage26 = valueStage26?.toString()?.trim()?.takeIf(String::isNotBlank) ?: return
            if (!linesStage26.add(lineStage26)) duplicatesStage26 += 1
        }
        addStage26(runCatching { nodeStage26.text }.getOrNull())
        addStage26(runCatching { nodeStage26.contentDescription }.getOrNull())
        // Fast local check before descending: an ancestor that already exposes the complete card
        // is not expanded into dozens of child/parent copies.
        parserStage26 += 1
        val stage38EarlyTwoAddressLeads = depthStage26 > 0 && FarolVisualIdentityStage23.hasTwoAddressLeads(linesStage26.asSequence())
        val stage38NodeBounds = Rect()
        runCatching { nodeStage26.getBoundsInScreen(stage38NodeBounds) }
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_ACCESSIBILITY_NODE_VISITED", packageNameStage26,
            details = "id=$idStage26; parent=${parentIdStage26.orEmpty()}; window=$windowIdStage26; layer=$windowLayerStage26; depth=$depthStage26; budget=${budgetStage26[0]}; bounds=${stage38NodeBounds.left},${stage38NodeBounds.top},${stage38NodeBounds.right},${stage38NodeBounds.bottom}; earlyTwoAddressLeads=$stage38EarlyTwoAddressLeads; lines=${linesStage26.joinToString(" || ").take(1000)}",
        )
        if (stage38EarlyTwoAddressLeads) {
            val boundsStage26 = Rect()
            runCatching { nodeStage26.getBoundsInScreen(boundsStage26) }
            val blockStage26 = FarolCardBlock0188(
                id = idStage26,
                parentId = parentIdStage26,
                packageName = packageNameStage26,
                windowId = windowIdStage26,
                windowLayer = windowLayerStage26,
                depth = depthStage26,
                text = linesStage26.joinToString("\n").take(MAX_ACCESSIBILITY_TEXT_CHARS_0167),
                source = FarolEvidenceSource0188.Accessibility,
                left = boundsStage26.left,
                top = boundsStage26.top,
                right = boundsStage26.right,
                bottom = boundsStage26.bottom,
                syntheticRoot = false,
            )
            return Stage26TreeResult(linesStage26, blockStage26, parserStage26, duplicatesStage26)
        }

        val childCountStage26 = runCatching { nodeStage26.childCount }.getOrDefault(0).coerceIn(0, 64)
        for (indexStage26 in 0 until childCountStage26) {
            if (budgetStage26[0] >= MAX_ACCESSIBILITY_NODES_0167) break
            val childStage26 = runCatching { nodeStage26.getChild(indexStage26) }.getOrNull() ?: continue
            val childResultStage26 = collectCompactSubtreeStage26(
                childStage26,
                "$idStage26/$indexStage26",
                idStage26,
                depthStage26 + 1,
                packageNameStage26,
                windowIdStage26,
                windowLayerStage26,
                budgetStage26,
            )
            parserStage26 += childResultStage26.addressParserInvocations
            duplicatesStage26 += childResultStage26.duplicateSubtreesAvoided
            if (childResultStage26.completeBlock != null) return Stage26TreeResult(linesStage26, childResultStage26.completeBlock, parserStage26, duplicatesStage26)
            childResultStage26.lines.forEach { if (!linesStage26.add(it)) duplicatesStage26 += 1 }
            if (linesStage26.size > 16) break
        }

        parserStage26 += 1
        val localCompleteStage26 = depthStage26 > 0 && FarolVisualIdentityStage23.hasTwoAddressLeads(linesStage26.asSequence())
        if (!localCompleteStage26) return Stage26TreeResult(linesStage26, null, parserStage26, duplicatesStage26)
        val boundsStage26 = Rect()
        runCatching { nodeStage26.getBoundsInScreen(boundsStage26) }
        val blockStage26 = FarolCardBlock0188(
            id = idStage26,
            parentId = parentIdStage26,
            packageName = packageNameStage26,
            windowId = windowIdStage26,
            windowLayer = windowLayerStage26,
            depth = depthStage26,
            text = linesStage26.joinToString("\n").take(MAX_ACCESSIBILITY_TEXT_CHARS_0167),
            source = FarolEvidenceSource0188.Accessibility,
            left = boundsStage26.left,
            top = boundsStage26.top,
            right = boundsStage26.right,
            bottom = boundsStage26.bottom,
            syntheticRoot = false,
        )
        return Stage26TreeResult(linesStage26, blockStage26, parserStage26, duplicatesStage26)
    }

    private fun isStage36WorkFresh(tokenStage36: FarolRuntimeAuthorityStage36.WorkToken): Boolean =
        serviceReady && WorkModePolicy0162.isEnabled(currentSettings) &&
            ::stage36RuntimeAuthority.isInitialized && stage36RuntimeAuthority.isFresh(tokenStage36)

    private fun observePackageForWindowIdStage46R3(windowIdStage46R3: Int): String? {
        if (windowIdStage46R3 > 0) {
            runCatching { windows }.getOrNull().orEmpty().forEach { windowStage46R3 ->
                if (runCatching { windowStage46R3.id }.getOrDefault(0) == windowIdStage46R3) {
                    val packageStage46R3 = runCatching { windowStage46R3.root?.packageName?.toString() }.getOrNull()
                    val normalizedStage46R3 = FarolAcquisitionSurfaceStage46R3.normalizePackage(packageStage46R3)
                    if (normalizedStage46R3 != null && !FarolAcquisitionSurfaceStage46R3.isSystemOrOwn(normalizedStage46R3, packageName)) {
                        return normalizedStage46R3
                    }
                }
            }
            val rememberedStage46R3 = stage46AcquisitionSurfaceByWindowId[windowIdStage46R3]
            if (rememberedStage46R3 != null && rememberedStage46R3.first == stage46VisualEpoch) {
                return rememberedStage46R3.second
            }
        }
        return FarolAcquisitionSurfaceStage46R3.normalizePackage(currentRootPackageName())
            ?.takeUnless { FarolAcquisitionSurfaceStage46R3.isSystemOrOwn(it, packageName) }
    }

    private fun observeTargetSurfaceStage46R3(targetPackageStage46: String?): FarolAcquisitionSurfaceStage46R3.SurfacePresence {
        val expectedStage46 = FarolAcquisitionSurfaceStage46R3.normalizePackage(targetPackageStage46)
            ?: return FarolAcquisitionSurfaceStage46R3.SurfacePresence()
        val matchesStage46 = runCatching { windows }.getOrNull().orEmpty().mapNotNull { windowStage46 ->
            val windowPackageStage46 = runCatching { windowStage46.root?.packageName?.toString() }.getOrNull()
            if (FarolAcquisitionSurfaceStage46R3.normalizePackage(windowPackageStage46) != expectedStage46) {
                null
            } else {
                val idStage46 = runCatching { windowStage46.id }.getOrDefault(0)
                if (idStage46 <= 0) null else FarolAcquisitionSurfaceStage46R3.SurfacePresence(
                    windowId = idStage46,
                    active = runCatching { windowStage46.isActive }.getOrDefault(false),
                    focused = runCatching { windowStage46.isFocused }.getOrDefault(false),
                    layer = runCatching { windowStage46.layer }.getOrDefault(Int.MIN_VALUE),
                )
            }
        }
        val bestStage46 = matchesStage46.maxWithOrNull(
            compareBy<FarolAcquisitionSurfaceStage46R3.SurfacePresence> { if (it.active) 1 else 0 }
                .thenBy { if (it.focused) 1 else 0 }
                .thenBy { it.layer },
        )
        if (bestStage46 != null) return bestStage46

        val rootStage46 = runCatching { rootInActiveWindow }.getOrNull()
        val rootPackageStage46 = FarolAcquisitionSurfaceStage46R3.normalizePackage(rootStage46?.packageName?.toString())
        return if (rootPackageStage46 == expectedStage46) {
            FarolAcquisitionSurfaceStage46R3.SurfacePresence(
                windowId = runCatching { rootStage46?.windowId ?: 0 }.getOrDefault(0),
                active = true,
                focused = true,
                layer = Int.MAX_VALUE,
            )
        } else FarolAcquisitionSurfaceStage46R3.SurfacePresence()
    }

    private fun observeTargetWindowIdStage46(targetPackageStage46: String?): Int {
        val expectedStage46 = FarolTargetSurfaceStage46R2.normalizePackage(targetPackageStage46) ?: return 0
        val observedStage46 = runCatching { windows }.getOrNull().orEmpty()
        observedStage46.forEach { windowStage46 ->
            val windowPackageStage46 = runCatching { windowStage46.root?.packageName?.toString() }.getOrNull()
            if (FarolTargetSurfaceStage46R2.normalizePackage(windowPackageStage46) == expectedStage46) {
                val idStage46 = runCatching { windowStage46.id }.getOrDefault(0)
                if (idStage46 > 0) return idStage46
            }
        }
        val rootStage46 = runCatching { rootInActiveWindow }.getOrNull()
        val rootPackageStage46 = FarolTargetSurfaceStage46R2.normalizePackage(rootStage46?.packageName?.toString())
        return if (rootPackageStage46 == expectedStage46) runCatching { rootStage46?.windowId ?: 0 }.getOrDefault(0) else 0
    }

    private fun bindCandidateTargetSurfaceStage46(candidatePackageStage46: String?, candidateWindowStage46: Int, sourceStage46: String) {
        val targetPackageStage46 = FarolTargetSurfaceStage46R2.chooseCandidateTargetPackage(
            currentRootPackageName(), candidatePackageStage46, packageName,
        ) ?: return
        val observedWindowStage46 = observeTargetWindowIdStage46(targetPackageStage46)
        val resolvedWindowStage46 = observedWindowStage46.takeIf { it > 0 }
            ?: candidateWindowStage46.takeIf { it > 0 }
            ?: runCatching { rootInActiveWindow?.windowId ?: 0 }.getOrDefault(0)
        val changedStage46 = targetPackageStage46 != stage46TargetSourcePackage ||
            (resolvedWindowStage46 > 0 && stage46TargetWindowId > 0 && resolvedWindowStage46 != stage46TargetWindowId)
        stage46TargetSourcePackage = targetPackageStage46
        if (resolvedWindowStage46 > 0) stage46TargetWindowId = resolvedWindowStage46
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_R2_TARGET_BOUND", candidatePackageStage46,
            details = "source=$sourceStage46; target=$targetPackageStage46; targetWindow=$stage46TargetWindowId; candidateWindow=$candidateWindowStage46; root=${currentRootPackageName().orEmpty()}; changed=$changedStage46; epoch=$stage46VisualEpoch",
        )
    }

    private fun releaseConfirmedTargetStage46R3(reasonStage46R3: String, eventPackageStage46R3: String?) {
        val oldPackageStage46R3 = stage46TargetSourcePackage
        val oldWindowStage46R3 = stage46TargetWindowId
        stage46TargetSourcePackage = null
        stage46TargetWindowId = 0
        stage46LastHardBoundaryGeneration = Long.MIN_VALUE
        stage46AcquisitionSurfaceByWindowId.clear()
        if (oldPackageStage46R3 != null || oldWindowStage46R3 > 0) {
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_R3_TARGET_RELEASED", eventPackageStage46R3,
                details = "reason=$reasonStage46R3; oldTarget=${oldPackageStage46R3.orEmpty()}; oldWindow=$oldWindowStage46R3; epoch=$stage46VisualEpoch",
            )
        }
    }

    private fun revokeForegroundSurfaceHandoffStage46R3(
        eventStartedNsStage46R3: Long,
        eventPackageStage46R3: String?,
        eventWindowStage46R3: Int,
        admissionGenerationStage46R3: Long,
        newRootStage46R3: String?,
    ) {
        val previousEpochStage46R3 = stage46VisualEpoch
        val oldTargetStage46R3 = stage46TargetSourcePackage
        val oldWindowStage46R3 = stage46TargetWindowId
        stage46VisualEpoch += 1L
        if (::stage36RuntimeAuthority.isInitialized) stage36RuntimeAuthority.clearVisualLease("stage46_r3_foreground_handoff")
        screenshotFallbackJob127?.cancel(); screenshotFallbackJob127 = null
        universalRouteJob?.cancel(); universalRouteJob = null
        stage19OcrSerial += 1L
        stage19OcrRerunRequested = false
        stage36BindingWorkToken.clear()
        stage46BindingSurfaceToken.clear()
        universalScreenGeneration += 1L
        universalWindowGeneration += 1L
        universalActiveAddressSignature = null
        lastAnalyzedHash = null
        currentDistanceKm = null
        stage19VisualVerificationPending = true
        showOverlay(RadarColor.Default, distanceKm = null)
        releaseConfirmedTargetStage46R3("foreground_handoff", eventPackageStage46R3)
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_R3_FOREGROUND_HANDOFF_FINAL_REVOKED", eventPackageStage46R3,
            details = "fromEpoch=$previousEpochStage46R3; toEpoch=$stage46VisualEpoch; oldTarget=${oldTargetStage46R3.orEmpty()}; oldWindow=$oldWindowStage46R3; newRoot=${newRootStage46R3.orEmpty()}; eventWindow=$eventWindowStage46R3; admissionGeneration=$admissionGenerationStage46R3; yellowCommitted=true; acquisitionContinuesSameCycle=true",
        )
        FarolCausalLatencyStage28.Metrics.increment("stage46R3ForegroundHandoffRevoked")
        FarolCausalLatencyStage28.Metrics.sample(
            "eventToStage46R3ForegroundHandoffRevoked",
            SystemClock.elapsedRealtimeNanos() - eventStartedNsStage46R3,
        )
    }

    private fun revokeEmptyTargetStage46(
        eventStartedNsStage46: Long,
        eventPackageStage46: String?,
        eventWindowStage46: Int,
        snapshotHashStage46: Long,
    ) {
        val previousEpochStage46 = stage46VisualEpoch
        val releasedTargetPackageStage46R3 = stage46TargetSourcePackage
        val releasedTargetWindowStage46R3 = stage46TargetWindowId
        stage46VisualEpoch += 1L
        if (::stage36RuntimeAuthority.isInitialized) stage36RuntimeAuthority.clearVisualLease("stage46_r2_target_empty")
        screenshotFallbackJob127?.cancel(); screenshotFallbackJob127 = null
        universalRouteJob?.cancel(); universalRouteJob = null
        stage19OcrSerial += 1L
        stage19OcrRerunRequested = false
        stage36BindingWorkToken.clear()
        stage46BindingSurfaceToken.clear()
        universalScreenGeneration += 1L
        universalWindowGeneration += 1L
        universalActiveAddressSignature = null
        lastAnalyzedHash = null
        currentDistanceKm = null
        stage19VisualVerificationPending = true
        showOverlay(RadarColor.Default, distanceKm = null)
        releaseConfirmedTargetStage46R3("target_empty", eventPackageStage46)
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_TARGET_EMPTY_FINAL_REVOKED", eventPackageStage46,
            details = "fromEpoch=$previousEpochStage46; toEpoch=$stage46VisualEpoch; target=${releasedTargetPackageStage46R3.orEmpty()}; targetWindow=$releasedTargetWindowStage46R3; eventWindow=$eventWindowStage46; snapshotHash=$snapshotHashStage46; oldWorkCancelled=true; yellowCommitted=true; targetReleased=true",
        )
        FarolCausalLatencyStage28.Metrics.increment("stage46TargetEmptyFinalRevoked")
        FarolCausalLatencyStage28.Metrics.sample(
            "eventToStage46TargetEmptyRevoked",
            SystemClock.elapsedRealtimeNanos() - eventStartedNsStage46,
        )
    }

    private fun advanceHardVisualEpochStage46(
        admissionGenerationStage46: Long,
        eventStartedNsStage46: Long,
        eventPackageStage46: String?,
        eventWindowStage46: Int,
        structuralStage46: String,
    ) {
        val previousEpochStage46 = stage46VisualEpoch
        stage46VisualEpoch += 1L
        stage46LastHardBoundaryGeneration = admissionGenerationStage46

        // A true Android window-list transition is explicit visual disappearance/replacement evidence.
        // Unlike Stage36 raw churn, old OCR/route/final work is not allowed to survive this boundary.
        if (::stage36RuntimeAuthority.isInitialized) {
            stage36RuntimeAuthority.clearVisualLease("stage46_hard_window_boundary")
        }
        analyzeJob?.cancel(); analyzeJob = null
        screenshotFallbackJob127?.cancel(); screenshotFallbackJob127 = null
        universalRouteJob?.cancel(); universalRouteJob = null
        stage19OcrSerial += 1L
        stage19OcrRerunRequested = false
        stage36BindingWorkToken.clear()
        stage46BindingSurfaceToken.clear()
        universalScreenGeneration += 1L
        universalWindowGeneration += 1L
        universalActiveAddressSignature = null
        lastAnalyzedHash = null
        currentDistanceKm = null
        stage19VisualVerificationPending = true
        showOverlay(RadarColor.Default, distanceKm = null)
        releaseConfirmedTargetStage46R3("hard_visual_boundary", eventPackageStage46)

        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_HARD_VISUAL_BOUNDARY", eventPackageStage46,
            details = "fromEpoch=$previousEpochStage46; toEpoch=$stage46VisualEpoch; admissionGeneration=$admissionGenerationStage46; eventWindow=$eventWindowStage46; structural=${structuralStage46.take(300)}; oldWorkCancelled=true; oldSignatureCleared=true; yellowCommitted=true",
        )
        FarolCausalLatencyStage28.Metrics.increment("stage46HardVisualBoundary")
        FarolCausalLatencyStage28.Metrics.sample(
            "eventToStage46HardBoundary",
            SystemClock.elapsedRealtimeNanos() - eventStartedNsStage46,
        )
    }

    private fun isStage46OcrWorkFresh(
        tokenStage36: FarolRuntimeAuthorityStage36.WorkToken,
        surfaceStage46: FarolVisualEpochNoResultStage46.SurfaceToken,
    ): Boolean {
        val runtimeFreshStage46 = isStage36WorkFresh(tokenStage36)
        val acquisitionPresenceStage46R3 = observeTargetSurfaceStage46R3(surfaceStage46.packageName)
        val surfaceFreshStage46 = FarolAcquisitionSurfaceStage46R3.acquisitionSurfaceFresh(
            surfaceStage46, currentRootPackageName(), acquisitionPresenceStage46R3, stage46VisualEpoch,
        )
        if (runtimeFreshStage46 && !surfaceFreshStage46) {
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_STALE_OCR_SURFACE_DROPPED", currentRootPackageName(),
                details = "captured=${surfaceStage46.packageName.orEmpty()}; current=${currentRootPackageName().orEmpty()}; window=${surfaceStage46.windowId}; capturedEpoch=${surfaceStage46.visualEpoch}; currentEpoch=$stage46VisualEpoch",
            )
        }
        return runtimeFreshStage46 && surfaceFreshStage46
    }

    private fun requestUniversalScreenshotStage19(
        eventPackageStage19: String?,
        cycleIdStage20: Long? = null,
        rerunDemandStage23: FarolVisualIdentityStage23.OcrDemand? = null,
    ) {
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_OCR_DEMAND_ENTER", eventPackageStage19, cycleId = cycleIdStage20,
            details = "rerun=${rerunDemandStage23 != null}; screenshotBusy=${screenshotInProgress.get()}; workMode=${WorkModePolicy0162.isEnabled(currentSettings)}; serviceReady=$serviceReady; bubbleGesture=$bubbleGestureActive",
        )
        if (!serviceReady || !WorkModePolicy0162.isEnabled(currentSettings)) return
        if (bubbleGestureActive) return // bubble_drag_screenshot_pause_0_1_116
        // bubble_drag_ocr_background_0_1_116 — OCR extraction remains on Dispatchers.Default below.
        val activationStage26 = stage26ReadingActivation.snapshot()
        if (!activationStage26.enabled || !activationStage26.usageAccessGranted) return
        stage26OcrActivationGeneration = activationStage26.generation
        FarolReadingActivationStage26.Metrics.increment("ocrRequests")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val semanticStage32 = stage32SemanticGate.snapshot()
        if (screenshotInProgress.get()) {
            stage32ScreenshotRateGate.queue(semanticStage32.generation)
            FarolForensicCardBlackBoxStage32.recordOcrRequest(SystemClock.elapsedRealtimeNanos(), false, "single_flight_busy_queued")
            return
        }
        val rateStage32 = stage32ScreenshotRateGate.request(SystemClock.uptimeMillis(), semanticStage32.generation)
        if (!rateStage32.startNow) {
            FarolForensicCardBlackBoxStage32.recordOcrRequest(SystemClock.elapsedRealtimeNanos(), false, rateStage32.reason)
            FarolForensicTraceStage20.note(
                SystemClock.elapsedRealtimeNanos(), "S32_OCR_SCREENSHOT_DEFERRED", cycleIdStage20,
                details = "reason=${rateStage32.reason}; semantic_generation=${semanticStage32.generation}; eligible_at=${rateStage32.eligibleAtUptimeMs}",
            )
            return
        }

        val demandStage23 = FarolVisualIdentityStage23.OcrDemand(
            visualGeneration = semanticStage32.generation,
            snapshotHash = semanticStage32.fingerprint,
            packageHint = eventPackageStage19,
            cycleId = cycleIdStage20,
        )
        FarolVisualIdentityStage23.Metrics.increment("ocrRequests")
        val requestStage23 = if (rerunDemandStage23 != null) stage23OcrGate.installRerun(demandStage23) else stage23OcrGate.request(demandStage23)
        if (!requestStage23.startNow) {
            stage32ScreenshotRateGate.complete(semanticStage32.generation, false)
            stage32ScreenshotRateGate.queue(semanticStage32.generation)
            FarolVisualIdentityStage23.Metrics.increment("ocrDeferred")
            FarolForensicCardBlackBoxStage32.recordOcrRequest(SystemClock.elapsedRealtimeNanos(), false, requestStage23.reason)
            stage19OcrRerunRequested = false
            FarolForensicTraceStage20.ocrStage(
                SystemClock.elapsedRealtimeNanos(),
                requestStage23.token,
                "DEFERRED_BUSY",
                cycleIdStage20,
                "stage23=${requestStage23.reason}; visual_generation=${demandStage23.visualGeneration}; visual_snapshot_hash=${demandStage23.snapshotHash}",
            )
            FarolForensicTraceStage20.note(
                SystemClock.elapsedRealtimeNanos(),
                if (requestStage23.reason == "same_visual_generation_busy") "S23_OCR_DEFERRED_SAME_VISUAL" else "S23_OCR_DEFERRED_NEW_VISUAL",
                cycleIdStage20,
                operationId = "ocr-${requestStage23.token}",
                details = "reason=${requestStage23.reason}; visual_generation=${demandStage23.visualGeneration}; visual_snapshot_hash=${demandStage23.snapshotHash}",
            )
            return
        }

        FarolVisualIdentityStage23.Metrics.increment("ocrStarts")
        FarolReadingActivationStage26.Metrics.increment("ocrStarts")
        FarolForensicCardBlackBoxStage32.recordOcrRequest(SystemClock.elapsedRealtimeNanos(), true, requestStage23.reason)
        val serialStage19 = ++stage19OcrSerial
        val tokenStage23 = requestStage23.token
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_OCR_REQUEST_ACCEPTED", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",
            details = "s23token=$tokenStage23; visualGeneration=${demandStage23.visualGeneration}; snapshotHash=${demandStage23.snapshotHash}; reason=${requestStage23.reason}",
        )
        val workTokenStage36 = stage36RuntimeAuthority.captureWorkToken() ?: run {
            stage23OcrGate.complete(tokenStage23)
            stage32ScreenshotRateGate.complete(semanticStage32.generation, false)
            return
        }
        FarolForensicTraceStage20.ocrStage(
            SystemClock.elapsedRealtimeNanos(),
            serialStage19,
            "REQUEST",
            cycleIdStage20,
            "package=${eventPackageStage19.orEmpty()}; s23token=$tokenStage23; visual_generation=${demandStage23.visualGeneration}; visual_snapshot_hash=${demandStage23.snapshotHash}; reason=${requestStage23.reason}",
        )
        val visualWindowIdStage19 = stage19ActiveWindowId ?: runCatching { rootInActiveWindow?.windowId }.getOrNull() ?: 0
        val confirmedPresenceStage46R3 = observeTargetSurfaceStage46R3(stage46TargetSourcePackage)
        val acquisitionStage46R3 = FarolAcquisitionSurfaceStage46R3.chooseAcquisitionPackage(
            stage46TargetSourcePackage,
            currentRootPackageName(),
            eventPackageStage19,
            packageName,
            confirmedPresenceStage46R3,
        )
        val targetPackageForOcrStage46 = acquisitionStage46R3.packageName
        val observedTargetWindowForOcrStage46 = observeTargetWindowIdStage46(targetPackageForOcrStage46)
        val targetWindowForOcrStage46 = observedTargetWindowForOcrStage46.takeIf { it > 0 } ?: visualWindowIdStage19
        val surfaceTokenStage46 = FarolVisualEpochNoResultStage46.captureSurface(
            targetPackageForOcrStage46, null, targetWindowForOcrStage46, stage46VisualEpoch,
        )
        val acquisitionPackageStage46R3 = FarolAcquisitionSurfaceStage46R3.normalizePackage(surfaceTokenStage46.packageName)
        if (surfaceTokenStage46.windowId > 0 && acquisitionPackageStage46R3 != null) {
            if (stage46AcquisitionSurfaceByWindowId.size >= 16) {
                val firstStage46R3 = stage46AcquisitionSurfaceByWindowId.keys.firstOrNull()
                if (firstStage46R3 != null) stage46AcquisitionSurfaceByWindowId.remove(firstStage46R3)
            }
            stage46AcquisitionSurfaceByWindowId[surfaceTokenStage46.windowId] = stage46VisualEpoch to acquisitionPackageStage46R3
        }
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_R3_ACQUISITION_SURFACE_CAPTURED", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",
            details = "surfacePackage=${surfaceTokenStage46.packageName.orEmpty()}; surfaceWindow=${surfaceTokenStage46.windowId}; visualEpoch=${surfaceTokenStage46.visualEpoch}; root=${currentRootPackageName().orEmpty()}; confirmedTarget=${stage46TargetSourcePackage.orEmpty()}; confirmedWindow=${confirmedPresenceStage46R3.windowId}; confirmedActive=${confirmedPresenceStage46R3.active}; confirmedFocused=${confirmedPresenceStage46R3.focused}; reason=${acquisitionStage46R3.reason}",
        )
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_OCR_SURFACE_CAPTURED", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",
            details = "surfacePackage=${surfaceTokenStage46.packageName.orEmpty()}; surfaceWindow=${surfaceTokenStage46.windowId}; visualEpoch=${surfaceTokenStage46.visualEpoch}; root=${currentRootPackageName().orEmpty()}; ownedTarget=${stage46TargetSourcePackage.orEmpty()}; acquisitionReason=${acquisitionStage46R3.reason}",
        )
        if (!screenshotInProgress.compareAndSet(false, true)) {
            FarolVisualIdentityStage23.Metrics.increment("ocrDeferred")
            stage23OcrGate.complete(tokenStage23)
            FarolForensicTraceStage20.ocrStage(
                SystemClock.elapsedRealtimeNanos(), serialStage19, "DEFERRED_BUSY", cycleIdStage20,
                "stage23=atomic_race; visual_generation=${demandStage23.visualGeneration}; visual_snapshot_hash=${demandStage23.snapshotHash}",
            )
            return
        }
        stage19OcrRerunRequested = false

        fun rerunIfUsefulStage23(completionStage23: FarolVisualIdentityStage23.OcrCompletion) {
            val rerunStage23 = completionStage23.rerun ?: return
            val currentSemanticStage32 = stage32SemanticGate.snapshot()
            if (rerunStage23.visualGeneration != currentSemanticStage32.generation || rerunStage23.snapshotHash != currentSemanticStage32.fingerprint) {
                FarolForensicTraceStage20.note(SystemClock.elapsedRealtimeNanos(), "S32_OCR_RERUN_DROPPED_REAL_SEMANTIC_STALE", rerunStage23.cycleId)
                return
            }
            // Do not recursively call takeScreenshot. Android enforces >333 ms between requests.
            // Queue exactly one demand and let the next qualifying Accessibility event drain it.
            stage32ScreenshotRateGate.queue(currentSemanticStage32.generation)
            FarolForensicCardBlackBoxStage32.recordOcrRetry(SystemClock.elapsedRealtimeNanos(), "queued_for_next_event_after_single_flight")
            FarolVisualIdentityStage23.Metrics.increment("ocrReruns")
        }

        runCatching {
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_SCREENSHOT_REQUEST", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",
                details = "display=${Display.DEFAULT_DISPLAY}; visualGeneration=${demandStage23.visualGeneration}; snapshotHash=${demandStage23.snapshotHash}",
            )
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        FarolMaximumForensicsStage38.record(
                            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_SCREENSHOT_CALLBACK", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",
                            details = "status=success_callback; visualGeneration=${demandStage23.visualGeneration}; snapshotHash=${demandStage23.snapshotHash}",
                        )
                        FarolForensicCardBlackBoxStage32.recordScreenshot(SystemClock.elapsedRealtimeNanos(), "CALLBACK")
                        FarolForensicTraceStage20.ocrStage(
                            SystemClock.elapsedRealtimeNanos(), serialStage19, "SCREENSHOT_CALLBACK", cycleIdStage20,
                            "visual_generation=${demandStage23.visualGeneration}; visual_snapshot_hash=${demandStage23.snapshotHash}",
                        )
                        scope.launch {
                            var bitmapStage19: Bitmap? = null
                            try {
                                if (!isStage46OcrWorkFresh(workTokenStage36, surfaceTokenStage46)) {
                                    FarolVisualIdentityStage23.Metrics.increment("ocrStaleBeforeBitmap")
                                    FarolReadingActivationStage26.Metrics.increment("ocrStale")
                                    FarolForensicCardBlackBoxStage32.recordOcrStale(SystemClock.elapsedRealtimeNanos(), true, "semantic_generation_or_activation_changed")
                                    FarolForensicTraceStage20.ocrStage(
                                        SystemClock.elapsedRealtimeNanos(), serialStage19, "STALE_BEFORE_BITMAP", cycleIdStage20,
                                        "stage23_visual_generation=${demandStage23.visualGeneration}; stage23_visual_snapshot_hash=${demandStage23.snapshotHash}",
                                    )
                                    return@launch
                                }
                                FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "BITMAP_CONVERT_START", cycleIdStage20)
                                bitmapStage19 = screenshot.toSoftwareBitmap() ?: run {
                                    FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "BITMAP_CONVERT_FAILED", cycleIdStage20)
                                    return@launch
                                }
                                val screenshotHashStage32 = FarolPrintStoreStage32.sampleHash(bitmapStage19!!)
                                FarolMaximumForensicsStage38.record(
                                    SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_SCREENSHOT_BITMAP_READY", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",
                                    details = "hash=$screenshotHashStage32; width=${bitmapStage19!!.width}; height=${bitmapStage19!!.height}; config=${bitmapStage19!!.config}",
                                )
                                FarolForensicCardBlackBoxStage32.recordScreenshot(SystemClock.elapsedRealtimeNanos(), "SUCCESS", hash = screenshotHashStage32)
                                FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "BITMAP_CONVERT_END", cycleIdStage20)
                                if (!isStage46OcrWorkFresh(workTokenStage36, surfaceTokenStage46)) {
                                    FarolVisualIdentityStage23.Metrics.increment("ocrStaleBeforeExtract")
                                    FarolReadingActivationStage26.Metrics.increment("ocrStale")
                                    FarolForensicCardBlackBoxStage32.recordOcrStale(SystemClock.elapsedRealtimeNanos(), true, "semantic_generation_or_activation_changed")
                                    FarolForensicTraceStage20.ocrStage(
                                        SystemClock.elapsedRealtimeNanos(), serialStage19, "STALE_BEFORE_EXTRACT", cycleIdStage20,
                                        "stage23_visual_generation=${demandStage23.visualGeneration}; stage23_visual_snapshot_hash=${demandStage23.snapshotHash}",
                                    )
                                    return@launch
                                }

                                val ocrStartedNsStage20 = SystemClock.elapsedRealtimeNanos()
                                FarolMaximumForensicsStage38.record(
                                    ocrStartedNsStage20, System.currentTimeMillis(), "S38_OCR_EXTRACT_START", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",
                                    details = "screenshotHash=$screenshotHashStage32",
                                )
                                val structuredStage19 = withContext(Dispatchers.Default) {
                                    ocrService.extractStructuredText(bitmapStage19)
                                }
                                val extractEndedNsStage20 = SystemClock.elapsedRealtimeNanos()
                                FarolMaximumForensicsStage38.record(
                                    extractEndedNsStage20, System.currentTimeMillis(), "S38_OCR_EXTRACT_END", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",
                                    details = "duration_ns=${(extractEndedNsStage20 - ocrStartedNsStage20).coerceAtLeast(0L)}; blocks=${structuredStage19.blocks.size}; text_len=${structuredStage19.text.length}; text_hash=${structuredStage19.text.hashCode()}; fullText=${structuredStage19.text.take(1300)}",
                                )
                                structuredStage19.blocks.forEachIndexed { index38, block38 ->
                                    FarolMaximumForensicsStage38.record(
                                        SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_OCR_BLOCK", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",
                                        details = "index=$index38; id=${block38.id}; bounds=${block38.left},${block38.top},${block38.right},${block38.bottom}; text=${block38.text.take(1300)}",
                                    )
                                }
                                FarolForensicTraceStage20.ocrStage(
                                    extractEndedNsStage20,
                                    serialStage19,
                                    "EXTRACT_END",
                                    cycleIdStage20,
                                    "extract_us=${(extractEndedNsStage20 - ocrStartedNsStage20).coerceAtLeast(0L) / 1000L}; blocks=${structuredStage19.blocks.size}; stage23_non_cancelable_call=true",
                                )
                                FarolForensicCardBlackBoxStage32.recordOcrExtract(extractEndedNsStage20, structuredStage19.blocks.size, extractEndedNsStage20 - ocrStartedNsStage20)
                                if (!isStage46OcrWorkFresh(workTokenStage36, surfaceTokenStage46)) {
                                    FarolVisualIdentityStage23.Metrics.increment("ocrStaleAfterExtract")
                                    FarolReadingActivationStage26.Metrics.increment("ocrStale")
                                    FarolForensicCardBlackBoxStage32.recordOcrStale(SystemClock.elapsedRealtimeNanos(), true, "semantic_generation_or_activation_changed")
                                    FarolForensicTraceStage20.ocrStage(
                                        SystemClock.elapsedRealtimeNanos(), serialStage19, "STALE_AFTER_EXTRACT", cycleIdStage20,
                                        "latestSerial=$stage19OcrSerial; stage23_non_cancelable_extract_completed_stale=true; visual_generation=${demandStage23.visualGeneration}; visual_snapshot_hash=${demandStage23.snapshotHash}",
                                    )
                                    return@launch
                                }

                                val fragmentsStage19 = structuredStage19.blocks.take(120).mapIndexedNotNull { indexStage19, blockStage19 ->
                                    val textStage46 = blockStage19.text.takeIf(String::isNotBlank) ?: return@mapIndexedNotNull null
                                    if (FarolVisualEpochNoResultStage46.shouldDropSelfOverlayDecimal(
                                            textStage46,
                                            blockStage19.left, blockStage19.top, blockStage19.right, blockStage19.bottom,
                                            bitmapStage19?.width ?: 0, bitmapStage19?.height ?: 0,
                                        )
                                    ) {
                                        FarolMaximumForensicsStage38.record(
                                            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_SELF_OVERLAY_OCR_FRAGMENT_DROPPED", eventPackageStage19,
                                            cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",
                                            details = "text=${textStage46.take(80)}; bounds=${blockStage19.left},${blockStage19.top},${blockStage19.right},${blockStage19.bottom}",
                                        )
                                        null
                                    } else {
                                        FarolSpatialFragment0189(
                                            id = "stage19-ocr:$serialStage19/$indexStage19",
                                            text = textStage46,
                                            left = blockStage19.left,
                                            top = blockStage19.top,
                                            right = blockStage19.right,
                                            bottom = blockStage19.bottom,
                                        )
                                    }
                                }
                                val blocksStage19 = FarolVisualPriority0189.cluster("stage19-ocr:$serialStage19", fragmentsStage19)
                                    .map { groupStage19 ->
                                        // Stage45 repairs OCR wrapping only AFTER bounds-based clustering. Different
                                        // visual groups/cards are never concatenated by this reconstruction layer.
                                        val sanitizedStage46 = FarolVisualEpochNoResultStage46.sanitizeForReconstruction(groupStage19.text)
                                        if (sanitizedStage46.changed) {
                                            FarolMaximumForensicsStage38.record(
                                                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_OCR_RECONSTRUCTION_DECONTAMINATED", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",
                                                details = "group=${groupStage19.id}; decimals=${sanitizedStage46.removedStandaloneDecimals}; closures=${sanitizedStage46.syntheticClosures}; before=${groupStage19.text.replace("\n", " | ").take(900)}; after=${sanitizedStage46.text.replace("\n", " | ").take(900)}",
                                            )
                                        }
                                        val reconstructionStage45 = FarolOcrMultilineAddressStage45.reconstruct(sanitizedStage46.text)
                                        if (reconstructionStage45.changed) {
                                            FarolMaximumForensicsStage38.record(
                                                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S45_OCR_MULTILINE_RECONSTRUCTED", eventPackageStage19,
                                                cycleId = cycleIdStage20,
                                                details = "group=${groupStage19.id}; bounds=${groupStage19.left},${groupStage19.top},${groupStage19.right},${groupStage19.bottom}; streetMerges=${reconstructionStage45.mergedStreetLines}; parenMerges=${reconstructionStage45.mergedParenthesisLines}; directNumbers=${reconstructionStage45.normalizedDirectNumbers}; before=${groupStage19.text.take(600).replace("\n", " | ")}; after=${reconstructionStage45.text.take(600).replace("\n", " | ")}",
                                            )
                                        }
                                        FarolUniversalVisualPipelineStage19.VisualBlock(
                                            id = groupStage19.id,
                                            metadataPackageName = eventPackageStage19,
                                            windowId = surfaceTokenStage46.windowId,
                                            windowLayer = Int.MAX_VALUE,
                                            depth = 1,
                                            text = reconstructionStage45.text,
                                            source = FarolUniversalVisualPipelineStage19.Source.Ocr,
                                            left = groupStage19.left,
                                            top = groupStage19.top,
                                            right = groupStage19.right,
                                            bottom = groupStage19.bottom,
                                        )
                                    }
                                blocksStage19.forEachIndexed { index38, block38 ->
                                    FarolMaximumForensicsStage38.record(
                                        SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_OCR_CLUSTER", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",
                                        details = "index=$index38; id=${block38.id}; window=${block38.windowId}; bounds=${block38.left},${block38.top},${block38.right},${block38.bottom}; text=${block38.text.take(1300)}",
                                    )
                                }
                                var evaluationStage19 = withContext(Dispatchers.Default) {
                                    FarolUniversalVisualPipelineStage19.evaluate(blocksStage19)
                                        ?: FarolRouteLocationEvidenceStage46R8.evaluate(blocksStage19)
                                }
                                FarolMaximumForensicsStage38.record(
                                    SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_OCR_EVALUATION_RESULT", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",
                                    details = "candidate=${evaluationStage19 != null}; pickup=${evaluationStage19?.pickup.orEmpty()}; destination=${evaluationStage19?.destination.orEmpty()}; signature=${evaluationStage19?.addressSignature.orEmpty()}",
                                )
                                if (evaluationStage19 == null) {
                                    FarolCausalCorrectionStage21.forensicExplainEvaluationStage38(blocksStage19).take(420).forEachIndexed { index38, step38 ->
                                        FarolMaximumForensicsStage38.record(
                                            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_OCR_EVALUATION_RULE", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",
                                            details = "step=$index38; $step38",
                                        )
                                    }
                                }
                                FarolForensicTraceStage20.ocrStage(
                                    SystemClock.elapsedRealtimeNanos(), serialStage19, "EVALUATE_END", cycleIdStage20,
                                    "candidate=${evaluationStage19 != null}; visual_generation=${demandStage23.visualGeneration}; visual_snapshot_hash=${demandStage23.snapshotHash}",
                                )
                                if (evaluationStage19 == null) {
                                    val pairBandsStage46 = FarolVisualEpochNoResultStage46.buildLocalAddressPairBands(
                                        fragmentsStage19.map { fragmentStage46 ->
                                            FarolVisualEpochNoResultStage46.Fragment(
                                                fragmentStage46.id, fragmentStage46.text,
                                                fragmentStage46.left, fragmentStage46.top, fragmentStage46.right, fragmentStage46.bottom,
                                            )
                                        },
                                    )
                                    FarolMaximumForensicsStage38.record(
                                        SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_NO_RESULT_RECOVERY_ATTEMPT", eventPackageStage19,
                                        cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",
                                        details = "pairBands=${pairBandsStage46.size}; fragments=${fragmentsStage19.size}",
                                    )
                                    for (bandStage46 in pairBandsStage46) {
                                        val sanitizedBandStage46 = FarolVisualEpochNoResultStage46.sanitizeForReconstruction(bandStage46.text)
                                        val rebuiltBandStage46 = FarolOcrMultilineAddressStage45.reconstructClusterText(sanitizedBandStage46.text)
                                        val blockStage46 = FarolUniversalVisualPipelineStage19.VisualBlock(
                                            id = "stage46-pair:$serialStage19:${bandStage46.index}",
                                            metadataPackageName = eventPackageStage19,
                                            windowId = surfaceTokenStage46.windowId,
                                            windowLayer = Int.MAX_VALUE,
                                            depth = 1,
                                            text = rebuiltBandStage46,
                                            source = FarolUniversalVisualPipelineStage19.Source.Ocr,
                                            left = bandStage46.left,
                                            top = bandStage46.top,
                                            right = bandStage46.right,
                                            bottom = bandStage46.bottom,
                                        )
                                        val candidateStage46 = withContext(Dispatchers.Default) {
                                            FarolCausalCorrectionStage21.evaluate(listOf(blockStage46))
                                        }
                                        val semanticStage46 = candidateStage46?.let(FarolCausalCorrectionStage21::validateEvaluation)
                                        FarolMaximumForensicsStage38.record(
                                            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_NO_RESULT_PAIR_EVALUATED", eventPackageStage19,
                                            cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",
                                            details = "band=${bandStage46.index}; accepted=${semanticStage46?.accepted == true}; reason=${semanticStage46?.reason.orEmpty()}; pickup=${candidateStage46?.pickup.orEmpty().take(500)}; destination=${candidateStage46?.destination.orEmpty().take(500)}",
                                        )
                                        if (candidateStage46 != null && semanticStage46?.accepted == true) {
                                            evaluationStage19 = candidateStage46
                                            FarolMaximumForensicsStage38.record(
                                                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_NO_RESULT_RECOVERED", eventPackageStage19,
                                                cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",
                                                details = "band=${bandStage46.index}; signature=${candidateStage46.addressSignature}; destination=${candidateStage46.destination.take(700)}",
                                            )
                                            break
                                        }
                                    }
                                    if (evaluationStage19 == null && pairBandsStage46.isNotEmpty()) {
                                        FarolMaximumForensicsStage38.record(
                                            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_NO_RESULT_WITH_ADDRESS_EVIDENCE", eventPackageStage19,
                                            cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",
                                            details = "pairBands=${pairBandsStage46.size}; failClosed=true",
                                        )
                                    }
                                }

                                if (!isStage46OcrWorkFresh(workTokenStage36, surfaceTokenStage46)) {
                                    FarolVisualIdentityStage23.Metrics.increment("ocrStaleAfterEvaluate")
                                    FarolReadingActivationStage26.Metrics.increment("ocrStale")
                                    FarolForensicCardBlackBoxStage32.recordOcrStale(SystemClock.elapsedRealtimeNanos(), true, "semantic_generation_or_activation_changed")
                                    FarolForensicTraceStage20.ocrStage(
                                        SystemClock.elapsedRealtimeNanos(), serialStage19, "STALE_AFTER_EVALUATE", cycleIdStage20,
                                        "latestSerial=$stage19OcrSerial; visual_generation=${demandStage23.visualGeneration}; visual_snapshot_hash=${demandStage23.snapshotHash}",
                                    )
                                    return@launch
                                }
                                stage19VisualVerificationPending = false
                                if (evaluationStage19 != null) {
                                    stage26CandidateEventStartedNs = SystemClock.elapsedRealtimeNanos()
                                    stage26CandidateActivationGeneration = stage26OcrActivationGeneration
                                    stage32SemanticGate.observeCandidate(evaluationStage19.addressSignature)
                                    FarolForensicCardBlackBoxStage32.recordCandidate(
                                        SystemClock.elapsedRealtimeNanos(), "Ocr", evaluationStage19.pickup, evaluationStage19.destination, evaluationStage19.addressSignature,
                                    )
                                    processUniversalVisualStage19(evaluationStage19, "Ocr", cycleIdStage20)
                                } else {
                                    FarolForensicCardBlackBoxStage32.markOcrNoCandidate(SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis())
                                    FarolForensicCaseStoreStage32.persistIfIntensive(applicationContext)
                                    FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "NO_CANDIDATE", cycleIdStage20)
                                    hardClearUniversalTwoAddress(
                                        reason = "Snapshot visual atual sem dois endereços semanticamente completos Stage23.",
                                        keepWaitingYellow = true,
                                    )
                                }
                            } finally {
                                FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "COMPLETE", cycleIdStage20)
                                bitmapStage19?.takeUnless(Bitmap::isRecycled)?.recycle()
                                screenshotInProgress.set(false)
                                stage32ScreenshotRateGate.complete(demandStage23.visualGeneration, true)
                                val completionStage23 = stage23OcrGate.complete(tokenStage23)
                                stage19OcrRerunRequested = false
                                rerunIfUsefulStage23(completionStage23)
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        FarolMaximumForensicsStage38.record(
                            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_SCREENSHOT_FAILURE", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",
                            details = "errorCode=$errorCode; intervalShort=${errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT}; visualGeneration=${demandStage23.visualGeneration}; snapshotHash=${demandStage23.snapshotHash}",
                        )
                        FarolForensicTraceStage20.ocrStage(
                            SystemClock.elapsedRealtimeNanos(), serialStage19, "SCREENSHOT_FAILURE", cycleIdStage20,
                            "errorCode=$errorCode; semantic_generation=${demandStage23.visualGeneration}; semantic_fingerprint=${demandStage23.snapshotHash}",
                        )
                        screenshotInProgress.set(false)
                        val intervalShortStage32 = errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT
                        if (intervalShortStage32) {
                            stage32ScreenshotRateGate.markIntervalShort(demandStage23.visualGeneration)
                        } else {
                            stage32ScreenshotRateGate.complete(demandStage23.visualGeneration, false)
                        }
                        FarolForensicCardBlackBoxStage32.markScreenshotFailure(
                            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), errorCode, terminal = !intervalShortStage32,
                        )
                        stage19VisualVerificationPending = true
                        val completionStage23 = stage23OcrGate.complete(tokenStage23)
                        stage19OcrRerunRequested = false
                        if (completionStage23.rerun != null) stage32ScreenshotRateGate.queue(stage32SemanticGate.snapshot().generation)
                        FarolForensicCaseStoreStage32.persistIfIntensive(applicationContext)
                    }
                },
            )
        }.onFailure { errorStage32 ->
            screenshotInProgress.set(false)
            stage32ScreenshotRateGate.complete(demandStage23.visualGeneration, false)
            stage23OcrGate.complete(tokenStage23)
            stage19OcrRerunRequested = false
            stage19VisualVerificationPending = true
            FarolForensicCardBlackBoxStage32.markScreenshotFailure(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), -1, terminal = true,
            )
            FarolForensicTraceStage20.note(SystemClock.elapsedRealtimeNanos(), "S32_SCREENSHOT_THROWN", cycleIdStage20, details = "type=${errorStage32::class.java.simpleName}")
            FarolForensicCaseStoreStage32.persistIfIntensive(applicationContext)
        }
    }

    private suspend fun processUniversalVisualStage19(
        evaluationStage19: FarolUniversalVisualPipelineStage19.Evaluation,
        sourceStage19: String,
        cycleIdStage20: Long? = null,
    ) {
        if (!serviceReady || !WorkModePolicy0162.isEnabled(currentSettings)) return
        if (!stage36RuntimeAuthority.snapshot().enabled) return
        stage26RouteResponseNs = 0L
        val semanticStage21 = FarolRouteLocationEvidenceStage46R8.validateEvaluation(evaluationStage19)
        val singleImmediateAddressStage46R7 = FarolRouteLocationEvidenceStage46R8.isSingleImmediateEvaluation(evaluationStage19)
        if (singleImmediateAddressStage46R7) {
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_R8_POSITIVE_SINGLE_LOCATION", observePackageForWindowIdStage46R3(evaluationStage19.windowId), cycleId = cycleIdStage20,
                details = "window=${evaluationStage19.windowId}; destination=${evaluationStage19.destination}; signature=${evaluationStage19.addressSignature}; semanticAccepted=${semanticStage21.accepted}; reason=${semanticStage21.reason}; source=$sourceStage19; noPairWait=true; addressDetectedImmediate=true; noDestinationCueWait=true; noOcrWaitWhenEventTextSingle=true",
            )
        } else if (FarolRouteLocationEvidenceStage46R8.isAggregateLastAddressEvaluation(evaluationStage19)) {
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_R8_LAST_VALID_LOCATION", observePackageForWindowIdStage46R3(evaluationStage19.windowId), cycleId = cycleIdStage20,
                details = "window=${evaluationStage19.windowId}; addressCount=${evaluationStage19.addresses.size}; destination=${evaluationStage19.destination}; signature=${evaluationStage19.addressSignature}; semanticAccepted=${semanticStage21.accepted}; source=$sourceStage19; lastAddressAuthority=true",
            )
        }
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_CANDIDATE_SEMANTIC_VALIDATION", packageName = null, cycleId = cycleIdStage20,
            details = "source=$sourceStage19; accepted=${semanticStage21.accepted}; reason=${semanticStage21.reason}; pickup=${evaluationStage19.pickup.take(700)}; destination=${evaluationStage19.destination.take(700)}; addresses=${evaluationStage19.addresses.joinToString(" || ").take(1300)}; signature=${evaluationStage19.addressSignature}",
        )
        stage36RuntimeAuthority.bindDestination(evaluationStage19.addressSignature)
        stage32SemanticGate.observeCandidate(evaluationStage19.addressSignature)
        FarolForensicCardBlackBoxStage32.recordCandidate(
            SystemClock.elapsedRealtimeNanos(), sourceStage19, evaluationStage19.pickup, evaluationStage19.destination, evaluationStage19.addressSignature,
        )
        if (!semanticStage21.accepted) {
            FarolForensicTraceStage20.note(
                SystemClock.elapsedRealtimeNanos(), "S21_SEMANTIC_REJECT_BEFORE_CACHE_ROUTE", cycleIdStage20,
                details = "reason=${semanticStage21.reason}; destination=${evaluationStage19.destination}",
            )
            hardClearUniversalTwoAddress(
                reason = "Destino visual incompleto rejeitado antes de cache/Google: ${semanticStage21.reason}.",
                keepWaitingYellow = true,
            )
            return
        }
        val candidatePackageStage46R3 = observePackageForWindowIdStage46R3(evaluationStage19.windowId)
        bindCandidateTargetSurfaceStage46(
            candidatePackageStage46R3,
            evaluationStage19.windowId,
            "stage21_semantic_accepted",
        )
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_R3_TARGET_PROMOTED_AFTER_STAGE21", candidatePackageStage46R3, cycleId = cycleIdStage20,
            details = "window=${evaluationStage19.windowId}; signature=${evaluationStage19.addressSignature}; source=$sourceStage19; epoch=$stage46VisualEpoch",
        )
        val previousBindingStage20 = currentStage20BindingSnapshot()
        val windowChangedStage19 = stage19ActiveWindowId != evaluationStage19.windowId ||
            stage19ActiveBlockId != evaluationStage19.blockId
        val visualChangedStage19 = universalActiveAddressSignature != evaluationStage19.addressSignature
        if (windowChangedStage19) universalWindowGeneration += 1L
        if (visualChangedStage19) {
            universalScreenGeneration += 1L
            if (universalRouteJob?.isActive == true) {
                FarolForensicTraceStage20.routeCancelled(FarolForensicTraceStage20.traceFor(previousBindingStage20), null, SystemClock.elapsedRealtimeNanos(), "visual_changed")
            }
            universalRouteJob?.cancel()
            universalRouteJob = null
            lastAnalyzedHash = null
            currentDistanceKm = null
            fastFarolStartedAtChecklist13 = System.currentTimeMillis()
        }
        stage19ActiveWindowId = evaluationStage19.windowId
        stage19ActiveBlockId = evaluationStage19.blockId
        universalActiveRidePackageName = null
        universalActiveAddressSignature = evaluationStage19.addressSignature
        lastSnapshotHash = evaluationStage19.screenHash
        universalLastActiveReadAtElapsedMillis0187 = SystemClock.elapsedRealtime()
        stage19VisualVerificationPending = false
        val currentBindingStage20 = currentStage20BindingSnapshot()
        if (windowChangedStage19 || visualChangedStage19) {
            FarolForensicTraceStage20.visualInvalidated(SystemClock.elapsedRealtimeNanos(), previousBindingStage20, currentBindingStage20, "windowChanged=$windowChangedStage19; visualChanged=$visualChangedStage19")
        }
        val traceIdStage20 = FarolForensicTraceStage20.bindCandidate(
            SystemClock.elapsedRealtimeNanos(), cycleIdStage20, currentBindingStage20, sourceStage19,
            evaluationStage19.destination, evaluationStage19.blockId,
        )
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_CANDIDATE_BOUND", packageName = null, cycleId = cycleIdStage20, traceId = traceIdStage20,
            details = "source=$sourceStage19; window=${evaluationStage19.windowId}; block=${evaluationStage19.blockId}; destination=${evaluationStage19.destination.take(900)}; screenGeneration=$universalScreenGeneration; windowGeneration=$universalWindowGeneration; screenHash=${evaluationStage19.screenHash}; addressSignature=${evaluationStage19.addressSignature}",
        )

        if (!visualChangedStage19 && (lastAnalyzedHash == evaluationStage19.screenHash || universalRouteJob?.isActive == true)) {
            FarolForensicTraceStage20.note(SystemClock.elapsedRealtimeNanos(), "S20_DUPLICATE_OR_ROUTE_ACTIVE_SKIP", cycleIdStage20, traceIdStage20, binding = currentBindingStage20)
            return
        }

        val fieldsStage19 = RideFields(
            pickup = evaluationStage19.pickup,
            destination = evaluationStage19.destination,
        )
        val settingsStage19 = currentSettings
        val targetsStage19 = fastWorkRegionTargetsChecklist13(settingsStage19)
        rememberBubbleReason(
            "stage19_visual_destination",
            if (singleImmediateAddressStage46R7) "Primeiro endereço válido atual detectado; calculando rota real imediatamente."
            else "Múltiplos endereços atuais detectados; o último endereço visual é o destino da rota.",
        )
        if (currentRadarColor != RadarColor.Orange || currentDistanceKm != null) {
            showOverlay(RadarColor.Default, distanceKm = null)
        }
        if (targetsStage19.destinations.isEmpty()) return

        val bindingStage19 = FarolUniversalVisualPipelineStage19.Binding(
            screenGeneration = universalScreenGeneration,
            windowGeneration = universalWindowGeneration,
            screenHash = evaluationStage19.screenHash,
            addressSignature = evaluationStage19.addressSignature,
        )
        bindReadingActivationStage26(bindingStage19, stage26CandidateActivationGeneration)
        FarolReadingActivationStage26.Metrics.sample(
            "candidateToRouteStart",
            (SystemClock.elapsedRealtimeNanos() - stage26CandidateEventStartedNs).coerceAtLeast(0L),
        )
        FarolForensicTraceStage20.cacheLookupStarted(traceIdStage20, SystemClock.elapsedRealtimeNanos())
        val cachedStage19 = googleMapsService.cachedDrivingDistancesFromAddressKm(
            originAddress = fieldsStage19.destination.orEmpty(),
            destinations = targetsStage19.destinations,
        )
        FarolForensicTraceStage20.cacheLookupFinished(traceIdStage20, SystemClock.elapsedRealtimeNanos(), cachedStage19 != null)
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_CACHE_RESULT", packageName = null, cycleId = cycleIdStage20, traceId = traceIdStage20, operationId = "CACHE",
            details = "hit=${cachedStage19 != null}; destination=${fieldsStage19.destination.orEmpty().take(900)}; targets=${targetsStage19.destinations.size}",
        )
        if (cachedStage19 != null) {
            FarolCausalLatencyStage28.Metrics.increment("routeCacheHits")
            FarolForensicCardBlackBoxStage32.recordCacheHit(SystemClock.elapsedRealtimeNanos())
            val cacheFreshStage20 = isStage19BindingFresh(bindingStage19)
            FarolForensicTraceStage20.bindingCheck(traceIdStage20, "CACHE", SystemClock.elapsedRealtimeNanos(), "CACHE_RESULT", stage20BindingSnapshot(bindingStage19), currentStage20BindingSnapshot(), cacheFreshStage20, stage19VisualVerificationPending)
            if (!cacheFreshStage20) return
            FarolForensicTraceStage20.decisionStarted(traceIdStage20, "CACHE", SystemClock.elapsedRealtimeNanos())
            val resultStage19 = decideFastWorkRegionChecklist13(
                snapshotText = evaluationStage19.analysisText,
                fields = fieldsStage19,
                settings = settingsStage19,
                targets = targetsStage19,
                routeDistances = cachedStage19,
            )
            FarolForensicTraceStage20.decisionFinished(traceIdStage20, "CACHE", SystemClock.elapsedRealtimeNanos(), resultStage19.recommendation.name, resultStage19.nearestConfiguredDistanceKm())
            bubblePrefs.edit().putString("fast_farol_last_path", "stage19_cache_exato").apply()
            applyUniversalTwoAddressResultStage19(resultStage19, bindingStage19, traceIdStage20, "CACHE")
            return
        }

        bubblePrefs.edit().putString("fast_farol_last_path", "stage19_rota_google").apply()
        val routeJobIdStage20 = FarolForensicTraceStage20.routeJobStarted(traceIdStage20, SystemClock.elapsedRealtimeNanos())
        FarolForensicCardBlackBoxStage32.recordRouteRequested(SystemClock.elapsedRealtimeNanos(), fieldsStage19.destination.orEmpty())
        universalRouteJob = scope.launch {
            analyzeUniversalTwoAddressStage19(
                snapshotTextStage19 = evaluationStage19.analysisText,
                fieldsStage19 = fieldsStage19,
                bindingStage19 = bindingStage19,
                traceIdStage20 = traceIdStage20,
                routeJobIdStage20 = routeJobIdStage20,
            )
        }
        UnifiedDebugEventStore.record(
            "STAGE19_VISUAL_ROUTE_STARTED",
            null,
            "source=$sourceStage19; destination=${fieldsStage19.destination.orEmpty()}; screenGeneration=${bindingStage19.screenGeneration}; windowGeneration=${bindingStage19.windowGeneration}; trace=$traceIdStage20; routeJob=$routeJobIdStage20",
        )
    }

    private fun stage20BindingSnapshot(bindingStage19: FarolUniversalVisualPipelineStage19.Binding) =
        FarolForensicTraceStage20.BindingSnapshot(bindingStage19.screenGeneration, bindingStage19.windowGeneration, bindingStage19.screenHash, bindingStage19.addressSignature)

    private fun currentStage20BindingSnapshot() = FarolForensicTraceStage20.BindingSnapshot(
        universalScreenGeneration, universalWindowGeneration, lastSnapshotHash, universalActiveAddressSignature,
    )

    private fun isStage19BindingFresh(bindingStage19: FarolUniversalVisualPipelineStage19.Binding): Boolean =
        serviceReady && WorkModePolicy0162.isEnabled(currentSettings) &&
            isReadingBindingFreshStage26(bindingStage19) &&
            bindingStage19.addressSignature == universalActiveAddressSignature


    private fun stage26BindingKey(bindingStage26: FarolUniversalVisualPipelineStage19.Binding): String =
        "${bindingStage26.screenGeneration}|${bindingStage26.windowGeneration}|${bindingStage26.screenHash}|${bindingStage26.addressSignature}"

    private fun bindReadingActivationStage26(
        bindingStage26: FarolUniversalVisualPipelineStage19.Binding,
        activationGenerationStage26: Long,
    ) {
        @Suppress("UNUSED_VARIABLE") val legacyActivationStage36 = activationGenerationStage26
        if (stage36BindingWorkToken.size >= 12) {
            val firstStage36 = stage36BindingWorkToken.keys.firstOrNull()
            if (firstStage36 != null) stage36BindingWorkToken.remove(firstStage36)
        }
        val tokenStage36 = stage36RuntimeAuthority.captureDestinationToken(bindingStage26.addressSignature) ?: return
        val keyStage46 = stage26BindingKey(bindingStage26)
        stage36BindingWorkToken[keyStage46] = tokenStage36
        if (stage46BindingSurfaceToken.size >= 12) {
            val firstSurfaceStage46 = stage46BindingSurfaceToken.keys.firstOrNull()
            if (firstSurfaceStage46 != null) stage46BindingSurfaceToken.remove(firstSurfaceStage46)
        }
        val routeTargetPackageStage46 = stage46TargetSourcePackage ?: currentRootPackageName()
        val routeTargetWindowStage46 = observeTargetWindowIdStage46(routeTargetPackageStage46).takeIf { it > 0 }
            ?: stage46TargetWindowId.takeIf { it > 0 }
            ?: stage19ActiveWindowId
            ?: 0
        stage46BindingSurfaceToken[keyStage46] = FarolVisualEpochNoResultStage46.captureSurface(
            routeTargetPackageStage46, null, routeTargetWindowStage46, stage46VisualEpoch,
        )
    }

    private fun isReadingBindingFreshStage26(bindingStage26: FarolUniversalVisualPipelineStage19.Binding): Boolean {
        val keyStage46 = stage26BindingKey(bindingStage26)
        val tokenStage36 = stage36BindingWorkToken[keyStage46] ?: return false
        val surfaceStage46 = stage46BindingSurfaceToken[keyStage46] ?: return false
        val runtimeFreshStage46 = stage36RuntimeAuthority.isFresh(tokenStage36)
        val currentTargetWindowStage46 = observeTargetWindowIdStage46(surfaceStage46.packageName)
        val surfaceFreshStage46 = FarolTargetSurfaceStage46R2.surfaceFresh(
            surfaceStage46, currentRootPackageName(), currentTargetWindowStage46, stage46VisualEpoch,
        )
        if (runtimeFreshStage46 && !surfaceFreshStage46) {
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_STALE_ROUTE_SURFACE_DROPPED", currentRootPackageName(),
                details = "captured=${surfaceStage46.packageName.orEmpty()}; current=${currentRootPackageName().orEmpty()}; capturedEpoch=${surfaceStage46.visualEpoch}; currentEpoch=$stage46VisualEpoch; binding=${bindingStage26.addressSignature}",
            )
        }
        return runtimeFreshStage46 && surfaceFreshStage46
    }

    private suspend fun analyzeUniversalTwoAddressStage19(
        snapshotTextStage19: String,
        fieldsStage19: RideFields,
        bindingStage19: FarolUniversalVisualPipelineStage19.Binding,
        traceIdStage20: String,
        routeJobIdStage20: String,
    ) {
        val initialFreshStage20 = isStage19BindingFresh(bindingStage19)
        FarolForensicTraceStage20.bindingCheck(traceIdStage20, routeJobIdStage20, SystemClock.elapsedRealtimeNanos(), "ROUTE_ENTER", stage20BindingSnapshot(bindingStage19), currentStage20BindingSnapshot(), initialFreshStage20, stage19VisualVerificationPending)
        if (!initialFreshStage20) return
        val settingsStage19 = currentSettings
        val apiKeyStage19 = GoogleMapsApiKeyPolicy.effective(
            settingsStage19.googleMapsApiKey,
            BuildConfig.GOOGLE_MAPS_API_KEY,
        )
        if (apiKeyStage19.isBlank()) return
        val targetsStage19 = fastWorkRegionTargetsChecklist13(settingsStage19)
        if (targetsStage19.destinations.isEmpty()) return
        FarolForensicTraceStage20.routeCallStarted(traceIdStage20, routeJobIdStage20, SystemClock.elapsedRealtimeNanos(), fieldsStage19.destination.orEmpty())
        val routeKeyStage28 = FarolCausalLatencyStage28.RouteKey(
            stage26CandidateActivationGeneration,
            stage26CurrentVisualGeneration,
            fieldsStage19.destination.orEmpty(),
        )
        if (!stage28RouteGate.begin(routeKeyStage28)) return
        val routeStartedNsStage26 = SystemClock.elapsedRealtimeNanos()
        FarolMaximumForensicsStage38.record(
            routeStartedNsStage26, System.currentTimeMillis(), "S38_GOOGLE_ROUTE_START", packageName = null, traceId = traceIdStage20, operationId = routeJobIdStage20,
            details = "destination=${fieldsStage19.destination.orEmpty().take(900)}; targets=${targetsStage19.destinations.joinToString(" || ").take(1200)}",
        )
        FarolCausalLatencyStage28.Metrics.sample(
            "candidateToRouteStart",
            (routeStartedNsStage26 - stage26CandidateEventStartedNs).coerceAtLeast(0L),
        )
        val distancesStage19 = googleMapsService.drivingDistancesFromAddressKm(
            originAddress = fieldsStage19.destination.orEmpty(),
            destinations = targetsStage19.destinations,
            apiKey = apiKeyStage19,
        )
        FarolForensicTraceStage20.routeCallFinished(traceIdStage20, routeJobIdStage20, SystemClock.elapsedRealtimeNanos(), distancesStage19.toString())
        val routeEndedNsStage26 = SystemClock.elapsedRealtimeNanos()
        FarolMaximumForensicsStage38.record(
            routeEndedNsStage26, System.currentTimeMillis(), "S38_GOOGLE_ROUTE_END", packageName = null, traceId = traceIdStage20, operationId = routeJobIdStage20,
            details = "duration_ns=${(routeEndedNsStage26 - routeStartedNsStage26).coerceAtLeast(0L)}; response=${distancesStage19.toString().take(1200)}",
        )
        FarolForensicCardBlackBoxStage32.recordRouteResponse(routeEndedNsStage26, distancesStage19 != null, routeEndedNsStage26 - routeStartedNsStage26)
        FarolReadingActivationStage26.Metrics.sample("route", routeEndedNsStage26 - routeStartedNsStage26)
        stage26RouteResponseNs = routeEndedNsStage26
        stage28RouteGate.finish(routeKeyStage28)
        FarolCausalLatencyStage28.Metrics.sample("route", routeEndedNsStage26 - routeStartedNsStage26)
        val routeFreshStage20 = isStage19BindingFresh(bindingStage19)
        FarolForensicTraceStage20.bindingCheck(traceIdStage20, routeJobIdStage20, SystemClock.elapsedRealtimeNanos(), "AFTER_ROUTE", stage20BindingSnapshot(bindingStage19), currentStage20BindingSnapshot(), routeFreshStage20, stage19VisualVerificationPending)
        if (!routeFreshStage20) return
        FarolForensicTraceStage20.decisionStarted(traceIdStage20, routeJobIdStage20, SystemClock.elapsedRealtimeNanos())
        val resultStage19 = decideFastWorkRegionChecklist13(
            snapshotText = snapshotTextStage19,
            fields = fieldsStage19,
            settings = settingsStage19,
            targets = targetsStage19,
            routeDistances = distancesStage19,
        )
        FarolForensicTraceStage20.decisionFinished(traceIdStage20, routeJobIdStage20, SystemClock.elapsedRealtimeNanos(), resultStage19.recommendation.name, resultStage19.nearestConfiguredDistanceKm())
        applyUniversalTwoAddressResultStage19(resultStage19, bindingStage19, traceIdStage20, routeJobIdStage20)
    }

    private suspend fun applyUniversalTwoAddressResultStage19(
        resultStage19: AnalysisResult,
        bindingStage19: FarolUniversalVisualPipelineStage19.Binding,
        traceIdStage20: String,
        operationIdStage20: String,
    ) {
        val paintFreshStage20 = isStage19BindingFresh(bindingStage19) && !stage19VisualVerificationPending
        FarolForensicTraceStage20.bindingCheck(traceIdStage20, operationIdStage20, SystemClock.elapsedRealtimeNanos(), "BEFORE_FINAL_PAINT", stage20BindingSnapshot(bindingStage19), currentStage20BindingSnapshot(), paintFreshStage20, stage19VisualVerificationPending)
        if (!paintFreshStage20) return
        val colorStage19 = when (resultStage19.recommendation) {
            Recommendation.GoodRide -> RadarColor.Green
            Recommendation.OutsideRadius -> RadarColor.Red
            Recommendation.InsufficientData -> RadarColor.Orange
        }
        val distanceStage19 = resultStage19.nearestConfiguredDistanceKm()
        FarolForensicCardBlackBoxStage32.recordPaintRequested(SystemClock.elapsedRealtimeNanos(), colorStage19.toString(), distanceStage19)
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_FINAL_PAINT_PREPARE", packageName = null, traceId = traceIdStage20, operationId = operationIdStage20,
            details = "recommendation=${resultStage19.recommendation}; color=$colorStage19; distanceKm=${distanceStage19 ?: -1.0}; reason=${resultStage19.reason.take(900)}; binding=${bindingStage19.screenGeneration}|${bindingStage19.windowGeneration}|${bindingStage19.screenHash}|${bindingStage19.addressSignature}",
        )
        lastAnalyzedHash = bindingStage19.screenHash
        rememberBubbleReason("stage19_visual_result", resultStage19.reason)
        val paintPreparedNsStage26 = SystemClock.elapsedRealtimeNanos()
        if (stage26RouteResponseNs > 0L) FarolReadingActivationStage26.Metrics.sample("routeResponseToPaint", paintPreparedNsStage26 - stage26RouteResponseNs)
        if (stage26CandidateEventStartedNs > 0L) FarolReadingActivationStage26.Metrics.sample("eventToFinalGreenRedKm", paintPreparedNsStage26 - stage26CandidateEventStartedNs)
        val paintTokenStage20 = FarolForensicTraceStage20.preparePaint(
            traceIdStage20, operationIdStage20, stage20BindingSnapshot(bindingStage19),
            colorStage19.toString(), distanceStage19, SystemClock.elapsedRealtimeNanos(),
        )
        stage20ExpectedPaintToken = paintTokenStage20
        try {
            showOverlay(colorStage19, distanceStage19)
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_FINAL_PAINT_APPLIED", packageName = null, traceId = traceIdStage20, operationId = operationIdStage20,
                details = "color=$colorStage19; distanceKm=${distanceStage19 ?: -1.0}; currentColor=$currentRadarColor; currentDistance=$currentDistanceKm",
            )
            FarolForensicCardBlackBoxStage32.recordFinal(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), colorStage19.toString(), distanceStage19, operationIdStage20,
            )
            FarolForensicCaseStoreStage32.persistIfIntensive(applicationContext)
        } finally {
            stage20ExpectedPaintToken = null
        }
        val finishedStage19 = System.currentTimeMillis()
        val elapsedStage19 = if (fastFarolStartedAtChecklist13 > 0L) {
            (finishedStage19 - fastFarolStartedAtChecklist13).coerceAtLeast(0L)
        } else 0L
        bubblePrefs.edit()
            .putLong("fast_farol_last_elapsed_ms", elapsedStage19)
            .putLong("fast_farol_last_finished_at", finishedStage19)
            .putString("fast_farol_last_destination", resultStage19.fields.destination.orEmpty())
            .apply()
        val persistenceStage19 = listOf(
            bindingStage19.addressSignature,
            resultStage19.recommendation.name,
            distanceStage19?.let { String.format(Locale.US, "%.3f", it) }.orEmpty(),
        ).joinToString("|")
        if (universalAnalysisDeduper.shouldPersist(persistenceStage19)) {
            scope.launch(Dispatchers.IO) { runCatching { repository.addAnalysis(resultStage19) } }
        }
    }

    private fun startContinuousScan() {
        // event_driven_farol_0_1_162: sem while, timer ou OCR continuo.
        // bubble_drag_scan_pause_0_1_116 — sem loop, portanto nenhum scan compete com o gesto.
        continuousScanStarted = true
    }

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
        lastDirectionalFix0184 = fix

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
                    directionalAlertOverlayChecklist5.hideFromEngineIdle()
                } else {
                    directionalAlertOverlayChecklist5.showOrUpdate(
                        visual = visual,
                        actions = DirectionalAlertOverlayActions(
                            onDismiss = { directionalAlertEngineChecklist5.dismissUntilExit(visual.targetId) },
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
                            onEditRadar = { radarId ->
                                currentImportedRadars.firstOrNull { it.id == radarId }
                                    ?.let(::openImportedRadarEditor0178)
                            },
                            onDeleteRadar = { radarId ->
                                directionalAlertEngineChecklist5.dismissUntilExit(visual.targetId)
                                scope.launch {
                                    repository.removeImportedRadar(radarId)
                                    directionalAlertOverlayChecklist5.hide()
                                    toast("Radar excluído.")
                                }
                            },
                        ),
                    )
                }
            },
        )
    } // directional_alert_check_checklist_5

    private fun scheduleVisibleTextAnalysis(delayMs: Long, allowPopupCandidate: Boolean = false) {
        val scheduledActivationStage26 = stage26ReadingActivation.snapshot()
        if (!scheduledActivationStage26.enabled || !scheduledActivationStage26.usageAccessGranted) {
            FarolReadingActivationStage26.Metrics.increment("eventsRejectedReadingOff")
            FarolReadingActivationStage26.Metrics.increment("heavyCollectionsAvoided")
            return
        }
        @Suppress("UNUSED_VARIABLE") val ignoredDelayStage23 = delayMs
        @Suppress("UNUSED_VARIABLE") val ignoredPopupStage23 = allowPopupCandidate
        if (!serviceReady || !WorkModePolicy0162.isEnabled(currentSettings) || bubbleGestureActive) return
        val demandStage23 = stage23ScheduleGate.create(
            stage23VisualGate.currentGeneration(),
            stage23VisualGate.currentHash(),
        )
        analyzeJob?.cancel()
        analyzeJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (!isReadingActivationGenerationFreshStage26(scheduledActivationStage26.generation)) {
                FarolReadingActivationStage26.Metrics.increment("heavyCollectionsAvoided")
                return@launch
            }
            if (!stage23ScheduleGate.shouldRun(
                    demandStage23,
                    stage23VisualGate.currentGeneration(),
                    stage23VisualGate.currentHash(),
                )
            ) {
                FarolVisualIdentityStage23.Metrics.increment("scheduledCancelled")
                FarolReadingActivationStage26.Metrics.increment("heavyCollectionsAvoided")
                FarolForensicTraceStage20.note(
                    SystemClock.elapsedRealtimeNanos(),
                    "S23_SCHEDULED_CANCELLED_BEFORE_COLLECT",
                    details = "token=${demandStage23.token}; demand_generation=${demandStage23.visualGeneration}; demand_hash=${demandStage23.snapshotHash ?: 0L}; current_generation=${stage23VisualGate.currentGeneration()}; current_hash=${stage23VisualGate.currentHash() ?: 0L}",
                )
                return@launch
            }

            val eventStartedNsStage23 = SystemClock.elapsedRealtimeNanos()
            val cycleIdStage20 = FarolForensicTraceStage20.beginCycle(
                eventStartedNsStage23,
                null,
                -23,
                runCatching { rootInActiveWindow?.windowId }.getOrNull() ?: 0,
            )
            stage20LastCycleId = cycleIdStage20
            val collectStartedNsStage23 = SystemClock.elapsedRealtimeNanos()
            FarolForensicTraceStage20.accessibilityCollectStarted(cycleIdStage20, collectStartedNsStage23)
            FarolReadingActivationStage26.Metrics.increment("heavyCollectionsStarted")
            val collectionStage23 = collectUniversalAccessibilitySnapshotStage26()
            val collectEndedNsStage23 = SystemClock.elapsedRealtimeNanos()
            FarolReadingActivationStage26.Metrics.sample("collect", collectEndedNsStage23 - collectStartedNsStage23)
            FarolReadingActivationStage26.Metrics.addTotal("nodesVisited", collectionStage23.stats.blocksVisited.toLong())
            FarolReadingActivationStage26.Metrics.addTotal("blocksEmitted", collectionStage23.stats.blocksEmitted.toLong())
            FarolReadingActivationStage26.Metrics.addTotal("addressParserInvocations", collectionStage23.addressParserInvocations.toLong())
            FarolReadingActivationStage26.Metrics.addTotal("duplicateSubtreesAvoided", collectionStage23.duplicateSubtreesAvoided.toLong())
            if (!isReadingActivationGenerationFreshStage26(scheduledActivationStage26.generation)) {
                FarolReadingActivationStage26.Metrics.increment("workCancelledOnReadingOff")
                return@launch
            }

            // A known scheduled demand is not allowed to adopt a newer visual generation.
            if (demandStage23.snapshotHash != null && collectionStage23.snapshot.hash != demandStage23.snapshotHash) {
                FarolVisualIdentityStage23.Metrics.increment("scheduledCancelled")
                FarolForensicTraceStage20.accessibilityCollectFinished(
                    cycleIdStage20,
                    collectEndedNsStage23,
                    collectionStage23.stats.visibleWindowsTotal,
                    collectionStage23.stats.blocksEmitted,
                )
                FarolForensicTraceStage20.note(
                    collectEndedNsStage23,
                    "S23_SCHEDULED_CANCELLED_VISUAL_CHANGED",
                    cycleIdStage20,
                    details = "token=${demandStage23.token}; demand_generation=${demandStage23.visualGeneration}; demand_hash=${demandStage23.snapshotHash}; observed_hash=${collectionStage23.snapshot.hash}",
                )
                return@launch
            }

            val visualDecisionStage23 = stage23VisualGate.observe(collectionStage23.snapshot.hash)
            FarolVisualIdentityStage23.Metrics.recordCollection(
                "AccessibilityScheduled",
                collectEndedNsStage23 - collectStartedNsStage23,
                collectionStage23.stats,
                visualDecisionStage23.process,
            )
            FarolForensicTraceStage20.accessibilityCollectFinished(
                cycleIdStage20,
                collectEndedNsStage23,
                collectionStage23.stats.visibleWindowsTotal,
                collectionStage23.stats.blocksEmitted,
            )
            FarolForensicTraceStage20.note(
                collectEndedNsStage23,
                "S23_ACCESSIBILITY_COLLECT_STATS",
                cycleIdStage20,
                details = "source=AccessibilityScheduled; visible_windows_total=${collectionStage23.stats.visibleWindowsTotal}; windows_traversed=${collectionStage23.stats.windowsTraversed}; windows_skipped_self=${collectionStage23.stats.windowsSkippedSelf}; windows_skipped_lower_layer=${collectionStage23.stats.windowsSkippedLowerLayer}; blocks_visited=${collectionStage23.stats.blocksVisited}; blocks_emitted=${collectionStage23.stats.blocksEmitted}; early_exit_window=${collectionStage23.stats.earlyExitWindow ?: -1}; early_exit_reason=${collectionStage23.stats.earlyExitReason}; visual_snapshot_hash=${collectionStage23.snapshot.hash}",
            )
            if (!visualDecisionStage23.process) {
                FarolVisualIdentityStage23.Metrics.increment("scheduledCancelled")
                FarolVisualIdentityStage23.Metrics.increment("unchangedVisualSkipped")
                FarolForensicTraceStage20.note(
                    SystemClock.elapsedRealtimeNanos(),
                    "S23_SCHEDULED_CANCELLED_ALREADY_PROCESSED",
                    cycleIdStage20,
                    details = "token=${demandStage23.token}; generation=${visualDecisionStage23.generation}; visual_snapshot_hash=${collectionStage23.snapshot.hash}",
                )
                return@launch
            }

            val evaluateStartedNsStage23 = SystemClock.elapsedRealtimeNanos()
            FarolForensicTraceStage20.accessibilityEvaluateStarted(cycleIdStage20, evaluateStartedNsStage23)
            val evaluationStage19 = withContext(Dispatchers.Default) {
                FarolUniversalVisualPipelineStage19.evaluate(collectionStage23.blocks)
            }
            val evaluateEndedNsStage23 = SystemClock.elapsedRealtimeNanos()
            FarolVisualIdentityStage23.Metrics.recordEvaluate(
                "AccessibilityScheduled",
                evaluationStage19 != null,
                evaluateEndedNsStage23 - evaluateStartedNsStage23,
            )
            FarolForensicTraceStage20.accessibilityEvaluateFinished(
                cycleIdStage20,
                evaluateEndedNsStage23,
                evaluationStage19 != null,
            )
            stage23VisualGate.markProcessed(collectionStage23.snapshot.hash, visualDecisionStage23.generation)
            stage23ScheduleGate.satisfy(visualDecisionStage23.generation, collectionStage23.snapshot.hash)

            if (evaluationStage19 != null) {
                FarolVisualIdentityStage23.Metrics.recordEventToCandidate(
                    "AccessibilityScheduled",
                    SystemClock.elapsedRealtimeNanos() - eventStartedNsStage23,
                )
                FarolReadingActivationStage26.Metrics.sample("eventToCandidate", SystemClock.elapsedRealtimeNanos() - eventStartedNsStage23)
                stage26CandidateEventStartedNs = eventStartedNsStage23
                stage26CandidateActivationGeneration = scheduledActivationStage26.generation
                if (!isReadingActivationGenerationFreshStage26(stage26CandidateActivationGeneration)) {
                    FarolReadingActivationStage26.Metrics.increment("workCancelledOnReadingOff")
                    return@launch
                }
                stage19VisualVerificationPending = false
                stage19OcrSerial += 1L
                stage23OcrGate.cancelBecauseAccessibilityWon(visualDecisionStage23.generation, collectionStage23.snapshot.hash)
                stage21OcrGate.cancelBecauseAccessibilityWon()
                stage19OcrRerunRequested = false
                processUniversalVisualStage19(evaluationStage19, "AccessibilityScheduled", cycleIdStage20)
            } else {
                stage19VisualVerificationPending = true
                requestUniversalScreenshotStage19(null, cycleIdStage20)
            }
        }
    } // stage23_scheduled_demand_bound_to_visual_generation

    private fun scheduleScreenshotFallback127(expectedPackage: String) {
        val sessionToken0162 = driverCardSessionGate0162.current()?.takeIf { it.packageName == expectedPackage } ?: return
        val identity0189 = listOf(
            expectedPackage,
            sessionToken0162.generation.toString(),
            sessionToken0162.windowId.toString(),
            universalScreenGeneration.toString(),
            universalWindowGeneration.toString(),
            (lastImmediateScreenFingerprintChecklist13 ?: 0).toString(),
        ).joinToString("|")
        val now0189 = System.currentTimeMillis()
        if (scheduledOcrIdentity0189 == identity0189 && screenshotFallbackJob127?.isActive == true) {
            FarolFlightRecorder0163.record(
                stage = "OCR_FALLBACK_DEDUPED_0189",
                packageName = expectedPackage,
                details = "identity=$identity0189; reason=same_generation_already_scheduled",
            )
            return
        }
        if (lastOcrAttemptIdentity0189 == identity0189 && now0189 - lastOcrAttemptAtMillis0189 < 350L) {
            return
        }
        screenshotFallbackJob127?.cancel()
        scheduledOcrIdentity0189 = identity0189
        FarolFlightRecorder0163.record(
            stage = "OCR_FALLBACK_SCHEDULED",
            packageName = expectedPackage,
            details = "delay_ms=0; generation=$universalScreenGeneration; windowGeneration=$universalWindowGeneration; identity=$identity0189",
        )
        val scheduledAt127 = System.currentTimeMillis()
        screenshotFallbackJob127 = scope.launch {
            try {
                if (!serviceReady || !WorkModePolicy0162.isEnabled(currentSettings)) return@launch
                if (!driverCardSessionGate0162.isCurrent(sessionToken0162)) return@launch
                if (scheduledOcrIdentity0189 != identity0189) return@launch
                if (expectedPackage != strictSelectedRootPackageChecklist1()) return@launch
                if (!shouldScanPackage(expectedPackage)) return@launch
                if (lastAccessibilityAcceptedAtMillis127 >= scheduledAt127) return@launch
                lastOcrAttemptIdentity0189 = identity0189
                lastOcrAttemptAtMillis0189 = System.currentTimeMillis()
                requestScreenshotAnalysis(allowPopupCandidate = true)
            } finally {
                if (scheduledOcrIdentity0189 == identity0189) scheduledOcrIdentity0189 = null
            }
        }
    } // ocr_single_flight_per_generation_0_1_189


    // subsecond_capture_helpers_final_checklist_6

// low_priority_capture_final_checklist_6
 // automatic_capture_nonblocking_0_1_129

    private fun requestScreenshotAnalysis(allowPopupCandidate: Boolean = false) {
        val ocrAttemptStartedElapsedNanos0163 = android.os.SystemClock.elapsedRealtimeNanos()
        FarolFlightRecorder0163.record(
            stage = "OCR_REQUEST_EVALUATE",
            packageName = universalResolvedForegroundPackage(),
            details = "routeActive=${universalRouteJob?.isActive == true}; lastAnalyzedHash=$lastAnalyzedHash; lastSnapshotHash=$lastSnapshotHash; strictRoot=${hasStrictSelectedRootChecklist1()}; live=${currentSettings.liveReadingEnabled}; gesture=$bubbleGestureActive; ready=$serviceReady; external=${isUniversalExternalWindowActive()}; sdk=${Build.VERSION.SDK_INT}; generation=$universalScreenGeneration; windowGeneration=$universalWindowGeneration",
            elapsedRealtimeNanos = ocrAttemptStartedElapsedNanos0163,
        )
        // bubble_instant_drag_0_1_116
        // bubble_drag_screenshot_pause_0_1_116
        // bubble_drag_ocr_background_0_1_116
        @Suppress("UNUSED_VARIABLE") val ignoredAllowPopupCandidate0161 = allowPopupCandidate
        if (universalRouteJob?.isActive == true || (lastAnalyzedHash != null && lastAnalyzedHash == lastSnapshotHash)) return
        if (!hasStrictSelectedRootChecklist1()) return
        if (!currentSettings.liveReadingEnabled || bubbleGestureActive) return
        if (!serviceReady || !isUniversalExternalWindowActive() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        val sessionToken0162 = driverCardSessionGate0162.current() ?: return
        val resolvedOcrPackage = sessionToken0162.packageName
        val savedPackages0161 = SelectedRideAppStore.read(applicationContext)
        if (resolvedOcrPackage !in savedPackages0161 || !shouldScanPackage(resolvedOcrPackage)) return
        val rootHandle0187 = captureRootHandle0187() ?: return
        if (rootHandle0187.packageName != resolvedOcrPackage || rootHandle0187.windowId != sessionToken0162.windowId) return
        val accessibilitySnapshot0161 = collectImmediateVisibleTextChecklist13(rootHandle0187.node)
        val nodeSnapshot0161 = collectFailedCardNodeLines0161(rootHandle0187.node)
        val parserEvaluation0161 = SimpleSavedAppFarolPolicy.evaluate(
            packageName = resolvedOcrPackage,
            savedPackages = savedPackages0161,
            text = DriverCardTextSanitizer0162.prepare(resolvedOcrPackage, accessibilitySnapshot0161),
        )
        val probableCard0161 = FailedCardRecoveryEngine0161.probableRideCard(
            text = accessibilitySnapshot0161,
            packageName = resolvedOcrPackage,
        )
        val selectedRootAllowsOcr0166 = FarolSelectedAppInputPolicy0166.shouldAttemptOcr(
            packageName = resolvedOcrPackage,
            selectedPackages = savedPackages0161,
            strictRootPackageName = rootHandle0187.packageName,
            parserAlreadyActive = parserEvaluation0161.active,
        )
        if (!selectedRootAllowsOcr0166) return
        val probableCardForCapture0166 = probableCard0161 || selectedRootAllowsOcr0166

        val ocrRequestToken = UniversalFastReadPolicy.createOcrRequestToken(
            observedPackageName = universalForegroundPackageName ?: activePackageName,
            resolvedPackageName = resolvedOcrPackage,
            ownPackageName = this.packageName,
            screenGeneration = universalScreenGeneration,
            windowGeneration = universalWindowGeneration,
        ) ?: return
        val requestedPackage = resolvedOcrPackage // immutable_selected_session_0_1_162
        if (!UniversalFastReadPolicy.shouldScanLivePackage(requestedPackage, this.packageName)) return
        if (!UniversalFastReadPolicy.shouldRequestOcr(
                accessibilityOwnsCard = universalAccessibilityOwnsCard,
                hasActiveAddressSignature = universalActiveAddressSignature != null,
            )
        ) return

        val now0161 = System.currentTimeMillis()
        val minimumOcrIntervalMillis = UniversalFastReadPolicy.minimumOcrIntervalMillis(
            hasActiveAddressSignature = universalActiveAddressSignature != null,
        )
        if (now0161 - lastScreenshotMillis < minimumOcrIntervalMillis) return
        val windowId0161 = rootHandle0187.windowId ?: return
        val captureSignature0161 = FailedCardRecoveryEngine0161.signature(
            packageName = requestedPackage,
            windowId = windowId0161,
            text = accessibilitySnapshot0161,
            nodes = nodeSnapshot0161,
        )
        val recoveryBinding0187 = FarolRecoveryBinding0187(
            packageName = requestedPackage,
            sessionGeneration = sessionToken0162.generation,
            windowId = windowId0161,
            screenGeneration = universalScreenGeneration,
            windowGeneration = universalWindowGeneration,
            captureSignature = captureSignature0161,
        )
        if (!screenshotInProgress.compareAndSet(false, true)) return
        val captureReserved0161 = failedCardAutoCaptureGate0161.tryStart(
            signature = captureSignature0161,
            probableCard = probableCardForCapture0166,
            parserActive = parserEvaluation0161.active,
            routeInFlight = universalRouteJob?.isActive == true,
            hasDecision = currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red,
            nowMillis = now0161,
        )
        if (!captureReserved0161) {
            screenshotInProgress.set(false)
            return
        }

        lastScreenshotMillis = now0161
        lastFailedCardNodes0161 = nodeSnapshot0161
        lastFailedCardSignature0161 = captureSignature0161
        lastFailedCardAccessibilityHash0161 = accessibilitySnapshot0161.hashCode()
        rememberSourceText(requestedPackage, TextSource.Accessibility, accessibilitySnapshot0161)
        UnifiedDebugEventStore.record(
            "BUBBLE_FAILED_CARD_CAPTURE_STARTED",
            requestedPackage,
            "signature=$captureSignature0161; window=$windowId0161; texto=${accessibilitySnapshot0161.length}; nodes=${nodeSnapshot0161.size}",
        )

        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        scope.launch {
                            var bitmap0161: Bitmap? = null
                            try {
                                val stillFresh0161 = UniversalFastReadPolicy.isOcrRequestFresh(
                                    token = ocrRequestToken,
                                    observedPackageName = universalForegroundPackageName ?: activePackageName,
                                    resolvedPackageName = universalResolvedForegroundPackage(),
                                    ownPackageName = this@LiveRideAccessibilityService.packageName,
                                    screenGeneration = universalScreenGeneration,
                                    windowGeneration = universalWindowGeneration,
                                )
                                if (!stillFresh0161 || !driverCardSessionGate0162.isCurrent(sessionToken0162)) return@launch
                                if (!isRecoveryBindingFresh0187(recoveryBinding0187, accessibilitySnapshot0161)) return@launch

                                bitmap0161 = screenshot.toSoftwareBitmap() ?: return@launch
                                val ocrResult0188 = withContext(Dispatchers.Default) {
                                    run {
                                        val farolLatencyOcrStartedNsStage9 = android.os.SystemClock.elapsedRealtimeNanos()
                                        val farolLatencyOcrResultStage9 = ocrService.extractStructuredText(bitmap0161)
                                        FarolLatencyProbeStage9.recordOcrStructured(
                                            startedNs = farolLatencyOcrStartedNsStage9,
                                            textLength = farolLatencyOcrResultStage9.text.length,
                                            blockCount = farolLatencyOcrResultStage9.blocks.size,
                                        )
                                        farolLatencyOcrResultStage9
                                    }
                                }
                                val ocrText0161 = ocrResult0188.text
                                val recoveryEvidence0187 = captureRecoveryEvidence0187(
                                    recoveryBinding0187,
                                    mergeRideTexts(accessibilitySnapshot0161, ocrText0161),
                                )
                                if (recoveryEvidence0187 == null) {
                                    UnifiedDebugEventStore.record(
                                        "BUBBLE_FAILED_CARD_RECOVERY_DISCARDED_0187",
                                        requestedPackage,
                                        "captura ficou antiga antes da decisão; signature=$captureSignature0161",
                                    )
                                    return@launch
                                }
                                val currentAccessibility0187 = recoveryEvidence0187.accessibilityText
                                val currentNodes0187 = recoveryEvidence0187.nodes
                                val recoveredSnapshotText0187 = mergeRideTexts(currentAccessibility0187, ocrText0161)
                                rememberSourceText(requestedPackage, TextSource.Ocr, ocrText0161)
                                val models0161 = failedCardLayoutModelStore0161.modelsFor(requestedPackage)
                                val recovery0161 = withContext(Dispatchers.Default) {
                                    FailedCardRecoveryEngine0161.recover(
                                        packageName = requestedPackage,
                                        savedPackages = savedPackages0161,
                                        accessibilityText = DriverCardTextSanitizer0162.prepare(requestedPackage, currentAccessibility0187),
                                        ocrText = DriverCardTextSanitizer0162.prepare(requestedPackage, ocrText0161),
                                        nodes = currentNodes0187,
                                        knownModels = models0161,
                                    )
                                }
                                if (!isRecoveryBindingFresh0187(recoveryBinding0187, recoveredSnapshotText0187)) return@launch
                                recovery0161?.modelCandidate?.let(failedCardLayoutModelStore0161::saveCandidate)

                                UnifiedDebugEventStore.record(
                                    "BUBBLE_FAILED_CARD_EVIDENCE_ONLY_0188",
                                    requestedPackage,
                                    "recovered=${recovery0161 != null}; strategy=${recovery0161?.strategy ?: "nenhuma"}; routeAuthority=false",
                                )
                                processRideText(
                                    ocrText0161,
                                    TextSource.Ocr,
                                    allowPopupCandidate = true,
                                    packageHint152 = requestedPackage,
                                    ocrBlocks0188 = ocrResult0188.blocks,
                                )

                                withContext(Dispatchers.IO) {
                                    FailedCardTechnicalCaptureStore0161.save(
                                        context = applicationContext,
                                        snapshot = FailedCardTechnicalSnapshot0161(
                                            signature = captureSignature0161,
                                            packageName = requestedPackage,
                                            windowId = windowId0161,
                                            createdAtMillis = now0161,
                                            accessibilityText = accessibilitySnapshot0161,
                                            ocrText = ocrText0161,
                                            nodes = nodeSnapshot0161,
                                            recovered = recovery0161 != null,
                                            recoveryStrategy = recovery0161?.strategy,
                                        ),
                                        bitmap = bitmap0161,
                                    )
                                }
                                UnifiedDebugEventStore.record(
                                    "BUBBLE_FAILED_CARD_CAPTURE_FINISHED",
                                    requestedPackage,
                                    "signature=$captureSignature0161; recovered=${recovery0161 != null}; strategy=${recovery0161?.strategy ?: "nenhuma"}",
                                )
                            } catch (error0161: Throwable) {
                                recordDiagnostic(
                                    stage = "failed_card_auto_capture_error_0161",
                                    reason = "Falha isolada na captura automatica do card amarelo.",
                                    error = error0161,
                                )
                            } finally {
                                bitmap0161?.takeUnless(Bitmap::isRecycled)?.recycle()
                                failedCardAutoCaptureGate0161.finish(captureSignature0161)
                                screenshotInProgress.set(false)
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        failedCardAutoCaptureGate0161.finish(captureSignature0161)
                        screenshotInProgress.set(false)
                        UnifiedDebugEventStore.record(
                            "BUBBLE_FAILED_CARD_CAPTURE_FAILED",
                            requestedPackage,
                            "signature=$captureSignature0161; codigo=$errorCode",
                        )
                    }
                },
            )
        }.onFailure { error0161 ->
            failedCardAutoCaptureGate0161.releaseForRetry(captureSignature0161)
            screenshotInProgress.set(false)
            recordDiagnostic(
                stage = "failed_card_auto_capture_request_error_0161",
                reason = "Android nao iniciou a captura automatica do card amarelo.",
                error = error0161,
            )
        }
    } // failed_card_auto_capture_0_1_161

    private fun collectVisibleText(allowPopupCandidate: Boolean = false): String {
        if (!hasStrictSelectedRootChecklist1()) return "" // strict_tree_gate_checklist_1

        if (!serviceReady || !isUniversalExternalWindowActive()) return ""
        val root = safeRootInActiveWindow0185() ?: return ""
        val rootPackage = safeNodePackageName0185(root)
        val expectedPackage = universalForegroundPackageName
        if (rootPackage == this.packageName || expectedPackage == this.packageName) return ""
        if (rootPackage != null && expectedPackage != null && rootPackage != expectedPackage && !SelectedRideOverlayWindowPolicy.isTransient(rootPackage)) return "" // selected_overlay_tree_bridge_checklist_11
        val lines = mutableListOf<String>()
        collectNodeText(root, lines)
        return lines.map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString("\n")
    } // universal_stable_collect_0_1_101

    private fun collectNodeText(node: AccessibilityNodeInfo?, lines: MutableList<String>) {
        if (node == null || lines.size >= MAX_ACCESSIBILITY_NODES_0167) return
        runCatching { node.text?.toString() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { lines += it }
        runCatching { node.contentDescription?.toString() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { lines += it }
        val remainingNodes0185 = (MAX_ACCESSIBILITY_NODES_0167 - lines.size).coerceAtLeast(0)
        val childCount0185 = runCatching { node.childCount }
            .getOrDefault(0)
            .coerceIn(0, remainingNodes0185)
        for (index in 0 until childCount0185) {
            if (lines.size >= MAX_ACCESSIBILITY_NODES_0167) break
            collectNodeText(runCatching { node.getChild(index) }.getOrNull(), lines)
        }
    }


    private fun collectFailedCardNodeLines0161(): List<FailedCardNodeLine0161> {
        val rootHandle0187 = captureRootHandle0187() ?: return emptyList()
        val rootPackage0187 = rootHandle0187.packageName ?: return emptyList()
        if (!SelectedRideAppStore.read(applicationContext).contains(rootPackage0187)) return emptyList()
        return collectFailedCardNodeLines0161(rootHandle0187.node)
    }

    private fun collectFailedCardNodeLines0161(root0161: AccessibilityNodeInfo): List<FailedCardNodeLine0161> {
        val output0161 = mutableListOf<FailedCardNodeLine0161>()
        collectFailedCardNodeLines0161(root0161, output0161)
        return output0161
            .filter { it.text.isNotBlank() }
            .distinctBy { listOf(it.text.trim(), it.top, it.left, it.className, it.viewId) }
            .take(160)
    }

    private fun collectFailedCardNodeLines0161(
        node0161: AccessibilityNodeInfo?,
        output0161: MutableList<FailedCardNodeLine0161>,
    ) {
        if (node0161 == null || output0161.size >= 160) return
        val bounds0161 = Rect()
        runCatching { node0161.getBoundsInScreen(bounds0161) }
        val className0161 = runCatching { node0161.className?.toString() }.getOrNull().orEmpty()
        val viewId0161 = runCatching { node0161.viewIdResourceName }.getOrNull().orEmpty()
        linkedSetOf(
            runCatching { node0161.text?.toString() }.getOrNull().orEmpty(),
            runCatching { node0161.contentDescription?.toString() }.getOrNull().orEmpty(),
        ).asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach { text0161 ->
                if (output0161.size < 160) {
                    output0161 += FailedCardNodeLine0161(
                        text = text0161.take(500),
                        top = bounds0161.top,
                        left = bounds0161.left,
                        bottom = bounds0161.bottom,
                        right = bounds0161.right,
                        className = className0161.take(160),
                        viewId = viewId0161.take(200),
                    )
                }
            }
        for (index0161 in 0 until runCatching { node0161.childCount }.getOrDefault(0).coerceIn(0, 160)) {
            if (output0161.size >= 160) break
            collectFailedCardNodeLines0161(
                runCatching { node0161.getChild(index0161) }.getOrNull(),
                output0161,
            )
        }
    } // failed_card_accessibility_structure_0_1_161

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

    private fun collectImmediateVisibleTextChecklist13(): String =
        captureRootHandle0187()?.let { collectImmediateVisibleTextChecklist13(it.node) }.orEmpty()

    private fun collectImmediateVisibleTextChecklist13(root: AccessibilityNodeInfo): String {
        val pendingNodes0167 = java.util.ArrayDeque<AccessibilityNodeInfo>()
        val uniqueLines0167 = LinkedHashSet<String>(64)
        pendingNodes0167.add(root)
        var visitedNodes0167 = 0
        var acceptedCharacters0167 = 0

        fun acceptLine0167(raw: CharSequence?) {
            if (acceptedCharacters0167 >= MAX_ACCESSIBILITY_TEXT_CHARS_0167) return
            val line = raw?.toString()?.trim().orEmpty()
            if (line.isBlank() || line in uniqueLines0167) return
            val remaining = MAX_ACCESSIBILITY_TEXT_CHARS_0167 - acceptedCharacters0167
            val bounded = line.take(remaining)
            if (bounded.isNotBlank()) {
                uniqueLines0167 += bounded
                acceptedCharacters0167 += bounded.length + 1
            }
        }

        while (pendingNodes0167.isNotEmpty() &&
            visitedNodes0167 < MAX_ACCESSIBILITY_NODES_0167 &&
            acceptedCharacters0167 < MAX_ACCESSIBILITY_TEXT_CHARS_0167
        ) {
            val node0167 = pendingNodes0167.removeLast()
            visitedNodes0167 += 1
            acceptLine0167(runCatching { node0167.text }.getOrNull())
            acceptLine0167(runCatching { node0167.contentDescription }.getOrNull())
            val childCount0185 = runCatching { node0167.childCount }
                .getOrDefault(0)
                .coerceIn(0, MAX_ACCESSIBILITY_NODES_0167 - visitedNodes0167)
            for (index0167 in childCount0185 - 1 downTo 0) {
                runCatching { node0167.getChild(index0167) }.getOrNull()?.let(pendingNodes0167::addLast)
            }
        }
        return uniqueLines0167.joinToString("\n")
    } // bounded_allocation_light_accessibility_tree_0_1_167

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
        safeRootWindowId0185()?.takeIf { it >= 0 }
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
            val sessionToken0187 = driverCardSessionGate0162.current()
                ?.takeIf { it.packageName == packageName }
                ?: return@launch
            val rootHandle0187 = captureRootHandle0187() ?: return@launch
            if (rootHandle0187.packageName != packageName || rootHandle0187.windowId != sessionToken0187.windowId) return@launch
            val readBinding0187 = FarolReadBinding0187(
                packageName = packageName,
                sessionGeneration = sessionToken0187.generation,
                windowId = sessionToken0187.windowId,
                screenGeneration = universalScreenGeneration,
                windowGeneration = universalWindowGeneration,
            )
            val confirmedTextChecklist14 = collectImmediateVisibleTextChecklist13(rootHandle0187.node)
            val confirmedEvaluationChecklist14 = withContext(Dispatchers.Default) {
                SimpleSavedAppFarolPolicy.evaluate(packageName, savedPackagesChecklist14, confirmedTextChecklist14)
            }
            partialReadConfirmationJobChecklist14 = null
            if (!isReadBindingFresh0187(readBinding0187)) return@launch
            if (confirmedEvaluationChecklist14.active) {
                processRideText(
                    confirmedTextChecklist14,
                    TextSource.Accessibility,
                    allowPopupCandidate = true,
                    packageHint152 = packageName,
                    readBinding0187 = readBinding0187,
                )
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

    private fun authorizeRoute0188(
        packageName0188: String,
        savedPackages0188: Set<String>,
        source0188: TextSource,
        readBinding0187: FarolReadBinding0187?,
        ocrBlocks0188: List<OcrTextBlock0188>,
    ): FarolRouteAuthorization0188? {
        val session0188 = driverCardSessionGate0162.current()
            ?.takeIf { it.packageName == packageName0188 }
            ?: return null
        if (!driverCardSessionGate0162.isCurrent(session0188)) return null
        val expectedWindow0188 = readBinding0187?.windowId ?: session0188.windowId
        val blocks0188 = when (source0188) {
            TextSource.Accessibility -> FarolLatencyProbeStage9.measureBlocks(
                stage = "ACCESSIBILITY_CARD_BLOCKS",
                source = "Accessibility",
            ) {
                collectAccessibilityCardBlocks0188(
                    expectedPackage0188 = packageName0188,
                    expectedWindowId0188 = expectedWindow0188,
                )
            }
            TextSource.Ocr -> FarolLatencyProbeStage9.measureBlocks(
                stage = "OCR_CARD_GROUPS",
                source = "OCR",
            ) {
                collectOcrCardBlocks0188(
                    packageName0188 = packageName0188,
                    windowId0188 = expectedWindow0188,
                    ocrBlocks0188 = ocrBlocks0188,
                )
            }
        }
        if (!FarolAppIdentityIsolationStage18.blocksBelongToSingleAuthority(
                authorityPackageName = packageName0188,
                blockPackages = blocks0188.map { it.packageName },
            )
        ) {
            UnifiedDebugEventStore.record(
                "BUBBLE_MIXED_APP_BLOCKS_REJECTED_STAGE18", packageName0188,
                "blocos de pacotes distintos não podem formar um único card/snapshot",
            )
            return null
        }
        val gateSnapshotStage16 = FarolVisibleCardPriorityStage16.gateSnapshotIdentity(
            packageName = packageName0188,
            sessionGeneration = readBinding0187?.sessionGeneration ?: session0188.generation,
            expectedWindowId = expectedWindow0188,
            screenGeneration = readBinding0187?.screenGeneration ?: universalScreenGeneration,
            windowGeneration = readBinding0187?.windowGeneration ?: universalWindowGeneration,
            blocks = blocks0188.map(::toStage16BlockEvidence),
        )
        val cachedAuthorizationStage16 = stage16AcceptedGateAuthorization
        val useAcceptedGateCacheStage16 = FarolLatencyProbeStage9.measureValue(
            stage = "STAGE16_ACCEPTED_GATE_CACHE_LOOKUP",
            source = source0188.name,
        ) {
            FarolVisibleCardPriorityStage16.canReuseAcceptedAuthorization(
                cached = stage16AcceptedGateSnapshot,
                current = gateSnapshotStage16,
                cachedPackageName = cachedAuthorizationStage16?.packageName,
                cachedWindowId = cachedAuthorizationStage16?.windowId,
                cachedAddressSignature = cachedAuthorizationStage16?.addressSignature,
                cachedScreenHash = cachedAuthorizationStage16?.screenHash,
                activePackageName = universalActiveRidePackageName,
                activeAddressSignature = universalActiveAddressSignature,
                activeScreenHash = lastSnapshotHash,
                routeInFlight = universalRouteJob?.isActive == true,
                stableDecision = currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red,
                transientEmptyPending = stage16TransientEmptyBinding != null,
            )
        }
        if (useAcceptedGateCacheStage16 && cachedAuthorizationStage16 != null) {
            UnifiedDebugEventStore.record(
                "BUBBLE_ROUTE_GATE_CACHE_HIT_STAGE16", packageName0188,
                "source=${source0188.name}; window=$expectedWindow0188; blocks=${blocks0188.size}; screenHash=${cachedAuthorizationStage16.screenHash}",
            )
            return cachedAuthorizationStage16
        }
        val decision0188 = FarolLatencyProbeStage9.measureValue(
            stage = "REAL_DEVICE_GATE",
            source = source0188.name,
        ) {
            FarolRealDeviceGate0188.evaluate(
                selectedPackageName = packageName0188,
                selectedPackages = savedPackages0188,
                blocks = blocks0188,
            )
        }
        decision0188.authorization?.let { authorizationStage16 ->
            stage16AcceptedGateSnapshot = gateSnapshotStage16
            stage16AcceptedGateAuthorization = authorizationStage16
        }
        UnifiedDebugEventStore.record(
            if (decision0188.authorized) "BUBBLE_ROUTE_GATE_ACCEPTED_0188" else "BUBBLE_ROUTE_GATE_REJECTED_0188",
            packageName0188,
            "source=${source0188.name}; window=$expectedWindow0188; blocks=${blocks0188.size}; reason=${decision0188.reason}; block=${decision0188.authorization?.blockId ?: "none"}",
        )
        return decision0188.authorization
    }

    private fun collectOcrCardBlocks0188(
        packageName0188: String,
        windowId0188: Int,
        ocrBlocks0188: List<OcrTextBlock0188>,
    ): List<FarolCardBlock0188> {
        if (ocrBlocks0188.isEmpty()) return emptyList()
        val fragments0189 = ocrBlocks0188.take(120).mapIndexedNotNull { index0189, block0189 ->
            block0189.text.takeIf(String::isNotBlank)?.let {
                FarolSpatialFragment0189(
                    id = "ocr:$windowId0188/block:$index0189",
                    text = it,
                    left = block0189.left,
                    top = block0189.top,
                    right = block0189.right,
                    bottom = block0189.bottom,
                )
            }
        }
        return FarolVisualPriority0189.cluster("ocr:$windowId0188", fragments0189).map { group0189 ->
            FarolCardBlock0188(
                id = group0189.id,
                packageName = packageName0188,
                windowId = windowId0188,
                windowLayer = Int.MAX_VALUE,
                depth = 1,
                text = group0189.text,
                source = FarolEvidenceSource0188.Ocr,
                left = group0189.left,
                top = group0189.top,
                right = group0189.right,
                bottom = group0189.bottom,
            )
        }
    }

    private fun collectAccessibilityCardBlocks0188(
        expectedPackage0188: String,
        expectedWindowId0188: Int,
    ): List<FarolCardBlock0188> {
        val output0188 = ArrayList<FarolCardBlock0188>(96)
        val budget0188 = intArrayOf(0)
        val seenWindows0188 = HashSet<Int>()
        val interactive0188 = runCatching { windows }.getOrDefault(emptyList())
            .sortedByDescending { runCatching { it.layer }.getOrDefault(0) }
        for (window0188 in interactive0188) {
            val windowId0188 = runCatching { window0188.id }.getOrDefault(-1)
            val root0188 = runCatching { window0188.root }.getOrNull() ?: continue
            val rootPackage0188 = safeNodePackageName0185(root0188) ?: continue
            if (rootPackage0188 != expectedPackage0188) continue
            val layer0188 = runCatching { window0188.layer }.getOrDefault(0)
            val rootId0188 = "a11y:$windowId0188"
            collectAccessibilitySubtreeBlocks0188(
                node0188 = root0188,
                id0188 = rootId0188,
                parentId0188 = null,
                depth0188 = 0,
                packageName0188 = expectedPackage0188,
                windowId0188 = windowId0188,
                windowLayer0188 = layer0188,
                output0188 = output0188,
                budget0188 = budget0188,
            )
            seenWindows0188 += windowId0188
        }
        if (expectedWindowId0188 !in seenWindows0188) {
            val rootHandle0188 = captureRootHandle0187()
            if (rootHandle0188?.packageName == expectedPackage0188 && rootHandle0188.windowId == expectedWindowId0188) {
                collectAccessibilitySubtreeBlocks0188(
                    node0188 = rootHandle0188.node,
                    id0188 = "a11y:$expectedWindowId0188",
                    parentId0188 = null,
                    depth0188 = 0,
                    packageName0188 = expectedPackage0188,
                    windowId0188 = expectedWindowId0188,
                    windowLayer0188 = 0,
                    output0188 = output0188,
                    budget0188 = budget0188,
                )
            }
        }
        return output0188.take(120)
    }

    private fun collectAccessibilitySubtreeBlocks0188(
        node0188: AccessibilityNodeInfo,
        id0188: String,
        parentId0188: String?,
        depth0188: Int,
        packageName0188: String,
        windowId0188: Int,
        windowLayer0188: Int,
        output0188: MutableList<FarolCardBlock0188>,
        budget0188: IntArray,
    ): List<String> {
        if (budget0188[0] >= MAX_ACCESSIBILITY_NODES_0167 || output0188.size >= 120) return emptyList()
        budget0188[0] += 1
        val lines0188 = LinkedHashSet<String>(16)
        fun addLine0188(value0188: CharSequence?) {
            value0188?.toString()?.trim()?.takeIf(String::isNotBlank)?.let(lines0188::add)
        }
        addLine0188(runCatching { node0188.text }.getOrNull())
        addLine0188(runCatching { node0188.contentDescription }.getOrNull())
        val childCount0188 = runCatching { node0188.childCount }.getOrDefault(0).coerceIn(0, 64)
        for (index0188 in 0 until childCount0188) {
            if (budget0188[0] >= MAX_ACCESSIBILITY_NODES_0167) break
            val child0188 = runCatching { node0188.getChild(index0188) }.getOrNull() ?: continue
            val childId0188 = "$id0188/$index0188"
            collectAccessibilitySubtreeBlocks0188(
                node0188 = child0188,
                id0188 = childId0188,
                parentId0188 = id0188,
                depth0188 = depth0188 + 1,
                packageName0188 = packageName0188,
                windowId0188 = windowId0188,
                windowLayer0188 = windowLayer0188,
                output0188 = output0188,
                budget0188 = budget0188,
            ).forEach(lines0188::add)
        }
        val text0188 = lines0188.joinToString("\n").take(MAX_ACCESSIBILITY_TEXT_CHARS_0167)
        if (text0188.isNotBlank()) {
            val bounds0189 = Rect()
            runCatching { node0188.getBoundsInScreen(bounds0189) }
            output0188 += FarolCardBlock0188(
                id = id0188,
                parentId = parentId0188,
                packageName = packageName0188,
                windowId = windowId0188,
                windowLayer = windowLayer0188,
                depth = depth0188,
                text = text0188,
                source = FarolEvidenceSource0188.Accessibility,
                left = bounds0189.left,
                top = bounds0189.top,
                right = bounds0189.right,
                bottom = bounds0189.bottom,
                syntheticRoot = depth0188 == 0,
            )
        }
        return lines0188.toList()
    }

    private suspend fun processRideText(
        textRaw0168: String,
        source: TextSource,
        allowPopupCandidate: Boolean = false,
        packageHint152: String? = null,
        readBinding0187: FarolReadBinding0187? = null,
        ocrBlocks0188: List<OcrTextBlock0188> = emptyList(),
    ) {
        val text = FarolUnifiedVisual0168.normalizeForAnalysis(textRaw0168) // farol_unified_visual_0_1_168

        @Suppress("UNUSED_VARIABLE") val ignoredPopupCandidateChecklist13 = allowPopupCandidate

        FarolFlightRecorder0163.recordTextSnapshot(
            stage = "BUBBLE_PROCESS",
            packageName = universalResolvedForegroundPackage(),
            source = source.name,
            text = text,
        )
        UnifiedDebugEventStore.record(
            "BUBBLE_PROCESS_ENTER",
            universalResolvedForegroundPackage(),
            "fonte=${source.name}; tamanho=${text.length}; hash=${text.hashCode()}; gesture=$bubbleGestureActive; ready=$serviceReady; appEnabled=${currentSettings.appEnabled}; live=${currentSettings.liveReadingEnabled}",
        )
        if (bubbleGestureActive || !serviceReady || !currentSettings.appEnabled || !currentSettings.liveReadingEnabled) return // bubble_drag_process_pause_0_1_116
        val savedPackagesChecklist13 = SelectedRideAppStore.read(applicationContext)
        val selectedPackageChecklist13 = normalizePackageName(packageHint152)
            ?.takeIf { it in savedPackagesChecklist13 && DriverAppPackagePolicy0162.isEligible(it, packageName) }
            ?: return // immutable_package_required_0_1_162
        val sessionToken0162 = driverCardSessionGate0162.current()
            ?.takeIf { it.packageName == selectedPackageChecklist13 }
            ?: return
        if (!driverCardSessionGate0162.isCurrent(sessionToken0162)) return
        if (source == TextSource.Accessibility &&
            (readBinding0187 == null || !isReadBindingFresh0187(readBinding0187))
        ) {
            UnifiedDebugEventStore.record(
                "BUBBLE_ACCESSIBILITY_READ_DISCARDED_0187",
                selectedPackageChecklist13,
                "leitura perdeu pacote, sessão, janela ou geração antes da avaliação",
            )
            return
        }

        val rawSnapshotText0185 = text.trim()
        val cardEvidence0185 = RideCardConfirmationPolicy0185.prepare(
            packageName = selectedPackageChecklist13,
            rawText = rawSnapshotText0185,
        )
        if (cardEvidence0185.rejectedFeed) {
            UnifiedDebugEventStore.record(
                "BUBBLE_UNCONFIRMED_CARD_REJECTED_0185",
                selectedPackageChecklist13,
                "fonte=${source.name}; motivo=${cardEvidence0185.reason}; hash=${FarolUnifiedVisual0168.semanticHash(rawSnapshotText0185)}",
            )
            hardClearUniversalTwoAddress(
                reason = cardEvidence0185.reason,
                keepWaitingYellow = true,
            )
            return
        }
        val routeAuthorization0188 = authorizeRoute0188(
            packageName0188 = selectedPackageChecklist13,
            savedPackages0188 = savedPackagesChecklist13,
            source0188 = source,
            readBinding0187 = readBinding0187,
            ocrBlocks0188 = ocrBlocks0188,
        )
        if (routeAuthorization0188 == null) {
            UnifiedDebugEventStore.record(
                "BUBBLE_ROUTE_GATE_REJECTED_0188",
                selectedPackageChecklist13,
                "source=${source.name}; selectedPackageObservationOnly=true",
            )
            hardClearUniversalTwoAddress(
                reason = "Aplicativo selecionado ativo, mas nenhum card atual com destino final confirmado.",
                keepWaitingYellow = true,
            )
            if (source == TextSource.Accessibility) scheduleScreenshotFallback127(selectedPackageChecklist13)
            return
        }
        val snapshotTextChecklist13 = routeAuthorization0188.analysisText
        val evaluationChecklist13 = SimpleSavedAppFarolPolicy.Evaluation(
            packageName = selectedPackageChecklist13,
            addresses = routeAuthorization0188.addresses,
            pickup = routeAuthorization0188.pickup,
            destination = routeAuthorization0188.destination,
            addressSignature = routeAuthorization0188.addressSignature,
            screenHash = routeAuthorization0188.screenHash,
            active = true,
        )
        if (source == TextSource.Accessibility &&
            (readBinding0187 == null || !FarolLatencyProbeStage9.measureValue(
                stage = "POST_AUTH_READ_BINDING_FRESH",
                source = source.name,
            ) {
                isReadBindingFresh0187(readBinding0187)
            })
        ) {
            UnifiedDebugEventStore.record(
                "BUBBLE_ACCESSIBILITY_READ_DISCARDED_0187",
                selectedPackageChecklist13,
                "leitura perdeu pacote, sessão, janela ou geração durante a avaliação",
            )
            return
        }
        if (stage16TransientEmptyBinding != null) {
            stage16TransientEmptyBinding = null
            UnifiedDebugEventStore.record(
                "BUBBLE_TRANSIENT_EMPTY_RESOLVED_STAGE16", selectedPackageChecklist13,
                "leitura positiva atual passou novamente pelo gate completo",
            )
        }
        if (source == TextSource.Accessibility && lastFailedCardAccessibilityHash0161 != snapshotTextChecklist13.hashCode()) {
            lastOcrText = ""
            lastOcrTextAtMillis = 0L
            lastFailedCardAccessibilityHash0161 = snapshotTextChecklist13.hashCode()
        }
        rememberSourceText(selectedPackageChecklist13, source, snapshotTextChecklist13)
        UnifiedDebugEventStore.record(
            "BUBBLE_ADDRESS_EVALUATION",
            selectedPackageChecklist13,
            "ativo=${evaluationChecklist13.active}; pickup=${evaluationChecklist13.pickup.orEmpty()}; destination=${evaluationChecklist13.destination.orEmpty()}; assinatura=${evaluationChecklist13.addressSignature}; screenHash=${evaluationChecklist13.screenHash}",
        )
        if (!evaluationChecklist13.active) {
            val failureNodes0161 = if (source == TextSource.Accessibility) {
                collectFailedCardNodeLines0161().also { lastFailedCardNodes0161 = it }
            } else {
                lastFailedCardNodes0161
            }
            val now0161 = System.currentTimeMillis()
            val freshAccessibility0161 = lastAccessibilityText.takeIf {
                now0161 - lastAccessibilityTextAtMillis in 0L..2_000L
            }.orEmpty()
            val freshOcr0161 = lastOcrText.takeIf {
                now0161 - lastOcrTextAtMillis in 0L..2_000L
            }.orEmpty()
            // Recuperação nunca decide diretamente a partir de buffers de momentos diferentes.
            // O screenshot one-shot abaixo cria uma identidade imutável de pacote, sessão,
            // janela e geração; somente essa captura pode chegar ao cálculo de rota.
            val mergedFailureText0161 = mergeRideTexts(freshAccessibility0161, freshOcr0161)
            if (FailedCardRecoveryEngine0161.probableRideCard(mergedFailureText0161, selectedPackageChecklist13)) {
                val windowId0161 = readBinding0187?.windowId
                    ?: captureRootHandle0187()?.windowId
                    ?: lastStableFarolWindowIdChecklist14
                    ?: 0
                lastFailedCardSignature0161 = FailedCardRecoveryEngine0161.signature(
                    packageName = selectedPackageChecklist13,
                    windowId = windowId0161,
                    text = mergedFailureText0161,
                    nodes = failureNodes0161,
                )
                UnifiedDebugEventStore.record(
                    "BUBBLE_FAILED_CARD_CAPTURE_ARMED",
                    selectedPackageChecklist13,
                    "signature=${lastFailedCardSignature0161}; window=$windowId0161; source=${source.name}",
                )
            }
            val decisionAge141 = FarolElapsedTimePolicy0187.ageMillis(SystemClock.elapsedRealtime(), universalLastActiveReadAtElapsedMillis0187)
            val preserveStableDecision141 =
                universalActiveRidePackageName == selectedPackageChecklist13 &&
                    universalActiveAddressSignature != null &&
                    (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red) &&
                    universalForegroundPackageName == selectedPackageChecklist13 &&
                    decisionAge141?.let { it <= 5_000L } == true
            val preserveRouteInFlight143 =
                universalActiveRidePackageName == selectedPackageChecklist13 &&
                    universalActiveAddressSignature != null &&
                    universalRouteJob?.isActive == true &&
                    decisionAge141?.let { it <= 8_000L } == true
            val preserveRecentValidatedCard144 =
                universalActiveRidePackageName == selectedPackageChecklist13 &&
                    universalActiveAddressSignature != null &&
                    universalForegroundPackageName == selectedPackageChecklist13 &&
                    decisionAge141?.let { it <= 5_000L } == true
            if (preserveStableDecision141 || preserveRouteInFlight143 || preserveRecentValidatedCard144) {
                UnifiedDebugEventStore.record(
                    "BUBBLE_INVALID_READ_DEFERRED",
                    selectedPackageChecklist13,
                    "fonte=${source.name}; OCR/card validado preservado; idade=${FarolElapsedTimePolicy0187.formatAge(decisionAge141)}; rotaAtiva=${universalRouteJob?.isActive == true}",
                )
                if (source == TextSource.Accessibility) scheduleScreenshotFallback127(selectedPackageChecklist13)
                return
            }
            hardClearUniversalTwoAddress(
                reason = "Tela sem dois enderecos validos por tempo suficiente; cor e quilometros removidos.",
                keepWaitingYellow = true,
            ) // confirmed_absence_clear_0_1_141
            return
        }

        if (source == TextSource.Accessibility) {
            lastAccessibilityAcceptedAtMillis127 = System.currentTimeMillis()
            screenshotFallbackJob127?.cancel()
            screenshotFallbackJob127 = null
        }
        universalLastActiveReadAtElapsedMillis0187 = SystemClock.elapsedRealtime()
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
            rebindAcceptedGateCacheStage16(evaluationChecklist13)
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
            FarolLatencyProbeStage9.recordDuplicateTotal(
                source = source.name,
                textLength = snapshotTextChecklist13.length,
            )
            UnifiedDebugEventStore.record(
                "BUBBLE_DUPLICATE_SKIPPED",
                selectedPackageChecklist13,
                "lastAnalyzedHash=$lastAnalyzedHash; screenHash=${evaluationChecklist13.screenHash}; routeActive=${universalRouteJob?.isActive == true}",
            )
            return
        }

        rememberBubbleReason(
            "destination_confirmed_0189",
            "Último endereço do bloco superior confirmado; preparando rota real.",
        )
        UnifiedDebugEventStore.record(
            "BUBBLE_DESTINATION_CONFIRMED_ORANGE_0189",
            selectedPackageChecklist13,
            "destination=${fieldsChecklist13.destination.orEmpty()}; screenHash=${evaluationChecklist13.screenHash}",
        )
        if (currentRadarColor != RadarColor.Orange || currentDistanceKm != null) {
            showOverlay(RadarColor.Default, distanceKm = null)
        }

        val settingsChecklist13 = currentSettings
        val targetsChecklist13 = fastWorkRegionTargetsChecklist13(settingsChecklist13)
        if (targetsChecklist13.destinations.isEmpty()) {
            rememberBubbleReason("work_region_missing", "Destino confirmado, mas falta Casa ou alfinete com coordenada validada.")
            showOverlay(RadarColor.Default, distanceKm = null)
            return
        }

        val cachedDistancesChecklist13 = googleMapsService.cachedDrivingDistancesFromAddressKm(
            originAddress = fieldsChecklist13.destination.orEmpty(),
            destinations = targetsChecklist13.destinations,
        )
        val decisionBindingChecklist13 = createDecisionBinding0187Phase4(
            packageName0187Phase4 = selectedPackageChecklist13,
            session0187Phase4 = sessionToken0162,
            screenHash0187Phase4 = evaluationChecklist13.screenHash,
            addressSignature0187Phase4 = evaluationChecklist13.addressSignature,
        ) ?: return
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
                decisionBindingChecklist13,
            ) // exact_cache_before_yellow_checklist_13
            return
        }

        screenshotFallbackJob127?.cancel()
        screenshotFallbackJob127 = null
        lastAccessibilityAcceptedAtMillis127 = System.currentTimeMillis()
        // accessibility_card_cancels_ocr_0_1_157
        UnifiedDebugEventStore.record("BUBBLE_ROUTE_REQUESTED", selectedPackageChecklist13, "destino=${fieldsChecklist13.destination.orEmpty()}; alvos=${targetsChecklist13.destinations.size}; generation=${decisionBindingChecklist13.screenGeneration}; windowGeneration=${decisionBindingChecklist13.windowGeneration}")
        rememberBubbleReason("universal_waiting", "Destino final confirmado; rota real em cálculo.")
        if (currentRadarColor != RadarColor.Orange || currentDistanceKm != null) {
            showOverlay(RadarColor.Default, distanceKm = null)
        } // destination_confirmed_orange_0_1_189
        bubblePrefs.edit().putString("fast_farol_last_path", "rota_google").apply()
        universalRouteJob = scope.launch {
            if (!driverCardSessionGate0162.isCurrent(sessionToken0162)) return@launch
            analyzeUniversalTwoAddress(
                snapshotText = snapshotTextChecklist13,
                fields = fieldsChecklist13,
                decisionBinding0187Phase4 = decisionBindingChecklist13,
            )
        }
    } // simple_saved_app_process_checklist_13
 // stable_farol_process_contract_checklist_14
 // simple_saved_app_process_checklist_13
 // universal_stable_process_0_1_101

    //    private fun resolveRidePackageForText( compatibility_boundary_0_1_102

    private data class FarolRecoveryEvidence0187(
        val accessibilityText: String,
        val nodes: List<FailedCardNodeLine0161>,
    )

    private fun isReadBindingFresh0187(binding0187: FarolReadBinding0187): Boolean {
        val currentSession0187 = driverCardSessionGate0162.current() ?: return false
        val rootHandle0187 = captureRootHandle0187() ?: return false
        return FarolReadBindingPolicy0187.isFresh(
            binding = binding0187,
            currentPackageName = rootHandle0187.packageName,
            currentSessionGeneration = currentSession0187.generation,
            currentWindowId = rootHandle0187.windowId,
            currentScreenGeneration = universalScreenGeneration,
            currentWindowGeneration = universalWindowGeneration,
        )
    }

    private fun captureRecoveryEvidence0187(
        binding0187: FarolRecoveryBinding0187,
        candidateText0187: String,
    ): FarolRecoveryEvidence0187? {
        val currentSession0187 = driverCardSessionGate0162.current() ?: return null
        val rootHandle0187 = captureRootHandle0187() ?: return null
        val currentWindow0187 = rootHandle0187.windowId ?: return null
        if (rootHandle0187.packageName != binding0187.packageName) return null
        val currentAccessibility0187 = collectImmediateVisibleTextChecklist13(rootHandle0187.node)
        val currentNodes0187 = collectFailedCardNodeLines0161(rootHandle0187.node)
        val currentSignature0187 = FailedCardRecoveryEngine0161.signature(
            packageName = binding0187.packageName,
            windowId = currentWindow0187,
            text = currentAccessibility0187,
            nodes = currentNodes0187,
        )
        val evidence0187 = RideCardConfirmationPolicy0185.prepare(
            packageName = binding0187.packageName,
            rawText = mergeRideTexts(currentAccessibility0187, candidateText0187),
        )
        if (!evidence0187.confirmedIndividualCard || evidence0187.rejectedFeed) return null
        if (!FarolRecoveryBindingPolicy0187.isFresh(
                binding = binding0187,
                currentPackageName = currentSession0187.packageName,
                currentSessionGeneration = currentSession0187.generation,
                currentWindowId = currentWindow0187,
                currentScreenGeneration = universalScreenGeneration,
                currentWindowGeneration = universalWindowGeneration,
                currentCaptureSignature = currentSignature0187,
            )
        ) return null
        return FarolRecoveryEvidence0187(
            accessibilityText = currentAccessibility0187,
            nodes = currentNodes0187,
        )
    }

    private fun isRecoveryBindingFresh0187(
        binding0187: FarolRecoveryBinding0187,
        candidateText0187: String,
    ): Boolean = captureRecoveryEvidence0187(binding0187, candidateText0187) != null

    private suspend fun applyRecoveredCard0161(
        selectedPackage0161: String,
        snapshotText0161: String,
        recovery0161: FailedCardRecoveryResult0161,
        binding0187: FarolRecoveryBinding0187,
    ) {
        if (!isRecoveryBindingFresh0187(binding0187, snapshotText0161)) {
            UnifiedDebugEventStore.record(
                "BUBBLE_FAILED_CARD_RECOVERY_DISCARDED_0187",
                selectedPackage0161,
                "resultado atrasado rejeitado antes de alterar o estado do farol",
            )
            return
        }
        val pickup0161 = recovery0161.fields.pickup
            ?.let(DestinationAddressIdentityPolicy::cleanDisplayAddress)
            .orEmpty()
        val destination0161 = recovery0161.fields.destination
            ?.let(DestinationAddressIdentityPolicy::cleanDisplayAddress)
            .orEmpty()
        if (pickup0161.isBlank() || destination0161.isBlank() ||
            pickup0161.equals(destination0161, ignoreCase = true)
        ) return
        if (selectedPackage0161 !in SelectedRideAppStore.read(applicationContext) ||
            !shouldScanPackage(selectedPackage0161)
        ) return

        val fields0161 = RideFields(pickup = pickup0161, destination = destination0161)
        val signature0161 = DestinationAddressIdentityPolicy.signature(selectedPackage0161, destination0161)
        val screenHash0161 = FarolDisplayStabilityPolicy.stableScreenHash(selectedPackage0161, signature0161)
        val cardChanged0161 = universalActiveAddressSignature != signature0161 || lastSnapshotHash != screenHash0161
        if (cardChanged0161 && (
                universalActiveAddressSignature != null ||
                    currentDistanceKm != null ||
                    currentRadarColor == RadarColor.Green ||
                    currentRadarColor == RadarColor.Red
            )
        ) {
            hardClearUniversalTwoAddress(
                reason = "Novo destino recuperado pela captura automatica; resultado anterior removido.",
                keepWaitingYellow = true,
            )
        }

        universalLastActiveReadAtElapsedMillis0187 = SystemClock.elapsedRealtime()
        universalActiveRidePackageName = selectedPackage0161
        universalActiveAddressSignature = signature0161
        lastSnapshotHash = screenHash0161
        universalAccessibilityOwnsCard = recovery0161.strategy == "modelo_local"
        screenshotFallbackJob127?.cancel()
        screenshotFallbackJob127 = null
        lastAccessibilityAcceptedAtMillis127 = System.currentTimeMillis()

        if (cardChanged0161) {
            universalScreenGeneration += 1L
            universalRouteJob?.cancel()
            universalRouteJob = null
            lastAnalyzedHash = null
            currentDistanceKm = null
            fastFarolStartedAtChecklist13 = System.currentTimeMillis()
            bubblePrefs.edit()
                .putLong("fast_farol_started_at", fastFarolStartedAtChecklist13)
                .putString("fast_farol_last_destination", destination0161)
                .putString("fast_farol_recovery_strategy_0161", recovery0161.strategy)
                .apply()
        } else if (lastAnalyzedHash == screenHash0161 || universalRouteJob?.isActive == true) {
            return
        }

        val settings0161 = currentSettings
        val targets0161 = fastWorkRegionTargetsChecklist13(settings0161)
        if (targets0161.destinations.isEmpty()) {
            rememberBubbleReason("work_region_missing", "Configure Casa ou pelo menos um alfinete com coordenada validada.")
            showOverlay(RadarColor.Default, distanceKm = null)
            return
        }

        val cachedDistances0161 = googleMapsService.cachedDrivingDistancesFromAddressKm(
            originAddress = destination0161,
            destinations = targets0161.destinations,
        )
        val recoverySession0162 = driverCardSessionGate0162.current()
            ?.takeIf { it.packageName == selectedPackage0161 }
            ?: return
        val decisionBinding0161Phase4 = createDecisionBinding0187Phase4(
            packageName0187Phase4 = selectedPackage0161,
            session0187Phase4 = recoverySession0162,
            screenHash0187Phase4 = screenHash0161,
            addressSignature0187Phase4 = signature0161,
        ) ?: return
        if (cachedDistances0161 != null) {
            val cachedResult0161 = decideFastWorkRegionChecklist13(
                snapshotText = snapshotText0161,
                fields = fields0161,
                settings = settings0161,
                targets = targets0161,
                routeDistances = cachedDistances0161,
            )
            bubblePrefs.edit().putString("fast_farol_last_path", "cache_exato_recuperado_0161").apply()
            applyUniversalTwoAddressResult(cachedResult0161, decisionBinding0161Phase4)
            return
        }

        UnifiedDebugEventStore.record(
            "BUBBLE_ROUTE_REQUESTED",
            selectedPackage0161,
            "destino=$destination0161; alvos=${targets0161.destinations.size}; generation=${decisionBinding0161Phase4.screenGeneration}; windowGeneration=${decisionBinding0161Phase4.windowGeneration}; recovery=${recovery0161.strategy}",
        )
        rememberBubbleReason("universal_waiting", "Card recuperado; calculando o ultimo destino.")
        if (currentRadarColor != RadarColor.Default || currentDistanceKm != null) {
            showOverlay(RadarColor.Default, distanceKm = null)
        }
        bubblePrefs.edit().putString("fast_farol_last_path", "rota_google_recuperada_0161").apply()
        universalRouteJob = scope.launch {
            if (!driverCardSessionGate0162.isCurrent(recoverySession0162)) return@launch
            analyzeUniversalTwoAddress(
                snapshotText = snapshotText0161,
                fields = fields0161,
                decisionBinding0187Phase4 = decisionBinding0161Phase4,
            )
        }
    } // failed_card_recovered_route_0_1_161

    private fun createDecisionBinding0187Phase4(
        packageName0187Phase4: String,
        session0187Phase4: DriverCardSession0162,
        screenHash0187Phase4: Int,
        addressSignature0187Phase4: String,
    ): FarolDecisionBinding0187Phase4? {
        if (!driverCardSessionGate0162.isCurrent(session0187Phase4)) return null
        if (session0187Phase4.packageName != packageName0187Phase4) return null
        return FarolDecisionBinding0187Phase4(
            packageName = packageName0187Phase4,
            sessionGeneration = session0187Phase4.generation,
            windowId = session0187Phase4.windowId,
            screenGeneration = universalScreenGeneration,
            windowGeneration = universalWindowGeneration,
            screenHash = screenHash0187Phase4,
            addressSignature = addressSignature0187Phase4,
        )
    }

    private fun isDecisionBindingFresh0187Phase4(
        binding0187Phase4: FarolDecisionBinding0187Phase4,
    ): Boolean {
        val currentSession0187Phase4 = driverCardSessionGate0162.current() ?: return false
        val activePackage0187Phase4 = normalizePackageName(universalActiveRidePackageName)
            ?: normalizePackageName(universalResolvedForegroundPackage())
        val baseFreshStage16 = serviceReady &&
            currentSettings.appEnabled &&
            currentSettings.liveReadingEnabled &&
            activePackage0187Phase4 == normalizePackageName(binding0187Phase4.packageName) &&
            binding0187Phase4.packageName in SelectedRideAppStore.read(applicationContext) &&
            shouldScanPackage(binding0187Phase4.packageName) &&
            FarolDecisionBindingPolicy0187Phase4.isFresh(
                binding = binding0187Phase4,
                currentPackageName = currentSession0187Phase4.packageName,
                currentSessionGeneration = currentSession0187Phase4.generation,
                currentWindowId = currentSession0187Phase4.windowId,
                currentScreenGeneration = universalScreenGeneration,
                currentWindowGeneration = universalWindowGeneration,
                currentScreenHash = lastSnapshotHash,
                currentAddressSignature = universalActiveAddressSignature,
            )
        val pendingForBindingStage16 = stage16TransientEmptyBinding?.let { pendingStage16 ->
            FarolVisibleCardPriorityStage16.pendingMatches(
                pendingStage16,
                binding0187Phase4.toActiveCardBindingStage16(),
            )
        } == true
        return FarolVisibleCardPriorityStage16.routeResultMayPaint(
            bindingFresh = baseFreshStage16,
            transientEmptyPendingForBinding = pendingForBindingStage16,
        )
    }

    private suspend fun analyzeUniversalTwoAddress(
        snapshotText: String,
        fields: RideFields,
        decisionBinding0187Phase4: FarolDecisionBinding0187Phase4,
    ) {
        if (!isDecisionBindingFresh0187Phase4(decisionBinding0187Phase4)) return
        val settingsChecklist13 = currentSettings
        val apiKeyChecklist13 = GoogleMapsApiKeyPolicy.effective(
            settingsChecklist13.googleMapsApiKey,
            BuildConfig.GOOGLE_MAPS_API_KEY,
        )
        if (apiKeyChecklist13.isBlank()) {
            rememberBubbleReason("google_maps_api_required", "Destino confirmado, mas a Chave Google Maps API está ausente.")
            showOverlay(RadarColor.Default, distanceKm = null)
            return
        }
        val targetsChecklist13 = fastWorkRegionTargetsChecklist13(settingsChecklist13)
        if (targetsChecklist13.destinations.isEmpty()) {
            rememberBubbleReason("work_region_missing", "Destino confirmado, mas falta Casa ou alfinete com coordenada validada.")
            showOverlay(RadarColor.Default, distanceKm = null)
            return
        }
        UnifiedDebugEventStore.record("BUBBLE_ROUTE_CALL_START", universalActiveRidePackageName, "destino=${fields.destination.orEmpty()}; alvos=${targetsChecklist13.destinations.size}; generation=${decisionBinding0187Phase4.screenGeneration}; windowGeneration=${decisionBinding0187Phase4.windowGeneration}")
        val routeDistancesChecklist13 = googleMapsService.drivingDistancesFromAddressKm(
            originAddress = fields.destination.orEmpty(),
            destinations = targetsChecklist13.destinations,
            apiKey = apiKeyChecklist13,
        ) // single_exact_route_matrix_checklist_13
        if (!isDecisionBindingFresh0187Phase4(decisionBinding0187Phase4)) {
            UnifiedDebugEventStore.record(
                "BUBBLE_ROUTE_RESULT_DISCARDED_0187_PHASE4",
                decisionBinding0187Phase4.packageName,
                "generation=${decisionBinding0187Phase4.screenGeneration}; windowGeneration=${decisionBinding0187Phase4.windowGeneration}; reason=stale_decision_binding",
            )
            return
        }
        UnifiedDebugEventStore.record("BUBBLE_ROUTE_CALL_END", universalActiveRidePackageName, "distancias=$routeDistancesChecklist13; fresh=true")
        val resultChecklist13 = decideFastWorkRegionChecklist13(
            snapshotText = snapshotText,
            fields = fields,
            settings = settingsChecklist13,
            targets = targetsChecklist13,
            routeDistances = routeDistancesChecklist13,
        )
        applyUniversalTwoAddressResult(resultChecklist13, decisionBinding0187Phase4)
    } // simple_saved_app_route_checklist_13
 // simple_saved_app_route_checklist_13


    private suspend fun applyUniversalTwoAddressResult(
        result: AnalysisResult,
        decisionBinding0187Phase4: FarolDecisionBinding0187Phase4,
    ) {
        if (!isDecisionBindingFresh0187Phase4(decisionBinding0187Phase4)) return
        val colorChecklist13 = when (result.recommendation) {
            Recommendation.GoodRide -> RadarColor.Green
            Recommendation.OutsideRadius -> RadarColor.Red
            Recommendation.InsufficientData -> RadarColor.Orange
        }
        val distanceChecklist13 = result.nearestConfiguredDistanceKm()
        lastAnalyzedHash = decisionBinding0187Phase4.screenHash
        UnifiedDebugEventStore.record(
            "BUBBLE_DECISION_READY",
            universalActiveRidePackageName,
            "recomendacao=${result.recommendation}; cor=$colorChecklist13; distancia=$distanceChecklist13; destino=${result.fields.destination.orEmpty()}; generation=${decisionBinding0187Phase4.screenGeneration}; windowGeneration=${decisionBinding0187Phase4.windowGeneration}; screenHash=${decisionBinding0187Phase4.screenHash}",
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
            decisionBinding0187Phase4.addressSignature,
            result.recommendation.name,
            distanceChecklist13?.let { String.format(Locale.US, "%.3f", it) }.orEmpty(),
        ).joinToString("|")
        if (universalAnalysisDeduper.shouldPersist(persistenceSignatureChecklist13)) {
            scope.launch(Dispatchers.IO) { runCatching { repository.addAnalysis(result) } }
        }
    } // simple_saved_app_apply_checklist_13
 // simple_saved_app_apply_checklist_13


    private fun hardClearUniversalTwoAddress(
        reason: String,
        keepWaitingYellow: Boolean = false,
    ) {
        val passiveClear156 = reason.contains("Pacote passivo", ignoreCase = true) ||
            reason.contains("Aplicativo não selecionado", ignoreCase = true)
        if (passiveClear156 &&
            currentRadarColor == RadarColor.Idle &&
            currentDistanceKm == null &&
            universalActiveAddressSignature == null
        ) {
            return // passive_clear_noop_0_1_156
        }
        UnifiedDebugEventStore.record(
            "BUBBLE_CLEAR_REQUEST",
            universalResolvedForegroundPackage(),
            "reason=$reason; keepWaitingYellow=$keepWaitingYellow; corAtual=$currentRadarColor; distanciaAtual=$currentDistanceKm; assinatura=${universalActiveAddressSignature ?: "nenhuma"}; hash=${lastSnapshotHash ?: 0}",
        )
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
        invalidateFarolAsyncWork0187Phase4(
            reason0187Phase4 = "hard_clear:$reason",
            invalidateSession0187Phase4 = false,
            advanceScreenGeneration0187Phase4 = true,
            advanceWindowGeneration0187Phase4 = true,
            clearReadEvidence0187Phase4 = true,
        )
        if (::stage36RuntimeAuthority.isInitialized) stage36RuntimeAuthority.clearVisualLease(reason)
        universalActiveAddressSignature = null
        lastSnapshotHash = null
        lastAnalyzedHash = null
        analyzing = false
        stage19ActiveWindowId = null
        stage19ActiveBlockId = null
        stage19VisualVerificationPending = false
        currentDistanceKm = null
        lastAccessibilityText = ""
        lastOcrText = ""
        universalAccessibilityOwnsCard = false
        universalLastActiveReadAtElapsedMillis0187 = 0L
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
        return FarolElapsedTimePolicy0187.isWithin(
            nowElapsedMillis = SystemClock.elapsedRealtime(),
            startedElapsedMillis = universalLastActiveReadAtElapsedMillis0187,
            maxAgeMillis = 10_000L,
        )
    } // locked_popup_session_guard_0_1_128

    private fun universalResolvedForegroundPackage(): String? {
        driverCardSessionGate0162.current()?.takeIf { shouldScanPackage(it.packageName) }?.let { return it.packageName }
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

    private fun saveCurrentPlaceFromBubble(type: SavedPlaceType, defaultName: String = "") {
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
            showSavePlacePopup(coordinate, resolved.addressLine, type, defaultName)
        }
    }

    private fun showSavePlacePopup(
        coordinate: Coordinate,
        resolvedAddress: String,
        type: SavedPlaceType,
        initialName: String = "",
    ) {
        hideActionMenu()
        hideSavedPlacePopup()
        val manager = windowManager ?: return
        val fallbackAddress = String.format(
            Locale("pt", "BR"),
            "%.5f, %.5f",
            coordinate.latitude,
            coordinate.longitude,
        )
        val address = SavedPlacePopupPolicy0181.displayAddress(resolvedAddress, fallbackAddress)
        val isAlert = type == SavedPlaceType.ProximityAlert
        val nameInput = EditText(this).apply {
            hint = "Nome"
            setText(initialName)
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            contentDescription = "Nome do local"
        }
        val popup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.argb(250, 32, 32, 32))
                setStroke(dp(1), Color.argb(230, 255, 255, 255))
            }
            setPadding(dp(18), dp(16), dp(18), dp(14))
            addView(TextView(this@LiveRideAccessibilityService).apply {
                text = if (isAlert) "Nome do alerta" else "Nome do local"
                textSize = 20f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(this@LiveRideAccessibilityService).apply {
                text = if (isAlert) {
                    "Digite o nome que sera falado ou salve vazio para usar Alerta de proximidade."
                } else {
                    "Digite um nome ou salve vazio para usar Local salvo."
                }
                textSize = 14f
                setTextColor(Color.LTGRAY)
                setPadding(0, dp(6), 0, dp(12))
            })
            addView(TextView(this@LiveRideAccessibilityService).apply {
                text = "Endereco completo"
                textSize = 13f
                setTextColor(Color.LTGRAY)
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(this@LiveRideAccessibilityService).apply {
                text = address
                textSize = 16f
                setTextColor(Color.WHITE)
                setPadding(0, dp(4), 0, dp(10))
                contentDescription = "Endereco completo: $address"
            })
            addView(nameInput, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ))
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val cancelButton = Button(this).apply {
            text = "Cancelar"
            setOnClickListener { hideSavedPlacePopup() }
        }
        val saveButton = Button(this).apply {
            text = "Salvar"
            setOnClickListener {
                val createdAt = System.currentTimeMillis()
                val place = SavedPlace(
                    id = "place-$createdAt-${coordinate.latitude}-${coordinate.longitude}",
                    name = SavedPlacePopupPolicy0181.savedName(
                        nameInput.text?.toString().orEmpty(),
                        type,
                    ),
                    type = type,
                    address = address,
                    coordinate = coordinate,
                    alertDistanceMeters = if (isAlert) currentSettings.proximityAlertDistanceMeters else null,
                    createdAtMillis = createdAt,
                )
                hideSavedPlacePopup()
                scope.launch {
                    repository.addSavedPlace(place)
                    toast(if (isAlert) "Alerta salvo." else "Local salvo.")
                    recordDiagnostic(
                        stage = if (isAlert) "bubble_save_proximity_alert" else "bubble_save_place",
                        color = currentRadarColor,
                        reason = if (isAlert) {
                            "Alerta de proximidade salvo pela bolinha sem sair da tela atual."
                        } else {
                            "Local salvo pela bolinha sem sair da tela atual."
                        },
                    )
                }
            }
        }
        buttons.addView(
            cancelButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        buttons.addView(
            saveButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        popup.addView(
            buttons,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) },
        )

        val params = WindowManager.LayoutParams(
            dp(336),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        if (runCatching { manager.addView(popup, params) }.isSuccess) {
            savedPlacePopupView = popup
            nameInput.requestFocus()
            popup.post {
                val keyboard = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                keyboard?.showSoftInput(nameInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun hideSavedPlacePopup() {
        val popup = savedPlacePopupView ?: return
        val keyboard = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        keyboard?.hideSoftInputFromWindow(popup.windowToken, 0)
        runCatching { windowManager?.removeView(popup) }
        savedPlacePopupView = null
    }

    private fun collectVisibleTextForAction(): String {
        if (!hasStrictSelectedRootChecklist1()) return "" // strict_manual_tree_gate_checklist_1

        val root = safeRootInActiveWindow0185() ?: return ""
        val lines = mutableListOf<String>()
        collectNodeText(root, lines)
        return lines.map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString("\n")
    }

    private fun strictSelectedRootPackageChecklist1(): String? {
        val selected0162 = SelectedRideAppStore.read(applicationContext)
        return DriverCardEventResolver0162.resolve(
            eventPackageName = null,
            rootPackageName = currentRootPackageName(),
            selectedPackages = selected0162,
            ownPackageName = packageName,
        )?.takeIf(::shouldScanPackage)
    } // immutable_selected_root_0_1_162

    private fun hasStrictSelectedRootChecklist1(): Boolean =
        strictSelectedRootPackageChecklist1() != null // strict_selected_root_helper_checklist_1

    private fun shouldScanCurrentWindow(): Boolean = shouldScanPackage(currentWindowPackageName())

    private fun isOwnAppMainWindowVisible(): Boolean {
        val root = safeRootInActiveWindow0185() ?: return false
        if (safeNodePackageName0185(root) != this.packageName) return false
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
        return runCatching { root.childCount }.getOrDefault(0) > 0 && normalized.length >= 40 && strongMarkers.any { marker -> marker in normalized }
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
    private data class VisibleRootCandidateStage16(
        val root: AccessibilityNodeInfo,
        val evidence: FarolVisibleCardPriorityStage16.WindowEvidence,
    )

    private data class VisibleRootResolutionStage16(
        val selection: FarolVisibleCardPriorityStage16.WindowSelection,
        val rootHandle: FarolRootHandle0187?,
    )

    private fun resolveVisibleAuthorizedRootStage16(
        selectedPackagesStage16: Set<String>,
    ): VisibleRootResolutionStage16 {
        val candidatesStage16 = runCatching { windows }.getOrDefault(emptyList())
            .mapNotNull { windowStage16 ->
                val rootStage16 = runCatching { windowStage16.root }.getOrNull() ?: return@mapNotNull null
                val packageStage16 = safeNodePackageName0185(rootStage16)
                val windowIdStage16 = runCatching { windowStage16.id }.getOrDefault(-1)
                val layerStage16 = runCatching { windowStage16.layer }.getOrDefault(0)
                val typeStage16 = runCatching { windowStage16.type }.getOrDefault(0)
                VisibleRootCandidateStage16(
                    root = rootStage16,
                    evidence = FarolVisibleCardPriorityStage16.WindowEvidence(
                        windowId = windowIdStage16,
                        packageName = packageStage16,
                        layer = layerStage16,
                        kind = when (typeStage16) {
                            AccessibilityWindowInfo.TYPE_APPLICATION -> FarolVisibleCardPriorityStage16.WindowKind.APPLICATION
                            AccessibilityWindowInfo.TYPE_SYSTEM -> FarolVisibleCardPriorityStage16.WindowKind.SYSTEM
                            AccessibilityWindowInfo.TYPE_INPUT_METHOD -> FarolVisibleCardPriorityStage16.WindowKind.INPUT_METHOD
                            AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> FarolVisibleCardPriorityStage16.WindowKind.ACCESSIBILITY_OVERLAY
                            else -> FarolVisibleCardPriorityStage16.WindowKind.OTHER
                        },
                        hasRoot = true,
                    ),
                )
            }
        val selectionStage16 = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(
            windows = candidatesStage16.map { it.evidence },
            selectedPackages = selectedPackagesStage16,
        )
        val authorityStage16 = selectionStage16.authority
        val candidateStage16 = authorityStage16?.let { authority ->
            candidatesStage16.firstOrNull { candidate ->
                candidate.evidence.windowId == authority.windowId &&
                    candidate.evidence.layer == authority.layer &&
                    normalizePackageName(candidate.evidence.packageName) == normalizePackageName(authority.packageName)
            }
        }
        return VisibleRootResolutionStage16(
            selection = selectionStage16,
            rootHandle = candidateStage16?.let { candidate ->
                FarolRootHandle0187(candidate.root, normalizePackageName(candidate.evidence.packageName), candidate.evidence.windowId)
            },
        )
    }

    private fun toStage16BlockEvidence(blockStage16: FarolCardBlock0188) =
        FarolVisibleCardPriorityStage16.BlockEvidence(
            id = blockStage16.id,
            parentId = blockStage16.parentId,
            packageName = blockStage16.packageName,
            windowId = blockStage16.windowId,
            windowLayer = blockStage16.windowLayer,
            depth = blockStage16.depth,
            text = blockStage16.text,
            source = blockStage16.source.name,
            left = blockStage16.left,
            top = blockStage16.top,
            right = blockStage16.right,
            bottom = blockStage16.bottom,
            syntheticRoot = blockStage16.syntheticRoot,
        )

    private fun activeCardBindingStage16(packageNameStage16: String): FarolVisibleCardPriorityStage16.ActiveCardBinding? {
        val sessionStage16 = driverCardSessionGate0162.current()
            ?.takeIf { normalizePackageName(it.packageName) == normalizePackageName(packageNameStage16) }
            ?: return null
        val signatureStage16 = universalActiveAddressSignature?.takeIf(String::isNotBlank) ?: return null
        val screenHashStage16 = lastSnapshotHash ?: return null
        return FarolVisibleCardPriorityStage16.ActiveCardBinding(
            packageName = packageNameStage16,
            sessionGeneration = sessionStage16.generation,
            windowId = sessionStage16.windowId,
            screenGeneration = universalScreenGeneration,
            windowGeneration = universalWindowGeneration,
            screenHash = screenHashStage16,
            addressSignature = signatureStage16,
        )
    }

    private fun FarolDecisionBinding0187Phase4.toActiveCardBindingStage16() =
        FarolVisibleCardPriorityStage16.ActiveCardBinding(
            packageName = packageName,
            sessionGeneration = sessionGeneration,
            windowId = windowId,
            screenGeneration = screenGeneration,
            windowGeneration = windowGeneration,
            screenHash = screenHash,
            addressSignature = addressSignature,
        )

    private fun confirmTransientEmptyVisualStage16(
        active: FarolVisibleCardPriorityStage16.ActiveCardBinding,
        savedPackages: Set<String>,
    ): FarolVisibleCardPriorityStage16.EmptyVisualConfirmation {
        val visibleStage16 = resolveVisibleAuthorizedRootStage16(savedPackages)
        val selectionStage16 = visibleStage16.selection
        val authorityStage16 = selectionStage16.authority
        if (selectionStage16.outcome != FarolVisibleCardPriorityStage16.WindowSelectionOutcome.AUTHORIZED_SELECTED_WINDOW ||
            authorityStage16 == null ||
            normalizePackageName(authorityStage16.packageName) != normalizePackageName(active.packageName) ||
            authorityStage16.windowId != active.windowId
        ) {
            return FarolVisibleCardPriorityStage16.classifyEmptyVisualConfirmation(active, selectionStage16, null)
        }
        val blocksStage16 = FarolLatencyProbeStage9.measureBlocks(
            stage = "STAGE16_TRANSIENT_CONFIRM_BLOCKS",
            source = "Accessibility",
        ) {
            collectAccessibilityCardBlocks0188(
                expectedPackage0188 = active.packageName,
                expectedWindowId0188 = active.windowId,
            )
        }
        if (blocksStage16.isEmpty()) return FarolVisibleCardPriorityStage16.EmptyVisualConfirmation.AMBIGUOUS
        val decisionStage16 = FarolLatencyProbeStage9.measureValue(
            stage = "STAGE16_TRANSIENT_CONFIRM_GATE",
            source = "Accessibility",
        ) {
            FarolRealDeviceGate0188.evaluate(
                selectedPackageName = active.packageName,
                selectedPackages = savedPackages,
                blocks = blocksStage16,
            )
        }
        val confirmedStage16 = decisionStage16.authorization?.let { authorizationStage16 ->
            active.copy(
                windowId = authorizationStage16.windowId,
                screenHash = authorizationStage16.screenHash,
                addressSignature = authorizationStage16.addressSignature,
            )
        }
        return FarolVisibleCardPriorityStage16.classifyEmptyVisualConfirmation(
            active = active,
            selection = selectionStage16,
            confirmedCard = confirmedStage16,
        )
    }

    private fun rebindAcceptedGateCacheStage16(evaluationStage16: SimpleSavedAppFarolPolicy.Evaluation) {
        val cachedStage16 = stage16AcceptedGateSnapshot ?: return
        val authorizationStage16 = stage16AcceptedGateAuthorization ?: return
        val sessionStage16 = driverCardSessionGate0162.current() ?: return
        if (authorizationStage16.screenHash != evaluationStage16.screenHash ||
            authorizationStage16.addressSignature != evaluationStage16.addressSignature ||
            normalizePackageName(authorizationStage16.packageName) != normalizePackageName(evaluationStage16.packageName)
        ) return
        stage16AcceptedGateSnapshot = cachedStage16.copy(
            sessionGeneration = sessionStage16.generation,
            expectedWindowId = sessionStage16.windowId,
            screenGeneration = universalScreenGeneration,
            windowGeneration = universalWindowGeneration,
        )
    }

    private fun clearStage16VisualProof() {
        stage16TransientEmptyBinding = null
        stage16AcceptedGateSnapshot = null
        stage16AcceptedGateAuthorization = null
    }

    private fun captureRootHandle0187(): FarolRootHandle0187? {
        val root0187 = runCatching { rootInActiveWindow }.getOrNull() ?: return null
        val package0187 = safeNodePackageName0185(root0187)
        val window0187 = runCatching { root0187.windowId }.getOrNull()?.takeIf { it >= 0 }
        return FarolRootHandle0187(root0187, package0187, window0187)
    }

    private fun safeRootInActiveWindow0185(): AccessibilityNodeInfo? =
        runCatching { rootInActiveWindow }.getOrNull()

    private fun safeRootWindowId0185(): Int? =
        safeRootInActiveWindow0185()?.let { root0185 ->
            runCatching { root0185.windowId }.getOrNull()
        }

    private fun safeNodePackageName0185(node0185: AccessibilityNodeInfo?): String? =
        normalizePackageName(runCatching { node0185?.packageName?.toString() }.getOrNull())

    private fun currentRootPackageName(): String? =
        safeNodePackageName0185(safeRootInActiveWindow0185())

    private fun shouldScanPackage(packageName: String?): Boolean {
        if (!serviceReady || !WorkModePolicy0162.isEnabled(currentSettings)) return false
        val normalized = normalizePackageName(packageName) ?: return false
        if (!DriverAppPackagePolicy0162.isEligible(normalized, this.packageName)) return false
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
    ) {
        FarolFlightRecorder0163.recordDiagnostic(
            stage = stage,
            packageName = universalResolvedForegroundPackage(),
            color = color?.name,
            reason = reason,
            text = text,
            fields = fields,
            result = result,
            error = error,
        )
    }

    private fun traceEvent(message: String) {
        FarolFlightRecorder0163.record(
            stage = "TRACE",
            packageName = universalResolvedForegroundPackage(),
            details = message,
        )
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

    private fun showOverlay(color: RadarColor, distanceKm: Double? = null, forcePhysicalCommitStage43: Boolean = false) {
        if (!serviceReady) return
        val readingEnabledStage40 = ::stage36RuntimeAuthority.isInitialized &&
            stage36RuntimeAuthority.snapshot().enabled && WorkModePolicy0162.isEnabled(currentSettings)
        val decisionStage40 = FarolVisualStateAuthorityStage40.decide(readingEnabledStage40, color.name, distanceKm)
        val effectiveColorStage40 = when (decisionStage40.state) {
            FarolVisualStateAuthorityStage40.PublicState.GRAY -> RadarColor.Idle
            FarolVisualStateAuthorityStage40.PublicState.YELLOW -> RadarColor.Default
            FarolVisualStateAuthorityStage40.PublicState.GREEN -> RadarColor.Green
            FarolVisualStateAuthorityStage40.PublicState.RED -> RadarColor.Red
        }
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S40_VISUAL_AUTHORITY_DECISION", universalResolvedForegroundPackage(),
            details = "requested=$color; requestedDistance=${distanceKm ?: -1.0}; effective=$effectiveColorStage40; effectiveDistance=${decisionStage40.distanceKm ?: -1.0}; reading=$readingEnabledStage40; reason=${decisionStage40.reason}",
        )
        renderOverlayStage40(effectiveColorStage40, decisionStage40.distanceKm, forcePhysicalCommitStage43)
    }

    private fun renderOverlayStage40(color: RadarColor, distanceKm: Double? = null, forcePhysicalCommitStage43: Boolean = false) {
        if (!serviceReady) return
        val manager = windowManager ?: return
        if (color != currentRadarColor || distanceKm != currentDistanceKm) {
            stage21SelfEventSuppressionUntilNs = SystemClock.elapsedRealtimeNanos() + 250_000_000L
        }
        val stage20Origin = if (color == RadarColor.Green || color == RadarColor.Red) {
            FarolForensicTraceStage20.callSite(Thread.currentThread().stackTrace)
        } else "showOverlay"
        val stage20Binding = currentStage20BindingSnapshot()
        val nextTextChecklist15 = formatBubbleDistanceKm(distanceKm)
        val existingViewChecklist15 = overlayView
        if (!forcePhysicalCommitStage43 && existingViewChecklist15 != null && currentRadarColor == color &&
            existingViewChecklist15.text.toString() == nextTextChecklist15
        ) {
            FarolForensicTraceStage20.overlayIdempotentSkipped(stage20ExpectedPaintToken, SystemClock.elapsedRealtimeNanos(), color.toString(), distanceKm, stage20Binding, stage20Origin)
            currentDistanceKm = distanceKm
            return // overlay_idempotent_same_value_checklist_15
        }
        FarolForensicTraceStage20.overlayRequested(stage20ExpectedPaintToken, SystemClock.elapsedRealtimeNanos(), color.toString(), distanceKm, stage20Binding, stage20Origin)
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_OVERLAY_RENDER_REQUEST", universalResolvedForegroundPackage(),
            traceId = stage20ExpectedPaintToken?.traceId, operationId = stage20ExpectedPaintToken?.operationId,
            details = "requestedColor=$color; requestedDistance=${distanceKm ?: -1.0}; currentColor=$currentRadarColor; currentDistance=${currentDistanceKm ?: -1.0}; origin=$stage20Origin; binding=${stage20Binding.stableKey()}",
        )
        FarolFlightRecorder0163.record(
            stage = "OVERLAY_RENDER_REQUEST",
            packageName = universalResolvedForegroundPackage(),
            details = "requestedColor=$color; requestedDistance=$distanceKm; currentColor=$currentRadarColor; currentDistance=$currentDistanceKm; ready=$serviceReady",
        )
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
        FarolFlightRecorder0163.record(
            stage = "OVERLAY_RENDER_APPLIED",
            packageName = universalResolvedForegroundPackage(),
            details = "color=$color; distance=$distanceKm; text=${view.text}; viewCreated=${existingViewChecklist15 == null}",
        )
        FarolForensicTraceStage20.overlayApplied(stage20ExpectedPaintToken, SystemClock.elapsedRealtimeNanos(), color.toString(), distanceKm, currentStage20BindingSnapshot(), stage20Origin)
        if (forcePhysicalCommitStage43 && color == RadarColor.Idle && distanceKm == null) {
            stage43OffRenderAppliedSerial += 1L
        }
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_OVERLAY_RENDER_APPLIED", universalResolvedForegroundPackage(),
            traceId = stage20ExpectedPaintToken?.traceId, operationId = stage20ExpectedPaintToken?.operationId,
            details = "color=$color; distance=${distanceKm ?: -1.0}; text=${view.text}; viewCreated=${existingViewChecklist15 == null}; x=${overlayParams?.x ?: -1}; y=${overlayParams?.y ?: -1}",
        )
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
        hideShortcutModulePopup0181()
        hideSavedPlacePopup()
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
        val resolved0179 = shortcutGridStore0179.readResolved()
        val labels = resolved0179.joinToString("|") { it.spec.label }
        bubblePrefs.edit()
            .putBoolean(KEY_RUNTIME_SHORTCUTS_OPEN, visible)
            .putInt(KEY_RUNTIME_SHORTCUT_COUNT, if (visible) resolved0179.size else 0)
            .putString(KEY_RUNTIME_SHORTCUT_LABELS, if (visible) labels else "")
            .apply()
    }

    private fun closeResourceShortcuts() {
        shortcutOverlayController.hideShortcuts()
        persistResourceShortcutState()
    }

    private fun toggleResourceShortcuts() {
        val params = overlayParams ?: return
        val shortcuts0184 = shortcutGridStore0179.readResolved()
        if (shortcuts0184.isEmpty()) {
            launchShortcutActivity0176(
                shortcutId = "empty_action_grid_0184",
                intent = Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_OPEN_TAB, TAB_CONFIG)
                    .putExtra(EXTRA_HOME_LAUNCH_MODE_0186, HomeLaunchPolicy0186.MODE_COLLAPSED),
                failureMessage = "Não consegui abrir a Home para escolher atalhos.",
            )
            toast("Escolha na Home as ações que deseja adicionar à grade.")
            return
        }
        shortcutOverlayController.toggleShortcuts(
            anchor = params,
            shortcuts = shortcuts0184,
            onShortcut = ::executeShortcutQuickTap0180,
            onShortcutLongPress = ::executeShortcutHold0180,
        )
        persistResourceShortcutState()
        Unit /* diagnostics_off_checklist_4 */
        Unit /* diagnostics_off_checklist_4 */
    }

    private fun executeShortcutQuickTap0180(entry0180: ResolvedShortcutGridEntry0179) {
        executeShortcutModule(entry0180.spec)
    }

    private fun executeShortcutHold0180(entry0180: ResolvedShortcutGridEntry0179) {
        UnifiedDebugEventStore.record(
            "SHORTCUT_HOLD_0186",
            universalResolvedForegroundPackage(),
            "entry=${entry0180.entryId}; id=${entry0180.shortcutId}; type=${entry0180.holdActionType0186.name}",
        )
        when (entry0180.holdActionType0186) {
            ShortcutHoldActionType0186.OPEN_MODULE -> {
                val moduleSpec0186 = ShortcutActionCatalog0184.moduleSpecForAction(entry0180.shortcutId)
                if (moduleSpec0186 != null) openShortcutModule0171(moduleSpec0186)
            }
            ShortcutHoldActionType0186.SAFE_ACTION -> entry0180.holdShortcutSpec0186?.let(::executeShortcutModule)
            ShortcutHoldActionType0186.NONE -> Unit
        }
    }

    private fun dispatchShortcutGesture0180(
        entry0180: ResolvedShortcutGridEntry0179,
        action0180: ShortcutGestureAction0180,
        gesture0180: String,
    ) {
        UnifiedDebugEventStore.record(
            "SHORTCUT_GESTURE_0180",
            universalResolvedForegroundPackage(),
            "entry=${entry0180.entryId}; id=${entry0180.shortcutId}; gesture=$gesture0180; action=${action0180.name}",
        )
        when (action0180) {
            ShortcutGestureAction0180.PRIMARY_ACTION -> dispatchShortcutPrimaryDirect0182(entry0180.spec)
            ShortcutGestureAction0180.OPEN_MODULE -> openShortcutModule0171(entry0180.spec)
            ShortcutGestureAction0180.NONE -> Unit
        }
    }

    private fun dispatchShortcutPrimaryDirect0182(spec: BubbleShortcutSpec) {
        when (spec.id) {
            "saved_places" -> saveCurrentPlaceFromBubble(SavedPlaceType.Place)
            "alerts" -> saveCurrentPlaceFromBubble(SavedPlaceType.ProximityAlert)
            else -> executeShortcutModule(spec)
        }
    }

    private fun showShortcutActionMenu0183(spec: BubbleShortcutSpec) {
        hideActionMenu()
        hideSavedPlacePopup()
        hideShortcutModulePopup0181()
        val manager = windowManager ?: return
        val popup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.argb(250, 32, 32, 32))
                setStroke(dp(1), Color.argb(230, 255, 255, 255))
            }
            setPadding(dp(18), dp(16), dp(18), dp(14))
            addView(TextView(this@LiveRideAccessibilityService).apply {
                text = "${spec.emoji}  ${spec.displayLabel}"
                textSize = 20f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(this@LiveRideAccessibilityService).apply {
                text = "Escolha o que deseja fazer agora."
                textSize = 14f
                setTextColor(Color.LTGRAY)
                setPadding(0, dp(8), 0, dp(10))
            })
        }
        popup.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                hideShortcutModulePopup0181()
                true
            } else {
                false
            }
        }

        ShortcutContextMenuPolicy0183.quickActionLabel(spec.id, spec.doubleTapAction)?.let { label ->
            popup.addView(Button(this).apply {
                text = label
                setOnClickListener {
                    hideShortcutModulePopup0181()
                    executeShortcutContextAction0183(spec)
                }
            })
        }

        if (spec.id == "clear_clipboard") {
            popup.addView(Button(this).apply {
                text = "Limpar área de transferência"
                setOnClickListener {
                    hideShortcutModulePopup0181()
                    clearClipboardFromBubble()
                }
            })
            popup.addView(Button(this).apply {
                text = "Limpar cache do Rota Certa"
                setOnClickListener {
                    hideShortcutModulePopup0181()
                    clearOwnCache0183()
                }
            })
            popup.addView(Button(this).apply {
                text = "Abrir módulo Limpar"
                setOnClickListener {
                    hideShortcutModulePopup0181()
                    openShortcutModule0171(spec)
                }
            })
        } else {
            popup.addView(Button(this).apply {
                text = ShortcutContextMenuPolicy0183.primaryActionLabel(spec.id)
                setOnClickListener {
                    hideShortcutModulePopup0181()
                    executeShortcutModule(spec)
                }
            })
        }

        popup.addView(Button(this).apply {
            text = "Fechar"
            setOnClickListener { hideShortcutModulePopup0181() }
        })

        val metrics = resources.displayMetrics
        val params = WindowManager.LayoutParams(
            dp(336).coerceAtMost(metrics.widthPixels - dp(24)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
        }
        if (runCatching { manager.addView(popup, params) }.isSuccess) {
            shortcutModulePopupView0181 = popup
            UnifiedDebugEventStore.record(
                "SHORTCUT_CONTEXT_MENU_0183",
                universalResolvedForegroundPackage(),
                "id=${spec.id}",
            )
        }
    }

    private fun executeShortcutContextAction0183(spec: BubbleShortcutSpec) {
        when (spec.id) {
            "alerts" -> saveCurrentPlaceFromBubble(SavedPlaceType.ProximityAlert)
            "saved_places" -> saveCurrentPlaceFromBubble(SavedPlaceType.Place)
            else -> executeShortcutDoubleTap(spec)
        }
    }

    private fun clearOwnCache0183() {
        scope.launch(Dispatchers.IO) {
            val cleared = runCatching {
                cacheDir.listFiles()?.forEach { file -> file.deleteRecursively() }
                true
            }.getOrDefault(false)
            withContext(Dispatchers.Main.immediate) {
                toast(
                    if (cleared) "Cache do Rota Certa limpo" else "Não foi possível limpar o cache",
                )
            }
        }
    }

    private fun hideShortcutModulePopup0181() {
        val popup = shortcutModulePopupView0181 ?: return
        runCatching { windowManager?.removeView(popup) }
        shortcutModulePopupView0181 = null
    }

    private fun openHomeCollapsed0186() {
        launchShortcutActivity0176(
            shortcutId = "home_collapsed_0186",
            intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_OPEN_TAB, TAB_CONFIG)
                .putExtra(EXTRA_HOME_LAUNCH_MODE_0186, HomeLaunchPolicy0186.MODE_COLLAPSED),
            failureMessage = "Não foi possível abrir a Home.",
        )
    }

    private fun openShortcutModule0171(spec: BubbleShortcutSpec) {
        val intent0171 = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN_TAB, spec.targetTab ?: TAB_CONFIG)
            .putExtra(EXTRA_HOME_LAUNCH_MODE_0186, HomeLaunchPolicy0186.MODE_MODULE)
            .putExtra(EXTRA_OPEN_SHORTCUT_MODULE_0171, spec.id)
        spec.targetGroup?.let { intent0171.putExtra(EXTRA_OPEN_BUBBLE_GROUP, it) }
        launchShortcutActivity0176(
            shortcutId = spec.id,
            intent = intent0171,
            failureMessage = "Não foi possível abrir o módulo ${spec.displayLabel}.",
        )
    }

    private fun openShortcutCustomization0179(entryId0180: String? = null) {
        val intent0179 = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN_TAB, TAB_CONFIG)
            .putExtra(EXTRA_OPEN_SHORTCUT_CUSTOMIZATION_0179, true)
        entryId0180?.let { intent0179.putExtra(EXTRA_EDIT_SHORTCUT_ENTRY_ID_0180, it) }
        launchShortcutActivity0176(
            shortcutId = "shortcut_customization_0179",
            intent = intent0179,
            failureMessage = "Não foi possível abrir a Central de atalhos.",
        )
    }

    private fun executeShortcutDoubleTap(spec: BubbleShortcutSpec) {
        when (spec.doubleTapAction) {
            BubbleShortcutQuickAction.CopyAllVisibleText -> copyAllVisibleTextFromBubble138()
            BubbleShortcutQuickAction.CreateQuickReply -> openQuickRepliesFromBubble(createNew = true)
            BubbleShortcutQuickAction.CreateRadarAtCurrentLocation -> createManualRadarFromBubble138()
            BubbleShortcutQuickAction.CreateNamedAlertAtCurrentLocation -> openNamedPlaceShortcut138(SavedPlaceType.ProximityAlert)
            BubbleShortcutQuickAction.CreateNamedSavedPlaceAtCurrentLocation -> openNamedPlaceShortcut138(SavedPlaceType.Place)
            BubbleShortcutQuickAction.DefineDestinationAtCurrentLocation -> openDestinationConfirmationFromBubble138()
            BubbleShortcutQuickAction.CaptureCurrentAppAndScreen -> captureCurrentAppAndScreen138()
            BubbleShortcutQuickAction.OpenPrimaryQuickLink -> openPrimaryQuickLink0172()
            BubbleShortcutQuickAction.ClearApplicationCache -> clearApplicationCache0172()
            null -> executeShortcutModule(spec)
        }
    }

    private fun launchShortcutActivity0176(
        shortcutId: String,
        intent: Intent,
        failureMessage: String,
        failureAction: (() -> Unit)? = null,
    ): Boolean {
        val launchIntent0176 = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val target0176 = launchIntent0176.component?.className
            ?: launchIntent0176.action
            ?: "unknown"
        val requestCode0176 = ShortcutActivityLaunchPolicy0176.requestCode(
            shortcutActivityLaunchRequestCode0176.incrementAndGet(),
        )
        val result0176 = runCatching {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                ShortcutActivityLaunchPolicy0176.usePendingIntent(Build.VERSION.SDK_INT)
            ) {
                val creatorOptions0176 = ActivityOptions.makeBasic()
                    .setPendingIntentCreatorBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                    )
                    .toBundle()
                val pendingIntent0176 = PendingIntent.getActivity(
                    this,
                    requestCode0176,
                    launchIntent0176,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    creatorOptions0176,
                )
                val senderOptions0176 = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                    )
                    .toBundle()
                pendingIntent0176.send(
                    this,
                    0,
                    null,
                    null,
                    null,
                    null,
                    senderOptions0176,
                )
            } else {
                startActivity(launchIntent0176)
            }
        }
        if (result0176.isSuccess) {
            UnifiedDebugEventStore.record(
                ShortcutActivityLaunchPolicy0176.DISPATCHED_STAGE,
                universalResolvedForegroundPackage(),
                "id=$shortcutId; target=$target0176",
            )
            shortcutOverlayController.hideAll()
            persistResourceShortcutState()
            return true
        }
        val error0176 = result0176.exceptionOrNull()
        UnifiedDebugEventStore.record(
            ShortcutActivityLaunchPolicy0176.FAILED_STAGE,
            universalResolvedForegroundPackage(),
            "id=$shortcutId; type=${error0176?.javaClass?.simpleName.orEmpty()}",
        )
        if (failureAction != null) failureAction() else toast(failureMessage)
        return false
    }

    private fun openBackupFileAction0184(create: Boolean) {
        val intent0184 = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN_TAB, TAB_CONFIG)
            .putExtra(EXTRA_OPEN_SHORTCUT_MODULE_0171, "backup")
            .putExtra(if (create) EXTRA_CREATE_BACKUP_0184 else EXTRA_RESTORE_BACKUP_0184, true)
        launchShortcutActivity0176(
            shortcutId = if (create) "action_create_backup" else "action_restore_backup",
            intent = intent0184,
            failureMessage = if (create) "Não consegui iniciar o backup." else "Não consegui abrir a restauração.",
        )
    }

    private fun openWhatsAppAppFromBubble0184() {
        val launch = packageManager.getLaunchIntentForPackage("com.whatsapp")
            ?: packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
        if (launch == null) {
            toast("WhatsApp não encontrado")
            return
        }
        launchShortcutActivity0176(
            shortcutId = "action_open_whatsapp_app",
            intent = launch,
            failureMessage = "Não consegui abrir o WhatsApp.",
        )
    }

    private fun openWorkTracking0184() {
        launchShortcutActivity0176(
            shortcutId = "action_open_work_tracking",
            intent = Intent(this, WorkTrackingActivity::class.java),
            failureMessage = "Não consegui abrir o rastreamento de trabalho.",
        )
    }

    private fun setWorkTracking0184(start: Boolean) {
        val intent0184 = Intent(this, WorkTrackingService::class.java).setAction(
            if (start) WorkTrackingService.ACTION_START else WorkTrackingService.ACTION_STOP,
        )
        runCatching {
            if (start) ContextCompat.startForegroundService(this, intent0184) else startService(intent0184)
        }.onSuccess {
            toast(if (start) "Rastreamento iniciado" else "Rastreamento encerrado")
        }.onFailure {
            toast(if (start) "Autorize a localização para iniciar o rastreamento." else "Não foi possível parar o rastreamento.")
        }
    }

    private fun executeShortcutModule(spec: BubbleShortcutSpec) {
        Unit /* diagnostics_off_checklist_4 */
        Unit /* diagnostics_off_checklist_4 */
        when (spec.id) {
            "action_copy_visible_text" -> { copyAllVisibleTextFromBubble138(); return }
            "action_clear_cache" -> { clearOwnCache0183(); return }
            "action_create_alert_here" -> { saveCurrentPlaceFromBubble(SavedPlaceType.ProximityAlert); return }
            "action_save_place_here" -> { saveCurrentPlaceFromBubble(SavedPlaceType.Place); return }
            "action_create_radar_here" -> { createManualRadarFromBubble138(); return }
            "action_define_destination_here" -> { openDestinationConfirmationFromBubble138(); return }
            "action_create_backup" -> { openBackupFileAction0184(create = true); return }
            "action_restore_backup" -> { openBackupFileAction0184(create = false); return }
            "action_create_quick_reply" -> { openQuickRepliesFromBubble(createNew = true); return }
            "action_open_primary_link" -> { openPrimaryQuickLink0172(); return }
            "action_open_whatsapp_app" -> { openWhatsAppAppFromBubble0184(); return }
            "work_tracking", "action_open_work_tracking" -> { openWorkTracking0184(); return }
            "action_start_work_tracking" -> { setWorkTracking0184(start = true); return }
            "action_stop_work_tracking" -> { setWorkTracking0184(start = false); return }
        }
        when (spec.action) {
            BubbleShortcutAction.CopyTripConfirmation -> copyTripConfirmationFromBubbleChecklist8() // trip_confirmation_action_checklist_8
            BubbleShortcutAction.CopyPassengerValue -> copyPassengerValue159()
            BubbleShortcutAction.OpenFinance -> openFinance159()
            BubbleShortcutAction.OpenQuickReplies -> openQuickRepliesFromBubble(createNew = false) // quick_reply_action_checklist_3
            BubbleShortcutAction.OpenQuickLinks -> openQuickLinks0172()
            BubbleShortcutAction.OpenTextCorrection -> openTextCorrection0186()
            BubbleShortcutAction.OpenMessageTemplates -> openMessageTemplates0172()
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
            -> openHomeCollapsed0186()

            BubbleShortcutAction.OpenScreenWhatsApp -> capturePhoneAndOpenWhatsApp118()
            BubbleShortcutAction.ClearClipboard -> clearClipboardFromBubble()
            BubbleShortcutAction.ExportDiagnostic -> exportDiagnosticFromBubble()
            BubbleShortcutAction.StopApplication -> stopApplicationFromBubble()
            BubbleShortcutAction.CaptureCurrentAppAndScreen -> captureCurrentAppAndScreen138()
            BubbleShortcutAction.SaveScreenPrint -> saveScreenPrintStage32()
            BubbleShortcutAction.OpenAuthorizedAppsAndCards -> openAuthorizedAppsAndCards146()
            BubbleShortcutAction.CreateAlert -> saveCurrentPlaceFromBubble(SavedPlaceType.ProximityAlert, requireNotNull(spec.defaultName))
            BubbleShortcutAction.CreateSavedPlace -> saveCurrentPlaceFromBubble(SavedPlaceType.Place, requireNotNull(spec.defaultName))
            BubbleShortcutAction.ToggleReading -> toggleLiveReadingFromBubble()
        }
    }


// shortcut_long_press_customization_0_1_171
// capture_teaches_app_and_triggers_farol_checklist_13
 // capture_teaches_app_and_triggers_farol_checklist_13


    // manual_card_capture_complete_checklist_12

    private fun openAuthorizedAppsAndCards146() {
        launchShortcutActivity0176(
            shortcutId = "manual_capture",
            intent = Intent(this, InstalledRideAppPickerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            failureMessage = "Não consegui abrir os aplicativos autorizados.",
        )
    }

    private fun saveScreenPrintStage32() {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        if (!printInProgressStage32.compareAndSet(false, true)) {
            toast("Um Print já está em andamento.")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            printInProgressStage32.set(false)
            toast("Print indisponível nesta versão do Android.")
            return
        }
        val caseIdStage32 = FarolForensicCardBlackBoxStage32.currentCaseId()
        val ownerStage32 = FarolForensicCardBlackBoxStage32.currentOwnerToken()
        FarolFlightRecorder0163.record("PRINT_REQUESTED_STAGE32", currentWindowPackageName(), "case=${caseIdStage32 ?: "none"}; owner=${ownerStage32 ?: "UNKNOWN"}")
        if (screenshotInProgress.get()) {
            printInProgressStage32.set(false)
            FarolFlightRecorder0163.record("PRINT_FAILED_STAGE32", currentWindowPackageName(), "reason=shared_screenshot_busy; case=${caseIdStage32 ?: "none"}")
            toast("O leitor está capturando a tela agora. Tente o Print novamente.")
            return
        }
        val rateStage32 = stage32ScreenshotRateGate.requestExplicit(SystemClock.uptimeMillis(), stage32SemanticGate.snapshot().generation)
        if (!rateStage32.startNow || !screenshotInProgress.compareAndSet(false, true)) {
            printInProgressStage32.set(false)
            FarolFlightRecorder0163.record("PRINT_FAILED_STAGE32", currentWindowPackageName(), "reason=${rateStage32.reason}; case=${caseIdStage32 ?: "none"}")
            toast("Print muito próximo de outra captura. Tente novamente.")
            return
        }
        runCatching {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    scope.launch(Dispatchers.IO) {
                        var bitmapStage32: Bitmap? = null
                        try {
                            bitmapStage32 = screenshot.toSoftwareBitmap() ?: error("bitmap indisponível")
                            val savedStage32 = FarolPrintStoreStage32.savePng(applicationContext, bitmapStage32!!, caseIdStage32, ownerStage32).getOrThrow()
                            stage32ScreenshotRateGate.complete(stage32SemanticGate.snapshot().generation, true)
                            FarolFlightRecorder0163.record("PRINT_SUCCESS_STAGE32", currentWindowPackageName(), "case=${caseIdStage32 ?: "none"}; name=${savedStage32.displayName}; uri=${savedStage32.uri}; hash=${savedStage32.contentHash}")
                            withContext(Dispatchers.Main.immediate) { toast("Print salvo na Galeria / Pictures/Rota Certa") }
                        } catch (errorStage32: Throwable) {
                            stage32ScreenshotRateGate.complete(stage32SemanticGate.snapshot().generation, false)
                            FarolFlightRecorder0163.record("PRINT_FAILED_STAGE32", currentWindowPackageName(), "case=${caseIdStage32 ?: "none"}; type=${errorStage32::class.java.simpleName}")
                            withContext(Dispatchers.Main.immediate) { toast("Não foi possível salvar o Print.") }
                        } finally {
                            bitmapStage32?.takeUnless(Bitmap::isRecycled)?.recycle()
                            screenshotInProgress.set(false)
                            printInProgressStage32.set(false)
                        }
                    }
                }
                override fun onFailure(errorCode: Int) {
                    screenshotInProgress.set(false); printInProgressStage32.set(false)
                    if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                        stage32ScreenshotRateGate.markIntervalShort(stage32SemanticGate.snapshot().generation)
                    } else stage32ScreenshotRateGate.complete(stage32SemanticGate.snapshot().generation, false)
                    FarolFlightRecorder0163.record("PRINT_FAILED_STAGE32", currentWindowPackageName(), "case=${caseIdStage32 ?: "none"}; errorCode=$errorCode")
                    toast("Não foi possível tirar o Print (código $errorCode).")
                }
            })
        }.onFailure { errorStage32 ->
            screenshotInProgress.set(false); printInProgressStage32.set(false)
            stage32ScreenshotRateGate.complete(stage32SemanticGate.snapshot().generation, false)
            FarolFlightRecorder0163.record("PRINT_FAILED_STAGE32", currentWindowPackageName(), "case=${caseIdStage32 ?: "none"}; type=${errorStage32::class.java.simpleName}")
            toast("Não foi possível tirar o Print.")
        }
    }

    private fun captureCurrentAppAndScreen138() {
        shortcutOverlayController.hideAll()
        val stage32UserCaseId = FarolForensicCardBlackBoxStage32.userMark(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), currentWindowPackageName(), stage32SemanticGate.snapshot(),
        )
        FarolFlightRecorder0163.record(
            stage = "USER_MARKED_CASE_STAGE32", packageName = currentWindowPackageName(), details = "case=$stage32UserCaseId; source=Capturar",
        )
        FarolForensicCaseStoreStage32.persist(applicationContext)
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
            normalizePackageName(candidate)?.takeIf {
                DriverAppPackagePolicy0162.isEligible(it, packageName)
            }
        }
        if (externalPackage == null) {
            manualCaptureInProgress138.set(false)
            toast("Abra o aplicativo que deseja capturar e tente novamente.")
            return
        }
        val visibleText = collectAllVisibleTextForCopy138()
        SelectedRideAppStore.add(applicationContext, externalPackage)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !screenshotInProgress.compareAndSet(false, true)) {
            ManualAppScreenCaptureStore.save(applicationContext, externalPackage, visibleText, null)
            manualCaptureInProgress138.set(false)
            toast("Aplicativo selecionado para ativar a leitura; texto capturado")
            showSaveConfirmationNotification("Aplicativo selecionado para ativar a leitura", externalPackage)
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
                                toast("Aplicativo selecionado para ativar a leitura; tela capturada")
                                showSaveConfirmationNotification("Aplicativo selecionado para ativar a leitura", externalPackage)
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
                        toast("Aplicativo selecionado para ativar a leitura; texto capturado")
                    }
                },
            )
        }.onFailure {
            ManualAppScreenCaptureStore.save(applicationContext, externalPackage, visibleText, null)
            screenshotInProgress.set(false)
            manualCaptureInProgress138.set(false)
            toast("Aplicativo selecionado para ativar a leitura; texto capturado")
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
            val capturedHeading0184 = lastDirectionalFix0184
                ?.headingDegrees
                ?.roundToInt()
                ?.let { ((it % 360) + 360) % 360 }
            val radar = ImportedRadar(
                id = "manual-radar-$now-${coordinate.latitude}-${coordinate.longitude}",
                coordinate = coordinate,
                type = 0,
                directionType = capturedHeading0184?.let { 1 },
                direction = capturedHeading0184,
                source = "Manual",
                createdAtMillis = now,
            )
            repository.replaceImportedRadars(listOf(radar) + currentImportedRadars)
            toast("Radar salvo")
            showSaveConfirmationNotification("Radar salvo", "Radar manual criado no local atual")
        }
    }

    private fun openNamedPlaceShortcut138(type: SavedPlaceType) {
        val group = if (type == SavedPlaceType.ProximityAlert) "alerts" else "saved_places"
        launchShortcutActivity0176(
            shortcutId = "$group.create",
            intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_OPEN_TAB, TAB_CONFIG)
                .putExtra(EXTRA_OPEN_BUBBLE_GROUP, group)
                .putExtra(EXTRA_CREATE_SAVED_PLACE_TYPE_138, type.name),
            failureMessage = "Não consegui abrir o cadastro agora.",
        )
    }

    private fun openDestinationConfirmationFromBubble138() {
        launchShortcutActivity0176(
            shortcutId = "destination.confirm",
            intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_OPEN_TAB, TAB_ANALYSIS)
                .putExtra(EXTRA_OPEN_BUBBLE_GROUP, "destination")
                .putExtra(EXTRA_CONFIRM_DESTINATION_GPS_138, true),
            failureMessage = "Não consegui abrir a confirmação do destino.",
        )
    }

    private fun copyAllVisibleTextFromBubble138() {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        if (!fullScreenCopyInProgress138.compareAndSet(false, true)) {
            toast("A cópia completa da tela já está em andamento.")
            return
        }
        val accessibilityText = collectAllVisibleTextForCopy138()
        requestFullScreenCopyOcr138(accessibilityText)
    }

    private fun collectAllVisibleTextForCopy138(): String {
        val root = safeRootInActiveWindow0185() ?: return ""
        val lines = mutableListOf<String>()
        collectNodeText(root, lines)
        return lines
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
    }

    private fun requestFullScreenCopyOcr138(accessibilityText: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            fullScreenCopyInProgress138.set(false)
            if (accessibilityText.isNotBlank()) copyAllVisibleTextToClipboard138(accessibilityText)
            else toast("Esta tela não disponibilizou texto para copiar.")
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
                                val ocrText = bitmap?.let { ocrService.extractText(it) }.orEmpty()
                                val text = sequenceOf(accessibilityText, ocrText)
                                    .flatMap { source -> source.lineSequence() }
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

    private fun copyPassengerValue159() {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()

        val now = System.currentTimeMillis()
        val previousGeneration = passengerValueCaptureGeneration160.get()
        val activeAge = now - passengerValueCaptureStartedAt160
        if (passengerValueCaptureInProgress159.get() && activeAge in 0L until PASSENGER_VALUE_STALE_AFTER_MS_160) {
            shortcutOverlayController.showSilentStatus159("Leitura em andamento. Aguarde um instante.", false)
            return
        }

        if (passengerValueScreenshotOwner160.compareAndSet(previousGeneration, 0L)) {
            screenshotInProgress.set(false)
        }
        val generation = passengerValueCaptureGeneration160.incrementAndGet()
        passengerValueCaptureStartedAt160 = now
        passengerValueCaptureInProgress159.set(true)
        armPassengerValueWatchdog160(generation)

        val accessibilityText = collectAllVisibleTextForCopy138()
        val immediate = PassengerValueFormatter.extract(accessibilityText)
        if (immediate != null) {
            completePassengerValue159(immediate)
            finishPassengerValueCapture160(generation)
            return
        }
        requestPassengerValueOcr159(accessibilityText, attempt = 0, generation = generation)
    }

    private fun armPassengerValueWatchdog160(generation: Long) {
        scope.launch {
            delay(PASSENGER_VALUE_WATCHDOG_MS_160)
            if (passengerValueCaptureGeneration160.get() == generation && passengerValueCaptureInProgress159.get()) {
                finishPassengerValueCapture160(generation)
                shortcutOverlayController.showSilentStatus159("Leitura liberada. Toque em Valor novamente.", false)
            }
        }
    }

    private fun finishPassengerValueCapture160(generation: Long) {
        if (passengerValueCaptureGeneration160.get() != generation) return
        passengerValueCaptureInProgress159.set(false)
        passengerValueCaptureStartedAt160 = 0L
        if (passengerValueScreenshotOwner160.compareAndSet(generation, 0L)) {
            screenshotInProgress.set(false)
        }
    }

    private fun requestPassengerValueOcr159(accessibilityText: String, attempt: Int, generation: Long) {
        if (passengerValueCaptureGeneration160.get() != generation) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            finishPassengerValueCapture160(generation)
            shortcutOverlayController.showSilentStatus159("Deixe nome, rota, lugares e valor visíveis", false)
            return
        }
        if (!screenshotInProgress.compareAndSet(false, true)) {
            if (attempt < PASSENGER_VALUE_SCREENSHOT_RETRIES_160) {
                scope.launch {
                    delay(120L)
                    requestPassengerValueOcr159(accessibilityText, attempt + 1, generation)
                }
            } else {
                finishPassengerValueCapture160(generation)
                shortcutOverlayController.showSilentStatus159("Tente novamente em um instante", false)
            }
            return
        }
        passengerValueScreenshotOwner160.set(generation)
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        scope.launch {
                            var bitmap: Bitmap? = null
                            try {
                                if (passengerValueCaptureGeneration160.get() != generation) return@launch
                                bitmap = screenshot.toSoftwareBitmap()
                                val ocrText = bitmap?.let { ocrService.extractText(it) }.orEmpty()
                                val combined = listOf(accessibilityText, ocrText)
                                    .filter(String::isNotBlank)
                                    .joinToString("\n")
                                val data = PassengerValueFormatter.extract(combined)
                                if (data == null) {
                                    shortcutOverlayController.showSilentStatus159("Deixe nome, rota, lugares e valor visíveis", false)
                                } else {
                                    completePassengerValue159(data)
                                }
                            } finally {
                                bitmap?.recycle()
                                finishPassengerValueCapture160(generation)
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        finishPassengerValueCapture160(generation)
                        shortcutOverlayController.showSilentStatus159("Não foi possível ler esta tela", false)
                    }
                },
            )
        }.onFailure {
            finishPassengerValueCapture160(generation)
            shortcutOverlayController.showSilentStatus159("Não foi possível ler esta tela", false)
        }
    }

    private fun completePassengerValue159(data: PassengerValueData) {
        val message = MessageTemplateStore0172.formatValue(applicationContext, data)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Valor da reserva", message))
        val registration = runCatching {
            FinancialRepository(applicationContext).registerPassengerValue(
                data = data,
                sourcePackage = currentRootPackageName() ?: currentWindowPackageName(),
            )
        }.getOrNull()
        val status = when (registration) {
            is PassengerRevenueRegistration.Added -> "Valor copiado • receita pendente"
            is PassengerRevenueRegistration.AlreadyExists -> "Valor copiado • já registrado"
            is PassengerRevenueRegistration.AmountConflict -> "Valor copiado • confira o Financeiro"
            null -> "Valor copiado • falha ao registrar"
        }
        shortcutOverlayController.showSilentStatus159(status, registration !is PassengerRevenueRegistration.AmountConflict && registration != null)
    }

    private fun openQuickLinks0172() {
        launchShortcutActivity0176(
            shortcutId = "quick_links",
            intent = Intent(this, QuickLinksActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            failureMessage = "Não foi possível abrir os Links rápidos.",
        )
    }

    private fun openTextCorrection0186() {
        val focused0186 = runCatching {
            safeRootInActiveWindow0185()?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        }.getOrNull()
        val ticket0186 = TextReplacementSession0186.create(focused0186)
        @Suppress("DEPRECATION")
        runCatching { focused0186?.recycle() }
        val clipboardText0186 = runCatching {
            val clipboard0186 = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard0186.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        }.getOrDefault("").take(12_000)
        val initialText0186 = ticket0186?.capturedText ?: clipboardText0186
        val intent0186 = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN_TAB, TAB_CONFIG)
            .putExtra(EXTRA_HOME_LAUNCH_MODE_0186, HomeLaunchPolicy0186.MODE_MODULE)
            .putExtra(EXTRA_OPEN_SHORTCUT_MODULE_0171, "text_correction")
            .putExtra(EXTRA_TEXT_CORRECTION_INITIAL_0186, initialText0186)
            .putExtra(EXTRA_TEXT_REPLACEMENT_TOKEN_0186, ticket0186?.token)
            .putExtra(EXTRA_TEXT_CORRECTION_REQUEST_KEY_0186, System.nanoTime().toString())
        launchShortcutActivity0176(
            shortcutId = "text_correction",
            intent = intent0186,
            failureMessage = "Não foi possível abrir a Correção de texto.",
            failureAction = { ticket0186?.token?.let(TextReplacementSession0186::clear) },
        )
    }

    private fun openMessageTemplates0172() {
        launchShortcutActivity0176(
            shortcutId = "message_templates",
            intent = Intent(this, MessageTemplatesActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            failureMessage = "Não foi possível abrir as frases predefinidas.",
        )
    }

    private fun openPrimaryQuickLink0172() {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        if (!QuickLinkStore0172.openPrimary(applicationContext)) {
            openQuickLinks0172()
            toast("Escolha e marque um link principal.")
        }
    }

    private fun clearApplicationCache0172() {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        scope.launch(Dispatchers.IO) {
            val result0172 = runCatching { CacheCleaner0172.clean(applicationContext) }.getOrNull()
            withContext(Dispatchers.Main) {
                if (result0172 == null) {
                    shortcutOverlayController.showSilentStatus159("Não foi possível limpar o cache", false)
                } else {
                    shortcutOverlayController.showSilentStatus159(
                        "Cache limpo • ${CacheCleaner0172.humanBytes(result0172.bytesFreed)} liberados",
                        true,
                    )
                }
            }
        }
    }

    private fun openFinance159() {
        launchShortcutActivity0176(
            shortcutId = "finance",
            intent = Intent(this, FinancialActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            failureMessage = "Não foi possível abrir o Financeiro",
            failureAction = {
                shortcutOverlayController.showSilentStatus159("Não foi possível abrir o Financeiro", false)
            },
        )
    }

    private fun copyTripConfirmationFromBubbleChecklist8() {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        if (!tripConfirmationCopyInProgressChecklist8.compareAndSet(false, true)) {
            toast("A confirmação da viagem já está sendo preparada.")
            return
        }

        val accessibilityText = collectTripConfirmationVisibleTextChecklist8()
        val immediateData0172 = TripConfirmationFormatter.extract(accessibilityText)
        if (immediateData0172 != null) {
            copyTripConfirmationToClipboardChecklist8(MessageTemplateStore0172.formatTrip(applicationContext, immediateData0172))
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
        val root = safeRootInActiveWindow0185() ?: return ""
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
                                val tripData0172 = TripConfirmationFormatter.extract(combinedText)
                                if (tripData0172 == null) {
                                    toast("Não encontrei rota, dia e horário. Abra a conversa do passageiro no BlaBlaCar e tente novamente.")
                                } else {
                                    copyTripConfirmationToClipboardChecklist8(MessageTemplateStore0172.formatTrip(applicationContext, tripData0172))
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
        launchShortcutActivity0176(
            shortcutId = "quick_replies",
            intent = Intent(this, QuickRepliesActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_QUICK_REPLY_TARGET_PACKAGE, targetPackage)
                .putExtra(EXTRA_QUICK_REPLY_CREATE, createNew)
                .putExtra(EXTRA_QUICK_REPLY_OVERLAY_MODE_0172, true),
            failureMessage = "Não foi possível abrir as respostas rápidas.",
        )
    } // open_quick_replies_checklist_3

    private fun exportDiagnosticFromBubble() {
        UnifiedDebugEventStore.record(
            "BUBBLE_REPORT_SHORTCUT_OPENED",
            universalResolvedForegroundPackage(),
            "grade abriu a area de relatorios; exportacao automatica desativada",
        )
        launchShortcutActivity0176(
            shortcutId = "diagnostic",
            intent = Intent(this@LiveRideAccessibilityService, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_OPEN_TAB, TAB_HISTORY)
                .putExtra(EXTRA_OPEN_BUBBLE_GROUP, "reports"),
            failureMessage = "Nao foi possivel abrir a area de relatorios.",
        )
    } // unified_manual_report_from_grid_0_1_142

    private fun toggleLiveReadingFromBubble() {
        shortcutOverlayController.hideShortcuts()
        persistResourceShortcutState()
        val enabled0162 = !FarolManualToggleRuntimeSyncStage43.enabled(currentSettings)
        applyManualReadingCommandStage43(enabled0162, "grid_shortcut")
        Toast.makeText(
            applicationContext,
            if (enabled0162) "Leitura do Farol ATIVADA" else "Leitura do Farol DESLIGADA",
            Toast.LENGTH_LONG,
        ).show()
        bubblePrefs.edit().putString("runtime_reading_status", if (enabled0162) "active" else "paused").apply()
    } // master_work_mode_from_bubble_0_1_162

    private fun stopApplicationFromBubble() {
        val updated0162 = WorkModePolicy0162.setEnabled(currentSettings, false)
        currentSettings = updated0162
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        applyWorkModeRuntime0162(false)
        scope.launch { runCatching { repository.saveSettings(updated0162) } }
        toast("Leitura do Farol desligada. Use a Home ou a grade para ligar novamente.")
    } // reversible_stop_work_mode_0_1_162

    private fun openResourceGroup(group: String, tab: String) {
        launchShortcutActivity0176(
            shortcutId = group,
            intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_OPEN_TAB, tab)
                .putExtra(EXTRA_OPEN_BUBBLE_GROUP, group),
            failureMessage = "Não foi possível abrir o módulo agora.",
        )
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
        val root = safeRootInActiveWindow0185() ?: return ""
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

    private fun openImportedRadarEditor0178(radar: ImportedRadar) {
        launchShortcutActivity0176(
            shortcutId = "radars.edit",
            intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_OPEN_TAB, TAB_CONFIG)
                .putExtra(EXTRA_IMPORTED_RADAR_ID_0178, radar.id)
                .putExtra(EXTRA_OPEN_SHORTCUT_MODULE_0171, "radars")
                .putExtra(EXTRA_OPEN_BUBBLE_GROUP, "radars"),
            failureMessage = "Não foi possível abrir este radar.",
        )
    }

    private fun openSavedPlaceEditor(place: SavedPlace) {
        launchShortcutActivity0176(
            shortcutId = if (place.type == SavedPlaceType.ProximityAlert) "alerts.edit" else "saved_places.edit",
            intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_OPEN_TAB, TAB_CONFIG)
                .putExtra(EXTRA_SAVED_PLACE_ID, place.id)
                .putExtra(EXTRA_OPEN_BUBBLE_GROUP, if (place.type == SavedPlaceType.ProximityAlert) "alerts" else "saved_places"),
            failureMessage = "Não foi possível abrir este local.",
        )
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
        private val mainHoldHandler0179 = android.os.Handler(android.os.Looper.getMainLooper())
        private var mainHoldView0179: View? = null
        private var mainHoldTriggered0179 = false
        private val mainHoldAction0179 = Runnable {
            if (!moved) {
                mainHoldTriggered0179 = true
                mainHoldView0179?.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                shortcutOverlayController.hideShortcuts()
                persistResourceShortcutState()
                openShortcutCustomization0179()
            }
        }
        private val touchSlop: Int by lazy {
            android.view.ViewConfiguration.get(this@LiveRideAccessibilityService).scaledTouchSlop.coerceAtLeast(1)
        }

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_BUBBLE_MOTION_EVENT", universalResolvedForegroundPackage(),
                details = "action=${event.actionMasked}; actionIndex=${event.actionIndex}; eventTimeMs=${event.eventTime}; downTimeMs=${event.downTime}; rawX=${event.rawX}; rawY=${event.rawY}; x=${event.x}; y=${event.y}; pointers=${event.pointerCount}; pressure=${runCatching { event.getPressure(0) }.getOrDefault(0f)}; size=${runCatching { event.getSize(0) }.getOrDefault(0f)}",
            )
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
                    mainHoldTriggered0179 = false
                    mainHoldView0179 = view
                    mainHoldHandler0179.removeCallbacks(mainHoldAction0179)
                    mainHoldHandler0179.postDelayed(
                        mainHoldAction0179,
                        ShortcutGesturePolicy0179.MAIN_CUSTOMIZATION_HOLD_MILLIS,
                    )
                    view.animate().cancel()
                    Unit /* diagnostics_off_checklist_4 */
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    if (!moved && BubbleDragPolicy.hasExceededTouchSlop(deltaX, deltaY, touchSlop)) {
                        moved = true
                        mainHoldHandler0179.removeCallbacks(mainHoldAction0179)
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
                    mainHoldHandler0179.removeCallbacks(mainHoldAction0179)
                    mainHoldView0179 = null
                    if (mainHoldTriggered0179) {
                        mainHoldTriggered0179 = false
                        pendingSingleTapJob?.cancel()
                        pendingSingleTapJob = null
                        lastTapUpMillis = 0L
                    } else if (moved) {
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
                    mainHoldHandler0179.removeCallbacks(mainHoldAction0179)
                    mainHoldView0179 = null
                    mainHoldTriggered0179 = false
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
        Orange(Color.rgb(243, 156, 18), Color.rgb(145, 82, 0), "laranja"),
        Green(Color.rgb(46, 204, 113), Color.rgb(24, 106, 59), "verde"),
        Red(Color.rgb(231, 76, 60), Color.rgb(127, 29, 29), "vermelho");

        fun argb(settings: AppSettings): Int {
            val base = if (settings.bubbleDarkMode) darkArgb else normalArgb
            val alpha = (settings.bubbleOpacity.coerceIn(0.25, 1.0) * 255).roundToInt()
            return Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
        }
    }

    private companion object {
        const val PASSENGER_VALUE_STALE_AFTER_MS_160 = 4_000L
        const val PASSENGER_VALUE_WATCHDOG_MS_160 = 6_000L
        const val PASSENGER_VALUE_SCREENSHOT_RETRIES_160 = 8
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
        private const val MAX_ACCESSIBILITY_NODES_0167 = 768
        private const val MAX_ACCESSIBILITY_TEXT_CHARS_0167 = 24_000
        const val BUBBLE_PREFS = "rota_certa_bubble"
        private const val NOTIFICATION_INITIAL_RETRY_DELAY_MILLIS_0169 = 180L
        private const val NOTIFICATION_VERIFY_DELAY_MILLIS_0169 = 5_000L
        private const val NOTIFICATION_FINAL_VERIFY_DELAY_MILLIS_0169 = 4_500L
        private const val NOTIFICATION_CAPTURE_TIMEOUT_MILLIS_0169 = 2_500L
        const val KEY_RUNTIME_SHORTCUTS_OPEN = "runtime_shortcuts_open"
        const val KEY_RUNTIME_SHORTCUT_COUNT = "runtime_shortcut_count"
        const val KEY_RUNTIME_SHORTCUT_LABELS = "runtime_shortcut_labels"
        const val EXTRA_OPEN_BUBBLE_GROUP = "open_bubble_group"
        // radar_edit_delete_dismiss_0_1_178
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

