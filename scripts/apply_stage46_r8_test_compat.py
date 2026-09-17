#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
TESTS = ROOT / 'app/src/test/java/br/com/mapeiaia/rotacerta'
R6 = TESTS / 'FarolStage46SingleDestinationFastPathR6Test.kt'
R7 = TESTS / 'FarolStage46ImmediateAddressRouteR7Test.kt'
R7_UNIVERSAL = TESTS / 'FarolStage46UniversalVisibleLocationR7Test.kt'
if not R6.exists() or not R7.exists() or not R7_UNIVERSAL.exists():
    raise SystemExit('Stage46 R8 requires materialized R6/R7 test inventory')


def exact(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, got {count}')
    return text.replace(old, new, 1)

# R6 inherited runtime-integration assertions now point at the R8 authority. Pure R6 helper
# regressions remain untouched; the R7 helper itself is protected byte-for-byte by the R8 CI.
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

# R7 service/runtime assertions likewise follow the R8 authority while its pure helper inventory
# remains materialized and protected.
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

# R6 had already advanced these historical version assertions to 0.1.224/5508. They validate the
# materialized final application, so R8 must advance only their version expectation, not behavior.
stage46_version_files = (
    'FarolStage46VisualEpochNoResultTest.kt',
    'FarolStage46TargetSurfaceR2Test.kt',
    'FarolStage46AcquisitionSurfaceR3Test.kt',
    'FarolStage46StableFinalLatchR4Test.kt',
    'FarolStage46AtomicTransitionR5Test.kt',
)
for name in stage46_version_files:
    path = TESTS / name
    s = path.read_text(encoding='utf-8')
    if s.count('versionCode = 5508') != 1 or s.count(r'versionName = \"0.1.224\"') != 1:
        raise SystemExit(f'{name}: inherited R6 final-version assertion not found exactly once')
    s = s.replace('versionCode = 5508', 'versionCode = 5510', 1)
    s = s.replace(r'versionName = \"0.1.224\"', r'versionName = \"0.1.226\"', 1)
    s = s.replace('version_is_stage46_r6_0_1_224_5508', 'inherited_stage46_version_tracks_r8_0_1_226_5510', 1)
    path.write_text(s, encoding='utf-8')

legacy_exact = (
    (
        'FarolStage34Test.kt',
        'assertTrue(s.contains("versionCode = 5508")); assertTrue(s.contains("versionName = \\"0.1.224\\""))',
        'assertTrue(s.contains("versionCode = 5510")); assertTrue(s.contains("versionName = \\"0.1.226\\""))',
    ),
    (
        'FarolStage36RuntimeTest.kt',
        'assertTrue(b.contains("versionCode = 5508"));assertTrue(b.contains("versionName = \\"0.1.224\\""))',
        'assertTrue(b.contains("versionCode = 5510"));assertTrue(b.contains("versionName = \\"0.1.226\\""))',
    ),
)
for name, old, new in legacy_exact:
    path = TESTS / name
    s = path.read_text(encoding='utf-8')
    path.write_text(exact(s, old, new, f'{name} inherited final-version assertion'), encoding='utf-8')

# The R7 universal-location suite describes observable route behavior. In R8, runtime authority moved
# to the positive-evidence wrapper, so exercise the same unchanged expectations through R8 while the
# R7 helper remains byte-for-byte protected. No expected destination, hygiene rule or marker is relaxed.
universal = R7_UNIVERSAL.read_text(encoding='utf-8')
eval_old = 'FarolImmediateAddressRouteStage46R7.evaluate('
eval_count = universal.count(eval_old)
if eval_count < 8:
    raise SystemExit(f'R7 universal runtime evaluator inventory unexpectedly small: {eval_count}')
universal = universal.replace(eval_old, 'FarolRouteLocationEvidenceStage46R8.evaluate(')
validator_old = 'FarolImmediateAddressRouteStage46R7.validateEvaluation('
validator_count = universal.count(validator_old)
if validator_count != 3:
    raise SystemExit(f'R7 universal validator inventory expected 3, got {validator_count}')
universal = universal.replace(validator_old, 'FarolRouteLocationEvidenceStage46R8.validateEvaluation(')
for preserved_expectation in (
    'assertEquals("Hospital das Clínicas", result!!.destination)',
    'assertEquals("Estação da Luz (Metrô)", result!!.destination)',
    'assertTrue(result!!.destination.contains("Hospital das Clínicas"))',
    'assertNull(FarolRouteLocationEvidenceStage46R8.evaluate(listOf(block("R$ 31,50", "w/fare", 400, 500))))',
    'assertFalse(helper.contains("FarolCausalCorrectionStage21.validateAddress("))',
):
    if preserved_expectation not in universal:
        raise SystemExit(f'R7 universal preserved expectation missing after R8 authority migration: {preserved_expectation}')
R7_UNIVERSAL.write_text(universal, encoding='utf-8')

print(
    'stage46_r8_test_compat=PASS '
    'inherited_runtime_assertions_moved_to_r8=true '
    'inherited_versions_track_r8=true '
    f'r7_universal_runtime_calls_moved_to_r8={eval_count} '
    'r7_universal_expectations_unchanged=true '
    'pure_r6_r7_helpers_preserved=true version=0.1.226/5510'
)
