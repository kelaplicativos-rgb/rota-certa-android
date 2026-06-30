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
    fun returnsInsufficientDataWhenPickupIsMissing() {
        val result = DecisionEngine().decide(
            fields = RideFields(fare = "R$ 10,00"),
            settings = AppSettings(),
            pickupCoordinate = null,
            homeCoordinate = null,
            alternativeCoordinate = null,
            fullText = "R$ 10,00",
        )

        assertEquals(Recommendation.InsufficientData, result.recommendation)
    }

    @Test
    fun recommendsRideInsideHomeRadius() {
        val result = DecisionEngine().decide(
            fields = RideFields(pickup = "Rua das Flores"),
            settings = AppSettings(homeRadiusKm = 5.0),
            pickupCoordinate = Coordinate(-23.5505, -46.6333),
            homeCoordinate = Coordinate(-23.5510, -46.6340),
            alternativeCoordinate = null,
            fullText = "Rua das Flores",
        )

        assertEquals(Recommendation.GoodRide, result.recommendation)
        assertTrue(result.pickupToHomeKm != null && result.pickupToHomeKm < 1.0)
    }
}
