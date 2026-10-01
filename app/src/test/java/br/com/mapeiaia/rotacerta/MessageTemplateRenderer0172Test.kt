package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTemplateRenderer0172Test {
    @Test
    fun replacesKnownFieldsWithoutChangingLiteralText() {
        val result = MessageTemplateRenderer0172.apply(
            "Olá, {nome}! {origem} → {destino}",
            mapOf("nome" to "Ana", "origem" to "Santo André", "destino" to "Três Corações"),
        )
        assertEquals("Olá, Ana! Santo André → Três Corações", result)
    }

    @Test
    fun defaultsAreConciseAndPassengerNameIsFirstNameOnly() {
        assertEquals("Gabriela", MessageTemplateStore0172.messageFirstName0714("Gabriela Souza"))
        assertTrue(MessageTemplateStore0172.DEFAULT_TRIP.startsWith("Oi, {nome}!"))
        assertTrue(MessageTemplateStore0172.DEFAULT_TRIP.contains("Está tudo certo para você?"))
        assertEquals(
            "Oi, {nome}! O valor da sua reserva para {lugares} é {valor}.",
            MessageTemplateStore0172.DEFAULT_VALUE,
        )
    }

    @Test
    fun outputIsBounded() {
        val result = MessageTemplateRenderer0172.apply("x".repeat(5_000), emptyMap())
        assertTrue(result.length <= 4_000)
    }
}
