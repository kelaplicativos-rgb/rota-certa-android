package br.com.mapeiaia.rotacerta

import android.content.Context
import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
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
    const val RUNTIME_CAPTURE_ENRICHMENT_MARKER_0704 = "RUNTIME_CARD_CAPTURE_ENRICHMENT_0704"
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
        val stored = raw?.let {
            runCatching { json.decodeFromString<RideReaderProfile0700>(it) }.getOrNull()
        }
        val captures = ManualAppScreenCaptureStore.readForPackage(context.applicationContext, pkg)
        return RuntimeRideCardEvidence0704.enrich(pkg, stored, captures)
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

/**
 * Evidência capturada de um card real só enriquece o Reader declarativo.
 * Ela nunca decide cor, raio ou quilometragem e nunca autoriza outro package.
 * A tela atual ainda precisa satisfazer os gates de sessão/janela do FAROL.
 */
internal object RuntimeRideCardEvidence0704 {
    const val CONTRACT_MARKER = "RUNTIME_CARD_TEACHER_0704"
    private val SYNTHETIC_SHA = "0704".repeat(16)

    private val destinationLabelRegex = Regex(
        "(?iu)^(?:destino|destination|drop.?off|desembarque|chegada|para onde|onde vai|endere[cç]o destino)\\b.*",
    )
    private val pickupLabelRegex = Regex(
        "(?iu)^(?:origem|origin|pickup|pick.?up|embarque|partida|buscar em|retirada)\\b.*",
    )
    private val anchorRegex = Regex(
        "(?iu)\\b(?:corrida|viagem|ride|trip|pedido|order|solicita[cç][aã]o|oferta|offer|aceitar|accept|recusar|reject)\\b",
    )

    private data class Evidence(
        val pickupLabels: Set<String>,
        val destinationLabels: Set<String>,
        val anchors: Set<String>,
    ) {
        val useful: Boolean get() = destinationLabels.isNotEmpty() || anchors.isNotEmpty()
    }

    fun enrich(
        packageName: String,
        stored: RideReaderProfile0700?,
        captures: List<ManualAppScreenCapture>,
    ): RideReaderProfile0700? {
        if (captures.isEmpty()) return stored
        val evidence = captures.asSequence()
            .take(12)
            .mapNotNull { derive(it.textPreview) }
            .fold(
                Evidence(emptySet(), emptySet(), emptySet()),
            ) { acc, next ->
                Evidence(
                    pickupLabels = (acc.pickupLabels + next.pickupLabels).take(16).toSet(),
                    destinationLabels = (acc.destinationLabels + next.destinationLabels).take(16).toSet(),
                    anchors = (acc.anchors + next.anchors).take(24).toSet(),
                )
            }
        if (!evidence.useful) return stored

        val runtimeConfidence = if (evidence.destinationLabels.isNotEmpty()) 0.94 else 0.82
        return if (stored != null) {
            stored.copy(
                confidence = maxOf(stored.confidence, runtimeConfidence),
                pickupLabels = (stored.pickupLabels + evidence.pickupLabels).distinct().take(32),
                destinationLabels = (stored.destinationLabels + evidence.destinationLabels).distinct().take(32),
                rideAnchors = (stored.rideAnchors + evidence.anchors).distinct().take(40),
                resourceHints = (stored.resourceHints + CONTRACT_MARKER).distinct().take(40),
                reason = listOf(stored.reason, RideAppLearningStore0700.RUNTIME_CAPTURE_ENRICHMENT_MARKER_0704)
                    .filter(String::isNotBlank)
                    .joinToString("; ")
                    .take(360),
            )
        } else {
            RideReaderProfile0700(
                packageName = packageName,
                apkSha256 = SYNTHETIC_SHA,
                profileVersion = 2,
                confidence = runtimeConfidence,
                pickupLabels = evidence.pickupLabels.toList(),
                destinationLabels = evidence.destinationLabels.toList(),
                rideAnchors = evidence.anchors.toList(),
                resourceHints = listOf(CONTRACT_MARKER),
                provider = "runtime-card",
                model = "local-0704",
                reason = RideAppLearningStore0700.RUNTIME_CAPTURE_ENRICHMENT_MARKER_0704,
                learnedAtMillis = captures.maxOfOrNull(ManualAppScreenCapture::createdAtMillis) ?: 0L,
            )
        }
    }

