package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForensicHotPath0193ContractTest {
    @Test
    fun `caminho comum do monitor nao instancia regex nem faz polling`() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/ForensicIncidentMonitor0193.kt").readText()
        assertFalse(source.contains("Regex("))
        assertFalse(source.contains("scheduleAtFixedRate"))
        assertFalse(source.contains("Timer("))
        assertFalse(source.contains("while (true)"))
        assertTrue(source.contains("details.indexOf(needle, searchFrom)"))
        assertTrue(source.contains("SystemClock.elapsedRealtimeNanos()"))
    }

    @Test
    fun `parser otimizado preserva delimitadores e escopo por pacote`() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/ForensicIncidentMonitor0193.kt").readText()
        assertTrue(source.contains("details[start - 1] == ';'"))
        assertTrue(source.contains("details[start - 1] == ' '"))
        assertTrue(source.contains("details[start - 1] == ','"))
        assertTrue(source.contains("packageName?.hashCode() ?: 0"))
    }
}
