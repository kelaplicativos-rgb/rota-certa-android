package br.com.mapeiaia.rotacerta

import java.security.MessageDigest
import java.text.Normalizer
import java.util.LinkedHashMap
import java.util.Locale

/** A bounded, text-only representation of one accessibility node on a failed card. */
data class FailedCardNodeLine0161(
    val text: String,
    val top: Int,
    val left: Int,
    val bottom: Int,
    val right: Int,
    val className: String = "",
    val viewId: String = "",
)

data class FailedCardLayoutModel0161(
    val packageName: String,
    val originMarker: String,
    val destinationMarker: String,
    val originOffset: Int,
    val destinationOffset: Int,
    val structureKey: String,
    val confidence: Int,
)

data class FailedCardRecoveryResult0161(
    val fields: RideFields,
    val strategy: String,
    val confidence: Int,
    val modelCandidate: FailedCardLayoutModel0161? = null,
)

object TransientOverlayPackagePolicy0161 {
    private val exactPackages = setOf(
        "android",
        "com.android.systemui",
        "com.samsung.android.app.smartcapture",
        "com.google.android.projection.gearhead",
    )

    fun shouldPreferSelectedRoot(
        eventPackageName: String?,
        rootPackageName: String?,
        selectedPackages: Set<String>,
        ownPackageName: String,
    ): Boolean {
        val event = normalize(eventPackageName) ?: return false
        val root = normalize(rootPackageName) ?: return false
        val own = normalize(ownPackageName)
        if (event == root || root !in selectedPackages.mapNotNull(::normalize).toSet()) return false
        return event == own || isTransient(event)
    }

    fun isTransient(packageName: String?): Boolean {
        val normalized = normalize(packageName) ?: return false
        return normalized in exactPackages ||
            normalized.contains("inputmethod") ||
            normalized.contains("keyboard") ||
            normalized.contains("launcher") ||
            normalized.contains("keyguard") ||
            normalized.contains("notification")
    }

    private fun normalize(value: String?): String? = value
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf(String::isNotBlank)
}

/**
 * One-shot gate for failed-card screenshots. It never starts a continuous loop.
 * A timed-out reservation is released so a dead Android screenshot callback cannot
 * permanently block later cards.
 */
class FailedCardAutoCaptureGate0161(
    private val lockTimeoutMillis: Long = 4_000L,
    private val retentionMillis: Long = 120_000L,
    private val maxEntries: Int = 32,
) {
    private data class Entry(
        var startedAtMillis: Long = 0L,
        var completedAtMillis: Long = 0L,
    )

    private val entries = LinkedHashMap<String, Entry>()

    @Synchronized
    fun tryStart(
        signature: String,
        probableCard: Boolean,
        parserActive: Boolean,
        routeInFlight: Boolean,
        hasDecision: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        if (signature.isBlank() || !probableCard || parserActive || routeInFlight || hasDecision) return false
        prune(nowMillis)
        val entry = entries.getOrPut(signature) { Entry() }
        if (entry.completedAtMillis > 0L) return false
        if (entry.startedAtMillis > 0L && nowMillis - entry.startedAtMillis < lockTimeoutMillis) return false
        entry.startedAtMillis = nowMillis
        trimToLimit()
        return true
    }

    @Synchronized
    fun finish(signature: String, nowMillis: Long = System.currentTimeMillis()) {
        val entry = entries.getOrPut(signature) { Entry() }
        entry.startedAtMillis = 0L
        entry.completedAtMillis = nowMillis
        trimToLimit()
    }

    @Synchronized
    fun releaseForRetry(signature: String) {
        entries[signature]?.startedAtMillis = 0L
    }

    @Synchronized
    fun reset() = entries.clear()

    @Synchronized
    fun hasCompleted(signature: String): Boolean = entries[signature]?.completedAtMillis?.let { it > 0L } == true

    private fun prune(nowMillis: Long) {
        entries.entries.removeAll { (_, entry) ->
            val reference = maxOf(entry.startedAtMillis, entry.completedAtMillis)
            reference > 0L && nowMillis >= reference && nowMillis - reference > retentionMillis
        }
    }

    private fun trimToLimit() {
        val excess = (entries.size - maxEntries).coerceAtLeast(0)
        entries.keys.take(excess).toList().forEach(entries::remove)
    }
}

