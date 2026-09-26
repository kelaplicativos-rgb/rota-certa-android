#!/usr/bin/env python3
"""Stage 9 FAROL latency instrumentation, fail-closed and memory-only."""
from __future__ import annotations
import argparse
import hashlib
import re
from pathlib import Path

SERVICE = Path('app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt')
HELPER = Path('app/src/main/java/br/com/mapeiaia/rotacerta/FarolLatencyProbeStage9.kt')
BUILD = Path('app/build.gradle.kts')
MARKER = 'FAROL_LATENCY_STAGE9'
PROTECTED_NEVER_WRITE = (
    Path('app/src/main/AndroidManifest.xml'),
    Path('app/src/main/java/br/com/mapeiaia/rotacerta/DecisionEngine.kt'),
    Path('app/src/main/java/br/com/mapeiaia/rotacerta/RideTextParser.kt'),
    Path('app/src/main/java/br/com/mapeiaia/rotacerta/UniversalScreenAddressParser.kt'),
    Path('app/src/main/java/br/com/mapeiaia/rotacerta/FarolRealDeviceGate0188.kt'),
    Path('app/src/main/java/br/com/mapeiaia/rotacerta/FarolVisualPriority0189.kt'),
    Path('app/src/main/java/br/com/mapeiaia/rotacerta/FailedCardRecovery0161.kt'),
    Path('app/src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt'),
    Path('app/src/main/java/br/com/mapeiaia/rotacerta/GpsAddressResolver.kt'),
    Path('app/src/main/java/br/com/mapeiaia/rotacerta/RadarImport.kt'),
    Path('app/src/main/java/br/com/mapeiaia/rotacerta/ForensicIncidentMonitor0193.kt'),
    Path('app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt'),
)
HELPER_SOURCE = r'''package br.com.mapeiaia.rotacerta

import android.os.SystemClock
import java.util.ArrayDeque

/** Temporary Stage 9 latency probe. Memory-only; never participates in FAROL decisions. */
internal object FarolLatencyProbeStage9 {
    const val MARKER = "FAROL_LATENCY_STAGE9"
    private const val MAX_EVENTS = 512
    private val lock = Any()
    private val events = ArrayDeque<String>(MAX_EVENTS)

    @Volatile private var accessibilityReadStartNs: Long = 0L
    @Volatile private var ocrReadStartNs: Long = 0L

    private fun sourceStart(source: String): Long = when (source) {
        "Accessibility" -> accessibilityReadStartNs
        "Ocr", "OCR" -> ocrReadStartNs
        else -> 0L
    }

    private fun rememberSourceStart(source: String, startedNs: Long) {
        when (source) {
            "Accessibility" -> accessibilityReadStartNs = startedNs
            "Ocr", "OCR" -> ocrReadStartNs = startedNs
        }
    }

    private fun durationDetails(startedNs: Long, endedNs: Long): String {
        val ns = (endedNs - startedNs).coerceAtLeast(0L)
        return "duration_us=${ns / 1_000L}; duration_ms=${ns / 1_000_000L}"
    }

    private fun record(stage: String, source: String, details: String) {
        val line = "${MARKER}_$stage; source=$source; $details"
        synchronized(lock) {
            if (events.size >= MAX_EVENTS) events.removeFirst()
            events.addLast(line)
        }
    }

    fun dump(): String = synchronized(lock) { events.joinToString("\n") }
    fun size(): Int = synchronized(lock) { events.size }
    fun clear() = synchronized(lock) { events.clear() }

    fun measureText(stage: String, source: String, block: () -> String): String {
        val startedNs = SystemClock.elapsedRealtimeNanos()
        rememberSourceStart(source, startedNs)
        val result = block()
        val endedNs = SystemClock.elapsedRealtimeNanos()
        record(stage, source, "${durationDetails(startedNs, endedNs)}; text_length=${result.length}; duplicate_skipped=false")
        return result
    }

    fun <T> measureBlocks(stage: String, source: String, block: () -> List<T>): List<T> {
        val startedNs = SystemClock.elapsedRealtimeNanos()
        val result = block()
        val endedNs = SystemClock.elapsedRealtimeNanos()
        record(stage, source, "${durationDetails(startedNs, endedNs)}; blocks=${result.size}; duplicate_skipped=false")
        return result
    }

    fun <T> measureValue(stage: String, source: String, block: () -> T): T {
        val startedNs = SystemClock.elapsedRealtimeNanos()
        val result = block()
        val endedNs = SystemClock.elapsedRealtimeNanos()
        record(stage, source, "${durationDetails(startedNs, endedNs)}; duplicate_skipped=false")
        return result
    }

    fun recordOcrStructured(startedNs: Long, textLength: Int, blockCount: Int) {
        val endedNs = SystemClock.elapsedRealtimeNanos()
        rememberSourceStart("OCR", startedNs)
        record("OCR_EXTRACT_STRUCTURED", "OCR", "${durationDetails(startedNs, endedNs)}; text_length=$textLength; ocr_blocks=$blockCount; duplicate_skipped=false")
    }

    fun recordDuplicateTotal(source: String, textLength: Int) {
        val startedNs = sourceStart(source)
        if (startedNs <= 0L) {
            record("READ_TO_DUPLICATE_SKIP", source, "duration_us=-1; duration_ms=-1; text_length=$textLength; duplicate_skipped=true; start_missing=true")
            return
        }
        val endedNs = SystemClock.elapsedRealtimeNanos()
        record("READ_TO_DUPLICATE_SKIP", source, "${durationDetails(startedNs, endedNs)}; text_length=$textLength; duplicate_skipped=true; start_missing=false")
    }
}
'''

