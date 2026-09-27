package br.com.mapeiaia.rotacerta.trips

import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SegmentPaxTimeline0671Test {
    private fun row(name: String, seats: Int, boarding: Int, dropoff: Int) =
        EnhancedPassengerCardRow(
            name = name,
            phone = "+5511999999999",
            seats = seats,
            boarding = "A",
            dropoff = "B",
            sources = setOf(BookingSource.BLABLACAR),
            boardingStopIndex = boarding,
            dropoffStopIndex = dropoff,
        )

    @Test
    fun paxIsDerivedFromTheRealBoardingAndDropoffWindow() {
        val rows = listOf(
            row("Ana", 1, 0, 2),
            row("Bruno", 2, 1, 3),
            row("Carla", 1, 2, 3),
        )
        assertEquals("Ana", passengerSegmentPaxLabel0671(rows, 0, 1))
        assertEquals("Ana • Bruno", passengerSegmentPaxLabel0671(rows, 1, 3))
        assertEquals("Bruno • Carla", passengerSegmentPaxLabel0671(rows, 2, 3))
        assertFalse(passengerActiveOnSegment0671(rows[0], 2))
    }

    @Test
    fun unresolvedRosterNeverInventsPaxLabelsOrNumbers() {
        val rows = listOf(row("Ana", 1, 0, 2))
        assertEquals("Ana", passengerSegmentPaxLabel0671(rows, 0, 3))
        assertEquals("", passengerSegmentPaxLabel0671(emptyList(), 0, 2))
        assertEquals("", passengerSegmentPaxLabel0671(emptyList(), 0, 0))
    }

    @Test
    fun segmentTimeUsesCanonicalFromStop() {
        val zone = ZoneId.of("America/Sao_Paulo")
        fun millis(hour: Int, minute: Int) =
            ZonedDateTime.of(2026, 9, 27, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
        val a = TripStop(id = "a", order = 0, name = "A")
        val b = TripStop(id = "b", order = 1, name = "B", plannedDepartureMillis = millis(11, 20))
        val c = TripStop(id = "c", order = 2, name = "C")
        val trip = Trip(
            id = "t",
            title = "A → C",
            departureAtMillis = millis(10, 30),
            stops = listOf(a, b, c),
        )
        val load = SegmentLoad(from = b, to = c, occupiedSeats = 1, availableSeats = 3)
        assertEquals(millis(11, 20), segmentStartTimeMillis0671(trip, load, millis(10, 30), millis(13, 0)))
    }

    @Test
    fun manualPhoneIsNormalizedAndUiKeepsEditBesideWhatsapp() {
        assertEquals("+5511999999999", passengerPhoneForStorage0671("(11) 99999-9999"))
        assertEquals("+5535999999999", passengerPhoneForStorage0671("+55 35 99999-9999"))
        assertEquals(null, passengerPhoneForStorage0671("123"))

        val passenger = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt").readText()
        val trips = File("src/main/java/br/com/mapeiaia/rotacerta/trips/OperationalAllTripsBrowserUi0563.kt").readText()
        val central = File("src/main/java/br/com/mapeiaia/rotacerta/trips/CentralDoDia0552.kt").readText()

        assertTrue(passenger.contains("PASSENGER_PHONE_EDITED_0671"))
        assertTrue(passenger.contains("phoneEditRow0671 = passenger"))
        assertTrue(passenger.contains("communicationShortcutRow0672 = passenger"))
        assertTrue(passenger.contains("onPassengerClick0672"))
        assertFalse(passenger.contains("append(\"PAX: \""))
        assertFalse(passenger.contains("PAX sem nome"))
        assertTrue(passenger.contains("privateMetadata0494?.passengerContact"))
        assertTrue(trips.contains("segmentLoads0671 = row.segmentLoads0602"))
        assertTrue(central.contains("segmentLoads0671 = item.segmentLoads"))
        assertFalse(trips.contains("val dots0602"))
        assertFalse(central.contains("val dots0595"))
    }
}
