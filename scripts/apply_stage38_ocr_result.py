#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
PKG = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta'
PATCH_ROOT = Path(__file__).resolve().parents[1]

def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, got {count}')
    return text.replace(old, new, 1)

def insert_before(text: str, anchor: str, addition: str, label: str) -> str:
    count = text.count(anchor)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 anchor, got {count}')
    return text.replace(anchor, addition + anchor, 1)

service = PKG / 'LiveRideAccessibilityService.kt'
s = service.read_text()

s = once(
    s,
    '                                val ocrStartedNsStage20 = SystemClock.elapsedRealtimeNanos()\n                                val structuredStage19 = withContext(Dispatchers.Default) {\n',
    '                                val ocrStartedNsStage20 = SystemClock.elapsedRealtimeNanos()\n'
    '                                FarolMaximumForensicsStage38.record(\n'
    '                                    ocrStartedNsStage20, System.currentTimeMillis(), "S38_OCR_EXTRACT_START", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",\n'
    '                                    details = "screenshotHash=$screenshotHashStage32",\n'
    '                                )\n'
    '                                val structuredStage19 = withContext(Dispatchers.Default) {\n',
    'OCR extract start Stage38',
)

s = once(
    s,
    '                                val extractEndedNsStage20 = SystemClock.elapsedRealtimeNanos()\n                                FarolForensicTraceStage20.ocrStage(\n',
    '                                val extractEndedNsStage20 = SystemClock.elapsedRealtimeNanos()\n'
    '                                FarolMaximumForensicsStage38.record(\n'
    '                                    extractEndedNsStage20, System.currentTimeMillis(), "S38_OCR_EXTRACT_END", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",\n'
    '                                    details = "duration_ns=${(extractEndedNsStage20 - ocrStartedNsStage20).coerceAtLeast(0L)}; blocks=${structuredStage19.blocks.size}; text_len=${structuredStage19.text.length}; text_hash=${structuredStage19.text.hashCode()}; fullText=${structuredStage19.text.take(1300)}",\n'
    '                                )\n'
    '                                structuredStage19.blocks.forEachIndexed { index38, block38 ->\n'
    '                                    FarolMaximumForensicsStage38.record(\n'
    '                                        SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_OCR_BLOCK", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",\n'
    '                                        details = "index=$index38; id=${block38.id}; bounds=${block38.left},${block38.top},${block38.right},${block38.bottom}; text=${block38.text.take(1300)}",\n'
    '                                    )\n'
    '                                }\n'
    '                                FarolForensicTraceStage20.ocrStage(\n',
    'OCR extract end and blocks Stage38',
)

s = once(
    s,
    '                                val evaluationStage19 = withContext(Dispatchers.Default) {\n                                    FarolUniversalVisualPipelineStage19.evaluate(blocksStage19)\n                                }\n',
    '                                blocksStage19.forEachIndexed { index38, block38 ->\n'
    '                                    FarolMaximumForensicsStage38.record(\n'
    '                                        SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_OCR_CLUSTER", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",\n'
    '                                        details = "index=$index38; id=${block38.id}; window=${block38.windowId}; bounds=${block38.left},${block38.top},${block38.right},${block38.bottom}; text=${block38.text.take(1300)}",\n'
    '                                    )\n'
    '                                }\n'
    '                                val evaluationStage19 = withContext(Dispatchers.Default) {\n'
    '                                    FarolUniversalVisualPipelineStage19.evaluate(blocksStage19)\n'
    '                                }\n'
    '                                FarolMaximumForensicsStage38.record(\n'
    '                                    SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_OCR_EVALUATION_RESULT", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",\n'
    '                                    details = "candidate=${evaluationStage19 != null}; pickup=${evaluationStage19?.pickup.orEmpty()}; destination=${evaluationStage19?.destination.orEmpty()}; signature=${evaluationStage19?.addressSignature.orEmpty()}",\n'
    '                                )\n'
    '                                if (evaluationStage19 == null) {\n'
    '                                    FarolCausalCorrectionStage21.forensicExplainEvaluationStage38(blocksStage19).take(420).forEachIndexed { index38, step38 ->\n'
    '                                        FarolMaximumForensicsStage38.record(\n'
    '                                            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_OCR_EVALUATION_RULE", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",\n'
    '                                            details = "step=$index38; $step38",\n'
    '                                        )\n'
    '                                    }\n'
    '                                }\n',
    'OCR evaluation Stage38',
)

s = once(
    s,
    '                    override fun onFailure(errorCode: Int) {\n                        FarolForensicTraceStage20.ocrStage(\n',
    '                    override fun onFailure(errorCode: Int) {\n'
    '                        FarolMaximumForensicsStage38.record(\n'
    '                            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_SCREENSHOT_FAILURE", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",\n'
    '                            details = "errorCode=$errorCode; intervalShort=${errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT}; visualGeneration=${demandStage23.visualGeneration}; snapshotHash=${demandStage23.snapshotHash}",\n'
    '                        )\n'
    '                        FarolForensicTraceStage20.ocrStage(\n',
    'screenshot failure Stage38',
)



service.write_text(s)
print('stage38_ocr_result=PASS')
