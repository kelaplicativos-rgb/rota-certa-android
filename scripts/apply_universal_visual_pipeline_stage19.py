#!/usr/bin/env python3
from __future__ import annotations

import argparse
import shutil
from pathlib import Path

SERVICE = Path("app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
HELPER = Path("app/src/main/java/br/com/mapeiaia/rotacerta/FarolUniversalVisualPipelineStage19.kt")
TEST = Path("app/src/test/java/br/com/mapeiaia/rotacerta/FarolUniversalVisualPipelineStage19Test.kt")
BUILD = Path("app/build.gradle.kts")
MARKER = "UNIVERSAL_VISUAL_AUTHORITY_STAGE19"
PATCH_ROOT = Path(__file__).resolve().parents[1]
HELPER_TEMPLATE = PATCH_ROOT / "stage19/FarolUniversalVisualPipelineStage19.kt"
TEST_TEMPLATE = PATCH_ROOT / "stage19/FarolUniversalVisualPipelineStage19Test.kt"
SERVICE_FRAGMENT = PATCH_ROOT / "stage19/LiveRideAccessibilityServiceStage19.inc.kt"

SCHEDULE_SOURCE = r'''    private fun scheduleVisibleTextAnalysis(delayMs: Long, allowPopupCandidate: Boolean = false) {
        @Suppress("UNUSED_VARIABLE") val ignoredDelayStage19 = delayMs
        @Suppress("UNUSED_VARIABLE") val ignoredPopupStage19 = allowPopupCandidate
        if (!serviceReady || !WorkModePolicy0162.isEnabled(currentSettings) || bubbleGestureActive) return
        analyzeJob?.cancel()
        analyzeJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val blocksStage19 = collectUniversalAccessibilityBlocksStage19()
            val evaluationStage19 = withContext(Dispatchers.Default) {
                FarolUniversalVisualPipelineStage19.evaluate(blocksStage19)
            }
            if (evaluationStage19 != null) {
                stage19VisualVerificationPending = false
                stage19OcrSerial += 1L
                stage19OcrRerunRequested = false
                processUniversalVisualStage19(evaluationStage19, "AccessibilityScheduled")
            } else {
                stage19VisualVerificationPending = true
                requestUniversalScreenshotStage19(null)
            }
        }
    } // stage19_universal_scheduled_snapshot

'''


def fail(message: str) -> None:
    raise SystemExit(message)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        fail(f"Stage19 anchor {label}: expected 1, found {count}")
    return text.replace(old, new, 1)


def self_test() -> None:
    for path in (HELPER_TEMPLATE, TEST_TEMPLATE, SERVICE_FRAGMENT):
        if not path.is_file():
            fail(f"Stage19 support file missing: {path}")
    helper = HELPER_TEMPLATE.read_text(encoding="utf-8")
    tests = TEST_TEMPLATE.read_text(encoding="utf-8")
    fragment = SERVICE_FRAGMENT.read_text(encoding="utf-8")
    for required in (
        MARKER,
        "PACKAGE_IDENTITY_IS_NOT_VISUAL_AUTHORITY_STAGE19",
        "bindingMatchesCurrent",
        "metadataPackageName",
    ):
        if required not in helper:
            fail(f"Stage19 helper marker missing: {required}")
    for forbidden in (
        "SelectedRideAppStore",
        "shouldScanPackage",
        "FarolAppIdentityIsolationStage18",
        "RideCardConfirmationPolicy",
    ):
        if forbidden in helper:
            fail(f"Package/model authority leaked into Stage19 helper: {forbidden}")
    if tests.count("@Test") != 29:
        fail(f"Expected exactly 29 Stage19 tests, found {tests.count('@Test')}")
    for required in (
        "twoAddressesOverLauncherAnalyzes",
        "twoAddressesOverWhatsAppAnalyzes",
        "twoAddressesOverChatGptAnalyzes",
        "twoAddressesOverYouTubeAnalyzes",
        "twoAddressesOverMapsAnalyzes",
        "twoAddressesOverWazeAnalyzes",
        "twoAddressesOverUberAnalyzes",
        "twoAddressesOver99Analyzes",
        "twoAddressesOverInDriveAnalyzes",
        "twoAddressesOverUnknownPackageAnalyzes",
        "accessibilityEmptyAndValidOcrAnalyzes",
        "twoDifferentAddressBlocksAreNotMixed",
        "oldOcrBindingIsRejected",
        "oldRouteBindingIsRejected",
        "cacheFromOtherDestinationCannotMatchCurrentBinding",
    ):
        if required not in tests:
            fail(f"Mandatory Stage19 regression missing: {required}")
    for required in (
        "collectUniversalAccessibilityBlocksStage19",
        "requestUniversalScreenshotStage19",
        "processUniversalVisualStage19",
        "cachedDrivingDistancesFromAddressKm",
        "drivingDistancesFromAddressKm",
        "decideFastWorkRegionChecklist13",
        "stage19VisualVerificationPending",
    ):
        if required not in fragment:
            fail(f"Stage19 service integration missing: {required}")
    for forbidden in ("delay(", "Thread.sleep(", "SystemClock.sleep("):
        if forbidden in fragment or forbidden in SCHEDULE_SOURCE:
            fail(f"Artificial critical-path delay found in Stage19: {forbidden}")
    for forbidden in ("SelectedRideAppStore", "shouldScanPackage", "hasStrictSelectedRootChecklist1", "FarolAppIdentityIsolationStage18"):
        if forbidden in SCHEDULE_SOURCE:
            fail(f"Package authority leaked into Stage19 scheduled current-screen path: {forbidden}")
    for required in ("collectUniversalAccessibilityBlocksStage19", "requestUniversalScreenshotStage19", "processUniversalVisualStage19"):
        if required not in SCHEDULE_SOURCE:
            fail(f"Stage19 scheduled current-screen path incomplete: {required}")
    print("stage19_self_test=passed")
    print(f"stage19_test_methods={tests.count('@Test')}")
    print("package_identity_is_authority=false")
    print("card_model_is_authority=false")
    print("visual_generation_is_authority=true")
    print("scheduled_activation_package_gate=false")


def apply(root: Path) -> None:
    service_path = root / SERVICE
    build_path = root / BUILD
    if not service_path.is_file() or not build_path.is_file():
        fail("Stage19 requires materialized Stage18 app source")
    service = service_path.read_text(encoding="utf-8")
    build = build_path.read_text(encoding="utf-8")
    if MARKER in service or (root / HELPER).exists():
        fail("Stage19 already appears applied")
    if 'versionCode = 5482' not in build or 'versionName = "0.1.198"' not in build:
        fail("Stage19 requires exact 0.1.198/5482 baseline")

    service = replace_once(
        service,
        "    private var screenshotFallbackJob127: Job? = null // deferred_ocr_job_0_1_127\n",
        "    private var screenshotFallbackJob127: Job? = null // deferred_ocr_job_0_1_127\n"
        "    private var stage19OcrSerial: Long = 0L\n"
        "    private var stage19OcrRerunRequested: Boolean = false\n"
        "    private var stage19VisualVerificationPending: Boolean = false\n"
        "    private var stage19ActiveWindowId: Int? = null\n"
        "    private var stage19ActiveBlockId: String? = null\n"
        "    // UNIVERSAL_VISUAL_AUTHORITY_STAGE19\n",
        "state fields",
    )

    event_anchor = (
        "        val eventType0187 = runCatching { event.eventType }.getOrDefault(0)\n"
        "        if (!AccessibilityEventFloodGate.isRelevantEventType(eventType0187)) return\n"
        "        val eventPackage = normalizePackageName(runCatching { event.packageName?.toString() }.getOrNull())\n"
    )
    service = replace_once(
        service,
        event_anchor,
        event_anchor +
        "        // Stage19 owns the visual critical path before package/root/model gates.\n"
        "        if (handleUniversalVisualEventStage19(eventPackage)) return\n",
        "event visual authority",
    )

    fragment = SERVICE_FRAGMENT.read_text(encoding="utf-8")
    service = replace_once(
        service,
        "    private fun startContinuousScan() {\n",
        fragment + "    private fun startContinuousScan() {\n",
        "service integration",
    )
    service = replace_once(
        service,
        "    private fun handleUniversalVisualEventStage19(eventPackageStage19: String?): Boolean {\n"
        "        if (!serviceReady || !WorkModePolicy0162.isEnabled(currentSettings)) return false\n",
        "    private fun handleUniversalVisualEventStage19(eventPackageStage19: String?): Boolean {\n"
        "        if (!serviceReady || !WorkModePolicy0162.isEnabled(currentSettings)) return false\n"
        "        if (bubbleGestureActive) return true // bubble_drag_accessibility_pause_0_1_116\n",
        "gesture pauses universal accessibility",
    )

    schedule_start = service.find("    private fun scheduleVisibleTextAnalysis(delayMs: Long, allowPopupCandidate: Boolean = false) {")
    schedule_end = service.find("    private fun scheduleScreenshotFallback127", schedule_start)
    if schedule_start < 0 or schedule_end <= schedule_start:
        fail("Stage19 could not isolate legacy scheduled current-screen analysis")
    legacy_schedule = service[schedule_start:schedule_end]
    for required_legacy in ("hasStrictSelectedRootChecklist1", "shouldScanPackage", "driverCardSessionGate0162"):
        if required_legacy not in legacy_schedule:
            fail(f"Unexpected Stage18 scheduled path before replacement: {required_legacy} missing")
    service = service[:schedule_start] + SCHEDULE_SOURCE + service[schedule_end:]

    clear_anchor = (
        "        )\n"
        "        universalActiveAddressSignature = null\n"
        "        lastSnapshotHash = null\n"
        "        lastAnalyzedHash = null\n"
        "        analyzing = false\n"
    )
    service = replace_once(
        service,
        clear_anchor,
        clear_anchor +
        "        stage19ActiveWindowId = null\n"
        "        stage19ActiveBlockId = null\n"
        "        stage19VisualVerificationPending = false\n",
        "hard clear visual binding",
    )

    build = replace_once(build, "versionCode = 5482", "versionCode = 5484", "versionCode")
    build = replace_once(build, 'versionName = "0.1.198"', 'versionName = "0.1.200"', "versionName")

    (root / HELPER).parent.mkdir(parents=True, exist_ok=True)
    (root / TEST).parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(HELPER_TEMPLATE, root / HELPER)
    shutil.copyfile(TEST_TEMPLATE, root / TEST)
    service_path.write_text(service, encoding="utf-8")
    build_path.write_text(build, encoding="utf-8")

    transformed = service_path.read_text(encoding="utf-8")
    visual_index = transformed.index("handleUniversalVisualEventStage19(eventPackage)")
    selected_index = transformed.index("val selectedPackages156")
    identity_index = transformed.index("FarolAppIdentityIsolationStage18.resolve")
    if not visual_index < selected_index < identity_index:
        fail("Stage19 visual fast path is not before package identity gates")
    if "bubbleGestureActive) return true // bubble_drag_accessibility_pause_0_1_116" not in transformed:
        fail("Stage19 universal event path lost bubble gesture pause")
    schedule_start = transformed.index("    private fun scheduleVisibleTextAnalysis(")
    schedule_end = transformed.index("    private fun scheduleScreenshotFallback127", schedule_start)
    scheduled_section = transformed[schedule_start:schedule_end]
    for forbidden in ("SelectedRideAppStore", "shouldScanPackage", "hasStrictSelectedRootChecklist1", "driverCardSessionGate0162"):
        if forbidden in scheduled_section:
            fail(f"Scheduled current-screen path still depends on package/session authority: {forbidden}")
    for required in (
        "collectUniversalAccessibilityBlocksStage19",
        "requestUniversalScreenshotStage19",
        "processUniversalVisualStage19",
        "cachedDrivingDistancesFromAddressKm",
        "drivingDistancesFromAddressKm",
        "isStage19BindingFresh",
    ):
        if required not in transformed:
            fail(f"Applied service missing Stage19 integration: {required}")

    print("stage19_apply=passed")
    print("versionName=0.1.200")
    print("versionCode=5484")
    print("visual_fast_path_precedes_package_gates=true")
    print("accessibility_empty_uses_immediate_ocr=true")
    print("exact_cache_before_network=true")
    print("scheduled_activation_package_gate=false")
    print("bubble_gesture_pause_preserved=true")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source_root", nargs="?", type=Path)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    self_test()
    if args.self_test:
        return
    if args.source_root is None:
        fail("source_root required")
    apply(args.source_root.resolve())


if __name__ == "__main__":
    main()
