#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
TEST = ROOT / 'app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage46SingleDestinationFastPathR6Test.kt'
if not TEST.exists():
    raise SystemExit('Stage46 R7 requires materialized R6 test')
s = TEST.read_text(encoding='utf-8')


def exact(old: str, new: str, label: str) -> None:
    global s
    count = s.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, got {count}')
    s = s.replace(old, new, 1)

# Preserve every pure R6 helper regression. Only assertions that inspect the runtime service integration
# are redirected to R7, because R7 supersedes the service call-sites while the R6 helper stays unchanged.
exact(
    'fun service_integrates_r6_as_fallback_in_accessibility_and_ocr_paths()',
    'fun inherited_r6_service_contract_is_superseded_by_r7_in_accessibility_and_ocr_paths()',
    'service integration test name',
)
exact(
    'assertTrue(s.contains("FAROL_SINGLE_DESTINATION_FAST_PATH_STAGE46_R6 service integration"))',
    'assertTrue(s.contains("FAROL_IMMEDIATE_ADDRESS_ROUTE_STAGE46_R7 service integration"))',
    'service marker assertion',
)
exact(
    'assertTrue(s.split("FarolSingleDestinationFastPathStage46R6.evaluate(").size - 1 >= 2)',
    'assertTrue(s.split("FarolImmediateAddressRouteStage46R7.evaluate(").size - 1 >= 2)',
    'service evaluator count assertion',
)
exact(
    'assertTrue(s.contains("FarolSingleDestinationFastPathStage46R6.validateEvaluation(evaluationStage19)"))',
    'assertTrue(s.contains("FarolImmediateAddressRouteStage46R7.validateEvaluation(evaluationStage19)"))',
    'service validator assertion',
)
exact(
    'assertTrue(s.contains("S46_R6_SINGLE_DESTINATION_FAST_PATH"))',
    'assertTrue(s.contains("S46_R7_IMMEDIATE_SINGLE_ADDRESS"))',
    'service forensic marker assertion',
)
exact(
    'fun legacy_evaluator_is_always_attempted_before_r6_fallback()',
    'fun legacy_evaluator_is_always_attempted_before_r7_fallback()',
    'legacy-first test name',
)
exact(
    'val r6Calls = Regex("FarolSingleDestinationFastPathStage46R6\\\\.evaluate\\\\(").findAll(s).map { it.range.first }.toList()',
    'val r7Calls = Regex("FarolImmediateAddressRouteStage46R7\\\\.evaluate\\\\(").findAll(s).map { it.range.first }.toList()',
    'service evaluator regex',
)
exact('assertTrue(r6Calls.size >= 2)', 'assertTrue(r7Calls.size >= 2)', 'service evaluator count variable')
exact('r6Calls.forEach { r6Index ->', 'r7Calls.forEach { r6Index ->', 'service evaluator loop variable')
exact(
    'fun semantic_acceptance_still_precedes_r3_target_promotion_and_route()',
    'fun r7_semantic_acceptance_still_precedes_r3_target_promotion_and_route()',
    'semantic-order test name',
)
exact(
    'process.indexOf("FarolSingleDestinationFastPathStage46R6.validateEvaluation(evaluationStage19)")',
    'process.indexOf("FarolImmediateAddressRouteStage46R7.validateEvaluation(evaluationStage19)")',
    'semantic-order validator string',
)
exact(
    'fun version_is_stage46_r6_0_1_224_5508()',
    'fun inherited_r6_test_tracks_stage46_r7_0_1_225_5509()',
    'version test name',
)
exact('versionCode = 5508', 'versionCode = 5509', 'versionCode assertion')
exact(r'versionName = \"0.1.224\"', r'versionName = \"0.1.225\"', 'versionName assertion')

# Guard against accidentally rewriting the historical helper behavior.
if s.count('FarolSingleDestinationFastPathStage46R6.evaluate(') < 10:
    raise SystemExit('Stage46 R7 compatibility unexpectedly rewrote pure R6 evaluate regressions')
if s.count('FarolSingleDestinationFastPathStage46R6.decide(') < 5:
    raise SystemExit('Stage46 R7 compatibility unexpectedly rewrote pure R6 decide regressions')
required = (
    'FarolImmediateAddressRouteStage46R7.evaluate(',
    'FarolImmediateAddressRouteStage46R7.validateEvaluation(evaluationStage19)',
    'S46_R7_IMMEDIATE_SINGLE_ADDRESS',
    'versionCode = 5509',
    r'versionName = \"0.1.225\"',
    'FAROL_SINGLE_DESTINATION_FAST_PATH_STAGE46_R6',
)
for value in required:
    if value not in s:
        raise SystemExit(f'Stage46 R7 inherited R6 test compatibility missing: {value}')

TEST.write_text(s, encoding='utf-8')
print('stage46_r7_r6_test_compat=PASS runtime_integration_assertions_moved_to_r7=true pure_r6_helper_regressions_preserved=true')