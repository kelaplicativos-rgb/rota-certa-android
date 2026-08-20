package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenPhoneLinkTest {
    @Test
    fun extractsFormattedMobileNumber() {
        val target = ScreenPhoneLink.findBest("Telefone: (11) 98765-4321")

        requireNotNull(target)
        assertEquals("11987654321", target.nationalDigits)
        assertEquals("5511987654321", target.internationalDigits)
        assertEquals("(11) 98765-4321", target.displayNumber)
        assertEquals("https://wa.me/5511987654321", target.url)
    }

    @Test
    fun extractsNumberWithBrazilCountryCode() {
        val target = ScreenPhoneLink.findBest("WhatsApp +55 35 99999-1234")

        requireNotNull(target)
        assertEquals("35999991234", target.nationalDigits)
    }

    @Test
    fun extractsBrazilianLandline() {
        val target = ScreenPhoneLink.findBest("Contato: (31) 3333-4455")

        requireNotNull(target)
        assertEquals("3133334455", target.nationalDigits)
        assertEquals("(31) 3333-4455", target.displayNumber)
    }

    @Test
    fun prefersWhatsAppLabeledNumber() {
        val target = ScreenPhoneLink.findBest(
            """
            Central: (11) 3333-4444
            WhatsApp do passageiro: (35) 98888-7777
            """.trimIndent(),
        )

        requireNotNull(target)
        assertEquals("35988887777", target.nationalDigits)
        assertTrue(target.score > 100)
    }

    @Test
    fun rejectsCpfAndUnrelatedNumbers() {
        assertNull(ScreenPhoneLink.findBest("CPF: 123.456.789-01"))
        assertNull(ScreenPhoneLink.findBest("Valor R$ 119,99 - chegada 18:30"))
        assertNull(ScreenPhoneLink.findBest("Codigo da corrida: 123456789"))
    }

    @Test
    fun rejectsInvalidAreaCodeAndRepeatedPlaceholder() {
        assertNull(ScreenPhoneLink.findBest("Telefone: (20) 99999-1234"))
        assertNull(ScreenPhoneLink.findBest("Telefone: (11) 99999-9999"))
    }
}
