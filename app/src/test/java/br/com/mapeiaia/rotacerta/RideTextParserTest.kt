package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RideTextParserTest {
    @Test
    fun parsesBasicRidePrintText() {
        val text = """
            Nova corrida
            Embarque
            Rua das Flores, 123
            Destino
            Avenida Brasil, 900
            R$ 24,50
            4,8 km
            12 min
        """.trimIndent()

        val fields = RideTextParser().parse(text)

        assertEquals("Rua das Flores, 123", fields.pickup)
        assertEquals("Avenida Brasil, 900", fields.destination)
        assertEquals("R$ 24,50", fields.fare)
        assertEquals("4,8 km", fields.distance)
        assertEquals("12 min", fields.time)
    }

    @Test
    fun returnsInsufficientDataWhenDestinationIsMissing() {
        val result = DecisionEngine().decide(
            fields = RideFields(fare = "R$ 10,00"),
            settings = AppSettings(),
            destinationCoordinate = null,
            homeCoordinate = null,
            alternativeCoordinate = null,
            fullText = "R$ 10,00",
        )

        assertEquals(Recommendation.InsufficientData, result.recommendation)
    }

    @Test
    fun recommendsRideWhenDestinationIsInsideHomeRadius() {
        val result = DecisionEngine().decide(
            fields = RideFields(destination = "Avenida Brasil"),
            settings = AppSettings(homeRadiusKm = 5.0),
            destinationCoordinate = Coordinate(-23.5505, -46.6333),
            homeCoordinate = Coordinate(-23.5510, -46.6340),
            alternativeCoordinate = null,
            fullText = "Avenida Brasil",
        )

        assertEquals(Recommendation.GoodRide, result.recommendation)
        assertTrue(result.pickupToHomeKm != null && result.pickupToHomeKm < 1.0)
    }

    @Test
    fun joinsWrappedDestinationNeighborhoodFromOcr() {
        val text = """
            Dinheiro
            R$9,00
            R$3,88/km
            3min (462m)
            Comercial Esperança, Avenida
            Sapopemba, 13309 - Jardim Aduto
            5min (1,9km)
            Rua Augustin Luberti, 1053, Fazenda
            da Juta
        """.trimIndent()

        val fields = RideTextParser().parse(text)

        assertEquals("Comercial Esperança, Avenida Sapopemba, 13309 - Jardim Aduto", fields.pickup)
        assertEquals("Rua Augustin Luberti, 1053, Fazenda da Juta", fields.destination)
        assertEquals("R$9,00", fields.fare)
        assertEquals("1,9km", fields.distance)
        assertEquals("3min", fields.time)
    }

    @Test
    fun joinsWrappedMapPointAddressFromOcr() {
        val text = """
            Pedido de viagem
            R$ 14
            A Rua Lagoa Bonita 42(Jardim
            Imperador (Zona Leste))
            B Rua Moysés Zunta 189 (Parque
            Savoi City)
        """.trimIndent()

        val fields = RideTextParser().parse(text)

        assertEquals("A Rua Lagoa Bonita 42(Jardim Imperador (Zona Leste))", fields.pickup)
        assertEquals("Rua Moysés Zunta 189 (Parque Savoi City)", fields.destination)
    }
}
