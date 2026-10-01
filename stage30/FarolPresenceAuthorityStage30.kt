package br.com.mapeiaia.rotacerta

import java.util.Locale

/**
 * Stage30 presence authority.
 *
 * Selected packages are ONLY the ON/OFF key for reading. Visual package identity remains irrelevant
 * after activation. The authoritative state is causal and session-bounded: current Accessibility
 * evidence plus UsageEvents observed after this service session started. runningAppProcesses is
 * retained only as a diagnostic shadow and can never switch reading ON or OFF.
 */
object FarolPresenceAuthorityStage30 {
    const val CONTRACT_MARKER = "FAROL_PRESENCE_AUTHORITY_STAGE30"
    const val DIRECT_ACCESSIBILITY_MARKER = "ACCESSIBILITY_DIRECT_ACTIVATES_STAGE30"
    const val SESSION_USAGE_MARKER = "SESSION_BOUNDED_USAGE_EVENTS_STAGE30"
    const val PROCESS_SHADOW_MARKER = "RUNNING_APP_PROCESSES_SHADOW_ONLY_STAGE30"
    const val SHADOW_AUTHORITY_MARKER = "SHADOW_AUTHORITY_STAGE30"
    const val REPLAY_MARKER = "PHYSICAL_FAILURE_REPLAY_STAGE30"
    const val NO_POLLING_MARKER = "NO_POLLING_NO_SLEEP_NO_DEBOUNCE_STAGE30"
    const val UNIVERSAL_VISUAL_MARKER = "SELECTED_PACKAGE_ONLY_TURNS_READING_ON_OFF_STAGE30"

    enum class UsageSignal {
        ACTIVITY_RESUMED,
        ACTIVITY_PAUSED,
        ACTIVITY_STOPPED,
        FOREGROUND_SERVICE_START,
        FOREGROUND_SERVICE_STOP,
    }

    data class UsageEvidence(
        val packageName: String,
        val signal: UsageSignal,
        val timestampMillis: Long,
    )

    data class Snapshot(
        val enabled: Boolean,
        val usageAccessGranted: Boolean,
        val selectedPackages: Set<String>,
        val authoritativeActivePackages: Set<String>,
        val accessibilityActivePackages: Set<String>,
        val usageActivePackages: Set<String>,
        val processShadowActivePackages: Set<String>,
        val generation: Long,
        val sessionStartWallMillis: Long,
    )

    class Authority(val sessionStartWallMillis: Long) {
        private var selected = emptySet<String>()
        private var usageAccessGranted = false
        private var currentAccessibilitySelected: String? = null
        private val resumed = LinkedHashSet<String>()
        private val foregroundServices = LinkedHashSet<String>()
        private var processShadow = emptySet<String>()
        private var enabled = false
        private var generation = 0L

        @Synchronized
        fun updateSelection(packages: Set<String>): Snapshot {
            selected = packages.mapNotNull(::normalizePackage).toSet()
            resumed.retainAll(selected)
            foregroundServices.retainAll(selected)
            processShadow = processShadow.filterTo(LinkedHashSet()) { it in selected }
            if (currentAccessibilitySelected?.let { it in selected } != true) currentAccessibilitySelected = null
            return recompute("selection")
        }

        @Synchronized
        fun setUsageAccess(granted: Boolean): Snapshot {
            usageAccessGranted = granted
            if (!granted) {
                resumed.clear()
                foregroundServices.clear()
                currentAccessibilitySelected = null
            }
            return recompute("usage_access")
        }

        /**
         * A current Accessibility event from a selected package is direct evidence that the selected
         * app/window/popup is present now. It must switch reading on before any process query can veto it.
         */
        @Synchronized
        fun observeAccessibility(packageName: String?, eventType: Int = 0, timestampMillis: Long = 0L): Snapshot {
            val pkg = normalizePackage(packageName)
            if (pkg != null && pkg in selected) {
                currentAccessibilitySelected = pkg
                Metrics.increment("accessibilityDirectSelected")
                Diagnostics.noteDirect(pkg, eventType, timestampMillis)
            }
            return recompute("accessibility")
        }

