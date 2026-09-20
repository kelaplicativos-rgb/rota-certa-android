package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CentralSegmentAvailability0594Test {
    @Test
    fun `segment labels expose remaining seats without opening the card`() {
        val two = CentralSegmentAvailability0552(
            from = "Santo André",
            to = "São Paulo",
            occupiedSeats = 2,
            availableSeats = 2,
            capacity = 4,
            overbookingSeats = 0,
            reliable = true,
        )
        val full = two.copy(
            from = "São Paulo",
            to = "Pouso Alegre",
            occupiedSeats = 4,
            availableSeats = 0,
        )
        val one = two.copy(
            from = "Pouso Alegre",
            to = "Três Corações",
            occupiedSeats = 3,
            availableSeats = 1,
        )
        val empty = two.copy(
            from = "Três Corações",
            to = "São Tomé das Letras",
            occupiedSeats = 0,
            availableSeats = 4,
        )

        assertEquals("●●○○", centralSegmentOccupancyDots0594(two))
        assertTrue(centralSegmentAvailabilityLabel0594(two).contains("2 vagas"))
        assertTrue(centralSegmentAvailabilityLabel0594(full).contains("LOTADO"))
        assertTrue(centralSegmentAvailabilityLabel0594(one).contains("1 vaga"))
        assertTrue(centralSegmentAvailabilityLabel0594(empty).contains("4 vagas"))
    }

    @Test
    fun `overbooking and unreliable capacity are explicit`() {
        val overbooked = CentralSegmentAvailability0552(
            from = "A",
            to = "B",
            occupiedSeats = 5,
            availableSeats = 0,
            capacity = 4,
            overbookingSeats = 1,
            reliable = true,
        )
        val unknown = overbooked.copy(reliable = false)

        assertEquals("●●●●", centralSegmentOccupancyDots0594(overbooked))
        assertTrue(centralSegmentAvailabilityLabel0594(overbooked).contains("5/4"))
        assertTrue(centralSegmentAvailabilityLabel0594(overbooked).contains("+1"))
        assertTrue(centralSegmentAvailabilityLabel0594(unknown).contains("não verificáveis"))
    }

    @Test
    fun `central renders all segment rows in the collapsed card shell`() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/CentralDoDia0552.kt").readText()
        val heading = source.indexOf("Text(\"Vagas por trecho\"")
        val segmentLoop = source.indexOf("item.segments.forEach", heading)
        val passengerExpansion = source.indexOf("if (passengersExpanded0591)", segmentLoop)

        assertTrue(heading >= 0)
        assertTrue(segmentLoop > heading)
        assertTrue(passengerExpansion > segmentLoop)
        assertTrue(source.contains("SeatAvailabilityEngine.segmentLoads(trip, bookings, nowMillis)"))
        assertTrue(source.contains("from = load.from.name.trim()"))
        assertTrue(source.contains("to = load.to.name.trim()"))
        assertTrue(source.contains("availableSeats = load.availableSeats.coerceAtLeast(0)"))
    }
}
