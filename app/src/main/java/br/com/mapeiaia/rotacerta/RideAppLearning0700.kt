package br.com.mapeiaia.rotacerta

import android.content.Context
import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RideReaderProfile0700(
    val packageName: String,
    val versionName: String = "",
    val versionCode: Long = 0L,
    val apkSha256: String,
    val profileVersion: Int = 1,
    val confidence: Double = 0.0,
    val pickupLabels: List<String> = emptyList(),
    val destinationLabels: List<String> = emptyList(),
    val fareLabels: List<String> = emptyList(),
    val distanceLabels: List<String> = emptyList(),
    val rideAnchors: List<String> = emptyList(),
    val actionLabels: List<String> = emptyList(),
    val resourceHints: List<String> = emptyList(),
    val ignoreLabels: List<String> = emptyList(),
    val provider: String = "",
    val model: String = "",
    val reason: String = "",
    val learnedAtMillis: Long = 0L,
) {
    val usable: Boolean
        get() = packageName.isNotBlank() &&
            apkSha256.length >= 32 &&
            confidence >= 0.60 &&
            (destinationLabels.isNotEmpty() || resourceHints.isNotEmpty())
}

object RideAppLearningStore0700 {
    const val CONTRACT_MARKER = "RIDE_APP_LEARNING_PROFILE_STORE_0700"
    private const val PREFS = "ride_app_learning_0700"
    private const val INDEX = "packages"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun save(context: Context, profile: RideReaderProfile0700) {
        val pkg = normalizePackage(profile.packageName) ?: return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val index = prefs.getStringSet(INDEX, emptySet()).orEmpty().toMutableSet().apply { add(pkg) }
        prefs.edit()
            .putString(profileKey(pkg), json.encodeToString(profile.copy(packageName = pkg)))
            .putStringSet(INDEX, index)
            .apply()
    }

    fun read(context: Context, packageName: String?): RideReaderProfile0700? {
        val pkg = normalizePackage(packageName) ?: return null
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(profileKey(pkg), null)
            ?: return null
        return runCatching { json.decodeFromString<RideReaderProfile0700>(raw) }.getOrNull()
    }

    fun all(context: Context): List<RideReaderProfile0700> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(INDEX, emptySet()).orEmpty()
            .mapNotNull { read(context, it) }
            .sortedBy { it.packageName }
    }

    fun findByApkSha(context: Context, sha256: String): RideReaderProfile0700? {
        val normalized = sha256.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) return null
        return all(context).firstOrNull { it.apkSha256.equals(normalized, ignoreCase = true) && it.usable }
    }

    fun remove(context: Context, packageName: String) {
        val pkg = normalizePackage(packageName) ?: return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val index = prefs.getStringSet(INDEX, emptySet()).orEmpty().toMutableSet().apply { remove(pkg) }
        prefs.edit().remove(profileKey(pkg)).putStringSet(INDEX, index).apply()
    }

    private fun profileKey(pkg: String) = "profile:$pkg"
    private fun normalizePackage(value: String?): String? =
        value?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotBlank)
}

object LearnedRideReader0700 {
    const val CONTRACT_MARKER = "LEARNED_RIDE_READER_0700"
    const val APPLIED_MARKER = "LEARNED_RIDE_PROFILE_APPLIED_0700"
    const val LOCAL_ONLY_MARKER = "LEARNED_PROFILE_EXECUTES_LOCALLY_0700"

    data class Result(
        val text: String,
        val applied: Boolean,
        val pickup: String? = null,
        val destination: String? = null,
        val evidence: String = "",
    )

    fun apply(
        profile: RideReaderProfile0700?,
        packageName: String?,
        rawText: String,
    ): Result {
        if (profile == null || !profile.usable || rawText.isBlank()) return Result(rawText, false)
        if (!samePackage(profile.packageName, packageName)) return Result(rawText, false)

        val lines = rawText.lines()
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter(String::isNotBlank)
            .take(220)
        if (lines.isEmpty()) return Result(rawText, false)

        val destination = extractValue(lines, profile.destinationLabels)
        val pickup = extractValue(lines, profile.pickupLabels)
        if (destination == null && pickup == null) return Result(rawText, false)

        val additions = ArrayList<String>(3)
        pickup?.let { additions += "Origem: $it" }
        destination?.let { additions += "Destino: $it" }
        additions += APPLIED_MARKER
        return Result(
            text = buildString {
                append(rawText.trim())
                additions.forEach { append('\n').append(it) }
            },
            applied = true,
            pickup = pickup,
            destination = destination,
            evidence = "profileVersion=\${profile.profileVersion}; confidence=\${profile.confidence}",
        )
    }

    internal fun extractValue(lines: List<String>, labels: List<String>): String? {
        val safeLabels = labels.map(::canonical).filter(String::isNotBlank).distinct().take(40)
        if (safeLabels.isEmpty()) return null
        for (index in lines.indices) {
            val raw = lines[index]
            val canon = canonical(raw)
            for (label in safeLabels) {
                if (canon == label) {
                    val next = lines.drop(index + 1).firstOrNull { candidate ->
                        plausibleValue(candidate) && canonical(candidate) !in safeLabels
                    }
                    if (next != null) return next
                }
                if (canon.startsWith("$label ")) {
                    val rawLabel = findRawPrefix(raw, label)
                    if (rawLabel != null) {
                        val value = raw.substring(rawLabel.length)
                            .trim()
                            .trimStart(':', '-', '–', '—')
                            .trim()
                        if (plausibleValue(value)) return value
                    }
                }
            }
        }
        return null
    }

    private fun findRawPrefix(raw: String, canonicalLabel: String): String? {
        for (length in 1..raw.length) {
            if (canonical(raw.substring(0, length)) == canonicalLabel) return raw.substring(0, length)
        }
        return null
    }

    private fun plausibleValue(value: String): Boolean {
        val v = value.trim()
        if (v.length !in 4..260) return false
        if (Regex("(?iu)^R\\$\\s*\\d").containsMatchIn(v)) return false
        if (Regex("(?iu)^\\d+(?:[.,]\\d+)?\\s*(?:km|m|min|minutos?|seg|s)$").matches(v)) return false
        return v.any(Char::isLetter)
    }

    private fun samePackage(a: String?, b: String?): Boolean =
        a?.trim()?.lowercase(Locale.ROOT) == b?.trim()?.lowercase(Locale.ROOT)

    internal fun canonical(value: String): String = Normalizer
        .normalize(value.lowercase(Locale("pt", "BR")), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
