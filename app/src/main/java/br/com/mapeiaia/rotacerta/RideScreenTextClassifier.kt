package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

object RideScreenTextClassifier {
    private val moneyRegex = Regex("""R\$\s*\d""", RegexOption.IGNORE_CASE)
    private val distanceRegex = Regex("""\b\d+(?:[,.]\d+)?\s*km\b""", RegexOption.IGNORE_CASE)
    private val routeStepRegex = Regex(
        """\b\d{1,3}\s*(?:min|minuto|minutos)\.?\s*(?:\(\s*(?:\d+(?:[,.]\d+)?\s*)?(?:m|km)\s*\))?""",
        RegexOption.IGNORE_CASE,
    )
    private val addressRegex = Regex(
        """\b(?:rua|r\.|avenida|av\.|rodovia|estrada|travessa|alameda|praca|bairro)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val mapAddressMarkerRegex = Regex(
        """(?m)^\s*[ab]\s+(?:rua|r\.|avenida|av\.|rodovia|estrada|travessa|alameda|praca|bairro|[\wÀ-ÿ]+\s*,?\s*\d{1,5})\b""",
        RegexOption.IGNORE_CASE,
    )
    private val rideKeywords = listOf(
        "aceitar",
        "corrida",
        "corridas",
        "pedido de viagem",
        "pedidos de viagem",
        "viagem longa",
        "uber",
        "uberx",
        "dinheiro",
        "pix",
        "parada(s)",
        "ofereca sua tarifa",
        "negocia",
        "exclusivo",
        "preco justo",
    )
    private val viewerShellKeywords = listOf(
        "files by google",
        "editar",
        "pesquisar na imagem",
        "navegar para cima",
        "compartilhar",
        "adicionar a pasta",
        "com estrela",
        "mais opcoes",
        "google fotos",
        "galeria",
    )

    fun ignoreReason(text: String): String? {
        val normalized = text.normalizedForMatch()
        if (looksLikeAndroidSystemShade(normalized)) {
            return "Tela do sistema/atalhos Android detectada; nenhum card de chamada ativo."
        }
        if (looksLikeNinetyNineSettingsMenu(normalized)) {
            return "Menu/configuracoes da 99 detectado; nenhum card de chamada ativo."
        }
        if (looksLikeUberIdleScreen(normalized)) {
            return "Tela inicial/offline/area de espera do Uber detectada; nenhum card de chamada ativo."
        }

        val viewerHits = viewerShellKeywords.count { normalized.contains(it) }
        if (viewerHits >= 2 && !hasRideEvidence(text)) {
            return "Tela de visualizador/galeria detectada sem texto de card de corrida."
        }

        return null
    }

    fun shouldIgnore(text: String): Boolean = ignoreReason(text) != null

    fun looksLikeRideCard(text: String): Boolean {
        if (shouldIgnore(text)) return false
        return hasRideEvidence(text)
    }

    private fun hasRideEvidence(text: String): Boolean {
        val normalized = text.normalizedForMatch()
        val hasMoney = moneyRegex.containsMatchIn(text)
        val hasRideKeyword = rideKeywords.any { normalized.contains(it) }
        val hasRouteSignal = routeStepRegex.containsMatchIn(text) || distanceRegex.containsMatchIn(text)
        val hasAddressSignal = addressRegex.containsMatchIn(normalized) || mapAddressMarkerRegex.containsMatchIn(text)

        return hasMoney && (
            hasRideKeyword && (hasRouteSignal || hasAddressSignal) ||
                hasRouteSignal && hasAddressSignal
            )
    }

    private fun looksLikeAndroidSystemShade(normalized: String): Boolean {
        val systemHits = listOf(
            "ativado",
            "desativado",
            "reducao de brilho extra",
            "editar atalhos",
            "killapps",
            "ccleaner",
            "baxa",
        ).count { normalized.contains(it) }
        return systemHits >= 2
    }

    private fun looksLikeNinetyNineSettingsMenu(normalized: String): Boolean {
        val menuHits = listOf(
            "configurar solicitacoes",
            "preferencias de servicos",
            "ferramentas de aceitacao",
            "definir meu destino",
            "status da solicitacao",
            "teste de status",
            "eventos futuros",
            "desconectar",
        ).count { normalized.contains(it) }
        val hasOfferAction = normalized.contains("selecionar") || normalized.contains("aceitar")
        return menuHits >= 3 && !hasOfferAction
    }

    private fun looksLikeUberIdleScreen(normalized: String): Boolean {
        val hasOfferAction = listOf("aceitar", "uberx", "viagem longa", "exclusivo").any { normalized.contains(it) }
        if (hasOfferAction) return false

        val hasOfflineSignal = normalized.contains("voce esta offline") ||
            normalized.contains("nao e possivel ficar offline")
        val hasWaitingAreaSignal = normalized.contains("area de espera") ||
            normalized.contains("motorista parceiro") ||
            normalized.contains("gru")
        val hasHomeSignals = listOf(
            "pagina inicial",
            "pesquisar locais",
            "recursos de seguranca",
            "tendencias de ganhos",
            "preferencias",
            "agenda de viagens",
            "ver tempo ao volante",
            "registro de viagens",
        ).count { normalized.contains(it) }
        val hasZeroEarnings = normalized.contains("r$ 0,00") || normalized.contains("r$ 0.00")

        return hasOfflineSignal || (hasHomeSignals >= 3 && hasZeroEarnings) || (hasWaitingAreaSignal && hasZeroEarnings)
    }

    private fun String.normalizedForMatch(): String =
        Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
}
