#!/usr/bin/env python3
from __future__ import annotations

import argparse
import shutil
from pathlib import Path

PATCH_ROOT = Path(__file__).resolve().parents[1]
STAGE28_APPLIER = PATCH_ROOT / 'scripts/apply_farol_causal_latency_stage28_v2.py'
SERVICE = Path('app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt')
REPORT = Path('app/src/main/java/br/com/mapeiaia/rotacerta/ManualTechnicalReportBuilder.kt')
BUILD = Path('app/build.gradle.kts')
AUTHORITY = Path('app/src/main/java/br/com/mapeiaia/rotacerta/FarolPresenceAuthorityStage30.kt')
ADAPTER = Path('app/src/main/java/br/com/mapeiaia/rotacerta/SelectedAppPresenceStateStage30.kt')
TEST = Path('app/src/test/java/br/com/mapeiaia/rotacerta/FarolPresenceAuthorityStage30Test.kt')
AUTHORITY_TEMPLATE = PATCH_ROOT / 'stage30/FarolPresenceAuthorityStage30.kt'
ADAPTER_TEMPLATE = PATCH_ROOT / 'stage30/SelectedAppPresenceStateStage30.kt'
TEST_TEMPLATE = PATCH_ROOT / 'stage30/FarolPresenceAuthorityStage30Test.kt'


def fail(message: str) -> None:
    raise SystemExit(message)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        fail(f'Stage30 anchor {label}: expected 1, found {count}')
    return text.replace(old, new, 1)


def replace_section(text: str, start: str, end: str, replacement: str, label: str) -> str:
    a = text.find(start)
    b = text.find(end, a + len(start))
    if a < 0 or b <= a:
        fail(f'Stage30 section {label}: markers not found')
    return text[:a] + replacement + text[b:]


def self_test() -> None:
    for path in (STAGE28_APPLIER, AUTHORITY_TEMPLATE, ADAPTER_TEMPLATE, TEST_TEMPLATE):
        if not path.is_file():
            fail(f'missing Stage30 dependency: {path}')
    authority = AUTHORITY_TEMPLATE.read_text(encoding='utf-8')
    adapter = ADAPTER_TEMPLATE.read_text(encoding='utf-8')
    tests = TEST_TEMPLATE.read_text(encoding='utf-8')
    required_authority = (
        'FAROL_PRESENCE_AUTHORITY_STAGE30',
        'ACCESSIBILITY_DIRECT_ACTIVATES_STAGE30',
        'SESSION_BOUNDED_USAGE_EVENTS_STAGE30',
        'RUNNING_APP_PROCESSES_SHADOW_ONLY_STAGE30',
        'SHADOW_AUTHORITY_STAGE30',
        'PHYSICAL_FAILURE_REPLAY_STAGE30',
        'NO_POLLING_NO_SLEEP_NO_DEBOUNCE_STAGE30',
        'SELECTED_PACKAGE_ONLY_TURNS_READING_ON_OFF_STAGE30',
        'observeAccessibility', 'observeWindowBoundary', 'applyUsageEvidence', 'updateProcessShadow',
    )
    for marker in required_authority:
        if marker not in authority:
            fail(f'Stage30 authority missing {marker}')
    required_adapter = (
        'UsageEventsQuery.Builder', 'queryEvents', 'sessionStartWallMillis', 'usageCursorMillis',
        'DELIVERY_OVERLAP_MS', 'RUNNING_PROCESS_NEVER_AUTHORITY_STAGE30', 'runningAppProcesses',
    )
    for marker in required_adapter:
        if marker not in adapter:
            fail(f'Stage30 adapter missing {marker}')
    for forbidden in ('Thread.sleep(', 'SystemClock.sleep(', 'postDelayed(', 'Timer(', 'scheduleAtFixedRate('):
        if forbidden in authority or forbidden in adapter:
            fail(f'Stage30 forbidden polling/debounce primitive: {forbidden}')
    if tests.count('@Test') != 48:
        fail(f'expected exactly 48 Stage30 tests, found {tests.count("@Test")}')
    mandatory = (
        'directUberAccessibilityMeansOn', 'direct99AccessibilityMeansOn', 'directIndriveAccessibilityMeansOn',
        'stage29UberReplayWorksWithEmptyProcessShadow', 'stage29App99ReplayWorksWithEmptyProcessShadow',
        'stage29IndriveReplayWorksWithEmptyProcessShadow', 'processShadowAloneCannotTurnOn',
        'oldUsageBeforeSessionCannotTurnOn', 'closeOneOfTwoKeepsOn', 'closeLastOfTwoTurnsOff',
        'windowBoundaryAwayKeepsOnWhenForegroundServiceCurrent', 'stage28OldWorkStillCannotPaintAfterReadingOff',
        'stage28OcrCoalescenceStillWorks', 'stage28RouteDedupStillWorks', 'shadowReportNamesAllAuthorities',
    )
    for name in mandatory:
        if name not in tests:
            fail(f'Stage30 mandatory test missing {name}')
    print('stage30_self_test=passed')
    print('stage30_test_methods=48')
    print('authority=accessibility_current_plus_session_usage')
    print('running_process=shadow_only')
    print('usage_history_before_session=false')
    print('physical_replays=indrive,99,uber')
    print('polling=false')


