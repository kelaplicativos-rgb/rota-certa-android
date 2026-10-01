#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
PKG = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta'
ACT = PKG / 'FarolReadingActivationStage26.kt'
SERVICE = PKG / 'LiveRideAccessibilityService.kt'

def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, got {count}')
    return text.replace(old, new, 1)

a = ACT.read_text()
a = once(
    a,
    '''    data class CheapVisualSignal(\n        val ownOverlay: Boolean,\n        val windowSignature: String,\n        val sourceText: String,\n        val sourceSlot: String = "",\n        val contentChangeTypes: Int = 0,\n    )\n''',
    '''    data class CheapVisualSignal(\n        val ownOverlay: Boolean,\n        val windowSignature: String,\n        val sourceText: String,\n        val sourceSlot: String = "",\n        val contentChangeTypes: Int = 0,\n        val eventType: Int = 0,\n        val structuralSignature: String = "",\n        val bootstrapText: String = "",\n        val bootstrapEligible: Boolean = false,\n    )\n''',
    'CheapVisualSignal Stage40 bounded bootstrap fields',
)

start = a.index('    class PreCollectGate {')
end = a.index('    data class PaintState', start)
new_gate = r'''    class PreCollectGate {
        private var lastWindowSignature: String? = null
        private var lastRelevantValue: String? = null
        private val bootstrapValueByStructure = LinkedHashMap<Long, String>()
        private var generation = 0L

        @Synchronized
        fun admit(readingEnabled: Boolean, signal: CheapVisualSignal): Admission {
            Metrics.increment("eventsReceived")
            if (!readingEnabled) {
                Metrics.increment("eventsRejectedReadingOff")
                Metrics.increment("heavyCollectionsAvoided")
                return Admission(false, false, "reading_off", generation, null)
            }
            if (signal.ownOverlay) {
                Metrics.increment("ownOverlayEventsIgnored")
                Metrics.increment("heavyCollectionsAvoided")
                return Admission(false, false, "own_overlay", generation, null)
            }

            val previousWindow = lastWindowSignature
            val window = canonical(signal.windowSignature)
            val addressValue = canonicalStage34Visual(signal.sourceText).take(1024)
            val bootstrapValue = canonicalStage40BootstrapVisual(signal.bootstrapText).take(1024)
            val structuralValue = canonical(signal.structuralSignature).take(768)
            val windowChanged = previousWindow != null && previousWindow != window
            val previousAddress = lastRelevantValue
            lastWindowSignature = window

            // Strong path: actual address evidence is always authoritative for acquisition.
            if (addressValue.isNotBlank()) {
                if (previousAddress == addressValue) {
                    Metrics.increment("preCollectDuplicateSkipped")
                    Metrics.increment("heavyCollectionsAvoided")
                    Metrics.increment("stage40SameAddressEvidencePreserved")
                    return Admission(false, false, "stage40_same_address_evidence", generation, stableHash64(addressValue))
                }
                lastRelevantValue = addressValue
                generation += 1L
                Metrics.increment("heavyCollectionsStarted")
                Metrics.increment("stage40AddressEvidenceCollect")
                return Admission(
                    true, true,
                    if (previousAddress == null) "stage40_first_address_evidence" else "stage40_address_evidence_changed",
                    generation, stableHash64(addressValue),
                )
            }

            // Preserve the Stage34 same-context clear verification after real address evidence.
            if (previousAddress != null && !windowChanged) {
                lastRelevantValue = null
                generation += 1L
                Metrics.increment("heavyCollectionsStarted")
                Metrics.increment("stage40SameContextClearVerification")
                return Admission(true, true, "stage40_same_context_content_cleared_verify", generation, stableHash64("clear:$window"))
            }

            // Bounded fallback: blank/no-address events may bootstrap only when the cheap builder
            // proved a window transition or a large visual surface. Small leaves/map animation remain cheap.
            if (!signal.bootstrapEligible || structuralValue.isBlank()) {
                Metrics.increment("preCollectDuplicateSkipped")
                Metrics.increment("heavyCollectionsAvoided")
                Metrics.increment("stage40BootstrapNotEligible")
                return Admission(false, false, "stage40_bootstrap_not_eligible", generation, stableHash64("not-eligible"))
            }

            // Window id is deliberately excluded: package/window is provenance, not card authority.
            val structureFingerprint = stableHash64(structuralValue)
            val previousBootstrap = bootstrapValueByStructure[structureFingerprint]
            val firstStructure = !bootstrapValueByStructure.containsKey(structureFingerprint)
            val bootstrapChanged = bootstrapValue.isNotBlank() && previousBootstrap != bootstrapValue
            if (firstStructure || bootstrapChanged) {
                bootstrapValueByStructure.remove(structureFingerprint)
                bootstrapValueByStructure[structureFingerprint] = bootstrapValue
                while (bootstrapValueByStructure.size > 32) {
                    val iterator = bootstrapValueByStructure.entries.iterator()
                    if (iterator.hasNext()) { iterator.next(); iterator.remove() } else break
                }
                generation += 1L
                Metrics.increment("heavyCollectionsStarted")
                Metrics.increment(if (firstStructure) "stage40StructuralBootstrap" else "stage40BootstrapContentChanged")
                return Admission(
                    true, true,
                    if (firstStructure) "stage40_structural_bootstrap" else "stage40_bootstrap_content_changed",
                    generation, stableHash64("bootstrap:$structuralValue|$bootstrapValue"),
                )
            }

            Metrics.increment("preCollectDuplicateSkipped")
            Metrics.increment("heavyCollectionsAvoided")
            Metrics.increment("stage40BootstrapCoalesced")
            return Admission(false, false, "stage40_bootstrap_duplicate_coalesced", generation, structureFingerprint)
        }

        @Synchronized
        fun invalidate() {
            lastWindowSignature = null
            lastRelevantValue = null
            bootstrapValueByStructure.clear()
            generation += 1L
        }

        @Synchronized fun currentGeneration(): Long = generation
    }

'''
a = a[:start] + new_gate + a[end:]
anchor = '    private fun stableHash64(value: String): Long {'
helper = r'''    private fun canonicalStage40BootstrapVisual(value:String):String {
        val stable=value
            .replace(Regex("(?iu)r\\$\\s*\\d+(?:[.,]\\d{1,2})?(?:\\s*/\\s*km)?")," valor ")
            .replace(Regex("(?iu)(?:~\\s*)?\\b\\d+(?:[.,]\\d+)?\\s*(?:km|m)\\b")," distancia ")
            .replace(Regex("(?iu)\\b\\d{1,3}\\s*(?:s|seg|segs|segundos|min|mins|minutos|h|hora|horas)\\b")," tempo ")
            .replace(Regex("\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\b")," horario ")
            .replace(Regex("\\b\\d{1,3}%\\b")," percentual ")
        return canonical(stable)
    }

'''
if a.count(anchor) != 1:
    raise SystemExit('Stage40 bootstrap canonical anchor mismatch')
