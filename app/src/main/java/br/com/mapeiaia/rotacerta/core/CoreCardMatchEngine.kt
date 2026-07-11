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
    private val markerLineRegex = Regex("(?m)^\\s*[ab]\\b", RegexOption.IGNORE_CASE)
    private val titleRegex = Regex("pedido[s]?\\s+de\\s+viagem", RegexOption.IGNORE_CASE)

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
        val normalizedText = text.normalizedCoreText()
        val features = RideCardTemplateMatcher.featuresFor(text)
        val contract = CoreRideCardContractRegistry.contractFor(normalizedPackage)
        val contractResult = contract.evaluate(text, normalizedPackage, features)
        if (!contractResult.accepted) {
            return CoreCardMatchResult.rejected(
                reason = contractResult.reason,
                isListLike = contractResult.isListLike,
                contractName = contractResult.contractName,
            )
        }
        if (isListLikeRideFeed(text, normalizedText)) {
            return CoreCardMatchResult.rejected(
                reason = "Tela parece lista/feed de corridas; somente card individual cadastrado libera o farol.",
                isListLike = true,
                contractName = contract.name,
            )
        }
        if ("card.crop.route_block" !in features) {
            return CoreCardMatchResult.rejected(
                reason = "Leitura ainda nao contem bloco individual de rota do card cadastrado.",
                contractName = contract.name,
            )
        }
        val strictMatch = RideCardTemplateMatcher.match(text, normalizedPackage, templates)
        val match = strictMatch ?: FastRideCardMatcher.match(text, normalizedPackage, templates)
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
        val titleCount = titleRegex.findAll(text).count()
        val acceptButtonCount = acceptButtonRegex.findAll(text).count()
        val markerCount = markerLineRegex.findAll(text).count()
        val moneyCount = moneyRegex.findAll(text).count()
        val distanceCount = distanceRegex.findAll(text).count()
        if (titleCount >= 2) return true
        if (acceptButtonCount >= 2) return true
        if (markerCount >= 4 && moneyCount >= 2 && distanceCount >= 2) return true
        val listMarkers = listOf(
            "corridas disponiveis",
            "viagens disponiveis",
            "ofertas disponiveis",
            "lista de corridas",
            "pedidos proximos",
            "novos pedidos",
        )
        return listMarkers.any { it in normalizedText }
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
