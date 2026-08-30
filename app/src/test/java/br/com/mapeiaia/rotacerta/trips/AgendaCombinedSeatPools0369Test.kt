package br.com.mapeiaia.rotacerta.trips

import java.io.File
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgendaCombinedSeatPools0369Test {
    private val zone = ZoneId.of("America/Sao_Paulo")

    @Test
    fun threeBlaBlaPlusFourRotaCertaEqualsSevenAvailableInAgenda() {
        assertEquals(7, PublicAgendaAutoSync0300.combinedAgendaAvailableSeats(3, 4))

        val source = BlaBlaCollectorTrip(
            profile_uuid = "profile-ezequiel",
            date = "2030-09-04",
            departure_time = "10:30",
            actual_departure = "São Paulo",
            actual_arrival = "São Tomé das Letras",
            published_seats = 3,
            booked_seats = 3,
            passengers = listOf(
                BlaBlaCollectorPassenger(name = "P1", seats = 1),
                BlaBlaCollectorPassenger(name = "P2", seats = 1),
                BlaBlaCollectorPassenger(name = "P3", seats = 1),
            ),
        )

        val publicTrip = PublicAgendaAutoSync0300.toPublicTrip(source, 7, 0L, zone)
        assertNotNull(publicTrip)
        assertEquals(7, publicTrip.trip.capacity)
        assertEquals(3, publicTrip.trip.publishedSeats)
        assertEquals(TripStatus.PUBLISHED, publicTrip.trip.status)
        assertTrue(publicTrip.capacityClaims.isEmpty())

        val loads = SeatAvailabilityEngine.segmentLoads(publicTrip.trip, publicTrip.capacityClaims)
        assertTrue(loads.isNotEmpty())
        assertTrue(loads.all { it.occupiedSeats == 0 })
        assertTrue(loads.all { it.availableSeats == 7 })
    }

    @Test
    fun normalSynchronizationReadsPublishedSeatsButCannotWriteThem() {
        val policy = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaHarvestPolicy.kt").readText()
        val dynamic = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()
        assertTrue(policy.contains("AUTOMATIC_PUBLISHED_SEAT_LOOKUP: Boolean = true"))
        assertTrue(dynamic.contains("BlaBlaBrowserRequest.SEAT_OPTIONS"))
        assertTrue(!dynamic.contains("executeRemoteWrite("))
        assertTrue(!dynamic.contains("BlaBlaBrowserRequest.SEAT_CHANGE"))
        assertTrue(!dynamic.contains("BlaBlaBrowserRequest.SEAT_SAVE"))
    }

    @Test
    fun publicProjectionDeclaresAdditivePoolAsSingleCapacityAuthority() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PublicAgendaAutoSync0300.kt").readText()
        assertTrue(source.contains("capacitySource=additive_pools"))
        assertTrue(source.contains("combinedAgendaAvailableSeats"))
        assertTrue(source.contains("blablaAvailableSeats"))
        assertTrue(source.contains("rotaCertaAvailableSeats"))
        assertTrue(!source.contains("driverReservedGap("))
        assertTrue(!source.contains("nonBlaBlaOccupancyImpact("))
    }
}
