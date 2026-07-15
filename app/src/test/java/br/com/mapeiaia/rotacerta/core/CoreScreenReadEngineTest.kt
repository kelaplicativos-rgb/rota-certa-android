package br.com.mapeiaia.rotacerta.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreScreenReadEngineTest {
    @Test
    fun prepareUsesOnlyCurrentCallbackAndNeverMergesPreviousCardBuffers() {
        val snapshot = CoreScreenReadEngine.prepare(
            accessibilityText = "Pedido antigo\nRua antiga\nAceitar por R$ 30",
            ocrText = "OCR antigo\nDestino antigo",
            fallbackText = "  Pedido atual  \n  Rua atual  \n  Aceitar por R$ 44  ",
            allowPopupCandidate = false,
        )

        assertEquals(CoreScreenReadKind.Ready, snapshot.kind)
        assertEquals(
            "Pedido atual\nRua atual\nAceitar por R$ 44",
            snapshot.text,
        )
        assertEquals(CoreScreenReadEngine.stableHash(snapshot.text), snapshot.hash)
        assertTrue(snapshot.sourceSummary.contains("isolated=true"))
    }

    @Test
    fun prepareUsesCurrentFallbackWhenStoredSourcesAreEmpty() {
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
    fun preparePopupCandidateUsesOnlyCurrentText() {
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
    fun prepareEmptyCurrentCallbackReturnsEmptyEvenWhenOldBuffersContainCard() {
        val snapshot = CoreScreenReadEngine.prepare(
            accessibilityText = "Pedido antigo\nDestino antigo",
            ocrText = "Aceitar por R$ 55",
            fallbackText = "   ",
            allowPopupCandidate = false,
        )

        assertEquals(CoreScreenReadKind.Empty, snapshot.kind)
        assertEquals("", snapshot.text)
        assertEquals(0, snapshot.hash)
    }

    @Test
    fun mergeRemainsAvailableForExplicitNonLiveUse() {
        assertEquals(
            "Pedido de viagem\nRua A\nRua B\nAceitar por R$ 44",
            CoreScreenReadEngine.merge(
                accessibilityText = "Pedido de viagem\nRua A\nRua B",
                ocrText = "Rua A\nAceitar por R$ 44",
            ),
        )
    }

    @Test
    fun stableHashNormalizesDirtyTextBeforeHashing() {
        val clean = "Pedido de viagem\nAceitar por R$ 44"
        val dirty = "  Pedido de viagem  \n\n  Aceitar por R$ 44  \n"
        val sameMeaningDifferentFormatting = "PEDIDO DE VIAGEM\nAceitar por R$44"
        val differentCard = "Pedido de viagem\nAceitar por R$ 55"

        assertEquals(clean, CoreScreenReadEngine.normalizeText(dirty))
        assertEquals(CoreScreenReadEngine.stableHash(clean), CoreScreenReadEngine.stableHash(dirty))
        assertEquals(CoreScreenReadEngine.stableHash(clean), CoreScreenReadEngine.stableHash(sameMeaningDifferentFormatting))
        assertNotEquals(CoreScreenReadEngine.stableHash(clean), CoreScreenReadEngine.stableHash(differentCard))
    }
}
