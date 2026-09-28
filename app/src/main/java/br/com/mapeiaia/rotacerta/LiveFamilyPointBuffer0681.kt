package br.com.mapeiaia.rotacerta

import android.content.Context
import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 0.1.681: buffer separado do "Rastreamento de trabalho".
 *
 * O Location Core pode continuar alimentando o link familiar e os radares mesmo
 * quando o motorista toca em Parar no registro de trabalho. Por isso pontos
 * usados na publicação familiar não podem depender do histórico de trabalho.
 */
internal class LiveFamilyPointBuffer0681(context: Context) {
    private val appContext = context.applicationContext
    private val tenant = RotaCertaTenantRegistry(appContext).activeScope()
    private val file = File(appContext.filesDir, "live-family-points-${tenant.namespace}.jsonl")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun append(point: WorkTrackPoint) {
        synchronized(LOCK) {
            file.parentFile?.mkdirs()
            file.appendText(json.encodeToString(point) + "\n")
        }
        prune(point.recordedAtMillis)
    }

    fun readAll(): List<WorkTrackPoint> = synchronized(LOCK) {
        if (!file.exists()) return@synchronized emptyList()
        file.useLines { lines ->
            lines.mapNotNull { raw ->
                raw.takeIf(String::isNotBlank)?.let {
                    runCatching { json.decodeFromString<WorkTrackPoint>(it) }.getOrNull()
                }
            }.toList()
        }
    }

    fun prune(nowMillis: Long = System.currentTimeMillis()) {
        val minimum = nowMillis - RETENTION_MILLIS
        val points = readAll()
        if (points.firstOrNull()?.recordedAtMillis?.let { it < minimum } != true) return
        rewrite(points.filter { it.recordedAtMillis >= minimum })
    }

    private fun rewrite(points: List<WorkTrackPoint>) = synchronized(LOCK) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.bufferedWriter().use { writer ->
            points.forEach { writer.append(json.encodeToString(it)).append('\n') }
        }
        if (!temporary.renameTo(file)) {
            file.writeText(temporary.readText())
            temporary.delete()
        }
    }

    private companion object {
        val LOCK = Any()
        const val RETENTION_MILLIS = 7L * 24L * 60L * 60L * 1000L
    }
}
