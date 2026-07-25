package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrimaryVisibleRideCardSelectorTest {
    private val realMultiOfferScreen = """
        Nova notificação
        Luan
        4.71
        (12)
        50 seg.
        R$ 2,5/km
        ~1,2 km
        R$ 31
        Preço justo
        Avenida Mateo Bei 1370 (Jardim Tiete)
        Futuro Geninho (Avenida Presidente Castelo Branco - Jardim Zaira, Mauá - SP),
        Rua Domingas Viola Chiaroto, 147 (Jardim Zaira, Mauá - SP)
        Reclamar
        Ocultar
        Escolher no mapa
        Danilo Ferreira
        4.85
        (6)
        9 min.
        R$ 2/km
        ~5,0 km
        R$ 60
        Avenida Inconfidência Mineira 1712 (Vila Antonieta)
        Rua Vale da Perdiz, 314 (Jardim Miriam, São Paulo - SP),
        Avenida Bento XV, 268 (Vila Missionaria, São Paulo - SP)
        Bruna
        5.0
        (0)
        3 min.
        R$ 1,5/km
        R$ 10
        Nagumo Supermercados (Avenida Sapopemba - Jardim Grimaldi, São Paulo - SP)
        Av. Adutora do Rio Claro, 663 (Jardim Sapopemba, São Paulo - SP)
        PIX
        Mostrar novos pedidos
        Pedidos de viagem
        Demanda
        Desempenho
    """.trimIndent()

    @Test
    fun selectsFirstCompleteVisibleOfferWithoutMixingPassengersOrAddresses() {
        val selection = PrimaryVisibleRideCardSelector.select(realMultiOfferScreen)

        assertEquals(3, selection.cardCount)
        assertEquals(0, selection.selectedIndex)
        assertEquals("Luan", selection.passengerName)
        assertEquals("primeiro_card_completo_visivel", selection.reason)
        assertTrue(selection.selectedText.contains("Avenida Mateo Bei 1370"))
        assertTrue(selection.selectedText.contains("Rua Domingas Viola Chiaroto, 147"))
        assertFalse(selection.selectedText.contains("Danilo Ferreira"))
        assertFalse(selection.selectedText.contains("Bruna"))
        assertFalse(selection.selectedText.contains("Av. Adutora do Rio Claro"))

        val passenger = RidePassengerIdentityPolicy.evaluate(selection.selectedText)
        val trigger = UniversalAddressTrigger.evaluate(selection.selectedText)

        assertTrue(passenger.accepted)
        assertEquals(listOf("Luan"), passenger.candidates)
        assertEquals("Avenida Mateo Bei 1370 (Jardim Tiete)", trigger.pickup)
        assertEquals("Rua Domingas Viola Chiaroto, 147 (Jardim Zaira, Mauá - SP)", trigger.destination)
        assertTrue(trigger.addresses.size >= 2)
    }

    @Test
    fun notificationHeaderIsNeverClassifiedAsPassenger() {
        val passenger = RidePassengerIdentityPolicy.evaluate(
            """
                Nova notificação
                Luan
                4.71
                (12)
                Avenida Mateo Bei 1370
                Rua Domingas Viola Chiaroto, 147
            """.trimIndent(),
        )

        assertTrue(passenger.accepted)
        assertEquals(listOf("Luan"), passenger.candidates)
    }

    @Test
    fun singleCardLayoutIsPreserved() {
        val single = """
            Pedido de viagem
            Lúcio Ramos
            5.0
            (4)
            Rua Normanda 61
            R. Pomerânia, 100
        """.trimIndent()

        val selection = PrimaryVisibleRideCardSelector.select(single)

        assertEquals(single, selection.selectedText)
        assertEquals("card_individual_ou_layout_sem_lista", selection.reason)
    }
}
