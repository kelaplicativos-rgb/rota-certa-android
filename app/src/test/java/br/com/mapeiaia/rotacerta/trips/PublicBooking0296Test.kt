package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
    @Test
    fun remoteRefreshPreservesLocalMetadataMarkersForIdempotentCompare() {
        val existing = Booking(
            id = "booking-1",
            tripId = "local-trip",
            passengerName = "Passenger",
            passengerContact = "11999999999",
            boardingStopId = "a",
            dropoffStopId = "b",
            seats = 1,
            status = BookingStatus.CONFIRMED,
            passengerId = "passenger-strong-id",
            fareMinorUnits = 1234L,
            fareCurrencyCode = "BRL",
            boardingAddress = "Pickup",
            dropoffAddress = "Dropoff",
            cancellationToken = "local-token",
            localMetadataTouched = true,
            updatedAtMillis = 123456L,
        )
        val remote = RemoteBooking(
            id = existing.id,
            passengerName = existing.passengerName,
            passengerContact = existing.passengerContact,
            boardingStopId = existing.boardingStopId,
            dropoffStopId = existing.dropoffStopId,
            seats = existing.seats,
            source = existing.source,
            status = existing.status.name,
            passengerId = existing.passengerId,
            updatedAtMillis = existing.updatedAtMillis,
        )

        val mapped = remote.toLocalBooking(existing.tripId, existing)

        assertEquals(existing.passengerId, mapped.passengerId)
        assertEquals(existing.fareMinorUnits, mapped.fareMinorUnits)
        assertEquals(existing.fareCurrencyCode, mapped.fareCurrencyCode)
        assertEquals(existing.boardingAddress, mapped.boardingAddress)
        assertEquals(existing.dropoffAddress, mapped.dropoffAddress)
        assertEquals(existing.cancellationToken, mapped.cancellationToken)
        assertTrue(mapped.localMetadataTouched)
    }

}
