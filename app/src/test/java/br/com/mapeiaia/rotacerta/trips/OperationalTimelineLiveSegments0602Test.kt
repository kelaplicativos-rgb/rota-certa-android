package br.com.mapeiaia.rotacerta.trips

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationalTimelineLiveSegments0602Test {
    @Test
    fun canonicalSegmentProjectionIsExposedWithoutRecalculatingTimelineInventory() {
        val trip = Trip(
            id = "trip-0602",
            title = "A → C",
            departureAtMillis = 1_000L,
            capacity = 4,
            status = TripStatus.PUBLISHED,
            capacityReliable = true,
            stops = listOf(
                TripStop(id = "a", order = 0, name = "A"),
                TripStop(id = "b", order = 1, name = "B"),
                TripStop(id = "c", order = 2, name = "C"),
            ),
        )
        val entry = TripTimelineEntry(
            tripId = trip.id,
            profileId = "profile",
            profileLabel = "Motorista",
            departureAtMillis = trip.departureAtMillis,
            arrivalAtMillis = 5_000L,
            origin = "A",
            destination = "C",
            status = TripStatus.PUBLISHED,
            capacity = 4,
            minimumOccupiedSeats = 2,
            maximumOccupiedSeats = 4,
            sourcePassengerSeats = emptyMap(),
            localTripId = trip.id,
            canonicalBackendAuthoritative0494 = true,
            canonicalCapacityReliable0494 = true,
            canonicalSegmentLoads0494 = listOf(2, 4),
            canonicalSegmentPassengerLoads0494 = listOf(2, 4),
            canonicalSegmentBlockedLoads0494 = listOf(0, 0),
            canonicalSegmentAvailableSeats0494 = listOf(2, 0),
        )

        val loads = operationalTimelineSegmentLoads0602(entry, trip)

        assertEquals(2, loads.size)
        assertEquals("A", loads[0].from.name)
        assertEquals("B", loads[0].to.name)
        assertEquals(2, loads[0].passengerSeats)
        assertEquals(2, loads[0].availableSeats)
        assertEquals("B", loads[1].from.name)
        assertEquals("C", loads[1].to.name)
        assertEquals(4, loads[1].passengerSeats)
        assertEquals(0, loads[1].availableSeats)
    }

    @Test
    fun unreliableCapacityFailsClosedInsteadOfShowingFalseLotado() {
        val trip = Trip(
            id = "trip-unknown",
            title = "A → B",
            departureAtMillis = 1_000L,
            capacity = 0,
            status = TripStatus.PUBLISHED,
            capacityReliable = false,
            stops = listOf(
                TripStop(id = "a", order = 0, name = "A"),
                TripStop(id = "b", order = 1, name = "B"),
            ),
        )
        val entry = TripTimelineEntry(
            tripId = trip.id,
            profileId = "profile",
            profileLabel = "Motorista",
            departureAtMillis = trip.departureAtMillis,
            arrivalAtMillis = 2_000L,
            origin = "A",
            destination = "B",
            status = TripStatus.PUBLISHED,
            capacity = 0,
            minimumOccupiedSeats = 0,
            maximumOccupiedSeats = 0,
            sourcePassengerSeats = emptyMap(),
            localTripId = trip.id,
            canonicalBackendAuthoritative0494 = true,
            canonicalCapacityReliable0494 = false,
            canonicalSegmentLoads0494 = listOf(0),
            canonicalSegmentAvailableSeats0494 = listOf(0),
        )

        assertTrue(operationalTimelineSegmentLoads0602(entry, trip).isEmpty())
    }

    @Test
    fun allTripsCardsKeepDriverOnlyLiveSegmentGlanceVisible() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/OperationalAllTripsBrowserUi0563.kt",
        ).readText()

        val start = source.indexOf("// 0.1.602 — leitura viva de vagas por trecho diretamente no card da Timeline.")
        val end = source.indexOf("if (!targetConfirmed)", start)
        assertTrue(start >= 0 && end > start)
        val section = source.substring(start, end)

        assertTrue(source.contains("projectedTimeline0602"))
        assertTrue(source.contains("operationalTimelineSegmentLoads0602("))
        assertTrue(source.contains("canonicalTimelineSegmentLoads0494(entry, trip)"))
        assertTrue(section.contains("Vagas por trecho"))
        assertTrue(section.contains("row.segmentLoads0602.forEach"))
        assertTrue(section.contains("load0602.availableSeats"))
        assertTrue(section.contains("load0602.passengerSeats"))
        assertTrue(section.contains("load0602.blockedSeats"))
        assertTrue(section.contains("load0602.overbookingSeats"))
        assertTrue(section.contains("\"LOTADO\""))
        assertTrue(section.contains("Aguardando atualização canônica das vagas."))
        assertTrue(!section.contains("Reserve Já"))
        assertTrue(!section.contains("Indisponível"))
        assertTrue(!source.contains("SeatAvailabilityEngine.segmentLoads"))
    }
}
