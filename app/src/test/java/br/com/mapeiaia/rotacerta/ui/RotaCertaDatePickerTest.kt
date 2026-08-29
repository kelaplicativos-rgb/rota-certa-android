package br.com.mapeiaia.rotacerta.ui

import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RotaCertaDatePickerTest {
    @Test
    fun singleDateKeepsOnlyTappedDay() {
        val initial = RotaCertaDateSelection(mode = RotaCertaDateSelectionMode.SINGLE)
        val selected = rotaCertaApplyDateTap(initial, LocalDate.of(2026, 9, 5))
        assertEquals(listOf(LocalDate.of(2026, 9, 5)), selected.normalizedDates)
        assertEquals("5 de setembro", rotaCertaDateSelectionSummary(selected))
    }

    @Test
    fun multipleDatesCanBeNonConsecutiveAndTappedAgainToDeselect() {
        var selection = RotaCertaDateSelection(mode = RotaCertaDateSelectionMode.MULTIPLE)
        listOf(5, 7, 12).forEach { day ->
            selection = rotaCertaApplyDateTap(selection, LocalDate.of(2026, 9, day))
        }
        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 9, 7),
                LocalDate.of(2026, 9, 12),
            ),
            selection.normalizedDates,
        )
        assertEquals("5, 7 e 12 de setembro", rotaCertaDateSelectionSummary(selection))

        selection = rotaCertaApplyDateTap(selection, LocalDate.of(2026, 9, 7))
        assertFalse(LocalDate.of(2026, 9, 7) in selection.normalizedDates)
        assertEquals(2, selection.normalizedDates.size)
    }

    @Test
    fun rangeSelectsEveryDayBetweenStartAndEnd() {
        var selection = RotaCertaDateSelection(mode = RotaCertaDateSelectionMode.RANGE)
        selection = rotaCertaApplyDateTap(selection, LocalDate.of(2026, 9, 5))
        selection = rotaCertaApplyDateTap(selection, LocalDate.of(2026, 9, 12))
        assertEquals(8, selection.normalizedDates.size)
        assertEquals(LocalDate.of(2026, 9, 5), selection.normalizedDates.first())
        assertEquals(LocalDate.of(2026, 9, 12), selection.normalizedDates.last())
        assertEquals("5 a 12 de setembro", rotaCertaDateSelectionSummary(selection))
    }

    @Test
    fun monthModeSelectsWholeMonth() {
        val selection = rotaCertaSelectMonth(YearMonth.of(2026, 9))
        assertEquals(30, selection.normalizedDates.size)
        assertEquals(LocalDate.of(2026, 9, 1), selection.normalizedDates.first())
        assertEquals(LocalDate.of(2026, 9, 30), selection.normalizedDates.last())
        assertTrue(selection.mode == RotaCertaDateSelectionMode.MONTH)
    }
}
