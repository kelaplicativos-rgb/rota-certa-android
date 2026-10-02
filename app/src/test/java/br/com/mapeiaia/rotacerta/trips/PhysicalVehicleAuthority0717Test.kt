package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhysicalVehicleAuthority0717Test {
    private fun trip(capacity: Int) = Trip(
        id = "trip-0717",
        title = "A → C",
        departureAtMillis = 1_800_000_000_000L,
        capacity = capacity,
        physicalSeatCapacity = capacity,
        vehicleDayConfigured = true,
        vehicleMakeModel = "Hyundai HB20",
        vehicleColor = "Cinza",
        vehiclePlate = "TBJ4F74",
        status = TripStatus.PUBLISHED,
        publishedSeats = capacity,
        rotaCertaSeatAllocation = capacity,
        stops = listOf(
            TripStop(id = "a", order = 0, name = "A"),
            TripStop(id = "b", order = 1, name = "B"),
            TripStop(id = "c", order = 2, name = "C"),
        ),
    )

    private fun booking(id: String, from: String = "a", to: String = "c", seats: Int = 1) = Booking(
        id = id,
        tripId = "trip-0717",
        passengerName = id,
        boardingStopId = from,
        dropoffStopId = to,
        seats = seats,
        status = BookingStatus.CONFIRMED,
        source = BookingSource.ROTA_CERTA,
        capacityClaimType = CapacityClaimType.PASSENGER,
    )

    @Test
    fun fourSeatCarNeverBecomesSevenWhenChannelsAndPassengersArePresent() {
        val trip = trip(4).copy(publishedSeats = 4, rotaCertaSeatAllocation = 4)
        val bookings = listOf(booking("p1"), booking("p2"), booking("p3"))
        assertEquals(4, operationalInventoryCapacity(trip, bookings))
        val loads = SeatAvailabilityEngine.segmentLoads(trip, bookings)
        assertEquals(listOf(1, 1), loads.map(SegmentLoad::availableSeats))
        assertTrue(loads.all { it.availableSeats in 0..4 })
    }

    @Test
    fun sevenAndSixtySeatVehiclesDriveAvailabilityWithoutArtificialClamps() {
        val seven = trip(7)
        assertEquals(7, operationalInventoryCapacity(seven, emptyList()))
        assertEquals(2, SeatAvailabilityEngine.segmentLoads(seven, listOf(booking("group", seats = 5))).first().availableSeats)

        val sixty = trip(60)
        assertEquals(60, operationalInventoryCapacity(sixty, emptyList()))
        assertEquals(46, SeatAvailabilityEngine.segmentLoads(sixty, listOf(booking("bus", seats = 14))).first().availableSeats)
    }

    @Test
    fun overbookingIsReportedInsteadOfIncreasingCapacity() {
        val four = trip(4)
        val load = SeatAvailabilityEngine.segmentLoads(four, listOf(booking("five", seats = 5))).first()
        assertEquals(0, load.availableSeats)
        assertEquals(1, load.overbookingSeats)
        assertEquals(4, four.capacity)
    }

    @Test
    fun timelineExposesCarOfDayFieldsAndMessagesUsePlateWithParagraphs() {
        val timeline = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()
        val messages = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt").readText()
        assertContains(timeline, "🚗 Carro do dia")
        assertContains(timeline, "Lugares para passageiros")
        assertContains(timeline, "vehiclePlate")
        assertContains(timeline, "CARRO_DO_DIA_SAVED_0717")
        assertContains(messages, "vehiclePlate")
        assertContains(messages, "Placa:")
        assertContains(messages, "\\n\\nPerto do horário envio minha localização em tempo real")
    }

    @Test
    fun passengerAdminKeepsOnlyClearPasswordAction() {
        val admin = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerAdminUi.kt").readText()
        val api = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripRemoteApi.kt").readText()
        assertContains(admin, "Limpar senha")
        assertFalse(admin.contains("Gerar nova senha"))
        assertFalse(admin.contains("Gerar senha de primeiro acesso"))
        assertFalse(admin.contains("Reparar acesso"))
        assertContains(api, "val cleared: Boolean = false")
        assertContains(api, "val invalidatedSessions: Int = 0")
    }
}
