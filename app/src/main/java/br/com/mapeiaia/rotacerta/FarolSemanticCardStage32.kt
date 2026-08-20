package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.ceil

/**
 * Stage32 separates semantic card identity from raw Accessibility churn.
 * Package identity is provenance/diagnostic metadata only and never authorizes visible content.
 */
object FarolSemanticCardStage32 {
    const val CONTRACT_MARKER = "FAROL_SEMANTIC_CARD_GENERATION_STAGE32"
    const val OCR_IDENTITY_MARKER = "FAROL_OCR_IDENTITY_PRESERVATION_STAGE32"
    const val GLOBAL_LAST_RESORT_MARKER = "FAROL_GLOBAL_COLLECTION_LAST_RESORT_STAGE32"
    const val GOOGLE_MARKER = "FAROL_REAL_GOOGLE_PRESERVED_STAGE32"
    const val NO_POLLING_MARKER = "NO_POLLING_NO_SLEEP_NO_ARTIFICIAL_DEBOUNCE_STAGE32"
    const val SCREENSHOT_RATE_MARKER = "ANDROID_SCREENSHOT_333MS_EVENT_DRIVEN_GATE_STAGE32"
    const val ANDROID_SCREENSHOT_MIN_INTERVAL_MS = 333L

    data class Signal(
        val triggerPackage: String?,
        val sourcePackage: String?,
        val windowPackage: String?,
        val windowId: Int,
        val sourceSlot: String,
        val sourceText: String,
        val eventType: Int,
    )

    data class Snapshot(
        val generation: Long,
        val fingerprint: Long,
        val ownerPackage: String?,
        val windowId: Int,
        val sourceSlot: String,
        val sourceText: String,
    )

    data class Decision(
        val mutation: Boolean,
        val strongMutation: Boolean,
        val generation: Long,
        val fingerprint: Long,
        val reason: String,
        val snapshot: Snapshot,
    )

    data class Lease(val generation: Long, val fingerprint: Long)

    /**
     * A blank/no-address event on the same window is not proof that the card changed.
     * Broad bounded source text, a window transition, a source-slot clear, or an exact candidate
     * signature are proof. This preserves a useful OCR through harmless 99/Uber event churn.
     */
    class SemanticGate {
        private val cardLease = FarolCardLeaseStage34.Authority()
        private var owner: String? = null
        private var windowId = Int.MIN_VALUE
        private var slot = ""
        private var lastText = ""
        private var offGeneration = 0L
        private var lastSnapshot = Snapshot(0L, stableHash64("stage34-empty"), null, Int.MIN_VALUE, "", "")

        @Synchronized fun observe(signal: Signal): Decision {
            owner = firstNonBlank(normalizePackage(signal.sourcePackage), normalizePackage(signal.triggerPackage), normalizePackage(signal.windowPackage), owner)
            if (signal.windowId >= 0) windowId = signal.windowId
            slot = canonical(signal.sourceSlot).ifBlank { "provenance" }
            val text = canonicalSemanticSource(signal.sourceText).take(1800)
            if (text.isNotBlank()) lastText = text
            val lease = cardLease.observeRawEvent()
            val generation = maxOf(lease.leaseId, offGeneration)
            val fingerprint = if (lease.candidateBound) lease.identityHash else stableHash64("stage34-acquiring:$generation")
            lastSnapshot = Snapshot(generation, fingerprint, owner, windowId, slot, text)
            Metrics.increment("rawEventsPreserved")
            Metrics.increment("stage34PackageWindowProvenanceOnly")
            return Decision(false, false, generation, fingerprint, "stage34_raw_event_provenance_only", lastSnapshot)
        }

