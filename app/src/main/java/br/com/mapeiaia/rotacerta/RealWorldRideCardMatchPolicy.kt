package br.com.mapeiaia.rotacerta

/**
 * Separa sinais permanentes do layout dos sinais variáveis de cada corrida.
 * O modelo continua obrigatório e preso ao aplicativo escolhido, mas preço,
 * alerta de risco e quantidade exata de tempos/distâncias não bloqueiam outro
 * card legítimo do mesmo formato.
 */
object RealWorldRideCardMatchPolicy {
    data class Evaluation(
        val accepted: Boolean,
        val score: Double,
        val matchedFeatures: Set<String>,
    )

    private val volatileFeatures = setOf(
        "card.route.two_time_distance",
        "card.route.two_distances",
        "card.route.two_times",
        "card.risk_area",
        "valor em reais",
        "dinheiro",
        "pix",
    )

    private val structuralFeatures = setOf(
        "distancia em km",
        "tempo de rota",
        "endereco",
        "marcadores a/b",
        "card.route.address",
        "card.route.two_addresses",
        "card.route.ab_markers",
        "card.route.marked_stops",
    )

    private val appPhrases = setOf(
        "pedido de viagem",
        "pedidos de viagem",
        "aceitar por",
        "aceitar",
        "selecionar",
        "ofereca sua tarifa",
        "ofereça sua tarifa",
        "negocia",
        "perfil premium",
        "perfil essencial",
        "uberx",
        "pop expresso",
        "exclusivo",
        "viagem longa",
        "radar de viagens",
        "preco justo",
        "preço justo",
    )

    fun evaluate(
        requiredFeatures: Set<String>,
        liveFeatures: Set<String>,
        samePackage: Boolean,
        universalPackage: Boolean,
        learnableLiveCard: Boolean,
    ): Evaluation {
        val stableRequired = requiredFeatures
            .filterNot { it.startsWith("adaptive.") || it in volatileFeatures }
            .toSet()
        if (stableRequired.isEmpty()) return Evaluation(false, 0.0, emptySet())

        val matched = stableRequired.intersect(liveFeatures)
        val score = matched.size.toDouble() / stableRequired.size.coerceAtLeast(1)
        val routeBlock = "card.crop.route_block" in liveFeatures
        val structuralMatches = structuralFeatures.count { it in liveFeatures }
        val requiredAppPhrases = stableRequired.intersect(appPhrases)
        val appPhraseOverlap = if (requiredAppPhrases.isEmpty()) {
            appPhrases.any { it in liveFeatures }
        } else {
            requiredAppPhrases.any { it in liveFeatures }
        }

        val accepted = if (universalPackage) {
            learnableLiveCard &&
                routeBlock &&
                structuralMatches >= 3 &&
                appPhraseOverlap &&
                score >= UNIVERSAL_MIN_STABLE_SCORE
        } else {
            samePackage &&
                routeBlock &&
                structuralMatches >= 3 &&
                appPhraseOverlap &&
                score >= KNOWN_APP_MIN_STABLE_SCORE
        }
        return Evaluation(accepted, score, matched)
    }

    private const val KNOWN_APP_MIN_STABLE_SCORE = 0.55
    private const val UNIVERSAL_MIN_STABLE_SCORE = 0.68
}
