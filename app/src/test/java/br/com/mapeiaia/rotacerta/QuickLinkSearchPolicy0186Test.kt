package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickLinkSearchPolicy0186Test {
    private val links = listOf(
        QuickLink0172("1", "Mapa São Paulo", "Rota principal", "https://example.com/Viagem/ABC"),
        QuickLink0172("2", "Financeiro", "Controle diário", "https://caixa.example.br"),
    )

    @Test
    fun searchesNameDescriptionAndUrlIgnoringCaseAccentsAndSpaces() {
        assertEquals(listOf("1"), QuickLinkSearchPolicy0186.filter(links, "  sao   PAULO ").map { it.id })
        assertEquals(listOf("2"), QuickLinkSearchPolicy0186.filter(links, "diario").map { it.id })
        assertEquals(listOf("1"), QuickLinkSearchPolicy0186.filter(links, "viagem/abc").map { it.id })
    }

    @Test
    fun emptyQueryReturnsAll() {
        assertEquals(links, QuickLinkSearchPolicy0186.filter(links, "   "))
    }
}
