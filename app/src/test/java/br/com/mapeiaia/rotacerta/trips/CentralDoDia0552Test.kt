package br.com.mapeiaia.rotacerta.trips

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CentralDoDia0552Test {
    private val zone = ZoneId.of("UTC")
    private val day = LocalDate.of(2026, 9, 13)
    private val start = day.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun integrityPercentageExcludesUnknownInsteadOfCallingUnknownCorrect() {
        val trip = trip("t1", start, profileUuid = "", externalTripId = "")
        val model = CentralDayReadModelBuilder0552.build(
            trips = listOf(trip),
            bookings = emptyList(),
            accounts = emptyList(),
            date = day,
            nowMillis = start - 60_000,
            zoneId = zone,
        )

        assertTrue(model.summary.unknownCount > 0)
        assertTrue(model.summary.verifiableChecks > 0)
        assertTrue(model.summary.integrityPercent != null)
    }

    @Test
    fun impossibleOverlapForSameStrongProfileIsActionRequired() {
        val first = trip(
            id = "a",
            departure = start,
            profileUuid = "11111111-1111-1111-1111-111111111111",
            externalTripId = "ext-a",
            origin = "Origem A",
            destination = "Destino A",
            arrival = start + 3 * 60 * 60_000L,
        )
        val second = trip(
            id = "b",
            departure = start + 2 * 60 * 60_000L,
            profileUuid = first.blablaProfileUuid.orEmpty(),
            externalTripId = "ext-b",
            origin = "Origem B",
            destination = "Destino B",
            arrival = start + 5 * 60 * 60_000L,
        )

        val model = CentralDayReadModelBuilder0552.build(
            trips = listOf(first, second),
            bookings = emptyList(),
            accounts = emptyList(),
            date = day,
            nowMillis = start - 60_000,
            zoneId = zone,
        )

        val conflict = model.trips.single { it.canonicalTripId == "b" }
        assertEquals(CentralIntegrityLevel0552.ACTION_REQUIRED, conflict.integrity)
        assertTrue(conflict.checks.any { it.key == "physical_continuity" && it.level == CentralIntegrityLevel0552.ACTION_REQUIRED })
    }

    @Test
    fun passengerAndCapacityUseCanonicalEngineNotParallelArithmetic() {
        val trip = trip("capacity", start, "22222222-2222-2222-2222-222222222222", "ext-cap")
            .copy(publishedSeats = 3, rotaCertaSeatAllocation = 1, capacity = 4, capacityReliable = true)
        val booking = Booking(
            id = "booking",
            tripId = trip.id,
            passengerName = "Pessoa Teste",
            boardingStopId = trip.stops.first().id,
            dropoffStopId = trip.stops.last().id,
            seats = 2,
            status = BookingStatus.CONFIRMED,
            operationalStatus = PassengerOperationalStatus.CONFIRMED,
            source = BookingSource.ROTA_CERTA,
        )

        val expected = operationalSeatSummary(trip, listOf(booking), start).availableSeats
        val model = CentralDayReadModelBuilder0552.build(
            trips = listOf(trip),
            bookings = listOf(booking),
            accounts = emptyList(),
            date = day,
            nowMillis = start,
            zoneId = zone,
        )

        assertEquals(expected, model.trips.single().availableSeats)
    }

    private fun trip(
        id: String,
        departure: Long,
        profileUuid: String,
        externalTripId: String,
        origin: String = "Origem",
        destination: String = "Destino",
        arrival: Long = departure + 60 * 60_000L,
    ): Trip = Trip(
        id = id,
        title = "$origin → $destination",
        departureAtMillis = departure,
        capacity = 0,
        status = TripStatus.PUBLISHED,
        stops = listOf(
            TripStop(id = "$id-o", order = 0, name = origin, plannedDepartureMillis = departure),
            TripStop(id = "$id-d", order = 1, name = destination, plannedArrivalMillis = arrival),
        ),
        blablaProfileUuid = profileUuid.takeIf(String::isNotBlank),
        blablaTripId = externalTripId.takeIf(String::isNotBlank),
        blablaManageUrl = externalTripId.takeIf(String::isNotBlank)?.let { "https://www.blablacar.com.br/rides/offer/$it" },
        tripKey = "tripkey:$id",
        canonicalRevision = 1,
        canonicalStateHash = "hash-$id",
    )
}
