package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealWorldRideCardMatchPolicyTest {
    @Test
    fun acceptsSameInDriveLayoutWhenRiskAndPriceSignalsChange() {
        val required = setOf(
            "pedido de viagem",
            "preco justo",
            "valor em reais",
            "distancia em km",
            "tempo de rota",
            "endereco",
            "card.crop.route_block",
            "card.route.two_time_distance",
            "card.route.two_distances",
            "card.route.two_times",
            "card.route.two_addresses",
            "card.risk_area",
        )
        val live = setOf(
            "pedido de viagem",
            "preco justo",
            "distancia em km",
            "tempo de rota",
            "endereco",
            "card.crop.route_block",
            "card.route.time_distance",
            "card.route.address",
            "card.route.two_addresses",
        )

        val result = RealWorldRideCardMatchPolicy.evaluate(
            requiredFeatures = required,
            liveFeatures = live,
            samePackage = true,
            universalPackage = false,
            learnableLiveCard = true,
        )
        assertTrue(result.accepted)
    }

    @Test
    fun stillRejectsWrongPackageOrScreenWithoutRouteBlock() {
        val required = setOf(
            "pedido de viagem",
            "distancia em km",
            "tempo de rota",
            "endereco",
            "card.crop.route_block",
        )
        val live = required + "card.route.address"
        assertFalse(
            RealWorldRideCardMatchPolicy.evaluate(required, live, false, false, true).accepted,
        )
        assertFalse(
            RealWorldRideCardMatchPolicy.evaluate(
                required,
                live - "card.crop.route_block",
                true,
                false,
                true,
            ).accepted,
        )
    }
}
