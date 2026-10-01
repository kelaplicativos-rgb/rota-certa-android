package br.com.mapeiaia.rotacerta

import android.content.Context
import br.com.mapeiaia.rotacerta.trips.FarolPaidRoadTarget0715
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/**
 * 0.1.715 — one terminal paid road fallback per stable card/target fingerprint.
 *
 * This gate is deliberately separate from FarolPaidAiGate0695: 0695 recovers address
 * interpretation, while 0715 runs only after the normal road providers are exhausted.
 */
class FarolPaidRoadGate0715 private constructor(
    private val store: Store,
    private val nowMillis: () -> Long,
) {
    data class Ticket(
        val key: String,
        val packageName: String,
        val destination: String,
        val sanitizedContext: String,
        val targets: List<FarolPaidRoadTarget0715>,
    )

    sealed class Start {
        data class Cached(
            val key: String,
            val normalizedAddress: String,
            val confidence: Double,
            val roadKm: Double,
            val routeProvider: String,
        ) : Start()

        data class Network(val ticket: Ticket) : Start()
        data class Suppressed(val key: String, val reason: String) : Start()
    }

    private val inFlight = HashSet<String>()

    @Synchronized
    fun start(
        packageName: String,
        destination: String,
        cardContext: String,
        targets: List<FarolPaidRoadTarget0715>,
    ): Start {
        val normalizedPackage = packageName.trim().lowercase(Locale.ROOT)
        val normalizedDestination = destination.trim().take(MAX_DESTINATION_CHARS)
        val sanitizedContext = FarolPaidAiGate0695.sanitizeForRemote(cardContext)
        val normalizedTargets = targets
            .filter { it.latitude.isFinite() && it.longitude.isFinite() }
            .take(MAX_TARGETS)
        if (normalizedPackage.isBlank() || normalizedDestination.isBlank() || normalizedTargets.isEmpty()) {
            return Start.Suppressed("", "invalid_terminal_input")
        }

        val key = fingerprint(normalizedPackage, normalizedDestination, sanitizedContext, normalizedTargets)
        val cachedRoad = store.getString(roadKey(key))?.toDoubleOrNull()
        val cachedAddress = store.getString(addressKey(key)).orEmpty()
        if (cachedRoad != null && cachedRoad.isFinite() && cachedRoad >= 0.0 && cachedAddress.isNotBlank()) {
            return Start.Cached(
                key = key,
                normalizedAddress = cachedAddress,
                confidence = store.getString(confidenceKey(key))?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 1.0,
                roadKm = cachedRoad,
                routeProvider = store.getString(routeProviderKey(key)).orEmpty(),
            )
        }

        if (key in inFlight) return Start.Suppressed(key, "already_in_flight")
        val failedAt = store.getLong(failureKey(key))
        if (failedAt > 0L && nowMillis() - failedAt in 0 until FAILURE_COOLDOWN_MILLIS) {
            return Start.Suppressed(key, "failure_cooldown")
        }

        inFlight += key
        return Start.Network(
            Ticket(
                key = key,
                packageName = normalizedPackage,
                destination = normalizedDestination,
                sanitizedContext = sanitizedContext,
                targets = normalizedTargets,
            ),
        )
    }

    @Synchronized
    fun success(
        ticket: Ticket,
        normalizedAddress: String,
        confidence: Double,
        roadKm: Double,
        routeProvider: String,
    ) {
        inFlight.remove(ticket.key)
        if (normalizedAddress.isBlank() || !roadKm.isFinite() || roadKm < 0.0) {
            failure(ticket)
            return
        }
        store.putString(addressKey(ticket.key), normalizedAddress.trim().take(MAX_DESTINATION_CHARS))
        store.putString(confidenceKey(ticket.key), confidence.coerceIn(0.0, 1.0).toString())
        store.putString(roadKey(ticket.key), roadKm.toString())
        store.putString(routeProviderKey(ticket.key), routeProvider.trim().take(80))
        store.putLong(failureKey(ticket.key), 0L)
    }

    @Synchronized
    fun failure(ticket: Ticket) {
        inFlight.remove(ticket.key)
        store.putLong(failureKey(ticket.key), nowMillis())
    }

    @Synchronized
    fun releaseWithoutCharge(ticket: Ticket) {
        inFlight.remove(ticket.key)
    }

    companion object {
        const val CONTRACT_MARKER = "FAROL_PAID_ROAD_FALLBACK_0715"
        const val STARTED_MARKER = "FAROL_PAID_ROAD_FALLBACK_STARTED_0715"
        const val CACHE_HIT_MARKER = "FAROL_PAID_ROAD_CACHE_HIT_0715"
        const val RESOLVED_MARKER = "FAROL_PAID_ROAD_RESOLVED_0715"
        const val UNRESOLVED_MARKER = "FAROL_PAID_ROAD_UNRESOLVED_0715"
        const val STALE_DROPPED_MARKER = "FAROL_PAID_ROAD_STALE_DROPPED_0715"
        const val FAILED_MARKER = "FAROL_PAID_ROAD_FAILED_0715"

        private const val PREFS = "farol_paid_road_gate_0715"
        private const val FAILURE_COOLDOWN_MILLIS = 10L * 60L * 1000L
        private const val MAX_DESTINATION_CHARS = 500
        private const val MAX_TARGETS = 12

        fun create(context: Context): FarolPaidRoadGate0715 {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return FarolPaidRoadGate0715(
                store = object : Store {
                    override fun getString(key: String): String? = prefs.getString(key, null)
                    override fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
                    override fun getLong(key: String): Long = prefs.getLong(key, 0L)
                    override fun putLong(key: String, value: Long) { prefs.edit().putLong(key, value).apply() }
                },
                nowMillis = System::currentTimeMillis,
            )
        }

        internal fun inMemoryForTests(
            values: MutableMap<String, String> = LinkedHashMap(),
            nowMillis: () -> Long = System::currentTimeMillis,
        ): FarolPaidRoadGate0715 = FarolPaidRoadGate0715(
            store = object : Store {
                override fun getString(key: String): String? = values[key]
                override fun putString(key: String, value: String) { values[key] = value }
                override fun getLong(key: String): Long = values[key]?.toLongOrNull() ?: 0L
                override fun putLong(key: String, value: Long) { values[key] = value.toString() }
            },
            nowMillis = nowMillis,
        )

        fun fingerprint(
            packageName: String,
            destination: String,
            sanitizedContext: String,
            targets: List<FarolPaidRoadTarget0715>,
        ): String {
            val targetKey = targets.joinToString(";") {
                String.format(Locale.US, "%.6f,%.6f", it.latitude, it.longitude)
            }
            val stable = canonical(packageName) + "|" +
                canonical(destination) + "|" +
                canonical(sanitizedContext) + "|" +
                targetKey
            val bytes = MessageDigest.getInstance("SHA-256").digest(stable.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }.take(40)
        }

        private fun canonical(value: String): String = Normalizer
            .normalize(value.lowercase(Locale("pt", "BR")), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        private fun addressKey(key: String) = "address:$key"
        private fun confidenceKey(key: String) = "confidence:$key"
        private fun roadKey(key: String) = "road:$key"
        private fun routeProviderKey(key: String) = "routeProvider:$key"
        private fun failureKey(key: String) = "failure:$key"
    }

    private interface Store {
        fun getString(key: String): String?
        fun putString(key: String, value: String)
        fun getLong(key: String): Long
        fun putLong(key: String, value: Long)
    }
}
