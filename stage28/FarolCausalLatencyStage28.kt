package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.ceil

/** Stage28 cheap causal guards. No timer, OCR, network or package-based visual authority lives here. */
object FarolCausalLatencyStage28 {
    const val CONTRACT_MARKER = "FAROL_CAUSAL_LATENCY_STALE_ACTIVATION_STAGE28"
    const val LIVE_ACTIVATION_MARKER = "LIVE_EXECUTION_NOT_USAGE_HISTORY_STAGE28"
    const val IMMEDIATE_INVALIDATION_MARKER = "OLD_PAINT_UI_REVOKED_BEFORE_HEAVY_WORK_STAGE28"
    const val UNIVERSAL_TWO_ADDRESS_MARKER = "TWO_CURRENT_ADDRESSES_ANY_VISIBLE_PACKAGE_STAGE28"
    const val SOURCE_FAST_PATH_MARKER = "EVENT_SOURCE_SUBTREE_BEFORE_ALL_WINDOWS_STAGE28"
    const val OCR_COALESCENCE_MARKER = "ONE_OCR_PER_VISUAL_GENERATION_STAGE28"
    const val ROUTE_COALESCENCE_MARKER = "ONE_ROUTE_PER_CURRENT_DESTINATION_STAGE28"
    const val O1_STALE_MARKER = "O1_GENERATION_TOKEN_STALE_GUARD_STAGE28"
    const val GOOGLE_REAL_MARKER = "REAL_GOOGLE_ROUTE_PRESERVED_STAGE28"

    data class Lease(
        val activationGeneration: Long,
        val visualGeneration: Long,
        val paintToken: Long,
    )

    data class PaintState(
        val color: String,
        val distanceKm: Double?,
        val activationGeneration: Long,
        val visualGeneration: Long,
        val paintToken: Long,
    )

    /** Pure state mirror used by deterministic tests and cheap runtime freshness checks. */
    class WorkCoordinator {
        private var enabled = false
        private var activationGeneration = 0L
        private var visualGeneration = 0L
        private var paintToken = 0L
        private var paint = PaintState("IDLE", null, 0L, 0L, 0L)

        @Synchronized
        fun readingOn(): PaintState {
            if (!enabled) {
                enabled = true
                activationGeneration += 1L
                paintToken += 1L
                paint = PaintState("WAITING", null, activationGeneration, visualGeneration, paintToken)
            }
            return paint
        }

        @Synchronized
        fun readingOff(): PaintState {
            if (enabled) activationGeneration += 1L
            enabled = false
            visualGeneration += 1L
            paintToken += 1L
            paint = PaintState("IDLE", null, activationGeneration, visualGeneration, paintToken)
            return paint
        }

        @Synchronized
        fun visualChanged(): PaintState {
            if (!enabled) return paint
            visualGeneration += 1L
            paintToken += 1L
            paint = PaintState("WAITING", null, activationGeneration, visualGeneration, paintToken)
            return paint
        }

        @Synchronized
        fun seedFinal(color: String, km: Double): PaintState {
            paint = PaintState(color, km, activationGeneration, visualGeneration, paintToken)
            return paint
        }

        @Synchronized
        fun lease(): Lease = Lease(activationGeneration, visualGeneration, paintToken)

        @Synchronized
        fun isFresh(lease: Lease): Boolean = enabled &&
            lease.activationGeneration == activationGeneration &&
            lease.visualGeneration == visualGeneration &&
            lease.paintToken == paintToken

        @Synchronized
        fun applyFinalIfFresh(lease: Lease, color: String, km: Double): Boolean {
            if (!isFresh(lease)) return false
            paint = PaintState(color, km, activationGeneration, visualGeneration, paintToken)
            return true
        }

        @Synchronized fun state(): PaintState = paint
    }

    data class VisualSignal(
        val ownOverlay: Boolean,
        val windowKey: String,
        val slotKey: String,
        val relevantText: String,
    )

    data class VisualAdmission(
        val process: Boolean,
        val mutation: Boolean,
        val generation: Long,
        val fingerprint: Long,
        val reason: String,
    )

    /** Content identity, not time, decides whether heavy work is admitted. */
    class VisualGate {
        private var generation = 0L
        private var windowKey = ""
        private val bySlot = LinkedHashMap<String, String>()

