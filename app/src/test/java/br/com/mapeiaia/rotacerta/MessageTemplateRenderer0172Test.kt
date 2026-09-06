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
    fun outputIsBounded() {
        val result = MessageTemplateRenderer0172.apply("x".repeat(5_000), emptyMap())
        assertTrue(result.length <= 4_000)
    }
}
