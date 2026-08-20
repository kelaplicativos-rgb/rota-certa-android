package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

/**
 * Stage36: one end-to-end runtime authority for the FAROL.
 *
 * A selected ride app is only the trigger that arms reading. Visual package/window identity is
 * provenance only. Once armed, transient Home/SystemUI/launcher/WhatsApp window boundaries and
 * ACTIVITY_PAUSED/ACTIVITY_STOPPED alone cannot turn reading off. A terminal OFF needs explicit
 * configuration/access loss or paired current-session activity + foreground-service termination.
 *
 * OCR, route and final paint bind to the same reading epoch + logical card lease. Raw visual,
 * package, window and legacy serial/generation churn are never freshness authority.
 */
object FarolRuntimeAuthorityStage36 {
    const val CONTRACT_MARKER = "FAROL_SINGLE_RUNTIME_AUTHORITY_STAGE36"
    const val ACTIVATION_MARKER = "SELECTED_APP_ARMS_READING_SESSION_STAGE36"
    const val WINDOW_MARKER = "WINDOW_PACKAGE_PROVENANCE_ONLY_STAGE36"
    const val FRESHNESS_MARKER = "READING_EPOCH_CARD_LEASE_ONLY_FRESHNESS_STAGE36"
    const val OCR_MARKER = "OCR_SURVIVES_RAW_WINDOW_SERIAL_CHURN_STAGE36"
    const val ROUTE_MARKER = "ROUTE_SURVIVES_RAW_WINDOW_CHURN_STAGE36"
    const val PAINT_MARKER = "PAINT_REQUIRES_CURRENT_LEASE_STAGE36"
    const val GOOGLE_MARKER = "REAL_GOOGLE_DRIVING_ROUTE_STAGE36"
    const val NO_TIMER_MARKER = "NO_TIMER_NO_SLEEP_NO_POLLING_STAGE36"
    const val STAGE40_PRESENCE_MARKER = "CURRENT_WINDOW_OR_RESUMED_ACTIVITY_PRESENCE_STAGE40"

    data class Snapshot(
        val enabled: Boolean,
        val usageAccessGranted: Boolean,
        val selectedPackages: Set<String>,
        val authoritativeActivePackages: Set<String>,
        val readingEpoch: Long,
        val leaseId: Long,
        val destinationKey: String?,
        val reason: String,
    )

    data class WorkToken(
        val readingEpoch: Long,
        val leaseId: Long,
        val destinationKey: String?,
    )

    class Authority(private val sessionStartWallMillis: Long) {
        private var selected = emptySet<String>()
        private var usageAccessGranted = false
        private val armed = LinkedHashSet<String>()
        private val resumed = LinkedHashSet<String>()
        private val foregroundServices = LinkedHashSet<String>()
        private val seenForegroundServicePositive = LinkedHashSet<String>()
        private val activityStoppedAfterPositive = LinkedHashSet<String>()
        private val foregroundServiceStoppedAfterPositive = LinkedHashSet<String>()
        private var enabled = false
        private var readingEpoch = 0L
        private var leaseSerial = 0L
        private var leaseId = 0L
        private var destinationKey: String? = null
        private var reason = "initial"

        @Synchronized
        fun updateSelection(packages: Set<String>): Snapshot {
            selected = packages.mapNotNull(::normalizePackage).toSet()
            armed.retainAll(selected)
            resumed.retainAll(selected)
            foregroundServices.retainAll(selected)
            seenForegroundServicePositive.retainAll(selected)
            activityStoppedAfterPositive.retainAll(selected)
            foregroundServiceStoppedAfterPositive.retainAll(selected)
            if (selected.isEmpty()) hardOffLocked("selection_empty")
            else if (enabled && armed.isEmpty()) hardOffLocked("selected_session_removed")
            return snapshotLocked()
        }

        @Synchronized
        fun setUsageAccess(granted: Boolean): Snapshot {
            usageAccessGranted = granted
            if (!granted) hardOffLocked("usage_access_revoked")
            return snapshotLocked()
        }

