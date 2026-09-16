package br.com.mapeiaia.rotacerta.trips

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationalTripAutoArchive0566Test {
    @Test
    fun `past departure is archived while exact-now and future remain active`() {
        val result = operationalArchiveSelection0566(
            items = listOf(100L, 200L, 300L),
            nowMillis = 200L,
            departureAtMillis = { it },
        )

        assertEquals(listOf(200L, 300L), result.active)
        assertEquals(listOf(100L), result.archived)
    }

    @Test
    fun `archived trips are ordered from most recent to oldest`() {
        val result = operationalArchiveSelection0566(
            items = listOf(100L, 300L, 200L),
            nowMillis = 400L,
            departureAtMillis = { it },
        )

        assertEquals(emptyList<Long>(), result.active)
        assertEquals(listOf(300L, 200L, 100L), result.archived)
    }

    @Test
    fun `operational screen advances its clock and exposes archived section`() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/OperationalAllTripsBrowserUi0563.kt",
        ).readText()

        assertTrue(source.contains("operationalArchiveSelection0566"))
        assertTrue(source.contains("System.currentTimeMillis()"))
        assertTrue(source.contains("delay(waitMillis)"))
        assertTrue(source.contains("Viagens arquivadas"))
        assertTrue(source.contains("Nenhuma viagem atual ou futura."))
    }
}
