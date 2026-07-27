package br.com.mapeiaia.rotacerta

import java.util.Locale

object FastRideCardMatcher {
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
        "marcadores a/b",
    )

    fun match(text: String, packageName: String?, templates: List<RideCardTemplate>): RideCardTemplateMatch? {
        val normalizedPackage = packageName?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val liveFeatures = RideCardTemplateMatcher.featuresFor(text)
        val liveCore = liveFeatures.intersect(routeSignals)
        if (liveFeatures.size < 3 || liveCore.size < 2) return null

        return templates
            .asSequence()
            .mapNotNull { template ->
                val required = template.requiredFeatures.filterNot { it.startsWith("adaptive.") }.toSet()
                if (required.isEmpty()) return@mapNotNull null
                val matched = required.intersect(liveFeatures)
                val matchedCore = required.intersect(routeSignals).intersect(liveCore)
                val score = matched.size.toDouble() / required.size.coerceAtLeast(1)
                if (matched.size < 3 || matchedCore.size < 2 || score < 0.25) return@mapNotNull null
                RideCardTemplateMatch(template = template, score = score, matchedFeatures = matched.toList().sorted())
            }
            .maxByOrNull { match ->
                match.score + if (match.template.packageName?.equals(normalizedPackage, ignoreCase = true) == true) 0.08 else 0.0
            }
    }
}
// open_all_fast_matcher_0_1_94
