package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgendaMinimal0277Test {
    @Test
    fun `same passenger UUID in one card is coalesced before contact traversal`() {
        val uuid = "123e4567-e89b-12d3-a456-426614174000"
        val passengers = listOf(
            BlaBlaCollectorPassenger(
                name = "Passageiro",
                seats = 1,
                booking_href = "https://www.blablacar.com.br/rides/offer/passenger/$uuid/0?id=trip-12345678",
            ),
            BlaBlaCollectorPassenger(
                name = "Passageiro",
                seats = 1,
                booking_href = "https://www.blablacar.com.br/booking/$uuid/details?id=trip-12345678",
            ),
        )

        val result = BlaBlaCollectorPassengerModule.coalesceDuplicateEvidence(passengers)

        assertEquals(1, result.size)
        assertEquals(1, result.single().seats)
    }

    @Test
    fun `different passenger UUIDs in one card remain distinct`() {
        val passengers = listOf(
            BlaBlaCollectorPassenger(
                name = "Passageiro A",
                booking_href = "https://www.blablacar.com.br/rides/offer/passenger/123e4567-e89b-12d3-a456-426614174000/0?id=trip-12345678",
            ),
            BlaBlaCollectorPassenger(
                name = "Passageiro B",
                booking_href = "https://www.blablacar.com.br/rides/offer/passenger/223e4567-e89b-12d3-a456-426614174000/0?id=trip-12345678",
            ),
        )

        assertEquals(2, BlaBlaCollectorPassengerModule.coalesceDuplicateEvidence(passengers).size)
    }

    @Test
    fun `same UUID remains valid when processed in another card`() {
        val uuid = "123e4567-e89b-12d3-a456-426614174000"
        val firstCard = BlaBlaCollectorPassengerModule.coalesceDuplicateEvidence(
            listOf(BlaBlaCollectorPassenger(booking_href = "https://www.blablacar.com.br/rides/offer/passenger/$uuid/0?id=trip-11111111")),
        )
        val secondCard = BlaBlaCollectorPassengerModule.coalesceDuplicateEvidence(
            listOf(BlaBlaCollectorPassenger(booking_href = "https://www.blablacar.com.br/rides/offer/passenger/$uuid/0?id=trip-22222222")),
        )

        assertEquals(1, firstCard.size)
        assertEquals(1, secondCard.size)
    }

    @Test
    fun `planner allows outbound only`() {
        val account = verifiedAccount()
        val draft = AgendaPublisherDraft(
            monthYear = "08/2026",
            outbound = validTemplate("Três Corações", "São Tomé das Letras"),
            inbound = AgendaPublishTemplate(),
            profiles = listOf(AgendaPublishProfileDraft(accountId = account.id, outboundDays = "25", inboundDays = "")),
        )

        val plan = AgendaBatchPublisherPlanner.plan(draft, listOf(account), emptyList())

        assertTrue(plan.errors.isEmpty(), plan.errors.joinToString())
        assertEquals(1, plan.batches.size)
        assertEquals(AgendaPublishDirection.IDA, plan.batches.single().direction)
    }

    @Test
    fun `planner allows inbound only`() {
        val account = verifiedAccount()
        val draft = AgendaPublisherDraft(
            monthYear = "08/2026",
            outbound = AgendaPublishTemplate(),
            inbound = validTemplate("São Tomé das Letras", "Três Corações"),
            profiles = listOf(AgendaPublishProfileDraft(accountId = account.id, outboundDays = "", inboundDays = "25")),
        )

        val plan = AgendaBatchPublisherPlanner.plan(draft, listOf(account), emptyList())

        assertTrue(plan.errors.isEmpty(), plan.errors.joinToString())
        assertEquals(1, plan.batches.size)
        assertEquals(AgendaPublishDirection.VOLTA, plan.batches.single().direction)
    }

    private fun verifiedAccount() = BlaBlaDynamicAccount(
        id = "account-1",
        label = "Conta teste",
        webProfileName = "profile-test",
        profileUuid = "7371f028-9c55-4903-8444-308015823efd",
    )

    private fun validTemplate(origin: String, destination: String) = AgendaPublishTemplate(
        originAddress = origin,
        destinationAddress = destination,
        departureTime = "10:30",
        seats = 4,
    )
}
