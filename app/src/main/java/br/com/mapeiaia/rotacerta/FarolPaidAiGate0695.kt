package br.com.mapeiaia.rotacerta

import android.content.Context
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/**
 * 0.1.695 — one-paid-call-per-card gate with persistent successful-result cache.
 *
 * The paid model is a last-resort address interpreter only. A cached address is reusable for the
 * exact stable card fingerprint; failed attempts enter cooldown so Accessibility churn cannot
 * multiply paid requests.
 */
class FarolPaidAiGate0695 private constructor(
    private val store: Store,
    private val nowMillis: () -> Long,
) {
    data class Ticket(
        val key: String,
        val packageName: String,
        val sanitizedText: String,
    )

    sealed class Start {
        data class Cached(
            val key: String,
            val address: String,
            val confidence: Double,
        ) : Start()

        data class Network(val ticket: Ticket) : Start()
        data class Suppressed(val key: String, val reason: String) : Start()
    }

    private val inFlight = HashSet<String>()

    @Synchronized
    fun start(packageName: String, rawText: String): Start {
        val sanitized = sanitizeForRemote(rawText)
        if (sanitized.isBlank()) return Start.Suppressed("", "empty_after_sanitization")
        val key = fingerprint(packageName, sanitized)

        val cachedAddress = store.getString(addressKey(key)).orEmpty()
        if (cachedAddress.isNotBlank()) {
            val confidence = store.getString(confidenceKey(key))?.toDoubleOrNull() ?: 1.0
            return Start.Cached(key, cachedAddress, confidence.coerceIn(0.0, 1.0))
        }

        if (key in inFlight) return Start.Suppressed(key, "already_in_flight")

        val failedAt = store.getLong(failureKey(key))
        if (failedAt > 0L && nowMillis() - failedAt in 0 until FAILURE_COOLDOWN_MILLIS) {
            return Start.Suppressed(key, "failure_cooldown")
        }

        inFlight += key
        return Start.Network(Ticket(key, packageName.trim(), sanitized))
    }

    @Synchronized
    fun success(ticket: Ticket, address: String, confidence: Double) {
        inFlight.remove(ticket.key)
        val cleanAddress = address.trim().take(MAX_CACHED_ADDRESS_CHARS)
        if (cleanAddress.isBlank()) {
            failure(ticket)
            return
        }
        store.putString(addressKey(ticket.key), cleanAddress)
        store.putString(confidenceKey(ticket.key), confidence.coerceIn(0.0, 1.0).toString())
        store.putLong(successKey(ticket.key), nowMillis())
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
        const val CONTRACT_MARKER = "FAROL_PAID_AI_GATE_0695"
        const val RESULT_MARKER = "FAROL_PAID_AI_RESULT_0695"
        const val ONE_PAID_CALL_PER_CARD_MARKER = "ONE_PAID_CALL_PER_CARD_FINGERPRINT_0695"
        const val PERSISTENT_RESULT_CACHE_MARKER = "PERSISTENT_PAID_AI_ADDRESS_CACHE_0695"
        const val LOCAL_FIRST_MARKER = "PAID_AI_ONLY_AFTER_LOCAL_FAILURE_0695"

        private const val PREFS = "farol_paid_ai_gate_0695"
        private const val FAILURE_COOLDOWN_MILLIS = 10L * 60L * 1000L
        private const val MAX_REMOTE_CHARS = 1_800
        private const val MAX_CACHED_ADDRESS_CHARS = 320

        fun create(context: Context): FarolPaidAiGate0695 {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return FarolPaidAiGate0695(
                store = object : Store {
                    override fun getString(key: String): String? = prefs.getString(key, null)
                    override fun putString(key: String, value: String) {
                        prefs.edit().putString(key, value).apply()
                    }
                    override fun getLong(key: String): Long = prefs.getLong(key, 0L)
                    override fun putLong(key: String, value: Long) {
                        prefs.edit().putLong(key, value).apply()
                    }
                },
                nowMillis = System::currentTimeMillis,
            )
        }

        internal fun inMemoryForTests(
            values: MutableMap<String, String> = LinkedHashMap(),
            nowMillis: () -> Long = System::currentTimeMillis,
        ): FarolPaidAiGate0695 = FarolPaidAiGate0695(
            store = object : Store {
                override fun getString(key: String): String? = values[key]
                override fun putString(key: String, value: String) { values[key] = value }
                override fun getLong(key: String): Long = values[key]?.toLongOrNull() ?: 0L
                override fun putLong(key: String, value: Long) { values[key] = value.toString() }
            },
            nowMillis = nowMillis,
        )

        fun sanitizeForRemote(rawText: String): String {
            if (rawText.isBlank()) return ""
            val lines = rawText
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ')
                .lines()
                .map { it.trim().replace(Regex("\\s+"), " ") }
                .filter(String::isNotBlank)
                .take(80)

            if (lines.isEmpty()) return ""

            val relevant = LinkedHashSet<Int>()
            lines.forEachIndexed { index, line ->
                val address = UniversalScreenAddressParser.findAddresses(line).isNotEmpty()
                val cue = REMOTE_CUE_REGEX.containsMatchIn(canonical(line))
                if (address || cue) {
                    for (candidate in (index - 1)..(index + 1)) {
                        if (candidate in lines.indices) relevant += candidate
                    }
                }
            }
            val selected = if (relevant.isEmpty()) {
                lines.take(20)
            } else {
                relevant.sorted().map(lines::get)
            }

            return selected
                .joinToString("\n")
                .replace(PHONE_REGEX, "[telefone]")
                .replace(MONEY_REGEX, "[valor]")
                .take(MAX_REMOTE_CHARS)
                .trim()
        }

        fun fingerprint(packageName: String, sanitizedText: String): String {
            val stable = canonical(
                sanitizedText
                    .replace(MONEY_REGEX, " valor ")
                    .replace(DYNAMIC_METRIC_REGEX, " metrica ")
                    .replace(PHONE_REGEX, " telefone "),
            )
            val raw = packageName.trim().lowercase(Locale.ROOT) + "|" + stable
            val bytes = MessageDigest.getInstance("SHA-256")
                .digest(raw.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }.take(40)
        }

        private fun canonical(value: String): String = Normalizer
            .normalize(value.lowercase(Locale("pt", "BR")), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        private val REMOTE_CUE_REGEX = Regex(
            "\\b(?:origem|embarque|partida|pickup|destino|chegada|desembarque|dropoff|drop off|pedido de viagem|pedido de corrida|aceitar|ofereca sua tarifa|ofereça sua tarifa)\\b",
        )
        private val PHONE_REGEX = Regex("(?<!\\d)(?:\\+?55\\s*)?(?:\\(?\\d{2}\\)?\\s*)?9?\\d{4}[- .]?\\d{4}(?!\\d)")
        private val MONEY_REGEX = Regex("(?iu)R\\$\\s*\\d+(?:[.,]\\d{1,2})?")
        private val DYNAMIC_METRIC_REGEX = Regex("(?iu)\\b\\d+(?:[.,]\\d+)?\\s*(?:km|m|min|minutos?|seg|s)\\b")

        private fun addressKey(key: String) = "address:$key"
        private fun confidenceKey(key: String) = "confidence:$key"
        private fun successKey(key: String) = "success:$key"
        private fun failureKey(key: String) = "failure:$key"
    }

    private interface Store {
        fun getString(key: String): String?
        fun putString(key: String, value: String)
        fun getLong(key: String): Long
        fun putLong(key: String, value: Long)
    }
}
