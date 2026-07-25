package br.com.mapeiaia.rotacerta

import java.util.Locale

object RideOfferDetector {
    fun looksLikeRideOffer(text: String, fields: RideFields, packageName: String?): Boolean {
        if (!RideScreenTextClassifier.looksLikeRideCard(text)) return false
        if (containsNonRideScreenNoise(text)) return false
        val destination = fields.destination?.lowercase(Locale.ROOT).orEmpty()
        if (destination.isBlank()) return false
        val pickup = fields.pickup?.lowercase(Locale.ROOT).orEmpty()
        if (pickup.isNotBlank() && pickup == destination) return false

        val normalized = text.lowercase(Locale.ROOT)
        val hasDestinationAddressSignal = listOf(
            "rua", "r.", "avenida", "av.", "travessa", "bairro", "jardim", "cidade", "parque", "tatuape", "tatuapé",
            "sao paulo", "são paulo", "district", "state of", "restaurante", "lanchonete", "mercado", "shopping",
            "hospital", "comercial", "consultoria", "condominio", "condomínio",
        ).any { destination.contains(it) } || Regex("""\b\d{1,5}\b""").containsMatchIn(destination)
        val hasRideCardSignal = listOf(
            "pedido de viagem", "pedidos de viagem", "aceitar", "aceitar por", "selecionar", "negocia",
            "perfil premium", "perfil essencial", "uberx", "pop expresso", "exclusivo", "viagem longa",
            "radar de viagens", "ofereça sua tarifa", "ofereca sua tarifa", "preço justo", "preco justo",
        ).any { normalized.contains(it) }
        val hasMapPointSignal = Regex("""(?m)^\s*[ab]\s+""", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val normalizedPackage = packageName?.lowercase(Locale.ROOT).orEmpty()
        val hasPositiveFare = fields.fare?.let {
            Regex("""^R\$\s*(?!0+(?:[,.]0{1,2})?\b)\d""", RegexOption.IGNORE_CASE).containsMatchIn(it)
        } == true
        val hasNinetyNinePackageSignal = normalizedPackage == PACKAGE_99_DRIVER &&
            hasPositiveFare &&
            Regex("""\b\d+(?:[,.]\d+)?\s*km\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)
        return hasDestinationAddressSignal && (hasRideCardSignal || hasMapPointSignal || hasNinetyNinePackageSignal)
    }

    fun rejectReason(fields: RideFields): String = when {
        fields.destination.isNullOrBlank() -> "Destino final nao identificado no texto lido."
        !fields.pickup.isNullOrBlank() && fields.pickup.equals(fields.destination, ignoreCase = true) ->
            "Destino final igual ao embarque; aguardando leitura mais completa."
        else -> "Destino foi lido, mas a tela nao parece um card de corrida aceito pelo filtro."
    }

    private fun containsNonRideScreenNoise(text: String): Boolean {
        val normalized = text.lowercase(Locale.ROOT)
        return nonRideScreenPhrases.any { normalized.contains(it) }
    }

    private val nonRideScreenPhrases = listOf(
        "permissões do app",
        "permissoes do app",
        "nenhuma permissão negada",
        "nenhuma permissao negada",
        "configurações de apps não usados",
        "configuracoes de apps nao usados",
        "gerenciar app que não está",
        "gerenciar app que nao esta",
        "remover permissões",
        "remover permissoes",
        "abrir rota certa",
        "salvar card de corrida",
        "salvar este local",
        "criar alerta de proximidade",
    )

    private const val PACKAGE_99_DRIVER = "com.app99.driver"
}