        @Synchronized
        fun admit(readingEnabled: Boolean, signal: VisualSignal): VisualAdmission {
            Metrics.increment("eventsReceived")
            if (!readingEnabled) {
                Metrics.increment("eventsRejectedReadingOff")
                Metrics.increment("heavyCollectionsAvoided")
                return VisualAdmission(false, false, generation, 0L, "reading_off")
            }
            if (signal.ownOverlay) {
                Metrics.increment("ownOverlayEventsIgnored")
                Metrics.increment("heavyCollectionsAvoided")
                return VisualAdmission(false, false, generation, 0L, "own_overlay")
            }
            val w = canonical(signal.windowKey)
            val slot = canonical(signal.slotKey).ifBlank { "window" }
            val value = canonical(signal.relevantText).take(1200)
            val changedWindow = windowKey != w
            if (changedWindow) {
                windowKey = w
                bySlot.clear()
                if (value.isNotBlank()) bySlot[slot] = value
                generation += 1L
                Metrics.increment("visualIdentityChanged")
                return VisualAdmission(true, true, generation, stableHash64("$w|$slot|$value"), "window_changed")
            }
            val previous = bySlot[slot]
            if (value.isBlank()) {
                if (previous == null) {
                    Metrics.increment("preCollectDuplicateSkipped")
                    Metrics.increment("eventsCoalesced")
                    Metrics.increment("visualIdentityRepeated")
                    Metrics.increment("heavyCollectionsAvoided")
                    return VisualAdmission(false, false, generation, stableHash64("$w|empty"), "irrelevant_noise")
                }
                bySlot.remove(slot)
                generation += 1L
                Metrics.increment("visualIdentityChanged")
                return VisualAdmission(true, true, generation, stableHash64("$w|$slot|cleared"), "address_slot_cleared")
            }
            if (previous == value) {
                Metrics.increment("preCollectDuplicateSkipped")
                Metrics.increment("eventsCoalesced")
                Metrics.increment("visualIdentityRepeated")
                Metrics.increment("heavyCollectionsAvoided")
                return VisualAdmission(false, false, generation, stableHash64("$w|$slot|$value"), "same_identity")
            }
            bySlot[slot] = value
            generation += 1L
            Metrics.increment("visualIdentityChanged")
            return VisualAdmission(true, true, generation, stableHash64("$w|$slot|$value"), if (previous == null) "new_address_slot" else "address_changed")
        }

        @Synchronized fun invalidate() { windowKey = ""; bySlot.clear(); generation += 1L }
        @Synchronized fun generation(): Long = generation
    }

    /** Deduplicates OCR by exact current visual generation. */
    class OcrGate {
        private var activeGeneration: Long? = null
        private var completedGeneration: Long? = null

        @Synchronized fun request(generation: Long): Boolean {
            if (activeGeneration == generation || completedGeneration == generation) {
                Metrics.increment("ocrCoalesced")
                return false
            }
            activeGeneration = generation
            Metrics.increment("ocrRequests")
            return true
        }

        @Synchronized fun started(generation: Long): Boolean {
            if (activeGeneration != generation) return false
            Metrics.increment("ocrStarts")
            return true
        }

        @Synchronized fun complete(generation: Long) {
            if (activeGeneration == generation) activeGeneration = null
            completedGeneration = generation
        }

        @Synchronized fun invalidate(currentGeneration: Long) {
            if (activeGeneration != null && activeGeneration != currentGeneration) Metrics.increment("ocrCancelled")
            activeGeneration = null
            if (completedGeneration != currentGeneration) completedGeneration = null
        }
    }

    data class RouteKey(val activationGeneration: Long, val visualGeneration: Long, val destination: String)

    /** One Google request per current destination/generation; no distance heuristics. */
    class RouteGate {
        private val active = LinkedHashSet<RouteKey>()
        @Synchronized fun begin(key: RouteKey): Boolean {
            val normalized = key.copy(destination = canonical(key.destination))
            if (!active.add(normalized)) {
                Metrics.increment("routeDeduplicated")
                return false
            }
            Metrics.increment("routeRequests")
            return true
        }
        @Synchronized fun finish(key: RouteKey) { active.remove(key.copy(destination = canonical(key.destination))) }
        @Synchronized fun invalidateExcept(activationGeneration: Long, visualGeneration: Long) {
            val before = active.size
            active.removeAll { it.activationGeneration != activationGeneration || it.visualGeneration != visualGeneration }
            if (before > active.size) Metrics.increment("routeCancelledStale", (before - active.size).toLong())
        }
        @Synchronized fun size(): Int = active.size
    }

    /**
     * Removes obvious prose appended after a street-number address. It does not validate package,
     * city, state, distance or card model; the goal is delimitation only.
     */
    fun trimNarrativeSuffix(value: String): String {
        val normalized = value.replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return normalized
        val match = narrativeBoundary.find(normalized) ?: return normalized
        val prefix = normalized.substring(0, match.range.first).trim().trimEnd(',', ';', ':', '-', '–', '—')
        return if (containsPlausibleStreetNumber(prefix)) prefix else normalized
    }

