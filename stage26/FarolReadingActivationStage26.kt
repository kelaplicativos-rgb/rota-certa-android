package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.ceil

/**
 * Stage26 contract: selected packages activate/deactivate the reading infrastructure only.
 * They never authorize the visual card. Visual authority remains the Stage19 universal pipeline.
 */
object FarolReadingActivationStage26 {
    const val CONTRACT_MARKER = "FAROL_READING_ACTIVATION_STAGE26"
    const val ACTIVATION_ONLY_MARKER = "SELECTED_PACKAGES_ACTIVATE_INFRASTRUCTURE_ONLY_STAGE26"
    const val FAIL_CLOSED_MARKER = "USAGE_ACCESS_FAIL_CLOSED_STAGE26"
    const val PRECOLLECT_MARKER = "PRECOLLECT_VISUAL_ADMISSION_BEFORE_HEAVY_TRAVERSAL_STAGE26"
    const val INVALIDATION_MARKER = "OLD_PAINT_INVALIDATED_BEFORE_COLLECT_STAGE26"
    const val COMPACT_COLLECTOR_MARKER = "COMPACT_NON_OVERLAPPING_COLLECTOR_STAGE26"
    const val ATOMIC_PAINT_MARKER = "FINAL_COLOR_AND_KM_SAME_GENERATION_STAGE26"
    const val NO_TEMPORAL_DEBOUNCE_MARKER = "NO_TEMPORAL_DEBOUNCE_AUTHORITY_STAGE26"

    enum class UsageSignal {
        ACTIVITY_RESUMED,
        ACTIVITY_PAUSED,
        ACTIVITY_STOPPED,
        FOREGROUND_SERVICE_START,
        FOREGROUND_SERVICE_STOP,
        PROCESS_GONE,
    }

    data class UsageEvent(val packageName: String, val signal: UsageSignal, val timestampMillis: Long = 0L)

    data class ActivationSnapshot(
        val enabled: Boolean,
        val usageAccessGranted: Boolean,
        val selectedPackages: Set<String>,
        val activeSelectedPackages: Set<String>,
        val generation: Long,
    ) {
        val selectedAppsActiveCount: Int get() = activeSelectedPackages.size
    }

    data class Lease(val activationGeneration: Long, val visualGeneration: Long, val paintToken: Long)

    class ActivationMachine {
        private var selected = emptySet<String>()
        private val resumed = LinkedHashSet<String>()
        private val foregroundServices = LinkedHashSet<String>()
        private var usageAccessGranted = false
        private var generation = 0L
        private var enabled = false

        @Synchronized
        fun updateSelection(packages: Set<String>): ActivationSnapshot {
            selected = packages.mapNotNull(::normalizePackage).toSet()
            resumed.retainAll(selected)
            foregroundServices.retainAll(selected)
            return recompute()
        }

        @Synchronized
        fun setUsageAccess(granted: Boolean): ActivationSnapshot {
            usageAccessGranted = granted
            if (!granted) {
                resumed.clear()
                foregroundServices.clear()
            }
            return recompute()
        }

        @Synchronized
        fun replaceUsageState(events: List<UsageEvent>): ActivationSnapshot {
            resumed.clear()
            foregroundServices.clear()
            if (usageAccessGranted) {
                events.sortedBy { it.timestampMillis }.forEach(::applyEventLocked)
            }
            return recompute()
        }

        @Synchronized
        fun observe(event: UsageEvent): ActivationSnapshot {
            if (usageAccessGranted) applyEventLocked(event)
            return recompute()
        }

        @Synchronized
        fun snapshot(): ActivationSnapshot = snapshotLocked()

        @Synchronized
        fun lease(visualGeneration: Long, paintToken: Long): Lease = Lease(generation, visualGeneration, paintToken)

        @Synchronized
        fun isLeaseFresh(lease: Lease, currentVisualGeneration: Long, currentPaintToken: Long): Boolean =
            enabled && usageAccessGranted && lease.activationGeneration == generation &&
                lease.visualGeneration == currentVisualGeneration && lease.paintToken == currentPaintToken

