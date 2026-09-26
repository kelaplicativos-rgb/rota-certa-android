package br.com.mapeiaia.rotacerta.trips

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class BlaBlaDateYear0287Test {
    private val today = LocalDate.of(2026, 8, 26)

    @Test
    fun abbreviatedJuneWithDotKeepsExplicit2027Year() {
        assertEquals(
            LocalDate.of(2027, 6, 28),
            BlaBlaDomNormalizer.parseDate("Seg. 28 Jun. 2027", today),
        )
        assertEquals(
            LocalDate.of(2027, 6, 30),
            BlaBlaDomNormalizer.parseDate("Qua. 30 Jun. 2027", today),
        )
    }

    @Test
    fun abbreviatedMonthWithoutYearStillUsesExistingInference() {
        assertEquals(
            LocalDate.of(2026, 10, 2),
            BlaBlaDomNormalizer.parseDate("Sex. 02 Out.", today),
        )
    }

    @Test
    fun existingMonthFormatsRemainAccepted() {
        assertEquals(
            LocalDate.of(2027, 6, 28),
            BlaBlaDomNormalizer.parseDate("28 Jun 2027", today),
        )
        assertEquals(
            LocalDate.of(2027, 6, 28),
            BlaBlaDomNormalizer.parseDate("28 de Junho de 2027", today),
        )
    }
}
