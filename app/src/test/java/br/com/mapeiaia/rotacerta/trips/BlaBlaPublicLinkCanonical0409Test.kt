package br.com.mapeiaia.rotacerta.trips

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class BlaBlaPublicLinkCanonical0409Test {
    private val tripIdA = "trip-0409-alpha"
    private val tripIdB = "trip-0409-beta"

    @Test
    fun officialMarketPermalinksAreValidatedByStrongTripIdWithoutRegionalPinning() {
        assertEquals(
            "https://www.blablacar.com.br/trip?id=$tripIdA",
            BlaBlaCollectorUrlModule.publicTrip(
                "https://www.blablacar.com.br/trip?id=$tripIdA&search_uuid=tracking",
                tripIdA,
            ),
        )
        assertEquals(
            "https://www.blablacar.fr/trip/$tripIdA",
            BlaBlaCollectorUrlModule.publicTrip("https://www.blablacar.fr/trip/$tripIdA", tripIdA),
        )
        assertEquals(
            "https://www.blablacar.co.uk/trip?id=$tripIdA",
            BlaBlaCollectorUrlModule.publicTrip("https://www.blablacar.co.uk/trip?id=$tripIdA", tripIdA),
        )
        assertNull(BlaBlaCollectorUrlModule.publicTrip("https://blablacar.evil.com/trip?id=$tripIdA", tripIdA))
        assertNull(BlaBlaCollectorUrlModule.publicTrip("http://www.blablacar.fr/trip?id=$tripIdA", tripIdA))
        assertNull(BlaBlaCollectorUrlModule.publicTrip("https://www.blablacar.fr/search?id=$tripIdA", tripIdA))
        assertNull(BlaBlaCollectorUrlModule.publicTrip("https://www.blablacar.fr/trip?id=$tripIdB", tripIdA))
    }

    @Test
    fun staleObservationWithoutPermalinkCannotEraseCanonicalPermalink() {
        val existing = "https://www.blablacar.com.br/trip?id=$tripIdA"
        assertEquals(existing, canonicalBlaBlaPublicUrl0409(existing, null, tripIdA))
        assertEquals(existing, canonicalBlaBlaPublicUrl0409(existing, "", tripIdA))
        assertEquals(existing, canonicalBlaBlaPublicUrl0409(existing, "https://example.com/trip?id=$tripIdA", tripIdA))
        assertEquals(
            "https://www.blablacar.fr/trip?id=$tripIdA",
            canonicalBlaBlaPublicUrl0409(
                existing,
                "https://www.blablacar.fr/trip?id=$tripIdA&search_uuid=temporary",
                tripIdA,
            ),
        )
    }

    @Test
    fun canonicalHashTreatsPublicPermalinkAsAuthoritativeTripState() {
        val base = trip(tripIdA, "https://www.blablacar.com.br/trip?id=$tripIdA")
        val changed = base.copy(blablaPublicUrl = "https://www.blablacar.fr/trip?id=$tripIdA")
        val tracked = base.copy(blablaPublicUrl = "https://www.blablacar.com.br/trip?id=$tripIdA&search_uuid=temporary")
        assertNotEquals(canonicalTripStateHash0406(base, emptyList()), canonicalTripStateHash0406(changed, emptyList()))
        assertEquals(canonicalTripStateHash0406(base, emptyList()), canonicalTripStateHash0406(tracked, emptyList()))
    }

    @Test
    fun collectorFingerprintChangesOnlyForTheTripWhosePermalinkChanges() {
        val a1 = source(tripIdA, "https://www.blablacar.com.br/trip?id=$tripIdA")
        val a2 = a1.copy(public_trip_href = "https://www.blablacar.fr/trip?id=$tripIdA")
        val b = source(tripIdB, "https://www.blablacar.com.br/trip?id=$tripIdB")
        val bAgain = b.copy()
        assertNotEquals(
            PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(a1, 0),
            PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(a2, 0),
        )
        assertEquals(
            PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(b, 0),
            PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(bAgain, 0),
        )
    }

    @Test
    fun canonicalProjectionKeepsPersistedPermalinkWhenFreshCollectorCardOmitsIt() {
        val canonicalUrl = "https://www.blablacar.com.br/trip?id=$tripIdA"
        val canonical = trip(tripIdA, canonicalUrl)
        val projection = PublicAgendaAutoSync0300.toCanonicalExternalProjection0406(
            canonical = canonical,
            source = source(tripIdA, null),
            nowMillis = 0L,
        )
        assertEquals(canonicalUrl, projection?.trip?.blablaPublicUrl)
        assertEquals(canonicalUrl, projection?.blablaPublicHref)
        assertEquals(tripIdA, projection?.blablaTripId)
    }

    @Test
    fun publicPermalinkPersistsWithTheSameStrongTripIdentity() {
        val original = trip(tripIdA, "https://www.blablacar.com.br/trip?id=$tripIdA")
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val restored = json.decodeFromString<Trip>(json.encodeToString(original))
        assertEquals(tripIdA, restored.blablaTripId)
        assertEquals(original.blablaPublicUrl, restored.blablaPublicUrl)
        assertEquals(original.blablaProfileUuid, restored.blablaProfileUuid)
    }

    @Test
    fun twoTripIdsNeverCrossAssignTheirPermalinks() {
        val hrefA = "https://www.blablacar.com.br/trip?id=$tripIdA"
        val hrefB = "https://www.blablacar.com.br/trip?id=$tripIdB"
        assertEquals(hrefA, exactPublicTripHrefForTrip(tripIdA, listOf(hrefB, hrefA)))
        assertEquals(hrefB, exactPublicTripHrefForTrip(tripIdB, listOf(hrefA, hrefB)))
        assertNull(exactPublicTripHrefForTrip(tripIdA, listOf(hrefB)))
    }

    private fun source(tripId: String, publicUrl: String?) = BlaBlaCollectorTrip(
        profile_uuid = "11111111-1111-4111-8111-111111111111",
        date = "2026-09-10",
        departure_time = "11:00",
        search_from = "Origem",
        search_to = "Destino",
        actual_departure = "Origem",
        actual_arrival = "Destino",
        trip_href = "https://www.blablacar.com.br/rides/offer/$tripId",
        public_trip_href = publicUrl,
        trip_id = tripId,
        availability = "available",
        published_seats = 2,
        passenger_roster_complete = true,
    )

    private fun trip(tripId: String, publicUrl: String?) = Trip(
        id = "canonical-$tripId",
        title = "Origem → Destino",
        departureAtMillis = 1_800_000_000_000L,
        capacity = 2,
        status = TripStatus.PUBLISHED,
        stops = listOf(
            TripStop(id = "a", order = 0, name = "Origem"),
            TripStop(id = "b", order = 1, name = "Destino"),
        ),
        blablaProfileUuid = "11111111-1111-4111-8111-111111111111",
        blablaTripId = tripId,
        blablaPublicUrl = publicUrl,
        publishedSeats = 2,
        recordOrigin = TripRecordOrigin.EXTERNAL_BACKING,
        tripKey = "tenant|blablacar|profile|$tripId",
    )
}
