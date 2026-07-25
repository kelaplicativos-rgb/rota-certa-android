package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FastRouteScreenOff128Test {
    private fun sourceFile(name: String): File = listOf(
        File("src/main/java/br/com/mapeiaia/rotacerta/$name"),
        File("app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
    ).firstOrNull(File::exists) ?: error("$name nao encontrado")

    @Test
    fun markerlessInDrivePopupStillMatchesManualTemplateFromSamePackage() {
        val learnedCard = """
            Pedido de viagem
            R$ 19
            4 min (1,6 km)
            A
            Avenida Manuel Velho Moreira, 459
            B
            Rua Alberto Gomes Leite, 100
            Aceitar por R$ 19
            Ofereça sua tarifa
        """.trimIndent()
        val lockedPopupRead = """
            Mapa do Google
            Pedido de viagem
            Jeniffer
            4.82
            (104)
            Agora mesmo
            R$ 2,8/km
            ~675 m
            R$ 19
            Preço justo
            Avenida Manuel Velho Moreira, 459 (Parque Colonial, São Paulo - SP)
            Hotel Triunfo inn (Rua Alberto Gomes Leite - Vila Portuguesa, São Paulo - SP)
            Aceitar por R$ 19
            Ofereça sua tarifa
            R$ 21
            R$ 23
            Pular
        """.trimIndent()
        val template = RideCardTemplateMatcher.createTemplate(
            packageName = RideCardTemplateMatcher.INDRIVE_PACKAGE,
            text = learnedCard,
        )

        val match = RideCardTemplateMatcher.match(
            text = lockedPopupRead,
            packageName = RideCardTemplateMatcher.INDRIVE_PACKAGE,
            templates = listOf(template),
        )

        assertNotNull(match)
        assertEquals(template.id, match?.template?.id)
    }

    @Test
    fun exactAddressRouteCanDecideWithoutWaitingForSeparateGeocode() {
        val engine = DecisionEngine()
        val result = engine.decide(
            fields = RideFields(destination = "Hotel Triunfo inn, São Paulo - SP"),
            settings = AppSettings(
                homeTargetEnabled = true,
                alternativeTargetEnabled = false,
                homeRadiusKm = 7.0,
            ),
            destinationCoordinate = null,
            homeCoordinate = Coordinate(-23.59446, -46.47958),
            alternativeCoordinate = null,
            fullText = "card",
            homeDistanceKm = 4.9,
            alternativeDistanceKm = null,
        )

        assertEquals(Recommendation.GoodRide, result.recommendation)
        assertEquals(4.9, result.destinationToHomeKm ?: 0.0, 0.0001)
    }

    @Test
    fun generatedServiceUsesOneExactAddressMatrixAndPersistentCache() {
        val service = sourceFile("LiveRideAccessibilityService.kt").readText()
        val maps = sourceFile("GoogleMapsService.kt").readText()
        val routeStart = service.indexOf("private suspend fun analyzeUniversalTwoAddress(")
        val routeEnd = service.indexOf("private suspend fun applyUniversalTwoAddressResult(", routeStart)
        val route = service.substring(routeStart, routeEnd)

        assertTrue("mudança de tela precisa limpar imediatamente", "immediate_screen_change_clear_checklist_13" in service)
        assertTrue("Casa e alfinetes devem compartilhar uma chamada", "single_exact_route_matrix_checklist_13" in route)
        assertEquals(1, Regex("drivingDistancesFromAddressKm\\(").findAll(route).count())
        assertTrue("Routes API deve receber endereco diretamente", "direct_address_route_matrix_0_1_128" in maps)
        assertTrue("cache de rota deve sobreviver ao processo", "PERSISTENT_ADDRESS_ROUTE_PREFIX" in maps)
        assertTrue("cache exato precisa ser consultável antes da rede", "simple_cached_route_peek_checklist_13" in maps)
        assertTrue("primeiro caminho nao deve repetir timeout longo", "const val ROUTE_REQUEST_ATTEMPTS = 1" in maps)
    }
}