        /** Stage42 functional authority: explicit user ON/OFF, no package-presence prerequisite. */
        @Synchronized
        fun setManualAuthority(enabledNow: Boolean): Snapshot {
            selected = emptySet()
            armed.clear()
            resumed.clear()
            foregroundServices.clear()
            seenForegroundServicePositive.clear()
            activityStoppedAfterPositive.clear()
            foregroundServiceStoppedAfterPositive.clear()
            usageAccessGranted = enabledNow
            if (enabledNow) {
                if (!enabled) {
                    enabled = true
                    readingEpoch += 1L
                    Metrics.increment("activationOn")
                }
                reason = "stage42_manual_user_on"
                Metrics.increment("stage42ManualOnRefresh")
            } else {
                hardOffLocked("stage42_manual_user_off")
                Metrics.increment("stage42ManualOffRefresh")
            }
            return snapshotLocked()
        }

        /** Direct selected Accessibility evidence arms reading immediately. */
        @Synchronized
        fun observeAccessibility(packageName: String?): Snapshot {
            val pkg = normalizePackage(packageName)
            if (usageAccessGranted && pkg != null && pkg in selected) {
                markPositiveLocked(pkg, foreground = false)
                ensureOnLocked("selected_accessibility")
                Metrics.increment("directSelectedActivations")
            }
            return snapshotLocked()
        }

        /** Window/package transitions are provenance only and can never turn reading off. */
        @Synchronized
        fun observeWindowBoundary(packageName: String?): Snapshot {
            val pkg = normalizePackage(packageName)
            Metrics.increment("windowBoundariesObserved")
            if (pkg == null || pkg !in selected) Metrics.increment("nonSelectedWindowBoundariesPreserved")
            return snapshotLocked()
        }

        /**
         * Current-session UsageEvents strengthen/terminate the armed session. PAUSED and STOPPED
         * without a matching observed foreground-service lifecycle are deliberately insufficient
         * to prove true OFF: Android can emit them while the ride app popup/service remains useful.
         */
        @Synchronized
        fun applyUsageEvidence(events: List<FarolPresenceAuthorityStage30.UsageEvidence>): Snapshot {
            for (event in events.sortedBy { it.timestampMillis }) {
                val pkg = normalizePackage(event.packageName) ?: continue
                if (pkg !in selected) continue
                if (event.timestampMillis < sessionStartWallMillis) {
                    Metrics.increment("usageBeforeSessionIgnored")
                    continue
                }
                when (event.signal) {
                    FarolPresenceAuthorityStage30.UsageSignal.ACTIVITY_RESUMED -> {
                        resumed += pkg
                        markPositiveLocked(pkg, foreground = false)
                        ensureOnLocked("usage_activity_resumed")
                    }
                    FarolPresenceAuthorityStage30.UsageSignal.ACTIVITY_PAUSED -> {
                        resumed -= pkg
                        Metrics.increment("activityPausedPreserved")
                    }
                    FarolPresenceAuthorityStage30.UsageSignal.ACTIVITY_STOPPED -> {
                        resumed -= pkg
                        if (pkg in armed) activityStoppedAfterPositive += pkg
                        Metrics.increment("activityStoppedObserved")
                    }
                    FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_START -> {
                        foregroundServices += pkg
                        seenForegroundServicePositive += pkg
                        markPositiveLocked(pkg, foreground = true)
                        ensureOnLocked("usage_foreground_service_start")
                    }
                    FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_STOP -> {
                        foregroundServices -= pkg
                        if (pkg in armed) foregroundServiceStoppedAfterPositive += pkg
                        Metrics.increment("foregroundServiceStoppedObserved")
                    }
                }
            }
            maybeTerminalOffLocked()
            return snapshotLocked()
        }