def fail(msg: str): raise SystemExit(msg)
def one(text: str, old: str, new: str, label: str) -> str:
    c=text.count(old)
    if c != 1: fail(f'{label}: esperado exatamente 1 trecho, encontrado {c}')
    return text.replace(old,new,1)
def sha(path: Path) -> str: return hashlib.sha256(path.read_bytes()).hexdigest()

def snapshot(root: Path) -> dict[str,str]:
    out={}
    for rel in PROTECTED_NEVER_WRITE:
        p=root/rel
        if not p.is_file(): fail(f'arquivo protegido ausente: {rel}')
        out[str(rel)]=sha(p)
    return out

def instrument(before: str) -> str:
    if MARKER in before or 'FarolLatencyProbeStage9' in before: fail('instrumentação Stage9 já presente')
    after=before
    after=one(after,
        '        val immediateTextChecklist13 = collectImmediateVisibleTextChecklist13(rootHandle0187.node)\n',
        '        val immediateTextChecklist13 = FarolLatencyProbeStage9.measureText(\n            stage = "ACCESSIBILITY_IMMEDIATE_TEXT",\n            source = "Accessibility",\n        ) {\n            collectImmediateVisibleTextChecklist13(rootHandle0187.node)\n        }\n',
        'immediate accessibility read')
    after=one(after,
        '                                    ocrService.extractStructuredText(bitmap0161)\n',
        '                                    run {\n                                        val farolLatencyOcrStartedNsStage9 = android.os.SystemClock.elapsedRealtimeNanos()\n                                        val farolLatencyOcrResultStage9 = ocrService.extractStructuredText(bitmap0161)\n                                        FarolLatencyProbeStage9.recordOcrStructured(\n                                            startedNs = farolLatencyOcrStartedNsStage9,\n                                            textLength = farolLatencyOcrResultStage9.text.length,\n                                            blockCount = farolLatencyOcrResultStage9.blocks.size,\n                                        )\n                                        farolLatencyOcrResultStage9\n                                    }\n',
        'OCR structured extract')
    after=one(after,
        '            TextSource.Accessibility -> collectAccessibilityCardBlocks0188(\n                expectedPackage0188 = packageName0188,\n                expectedWindowId0188 = expectedWindow0188,\n            )\n',
        '            TextSource.Accessibility -> FarolLatencyProbeStage9.measureBlocks(\n                stage = "ACCESSIBILITY_CARD_BLOCKS",\n                source = "Accessibility",\n            ) {\n                collectAccessibilityCardBlocks0188(\n                    expectedPackage0188 = packageName0188,\n                    expectedWindowId0188 = expectedWindow0188,\n                )\n            }\n',
        'accessibility card blocks')
    after=one(after,
        '            TextSource.Ocr -> collectOcrCardBlocks0188(\n                packageName0188 = packageName0188,\n                windowId0188 = expectedWindow0188,\n                ocrBlocks0188 = ocrBlocks0188,\n            )\n',
        '            TextSource.Ocr -> FarolLatencyProbeStage9.measureBlocks(\n                stage = "OCR_CARD_GROUPS",\n                source = "OCR",\n            ) {\n                collectOcrCardBlocks0188(\n                    packageName0188 = packageName0188,\n                    windowId0188 = expectedWindow0188,\n                    ocrBlocks0188 = ocrBlocks0188,\n                )\n            }\n',
        'OCR card groups')
    after=one(after,
        '''        val decision0188 = FarolRealDeviceGate0188.evaluate(\n            selectedPackageName = packageName0188,\n            selectedPackages = savedPackages0188,\n            blocks = blocks0188,\n        )\n''',
        '''        val decision0188 = FarolLatencyProbeStage9.measureValue(\n            stage = "REAL_DEVICE_GATE",\n            source = source0188.name,\n        ) {\n            FarolRealDeviceGate0188.evaluate(\n                selectedPackageName = packageName0188,\n                selectedPackages = savedPackages0188,\n                blocks = blocks0188,\n            )\n        }\n''',
        'real device gate')
    auth_pos=after.find('        val routeAuthorization0188 = authorizeRoute0188(')
    dup_pos=after.find('"BUBBLE_DUPLICATE_SKIPPED"', auth_pos)
    if auth_pos < 0 or dup_pos < 0: fail('authorization/duplicate anchors missing')
    needle='isReadBindingFresh0187(readBinding0187)'
    positions=[]; cur=auth_pos
    while True:
        p=after.find(needle,cur,dup_pos)
        if p<0: break
        positions.append(p); cur=p+len(needle)
    if len(positions)!=1: fail(f'post-auth freshness: esperado 1, encontrado {len(positions)}')
    p=positions[0]
    repl='FarolLatencyProbeStage9.measureValue(\n                stage = "POST_AUTH_READ_BINDING_FRESH",\n                source = source.name,\n            ) {\n                isReadBindingFresh0187(readBinding0187)\n            }'
    after=after[:p]+repl+after[p+len(needle):]
    dup_block='''            UnifiedDebugEventStore.record(\n                "BUBBLE_DUPLICATE_SKIPPED",\n'''
    after=one(after, dup_block,
        '''            FarolLatencyProbeStage9.recordDuplicateTotal(\n                source = source.name,\n                textLength = snapshotTextChecklist13.length,\n            )\n            UnifiedDebugEventStore.record(\n                "BUBBLE_DUPLICATE_SKIPPED",\n''',
        'duplicate total')
    return after

