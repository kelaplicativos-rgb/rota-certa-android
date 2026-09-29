package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolTemporalUiNoise0697Test {
    @Test
    fun removesAgoraMesmoSplicedBeforeHouseNumber() {
        val result = FarolTemporalUiNoise0697.clean(
            "Avenida dos Nacionalistas Agora mesmo, 564 (Jardim Tango)",
        )
        assertTrue(result.changed)
        assertEquals("Avenida dos Nacionalistas, 564 (Jardim Tango)", result.cleaned)
    }

    @Test
    fun removesRelativeMinuteLabelBeforeHouseNumber() {
        val result = FarolTemporalUiNoise0697.clean("Rua das Flores há 2 min, 123 (Centro)")
        assertEquals("Rua das Flores, 123 (Centro)", result.cleaned)
    }

    @Test
    fun doesNotRewriteTitleCasedStreetName() {
        val result = FarolTemporalUiNoise0697.clean("Avenida Agora Mesmo, 10")
        assertFalse(result.changed)
        assertEquals("Avenida Agora Mesmo, 10", result.cleaned)
    }

    @Test
    fun ordinaryAddressIsUntouched() {
        val result = FarolTemporalUiNoise0697.clean("Rua Ana Letícia, 225")
        assertFalse(result.changed)
        assertEquals("Rua Ana Letícia, 225", result.cleaned)
    }
}
