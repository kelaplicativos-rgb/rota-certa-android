package br.com.mapeiaia.rotacerta

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

object FarolCardSignatureCompiler638 {
    private val rideAnchors = linkedMapOf(
        "accept" to Regex("\\b(?:aceitar|confirmar|pegar|iniciar)\\b", RegexOption.IGNORE_CASE),
        "decline" to Regex("\\b(?:recusar|rejeitar|ignorar)\\b", RegexOption.IGNORE_CASE),
        "ride" to Regex("\\b(?:pedido de viagem|pedido de corrida|nova corrida|nova viagem|corrida|viagem)\\b", RegexOption.IGNORE_CASE),
        "passenger" to Regex("\\bpassageir[oa]\\b", RegexOption.IGNORE_CASE),
        "fare" to Regex("R\\$\\s*\\d", RegexOption.IGNORE_CASE),
        "distance" to Regex("\\b\\d+(?:[.,]\\d+)?\\s*km\\b", RegexOption.IGNORE_CASE),
        "eta" to Regex("\\b\\d{1,3}\\s*(?:min|minutos?)\\b", RegexOption.IGNORE_CASE),
        "rating" to Regex("\\b[1-5][.,]\\d\\b"),
        "profile" to Regex("\\b(?:perfil|uberx|comfort|confort|essencial|moto|flash)\\b", RegexOption.IGNORE_CASE),
    )

    fun compileProbe(
        packageName: String,
        text: String,
        nodes: List<FailedCardNodeLine0161>,
    ): FarolCardSignatureProbe638 {
        val normalizedPackage = SelectedRideAppStore.normalize(packageName).orEmpty()
        val mergedText = buildString {
            append(text.take(8_000))
            nodes.asSequence().take(96).forEach { append('\n').append(it.text.take(300)) }
        }
        val anchors = rideAnchors.mapNotNullTo(linkedSetOf()) { (id, regex) ->
            id.takeIf { regex.containsMatchIn(mergedText) }
        }
        val structures = nodes.asSequence()
            .take(96)
            .map { node ->
                listOf(
                    simpleClass(node.className),
                    simpleViewId(node.viewId),
                    bucket(node.left, 48),
                    bucket(node.top, 48),
                    bucket((node.right - node.left).coerceAtLeast(0), 64),
                    bucket((node.bottom - node.top).coerceAtLeast(0), 48),
                    textShape(node.text),
                ).joinToString(":")
            }
            .filter { it.isNotBlank() }
            .toCollection(linkedSetOf())
        return FarolCardSignatureProbe638(
            packageName = normalizedPackage,
            anchorTokens = anchors,
            structureTokens = structures,
        )
    }

    fun newModel(
        packageName: String,
        text: String,
        nodes: List<FailedCardNodeLine0161>,
        visualHash: String?,
        nowMillis: Long,
    ): FarolCardSignatureModel638 {
        val probe = compileProbe(packageName, text, nodes)
        require(probe.packageName.isNotBlank()) { "package vazio" }
        require(probe.structureTokens.size >= 4) { "estrutura insuficiente" }
        val fingerprint = sha256(
            probe.packageName + "|" +
                probe.anchorTokens.sorted().joinToString(",") + "|" +
                probe.structureTokens.sorted().take(80).joinToString(","),
        ).take(20)
        val confidence = (62 + probe.anchorTokens.size * 5 + probe.structureTokens.size.coerceAtMost(20)).coerceAtMost(94)
        return FarolCardSignatureModel638(
            id = "$nowMillis-$fingerprint",
            packageName = probe.packageName,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
            sampleCount = 1,
            anchorTokens = probe.anchorTokens,
            structureTokens = probe.structureTokens,
            visualHash = visualHash,
            confidence = confidence,
        )
    }