        /** Raw visual evidence opens/keeps one acquiring lease, never a new one by itself. */
        @Synchronized
        fun observeVisualEvidence(): Snapshot {
            if (!enabled) return snapshotLocked()
            ensureLeaseLocked()
            Metrics.increment("rawVisualPreserved")
            return snapshotLocked()
        }

        /** Final address is the logical card identity. First bind stays on the acquiring lease. */
        @Synchronized
        fun bindDestination(addressSignature: String): Snapshot {
            if (!enabled) return snapshotLocked()
            ensureLeaseLocked()
            val next = destinationFromAddressSignature(addressSignature)
            if (next.isBlank()) return snapshotLocked()
            val old = destinationKey
            when {
                old.isNullOrBlank() -> {
                    destinationKey = next
                    Metrics.increment("candidateFirstBinds")
                }
                old == next -> Metrics.increment("candidateConfirms")
                else -> {
                    leaseId = ++leaseSerial
                    destinationKey = next
                    Metrics.increment("destinationTransitions")
                }
            }
            return snapshotLocked()
        }

        /** Explicit visual disappearance invalidates only the card lease; reading may remain armed. */
        @Synchronized
        fun clearVisualLease(reasonText: String): Snapshot {
            if (leaseId != 0L || destinationKey != null) Metrics.increment("visualLeaseClears")
            leaseId = 0L
            destinationKey = null
            reason = "visual_clear:${reasonText.take(80)}"
            return snapshotLocked()
        }

        /** Token shared by OCR, route and paint. */
        @Synchronized
        fun captureWorkToken(): WorkToken? {
            if (!enabled) return null
            ensureLeaseLocked()
            return WorkToken(readingEpoch, leaseId, destinationKey)
        }

        @Synchronized
        fun captureDestinationToken(addressSignature: String): WorkToken? {
            if (!enabled) return null
            bindDestination(addressSignature)
            return WorkToken(readingEpoch, leaseId, destinationKey)
        }

        @Synchronized
        fun isFresh(token: WorkToken?): Boolean {
            if (token == null || !enabled) return false
            if (token.readingEpoch != readingEpoch || token.leaseId != leaseId) return false
            val expected = token.destinationKey
            return expected == null || expected == destinationKey
        }

        /** Idempotent explicit OFF hook for service teardown/configuration paths. */
        @Synchronized
        fun markExplicitOff(reasonText: String): Snapshot {
            hardOffLocked(reasonText)
            return snapshotLocked()
        }

        @Synchronized
        fun expireStalePresence(currentSelectedWindows: Set<String>): Snapshot {
            if (!usageAccessGranted || selected.isEmpty()) { hardOffLocked("stage40_presence_prerequisite_off"); return snapshotLocked() }
            val visible=currentSelectedWindows.mapNotNull(::normalizePackage).filterTo(LinkedHashSet()){it in selected}
            val current=LinkedHashSet<String>(); current+=visible; current+=resumed.filter{it in selected}
            if(current.isEmpty()){ hardOffLocked("stage40_no_current_selected_presence"); Metrics.increment("stage40PresenceExpired"); return snapshotLocked() }
            val removed=armed.filter{it !in current}.toSet(); armed.retainAll(current); armed.addAll(current)
            foregroundServices.removeAll(removed); seenForegroundServicePositive.removeAll(removed); activityStoppedAfterPositive.removeAll(removed); foregroundServiceStoppedAfterPositive.removeAll(removed)
            if(!enabled) ensureOnLocked("stage40_current_presence"); reason="stage40_presence_reconciled"; Metrics.increment("stage40PresenceReconciled"); return snapshotLocked()
        }

        @Synchronized fun snapshot(): Snapshot = snapshotLocked()

        private fun markPositiveLocked(pkg: String, foreground: Boolean) {
            armed += pkg
            activityStoppedAfterPositive -= pkg
            foregroundServiceStoppedAfterPositive -= pkg
            if (foreground) seenForegroundServicePositive += pkg
        }