a = a.replace(anchor, helper + anchor, 1)
ACT.write_text(a)

s = SERVICE.read_text()
old_fn_start = s.index('    private fun buildCheapVisualSignalStage26(')
old_fn_end = s.index('    private fun collectUniversalAccessibilitySnapshotStage28(', old_fn_start)
new_fn = r'''    private fun buildCheapVisualSignalStage26(
        eventPackageStage26: String?,
        eventTypeStage26: Int,
        eventWindowIdStage26: Int,
        eventStage26: AccessibilityEvent,
    ): FarolReadingActivationStage26.CheapVisualSignal {
        val sourceStage40 = runCatching { eventStage26.source }.getOrNull()
        val parentStage40 = runCatching { sourceStage40?.parent }.getOrNull()
        val sourcePackageStage40 = normalizePackageName(runCatching { sourceStage40?.packageName?.toString() }.getOrNull())
        val parentPackageStage40 = normalizePackageName(runCatching { parentStage40?.packageName?.toString() }.getOrNull())
        val eventPackageNormalizedStage40 = normalizePackageName(eventPackageStage26)
        val ownPackageStage40 = normalizePackageName(packageName)
        val sourceBoundsStage40 = Rect()
        val parentBoundsStage40 = Rect()
        runCatching { sourceStage40?.getBoundsInScreen(sourceBoundsStage40) }
        runCatching { parentStage40?.getBoundsInScreen(parentBoundsStage40) }
        val screenWidthStage40 = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        val screenHeightStage40 = resources.displayMetrics.heightPixels.coerceAtLeast(1)
        fun largeSurfaceStage40(boundsStage40: Rect): Boolean =
            boundsStage40.width() * 100 >= screenWidthStage40 * 55 &&
                boundsStage40.height() * 100 >= screenHeightStage40 * 25
        val sourceLargeStage40 = largeSurfaceStage40(sourceBoundsStage40)
        val parentLargeStage40 = largeSurfaceStage40(parentBoundsStage40)
        val sourceSlotStage40 = buildString {
            append(eventWindowIdStage26); append(':')
            append(runCatching { sourceStage40?.viewIdResourceName }.getOrNull().orEmpty()); append(':')
            append(sourceBoundsStage40.left); append(':'); append(sourceBoundsStage40.top); append(':')
            append(sourceBoundsStage40.right); append(':'); append(sourceBoundsStage40.bottom)
        }
        val addressStage40 = LinkedHashSet<String>(8)
        val bootstrapStage40 = LinkedHashSet<String>(16)
        val structurePiecesStage40 = LinkedHashSet<String>(16)
        val sourceEditableStage40 = runCatching { sourceStage40?.isEditable == true }.getOrDefault(false)

        fun addTextStage40(valueStage40: CharSequence?, allowBootstrapStage40: Boolean = true) {
            val textStage40 = valueStage40?.toString()?.trim()?.takeIf(String::isNotBlank) ?: return
            if (FarolVisualIdentityStage23.countAddressLeads(textStage40) > 0) addressStage40 += textStage40.take(420)
            if (allowBootstrapStage40 && textStage40.length <= 120 && textStage40.count { it == '\n' } <= 1) {
                bootstrapStage40 += textStage40.take(120)
            }
        }
        fun addStructureStage40(nodeStage40: AccessibilityNodeInfo?) {
            nodeStage40 ?: return
            val boundsStage40 = Rect()
            runCatching { nodeStage40.getBoundsInScreen(boundsStage40) }
            val idStage40 = runCatching { nodeStage40.viewIdResourceName }.getOrNull().orEmpty()
            val classStage40 = runCatching { nodeStage40.className?.toString() }.getOrNull().orEmpty()
            val childrenStage40 = runCatching { nodeStage40.childCount }.getOrDefault(0).coerceAtLeast(0)
            structurePiecesStage40 += "$idStage40:$classStage40:${boundsStage40.left / 48}:${boundsStage40.top / 48}:${boundsStage40.right / 48}:${boundsStage40.bottom / 48}:$childrenStage40"
        }

        addTextStage40(runCatching { sourceStage40?.text }.getOrNull(), !sourceEditableStage40)
        addTextStage40(runCatching { sourceStage40?.contentDescription }.getOrNull(), !sourceEditableStage40)
        // Stage39 physical evidence proved an inDrive address can occur after the sixth fragment.
        runCatching { eventStage26.text }.getOrDefault(emptyList()).take(16).forEach { addTextStage40(it, !sourceEditableStage40) }
        addTextStage40(runCatching { parentStage40?.text }.getOrNull())
        addTextStage40(runCatching { parentStage40?.contentDescription }.getOrNull())

        val surfaceStage40 = if (sourceLargeStage40) sourceStage40 else if (parentLargeStage40) parentStage40 else sourceStage40
        addStructureStage40(surfaceStage40)
        val surfaceChildrenStage40 = runCatching { surfaceStage40?.childCount ?: 0 }.getOrDefault(0).coerceIn(0, 12)
        for (indexStage40 in 0 until surfaceChildrenStage40) {
            val childStage40 = runCatching { surfaceStage40?.getChild(indexStage40) }.getOrNull() ?: continue
            val childEditableStage40 = runCatching { childStage40.isEditable }.getOrDefault(false)
            addTextStage40(runCatching { childStage40.text }.getOrNull(), !childEditableStage40)
            addTextStage40(runCatching { childStage40.contentDescription }.getOrNull(), !childEditableStage40)
            addStructureStage40(childStage40)
            if (addressStage40.size >= 6 && bootstrapStage40.size >= 12 && structurePiecesStage40.size >= 12) break
        }

        val bootstrapEligibleStage40 = when (eventTypeStage26) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> true
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> sourceLargeStage40 || parentLargeStage40
            else -> false
        }
        val structuralPackageStage40 = normalizePackageName(
            runCatching { surfaceStage40?.packageName?.toString() }.getOrNull()
        ) ?: parentPackageStage40 ?: sourcePackageStage40 ?: eventPackageNormalizedStage40
        val surfaceBoundsStage40 = if (sourceLargeStage40) sourceBoundsStage40 else if (parentLargeStage40) parentBoundsStage40 else sourceBoundsStage40
        val structuralSignatureStage40 = when {
            !bootstrapEligibleStage40 -> ""
            eventTypeStage26 == AccessibilityEvent.TYPE_WINDOWS_CHANGED && sourceStage40 == null ->
                "window-transition:$eventWindowIdStage26"
            else -> buildString {
                append(structuralPackageStage40.orEmpty()); append(':')
                append(eventTypeStage26); append(':')
                append(surfaceBoundsStage40.left / 48); append(':'); append(surfaceBoundsStage40.top / 48); append(':')
                append(surfaceBoundsStage40.right / 48); append(':'); append(surfaceBoundsStage40.bottom / 48); append(':')
                append(structurePiecesStage40.sorted().joinToString("|").take(1200))
            }
        }
        val ownEventStage40 = eventPackageNormalizedStage40 == ownPackageStage40 &&
            (sourcePackageStage40 == ownPackageStage40 || sourcePackageStage40 == null)
        val ownOverlayStage40 = ownEventStage40 && addressStage40.isEmpty()
        return FarolReadingActivationStage26.CheapVisualSignal(
            ownOverlay = ownOverlayStage40,
            windowSignature = "$eventWindowIdStage26:${sourcePackageStage40.orEmpty()}",
            sourceText = addressStage40.sorted().joinToString("\n").take(1800),
            sourceSlot = sourceSlotStage40,
            contentChangeTypes = runCatching { eventStage26.contentChangeTypes }.getOrDefault(0),
            eventType = eventTypeStage26,
            structuralSignature = structuralSignatureStage40,
            bootstrapText = bootstrapStage40.sorted().joinToString("\n").take(1400),
            bootstrapEligible = bootstrapEligibleStage40,
        )
    }

'''
s = s[:old_fn_start] + new_fn + s[old_fn_end:]

