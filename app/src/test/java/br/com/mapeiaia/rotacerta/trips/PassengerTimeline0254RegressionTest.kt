package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PassengerTimeline0254RegressionTest {
    @Test
    fun externalBoardingDoesNotRepeatPassengerName() {
        assertEquals(
            "São Paulo",
            passengerTimelinePlaceLabel("Fabíola", "Fabíola, São Paulo"),
        )
    }

    @Test
    fun externalBoardingRemovesSeatSuffixTogetherWithPassengerName() {
        assertEquals(
            "Três Corações",
            passengerTimelinePlaceLabel("Taciani", "Taciani (2), Três Corações"),
        )
    }

    @Test
    fun passengerNameMatchingIsAccentInsensitiveButDoesNotInventPlaces() {
        assertEquals(
            "São Paulo",
            passengerTimelinePlaceLabel("Fabiola", "Fabíola, São Paulo"),
        )
        assertEquals(
            "Santa Bárbara",
            passengerTimelinePlaceLabel("Ana", "Santa Bárbara"),
        )
    }

    @Test
    fun longRouteLabelsAreCompactedGenerically() {
        val compact = passengerTimelineCompactPlace("São Tomé das Letras", maxLength = 18)
        assertEquals("São Tomé d. L.", compact)
        assertTrue(compact.length <= 18)
        assertEquals("Três Corações", passengerTimelineCompactPlace("Três Corações", maxLength = 18))
    }

    @Test
    fun fareClipboardContainsOnlyTheFormattedValue() {
        val clipboard = passengerTimelineFareClipboardText(
            amountMinorUnits = 19_250L,
            currencyCode = "BRL",
            localeTag = "pt-BR",
        )
        assertTrue(clipboard.contains("192"))
        assertFalse(clipboard.contains("Olá", ignoreCase = true))
        assertFalse(clipboard.contains("Fabíola", ignoreCase = true))
        assertFalse(clipboard.contains("reserva", ignoreCase = true))
    }
}
