package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullScreenRideCapturePolicyTest {
    @Test
    fun savesSelectedInDriveScreenWithDestinationEvenBeforeModelMatch() {
        assertTrue(
            FullScreenRideCapturePolicy.shouldSaveCandidate(
                packageSelected = true,
                automaticCaptureEnabled = true,
                text = "Pedido de viagem\nR$ 31\nA Rua A, 10\nB Rua B, 20\nAceitar por R$ 31",
                fields = RideFields(
                    pickup = "Rua A, 10",
                    destination = "Rua B, 20",
                ),
            ),
        )
    }

    @Test
    fun refusesUnselectedAppsAndScreensWithoutDestination() {
        val text = "Pedido de viagem\nAceitar por R$ 31"
        assertFalse(
            FullScreenRideCapturePolicy.shouldSaveCandidate(
                packageSelected = false,
                automaticCaptureEnabled = true,
                text = text,
                fields = RideFields(destination = "Rua B, 20"),
            ),
        )
        assertFalse(
            FullScreenRideCapturePolicy.shouldSaveCandidate(
                packageSelected = true,
                automaticCaptureEnabled = true,
                text = text,
                fields = RideFields(),
            ),
        )
    }
}
