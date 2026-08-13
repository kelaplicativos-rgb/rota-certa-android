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

# OCR demand entry.
s = once(
    s,
    '    ) {\n        if (!serviceReady || !WorkModePolicy0162.isEnabled(currentSettings)) return\n        if (bubbleGestureActive) return // bubble_drag_screenshot_pause_0_1_116\n',
    '    ) {\n'
    '        FarolMaximumForensicsStage38.record(\n'
    '            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_OCR_DEMAND_ENTER", eventPackageStage19, cycleId = cycleIdStage20,\n'
    '            details = "rerun=${rerunDemandStage23 != null}; screenshotBusy=${screenshotInProgress.get()}; workMode=${WorkModePolicy0162.isEnabled(currentSettings)}; serviceReady=$serviceReady; bubbleGesture=$bubbleGestureActive",\n'
    '        )\n'
    '        if (!serviceReady || !WorkModePolicy0162.isEnabled(currentSettings)) return\n'
    '        if (bubbleGestureActive) return // bubble_drag_screenshot_pause_0_1_116\n',
    'OCR demand entry Stage38',
)

s = once(
    s,
    '        val serialStage19 = ++stage19OcrSerial\n        val tokenStage23 = requestStage23.token\n',
    '        val serialStage19 = ++stage19OcrSerial\n'
    '        val tokenStage23 = requestStage23.token\n'
    '        FarolMaximumForensicsStage38.record(\n'
    '            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_OCR_REQUEST_ACCEPTED", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",\n'
    '            details = "s23token=$tokenStage23; visualGeneration=${demandStage23.visualGeneration}; snapshotHash=${demandStage23.snapshotHash}; reason=${requestStage23.reason}",\n'
    '        )\n',
    'OCR request accepted Stage38',
)

s = once(
    s,
    '            FarolVisualIdentityStage23.Metrics.increment("ocrReruns")\n        }\n\n        runCatching {\n            takeScreenshot(\n',
    '            FarolVisualIdentityStage23.Metrics.increment("ocrReruns")\n        }\n\n        runCatching {\n'
    '            FarolMaximumForensicsStage38.record(\n'
    '                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_SCREENSHOT_REQUEST", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",\n'
    '                details = "display=${Display.DEFAULT_DISPLAY}; visualGeneration=${demandStage23.visualGeneration}; snapshotHash=${demandStage23.snapshotHash}",\n'
    '            )\n'
    '            takeScreenshot(\n',
    'screenshot request Stage38',
)

s = once(
    s,
    '                    override fun onSuccess(screenshot: ScreenshotResult) {\n                        FarolForensicCardBlackBoxStage32.recordScreenshot(SystemClock.elapsedRealtimeNanos(), "CALLBACK")\n',
    '                    override fun onSuccess(screenshot: ScreenshotResult) {\n'
    '                        FarolMaximumForensicsStage38.record(\n'
    '                            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_SCREENSHOT_CALLBACK", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",\n'
    '                            details = "status=success_callback; visualGeneration=${demandStage23.visualGeneration}; snapshotHash=${demandStage23.snapshotHash}",\n'
    '                        )\n'
    '                        FarolForensicCardBlackBoxStage32.recordScreenshot(SystemClock.elapsedRealtimeNanos(), "CALLBACK")\n',
    'screenshot callback Stage38',
)

s = once(
    s,
    '                                val screenshotHashStage32 = FarolPrintStoreStage32.sampleHash(bitmapStage19!!)\n',
    '                                val screenshotHashStage32 = FarolPrintStoreStage32.sampleHash(bitmapStage19!!)\n'
    '                                FarolMaximumForensicsStage38.record(\n'
    '                                    SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_SCREENSHOT_BITMAP_READY", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",\n'
    '                                    details = "hash=$screenshotHashStage32; width=${bitmapStage19!!.width}; height=${bitmapStage19!!.height}; config=${bitmapStage19!!.config}",\n'
    '                                )\n',
    'bitmap Stage38',
)


service.write_text(s)
print('stage38_ocr_request=PASS')
