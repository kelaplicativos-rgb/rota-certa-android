package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CentralDriverSegmentGlance0601Test {
    private fun centralSource(): String {
        val candidates = listOf(
            File("src/main/java/br/com/mapeiaia/rotacerta/trips/CentralDoDia0552.kt"),
            File("app/src/main/java/br/com/mapeiaia/rotacerta/trips/CentralDoDia0552.kt"),
        )
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("CentralDoDia0552.kt not found")
    }

    @Test
    fun segmentAvailabilityIsAReadOnlyDriverGlanceWithoutPassengerReservationCtas() {
        val source = centralSource()
        val start = source.indexOf("if (item.segmentLoads.isNotEmpty())")
        val end = source.indexOf(
            "Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp))",
            start,
        )
        assertTrue(start >= 0 && end > start, "Central segment section not found")
        val section = source.substring(start, end)
        assertTrue(section.contains("\"Vagas por trecho\""))

        assertTrue(section.contains("item.segmentLoads.forEach"))
        assertTrue(section.contains("\${load0595.from.name} → \${load0595.to.name}"))
        assertTrue(section.contains("load0595.occupiedSeats"))
        assertTrue(section.contains("load0595.passengerSeats"))
        assertTrue(section.contains("load0595.blockedSeats"))
        assertTrue(section.contains("load0595.availableSeats"))
        assertTrue(section.contains("load0595.overbookingSeats"))
        assertTrue(section.contains("\"LOTADO\""))
        assertTrue(section.contains("Text(\"👥 \$occupancy0595\""))
        assertTrue(section.contains("MaterialTheme.typography.titleSmall"))

        assertFalse(section.contains("Reserve Já"))
        assertFalse(section.contains("Indisponível"))
        assertFalse(section.contains("Button("))
        assertFalse(section.contains("TextButton("))
        assertFalse(section.contains("onOpenPublic"))
        assertFalse(section.contains("onReserve"))
    }

    @Test
    fun driverGlanceAllowsRouteWrappingAndKeepsVacancyAtTheRightSide() {
        val source = centralSource()
        val start = source.indexOf("// 0.1.601 — leitura operacional do motorista.")
        val end = source.indexOf(
            "Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp))",
            start,
        )
        assertTrue(start >= 0 && end > start)
        val section = source.substring(start, end)

        assertTrue(section.contains("Column("))
        assertTrue(section.contains("maxLines = 2"))
        assertTrue(section.contains("horizontalArrangement = Arrangement.spacedBy(8.dp)"))
        assertTrue(section.contains("style = MaterialTheme.typography.titleSmall"))
        assertTrue(section.contains("nunca oferece ação de reserva"))
    }
}
