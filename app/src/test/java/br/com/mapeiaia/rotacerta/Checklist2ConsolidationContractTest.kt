package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Checklist2ConsolidationContractTest {
    @Test
    fun `novos recursos nascem seguros sem reabrir leitura legada`() {
        val settings = AppSettings()

        assertTrue(settings.restrictToSelectedRideApps)
        assertFalse(settings.monitor99)
        assertFalse(settings.monitorUber)
        assertFalse(settings.monitorInDrive)
        assertTrue(settings.requireRegisteredRideCard)
        assertFalse(settings.diagnosticsEnabled)
        assertTrue(settings.automaticCardCaptureEnabled)
        assertTrue(settings.multiCardFocusLockEnabled)
        assertTrue(settings.proximityPopupAutoCloseEnabled)
    }

    @Test
    fun `backup aceita respostas rapidas sem quebrar backups antigos`() {
        val emptyBackup = RotaCertaBackup()
        assertTrue(emptyBackup.quickReplies.isEmpty())

        val withReply = emptyBackup.copy(
            quickReplies = listOf(
                QuickReply(
                    id = "reply-1",
                    title = "Chegada",
                    text = "Estou chegando ao ponto de encontro.",
                ),
            ),
        )
        assertTrue(withReply.quickReplies.single().text.contains("chegando"))
    }
}
