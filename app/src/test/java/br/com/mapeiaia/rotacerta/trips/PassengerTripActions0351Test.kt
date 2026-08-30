package br.com.mapeiaia.rotacerta.trips

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PassengerTripActions0351Test {
    @Test
    fun mainActionsAreThreeIndependentResponsibilities() {
        val timeline = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()
        assertTrue(timeline.contains("ResponsiveTripAction(\"👥 Passageiros\", onClick = onOpenPassengers)"))
        assertTrue(timeline.contains("ResponsiveTripAction(\"➕ Adicionar a uma viagem\")"))
        assertTrue(timeline.contains("ResponsiveTripAction(\"🛣️ Nova viagem\", onClick = onCreateTrip)"))
        assertFalse(timeline.contains("showPassengerMenu"))
        assertFalse(timeline.contains("👥 Ver / gerenciar passageiros"))
    }

    @Test
    fun addFlowIsPassengerThenTripThenBookingData() {
        val flow = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripGlobalPassengerFlow0256.kt").readText()
        val passenger = flow.indexOf("1. Selecionar passageiro")
        val trip = flow.indexOf("2. Selecionar viagem")
        val booking = flow.indexOf("3. Dados da reserva")
        assertTrue(passenger >= 0)
        assertTrue(trip > passenger)
        assertTrue(booking > trip)
        assertTrue(flow.contains("Cadastrar novo passageiro"))
        assertTrue(flow.contains("Cadastrar e continuar"))
        assertTrue(flow.contains("passengerStore.exactContactMatches(newWhatsapp)"))
        assertTrue(flow.contains("profile.agendaAccessContact()"))
        assertTrue(flow.contains("agendaAccessWhatsapp = newWhatsapp.trim()"))
        assertTrue(flow.contains("entry.minimumAvailableSeats"))
        assertTrue(flow.contains("entry.maximumAvailableSeats"))
        assertFalse(flow.contains("Viagem BlaBlaCar: escolha os pontos conhecidos da rota. Viagem particular: use Nova viagem"))
    }

    @Test
    fun newTripResumesWithTheSameSelectedPassenger() {
        val activity = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()
        assertTrue(activity.contains("pendingCreateForPassengerId = passengerId"))
        assertTrue(activity.contains("addPassengerResumePassengerId = resumePassengerId"))
        assertTrue(activity.contains("addPassengerResumeTripId = trip.id"))
        assertTrue(activity.contains("addPassengerResumeToken++"))
    }

    @Test
    fun selectedCanonicalPassengerIdIsPreservedInRealBooking() {
        val trip = Trip(
            id = "trip-0351",
            title = "A → B",
            departureAtMillis = 1_800_000_000_000L,
            capacity = 2,
            status = TripStatus.PUBLISHED,
            stops = listOf(
                TripStop(id = "a", order = 0, name = "A"),
                TripStop(id = "b", order = 1, name = "B"),
            ),
        )
        val plan = QuickPassengerEngine.build(
            trip = trip,
            existingBookings = emptyList(),
            request = QuickPassengerRequest(
                passengerName = "Pessoa",
                passengerContact = "11999999999",
                passengerId = "passenger-canonical",
                boardingStopId = "a",
                dropoffStopId = "b",
                seats = 1,
                fareMinorUnits = 1000L,
                fareCurrencyCode = "BRL",
                source = BookingSource.PRIVATE,
            ),
            idFactory = { "booking-0351" },
        )
        assertEquals("passenger-canonical", plan.passenger.passengerId)
        assertEquals(CapacityClaimType.PASSENGER, plan.passenger.capacityClaimType)
    }

    @Test
    fun fullSegmentStillRejectsAnExtraPassenger() {
        val trip = Trip(
            id = "trip-full-0351",
            title = "A → B",
            departureAtMillis = 1_800_000_000_000L,
            capacity = 1,
            status = TripStatus.FULL,
            stops = listOf(
                TripStop(id = "a", order = 0, name = "A"),
                TripStop(id = "b", order = 1, name = "B"),
            ),
        )
        val existing = Booking(
            id = "occupied",
            tripId = trip.id,
            passengerName = "Já está no carro",
            boardingStopId = "a",
            dropoffStopId = "b",
            seats = 1,
            status = BookingStatus.CONFIRMED,
            source = BookingSource.PRIVATE,
        )
        assertThrows(IllegalArgumentException::class.java) {
            QuickPassengerEngine.build(
                trip,
                listOf(existing),
                QuickPassengerRequest(
                    passengerName = "Novo",
                    passengerContact = "11988888888",
                    passengerId = "new-passenger",
                    boardingStopId = "a",
                    dropoffStopId = "b",
                    seats = 1,
                    fareMinorUnits = 1000L,
                    fareCurrencyCode = "BRL",
                    source = BookingSource.PRIVATE,
                ),
            )
        }
    }

    @Test
    fun segmentedCapacityBlocksOnlyTheOccupiedSegments() {
        val trip = Trip(
            id = "trip-segments-0351",
            title = "A → D",
            departureAtMillis = 1_800_000_000_000L,
            capacity = 2,
            status = TripStatus.PUBLISHED,
            stops = listOf(
                TripStop(id = "a", order = 0, name = "A"),
                TripStop(id = "b", order = 1, name = "B"),
                TripStop(id = "c", order = 2, name = "C"),
                TripStop(id = "d", order = 3, name = "D"),
            ),
        )
        val occupied = Booking(
            id = "a-c-full",
            tripId = trip.id,
            passengerName = "A até C",
            boardingStopId = "a",
            dropoffStopId = "c",
            seats = 2,
            status = BookingStatus.CONFIRMED,
            source = BookingSource.PRIVATE,
        )

        assertFalse(
            SeatAvailabilityEngine.availability(
                trip = trip,
                bookings = listOf(occupied),
                boardingStopId = "a",
                dropoffStopId = "b",
                requestedSeats = 1,
            ).canBook,
        )
        assertTrue(
            SeatAvailabilityEngine.availability(
                trip = trip,
                bookings = listOf(occupied),
                boardingStopId = "c",
                dropoffStopId = "d",
                requestedSeats = 1,
            ).canBook,
        )
        assertThrows(IllegalArgumentException::class.java) {
            QuickPassengerEngine.build(
                trip = trip,
                existingBookings = listOf(occupied),
                request = QuickPassengerRequest(
                    passengerName = "Não cabe em A-B",
                    boardingStopId = "a",
                    dropoffStopId = "b",
                    seats = 1,
                ),
            )
        }
        val allowed = QuickPassengerEngine.build(
            trip = trip,
            existingBookings = listOf(occupied),
            request = QuickPassengerRequest(
                passengerName = "Cabe em C-D",
                passengerId = "passenger-c-d",
                boardingStopId = "c",
                dropoffStopId = "d",
                seats = 1,
            ),
            idFactory = { "booking-c-d" },
        )
        assertEquals(
            listOf(2, 2, 1),
            SeatAvailabilityEngine.segmentLoads(trip, listOf(occupied, allowed.passenger)).map { it.occupiedSeats },
        )
    }

    @Test
    fun blockedPassengerIsRejectedAgainAtSaveAndExistingCancellationPathRemains() {
        val quick = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripQuickPassengerUi.kt").readText()
        val flow = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripGlobalPassengerFlow0256.kt").readText()
        val operational = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt").readText()
        assertTrue(flow.contains("enabled = !profile.blocked"))
        assertTrue(flow.contains("NÃO ACEITO NO MEU CARRO"))
        assertTrue(quick.contains("passengerStore.profile(canonicalPassengerId)?.blocked == true"))
        assertTrue(quick.contains("TripRemoteApi(settings).upsertDriverBooking"))
        assertTrue(quick.contains("store.saveBooking(plan.passenger)"))
        assertTrue(operational.contains("BookingStatus.CANCELLED"))
        assertTrue(operational.contains("BOOKING_CANCEL_PERSISTED"))
        assertTrue(operational.contains("SEGMENT_CAPACITY_RECALCULATED"))
        assertTrue(operational.contains("PUBLIC_BOOKING_CANCEL_SYNC"))
    }
}
