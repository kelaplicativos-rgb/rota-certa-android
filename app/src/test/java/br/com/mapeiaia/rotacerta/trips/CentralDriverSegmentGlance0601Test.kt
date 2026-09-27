package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CentralDriverSegmentGlance0601Test {
    private fun source(path: String): String {
        val candidates = listOf(File(path), File("app/$path"))
        return candidates.firstOrNull(File::isFile)?.readText() ?: error("$path not found")
    }

    @Test
    fun centralUsesTheSameSingleSegmentPaxRendererAsTrips() {
        val central = source("src/main/java/br/com/mapeiaia/rotacerta/trips/CentralDoDia0552.kt")
        val passenger = source("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt")

        assertTrue(central.contains("segmentLoads0671 = item.segmentLoads"))
        assertTrue(central.contains("bloco único de vagas/PAX"))
        assertFalse(central.contains("val dots0595"))
        assertFalse(central.contains("Text(\"👥 $occupancy0595\""))
        assertTrue(passenger.contains("SegmentVacancyLine0671("))
        assertTrue(passenger.contains("passengerSegmentPaxLabel0671("))
        assertTrue(passenger.contains("load0671.availableSeats"))
        assertTrue(passenger.contains("load0671.overbookingSeats"))
        assertTrue(passenger.contains("\"LOTADO\""))
        assertFalse(passenger.contains("Reserve Já"))
    }
}
