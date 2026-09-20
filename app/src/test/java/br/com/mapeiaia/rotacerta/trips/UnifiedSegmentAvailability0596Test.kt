package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnifiedSegmentAvailability0596Test {
    private fun source(path: String): String {
        val candidates = listOf(
            File("src/main/java/$path"),
            File("app/src/main/java/$path"),
        )
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Source file not found for contract test: $path")
    }

    @Test
    fun timelinePermanentRowsExposeCanonicalOccupancyVacanciesAndAnomalies() {
        val source = source("br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt")
        val start = source.indexOf("Text(\"Vagas por trecho\"")
        val end = source.indexOf("if (expanded)", start)
        assertTrue(start >= 0 && end > start, "Permanent Timeline segment section not found")
        val section = source.substring(start, end)

        assertTrue(section.contains("publicLoads0549.forEach"))
        assertTrue(section.contains("load0580.occupiedSeats"))
        assertTrue(section.contains("load0580.passengerSeats"))
        assertTrue(section.contains("load0580.blockedSeats"))
        assertTrue(section.contains("load0580.availableSeats"))
        assertTrue(section.contains("load0580.overbookingSeats"))
        assertTrue(section.contains("\"LOTADO\""))
        assertTrue(section.contains("\"👥 \$occupancy0580\""))
        assertFalse(section.contains("passengerName"))
        assertFalse(section.contains("bookingId"))
    }

    @Test
    fun centralAndTimelineKeepTheSameCanonicalSegmentSemantics() {
        val central = source("br/com/mapeiaia/rotacerta/trips/CentralDoDia0552.kt")
        val timeline = source("br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt")

        for (token in listOf("occupiedSeats", "passengerSeats", "blockedSeats", "availableSeats", "overbookingSeats")) {
            assertTrue(central.contains("load0595.$token"), "Central missing $token")
            assertTrue(timeline.contains("load0580.$token"), "Timeline missing $token")
        }
        assertTrue(central.contains("SeatAvailabilityEngine.segmentLoads"))
        assertTrue(timeline.contains("canonicalTimelineSegmentLoads0494"))
    }
}
