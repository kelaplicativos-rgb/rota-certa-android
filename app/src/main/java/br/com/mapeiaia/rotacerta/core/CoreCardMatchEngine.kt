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
            ?: RideCardTemplateMatcher.UNIVERSAL_LEARNED_PACKAGE
        if (templates.isEmpty()) {
            return CoreCardMatchResult.rejected("Nenhum card cadastrado para comparar com a tela atual.")
        }

        val preparedText = CoreRideTextSanitizer.sanitize(text, normalizedPackage)
        val normalizedText = preparedText.normalizedCoreText()
        if (isListLikeRideFeed(text, text.normalizedCoreText()) || isListLikeRideFeed(preparedText, normalizedText)) { // open_all_raw_feed_guard_0_1_94
            return CoreCardMatchResult.rejected(
                reason = "Foram detectadas varias ofertas ao mesmo tempo; aguardando um card individual visivel.",
                isListLike = true,
                contractName = CoreRideCardContractRegistry.contractFor(normalizedPackage).name,
            )
        }

        val detectedFeatures = RideCardTemplateMatcher.featuresFor(preparedText)
        val routeEvidence = routeSignals.intersect(detectedFeatures)
        val features = if ("card.crop.route_block" in detectedFeatures || routeEvidence.size < 2) {
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

        val match = RideCardTemplateMatcher.match(preparedText, normalizedPackage, templates)
            ?: FastRideCardMatcher.match(preparedText, normalizedPackage, templates)
            ?: registeredFamilyFallback(normalizedPackage, templates, features)
            ?: return CoreCardMatchResult.rejected(
                reason = "A tela foi lida sem trava, mas ainda nao possui semelhanca suficiente com um modelo cadastrado.",
                contractName = contract.name,
            )

        return CoreCardMatchResult.accepted(
            match = match,
            reason = "Modelo cadastrado confirmado sem trava de pacote ou tela.",
            contractName = contract.name,
        )
    } // open_all_core_match_0_1_94

    fun isListLikeRideFeed(text: String, normalizedText: String = text.normalizedCoreText()): Boolean {
        val normalizedLines = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.normalizedCoreText() }
        val singularTitleCount = normalizedLines.count { it == "pedido de viagem" }
        val hasPluralHeader = normalizedLines.any { it == "pedidos de viagem" }
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

        if (acceptButtonCount >= 2) return true
        if (singularTitleCount >= 2 && endpointCount >= 4) return true
        if (endpointCount >= 4 && moneyCount >= 2 && distanceCount >= 2) return true
        if (hasPluralHeader && moneyCount >= 3 && distanceCount >= 2) return true
        return false
    } // open_all_list_detection_0_1_94

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
        val liveRouteSignals = routeSignals.intersect(liveFeatures)
        if (liveRouteSignals.size < 2) return null

        return templates
            .asSequence()
            .mapNotNull { template ->
                val required = template.requiredFeatures
                    .filterNot { it.startsWith("adaptive.") }
                    .toSet()
                if (required.isEmpty()) return@mapNotNull null
                val matched = required.intersect(liveFeatures)
                val matchedRouteSignals = routeSignals.intersect(matched)
                val score = matched.size.toDouble() / required.size.coerceAtLeast(1)
                if (matched.size < 3 || matchedRouteSignals.size < 2 || score < 0.25) return@mapNotNull null
                RideCardTemplateMatch(
                    template = template,
                    score = score,
                    matchedFeatures = matched.toList().sorted(),
                )
            }
            .maxByOrNull { match ->
                match.score + if (match.template.packageName?.equals(packageName, ignoreCase = true) == true) 0.08 else 0.0
            }
    } // open_all_family_fallback_0_1_94

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