        /**
         * A true window-state boundary to a different package ends only the *visible* selected witness.
         * A selected app may remain authoritative through a current-session foreground-service/activity
         * usage state. Generic content events never clear this witness.
         */
        @Synchronized
        fun observeWindowBoundary(packageName: String?): Snapshot {
            val pkg = normalizePackage(packageName)
            if (pkg == null || pkg !in selected) {
                if (currentAccessibilitySelected != null) Metrics.increment("accessibilityVisibleCleared")
                currentAccessibilitySelected = null
            } else {
                currentAccessibilitySelected = pkg
            }
            return recompute("window_boundary")
        }

        /** Only current-session UsageEvents are accepted. Old history is ignored by construction. */
        @Synchronized
        fun applyUsageEvidence(events: List<UsageEvidence>): Snapshot {
            for (event in events.sortedBy { it.timestampMillis }) {
                val pkg = normalizePackage(event.packageName) ?: continue
                if (pkg !in selected) continue
                if (event.timestampMillis < sessionStartWallMillis) {
                    Metrics.increment("usageEvidenceBeforeSessionIgnored")
                    continue
                }
                when (event.signal) {
                    UsageSignal.ACTIVITY_RESUMED -> resumed += pkg
                    UsageSignal.ACTIVITY_PAUSED,
                    UsageSignal.ACTIVITY_STOPPED -> resumed -= pkg
                    UsageSignal.FOREGROUND_SERVICE_START -> foregroundServices += pkg
                    UsageSignal.FOREGROUND_SERVICE_STOP -> foregroundServices -= pkg
                }
                Metrics.increment("usageEvidenceApplied")
            }
            return recompute("usage")
        }

        /** Diagnostic shadow only. This method never participates in authoritativeActivePackages. */
        @Synchronized
        fun updateProcessShadow(activePackages: Set<String>): Snapshot {
            processShadow = activePackages.mapNotNull(::normalizePackage).filterTo(LinkedHashSet()) { it in selected }
            val authoritative = authoritativeActiveLocked()
            if (authoritative.isNotEmpty() && processShadow.isEmpty()) Metrics.increment("processShadowFalseNegative")
            if (authoritative.isEmpty() && processShadow.isNotEmpty()) Metrics.increment("processShadowWouldFalsePositive")
            Diagnostics.noteShadow(authoritative, accessibilityActiveLocked(), usageActiveLocked(), processShadow)
            return snapshotLocked()
        }

        @Synchronized fun snapshot(): Snapshot = snapshotLocked()

        private fun authoritativeActiveLocked(): Set<String> {
            if (!usageAccessGranted || selected.isEmpty()) return emptySet()
            val result = usageActiveLocked().toMutableSet()
            currentAccessibilitySelected?.takeIf { it in selected }?.let(result::add)
            return result
        }

        private fun accessibilityActiveLocked(): Set<String> =
            currentAccessibilitySelected?.takeIf { usageAccessGranted && it in selected }?.let { setOf(it) } ?: emptySet()

        private fun usageActiveLocked(): Set<String> =
            if (!usageAccessGranted) emptySet() else (resumed + foregroundServices).filterTo(LinkedHashSet()) { it in selected }

        private fun recompute(reason: String): Snapshot {
            val active = authoritativeActiveLocked()
            val next = usageAccessGranted && selected.isNotEmpty() && active.isNotEmpty()
            if (next != enabled) {
                enabled = next
                generation += 1L
                Metrics.increment(if (enabled) "activationOn" else "activationOff")
                Diagnostics.noteTransition(enabled, generation, reason, active)
            }
            Metrics.setGauge("authoritativeActiveCount", active.size.toLong())
            Metrics.setGauge("accessibilityActiveCount", accessibilityActiveLocked().size.toLong())
            Metrics.setGauge("usageActiveCount", usageActiveLocked().size.toLong())
            Metrics.setGauge("processShadowActiveCount", processShadow.size.toLong())
            Metrics.setGauge("generation", generation)
            return snapshotLocked()
        }

