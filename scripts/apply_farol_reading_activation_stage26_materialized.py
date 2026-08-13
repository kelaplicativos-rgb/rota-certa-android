#!/usr/bin/env python3
"""Stage26 materialization entry point.

Preserves the Stage23 OCR freshness helper while replacing only the Stage23
accessibility admission/collector section. It also bridges the retained Stage23
scheduled fallback to the Stage26 compact collector and activation generation,
without restoring the removed heavy Stage23 collector. The compiled chain stays:
Stage18 -> Stage19 -> Stage20 -> Stage21 -> Stage23 -> Stage26.
"""
from __future__ import annotations

import importlib.util
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("apply_farol_reading_activation_stage26.py")
spec = importlib.util.spec_from_file_location("stage26_impl", MODULE_PATH)
if spec is None or spec.loader is None:
    raise SystemExit("Stage26 materializer could not load implementation")
impl = importlib.util.module_from_spec(spec)
spec.loader.exec_module(impl)

_original_replace_section = impl.replace_section
_original_apply = impl.apply


def _replace_section_preserving_stage23_ocr(text: str, start: str, end: str, replacement: str, label: str) -> str:
    if label == "Stage26 gate/precollect/collector":
        expected_old_end = "    private fun requestUniversalScreenshotStage19("
        if end != expected_old_end:
            raise SystemExit(f"Stage26 unexpected original boundary: {end!r}")
        end = "    private fun isStage23OcrDemandFresh("
        if text.count(end) != 1:
            raise SystemExit(f"Stage26 must preserve exactly one Stage23 OCR freshness helper, found {text.count(end)}")
    return _original_replace_section(text, start, end, replacement, label)


def _replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Stage26 materialized bridge {label}: expected 1, found {count}")
    return text.replace(old, new, 1)


