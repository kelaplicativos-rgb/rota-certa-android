package br.com.mapeiaia.rotacerta.trips

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineAgendaCardParity0549Test {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun timelineCardUsesAgendaSurfaceAndSuppressesLegacyDerivedDiagnostics() {
        val ui = source("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt")
        val start = ui.indexOf("private fun TimelineEntryCard(")
        val end = ui.indexOf("if (showSeatDetails)", start)
        assertTrue(start >= 0)
        assertTrue(end > start)
        val card = ui.substring(start, end)

        assertTrue(card.contains("Vagas por trecho"))
        assertTrue(card.contains("Recolher trajeto"))
        assertTrue(card.contains("TripBlaBlaTripActionRow("))
        assertTrue(card.contains("showTripActions0549 = false"))
        assertTrue(card.contains("RoundedCornerShape(26.dp)"))
        assertTrue(card.contains("minimumAvailable0549"))

        assertFalse(card.contains("❌ URGENTE:"))
        assertFalse(card.contains("BlaBlaCar ${'$'}{allocation.blablaQuota"))
        assertFalse(card.contains("sourceLine = entry.sourcePassengerSeats"))
        assertFalse(card.contains("Passageiros confirmados:"))
        assertFalse(card.contains("Identidade externa incompleta;"))
        assertFalse(card.contains("Toque para fechar"))
    }

    @Test
    fun driverShortcutsHaveSingleTopOwner() {
        val passenger = source("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt")
        assertTrue(passenger.contains("showTripActions0549: Boolean = true"))
        assertTrue(passenger.contains("leadingActions0549?.invoke()"))
        assertTrue(passenger.contains("trailingActions0549?.invoke()"))
        assertTrue(passenger.contains("internal fun TripBlaBlaTripActionRow("))
    }
}
