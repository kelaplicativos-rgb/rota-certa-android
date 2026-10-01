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
        if (!activationStage26.enabled) {
            applyReadingOffStage26(activationStage26)
            FarolReadingActivationStage26.Metrics.increment("eventsReceived")
            FarolReadingActivationStage26.Metrics.increment("eventsRejectedReadingOff")
            FarolReadingActivationStage26.Metrics.increment("heavyCollectionsAvoided")
            return true
        }
        if (bubbleGestureActive) {
            FarolReadingActivationStage26.Metrics.increment("eventsReceived")
            FarolReadingActivationStage26.Metrics.increment("ownOverlayEventsIgnored")
            FarolReadingActivationStage26.Metrics.increment("heavyCollectionsAvoided")
            return true
        }

        val cheapSignalStage26 = buildCheapVisualSignalStage26(
            eventPackageStage19,
            eventTypeStage20,
            eventWindowIdStage20,
            eventStage26,
        )
        val admissionStage26 = stage26PreCollectGate.admit(true, cheapSignalStage26)
        val mutationDetectedNsStage26 = SystemClock.elapsedRealtimeNanos()
        FarolReadingActivationStage26.Metrics.sample(
            "eventToMutationDetected",
            mutationDetectedNsStage26 - eventStartedNsStage26,
        )
        if (!admissionStage26.heavyCollect) return true

        // Mandatory Stage26 order: previous generation/result is invalidated BEFORE heavy traversal.
        invalidateOldVisualBeforeCollectStage26(admissionStage26.visualGeneration, eventStartedNsStage26)

        val cycleIdStage20 = FarolForensicTraceStage20.beginCycle(
            nowNs = eventStartedNsStage26,
            packageName = eventPackageStage19,
            eventType = eventTypeStage20,
            eventWindowId = eventWindowIdStage20,
        )
        stage20LastCycleId = cycleIdStage20

        val collectStartedNsStage26 = SystemClock.elapsedRealtimeNanos()
        FarolForensicTraceStage20.accessibilityCollectStarted(cycleIdStage20, collectStartedNsStage26)
        val collectionStage26 = collectUniversalAccessibilitySnapshotStage26()
        val collectEndedNsStage26 = SystemClock.elapsedRealtimeNanos()
        FarolReadingActivationStage26.Metrics.sample("collect", collectEndedNsStage26 - collectStartedNsStage26)
        FarolReadingActivationStage26.Metrics.addTotal("nodesVisited", collectionStage26.stats.blocksVisited.toLong())
        FarolReadingActivationStage26.Metrics.addTotal("blocksEmitted", collectionStage26.stats.blocksEmitted.toLong())
        FarolReadingActivationStage26.Metrics.addTotal("addressParserInvocations", collectionStage26.addressParserInvocations.toLong())
        FarolReadingActivationStage26.Metrics.addTotal("duplicateSubtreesAvoided", collectionStage26.duplicateSubtreesAvoided.toLong())

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
        if (!visualDecisionStage23.process) {
            // Second guard remains as a safety net; pre-collect coalescing is the primary admission.
            FarolVisualIdentityStage23.Metrics.increment("unchangedVisualSkipped")
            return true
        }

        val evaluateStartedNsStage26 = SystemClock.elapsedRealtimeNanos()
        FarolForensicTraceStage20.accessibilityEvaluateStarted(cycleIdStage20, evaluateStartedNsStage26)
        val evaluationStage19 = FarolLatencyProbeStage9.measureValue(
            stage = "STAGE26_UNIVERSAL_VISUAL_ACCESSIBILITY",
            source = "Accessibility",
        ) {
            FarolUniversalVisualPipelineStage19.evaluate(collectionStage26.blocks)
        }
        val evaluateEndedNsStage26 = SystemClock.elapsedRealtimeNanos()
        FarolReadingActivationStage26.Metrics.sample("evaluate", evaluateEndedNsStage26 - evaluateStartedNsStage26)
        FarolVisualIdentityStage23.Metrics.recordEvaluate("Accessibility", evaluationStage19 != null, evaluateEndedNsStage26 - evaluateStartedNsStage26)
        FarolForensicTraceStage20.accessibilityEvaluateFinished(cycleIdStage20, evaluateEndedNsStage26, evaluationStage19 != null)
        stage23VisualGate.markProcessed(collectionStage26.snapshot.hash, visualDecisionStage23.generation)
        stage23ScheduleGate.satisfyDirect(visualDecisionStage23.generation, collectionStage26.snapshot.hash)

        if (!isReadingActivationGenerationFreshStage26(activationStage26.generation)) {
            FarolReadingActivationStage26.Metrics.increment("workCancelledOnReadingOff")
            return true
        }
        if (evaluationStage19 != null) {
            FarolReadingActivationStage26.Metrics.sample("eventToCandidate", SystemClock.elapsedRealtimeNanos() - eventStartedNsStage26)
            FarolVisualIdentityStage23.Metrics.recordEventToCandidate("Accessibility", SystemClock.elapsedRealtimeNanos() - eventStartedNsStage26)
            stage26CandidateEventStartedNs = eventStartedNsStage26
            stage26CandidateActivationGeneration = activationStage26.generation
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

    private fun refreshReadingActivationStage26(
        eventPackageStage26: String?,
        eventTypeStage26: Int,
    ): FarolReadingActivationStage26.ActivationSnapshot {
        val selectedStage26 = SelectedRideAppStore.read(applicationContext)
        stage26ReadingActivation.updateSelection(selectedStage26)
        val usageGrantedStage26 = stage26UsageState.hasUsageAccess()
        stage26ReadingActivation.setUsageAccess(usageGrantedStage26)
        if (!usageGrantedStage26 || selectedStage26.isEmpty()) {
            stage26UsageInitialized = false
            return stage26ReadingActivation.snapshot()
        }
        val normalizedEventStage26 = normalizePackageName(eventPackageStage26)
        val shouldRefreshUsageStage26 = !stage26UsageInitialized ||
            eventTypeStage26 == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventTypeStage26 == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            normalizedEventStage26 in selectedStage26
        if (shouldRefreshUsageStage26) {
            val usageEventsStage26 = stage26UsageState.readSelectedActivity(selectedStage26)
            stage26ReadingActivation.replaceUsageState(usageEventsStage26)
            stage26UsageInitialized = true
        }
        return stage26ReadingActivation.snapshot()
    }

    private fun applyReadingOffStage26(snapshotStage26: FarolReadingActivationStage26.ActivationSnapshot) {
        if (stage26LastAppliedActivationGeneration == snapshotStage26.generation &&
            currentRadarColor == RadarColor.Orange && currentDistanceKm == null) return
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
        FarolReadingActivationStage26.Metrics.increment("workCancelledOnReadingOff")
        if (currentRadarColor != RadarColor.Orange || currentDistanceKm != null) {
            showOverlay(RadarColor.Orange, distanceKm = null)
        }
    }

    private fun isReadingActivationGenerationFreshStage26(expectedGenerationStage26: Long): Boolean {
        val currentStage26 = stage26ReadingActivation.snapshot()
        return currentStage26.enabled && currentStage26.usageAccessGranted && currentStage26.generation == expectedGenerationStage26
    }

    private fun buildCheapVisualSignalStage26(
        eventPackageStage26: String?,
        eventTypeStage26: Int,
        eventWindowIdStage26: Int,
        eventStage26: AccessibilityEvent,
    ): FarolReadingActivationStage26.CheapVisualSignal {
        var eventWindowRootPackageStage26: String? = null
        val windowSignatureStage26 = runCatching {
            windows.sortedByDescending { it.layer }.take(6).map { windowStage26 ->
                val rootStage26 = runCatching { windowStage26.root }.getOrNull()
                val rootPackageStage26 = rootStage26?.let(::safeNodePackageName0185).orEmpty()
                if (windowStage26.id == eventWindowIdStage26) eventWindowRootPackageStage26 = normalizePackageName(rootPackageStage26)
                "${windowStage26.id}:${windowStage26.layer}:$rootPackageStage26:${windowStage26.type}"
            }.joinToString("|")
        }.getOrDefault("$eventWindowIdStage26")
        val ownPackageStage26 = normalizePackageName(packageName)
        val eventIsOwnStage26 = normalizePackageName(eventPackageStage26) == ownPackageStage26
        val ownOverlayStage26 = eventIsOwnStage26 && eventWindowRootPackageStage26 == ownPackageStage26

        val relevantLinesStage26 = LinkedHashSet<String>(8)
        val sourceStage26 = runCatching { eventStage26.source }.getOrNull()
        val boundsStage26 = Rect()
        runCatching { sourceStage26?.getBoundsInScreen(boundsStage26) }
        val sourceSlotStage26 = buildString {
            append(eventWindowIdStage26); append(':')
            append(runCatching { sourceStage26?.viewIdResourceName }.getOrNull().orEmpty()); append(':')
            append(boundsStage26.left); append(':'); append(boundsStage26.top); append(':'); append(boundsStage26.right); append(':'); append(boundsStage26.bottom)
        }
        fun addRelevantStage26(valueStage26: CharSequence?) {
            val value = valueStage26?.toString()?.trim()?.takeIf(String::isNotBlank) ?: return
            if (FarolVisualIdentityStage23.countAddressLeads(value) > 0) relevantLinesStage26 += value
        }
        addRelevantStage26(runCatching { sourceStage26?.text }.getOrNull())
        addRelevantStage26(runCatching { sourceStage26?.contentDescription }.getOrNull())
        runCatching { eventStage26.text }.getOrDefault(emptyList()).take(8).forEach(::addRelevantStage26)
        // Cheap local context only: source, parent and direct siblings. No full-window traversal.
        var cursorStage26 = sourceStage26
        repeat(3) {
            val parentStage26 = runCatching { cursorStage26?.parent }.getOrNull() ?: return@repeat
            addRelevantStage26(runCatching { parentStage26.text }.getOrNull())
            addRelevantStage26(runCatching { parentStage26.contentDescription }.getOrNull())
            val childCountStage26 = runCatching { parentStage26.childCount }.getOrDefault(0).coerceIn(0, 12)
            for (indexStage26 in 0 until childCountStage26) {
                val childStage26 = runCatching { parentStage26.getChild(indexStage26) }.getOrNull() ?: continue
                addRelevantStage26(runCatching { childStage26.text }.getOrNull())
                addRelevantStage26(runCatching { childStage26.contentDescription }.getOrNull())
                if (relevantLinesStage26.size >= 6) break
            }
            cursorStage26 = parentStage26
        }
        val relevantTextStage26 = relevantLinesStage26.sorted().joinToString("\n")
        return FarolReadingActivationStage26.CheapVisualSignal(
            ownOverlay = ownOverlayStage26,
            windowSignature = windowSignatureStage26,
            sourceText = relevantTextStage26,
            sourceSlot = sourceSlotStage26,
            contentChangeTypes = runCatching { eventStage26.contentChangeTypes }.getOrDefault(0),
        )
    }

    private fun invalidateOldVisualBeforeCollectStage26(newGenerationStage26: Long, eventStartedNsStage26: Long) {
        universalScreenGeneration += 1L
        universalWindowGeneration += 1L
        universalRouteJob?.cancel(); universalRouteJob = null
        analyzeJob?.cancel(); analyzeJob = null
        screenshotFallbackJob127?.cancel(); screenshotFallbackJob127 = null
        stage19OcrSerial += 1L
        stage19OcrRerunRequested = false
        stage21OcrGate.cancelBecauseAccessibilityWon()
        stage23VisualGate.currentHash()?.let(stage23VisualGate::invalidateForExplicitRecovery)
        stage23OcrGate.cancelBecauseAccessibilityWon(stage23VisualGate.currentGeneration(), stage23VisualGate.currentHash() ?: Long.MIN_VALUE)
        lastAnalyzedHash = null
        lastSnapshotHash = null
        universalActiveAddressSignature = null
        currentDistanceKm = null
        stage19VisualVerificationPending = true
        fastFarolStartedAtChecklist13 = System.currentTimeMillis()
        rememberBubbleReason("stage26_visual_mutation", "Mudança visual externa detectada; resultado anterior invalidado antes da nova coleta.")
        showOverlay(RadarColor.Orange, distanceKm = null)
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
        if (depthStage26 > 0 && FarolVisualIdentityStage23.hasTwoAddressLeads(linesStage26.asSequence())) {
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

