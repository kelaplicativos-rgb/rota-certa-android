package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TripTimelineExternalQuickPassengerStage47Test {
    private fun entry(
        tripId: String = "blablacar:external",
        localTripId: String? = null,
        status: TripStatus = TripStatus.PUBLISHED,
        profileId: String = "11111111-1111-4111-8111-111111111111",
        blablaProfileUuid: String? = profileId,
    ) = TripTimelineEntry(
        tripId = tripId,
        profileId = profileId,
        profileLabel = "Perfil externo",
        departureAtMillis = 1_800_000_000_000L,
        arrivalAtMillis = 1_800_003_600_000L,
        origin = "Origem",
        destination = "Destino",
        status = status,
        capacity = 0,
        minimumOccupiedSeats = 1,
        maximumOccupiedSeats = 1,
        sourcePassengerSeats = mapOf(BookingSource.BLABLACAR to 1),
        localTripId = localTripId,
        blablaProfileUuid = blablaProfileUuid,
    )

    private fun localTrip(id: String = "local") = Trip(
        id = id,
        title = "Origem → Destino",
        departureAtMillis = 1_800_000_000_000L,
        capacity = 4,
        status = TripStatus.DRAFT,
        stops = listOf(
            TripStop(id = "a", order = 0, name = "Origem"),
            TripStop(id = "b", order = 1, name = "Destino"),
        ),
    )

    @Test
    fun externalPublicationAppearsEvenWithoutLocalTrip() {
        val option = timelineQuickPassengerOptions(listOf(entry()), emptyList()).single()

        assertEquals("blablacar:external", option.entry.tripId)
        assertNull(option.localTrip)
    }

    @Test
    fun existingPhysicalLocalTripIsReusedInsteadOfCreatingAnotherQuickPassengerTarget() {
        val trip = localTrip()
        val option = timelineQuickPassengerOptions(listOf(entry()), listOf(trip)).single()

        assertSame(trip, option.localTrip)
    }

    @Test
    fun localDraftWithoutExternalEvidenceIsNotOfferedAsPublishedQuickPassengerTarget() {
        val draft = entry(
            tripId = "draft",
            localTripId = "draft",
            status = TripStatus.DRAFT,
            profileId = "local",
            blablaProfileUuid = null,
        )

        assertTrue(timelineQuickPassengerOptions(listOf(draft), listOf(localTrip("draft"))).isEmpty())
    }

    @Test
    fun backingTripUsesExplicitCapacityAndCollectedRouteWithoutClaimingExternalWrite() {
        val backing = buildTimelineBackingTrip(entry(), capacity = 6)

        assertEquals(6, backing.capacity)
        assertEquals(TripStatus.DRAFT, backing.status)
        assertEquals("Origem", backing.stops.first().name)
        assertEquals("Destino", backing.stops.last().name)
        assertEquals(1_800_000_000_000L, backing.departureAtMillis)
        assertEquals(1_800_003_600_000L, backing.stops.last().plannedArrivalMillis)
    }

    @Test
    fun canonicalExternalProfileUuidHasPriorityForTargetedSync() {
        val external = entry(
            profileId = "local-or-display-id",
            blablaProfileUuid = "22222222-2222-4222-8222-222222222222",
        )

        assertEquals("22222222-2222-4222-8222-222222222222", canonicalTimelineProfileUuid(external))
    }
}
