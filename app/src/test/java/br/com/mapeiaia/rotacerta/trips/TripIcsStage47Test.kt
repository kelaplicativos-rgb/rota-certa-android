package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripIcsStage47Test {
    @Test
    fun calendarPayloadIsAValidMinimalVcalendarAndDoesNotLeakOtherPassengers() {
        val trip = Trip(
            id = "trip-1",
            title = "Santo André → Três Corações",
            departureAtMillis = 1_800_000_000_000L,
            capacity = 3,
            status = TripStatus.PUBLISHED,
            stops = listOf(
                TripStop(id = "a", order = 0, name = "Santo André", address = "Rodoviária", plannedDepartureMillis = 1_800_000_000_000L),
                TripStop(id = "b", order = 1, name = "Três Corações", address = "Centro", plannedArrivalMillis = 1_800_010_800_000L),
            ),
            publicUrl = "https://example.invalid/t/abc",
        )
        val booking = Booking(
            id = "res-1",
            tripId = trip.id,
            passengerName = "Passageiro A",
            passengerContact = "privado@example.invalid",
            boardingStopId = "a",
            dropoffStopId = "b",
            status = BookingStatus.CONFIRMED,
        )
        val ics = TripIcs.build(trip, booking)
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(ics.contains("BEGIN:VEVENT\r\n"))
        assertTrue(ics.contains("UID:res-1@rotacerta"))
        assertTrue(ics.contains("URL:https://example.invalid/t/abc"))
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
        assertFalse(ics.contains("privado@example.invalid"))
        assertFalse(ics.contains("Passageiro A"))
    }

    @Test
    fun textEscapingProtectsIcalendarSeparators() {
        val escaped = TripIcs.escape("A; B, C\\D\nE")
        assertTrue(escaped.contains("\\;"))
        assertTrue(escaped.contains("\\,"))
        assertTrue(escaped.contains("\\\\"))
        assertTrue(escaped.contains("\\n"))
    }
}