        private fun snapshotLocked() = Snapshot(
            enabled = enabled,
            usageAccessGranted = usageAccessGranted,
            selectedPackages = selected,
            authoritativeActivePackages = authoritativeActiveLocked(),
            accessibilityActivePackages = accessibilityActiveLocked(),
            usageActivePackages = usageActiveLocked(),
            processShadowActivePackages = processShadow,
            generation = generation,
            sessionStartWallMillis = sessionStartWallMillis,
        )
    }

    object Diagnostics {
        private val lock = Any()
        private var transition = "none"
        private var direct = "none"
        private var shadow = "none"

        fun resetForTests() = synchronized(lock) { transition = "none"; direct = "none"; shadow = "none" }
        fun noteTransition(enabled: Boolean, generation: Long, reason: String, active: Set<String>) = synchronized(lock) {
            transition = "enabled=$enabled; generation=$generation; reason=$reason; authoritative=${active.sorted().joinToString(",")}"
        }
        fun noteDirect(pkg: String, eventType: Int, timestampMillis: Long) = synchronized(lock) {
            direct = "package=$pkg; eventType=$eventType; wall_ms=$timestampMillis"
        }
        fun noteShadow(authoritative: Set<String>, accessibility: Set<String>, usage: Set<String>, process: Set<String>) = synchronized(lock) {
            shadow = "authoritative=${authoritative.sorted().joinToString(",")}; accessibility=${accessibility.sorted().joinToString(",")}; usage=${usage.sorted().joinToString(",")}; process=${process.sorted().joinToString(",")}"
        }
        fun export(): String = synchronized(lock) {
            buildString {
                appendLine("ROTA CERTA — STAGE30 PRESENCE AUTHORITY / SHADOW")
                appendLine("marker=$CONTRACT_MARKER")
                appendLine("authority=accessibility_current_plus_session_usage")
                appendLine("runningAppProcesses=shadow_only")
                appendLine("lastTransition=$transition")
                appendLine("lastDirect=$direct")
                appendLine("lastShadow=$shadow")
                appendLine("activationOn=${Metrics.counter("activationOn")}")
                appendLine("activationOff=${Metrics.counter("activationOff")}")
                appendLine("accessibilityDirectSelected=${Metrics.counter("accessibilityDirectSelected")}")
                appendLine("usageEvidenceApplied=${Metrics.counter("usageEvidenceApplied")}")
                appendLine("usageEvidenceBeforeSessionIgnored=${Metrics.counter("usageEvidenceBeforeSessionIgnored")}")
                appendLine("processShadowFalseNegative=${Metrics.counter("processShadowFalseNegative")}")
                appendLine("processShadowWouldFalsePositive=${Metrics.counter("processShadowWouldFalsePositive")}")
                appendLine("authoritativeActiveCount=${Metrics.gauge("authoritativeActiveCount")}")
                appendLine("accessibilityActiveCount=${Metrics.gauge("accessibilityActiveCount")}")
                appendLine("usageActiveCount=${Metrics.gauge("usageActiveCount")}")
                appendLine("processShadowActiveCount=${Metrics.gauge("processShadowActiveCount")}")
                appendLine("generation=${Metrics.gauge("generation")}")
            }.trimEnd()
        }
    }

    object Metrics {
        private val lock = Any()
        private val counters = LinkedHashMap<String, Long>()
        private val gauges = LinkedHashMap<String, Long>()
        fun resetForTests() = synchronized(lock) { counters.clear(); gauges.clear() }
        fun increment(name: String, amount: Long = 1L) = synchronized(lock) { counters[name] = (counters[name] ?: 0L) + amount }
        fun setGauge(name: String, value: Long) = synchronized(lock) { gauges[name] = value }
        fun counter(name: String): Long = synchronized(lock) { counters[name] ?: 0L }
        fun gauge(name: String): Long = synchronized(lock) { gauges[name] ?: 0L }
    }

    fun normalizePackage(value: String?): String? =
        value?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotBlank)
}
