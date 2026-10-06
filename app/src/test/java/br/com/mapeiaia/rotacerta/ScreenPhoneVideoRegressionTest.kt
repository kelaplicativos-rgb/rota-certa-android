package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ScreenPhoneVideoRegressionTest {
    @Test
    fun recognizesExactDialerNumberFromVideo() {
        val target = ScreenPhoneLink.findBest("+55 11 99499-1598")
        assertNotNull(target)
        assertEquals("11994991598", target?.nationalDigits)
        assertEquals("https://wa.me/5511994991598", target?.url)
    }

    @Test
    fun recognizesOcrNumberSplitAcrossLines() {
        val target = ScreenPhoneLink.findBest("+55 11\n99499-1598")
        assertNotNull(target)
        assertEquals("11994991598", target?.nationalDigits)
    }
}
