package br.com.mapeiaia.rotacerta

import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Base64
import java.util.Locale

class LiveGeocodeCache(
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val maxEntries: Int = 512,
    private val ttlMillis: Long = 30L * 24L * 60L * 60L * 1000L,
) {
    private val entries = object : LinkedHashMap<String, Entry>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > maxEntries
    }

    @Synchronized
    fun get(query: String?): Coordinate? {
        val key = normalize(query) ?: return null
        val entry = entries[key] ?: return null
        if (nowMillis() - entry.createdAtMillis !in 0L..ttlMillis) {
            entries.remove(key)
            return null
        }
        return entry.coordinate
    }

    @Synchronized
    fun put(query: String?, coordinate: Coordinate?) {
        val key = normalize(query) ?: return
        coordinate ?: return
        entries[key] = Entry(coordinate, nowMillis())
    }

    @Synchronized
    fun exportSnapshot(): String = buildString {
        appendLine(SNAPSHOT_VERSION)
        entries.forEach { (key, entry) ->
            append(encode(key)).append('\t')
            append(entry.coordinate.latitude).append('\t')
            append(entry.coordinate.longitude).append('\t')
            append(entry.createdAtMillis).appendLine()
        }
    }

    @Synchronized
    fun importSnapshot(snapshot: String): Int {
        val lines = snapshot.lineSequence().filter(String::isNotBlank).toList()
        if (lines.firstOrNull() != SNAPSHOT_VERSION) return 0
        entries.clear()
        val now = nowMillis()
        lines.drop(1).takeLast(maxEntries).forEach { line ->
            val parts = line.split('\t')
            if (parts.size != 4) return@forEach
            val key = runCatching { decode(parts[0]) }.getOrNull() ?: return@forEach
            val coordinate = Coordinate(parts[1].toDoubleOrNull() ?: return@forEach, parts[2].toDoubleOrNull() ?: return@forEach)
            val created = parts[3].toLongOrNull() ?: return@forEach
            if (now - created in 0L..ttlMillis) entries[key] = Entry(coordinate, created)
        }
        return entries.size
    }

    @Synchronized
    fun entryCount(): Int = entries.size

    private data class Entry(val coordinate: Coordinate, val createdAtMillis: Long)

    private fun normalize(value: String?): String? = Normalizer
        .normalize(value.orEmpty().lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9,. -]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .takeIf(String::isNotBlank)

    private fun encode(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String): String = String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)

    private companion object {
        const val SNAPSHOT_VERSION = "RC_GEOCODE_CACHE_V1"
    }
}