        @Synchronized fun observeCandidate(addressSignature: String): Decision {
            if (addressSignature.isBlank()) return Decision(false,false,lastSnapshot.generation,lastSnapshot.fingerprint,"blank_candidate_signature",snapshotLocked())
            val decision=cardLease.bindCandidate(addressSignature)
            val generation=maxOf(decision.snapshot.leaseId,offGeneration)
            val fingerprint=decision.snapshot.identityHash
            lastSnapshot=Snapshot(generation,fingerprint,owner,windowId,"candidate",decision.snapshot.destinationKey.orEmpty())
            if (decision.leaseTransition) {
                Metrics.increment("candidateSemanticMutations"); Metrics.increment("stage34RealDestinationTransitions")
            } else Metrics.increment("stage34SameDestinationPreserved")
            return Decision(decision.leaseTransition,decision.leaseTransition,generation,fingerprint,decision.reason,lastSnapshot)
        }

        @Synchronized fun markReadingOff(): Snapshot {
            val old=cardLease.markReadingOff()
            offGeneration=maxOf(offGeneration+1L,(old?.leaseId?:0L)+1L)
            owner=null; windowId=Int.MIN_VALUE; slot=""; lastText=""
            lastSnapshot=Snapshot(offGeneration,stableHash64("stage34-off:$offGeneration"),null,Int.MIN_VALUE,"","")
            Metrics.increment("semanticResetReadingOff"); Metrics.increment("stage34ReadingOffLeaseInvalidated")
            return lastSnapshot
        }

        @Synchronized fun snapshot(): Snapshot = snapshotLocked()
        @Synchronized fun lease(): Lease = Lease(lastSnapshot.generation,lastSnapshot.fingerprint)
        @Synchronized fun isFresh(lease: Lease): Boolean { val c=snapshotLocked(); return lease.generation==c.generation && lease.fingerprint==c.fingerprint }
        private fun snapshotLocked(): Snapshot {
            cardLease.snapshot()?.let { x ->
                val g=maxOf(x.leaseId,offGeneration); val f=if(x.candidateBound)x.identityHash else stableHash64("stage34-acquiring:$g")
                lastSnapshot=lastSnapshot.copy(generation=g,fingerprint=f)
            }
            return lastSnapshot
        }
    }

    enum class ScreenshotDecisionKind { START, COALESCE_BUSY, RATE_LIMITED_PENDING, DUPLICATE_COMPLETED }

    data class ScreenshotDecision(
        val kind: ScreenshotDecisionKind,
        val semanticGeneration: Long,
        val eligibleAtUptimeMs: Long,
        val reason: String,
    ) {
        val startNow: Boolean get() = kind == ScreenshotDecisionKind.START
    }

    /** Android enforces 333 ms between Accessibility screenshots. No timer is introduced here. */
    class ScreenshotRateGate {
        private var lastRequestUptimeMs = Long.MIN_VALUE
        private var activeGeneration: Long? = null
        private var completedGeneration: Long? = null
        private var pendingGeneration: Long? = null

        @Synchronized
        fun request(nowUptimeMs: Long, semanticGeneration: Long): ScreenshotDecision = requestInternal(nowUptimeMs, semanticGeneration, dedupeCompleted = true)

        @Synchronized
        fun requestExplicit(nowUptimeMs: Long, semanticGeneration: Long): ScreenshotDecision = requestInternal(nowUptimeMs, semanticGeneration, dedupeCompleted = false)

        private fun requestInternal(nowUptimeMs: Long, semanticGeneration: Long, dedupeCompleted: Boolean): ScreenshotDecision {
            if (activeGeneration != null) {
                if (activeGeneration != semanticGeneration) pendingGeneration = semanticGeneration
                Metrics.increment("screenshotCoalescedBusy")
                return ScreenshotDecision(ScreenshotDecisionKind.COALESCE_BUSY, semanticGeneration, eligibleAt(), "single_flight_busy")
            }
            if (dedupeCompleted && completedGeneration == semanticGeneration && pendingGeneration == null) {
                Metrics.increment("stage34CompletedLeaseReacquireEligible")
            }
            val elapsed = if (lastRequestUptimeMs == Long.MIN_VALUE) Long.MAX_VALUE else nowUptimeMs - lastRequestUptimeMs
            if (elapsed <= ANDROID_SCREENSHOT_MIN_INTERVAL_MS) {
                pendingGeneration = semanticGeneration
                Metrics.increment("screenshotPlatformRateLimitedAvoided")
                return ScreenshotDecision(
                    ScreenshotDecisionKind.RATE_LIMITED_PENDING,
                    semanticGeneration,
                    lastRequestUptimeMs + ANDROID_SCREENSHOT_MIN_INTERVAL_MS + 1L,
                    "android_333ms_interval_not_elapsed",
                )
            }
            lastRequestUptimeMs = nowUptimeMs
            activeGeneration = semanticGeneration
            pendingGeneration = null
            Metrics.increment("screenshotStarts")
            return ScreenshotDecision(ScreenshotDecisionKind.START, semanticGeneration, nowUptimeMs, "start")
        }

