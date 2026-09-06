package br.com.mapeiaia.rotacerta

/**
 * Prova conservadora de que um destino esta fora de todos os raios configurados.
 *
 * A distancia de qualquer rota dirigivel nunca pode ser menor que a distancia
 * geodesica entre os mesmos pontos. Portanto, quando a linha reta ja ultrapassa
 * todos os raios ativos, o vermelho pode ser aplicado imediatamente sem aguardar
 * a API de rotas e sem exibir um quilometro estimado.
 */
data class ExactRadiusLowerBoundDecision(
    val definitelyOutside: Boolean,
    val nearestLowerBoundKm: Double?,
    val evaluatedTargets: Int,
)

object ExactRadiusLowerBoundPolicy {
    fun evaluate(
        destinationCoordinate: Coordinate?,
        settings: AppSettings,
        homeCoordinate: Coordinate?,
        alternativeCoordinate: Coordinate?,
        additionalAlternativeCoordinates: List<Coordinate> = emptyList(),
    ): ExactRadiusLowerBoundDecision {
        destinationCoordinate ?: return ExactRadiusLowerBoundDecision(false, null, 0)

        val targets = buildList {
            if (settings.homeTargetEnabled && homeCoordinate != null) {
                add(
                    TargetLowerBound(
                        distanceKm = GeoDistance.meters(destinationCoordinate, homeCoordinate) / 1000.0,
                        radiusKm = settings.homeRadiusKm,
                    ),
                )
            }

            if (settings.alternativeTargetEnabled) {
                (listOfNotNull(alternativeCoordinate) + additionalAlternativeCoordinates)
                    .distinctBy { coordinate -> "%.6f,%.6f".format(coordinate.latitude, coordinate.longitude) }
                    .forEach { coordinate ->
                        add(
                            TargetLowerBound(
                                distanceKm = GeoDistance.meters(destinationCoordinate, coordinate) / 1000.0,
                                radiusKm = settings.alternativeRadiusKm,
                            ),
                        )
                    }
            }
        }

        if (targets.isEmpty()) return ExactRadiusLowerBoundDecision(false, null, 0)

        return ExactRadiusLowerBoundDecision(
            definitelyOutside = targets.all { target -> target.distanceKm > target.radiusKm },
            nearestLowerBoundKm = targets.minOfOrNull(TargetLowerBound::distanceKm),
            evaluatedTargets = targets.size,
        )
    }

    private data class TargetLowerBound(
        val distanceKm: Double,
        val radiusKm: Double,
    )
}
