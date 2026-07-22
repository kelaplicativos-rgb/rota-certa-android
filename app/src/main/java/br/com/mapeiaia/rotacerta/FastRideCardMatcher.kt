package br.com.mapeiaia.rotacerta

import java.util.Locale

object FastRideCardMatcher {
    private val rideSignals = setOf(
        "pedido de viagem",
        "pedidos de viagem",
        "aceitar",
        "aceitar por",
        "ofereca sua tarifa",
        "preco justo",
        "uberx",
        "pop expresso",
        "negocia",
    )

    private val routeSignals = setOf(
        "card.crop.route_block",
        "card.route.address",
        "card.route.two_addresses",
        "card.route.ab_markers",
        "card.route.marked_stops",
        "distancia em km",
        "tempo de rota",
        "endereco",
        "marcadores a/b",
    )

    fun match(text: String, packageName: String?, templates: List<RideCardTemplate>): RideCardTemplateMatch? {
        val normalizedPackage = packageName?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (normalizedPackage.isBlank()) return null
        val liveFeatures = RideCardTemplateMatcher.featuresFor(text)
        if ("card.crop.route_block" !in liveFeatures) return null
        if ("card.route.address" !in liveFeatures && "endereco" !in liveFeatures) return null
        if (rideSignals.none { it in liveFeatures }) return null

        return templates
            .asSequence()
            .filter { template -> template.packageName?.equals(normalizedPackage, ignoreCase = true) == true }
            .mapNotNull { template ->
                val required = template.requiredFeatures.filterNot { it.startsWith("adaptive.") }.toSet()
                if (required.isEmpty()) return@mapNotNull null
                val matched = required.intersect(liveFeatures)
                val learnedCore = required.intersect(routeSignals)
                val liveCore = liveFeatures.intersect(routeSignals)
                val matchedCore = learnedCore.intersect(liveCore)
                val score = matched.size.toDouble() / required.size.coerceAtLeast(1)
                if (matched.size < 4 || matchedCore.size < 3 || score < 0.45) return@mapNotNull null
                RideCardTemplateMatch(template = template, score = score, matchedFeatures = matched.toList().sorted())
            }
            .maxByOrNull { it.score }
    }
}