        private fun ensureOnLocked(nextReason: String) {
            if (!usageAccessGranted || selected.isEmpty() || armed.isEmpty()) return
            if (!enabled) {
                enabled = true
                readingEpoch += 1L
                Metrics.increment("activationOn")
            }
            reason = nextReason
        }

        /**
         * Strong terminal proof: every armed package must have both activity-stop and a matching
         * foreground-service stop after a foreground-service positive witness, with no resumed/FGS
         * evidence left. This biases against false OFF because false OFF destroys useful work.
         */
        private fun maybeTerminalOffLocked() {
            if (!enabled || armed.isEmpty()) return
            if (resumed.isNotEmpty() || foregroundServices.isNotEmpty()) return
            val everyArmedTerminal = armed.all { pkg ->
                pkg in seenForegroundServicePositive &&
                    pkg in activityStoppedAfterPositive &&
                    pkg in foregroundServiceStoppedAfterPositive
            }
            if (everyArmedTerminal) hardOffLocked("usage_terminal_pair")
        }

        private fun hardOffLocked(nextReason: String) {
            if (enabled) {
                enabled = false
                readingEpoch += 1L
                Metrics.increment("activationOff")
            }
            armed.clear()
            resumed.clear()
            foregroundServices.clear()
            seenForegroundServicePositive.clear()
            activityStoppedAfterPositive.clear()
            foregroundServiceStoppedAfterPositive.clear()
            leaseId = 0L
            destinationKey = null
            reason = nextReason
        }

        private fun ensureLeaseLocked() {
            if (leaseId == 0L) {
                leaseId = ++leaseSerial
                destinationKey = null
                Metrics.increment("leasesOpened")
            }
        }

        private fun snapshotLocked() = Snapshot(
            enabled = enabled,
            usageAccessGranted = usageAccessGranted,
            selectedPackages = selected,
            authoritativeActivePackages = armed.toSet(),
            readingEpoch = readingEpoch,
            leaseId = leaseId,
            destinationKey = destinationKey,
            reason = reason,
        )
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
        prefixes.forEach { (pattern, replacement) -> out = out.replace(pattern, replacement) }
        return out
    }

    private fun canonical(value: String): String = Normalizer.normalize(
        value.replace(Regex("\\s+"), " ").trim().lowercase(Locale.ROOT), Normalizer.Form.NFD,
    ).replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    object Metrics {
        private val lock = Any()
        private val counters = LinkedHashMap<String, Long>()
        fun increment(name: String) = synchronized(lock) { counters[name] = (counters[name] ?: 0L) + 1L }
        fun counter(name: String): Long = synchronized(lock) { counters[name] ?: 0L }
        fun resetForTests() = synchronized(lock) { counters.clear() }
        fun exportReport(authority: Authority?): String = synchronized(lock) {
            val x = authority?.snapshot()
            buildString {
                appendLine("ROTA CERTA — STAGE36 SINGLE RUNTIME AUTHORITY")
                appendLine("marker=$CONTRACT_MARKER")
                appendLine("activation=$ACTIVATION_MARKER")
                appendLine("freshness=$FRESHNESS_MARKER")
                appendLine("window=$WINDOW_MARKER")
                appendLine("enabled=${x?.enabled ?: false}; epoch=${x?.readingEpoch ?: -1L}; lease=${x?.leaseId ?: -1L}; destination=${x?.destinationKey ?: "none"}; reason=${x?.reason ?: "none"}")
                listOf(
                    "activationOn", "activationOff", "directSelectedActivations", "windowBoundariesObserved",
                    "nonSelectedWindowBoundariesPreserved", "activityPausedPreserved", "activityStoppedObserved",
                    "foregroundServiceStoppedObserved", "leasesOpened", "rawVisualPreserved", "candidateFirstBinds",
                    "candidateConfirms", "destinationTransitions", "visualLeaseClears",
                ).forEach { appendLine("$it=${counters[it] ?: 0L}") }
            }.trimEnd()
        }
    }

    fun normalizePackage(value: String?): String? = value?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotBlank)
}
