#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
BUILD = ROOT / 'app/build.gradle.kts'
TESTS = ROOT / 'app/src/test/java/br/com/mapeiaia/rotacerta'


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding='utf-8')
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 inherited assertion, got {count}')
    path.write_text(text.replace(old, new, 1), encoding='utf-8')


# Stage42 intentionally supersedes five historical source-contract assertions while keeping
# their regression value: manual reading must be present in Home/grid, and selected-app/process
# presence must be absent from the functional activation critical path.
replace_once(
    TESTS / 'AccessibilityResilienceAndTools0172ContractTest.kt',
    '        assertTrue(catalog.contains("modules.size >= 21"))\n',
    '        assertTrue(catalog.contains("modules.size >= 22"))\n'
    '        assertTrue(catalog.contains("ReadingBubbleShortcutModule"))\n',
    '0172 Home catalog now includes manual reading module',
)
replace_once(
    TESTS / 'GeneralControlsPlacesPopupChecklist7Test.kt',
    '        assertFalse("leitura não pode continuar no popup", "ReadingBubbleShortcutModule," in catalog)\n',
    '        assertTrue("leitura manual deve estar no catálogo da Home e ser elegível para a grade", "ReadingBubbleShortcutModule," in catalog)\n',
    'checklist7 reading module promotion',
)
replace_once(
    TESTS / 'WorkModeSessionContract0162Test.kt',
    '        assertTrue("Modo Trabalho" in activity)\n        assertTrue("WorkModePolicy0162.setEnabled" in activity)\n',
    '        assertTrue("Leitura do Farol" in activity)\n'
    '        assertTrue("FarolManualReadingAuthorityStage42.setEnabled" in activity)\n',
    '0162 manual reading UI authority',
)
replace_once(
    TESTS / 'FarolStage36RuntimeTest.kt',
    '    @Test fun serviceUsesStage36Authority(){val s=service();assertTrue(s.contains("stage36RuntimeAuthority"));assertTrue(s.contains("stage36RuntimeAuthority.observeVisualEvidence()"));assertTrue(s.contains("stage36RuntimeAuthority.observeWindowBoundary"))}\n',
    '    @Test fun serviceUsesStage36Authority(){val s=service();assertTrue(s.contains("stage36RuntimeAuthority"));assertTrue(s.contains("stage36RuntimeAuthority.setManualAuthority"));assertTrue(s.contains("private fun isStage36WorkFresh("));assertFalse(s.substringAfter("private fun refreshReadingActivationStage26(").substringBefore("private fun applyReadingOffStage26(").contains("observeWindowBoundary"))}\n',
    'Stage36 authority preserved without package/window activation observers',
)
replace_once(
    TESTS / 'FarolStage36RuntimeTest.kt',
    '    @Test fun processShadowStillDiagnostic(){assertTrue(service().contains("updateProcessShadow"));assertTrue(src("FarolPresenceAuthorityStage30.kt").contains("RUNNING_APP_PROCESSES_SHADOW_ONLY_STAGE30"))}\n',
    '    @Test fun processShadowStillDiagnostic(){val f=service().substringAfter("private fun refreshReadingActivationStage26(").substringBefore("private fun applyReadingOffStage26(");assertFalse(f.contains("updateProcessShadow"));assertFalse(f.contains("readProcessShadow"));assertTrue(src("FarolPresenceAuthorityStage30.kt").contains("RUNNING_APP_PROCESSES_SHADOW_ONLY_STAGE30"))}\n',
    'Stage30 process shadow retained as historical diagnostic but removed from critical path',
)

s = BUILD.read_text(encoding='utf-8')
if s.count('versionCode = 5497') != 1:
    raise SystemExit('Stage42 expected versionCode 5497 exactly once')
if s.count('versionName = "0.1.213"') != 1:
    raise SystemExit('Stage42 expected versionName 0.1.213 exactly once')
s = s.replace('versionCode = 5497', 'versionCode = 5498', 1)
s = s.replace('versionName = "0.1.213"', 'versionName = "0.1.214"', 1)
BUILD.write_text(s, encoding='utf-8')

checks = [
    (
        TESTS / 'FarolStage34Test.kt',
        'assertTrue(s.contains("versionCode = 5497")); assertTrue(s.contains("versionName = \\"0.1.213\\""))',
        'assertTrue(s.contains("versionCode = 5498")); assertTrue(s.contains("versionName = \\"0.1.214\\""))',
        'Stage34 version',
    ),
    (
        TESTS / 'FarolStage36RuntimeTest.kt',
        'assertTrue(b.contains("versionCode = 5497"));assertTrue(b.contains("versionName = \\"0.1.213\\""))',
        'assertTrue(b.contains("versionCode = 5498"));assertTrue(b.contains("versionName = \\"0.1.214\\""))',
        'Stage36 version',
    ),
]
for path, old, new, label in checks:
    replace_once(path, old, new, label)

print('stage42_version=PASS versionName=0.1.214 versionCode=5498 inherited_version_assertions=2 superseded_contracts=5')