    private fun derive(rawText: String): Evidence? {
        val lines = rawText.lines()
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter(String::isNotBlank)
            .take(240)
        if (lines.isEmpty()) return null

        val normalized = WrappedAddressTextNormalizer.normalize(rawText)
        val addresses = UniversalScreenAddressParser.findAddresses(normalized)
            .map(DestinationAddressIdentityPolicy::cleanDisplayAddress)
            .filter(String::isNotBlank)
            .distinctBy(LearnedRideReader0700::canonical)
        val canonicalAddresses = addresses.map(LearnedRideReader0700::canonical)

        fun precedingLabel(address: String, regex: Regex): String? {
            val addressKey = LearnedRideReader0700.canonical(address)
            val index = lines.indexOfFirst { line ->
                val key = LearnedRideReader0700.canonical(line)
                key == addressKey || key.contains(addressKey) || addressKey.contains(key)
            }
            if (index <= 0) return null
            return lines.subList(maxOf(0, index - 2), index)
                .asReversed()
                .firstOrNull { candidate ->
                    candidate.length in 3..80 &&
                        regex.containsMatchIn(candidate) &&
                        canonicalAddresses.none { it == LearnedRideReader0700.canonical(candidate) }
                }
        }

        val pickupLabels = LinkedHashSet<String>()
        val destinationLabels = LinkedHashSet<String>()
        if (addresses.size >= 2) {
            precedingLabel(addresses.first(), pickupLabelRegex)?.let(pickupLabels::add)
            precedingLabel(addresses.last(), destinationLabelRegex)?.let(destinationLabels::add)
        } else if (addresses.size == 1) {
            precedingLabel(addresses.single(), destinationLabelRegex)?.let(destinationLabels::add)
        }

        val anchors = lines.asSequence()
            .filter { it.length in 3..90 }
            .filter(anchorRegex::containsMatchIn)
            .filterNot { line -> addresses.any { LearnedRideReader0700.canonical(line).contains(LearnedRideReader0700.canonical(it)) } }
            .take(16)
            .toCollection(LinkedHashSet())

        val evidence = Evidence(pickupLabels, destinationLabels, anchors)
        return evidence.takeIf(Evidence::useful)
    }
}

object LearnedRideReader0700 {
    const val CONTRACT_MARKER = "LEARNED_RIDE_READER_0700"
    const val APPLIED_MARKER = "LEARNED_RIDE_PROFILE_APPLIED_0700"
    const val LOCAL_ONLY_MARKER = "LEARNED_PROFILE_EXECUTES_LOCALLY_0700"
    const val DESTINATION_ONLY_MARKER = "LEARNED_DESTINATION_ONLY_0703"
    const val UNIQUE_ADDRESS_MARKER = "LEARNED_UNIQUE_ADDRESS_CARD_0703"
    const val TWO_ADDRESS_MARKER_0704 = "LEARNED_TWO_ADDRESS_CARD_0704"

    data class Result(
        val text: String,
        val applied: Boolean,
        val pickup: String? = null,
        val destination: String? = null,
        val evidence: String = "",
    )

    private data class DestinationInference0704(
        val value: String,
        val kind: String,
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

        val labeledDestination = extractValue(lines, profile.destinationLabels)
        val pickup = extractValue(lines, profile.pickupLabels)
        val inferred = if (labeledDestination == null) {
            inferDestination0704(profile, rawText)
        } else null
        val destination = labeledDestination ?: inferred?.value
        if (destination == null && pickup == null) return Result(rawText, false)

        val additions = ArrayList<String>(6)
        pickup?.let { additions += "Origem: $it" }
        destination?.let { additions += "Destino: $it" }
        if (destination != null && pickup == null) additions += DESTINATION_ONLY_MARKER
        if (inferred?.kind == "unique") additions += UNIQUE_ADDRESS_MARKER
        if (inferred?.kind == "two-address") additions += TWO_ADDRESS_MARKER_0704
        additions += APPLIED_MARKER
        return Result(
            text = buildString {
                append(rawText.trim())
                additions.forEach { append('\n').append(it) }
            },
            applied = true,
            pickup = pickup,
            destination = destination,
            evidence = "profileVersion=" + profile.profileVersion +
                "; confidence=" + profile.confidence +
                "; destinationOnly=" + (destination != null && pickup == null) +
                "; uniqueAddress=" + (inferred?.kind == "unique") +
                "; twoAddress=" + (inferred?.kind == "two-address"),
        )
    }