        @Synchronized
        fun complete(semanticGeneration: Long, successful: Boolean) {
            if (activeGeneration == semanticGeneration) activeGeneration = null
            if (successful) completedGeneration = semanticGeneration
            Metrics.increment(if (successful) "screenshotSuccess" else "screenshotFailure")
        }

        @Synchronized
        fun markIntervalShort(semanticGeneration: Long) {
            if (activeGeneration == semanticGeneration) activeGeneration = null
            pendingGeneration = semanticGeneration
            Metrics.increment("screenshotError3")
        }

        @Synchronized
        fun pendingEligible(nowUptimeMs: Long, currentSemanticGeneration: Long): Boolean {
            val pending = pendingGeneration ?: return false
            if (pending != currentSemanticGeneration) {
                pendingGeneration = null
                return false
            }
            return activeGeneration == null && nowUptimeMs > lastRequestUptimeMs + ANDROID_SCREENSHOT_MIN_INTERVAL_MS
        }

        @Synchronized fun queue(semanticGeneration: Long) {
            pendingGeneration = semanticGeneration
            Metrics.increment("screenshotQueuedForNextEvent")
        }
        @Synchronized fun hasPending(): Boolean = pendingGeneration != null
        @Synchronized fun activeGeneration(): Long? = activeGeneration
        @Synchronized fun lastRequestUptimeMs(): Long = lastRequestUptimeMs
        @Synchronized fun reset() {
            activeGeneration = null
            pendingGeneration = null
            completedGeneration = null
            lastRequestUptimeMs = Long.MIN_VALUE
        }
        private fun eligibleAt(): Long = if (lastRequestUptimeMs == Long.MIN_VALUE) 0L else lastRequestUptimeMs + ANDROID_SCREENSHOT_MIN_INTERVAL_MS + 1L
    }

    enum class OwnerConfidence { DIRECT, DERIVED, UNKNOWN }
    data class Provenance(val ownerPackage: String?, val confidence: OwnerConfidence, val evidence: String)

    fun resolveProvenance(
        triggerPackage: String?,
        sourcePackage: String?,
        windowPackage: String?,
        selectedPackages: Set<String>,
    ): Provenance {
        val selected = selectedPackages.mapNotNull(::normalizePackage).toSet()
        val trigger = normalizePackage(triggerPackage)
        val source = normalizePackage(sourcePackage)
        val window = normalizePackage(windowPackage)
        val votes = listOfNotNull(trigger, source, window).filter { it in selected }
        val grouped = votes.groupingBy { it }.eachCount()
        val direct = grouped.entries.firstOrNull { it.value >= 2 }?.key
        if (direct != null) return Provenance(direct, OwnerConfidence.DIRECT, "matching_selected_trigger_source_or_window")
        val single = votes.distinct()
        if (single.size == 1 && (trigger == single.first() || source == single.first())) {
            return Provenance(single.first(), OwnerConfidence.DERIVED, "single_selected_direct_event_evidence")
        }
        return Provenance(null, OwnerConfidence.UNKNOWN, "insufficient_or_conflicting_evidence")
    }

    fun stableFingerprint(vararg parts: String?): Long = stableHash64(parts.joinToString("|") { canonical(it.orEmpty()) })

