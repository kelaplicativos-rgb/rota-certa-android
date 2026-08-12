package br.com.mapeiaia.rotacerta

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageEventsQuery
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

/** Android adapter. Called only from real accessibility/window events; it owns no polling loop. */
class SelectedAppUsageStateStage26(private val context: Context) {
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        return runCatching {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }

    fun readSelectedActivity(selectedPackages: Set<String>, nowMillis: Long = System.currentTimeMillis()): List<FarolReadingActivationStage26.UsageEvent> {
        if (selectedPackages.isEmpty() || !hasUsageAccess()) return emptyList()
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyList()
        // Events are system state, not a visual debounce. A bounded historical replay reconstructs
        // whether a selected activity/foreground service is currently alive; no background polling.
        val begin = (nowMillis - REPLAY_WINDOW_MS).coerceAtLeast(0L)
        val events = runCatching {
            if (Build.VERSION.SDK_INT >= 35) {
                val query = UsageEventsQuery.Builder(begin, nowMillis + 1L)
                    .setPackageNames(*selectedPackages.toTypedArray())
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
                manager.queryEvents(begin, nowMillis + 1L)
            }
        }.getOrNull() ?: return emptyList()
        val result = ArrayList<FarolReadingActivationStage26.UsageEvent>()
        val item = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(item)
            val pkg = item.packageName?.trim()?.lowercase() ?: continue
            if (pkg !in selectedPackages) continue
            val signal = when (item.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> FarolReadingActivationStage26.UsageSignal.ACTIVITY_RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED -> FarolReadingActivationStage26.UsageSignal.ACTIVITY_PAUSED
                UsageEvents.Event.ACTIVITY_STOPPED -> FarolReadingActivationStage26.UsageSignal.ACTIVITY_STOPPED
                UsageEvents.Event.FOREGROUND_SERVICE_START -> FarolReadingActivationStage26.UsageSignal.FOREGROUND_SERVICE_START
                UsageEvents.Event.FOREGROUND_SERVICE_STOP -> FarolReadingActivationStage26.UsageSignal.FOREGROUND_SERVICE_STOP
                else -> null
            } ?: continue
            result += FarolReadingActivationStage26.UsageEvent(pkg, signal, item.timeStamp)
        }
        return result
    }

    companion object { private const val REPLAY_WINDOW_MS = 24L * 60L * 60L * 1000L }
}
