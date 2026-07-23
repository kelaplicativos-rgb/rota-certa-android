package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualRideCardCapturePolicyTest {
    @Test
    fun selectedReadableCardCanStoreImageAndCreateTemplate() {
        val result = ManualRideCardCapturePolicy.evaluate(
            packageSelected = true,
            text = "Pedido de viagem\n5 min (1,2 km)\nRua A, 10\n8 min (3,4 km)\nRua B, 20\nAceitar por R$ 30",
            bitmapWidth = 1080,
            bitmapHeight = 2340,
            looksLikeRideCard = true,
        )

        assertTrue(result.canStoreImage)
        assertTrue(result.canCreateTemplate)
    }

    @Test
    fun partialReadableCardIsStoredAsCandidateOnly() {
        val result = ManualRideCardCapturePolicy.evaluate(
            packageSelected = true,
            text = "Pedido de viagem\nAceitar por R$ 30\nTrecho parcialmente reconhecido pelo OCR",
            bitmapWidth = 1080,
            bitmapHeight = 2340,
            looksLikeRideCard = false,
        )

        assertTrue(result.canStoreImage)
        assertFalse(result.canCreateTemplate)
    }

    @Test
    fun wrongAppOrUnrelatedScreenIsRejected() {
        assertFalse(
            ManualRideCardCapturePolicy.evaluate(
                packageSelected = false,
                text = "Pedido de viagem\nRua A\nRua B",
                bitmapWidth = 1080,
                bitmapHeight = 2340,
                looksLikeRideCard = true,
            ).canStoreImage,
        )
        assertFalse(
            ManualRideCardCapturePolicy.evaluate(
                packageSelected = true,
                text = "Tela inicial sem card de corrida",
                bitmapWidth = 1080,
                bitmapHeight = 2340,
                looksLikeRideCard = false,
            ).canStoreImage,
        )
    }
}
