    private fun handleUniversalVisualEventStage19(eventPackageStage19: String?): Boolean {
        if (!serviceReady || !WorkModePolicy0162.isEnabled(currentSettings)) return false
        val blocksStage19 = collectUniversalAccessibilityBlocksStage19()
        val evaluationStage19 = FarolLatencyProbeStage9.measureValue(
            stage = "STAGE19_UNIVERSAL_VISUAL_ACCESSIBILITY",
            source = "Accessibility",
        ) {
            FarolUniversalVisualPipelineStage19.evaluate(blocksStage19)
        }
        if (evaluationStage19 != null) {
            stage19VisualVerificationPending = false
            stage19OcrSerial += 1L
            stage19OcrRerunRequested = false
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                processUniversalVisualStage19(evaluationStage19, "Accessibility")
            }
        } else {
            stage19VisualVerificationPending = true
            requestUniversalScreenshotStage19(eventPackageStage19)
        }
        return true
    }

    private fun collectUniversalAccessibilityBlocksStage19(): List<FarolUniversalVisualPipelineStage19.VisualBlock> {
        val outputStage19 = ArrayList<FarolCardBlock0188>(160)
        val budgetStage19 = intArrayOf(0)
        val visibleWindowsStage19 = runCatching { windows }.getOrDefault(emptyList())
            .sortedByDescending { runCatching { it.layer }.getOrDefault(0) }
        for (windowStage19 in visibleWindowsStage19) {
            if (budgetStage19[0] >= MAX_ACCESSIBILITY_NODES_0167 || outputStage19.size >= 160) break
            val rootStage19 = runCatching { windowStage19.root }.getOrNull() ?: continue
            val packageStage19 = safeNodePackageName0185(rootStage19) ?: "visual.unknown"
            val windowIdStage19 = runCatching { windowStage19.id }.getOrDefault(-1)
            val layerStage19 = runCatching { windowStage19.layer }.getOrDefault(0)
            collectAccessibilitySubtreeBlocks0188(
                node0188 = rootStage19,
                id0188 = "stage19:$windowIdStage19",
                parentId0188 = null,
                depth0188 = 0,
                packageName0188 = packageStage19,
                windowId0188 = windowIdStage19,
                windowLayer0188 = layerStage19,
                output0188 = outputStage19,
                budget0188 = budgetStage19,
            )
        }
        return outputStage19.map { blockStage19 ->
            FarolUniversalVisualPipelineStage19.VisualBlock(
                id = blockStage19.id,
                parentId = blockStage19.parentId,
                metadataPackageName = blockStage19.packageName,
                windowId = blockStage19.windowId,
                windowLayer = blockStage19.windowLayer,
                depth = blockStage19.depth,
                text = blockStage19.text,
                source = FarolUniversalVisualPipelineStage19.Source.Accessibility,
                left = blockStage19.left,
                top = blockStage19.top,
                right = blockStage19.right,
                bottom = blockStage19.bottom,
                syntheticRoot = blockStage19.syntheticRoot,
            )
        }
    }

    private fun requestUniversalScreenshotStage19(eventPackageStage19: String?) {
        if (!serviceReady || !WorkModePolicy0162.isEnabled(currentSettings) || bubbleGestureActive) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val serialStage19 = ++stage19OcrSerial
        if (!screenshotInProgress.compareAndSet(false, true)) {
            stage19OcrRerunRequested = true
            return
        }
        stage19OcrRerunRequested = false
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        scope.launch {
                            var bitmapStage19: Bitmap? = null
                            try {
                                bitmapStage19 = screenshot.toSoftwareBitmap() ?: return@launch
                                val structuredStage19 = withContext(Dispatchers.Default) {
                                    ocrService.extractStructuredText(bitmapStage19)
                                }
                                if (serialStage19 != stage19OcrSerial || !WorkModePolicy0162.isEnabled(currentSettings)) return@launch
                                val fragmentsStage19 = structuredStage19.blocks.take(120).mapIndexedNotNull { indexStage19, blockStage19 ->
                                    blockStage19.text.takeIf(String::isNotBlank)?.let {
                                        FarolSpatialFragment0189(
                                            id = "stage19-ocr:$serialStage19/$indexStage19",
                                            text = it,
                                            left = blockStage19.left,
                                            top = blockStage19.top,
                                            right = blockStage19.right,
                                            bottom = blockStage19.bottom,
                                        )
                                    }
                                }
                                val blocksStage19 = FarolVisualPriority0189.cluster("stage19-ocr:$serialStage19", fragmentsStage19)
                                    .map { groupStage19 ->
                                        FarolUniversalVisualPipelineStage19.VisualBlock(
                                            id = groupStage19.id,
                                            metadataPackageName = eventPackageStage19,
                                            windowId = 0,
                                            windowLayer = Int.MAX_VALUE,
                                            depth = 1,
                                            text = groupStage19.text,
                                            source = FarolUniversalVisualPipelineStage19.Source.Ocr,
                                            left = groupStage19.left,
                                            top = groupStage19.top,
                                            right = groupStage19.right,
                                            bottom = groupStage19.bottom,
                                        )
                                    }
                                val evaluationStage19 = withContext(Dispatchers.Default) {
                                    FarolUniversalVisualPipelineStage19.evaluate(blocksStage19)
                                }
                                if (serialStage19 != stage19OcrSerial) return@launch
                                stage19VisualVerificationPending = false
                                if (evaluationStage19 != null) {
                                    processUniversalVisualStage19(evaluationStage19, "Ocr")
                                } else {
                                    hardClearUniversalTwoAddress(
                                        reason = "Snapshot visual atual confirmado sem dois endereços coerentes.",
                                        keepWaitingYellow = true,
                                    )
                                }
                            } finally {
                                bitmapStage19?.takeUnless(Bitmap::isRecycled)?.recycle()
                                screenshotInProgress.set(false)
                                if (stage19OcrRerunRequested && WorkModePolicy0162.isEnabled(currentSettings)) {
                                    stage19OcrRerunRequested = false
                                    requestUniversalScreenshotStage19(eventPackageStage19)
                                }
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        screenshotInProgress.set(false)
                        stage19VisualVerificationPending = false
                        if (stage19OcrRerunRequested && WorkModePolicy0162.isEnabled(currentSettings)) {
                            stage19OcrRerunRequested = false
                            requestUniversalScreenshotStage19(eventPackageStage19)
                        }
                    }
                },
            )
        }.onFailure {
            screenshotInProgress.set(false)
            stage19VisualVerificationPending = false
        }
    }

    private suspend fun processUniversalVisualStage19(
        evaluationStage19: FarolUniversalVisualPipelineStage19.Evaluation,
        sourceStage19: String,
    ) {
        if (!serviceReady || !WorkModePolicy0162.isEnabled(currentSettings)) return
        val windowChangedStage19 = stage19ActiveWindowId != evaluationStage19.windowId ||
            stage19ActiveBlockId != evaluationStage19.blockId
        val visualChangedStage19 = universalActiveAddressSignature != evaluationStage19.addressSignature ||
            lastSnapshotHash != evaluationStage19.screenHash
        if (windowChangedStage19) universalWindowGeneration += 1L
        if (visualChangedStage19) {
            universalScreenGeneration += 1L
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

        if (!visualChangedStage19 && (lastAnalyzedHash == evaluationStage19.screenHash || universalRouteJob?.isActive == true)) return

        val fieldsStage19 = RideFields(
            pickup = evaluationStage19.pickup,
            destination = evaluationStage19.destination,
        )
        val settingsStage19 = currentSettings
        val targetsStage19 = fastWorkRegionTargetsChecklist13(settingsStage19)
        rememberBubbleReason("stage19_visual_destination", "Dois endereços atuais confirmados; último endereço é o destino final.")
        if (currentRadarColor != RadarColor.Orange || currentDistanceKm != null) {
            showOverlay(RadarColor.Orange, distanceKm = null)
        }
        if (targetsStage19.destinations.isEmpty()) return

        val bindingStage19 = FarolUniversalVisualPipelineStage19.Binding(
            screenGeneration = universalScreenGeneration,
            windowGeneration = universalWindowGeneration,
            screenHash = evaluationStage19.screenHash,
            addressSignature = evaluationStage19.addressSignature,
        )
        val cachedStage19 = googleMapsService.cachedDrivingDistancesFromAddressKm(
            originAddress = fieldsStage19.destination.orEmpty(),
            destinations = targetsStage19.destinations,
        )
        if (cachedStage19 != null) {
            if (!isStage19BindingFresh(bindingStage19)) return
            val resultStage19 = decideFastWorkRegionChecklist13(
                snapshotText = evaluationStage19.analysisText,
                fields = fieldsStage19,
                settings = settingsStage19,
                targets = targetsStage19,
                routeDistances = cachedStage19,
            )
            bubblePrefs.edit().putString("fast_farol_last_path", "stage19_cache_exato").apply()
            applyUniversalTwoAddressResultStage19(resultStage19, bindingStage19)
            return
        }

        bubblePrefs.edit().putString("fast_farol_last_path", "stage19_rota_google").apply()
        universalRouteJob = scope.launch {
            analyzeUniversalTwoAddressStage19(
                snapshotTextStage19 = evaluationStage19.analysisText,
                fieldsStage19 = fieldsStage19,
                bindingStage19 = bindingStage19,
            )
        }
        UnifiedDebugEventStore.record(
            "STAGE19_VISUAL_ROUTE_STARTED",
            null,
            "source=$sourceStage19; destination=${fieldsStage19.destination.orEmpty()}; screenGeneration=${bindingStage19.screenGeneration}; windowGeneration=${bindingStage19.windowGeneration}",
        )
    }

    private fun isStage19BindingFresh(bindingStage19: FarolUniversalVisualPipelineStage19.Binding): Boolean =
        serviceReady && WorkModePolicy0162.isEnabled(currentSettings) &&
            FarolUniversalVisualPipelineStage19.bindingMatchesCurrent(
                binding = bindingStage19,
                currentScreenGeneration = universalScreenGeneration,
                currentWindowGeneration = universalWindowGeneration,
                currentScreenHash = lastSnapshotHash,
                currentAddressSignature = universalActiveAddressSignature,
                visualVerificationPending = stage19VisualVerificationPending,
            )

    private suspend fun analyzeUniversalTwoAddressStage19(
        snapshotTextStage19: String,
        fieldsStage19: RideFields,
        bindingStage19: FarolUniversalVisualPipelineStage19.Binding,
    ) {
        if (!isStage19BindingFresh(bindingStage19)) return
        val settingsStage19 = currentSettings
        val apiKeyStage19 = GoogleMapsApiKeyPolicy.effective(
            settingsStage19.googleMapsApiKey,
            BuildConfig.GOOGLE_MAPS_API_KEY,
        )
        if (apiKeyStage19.isBlank()) return
        val targetsStage19 = fastWorkRegionTargetsChecklist13(settingsStage19)
        if (targetsStage19.destinations.isEmpty()) return
        val distancesStage19 = googleMapsService.drivingDistancesFromAddressKm(
            originAddress = fieldsStage19.destination.orEmpty(),
            destinations = targetsStage19.destinations,
            apiKey = apiKeyStage19,
        )
        if (!isStage19BindingFresh(bindingStage19)) return
        val resultStage19 = decideFastWorkRegionChecklist13(
            snapshotText = snapshotTextStage19,
            fields = fieldsStage19,
            settings = settingsStage19,
            targets = targetsStage19,
            routeDistances = distancesStage19,
        )
        applyUniversalTwoAddressResultStage19(resultStage19, bindingStage19)
    }

    private suspend fun applyUniversalTwoAddressResultStage19(
        resultStage19: AnalysisResult,
        bindingStage19: FarolUniversalVisualPipelineStage19.Binding,
    ) {
        if (!isStage19BindingFresh(bindingStage19)) return
        val colorStage19 = when (resultStage19.recommendation) {
            Recommendation.GoodRide -> RadarColor.Green
            Recommendation.OutsideRadius -> RadarColor.Red
            Recommendation.InsufficientData -> RadarColor.Orange
        }
        val distanceStage19 = resultStage19.nearestConfiguredDistanceKm()
        lastAnalyzedHash = bindingStage19.screenHash
        rememberBubbleReason("stage19_visual_result", resultStage19.reason)
        showOverlay(colorStage19, distanceStage19)
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

