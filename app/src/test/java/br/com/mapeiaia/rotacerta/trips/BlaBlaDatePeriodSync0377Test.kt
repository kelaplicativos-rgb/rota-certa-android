package br.com.mapeiaia.rotacerta.trips

import br.com.mapeiaia.rotacerta.date.RotaCertaDateSelectionMode
import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlaBlaDatePeriodSync0377Test {
    private fun trip(
        id: String,
        date: String,
        time: String = "10:00",
        profileUuid: String = "profile-1",
    ) = BlaBlaCollectorTrip(
        profile_uuid = profileUuid,
        date = date,
        departure_time = time,
        actual_departure = "Origem",
        actual_arrival = "Destino",
        trip_id = id,
    )

    private fun candidate(id: String, dateText: String) = BlaBlaDomRideCandidate(
        href = "https://www.blablacar.com.br/rides/offer?id=$id",
        text = dateText,
        dateText = dateText,
    )

    @Test
    fun singleAndInclusivePeriodValidationRejectIncompleteOrReversedScopes() {
        val date = LocalDate.of(2026, 9, 4)
        assertEquals(
            listOf(date),
            validateBlaBlaSyncDateSelection(RotaCertaDateSelectionMode.SINGLE, date, null, null).dates,
        )
        assertTrue(
            validateBlaBlaSyncDateSelection(RotaCertaDateSelectionMode.SINGLE, null, null, null).error != null,
        )

        val period = validateBlaBlaSyncDateSelection(
            RotaCertaDateSelectionMode.RANGE,
            singleDate = null,
            startDate = LocalDate.of(2026, 9, 4),
            endDate = LocalDate.of(2026, 9, 7),
        )
        assertNull(period.error)
        assertEquals(
            listOf(4, 5, 6, 7),
            period.dates.map { it.dayOfMonth },
        )

        val oneDay = validateBlaBlaSyncDateSelection(
            RotaCertaDateSelectionMode.RANGE,
            null,
            LocalDate.of(2026, 9, 4),
            LocalDate.of(2026, 9, 4),
        )
        assertEquals(listOf(LocalDate.of(2026, 9, 4)), oneDay.dates)
        assertNull(oneDay.error)

        assertTrue(
            validateBlaBlaSyncDateSelection(
                RotaCertaDateSelectionMode.RANGE,
                null,
                LocalDate.of(2026, 9, 7),
                LocalDate.of(2026, 9, 4),
            ).error != null,
        )
        assertTrue(validateBlaBlaSyncDateSelection(RotaCertaDateSelectionMode.RANGE, null, null, LocalDate.of(2026, 9, 7)).error != null)
        assertTrue(validateBlaBlaSyncDateSelection(RotaCertaDateSelectionMode.RANGE, null, LocalDate.of(2026, 9, 4), null).error != null)
    }

    @Test
    fun responseScopeContainsOnlySelectedInclusiveDates() {
        val response = BlaBlaCollectorMonthResponse(
            status = "success",
            trips = (3..8).map { day -> trip("trip-$day", "2026-09-${day.toString().padStart(2, '0')}") },
        )
        val scoped = BlaBlaCollectorTimelineModule.scopeResponseToDates(
            response,
            (4..7).map { day -> LocalDate.of(2026, 9, day) },
        )
        assertEquals(listOf("trip-4", "trip-5", "trip-6", "trip-7"), scoped.trips.mapNotNull { it.trip_id })
        assertFalse(scoped.trips.any { it.date == "2026-09-03" || it.date == "2026-09-08" })
    }

    @Test
    fun cardTraversalEligibilityNeverOpensCardsOutsideSelectedDates() {
        val today = LocalDate.of(2026, 8, 31)
        val selected = BlaBlaCollectorCardModule.candidatesOnDates(
            candidates = listOf(
                candidate("03", "03/09/2026"),
                candidate("04", "04/09/2026"),
                candidate("05", "05/09/2026"),
                candidate("06", "06/09/2026"),
                candidate("07", "07/09/2026"),
                candidate("08", "08/09/2026"),
            ),
            targetDates = (4..7).map { LocalDate.of(2026, 9, it) },
            today = today,
        )
        assertEquals(listOf("04", "05", "06", "07"), selected.map { BlaBlaTripIdentity.externalTripIdFromHref(it.href) })
    }

    @Test
    fun authoritativeScopedReconciliationPreservesOutsideDatesAndReconcilesInside() {
        val previous = listOf(
            trip("outside-before", "2026-09-03"),
            trip("same-trip", "2026-09-04", "10:00"),
            trip("stale-inside", "2026-09-05"),
            trip("outside-after", "2026-09-08"),
        )
        val current = listOf(
            trip("same-trip", "2026-09-04", "11:30"),
            trip("new-inside", "2026-09-06"),
        )
        val scope = (4..7).map { "2026-09-${it.toString().padStart(2, '0')}" }.toSet()
        val merged = BlaBlaCollectorTimelineModule.mergeSnapshotTrips(
            previous = previous,
            current = current,
            authoritativeComplete = true,
            authoritativeDateScope = scope,
        )

        assertEquals(setOf("outside-before", "same-trip", "new-inside", "outside-after"), merged.trips.mapNotNull { it.trip_id }.toSet())
        assertFalse(merged.trips.any { it.trip_id == "stale-inside" })
        assertEquals("11:30", merged.trips.single { it.trip_id == "same-trip" }.departure_time)
    }

    @Test
    fun partialScopedReconciliationNeverDeletesInsideOrOutsideScope() {
        val previous = listOf(
            trip("outside-before", "2026-09-03"),
            trip("inside", "2026-09-04"),
            trip("outside-after", "2026-09-08"),
        )
        val merged = BlaBlaCollectorTimelineModule.mergeSnapshotTrips(
            previous = previous,
            current = emptyList(),
            authoritativeComplete = false,
            authoritativeDateScope = setOf("2026-09-04"),
        )
        assertEquals(previous.mapNotNull { it.trip_id }.toSet(), merged.trips.mapNotNull { it.trip_id }.toSet())
    }

    @Test
    fun repeatedScopedSyncIsIdempotentAndKeepsCanonicalIdentity() {
        val original = trip("canonical-trip", "2026-09-04")
        val first = BlaBlaCollectorTimelineModule.mergeSnapshotTrips(
            previous = listOf(original),
            current = listOf(original.copy(departure_time = "10:30")),
            authoritativeComplete = true,
            authoritativeDateScope = setOf("2026-09-04"),
        ).trips
        val second = BlaBlaCollectorTimelineModule.mergeSnapshotTrips(
            previous = first,
            current = listOf(original.copy(departure_time = "10:30")),
            authoritativeComplete = true,
            authoritativeDateScope = setOf("2026-09-04"),
        ).trips

        assertEquals(1, second.count { it.trip_id == "canonical-trip" })
        assertEquals(
            BlaBlaTripIdentity.evidence(first.single()).key,
            BlaBlaTripIdentity.evidence(second.single()).key,
        )
    }

    @Test
    fun twoAccountsKeepStrongIdsEvenWhenDisplayLabelsMatch() {
        val first = BlaBlaDynamicAccount(
            id = "account-1",
            label = "Motorista",
            webProfileName = "web-1",
            profileUuid = "uuid-profile-1",
        )
        val second = BlaBlaDynamicAccount(
            id = "account-2",
            label = "Motorista",
            webProfileName = "web-2",
            profileUuid = "uuid-profile-2",
        )
        assertEquals(first.displayLabel, second.displayLabel)
        assertNotEquals(first.verifiedDefinition()!!.slot, second.verifiedDefinition()!!.slot)
        assertNotEquals(first.verifiedDefinition()!!.uuid, second.verifiedDefinition()!!.uuid)
    }

    @Test
    fun collectorUiReusesGlobalDatePickerAndExistingSequentialPipeline() {
        val ui = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripBlaBlaCollectorUi.kt").readText()
        val dynamic = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()
        assertTrue(ui.contains("📅 Sincronizar por data/período"))
        assertFalse(ui.contains("Text(\"Sincronizar só hoje\")"))
        assertTrue(ui.contains("RotaCertaDatePickerDialog("))
        assertTrue(ui.contains("RotaCertaDateSelectionField("))
        assertTrue(ui.contains("syncQueue = accounts.map { it.id }"))
        assertTrue(ui.contains("enabled = !syncing && !archiving && !manualSeatSyncing"))
        assertTrue(ui.contains("BlaBlaDynamicSessionIntents.syncDates(context, account, dateScope)"))
        assertTrue(dynamic.contains("EXTRA_ACCOUNT_ID"))
        assertTrue(dynamic.contains("dateScope = targetDates.takeIf { it.isNotEmpty() }"))
        assertTrue(dynamic.contains("outOfScopeCardOpened=false"))
    }
}
