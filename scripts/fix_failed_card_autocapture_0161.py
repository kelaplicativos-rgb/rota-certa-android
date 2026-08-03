from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[1]
main = root / "app/src/main/java/br/com/mapeiaia/rotacerta"
test = root / "app/src/test/java/br/com/mapeiaia/rotacerta"
service_path = main / "LiveRideAccessibilityService.kt"
source = service_path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global source
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"0.1.161 anchor {label} expected once, found {count}")
    source = source.replace(old, new, 1)

replace_once(
    "import android.graphics.PixelFormat\n",
    "import android.graphics.PixelFormat\nimport android.graphics.Rect\n",
    "Rect import",
)

replace_once(
    "    private val screenshotInProgress = AtomicBoolean(false)\n",
    """    private val screenshotInProgress = AtomicBoolean(false)
    private val failedCardAutoCaptureGate0161 = FailedCardAutoCaptureGate0161()
    private lateinit var failedCardLayoutModelStore0161: FailedCardLayoutModelStore0161
    private var lastFailedCardNodes0161 = emptyList<FailedCardNodeLine0161>()
    private var lastFailedCardSignature0161: String? = null
    private var lastFailedCardAccessibilityHash0161: Int? = null
""",
    "auto capture fields",
)

replace_once(
    "        repository = SettingsRepository(applicationContext)\n",
    """        repository = SettingsRepository(applicationContext)
        failedCardLayoutModelStore0161 = FailedCardLayoutModelStore0161(applicationContext)
""",
    "model store init",
)

replace_once(
    "        serviceReady = false\n        screenshotInProgress.set(false)\n",
    """        serviceReady = false
        failedCardAutoCaptureGate0161.reset()
        lastFailedCardNodes0161 = emptyList()
        lastFailedCardSignature0161 = null
        lastFailedCardAccessibilityHash0161 = null
        screenshotInProgress.set(false)
""",
    "destroy cleanup",
)

old_transient = """        val eventPackage = normalizePackageName(event.packageName?.toString())
        val rootPackage = currentRootPackageName()
        val transientOverlayPackages151 = setOf(
            packageName,
            "com.android.systemui",
            "com.samsung.android.app.smartcapture",
        )
        val transientOverlayEvent151 = eventPackage in transientOverlayPackages151 &&
            rootPackage != null && rootPackage != eventPackage
        val selectedPackages156 = SelectedRideAppStore.read(applicationContext)
"""
new_transient = """        val eventPackage = normalizePackageName(event.packageName?.toString())
        val rootPackage = currentRootPackageName()
        val selectedPackages156 = SelectedRideAppStore.read(applicationContext)
        val transientOverlayEvent151 = TransientOverlayPackagePolicy0161.shouldPreferSelectedRoot(
            eventPackageName = eventPackage,
            rootPackageName = rootPackage,
            selectedPackages = selectedPackages156,
            ownPackageName = packageName,
        )
"""
replace_once(old_transient, new_transient, "transient package resolution")

replace_once(
    """                        eventPackage == "com.samsung.android.app.smartcapture" ||
                        resolvedPackage.contains("launcher", ignoreCase = true))
""",
    """                        eventPackage == "com.samsung.android.app.smartcapture" ||
                        TransientOverlayPackagePolicy0161.isTransient(eventPackage) ||
                        resolvedPackage.contains("launcher", ignoreCase = true))
""",
    "blocked transient preservation",
)

start = source.index("    private fun requestScreenshotAnalysis(allowPopupCandidate: Boolean = false) {")
end_marker = "    } // universal_stable_screenshot_0_1_101\n"
end = source.index(end_marker, start) + len(end_marker)
new_screenshot = r'''    private fun requestScreenshotAnalysis(allowPopupCandidate: Boolean = false) {
        @Suppress("UNUSED_VARIABLE") val ignoredAllowPopupCandidate0161 = allowPopupCandidate
        if (universalRouteJob?.isActive == true || (lastAnalyzedHash != null && lastAnalyzedHash == lastSnapshotHash)) return
        if (!hasStrictSelectedRootChecklist1()) return
        if (!currentSettings.liveReadingEnabled || bubbleGestureActive) return
        if (!serviceReady || !isUniversalExternalWindowActive() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        val resolvedOcrPackage = universalResolvedForegroundPackage() ?: return
        val savedPackages0161 = SelectedRideAppStore.read(applicationContext)
        if (resolvedOcrPackage !in savedPackages0161 || !shouldScanPackage(resolvedOcrPackage)) return
        val accessibilitySnapshot0161 = collectImmediateVisibleTextChecklist13()
        val nodeSnapshot0161 = collectFailedCardNodeLines0161()
        val parserEvaluation0161 = SimpleSavedAppFarolPolicy.evaluate(
            packageName = resolvedOcrPackage,
            savedPackages = savedPackages0161,
            text = accessibilitySnapshot0161,
        )
        val probableCard0161 = FailedCardRecoveryEngine0161.probableRideCard(
            text = accessibilitySnapshot0161,
            packageName = resolvedOcrPackage,
        )
        if (parserEvaluation0161.active || !probableCard0161) return

        val ocrRequestToken = UniversalFastReadPolicy.createOcrRequestToken(
            observedPackageName = universalForegroundPackageName ?: activePackageName,
            resolvedPackageName = resolvedOcrPackage,
            ownPackageName = this.packageName,
            screenGeneration = universalScreenGeneration,
            windowGeneration = universalWindowGeneration,
        ) ?: return
        val requestedPackage = ocrRequestToken.observedPackageName
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
        val windowId0161 = rootInActiveWindow?.windowId ?: lastStableFarolWindowIdChecklist14 ?: 0
        val captureSignature0161 = FailedCardRecoveryEngine0161.signature(
            packageName = requestedPackage,
            windowId = windowId0161,
            text = accessibilitySnapshot0161,
            nodes = nodeSnapshot0161,
        )
        if (!screenshotInProgress.compareAndSet(false, true)) return
        val captureReserved0161 = failedCardAutoCaptureGate0161.tryStart(
            signature = captureSignature0161,
            probableCard = probableCard0161,
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
                                if (!stillFresh0161 || !hasStrictSelectedRootChecklist1()) return@launch

                                bitmap0161 = screenshot.toSoftwareBitmap() ?: return@launch
                                val ocrText0161 = withContext(Dispatchers.Default) {
                                    ocrService.extractText(bitmap0161)
                                }
                                rememberSourceText(requestedPackage, TextSource.Ocr, ocrText0161)
                                val models0161 = failedCardLayoutModelStore0161.modelsFor(requestedPackage)
                                val recovery0161 = withContext(Dispatchers.Default) {
                                    FailedCardRecoveryEngine0161.recover(
                                        packageName = requestedPackage,
                                        savedPackages = savedPackages0161,
                                        accessibilityText = accessibilitySnapshot0161,
                                        ocrText = ocrText0161,
                                        nodes = nodeSnapshot0161,
                                        knownModels = models0161,
                                    )
                                }
                                recovery0161?.modelCandidate?.let(failedCardLayoutModelStore0161::saveCandidate)

                                if (recovery0161 != null) {
                                    applyRecoveredCard0161(
                                        selectedPackage0161 = requestedPackage,
                                        snapshotText0161 = mergeRideTexts(accessibilitySnapshot0161, ocrText0161),
                                        recovery0161 = recovery0161,
                                    )
                                } else {
                                    processRideText(
                                        ocrText0161,
                                        TextSource.Ocr,
                                        allowPopupCandidate = true,
                                        packageHint152 = requestedPackage,
                                    )
                                }

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
'''
source = source[:start] + new_screenshot + source[end:]

