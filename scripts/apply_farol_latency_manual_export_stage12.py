#!/usr/bin/env python3
"""Stage 12: expose Stage 9 bounded-memory latency events only through manual report export."""
from __future__ import annotations

import argparse
import hashlib
import re
from pathlib import Path

REPORT = Path('app/src/main/java/br/com/mapeiaia/rotacerta/ManualTechnicalReportBuilder.kt')
SERVICE = Path('app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt')
GATE = Path('app/src/main/java/br/com/mapeiaia/rotacerta/FarolRealDeviceGate0188.kt')
PARSER = Path('app/src/main/java/br/com/mapeiaia/rotacerta/UniversalScreenAddressParser.kt')
HELPER = Path('app/src/main/java/br/com/mapeiaia/rotacerta/FarolLatencyProbeStage9.kt')
MAIN = Path('app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt')
BUILD = Path('app/build.gradle.kts')
MARKER = 'FAROL_LATENCY_STAGE9_SNAPSHOT'
DUMP_CALL = 'FarolLatencyProbeStage9.dump()'
CLEAR_CALL = 'FarolLatencyProbeStage9.clear()'

PROTECTED_NEVER_WRITE = (
    Path('app/src/main/AndroidManifest.xml'),
    Path('app/src/main/java/br/com/mapeiaia/rotacerta/DecisionEngine.kt'),
    Path('app/src/main/java/br/com/mapeiaia/rotacerta/RideTextParser.kt'),
    PARSER,
    GATE,
    Path('app/src/main/java/br/com/mapeiaia/rotacerta/FarolVisualPriority0189.kt'),
    Path('app/src/main/java/br/com/mapeiaia/rotacerta/FailedCardRecovery0161.kt'),
    Path('app/src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt'),
    Path('app/src/main/java/br/com/mapeiaia/rotacerta/GpsAddressResolver.kt'),
    Path('app/src/main/java/br/com/mapeiaia/rotacerta/RadarImport.kt'),
    Path('app/src/main/java/br/com/mapeiaia/rotacerta/ForensicIncidentMonitor0193.kt'),
    MAIN,
    SERVICE,
    HELPER,
)

ANCHOR = '''            appendLine("Logs continuos: DESATIVADOS")
            appendLine()
'''

SNAPSHOT_BLOCK = '''            appendLine("Logs continuos: DESATIVADOS")
            appendLine()
            appendLine("--- FAROL_LATENCY_STAGE9_SNAPSHOT ---")
            val farolLatencyStage9SnapshotStage12 = FarolLatencyProbeStage9.dump()
            if (farolLatencyStage9SnapshotStage12.isBlank()) {
                appendLine("(buffer Stage 9 vazio)")
            } else {
                appendLine(farolLatencyStage9SnapshotStage12)
            }
            appendLine()
'''

EXPECTED_COUNTS = {
    'return_tokens': 283,
    'immediate_text_collect': 5,
    'ocr_extract_structured': 1,
    'accessibility_card_blocks': 2,
    'ocr_card_blocks': 2,
    'real_device_gate': 1,
    'authorize_route': 2,
    'bubble_duplicate_skipped': 1,
}


