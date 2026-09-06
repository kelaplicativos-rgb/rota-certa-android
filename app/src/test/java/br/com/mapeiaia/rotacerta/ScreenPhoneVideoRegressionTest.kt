package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ScreenPhoneVideoRegressionTest {
    @Test
    fun recognizesExactDialerNumberFromVideo() {
        val target = ScreenPhoneLink.findBest("+55 11 98504-3222")
        assertNotNull(target)
        assertEquals("11985043222", target?.nationalDigits)
        assertEquals("https://wa.me/5511985043222", target?.url)
    }

    @Test
    fun recognizesOcrNumberSplitAcrossLines() {
        val target = ScreenPhoneLink.findBest("+55 11\n98504-3222")
        assertNotNull(target)
        assertEquals("11985043222", target?.nationalDigits)
    }
}