collect_anchor = """    private fun collectNodeText(node: AccessibilityNodeInfo?, lines: MutableList<String>) {
        if (node == null) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { lines += it }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { lines += it }
        for (index in 0 until node.childCount) {
            collectNodeText(runCatching { node.getChild(index) }.getOrNull(), lines)
        }
    }
"""
collect_new = collect_anchor + r'''

    private fun collectFailedCardNodeLines0161(): List<FailedCardNodeLine0161> {
        val root0161 = rootInActiveWindow ?: return emptyList()
        val rootPackage0161 = normalizePackageName(root0161.packageName?.toString())
        val selectedPackages0161 = SelectedRideAppStore.read(applicationContext)
        if (rootPackage0161 !in selectedPackages0161) return emptyList()
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
        node0161.getBoundsInScreen(bounds0161)
        val className0161 = node0161.className?.toString().orEmpty()
        val viewId0161 = node0161.viewIdResourceName.orEmpty()
        linkedSetOf(
            node0161.text?.toString().orEmpty(),
            node0161.contentDescription?.toString().orEmpty(),
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
        for (index0161 in 0 until node0161.childCount) {
            if (output0161.size >= 160) break
            collectFailedCardNodeLines0161(
                runCatching { node0161.getChild(index0161) }.getOrNull(),
                output0161,
            )
        }
    } // failed_card_accessibility_structure_0_1_161
'''
replace_once(collect_anchor, collect_new, "node structure collector")

snapshot_anchor = """        val snapshotTextChecklist13 = text.trim()
        val evaluationChecklist13 = withContext(Dispatchers.Default) {
"""
snapshot_new = """        val snapshotTextChecklist13 = text.trim()
        if (source == TextSource.Accessibility && lastFailedCardAccessibilityHash0161 != snapshotTextChecklist13.hashCode()) {
            lastOcrText = ""
            lastOcrTextAtMillis = 0L
            lastFailedCardAccessibilityHash0161 = snapshotTextChecklist13.hashCode()
        }
        rememberSourceText(selectedPackageChecklist13, source, snapshotTextChecklist13)
        val evaluationChecklist13 = withContext(Dispatchers.Default) {
"""
replace_once(snapshot_anchor, snapshot_new, "source pairing")

inactive_anchor = """        if (!evaluationChecklist13.active) {
            val decisionAge141 = System.currentTimeMillis() - universalLastActiveReadAtMillis
"""
inactive_new = r'''        if (!evaluationChecklist13.active) {
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
            val models0161 = failedCardLayoutModelStore0161.modelsFor(selectedPackageChecklist13)
            val recovery0161 = withContext(Dispatchers.Default) {
                FailedCardRecoveryEngine0161.recover(
                    packageName = selectedPackageChecklist13,
                    savedPackages = savedPackagesChecklist13,
                    accessibilityText = freshAccessibility0161,
                    ocrText = freshOcr0161,
                    nodes = failureNodes0161,
                    knownModels = models0161,
                )
            }
            if (recovery0161 != null) {
                recovery0161.modelCandidate?.let(failedCardLayoutModelStore0161::saveCandidate)
                UnifiedDebugEventStore.record(
                    "BUBBLE_FAILED_CARD_RECOVERED",
                    selectedPackageChecklist13,
                    "strategy=${recovery0161.strategy}; confidence=${recovery0161.confidence}; pickup=${recovery0161.fields.pickup.orEmpty()}; destination=${recovery0161.fields.destination.orEmpty()}",
                )
                applyRecoveredCard0161(
                    selectedPackage0161 = selectedPackageChecklist13,
                    snapshotText0161 = mergeRideTexts(freshAccessibility0161, freshOcr0161),
                    recovery0161 = recovery0161,
                )
                return
            }
            val mergedFailureText0161 = mergeRideTexts(freshAccessibility0161, freshOcr0161)
            if (FailedCardRecoveryEngine0161.probableRideCard(mergedFailureText0161, selectedPackageChecklist13)) {
                val windowId0161 = rootInActiveWindow?.windowId ?: lastStableFarolWindowIdChecklist14 ?: 0
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
            val decisionAge141 = System.currentTimeMillis() - universalLastActiveReadAtMillis
'''
replace_once(inactive_anchor, inactive_new, "inactive recovery")

