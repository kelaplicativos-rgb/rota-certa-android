from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[1]
service_path = root / 'app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt'
recovery_path = root / 'app/src/main/java/br/com/mapeiaia/rotacerta/FailedCardRecovery0161.kt'

service = service_path.read_text(encoding='utf-8')
anchor = '''    private fun requestScreenshotAnalysis(allowPopupCandidate: Boolean = false) {
        @Suppress("UNUSED_VARIABLE") val ignoredAllowPopupCandidate0161 = allowPopupCandidate
'''
replacement = '''    private fun requestScreenshotAnalysis(allowPopupCandidate: Boolean = false) {
        // bubble_instant_drag_0_1_116
        // bubble_drag_screenshot_pause_0_1_116
        // bubble_drag_ocr_background_0_1_116
        @Suppress("UNUSED_VARIABLE") val ignoredAllowPopupCandidate0161 = allowPopupCandidate
'''
if service.count(anchor) != 1:
    raise SystemExit('0.1.161 rerun service marker anchor not found exactly once')
service_path.write_text(service.replace(anchor, replacement, 1), encoding='utf-8')

recovery = recovery_path.read_text(encoding='utf-8')

old_prune = '''    private fun prune(nowMillis: Long) {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            val reference = maxOf(entry.startedAtMillis, entry.completedAtMillis)
            if (reference > 0L && nowMillis >= reference && nowMillis - reference > retentionMillis) iterator.remove()
        }
    }
'''
new_prune = '''    private fun prune(nowMillis: Long) {
        entries.entries.removeAll { (_, entry) ->
            val reference = maxOf(entry.startedAtMillis, entry.completedAtMillis)
            reference > 0L && nowMillis >= reference && nowMillis - reference > retentionMillis
        }
    }
'''
if recovery.count(old_prune) != 1:
    raise SystemExit('0.1.161 rerun prune loop anchor not found exactly once')
recovery = recovery.replace(old_prune, new_prune, 1)
old_trim = '''    private fun trimToLimit() {
        while (entries.size > maxEntries) {
            val first = entries.entries.firstOrNull()?.key ?: return
            entries.remove(first)
        }
    }
'''
new_trim = '''    private fun trimToLimit() {
        val excess = (entries.size - maxEntries).coerceAtLeast(0)
        entries.keys.take(excess).toList().forEach(entries::remove)
    }
'''
if recovery.count(old_trim) != 1:
    raise SystemExit('0.1.161 rerun bounded gate anchor not found exactly once')
recovery = recovery.replace(old_trim, new_trim, 1)

old_merge = '''        val merged = merge(accessibilityText, ocrText, nodes)
        if (!probableRideCard(merged, packageName)) return null

        val mergedEvaluation = SimpleSavedAppFarolPolicy.evaluate(packageName, savedPackages, merged)
'''
new_merge = '''        val merged = merge(accessibilityText, ocrText, nodes)
        if (!probableRideCard(merged, packageName)) return null
        val lines = orderedLines(accessibilityText, ocrText, nodes)
        if (hasAmbiguousMarkedLocation(lines, originMarkers) ||
            hasAmbiguousMarkedLocation(lines, destinationMarkers)
        ) return null

        val mergedEvaluation = SimpleSavedAppFarolPolicy.evaluate(packageName, savedPackages, merged)
'''
if recovery.count(old_merge) != 1:
    raise SystemExit('0.1.161 rerun merged ambiguity anchor not found exactly once')
recovery = recovery.replace(old_merge, new_merge, 1)

old_later_lines = '''
        val lines = orderedLines(accessibilityText, ocrText, nodes)
        val origin = extractMarkedLocation(lines, originMarkers) ?: return null
'''
new_later_lines = '''
        val origin = extractMarkedLocation(lines, originMarkers) ?: return null
'''
if recovery.count(old_later_lines) != 1:
    raise SystemExit('0.1.161 rerun duplicate lines anchor not found exactly once')
recovery = recovery.replace(old_later_lines, new_later_lines, 1)

extract_anchor = '''    private fun extractMarkedLocation(lines: List<String>, markers: List<String>): MarkedLocation? {
'''
ambiguity_helper = '''    private fun hasAmbiguousMarkedLocation(lines: List<String>, markers: List<String>): Boolean {
        val candidates = linkedSetOf<String>()
        lines.forEachIndexed { index, raw ->
            val line = clean(raw)
            val marker = markers.firstOrNull { markerMatches(line, it) } ?: return@forEachIndexed
            val sameLine = remainderAfterMarker(line, marker)
            if (sameLine.isNotBlank() && safeLocation(sameLine, strongMarker = marker.length > 1)) {
                candidates += canonical(sameLine)
            } else {
                for (offset in 1..2) {
                    val candidate = lines.getOrNull(index + offset)?.let(::clean).orEmpty()
                    if (safeLocation(candidate, strongMarker = marker.length > 1)) {
                        candidates += canonical(candidate)
                        break
                    }
                    if (candidate.isNotBlank() && isAnotherMarker(candidate)) break
                }
            }
        }
        return candidates.size > 1
    }

'''
if recovery.count(extract_anchor) != 1:
    raise SystemExit('0.1.161 rerun ambiguity helper anchor not found exactly once')
recovery = recovery.replace(extract_anchor, ambiguity_helper + extract_anchor, 1)
recovery_path.write_text(recovery, encoding='utf-8')
print('0.1.161 rerun: drag contracts restored, bounded gate loop removed, ambiguity fails closed')