old_log = 'details = "heavyCollect=${admissionStage26.heavyCollect}; visualGeneration=${admissionStage26.visualGeneration}; ownOverlay=${cheapSignalStage26.ownOverlay}; windowSignature=${cheapSignalStage26.windowSignature}; sourceSlot=${cheapSignalStage26.sourceSlot}; contentChangeTypes=${cheapSignalStage26.contentChangeTypes}; sourceText=${cheapSignalStage26.sourceText.take(900)}",'
new_log = 'details = "heavyCollect=${admissionStage26.heavyCollect}; reason=${admissionStage26.reason}; visualGeneration=${admissionStage26.visualGeneration}; ownOverlay=${cheapSignalStage26.ownOverlay}; windowSignature=${cheapSignalStage26.windowSignature}; sourceSlot=${cheapSignalStage26.sourceSlot}; eventType=${cheapSignalStage26.eventType}; contentChangeTypes=${cheapSignalStage26.contentChangeTypes}; bootstrapEligible=${cheapSignalStage26.bootstrapEligible}; structural=${cheapSignalStage26.structuralSignature.take(500)}; sourceText=${cheapSignalStage26.sourceText.take(900)}; bootstrapText=${cheapSignalStage26.bootstrapText.take(900)}",'
s = once(s, old_log, new_log, 'Stage38 precollect bounded successor diagnostics')

if s.count('stage23ScheduleGate.satisfyDirect(') < 1:
    raise SystemExit('Stage40 expected direct-path scheduled-demand satisfaction is missing')
SERVICE.write_text(s)

print('stage40_precollect_bounded=PASS')
