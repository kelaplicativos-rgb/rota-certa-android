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
            blablaProfileUuid = "7371f028-9c55-4903-8444-308015823efd",
            blablaTripId = id,
            canonicalRevision = revision,
        )
}
