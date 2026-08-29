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
    fun noSelectedDateStartsTodayAndContinuesOnlyThroughCurrentMonth() {
        val tasks = BlaBlaPublicSearchPlanner.tasks(
            BlaBlaPublicSearchRequest(
                targetNames = emptyList(),
                from = "Santo André",
                to = "São Thomé das Letras",
                period = "",
                includeReverse = false,
                selectedDates = emptyList(),
            ),
            today = LocalDate.of(2026, 8, 29),
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 8, 29),
                LocalDate.of(2026, 8, 30),
                LocalDate.of(2026, 8, 31),
            ),
            tasks.map(BlaBlaPublicSearchTask::date),
        )
        assertTrue(tasks.none { it.date.isBefore(LocalDate.of(2026, 8, 29)) })
    }

    @Test
    fun threeExplicitDatesWithReverseCreateExactlySixIndependentTasks() {
        val tasks = BlaBlaPublicSearchPlanner.tasks(
            BlaBlaPublicSearchRequest(
                targetNames = emptyList(),
                from = "Santo André",
                to = "São Thomé das Letras",
                period = "",
                includeReverse = true,
                selectedDates = listOf("2026-09-05", "2026-09-07", "2026-09-12"),
            ),
            today = LocalDate.of(2026, 8, 29),
        )
        assertEquals(6, tasks.size)
        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 9, 7),
                LocalDate.of(2026, 9, 12),
            ),
            tasks.map(BlaBlaPublicSearchTask::date).distinct(),
        )
        tasks.chunked(2).forEach { pair ->
            assertEquals("Santo André", pair[0].from)
            assertEquals("São Thomé das Letras", pair[0].to)
            assertEquals("São Thomé das Letras", pair[1].from)
            assertEquals("Santo André", pair[1].to)
        }
    }

    @Test
    fun demandToggleOffSkipsAdditionalDemandProcessing() {
        val request = BlaBlaPublicSearchRequest(
            targetNames = emptyList(),
            from = "Santo André",
            to = "São Thomé das Letras",
            selectedDates = listOf("2026-09-05"),
            captureDemand = false,
        )
        val task = BlaBlaPublicSearchTask(LocalDate.of(2026, 9, 5), request.from, request.to)
        assertEquals(
            null,
            publicSearchDemandFor(
                request,
                task,
                "Trecho concorrido! É bom reservar logo. 62% das viagens já estão reservadas.",
                capturedAtMillis = 123L,
            ),
        )
    }

    @Test
    fun demandCaptureExtractsBusyFlagPercentageMessageAndTimestamp() {
        val request = BlaBlaPublicSearchRequest(
            targetNames = emptyList(),
            from = "Santo André",
            to = "São Thomé das Letras",
            selectedDates = listOf("2026-09-05"),
            captureDemand = true,
        )
        val task = BlaBlaPublicSearchTask(LocalDate.of(2026, 9, 5), request.from, request.to)
        val demand = publicSearchDemandFor(
            request,
            task,
            "Trecho concorrido! É bom reservar logo. 62% das viagens já estão reservadas.",
            capturedAtMillis = 987654321L,
        )!!
        assertTrue(demand.indicadorDemandaEncontrado)
        assertEquals(true, demand.trechoConcorrido)
        assertEquals(62, demand.percentualReservado)
        assertTrue(demand.mensagemDemanda.orEmpty().contains("Trecho concorrido", ignoreCase = true))
        assertTrue(demand.mensagemDemanda.orEmpty().contains("62%"))
        assertEquals("2026-09-05", demand.date)
        assertEquals("Santo André", demand.from)
        assertEquals("São Thomé das Letras", demand.to)
        assertEquals(987654321L, demand.dataHoraCaptura)
    }

    @Test
    fun missingDemandIndicatorStaysUnknownInsteadOfInventingLowDemand() {
        val request = BlaBlaPublicSearchRequest(
            targetNames = emptyList(),
            from = "Santo André",
            to = "São Thomé das Letras",
            selectedDates = listOf("2026-09-05"),
            captureDemand = true,
        )
        val task = BlaBlaPublicSearchTask(LocalDate.of(2026, 9, 5), request.from, request.to)
        val demand = publicSearchDemandFor(
            request,
            task,
            "Viagens encontradas. Escolha uma opção abaixo.",
            capturedAtMillis = 10L,
        )!!
        assertFalse(demand.indicadorDemandaEncontrado)
        assertEquals(null, demand.trechoConcorrido)
        assertEquals(null, demand.percentualReservado)
        assertEquals(null, demand.mensagemDemanda)
    }

    @Test
    fun demandStaysAssociatedWithExactDateAndDirection() {
        val request = BlaBlaPublicSearchRequest(
            targetNames = emptyList(),
            from = "Santo André",
            to = "São Thomé das Letras",
            selectedDates = listOf("2026-09-05"),
            includeReverse = true,
            captureDemand = true,
        )
        val tasks = BlaBlaPublicSearchPlanner.tasks(request, LocalDate.of(2026, 8, 29))
        val outbound = publicSearchDemandFor(
            request,
            tasks[0],
            "Trecho concorrido! É bom reservar logo. 78% das viagens já estão reservadas.",
            capturedAtMillis = 1L,
        )!!
        val inbound = publicSearchDemandFor(
            request,
            tasks[1],
            "Viagens encontradas sem indicador especial.",
            capturedAtMillis = 2L,
        )!!
        assertEquals("Santo André", outbound.from)
        assertEquals("São Thomé das Letras", outbound.to)
        assertEquals(78, outbound.percentualReservado)
        assertEquals("São Thomé das Letras", inbound.from)
        assertEquals("Santo André", inbound.to)
        assertFalse(inbound.indicadorDemandaEncontrado)
        assertEquals(null, inbound.percentualReservado)
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