def _apply_with_scheduled_compact_bridge(root: Path) -> None:
    _original_apply(root)
    service_path = root / impl.SERVICE
    text = service_path.read_text(encoding="utf-8")

    # PACKAGE_USAGE_STATS is a required app-op declaration for the Stage26 fail-closed
    # Usage Access gate. Keep Lint enabled globally and scope the exception to this one
    # manifest declaration only; runtime permission semantics remain unchanged.
    manifest_path = root / impl.MANIFEST
    if not manifest_path.is_file():
        raise SystemExit("Stage26 manifest missing")
    manifest = manifest_path.read_text(encoding="utf-8")
    usage_permission = '    <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />'
    scoped_usage_permission = (
        '    <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" '
        'tools:ignore="ProtectedPermissions" />'
    )
    if scoped_usage_permission not in manifest:
        if usage_permission not in manifest:
            raise SystemExit("Stage26 PACKAGE_USAGE_STATS declaration missing")
        tools_namespace = 'xmlns:tools="http://schemas.android.com/tools"'
        if tools_namespace not in manifest:
            manifest_anchor = '<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
            if manifest.count(manifest_anchor) != 1:
                raise SystemExit(
                    f"Stage26 manifest namespace anchor expected 1, found {manifest.count(manifest_anchor)}"
                )
            manifest = manifest.replace(
                manifest_anchor,
                '<manifest xmlns:android="http://schemas.android.com/apk/res/android"\n'
                '    xmlns:tools="http://schemas.android.com/tools">',
                1,
            )
        manifest = manifest.replace(usage_permission, scoped_usage_permission, 1)
        manifest_path.write_text(manifest, encoding="utf-8")
    if scoped_usage_permission not in manifest:
        raise SystemExit("Stage26 scoped PACKAGE_USAGE_STATS Lint suppression missing")

    # Stage18 subscribed to notification-state events so notification wakeup can run
    # before strict root/package resolution. The Stage26 workflow's reconstructed XML
    # omitted that event type; repair the compiled materialization itself, not the test.
    accessibility_xml_path = root / "app/src/main/res/xml/rota_certa_accessibility.xml"
    if not accessibility_xml_path.is_file():
        raise SystemExit("Stage26 accessibility service XML missing")
    accessibility_xml = accessibility_xml_path.read_text(encoding="utf-8")
    if "typeNotificationStateChanged" not in accessibility_xml:
        xml_anchor = 'typeWindowsChanged"'
        if accessibility_xml.count(xml_anchor) != 1:
            raise SystemExit(f"Stage26 notification subscription anchor expected 1, found {accessibility_xml.count(xml_anchor)}")
        accessibility_xml = accessibility_xml.replace(
            xml_anchor,
            'typeWindowsChanged|typeNotificationStateChanged"',
            1,
        )
        accessibility_xml_path.write_text(accessibility_xml, encoding="utf-8")
    if "typeNotificationStateChanged" not in accessibility_xml:
        raise SystemExit("Stage26 notification state event is not subscribed")

    # Restore the legacy drag pause contract on the new Stage26 direct-event path.
    # This is executable behavior, not a test-only marker: heavy collection remains
    # blocked while the bubble owns the gesture.
    text = _replace_once(
        text,
        '''        if (bubbleGestureActive) {\n            FarolReadingActivationStage26.Metrics.increment("eventsReceived")\n            FarolReadingActivationStage26.Metrics.increment("ownOverlayEventsIgnored")\n            FarolReadingActivationStage26.Metrics.increment("heavyCollectionsAvoided")\n            return true\n        }\n\n        val cheapSignalStage26 = buildCheapVisualSignalStage26(\n''',
        '''        if (bubbleGestureActive) {\n            FarolReadingActivationStage26.Metrics.increment("eventsReceived")\n            FarolReadingActivationStage26.Metrics.increment("ownOverlayEventsIgnored")\n            FarolReadingActivationStage26.Metrics.increment("heavyCollectionsAvoided")\n            return true\n        } // bubble_drag_accessibility_pause_0_1_116\n\n        val cheapSignalStage26 = buildCheapVisualSignalStage26(\n''',
        "bubble drag accessibility pause",
    )

    # Stage23 already keeps the screenshot request gesture-gated and performs OCR
    # extraction on Dispatchers.Default. Stage26 preserves those semantics while
    # restoring the historical contract markers removed by Stage23 section replacement.
    text = _replace_once(
        text,
        '''        if (!serviceReady || !WorkModePolicy0162.isEnabled(currentSettings) || bubbleGestureActive) return\n        val activationStage26 = stage26ReadingActivation.snapshot()\n''',
        '''        if (!serviceReady || !WorkModePolicy0162.isEnabled(currentSettings)) return\n        if (bubbleGestureActive) return // bubble_drag_screenshot_pause_0_1_116\n        // bubble_drag_ocr_background_0_1_116 — OCR extraction remains on Dispatchers.Default below.\n        val activationStage26 = stage26ReadingActivation.snapshot()\n''',
        "bubble drag screenshot and OCR contract",
    )

    text = _replace_once(
        text,
        '''    private fun scheduleVisibleTextAnalysis(delayMs: Long, allowPopupCandidate: Boolean = false) {\n        if (!stage26ReadingActivation.snapshot().enabled) {\n            FarolReadingActivationStage26.Metrics.increment("eventsRejectedReadingOff")\n            return\n        }\n''',
        '''    private fun scheduleVisibleTextAnalysis(delayMs: Long, allowPopupCandidate: Boolean = false) {\n        val scheduledActivationStage26 = stage26ReadingActivation.snapshot()\n        if (!scheduledActivationStage26.enabled || !scheduledActivationStage26.usageAccessGranted) {\n            FarolReadingActivationStage26.Metrics.increment("eventsRejectedReadingOff")\n            FarolReadingActivationStage26.Metrics.increment("heavyCollectionsAvoided")\n            return\n        }\n''',
        "scheduled activation snapshot",
    )
    text = _replace_once(
        text,
        '''        analyzeJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {\n            if (!stage23ScheduleGate.shouldRun(\n''',
        '''        analyzeJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {\n            if (!isReadingActivationGenerationFreshStage26(scheduledActivationStage26.generation)) {\n                FarolReadingActivationStage26.Metrics.increment("heavyCollectionsAvoided")\n                return@launch\n            }\n            if (!stage23ScheduleGate.shouldRun(\n''',
        "scheduled activation before Stage23 demand gate",
    )
    text = _replace_once(
        text,
        '''                FarolVisualIdentityStage23.Metrics.increment("scheduledCancelled")\n                FarolForensicTraceStage20.note(\n''',
        '''                FarolVisualIdentityStage23.Metrics.increment("scheduledCancelled")\n                FarolReadingActivationStage26.Metrics.increment("heavyCollectionsAvoided")\n                FarolForensicTraceStage20.note(\n''',
        "scheduled avoided metric",
    )
    text = _replace_once(
        text,
        '''            FarolForensicTraceStage20.accessibilityCollectStarted(cycleIdStage20, collectStartedNsStage23)\n            val collectionStage23 = collectUniversalAccessibilitySnapshotStage23()\n            val collectEndedNsStage23 = SystemClock.elapsedRealtimeNanos()\n''',
        '''            FarolForensicTraceStage20.accessibilityCollectStarted(cycleIdStage20, collectStartedNsStage23)\n            FarolReadingActivationStage26.Metrics.increment("heavyCollectionsStarted")\n            val collectionStage23 = collectUniversalAccessibilitySnapshotStage26()\n            val collectEndedNsStage23 = SystemClock.elapsedRealtimeNanos()\n            FarolReadingActivationStage26.Metrics.sample("collect", collectEndedNsStage23 - collectStartedNsStage23)\n            FarolReadingActivationStage26.Metrics.addTotal("nodesVisited", collectionStage23.stats.blocksVisited.toLong())\n            FarolReadingActivationStage26.Metrics.addTotal("blocksEmitted", collectionStage23.stats.blocksEmitted.toLong())\n            FarolReadingActivationStage26.Metrics.addTotal("addressParserInvocations", collectionStage23.addressParserInvocations.toLong())\n            FarolReadingActivationStage26.Metrics.addTotal("duplicateSubtreesAvoided", collectionStage23.duplicateSubtreesAvoided.toLong())\n            if (!isReadingActivationGenerationFreshStage26(scheduledActivationStage26.generation)) {\n                FarolReadingActivationStage26.Metrics.increment("workCancelledOnReadingOff")\n                return@launch\n            }\n''',
        "scheduled compact collector bridge",
    )
    text = _replace_once(
        text,
        '''            if (evaluationStage19 != null) {\n                FarolVisualIdentityStage23.Metrics.recordEventToCandidate(\n                    "AccessibilityScheduled",\n                    SystemClock.elapsedRealtimeNanos() - eventStartedNsStage23,\n                )\n                stage19VisualVerificationPending = false\n''',
        '''            if (evaluationStage19 != null) {\n                FarolVisualIdentityStage23.Metrics.recordEventToCandidate(\n                    "AccessibilityScheduled",\n                    SystemClock.elapsedRealtimeNanos() - eventStartedNsStage23,\n                )\n                FarolReadingActivationStage26.Metrics.sample("eventToCandidate", SystemClock.elapsedRealtimeNanos() - eventStartedNsStage23)\n                stage26CandidateEventStartedNs = eventStartedNsStage23\n                stage26CandidateActivationGeneration = scheduledActivationStage26.generation\n                if (!isReadingActivationGenerationFreshStage26(stage26CandidateActivationGeneration)) {\n                    FarolReadingActivationStage26.Metrics.increment("workCancelledOnReadingOff")\n                    return@launch\n                }\n                stage19VisualVerificationPending = false\n''',
        "scheduled candidate activation binding",
    )

    if "collectUniversalAccessibilitySnapshotStage23()" in text:
        raise SystemExit("Stage26 materialization retained obsolete Stage23 heavy collector reference")
    schedule_start = text.index("    private fun scheduleVisibleTextAnalysis(")
    schedule_end = text.index("    private fun scheduleScreenshotFallback127", schedule_start)
    schedule = text[schedule_start:schedule_end]
    for required in (
        "scheduledActivationStage26",
        "isReadingActivationGenerationFreshStage26",
        "collectUniversalAccessibilitySnapshotStage26()",
        "heavyCollectionsAvoided",
        "heavyCollectionsStarted",
        "stage26CandidateActivationGeneration = scheduledActivationStage26.generation",
    ):
        if required not in schedule:
            raise SystemExit(f"Stage26 scheduled bridge missing {required}")

    # Preserve the Stage18 drag contract through Stage23/26 materialization and
    # verify that the restored markers correspond to real guards/background OCR.
    for required in (
        "bubble_instant_drag_0_1_116",
        "bubble_drag_accessibility_pause_0_1_116",
        "bubble_drag_screenshot_pause_0_1_116",
        "bubble_drag_process_pause_0_1_116",
        "bubble_drag_scan_pause_0_1_116",
        "bubble_drag_ocr_background_0_1_116",
        "analyzeJob?.cancel()",
        "withContext(Dispatchers.Default)",
        "ocrService.extractStructuredText",
    ):
        if required not in text:
            raise SystemExit(f"Stage26 drag regression contract missing {required}")
    if "bubbleGestureActive = true" not in text and "bubbleGestureActive = (true)" not in text:
        raise SystemExit("Stage26 drag regression contract missing ACTION_DOWN gesture ownership")

    # Notification wakeup must remain before the legacy strict package/root resolver.
    notification_handler = text.find("handleNotificationWakeup0169")
    strict_resolver = text.find("DriverCardEventResolver0162.resolve")
    if notification_handler < 0 or strict_resolver <= notification_handler:
        raise SystemExit("Stage26 notification wakeup ordering regression")

    service_path.write_text(text, encoding="utf-8")
    print("stage26_scheduled_compact_bridge=passed")
    print("scheduled_legacy_heavy_collector_reference=false")
    print("scheduled_bound_to_activation_generation=true")
    print("bubble_drag_contract_preserved=true")
    print("notification_event_subscribed=true")
    print("notification_wakeup_order_preserved=true")
    print("package_usage_stats_lint_scope=passed")


impl.replace_section = _replace_section_preserving_stage23_ocr
impl.apply = _apply_with_scheduled_compact_bridge

if __name__ == "__main__":
    impl.main()
