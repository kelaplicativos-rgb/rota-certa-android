package br.com.mapeiaia.rotacerta

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Calendar

class WorkTrackingRepository(context: Context) {
    private val appContext = context.applicationContext
    private val tenantScope = RotaCertaTenantRegistry(appContext).activeScope()
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val activeKey = tenantScope.key(KEY_ACTIVE)
    private val sessionStartedAtKey = tenantScope.key(KEY_SESSION_STARTED_AT)
    private val pointsFile = File(
        appContext.filesDir,
        if (tenantScope.usesLegacyKeys) POINTS_FILE_NAME else "work-tracking-points-${tenantScope.namespace}.jsonl",
    )
    private val json = Json { ignoreUnknownKeys = true }

    fun isTrackingActive(): Boolean = preferences.getBoolean(activeKey, false)

    fun sessionStartedAtMillis(): Long? = preferences
        .getLong(sessionStartedAtKey, 0L)
        .takeIf { it > 0L }

    fun markTrackingStarted(nowMillis: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putBoolean(activeKey, true)
            .putLong(sessionStartedAtKey, nowMillis)
            .apply()
        pruneOldPoints(nowMillis)
    }

    fun markTrackingStopped() {
        preferences.edit()
            .putBoolean(activeKey, false)
            .remove(sessionStartedAtKey)
            .apply()
    }

    fun append(point: WorkTrackPoint) {
        synchronized(FILE_LOCK) {
            pointsFile.parentFile?.mkdirs()
            pointsFile.appendText(json.encodeToString(point) + "\n")
        }
    }

    fun readAllPoints(): List<WorkTrackPoint> = synchronized(FILE_LOCK) {
        if (!pointsFile.exists()) return@synchronized emptyList()
        pointsFile.useLines { lines ->
            lines.mapNotNull { line ->
                line.takeIf(String::isNotBlank)?.let { raw ->
                    runCatching { json.decodeFromString<WorkTrackPoint>(raw) }.getOrNull()
                }
            }.toList()
        }
    }

    fun todaySummary(nowMillis: Long = System.currentTimeMillis()): WorkTrackingSummary {
        val (start, end) = dayBounds(nowMillis)
        return buildWorkTrackingSummary(readAllPoints(), start, end)
    }

    fun clearToday(nowMillis: Long = System.currentTimeMillis()) {
        val (start, end) = dayBounds(nowMillis)
        rewrite(readAllPoints().filterNot { it.recordedAtMillis in start until end })
    }

    fun pruneOldPoints(nowMillis: Long = System.currentTimeMillis()) {
        val minimum = nowMillis - RETENTION_MILLIS
        val points = readAllPoints()
        if (points.firstOrNull()?.recordedAtMillis?.let { it < minimum } == true) {
            rewrite(points.filter { it.recordedAtMillis >= minimum })
        }
    }

    private fun rewrite(points: List<WorkTrackPoint>) {
        synchronized(FILE_LOCK) {
            pointsFile.parentFile?.mkdirs()
            val temporary = File(pointsFile.parentFile, pointsFile.name + ".tmp")
            temporary.bufferedWriter().use { writer ->
                points.forEach { point ->
                    writer.append(json.encodeToString(point)).append('\n')
                }
            }
            if (!temporary.renameTo(pointsFile)) {
                pointsFile.writeText(temporary.readText())
                temporary.delete()
            }
        }
    }

    private fun dayBounds(nowMillis: Long): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        return start to calendar.timeInMillis
    }

    private companion object {
        val FILE_LOCK = Any()
        const val PREFERENCES_NAME = "rota_certa_work_tracking"
        const val POINTS_FILE_NAME = "work-tracking-points.jsonl"
        const val KEY_ACTIVE = "active"
        const val KEY_SESSION_STARTED_AT = "session_started_at"
        const val RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1000L
    }
}
