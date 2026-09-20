package br.com.mapeiaia.rotacerta.trips

import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun centralCardCarriesCanonicalVacanciesAndOccupancyForEverySegment() {
        val trip = trip(
            id = "segments",
            departure = start,
            profileUuid = "",
            externalTripId = "",
            origin = "São Paulo",
            destination = "Pouso Alegre",
            arrival = start + 3L * 60L * 60_000L,
        ).copy(
            capacity = 99,
            publishedSeats = 4,
            capacityReliable = true,
            stops = listOf(
                TripStop(id = "sp", order = 0, name = "São Paulo", plannedDepartureMillis = start),
                TripStop(id = "ext", order = 1, name = "Extrema", plannedArrivalMillis = start + 90L * 60_000L),
                TripStop(id = "pa", order = 2, name = "Pouso Alegre", plannedArrivalMillis = start + 3L * 60L * 60_000L),
            ),
        )
        val bookings = listOf(
            Booking(
                id = "sp-ext",
                tripId = trip.id,
                passengerName = "Grupo A",
                boardingStopId = "sp",
                dropoffStopId = "ext",
                seats = 2,
                status = BookingStatus.CONFIRMED,
                operationalStatus = PassengerOperationalStatus.CONFIRMED,
                source = BookingSource.BLABLACAR,
            ),
            Booking(
                id = "ext-pa",
                tripId = trip.id,
                passengerName = "Pessoa B",
                boardingStopId = "ext",
                dropoffStopId = "pa",
                seats = 1,
                status = BookingStatus.CONFIRMED,
                operationalStatus = PassengerOperationalStatus.CONFIRMED,
                source = BookingSource.BLABLACAR,
            ),
        )

        val model = CentralDayReadModelBuilder0552.build(
            trips = listOf(trip),
            bookings = bookings,
            accounts = emptyList(),
            date = day,
            nowMillis = start,
            zoneId = zone,
        )

        val card = model.trips.single()
        assertEquals(4, card.operationalCapacity)
        assertEquals(2, card.segmentLoads.size)
        assertEquals("São Paulo", card.segmentLoads[0].from.name)
        assertEquals("Extrema", card.segmentLoads[0].to.name)
        assertEquals(2, card.segmentLoads[0].passengerSeats)
        assertEquals(2, card.segmentLoads[0].availableSeats)
        assertEquals("Extrema", card.segmentLoads[1].from.name)
        assertEquals("Pouso Alegre", card.segmentLoads[1].to.name)
        assertEquals(1, card.segmentLoads[1].passengerSeats)
        assertEquals(3, card.segmentLoads[1].availableSeats)
    }

    @Test
    fun unreliableCapacityNeverTurnsUnknownSegmentsIntoFalseLotado() {
        val trip = trip(
            id = "unknown-capacity",
            departure = start,
            profileUuid = "",
            externalTripId = "",
        ).copy(capacity = 0, capacityReliable = false)

        val model = CentralDayReadModelBuilder0552.build(
            trips = listOf(trip),
            bookings = emptyList(),
            accounts = emptyList(),
            date = day,
            nowMillis = start,
            zoneId = zone,
        )

        val card = model.trips.single()
        assertEquals(null, card.operationalCapacity)
        assertTrue(card.segmentLoads.isEmpty())
        assertEquals(null, card.availableSeats)
    }

    @Test
    fun ongoingTripRemainsGreenInAllTripsVisibilityAfterDeparture() {
        val profile = "33333333-3333-4333-8333-333333333333"
        val ongoing = trip(
            id = "ongoing",
            departure = start,
            profileUuid = profile,
            externalTripId = "ongoing-trip",
            arrival = start + 4L * 60L * 60_000L,
        )
        val account = BlaBlaDynamicAccount(
            id = "account-1",
            label = "Motorista",
            webProfileName = "profile-account-1",
            profileUuid = profile,
        )

        val model = CentralDayReadModelBuilder0552.build(
            trips = listOf(ongoing),
            bookings = emptyList(),
            accounts = listOf(account),
            date = day,
            nowMillis = start + 90L * 60_000L,
            zoneId = zone,
        )

        val check = model.trips.single().checks.single { it.key == "all_trips_visibility" }
        assertEquals(CentralIntegrityLevel0552.OK, check.level)
        assertTrue(check.title.contains("mantém esta viagem operacional"))
    }

    @Test
    fun overbookingDiagnosticNamesTheExactSegmentInsteadOfOnlyAHashOrCounter() {
        val profile = "44444444-4444-4444-8444-444444444444"
        val overloaded = trip(
            id = "overloaded",
            departure = start,
            profileUuid = profile,
            externalTripId = "overloaded-trip",
            origin = "Origem Teste",
            destination = "Destino Teste",
            arrival = start + 2L * 60L * 60_000L,
        ).copy(capacity = 2, publishedSeats = 2, capacityReliable = true)
        val booking = Booking(
            id = "overloaded-booking",
            tripId = overloaded.id,
            passengerName = "Grupo",
            boardingStopId = overloaded.stops.first().id,
            dropoffStopId = overloaded.stops.last().id,
            seats = 3,
            status = BookingStatus.CONFIRMED,
            operationalStatus = PassengerOperationalStatus.CONFIRMED,
            source = BookingSource.BLABLACAR,
        )
        val account = BlaBlaDynamicAccount(
            id = "account-2",
            label = "Motorista",
            webProfileName = "profile-account-2",
            profileUuid = profile,
        )

        val model = CentralDayReadModelBuilder0552.build(
            trips = listOf(overloaded),
            bookings = listOf(booking),
            accounts = listOf(account),
            date = day,
            nowMillis = start + 30L * 60_000L,
            zoneId = zone,
        )

        val capacity = model.trips.single().checks.single { it.key == "capacity" }
        assertEquals(CentralIntegrityLevel0552.ACTION_REQUIRED, capacity.level)
        assertTrue(capacity.actual.contains("Origem Teste → Destino Teste"))
        assertTrue(capacity.actual.contains("1 acima"))
        assertTrue(capacity.detail.contains("simultaneidade"))
    }

    @Test
    fun centralDayUiStaysCompactUntilOperatorAsksForPassengerDetails() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/CentralDoDia0552.kt").readText()

        assertFalse(source.contains("Text(\"Central do Dia\", style = MaterialTheme.typography.titleLarge)"))
        assertTrue(source.contains("expandedPassengerTripIds0591"))
        assertTrue(source.contains("Passageiros \${item.passengers.size} ▼"))
        assertTrue(source.contains("if (passengersExpanded0591)"))
        assertTrue(source.contains("↻ Corrigir"))
        assertFalse(source.contains("Text(\"🔄 Corrigir esta viagem\")"))
        assertTrue(source.contains("all_trips_visibility"))
        assertTrue(source.contains("\"Vagas por trecho\""))
        assertTrue(source.contains("item.segmentLoads.forEach"))
        assertTrue(source.contains("Text(\"👥 \$occupancy0595\""))
        assertTrue(source.contains("\"LOTADO\""))
        assertFalse(source.contains("Text(\"expectedHash:"))
        assertFalse(source.contains("Text(\"actualHash:"))
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
