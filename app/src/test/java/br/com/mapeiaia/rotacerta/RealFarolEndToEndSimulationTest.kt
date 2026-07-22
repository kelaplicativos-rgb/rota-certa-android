package br.com.mapeiaia.rotacerta

import br.com.mapeiaia.rotacerta.core.CoreBubbleDecisionEngine
import br.com.mapeiaia.rotacerta.core.CoreBubbleMode
import br.com.mapeiaia.rotacerta.core.CoreBubblePresenter
import br.com.mapeiaia.rotacerta.core.RideWindowEventAction
import br.com.mapeiaia.rotacerta.core.RideWindowEventPolicy
import br.com.mapeiaia.rotacerta.core.RotaCertaCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate de liberacao baseado nos cards reais enviados pelo usuario.
 *
 * O teste percorre o mesmo contrato de negocio do app:
 * texto real -> parser -> destino B -> modelo cadastrado -> classificacao de
 * card aberto -> decisao por distancia real recebida -> cor e km da bolinha.
 */
class RealFarolEndToEndSimulationTest {
    private val parser = RideTextParser()
    private val packageName = RideCardTemplateMatcher.INDRIVE_PACKAGE
    private val homeCoordinate = Coordinate(-23.5954434, -46.4796678)

    @Test
    fun passiveSystemEventsCannotKillTheInDriveWindowBeforeDecision() {
        listOf(
            "com.android.systemui",
            "com.sec.android.app.launcher",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
        ).forEach { eventPackage ->
            val action = RideWindowEventPolicy.decide(
                eventPackageIsMonitored = false,
                rootPackageIsMonitored = true,
                eventPackageIsPassive = true,
                hasActiveRegisteredDecision = false,
            )
            assertEquals("Evento $eventPackage apagou o card antes da decisao", RideWindowEventAction.PreserveMonitoredRoot, action)
        }
    }

    @Test
    fun exactRealCardsReachGreenOrRedAndAlwaysShowDestinationDistance() {
        val template = RideCardTemplateMatcher.createTemplate(
            packageName = packageName,
            text = realCards.first().text,
            name = "Card inDrive real de referencia",
        )
        val settings = AppSettings(
            homeAddress = "R. Lateral, 15 - Cidade São Mateus, São Paulo - SP, 04891-240, Brasil",
            homeCoordinate = homeCoordinate,
            homeRadiusKm = 5.0,
            googleMapsApiKey = "simulation-key-present",
            requireRegisteredRideCard = true,
        )

        realCards.forEachIndexed { index, case ->
            val fields = parser.parse(case.text, packageName)
            assertEquals("Destino final errado no caso ${case.name}", case.destination, fields.destination)
            assertFalse("Embarque e destino ficaram iguais no caso ${case.name}", fields.pickup.equals(fields.destination, ignoreCase = true))

            val coreDecision = RotaCertaCore.matchRegisteredOpenCard(
                packageName = packageName,
                text = case.text,
                fields = fields,
                templates = listOf(template),
            )
            assertTrue("Card real nao chegou ao gate de rota: ${case.name}: ${coreDecision.reason}", coreDecision.canAnalyzeRoute)
            assertNotNull("Modelo cadastrado nao foi associado ao card ${case.name}", coreDecision.matched)

            val simulatedRealRouteKm = if (index % 2 == 0) 3.9 else 8.4
            val result = DecisionEngine().decide(
                fields = fields,
                settings = settings,
                destinationCoordinate = Coordinate(-23.60 - index / 1000.0, -46.48 - index / 1000.0),
                homeCoordinate = homeCoordinate,
                alternativeCoordinate = null,
                fullText = case.text,
                homeDistanceKm = simulatedRealRouteKm,
                alternativeDistanceKm = null,
            )
            val render = CoreBubbleDecisionEngine.fromAnalysis(
                classification = coreDecision.classification,
                result = result,
                distanceKm = result.pickupToHomeKm,
            )
            val presentation = CoreBubblePresenter.present(render.mode, render.distanceKm)

            val expectedMode = if (simulatedRealRouteKm <= settings.homeRadiusKm) CoreBubbleMode.Good else CoreBubbleMode.Bad
            assertEquals("Cor errada no caso ${case.name}", expectedMode, render.mode)
            assertEquals("Km real nao chegou ao render no caso ${case.name}", simulatedRealRouteKm, render.distanceKm ?: -1.0, 0.0001)
            assertTrue("Bolinha ficou sem numero no caso ${case.name}", presentation.text.isNotBlank())
        }
    }

    @Test
    fun pluralRideListStillCannotReleaseFarol() {
        val listText = """
            Pedidos de viagem
            Offline
            R$ 2,4/km ~1,8 km
            R$ 23 Preço justo
            Avenida Ragueb Chohfi 1400 (Jardim Três Marias)
            Poupatempo - Cidade Tiradentes (Rua Sara Kubitscheck - Cidade Tiradentes, São Paulo - SP)
            PIX
            R$ 1,4/km ~2,7 km
            R$ 28
            Rua Outra, 16
            Avenida Final, 900
        """.trimIndent()
        val fields = parser.parse(listText, packageName)
        val template = RideCardTemplateMatcher.createTemplate(packageName, realCards.first().text)
        val decision = RotaCertaCore.matchRegisteredOpenCard(packageName, listText, fields, listOf(template))
        assertFalse(decision.canAnalyzeRoute)
    }

