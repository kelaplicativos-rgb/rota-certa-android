package br.com.mapeiaia.rotacerta.core

import br.com.mapeiaia.rotacerta.FastRideCardMatcher
import br.com.mapeiaia.rotacerta.RideCardTemplate
import br.com.mapeiaia.rotacerta.RideCardTemplateMatch
import br.com.mapeiaia.rotacerta.RideCardTemplateMatcher
import java.text.Normalizer
import java.util.Locale

/**
 * Motor de match/assinatura de card cadastrado.
 * Ele decide se a leitura atual e realmente um card individual cadastrado.
 * Lista/feed nunca deve liberar rota, cache, verde ou vermelho.
 */
object CoreCardMatchEngine {
    private val acceptButtonRegex = Regex("aceitar\\s+por\\s+r\\$\\s*\\d+", RegexOption.IGNORE_CASE)
    private val moneyRegex = Regex("r\\$\\s*\\d+", RegexOption.IGNORE_CASE)
    private val distanceRegex = Regex("\\b\\d+(?:[,.]\\d+)?\\s*km\\b", RegexOption.IGNORE_CASE)
    private val markerLineRegex = Regex("^\\s*[ab](?:\\s+.+)?$", RegexOption.IGNORE_CASE)
    private val titleRegex = Regex("pedido[s]?\\s+de\\s+viagem", RegexOption.IGNORE_CASE)
    private val addressRegex = Regex(
        "(?:\\b(?:rua|avenida|rodovia|estrada|travessa|alameda|praca|praça|bairro|jardim|cidade|parque|terminal|estacao|estação|condominio|condomínio)\\b|\\b(?:r|av)\\.)",
        RegexOption.IGNORE_CASE,
    )

    private val rideSignals = setOf(
        "pedido de viagem",
        "aceitar",
        "aceitar por",
        "ofereca sua tarifa",
        "preco justo",
        "pix",
    )

    private val routeSignals = setOf(
        "card.crop.route_block",
        "card.route.address",
        "card.route.two_addresses",
        "card.route.two_distances",
        "card.route.two_times",
        "card.route.ab_markers",
        "card.route.marked_stops",
        "distancia em km",
        "tempo de rota",
        "endereco",
    )

    fun match(
        text: String,
        packageName: String?,
        templates: List<RideCardTemplate>,
    ): CoreCardMatchResult {
        val normalizedPackage = CorePackageMonitor.normalize(packageName)
        if (normalizedPackage.isNullOrBlank()) {
            return CoreCardMatchResult.rejected("Pacote do card nao identificado pelo Core.")
        }
        if (templates.isEmpty()) {
            return CoreCardMatchResult.rejected("Nenhum card cadastrado para comparar com a tela atual.")
        }

        val preparedText = CoreRideTextSanitizer.sanitize(text, normalizedPackage)
        val normalizedText = preparedText.normalizedCoreText()
        if (isListLikeRideFeed(preparedText, normalizedText)) {
            return CoreCardMatchResult.rejected(
                reason = "Tela parece lista/feed de corridas; somente card individual cadastrado libera o farol.",
                isListLike = true,
                contractName = CoreRideCardContractRegistry.contractFor(normalizedPackage).name,
            )
        }

        val detectedFeatures = RideCardTemplateMatcher.featuresFor(preparedText)
        val features = if ("card.crop.route_block" in detectedFeatures || !hasStrongOpenedCardEvidence(preparedText, normalizedText)) {
            detectedFeatures
        } else {
            detectedFeatures + "card.crop.route_block"
        }
        val contract = CoreRideCardContractRegistry.contractFor(normalizedPackage)
        val contractResult = contract.evaluate(preparedText, normalizedPackage, features)
        if (!contractResult.accepted) {
            return CoreCardMatchResult.rejected(
                reason = contractResult.reason,
                isListLike = contractResult.isListLike,
                contractName = contractResult.contractName,
            )
        }
        if ("card.crop.route_block" !in features) {
            return CoreCardMatchResult.rejected(
                reason = "Leitura ainda nao contem bloco individual de rota do card cadastrado.",
                contractName = contract.name,
            )
        }

        val strictMatch = RideCardTemplateMatcher.match(preparedText, normalizedPackage, templates)
        val match = strictMatch
            ?: FastRideCardMatcher.match(preparedText, normalizedPackage, templates)
            ?: registeredFamilyFallback(normalizedPackage, templates, features)
        if (match == null) {
            return CoreCardMatchResult.rejected(
                reason = "Tela parece card de corrida, mas ainda nao bate com nenhum card cadastrado.",
                contractName = contract.name,
            )
        }
        if (!belongsToPackage(match.template, normalizedPackage)) {
            return CoreCardMatchResult.rejected(
                reason = "Card encontrado pertence a outro pacote; leitura bloqueada pelo Core.",
                contractName = contract.name,
            )
        }
        return CoreCardMatchResult.accepted(
            match = match,
            reason = "Card individual cadastrado confirmado pelo contrato ${contract.name}.",
            contractName = contract.name,
        )
    }