def apply(root: Path) -> None:
    for path in (SERVICE, REPORT, BUILD):
        if not (root / path).is_file():
            fail(f'Stage30 requires source tree: {path}')
    service = (root / SERVICE).read_text(encoding='utf-8')
    report = (root / REPORT).read_text(encoding='utf-8')
    build = (root / BUILD).read_text(encoding='utf-8')
    if 'FAROL_CAUSAL_LATENCY_STALE_ACTIVATION_STAGE28' not in service:
        fail('Stage30 must be applied after exact Stage28 materialization')
    if 'versionCode = 5489' not in build or 'versionName = "0.1.205"' not in build:
        fail('Stage30 requires exact Stage28 0.1.205/5489 baseline')
    if (root / AUTHORITY).exists() or 'FAROL_PRESENCE_AUTHORITY_STAGE30' in service:
        fail('Stage30 already appears applied')

    shutil.copyfile(AUTHORITY_TEMPLATE, root / AUTHORITY)
    shutil.copyfile(ADAPTER_TEMPLATE, root / ADAPTER)
    shutil.copyfile(TEST_TEMPLATE, root / TEST)

    service = replace_once(
        service,
        '    private lateinit var stage28UsageState: SelectedAppUsageStateStage28\n',
        '    private lateinit var stage30PresenceState: SelectedAppPresenceStateStage30\n'
        '    private lateinit var stage30PresenceAuthority: FarolPresenceAuthorityStage30.Authority\n'
        '    // FAROL_PRESENCE_AUTHORITY_STAGE30 — process state is shadow only; visual package never authorizes content.\n',
        'Stage30 service presence state',
    )
    service = replace_once(
        service,
        '        stage28UsageState = SelectedAppUsageStateStage28(applicationContext)\n',
        '        stage30PresenceState = SelectedAppPresenceStateStage30(applicationContext)\n'
        '        stage30PresenceAuthority = FarolPresenceAuthorityStage30.Authority(stage30PresenceState.sessionStartWallMillis)\n',
        'Stage30 presence init',
    )

    refresh = '''    private fun refreshReadingActivationStage26(
        eventPackageStage26: String?,
        eventTypeStage26: Int,
    ): FarolReadingActivationStage26.ActivationSnapshot {
        val startedStage30 = SystemClock.elapsedRealtimeNanos()
        val nowWallStage30 = System.currentTimeMillis()
        val selectedStage30 = SelectedRideAppStore.read(applicationContext)
        val selectedNormalizedStage30 = selectedStage30.mapNotNull(FarolPresenceAuthorityStage30::normalizePackage).toSet()
        stage26ReadingActivation.updateSelection(selectedStage30)
        stage30PresenceAuthority.updateSelection(selectedStage30)
        val usageAccessStage30 = stage30PresenceState.hasUsageAccess()
        stage30PresenceAuthority.setUsageAccess(usageAccessStage30)

        val eventPackageNormalizedStage30 = FarolPresenceAuthorityStage30.normalizePackage(eventPackageStage26)
        val selectedEventStage30 = eventPackageNormalizedStage30 != null && eventPackageNormalizedStage30 in selectedNormalizedStage30
        val windowBoundaryStage30 = eventTypeStage26 == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        val windowsChangedStage30 = eventTypeStage26 == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        val wasEnabledStage30 = stage30PresenceAuthority.snapshot().enabled

        if (windowBoundaryStage30) {
            stage30PresenceAuthority.observeWindowBoundary(eventPackageNormalizedStage30)
        }
        if (selectedEventStage30) {
            stage30PresenceAuthority.observeAccessibility(eventPackageNormalizedStage30, eventTypeStage26, nowWallStage30)
        }

        val refreshUsageStage30 = windowBoundaryStage30 || windowsChangedStage30 || (selectedEventStage30 && !wasEnabledStage30)
        if (usageAccessStage30 && refreshUsageStage30) {
            stage30PresenceAuthority.applyUsageEvidence(
                stage30PresenceState.readIncrementalUsage(selectedStage30, nowWallStage30),
            )
        }

        val presenceStage30 = stage30PresenceAuthority.snapshot()
        stage26ReadingActivation.setUsageAccess(presenceStage30.usageAccessGranted)
        stage26ReadingActivation.replaceUsageState(
            FarolCausalLatencyStage28.currentExecutionEvents(presenceStage30.authoritativeActivePackages),
        )
        stage26UsageInitialized = true
        val snapshotStage30 = stage26ReadingActivation.snapshot()

        if (snapshotStage30.enabled != stage28LastActivationEnabled) {
            FarolCausalLatencyStage28.Metrics.increment(if (snapshotStage30.enabled) "activationOn" else "activationOff")
            stage28LastActivationEnabled = snapshotStage30.enabled
        }
        FarolCausalLatencyStage28.Metrics.setGauge("selectedAppsActiveCount", snapshotStage30.selectedAppsActiveCount.toLong())
        FarolCausalLatencyStage28.Metrics.setGauge("activationGeneration", snapshotStage30.generation)
        FarolCausalLatencyStage28.Metrics.sample(
            "eventToActivationState",
            SystemClock.elapsedRealtimeNanos() - startedStage30,
        )

        if (selectedEventStage30 || windowBoundaryStage30 || windowsChangedStage30) {
            stage30PresenceAuthority.updateProcessShadow(stage30PresenceState.readProcessShadow(selectedStage30))
        }
        return snapshotStage30
    }

'''
    service = replace_section(
        service,
        '    private fun refreshReadingActivationStage26(',
        '    private fun applyReadingOffStage26(',
        refresh,
        'Stage30 causal presence refresh',
    )

    report = replace_once(
        report,
        '            appendLine(FarolCausalLatencyStage28.Metrics.exportReport())\n',
        '            appendLine(FarolCausalLatencyStage28.Metrics.exportReport())\n'
        '            appendLine()\n'
        '            appendLine(FarolPresenceAuthorityStage30.Diagnostics.export())\n',
        'Stage30 manual diagnostics',
    )

    build = replace_once(build, 'versionCode = 5489', 'versionCode = 5490', 'versionCode')
    build = replace_once(build, 'versionName = "0.1.205"', 'versionName = "0.1.206"', 'versionName')

    (root / SERVICE).write_text(service, encoding='utf-8')
    (root / REPORT).write_text(report, encoding='utf-8')
    (root / BUILD).write_text(build, encoding='utf-8')

    transformed = (root / SERVICE).read_text(encoding='utf-8')
    report_now = (root / REPORT).read_text(encoding='utf-8')
    build_now = (root / BUILD).read_text(encoding='utf-8')
    refresh_slice = transformed[
        transformed.index('    private fun refreshReadingActivationStage26('):
        transformed.index('    private fun applyReadingOffStage26(')
    ]
    required_runtime = (
        'FAROL_PRESENCE_AUTHORITY_STAGE30', 'stage30PresenceState', 'stage30PresenceAuthority',
        'observeAccessibility', 'observeWindowBoundary', 'readIncrementalUsage',
        'authoritativeActivePackages', 'readProcessShadow',
    )
    for marker in required_runtime:
        if marker not in transformed:
            fail(f'Stage30 runtime missing {marker}')
    if 'stage28UsageState.readCurrentExecution' in refresh_slice or 'SelectedAppUsageStateStage28' in refresh_slice:
        fail('Stage30 runtime still depends on Stage28 process authority')
    if refresh_slice.index('observeAccessibility') > refresh_slice.index('replaceUsageState'):
        fail('Stage30 selected Accessibility evidence must precede Stage26 activation snapshot')
    if 'readProcessShadow(selectedStage30)' not in refresh_slice:
        fail('Stage30 shadow comparison missing')
    if refresh_slice.index('readProcessShadow(selectedStage30)') < refresh_slice.index('val snapshotStage30 = stage26ReadingActivation.snapshot()'):
        fail('Stage30 process shadow cannot run before authoritative activation decision')
    if 'FarolPresenceAuthorityStage30.Diagnostics.export()' not in report_now:
        fail('Stage30 manual diagnostic report not wired')
    if 'versionCode = 5490' not in build_now or 'versionName = "0.1.206"' not in build_now:
        fail('Stage30 version not materialized')
    print('stage30_apply=passed')
    print('versionName=0.1.206')
    print('versionCode=5490')
    print('authority=accessibility_current_plus_session_usage')
    print('process_shadow_only=true')
    print('stage28_freshness_preserved=true')
    print('visual_package_authority=false')
    print('legacy_work_mode_not_activation_authority=true')


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('source_root', nargs='?', type=Path)
    parser.add_argument('--self-test', action='store_true')
    args = parser.parse_args()
    self_test()
    if args.self_test:
        return
    if args.source_root is None:
        fail('source_root required')
    apply(args.source_root.resolve())


if __name__ == '__main__':
    main()