    private data class RealCardCase(
        val name: String,
        val destination: String,
        val text: String,
    )

    private val realCards = listOf(
        RealCardCase(
            name = "Maua",
            destination = "Rua Joaquim Pereira dos Santos, 527 (Vila Assis Brasil, Mauá - State of São Paulo)",
            text = """
                Offline
                Pedido de viagem
                R$ 1,7/km ~2,3 km
                R$ 25
                A Rua Lúcio Cardim Filho, 311 (Jardim Sapopemba, São Paulo - SP)
                B Rua Joaquim Pereira dos Santos, 527 (Vila Assis Brasil, Mauá - State of São Paulo)
                Maquininha de cartão
                Aceitar por R$ 25
                Ofereça sua tarifa
            """.trimIndent(),
        ),
        RealCardCase(
            name = "Cidade Lider",
            destination = "Av. Maria Luiza Americano, 2673 (Cidade Líder)",
            text = """
                Online
                Pedido de viagem
                R$ 2,3/km ~1,4 km
                R$ 15
                A Comercial Esperança - São Paulo São Mateus (Avenida Mateo Bei - Cidade São Mateus, São Paulo - State of São Paulo)
                B Av. Maria Luiza Americano, 2673 (Cidade Líder)
                PIX
                Aceitar por R$ 15
                Ofereça sua tarifa
            """.trimIndent(),
        ),
        RealCardCase(
            name = "Bras",
            destination = "Rua Doutor Virgílio do Nascimento, 638 (Brás, São Paulo - Estado de São Paulo)",
            text = """
                Offline
                Pedido de viagem
                R$ 2,2/km ~2,1 km
                R$ 15
                A Rua Silva Pinto, 280 (Bom Retiro, São Paulo - Estado de São Paulo)
                B Rua Doutor Virgílio do
                Nascimento, 638 (Brás, São Paulo - Estado de São Paulo)
                Aceitar por R$ 15
                Ofereça sua tarifa
            """.trimIndent(),
        ),
        RealCardCase(
            name = "Rio Grande da Serra",
            destination = "Av. Francisco Morais Ramos, 1800 (Jardim Santa Tereza, Rio Grande da Serra - SP, 09450-000)",
            text = """
                Offline
                Pedido de viagem
                R$ 1,4/km ~4,3 km
                R$ 44
                A Rua Pedro Leme 56 (Parque Boa Esperança)
                B Av. Francisco Morais Ramos, 1800 (Jardim Santa Tereza, Rio Grande da Serra - SP, 09450-000)
                PIX
                Aceitar por R$ 44
                Ofereça sua tarifa
            """.trimIndent(),
        ),
        RealCardCase(
            name = "Imirim",
            destination = "Rua José Inácio de Oliveira, 18 (Imirim, São Paulo - SP)",
            text = """
                Pedido de viagem
                R$ 1,7/km ~1,7 km
                R$ 18
                A Rua Werner Von Siemens, 408 (Lapa de Baixo, São Paulo - SP)
                B Rua José Inácio de Oliveira, 18 (Imirim, São Paulo - SP)
                Aceitar por R$ 18
                Ofereça sua tarifa
            """.trimIndent(),
        ),
        RealCardCase(
            name = "Jardim Helian",
            destination = "Rua John Speers, 1469 (Jardim Helian, São Paulo - SP)",
            text = """
                Offline
                Pedido de viagem
                R$ 1,7/km ~6,6 km
                R$ 28 Preço justo
                A Rua Alves Seixas 296 (Sapopemba)
                B Rua John Speers, 1469 (Jardim Helian, São Paulo - SP)
                PIX
                Viagem Plus
                Aceitar por R$ 28
                Ofereça sua tarifa
            """.trimIndent(),
        ),
        RealCardCase(
            name = "Sao Rafael",
            destination = "Rua dos Jasmins, 14 (São Rafael, São Paulo - SP)",
            text = """
                Online
                Pedido de viagem
                R$ 2,7/km ~975 m
                R$ 16 Preço justo
                A Avenida Sapopemba 14446
                B Rua dos Jasmins, 14 (São Rafael, São Paulo - SP)
                Aceitar por R$ 16
                Ofereça sua tarifa
            """.trimIndent(),
        ),
        RealCardCase(
            name = "McDonalds Sao Mateus sem marcadores textuais",
            destination = "McDonald's (Avenida Mateo Bei - Cidade São Mateus, São Paulo - SP)",
            text = """
                Pedido de viagem
                R$ 4/km ~1,5 km
                R$ 10 Preço justo
                Avenida Ministro José Américo de Almeida, 464 (Jardim Sapopemba, São Paulo - SP)
                McDonald's (Avenida Mateo Bei - Cidade São Mateus, São Paulo - SP)
                PIX
                Aceitar por R$ 10
                Ofereça sua tarifa
            """.trimIndent(),
        ),
        RealCardCase(
            name = "Vila Regente Feijo",
            destination = "Rua Emília Marengo, 179 (Vila Regente Feijó, São Paulo - SP)",
            text = """
                Offline
                Pedido de viagem
                R$ 1,9/km ~4,0 km
                R$ 30
                A Rua Joaquim Meira de Siqueira
                591 (Jardim Nossa Senhora do Carmo, São Paulo - SP)
                B Rua Emília Marengo, 179 (Vila Regente Feijó, São Paulo - SP)
                Aceitar por R$ 30
                Ofereça sua tarifa
            """.trimIndent(),
        ),
    )
}