    private fun computeFingerprint(owner: String?, windowId: Int, candidate: String?, slots: Map<String, String>): Long =
        stableHash64(buildString {
            append(owner.orEmpty()); append('|'); append(windowId); append('|'); append(candidate.orEmpty())
            slots.toSortedMap().forEach { (k, v) -> append('|'); append(k); append('='); append(v) }
        })

    private fun firstNonBlank(vararg values: String?): String? = values.firstOrNull { !it.isNullOrBlank() }
    private fun normalizePackage(value: String?): String? = value?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotBlank)
    private fun canonical(value: String): String = Normalizer.normalize(
        value.replace(Regex("\\s+"), " ").trim().lowercase(Locale.ROOT),
        Normalizer.Form.NFD,
    ).replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ").trim()

    private fun canonicalSemanticSource(value: String): String {
        val stable = value
            .replace(Regex("(?iu)r\\$\\s*\\d+(?:[.,]\\d{1,2})?"), " valor ")
            .replace(Regex("(?iu)\\b\\d{1,3}\\s*(?:s|seg|segs|segundos|min|mins|minutos)\\b"), " tempo ")
            .replace(Regex("\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\b"), " horario ")
            .replace(Regex("\\b\\d{1,3}%\\b"), " percentual ")
        return canonical(stable)
    }

    private fun stableHash64(value: String): Long {
        var hash = -3750763034362895579L
        value.forEach { hash = (hash xor it.code.toLong()) * 1099511628211L }
        return hash
    }

    object Metrics {
        private const val MAX_SAMPLES = 256
        private val lock = Any()
        private val counters = LinkedHashMap<String, Long>()
        private val samples = LinkedHashMap<String, ArrayDeque<Long>>()
        fun resetForTests() = synchronized(lock) { counters.clear(); samples.clear() }
        fun increment(name: String, amount: Long = 1L) = synchronized(lock) { counters[name] = (counters[name] ?: 0L) + amount }
        fun counter(name: String): Long = synchronized(lock) { counters[name] ?: 0L }
        fun sample(name: String, ns: Long) = synchronized(lock) {
            val q = samples.getOrPut(name) { ArrayDeque() }
            if (q.size >= MAX_SAMPLES) q.removeFirst()
            q.addLast(ns.coerceAtLeast(0L))
        }
        private fun stats(name: String): String {
            val values = samples[name]?.toList()?.sorted().orEmpty()
            if (values.isEmpty()) return "count=0; median_us=-1; p95_us=-1; max_us=-1"
            fun p(x: Double) = values[(ceil(values.size * x).toInt() - 1).coerceIn(0, values.lastIndex)]
            return "count=${values.size}; median_us=${p(.5)/1000}; p95_us=${p(.95)/1000}; max_us=${values.last()/1000}"
        }
        fun exportReport(): String = synchronized(lock) {
            buildString {
                appendLine("ROTA CERTA — STAGE32 SEMANTIC / OCR RATE METRICS")
                appendLine("marker=$CONTRACT_MARKER")
                appendLine("ocrIdentity=$OCR_IDENTITY_MARKER")
                appendLine("globalCollection=$GLOBAL_LAST_RESORT_MARKER")
                appendLine("screenshotRate=$SCREENSHOT_RATE_MARKER")
                listOf(
                    "semanticMutations","candidateSemanticMutations","rawEventsPreserved","semanticResetReadingOff",
                    "screenshotStarts","screenshotSuccess","screenshotFailure","screenshotError3",
                    "screenshotCoalescedBusy","screenshotPlatformRateLimitedAvoided","screenshotDuplicateCompleted","screenshotQueuedForNextEvent",
                    "sourceFastPath","activeWindowFallback","globalFallback","globalFallbackAvoided","ocrPreservedAcrossRawMutation",
                ).forEach { appendLine("$it=${counters[it] ?: 0L}") }
                appendLine("eventToSemantic | ${stats("eventToSemantic")}")
                appendLine("sourceCollect | ${stats("sourceCollect")}")
            }.trimEnd()
        }
    }
}
