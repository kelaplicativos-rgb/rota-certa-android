package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextReplacementLengthPolicy0186Test {
    @Test
    fun acceptsReplacementThatFitsWithoutTruncatingSuffix() {
        assertEquals(12_000, TextReplacementLengthPolicy0186.allowedFinalLength(11_990, 10, 20, 12_000))
    }

    @Test
    fun rejectsReplacementThatWouldOverflowFieldLimit() {
        assertNull(TextReplacementLengthPolicy0186.allowedFinalLength(11_990, 5, 20, 12_000))
    }

    @Test
    fun rejectsInvalidLengths() {
        assertNull(TextReplacementLengthPolicy0186.allowedFinalLength(10, 11, 1, 12_000))
    }
}
