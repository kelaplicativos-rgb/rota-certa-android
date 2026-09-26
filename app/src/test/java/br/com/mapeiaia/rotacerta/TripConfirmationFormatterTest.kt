package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripConfirmationFormatterTest {
    @Test
    fun formatsBlaBlaCarPassengerConversationWithoutChatNoise() {
        val text = """
            Julio
            5,0/5
            São Paulo → São Tomé das Letras
            Confirmado • sex., 31 julho, 11:30
            Qualquer dúvida, pode me chamar
            21:43
            Que vai para São Thomé
            21:44
            00:16
        """.trimIndent()

        assertEquals(
            """
            Olá, Julio! Confirmando sua viagem:

            São Paulo → São Tomé das Letras
            Sexta-feira, 31 de julho, às 11h30.

            Está tudo certo?
            """.trimIndent(),
            TripConfirmationFormatter.extractAndFormat(text),
        )
    }

    @Test
    fun repairsBrokenOcrLinesAndDropsAudioDurationAndSymbols() {
        val text = """
            São Paulo → São
            Tomé das
            Letras
            00:16
            <
            Confirmado
            sex., 31 de julho, 11:30
        """.trimIndent()

        assertEquals(
            """
            Confirmando sua viagem:

            São Paulo → São Tomé das Letras
            Sexta-feira, 31 de julho, às 11h30.

            Está tudo certo?
            """.trimIndent(),
            TripConfirmationFormatter.extractAndFormat(text),
        )
    }

    @Test
    fun acceptsFullWeekdayAndHourWithoutMinutesInFinalMessage() {
        val text = """
            Sua mensagem para Lara
            Campinas → São Paulo
            Confirmado
            domingo, 2 de agosto, 08:00
        """.trimIndent()

        assertEquals(
            """
            Olá, Lara! Confirmando sua viagem:

            Campinas → São Paulo
            Domingo, 2 de agosto, às 8h.

            Está tudo certo?
            """.trimIndent(),
            TripConfirmationFormatter.extractAndFormat(text),
        )
    }

    @Test
    fun refusesToCopyWhenRouteOrScheduleIsMissing() {
        assertNull(TripConfirmationFormatter.extractAndFormat("Julio\nConfirmado\n11:30"))
        assertNull(TripConfirmationFormatter.extractAndFormat("São Paulo → Campinas\nConfirmado"))
    }
}
