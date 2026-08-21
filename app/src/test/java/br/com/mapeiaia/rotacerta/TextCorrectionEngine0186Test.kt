package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextCorrectionEngine0186Test {
    @Test
    fun performsConservativeOfflineCorrections() {
        val result = PortugueseTextCorrectionEngine0186.correct("voce nao confirmou,estao aqui")
        assertEquals("Você não confirmou, estão aqui", result.corrected)
        assertTrue(result.changeCount > 0)
    }

    @Test
    fun preservesUrlsQueriesEmailsAndNumbersExactly() {
        val url = "https://exemplo.com/voce?q=nao:sim&destino=endereco"
        val email = "contato.voce+nao@exemplo.com"
        val text = "acesse $url ou envie para $email numero 123"
        val result = PortugueseTextCorrectionEngine0186.correct(text)
        assertTrue(result.corrected.contains(url))
        assertTrue(result.corrected.contains(email))
        assertTrue(result.corrected.contains("123"))
    }

    @Test
    fun urlAtSentenceStartIsNeverCapitalized() {
        val url = "https://exemplo.com/voce?q=nao"
        assertEquals(url, PortugueseTextCorrectionEngine0186.correct(url).corrected)
    }
}
