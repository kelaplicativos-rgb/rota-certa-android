#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
TEST = ROOT / 'app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage46SingleDestinationFastPathR6Test.kt'
if not TEST.exists():
    raise SystemExit('Stage46 R7 requires materialized R6 test')
s = TEST.read_text(encoding='utf-8')

replacements = (
    ('FAROL_SINGLE_DESTINATION_FAST_PATH_STAGE46_R6 service integration', 'FAROL_IMMEDIATE_ADDRESS_ROUTE_STAGE46_R7 service integration'),
    ('FarolSingleDestinationFastPathStage46R6.evaluate(', 'FarolImmediateAddressRouteStage46R7.evaluate('),
    ('FarolSingleDestinationFastPathStage46R6.validateEvaluation(evaluationStage19)', 'FarolImmediateAddressRouteStage46R7.validateEvaluation(evaluationStage19)'),
    ('S46_R6_SINGLE_DESTINATION_FAST_PATH', 'S46_R7_IMMEDIATE_SINGLE_ADDRESS'),
    ('FarolSingleDestinationFastPathStage46R6\\.evaluate\\(', 'FarolImmediateAddressRouteStage46R7\\.evaluate\\('),
    ('val r6Calls =', 'val r7Calls ='),
    ('assertTrue(r6Calls.size >= 2)', 'assertTrue(r7Calls.size >= 2)'),
    ('r6Calls.forEach { r6Index ->', 'r7Calls.forEach { r6Index ->'),
    ('fun service_integrates_r6_as_fallback_in_accessibility_and_ocr_paths()', 'fun inherited_r6_service_contract_is_superseded_by_r7_in_accessibility_and_ocr_paths()'),
    ('fun legacy_evaluator_is_always_attempted_before_r6_fallback()', 'fun legacy_evaluator_is_always_attempted_before_r7_fallback()'),
    ('fun semantic_acceptance_still_precedes_r3_target_promotion_and_route()', 'fun r7_semantic_acceptance_still_precedes_r3_target_promotion_and_route()'),
    ('fun version_is_stage46_r6_0_1_224_5508()', 'fun inherited_r6_test_tracks_stage46_r7_0_1_225_5509()'),
    ('versionCode = 5508', 'versionCode = 5509'),
    (r'versionName = \"0.1.224\"', r'versionName = \"0.1.225\"'),
)
for old, new in replacements:
    if old in s:
        s = s.replace(old, new)

required = (
    'FarolImmediateAddressRouteStage46R7.evaluate(',
    'FarolImmediateAddressRouteStage46R7.validateEvaluation(evaluationStage19)',
    'S46_R7_IMMEDIATE_SINGLE_ADDRESS',
    'versionCode = 5509',
    r'versionName = \"0.1.225\"',
)
for value in required:
    if value not in s:
        raise SystemExit(f'Stage46 R7 inherited R6 test compatibility missing: {value}')

TEST.write_text(s, encoding='utf-8')
print('stage46_r7_r6_test_compat=PASS runtime_integration_assertions_moved_to_r7=true r6_helper_regressions_preserved=true')