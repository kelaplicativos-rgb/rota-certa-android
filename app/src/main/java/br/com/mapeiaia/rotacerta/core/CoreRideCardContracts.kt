package br.com.mapeiaia.rotacerta.core

import br.com.mapeiaia.rotacerta.RideCardTemplateMatcher
import java.text.Normalizer
import java.util.Locale

/**
 * Contratos especificos por aplicativo.
 * Cada app tem sua propria regra minima antes de tentar comparar com card cadastrado.
 */
interface CoreRideCardContract {
    val name: String
    fun evaluate(text: String, packageName: String, features: Set<String>): CoreRideCardContractResult
}

object CoreRideCardContractRegistry {
    fun contractFor(packageName: String?): CoreRideCardContract = when (CorePackageMonitor.normalize(packageName)) {
        RideCardTemplateMatcher.INDRIVE_PACKAGE -> InDriveCardContract
        RideCardTemplateMatcher.UBER_PACKAGE -> UberCardContract
        RideCardTemplateMatcher.NINETY_NINE_PACKAGE -> NinetyNineCardContract
        else -> UniversalCardContract
    }
}

object InDriveCardContract : CoreRideCardContract {
    override val name: String = "inDrive"

    private val acceptButtonRegex = Regex("aceitar\\s+por\\s+r\\$\\s*\\d+", RegexOption.IGNORE_CASE)
    private val offerButtonRegex = Regex("ofere[cç]a\\s+sua\\s+tarifa", RegexOption.IGNORE_CASE)
    private val moneyRegex = Regex("r\\$\\s*\\d", RegexOption.IGNORE_CASE)
    private val routeMarkerRegex = Regex("(?m)^\\s*[ab]\\b", RegexOption.IGNORE_CASE)

    override fun evaluate(text: String, packageName: String, features: Set<String>): CoreRideCardContractResult {
        val normalized = text.toContractText()
        if (CoreCardMatchEngine.isListLikeRideFeed(text, normalized)) {
            return CoreRideCardContractResult.rejected(name, "inDrive em lista/feed; bloquear rota e farol.", isListLike = true)
        }
        val hasRideTitle = "pedido de viagem" in normalized || "pedidos de viagem" in normalized // open_all_contract_titles_0_1_94
        val hasAccept = acceptButtonRegex.containsMatchIn(text) || "aceitar por" in normalized
        val hasOffer = offerButtonRegex.containsMatchIn(text) || "ofereca sua tarifa" in normalized
        val hasPrimaryAction = hasAccept || hasOffer
        val hasMoney = moneyRegex.containsMatchIn(text)
        val hasRoute = "card.route.address" in features ||
            "card.route.two_addresses" in features || // indrive_markerless_core_route_0_1_87
            "card.route.ab_markers" in features ||
            "card.route.marked_stops" in features ||
            routeMarkerRegex.findAll(text).count() >= 2
        val hasIndividualCardContract = hasRoute &&
            hasPrimaryAction &&
            (hasMoney || hasRideTitle) // indrive_core_contract_0_1_85

        return if (hasIndividualCardContract) {
            CoreRideCardContractResult.accepted(name, "Contrato inDrive aceito: card individual com rota e acao principal.")
        } else {
            CoreRideCardContractResult.rejected(name, "inDrive ainda nao confirmou card individual aberto.")
        }
    }
}

object UberCardContract : CoreRideCardContract {
    override val name: String = "Uber"

    override fun evaluate(text: String, packageName: String, features: Set<String>): CoreRideCardContractResult {
        val normalized = text.toContractText()
        if (CoreCardMatchEngine.isListLikeRideFeed(text, normalized)) {
            return CoreRideCardContractResult.rejected(name, "Uber em lista/feed; bloquear rota e farol.", isListLike = true)
        }
        val hasUberSignal = listOf("uberx", "exclusivo", "viagem longa", "radar de viagens", "pop expresso").any { it in normalized }
        val hasRoute = "card.route.address" in features || "card.route.marked_stops" in features || "card.route.ab_markers" in features
        return if (hasUberSignal || hasRoute) {
            CoreRideCardContractResult.accepted(name, "Contrato Uber aceito para card individual.")
        } else {
            CoreRideCardContractResult.rejected(name, "Uber ainda nao confirmou card individual.")
        }
    }
}

object NinetyNineCardContract : CoreRideCardContract {
    override val name: String = "99"

    override fun evaluate(text: String, packageName: String, features: Set<String>): CoreRideCardContractResult {
        val normalized = text.toContractText()
        if (CoreCardMatchEngine.isListLikeRideFeed(text, normalized)) {
            return CoreRideCardContractResult.rejected(name, "99 em lista/feed; bloquear rota e farol.", isListLike = true)
        }
        val has99Signal = listOf("negocia", "perfil premium", "perfil essencial", "pop expresso").any { it in normalized }
        val hasRoute = "card.route.address" in features || "card.route.marked_stops" in features || "card.route.ab_markers" in features
        return if (has99Signal || hasRoute) {
            CoreRideCardContractResult.accepted(name, "Contrato 99 aceito para card individual.")
        } else {
            CoreRideCardContractResult.rejected(name, "99 ainda nao confirmou card individual.")
        }
    }
}

object UniversalCardContract : CoreRideCardContract {
    override val name: String = "Universal"

    override fun evaluate(text: String, packageName: String, features: Set<String>): CoreRideCardContractResult {
        val normalized = text.toContractText()
        if (CoreCardMatchEngine.isListLikeRideFeed(text, normalized)) {
            return CoreRideCardContractResult.rejected(name, "Tela universal em lista/feed; bloquear rota e farol.", isListLike = true)
        }
        val hasRoute = "card.route.address" in features || "card.route.marked_stops" in features || "card.route.ab_markers" in features
        return if (hasRoute) {
            CoreRideCardContractResult.accepted(name, "Contrato universal aceito para card individual.")
        } else {
            CoreRideCardContractResult.rejected(name, "Universal ainda nao confirmou card individual.")
        }
    }
}

data class CoreRideCardContractResult(
    val contractName: String,
    val accepted: Boolean,
    val reason: String,
    val isListLike: Boolean = false,
) {
    companion object {
        fun accepted(contractName: String, reason: String): CoreRideCardContractResult =
            CoreRideCardContractResult(contractName = contractName, accepted = true, reason = reason)

        fun rejected(contractName: String, reason: String, isListLike: Boolean = false): CoreRideCardContractResult =
            CoreRideCardContractResult(contractName = contractName, accepted = false, reason = reason, isListLike = isListLike)
    }
}

private fun String.toContractText(): String =
    Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