object FailedCardRecoveryEngine0161 {
    private val originMarkers = listOf(
        "ponto de partida",
        "local de embarque",
        "endereco de partida",
        "endereço de partida",
        "origem",
        "embarque",
        "partida",
        "buscar",
        "pegue",
        "a",
    )
    private val destinationMarkers = listOf(
        "destino final",
        "local de destino",
        "endereco de destino",
        "endereço de destino",
        "destino",
        "chegada",
        "desembarque",
        "levar",
        "b",
    )
    private val rideMarkerRegex = Regex(
        "\\b(?:aceitar|ofere[cç]a|pedido\\s+de\\s+viagem|corrida|viagem|tarifa|pre[cç]o|passageiro|embarque|destino|origem|partida|chegada)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val moneyRegex = Regex("(?:R\\$\\s*\\d+|\\b\\d+[,.]\\d{2}\\s*(?:reais?|R\\$))", RegexOption.IGNORE_CASE)
    private val distanceRegex = Regex("\\b\\d+(?:[,.]\\d+)?\\s*(?:km|m|min|minutos?)\\b", RegexOption.IGNORE_CASE)
    private val timeRegex = Regex("\\b(?:[01]?\\d|2[0-3])[:h]\\d{2}\\b", RegexOption.IGNORE_CASE)
    private val phoneRegex = Regex("(?<!\\d)(?:\\+?55\\s*)?(?:\\(?\\d{2}\\)?\\s*)?(?:9\\s*)?\\d{4}[\\s-]?\\d{4}(?!\\d)")
    private val locationCueRegex = Regex(
        "\\b(?:rua|r\\.|avenida|av\\.|alameda|travessa|estrada|rodovia|bairro|jardim|vila|centro|parque|condom[ií]nio|residencial|loteamento|shopping|terminal|esta[cç][aã]o|aeroporto|rodovi[aá]ria|hospital|mercado|posto|igreja|escola|faculdade|universidade|s[ií]tio|fazenda)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val controlNoiseRegex = Regex(
        "^(?:aceitar|recusar|fechar|cancelar|voltar|detalhes|mais|menos|mapa|navegar|copiar|compartilhar|ofere[cç]a|confirmar|editar|excluir)$",
        RegexOption.IGNORE_CASE,
    )

    fun probableRideCard(text: String, packageName: String?): Boolean {
        if (packageName.isNullOrBlank() || text.length < 60) return false
        val markerCount = rideMarkerRegex.findAll(text).map { canonical(it.value) }.distinct().count()
        val metrics = listOf(moneyRegex, distanceRegex, timeRegex).count { it.containsMatchIn(text) }
        val hasBothLabels = containsAnyMarker(text, originMarkers) && containsAnyMarker(text, destinationMarkers)
        return hasBothLabels || markerCount >= 2 || (markerCount >= 1 && metrics >= 1) || metrics >= 2
    }

    fun signature(
        packageName: String,
        windowId: Int,
        text: String,
        nodes: List<FailedCardNodeLine0161>,
    ): String {
        val normalizedNodes = nodes.asSequence()
            .take(120)
            .map { node ->
                listOf(
                    normalizeDynamic(node.text),
                    (node.top / 24).toString(),
                    (node.left / 24).toString(),
                    canonical(node.className),
                ).joinToString(":")
            }
            .filter(String::isNotBlank)
            .joinToString("|")
        val payload = listOf(
            canonical(packageName),
            windowId.toString(),
            normalizeDynamic(text),
            normalizedNodes,
        ).joinToString("\u001E")
        return sha256(payload).take(24)
    }

    fun structureKey(nodes: List<FailedCardNodeLine0161>, text: String): String {
        val basis = if (nodes.isNotEmpty()) {
            nodes.asSequence()
                .take(120)
                .map { node -> "${normalizeDynamic(node.text)}@${node.top / 32}:${node.left / 32}" }
                .joinToString("|")
        } else {
            text.lineSequence().take(80).joinToString("|") { normalizeDynamic(it) }
        }
        return sha256(basis).take(20)
    }

    fun recover(
        packageName: String,
        savedPackages: Set<String>,
        accessibilityText: String,
        ocrText: String,
        nodes: List<FailedCardNodeLine0161>,
        knownModels: List<FailedCardLayoutModel0161> = emptyList(),
    ): FailedCardRecoveryResult0161? {
        if (packageName !in savedPackages) return null

        knownModels.asSequence()
            .filter { it.packageName == packageName }
            .forEach { model ->
                recoverWithModel(accessibilityText, model)?.let { fields ->
                    return FailedCardRecoveryResult0161(
                        fields = fields,
                        strategy = "modelo_local",
                        confidence = model.confidence,
                        modelCandidate = model,
                    )
                }
            }

        val merged = merge(accessibilityText, ocrText, nodes)
        if (!probableRideCard(merged, packageName)) return null
        val lines = orderedLines(accessibilityText, ocrText, nodes)
        if (hasAmbiguousMarkedLocation(lines, originMarkers) ||
            hasAmbiguousMarkedLocation(lines, destinationMarkers)
        ) return null

        val mergedEvaluation = SimpleSavedAppFarolPolicy.evaluate(packageName, savedPackages, merged)
        if (mergedEvaluation.active) {
            return FailedCardRecoveryResult0161(
                fields = RideFields(
                    pickup = mergedEvaluation.pickup,
                    destination = mergedEvaluation.destination,
                ),
                strategy = "acessibilidade_mais_ocr",
                confidence = 100,
            )
        }

        val origin = extractMarkedLocation(lines, originMarkers) ?: return null
        val destination = extractMarkedLocation(lines, destinationMarkers) ?: return null
        if (canonical(origin.value) == canonical(destination.value)) return null

        val evidence = UniversalRideCardEvidencePolicy.evaluate(
            text = merged,
            addresses = listOf(origin.value, destination.value),
            destination = destination.value,
            packageName = packageName,
        )
        if (!evidence.accepted && !(origin.strong && destination.strong && probableRideCard(merged, packageName))) return null

        val model = FailedCardLayoutModel0161(
            packageName = packageName,
            originMarker = origin.marker,
            destinationMarker = destination.marker,
            originOffset = origin.offset,
            destinationOffset = destination.offset,
            structureKey = structureKey(nodes, merged),
            confidence = if (evidence.accepted) 95 else 90,
        )
        return FailedCardRecoveryResult0161(
            fields = RideFields(pickup = origin.value, destination = destination.value),
            strategy = "marcadores_confirmados",
            confidence = model.confidence,
            modelCandidate = model,
        )
    }

    fun recoverWithModel(text: String, model: FailedCardLayoutModel0161): RideFields? {
        val lines = orderedLines(text, "", emptyList())
        val origin = extractByStoredMarker(lines, model.originMarker, model.originOffset) ?: return null
        val destination = extractByStoredMarker(lines, model.destinationMarker, model.destinationOffset) ?: return null
        if (canonical(origin) == canonical(destination)) return null
        return RideFields(pickup = origin, destination = destination)
    }

    private data class MarkedLocation(
        val value: String,
        val marker: String,
        val offset: Int,
        val strong: Boolean,
    )

    private fun hasAmbiguousMarkedLocation(lines: List<String>, markers: List<String>): Boolean {
        val candidates = linkedSetOf<String>()
        lines.forEachIndexed { index, raw ->
            val line = clean(raw)
            val marker = markers.firstOrNull { markerMatches(line, it) } ?: return@forEachIndexed
            val sameLine = remainderAfterMarker(line, marker)
            if (sameLine.isNotBlank() && safeLocation(sameLine, strongMarker = marker.length > 1)) {
                candidates += canonical(sameLine)
            } else {
                for (offset in 1..2) {
                    val candidate = lines.getOrNull(index + offset)?.let(::clean).orEmpty()
                    if (safeLocation(candidate, strongMarker = marker.length > 1)) {
                        candidates += canonical(candidate)
                        break
                    }
                    if (candidate.isNotBlank() && isAnotherMarker(candidate)) break
                }
            }
        }
        return candidates.size > 1
    }

    private fun extractMarkedLocation(lines: List<String>, markers: List<String>): MarkedLocation? {
        val matches = mutableListOf<MarkedLocation>()
        lines.forEachIndexed { index, raw ->
            val line = clean(raw)
            val marker = markers.firstOrNull { markerMatches(line, it) } ?: return@forEachIndexed
            val sameLine = remainderAfterMarker(line, marker)
            if (sameLine.isNotBlank() && safeLocation(sameLine, strongMarker = marker.length > 1)) {
                matches += MarkedLocation(sameLine, canonical(marker), 0, marker.length > 1)
                return@forEachIndexed
            }
            for (offset in 1..2) {
                val candidate = lines.getOrNull(index + offset)?.let(::clean).orEmpty()
                if (safeLocation(candidate, strongMarker = marker.length > 1)) {
                    matches += MarkedLocation(candidate, canonical(marker), offset, marker.length > 1)
                    break
                }
                if (candidate.isNotBlank() && isAnotherMarker(candidate)) break
            }
        }
        return matches.distinctBy { canonical(it.value) }.singleOrNull()
    }

    private fun extractByStoredMarker(lines: List<String>, marker: String, offset: Int): String? {
        lines.forEachIndexed { index, raw ->
            val line = clean(raw)
            if (!markerMatches(line, marker)) return@forEachIndexed
            val sameLine = remainderAfterMarker(line, marker)
            if (offset == 0 && safeLocation(sameLine, strongMarker = marker.length > 1)) return sameLine
            val candidate = lines.getOrNull(index + offset)?.let(::clean).orEmpty()
            if (safeLocation(candidate, strongMarker = marker.length > 1)) return candidate
        }
        return null
    }

    private fun orderedLines(
        accessibilityText: String,
        ocrText: String,
        nodes: List<FailedCardNodeLine0161>,
    ): List<String> {
        val ordered = mutableListOf<String>()
        accessibilityText.lineSequence().map(::clean).filter(String::isNotBlank).forEach(ordered::add)
        nodes.sortedWith(compareBy<FailedCardNodeLine0161> { it.top }.thenBy { it.left })
            .map { clean(it.text) }
            .filter(String::isNotBlank)
            .forEach(ordered::add)
        ocrText.lineSequence().map(::clean).filter(String::isNotBlank).forEach(ordered::add)
        return ordered.toList()
    }

    private fun merge(
        accessibilityText: String,
        ocrText: String,
        nodes: List<FailedCardNodeLine0161>,
    ): String = orderedLines(accessibilityText, ocrText, nodes).joinToString("\n")

    private fun containsAnyMarker(text: String, markers: List<String>): Boolean =
        text.lineSequence().map(::clean).any { line -> markers.any { marker -> markerMatches(line, marker) } }

    private fun markerMatches(line: String, marker: String): Boolean {
        val normalized = canonical(line)
        val canonicalMarker = canonical(marker)
        if (canonicalMarker.length == 1) return normalized == canonicalMarker || normalized.startsWith("$canonicalMarker ")
        return normalized == canonicalMarker ||
            normalized.startsWith("$canonicalMarker ") ||
            normalized.startsWith("$canonicalMarker:") ||
            normalized.startsWith("$canonicalMarker -")
    }

    private fun remainderAfterMarker(line: String, marker: String): String {
        val trimmed = line.trim()
        val markerRegex = Regex("^\\s*${Regex.escape(marker)}\\s*[:\\-–—]?\\s*", RegexOption.IGNORE_CASE)
        return trimmed.replaceFirst(markerRegex, "").trim()
    }

    private fun isAnotherMarker(value: String): Boolean =
        originMarkers.any { markerMatches(value, it) } || destinationMarkers.any { markerMatches(value, it) }

    private fun safeLocation(value: String, strongMarker: Boolean): Boolean {
        val cleaned = clean(value)
        if (cleaned.length !in 5..160) return false
        if (controlNoiseRegex.matches(cleaned)) return false
        if (moneyRegex.containsMatchIn(cleaned) || timeRegex.containsMatchIn(cleaned) || phoneRegex.containsMatchIn(cleaned)) return false
        if (distanceRegex.matches(cleaned)) return false
        if (UniversalScreenAddressParser.isRecognizedAddress(cleaned)) return true
        val words = canonical(cleaned).split(Regex("\\s+")).filter { it.length >= 2 }
        if (words.size < 2) return false
        val hasLocationCue = locationCueRegex.containsMatchIn(cleaned) ||
            cleaned.contains(',') ||
            cleaned.contains('(') ||
            Regex("\\b\\d{1,6}\\b").containsMatchIn(cleaned)
        return hasLocationCue && (strongMarker || words.size >= 3)
    }

    private fun clean(value: String): String = value
        .replace('\u00A0', ' ')
        .replace('\u202F', ' ')
        .replace(Regex("\\s+"), " ")
        .trim(' ', ',', ';', '-', '–', '—')

    private fun normalizeDynamic(value: String): String = canonical(value)
        .replace(Regex("\\b\\d+(?:[,.]\\d+)?\\b"), "#")
        .replace(Regex("\\s+"), " ")
        .take(2_000)

    private fun canonical(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}:]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