analyze_anchor = """    private suspend fun analyzeUniversalTwoAddress(
"""
helper = r'''    private suspend fun applyRecoveredCard0161(
        selectedPackage0161: String,
        snapshotText0161: String,
        recovery0161: FailedCardRecoveryResult0161,
    ) {
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

        universalLastActiveReadAtMillis = System.currentTimeMillis()
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
        val generation0161 = universalScreenGeneration
        if (cachedDistances0161 != null) {
            val cachedResult0161 = decideFastWorkRegionChecklist13(
                snapshotText = snapshotText0161,
                fields = fields0161,
                settings = settings0161,
                targets = targets0161,
                routeDistances = cachedDistances0161,
            )
            bubblePrefs.edit().putString("fast_farol_last_path", "cache_exato_recuperado_0161").apply()
            applyUniversalTwoAddressResult(cachedResult0161, screenHash0161, signature0161, generation0161)
            return
        }

        UnifiedDebugEventStore.record(
            "BUBBLE_ROUTE_REQUESTED",
            selectedPackage0161,
            "destino=$destination0161; alvos=${targets0161.destinations.size}; generation=$generation0161; recovery=${recovery0161.strategy}",
        )
        rememberBubbleReason("universal_waiting", "Card recuperado; calculando o ultimo destino.")
        if (currentRadarColor != RadarColor.Default || currentDistanceKm != null) {
            showOverlay(RadarColor.Default, distanceKm = null)
        }
        bubblePrefs.edit().putString("fast_farol_last_path", "rota_google_recuperada_0161").apply()
        universalRouteJob = scope.launch {
            analyzeUniversalTwoAddress(
                snapshotText = snapshotText0161,
                fields = fields0161,
                screenHash = screenHash0161,
                addressSignature = signature0161,
                generation = generation0161,
            )
        }
    } // failed_card_recovered_route_0_1_161

'''
replace_once(analyze_anchor, helper + analyze_anchor, "recovered route helper")

service_path.write_text(source, encoding="utf-8")