    fun isListLikeRideFeed(text: String, normalizedText: String = text.normalizedCoreText()): Boolean {
        val normalizedLines = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.normalizedCoreText() }

        val titleCount = normalizedLines.filter { titleRegex.containsMatchIn(it) }.distinct().size
        val acceptButtonCount = acceptButtonRegex.findAll(text)
            .map { it.value.normalizedCoreText().replace(" ", "") }
            .distinct()
            .count()
        val endpointCount = routeEndpointSignatures(text).size
        val moneyCount = moneyRegex.findAll(text)
            .map { it.value.normalizedCoreText().replace(" ", "") }
            .distinct()
            .count()
        val distanceCount = distanceRegex.findAll(text)
            .map { it.value.normalizedCoreText().replace(" ", "") }
            .distinct()
            .count()

        if (titleCount >= 2) return true
        if (acceptButtonCount >= 2) return true
        if (endpointCount >= 4 && moneyCount >= 2 && distanceCount >= 2) return true
        val listMarkers = listOf(
            "corridas disponiveis",
            "viagens disponiveis",
            "ofertas disponiveis",
            "lista de corridas",
            "pedidos proximos",
            "novos pedidos",
            "pedidos de viagem",
        )
        return listMarkers.any { it in normalizedText }
    }

    private fun hasStrongOpenedCardEvidence(text: String, normalizedText: String): Boolean {
        val hasPrimaryAction = acceptButtonRegex.containsMatchIn(text) ||
            "aceitar por" in normalizedText ||
            "ofereca sua tarifa" in normalizedText
        val hasMoney = moneyRegex.containsMatchIn(text)
        val hasDistance = distanceRegex.containsMatchIn(text)
        val hasAddress = addressRegex.containsMatchIn(text) || routeEndpointSignatures(text).isNotEmpty()
        val hasIdentity = titleRegex.containsMatchIn(text) || hasPrimaryAction
        return hasIdentity && hasPrimaryAction && hasMoney && hasDistance && hasAddress
    }

    private fun registeredFamilyFallback(
        packageName: String,
        templates: List<RideCardTemplate>,
        liveFeatures: Set<String>,
    ): RideCardTemplateMatch? {
        val hasLiveRideSignal = rideSignals.any { it in liveFeatures }
        val liveRouteSignals = routeSignals.intersect(liveFeatures)
        if (!hasLiveRideSignal || liveRouteSignals.size < 3) return null

        return templates
            .asSequence()
            .filter { belongsToPackage(it, packageName) }
            .mapNotNull { template ->
                val required = template.requiredFeatures
                    .filterNot { it.startsWith("adaptive.") }
                    .toSet()
                if (required.isEmpty()) return@mapNotNull null
                val matched = required.intersect(liveFeatures)
                val matchedRouteSignals = routeSignals.intersect(matched)
                val matchedRideSignals = rideSignals.intersect(matched)
                val score = matched.size.toDouble() / required.size.coerceAtLeast(1)
                if (matched.size < 5 || matchedRouteSignals.size < 3 || matchedRideSignals.isEmpty() || score < 0.35) {
                    return@mapNotNull null
                }
                RideCardTemplateMatch(
                    template = template,
                    score = score,
                    matchedFeatures = matched.toList().sorted(),
                )
            }
            .maxByOrNull { it.score }
    }

    private fun routeEndpointSignatures(text: String): Set<String> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val signatures = linkedSetOf<String>()
        lines.forEachIndexed { index, line ->
            if (!markerLineRegex.matches(line)) return@forEachIndexed
            val normalizedLine = line.normalizedCoreText()
            val markerOnly = normalizedLine == "a" || normalizedLine == "b"
            val endpoint = if (markerOnly) {
                lines.getOrNull(index + 1)?.normalizedCoreText().orEmpty()
            } else {
                normalizedLine.drop(1).trim()
            }
            if (endpoint.length >= 4) signatures += "${normalizedLine.firstOrNull() ?: '?'}|$endpoint"
        }
        return signatures
    }

    private fun belongsToPackage(template: RideCardTemplate, packageName: String): Boolean {
        val templatePackage = CorePackageMonitor.normalize(template.packageName)
        return templatePackage.isNullOrBlank() ||
            RideCardTemplateMatcher.isUniversalLearnedPackage(templatePackage) ||
            templatePackage == packageName
    }

    private fun String.normalizedCoreText(): String =
        Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
}

data class CoreCardMatchResult(
    val accepted: Boolean,
    val match: RideCardTemplateMatch?,
    val reason: String,
    val isListLike: Boolean = false,
    val contractName: String = "Core",
) {
    companion object {
        fun accepted(match: RideCardTemplateMatch, reason: String, contractName: String = "Core"): CoreCardMatchResult =
            CoreCardMatchResult(accepted = true, match = match, reason = reason, contractName = contractName)

        fun rejected(reason: String, isListLike: Boolean = false, contractName: String = "Core"): CoreCardMatchResult =
            CoreCardMatchResult(accepted = false, match = null, reason = reason, isListLike = isListLike, contractName = contractName)
    }
}
