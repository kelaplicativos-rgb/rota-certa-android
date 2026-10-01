package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

/** Stage34: final destination owns one logical lease; package/window are diagnostic provenance only. */
object FarolCardLeaseStage34 {
    const val CONTRACT_MARKER = "FAROL_SINGLE_CARD_LEASE_STAGE34"
    const val FRESHNESS_MARKER = "FAROL_SINGLE_FRESHNESS_AUTHORITY_STAGE34"
    const val PROVENANCE_ONLY_MARKER = "PACKAGE_WINDOW_PROVENANCE_ONLY_STAGE34"
    const val LATEST_FRAME_MARKER = "LATEST_FRAME_SAME_CARD_LEASE_STAGE34"
    const val GOOGLE_MARKER = "REAL_GOOGLE_ROUTE_PRESERVED_STAGE34"
    const val NO_TIMER_MARKER = "NO_POLLING_NO_SLEEP_NO_TIMER_STAGE34"

    data class Snapshot(
        val leaseId: Long,
        val rawRevision: Long,
        val destinationKey: String?,
        val lastCandidateRawRevision: Long,
    ) {
        val candidateBound: Boolean get() = !destinationKey.isNullOrBlank()
        val identityHash: Long get() = stableHash64("lease=$leaseId|dest=${destinationKey.orEmpty()}")
    }

    data class CandidateDecision(
        val snapshot: Snapshot,
        val firstBind: Boolean,
        val leaseTransition: Boolean,
        val sameDestination: Boolean,
        val reason: String,
    )

    private data class MutableLease(
        val leaseId: Long,
        var rawRevision: Long = 0L,
        var destinationKey: String? = null,
        var lastCandidateRawRevision: Long = -1L,
    )

    class Authority {
        private var serial = 0L
        private var current: MutableLease? = null

        @Synchronized fun observeRawEvent(): Snapshot {
            val lease = current ?: newLeaseLocked().also { Metrics.increment("leasesOpened") }
            lease.rawRevision += 1L
            Metrics.increment("rawEventsPreserved")
            return snapshotLocked(lease).also(Metrics::updateLease)
        }

        @Synchronized fun bindCandidate(addressSignature: String): CandidateDecision {
            var lease = current ?: newLeaseLocked().also { Metrics.increment("leasesOpened") }
            val destinationKey = destinationFromAddressSignature(addressSignature)
            require(destinationKey.isNotBlank()) { "Stage34 destination cannot be blank" }
            val old = lease.destinationKey
            val first = old.isNullOrBlank()
            val same = !first && old == destinationKey
            var transition = false
            val reason = when {
                first -> {
                    lease.destinationKey = destinationKey
                    lease.lastCandidateRawRevision = lease.rawRevision
                    Metrics.increment("candidateFirstBinds")
                    "candidate_bound_to_current_lease"
                }
                same -> {
                    lease.lastCandidateRawRevision = lease.rawRevision
                    Metrics.increment("candidateConfirms")
                    "same_destination_confirms_current_lease"
                }
                else -> {
                    val revision = lease.rawRevision
                    lease = newLeaseLocked().also { it.rawRevision = revision; it.destinationKey = destinationKey; it.lastCandidateRawRevision = revision }
                    transition = true
                    Metrics.increment("candidateTransitions")
                    "different_destination_opens_new_lease"
                }
            }
            val snapshot = snapshotLocked(lease).also(Metrics::updateLease)
            return CandidateDecision(snapshot, first, transition, same, reason)
        }

        @Synchronized fun markReadingOff(): Snapshot? {
            val old = current ?: return null
            current = null
            Metrics.increment("readingOffInvalidations")
            Metrics.updateLease(null)
            return snapshotLocked(old)
        }

        @Synchronized fun snapshot(): Snapshot? = current?.let(::snapshotLocked)
        @Synchronized fun resetForTests() { serial = 0L; current = null; Metrics.updateLease(null) }
        private fun newLeaseLocked(): MutableLease = MutableLease(++serial).also { current = it }
        private fun snapshotLocked(x: MutableLease) = Snapshot(x.leaseId, x.rawRevision, x.destinationKey, x.lastCandidateRawRevision)
    }

    fun destinationFromAddressSignature(signature: String): String {
        val raw = signature.split('|').map(String::trim).filter(String::isNotBlank).lastOrNull() ?: signature.trim()
        return canonicalDestination(raw)
    }

    fun canonicalDestination(value: String): String {
        var out = canonical(value.replace(Regex("(?iu)\\b(?:brasil|brazil)\\b"), " "))
        val prefixes = listOf(
            Regex("^av\\s+") to "avenida ", Regex("^avda\\s+") to "avenida ",
            Regex("^r\\s+") to "rua ", Regex("^estr\\s+") to "estrada ", Regex("^rod\\s+") to "rodovia ",
            Regex("^trav\\s+") to "travessa ", Regex("^al\\s+") to "alameda ", Regex("^pc\\s+") to "praca ",
        )
        prefixes.forEach { (p,r) -> out = out.replace(p,r) }
        return out
    }

    private fun canonical(value: String): String = Normalizer.normalize(
        value.replace(Regex("\\s+"), " ").trim().lowercase(Locale.ROOT), Normalizer.Form.NFD,
    ).replace(Regex("\\p{Mn}+"), "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").replace(Regex("\\s+"), " ").trim()

    private fun stableHash64(value: String): Long {
        var hash = -3750763034362895579L
        value.forEach { hash = (hash xor it.code.toLong()) * 1099511628211L }
        return hash
    }

    object Metrics {
        private val lock = Any()
        private val counters = LinkedHashMap<String,Long>()
        private var lastLease: Snapshot? = null
        fun resetForTests() = synchronized(lock) { counters.clear(); lastLease=null }
        fun increment(name:String) = synchronized(lock) { counters[name]=(counters[name]?:0L)+1L }
        fun counter(name:String):Long = synchronized(lock) { counters[name]?:0L }
        fun updateLease(x:Snapshot?) = synchronized(lock) { lastLease=x }
        fun exportReport():String = synchronized(lock) {
            val x=lastLease
            buildString {
                appendLine("ROTA CERTA — STAGE34 CARD LEASE CORE")
                appendLine("marker=$CONTRACT_MARKER")
                appendLine("freshness=$FRESHNESS_MARKER")
                appendLine("provenance=$PROVENANCE_ONLY_MARKER")
                appendLine("latestFrame=$LATEST_FRAME_MARKER")
                appendLine("lease=${x?.leaseId ?: -1L}; rawRevision=${x?.rawRevision ?: -1L}; candidateBound=${x?.candidateBound ?: false}; destination=${x?.destinationKey ?: "none"}")
                listOf("leasesOpened","rawEventsPreserved","candidateFirstBinds","candidateConfirms","candidateTransitions","readingOffInvalidations").forEach { appendLine("$it=${counters[it]?:0L}") }
            }.trimEnd()
        }
    }
}
