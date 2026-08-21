package br.com.mapeiaia.rotacerta.trips

import br.com.mapeiaia.rotacerta.Coordinate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun timelineSearchIsCaseAccentInsensitiveAndMultiTermAcrossProfilePassengerAndStops() {
        val external = entry().copy(
            profileLabel = "Ezequiel São",
            origin = "Santo André",
            destination = "São Tomé das Letras",
            blablaPassengers = listOf(
                BlaBlaCollectorPassenger(
                    name = "João Ávila",
                    boarding = "Extrema",
                    dropoff = "Três Corações",
                    phone = "+5511999999999",
                )
            ),
        )

        val matched = filterTimelineEntries(
            entries = listOf(external),
            trips = emptyList(),
            bookings = emptyList(),
            query = "EZEQUIEL joao tres",
            zoneId = ZoneId.of("UTC"),
            locale = Locale("pt", "BR"),
            nowMillis = 0L,
        )
        val missed = filterTimelineEntries(
            entries = listOf(external),
            trips = emptyList(),
            bookings = emptyList(),
            query = "barbosa joao",
            zoneId = ZoneId.of("UTC"),
            locale = Locale("pt", "BR"),
            nowMillis = 0L,
        )

        assertEquals(listOf(external), matched)
        assertTrue(missed.isEmpty())
    }

    @Test
    fun timelineSearchFindsLocalPassengerRouteSourceDateAndTime() {
        val trip = Trip(
            id = "local",
            title = "Santo André → São Tomé",
            departureAtMillis = 1_800_000_000_000L,
            capacity = 4,
            status = TripStatus.PUBLISHED,
            stops = listOf(
                TripStop(id = "sa", order = 0, name = "Santo André"),
                TripStop(id = "st", order = 1, name = "São Tomé das Letras"),
            ),
        )
        val booking = Booking(
            id = "p1",
            tripId = trip.id,
            passengerName = "Míriam Faria",
            passengerContact = "+5511988887777",
            boardingStopId = "sa",
            dropoffStopId = "st",
            seats = 1,
            status = BookingStatus.CONFIRMED,
            source = BookingSource.PRIVATE,
        )
        val localEntry = entry(
            tripId = trip.id,
            localTripId = trip.id,
            profileId = "local",
            blablaProfileUuid = null,
        ).copy(
            profileLabel = "Agenda",
            origin = "Santo André",
            destination = "São Tomé das Letras",
        )

        val result = filterTimelineEntries(
            entries = listOf(localEntry),
            trips = listOf(trip),
            bookings = listOf(booking),
            query = "miriam particular santo tome 15/01 08:00",
            zoneId = ZoneId.of("UTC"),
            locale = Locale("pt", "BR"),
            nowMillis = 0L,
        )

        assertEquals(listOf(localEntry), result)
    }

    @Test
    fun baseDirectionUsesPlainArrowsAndRequiresTrustedCoordinates() {
        val home = Coordinate(0.0, 0.0)
        val leaving = localTrip("leaving").copy(
            stops = listOf(
                TripStop(id = "la", order = 0, name = "Casa", latitude = 0.0, longitude = 0.0),
                TripStop(id = "lb", order = 1, name = "Fora", latitude = 0.1, longitude = 0.0),
            )
        )
        val returning = localTrip("returning").copy(
            stops = listOf(
                TripStop(id = "ra", order = 0, name = "Fora", latitude = 0.1, longitude = 0.0),
                TripStop(id = "rb", order = 1, name = "Casa", latitude = 0.0, longitude = 0.0),
            )
        )
        val neutral = localTrip("neutral").copy(
            stops = listOf(
                TripStop(id = "na", order = 0, name = "Fora A", latitude = 0.1, longitude = 0.0),
                TripStop(id = "nb", order = 1, name = "Fora B", latitude = 0.2, longitude = 0.0),
            )
        )

        assertEquals("↑", timelineBaseDirection(leaving, home, 1.0))
        assertEquals("↓", timelineBaseDirection(returning, home, 1.0))
        assertEquals("↔", timelineBaseDirection(neutral, home, 1.0))
        assertNull(timelineBaseDirection(localTrip("missing"), home, 1.0))
    }

    @Test
    fun geoResolverUsesOnlyPersistedTripStopEvidenceAndFailsClosedOnAmbiguity() {
        val trusted = TripStop(
            id = "trusted",
            order = 0,
            name = "São Tomé",
            address = "Centro, São Tomé",
            latitude = -21.72,
            longitude = -44.98,
        )
        val resolved = TripTimelineGeoResolver.resolveTrustedStops(
            places = listOf("sao tome", "Sem evidência"),
            trustedStops = listOf(trusted),
        )

        assertEquals(TimelineGeoPoint(-21.72, -44.98), resolved["sao tome"])
        assertFalse(resolved.containsKey("Sem evidência"))

        val ambiguous = TripTimelineGeoResolver.resolveTrustedStops(
            places = listOf("São Tomé"),
            trustedStops = listOf(
                trusted,
                trusted.copy(id = "conflict", latitude = -22.0, longitude = -45.2),
            ),
        )
        assertFalse(ambiguous.containsKey("São Tomé"))
    }
}