main.mkdir(parents=True, exist_ok=True)
test.mkdir(parents=True, exist_ok=True)
(main / 'FailedCardRecovery0161.kt').write_text('package br.com.mapeiaia.rotacerta\n\nimport java.security.MessageDigest\nimport java.text.Normalizer\nimport java.util.LinkedHashMap\nimport java.util.Locale\n\n/** A bounded, text-only representation of one accessibility node on a failed card. */\ndata class FailedCardNodeLine0161(\n    val text: String,\n    val top: Int,\n    val left: Int,\n    val bottom: Int,\n    val right: Int,\n    val className: String = "",\n    val viewId: String = "",\n)\n\ndata class FailedCardLayoutModel0161(\n    val packageName: String,\n    val originMarker: String,\n    val destinationMarker: String,\n    val originOffset: Int,\n    val destinationOffset: Int,\n    val structureKey: String,\n    val confidence: Int,\n)\n\ndata class FailedCardRecoveryResult0161(\n    val fields: RideFields,\n    val strategy: String,\n    val confidence: Int,\n    val modelCandidate: FailedCardLayoutModel0161? = null,\n)\n\nobject TransientOverlayPackagePolicy0161 {\n    private val exactPackages = setOf(\n        "android",\n        "com.android.systemui",\n        "com.samsung.android.app.smartcapture",\n        "com.google.android.projection.gearhead",\n    )\n\n    fun shouldPreferSelectedRoot(\n        eventPackageName: String?,\n        rootPackageName: String?,\n        selectedPackages: Set<String>,\n        ownPackageName: String,\n    ): Boolean {\n        val event = normalize(eventPackageName) ?: return false\n        val root = normalize(rootPackageName) ?: return false\n        val own = normalize(ownPackageName)\n        if (event == root || root !in selectedPackages.mapNotNull(::normalize).toSet()) return false\n        return event == own || isTransient(event)\n    }\n\n    fun isTransient(packageName: String?): Boolean {\n        val normalized = normalize(packageName) ?: return false\n        return normalized in exactPackages ||\n            normalized.contains("inputmethod") ||\n            normalized.contains("keyboard") ||\n            normalized.contains("launcher") ||\n            normalized.contains("keyguard") ||\n            normalized.contains("notification")\n    }\n\n    private fun normalize(value: String?): String? = value\n        ?.trim()\n        ?.lowercase(Locale.ROOT)\n        ?.takeIf(String::isNotBlank)\n}\n\n/**\n * One-shot gate for failed-card screenshots. It never starts a continuous loop.\n * A timed-out reservation is released so a dead Android screenshot callback cannot\n * permanently block later cards.\n */\nclass FailedCardAutoCaptureGate0161(\n    private val lockTimeoutMillis: Long = 4_000L,\n    private val retentionMillis: Long = 120_000L,\n    private val maxEntries: Int = 32,\n) {\n    private data class Entry(\n        var startedAtMillis: Long = 0L,\n        var completedAtMillis: Long = 0L,\n    )\n\n    private val entries = LinkedHashMap<String, Entry>()\n\n    @Synchronized\n    fun tryStart(\n        signature: String,\n        probableCard: Boolean,\n        parserActive: Boolean,\n        routeInFlight: Boolean,\n        hasDecision: Boolean,\n        nowMillis: Long = System.currentTimeMillis(),\n    ): Boolean {\n        if (signature.isBlank() || !probableCard || parserActive || routeInFlight || hasDecision) return false\n        prune(nowMillis)\n        val entry = entries.getOrPut(signature) { Entry() }\n        if (entry.completedAtMillis > 0L) return false\n        if (entry.startedAtMillis > 0L && nowMillis - entry.startedAtMillis < lockTimeoutMillis) return false\n        entry.startedAtMillis = nowMillis\n        trimToLimit()\n        return true\n    }\n\n    @Synchronized\n    fun finish(signature: String, nowMillis: Long = System.currentTimeMillis()) {\n        val entry = entries.getOrPut(signature) { Entry() }\n        entry.startedAtMillis = 0L\n        entry.completedAtMillis = nowMillis\n        trimToLimit()\n    }\n\n    @Synchronized\n    fun releaseForRetry(signature: String) {\n        entries[signature]?.startedAtMillis = 0L\n    }\n\n    @Synchronized\n    fun reset() = entries.clear()\n\n    @Synchronized\n    fun hasCompleted(signature: String): Boolean = entries[signature]?.completedAtMillis?.let { it > 0L } == true\n\n    private fun prune(nowMillis: Long) {\n        val iterator = entries.entries.iterator()\n        while (iterator.hasNext()) {\n            val entry = iterator.next().value\n            val reference = maxOf(entry.startedAtMillis, entry.completedAtMillis)\n            if (reference > 0L && nowMillis >= reference && nowMillis - reference > retentionMillis) iterator.remove()\n        }\n    }\n\n    private fun trimToLimit() {\n        while (entries.size > maxEntries) {\n            val first = entries.entries.firstOrNull()?.key ?: return\n            entries.remove(first)\n        }\n    }\n}\n\nobject FailedCardRecoveryEngine0161 {\n    private val originMarkers = listOf(\n        "ponto de partida",\n        "local de embarque",\n        "endereco de partida",\n        "endereço de partida",\n        "origem",\n        "embarque",\n        "partida",\n        "buscar",\n        "pegue",\n        "a",\n    )\n    private val destinationMarkers = listOf(\n        "destino final",\n        "local de destino",\n        "endereco de destino",\n        "endereço de destino",\n        "destino",\n        "chegada",\n        "desembarque",\n        "levar",\n        "b",\n    )\n    private val rideMarkerRegex = Regex(\n        "\\\\b(?:aceitar|ofere[cç]a|pedido\\\\s+de\\\\s+viagem|corrida|viagem|tarifa|pre[cç]o|passageiro|embarque|destino|origem|partida|chegada)\\\\b",\n        RegexOption.IGNORE_CASE,\n    )\n    private val moneyRegex = Regex("(?:R\\\\$\\\\s*\\\\d+|\\\\b\\\\d+[,.]\\\\d{2}\\\\s*(?:reais?|R\\\\$))", RegexOption.IGNORE_CASE)\n    private val distanceRegex = Regex("\\\\b\\\\d+(?:[,.]\\\\d+)?\\\\s*(?:km|m|min|minutos?)\\\\b", RegexOption.IGNORE_CASE)\n    private val timeRegex = Regex("\\\\b(?:[01]?\\\\d|2[0-3])[:h]\\\\d{2}\\\\b", RegexOption.IGNORE_CASE)\n    private val phoneRegex = Regex("(?<!\\\\d)(?:\\\\+?55\\\\s*)?(?:\\\\(?\\\\d{2}\\\\)?\\\\s*)?(?:9\\\\s*)?\\\\d{4}[\\\\s-]?\\\\d{4}(?!\\\\d)")\n    private val locationCueRegex = Regex(\n        "\\\\b(?:rua|r\\\\.|avenida|av\\\\.|alameda|travessa|estrada|rodovia|bairro|jardim|vila|centro|parque|condom[ií]nio|residencial|loteamento|shopping|terminal|esta[cç][aã]o|aeroporto|rodovi[aá]ria|hospital|mercado|posto|igreja|escola|faculdade|universidade|s[ií]tio|fazenda)\\\\b",\n        RegexOption.IGNORE_CASE,\n    )\n    private val controlNoiseRegex = Regex(\n        "^(?:aceitar|recusar|fechar|cancelar|voltar|detalhes|mais|menos|mapa|navegar|copiar|compartilhar|ofere[cç]a|confirmar|editar|excluir)$",\n        RegexOption.IGNORE_CASE,\n    )\n\n    fun probableRideCard(text: String, packageName: String?): Boolean {\n        if (packageName.isNullOrBlank() || text.length < 60) return false\n        val markerCount = rideMarkerRegex.findAll(text).map { canonical(it.value) }.distinct().count()\n        val metrics = listOf(moneyRegex, distanceRegex, timeRegex).count { it.containsMatchIn(text) }\n        val hasBothLabels = containsAnyMarker(text, originMarkers) && containsAnyMarker(text, destinationMarkers)\n        return hasBothLabels || markerCount >= 2 || (markerCount >= 1 && metrics >= 1) || metrics >= 2\n    }\n\n    fun signature(\n        packageName: String,\n        windowId: Int,\n        text: String,\n        nodes: List<FailedCardNodeLine0161>,\n    ): String {\n        val normalizedNodes = nodes.asSequence()\n            .take(120)\n            .map { node ->\n                listOf(\n                    normalizeDynamic(node.text),\n                    (node.top / 24).toString(),\n                    (node.left / 24).toString(),\n                    canonical(node.className),\n                ).joinToString(":")\n            }\n            .filter(String::isNotBlank)\n            .joinToString("|")\n        val payload = listOf(\n            canonical(packageName),\n            windowId.toString(),\n            normalizeDynamic(text),\n            normalizedNodes,\n        ).joinToString("\\u001E")\n        return sha256(payload).take(24)\n    }\n\n    fun structureKey(nodes: List<FailedCardNodeLine0161>, text: String): String {\n        val basis = if (nodes.isNotEmpty()) {\n            nodes.asSequence()\n                .take(120)\n                .map { node -> "${normalizeDynamic(node.text)}@${node.top / 32}:${node.left / 32}" }\n                .joinToString("|")\n        } else {\n            text.lineSequence().take(80).joinToString("|") { normalizeDynamic(it) }\n        }\n        return sha256(basis).take(20)\n    }\n\n    fun recover(\n        packageName: String,\n        savedPackages: Set<String>,\n        accessibilityText: String,\n        ocrText: String,\n        nodes: List<FailedCardNodeLine0161>,\n        knownModels: List<FailedCardLayoutModel0161> = emptyList(),\n    ): FailedCardRecoveryResult0161? {\n        if (packageName !in savedPackages) return null\n\n        knownModels.asSequence()\n            .filter { it.packageName == packageName }\n            .forEach { model ->\n                recoverWithModel(accessibilityText, model)?.let { fields ->\n                    return FailedCardRecoveryResult0161(\n                        fields = fields,\n                        strategy = "modelo_local",\n                        confidence = model.confidence,\n                        modelCandidate = model,\n                    )\n                }\n            }\n\n        val merged = merge(accessibilityText, ocrText, nodes)\n        if (!probableRideCard(merged, packageName)) return null\n\n        val mergedEvaluation = SimpleSavedAppFarolPolicy.evaluate(packageName, savedPackages, merged)\n        if (mergedEvaluation.active) {\n            return FailedCardRecoveryResult0161(\n                fields = RideFields(\n                    pickup = mergedEvaluation.pickup,\n                    destination = mergedEvaluation.destination,\n                ),\n                strategy = "acessibilidade_mais_ocr",\n                confidence = 100,\n            )\n        }\n\n        val lines = orderedLines(accessibilityText, ocrText, nodes)\n        val origin = extractMarkedLocation(lines, originMarkers) ?: return null\n        val destination = extractMarkedLocation(lines, destinationMarkers) ?: return null\n        if (canonical(origin.value) == canonical(destination.value)) return null\n\n        val evidence = UniversalRideCardEvidencePolicy.evaluate(\n            text = merged,\n            addresses = listOf(origin.value, destination.value),\n            destination = destination.value,\n            packageName = packageName,\n        )\n        if (!evidence.accepted && !(origin.strong && destination.strong && probableRideCard(merged, packageName))) return null\n\n        val model = FailedCardLayoutModel0161(\n            packageName = packageName,\n            originMarker = origin.marker,\n            destinationMarker = destination.marker,\n            originOffset = origin.offset,\n            destinationOffset = destination.offset,\n            structureKey = structureKey(nodes, merged),\n            confidence = if (evidence.accepted) 95 else 90,\n        )\n        return FailedCardRecoveryResult0161(\n            fields = RideFields(pickup = origin.value, destination = destination.value),\n            strategy = "marcadores_confirmados",\n            confidence = model.confidence,\n            modelCandidate = model,\n        )\n    }\n\n    fun recoverWithModel(text: String, model: FailedCardLayoutModel0161): RideFields? {\n        val lines = orderedLines(text, "", emptyList())\n        val origin = extractByStoredMarker(lines, model.originMarker, model.originOffset) ?: return null\n        val destination = extractByStoredMarker(lines, model.destinationMarker, model.destinationOffset) ?: return null\n        if (canonical(origin) == canonical(destination)) return null\n        return RideFields(pickup = origin, destination = destination)\n    }\n\n    private data class MarkedLocation(\n        val value: String,\n        val marker: String,\n        val offset: Int,\n        val strong: Boolean,\n    )\n\n    private fun extractMarkedLocation(lines: List<String>, markers: List<String>): MarkedLocation? {\n        val matches = mutableListOf<MarkedLocation>()\n        lines.forEachIndexed { index, raw ->\n            val line = clean(raw)\n            val marker = markers.firstOrNull { markerMatches(line, it) } ?: return@forEachIndexed\n            val sameLine = remainderAfterMarker(line, marker)\n            if (sameLine.isNotBlank() && safeLocation(sameLine, strongMarker = marker.length > 1)) {\n                matches += MarkedLocation(sameLine, canonical(marker), 0, marker.length > 1)\n                return@forEachIndexed\n            }\n            for (offset in 1..2) {\n                val candidate = lines.getOrNull(index + offset)?.let(::clean).orEmpty()\n                if (safeLocation(candidate, strongMarker = marker.length > 1)) {\n                    matches += MarkedLocation(candidate, canonical(marker), offset, marker.length > 1)\n                    break\n                }\n                if (candidate.isNotBlank() && isAnotherMarker(candidate)) break\n            }\n        }\n        return matches.distinctBy { canonical(it.value) }.singleOrNull()\n    }\n\n    private fun extractByStoredMarker(lines: List<String>, marker: String, offset: Int): String? {\n        lines.forEachIndexed { index, raw ->\n            val line = clean(raw)\n            if (!markerMatches(line, marker)) return@forEachIndexed\n            val sameLine = remainderAfterMarker(line, marker)\n            if (offset == 0 && safeLocation(sameLine, strongMarker = marker.length > 1)) return sameLine\n            val candidate = lines.getOrNull(index + offset)?.let(::clean).orEmpty()\n            if (safeLocation(candidate, strongMarker = marker.length > 1)) return candidate\n        }\n        return null\n    }\n\n    private fun orderedLines(\n        accessibilityText: String,\n        ocrText: String,\n        nodes: List<FailedCardNodeLine0161>,\n    ): List<String> {\n        val ordered = linkedSetOf<String>()\n        accessibilityText.lineSequence().map(::clean).filter(String::isNotBlank).forEach(ordered::add)\n        nodes.sortedWith(compareBy<FailedCardNodeLine0161> { it.top }.thenBy { it.left })\n            .map { clean(it.text) }\n            .filter(String::isNotBlank)\n            .forEach(ordered::add)\n        ocrText.lineSequence().map(::clean).filter(String::isNotBlank).forEach(ordered::add)\n        return ordered.toList()\n    }\n\n    private fun merge(\n        accessibilityText: String,\n        ocrText: String,\n        nodes: List<FailedCardNodeLine0161>,\n    ): String = orderedLines(accessibilityText, ocrText, nodes).joinToString("\\n")\n\n    private fun containsAnyMarker(text: String, markers: List<String>): Boolean =\n        text.lineSequence().map(::clean).any { line -> markers.any { marker -> markerMatches(line, marker) } }\n\n    private fun markerMatches(line: String, marker: String): Boolean {\n        val normalized = canonical(line)\n        val canonicalMarker = canonical(marker)\n        if (canonicalMarker.length == 1) return normalized == canonicalMarker || normalized.startsWith("$canonicalMarker ")\n        return normalized == canonicalMarker ||\n            normalized.startsWith("$canonicalMarker ") ||\n            normalized.startsWith("$canonicalMarker:") ||\n            normalized.startsWith("$canonicalMarker -")\n    }\n\n    private fun remainderAfterMarker(line: String, marker: String): String {\n        val trimmed = line.trim()\n        val markerRegex = Regex("^\\\\s*${Regex.escape(marker)}\\\\s*[:\\\\-–—]?\\\\s*", RegexOption.IGNORE_CASE)\n        return trimmed.replaceFirst(markerRegex, "").trim()\n    }\n\n    private fun isAnotherMarker(value: String): Boolean =\n        originMarkers.any { markerMatches(value, it) } || destinationMarkers.any { markerMatches(value, it) }\n\n    private fun safeLocation(value: String, strongMarker: Boolean): Boolean {\n        val cleaned = clean(value)\n        if (cleaned.length !in 5..160) return false\n        if (controlNoiseRegex.matches(cleaned)) return false\n        if (moneyRegex.containsMatchIn(cleaned) || timeRegex.containsMatchIn(cleaned) || phoneRegex.containsMatchIn(cleaned)) return false\n        if (distanceRegex.matches(cleaned)) return false\n        if (UniversalScreenAddressParser.isRecognizedAddress(cleaned)) return true\n        val words = canonical(cleaned).split(Regex("\\\\s+")).filter { it.length >= 2 }\n        if (words.size < 2) return false\n        val hasLocationCue = locationCueRegex.containsMatchIn(cleaned) ||\n            cleaned.contains(\',\') ||\n            cleaned.contains(\'(\') ||\n            Regex("\\\\b\\\\d{1,6}\\\\b").containsMatchIn(cleaned)\n        return hasLocationCue && (strongMarker || words.size >= 3)\n    }\n\n    private fun clean(value: String): String = value\n        .replace(\'\\u00A0\', \' \')\n        .replace(\'\\u202F\', \' \')\n        .replace(Regex("\\\\s+"), " ")\n        .trim(\' \', \',\', \';\', \'-\', \'–\', \'—\')\n\n    private fun normalizeDynamic(value: String): String = canonical(value)\n        .replace(Regex("\\\\b\\\\d+(?:[,.]\\\\d+)?\\\\b"), "#")\n        .replace(Regex("\\\\s+"), " ")\n        .take(2_000)\n\n    private fun canonical(value: String): String = Normalizer\n        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)\n        .replace(Regex("\\\\p{Mn}+"), "")\n        .replace(Regex("[^\\\\p{L}\\\\p{N}:]+"), " ")\n        .replace(Regex("\\\\s+"), " ")\n        .trim()\n\n    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")\n        .digest(value.toByteArray(Charsets.UTF_8))\n        .joinToString("") { byte -> "%02x".format(byte) }\n}\n', encoding="utf-8")
(main / 'FailedCardCaptureStore0161.kt').write_text('package br.com.mapeiaia.rotacerta\n\nimport android.content.Context\nimport android.graphics.Bitmap\nimport java.io.File\nimport java.net.URLDecoder\nimport java.net.URLEncoder\nimport java.nio.charset.StandardCharsets\nimport java.util.Locale\n\nclass FailedCardLayoutModelStore0161(context: Context) {\n    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)\n\n    fun modelsFor(packageName: String): List<FailedCardLayoutModel0161> = prefs\n        .getStringSet(KEY_MODELS, emptySet())\n        .orEmpty()\n        .mapNotNull(::decode)\n        .filter { it.packageName == packageName }\n        .sortedByDescending { it.confidence }\n        .take(MAX_MODELS_PER_PACKAGE)\n\n    fun saveCandidate(model: FailedCardLayoutModel0161) {\n        if (model.confidence < 90 || model.packageName.isBlank() || model.structureKey.isBlank()) return\n        val existing = prefs.getStringSet(KEY_MODELS, emptySet()).orEmpty()\n            .mapNotNull(::decode)\n            .filterNot {\n                it.packageName == model.packageName &&\n                    it.structureKey == model.structureKey &&\n                    it.originMarker == model.originMarker &&\n                    it.destinationMarker == model.destinationMarker\n            }\n            .toMutableList()\n        existing.add(0, model)\n        val bounded = existing\n            .groupBy { it.packageName }\n            .flatMap { (_, models) -> models.take(MAX_MODELS_PER_PACKAGE) }\n            .take(MAX_TOTAL_MODELS)\n            .map(::encode)\n            .toSet()\n        prefs.edit().putStringSet(KEY_MODELS, bounded).apply()\n    }\n\n    private fun encode(model: FailedCardLayoutModel0161): String = listOf(\n        model.packageName,\n        model.originMarker,\n        model.destinationMarker,\n        model.originOffset.toString(),\n        model.destinationOffset.toString(),\n        model.structureKey,\n        model.confidence.toString(),\n    ).joinToString("|") { part -> URLEncoder.encode(part, StandardCharsets.UTF_8.name()) }\n\n    private fun decode(value: String): FailedCardLayoutModel0161? = runCatching {\n        val parts = value.split(\'|\').map { part -> URLDecoder.decode(part, StandardCharsets.UTF_8.name()) }\n        if (parts.size != 7) return@runCatching null\n        FailedCardLayoutModel0161(\n            packageName = parts[0],\n            originMarker = parts[1],\n            destinationMarker = parts[2],\n            originOffset = parts[3].toInt(),\n            destinationOffset = parts[4].toInt(),\n            structureKey = parts[5],\n            confidence = parts[6].toInt(),\n        )\n    }.getOrNull()\n\n    companion object {\n        private const val PREFS_NAME = "failed_card_layout_models_0161"\n        private const val KEY_MODELS = "models"\n        private const val MAX_MODELS_PER_PACKAGE = 4\n        private const val MAX_TOTAL_MODELS = 12\n    }\n}\n\ndata class FailedCardTechnicalSnapshot0161(\n    val signature: String,\n    val packageName: String,\n    val windowId: Int,\n    val createdAtMillis: Long,\n    val accessibilityText: String,\n    val ocrText: String,\n    val nodes: List<FailedCardNodeLine0161>,\n    val recovered: Boolean,\n    val recoveryStrategy: String?,\n)\n\n/** Private, bounded diagnostic storage. No permission, server, database or background worker. */\nobject FailedCardTechnicalCaptureStore0161 {\n    private const val DIRECTORY = "failed-card-captures-0161"\n    private const val MAX_CAPTURES = 6\n    private const val MAX_AGE_MILLIS = 24L * 60L * 60L * 1_000L\n\n    fun save(\n        context: Context,\n        snapshot: FailedCardTechnicalSnapshot0161,\n        bitmap: Bitmap?,\n    ) {\n        val directory = File(context.filesDir, DIRECTORY)\n        if (!directory.exists() && !directory.mkdirs()) return\n        trim(directory, snapshot.createdAtMillis)\n        val baseName = "${snapshot.createdAtMillis}-${snapshot.signature.take(24)}"\n        val textFile = File(directory, "$baseName.txt")\n        val tempText = File(directory, "$baseName.txt.tmp")\n        tempText.writeText(buildText(snapshot), Charsets.UTF_8)\n        if (!tempText.renameTo(textFile)) {\n            textFile.writeText(tempText.readText(Charsets.UTF_8), Charsets.UTF_8)\n            tempText.delete()\n        }\n        if (bitmap != null && !bitmap.isRecycled) {\n            val imageFile = File(directory, "$baseName.jpg")\n            runCatching {\n                imageFile.outputStream().buffered().use { output ->\n                    bitmap.compress(Bitmap.CompressFormat.JPEG, 68, output)\n                }\n            }.onFailure { imageFile.delete() }\n        }\n        trim(directory, snapshot.createdAtMillis)\n    }\n\n    private fun buildText(snapshot: FailedCardTechnicalSnapshot0161): String = buildString {\n        appendLine("ROTA CERTA FAILED CARD CAPTURE 0.1.161")\n        appendLine("signature=${snapshot.signature}")\n        appendLine("package=${snapshot.packageName}")\n        appendLine("window=${snapshot.windowId}")\n        appendLine("createdAt=${snapshot.createdAtMillis}")\n        appendLine("recovered=${snapshot.recovered}")\n        appendLine("strategy=${snapshot.recoveryStrategy.orEmpty()}")\n        appendLine("--- ACCESSIBILITY ---")\n        appendLine(redactPhone(snapshot.accessibilityText).take(12_000))\n        appendLine("--- OCR ---")\n        appendLine(redactPhone(snapshot.ocrText).take(12_000))\n        appendLine("--- NODES ---")\n        snapshot.nodes.take(160).forEach { node ->\n            appendLine(\n                listOf(\n                    node.top,\n                    node.left,\n                    node.bottom,\n                    node.right,\n                    sanitize(node.className),\n                    sanitize(node.viewId),\n                    redactPhone(node.text),\n                ).joinToString("\\t"),\n            )\n        }\n    }\n\n    private fun sanitize(value: String): String = value\n        .replace(\'\\n\', \' \')\n        .replace(\'\\r\', \' \')\n        .replace(\'\\t\', \' \')\n        .trim()\n        .take(240)\n\n    private fun redactPhone(value: String): String = PHONE_REGEX.replace(value) { "[telefone mascarado]" }\n\n    private fun trim(directory: File, nowMillis: Long) {\n        val files = directory.listFiles().orEmpty().toList()\n        files.filter { file ->\n            nowMillis >= file.lastModified() && nowMillis - file.lastModified() > MAX_AGE_MILLIS\n        }.forEach(File::delete)\n\n        val captureGroups = directory.listFiles().orEmpty()\n            .groupBy { file -> file.name.substringBeforeLast(\'.\') }\n            .entries\n            .sortedByDescending { (_, groupFiles) -> groupFiles.maxOfOrNull(File::lastModified) ?: 0L }\n        captureGroups.drop(MAX_CAPTURES).flatMap { it.value }.forEach(File::delete)\n    }\n\n    private val PHONE_REGEX = Regex(\n        "(?<!\\\\d)(?:\\\\+?55\\\\s*)?(?:\\\\(?\\\\d{2}\\\\)?\\\\s*)?(?:9\\\\s*)?\\\\d{4}[\\\\s-]?\\\\d{4}(?!\\\\d)",\n        RegexOption.IGNORE_CASE,\n    )\n}\n', encoding="utf-8")
(test / 'FailedCardRecovery0161Test.kt').write_text('package br.com.mapeiaia.rotacerta\n\nimport org.junit.Assert.assertEquals\nimport org.junit.Assert.assertFalse\nimport org.junit.Assert.assertNotNull\nimport org.junit.Assert.assertNull\nimport org.junit.Assert.assertTrue\nimport org.junit.Test\n\nclass FailedCardRecovery0161Test {\n    private val packageName = "sinet.startup.indriver"\n    private val selected = setOf(packageName)\n\n    @Test\n    fun recognizedCardNeverStartsExtraCapture() {\n        val gate = FailedCardAutoCaptureGate0161()\n        assertFalse(\n            gate.tryStart(\n                signature = "recognized",\n                probableCard = true,\n                parserActive = true,\n                routeInFlight = false,\n                hasDecision = false,\n                nowMillis = 1_000L,\n            ),\n        )\n    }\n\n    @Test\n    fun failedProbableCardStartsOnlyOnceAndNewCardIsReleased() {\n        val gate = FailedCardAutoCaptureGate0161()\n        assertTrue(gate.tryStart("card-a", true, false, false, false, 1_000L))\n        gate.finish("card-a", 1_200L)\n        assertFalse(gate.tryStart("card-a", true, false, false, false, 2_000L))\n        assertTrue(gate.tryStart("card-b", true, false, false, false, 2_100L))\n    }\n\n    @Test\n    fun timeoutReleasesADeadScreenshotReservation() {\n        val gate = FailedCardAutoCaptureGate0161(lockTimeoutMillis = 500L)\n        assertTrue(gate.tryStart("dead", true, false, false, false, 100L))\n        assertFalse(gate.tryStart("dead", true, false, false, false, 400L))\n        assertTrue(gate.tryStart("dead", true, false, false, false, 700L))\n    }\n\n    @Test\n    fun routeOrDecisionAlwaysHasPriority() {\n        val gate = FailedCardAutoCaptureGate0161()\n        assertFalse(gate.tryStart("route", true, false, true, false, 1_000L))\n        assertFalse(gate.tryStart("decision", true, false, false, true, 1_000L))\n    }\n\n    @Test\n    fun accessibilityAndOcrAreMergedToRecoverTwoAddresses() {\n        val result = FailedCardRecoveryEngine0161.recover(\n            packageName = packageName,\n            savedPackages = selected,\n            accessibilityText = """\n                Pedido de viagem\n                10 min\n                5,4 km\n                R$ 32,00\n                Origem\n                Rua Miguel Martins Lisboa, 140 - São Paulo - SP\n            """.trimIndent(),\n            ocrText = """\n                Destino\n                Avenida Nordestina, 6680 - São Paulo - SP\n            """.trimIndent(),\n            nodes = emptyList(),\n        )\n\n        assertNotNull(result)\n        assertEquals("Rua Miguel Martins Lisboa, 140 - São Paulo - SP", result?.fields?.pickup)\n        assertEquals("Avenida Nordestina, 6680 - São Paulo - SP", result?.fields?.destination)\n        assertEquals("acessibilidade_mais_ocr", result?.strategy)\n    }\n\n    @Test\n    fun labeledLocationsCreateAHighConfidenceLocalModel() {\n        val text = """\n            Pedido de viagem\n            12 min\n            8,1 km\n            R$ 41,00\n            Origem\n            Jardim Aurora, São Paulo - SP\n            Destino\n            Parque Guaianazes, São Paulo - SP\n            Aceitar\n        """.trimIndent()\n\n        val result = FailedCardRecoveryEngine0161.recover(\n            packageName = packageName,\n            savedPackages = selected,\n            accessibilityText = text,\n            ocrText = "",\n            nodes = emptyList(),\n        )\n\n        assertNotNull(result)\n        assertEquals("marcadores_confirmados", result?.strategy)\n        assertTrue((result?.confidence ?: 0) >= 90)\n        assertNotNull(result?.modelCandidate)\n        val fieldsFromModel = FailedCardRecoveryEngine0161.recoverWithModel(text, result!!.modelCandidate!!)\n        assertEquals("Jardim Aurora, São Paulo - SP", fieldsFromModel?.pickup)\n        assertEquals("Parque Guaianazes, São Paulo - SP", fieldsFromModel?.destination)\n    }\n\n    @Test\n    fun ambiguousDestinationNeverInventsARoute() {\n        val result = FailedCardRecoveryEngine0161.recover(\n            packageName = packageName,\n            savedPackages = selected,\n            accessibilityText = """\n                Pedido de viagem\n                9 min\n                7 km\n                R$ 35,00\n                Origem\n                Jardim Aurora, São Paulo - SP\n                Destino\n                Parque Guaianazes, São Paulo - SP\n                Destino\n                Vila Matilde, São Paulo - SP\n            """.trimIndent(),\n            ocrText = "",\n            nodes = emptyList(),\n        )\n\n        assertNull(result)\n    }\n\n    @Test\n    fun androidAutoAndSystemUiCannotReplaceASelectedRoot() {\n        assertTrue(\n            TransientOverlayPackagePolicy0161.shouldPreferSelectedRoot(\n                eventPackageName = "com.google.android.projection.gearhead",\n                rootPackageName = packageName,\n                selectedPackages = selected,\n                ownPackageName = "br.com.mapeiaia.rotacerta",\n            ),\n        )\n        assertTrue(\n            TransientOverlayPackagePolicy0161.shouldPreferSelectedRoot(\n                eventPackageName = "com.android.systemui",\n                rootPackageName = packageName,\n                selectedPackages = selected,\n                ownPackageName = "br.com.mapeiaia.rotacerta",\n            ),\n        )\n        assertFalse(\n            TransientOverlayPackagePolicy0161.shouldPreferSelectedRoot(\n                eventPackageName = "com.openai.chatgpt",\n                rootPackageName = "com.openai.chatgpt",\n                selectedPackages = selected,\n                ownPackageName = "br.com.mapeiaia.rotacerta",\n            ),\n        )\n    }\n\n    @Test\n    fun signatureChangesWhenCardOrWindowChanges() {\n        val first = FailedCardRecoveryEngine0161.signature(packageName, 10, "Origem A\\nDestino B", emptyList())\n        val same = FailedCardRecoveryEngine0161.signature(packageName, 10, "Origem A\\nDestino B", emptyList())\n        val otherCard = FailedCardRecoveryEngine0161.signature(packageName, 10, "Origem A\\nDestino C", emptyList())\n        val otherWindow = FailedCardRecoveryEngine0161.signature(packageName, 11, "Origem A\\nDestino B", emptyList())\n        assertEquals(first, same)\n        assertTrue(first != otherCard)\n        assertTrue(first != otherWindow)\n    }\n}\n', encoding="utf-8")
(test / 'FailedCardAutoCaptureContract0161Test.kt').write_text('package br.com.mapeiaia.rotacerta\n\nimport org.junit.Assert.assertFalse\nimport org.junit.Assert.assertTrue\nimport org.junit.Test\nimport java.io.File\n\nclass FailedCardAutoCaptureContract0161Test {\n    @Test\n    fun capturePathIsSilentAndSeparatedFromValueAndFinance() {\n        val source = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()\n        val start = source.indexOf("private fun requestScreenshotAnalysis")\n        val end = source.indexOf("private fun collectVisibleText", start)\n        val section = source.substring(start, end)\n        assertTrue(section.contains("BUBBLE_FAILED_CARD_CAPTURE_STARTED"))\n        assertTrue(section.contains("FailedCardTechnicalCaptureStore0161.save"))\n        assertFalse(section.contains("speak("))\n        assertFalse(section.contains("textToSpeech"))\n        assertFalse(section.contains("announceForAccessibility"))\n        assertFalse(section.contains("FinancialActivity"))\n        assertFalse(section.contains("PassengerValue"))\n        assertFalse(section.contains("showOverlay("))\n        assertFalse(section.contains("resetToIdle("))\n    }\n\n    @Test\n    fun implementationAddsNoContinuousLoopOrBackgroundService() {\n        val recovery = File("src/main/java/br/com/mapeiaia/rotacerta/FailedCardRecovery0161.kt").readText()\n        val store = File("src/main/java/br/com/mapeiaia/rotacerta/FailedCardCaptureStore0161.kt").readText()\n        assertFalse(recovery.contains("while ("))\n        assertFalse(store.contains("while ("))\n        assertFalse(recovery.contains("Service()"))\n        assertFalse(store.contains("Service()"))\n        assertTrue(store.contains("MAX_CAPTURES = 6"))\n        assertTrue(store.contains("MAX_AGE_MILLIS"))\n    }\n\n    @Test\n    fun farolKeepsRoutePriorityAndBitmapIsAlwaysRecycled() {\n        val source = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()\n        assertTrue(source.contains("routeInFlight = universalRouteJob?.isActive == true"))\n        assertTrue(source.contains("bitmap0161?.takeUnless"))\n        assertTrue(source.contains("failedCardAutoCaptureGate0161.finish(captureSignature0161)"))\n        assertTrue(source.contains("screenshotInProgress.set(false)"))\n        assertTrue(source.contains("TransientOverlayPackagePolicy0161.shouldPreferSelectedRoot"))\n    }\n}\n', encoding="utf-8")

gradle_path = root / "app/build.gradle.kts"
gradle = gradle_path.read_text(encoding="utf-8")
if 'versionCode = 5210' not in gradle or 'versionName = "0.1.160"' not in gradle:
    raise SystemExit("0.1.161 version anchors from 0.1.160 not found")
gradle = gradle.replace('versionCode = 5210', 'versionCode = 5220', 1)
gradle = gradle.replace('versionName = "0.1.160"', 'versionName = "0.1.161"', 1)
gradle_path.write_text(gradle, encoding="utf-8")
print("0.1.161: isolated failed-card auto capture, recovery and transient overlay guard applied")
