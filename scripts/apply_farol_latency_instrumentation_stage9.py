#!/usr/bin/env python3
"""ETAPA 9: instrumentação monotônica e observacional do caminho crítico do FAROL 0.1.194.

Este transformador deve ser executado SOMENTE depois de materializar byte a byte a 0.1.194.
Ele não altera versão, autoridade, gate, parser, decisão, rota, binding, generation ou duplicate skip.
"""
from __future__ import annotations

import argparse
from pathlib import Path

SERVICE = Path("app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
HELPER = Path("app/src/main/java/br/com/mapeiaia/rotacerta/FarolLatencyProbeStage9.kt")
MARKER = "FAROL_LATENCY_STAGE9"

PROTECTED_NEVER_WRITE = (
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/br/com/mapeiaia/rotacerta/DecisionEngine.kt",
    "app/src/main/java/br/com/mapeiaia/rotacerta/RideTextParser.kt",
    "app/src/main/java/br/com/mapeiaia/rotacerta/UniversalScreenAddressParser.kt",
    "app/src/main/java/br/com/mapeiaia/rotacerta/FarolRealDeviceGate0188.kt",
    "app/src/main/java/br/com/mapeiaia/rotacerta/FarolVisualPriority0189.kt",
    "app/src/main/java/br/com/mapeiaia/rotacerta/FailedCardRecovery0161.kt",
    "app/src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt",
    "app/src/main/java/br/com/mapeiaia/rotacerta/GpsAddressResolver.kt",
    "app/src/main/java/br/com/mapeiaia/rotacerta/RadarImport.kt",
    "app/src/main/java/br/com/mapeiaia/rotacerta/ForensicIncidentMonitor0193.kt",
    "app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt",
)

HELPER_SOURCE = r'''package br.com.mapeiaia.rotacerta

import android.os.SystemClock

/**
 * Telemetria temporária da ETAPA 9.
 * Somente relógio monotônico, contadores O(1) e UnifiedDebugEventStore já existente.
 * Nenhum valor retornado por este objeto pode participar de decisão funcional.
 */
internal object FarolLatencyProbeStage9 {
    const val MARKER = "FAROL_LATENCY_STAGE9"

    @Volatile
    private var accessibilityReadStartNs: Long = 0L

    @Volatile
    private var ocrReadStartNs: Long = 0L

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
        val durationNs = (endedNs - startedNs).coerceAtLeast(0L)
        val durationUs = durationNs / 1_000L
        val durationMs = durationNs / 1_000_000L
        return "duration_us=$durationUs; duration_ms=$durationMs"
    }

    private fun record(stage: String, source: String, details: String) {
        UnifiedDebugEventStore.record(
            "${MARKER}_$stage",
            source,
            details,
        )
    }

    fun measureText(
        stage: String,
        source: String,
        block: () -> String,
    ): String {
        val startedNs = SystemClock.elapsedRealtimeNanos()
        rememberSourceStart(source, startedNs)
        val result = block()
        val endedNs = SystemClock.elapsedRealtimeNanos()
        record(
            stage,
            source,
            "${durationDetails(startedNs, endedNs)}; text_length=${result.length}; duplicate_skipped=false",
        )
        return result
    }

    fun <T> measureBlocks(
        stage: String,
        source: String,
        block: () -> List<T>,
    ): List<T> {
        val startedNs = SystemClock.elapsedRealtimeNanos()
        val result = block()
        val endedNs = SystemClock.elapsedRealtimeNanos()
        record(
            stage,
            source,
            "${durationDetails(startedNs, endedNs)}; blocks=${result.size}; duplicate_skipped=false",
        )
        return result
    }

    fun <T> measureValue(
        stage: String,
        source: String,
        block: () -> T,
    ): T {
        val startedNs = SystemClock.elapsedRealtimeNanos()
        val result = block()
        val endedNs = SystemClock.elapsedRealtimeNanos()
        record(
            stage,
            source,
            "${durationDetails(startedNs, endedNs)}; duplicate_skipped=false",
        )
        return result
    }

    fun recordOcrStructured(
        startedNs: Long,
        textLength: Int,
        blockCount: Int,
    ) {
        val endedNs = SystemClock.elapsedRealtimeNanos()
        rememberSourceStart("OCR", startedNs)
        record(
            "OCR_EXTRACT_STRUCTURED",
            "OCR",
            "${durationDetails(startedNs, endedNs)}; text_length=$textLength; ocr_blocks=$blockCount; duplicate_skipped=false",
        )
    }

    fun recordDuplicateTotal(
        source: String,
        textLength: Int,
    ) {
        val startedNs = sourceStart(source)
        if (startedNs <= 0L) {
            record(
                "READ_TO_DUPLICATE_SKIP",
                source,
                "duration_us=-1; duration_ms=-1; text_length=$textLength; duplicate_skipped=true; start_missing=true",
            )
            return
        }
        val endedNs = SystemClock.elapsedRealtimeNanos()
        record(
            "READ_TO_DUPLICATE_SKIP",
            source,
            "${durationDetails(startedNs, endedNs)}; text_length=$textLength; duplicate_skipped=true; start_missing=false",
        )
    }
}
'''


def fail(message: str) -> None:
    raise SystemExit(message)


def find_matching(text: str, open_index: int, open_char: str, close_char: str) -> int:
    if open_index >= len(text) or text[open_index] != open_char:
        fail(f"find_matching: índice {open_index} não aponta para {open_char!r}")
    depth = 0
    in_string = False
    escaped = False
    i = open_index
    while i < len(text):
        ch = text[i]
        if in_string:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                in_string = False
            i += 1
            continue
        if ch == '"':
            in_string = True
            i += 1
            continue
        if ch == open_char:
            depth += 1
        elif ch == close_char:
            depth -= 1
            if depth == 0:
                return i
        i += 1
    fail(f"delimitador {open_char}{close_char} não fechado")


def function_bounds(text: str, marker: str) -> tuple[int, int]:
    count = text.count(marker)
    if count != 1:
        fail(f"{marker}: esperado exatamente 1 marcador de função, encontrado {count}")
    start = text.index(marker)
    brace = text.find("{", start)
    if brace < 0:
        fail(f"{marker}: abertura de função ausente")
    end = find_matching(text, brace, "{", "}") + 1
    return start, end


def call_spans(region: str, needle: str) -> list[tuple[int, int]]:
    spans: list[tuple[int, int]] = []
    cursor = 0
    while True:
        idx = region.find(needle, cursor)
        if idx < 0:
            break
        prefix = region[max(0, idx - 24):idx]
        if "fun " not in prefix:
            open_paren = region.find("(", idx + len(needle))
            if open_paren < 0:
                fail(f"{needle}: chamada sem parêntese")
            end = find_matching(region, open_paren, "(", ")") + 1
            spans.append((idx, end))
            cursor = end
        else:
            cursor = idx + len(needle)
    return spans


def wrap_single_call(
    text: str,
    function_marker: str,
    call_needle: str,
    wrapper_prefix: str,
    wrapper_suffix: str = "\n        }",
) -> str:
    start, end = function_bounds(text, function_marker)
    region = text[start:end]
    spans = call_spans(region, call_needle)
    if len(spans) != 1:
        fail(f"{call_needle} em {function_marker}: esperado 1 call-site, encontrado {len(spans)}")
    rel_start, rel_end = spans[0]
    absolute_start = start + rel_start
    absolute_end = start + rel_end
    original = text[absolute_start:absolute_end]
    wrapped = wrapper_prefix + original + wrapper_suffix
    return text[:absolute_start] + wrapped + text[absolute_end:]


def wrap_single_global_call(text: str, call_needle: str, wrapper_prefix: str, wrapper_suffix: str) -> str:
    spans = call_spans(text, call_needle)
    if len(spans) != 1:
        fail(f"{call_needle}: esperado 1 call-site global, encontrado {len(spans)}")
    start, end = spans[0]
    return text[:start] + wrapper_prefix + text[start:end] + wrapper_suffix + text[end:]


def insert_duplicate_total(text: str) -> str:
    start, end = function_bounds(text, "private suspend fun processRideText(")
    region = text[start:end]
    marker = '"BUBBLE_DUPLICATE_SKIPPED"'
    if region.count(marker) != 1:
        fail(f"duplicate skip: esperado 1 marcador, encontrado {region.count(marker)}")
    marker_pos = start + region.index(marker)
    line_start = text.rfind("\n", start, marker_pos) + 1
    indent = text[line_start:marker_pos]
    indent = indent[: len(indent) - len(indent.lstrip())]
    addition = (
        f'{indent}FarolLatencyProbeStage9.recordDuplicateTotal(\n'
        f'{indent}    source = source.name,\n'
        f'{indent}    textLength = snapshotTextChecklist13.length,\n'
        f'{indent})\n'
    )
    return text[:line_start] + addition + text[line_start:]


def wrap_post_authorization_freshness(text: str) -> str:
    start, end = function_bounds(text, "private suspend fun processRideText(")
    region = text[start:end]
    auth_spans = call_spans(region, "authorizeRoute0188")
    if len(auth_spans) != 1:
        fail(f"authorizeRoute0188 em processRideText: esperado 1 call-site, encontrado {len(auth_spans)}")
    duplicate_pos = region.find('"BUBBLE_DUPLICATE_SKIPPED"')
    if duplicate_pos < 0:
        fail("BUBBLE_DUPLICATE_SKIPPED ausente em processRideText")
    candidates = call_spans(region, "isReadBindingFresh0187")
    candidates = [span for span in candidates if auth_spans[0][1] < span[0] < duplicate_pos]
    if len(candidates) != 1:
        fail(f"isReadBindingFresh0187 pós-autorização: esperado 1 call-site, encontrado {len(candidates)}")
    rel_start, rel_end = candidates[0]
    absolute_start = start + rel_start
    absolute_end = start + rel_end
    original = text[absolute_start:absolute_end]
    wrapped = (
        'FarolLatencyProbeStage9.measureValue(\n'
        '            stage = "POST_AUTH_READ_BINDING_FRESH",\n'
        '            source = source.name,\n'
        '        ) {\n'
        f'            {original}\n'
        '        }'
    )
    return text[:absolute_start] + wrapped + text[absolute_end:]


def instrument_ocr_extract(text: str) -> str:
    spans = call_spans(text, "ocrService.extractStructuredText")
    if len(spans) != 1:
        fail(f"ocrService.extractStructuredText: esperado 1 call-site, encontrado {len(spans)}")
    start, end = spans[0]
    original = text[start:end]
    # Mantém a chamada exatamente uma vez. A medição usa apenas relógio monotônico e os campos já produzidos.
    replacement = (
        'run {\n'
        '                                        val farolLatencyOcrStartedNsStage9 = android.os.SystemClock.elapsedRealtimeNanos()\n'
        f'                                        val farolLatencyOcrResultStage9 = {original}\n'
        '                                        FarolLatencyProbeStage9.recordOcrStructured(\n'
        '                                            startedNs = farolLatencyOcrStartedNsStage9,\n'
        '                                            textLength = farolLatencyOcrResultStage9.text.length,\n'
        '                                            blockCount = farolLatencyOcrResultStage9.blocks.size,\n'
        '                                        )\n'
        '                                        farolLatencyOcrResultStage9\n'
        '                                    }'
    )
    return text[:start] + replacement + text[end:]


def instrument_service(before: str) -> str:
    required = (
        "collectImmediateVisibleTextChecklist13",
        "private fun authorizeRoute0188(",
        "collectAccessibilityCardBlocks0188",
        "collectOcrCardBlocks0188",
        "FarolRealDeviceGate0188.evaluate",
        "isReadBindingFresh0187",
        '"BUBBLE_DUPLICATE_SKIPPED"',
        "ocrService.extractStructuredText",
        "authorityIdentity",
        "screenHash",
    )
    missing = [item for item in required if item not in before]
    if missing:
        fail("fonte 0.1.194 materializada incompatível; faltam: " + ", ".join(missing))
    if MARKER in before or "FarolLatencyProbeStage9" in before:
        fail("instrumentação Stage9 já existe na fonte")

    after = before
    after = wrap_single_global_call(
        after,
        "collectImmediateVisibleTextChecklist13",
        'FarolLatencyProbeStage9.measureText(\n            stage = "ACCESSIBILITY_IMMEDIATE_TEXT",\n            source = "Accessibility",\n        ) {\n            ',
        '\n        }',
    )
    after = instrument_ocr_extract(after)
    after = wrap_single_call(
        after,
        "private fun authorizeRoute0188(",
        "collectAccessibilityCardBlocks0188",
        'FarolLatencyProbeStage9.measureBlocks(\n            stage = "ACCESSIBILITY_CARD_BLOCKS",\n            source = "Accessibility",\n        ) {\n            ',
    )
    after = wrap_single_call(
        after,
        "private fun authorizeRoute0188(",
        "collectOcrCardBlocks0188",
        'FarolLatencyProbeStage9.measureBlocks(\n            stage = "OCR_CARD_GROUPS",\n            source = "OCR",\n        ) {\n            ',
    )
    after = wrap_single_call(
        after,
        "private fun authorizeRoute0188(",
        "FarolRealDeviceGate0188.evaluate",
        'FarolLatencyProbeStage9.measureValue(\n            stage = "REAL_DEVICE_GATE",\n            source = source0188.name,\n        ) {\n            ',
    )
    after = wrap_post_authorization_freshness(after)
    after = insert_duplicate_total(after)
    return after


def functional_fingerprint(text: str) -> dict[str, int]:
    return {
        "authorityIdentity": text.count("authorityIdentity"),
        "screenHash": text.count("screenHash"),
        "gateCalls": len(call_spans(text, "FarolRealDeviceGate0188.evaluate")),
        "duplicateMarkers": text.count('"BUBBLE_DUPLICATE_SKIPPED"'),
        "authorizeCalls": len(call_spans(text, "authorizeRoute0188")),
        "returnTokens": text.count("return"),
    }


def audit(before: str, after: str) -> None:
    before_fp = functional_fingerprint(before)
    after_fp = functional_fingerprint(after)
    if before_fp != after_fp:
        fail(f"neutralidade estrutural falhou: before={before_fp} after={after_fp}")
    if after.count("android.os.SystemClock.elapsedRealtimeNanos()") < 1:
        fail("relógio monotônico não foi inserido")
    forbidden_runtime = (
        "System.currentTimeMillis()",
        "takeScreenshot(",
        "requestScreenshotAnalysis(",
        "UniversalScreenAddressParser.findAddresses",
        "WrappedAddressTextNormalizer.normalize",
        "googleMapsService.",
        "DecisionEngine(",
        "Regex(",
        "Timer(",
        "scheduleAtFixedRate",
    )
    helper_lower = HELPER_SOURCE
    present = [item for item in forbidden_runtime if item in helper_lower]
    if present:
        fail("helper Stage9 contém trabalho proibido: " + ", ".join(present))
    for required in (
        "ACCESSIBILITY_IMMEDIATE_TEXT",
        "ACCESSIBILITY_CARD_BLOCKS",
        "OCR_CARD_GROUPS",
        "REAL_DEVICE_GATE",
        "POST_AUTH_READ_BINDING_FRESH",
        "recordOcrStructured",
        "recordDuplicateTotal",
    ):
        if required not in after:
            fail(f"ponto obrigatório não materializado: {required}")


def self_test() -> None:
    fixture = r'''class LiveRideAccessibilityService : AccessibilityService() {
    private fun outer() {
        val immediate = collectImmediateVisibleTextChecklist13(root)
        val structured = ocrService.extractStructuredText(bitmap)
    }
    private fun authorizeRoute0188(source0188: TextSource): FarolRouteAuthorization0188? {
        val accessibility = collectAccessibilityCardBlocks0188("pkg", 1)
        val ocr = collectOcrCardBlocks0188("pkg", 1, emptyList())
        return FarolRealDeviceGate0188.evaluate(accessibility + ocr)
    }
    private fun collectAccessibilityCardBlocks0188(a: String, b: Int) = emptyList<Any>()
    private fun collectOcrCardBlocks0188(a: String, b: Int, c: List<Any>) = emptyList<Any>()
    private suspend fun processRideText(text: String, source: TextSource) {
        val snapshotTextChecklist13 = text
        val routeAuthorization0188 = authorizeRoute0188(source)
        if (!isReadBindingFresh0187(routeAuthorization0188)) return
        val authorityIdentity = "pkg|window|block|signature"
        val screenHash = authorityIdentity.hashCode()
        if (screenHash == 1) {
            UnifiedDebugEventStore.record("BUBBLE_DUPLICATE_SKIPPED", "pkg", "same")
            return
        }
    }
}
'''
    transformed = instrument_service(fixture)
    audit(fixture, transformed)
    if transformed.count("collectImmediateVisibleTextChecklist13(root)") != 1:
        fail("self-test duplicou coleta Accessibility")
    if transformed.count("ocrService.extractStructuredText(bitmap)") != 1:
        fail("self-test duplicou OCR")
    if transformed.count("FarolRealDeviceGate0188.evaluate(accessibility + ocr)") != 1:
        fail("self-test duplicou gate")
    print("farol_latency_stage9_self_test=passed")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source_root", nargs="?", type=Path)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--check", action="store_true", help="audita pré-condições e transformação em memória sem escrever")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    if args.source_root is None:
        fail("uso: apply_farol_latency_instrumentation_stage9.py <source-root> [--check]")

    root = args.source_root.resolve()
    service = root / SERVICE
    helper = root / HELPER
    gradle = root / "app/build.gradle.kts"
    if not service.is_file() or not gradle.is_file():
        fail("fonte materializada ausente")
    gradle_text = gradle.read_text(encoding="utf-8")
    if 'versionName = "0.1.194"' not in gradle_text or "versionCode = 5478" not in gradle_text:
        fail("a instrumentação Stage9 exige a 0.1.194 materializada exatamente antes do diagnóstico")
    if helper.exists():
        fail(f"helper Stage9 já existe: {helper}")

    before = service.read_text(encoding="utf-8")
    after = instrument_service(before)
    audit(before, after)

    if args.check:
        print("farol_latency_stage9_check=passed")
        return

    # Únicas escritas permitidas: observabilidade no serviço e helper diagnóstico temporário.
    service.write_text(after, encoding="utf-8")
    helper.write_text(HELPER_SOURCE, encoding="utf-8")
    print("farol_latency_stage9_apply=passed")
    print(f"service={SERVICE}")
    print(f"helper={HELPER}")
    for path in PROTECTED_NEVER_WRITE:
        print(f"protected_unchanged_by_transformer={path}")


if __name__ == "__main__":
    main()