def audit(before: str, after: str, root: Path):
    for token in ('collectImmediateVisibleTextChecklist13(rootHandle0187.node)', 'ocrService.extractStructuredText(bitmap0161)',
                  'collectAccessibilityCardBlocks0188(', 'collectOcrCardBlocks0188(', 'FarolRealDeviceGate0188.evaluate(',
                  'authorizeRoute0188(', '"BUBBLE_DUPLICATE_SKIPPED"'):
        if before.count(token) != after.count(token): fail(f'neutralidade violada para {token}: {before.count(token)} -> {after.count(token)}')
    if len(re.findall(r'\breturn\b', before)) != len(re.findall(r'\breturn\b', after)):
        fail('quantidade de tokens return mudou')
    forbidden_new = ('takeScreenshot(', 'requestScreenshotAnalysis(', 'parser.parse', 'GoogleMapsService(', 'delay(', 'Timer(', 'scheduleAtFixedRate', 'while (true)')
    for token in forbidden_new:
        if after.count(token) != before.count(token): fail(f'novo comportamento proibido detectado: {token}')
    gate=(root/'app/src/main/java/br/com/mapeiaia/rotacerta/FarolRealDeviceGate0188.kt').read_text()
    for exact in ('val authorityIdentity = "$selected|${winner.block.windowId}|${winner.block.id}|$signature"',
                  'screenHash = authorityIdentity.hashCode()'):
        if exact not in gate: fail(f'autoridade esperada ausente: {exact}')
    for bad in ('UnifiedDebugEventStore', 'FarolFlightRecorder0163', 'SharedPreferences', 'DataStore', 'java.io.', 'File(', 'writeText(', 'appendText(', 'android.util.Log', 'Timer(', 'delay('):
        if bad in HELPER_SOURCE: fail(f'helper usa sink/I-O proibido: {bad}')
    for required in ('MAX_EVENTS = 512', 'ArrayDeque<String>', 'SystemClock.elapsedRealtimeNanos()', 'synchronized(lock)'):
        if required not in HELPER_SOURCE: fail(f'helper seguro incompleto: {required}')
    for event in ('ACCESSIBILITY_IMMEDIATE_TEXT','ACCESSIBILITY_CARD_BLOCKS','OCR_CARD_GROUPS','REAL_DEVICE_GATE','POST_AUTH_READ_BINDING_FRESH','OCR_EXTRACT_STRUCTURED','READ_TO_DUPLICATE_SKIP'):
        if event not in after and event not in HELPER_SOURCE: fail(f'evento ausente: {event}')

def validate_source(root: Path):
    b=(root/BUILD).read_text()
    if 'versionCode = 5478' not in b or 'versionName = "0.1.194"' not in b: fail('fonte não é 0.1.194/5478')
    s=root/SERVICE
    if not s.is_file(): fail('LiveRideAccessibilityService ausente')
    before=s.read_text()
    protected=snapshot(root)
    after=instrument(before)
    audit(before,after,root)
    return before,after,protected

def self_test():
    if 'UnifiedDebugEventStore' in HELPER_SOURCE or 'FarolFlightRecorder0163' in HELPER_SOURCE: fail('self-test sink unsafe')
    if 'MAX_EVENTS = 512' not in HELPER_SOURCE or 'ArrayDeque<String>' not in HELPER_SOURCE: fail('self-test bound missing')
    print('farol_latency_stage9_self_test=passed')

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('source_root', nargs='?', type=Path)
    ap.add_argument('--check', action='store_true')
    ap.add_argument('--self-test', action='store_true')
    args=ap.parse_args()
    if args.self_test:
        self_test()
        if args.source_root is None: return
    if args.source_root is None: fail('source_root obrigatório fora de --self-test isolado')
    root=args.source_root.resolve()
    before,after,protected=validate_source(root)
    if args.check:
        print('farol_latency_stage9_check=passed')
        print('sink=bounded_memory_only')
        return
    service=root/SERVICE; helper=root/HELPER
    service.write_text(after)
    helper.write_text(HELPER_SOURCE)
    now=snapshot(root)
    if now != protected: fail('arquivo protegido mudou durante aplicação')
    audit(before,service.read_text(),root)
    print('farol_latency_stage9_apply=passed')
    print('sink=bounded_memory_only')

if __name__ == '__main__': main()
