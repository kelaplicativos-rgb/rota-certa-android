package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReservationManagement0631Test {
    private val a = TripStop(id = "a", order = 0, name = "Santo André")
    private val b = TripStop(id = "b", order = 1, name = "São Paulo")
    private val c = TripStop(id = "c", order = 2, name = "Extrema")

    private val trip = Trip(
        id = "trip-1",
        title = "Santo André → Extrema",
        departureAtMillis = 1_800_000_000_000L,
        capacity = 4,
        status = TripStatus.PUBLISHED,
        stops = listOf(a, b, c),
        publishedSeats = 0,
        rotaCertaSeatAllocation = 4,
    )

    private fun booking(
        id: String,
        status: BookingStatus,
        from: String = "a",
        to: String = "c",
        seats: Int = 1,
        name: String = id,
        source: BookingSource = BookingSource.ROTA_CERTA,
        operational: PassengerOperationalStatus = PassengerOperationalStatus.CONFIRMED,
    ) = Booking(
        id = id,
        tripId = trip.id,
        passengerName = name,
        passengerContact = "11999999999",
        boardingStopId = from,
        dropoffStopId = to,
        seats = seats,
        status = status,
        operationalStatus = operational,
        source = source,
        occupancyGroupId = "group-$id",
    )

    @Test
    fun filtersSeparatePendingActiveAndHistoryWithoutCreatingAnotherReservationModel() {
        val pending = booking("pending", BookingStatus.REQUESTED, name = "Ana")
        val active = booking("active", BookingStatus.CONFIRMED, name = "Bruna")
        val cancelled = booking(
            "cancelled",
            BookingStatus.CANCELLED,
            name = "Carla",
            operational = PassengerOperationalStatus.CANCELLED,
        )
        val all = listOf(pending, active, cancelled)

        assertEquals(
            listOf("pending"),
            managedReservations0631(listOf(trip), all, ReservationFilter0631.PENDING).map { it.booking.id },
        )
        assertEquals(
            listOf("active"),
            managedReservations0631(listOf(trip), all, ReservationFilter0631.ACTIVE).map { it.booking.id },
        )
        assertEquals(
            listOf("cancelled"),
            managedReservations0631(listOf(trip), all, ReservationFilter0631.HISTORY).map { it.booking.id },
        )
        assertEquals(
            listOf("active"),
            managedReservations0631(
                listOf(trip),
                all,
                ReservationFilter0631.ACTIVE,
                queryRaw = "bruna",
            ).map { it.booking.id },
        )
    }

    @Test
    fun focusedBookingIsResolvedInsideItsCorrectState() {
        val active = booking("target", BookingStatus.CONFIRMED)
        val other = booking("other", BookingStatus.CONFIRMED)

        val rows = managedReservations0631(
            trips = listOf(trip),
            bookings = listOf(active, other),
            filter = ReservationFilter0631.ACTIVE,
            focusedBookingId = "target",
        )

        assertEquals(1, rows.size)
        assertEquals("target", rows.single().booking.id)
    }

    @Test
    fun editingRecalculatesCapacityWithoutCountingTheSameReservationTwice() {
        val current = booking("current", BookingStatus.CONFIRMED, from = "a", to = "c", seats = 2)
        val other = booking("other", BookingStatus.CONFIRMED, from = "a", to = "b", seats = 2)

        val moved = reservationEditedBooking0631(
            trip = trip,
            allBookings = listOf(current, other),
            current = current,
            boardingStopId = "b",
            dropoffStopId = "c",
            seats = 2,
            fareMinorUnits = 5800L,
            fareCurrencyCode = "BRL",
        )

        assertEquals("b", moved.boardingStopId)
        assertEquals("c", moved.dropoffStopId)
        assertEquals(2, moved.seats)
        assertEquals(5800L, moved.fareMinorUnits)
        assertTrue(moved.localMetadataTouched)
    }

    @Test
    fun editingFailsClosedWhenNewSegmentWouldOverbook() {
        val current = booking("current", BookingStatus.CONFIRMED, from = "b", to = "c", seats = 1)
        val other = booking("other", BookingStatus.CONFIRMED, from = "a", to = "b", seats = 2)

        assertFailsWith<IllegalArgumentException> {
            reservationEditedBooking0631(
                trip = trip,
                allBookings = listOf(current, other),
                current = current,
                boardingStopId = "a",
                dropoffStopId = "b",
                seats = 3,
                fareMinorUnits = null,
                fareCurrencyCode = "BRL",
            )
        }
    }

    @Test
    fun uiAndNavigationExposeReservationManagementAsARealRoot() {
        val ui = File("src/main/java/br/com/mapeiaia/rotacerta/trips/ReservationManagementUi0631.kt").readText()
        val activity = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()
        val header = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaHeaderNavigation0396.kt").readText()
        val home = File("src/main/java/br/com/mapeiaia/rotacerta/trips/ReservationRequestsHomeAlert0356.kt").readText()

        assertTrue(header.contains("RESERVATIONS(\"Reservas\")"))
        assertTrue(activity.contains("openReservationRequests || initialBookingId != null || initialPendingOnly -> TripScreen.RESERVATIONS"))
        assertTrue(activity.contains("TripScreen.RESERVATIONS -> ReservationManagementScreen0631("))
        assertTrue(home.contains("ACTION_OPEN_RESERVATION_REQUESTS"))
        assertTrue(ui.contains("mutationSource = \"RESERVATION_MANAGEMENT_0631\""))
        assertTrue(ui.contains("Text(\"WhatsApp\")"))
        assertTrue(ui.contains("Text(\"Editar\")"))
        assertTrue(ui.contains("Text(\"Cancelar\")"))
        assertTrue(ui.contains("Text(\"+ Passageiro\")"))
    }
}
