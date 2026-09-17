package br.com.mapeiaia.rotacerta

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.os.Process

/**
 * Stage28 current-execution witness.
 *
 * Historical usage records are deliberately NOT activation authority. A selected package is
 * active only while Android currently reports a non-cached process for it. Old usage-history
 * records therefore cannot keep the FAROL on indefinitely.
 */
class SelectedAppUsageStateStage28(private val context: Context) {
    data class ExecutionSnapshot(
        val usageAccessGranted: Boolean,
        val activeSelectedPackages: Set<String>,
    )

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

    fun readCurrentExecution(selectedPackages: Set<String>): ExecutionSnapshot {
        val normalized = selectedPackages.map { it.trim().lowercase() }.filter(String::isNotBlank).toSet()
        if (normalized.isEmpty()) return ExecutionSnapshot(hasUsageAccess(), emptySet())
        val granted = hasUsageAccess()
        if (!granted) return ExecutionSnapshot(false, emptySet())
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return ExecutionSnapshot(true, emptySet())
        val processes = runCatching { activityManager.runningAppProcesses }.getOrNull().orEmpty()
        val active = LinkedHashSet<String>()
        for (process in processes) {
            if (!isCurrentExecutionImportance(process.importance)) continue
            val candidates = LinkedHashSet<String>()
            process.pkgList?.forEach { candidates += it.trim().lowercase() }
            process.processName?.substringBefore(':')?.trim()?.lowercase()?.takeIf(String::isNotBlank)?.let(candidates::add)
            candidates.filterTo(active) { it in normalized }
        }
        return ExecutionSnapshot(true, active)
    }

    companion object {
        const val MARKER = "CURRENT_NON_CACHED_PROCESS_AUTHORITY_STAGE28"
        const val NO_HISTORY_AUTHORITY_MARKER = "USAGE_HISTORY_NEVER_KEEPS_READING_ON_STAGE28"

        /** Cached/empty processes are not proof that the selected app is really active. */
        fun isCurrentExecutionImportance(importance: Int): Boolean =
            importance > 0 && importance < ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
    }
}
