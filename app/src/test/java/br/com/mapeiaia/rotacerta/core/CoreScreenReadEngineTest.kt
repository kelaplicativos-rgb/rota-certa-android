package br.com.mapeiaia.rotacerta.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreScreenReadEngineTest {
    @Test
    fun prepareMergesAccessibilityAndOcrWithoutDuplicatingLines() {
        val snapshot = CoreScreenReadEngine.prepare(
            accessibilityText = "Pedido de viagem\nRua A\nRua B",
            ocrText = "Rua A\nAceitar por R$ 44",
            fallbackText = "fallback nao deve entrar",
            allowPopupCandidate = false,
        )

        assertEquals(CoreScreenReadKind.Ready, snapshot.kind)
        assertEquals(
            "Pedido de viagem\nRua A\nRua B\nAceitar por R$ 44",
            snapshot.text,
        )
        assertEquals(CoreScreenReadEngine.stableHash(snapshot.text), snapshot.hash)
        assertTrue(snapshot.sourceSummary.contains("popup=false"))
    }

    @Test
    fun prepareUsesFallbackWhenMergedLiveTextIsEmpty() {
        val snapshot = CoreScreenReadEngine.prepare(
            accessibilityText = "   ",
            ocrText = "\n\n",
            fallbackText = "  Pedido de viagem  \n  Aceitar por R$ 44  ",
            allowPopupCandidate = false,
        )

        assertEquals(CoreScreenReadKind.Ready, snapshot.kind)
        assertEquals("Pedido de viagem\nAceitar por R$ 44", snapshot.text)
        assertEquals(CoreScreenReadEngine.stableHash(snapshot.text), snapshot.hash)
    }

    @Test
    fun preparePopupCandidateUsesOnlyFallbackText() {
        val snapshot = CoreScreenReadEngine.prepare(
            accessibilityText = "texto antigo",
            ocrText = "ocr antigo",
            fallbackText = "  Card pop-up  \n  Aceitar  ",
            allowPopupCandidate = true,
        )

        assertEquals(CoreScreenReadKind.Ready, snapshot.kind)
        assertEquals("Card pop-up\nAceitar", snapshot.text)
        assertEquals(CoreScreenReadEngine.stableHash(snapshot.text), snapshot.hash)
        assertTrue(snapshot.sourceSummary.contains("popup=true"))
    }

    @Test
    fun prepareEmptyInputReturnsEmptySnapshot() {
        val snapshot = CoreScreenReadEngine.prepare(
            accessibilityText = "   ",
            ocrText = "   ",
            fallbackText = "   ",
            allowPopupCandidate = false,
        )

        assertEquals(CoreScreenReadKind.Empty, snapshot.kind)
        assertEquals("", snapshot.text)
        assertEquals(0, snapshot.hash)
    }

    @Test
    fun stableHashNormalizesDirtyTextBeforeHashing() {
        val clean = "Pedido de viagem\nAceitar por R$ 44"
        val dirty = "  Pedido de viagem  \n\n  Aceitar por R$ 44  \n"

        assertEquals(clean, CoreScreenReadEngine.normalizeText(dirty))
        assertEquals(clean.hashCode(), CoreScreenReadEngine.stableHash(dirty))
        assertEquals(CoreScreenReadEngine.stableHash(clean), CoreScreenReadEngine.stableHash(dirty))
    }
}
