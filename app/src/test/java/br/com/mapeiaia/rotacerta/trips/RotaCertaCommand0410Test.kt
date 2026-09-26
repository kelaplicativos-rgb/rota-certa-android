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
    fun pastedCanonicalJsonIsParsedLocally() {
        val parsed = RotaCertaAssistantJson0413.parse(
            raw = """
                {
                  "schemaVersion":"1.0",
                  "action":"LIST_TRIPS",
                  "temporal":{"explicitDate":"2026-10-10"}
                }
            """.trimIndent(),
            allowedActions = setOf(RotaCertaAction0410.LIST_TRIPS),
        )
        assertEquals(RotaCertaAction0410.LIST_TRIPS, parsed.action)
        assertEquals("2026-10-10", parsed.temporal.explicitDate)
        assertEquals("local_json_0413", parsed.interpretationNotes)
    }

    @Test
    fun createRoundTripCompatibilityPreservesBothTimes() {
        val parsed = RotaCertaAssistantJson0413.parse(
            raw = """
                {
                  "schemaVersion":"1.0",
                  "action":"CREATE_ROUND_TRIP",
                  "mode":"EXECUTE",
                  "date":"2026-12-01",
                  "outbound":{
                    "origin":"Santo André",
                    "destination":"São Tomé das Letras",
                    "departureTime":"11:00"
                  },
                  "return":{
                    "origin":"São Tomé das Letras",
                    "destination":"Santo André",
                    "departureTime":"19:00"
                  },
                  "validation":{
                    "validateCalendarDate":true,
                    "failClosed":true
                  },
                  "onInvalidDate":"REJECT_AND_DO_NOT_PUBLISH"
                }
            """.trimIndent(),
            allowedActions = setOf(RotaCertaAction0410.CREATE_TRIPS),
        )
        assertEquals(RotaCertaAction0410.CREATE_TRIPS, parsed.action)
        assertTrue(parsed.roundTrip)
        assertEquals("2026-12-01", parsed.temporal.explicitDate)
        assertEquals("11:00", parsed.temporal.time)
        assertEquals("19:00", parsed.returnDepartureTime)
        assertEquals("Santo André", parsed.origin)
        assertEquals("São Tomé das Letras", parsed.destination)
        assertEquals("EXECUTE", parsed.executionMode)
    }

    @Test
    fun exactUserJsonRejectsNovember31BeforePublicationPlanning() {
        val parsed = RotaCertaAssistantJson0413.parse(
            raw = """
                {
                  "schemaVersion":"1.0",
                  "action":"CREATE_ROUND_TRIP",
                  "mode":"EXECUTE",
                  "date":"2026-11-31",
                  "outbound":{
                    "origin":"Santo André",
                    "destination":"São Tomé das Letras",
                    "departureTime":"11:00"
                  },
                  "return":{
                    "origin":"São Tomé das Letras",
                    "destination":"Santo André",
                    "departureTime":"19:00"
                  },
                  "profileSelection":{
                    "strategy":"AUTO_RECONCILE",
                    "checkAllDriverProfiles":true
                  },
                  "preExecution":{
                    "runPublicCollector":true,
                    "checkExactDateAndDirection":true,
                    "confirmProfileUuid":true,
                    "checkExistingTrips":true,
                    "preventDuplicates":true,
                    "checkPhysicalContinuity":true,
                    "checkScheduleConflicts":true
                  },
                  "validation":{
                    "validateCalendarDate":true,
                    "failClosed":true
                  },
                  "onInvalidDate":"REJECT_AND_DO_NOT_PUBLISH"
                }
            """.trimIndent(),
            allowedActions = setOf(RotaCertaAction0410.CREATE_TRIPS),
        )
        val planned = RotaCertaCommandPlanner0410.plan(
            command = parsed,
            trips = emptyList(),
            bookings = emptyList(),
            now = now,
            zoneId = zone,
        )
        assertEquals(RotaCertaValidationCode0410.INVALID_DATE, planned.code)
        assertEquals(null, planned.plan)
    }

    @Test
    fun jsonCannotDisableCalendarFailClosedOrEnableBlockedAction() {
        val weakened = runCatching {
            RotaCertaAssistantJson0413.parse(
                raw = """
                    {
                      "schemaVersion":"1.0",
                      "action":"CREATE_TRIPS",
                      "date":"2026-12-01",
                      "validation":{"failClosed":false}
                    }
                """.trimIndent(),
                allowedActions = setOf(RotaCertaAction0410.CREATE_TRIPS),
            )
        }.exceptionOrNull()
        assertTrue(weakened is RotaCertaAssistantJsonException0413)

        val blocked = runCatching {
            RotaCertaAssistantJson0413.parse(
                raw = """{"schemaVersion":"1.0","action":"CANCEL_TRIP"}""",
                allowedActions = setOf(RotaCertaAction0410.LIST_TRIPS),
            )
        }.exceptionOrNull()
        assertTrue(blocked is RotaCertaAssistantJsonException0413)
    }

    @Test
    fun fencedLegacyCreateTripsJsonIsAccepted() {
        val parsed = RotaCertaAssistantJson0413.parse(
            raw = """
                ```json
                {
                  "schemaVersion":"1.0",
                  "action":"CREATE_TRIPS",
                  "mode":"SIMULATION",
                  "dates":["2026-09-05","2026-09-07"],
                  "roundTrip":true,
                  "route":{
                    "outbound":{
                      "origin":"Santo André",
                      "destination":"São Tomé das Letras",
                      "departureTime":"11:00"
                    },
                    "return":{
                      "origin":"São Tomé das Letras",
                      "destination":"Santo André",
                      "departureTime":"19:00"
                    }
                  }
                }
                ```
            """.trimIndent(),
            allowedActions = setOf(RotaCertaAction0410.CREATE_TRIPS),
        )
        assertEquals(
            listOf("2026-09-05", "2026-09-07"),
            parsed.dateTokens,
        )
        assertEquals("SIMULATION", parsed.executionMode)
        assertEquals("19:00", parsed.returnDepartureTime)
    }

    @Test
    fun readVehicleReturnsConfirmedSnapshotDetailsInsteadOfGenericMessage() {
        val account = BlaBlaDynamicAccount(
            id = "account-a",
            label = "Perfil principal",
            webProfileName = "profile-a",
            profileUuid = "11111111-1111-4111-8111-111111111111",
            profileName = "Motorista",
        )
        val snapshot = BlaBlaPublicProfileSnapshot(
            accountId = account.id,
            profileUuid = account.profileUuid!!,
            vehicleMakeModel = "Renault Kwid",
            vehicleColor = "Branco",
            amenities = "Ar-condicionado",
            identityVerified = true,
        )
        val result = assistantVehicleReadResult0414(
            settings = TripOnlineSettings(
                selectedPublicProfileAccountId = account.id,
            ),
            accounts = listOf(account),
            snapshots = listOf(snapshot),
        )
        assertTrue(result.contains("Renault Kwid"))
        assertTrue(result.contains("Branco"))
        assertTrue(result.contains("Ar-condicionado"))
        assertFalse(result.contains("detalhes desnecessários"))
    }

    @Test
    fun readVehicleFailsClosedWhenSnapshotIdentityDoesNotMatchAccount() {
        val account = BlaBlaDynamicAccount(
            id = "account-a",
            label = "Perfil principal",
            webProfileName = "profile-a",
            profileUuid = "11111111-1111-4111-8111-111111111111",
        )
        val mismatched = BlaBlaPublicProfileSnapshot(
            accountId = account.id,
            profileUuid = "22222222-2222-4222-8222-222222222222",
            vehicleMakeModel = "Veículo incorreto",
            identityVerified = true,
        )
        val result = assistantVehicleReadResult0414(
            settings = TripOnlineSettings(
                selectedPublicProfileAccountId = account.id,
            ),
            accounts = listOf(account),
            snapshots = listOf(mismatched),
        )
        assertTrue(result.contains("não tem snapshot autenticado confirmado"))
        assertFalse(result.contains("Veículo incorreto"))
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
