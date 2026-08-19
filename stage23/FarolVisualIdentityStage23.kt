package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.ceil

/** Stage23: event volume is advisory; cheap visual identity owns expensive-work admission. */
object FarolVisualIdentityStage23 {
    const val CONTRACT_MARKER = "FAROL_VISUAL_IDENTITY_COALESCING_STAGE23"
    const val VISUAL_IDENTITY_MARKER = "VISUAL_SNAPSHOT_FINGERPRINT_BEFORE_EVALUATE_STAGE23"
    const val SCHEDULE_MARKER = "SCHEDULED_DEMAND_BOUND_TO_VISUAL_GENERATION_STAGE23"
    const val OCR_MARKER = "OCR_DEMAND_BOUND_TO_VISUAL_GENERATION_STAGE23"
    const val METRICS_MARKER = "AUTOMATIC_LATENCY_METRICS_STAGE23"
    const val GOOGLE_REAL_PRESERVED_MARKER = "GOOGLE_MAPS_REAL_ROUTE_PRESERVED_STAGE23"
    const val FRESHNESS_PRESERVED_MARKER = "SCREEN_WINDOW_HASH_SIGNATURE_VERIFICATION_FRESHNESS_PRESERVED_STAGE23"
    const val PAINT_TOKEN_PRESERVED_MARKER = "PAINT_TOKEN_FINAL_BINDING_PRESERVED_STAGE23"

    data class VisualSeed(
        val windowId: Int,
        val windowLayer: Int,
        val text: String,
        val left: Int = 0,
        val top: Int = 0,
        val right: Int = 0,
        val bottom: Int = 0,
        val syntheticRoot: Boolean = false,
    )

    data class Snapshot(
        val hash: Long,
        val canonical: String,
        val hasAddressEvidence: Boolean,
        val hasTwoAddressLeads: Boolean,
    )

    data class VisualDecision(
        val process: Boolean,
        val generation: Long,
        val reason: String,
        val hash: Long,
    )

    class VisualSnapshotGate {
        private var generation = 0L
        private var lastObservedHash: Long? = null
        private var lastProcessedHash: Long? = null
        private var lastProcessedGeneration = -1L

        @Synchronized
        fun observe(hash: Long): VisualDecision {
            if (lastObservedHash == hash) {
                return VisualDecision(false, generation, "unchanged_visual_snapshot", hash)
            }
            generation += 1L
            lastObservedHash = hash
            return VisualDecision(true, generation, "new_visual_snapshot", hash)
        }

        @Synchronized
        fun markProcessed(hash: Long, observedGeneration: Long) {
            if (lastObservedHash == hash && observedGeneration == generation) {
                lastProcessedHash = hash
                lastProcessedGeneration = observedGeneration
            }
        }

        @Synchronized
        fun currentGeneration(): Long = generation

        @Synchronized
        fun currentHash(): Long? = lastObservedHash

        @Synchronized
        fun alreadyProcessed(hash: Long, observedGeneration: Long): Boolean =
            lastProcessedHash == hash && lastProcessedGeneration == observedGeneration

        @Synchronized
        fun invalidateForExplicitRecovery(hash: Long) {
            if (lastObservedHash == hash) {
                lastObservedHash = null
                lastProcessedHash = null
                lastProcessedGeneration = -1L
            }
        }
    }

    data class ScheduledDemand(
        val token: Long,
        val visualGeneration: Long,
        val snapshotHash: Long?,
    )

    class ScheduledDemandGate {
        private var tokenSerial = 0L
        private var satisfiedGeneration = -1L
        private var satisfiedHash: Long? = null

        @Synchronized
        fun create(visualGeneration: Long, snapshotHash: Long?): ScheduledDemand =
            ScheduledDemand(++tokenSerial, visualGeneration, snapshotHash)

        @Synchronized
        fun satisfy(visualGeneration: Long, snapshotHash: Long) {
            if (visualGeneration >= satisfiedGeneration) {
                satisfiedGeneration = visualGeneration
                satisfiedHash = snapshotHash
            }
        }

        @Synchronized
        fun satisfyDirect(visualGeneration: Long, snapshotHash: Long) = satisfy(visualGeneration, snapshotHash)

        @Synchronized
        fun shouldRun(demand: ScheduledDemand, currentGeneration: Long, currentHash: Long?): Boolean {
            if (demand.visualGeneration != currentGeneration) return false
            if (demand.snapshotHash != null && currentHash != demand.snapshotHash) return false
            if (satisfiedGeneration > demand.visualGeneration) return false
            if (satisfiedGeneration == demand.visualGeneration && satisfiedHash == currentHash && currentHash != null) return false
            return true
        }
    }

    data class OcrDemand(
        val visualGeneration: Long,
        val snapshotHash: Long,
        val packageHint: String?,
        val cycleId: Long?,
    ) {
        val key: String = "$visualGeneration|$snapshotHash"
    }

