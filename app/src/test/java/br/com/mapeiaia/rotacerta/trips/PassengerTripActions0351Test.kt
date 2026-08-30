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
        assertTrue(flow.contains("passengerStore.createProfile(newName, newWhatsapp)"))
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
            existing = emptyList(),
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
        assertTrue(operational.contains("PASSENGER_CANCEL_SUCCESS"))
    }
}