def fail(message: str) -> None:
    raise SystemExit(message)


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def snapshot(root: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for rel in PROTECTED_NEVER_WRITE:
        path = root / rel
        if not path.is_file():
            fail(f'arquivo protegido ausente: {rel}')
        result[str(rel)] = sha(path)
    return result


def structural_counts(service: str) -> dict[str, int]:
    return {
        'return_tokens': len(re.findall(r'\breturn\b', service)),
        'immediate_text_collect': service.count('collectImmediateVisibleTextChecklist13(rootHandle0187.node)'),
        'ocr_extract_structured': service.count('ocrService.extractStructuredText(bitmap0161)'),
        'accessibility_card_blocks': service.count('collectAccessibilityCardBlocks0188('),
        'ocr_card_blocks': service.count('collectOcrCardBlocks0188('),
        'real_device_gate': service.count('FarolRealDeviceGate0188.evaluate('),
        'authorize_route': service.count('authorizeRoute0188('),
        'bubble_duplicate_skipped': service.count('"BUBBLE_DUPLICATE_SKIPPED"'),
    }


def validate_stage9_helper(helper: str) -> None:
    required = (
        'private const val MAX_EVENTS = 512',
        'ArrayDeque<String>(MAX_EVENTS)',
        'SystemClock.elapsedRealtimeNanos()',
        'fun dump(): String',
        'fun size(): Int',
        'fun clear()',
    )
    for token in required:
        if token not in helper:
            fail(f'helper Stage 9 incompleto: {token}')
    forbidden = (
        'UnifiedDebugEventStore', 'FarolFlightRecorder0163', 'SharedPreferences', 'DataStore',
        'java.io.', 'File(', 'writeText(', 'appendText(', 'android.util.Log',
        'Timer(', 'delay(', 'scheduleAtFixedRate', 'while (true)',
    )
    for token in forbidden:
        if token in helper:
            fail(f'helper Stage 9 possui sink/comportamento proibido: {token}')


def validate_hot_path(root: Path) -> None:
    service = (root / SERVICE).read_text(encoding='utf-8')
    counts = structural_counts(service)
    if counts != EXPECTED_COUNTS:
        fail(f'neutralidade estrutural Stage 9/12 divergente: {counts!r}')
    if DUMP_CALL in service or CLEAR_CALL in service or MARKER in service:
        fail('dump/clear/snapshot Stage 12 apareceu no LiveRideAccessibilityService')

    gate = (root / GATE).read_text(encoding='utf-8')
    authority = 'val authorityIdentity = "$selected|${winner.block.windowId}|${winner.block.id}|$signature"'
    screen_hash = 'screenHash = authorityIdentity.hashCode()'
    if gate.count(authority) != 1 or gate.count(screen_hash) != 1:
        fail('autoridade FAROL divergente')
    for rel in (GATE, PARSER):
        text = (root / rel).read_text(encoding='utf-8')
        if DUMP_CALL in text or CLEAR_CALL in text or MARKER in text:
            fail(f'dump/clear/snapshot Stage 12 apareceu em hot-path protegido: {rel}')


def validate_manual_path(root: Path, report_text: str, expect_applied: bool) -> None:
    main = (root / MAIN).read_text(encoding='utf-8')
    if main.count('ManualTechnicalReportBuilder.build(context = context, settings = settings)') != 1:
        fail('caminho manual: esperado exatamente 1 uso de ManualTechnicalReportBuilder.build em MainActivity')
    if main.count('supportReportFileCreator.launch("rota-certa-relatorio-depuracao.txt")') != 2:
        fail('caminho manual: esperado exatamente 2 acionamentos do seletor de relatório')
    if 'buildManualSupportReport(' not in main:
        fail('caminho manual buildManualSupportReport ausente')
    if DUMP_CALL in main or CLEAR_CALL in main or MARKER in main:
        fail('MainActivity não pode conhecer Stage 12')
    if report_text.count('ForensicIncidentMonitor0193.markManualReport()') != 1:
        fail('marcador manual forense existente não foi preservado')
    if report_text.count('FarolFlightRecorder0163.exportReport') != 1:
        fail('export manual do FlightRecorder existente não foi preservado')
    expected_dump = 1 if expect_applied else 0
    expected_marker = 1 if expect_applied else 0
    if report_text.count(DUMP_CALL) != expected_dump:
        fail(f'relatório: dump esperado={expected_dump}, encontrado={report_text.count(DUMP_CALL)}')
    if report_text.count(MARKER) != expected_marker:
        fail(f'relatório: marcador esperado={expected_marker}, encontrado={report_text.count(MARKER)}')
    if CLEAR_CALL in report_text:
        fail('Stage 12 não pode limpar automaticamente o buffer')


def validate_source(root: Path, expect_applied: bool) -> str:
    build = (root / BUILD).read_text(encoding='utf-8')
    if 'versionCode = 5478' not in build or 'versionName = "0.1.194"' not in build:
        fail('fonte não é 0.1.194/5478')
    for rel in (REPORT, SERVICE, GATE, PARSER, HELPER, MAIN):
        if not (root / rel).is_file():
            fail(f'arquivo obrigatório ausente: {rel}')
    validate_stage9_helper((root / HELPER).read_text(encoding='utf-8'))
    validate_hot_path(root)
    report_text = (root / REPORT).read_text(encoding='utf-8')
    validate_manual_path(root, report_text, expect_applied=expect_applied)
    return report_text


def apply_wiring(report_text: str) -> str:
    if MARKER in report_text or DUMP_CALL in report_text:
        fail('wiring Stage 12 já presente')
    count = report_text.count(ANCHOR)
    if count != 1:
        fail(f'âncora do relatório manual: esperado 1, encontrado {count}')
    return report_text.replace(ANCHOR, SNAPSHOT_BLOCK, 1)


def self_test() -> None:
    sample = 'prefix\n' + ANCHOR + 'suffix\n'
    after = apply_wiring(sample)
    if after.count(DUMP_CALL) != 1 or after.count(MARKER) != 1:
        fail('self-test: wiring não foi inserido exatamente uma vez')
    if CLEAR_CALL in after:
        fail('self-test: clear proibido')
    print('farol_latency_stage12_self_test=passed')
    print('manual_snapshot_only=true')


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('source_root', nargs='?', type=Path)
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--self-test', action='store_true')
    args = parser.parse_args()

    if args.self_test:
        self_test()
        if args.source_root is None:
            return
    if args.source_root is None:
        fail('source_root obrigatório fora de --self-test isolado')

    root = args.source_root.resolve()
    report_before = validate_source(root, expect_applied=False)
    protected_before = snapshot(root)
    report_after = apply_wiring(report_before)

    if args.check:
        # Audit the prospective result without writing.
        if report_after.count(DUMP_CALL) != 1 or report_after.count(MARKER) != 1:
            fail('check: wiring prospectivo inválido')
        if CLEAR_CALL in report_after:
            fail('check: clear proibido')
        print('farol_latency_stage12_check=passed')
        print('manual_snapshot_only=true')
        print('hot_path_sink=absent')
        return

    (root / REPORT).write_text(report_after, encoding='utf-8')
    protected_after = snapshot(root)
    if protected_after != protected_before:
        fail('Stage 12 alterou arquivo protegido')
    validate_source(root, expect_applied=True)

    # The exact Stage 9 dump call may exist only in the manual report builder.
    production_root = root / 'app/src/main/java/br/com/mapeiaia/rotacerta'
    call_sites = []
    for path in production_root.glob('*.kt'):
        text = path.read_text(encoding='utf-8')
        if DUMP_CALL in text:
            call_sites.append(path.name)
    if call_sites != ['ManualTechnicalReportBuilder.kt']:
        fail(f'dump Stage 9 fora do caminho manual: {call_sites!r}')

    print('farol_latency_stage12_apply=passed')
    print('manual_snapshot_only=true')
    print('hot_path_sink=absent')
    print('buffer_auto_clear=false')


if __name__ == '__main__':
    main()