    internal fun inferUniqueDestination0703(profile: RideReaderProfile0700, rawText: String): String? {
        if (profile.confidence < 0.75) return null
        val addresses = parsedAddresses0704(rawText)
        if (addresses.size != 1) return null
        if (!hasStrongCardEvidence0704(profile, rawText, requireAction = false)) return null
        return addresses.single()
    }

    internal fun inferTwoAddressDestination0704(profile: RideReaderProfile0700, rawText: String): String? {
        if (profile.profileVersion < 2) return null
        if (profile.confidence < 0.80) return null
        val addresses = parsedAddresses0704(rawText)
        if (addresses.size != 2) return null
        if (!hasStrongCardEvidence0704(profile, rawText, requireAction = true)) return null

        val acceptLikeCount = Regex("(?iu)\\b(?:aceitar|accept|ofere(?:ç|c)a|offer|recusar|reject)\\b")
            .findAll(rawText)
            .count()
        val moneyCount = Regex("(?iu)R\\$\\s*\\d").findAll(rawText).count()
        if (acceptLikeCount > 2 || moneyCount > 3) return null

        return addresses.last()
    }

    private fun inferDestination0704(
        profile: RideReaderProfile0700,
        rawText: String,
    ): DestinationInference0704? {
        inferUniqueDestination0703(profile, rawText)?.let {
            return DestinationInference0704(it, "unique")
        }
        inferTwoAddressDestination0704(profile, rawText)?.let {
            return DestinationInference0704(it, "two-address")
        }
        return null
    }

    private fun parsedAddresses0704(rawText: String): List<String> =
        UniversalScreenAddressParser.findAddresses(
            WrappedAddressTextNormalizer.normalize(rawText),
        ).map(DestinationAddressIdentityPolicy::cleanDisplayAddress)
            .filter(String::isNotBlank)
            .distinctBy(::canonical)

    private fun hasStrongCardEvidence0704(
        profile: RideReaderProfile0700,
        rawText: String,
        requireAction: Boolean,
    ): Boolean {
        val canonicalText = canonical(rawText)
        val learnedCues = (
            profile.rideAnchors + profile.actionLabels + profile.fareLabels +
                profile.distanceLabels + profile.destinationLabels + profile.pickupLabels
            ).map(::canonical).filter { it.length >= 3 }.distinct().take(120)
        val learnedCueVisible = learnedCues.any { cue -> canonicalText.contains(cue) }

        val actionVisible = profile.actionLabels
            .map(::canonical)
            .filter { it.length >= 3 }
            .any(canonicalText::contains) ||
            Regex("(?iu)\\b(?:aceitar|accept|ofere(?:ç|c)a|offer|recusar|reject)\\b").containsMatchIn(rawText)

        val genericSignals = listOf(
            Regex("(?iu)R\\$\\s*\\d").containsMatchIn(rawText),
            Regex("(?iu)\\b(?:corrida|viagem|ride|trip|aceitar|accept|recusar|reject|oferta|offer|solicita[cç][aã]o|pedido|order)\\b")
                .containsMatchIn(rawText),
            Regex("(?iu)\\b\\d+(?:[.,]\\d+)?\\s*(?:km|min|minutos?)\\b").containsMatchIn(rawText),
        ).count { it }

        val profileHasCardEvidence = profile.rideAnchors.isNotEmpty() ||
            profile.actionLabels.isNotEmpty() ||
            profile.resourceHints.isNotEmpty()
        if (!profileHasCardEvidence) return false
        if (!learnedCueVisible && genericSignals < 2) return false
        if (requireAction && !actionVisible && genericSignals < 3) return false
        return true
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
