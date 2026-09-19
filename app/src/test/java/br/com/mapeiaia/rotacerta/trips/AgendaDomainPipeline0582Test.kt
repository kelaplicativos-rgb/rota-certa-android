package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaDomainPipeline0582Test {
    private val hour = 60L * 60L * 1000L
    private val now = 2_000_000_000_000L

    private fun trip(
        capacity: Int = 4,
        publicUrl: String? = "https://www.blablacar.com.br/trip?id=publicTrip0582A",
    ): Trip = Trip(
        id = "agenda-domain-0582",
        title = "Origem → Destino",
        departureAtMillis = now + hour,
        capacity = capacity,
        status = TripStatus.PUBLISHED,
        canonicalRevision = 18,
        publicationRevision = 21,
        blablaProfileUuid = "profile-0582",
        blablaTripId = "administrativeTrip0582",
        blablaPublicUrl = publicUrl,
        stops = listOf(
            TripStop(
                id = "origin",
                order = 0,
                name = "Origem",
                plannedDepartureMillis = now + hour,
            ),
            TripStop(
                id = "destination",
                order = 1,
                name = "Destino",
                plannedArrivalMillis = now + 5 * hour,
            ),
        ),
    ).withCanonicalAgendaVisibility0581()

    @Test
    fun publicLinkChangeCannotMutateVisibilitySeatsRouteOrSchedule() {
        val original = AgendaDomainPipeline0582.project(trip(), emptyList(), now)
        val changed = AgendaDomainPipeline0582.project(
            trip(publicUrl = "https://www.blablacar.com.br/trip?id=publicTrip0582B"),
            emptyList(),
            now,
        )

        assertTrue(original.publicLink.available)
        assertTrue(changed.publicLink.available)
        assertFalse(original.fingerprints.publicLink == changed.fingerprints.publicLink)
        assertEquals(
            emptySet(),
            AgendaRegressionGuard0582.unexpectedChanges(
                AgendaChangeScope0582.PUBLIC_LINK,
                original.fingerprints,
                changed.fingerprints,
            ),
        )
    }

    @Test
    fun seatChangeCannotMutateVisibilityLinkRouteOrSchedule() {
        val original = AgendaDomainPipeline0582.project(trip(capacity = 4), emptyList(), now)
        val changed = AgendaDomainPipeline0582.project(trip(capacity = 6), emptyList(), now)

        assertEquals(4, original.segments.single().availableSeats)
        assertEquals(6, changed.segments.single().availableSeats)
        assertEquals(
            emptySet(),
            AgendaRegressionGuard0582.unexpectedChanges(
                AgendaChangeScope0582.SEATS,
                original.fingerprints,
                changed.fingerprints,
            ),
        )
    }

    @Test
    fun clockProgressOnlyChangesVisibilityFacet() {
        val value = trip()
        val before = AgendaDomainPipeline0582.project(value, emptyList(), now)
        val after = AgendaDomainPipeline0582.project(
            value,
            emptyList(),
            value.agendaVisibleUntilMillis0581 + 1,
        )

        assertTrue(before.visibility.visible)
        assertFalse(after.visibility.visible)
        assertEquals(
            emptySet(),
            AgendaRegressionGuard0582.unexpectedChanges(
                AgendaChangeScope0582.VISIBILITY,
                before.fingerprints,
                after.fingerprints,
            ),
        )
    }

    @Test
    fun missingPublicLinkNeverChangesLifecycle() {
        val linked = AgendaDomainPipeline0582.project(trip(), emptyList(), now)
        val missing = AgendaDomainPipeline0582.project(trip(publicUrl = null), emptyList(), now)

        assertTrue(linked.visibility.visible)
        assertTrue(missing.visibility.visible)
        assertTrue(linked.publicLink.available)
        assertFalse(missing.publicLink.available)
        assertEquals(linked.fingerprints.visibility, missing.fingerprints.visibility)
    }

    @Test
    fun scheduleScopeAllowsLifecycleRecalculationButNotSeatsOrLink() {
        val originalTrip = trip()
        val shifted = originalTrip.copy(
            departureAtMillis = originalTrip.departureAtMillis + hour,
            stops = originalTrip.stops.map { stop ->
                stop.copy(
                    plannedArrivalMillis = stop.plannedArrivalMillis?.plus(hour),
                    plannedDepartureMillis = stop.plannedDepartureMillis?.plus(hour),
                )
            },
        ).withCanonicalAgendaVisibility0581()
        val before = AgendaDomainPipeline0582.project(originalTrip, emptyList(), now)
        val after = AgendaDomainPipeline0582.project(shifted, emptyList(), now)

        assertEquals(
            emptySet(),
            AgendaRegressionGuard0582.unexpectedChanges(
                AgendaChangeScope0582.SCHEDULE,
                before.fingerprints,
                after.fingerprints,
            ),
        )
    }
}
