package br.com.mapeiaia.rotacerta.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreRideCardContractsTest {
    @Test
    fun registrySelectsSpecificContractsByPackage() {
        assertEquals("inDrive", CoreRideCardContractRegistry.contractFor("sinet.startup.indriver").name)
        assertEquals("Uber", CoreRideCardContractRegistry.contractFor("com.ubercab.driver").name)
        assertEquals("99", CoreRideCardContractRegistry.contractFor("com.app99.driver").name)
        assertEquals("Universal", CoreRideCardContractRegistry.contractFor("outro.app").name)
    }

    @Test
    fun inDriveAcceptsOnlyOpenedIndividualCardWithStrongSignals() {
        val result = InDriveCardContract.evaluate(
            text = """
                Pedido de viagem
                R$ 44
                A Rua Origem, 100
                B Rua Destino, 200
                Aceitar por R$ 44
                Ofereça sua tarifa
            """.trimIndent(),
            packageName = "sinet.startup.indriver",
            features = setOf("card.crop.route_block", "card.route.address"),
        )

        assertTrue(result.accepted) // open_all_contract_assert_0_1_94
        assertFalse(result.isListLike)
    }

    @Test
    fun inDriveRejectsFeedEvenWithMoneyAndAcceptButtons() {
        val result = InDriveCardContract.evaluate(
            text = """
                Pedidos de viagem
                Pedido de viagem
                A Rua A
                B Rua B
                4 km
                Aceitar por R$ 30
                Pedido de viagem
                A Rua C
                B Rua D
                7 km
                Aceitar por R$ 60
            """.trimIndent(),
            packageName = "sinet.startup.indriver",
            features = setOf("card.crop.route_block", "card.route.address"),
        )

        assertFalse(result.accepted)
        assertTrue(result.isListLike)
    }

    @Test
    fun inDriveAcceptsRegisteredRouteWithoutLegacyCropFlag() { // open_all_contract_test_0_1_94
        val result = InDriveCardContract.evaluate(
            text = "Pedido de viagem\nR$ 44\nAceitar por R$ 44\nOfereça sua tarifa",
            packageName = "sinet.startup.indriver",
            features = setOf("card.route.address"),
        )

        assertTrue(result.accepted) // open_all_contract_assert_0_1_94
        assertFalse(result.isListLike)
    }

    @Test
    fun uberAcceptsIndividualCardWithKnownSignal() {
        val result = UberCardContract.evaluate(
            text = "UberX\nRua Origem\nRua Destino",
            packageName = "com.ubercab.driver",
            features = setOf("card.crop.route_block"),
        )

        assertTrue(result.accepted)
    }

    @Test
    fun ninetyNineAcceptsIndividualCardWithKnownSignal() {
        val result = NinetyNineCardContract.evaluate(
            text = "Perfil Premium\nRua Origem\nRua Destino",
            packageName = "com.app99.driver",
            features = setOf("card.crop.route_block"),
        )

        assertTrue(result.accepted)
    }

    @Test
    fun universalAcceptsOnlyWhenRouteFeatureExists() {
        val accepted = UniversalCardContract.evaluate(
            text = "Corrida\nRua Origem\nRua Destino",
            packageName = "outro.app",
            features = setOf("card.crop.route_block", "card.route.address"),
        )
        val rejected = UniversalCardContract.evaluate(
            text = "Corrida\nRua Origem\nRua Destino",
            packageName = "outro.app",
            features = setOf("card.crop.route_block"),
        )

        assertTrue(accepted.accepted)
        assertFalse(rejected.accepted)
    }
}
