package br.com.mapeiaia.rotacerta.trips

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BlaBlaPublicSearchTest {
    @Test
    fun septemberWholeMonthWithReverseCreatesSixtyQueries() {
        val tasks = BlaBlaPublicSearchPlanner.tasks(
            BlaBlaPublicSearchRequest(
                targetNames = listOf("Barbosa", "Ezequiel S"),
                from = "Santo André, SP, Brasil",
                to = "São Thomé das Letras, MG, Brasil",
                period = "2026-09",
                includeReverse = true,
            ),
            today = LocalDate.of(2026, 8, 29),
        )
        assertEquals(60, tasks.size)
        assertEquals(LocalDate.of(2026, 9, 1), tasks.first().date)
        assertEquals(LocalDate.of(2026, 9, 30), tasks.last().date)
    }

    @Test
    fun currentMonthWithoutDayStartsAtTodayAndNeverScansPastDays() {
        val tasks = BlaBlaPublicSearchPlanner.tasks(
            BlaBlaPublicSearchRequest(
                targetNames = listOf("Barbosa"),
                from = "Santo André",
                to = "São Thomé das Letras",
                period = "2026-08",
                includeReverse = true,
            ),
            today = LocalDate.of(2026, 8, 29),
        )

        assertEquals(6, tasks.size)
        assertEquals(
            listOf(
                LocalDate.of(2026, 8, 29),
                LocalDate.of(2026, 8, 30),
                LocalDate.of(2026, 8, 31),
            ),
            tasks.map(BlaBlaPublicSearchTask::date).distinct(),
        )
        assertTrue(tasks.none { it.date.isBefore(LocalDate.of(2026, 8, 29)) })
    }

    @Test
    fun pastMonthHasNoAutomaticTasksButExplicitPastDayRemainsAllowed() {
        val monthTasks = BlaBlaPublicSearchPlanner.tasks(
            BlaBlaPublicSearchRequest(
                targetNames = listOf("Barbosa"),
                from = "Santo André",
                to = "São Thomé das Letras",
                period = "2026-07",
                includeReverse = false,
            ),
            today = LocalDate.of(2026, 8, 29),
        )
        val exactDayTasks = BlaBlaPublicSearchPlanner.tasks(
            BlaBlaPublicSearchRequest(
                targetNames = listOf("Barbosa"),
                from = "Santo André",
                to = "São Thomé das Letras",
                period = "2026-07-15",
                includeReverse = false,
            ),
            today = LocalDate.of(2026, 8, 29),
        )

        assertTrue(monthTasks.isEmpty())
        assertEquals(listOf(LocalDate.of(2026, 7, 15)), exactDayTasks.map(BlaBlaPublicSearchTask::date))
    }

    @Test
    fun exactDateCreatesOnlyRequestedDirections() {
        val oneWay = BlaBlaPublicSearchPlanner.tasks(
            BlaBlaPublicSearchRequest(listOf("Barbosa"), "Santo André", "São Thomé das Letras", "2026-09-04", false),
        )
        val twoWay = BlaBlaPublicSearchPlanner.tasks(
            BlaBlaPublicSearchRequest(listOf("Barbosa"), "Santo André", "São Thomé das Letras", "2026-09-04", true),
        )
        assertEquals(1, oneWay.size)
        assertEquals(2, twoWay.size)
    }

    @Test
    fun driverMatchingIsAccentAndCaseInsensitiveButNotSubstringBased() {
        assertTrue(BlaBlaPublicSearchPlanner.matchesTarget("EZEQUIEL S", listOf("Ezequiel S")))
        assertTrue(BlaBlaPublicSearchPlanner.matchesTarget("Barbosa", listOf("BARBOSA")))
        assertFalse(BlaBlaPublicSearchPlanner.matchesTarget("João Barbosa", listOf("Barbosa")))
    }

    @Test
    fun emptyDriverFilterAcceptsEveryIdentifiedPublicDriver() {
        assertTrue(BlaBlaPublicSearchPlanner.matchesTarget("Ezequiel S", emptyList()))
        assertTrue(BlaBlaPublicSearchPlanner.matchesTarget("Barbosa", listOf(" ", "")))
        assertFalse(BlaBlaPublicSearchPlanner.matchesTarget("", emptyList()))
    }

    @Test
    fun explicitDriverFilterStillKeepsOnlyRequestedProfiles() {
        assertTrue(BlaBlaPublicSearchPlanner.matchesTarget("Barbosa", listOf("Barbosa")))
        assertFalse(BlaBlaPublicSearchPlanner.matchesTarget("Ezequiel S", listOf("Barbosa")))
    }

    @Test
    fun monitoredProfilesReceiveDistinctStableVisualSlots() {
        val targets = listOf("Ezequiel S", "Barbosa", "Carlos", "Daniela")
        val slots = targets.map { BlaBlaPublicSearchPlanner.profileVisualSlot(it, targets) }
        assertEquals(4, slots.distinct().size)
        assertEquals(
            BlaBlaPublicSearchPlanner.profileVisualSlot("Barbosa", targets),
            BlaBlaPublicSearchPlanner.profileVisualSlot("BARBOSA", targets.reversed()),
        )
        assertNotEquals(
            BlaBlaPublicSearchPlanner.profileVisualSlot("Barbosa", targets),
            BlaBlaPublicSearchPlanner.profileVisualSlot("Ezequiel S", targets),
        )
    }

    @Test
    fun directionComesFromTheSearchedRouteNotFromTheProfile() {
        val request = BlaBlaPublicSearchRequest(
            targetNames = listOf("Barbosa", "Ezequiel S"),
            from = "Santo André, SP, Brasil",
            to = "São Thomé das Letras, MG, Brasil",
            period = "2026-09",
            includeReverse = true,
        )
        assertEquals(
            BlaBlaPublicSearchDirection.PRIMARY,
            BlaBlaPublicSearchPlanner.direction("Santo André", "São Thomé das Letras", request),
        )
        assertEquals(
            BlaBlaPublicSearchDirection.REVERSE,
            BlaBlaPublicSearchPlanner.direction("São Thomé das Letras", "Santo André", request),
        )
        assertEquals(
            BlaBlaPublicSearchDirection.UNKNOWN,
            BlaBlaPublicSearchPlanner.direction("Santo André", "Três Corações", request),
        )
    }

    @Test
    fun knownCorridorCitiesResolveWithoutRailwayOrLogin() {
        assertTrue(BlaBlaPublicPlaceDirectory.supported("Santo André, SP, Brasil"))
        assertTrue(BlaBlaPublicPlaceDirectory.supported("São Thomé das Letras, MG, Brasil"))
        assertTrue(BlaBlaPublicPlaceDirectory.searchUrl(
            BlaBlaPublicSearchTask(LocalDate.of(2026, 9, 4), "Santo André, SP, Brasil", "São Thomé das Letras, MG, Brasil"),
        )!!.contains("db=2026-09-04"))
    }

    @Test
    fun publicCardMatchesAgendaByDateTimeAndDirection() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val trip = sampleTrip(zone)
        val card = BlaBlaPublicSearchCard(
            driverName = "Ezequiel S",
            date = "2026-09-04",
            searchFrom = "Santo André, SP, Brasil",
            searchTo = "São Thomé das Letras, MG, Brasil",
            departureTime = "11:00",
        )
        assertTrue(publicCardMatchesAgendaTrip(card, trip, zone))
        assertFalse(publicCardMatchesAgendaTrip(card.copy(departureTime = "13:00"), trip, zone))
    }

    @Test
    fun failedQueryNeverAuthorizesAgendaMissingConfirmation() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val trip = sampleTrip(zone)
        val request = BlaBlaPublicSearchRequest(
            targetNames = listOf("Ezequiel S"),
            from = "Santo André",
            to = "São Thomé das Letras",
            period = "2026-09",
            includeReverse = true,
        )
        val failed = BlaBlaPublicSearchResponse(
            status = "partial",
            request = request,
            queries = listOf(
                BlaBlaPublicSearchQueryResult(
                    date = "2026-09-04",
                    from = "Santo André",
                    to = "São Thomé das Letras",
                    status = "mismatch",
                ),
            ),
        )
        assertFalse(validatedQueryCoversAgendaTrip(failed, trip, zone))

        val validated = failed.copy(
            status = "validated",
            queries = listOf(
                BlaBlaPublicSearchQueryResult(
                    date = "2026-09-04",
                    from = "Santo André",
                    to = "São Thomé das Letras",
                    status = "validated",
                    zeroResultsConfirmed = true,
                ),
            ),
        )
        assertTrue(validatedQueryCoversAgendaTrip(validated, trip, zone))
    }

    private fun sampleTrip(zone: ZoneId): Trip {
        val departure = LocalDate.of(2026, 9, 4).atTime(11, 0).atZone(zone).toInstant().toEpochMilli()
        return Trip(
            title = "Santo André → São Thomé",
            departureAtMillis = departure,
            status = TripStatus.PUBLISHED,
            stops = listOf(
                TripStop(order = 0, name = "Santo André"),
                TripStop(order = 1, name = "São Thomé das Letras"),
            ),
        )
    }
}
