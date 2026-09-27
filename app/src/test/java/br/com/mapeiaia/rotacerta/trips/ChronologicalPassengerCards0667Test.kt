package br.com.mapeiaia.rotacerta.trips

import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChronologicalPassengerCards0667Test {
    private val zone = ZoneId.of("America/Sao_Paulo")

    private fun millis(hour: Int, minute: Int): Long =
        ZonedDateTime.of(2026, 9, 27, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun canonicalStopsAreRenderedByRouteOrderWithAuthoritativeTimes() {
        val departure = millis(10, 30)
        val arrival = millis(14, 10)
        val trip = Trip(
            id = "trip-0667",
            title = "Santo André → Pouso Alegre",
            departureAtMillis = departure,
            stops = listOf(
                TripStop(order = 2, name = "Extrema", plannedArrivalMillis = millis(13, 0)),
                TripStop(order = 0, name = "Santo André"),
                TripStop(order = 3, name = "Pouso Alegre"),
                TripStop(order = 1, name = "São Paulo", plannedDepartureMillis = millis(11, 30)),
            ),
        )

        val stops = tripChronologicalStops0667(
            trip = trip,
            departureAtMillis = departure,
            arrivalAtMillis = arrival,
        )

        assertEquals(
            listOf("Santo André", "São Paulo", "Extrema", "Pouso Alegre"),
            stops.map { it.stop.name },
        )
        assertEquals(
            listOf(departure, millis(11, 30), millis(13, 0), arrival),
            stops.map { it.timeMillis },
        )
        assertEquals("10:30", tripChronologicalStopTimeLabel0667(stops[0].timeMillis, zone))
        assertEquals("11:30", tripChronologicalStopTimeLabel0667(stops[1].timeMillis, zone))
        assertEquals("13:00", tripChronologicalStopTimeLabel0667(stops[2].timeMillis, zone))
        assertEquals("14:10", tripChronologicalStopTimeLabel0667(stops[3].timeMillis, zone))
    }

    @Test
    fun passengerBlocksStayAttachedToBoardingStopAndKeepBothExactPins() {
        val passengerSource = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt",
        ).readText()
        val tripsSource = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/OperationalAllTripsBrowserUi0563.kt",
        ).readText()
        val centralSource = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/CentralDoDia0552.kt",
        ).readText()

        assertTrue(passengerSource.contains("passenger.boardingStopIndex"))
        assertTrue(passengerSource.contains("ChronologicalTripStopLine0667("))
        assertTrue(passengerSource.contains("{ Text(\"📍\", maxLines = 1) }"))
        assertTrue(passengerSource.contains("{ Text(\"🏁\", maxLines = 1) }"))
        assertTrue(passengerSource.contains("passengerOperationalAddressLabel0656(passenger, boarding = true)"))
        assertTrue(passengerSource.contains("passengerOperationalAddressLabel0656(passenger, boarding = false)"))
        assertTrue(tripsSource.contains("segmentLoads0671 = row.segmentLoads0602"))
        assertTrue(centralSource.contains("segmentLoads0671 = item.segmentLoads"))
        assertFalse(tripsSource.contains("embedChronologicalStops0667 = true"))
        assertFalse(centralSource.contains("embedChronologicalStops0667 = true"))
        assertFalse(tripsSource.contains("Text(\"Atalhos\", maxLines = 1)"))
        assertFalse(centralSource.contains("Text(\"Atalhos\", maxLines = 1)"))
    }
}
