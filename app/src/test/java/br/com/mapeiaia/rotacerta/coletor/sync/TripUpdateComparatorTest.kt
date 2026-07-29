package br.com.mapeiaia.rotacerta.coletor.sync

import br.com.mapeiaia.rotacerta.coletor.model.Booking
import br.com.mapeiaia.rotacerta.coletor.model.BookingStatus
import br.com.mapeiaia.rotacerta.coletor.model.DriverAccount
import br.com.mapeiaia.rotacerta.coletor.model.Passenger
import br.com.mapeiaia.rotacerta.coletor.model.Trip
import br.com.mapeiaia.rotacerta.coletor.model.TripStop
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TripUpdateComparatorTest {
    private val origin = TripStop("Origem", "Rua A, 1")
    private val destination = TripStop("Destino", "Rua B, 2")

    @Test
    fun `detecta nova reserva sem duplicar as existentes`() {
        val saved = trip(bookings = listOf(booking("r1", "Ana")))
        val remote = trip(bookings = listOf(booking("r1", "Ana"), booking("r2", "Bruno")))

        val preview = TripUpdateComparator.compare(saved, remote)

        assertTrue(preview.hasChanges)
        assertEquals(1, preview.changes.filterIsInstance<TripChange.NewBooking>().size)
    }

    @Test
    fun `sinaliza conflito quando campo manualmente bloqueado mudou`() {
        val previous = booking("r1", "Ana").copy(manuallyLockedFields = setOf("boarding"))
        val current = previous.copy(boarding = TripStop("Novo", "Rua C, 3"))

        val change = TripUpdateComparator.compare(
            saved = trip(bookings = listOf(previous)),
            remote = trip(bookings = listOf(current)),
        ).changes.filterIsInstance<TripChange.BookingChanged>().single()

        assertEquals(setOf("boarding"), change.protectedConflicts)
    }

    @Test
    fun `detecta cancelamento de reserva`() {
        val previous = booking("r1", "Ana")
        val current = previous.copy(bookingStatus = BookingStatus.CANCELLED)

        val changes = TripUpdateComparator.compare(
            saved = trip(bookings = listOf(previous)),
            remote = trip(bookings = listOf(current)),
        ).changes

        assertEquals(1, changes.filterIsInstance<TripChange.CancelledBooking>().size)
    }

    private fun trip(bookings: List<Booking>) = Trip(
        externalTripId = "trip-1",
        account = DriverAccount.EZEQUIEL_S,
        date = LocalDate.of(2026, 7, 31),
        departureAt = LocalDateTime.of(2026, 7, 31, 19, 0),
        origin = origin,
        finalDestination = destination,
        offeredSeats = 4,
        bookings = bookings,
    )

    private fun booking(id: String, name: String) = Booking(
        externalBookingId = id,
        passenger = Passenger(name = name),
        boarding = origin,
        dropOff = destination,
        grossAmount = BigDecimal("70.00"),
    )
}
