    private fun scheduleVisibleTextAnalysis(delayMs: Long, allowPopupCandidate: Boolean = false) {
        @Suppress("UNUSED_VARIABLE") val ignoredDelayStage23 = delayMs
        @Suppress("UNUSED_VARIABLE") val ignoredPopupStage23 = allowPopupCandidate
        if (!serviceReady || !WorkModePolicy0162.isEnabled(currentSettings) || bubbleGestureActive) return
        val demandStage23 = stage23ScheduleGate.create(
            stage23VisualGate.currentGeneration(),
            stage23VisualGate.currentHash(),
        )
        analyzeJob?.cancel()
        analyzeJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (!stage23ScheduleGate.shouldRun(
                    demandStage23,
                    stage23VisualGate.currentGeneration(),
                    stage23VisualGate.currentHash(),
                )
            ) {
                FarolVisualIdentityStage23.Metrics.increment("scheduledCancelled")
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
            val collectionStage23 = collectUniversalAccessibilitySnapshotStage23()
            val collectEndedNsStage23 = SystemClock.elapsedRealtimeNanos()

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

