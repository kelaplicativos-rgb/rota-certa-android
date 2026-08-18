#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
TESTS = ROOT / 'app/src/test/java/br/com/mapeiaia/rotacerta'
R6 = TESTS / 'FarolStage46SingleDestinationFastPathR6Test.kt'
R7 = TESTS / 'FarolStage46ImmediateAddressRouteR7Test.kt'
if not R6.exists() or not R7.exists():
    raise SystemExit('Stage46 R8 requires materialized R6 and R7 tests')


def exact(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, got {count}')
    return text.replace(old, new, 1)

r6 = R6.read_text(encoding='utf-8')
for old, new, label in (
    ('FAROL_IMMEDIATE_ADDRESS_ROUTE_STAGE46_R7 service integration', 'FAROL_POSITIVE_LOCATION_EVIDENCE_STAGE46_R8 service integration', 'R6 service marker'),
    ('s.split("FarolImmediateAddressRouteStage46R7.evaluate(")', 's.split("FarolRouteLocationEvidenceStage46R8.evaluate(")', 'R6 evaluator count'),
    ('assertTrue(s.contains("FarolImmediateAddressRouteStage46R7.validateEvaluation(evaluationStage19)"))', 'assertTrue(s.contains("FarolRouteLocationEvidenceStage46R8.validateEvaluation(evaluationStage19)"))', 'R6 runtime validator assertion'),
    ('process.indexOf("FarolImmediateAddressRouteStage46R7.validateEvaluation(evaluationStage19)")', 'process.indexOf("FarolRouteLocationEvidenceStage46R8.validateEvaluation(evaluationStage19)")', 'R6 semantic-order validator string'),
    ('S46_R7_IMMEDIATE_SINGLE_ADDRESS', 'S46_R8_POSITIVE_SINGLE_LOCATION', 'R6 runtime marker'),
    ('Regex("FarolImmediateAddressRouteStage46R7\\\\.evaluate\\\\(")', 'Regex("FarolRouteLocationEvidenceStage46R8\\\\.evaluate\\\\(")', 'R6 runtime regex'),
    ('versionCode = 5509', 'versionCode = 5510', 'R6 version code'),
    (r'versionName = \"0.1.225\"', r'versionName = \"0.1.226\"', 'R6 version name'),
):
    r6 = exact(r6, old, new, label)
R6.write_text(r6, encoding='utf-8')

r7 = R7.read_text(encoding='utf-8')
for old, new, label in (
    ('assertTrue(s.contains("FAROL_IMMEDIATE_ADDRESS_ROUTE_STAGE46_R7 service integration"))', 'assertTrue(s.contains("FAROL_POSITIVE_LOCATION_EVIDENCE_STAGE46_R8 service integration"))', 'R7 service marker'),
    ('assertTrue(s.split("FarolImmediateAddressRouteStage46R7.evaluate(").size - 1 >= 2)', 'assertTrue(s.split("FarolRouteLocationEvidenceStage46R8.evaluate(").size - 1 >= 2)', 'R7 evaluator count'),
    ('assertTrue(s.contains("FarolImmediateAddressRouteStage46R7.evaluateImmediateText("))', 'assertTrue(s.contains("FarolRouteLocationEvidenceStage46R8.evaluateImmediateText("))', 'R7 immediate text'),
    ('assertTrue(s.contains("FarolImmediateAddressRouteStage46R7.validateEvaluation(evaluationStage19)"))', 'assertTrue(s.contains("FarolRouteLocationEvidenceStage46R8.validateEvaluation(evaluationStage19)"))', 'R7 validator'),
    ('assertTrue(s.contains("S46_R7_IMMEDIATE_SINGLE_ADDRESS"))', 'assertTrue(s.contains("S46_R8_POSITIVE_SINGLE_LOCATION"))', 'R7 single marker'),
    ('assertTrue(s.contains("S46_R7_LAST_VISUAL_DESTINATION"))', 'assertTrue(s.contains("S46_R8_LAST_VALID_LOCATION"))', 'R7 last marker'),
    ('val r7Calls = Regex("FarolImmediateAddressRouteStage46R7\\\\.evaluate\\\\(").findAll(s).map { it.range.first }.toList()', 'val r7Calls = Regex("FarolRouteLocationEvidenceStage46R8\\\\.evaluate\\\\(").findAll(s).map { it.range.first }.toList()', 'R7 fallback regex'),
    ('versionCode = 5509', 'versionCode = 5510', 'R7 version code'),
    (r'versionName = \"0.1.225\"', r'versionName = \"0.1.226\"', 'R7 version name'),
):
    r7 = exact(r7, old, new, label)
R7.write_text(r7, encoding='utf-8')

print('stage46_r8_test_compat=PASS inherited_runtime_assertions_moved_to_r8=true pure_r6_r7_helpers_preserved=true version=0.1.226/5510')
