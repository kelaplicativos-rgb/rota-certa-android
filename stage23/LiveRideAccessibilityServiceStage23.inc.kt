    private data class Stage23AccessibilitySnapshot(
        val blocks: List<FarolUniversalVisualPipelineStage19.VisualBlock>,
        val snapshot: FarolVisualIdentityStage23.Snapshot,
        val stats: FarolVisualIdentityStage23.CollectionStats,
    )

    private data class Stage23TreeResult(
        val lines: List<String>,
        val completeContext: Boolean,
    )

    private fun handleUniversalVisualEventStage19(
        eventPackageStage19: String?,
        eventTypeStage20: Int,
        eventWindowIdStage20: Int,
    ): Boolean {
        if (!serviceReady || !WorkModePolicy0162.isEnabled(currentSettings)) return false
        if (bubbleGestureActive) return true // bubble_drag_accessibility_pause_0_1_116
        val eventStartedNsStage23 = SystemClock.elapsedRealtimeNanos()

        // Stage21 event fingerprint remains diagnostic only. Stage23 never lets package/type/time
        // suppress a real visual change; the visual snapshot below is the expensive-work authority.
        val eventDecisionStage21 = stage21EventGate.decide(
            packageName = eventPackageStage19,
            eventType = eventTypeStage20,
            windowId = eventWindowIdStage20,
            nowNs = eventStartedNsStage23,
            selfPackageName = "__stage23_visual_authority__",
            selfSuppressionUntilNs = 0L,
            expensiveWorkActive = screenshotInProgress.get() || universalRouteJob?.isActive == true,
        )
        if (!eventDecisionStage21.process) {
            FarolVisualIdentityStage23.Metrics.increment("eventCoalesced")
            FarolForensicTraceStage20.note(
                eventStartedNsStage23,
                "S21_EVENT_COALESCED",
                details = "reason=${eventDecisionStage21.reason}; stage23=telemetry_only; package=${eventPackageStage19.orEmpty()}; eventType=$eventTypeStage20; window=$eventWindowIdStage20",
            )
        }

        val cycleIdStage20 = FarolForensicTraceStage20.beginCycle(
            nowNs = eventStartedNsStage23,
            packageName = eventPackageStage19,
            eventType = eventTypeStage20,
            eventWindowId = eventWindowIdStage20,
        )
        stage20LastCycleId = cycleIdStage20
        val collectStartedNsStage23 = SystemClock.elapsedRealtimeNanos()
        FarolForensicTraceStage20.accessibilityCollectStarted(cycleIdStage20, collectStartedNsStage23)
        val collectionStage23 = collectUniversalAccessibilitySnapshotStage23()
        val collectEndedNsStage23 = SystemClock.elapsedRealtimeNanos()
        val visualDecisionStage23 = stage23VisualGate.observe(collectionStage23.snapshot.hash)
        FarolVisualIdentityStage23.Metrics.recordCollection(
            "Accessibility",
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
            details = "source=Accessibility; visible_windows_total=${collectionStage23.stats.visibleWindowsTotal}; windows_traversed=${collectionStage23.stats.windowsTraversed}; windows_skipped_self=${collectionStage23.stats.windowsSkippedSelf}; windows_skipped_lower_layer=${collectionStage23.stats.windowsSkippedLowerLayer}; blocks_visited=${collectionStage23.stats.blocksVisited}; blocks_emitted=${collectionStage23.stats.blocksEmitted}; early_exit_window=${collectionStage23.stats.earlyExitWindow ?: -1}; early_exit_reason=${collectionStage23.stats.earlyExitReason}; visual_snapshot_hash=${collectionStage23.snapshot.hash}",
        )

        if (!visualDecisionStage23.process) {
            FarolVisualIdentityStage23.Metrics.increment("unchangedVisualSkipped")
            if (normalizePackageName(eventPackageStage19) == normalizePackageName(packageName)) {
                FarolVisualIdentityStage23.Metrics.increment("selfEventSkipped")
            }
            FarolForensicTraceStage20.note(
                SystemClock.elapsedRealtimeNanos(),
                "S23_VISUAL_SNAPSHOT_UNCHANGED_SKIP",
                cycleIdStage20,
                details = "generation=${visualDecisionStage23.generation}; visual_snapshot_hash=${collectionStage23.snapshot.hash}; package=${eventPackageStage19.orEmpty()}",
            )
            return true
        }

        FarolForensicTraceStage20.note(
            SystemClock.elapsedRealtimeNanos(),
            "S23_VISUAL_SNAPSHOT_NEW",
            cycleIdStage20,
            details = "generation=${visualDecisionStage23.generation}; visual_snapshot_hash=${collectionStage23.snapshot.hash}; event_fingerprint=${eventPackageStage19.orEmpty()}|$eventTypeStage20|$eventWindowIdStage20; final_screen_hash=pending; final_address_signature=pending",
        )

        val evaluateStartedNsStage23 = SystemClock.elapsedRealtimeNanos()
        FarolForensicTraceStage20.accessibilityEvaluateStarted(cycleIdStage20, evaluateStartedNsStage23)
        val evaluationStage19 = FarolLatencyProbeStage9.measureValue(
            stage = "STAGE19_UNIVERSAL_VISUAL_ACCESSIBILITY",
            source = "Accessibility",
        ) {
            FarolUniversalVisualPipelineStage19.evaluate(collectionStage23.blocks)
        }
        val evaluateEndedNsStage23 = SystemClock.elapsedRealtimeNanos()
        FarolVisualIdentityStage23.Metrics.recordEvaluate(
            "Accessibility",
            evaluationStage19 != null,
            evaluateEndedNsStage23 - evaluateStartedNsStage23,
        )
        FarolForensicTraceStage20.accessibilityEvaluateFinished(
            cycleIdStage20,
            evaluateEndedNsStage23,
            evaluationStage19 != null,
        )
        stage23VisualGate.markProcessed(collectionStage23.snapshot.hash, visualDecisionStage23.generation)
        stage23ScheduleGate.satisfyDirect(visualDecisionStage23.generation, collectionStage23.snapshot.hash)

        if (evaluationStage19 != null) {
            FarolVisualIdentityStage23.Metrics.recordEventToCandidate(
                "Accessibility",
                SystemClock.elapsedRealtimeNanos() - eventStartedNsStage23,
            )
            stage19VisualVerificationPending = false
            stage19OcrSerial += 1L
            stage23OcrGate.cancelBecauseAccessibilityWon(visualDecisionStage23.generation, collectionStage23.snapshot.hash)
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

    private fun collectUniversalAccessibilityBlocksStage19(): List<FarolUniversalVisualPipelineStage19.VisualBlock> =
        collectUniversalAccessibilitySnapshotStage23().blocks

    private fun collectUniversalAccessibilitySnapshotStage23(): Stage23AccessibilitySnapshot {
        val outputStage23 = ArrayList<FarolCardBlock0188>(96)
        val budgetStage23 = intArrayOf(0)
        val visibleWindowsStage23 = runCatching { windows }.getOrDefault(emptyList())
            .sortedByDescending { runCatching { it.layer }.getOrDefault(0) }
        val snapshotSeedsStage23 = ArrayList<FarolVisualIdentityStage23.VisualSeed>(96)
        var windowsTraversedStage23 = 0
        var windowsSkippedSelfStage23 = 0
        var windowsSkippedLowerStage23 = 0
        var earlyExitWindowStage23: Int? = null
        var earlyExitReasonStage23 = "exhausted_or_budget"

        for ((indexStage23, windowStage23) in visibleWindowsStage23.withIndex()) {
            if (budgetStage23[0] >= MAX_ACCESSIBILITY_NODES_0167 || outputStage23.size >= 96) {
                windowsSkippedLowerStage23 += (visibleWindowsStage23.size - indexStage23).coerceAtLeast(0)
                earlyExitReasonStage23 = "global_budget"
                break
            }
            val rootStage23 = runCatching { windowStage23.root }.getOrNull() ?: continue
            val packageStage23 = safeNodePackageName0185(rootStage23) ?: "visual.unknown"
            if (normalizePackageName(packageStage23) == normalizePackageName(packageName)) {
                windowsSkippedSelfStage23 += 1
                continue
            }
            windowsTraversedStage23 += 1
            val windowIdStage23 = runCatching { windowStage23.id }.getOrDefault(-1)
            val layerStage23 = runCatching { windowStage23.layer }.getOrDefault(0)
            snapshotSeedsStage23 += FarolVisualIdentityStage23.VisualSeed(
                windowId = windowIdStage23,
                windowLayer = layerStage23,
                text = "stage23-window",
                syntheticRoot = false,
            )
            val beforeOutputStage23 = outputStage23.size
            val treeStage23 = collectAccessibilitySubtreeStage23(
                nodeStage23 = rootStage23,
                idStage23 = "stage23:$windowIdStage23",
                parentIdStage23 = null,
                depthStage23 = 0,
                packageNameStage23 = packageStage23,
                windowIdStage23 = windowIdStage23,
                windowLayerStage23 = layerStage23,
                outputStage23 = outputStage23,
                budgetStage23 = budgetStage23,
            )
            if (treeStage23.completeContext && outputStage23.size > beforeOutputStage23) {
                earlyExitWindowStage23 = windowIdStage23
                earlyExitReasonStage23 = "complete_same_context_inside_top_window"
                windowsSkippedLowerStage23 += (visibleWindowsStage23.size - indexStage23 - 1).coerceAtLeast(0)
                break
            }
        }

        val blocksStage23 = outputStage23.take(96).map { blockStage23 ->
            FarolUniversalVisualPipelineStage19.VisualBlock(
                id = blockStage23.id,
                parentId = blockStage23.parentId,
                metadataPackageName = blockStage23.packageName,
                windowId = blockStage23.windowId,
                windowLayer = blockStage23.windowLayer,
                depth = blockStage23.depth,
                text = blockStage23.text,
                source = FarolUniversalVisualPipelineStage19.Source.Accessibility,
                left = blockStage23.left,
                top = blockStage23.top,
                right = blockStage23.right,
                bottom = blockStage23.bottom,
                syntheticRoot = blockStage23.syntheticRoot,
            )
        }
        blocksStage23.forEach { blockStage23 ->
            snapshotSeedsStage23 += FarolVisualIdentityStage23.VisualSeed(
                windowId = blockStage23.windowId,
                windowLayer = blockStage23.windowLayer,
                text = blockStage23.text,
                left = blockStage23.left,
                top = blockStage23.top,
                right = blockStage23.right,
                bottom = blockStage23.bottom,
                syntheticRoot = blockStage23.syntheticRoot,
            )
        }
        val visualSnapshotStage23 = FarolVisualIdentityStage23.snapshot(snapshotSeedsStage23.asSequence())
        val statsStage23 = FarolVisualIdentityStage23.CollectionStats(
            visibleWindowsTotal = visibleWindowsStage23.size,
            windowsTraversed = windowsTraversedStage23,
            windowsSkippedSelf = windowsSkippedSelfStage23,
            windowsSkippedLowerLayer = windowsSkippedLowerStage23,
            blocksVisited = budgetStage23[0],
            blocksEmitted = blocksStage23.size,
            earlyExitWindow = earlyExitWindowStage23,
            earlyExitReason = earlyExitReasonStage23,
            visualSnapshotHash = visualSnapshotStage23.hash,
        )
        return Stage23AccessibilitySnapshot(blocksStage23, visualSnapshotStage23, statsStage23)
    }

    private fun collectAccessibilitySubtreeStage23(
        nodeStage23: AccessibilityNodeInfo,
        idStage23: String,
        parentIdStage23: String?,
        depthStage23: Int,
        packageNameStage23: String,
        windowIdStage23: Int,
        windowLayerStage23: Int,
        outputStage23: MutableList<FarolCardBlock0188>,
        budgetStage23: IntArray,
    ): Stage23TreeResult {
        if (budgetStage23[0] >= MAX_ACCESSIBILITY_NODES_0167 || outputStage23.size >= 96) {
            return Stage23TreeResult(emptyList(), false)
        }
        budgetStage23[0] += 1
        val linesStage23 = LinkedHashSet<String>(16)
        fun addLineStage23(valueStage23: CharSequence?) {
            valueStage23?.toString()?.trim()?.takeIf(String::isNotBlank)?.let(linesStage23::add)
        }
        addLineStage23(runCatching { nodeStage23.text }.getOrNull())
        addLineStage23(runCatching { nodeStage23.contentDescription }.getOrNull())

        val childCountStage23 = runCatching { nodeStage23.childCount }.getOrDefault(0).coerceIn(0, 64)
        var childProvidedCompleteContextStage23 = false
        for (indexStage23 in 0 until childCountStage23) {
            if (budgetStage23[0] >= MAX_ACCESSIBILITY_NODES_0167 || outputStage23.size >= 96) break
            val childStage23 = runCatching { nodeStage23.getChild(indexStage23) }.getOrNull() ?: continue
            val childResultStage23 = collectAccessibilitySubtreeStage23(
                nodeStage23 = childStage23,
                idStage23 = "$idStage23/$indexStage23",
                parentIdStage23 = idStage23,
                depthStage23 = depthStage23 + 1,
                packageNameStage23 = packageNameStage23,
                windowIdStage23 = windowIdStage23,
                windowLayerStage23 = windowLayerStage23,
                outputStage23 = outputStage23,
                budgetStage23 = budgetStage23,
            )
            childResultStage23.lines.forEach(linesStage23::add)
            if (childResultStage23.completeContext) {
                childProvidedCompleteContextStage23 = true
                break
            }
            val compactSameContextStage23 = depthStage23 > 0 && FarolVisualIdentityStage23.shouldStopInsideWindow(
                linesStage23.asSequence(),
                budgetStage23[0],
                outputStage23.size,
            )
            if (compactSameContextStage23) break
        }

        val textStage23 = linesStage23.joinToString("\n").take(MAX_ACCESSIBILITY_TEXT_CHARS_0167)
        if (textStage23.isNotBlank()) {
            val boundsStage23 = Rect()
            runCatching { nodeStage23.getBoundsInScreen(boundsStage23) }
            outputStage23 += FarolCardBlock0188(
                id = idStage23,
                parentId = parentIdStage23,
                packageName = packageNameStage23,
                windowId = windowIdStage23,
                windowLayer = windowLayerStage23,
                depth = depthStage23,
                text = textStage23,
                source = FarolEvidenceSource0188.Accessibility,
                left = boundsStage23.left,
                top = boundsStage23.top,
                right = boundsStage23.right,
                bottom = boundsStage23.bottom,
                syntheticRoot = depthStage23 == 0,
            )
        }
        val currentContextCompleteStage23 = depthStage23 > 0 &&
            FarolVisualIdentityStage23.hasTwoAddressLeads(linesStage23.asSequence())
        return Stage23TreeResult(
            lines = linesStage23.toList(),
            completeContext = childProvidedCompleteContextStage23 || currentContextCompleteStage23,
        )
    }

