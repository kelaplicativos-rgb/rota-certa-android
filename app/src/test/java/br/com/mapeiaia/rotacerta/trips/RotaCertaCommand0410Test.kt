package br.com.mapeiaia.rotacerta.trips

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RotaCertaCommand0410Test {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val now = Instant.parse("2026-09-02T14:00:00Z")

    @Test
    fun everyActionHasExplicitCoverage() {
        assertEquals(
            RotaCertaAction0410.values().toSet(),
            RotaCertaCommandRegistry0410.entries.keys,
        )
    }

    @Test
    fun blockedStaticApkCapabilitiesNeverEnterInterpreterAllowlist() {
        val allowed = RotaCertaCommandRegistry0410.interpreterActions(
            hasPublisherAccount = true,
            hasVerifiedSeatTarget = true,
        )
        assertTrue(RotaCertaAction0410.CREATE_TRIPS in allowed)
        assertTrue(RotaCertaAction0410.SET_TRIP_SEATS in allowed)
        assertTrue(RotaCertaAction0410.PUBLIC_SEARCH in allowed)
        assertFalse(RotaCertaAction0410.SET_TRIP_BOOST in allowed)
        assertFalse(RotaCertaAction0410.CANCEL_TRIP in allowed)
        assertFalse(RotaCertaAction0410.SEND_MESSAGE in allowed)
    }

    @Test
    fun invalidDay32FailsClosed() {
        val command = command(
            action = RotaCertaAction0410.CREATE_TRIPS,
            temporal = RotaCertaTemporalReference0410(dayOfMonth = 32),
        )
        assertEquals(
            RotaCertaValidationCode0410.INVALID_DATE,
            RotaCertaTemporalResolver0410.resolve(command, now, zone).code,
        )
    }

    @Test
    fun february31FailsClosed() {
        val command = command(
            action = RotaCertaAction0410.CREATE_TRIPS,
            temporal = RotaCertaTemporalReference0410(
                dayOfMonth = 31,
                month = 2,
                year = 2026,
            ),
        )
        assertEquals(
            RotaCertaValidationCode0410.INVALID_DATE,
            RotaCertaTemporalResolver0410.resolve(command, now, zone).code,
        )
    }

    @Test
    fun nonLeapFebruary29FailsClosed() {
        val command = command(
            action = RotaCertaAction0410.CREATE_TRIPS,
            temporal = RotaCertaTemporalReference0410(
                explicitDate = "2026-02-29",
            ),
        )
        assertEquals(
            RotaCertaValidationCode0410.INVALID_DATE,
            RotaCertaTemporalResolver0410.resolve(command, now, zone).code,
        )
    }

    @Test
    fun invalidClockFailsClosed() {
        val command = command(
            action = RotaCertaAction0410.CREATE_TRIPS,
            temporal = RotaCertaTemporalReference0410(
                explicitDate = "2026-09-05",
                time = "25:00",
            ),
        )
        assertEquals(
            RotaCertaValidationCode0410.INVALID_TIME,
            RotaCertaTemporalResolver0410.resolve(command, now, zone).code,
        )
    }

    @Test
    fun weekdayConflictFailsClosed() {
        val command = command(
            action = RotaCertaAction0410.CREATE_TRIPS,
            temporal = RotaCertaTemporalReference0410(
                explicitDate = "2026-09-05",
                weekday = "segunda",
            ),
        )
        assertEquals(
            RotaCertaValidationCode0410.DATE_WEEKDAY_CONFLICT,
            RotaCertaTemporalResolver0410.resolve(command, now, zone).code,
        )
    }

    @Test
    fun plannerRequiresOneResolvedTripForTripScopedCommand() {
        val tripA = trip("a", 10L)
        val tripB = trip("b", 11L)
        val planned = RotaCertaCommandPlanner0410.plan(
            command = command(
                action = RotaCertaAction0410.READ_TRIP,
                tripReference = "Santo André",
            ),
            trips = listOf(tripA, tripB),
            bookings = emptyList(),
            now = now,
            zoneId = zone,
        )
        assertEquals(
            RotaCertaValidationCode0410.AMBIGUOUS_TRIP,
            planned.code,
        )
    }

    @Test
    fun stalePlanIsRejectedAfterCanonicalRevisionChanges() {
        val original = trip("a", 10L).copy(canonicalRevision = 7L)
        val planned = RotaCertaCommandPlanner0410.plan(
            command = command(
                action = RotaCertaAction0410.READ_TRIP,
                tripReference = "a",
            ),
            trips = listOf(original),
            bookings = emptyList(),
            now = now,
            zoneId = zone,
        )
        assertEquals(RotaCertaValidationCode0410.OK, planned.code)
        val plan = assertNotNull(planned.plan)
        assertFalse(
            RotaCertaCommandPlanner0410.isStale(
                plan,
                listOf(original),
            ),
        )
        assertTrue(
            RotaCertaCommandPlanner0410.isStale(
                plan,
                listOf(original.copy(canonicalRevision = 8L)),
            ),
        )
    }

    @Test
    fun dateOnlyListTripsFiltersCanonicalStateInsteadOfListingEverything() {
        val october10 = trip("oct10", 10L).copy(
            title = "Santo André para São Tomé das Letras",
            departureAtMillis = Instant.parse("2026-10-10T14:00:00Z").toEpochMilli(),
            stops = listOf(TripStop(order = 0, name = "Santo André"), TripStop(order = 1, name = "São Tomé das Letras")),
        )
        val september = trip("sep", 11L)
        val query = command(RotaCertaAction0410.LIST_TRIPS, RotaCertaTemporalReference0410(explicitDate = "2026-10-10"))
        val planned = RotaCertaCommandPlanner0410.plan(query, listOf(september, october10), emptyList(), now, zone)
        val plan = assertNotNull(planned.plan)
        assertEquals(listOf("oct10"), assistantMatchingTrips0411(query, plan, listOf(september, october10), zone).map(Trip::id))
    }

    @Test
    fun passengerQueryUsesDateTimeAndRouteToResolveOneTrip() {
        val target = trip("target", 10L).copy(
            title = "Santo André para São Tomé das Letras",
            departureAtMillis = Instant.parse("2026-10-10T14:00:00Z").toEpochMilli(),
            stops = listOf(TripStop(order = 0, name = "Santo André"), TripStop(order = 1, name = "São Tomé das Letras")),
        )
        val otherTime = target.copy(id = "other", blablaTripId = "other", departureAtMillis = Instant.parse("2026-10-10T15:00:00Z").toEpochMilli())
        val query = command(
            RotaCertaAction0410.READ_PASSENGERS,
            RotaCertaTemporalReference0410(explicitDate = "2026-10-10", time = "11:00"),
        ).copy(origin = "Santo André", destination = "São Tomé das Letras")
        val planned = RotaCertaCommandPlanner0410.plan(query, listOf(target, otherTime), emptyList(), now, zone)
        assertEquals(RotaCertaValidationCode0410.OK, planned.code)
        assertEquals("target", assertNotNull(planned.plan).trip?.id)
    }

    @Test
    fun publicSearchUsesExistingAuditableCollectorAndRequiresRoute() {
        val missing = RotaCertaCommandPlanner0410.plan(
            command(RotaCertaAction0410.PUBLIC_SEARCH).copy(publicTargetNames = listOf("Alessandra")),
            emptyList(), emptyList(), now, zone,
        )
        assertEquals(RotaCertaValidationCode0410.INVALID_ARGUMENT, missing.code)
        val valid = RotaCertaCommandPlanner0410.plan(
            command(RotaCertaAction0410.PUBLIC_SEARCH, RotaCertaTemporalReference0410(explicitDate = "2026-10-10"))
                .copy(origin = "Santo André", destination = "São Tomé das Letras", publicTargetNames = listOf("Alessandra")),
            emptyList(), emptyList(), now, zone,
        )
        assertEquals(RotaCertaValidationCode0410.OK, valid.code)
    }

    private fun command(
        action: RotaCertaAction0410,
        temporal: RotaCertaTemporalReference0410 = RotaCertaTemporalReference0410(),
        tripReference: String = "",
    ): RotaCertaStructuredCommand0410 =
        RotaCertaStructuredCommand0410(
            action = action,
            temporal = temporal,
            tripReference = tripReference,
        )

    private fun trip(
        id: String,
        revision: Long,
    ): Trip =
        Trip(
            id = id,
            title = "Santo André para Extrema",
            departureAtMillis = Instant.parse("2026-09-05T14:00:00Z").toEpochMilli(),
            status = TripStatus.PUBLISHED,
            stops = listOf(
                TripStop(order = 0, name = "Santo André"),
                TripStop(order = 1, name = "Extrema"),
            ),
            blablaProfileUuid = "11111111-1111-4111-8111-111111111111",
            blablaTripId = id,
            canonicalRevision = revision,
        )
}