        private fun applyEventLocked(event: UsageEvent) {
            val pkg = normalizePackage(event.packageName) ?: return
            if (pkg !in selected) return
            when (event.signal) {
                UsageSignal.ACTIVITY_RESUMED -> resumed += pkg
                UsageSignal.ACTIVITY_PAUSED,
                UsageSignal.ACTIVITY_STOPPED -> resumed -= pkg
                UsageSignal.FOREGROUND_SERVICE_START -> foregroundServices += pkg
                UsageSignal.FOREGROUND_SERVICE_STOP -> foregroundServices -= pkg
                UsageSignal.PROCESS_GONE -> {
                    resumed -= pkg
                    foregroundServices -= pkg
                }
            }
        }

        private fun recompute(): ActivationSnapshot {
            val newEnabled = usageAccessGranted && selected.isNotEmpty() &&
                ((resumed + foregroundServices).any { it in selected })
            if (newEnabled != enabled) {
                generation += 1L
                enabled = newEnabled
                Metrics.increment(if (enabled) "activationOn" else "activationOff")
            }
            Metrics.setGauge("selectedAppsActiveCount", activeSelectedLocked().size.toLong())
            return snapshotLocked()
        }

        private fun activeSelectedLocked(): Set<String> = (resumed + foregroundServices).filterTo(LinkedHashSet()) { it in selected }

        private fun snapshotLocked(): ActivationSnapshot = ActivationSnapshot(
            enabled = enabled,
            usageAccessGranted = usageAccessGranted,
            selectedPackages = selected,
            activeSelectedPackages = activeSelectedLocked(),
            generation = generation,
        )
    }

    data class CheapVisualSignal(
        val ownOverlay: Boolean,
        val windowSignature: String,
        val sourceText: String,
        val sourceSlot: String = "",
        val contentChangeTypes: Int = 0,
    )

    data class Admission(
        val heavyCollect: Boolean,
        val mutation: Boolean,
        val reason: String,
        val visualGeneration: Long,
        val fingerprint: Long?,
    )

    class PreCollectGate {
        private var lastWindowSignature: String? = null
        private val lastRelevantValueBySlot = LinkedHashMap<String, String>()
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
            val window = canonical(signal.windowSignature)
            val value = canonical(signal.sourceText).take(1024)
            val slot = canonical(signal.sourceSlot).ifBlank { "window" }
            val windowChanged = lastWindowSignature != window
            if (windowChanged) {
                lastWindowSignature = window
                lastRelevantValueBySlot.clear()
                if (value.isNotBlank()) lastRelevantValueBySlot[slot] = value
                generation += 1L
                Metrics.increment("heavyCollectionsStarted")
                return Admission(true, true, "window_or_popup_mutation", generation, stableHash64("$window|$slot|$value"))
            }
            if (value.isBlank()) {
                val previous = lastRelevantValueBySlot.remove(slot)
                if (previous != null) {
                    generation += 1L
                    Metrics.increment("heavyCollectionsStarted")
                    return Admission(true, true, "relevant_visual_slot_cleared", generation, stableHash64("$window|$slot|cleared"))
                }
                Metrics.increment("preCollectDuplicateSkipped")
                Metrics.increment("heavyCollectionsAvoided")
                return Admission(false, false, "same_window_without_relevant_visual_delta", generation, stableHash64(window))
            }
            val previous = lastRelevantValueBySlot[slot]
            if (previous == value) {
                Metrics.increment("preCollectDuplicateSkipped")
                Metrics.increment("heavyCollectionsAvoided")
                return Admission(false, false, "same_relevant_slot_value", generation, stableHash64("$window|$slot|$value"))
            }
            lastRelevantValueBySlot[slot] = value
            generation += 1L
            Metrics.increment("heavyCollectionsStarted")
            return Admission(true, true, if (previous == null) "new_relevant_visual_slot" else "relevant_visual_slot_changed", generation, stableHash64("$window|$slot|$value"))
        }

        @Synchronized
        fun invalidate() {
            lastWindowSignature = null
            lastRelevantValueBySlot.clear()
            generation += 1L
        }

