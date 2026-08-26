package br.com.mapeiaia.rotacerta.trips

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgendaCalendarDayLines0294Test {
    @Test
    fun timelineShowsEveryDayOfRepresentedMonthAndKeepsEmptyDays() {
        val items = listOf(
            LocalDate.of(2026, 9, 4),
            LocalDate.of(2026, 9, 7),
        )
        val days = agendaCalendarDaysForItems(items) { it }

        assertEquals(30, days.size)
        assertEquals(LocalDate.of(2026, 9, 1), days.first().date)
        assertEquals(LocalDate.of(2026, 9, 30), days.last().date)
        assertTrue(days.first { it.date == LocalDate.of(2026, 9, 5) }.items.isEmpty())
        assertEquals(listOf(LocalDate.of(2026, 9, 7)), days.first { it.date == LocalDate.of(2026, 9, 7) }.items)
    }

    @Test
    fun timelineKeepsCompletelyEmptyMonthBetweenDistantCards() {
        val items = listOf(
            LocalDate.of(2026, 9, 30),
            LocalDate.of(2026, 11, 1),
        )
        val days = agendaCalendarDaysForItems(items) { it }

        assertEquals(LocalDate.of(2026, 9, 1), days.first().date)
        assertEquals(LocalDate.of(2026, 11, 30), days.last().date)
        assertTrue(days.first { it.date == LocalDate.of(2026, 10, 15) }.items.isEmpty())
    }

    @Test
    fun monthlyPublicSearchShowsWholeMonthEvenWithoutCards() {
        val days = agendaCalendarDaysForPeriod<LocalDate>("2026-09", emptyList()) { it }
        assertEquals(30, days.size)
        assertEquals(LocalDate.of(2026, 9, 1), days.first().date)
        assertEquals(LocalDate.of(2026, 9, 30), days.last().date)
        assertTrue(days.all { it.items.isEmpty() })
    }

    @Test
    fun exactPublicSearchDateShowsOnlyThatDay() {
        val days = agendaCalendarDaysForPeriod<LocalDate>("2026-09-04", emptyList()) { it }
        assertEquals(listOf(LocalDate.of(2026, 9, 4)), days.map { it.date })
    }

    @Test
    fun portugueseDayLineMatchesRequestedVisualExample() {
        assertEquals(
            "sexta-feira, 4 de setembro de 2026",
            agendaCalendarDayLabel(LocalDate.of(2026, 9, 4)),
        )
    }
}