    data class OcrDecision(
        val startNow: Boolean,
        val token: Long,
        val reason: String,
        val demand: OcrDemand,
    )

    data class OcrCompletion(
        val rerun: OcrDemand?,
        val activeWasCurrent: Boolean,
    )

    class OcrDemandGate {
        private var tokenSerial = 0L
        private var invalidationSerial = 0L
        private var activeToken: Long? = null
        private var activeDemand: OcrDemand? = null
        private var pendingLatest: OcrDemand? = null

        @Synchronized
        fun request(demand: OcrDemand): OcrDecision {
            val active = activeDemand
            if (activeToken != null && active != null) {
                if (active.key == demand.key) {
                    return OcrDecision(false, activeToken!!, "same_visual_generation_busy", demand)
                }
                pendingLatest = demand
                return OcrDecision(false, activeToken!!, "new_visual_generation_pending", demand)
            }
            val token = ++tokenSerial
            activeToken = token
            activeDemand = demand
            pendingLatest = null
            return OcrDecision(true, token, "start", demand)
        }

        @Synchronized
        fun isCurrent(token: Long, demand: OcrDemand): Boolean =
            activeToken == token && activeDemand?.key == demand.key && invalidationSerial <= token

        @Synchronized
        fun cancelBecauseAccessibilityWon(visualGeneration: Long, snapshotHash: Long) {
            invalidationSerial = maxOf(invalidationSerial, tokenSerial + 1L)
            pendingLatest = pendingLatest?.takeUnless {
                it.visualGeneration <= visualGeneration || it.snapshotHash == snapshotHash
            }
        }

        @Synchronized
        fun complete(token: Long): OcrCompletion {
            val activeWasCurrent = activeToken == token
            if (activeWasCurrent) {
                activeToken = null
                activeDemand = null
            }
            val rerun = pendingLatest
            pendingLatest = null
            return OcrCompletion(rerun, activeWasCurrent)
        }

        @Synchronized
        fun installRerun(demand: OcrDemand): OcrDecision {
            if (activeToken != null) return request(demand)
            val token = ++tokenSerial
            activeToken = token
            activeDemand = demand
            return OcrDecision(true, token, "rerun", demand)
        }
    }

    data class CollectionStats(
        val visibleWindowsTotal: Int,
        val windowsTraversed: Int,
        val windowsSkippedSelf: Int,
        val windowsSkippedLowerLayer: Int,
        val blocksVisited: Int,
        val blocksEmitted: Int,
        val earlyExitWindow: Int?,
        val earlyExitReason: String,
        val visualSnapshotHash: Long,
    )

    fun snapshot(seeds: Sequence<VisualSeed>): Snapshot {
        val canonicalParts = ArrayList<String>(96)
        var addressLeads = 0
        seeds.take(96).forEach { seed ->
            if (seed.syntheticRoot) return@forEach
            val text = canonicalText(seed.text)
            if (text.isBlank()) return@forEach
            val compact = text.take(360)
            canonicalParts += listOf(
                seed.windowLayer.toString(), seed.windowId.toString(),
                seed.left.toString(), seed.top.toString(), seed.right.toString(), seed.bottom.toString(), compact,
            ).joinToString(":")
            addressLeads += countAddressLeads(seed.text).coerceAtMost(2)
        }
        val canonical = canonicalParts.joinToString("|")
        return Snapshot(
            hash = stableHash64(canonical),
            canonical = canonical,
            hasAddressEvidence = addressLeads > 0,
            hasTwoAddressLeads = addressLeads >= 2,
        )
    }

    fun countAddressLeads(text: String): Int = addressLeadRegex.findAll(normalizeWhitespace(text)).take(3).count()

    fun hasTwoAddressLeads(texts: Sequence<String>): Boolean {
        var count = 0
        texts.take(64).forEach { text ->
            count += countAddressLeads(text).coerceAtMost(2)
            if (count >= 2) return true
        }
        return false
    }

    fun shouldStopInsideWindow(texts: Sequence<String>, blocksVisited: Int, blocksEmitted: Int): Boolean =
        blocksVisited >= 4 && blocksEmitted >= 1 && hasTwoAddressLeads(texts)


    private fun stableHash64(value: String): Long {
        var hash = -3750763034362895579L
        value.forEach { ch ->
            hash = hash xor ch.code.toLong()
            hash *= 1099511628211L
        }
        return hash
    }

    private fun normalizeWhitespace(value: String): String = value.replace(Regex("\\s+"), " ").trim()

