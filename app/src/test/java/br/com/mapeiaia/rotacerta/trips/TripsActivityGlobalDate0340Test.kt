package br.com.mapeiaia.rotacerta.trips

import br.com.mapeiaia.rotacerta.date.RotaCertaDateSelection
import br.com.mapeiaia.rotacerta.date.RotaCertaDateSelectionMode
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TripsActivityGlobalDate0340Test {
    @Test
    fun tripEditorCombinesGlobalSelectedDateWithSeparateTime() {
        val selection = RotaCertaDateSelection(
            mode = RotaCertaDateSelectionMode.SINGLE,
            dates = listOf(LocalDate.of(2026, 9, 5)),
        )

        val millis = tripEditorDepartureMillis(
            selection = selection,
            timeText = "11:30",
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(
            LocalDate.of(2026, 9, 5).atTime(11, 30).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli(),
            millis,
        )
    }

    @Test
    fun tripEditorRejectsMissingDateOrInvalidTime() {
        assertNull(
            tripEditorDepartureMillis(
                selection = RotaCertaDateSelection(mode = RotaCertaDateSelectionMode.SINGLE),
                timeText = "11:30",
                zoneId = ZoneId.of("UTC"),
            ),
        )
        assertNull(
            tripEditorDepartureMillis(
                selection = RotaCertaDateSelection(
                    mode = RotaCertaDateSelectionMode.SINGLE,
                    dates = listOf(LocalDate.of(2026, 9, 5)),
                ),
                timeText = "25:99",
                zoneId = ZoneId.of("UTC"),
            ),
        )
    }
}