    fun containsPlausibleStreetNumber(value: String): Boolean =
        Regex("(?iu)\\b(?:rua|r\\.|avenida|av\\.|alameda|travessa|estrada|rodovia|praça|praca|largo|via|viela|beco|passagem)\\b.{0,100}?\\b\\d{1,6}[A-Za-z]?\\b").containsMatchIn(value)

    fun currentExecutionEvents(activePackages: Set<String>): List<FarolReadingActivationStage26.UsageEvent> =
        activePackages.sorted().map {
            FarolReadingActivationStage26.UsageEvent(
                packageName = it,
                signal = FarolReadingActivationStage26.UsageSignal.FOREGROUND_SERVICE_START,
                timestampMillis = 0L,
            )
        }

    private val narrativeBoundary = Regex(
        "(?iu)(?:[,;.]?\\s+)(?:mas\\b|por[eé]m\\b|contudo\\b|entretanto\\b|enquanto\\b|porque\\b|pois\\b|e\\s+a\\s+bolinha\\b|a\\s+bolinha\\b|mostrando\\b|continua\\s+(?:verde|vermelha|vermelho|amarela|cinza)\\b|resultado\\s+(?:anterior|antigo)\\b)",
    )

    private fun canonical(value: String): String = Normalizer.normalize(
        value.replace(Regex("\\s+"), " ").trim().lowercase(Locale.ROOT),
        Normalizer.Form.NFD,
    ).replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ").trim()

    private fun stableHash64(value: String): Long {
        var hash = -3750763034362895579L
        value.forEach { hash = (hash xor it.code.toLong()) * 1099511628211L }
        return hash
    }

    object Metrics {
        private const val MAX_SAMPLES = 512
        private val lock = Any()
        private val counters = LinkedHashMap<String, Long>()
        private val gauges = LinkedHashMap<String, Long>()
        private val samples = LinkedHashMap<String, ArrayDeque<Long>>()

        fun resetForTests() = synchronized(lock) { counters.clear(); gauges.clear(); samples.clear() }
        fun increment(name: String, amount: Long = 1L) = synchronized(lock) { counters[name] = (counters[name] ?: 0L) + amount }
        fun setGauge(name: String, value: Long) = synchronized(lock) { gauges[name] = value }
        fun sample(name: String, durationNs: Long) = synchronized(lock) {
            val q = samples.getOrPut(name) { ArrayDeque() }
            if (q.size >= MAX_SAMPLES) q.removeFirst()
            q.addLast(durationNs.coerceAtLeast(0L))
        }
        fun counter(name: String): Long = synchronized(lock) { counters[name] ?: 0L }
        fun gauge(name: String): Long = synchronized(lock) { gauges[name] ?: 0L }
        fun stats(name: String): String = synchronized(lock) {
            val values = samples[name]?.toList()?.sorted().orEmpty()
            if (values.isEmpty()) return@synchronized "count=0; median_us=-1; p95_us=-1; max_us=-1"
            fun p(v: Double): Long = values[(ceil(values.size * v).toInt() - 1).coerceIn(0, values.lastIndex)]
            "count=${values.size}; median_us=${p(.50)/1000}; p95_us=${p(.95)/1000}; max_us=${values.last()/1000}"
        }
        fun exportReport(): String = synchronized(lock) {
            buildString {
                appendLine("ROTA CERTA — STAGE28 CAUSAL LATENCY / STALE / ACTIVATION METRICS")
                appendLine("marker=$CONTRACT_MARKER")
                val names = listOf(
                    "activationOn","activationOff","eventsReceived","eventsRejectedReadingOff","preCollectDuplicateSkipped",
                    "eventsCoalesced","ownOverlayEventsIgnored","visualIdentityChanged","visualIdentityRepeated","oldPaintInvalidated",
                    "heavyCollectionsStarted","heavyCollectionsAvoided","nodesVisited","blocksEmitted","addressParserInvocations",
                    "duplicateSubtreesAvoided","ocrRequests","ocrStarts","ocrCancelled","ocrStale","ocrCoalesced",
                    "routeRequests","routeCacheHits","routeDeduplicated","routeCancelledStale","workCancelledOnReadingOff",
                    "stalePaintBlockedAfterReadingOff",
                )
                names.forEach { appendLine("$it=${counters[it] ?: 0L}") }
                appendLine("selectedAppsActiveCount=${gauges["selectedAppsActiveCount"] ?: 0L}")
                appendLine("activationGeneration=${gauges["activationGeneration"] ?: 0L}")
                listOf(
                    "eventToActivationState","eventToMutationDetected","eventToOldPaintInvalidated","collect","evaluate",
                    "eventToCandidate","candidateToRouteStart","route","routeResponseToPaint","eventToFinalGreenRedKm",
                ).forEach { appendLine("$it | ${stats(it)}") }
            }.trimEnd()
        }
    }
}
