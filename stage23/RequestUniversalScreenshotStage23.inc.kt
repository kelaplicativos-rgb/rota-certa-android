    private fun isStage23OcrDemandFresh(
        tokenStage23: Long,
        demandStage23: FarolVisualIdentityStage23.OcrDemand,
        serialStage19: Long,
    ): Boolean =
        stage23OcrGate.isCurrent(tokenStage23, demandStage23) &&
            stage23VisualGate.currentGeneration() == demandStage23.visualGeneration &&
            stage23VisualGate.currentHash() == demandStage23.snapshotHash &&
            serialStage19 == stage19OcrSerial &&
            WorkModePolicy0162.isEnabled(currentSettings)

    private fun requestUniversalScreenshotStage19(
        eventPackageStage19: String?,
        cycleIdStage20: Long? = null,
        rerunDemandStage23: FarolVisualIdentityStage23.OcrDemand? = null,
    ) {
        if (!serviceReady || !WorkModePolicy0162.isEnabled(currentSettings) || bubbleGestureActive) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        val demandStage23 = rerunDemandStage23 ?: FarolVisualIdentityStage23.OcrDemand(
            visualGeneration = stage23VisualGate.currentGeneration(),
            snapshotHash = stage23VisualGate.currentHash() ?: 0L,
            packageHint = eventPackageStage19,
            cycleId = cycleIdStage20,
        )
        FarolVisualIdentityStage23.Metrics.increment("ocrRequests")
        val requestStage23 = if (rerunDemandStage23 != null) {
            stage23OcrGate.installRerun(demandStage23)
        } else {
            stage23OcrGate.request(demandStage23)
        }
        if (!requestStage23.startNow) {
            FarolVisualIdentityStage23.Metrics.increment("ocrDeferred")
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
        val serialStage19 = ++stage19OcrSerial
        val tokenStage23 = requestStage23.token
        FarolForensicTraceStage20.ocrStage(
            SystemClock.elapsedRealtimeNanos(),
            serialStage19,
            "REQUEST",
            cycleIdStage20,
            "package=${eventPackageStage19.orEmpty()}; s23token=$tokenStage23; visual_generation=${demandStage23.visualGeneration}; visual_snapshot_hash=${demandStage23.snapshotHash}; reason=${requestStage23.reason}",
        )
        val visualWindowIdStage19 = stage19ActiveWindowId ?: runCatching { rootInActiveWindow?.windowId }.getOrNull() ?: 0
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
            val usefulStage23 = serviceReady && WorkModePolicy0162.isEnabled(currentSettings) &&
                stage23VisualGate.currentGeneration() == rerunStage23.visualGeneration &&
                stage23VisualGate.currentHash() == rerunStage23.snapshotHash
            if (!usefulStage23) {
                FarolForensicTraceStage20.note(
                    SystemClock.elapsedRealtimeNanos(),
                    "S23_OCR_RERUN_DROPPED_STALE_DEMAND",
                    rerunStage23.cycleId,
                    details = "visual_generation=${rerunStage23.visualGeneration}; visual_snapshot_hash=${rerunStage23.snapshotHash}; current_generation=${stage23VisualGate.currentGeneration()}; current_hash=${stage23VisualGate.currentHash() ?: 0L}",
                )
                return
            }
            FarolVisualIdentityStage23.Metrics.increment("ocrReruns")
            requestUniversalScreenshotStage19(
                rerunStage23.packageHint,
                rerunStage23.cycleId,
                rerunStage23,
            )
        }

        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        FarolForensicTraceStage20.ocrStage(
                            SystemClock.elapsedRealtimeNanos(), serialStage19, "SCREENSHOT_CALLBACK", cycleIdStage20,
                            "visual_generation=${demandStage23.visualGeneration}; visual_snapshot_hash=${demandStage23.snapshotHash}",
                        )
                        scope.launch {
                            var bitmapStage19: Bitmap? = null
                            try {
                                if (!isStage23OcrDemandFresh(tokenStage23, demandStage23, serialStage19)) {
                                    FarolVisualIdentityStage23.Metrics.increment("ocrStaleBeforeBitmap")
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
                                FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "BITMAP_CONVERT_END", cycleIdStage20)
                                if (!isStage23OcrDemandFresh(tokenStage23, demandStage23, serialStage19)) {
                                    FarolVisualIdentityStage23.Metrics.increment("ocrStaleBeforeExtract")
                                    FarolForensicTraceStage20.ocrStage(
                                        SystemClock.elapsedRealtimeNanos(), serialStage19, "STALE_BEFORE_EXTRACT", cycleIdStage20,
                                        "stage23_visual_generation=${demandStage23.visualGeneration}; stage23_visual_snapshot_hash=${demandStage23.snapshotHash}",
                                    )
                                    return@launch
                                }

                                val ocrStartedNsStage20 = SystemClock.elapsedRealtimeNanos()
                                val structuredStage19 = withContext(Dispatchers.Default) {
                                    ocrService.extractStructuredText(bitmapStage19)
                                }
                                val extractEndedNsStage20 = SystemClock.elapsedRealtimeNanos()
                                FarolForensicTraceStage20.ocrStage(
                                    extractEndedNsStage20,
                                    serialStage19,
                                    "EXTRACT_END",
                                    cycleIdStage20,
                                    "extract_us=${(extractEndedNsStage20 - ocrStartedNsStage20).coerceAtLeast(0L) / 1000L}; blocks=${structuredStage19.blocks.size}; stage23_non_cancelable_call=true",
                                )
                                if (!isStage23OcrDemandFresh(tokenStage23, demandStage23, serialStage19)) {
                                    FarolVisualIdentityStage23.Metrics.increment("ocrStaleAfterExtract")
                                    FarolForensicTraceStage20.ocrStage(
                                        SystemClock.elapsedRealtimeNanos(), serialStage19, "STALE_AFTER_EXTRACT", cycleIdStage20,
                                        "latestSerial=$stage19OcrSerial; stage23_non_cancelable_extract_completed_stale=true; visual_generation=${demandStage23.visualGeneration}; visual_snapshot_hash=${demandStage23.snapshotHash}",
                                    )
                                    return@launch
                                }

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
                                            windowId = visualWindowIdStage19,
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
                                FarolForensicTraceStage20.ocrStage(
                                    SystemClock.elapsedRealtimeNanos(), serialStage19, "EVALUATE_END", cycleIdStage20,
                                    "candidate=${evaluationStage19 != null}; visual_generation=${demandStage23.visualGeneration}; visual_snapshot_hash=${demandStage23.snapshotHash}",
                                )
                                if (!isStage23OcrDemandFresh(tokenStage23, demandStage23, serialStage19)) {
                                    FarolVisualIdentityStage23.Metrics.increment("ocrStaleAfterEvaluate")
                                    FarolForensicTraceStage20.ocrStage(
                                        SystemClock.elapsedRealtimeNanos(), serialStage19, "STALE_AFTER_EVALUATE", cycleIdStage20,
                                        "latestSerial=$stage19OcrSerial; visual_generation=${demandStage23.visualGeneration}; visual_snapshot_hash=${demandStage23.snapshotHash}",
                                    )
                                    return@launch
                                }
                                stage19VisualVerificationPending = false
                                if (evaluationStage19 != null) {
                                    processUniversalVisualStage19(evaluationStage19, "Ocr", cycleIdStage20)
                                } else {
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
                                val completionStage23 = stage23OcrGate.complete(tokenStage23)
                                stage19OcrRerunRequested = false
                                rerunIfUsefulStage23(completionStage23)
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        FarolForensicTraceStage20.ocrStage(
                            SystemClock.elapsedRealtimeNanos(), serialStage19, "SCREENSHOT_FAILURE", cycleIdStage20,
                            "errorCode=$errorCode; visual_generation=${demandStage23.visualGeneration}; visual_snapshot_hash=${demandStage23.snapshotHash}",
                        )
                        screenshotInProgress.set(false)
                        if (stage23VisualGate.currentGeneration() == demandStage23.visualGeneration &&
                            stage23VisualGate.currentHash() == demandStage23.snapshotHash
                        ) {
                            stage19VisualVerificationPending = true
                            stage23VisualGate.invalidateForExplicitRecovery(demandStage23.snapshotHash)
                        }
                        val completionStage23 = stage23OcrGate.complete(tokenStage23)
                        stage19OcrRerunRequested = false
                        rerunIfUsefulStage23(completionStage23)
                    }
                },
            )
        }.onFailure {
            screenshotInProgress.set(false)
            if (stage23VisualGate.currentGeneration() == demandStage23.visualGeneration &&
                stage23VisualGate.currentHash() == demandStage23.snapshotHash
            ) {
                stage19VisualVerificationPending = true
                stage23VisualGate.invalidateForExplicitRecovery(demandStage23.snapshotHash)
            }
            val completionStage23 = stage23OcrGate.complete(tokenStage23)
            stage19OcrRerunRequested = false
            rerunIfUsefulStage23(completionStage23)
        }
    }

