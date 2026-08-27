package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PublicBooking0296Test {
    @Test
    fun publicBookingIsOptInByDefault() {
        val trip = Trip(
            title = "A → B",
            departureAtMillis = 2_000L,
            capacity = 4,
            stops = listOf(TripStop(order = 0, name = "A"), TripStop(order = 1, name = "B")),
        )
        assertFalse(trip.publicBookingEnabled)
    }

    @Test
    fun agendaEnsureResponseCarriesValidatedPublicToken() {
        val response = DriverAgendaEnsureResponse(
            displayName = "Viagem Certa",
            username = "viagem-certa",
            publicAgendaToken = "abcdefghijklmnop",
            publicAgendaUrl = "https://example.test/?motorista=viagem-certa&agenda=abcdefghijklmnop",
            calendarUrl = "https://example.test/calendar/viagem-certa/abcdefghijklmnop.ics",
            repaired = true,
        )
        assertEquals("viagem-certa", response.username)
        assertEquals("abcdefghijklmnop", response.publicAgendaToken)
        assertEquals(true, response.repaired)
    }

    @Test
    fun remotePublicBookingMapsToSameLocalBookingDomain() {
        val remote = RemoteBooking(
            id = "public_booking_1",
            passengerName = "Joao",
            passengerContact = "+5511999999999",
            boardingStopId = "a",
            dropoffStopId = "b",
            seats = 2,
            source = BookingSource.ROTA_CERTA,
            status = BookingStatus.CONFIRMED.name,
        )
        val local = remote.toLocalBooking("local-trip")
        assertEquals("local-trip", local.tripId)
        assertEquals(BookingSource.ROTA_CERTA, local.source)
        assertEquals(2, local.seats)
        assertEquals(BookingStatus.CONFIRMED, local.status)
    }
}
