from base64 import b64decode
from pathlib import Path
from zlib import decompress

parts = sorted(Path(__file__).parent.glob("farol_flight_recorder_0163.payload.[0-9][0-9]"))
if not parts:
    raise SystemExit("payload do gravador de voo nao encontrado")
source = decompress(b64decode("".join(part.read_text(encoding="utf-8").strip() for part in parts))).decode("utf-8")
old = '''def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: esperado 1 trecho, encontrado {count}")
    return text.replace(old, new, 1)
'''
new = '''def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count == 1:
        return text.replace(old, new, 1)
    if count == 0 and label == "OCR fallback scheduled":
        start = text.find("private fun scheduleScreenshotFallback127")
        cancel = text.find("screenshotFallbackJob127?.cancel()", start)
        if start >= 0 and cancel >= 0:
            pos = text.find("\\n", cancel) + 1
            insertion = """        FarolFlightRecorder0163.record(
            stage = \"OCR_FALLBACK_SCHEDULED\",
            packageName = expectedPackage,
            details = \"delay_ms=${FarolCriticalPathPolicy.OCR_FALLBACK_DELAY_MILLIS}; generation=$universalScreenGeneration; lastAccessibilityAccepted=$lastAccessibilityAcceptedAtMillis127\",
        )
"""
            return text[:pos] + insertion + text[pos:]
    if count == 0 and label == "OCR fallback wake":
        start = text.find("private fun scheduleScreenshotFallback127")
        end = text.find("private fun", start + len("private fun"))
        if end < 0:
            end = len(text)
        marker = text.find("if (!serviceReady || !currentSettings.appEnabled || !currentSettings.liveReadingEnabled) return@launch", start, end)
        if marker >= 0:
            insertion = """FarolFlightRecorder0163.record(
                stage = \"OCR_FALLBACK_WAKE\",
                packageName = expectedPackage,
                details = \"ready=$serviceReady; appEnabled=${currentSettings.appEnabled}; live=${currentSettings.liveReadingEnabled}; resolved=${universalResolvedForegroundPackage()}; accessibilityWon=${lastAccessibilityAcceptedAtMillis127 >= scheduledAt127}\",
            )
            """
            return text[:marker] + insertion + text[marker:]
        return text
    if count == 0 and label == "OCR request evaluate":
        start = text.find("private fun requestScreenshotAnalysis")
        if start >= 0:
            pos = text.find("\\n", start) + 1
            insertion = """        val ocrAttemptStartedElapsedNanos0163 = android.os.SystemClock.elapsedRealtimeNanos()
        FarolFlightRecorder0163.record(
            stage = \"OCR_REQUEST_EVALUATE\",
            packageName = universalResolvedForegroundPackage(),
            details = \"routeActive=${universalRouteJob?.isActive == true}; lastAnalyzedHash=$lastAnalyzedHash; lastSnapshotHash=$lastSnapshotHash; strictRoot=${hasStrictSelectedRootChecklist1()}; live=${currentSettings.liveReadingEnabled}; gesture=$bubbleGestureActive; ready=$serviceReady; external=${isUniversalExternalWindowActive()}; sdk=${Build.VERSION.SDK_INT}; generation=$universalScreenGeneration; windowGeneration=$universalWindowGeneration\",
            elapsedRealtimeNanos = ocrAttemptStartedElapsedNanos0163,
        )
"""
            return text[:pos] + insertion + text[pos:]
        return text
    raise SystemExit(f"{label}: esperado 1 trecho, encontrado {count}")
'''
if old not in source:
    raise SystemExit("porta replace_once do payload nao encontrada")
source = source.replace(old, new, 1)
exec(compile(source, __file__, "exec"))
