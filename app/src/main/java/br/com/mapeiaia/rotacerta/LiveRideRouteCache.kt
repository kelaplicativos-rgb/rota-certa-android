package br.com.mapeiaia.rotacerta

import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Base64
import java.util.Locale
import kotlin.math.roundToInt

class LiveRideRouteCache(
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val maxEntries: Int = 512,
    private val ttlMillis: Long = ROUTE_CACHE_TTL_MILLIS,
) {
    private val entries = object : LinkedHashMap<Key, Entry>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Entry>?): Boolean = size > maxEntries
    }

    @Synchronized
    fun get(key: Key?): CachedRoute? {
        key ?: return null
        val entry = entries[key] ?: return null
        val age = nowMillis() - entry.createdAtMillis
        if (age !in 0L..ttlMillis) {
            entries.remove(key)
            return null
        }
        return CachedRoute(
            destinationCoordinate = entry.destinationCoordinate,
            homeCoordinate = entry.homeCoordinate,
            alternativeCoordinate = entry.alternativeCoordinate,
            homeDistanceKm = entry.homeDistanceKm,
            alternativeDistanceKm = entry.alternativeDistanceKm,
            ageMillis = age,
        )
    }

    @Synchronized
    fun put(key: Key?, route: CachedRoute) {
        key ?: return
        if (route.destinationCoordinate == null) return
        entries[key] = Entry(
            destinationCoordinate = route.destinationCoordinate,
            homeCoordinate = route.homeCoordinate,
            alternativeCoordinate = route.alternativeCoordinate,
            homeDistanceKm = route.homeDistanceKm,
            alternativeDistanceKm = route.alternativeDistanceKm,
            createdAtMillis = nowMillis(),
        )
    }

    /**
     * Exporta somente resultados exatos ja calculados. O formato e interno,
     * compacto e tolerante a campos vazios; nao representa um mapa offline.
     */
    @Synchronized
    fun exportSnapshot(): String {
        if (entries.isEmpty()) return SNAPSHOT_VERSION
        return buildString {
            appendLine(SNAPSHOT_VERSION)
            entries.forEach { (key, entry) ->
                appendLine(
                    listOf(
                        encode(key.destination),
                        encode(key.homeCoordinate),
                        encode(key.alternativeCoordinate),
                        encode(key.homeAddress),
                        encode(key.alternativeAddress),
                        key.homeRadiusMeters.toString(),
                        key.alternativeRadiusMeters.toString(),
                        encode(key.avoidedKeywords),
                        encode(key.packageName),
                        encode(key.cardSignature),
                        entry.destinationCoordinate.serializeCoordinate(),
                        entry.homeCoordinate.serializeCoordinate(),
                        entry.alternativeCoordinate.serializeCoordinate(),
                        entry.homeDistanceKm.serializeDouble(),
                        entry.alternativeDistanceKm.serializeDouble(),
                        entry.createdAtMillis.toString(),
                    ).joinToString(FIELD_SEPARATOR),
                )
            }
        }.trimEnd()
    }

    /** Restaura apenas entradas validas e ainda dentro do prazo de 14 dias. */
    @Synchronized
    fun importSnapshot(payload: String): Int {
        if (payload.isBlank()) return 0
        val lines = payload.lineSequence().filter(String::isNotBlank).toList()
        if (lines.firstOrNull() != SNAPSHOT_VERSION) return 0

        val now = nowMillis()
        entries.clear()
        lines.drop(1).takeLast(maxEntries).forEach { line ->
            val fields = line.split(FIELD_SEPARATOR, limit = SNAPSHOT_FIELD_COUNT)
            if (fields.size != SNAPSHOT_FIELD_COUNT) return@forEach

            val key = runCatching {
                Key(
                    destination = decode(fields[0]),
                    homeCoordinate = decode(fields[1]),
                    alternativeCoordinate = decode(fields[2]),
                    homeAddress = decode(fields[3]),
                    alternativeAddress = decode(fields[4]),
                    homeRadiusMeters = fields[5].toInt(),
                    alternativeRadiusMeters = fields[6].toInt(),
                    avoidedKeywords = decode(fields[7]),
                    packageName = decode(fields[8]),
                    cardSignature = decode(fields[9]),
                )
            }.getOrNull() ?: return@forEach

            val entry = runCatching {
                Entry(
                    destinationCoordinate = fields[10].deserializeCoordinate(),
                    homeCoordinate = fields[11].deserializeCoordinate(),
                    alternativeCoordinate = fields[12].deserializeCoordinate(),
                    homeDistanceKm = fields[13].deserializeDouble(),
                    alternativeDistanceKm = fields[14].deserializeDouble(),
                    createdAtMillis = fields[15].toLong(),
                )
            }.getOrNull() ?: return@forEach

            val age = now - entry.createdAtMillis
            if (entry.destinationCoordinate != null && age in 0L..ttlMillis) {
                entries[key] = entry
            }
        }
        return entries.size
    }

    @Synchronized
    fun entryCount(): Int = entries.size

    @Synchronized
    fun clear() {
        entries.clear()
    }

    data class Key(
        val destination: String,
        val homeCoordinate: String,
        val alternativeCoordinate: String,
        val homeAddress: String,
        val alternativeAddress: String,
        val homeRadiusMeters: Int,
        val alternativeRadiusMeters: Int,
        val avoidedKeywords: String,
        val packageName: String = "",
        val cardSignature: String = "",
    )

    data class CachedRoute(
        val destinationCoordinate: Coordinate?,
        val homeCoordinate: Coordinate?,
        val alternativeCoordinate: Coordinate?,
        val homeDistanceKm: Double?,
        val alternativeDistanceKm: Double?,
        val ageMillis: Long = 0L,
    )

    private data class Entry(
        val destinationCoordinate: Coordinate?,
        val homeCoordinate: Coordinate?,
        val alternativeCoordinate: Coordinate?,
        val homeDistanceKm: Double?,
        val alternativeDistanceKm: Double?,
        val createdAtMillis: Long,
    )

    companion object {
        const val ROUTE_CACHE_TTL_DAYS: Long = 14L
        const val ROUTE_CACHE_TTL_MILLIS: Long = ROUTE_CACHE_TTL_DAYS * 24L * 60L * 60L * 1000L
        private const val SNAPSHOT_VERSION = "RC_ROUTE_CACHE_V1"
        private const val FIELD_SEPARATOR = "\t"
        private const val SNAPSHOT_FIELD_COUNT = 16

        fun keyFor(
            fields: RideFields,
            settings: AppSettings,
            packageName: String? = null,
            cardSignature: String? = null,
        ): Key? {
            val destination = normalizeKey(fields.destination).takeIf { it.isNotBlank() } ?: return null
            return Key(
                destination = destination,
                homeCoordinate = settings.homeCoordinate.cachePart(),
                alternativeCoordinate = settings.alternativeCoordinate.cachePart(),
                homeAddress = normalizeKey(settings.homeAddress),
                alternativeAddress = normalizeKey(settings.alternativeAddress),
                homeRadiusMeters = (settings.homeRadiusKm * 1000).roundToInt(),
                alternativeRadiusMeters = (settings.alternativeRadiusKm * 1000).roundToInt(),
                avoidedKeywords = normalizeKey(settings.avoidedKeywords),
                packageName = normalizeKey(packageName),
                cardSignature = normalizeKey(cardSignature),
            )
        }

        private fun encode(value: String): String = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

        private fun decode(value: String): String = String(
            Base64.getUrlDecoder().decode(value),
            StandardCharsets.UTF_8,
        )

        private fun Coordinate?.serializeCoordinate(): String =
            this?.let { "${it.latitude},${it.longitude}" }.orEmpty()

        private fun String.deserializeCoordinate(): Coordinate? {
            if (isBlank()) return null
            val parts = split(',', limit = 2)
            if (parts.size != 2) return null
            val latitude = parts[0].toDoubleOrNull() ?: return null
            val longitude = parts[1].toDoubleOrNull() ?: return null
            return Coordinate(latitude, longitude)
        }

        private fun Double?.serializeDouble(): String = this?.toString().orEmpty()
        private fun String.deserializeDouble(): Double? = takeIf(String::isNotBlank)?.toDoubleOrNull()

        private fun Coordinate?.cachePart(): String =
            this?.let { "%.5f,%.5f".format(Locale.US, it.latitude, it.longitude) }.orEmpty()

        private fun normalizeKey(value: String?): String =
            Normalizer.normalize(value.orEmpty().lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(Regex("[^a-z0-9,. -]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
    }
}