    fun mergeIfSameFamily(
        existing: FarolCardSignatureModel638,
        incoming: FarolCardSignatureModel638,
    ): FarolCardSignatureModel638? {
        if (existing.packageName != incoming.packageName) return null
        val structureSimilarity = jaccard(existing.structureTokens, incoming.structureTokens)
        val anchorSimilarity = jaccard(existing.anchorTokens, incoming.anchorTokens)
        if (structureSimilarity < 0.56 && anchorSimilarity < 0.60) return null
        val stableStructure = existing.structureTokens.intersect(incoming.structureTokens)
            .takeIf { it.size >= 4 }
            ?: (existing.structureTokens + incoming.structureTokens).take(80).toSet()
        val stableAnchors = existing.anchorTokens.intersect(incoming.anchorTokens)
            .takeIf { it.isNotEmpty() }
            ?: (existing.anchorTokens + incoming.anchorTokens)
        return existing.copy(
            updatedAtMillis = incoming.updatedAtMillis,
            sampleCount = existing.sampleCount + 1,
            anchorTokens = stableAnchors,
            structureTokens = stableStructure,
            visualHash = incoming.visualHash ?: existing.visualHash,
            confidence = (existing.confidence + 3).coerceAtMost(99),
        )
    }

    internal fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.intersect(b).size.toDouble()
        val union = (a + b).size.toDouble().coerceAtLeast(1.0)
        return intersection / union
    }

    private fun textShape(value: String): String {
        val raw = value.trim()
        val canonical = canonical(raw)
            .replace(Regex("\\b\\d+(?:[.,]\\d+)?\\b"), "#")
            .replace(Regex("\\s+"), " ")
            .take(96)
        if (canonical.isBlank()) return "_"
        val anchors = rideAnchors.mapNotNull { (id, regex) -> id.takeIf { regex.containsMatchIn(raw) } }
        return when {
            anchors.isNotEmpty() -> "anchor:" + anchors.sorted().joinToString("+")
            Regex("R\\$\\s*\\d", RegexOption.IGNORE_CASE).containsMatchIn(raw) -> "money"
            Regex("\\b\\d+(?:[.,]\\d+)?\\s*km\\b", RegexOption.IGNORE_CASE).containsMatchIn(raw) -> "distance"
            Regex("\\b\\d{1,3}\\s*(?:min|minutos?)\\b", RegexOption.IGNORE_CASE).containsMatchIn(raw) -> "eta"
            Regex("\\b(?:rua|r\\.|avenida|av\\.|alameda|travessa|estrada|rodovia|bairro|jardim|vila|centro|parque|shopping|terminal|estacao|estação|aeroporto|rodoviaria|rodoviária|hospital|posto)\\b", RegexOption.IGNORE_CASE).containsMatchIn(raw) -> "location"
            raw.contains(',') && Regex("\\b\\d{1,6}\\b").containsMatchIn(raw) -> "location"
            canonical.length > 28 -> "dynamic_text"
            else -> canonical
        }
    }

    private fun simpleClass(value: String): String = value.substringAfterLast('.').lowercase(Locale.ROOT).take(32)
    private fun simpleViewId(value: String): String = value.substringAfterLast('/').lowercase(Locale.ROOT).take(48)
    private fun bucket(value: Int, size: Int): String = (value.coerceAtLeast(0) / size).toString()

    private fun canonical(value: String): String = Normalizer
        .normalize(value.lowercase(Locale("pt", "BR")), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

object FarolCardSignatureMatcher638 {
    fun match(
        packageName: String,
        text: String,
        nodes: List<FailedCardNodeLine0161>,
        models: List<FarolCardSignatureModel638>,
    ): FarolCardSignatureMatch638 {
        if (models.isEmpty()) return FarolCardSignatureMatch638(false, reason = "untrained_package")
        val probe = FarolCardSignatureCompiler638.compileProbe(packageName, text, nodes)
        var bestModel: FarolCardSignatureModel638? = null
        var bestScore = 0.0
        models.asSequence()
            .filter { it.packageName == probe.packageName }
            .forEach { model ->
                val structure = FarolCardSignatureCompiler638.jaccard(model.structureTokens, probe.structureTokens)
                val anchors = FarolCardSignatureCompiler638.jaccard(model.anchorTokens, probe.anchorTokens)
                val score = structure * 0.72 + anchors * 0.28
                if (score > bestScore) {
                    bestScore = score
                    bestModel = model
                }
            }
        val model = bestModel ?: return FarolCardSignatureMatch638(false, score = 0.0, reason = "no_model_for_package")
        val enoughAnchors = model.anchorTokens.isEmpty() || probe.anchorTokens.intersect(model.anchorTokens).isNotEmpty()
        val matched = bestScore >= 0.50 && enoughAnchors
        return FarolCardSignatureMatch638(
            matched = matched,
            modelId = model.id.takeIf { matched },
            score = bestScore,
            reason = if (matched) "trained_signature_match" else "trained_signature_miss",
        )
    }
}
