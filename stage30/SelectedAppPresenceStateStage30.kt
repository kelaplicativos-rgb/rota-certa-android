package br.com.mapeiaia.rotacerta

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageEventsQuery
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.util.ArrayDeque

/**
 * Android adapter for Stage30. It has no polling loop, timer, sleep, or visual-card authority.
 * UsageEvents are queried incrementally from this service session only. runningAppProcesses is
 * collected strictly as a diagnostic shadow and is never returned as activation authority.
 */
class SelectedAppPresenceStateStage30(private val context: Context) {
    val sessionStartWallMillis: Long = System.currentTimeMillis()
    private var usageCursorMillis: Long = sessionStartWallMillis
    private val seenUsageKeys = LinkedHashSet<String>()
    private val seenUsageOrder = ArrayDeque<String>()

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        return runCatching {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            ) == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }

    /**
     * Event-driven incremental query. The tiny overlap protects against late UsageEvents delivery;
     * deduplication prevents replay. The lower bound can never precede this service session.
     */
    fun readIncrementalUsage(
        selectedPackages: Set<String>,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<FarolPresenceAuthorityStage30.UsageEvidence> {
        val selected = selectedPackages.mapNotNull(FarolPresenceAuthorityStage30::normalizePackage).toSet()
        if (selected.isEmpty() || !hasUsageAccess()) return emptyList()
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyList()
        val begin = maxOf(sessionStartWallMillis, usageCursorMillis - DELIVERY_OVERLAP_MS)
        val end = maxOf(begin + 1L, nowMillis + 1L)
        val events = runCatching {
            if (Build.VERSION.SDK_INT >= 35) {
                val query = UsageEventsQuery.Builder(begin, end)
                    .setPackageNames(*selected.toTypedArray())
                    .setEventTypes(
                        UsageEvents.Event.ACTIVITY_RESUMED,
                        UsageEvents.Event.ACTIVITY_PAUSED,
                        UsageEvents.Event.ACTIVITY_STOPPED,
                        UsageEvents.Event.FOREGROUND_SERVICE_START,
                        UsageEvents.Event.FOREGROUND_SERVICE_STOP,
                    )
                    .build()
                manager.queryEvents(query)
            } else {
                @Suppress("DEPRECATION")
                manager.queryEvents(begin, end)
            }
        }.getOrNull() ?: return emptyList()
        usageCursorMillis = maxOf(usageCursorMillis, end)
        val out = ArrayList<FarolPresenceAuthorityStage30.UsageEvidence>()
        val item = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(item)
            val pkg = FarolPresenceAuthorityStage30.normalizePackage(item.packageName) ?: continue
            if (pkg !in selected || item.timeStamp < sessionStartWallMillis) continue
            val signal = when (item.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> FarolPresenceAuthorityStage30.UsageSignal.ACTIVITY_RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED -> FarolPresenceAuthorityStage30.UsageSignal.ACTIVITY_PAUSED
                UsageEvents.Event.ACTIVITY_STOPPED -> FarolPresenceAuthorityStage30.UsageSignal.ACTIVITY_STOPPED
                UsageEvents.Event.FOREGROUND_SERVICE_START -> FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_START
                UsageEvents.Event.FOREGROUND_SERVICE_STOP -> FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_STOP
                else -> null
            } ?: continue
            val key = "$pkg|${item.eventType}|${item.timeStamp}|${item.className.orEmpty()}"
            if (!remember(key)) continue
            out += FarolPresenceAuthorityStage30.UsageEvidence(pkg, signal, item.timeStamp)
        }
        return out
    }

    /** Diagnostic shadow only. Never use this set to decide Stage30 enabled/disabled. */
    fun readProcessShadow(selectedPackages: Set<String>): Set<String> {
        val selected = selectedPackages.mapNotNull(FarolPresenceAuthorityStage30::normalizePackage).toSet()
        if (selected.isEmpty()) return emptySet()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return emptySet()
        val processes = runCatching { am.runningAppProcesses }.getOrNull().orEmpty()
        val active = LinkedHashSet<String>()
        for (process in processes) {
            if (process.importance <= 0 || process.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED) continue
            process.pkgList?.mapNotNull(FarolPresenceAuthorityStage30::normalizePackage)?.filterTo(active) { it in selected }
            FarolPresenceAuthorityStage30.normalizePackage(process.processName?.substringBefore(':'))
                ?.takeIf { it in selected }?.let(active::add)
        }
        return active
    }

    private fun remember(key: String): Boolean {
        if (!seenUsageKeys.add(key)) return false
        seenUsageOrder.addLast(key)
        while (seenUsageOrder.size > MAX_SEEN_KEYS) {
            seenUsageKeys.remove(seenUsageOrder.removeFirst())
        }
        return true
    }

    companion object {
        const val MARKER = "SESSION_BOUNDED_USAGE_ADAPTER_STAGE30"
        const val PROCESS_SHADOW_ONLY_MARKER = "RUNNING_PROCESS_NEVER_AUTHORITY_STAGE30"
        private const val DELIVERY_OVERLAP_MS = 1500L
        private const val MAX_SEEN_KEYS = 256
    }
}
