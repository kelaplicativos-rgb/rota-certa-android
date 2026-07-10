package br.com.mapeiaia.rotacerta

import java.text.Normalizer
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
        if (age > ttlMillis) {
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

        private fun Coordinate?.cachePart(): String =
            this?.let { "%.5f,%.5f".format(Locale.US, it.latitude, it.longitude) }.orEmpty()

        private fun normalizeKey(value: String?): String =
            Normalizer.normalize(value.orEmpty().lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(Regex("[^a-z0-9,. -]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
    }
}
