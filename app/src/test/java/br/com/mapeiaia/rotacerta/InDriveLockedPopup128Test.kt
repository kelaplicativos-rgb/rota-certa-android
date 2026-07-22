package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InDriveLockedPopup128Test {
    private val registeredSample = """
        Pedido de viagem
        Carlos
        4.9
        R$ 27
        3 min (1,2 km)
        18 min (9,5 km)
        A
        Rua Serra de Braganca, 500 - Tatuape, Sao Paulo - SP
        B
        Avenida Paulista, 1000 - Bela Vista, Sao Paulo - SP
        Aceitar por R$ 27
        Ofereca sua tarifa
    """.trimIndent()

    private val lockedScreenOfferFromReport = """
        Mapa do Google
        sinet.startup.inDriver:id/de8a2567_PointA
        sinet.startup.inDriver:id/29625ac1_PointB
        Pedido de viagem
        Jeniffer
        4.82
        (104)
        Agora mesmo
        R$ 2,8/km
        ~675 m
        R$ 19
        Preco justo
        Avenida Manuel Velho Moreira, 459 (Parque Colonial, Sao Paulo - SP)
        Hotel Triunfo inn (Rua Alberto Gomes Leite - Vila Portuguesa, Sao Paulo - SP)
        Aceitar por R$ 19
        Ofereca sua tarifa
        R$ 21
        R$ 23
        Pular
    """.trimIndent()

    @Test
    fun manuallyRegisteredInDriveFamilyAcceptsLockedPopupVariant() {
        val template = RideCardTemplateMatcher.createTemplate(
            packageName = RideCardTemplateMatcher.INDRIVE_PACKAGE,
            text = registeredSample,
        )

        assertNotNull(
            RideCardTemplateMatcher.match(
                text = lockedScreenOfferFromReport,
                packageName = RideCardTemplateMatcher.INDRIVE_PACKAGE,
                templates = listOf(template),
            ),
        )
    }

    @Test
    fun sameTextCannotUseTemplateFromAnotherPackage() {
        val template = RideCardTemplateMatcher.createTemplate(
            packageName = RideCardTemplateMatcher.UBER_PACKAGE,
            text = registeredSample,
        )

        assertNull(
            RideCardTemplateMatcher.match(
                text = lockedScreenOfferFromReport,
                packageName = RideCardTemplateMatcher.INDRIVE_PACKAGE,
                templates = listOf(template),
            ),
        )
    }

    @Test
    fun genericSystemScreenWithAddressesIsNotAccepted() {
        val template = RideCardTemplateMatcher.createTemplate(
            packageName = RideCardTemplateMatcher.INDRIVE_PACKAGE,
            text = registeredSample,
        )
        val generic = """
            Configuracoes do sistema
            Rua Um, 100 - Sao Paulo - SP
            Avenida Dois, 200 - Sao Paulo - SP
            R$ 19
        """.trimIndent()

        assertNull(
            RideCardTemplateMatcher.match(
                text = generic,
                packageName = RideCardTemplateMatcher.INDRIVE_PACKAGE,
                templates = listOf(template),
            ),
        )
    }
}
