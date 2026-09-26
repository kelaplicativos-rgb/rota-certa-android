package br.com.mapeiaia.rotacerta.trips

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OperationalBrowserVideoStyle0568Test {
    private val today = LocalDate.of(2026, 9, 16)

    @Test
    fun dateLabelsMatchReferenceVideoForRelativeCurrentAndNextYearDates() {
        assertEquals("Hoje", operationalDateLabel0568(today, today))
        assertEquals("Ontem", operationalDateLabel0568(today.minusDays(1), today))
        assertEquals("Amanhã", operationalDateLabel0568(today.plusDays(1), today))
        assertEquals("Sex. 18 Set.", operationalDateLabel0568(LocalDate.of(2026, 9, 18), today))
        assertEquals("Sáb. 07 Ago. 2027", operationalDateLabel0568(LocalDate.of(2027, 8, 7), today))
        assertEquals("Qui. 23 Set. 2027", operationalDateLabel0568(LocalDate.of(2027, 9, 23), today))
    }

    @Test
    fun durationUsesCompactBlaBlaCarStyle() {
        val start = 1_000_000L
        assertEquals("5h40", operationalDurationLabel0568(start, start + (5 * 60 + 40) * 60_000L))
        assertEquals("2h", operationalDurationLabel0568(start, start + 2 * 60 * 60_000L))
        assertEquals("45min", operationalDurationLabel0568(start, start + 45 * 60_000L))
        assertNull(operationalDurationLabel0568(start, null))
        assertNull(operationalDurationLabel0568(start, start - 1L))
    }
}
