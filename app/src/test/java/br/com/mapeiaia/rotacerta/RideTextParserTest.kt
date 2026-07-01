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
            settings = AppSettings(googleMapsApiKey = "key"),
            destinationCoordinate = null,
            homeCoordinate = null,
            alternativeCoordinate = null,
            fullText = "R$ 10,00",
        )

        assertEquals(Recommendation.InsufficientData, result.recommendation)
    }

    @Test
    fun recommendsRideWithApproximateDistanceWhenGoogleMapsKeyIsMissing() {
        val result = DecisionEngine().decide(
            fields = RideFields(destination = "Avenida Brasil"),
            settings = AppSettings(homeRadiusKm = 5.0),
            destinationCoordinate = Coordinate(-23.5505, -46.6333),
            homeCoordinate = Coordinate(-23.5510, -46.6340),
            alternativeCoordinate = null,
            fullText = "Avenida Brasil",
        )

        assertEquals(Recommendation.GoodRide, result.recommendation)
        assertTrue(result.pickupToHomeKm != null && result.pickupToHomeKm < 5.0)
    }

    @Test
    fun recommendsRideWithApproximateDistanceWhenGoogleMapsRouteIsMissing() {
        val result = DecisionEngine().decide(
            fields = RideFields(destination = "Avenida Brasil"),
            settings = AppSettings(homeRadiusKm = 5.0, googleMapsApiKey = "key"),
            destinationCoordinate = Coordinate(-23.5505, -46.6333),
            homeCoordinate = Coordinate(-23.5510, -46.6340),
            alternativeCoordinate = null,
            fullText = "Avenida Brasil",
        )

        assertEquals(Recommendation.GoodRide, result.recommendation)
        assertTrue(result.pickupToHomeKm != null && result.pickupToHomeKm < 5.0)
    }

    @Test
    fun recommendsRideWhenGoogleMapsRouteDistanceIsInsideHomeRadius() {
        val result = DecisionEngine().decide(
            fields = RideFields(destination = "Avenida Brasil"),
            settings = AppSettings(homeRadiusKm = 5.0, googleMapsApiKey = "key"),
            destinationCoordinate = Coordinate(-23.5505, -46.6333),
            homeCoordinate = Coordinate(-23.5510, -46.6340),
            alternativeCoordinate = null,
            fullText = "Avenida Brasil",
            homeDistanceKm = 3.7,
        )

        assertEquals(Recommendation.GoodRide, result.recommendation)
        assertEquals(3.7, result.pickupToHomeKm ?: 0.0, 0.01)
    }

    @Test
    fun rejectsRideWhenGoogleMapsRouteDistanceIsOutsideHomeRadius() {
        val result = DecisionEngine().decide(
            fields = RideFields(destination = "Avenida Brasil"),
            settings = AppSettings(homeRadiusKm = 5.0, googleMapsApiKey = "key"),
            destinationCoordinate = Coordinate(-23.5505, -46.6333),
            homeCoordinate = Coordinate(-23.5510, -46.6340),
            alternativeCoordinate = null,
            fullText = "Avenida Brasil",
            homeDistanceKm = 38.4,
        )

        assertEquals(Recommendation.OutsideRadius, result.recommendation)
        assertTrue(result.pickupToHomeKm != null && result.pickupToHomeKm > 5.0)
    }

    @Test
    fun ignoresAvoidedKeywordOutsideFinalDestination() {
        val result = DecisionEngine().decide(
            fields = RideFields(
                pickup = "Rua Evanyr Prado Venturini, Jardim Tiete",
                destination = "Rua A-65, Sao Mateus",
            ),
            settings = AppSettings(
                homeRadiusKm = 5.0,
                avoidedKeywords = "Jardim Tiete",
                googleMapsApiKey = "key",
            ),
            destinationCoordinate = Coordinate(-23.6000, -46.4800),
            homeCoordinate = Coordinate(-23.6010, -46.4810),
            alternativeCoordinate = null,
            fullText = "Rua Evanyr Prado Venturini, Jardim Tiete\nRua A-65, Sao Mateus",
            homeDistanceKm = 3.0,
        )

        assertEquals(Recommendation.GoodRide, result.recommendation)
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

        assertEquals("Rua Lagoa Bonita 42(Jardim Imperador (Zona Leste))", fields.pickup)
        assertEquals("Rua Moysés Zunta 189 (Parque Savoi City)", fields.destination)
    }

    @Test
    fun parsesSeparatedMapPointMarkersFromOcr() {
        val text = """
            Pedido de viagem
            A
            Av. Mateo Bei, 1974 (Jardim Tiete,
            São Paulo - SP)
            B
            Av. Maria Luiza Americano, 2673
            (Cidade Líder, São Paulo - SP)
        """.trimIndent()

        val fields = RideTextParser().parse(text)

        assertEquals("Av. Mateo Bei, 1974 (Jardim Tiete, São Paulo - SP)", fields.pickup)
        assertEquals("Av. Maria Luiza Americano, 2673 (Cidade Líder, São Paulo - SP)", fields.destination)
    }

    @Test
    fun parsesOnlyFirstCompleteRideWhenOfferListHasManyAddresses() {
        val text = """
            Pedidos de viagem
            R$ 1,6/km ~3,7 km
            R$ 29
            Rua Baltazar Vidal 95 (Jardim Nossa Sra. do Carmo)
            Rua Coelho Lisboa, 419 (Cidade Mãe do Céu, São Paulo - SP)
            PIX
            R$ 2/km ~4,0 km
            R$ 37 Preço justo
            Rua Agave Dragão 81 (Jardim Santa Adelia)
            Rua Azevedo Soares, 1500 (Tatuapé, São Paulo - SP)
            PIX
        """.trimIndent()

        val fields = RideTextParser().parse(text)

        assertEquals("Rua Baltazar Vidal 95 (Jardim Nossa Sra. do Carmo)", fields.pickup)
        assertEquals("Rua Coelho Lisboa, 419 (Cidade Mãe do Céu, São Paulo - SP)", fields.destination)
        assertEquals("R$ 29", fields.fare)
    }

    @Test
    fun parsesInDriveSingleOfferWithMapMarkersAndFareActions() {
        val text = """
            Pedido de viagem
            R$ 1,6/km ~3,7 km
            R$ 29
            A Rua Baltazar Vidal 95 (Jardim Nossa Sra. do Carmo)
            B Rua Coelho Lisboa, 419 (Cidade Mãe do Céu, São Paulo - SP)
            PIX
            Aceitar por R$ 29
            Ofereça sua tarifa
            Fechar
        """.trimIndent()

        val fields = RideTextParser().parse(text)

        assertEquals("Rua Baltazar Vidal 95 (Jardim Nossa Sra. do Carmo)", fields.pickup)
        assertEquals("Rua Coelho Lisboa, 419 (Cidade Mãe do Céu, São Paulo - SP)", fields.destination)
        assertEquals("R$ 29", fields.fare)
    }

    @Test
    fun parsesUberCardWithAbbreviatedRuaAsOneRide() {
        val text = """
            UberX
            R$ 13,48
            7 min (2.0 km)
            R. Augusto Ferreira Ramos, Jardim Tiete, São Paulo
            15 minutos (4.8 km)
            Avenida Adélia Chohfi, 655, São Rafael, São Paulo
            Selecionar
        """.trimIndent()

        val fields = RideTextParser().parse(text)

        assertEquals("R. Augusto Ferreira Ramos, Jardim Tiete, São Paulo", fields.pickup)
        assertEquals("Avenida Adélia Chohfi, 655, São Rafael, São Paulo", fields.destination)
        assertEquals("R$ 13,48", fields.fare)
    }

    @Test
    fun parsesNinetyNineCardWhenOcrDropsPickupDistanceNumber() {
        val text = """
            Negocia - Dinheiro
            R$8,93
            R$1,60/km
            4,70 181 corridas
            Perfil Premium
            6min (km)
            Estacao Sao Mateus monotrilho., Av.
            Sapopemba, 15.000 - Jardim Adut...
            12min (4,5km)
            Rua Palmeira Bacaba, 491 , Jardim
            Elba
            R$9,38 R$9,82 R$10,27
        """.trimIndent()

        val fields = RideTextParser().parse(text)

        assertEquals("Estacao Sao Mateus monotrilho., Av. Sapopemba, 15.000 - Jardim Adut...", fields.pickup)
        assertEquals("Rua Palmeira Bacaba, 491 , Jardim Elba", fields.destination)
        assertEquals("R$8,93", fields.fare)
    }

    @Test
    fun parsesInDriveStackedAddressesFromLiveDiagnostic() {
        val text = """
            Pedido de viagem
            Bruno
            4.85
            (41)
            5 min.
            R$ 1,7/km
            ~3,0 km
            R$ 26
            R. Zacarias Alves de Melo, 108 (Jardim Ibitirama)
            Cr.P.Conv Talitha Kumi (Rua General Bagnuolo - Quinta da Paineira, São Paulo - SP)
            Rua Cônego Antônio Dias Pequeno, 475 (Jardim Tietê, São Paulo - State of São Paulo)
            PIX
            Aceitar por R$ 26
            Ofereça sua tarifa
            R$ 29
            R$ 32
            R$ 34
            R$ 37
            Fechar
        """.trimIndent()

        val fields = RideTextParser().parse(text)

        assertEquals("R. Zacarias Alves de Melo, 108 (Jardim Ibitirama)", fields.pickup)
        assertEquals("Rua Cônego Antônio Dias Pequeno, 475 (Jardim Tietê, São Paulo - State of São Paulo)", fields.destination)
        assertEquals("R$ 26", fields.fare)
        assertEquals("3,0 km", fields.distance)
        assertEquals("5 min", fields.time)
    }

    @Test
    fun parsesInDriveDiagnosticWhenMapLabelsAppearBeforeOfferAddresses() {
        val text = """
            17:31
            Passageiro Teste
            * 5.0
            (30)
            1 min.
            Pedido de viagem
            Offline
            Mapa 9 min
            2,5 km
            A
            PIX
            B
            R$ 15
            R$ 2,3/km ~2,5 km
            Ponto de referencia
            R$ 13 O Preço justo
            A Rua Exemplo Um, 177
            (Cidade Um)
            B Rua Exemplo Dois, 175
            (Cidade Dois,
            São Paulo - SP)
            Aceitar por R$ 13
            Ofereça sua tarifa
            R$ 16
            R$ 17
        """.trimIndent()

        val fields = RideTextParser().parse(text)

        assertEquals("Rua Exemplo Um, 177 (Cidade Um)", fields.pickup)
        assertEquals("Rua Exemplo Dois, 175 (Cidade Dois, São Paulo - SP)", fields.destination)
        assertEquals("R$ 13", fields.fare)
        assertEquals("2,5 km", fields.distance)
        assertEquals("1 min", fields.time)
    }
}