    private fun canonicalText(value: String): String = Normalizer
        .normalize(normalizeWhitespace(value).lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private val addressLeadRegex = Regex(
        "(?:^|[\\s:(])(?:r\\.|av\\.|rua|avenida|alameda|travessa|estrada|rodovia|praca|praça|largo|via|viela|beco|marginal|passagem|servidao|servidão|shopping|terminal|estacao|estação|aeroporto|rodoviaria|rodoviária|hospital|mercado|restaurante|hotel|pousada|escola|faculdade|universidade|posto|parque|condominio|condomínio|residencial)(?:\\b|(?=\\s))",
        RegexOption.IGNORE_CASE,
    )

    object Metrics {
        private const val MAX_SAMPLES = 512
        private val lock = Any()
        private val samples = LinkedHashMap<String, ArrayDeque<Long>>()
        private val counters = LinkedHashMap<String, Long>()
        private val sums = LinkedHashMap<String, Long>()

        fun resetForTests() = synchronized(lock) {
            samples.clear(); counters.clear(); sums.clear()
        }

        fun increment(name: String, amount: Long = 1L) = synchronized(lock) {
            counters[name] = (counters[name] ?: 0L) + amount
        }

        fun addTotal(name: String, amount: Long) = synchronized(lock) {
            sums[name] = (sums[name] ?: 0L) + amount
        }

        fun sample(name: String, durationNs: Long) = synchronized(lock) {
            val bucket = samples.getOrPut(name) { ArrayDeque() }
            if (bucket.size >= MAX_SAMPLES) bucket.removeFirst()
            bucket.addLast(durationNs.coerceAtLeast(0L))
        }

        fun recordCollection(source: String, durationNs: Long, stats: CollectionStats, snapshotNew: Boolean) {
            sample("collect.$source", durationNs)
            increment(if (snapshotNew) "visualSnapshotNew" else "visualSnapshotRepeated")
            addTotal("visibleWindowsTotal", stats.visibleWindowsTotal.toLong())
            addTotal("windowsTraversed", stats.windowsTraversed.toLong())
            addTotal("windowsSkippedSelf", stats.windowsSkippedSelf.toLong())
            addTotal("windowsSkippedLowerLayer", stats.windowsSkippedLowerLayer.toLong())
            addTotal("blocksVisited", stats.blocksVisited.toLong())
            addTotal("blocksEmitted", stats.blocksEmitted.toLong())
        }

        fun recordEvaluate(source: String, candidate: Boolean, durationNs: Long) {
            sample("evaluate.$source", durationNs)
            sample("evaluate.$source.candidate=$candidate", durationNs)
            increment("evaluate.$source.candidate=$candidate")
        }

        fun recordEventToCandidate(source: String, durationNs: Long) = sample("eventToCandidate.$source", durationNs)

        fun counter(name: String): Long = synchronized(lock) { counters[name] ?: 0L }
        fun total(name: String): Long = synchronized(lock) { sums[name] ?: 0L }

        fun stats(name: String): String = synchronized(lock) {
            val values = samples[name]?.toList()?.sorted().orEmpty()
            if (values.isEmpty()) return@synchronized "count=0; median_us=-1; p95_us=-1; max_us=-1"
            fun percentile(p: Double): Long {
                val index = (ceil(values.size * p).toInt() - 1).coerceIn(0, values.lastIndex)
                return values[index]
            }
            val median = percentile(0.50) / 1_000L
            val p95 = percentile(0.95) / 1_000L
            val max = values.last() / 1_000L
            "count=${values.size}; median_us=$median; p95_us=$p95; max_us=$max"
        }

        fun exportReport(): String = synchronized(lock) {
            buildString {
                appendLine("ROTA CERTA — STAGE23 VISUAL IDENTITY METRICS")
                appendLine("marker=$METRICS_MARKER")
                for (name in listOf(
                    "collect.Accessibility", "collect.AccessibilityScheduled",
                    "evaluate.Accessibility", "evaluate.AccessibilityScheduled",
                    "evaluate.Accessibility.candidate=true", "evaluate.Accessibility.candidate=false",
                    "evaluate.AccessibilityScheduled.candidate=true", "evaluate.AccessibilityScheduled.candidate=false",
                    "eventToCandidate.Accessibility", "eventToCandidate.AccessibilityScheduled",
                )) appendLine("$name | ${stats(name)}")
                for (name in listOf(
                    "visualSnapshotNew", "visualSnapshotRepeated", "unchangedVisualSkipped", "scheduledCancelled",
                    "selfEventSkipped", "eventCoalesced", "ocrRequests", "ocrStarts", "ocrDeferred", "ocrReruns",
                    "ocrStaleBeforeBitmap", "ocrStaleBeforeExtract", "ocrStaleAfterExtract", "ocrStaleAfterEvaluate",
                )) appendLine("$name=${counters[name] ?: 0L}")
                for (name in listOf(
                    "visibleWindowsTotal", "windowsTraversed", "windowsSkippedSelf", "windowsSkippedLowerLayer",
                    "blocksVisited", "blocksEmitted",
                )) appendLine("$name=${sums[name] ?: 0L}")
            }.trimEnd()
        }
    }
}