        @Synchronized fun currentGeneration(): Long = generation
    }

    data class PaintState(val color: String, val distanceKm: Double?, val generation: Long, val paintToken: Long)

    /** Small deterministic coordinator used by runtime and tests to enforce invalidation order/freshness. */
    class WorkCoordinator {
        private var paint = PaintState("NEUTRAL", null, 0L, 0L)
        private var routeActive = false
        private var ocrActive = false
        private var cacheActive = false
        private val order = ArrayList<String>()

        @Synchronized
        fun seedFinal(color: String, distanceKm: Double, generation: Long = 1L, paintToken: Long = 1L) {
            paint = PaintState(color, distanceKm, generation, paintToken)
        }

        @Synchronized
        fun beginAsync(route: Boolean = false, ocr: Boolean = false, cache: Boolean = false) {
            routeActive = route; ocrActive = ocr; cacheActive = cache
        }

        @Synchronized
        fun invalidateBeforeCollect(newGeneration: Long, nowNs: Long = 0L) {
            order += "invalidate_generation"
            routeActive = false; order += "cancel_route"
            ocrActive = false; order += "cancel_ocr"
            cacheActive = false; order += "invalidate_cache_result"
            paint = PaintState("NEUTRAL", null, newGeneration, paint.paintToken + 1L)
            order += "clear_old_paint"
            order += "collect"
            Metrics.sample("eventToOldPaintInvalidated", nowNs.coerceAtLeast(0L))
        }

        @Synchronized
        fun readingOff() {
            val cancelled = listOf(routeActive, ocrActive, cacheActive).count { it }
            if (cancelled > 0) Metrics.increment("workCancelledOnReadingOff", cancelled.toLong())
            routeActive = false; ocrActive = false; cacheActive = false
            paint = PaintState("NEUTRAL", null, paint.generation + 1L, paint.paintToken + 1L)
        }

        @Synchronized
        fun applyFinalIfFresh(fresh: Boolean, color: String, km: Double, generation: Long, paintToken: Long): Boolean {
            if (!fresh || generation != paint.generation || paintToken != paint.paintToken) {
                Metrics.increment("stalePaintBlockedAfterReadingOff")
                return false
            }
            paint = PaintState(color, km, generation, paintToken)
            return true
        }

        @Synchronized fun state(): PaintState = paint
        @Synchronized fun trace(): List<String> = order.toList()
    }

    data class CompactNode(
        val id: String,
        val text: String = "",
        val children: List<CompactNode> = emptyList(),
        val windowId: Int = 0,
        val windowLayer: Int = 0,
    )

    data class CompactResult(
        val blocks: List<String>,
        val nodesVisited: Int,
        val addressParserInvocations: Int,
        val duplicateSubtreesAvoided: Int,
    )

    /** Pure policy mirror for deterministic compact-collector budget/regression tests. */
    fun compact(node: CompactNode): CompactResult {
        var visited = 0
        var dupes = 0
        val seen = HashSet<Long>()
        val leaves = ArrayList<String>()
        fun walk(current: CompactNode) {
            if (visited >= 128 || leaves.size >= 12) return
            visited += 1
            val own = normalizeWhitespace(current.text)
            if (own.isNotBlank()) {
                val hash = stableHash64(canonical(own))
                if (!seen.add(hash)) dupes += 1 else leaves += own
                if (addressLeadCount(own) >= 2) return
            }
            for (child in current.children) {
                walk(child)
                if (leaves.any { addressLeadCount(it) >= 2 }) break
            }
        }
        walk(node)
        val minimal = ArrayList<String>()
        val context = LinkedHashSet<String>()
        for (line in leaves) {
            context += line
            if (addressLeadCount(line) > 0 || context.size <= 4) {
                // keep only unique minimal lines; never re-emit an aggregate ancestor copy
                if (line !in minimal) minimal += line
            }
            if (minimal.sumOf(::addressLeadCount) >= 2) break
        }
        Metrics.addTotal("nodesVisited", visited.toLong())
        Metrics.addTotal("blocksEmitted", minimal.size.toLong())
        val downstreamParserInvocations = minimal.size
        Metrics.addTotal("addressParserInvocations", downstreamParserInvocations.toLong())
        Metrics.addTotal("duplicateSubtreesAvoided", dupes.toLong())
        return CompactResult(minimal, visited, downstreamParserInvocations, dupes)
    }

    fun addressLeadCount(text: String): Int = addressLeadRegex.findAll(normalizeWhitespace(text)).take(3).count()

    private fun normalizePackage(value: String?): String? = value?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotBlank)
    private fun normalizeWhitespace(value: String): String = value.replace(Regex("\\s+"), " ").trim()
    private fun canonical(value: String): String = Normalizer.normalize(normalizeWhitespace(value).lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").replace(Regex("\\s+"), " ").trim()
    private fun stableHash64(value: String): Long {
        var hash = -3750763034362895579L
        value.forEach { ch -> hash = (hash xor ch.code.toLong()) * 1099511628211L }
        return hash
    }
    private val addressLeadRegex = Regex(
        "(?:^|[\\s:(])(?:r\\.|av\\.|rua|avenida|alameda|travessa|estrada|rodovia|praca|praça|largo|via|viela|beco|marginal|passagem|servidao|servidão|shopping|terminal|estacao|estação|aeroporto|rodoviaria|rodoviária|hospital|mercado|restaurante|hotel|pousada|escola|faculdade|universidade|posto|parque|condominio|condomínio|residencial)(?:\\b|(?=\\s))",
        RegexOption.IGNORE_CASE,
    )

    object Metrics {
        private const val MAX_SAMPLES = 512
        private val lock = Any()
        private val counters = LinkedHashMap<String, Long>()
        private val gauges = LinkedHashMap<String, Long>()
        private val totals = LinkedHashMap<String, Long>()
        private val samples = LinkedHashMap<String, ArrayDeque<Long>>()

        fun resetForTests() = synchronized(lock) { counters.clear(); gauges.clear(); totals.clear(); samples.clear() }
        fun increment(name: String, amount: Long = 1L) = synchronized(lock) { counters[name] = (counters[name] ?: 0L) + amount }
        fun setGauge(name: String, value: Long) = synchronized(lock) { gauges[name] = value }
        fun addTotal(name: String, value: Long) = synchronized(lock) { totals[name] = (totals[name] ?: 0L) + value }
        fun sample(name: String, durationNs: Long) = synchronized(lock) {
            val bucket = samples.getOrPut(name) { ArrayDeque() }
            if (bucket.size >= MAX_SAMPLES) bucket.removeFirst()
            bucket.addLast(durationNs.coerceAtLeast(0L))
        }
        fun counter(name: String): Long = synchronized(lock) { counters[name] ?: 0L }
        fun gauge(name: String): Long = synchronized(lock) { gauges[name] ?: 0L }
        fun total(name: String): Long = synchronized(lock) { totals[name] ?: 0L }
        fun stats(name: String): String = synchronized(lock) {
            val values = samples[name]?.toList()?.sorted().orEmpty()
            if (values.isEmpty()) return@synchronized "count=0; median_us=-1; p95_us=-1; max_us=-1"
            fun percentile(p: Double): Long = values[(ceil(values.size * p).toInt() - 1).coerceIn(0, values.lastIndex)]
            "count=${values.size}; median_us=${percentile(.50)/1000L}; p95_us=${percentile(.95)/1000L}; max_us=${values.last()/1000L}"
        }
        fun exportReport(): String = synchronized(lock) {
            buildString {
                appendLine("ROTA CERTA — STAGE26 READING ACTIVATION / LATENCY METRICS")
                appendLine("marker=$CONTRACT_MARKER")
                for (name in listOf("activationOn","activationOff","eventsReceived","eventsRejectedReadingOff","preCollectDuplicateSkipped","ownOverlayEventsIgnored","heavyCollectionsStarted","heavyCollectionsAvoided","ocrRequests","ocrStarts","ocrCancelled","ocrStale","workCancelledOnReadingOff","stalePaintBlockedAfterReadingOff")) appendLine("$name=${counters[name] ?: 0L}")
                appendLine("selectedAppsActiveCount=${gauges["selectedAppsActiveCount"] ?: 0L}")
                for (name in listOf("nodesVisited","blocksEmitted","addressParserInvocations","duplicateSubtreesAvoided")) appendLine("$name=${totals[name] ?: 0L}")
                for (name in listOf("eventToMutationDetected","eventToOldPaintInvalidated","collect","evaluate","eventToCandidate","candidateToRouteStart","route","routeResponseToPaint","eventToFinalGreenRedKm")) appendLine("$name | ${stats(name)}")
            }.trimEnd()
        }
    }
}
